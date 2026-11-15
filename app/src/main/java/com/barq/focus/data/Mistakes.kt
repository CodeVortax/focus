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

/**
 * A wrong answer (kind 0), or a lesson you did not fully understand (kind 1 = small review, kind 2 = the hard part
 * needs explaining again), to be tried again later: after 1 day, then 7, then 30.
 */
data class Mistake(
    val id: Long,
    val subject: String,
    val text: String,            // the question or idea, or the hardest point
    val mine: String = "",       // what you answered
    val correct: String = "",    // the right answer
    val cause: Int = -1,         // index into Mistakes.CAUSES, -1 = not said
    val avoid: String = "",      // how to avoid it next time
    val kind: Int = 0,
    val created: Long = 0L,
    val step: Int = 0,           // how many times it was solved again
    val due: Long = 0L,          // when to try it again
    val done: Boolean = false,
)

object Mistakes {
    private const val DAY = 24L * 60 * 60 * 1000
    private val GAPS = listOf(1L, 7L, 30L)

    val CAUSES = listOf("Did not understand the question", "Mixed up similar facts", "Forgot", "Rushed", "Time management")

    /** You solved it again: it comes back after a longer gap, and after the last one it is finished. */
    fun solved(m: Mistake, now: Long): Mistake {
        val s = m.step + 1
        return if (s >= GAPS.size) m.copy(step = s, done = true) else m.copy(step = s, due = now + GAPS[s] * DAY)
    }

    /** You missed it again: back to the start. */
    fun missed(m: Mistake, now: Long): Mistake = m.copy(step = 0, due = now + GAPS[0] * DAY)

    fun firstDue(now: Long) = now + GAPS[0] * DAY
}
