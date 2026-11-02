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

package com.barq.focus.ai

/**
 * Everything the AI mate may know about today, and nothing else.
 *
 * Only whole numbers, built on the phone. There is deliberately no String field: no task titles, no note text, no
 * app names, no PDF names can reach the model through this type.
 */
class DaySummary private constructor(
    val tasksDone: Int, val tasksTotal: Int, val shortsSwipes: Int, val gatesShown: Int,
    val lastTimerMinutes: Int, val workStreakDays: Int, val hourOfDay: Int,
) {
    /** The few lines the model is given. Same shape every time, so it is easy to read and to log ("what was sent"). */
    fun toPromptText(): String = buildString {
        append("Today so far (numbers only, made on the phone):\n")
        append("- Tasks done: $tasksDone of $tasksTotal\n")
        append("- Shorts swiped: $shortsSwipes\n")
        append("- Times the gate appeared: $gatesShown\n")
        append("- Longest recent study timer: $lastTimerMinutes minutes\n")
        append("- Work streak: $workStreakDays days\n")
        append("- Local hour: $hourOfDay\n")
    }

    companion object {
        /** Out-of-range values are clamped, so a bug upstream can't send a strange number. */
        fun of(tasksDone: Int, tasksTotal: Int, shortsSwipes: Int, gatesShown: Int, lastTimerMinutes: Int, workStreakDays: Int, hourOfDay: Int): DaySummary {
            val total = tasksTotal.coerceIn(0, 500)
            return DaySummary(
                tasksDone.coerceIn(0, total), total, shortsSwipes.coerceIn(0, 5000), gatesShown.coerceIn(0, 500),
                lastTimerMinutes.coerceIn(0, 1440), workStreakDays.coerceIn(0, 3650), hourOfDay.coerceIn(0, 23),
            )
        }
    }
}
