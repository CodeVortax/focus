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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.text.BidiFormatter
import com.barq.focus.data.CardType
import com.barq.focus.data.Config
import com.barq.focus.data.Game
import com.barq.focus.data.I18n
import com.barq.focus.data.Store
import com.barq.focus.data.startOfDay
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/**
 * One place that builds Focus' notifications.
 * Mixed English/Arabic text is what scrambled the old one, so every name is wrapped in a direction isolate and
 * each fact gets its own line: the collapsed view shows the minimum, the expanded view shows the detail.
 */
object Notifier {
    const val STATUS_ID = 1
    const val TIMER_ID = 4
    const val HEALTH_ID = 5
    private const val CH_HEALTH = "focus_health"
    const val CH_STATUS = "focus_watch"
    const val CH_REMIND = "focus_remind"
    private const val CH_TIMER = "focus_timer_live"
    private var lastStatus = ""
    private var lastTimer = ""

    private fun iso(s: String): String = BidiFormatter.getInstance(I18n.speakLocale()).unicodeWrap(s)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Focus", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Running timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_HEALTH, "Focus needs attention", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private data class Left(val title: String, val n: Int)

    private fun left(store: Store): List<Left> =
        store.cards().filter { it.type == CardType.STUDY }
            .map { Left(it.title, it.items.count { i -> !i.done }) }.filter { it.n > 0 }.sortedByDescending { it.n }

    private fun fmt(ms: Long): String {
        val s = (kotlin.math.abs(ms) + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    private fun statusSig(store: Store): String {
        val l = left(store)
        return "${I18n.code}|${I18n.version}|${l.joinToString { it.title + it.n }}|${store.bank()}|${store.shortsSwipes()}"
    }

    fun status(ctx: Context, store: Store): Notification {
        ensureChannels(ctx)
        val l = left(store)
        val total = l.sumOf { it.n }
        val planned = store.cards().filter { it.type == CardType.STUDY }.sumOf { it.items.size }
        val title = when {
            planned == 0 -> tr(Config.freeWord(startOfDay() / 86_400_000L))
            total == 0 -> tr("Plate cleared. Nice work.")
            else -> trf("%d tasks left", total)
        }
        // collapsed = the minimum: the two busiest subjects
        val mini = if (total > 0)
            l.take(2).joinToString("  ·  ") { iso(it.title) + " " + it.n } + (if (l.size > 2) "  +" + (l.size - 2) else "")
        else trf("Treat bank: %s min", store.bankText())
        // expanded = the detail, one fact per line
        val box = NotificationCompat.InboxStyle().setBigContentTitle(title)
        l.take(6).forEach { box.addLine("•  " + iso(it.title) + "  —  " + trf("%d left", it.n)) }
        if (l.size > 6) box.addLine(trf("+%d more subjects", l.size - 6))
        box.addLine(trf("Treat bank: %s min", store.bankText()))
        box.addLine(trf("Shorts today: %d swiped", store.shortsSwipes()))
        box.setSummaryText("🔥 " + Game.workStreak(store))
        return NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(mini)
            .setStyle(box)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(TimerAlarm.openApp(ctx))
            .build()
    }

    /** Re-posts only when something that shows in it actually changed. */
    fun refreshStatus(ctx: Context, store: Store) {
        val sig = statusSig(store)
        if (sig == lastStatus) return
        lastStatus = sig
        ctx.getSystemService(NotificationManager::class.java).notify(STATUS_ID, status(ctx, store))
    }

    /** The running study timer: the system counts it down by itself, so nothing needs updating each second. */
    /** The accessibility switch is on but Android stopped the service (it never restarts it by itself). Say so instead of failing silently. */
    fun accessibilityDead(ctx: Context, dead: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!dead) { nm.cancel(HEALTH_ID); return }
        ensureChannels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_HEALTH)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(tr("Focus stopped counting Shorts"))
            .setContentText(tr("Android switched the accessibility service off in the background. Open Accessibility settings and turn Focus off and on."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(tr("Android switched the accessibility service off in the background. Open Accessibility settings and turn Focus off and on.")))
            .setAutoCancel(true).setOnlyAlertOnce(true)
            .setContentIntent(android.app.PendingIntent.getActivity(ctx, 55,
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        try { nm.notify(HEALTH_ID, b.build()) } catch (_: SecurityException) {}
    }

    fun syncTimer(ctx: Context, store: Store) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = store.activeCardId
        if (id == -1L) {
            if (lastTimer.isNotEmpty()) { nm.cancel(TIMER_ID); lastTimer = "" }
            return
        }
        val now = System.currentTimeMillis()
        val end = store.activeEnd
        val paused = store.activePaused
        val over = !paused && end <= now
        val sig = "$id|$end|$paused|${store.activeRemaining}|$over|${I18n.code}"
        if (sig == lastTimer) return
        val card = store.cards().firstOrNull { it.id == id } ?: return
        lastTimer = sig
        ensureChannels(ctx)

        val b = NotificationCompat.Builder(ctx, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(iso(card.title))
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(TimerAlarm.openApp(ctx))
        when {
            paused -> b.setContentText(trf("Paused  ·  %s left", fmt(store.activeRemaining)))
            over -> b.setContentText(tr("Over budget")).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(false).setWhen(end)
            else -> b.setContentText(tr("Time left")).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true).setWhen(end)
        }
        val total = card.items.size
        if (total > 0) b.setSubText(trf("%d / %d tasks", card.items.count { it.done }, total))
        val open = card.items.filter { !it.done }.take(4)
        if (open.isNotEmpty()) {
            val box = NotificationCompat.InboxStyle().setBigContentTitle(iso(card.title))
            open.forEach { box.addLine("•  " + iso(it.text)) }
            b.setStyle(box)
        }
        if (paused) b.addAction(0, tr("Resume"), TimerAlarm.broadcast(ctx, TimerAlarm.ACTION_RESUME))
        else b.addAction(0, tr("Pause"), TimerAlarm.broadcast(ctx, TimerAlarm.ACTION_PAUSE))
        b.addAction(0, tr("+5 min"), TimerAlarm.broadcast(ctx, TimerAlarm.ACTION_ADD5))
        b.addAction(0, tr("Stop"), TimerAlarm.broadcast(ctx, TimerAlarm.ACTION_STOPT))
        nm.notify(TIMER_ID, b.build())
    }

    /** After a language change: rebuild everything in the new language. */
    fun refreshAll(ctx: Context) {
        val store = Store(ctx)
        lastStatus = ""; lastTimer = ""
        refreshStatus(ctx, store)
        syncTimer(ctx, store)
    }
}
