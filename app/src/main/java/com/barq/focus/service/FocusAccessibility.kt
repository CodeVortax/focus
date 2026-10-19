/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import com.barq.focus.data.tr
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.barq.focus.data.Config
import com.barq.focus.data.Db
import com.barq.focus.data.Store
import com.barq.focus.data.trf
import com.barq.focus.data.startOfDay
import kotlin.math.abs

/**
 * Detects short-form feeds (YouTube Shorts, Instagram Reels), counts swipes + time,
 * gates them with the treat bank, and backs out of apps cleanly (so no picture-in-picture).
 */
class FocusAccessibility : AccessibilityService() {
    companion object {
        @Volatile var instance: FocusAccessibility? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var db: Db
    private lateinit var ov: Overlay

    private var inShorts = false
    private var shortsPkg: String? = null
    private var row = -1L
    private var lastFoundAt = 0L
    private var lastScan = 0L
    // swipe counting: count video CHANGES after the page settles, not raw scroll events
    private var lastIdx = -1
    private var lastFp = 0
    private var burstIdx = -1
    private var burstPending = false
    private var lastScrollAt = 0L
    private val sw get() = resources.displayMetrics.widthPixels
    private val sh get() = resources.displayMetrics.heightPixels
    private var ignoreUntil = 0L
    private var allowedUntil = 0L

    override fun onServiceConnected() {
        instance = this
        store = Store(this); db = Db.get(this); ov = Overlay.get(this)
        Gray.recover(this)
        h.post(tick)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        h.removeCallbacksAndMessages(null)
        if (::db.isInitialized) endShorts()
        Gray.off(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg !in ShortsDetector.PACKAGES) return
        val now = System.currentTimeMillis()
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && inShorts &&
            pkg == shortsPkg && !sideways(e) && bigSource(e)
        ) {
            // the pager only reports an index when exactly one page is showing
            burstIdx = if (e.fromIndex >= 0 && e.fromIndex == e.toIndex) e.fromIndex else -1
            burstPending = true
            lastScrollAt = now
            h.removeCallbacks(settle)
            h.postDelayed(settle, 250)
        }
        if (now - lastScan < 700 || now < ignoreUntil) return
        lastScan = now
        try { scan(pkg) } catch (_: Exception) {}
    }

    /** Sideways scrolling is a carousel, not a video change. */
    private fun sideways(e: AccessibilityEvent) =
        Build.VERSION.SDK_INT >= 28 && e.scrollDeltaX != 0 && e.scrollDeltaY == 0

    /** The comment sheet and other small lists scroll too; the feed pager is screen-sized. */
    private fun bigSource(e: AccessibilityEvent): Boolean {
        val src = try { e.source } catch (_: Exception) { null } ?: return true
        val r = Rect()
        src.getBoundsInScreen(r)
        return r.width() >= sw * 0.85 && r.height() >= sh * 0.6
    }

    /** Runs once the page stopped moving: did the video really change, and by how many? */
    private val settle = Runnable {
        val pkg = shortsPkg
        val root = pkg?.let { rootFor(it) }
        if (!inShorts || pkg == null || root == null) {
            burstIdx = -1; burstPending = false
            return@Runnable
        }
        val fp = ShortsDetector.fingerprint(root)
        var steps = 0
        if (burstIdx >= 0 && lastIdx >= 0 && burstIdx != lastIdx) {
            val d = abs(burstIdx - lastIdx)
            steps = if (d > 8) 1 else d          // a huge jump is a feed refresh, not many swipes
        } else if (lastFp != 0 && fp != lastFp) {
            steps = 1                             // no usable index: the visible video changed
        }
        if (burstIdx >= 0) lastIdx = burstIdx
        lastFp = fp
        burstIdx = -1; burstPending = false
        repeat(steps) { store.addShortsSwipe(); if (row >= 0) db.addCount(row, 1) }
    }

