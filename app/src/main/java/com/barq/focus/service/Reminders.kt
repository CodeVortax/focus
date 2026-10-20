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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.barq.focus.data.Config
import com.barq.focus.data.EV_DONE
import com.barq.focus.data.EV_MISSED
import com.barq.focus.data.EV_PENDING
import com.barq.focus.data.EventRules
import com.barq.focus.data.EventStore
import com.barq.focus.data.FocusEvent
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import java.util.Calendar

/**
 * Event reminders (meetings...) and the "you haven't started" nudges.
 * Everything is driven by two alarms (one exact for events, one loose for nudges) plus the watcher tick.
 */
object Reminders {
    const val ACTION_EVENT = "com.barq.focus.EVENT_FIRE"
    const val ACTION_NUDGE = "com.barq.focus.NUDGE_FIRE"
    const val ACTION_DONE = "com.barq.focus.EVENT_DONE"
    const val ACTION_SNOOZE = "com.barq.focus.EVENT_SNOOZE"
    const val EXTRA_ID = "event_id"
    const val CH_REMIND = "focus_event"
    const val CH_RING = "focus_event_ring"
    const val CH_NUDGE = "focus_remind"
    const val NUDGE_NOTIF_ID = 2

    private fun nid(e: FocusEvent) = 1000 + (e.id and 0xFFFFF).toInt()

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, tr("Event reminders"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, tr("Event ringing"), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(true) }
        )
        nm.createNotificationChannel(NotificationChannel(CH_NUDGE, tr("Task reminders"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pending(c: Context, action: String, id: Long = 0L): PendingIntent =
        PendingIntent.getBroadcast(
            c, (id.toInt() * 31) + action.hashCode(),
            Intent(c, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun clock(ms: Long): String {
        val c = Calendar.getInstance(); c.timeInMillis = ms
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun leadText(left: Long): String {
        val m = ((left + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
        return if (m >= 60) {
            val h = m / 60; val r = m % 60
            if (r == 0) trf("%d h", h) else trf("%d h %d min", h, r)
        } else trf("%d min", m)
    }

    // ------------------------------------------------------------------ events

    /** Fires whatever is due. Returns true if any event changed. Safe to call as often as you like. */
    fun tickEvents(c: Context): Boolean {
        val db = EventStore.get(c)
        val now = System.currentTimeMillis()
        var changed = false
        for (e in db.all()) {
            if (e.state != EV_PENDING) continue
            val next = process(c, e, now)
            if (next != e) { db.upsert(next); changed = true }
        }
        return changed
    }

    private fun process(c: Context, e: FocusEvent, now: Long): FocusEvent {
        if (now < e.at) {
            val due = EventRules.steps(e).filter { it <= now && it > e.lastRemind }
            if (due.isEmpty()) return e
            remind(c, e, now)
            return e.copy(lastRemind = due.last())
        }
        if (now > e.at + EventRules.MISS_AFTER) {
            Alerts.stop()
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.cancel(nid(e))
            missedNotif(c, e)
            return e.copy(state = EV_MISSED)
        }
        if (e.snoozeUntil > now) return e
        if (e.lastRing == 0L || now - e.lastRing >= EventRules.RING_EVERY - 500L) {
            ring(c, e)
            return e.copy(lastRing = now, snoozeUntil = 0L)
        }
        return e
    }

    private fun remind(c: Context, e: FocusEvent, now: Long) {
        try {
            channels(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.notify(
                nid(e),
                NotificationCompat.Builder(c, CH_REMIND)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(trf("%s in %s", e.title, leadText(e.at - now)))
                    .setContentText(trf("At %s", clock(e.at)))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(TimerAlarm.openApp(c, "events"))
                    .addAction(0, tr("Done"), pending(c, ACTION_DONE, e.id))
                    .build()
            )
        } catch (_: Exception) {}
    }

    private fun ring(c: Context, e: FocusEvent) {
        try {
            channels(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.notify(
                nid(e),
                NotificationCompat.Builder(c, CH_RING)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(trf("%s: it's time!", e.title))
                    .setContentText(trf("It was set for %s. Tap Done when you're on it.", clock(e.at)))
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(TimerAlarm.openApp(c, "events"))
                    .addAction(0, tr("Done"), pending(c, ACTION_DONE, e.id))
                    .addAction(0, trf("Snooze %d min", (EventRules.SNOOZE / EventRules.M).toInt()), pending(c, ACTION_SNOOZE, e.id))
                    .build()
            )
        } catch (_: Exception) {}
        val store = Store(c)
        Alerts.run(c, "", EventRules.RING_SEC, if (store.speak) trf("%s. It's time, buddy!", e.title) else null, speakFirst = false)
    }

    private fun missedNotif(c: Context, e: FocusEvent) {
        try {
            channels(c)
            c.getSystemService(NotificationManager::class.java).notify(
                nid(e),
                NotificationCompat.Builder(c, CH_REMIND)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(trf("Missed: %s", e.title))
                    .setContentText(trf("It was set for %s.", clock(e.at)))
                    .setAutoCancel(true)
                    .setContentIntent(TimerAlarm.openApp(c, "events"))
                    .build()
            )
        } catch (_: Exception) {}
    }

    fun markDone(c: Context, id: Long) {
        val db = EventStore.get(c)
        db.get(id)?.let { db.upsert(it.copy(state = EV_DONE)) }
        Alerts.stop()
        try { c.getSystemService(NotificationManager::class.java).cancel(1000 + (id and 0xFFFFF).toInt()) } catch (_: Exception) {}
        rescheduleEvents(c)
    }

    fun snooze(c: Context, id: Long) {
        val db = EventStore.get(c)
        db.get(id)?.let { db.upsert(it.copy(snoozeUntil = System.currentTimeMillis() + EventRules.SNOOZE, lastRing = 0L)) }
        Alerts.stop()
        try { c.getSystemService(NotificationManager::class.java).cancel(1000 + (id and 0xFFFFF).toInt()) } catch (_: Exception) {}
        rescheduleEvents(c)
    }

    fun rescheduleEvents(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            val pi = pending(c, ACTION_EVENT)
            am.cancel(pi)
            val now = System.currentTimeMillis()
            val at = EventStore.get(c).all().mapNotNull { EventRules.nextFire(it, now) }.minOrNull() ?: return
            val t = maxOf(at, now + 1000L)
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, TimerAlarm.openApp(c, "events")), pi)
            } catch (_: Exception) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ "you haven't started" nudges

    /** Posts a nudge if it's after the start time, tasks are waiting and nothing has been started today. */
    fun checkNudge(c: Context, store: Store) {
        if (!store.startRemind) return
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (minute < store.startRemindFrom || minute >= Config.REMIND_TO_HOUR * 60) return
        if (now - store.lastStartRemind < store.startRemindEvery * 60_000L - 30_000L) return
        val cards = store.cards()
        val (left, desc) = store.leftSummary(cards)
        if (left == 0 || store.workedToday(cards)) return
        store.lastStartRemind = now
        try {
            channels(c)
            c.getSystemService(NotificationManager::class.java).notify(
                NUDGE_NOTIF_ID,
                NotificationCompat.Builder(c, CH_NUDGE)
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentTitle(tr("You haven't started yet"))
                    .setContentText(trf("%d tasks waiting: %s. Start one now.", left, desc))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(trf("%d tasks waiting: %s. Start one now.", left, desc)))
                    .setAutoCancel(true)
                    .setContentIntent(TimerAlarm.openApp(c))
                    .build()
            )
        } catch (_: Exception) {}
    }

    fun rescheduleNudge(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            val pi = pending(c, ACTION_NUDGE)
            am.cancel(pi)
            val store = Store(c)
            if (!store.startRemind) return
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val worked = store.workedToday(store.cards())
            fun at(dayOffset: Int, minuteOfDay: Int): Long {
                val x = Calendar.getInstance()
                x.add(Calendar.DAY_OF_YEAR, dayOffset)
                x.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); x.set(Calendar.MINUTE, minuteOfDay % 60)
                x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0)
                return x.timeInMillis
            }
            val t = when {
                worked || minute >= Config.REMIND_TO_HOUR * 60 -> at(1, store.startRemindFrom)
                minute < store.startRemindFrom -> at(0, store.startRemindFrom)
                else -> maxOf(now + store.startRemindEvery * 60_000L, store.lastStartRemind + store.startRemindEvery * 60_000L)
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(t, now + 5000L), pi)
        } catch (_: Exception) {}
    }

    fun rescheduleAll(c: Context) {
        tickEvents(c)
        rescheduleEvents(c)
        rescheduleNudge(c)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            when (i.action) {
                Reminders.ACTION_EVENT -> { Reminders.tickEvents(c); Reminders.rescheduleEvents(c) }
                Reminders.ACTION_NUDGE -> { Reminders.checkNudge(c, Store(c)); Reminders.rescheduleNudge(c) }
                Reminders.ACTION_DONE -> Reminders.markDone(c, i.getLongExtra(Reminders.EXTRA_ID, 0L))
                Reminders.ACTION_SNOOZE -> Reminders.snooze(c, i.getLongExtra(Reminders.EXTRA_ID, 0L))
            }
        } catch (_: Exception) {}
    }
}
