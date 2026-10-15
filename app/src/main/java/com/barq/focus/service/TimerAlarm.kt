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

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.barq.focus.MainActivity
import com.barq.focus.data.Store

object TimerAlarm {
    const val ACTION_FIRE = "com.barq.focus.TIMER_FIRE"
    const val ACTION_ADD = "com.barq.focus.TIMER_ADD"
    const val ACTION_STOP = "com.barq.focus.TIMER_STOP"
    const val ACTION_PAUSE = "com.barq.focus.TIMER_PAUSE"
    const val ACTION_RESUME = "com.barq.focus.TIMER_RESUME"
    const val ACTION_ADD5 = "com.barq.focus.TIMER_ADD5"
    const val ACTION_STOPT = "com.barq.focus.TIMER_STOPT"
    const val CHANNEL = "focus_timer"
    const val NOTIF_ID = 3

    fun broadcast(ctx: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, action.hashCode(), Intent(ctx, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Best-effort backup alarm for the next alert (end of budget, then each over-time nag).
     * The watcher service also checks every second, so a failure here must never crash the app.
     */
    fun reschedule(ctx: Context, store: Store) {
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val fire = broadcast(ctx, ACTION_FIRE)
            am.cancel(fire)
            if (store.activeCardId == -1L || store.activePaused) return
            val now = System.currentTimeMillis()
            val end = store.activeEnd
            val at = when {
                now < end -> end
                store.alertedEnd != end -> now + 1000
                store.nagMin > 0 -> maxOf(store.lastNag + store.nagMin * 60_000L, now + 1000)
                else -> return
            }
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(ctx)), fire)
            } catch (_: Exception) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            val store = Store(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            when (i.action) {
                TimerAlarm.ACTION_FIRE -> Alerter.check(c, store)
                TimerAlarm.ACTION_ADD -> {
                    Alerts.stop(); nm.cancel(TimerAlarm.NOTIF_ID)
                    store.addActiveTime(10 * 60_000L)
                    TimerAlarm.reschedule(c, store)
                    Notifier.syncTimer(c, store)
                }
                TimerAlarm.ACTION_STOP -> { Alerts.stop(); nm.cancel(TimerAlarm.NOTIF_ID) }
                TimerAlarm.ACTION_PAUSE -> { store.pauseActive(); TimerAlarm.reschedule(c, store); Notifier.syncTimer(c, store) }
                TimerAlarm.ACTION_RESUME -> { store.resumeActive(); TimerAlarm.reschedule(c, store); Notifier.syncTimer(c, store) }
                TimerAlarm.ACTION_ADD5 -> { store.addActiveTime(5 * 60_000L); TimerAlarm.reschedule(c, store); Notifier.syncTimer(c, store) }
                TimerAlarm.ACTION_STOPT -> {
                    Alerts.stop(); nm.cancel(TimerAlarm.NOTIF_ID)
                    store.stopActive(); TimerAlarm.reschedule(c, store); Notifier.syncTimer(c, store)
                }
            }
        } catch (_: Exception) {}
    }
}
