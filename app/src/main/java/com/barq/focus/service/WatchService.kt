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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.barq.focus.data.Apps
import com.barq.focus.data.Block
import com.barq.focus.data.Config
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.data.Db
import com.barq.focus.data.Store
import com.barq.focus.data.startOfDay
import java.util.Calendar

/**
 * Polls UsageStats once a second to learn the foreground app, logs time per tracked app,
 * shows the "what for?" overlay for gated apps, and logs media playback (incl. background music).
 */
class WatchService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var db: Db
    private lateinit var overlay: Overlay

    private var started = false
    private var replaying = false
    private var fg: String? = null
    private var fgRow = -1L
    private var lastEventTs = 0L
    private var tick = 0
    private var lastTouchTry = 0L
    private val allowedUntil = HashMap<String, Long>()
    private val blockPass = HashMap<String, Long>()
    private var always: Set<String> = emptySet()
    private val musicRows = HashMap<String, Long>()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        db = Db.get(this)
        overlay = Overlay.get(this)
        db.fixOpen()
        always = Apps.alwaysAllowed(this)
        Shared.watchAlive = true

        ServiceCompat.startForeground(
            this, Notifier.STATUS_ID, Notifier.status(this, store),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun refreshNotifs() {
        store.touchDay()
        store.notePlanned(store.cards())
        val nm = getSystemService(NotificationManager::class.java)
        Notifier.refreshStatus(this, store)

        val now = System.currentTimeMillis()
        Reminders.checkNudge(this, store)
        if (store.lastRemind == 0L) { store.lastRemind = now; return }
        val (left, desc) = store.leftSummary(store.cards())
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timerRunning = store.activeCardId != -1L && !store.activePaused
        if (left > 0 && !timerRunning && hour in Config.REMIND_FROM_HOUR until Config.REMIND_TO_HOUR &&
            now - store.lastRemind >= Config.REMIND_EVERY_MIN * 60_000L
        ) {
            store.lastRemind = now
            nm.notify(
                2,
                NotificationCompat.Builder(this, Notifier.CH_REMIND)
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentTitle(trf("%d tasks left on your plate", left))
                    .setContentText(trf("%s. Pick one and start the timer.", desc))
                    .setAutoCancel(true)
                    .setContentIntent(TimerAlarm.openApp(this))
                    .build()
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            bootstrap()
            h.post(loop)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Shared.watchAlive = false
        TouchCounter.stop()
        h.removeCallbacksAndMessages(null)
        overlay.hide()
        val now = System.currentTimeMillis()
        if (fgRow >= 0) db.close(fgRow, now)
        musicRows.values.forEach { db.close(it, now) }
        super.onDestroy()
    }

    private val loop = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { Log.e("Focus", "step failed", e) }
            h.postDelayed(this, 1000)
        }
    }

    private fun events(from: Long): List<Triple<String, Int, Long>> {
        val out = ArrayList<Triple<String, Int, Long>>()
        val usm = getSystemService(UsageStatsManager::class.java)
        val evs = usm.queryEvents(from, System.currentTimeMillis() + 1000) ?: return out
        val e = UsageEvents.Event()
        while (evs.hasNextEvent()) {
            evs.getNextEvent(e)
            out.add(Triple(e.packageName ?: "", e.eventType, e.timeStamp))
        }
        return out
    }

    private fun bootstrap() {
        val now = System.currentTimeMillis()
        var maxTs = 0L
        replaying = true
        for ((pkg, type, ts) in events(now - 2 * 3600_000L)) {
            maxTs = maxOf(maxTs, ts)
            onEvent(pkg, type, ts)
        }
        replaying = false
        lastEventTs = if (maxTs > 0L) maxTs else now
        // if we (re)started while a tracked app is already in front, start logging it from now
        val p = fg
        fg = null
        if (p != null) setFg(p, now)
    }

    private fun onEvent(pkg: String, type: Int, ts: Long) {
        when (type) {
            1 -> setFg(pkg, ts)                       // ACTIVITY_RESUMED
            2 -> if (pkg == fg) setFg(null, ts)       // ACTIVITY_PAUSED
            16 -> setFg(null, ts)                     // SCREEN_NON_INTERACTIVE
        }
    }

    private fun setFg(p: String?, ts: Long) {
        if (p == fg) return
        if (replaying) { fg = p; Shared.fg = p; return }
        if (fgRow >= 0) { db.close(fgRow, ts); fgRow = -1 }
        fg = p
        Shared.fg = p
        if (p != null && p in Config.TRACKED) fgRow = openOrMerge("app", p, ts, 90_000L)
    }

    private fun step() {
        val now = System.currentTimeMillis()
        for ((pkg, type, ts) in events(lastEventTs)) {
            if (ts <= lastEventTs) continue
            lastEventTs = ts
            onEvent(pkg, type, ts)
        }

        val cur = fg
        val gated = store.gated()
        val lockBlock = store.activeBlock()?.takeIf { it.lock }
        val blocked = lockBlock != null && cur != null && !isAllowed(lockBlock, cur)

        if (blocked) {
            if (!overlay.showing && now > Shared.suppressUntil && (blockPass[cur!!] ?: 0L) <= now) showBlock(lockBlock!!, cur)
        } else {
            if (overlay.showing && overlay.tag == "block") overlay.hide()
            if (cur == null || cur !in gated) {
                if (overlay.showing && overlay.tag == "gate") overlay.hide()
            } else if (!overlay.showing && now > Shared.suppressUntil && (allowedUntil[cur] ?: 0L) <= now) {
                showGate(cur)
            }
        }

        if (store.activeCardId != -1L) Alerter.check(this, store)
        Notifier.syncTimer(this, store)
        if (store.preciseSwipes) {
            if (!TouchCounter.running && now - lastTouchTry > 30_000L) {
                lastTouchTry = now
                TouchCounter.start { FocusAccessibility.instance?.countSwipe() }
            }
        } else if (TouchCounter.running) TouchCounter.stop()
        FocusWidgets.refresh(this)
        if (tick++ % 3 == 0) pollMusic(now)
        if (tick % 5 == 2 && Reminders.tickEvents(this)) Reminders.rescheduleEvents(this)
        if (tick % 30 == 1) refreshNotifs()
    }

    private fun isAllowed(b: Block, pkg: String) =
        pkg in always || pkg in b.allowed || Apps.callRelated(pkg)

    private fun showBlock(b: Block, cur: String) {
        val ch = ArrayList<Choice>()
        if (!b.strict) ch += Choice(tr("I really need this  ·  2 min"), 0xFFEA4335.toInt(), wait = 30, icon = "\uD83C\uDD98") {
            blockPass[cur] = System.currentTimeMillis() + 2 * 60_000L
            if (fgRow >= 0) db.setReason(fgRow, "Emergency (block)")
        }
        overlay.show(
            tag = "block",
            title = tr("Not-free time"),
            subtitle = (Config.TRACKED[cur] ?: cur) + "  ·  " + b.name + "  ·  " +
                trf("until %s", "%02d:%02d".format(b.endMin / 60, b.endMin % 60)),
            delaySec = 0,
            choices = ch,
            leaveLabel = tr("Back to work"),
            onLeave = {
                if (fgRow >= 0) db.setReason(fgRow, "blocked")
                Shared.suppressUntil = System.currentTimeMillis() + 5000
                Leaver.leave(applicationContext, cur)
            }
        )
    }

    /** Re-open the previous row when you come back within [gap] ms, so one visit is one row. */
    private fun openOrMerge(kind: String, pkg: String, ts: Long, gap: Long): Long {
        val last = db.lastRow(kind, pkg)
        val end = last?.t1
        if (last != null && end != null && ts - end in 0..gap) { db.reopen(last.id); return last.id }
        return db.open(kind, pkg, ts)
    }

    private fun grant(pkg: String, reason: String, minutes: Int) {
        allowedUntil[pkg] = System.currentTimeMillis() + minutes * 60_000L
        if (fgRow >= 0) db.setReason(fgRow, reason)
    }

    private fun showGate(cur: String) {
        val block = store.activeBlock()
        val bank = store.bank()
        val ch = ArrayList<Choice>()
        ch += Choice(trf("Just search  ·  %d min", Config.SEARCH_MINUTES), 0xFF4285F4.toInt(), icon = "\uD83D\uDD0D") { grant(cur, "Just search", Config.SEARCH_MINUTES) }
        ch += Choice(trf("A link  ·  %d min", Config.LINK_MINUTES), 0xFF4285F4.toInt(), icon = "\uD83D\uDD17") { grant(cur, "A link", Config.LINK_MINUTES) }
        if (bank >= Config.TREAT_STEP) {
            ch += Choice(trf("Treat  ·  %d min  (bank %d)", Config.TREAT_STEP, bank), 0xFF34A853.toInt(), icon = "\uD83C\uDF7F") {
                store.spend(Config.TREAT_STEP); grant(cur, "Treat", Config.TREAT_STEP)
            }
            if (bank > Config.TREAT_STEP) ch += Choice(trf("Use all  ·  %d min", bank), 0xFF34A853.toInt(), icon = "\uD83C\uDF89") {
                store.spend(bank); grant(cur, "Treat (all)", bank)
            }
        } else {
            ch += Choice(tr("Treat bank empty: finish tasks to earn"), 0xFF555866.toInt(), enabled = false) {}
        }
        ch += Choice(tr("I'm distracted"), 0xFFEA4335.toInt(), wait = Config.DISTRACTED_WAIT, icon = "\uD83C\uDF00") {
            grant(cur, "I'm distracted", Config.DISTRACTED_MINUTES)
        }

        overlay.show(
            tag = "gate",
            title = tr(if (allowedUntil.containsKey(cur)) "Time's up" else "What for?"),
            subtitle = (Config.TRACKED[cur] ?: cur) + (block?.let { "  ·  ${it.name}" } ?: ""),
            delaySec = if (block != null) Config.BLOCK_DELAY_SECONDS else Config.DELAY_SECONDS,
            choices = ch,
            typedLabel = trf("Go  ·  %d min", Config.CUSTOM_MINUTES),
            onTyped = { r -> grant(cur, r, Config.CUSTOM_MINUTES) },
            onLeave = {
                if (fgRow >= 0) db.setReason(fgRow, "left")
                allowedUntil.remove(cur)
                Shared.suppressUntil = System.currentTimeMillis() + 5000
                Leaver.leave(applicationContext, cur)
            }
        )
    }

    private fun pollMusic(now: Long) {
        val playing = HashSet<String>()
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            for (c in msm.getActiveSessions(ComponentName(this, MediaListener::class.java))) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) playing.add(c.packageName)
            }
        } catch (_: SecurityException) {
            return // notification access not granted yet
        }
        for (p in playing) if (p !in musicRows) musicRows[p] = openOrMerge("music", p, now, 30_000L)
        val it = musicRows.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in playing) { db.close(e.value, now); it.remove() }
        }
    }
}
