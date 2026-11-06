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

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockGuardTest {
    private val t0 = 1_700_000_000_000L

    @Test fun normalTimePassingIsNotAJump() {
        val g = ClockGuard()
        assertEquals(0L, g.check(t0, 1_000L))
        assertEquals(0L, g.check(t0 + 1_000L, 2_000L))
        assertEquals(0L, g.check(t0 + 61_000L, 62_000L))
    }

    @Test fun smallDriftIsIgnored() {
        val g = ClockGuard()
        g.check(t0, 1_000L)
        assertEquals(0L, g.check(t0 + 2_000L + 1_500L, 3_000L))      // 1.5 s of drift
    }

    @Test fun clockSetForwardReportsTheJump() {
        val g = ClockGuard()
        g.check(t0, 1_000L)
        val threeHours = 3 * 3_600_000L
        assertEquals(threeHours, g.check(t0 + 1_000L + threeHours, 2_000L))
        assertEquals(0L, g.check(t0 + 2_000L + threeHours, 3_000L))   // and only once
    }

    @Test fun clockSetBackReportsANegativeJump() {
        val g = ClockGuard()
        g.check(t0, 1_000L)
        val oneDay = 24 * 3_600_000L
        assertEquals(-oneDay, g.check(t0 + 1_000L - oneDay, 2_000L))
        assertEquals(0L, g.check(t0 + 2_000L - oneDay, 3_000L))
    }

    @Test fun deepSleepIsNotAJump() {
        // the monotonic clock keeps counting while the phone sleeps, so a long gap on both clocks is normal
        val g = ClockGuard()
        g.check(t0, 1_000L)
        assertEquals(0L, g.check(t0 + 8 * 3_600_000L, 1_000L + 8 * 3_600_000L))
    }
}
