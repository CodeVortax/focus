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

import java.util.Calendar

/** A Chrome-style named, coloured, collapsible group of tasks inside one subject. */
data class TaskGroup(val name: String, val color: Int, val collapsed: Boolean = false)

val FocusCard.dayItems: List<Item> get() = items.filter { !it.weekly }
val FocusCard.weekItems: List<Item> get() = items.filter { it.weekly }

/**
 * Weekly tasks: do them any day of the week. The sooner you finish, the bigger the multiplier:
 * x2 the moment you add it, sliding down to x1 at the end of the week, x0.5 once it is late.
 */
object Weekly {
    fun endOfWeek(from: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = from
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.WEEK_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun make(text: String, rewardMin: Int, group: String = ""): Item {
        val now = System.currentTimeMillis()
        return Item(text, false, weekly = true, reward = rewardMin, created = now, deadline = endOfWeek(now), group = group)
    }

    fun factor(i: Item, now: Long): Float {
        if (i.deadline <= i.created) return 1f
        if (now > i.deadline) return 0.5f
        val r = (i.deadline - now).toFloat() / (i.deadline - i.created).toFloat()
        return 1f + r.coerceIn(0f, 1f)
    }

    fun rewardSec(i: Item, now: Long): Int = (i.reward * 60 * factor(i, now)).toInt()

    fun daysLeft(i: Item, now: Long): Int = (((i.deadline - now).coerceAtLeast(0L) + 86_399_999L) / 86_400_000L).toInt()

    /** (weekly tasks still open, weekly tasks total) */
    fun progress(cards: List<FocusCard>): Pair<Int, Int> {
        val all = cards.filter { it.type == CardType.STUDY }.flatMap { it.weekItems }
        return all.count { !it.done } to all.size
    }
}