    /** Every app that has a window on screen right now: split screen, pop-up view, freeform and floating windows included. */
    fun visiblePackages(): Set<String> {
        val out = HashSet<String>()
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                w.root?.packageName?.toString()?.let { out += it }
            }
        } catch (_: Exception) {}
        return out
    }

    /** The window of [pkg], even when another app has the focus (the app may be in a floating window). */
    private fun rootFor(pkg: String): AccessibilityNodeInfo? {
        val active = try { rootInActiveWindow } catch (_: Exception) { null }
        if (active?.packageName?.toString() == pkg) return active
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root
                if (r?.packageName?.toString() == pkg) return r
            }
        } catch (_: Exception) {}
        return null
    }

    private fun scan(pkg: String) {
        val root = rootFor(pkg) ?: return

        val found = ShortsDetector.isShorts(pkg, root) || ShortsDetector.isPager(pkg, root, sw, sh)

        if (store.debug && pkg == ShortsDetector.YT) {
            store.debugIds = "shortsDetected=$found\n" + ShortsDetector.harvestIds(root).joinToString("\n")
        }

        if (found) {
            lastFoundAt = System.currentTimeMillis()
            if (!inShorts) startShorts(pkg)
        }
    }

    private fun startShorts(pkg: String) {
        val now = System.currentTimeMillis()
        inShorts = true; Shared.inShorts = true
        shortsPkg = pkg
        lastFoundAt = now
        if (store.strictShorts) Gray.on(this)
        lastIdx = -1; burstIdx = -1; burstPending = false
        lastFp = try { rootFor(pkg)?.let { ShortsDetector.fingerprint(it) } ?: 0 } catch (_: Exception) { 0 }
        // coming back within 90 s continues the same burst instead of starting a new row
        val last = db.lastRow("shorts", pkg)
        val end = last?.t1
        if (last != null && end != null && now - end in 0..90_000L) {
            db.reopen(last.id); row = last.id
        } else {
            row = db.open("shorts", pkg, now)
            db.addCount(row, 1); store.addShortsSwipe() // the video you landed on
        }
    }

    private fun endShorts() {
        if (!inShorts) return
        inShorts = false; Shared.inShorts = false
        Gray.off(this)
        if (row >= 0) { db.close(row, System.currentTimeMillis()); row = -1 }
        if (ov.showing && ov.tag == "shorts") ov.hide()
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                if (inShorts) {
                    if (now - lastFoundAt > 4000L || (Shared.watchAlive && Shared.fg != null && Shared.fg != shortsPkg && shortsPkg !in visiblePackages())) endShorts()
                    else if (now > allowedUntil && now > Shared.suppressUntil && !ov.showing) showShortsOverlay()
                    // keep the baseline fresh while nothing moves, so autoplay or a like is not mistaken for a swipe
                    if (inShorts && !burstPending && now - lastScrollAt > 800L) {
                        shortsPkg?.let { p -> rootFor(p)?.let { r -> lastFp = ShortsDetector.fingerprint(r) } }
                    }
                }
            } catch (_: Exception) {}
            h.postDelayed(this, 1000)
        }
    }

    private fun grant(reason: String, minutes: Int) {
        allowedUntil = System.currentTimeMillis() + minutes * 60_000L
        if (row >= 0) db.setReason(row, reason)
    }

    private fun showShortsOverlay() {
        if (store.strictShorts) { showStrictOverlay(); return }
        val bank = store.bank()
        val ch = ArrayList<Choice>()
        ch += Choice(trf("Just finish this one  ·  %d min", Config.SHORTS_GRACE_MIN), 0xFF4285F4.toInt()) {
            grant("Grace", Config.SHORTS_GRACE_MIN)
        }
        if (bank >= Config.TREAT_STEP) {
            ch += Choice(trf("Treat  ·  %d min  (bank %d)", Config.TREAT_STEP, bank), 0xFF34A853.toInt()) {
                store.spend(Config.TREAT_STEP); grant("Treat", Config.TREAT_STEP)
            }
            if (bank > Config.TREAT_STEP) ch += Choice(trf("Use all  ·  %d min", bank), 0xFF34A853.toInt()) {
                store.spend(bank); grant("Treat (all)", bank)
            }
        } else {
            ch += Choice(tr("Treat bank empty: finish tasks to earn"), 0xFF555866.toInt(), enabled = false) {}
        }
        val mins = db.totalsSince("shorts", startOfDay()).values.sum() / 60_000
        ov.show(
            tag = "shorts",
            title = tr(if (shortsPkg in ShortsDetector.TIKTOK) "TikTok" else "Shorts"),
            subtitle = trf("%d swiped today  ·  %d min", store.shortsSwipes(), mins),
            delaySec = Config.DELAY_SECONDS,
            choices = ch,
            leaveLabel = tr("Back out"),
            onLeave = { if (shortsPkg in ShortsDetector.TIKTOK) { exitApp(shortsPkg!!); endShorts() } else backOut() }
        )
    }

    /** Strict mode: no "finish this one", no treat button, no swipe count. The only way on is a sentence typed by hand. */
    private fun showStrictOverlay() {
        ov.show(
            tag = "shorts",
            title = tr(if (shortsPkg in ShortsDetector.TIKTOK) "TikTok" else "Shorts"),
            subtitle = tr("Strict mode"),
            delaySec = 0,
            choices = emptyList(),
            strictPhrase = tr("I am wasting my time right now and I know it hurts my focus."),
            typedLabel = trf("Typing it unlocks %d min. You have to type it every time.", Config.STRICT_EXTEND_MIN),
            onTyped = { grant("Typed", Config.STRICT_EXTEND_MIN) },
            leaveLabel = tr("Close it"),
            onLeave = { shortsPkg?.let { exitApp(it) }; endShorts() }
        )
    }

    /** One BACK leaves the Shorts feed; the cooldown stops us re-detecting during the transition. */
    private fun backOut() {
        val until = System.currentTimeMillis() + 2500
        Shared.suppressUntil = until
        ignoreUntil = until
        h.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK); endShorts() }, 250)
    }

    /** Press BACK until the app is gone (never HOME first: that is what triggers picture-in-picture). */
    fun exitApp(pkg: String) {
        Shared.suppressUntil = System.currentTimeMillis() + 6000
        var n = 0
        val r = object : Runnable {
            override fun run() {
                val cur = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
                if (cur != pkg) {
                    // not the focused window: if the app is still on screen (floating or split window), Back would hit the wrong window
                    if (pkg in visiblePackages()) { Leaver.home(this@FocusAccessibility); Leaver.kill(this@FocusAccessibility, pkg, 900) }
                    else Leaver.kill(this@FocusAccessibility, pkg, 500)
                    return
                }
                if (n >= 8) {
                    Leaver.home(this@FocusAccessibility)
                    Leaver.kill(this@FocusAccessibility, pkg, 900)
                    return
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                n++
                h.postDelayed(this, 450)
            }
        }
        h.postDelayed(r, 250)
    }
}
