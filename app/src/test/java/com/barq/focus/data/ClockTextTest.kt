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

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTextTest {
    @Test fun underAnHourIsMinutesAndSeconds() {
        assertEquals("00:00", clockText(0))
        assertEquals("05:09", clockText(309))
        assertEquals("59:59", clockText(3599))
    }

    @Test fun anHourOrMoreShowsHours() {
        assertEquals("1:00:00", clockText(3600))
        assertEquals("1:30:05", clockText(5405))
        assertEquals("12:00:01", clockText(43201))
    }

    @Test fun negativeIsClampedToZero() {
        assertEquals("00:00", clockText(-5))
    }
}
