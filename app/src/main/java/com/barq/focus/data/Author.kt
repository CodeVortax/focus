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

/** Who made Focus, and where to support it. Only the birth month is kept, so the age works itself out. */
object Author {
    const val NAME = "Mohamed Ahmed"
    const val COUNTRY = "Egypt"
    const val HANDLE = "MR.English2008"
    private const val BORN_YEAR = 2008
    private const val BORN_MONTH = 11          // November

    const val REPO = "https://github.com/CodeVortax/focus"
    const val COFFEE = ""                      // Buy me a coffee link: empty until it exists
    const val CRYPTO = ""                      // crypto address: empty until it exists

    /** Age now, and whether the next birthday is next month (then [next] is the age they turn). */
    class Age(val years: Int, val nextMonthTurns: Int?)

    fun age(now: Calendar = Calendar.getInstance()): Age {
        val m = now.get(Calendar.MONTH) + 1
        val y = now.get(Calendar.YEAR)
        val years = y - BORN_YEAR - (if (m < BORN_MONTH) 1 else 0)
        val nextM = if (m == 12) 1 else m + 1
        return Age(years, if (nextM == BORN_MONTH) years + 1 else null)
    }
}
