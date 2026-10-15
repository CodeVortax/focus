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

package com.barq.focus.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val EV_PENDING = 0
const val EV_DONE = 1
const val EV_MISSED = 2

/**
 * Something that happens at a set time (a meeting...).
 * level: 0 low = one reminder 1h before; 1 medium = every hour inside the window;
 * 2 high = hourly, then every 10 min during the last hour. If you let it pass it rings until you answer.
 */
data class FocusEvent(
    val id: Long, val title: String, val at: Long, val level: Int = 1, val windowH: Int = 3,
    val created: Long = System.currentTimeMillis(), val state: Int = EV_PENDING,
    val lastRemind: Long = 0L, val lastRing: Long = 0L, val snoozeUntil: Long = 0L, val noteId: Long = 0L,
)

object EventRules {
    const val H = 3_600_000L
    const val M = 60_000L
    const val RING_EVERY = 2 * M          // rings again every 2 min until you answer
    const val MISS_AFTER = 60 * M         // gives up (marks missed) an hour after the time
    const val SNOOZE = 10 * M
    const val RING_SEC = 45

    fun steps(e: FocusEvent): List<Long> {
        val l = ArrayList<Long>()
        when (e.level) {
            0 -> l += e.at - H
            1 -> for (k in e.windowH downTo 1) l += e.at - k * H
            else -> {
                for (k in e.windowH downTo 2) l += e.at - k * H
                for (m in 60 downTo 10 step 10) l += e.at - m * M
            }
        }
        return l.filter { it >= e.created - 1000L && it < e.at }.sorted()
    }

    /** When the engine next has something to do for this event (may be in the past = due now). */
    fun nextFire(e: FocusEvent, now: Long): Long? {
        if (e.state != EV_PENDING) return null
        if (now < e.at) return steps(e).firstOrNull { it > e.lastRemind } ?: e.at
        val miss = e.at + MISS_AFTER
        val ring = when {
            e.snoozeUntil > now -> e.snoozeUntil
            e.lastRing == 0L -> now
            else -> e.lastRing + RING_EVERY
        }
        return if (ring >= miss) miss + 500L else ring
    }
}

class EventStore private constructor(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("focus_events", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<FocusEvent> {
        val s = p.getString("events", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                FocusEvent(
                    o.getLong("id"), o.getString("title"), o.getLong("at"), o.optInt("lv", 1), o.optInt("w", 3),
                    o.optLong("c", 0L), o.optInt("s", EV_PENDING), o.optLong("lr", 0L), o.optLong("lg", 0L),
                    o.optLong("sn", 0L), o.optLong("n", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized fun saveAll(l: List<FocusEvent>) {
        val a = JSONArray()
        l.forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("at", it.at).put("lv", it.level).put("w", it.windowH)
                    .put("c", it.created).put("s", it.state).put("lr", it.lastRemind).put("lg", it.lastRing)
                    .put("sn", it.snoozeUntil).put("n", it.noteId)
            )
        }
        p.edit().putString("events", a.toString()).apply()
    }

    @Synchronized fun get(id: Long): FocusEvent? = all().firstOrNull { it.id == id }

    @Synchronized fun upsert(e: FocusEvent) {
        val l = all()
        saveAll(if (l.any { it.id == e.id }) l.map { if (it.id == e.id) e else it } else l + e)
    }

    @Synchronized fun remove(id: Long) = saveAll(all().filter { it.id != id })

    companion object {
        @Volatile private var inst: EventStore? = null
        fun get(ctx: Context): EventStore = inst ?: synchronized(this) { inst ?: EventStore(ctx).also { inst = it } }
    }
}
