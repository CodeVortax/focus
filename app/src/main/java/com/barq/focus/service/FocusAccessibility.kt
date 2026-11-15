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
import com.barq.focus.data.Moment
import com.barq.focus.data.support
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
        /** How long the feed may go unseen before the session ends (a paused Short sends no events). */
        private const val FOUND_TIMEOUT_MS = 8_000L
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
    private var lastRevive = 0L
    private var landingCounted = false     // startShorts already counted the video you landed on
    private var pagerSince = 0L            // the id-free fallback must hold for a moment before it starts a session
    private var pagerLastAt = 0L
    private var lastFailToast = 0L

    override fun onServiceConnected() {
        instance = this
        store = Store(this); db = Db.get(this); ov = Overlay.get(this)
        Shared.treatUntil = maxOf(Shared.treatUntil, store.treatUntilSaved)   // minutes already paid for survive a killed service
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
        // a swipe that arrives while no session is open (it timed out, or you came back within 90 s) must still count
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && !inShorts && now >= ignoreUntil &&
            now - lastScan >= 700 && !sideways(e) && bigSource(e)
        ) {
            lastScan = now
            try { scan(pkg) } catch (_: Exception) {}
            if (inShorts && !landingCounted) { store.addShortsSwipe(); if (row >= 0) db.addCount(row, 1) }
            checkGate()
            return
        }
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
        if (steps > 0) { lastOneCue(); checkGate() }
    }

    /** One quiet cue when the pass is about to run out, so the end is a known point and not a sudden wall. */
    private fun lastOneCue() {
        if (store.strictShorts) return
        val allow = store.shortsAllow()
        if (allow >= 0 && store.shortsSwipes() == allow) {
            try { android.widget.Toast.makeText(this, tr("Last one in this pass"), android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    /** Shows the gate at once when a swipe crosses the limit, not only on the next one-second tick. */
    private fun checkGate() {
        val now = System.currentTimeMillis()
        if (inShorts && !ov.showing && now > Shared.suppressUntil && shortsGateDue(now)) showShortsOverlay()
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

        val byId = ShortsDetector.isShorts(pkg, root)
        var found = byId
        if (!byId && ShortsDetector.isPager(pkg, root, sw, sh)) {
            // id-free fallback: it keeps a session alive at once, but starts one only if it holds for a moment, so
            // scrolling past a full-screen post in a normal feed cannot trigger the gate
            val t = System.currentTimeMillis()
            if (t - pagerLastAt > 3000L) pagerSince = t
            pagerLastAt = t
            found = inShorts || t - pagerSince >= 1200L
        }

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
        row = db.open("shorts", pkg, now)
        landingCounted = !(last != null && end != null && now - end in 0..90_000L)
        if (landingCounted) {
            db.addCount(row, 1); store.addShortsSwipe() // the video you landed on (not again when you just stepped out and back)
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
                // Android can kill the watcher service and never restart it; this service is brought back by the system, so it can.
                if (!Shared.watchAlive && now - lastRevive > 15_000L) { lastRevive = now; Perms.ensureService(this@FocusAccessibility) }
                if (inShorts) {
                    // Our own gate pauses the video (audio focus), and a paused feed sends no accessibility events, so nothing
                    // refreshes lastFoundAt while the gate is up. Without this the session timed out after 4 s, the gate was
                    // taken down, the video resumed, was found again, and the gate came back: the Shorts flicker.
                    // While the gate covers the app, being in front is the proof the feed is still there.
                    if (ov.showing && ov.tag == "shorts" && coveredByGate()) lastFoundAt = now
                    // a paused or still Short sends no events: look for the feed ourselves every few seconds
                    if (!ov.showing && now - lastScan > 3000L) { lastScan = now; shortsPkg?.let { try { scan(it) } catch (_: Exception) {} } }
                    if (now - lastFoundAt > FOUND_TIMEOUT_MS || (Shared.watchAlive && Shared.fg != null && Shared.fg != shortsPkg && shortsPkg !in visiblePackages())) endShorts()
                    else if (shortsGateDue(now) && now > Shared.suppressUntil && !ov.showing) showShortsOverlay()
                    // keep the baseline fresh while nothing moves, so autoplay or a like is not mistaken for a swipe
                    if (inShorts && !burstPending && now - lastScrollAt > 800L) {
                        shortsPkg?.let { p -> rootFor(p)?.let { r -> lastFp = ShortsDetector.fingerprint(r) } }
                    }
                }
            } catch (_: Exception) {}
            h.postDelayed(this, 1000)
        }
    }

    /** The Shorts app is still the one under our gate (in front, or at least still has a window on screen). */
    private fun coveredByGate(): Boolean {
        val p = shortsPkg ?: return false
        if (Shared.watchAlive && Shared.fg == p) return true
        return p in visiblePackages()
    }

    /**
     * A bought treat covers everything and a typed pass covers its minutes. Otherwise Shorts are counted in videos,
     * and a "watch more" pass also runs out with time, so an unused allowance cannot be saved for later.
     */
    private fun shortsGateDue(now: Long): Boolean {
        if (now < Shared.treatUntil) return false
        if (now < allowedUntil) return false
        if (store.strictShorts) return true
        val allow = store.shortsAllow()
        return allow < 0 || store.shortsSwipes() > allow || now >= store.shortsAllowUntil()
    }

    private fun grantCount(reason: String, n: Int) {
        store.setShortsAllow(store.shortsSwipes() + n)
        store.setShortsAllowUntil(System.currentTimeMillis() + Config.SHORTS_PASS_MINUTES * 60_000L)
        store.addShortsPass()
        if (row >= 0) db.setReason(row, reason)
    }

    /** A treat: its minutes cover every gate, and the end time is saved so a killed service does not lose it. */
    private fun giveTreat(minutes: Int) {
        Shared.treatUntil = System.currentTimeMillis() + minutes * 60_000L
        store.treatUntilSaved = Shared.treatUntil
    }

    private fun grant(reason: String, minutes: Int) {
        allowedUntil = System.currentTimeMillis() + minutes * 60_000L
        if (row >= 0) db.setReason(row, reason)
    }

    private fun showShortsOverlay() {
        // the gate cannot be drawn without the overlay permission: leave the feed instead of letting it through
        if (!android.provider.Settings.canDrawOverlays(this)) { failClosed(); return }
        if (store.strictShorts) { showStrictOverlay(tr("Strict mode")); return }
        val bank = store.bank()
        val ch = ArrayList<Choice>()
        val swiped = store.shortsSwipes()
        val limit = store.shortsLimit
        val left = if (limit <= 0) Int.MAX_VALUE else (limit - swiped).coerceAtLeast(0)
        val passes = store.shortsPasses()
        // the bargaining ends: after enough passes in one day, more needs the typed sentence
        if (passes >= Config.SHORTS_MAX_PASSES && left > 0) { showStrictOverlay(tr("Many passes today")); return }
        // one small offer, no big numbers to bargain for
        val n = minOf(Config.SHORTS_PASS_VIDEOS, left)
        if (n > 0) ch += Choice(trf("Watch %d more", n), 0xFF4285F4.toInt()) { grantCount("Shorts x$n", n) }
        else ch += Choice(tr("Daily limit reached"), 0xFF555866.toInt(), enabled = false) {}
        if (bank >= Config.TREAT_STEP) {
            ch += Choice(trf("Treat  ·  %d min  (bank %d)", Config.TREAT_STEP, bank), 0xFF34A853.toInt()) {
                store.spend(Config.TREAT_STEP); giveTreat(Config.TREAT_STEP); if (row >= 0) db.setReason(row, "Treat")
            }
        } else {
            ch += Choice(tr("Treat bank empty: finish tasks to earn"), 0xFF555866.toInt(), enabled = false) {}
        }
        val mins = db.totalsSince("shorts", startOfDay()).values.sum() / 60_000
        // the person's own reason first, then the companion's line
        val why = store.shortsWhy
        val note = listOfNotNull(
            if (why.isNotBlank()) tr("Your why") + ": " + why else null,
            store.support(if (bank >= Config.TREAT_STEP) Moment.GATE_EARNED else Moment.GATE_EMPTY),
        ).joinToString("\n\n").ifBlank { null }
        ov.show(
            tag = "shorts",
            title = tr(if (shortsPkg in ShortsDetector.TIKTOK) "TikTok" else "Shorts"),
            subtitle = if (limit > 0) trf("%d of %d today  ·  %d min", swiped, limit, mins) else trf("%d swiped today  ·  %d min", swiped, mins),
            // the wait doubles with every pass taken today: 3, 6, 12, 24, 30 s
            delaySec = minOf(Config.DELAY_SECONDS shl passes.coerceAtMost(4), Config.SHORTS_WAIT_MAX),
            choices = ch,
            leaveLabel = tr("Back out"),
            note = note,
            onLeave = {
                if (row >= 0) db.setReason(row, "Backed out")
                if (shortsPkg in ShortsDetector.TIKTOK) { exitApp(shortsPkg!!); endShorts() } else backOut()
            }
        )
    }

    /** The overlay permission was taken away: say so (rarely) and leave the feed, so that revoking it is not a way round the gate. */
    private fun failClosed() {
        val now = System.currentTimeMillis()
        if (now - lastFailToast > 30_000L) {
            lastFailToast = now
            try { android.widget.Toast.makeText(this, tr("Focus needs the Display over other apps permission to guard Shorts"), android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
        }
        backOut()
    }

    /** Strict mode: no "finish this one", no treat button, no swipe count. The only way on is a sentence typed by hand. */
    private fun showStrictOverlay(reason: String) {
        ov.show(
            tag = "shorts",
            title = tr(if (shortsPkg in ShortsDetector.TIKTOK) "TikTok" else "Shorts"),
            subtitle = reason,
            delaySec = 0,
            choices = emptyList(),
            strictPhrase = tr("I am choosing to scroll right now, and I know it takes time from what matters to me."),
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
