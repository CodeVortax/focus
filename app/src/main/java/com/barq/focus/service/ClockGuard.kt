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

/**
 * Notices that the wall clock was changed (set by hand, or a big network correction) by comparing it with the
 * monotonic clock, which nobody can set. Both clocks must be read at the same moment.
 *
 * Everything Focus keeps as "until <time>" (gate passes, treat time, the study timer) is a wall-clock time. If the
 * clock is moved, those times have to move with it, or a pass lasts for hours and a timer credits study that never happened.
 */
class ClockGuard(private val thresholdMs: Long = 3_000L) {
    private var anchor = 0L

    /** How far the wall clock jumped since the last call: 0 when it did not (small drift is ignored), + forward, - back. */
    fun check(wallMs: Long, monotonicMs: Long): Long {
        val a = wallMs - monotonicMs
        if (anchor == 0L) { anchor = a; return 0L }
        val d = a - anchor
        if (kotlin.math.abs(d) <= thresholdMs) return 0L
        anchor = a
        return d
    }
}
