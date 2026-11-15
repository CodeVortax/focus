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
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/**
 * "Now" that moving the phone's date or clock cannot change.
 *
 * The daily Shorts count, the treat bank and "today" are all keyed by the calendar day, so setting the phone's date
 * forward gave a fresh day (and a fresh allowance). This clock counts time with the monotonic clock (which nobody can
 * set, and which keeps running in sleep) from a saved anchor, and only follows the wall clock while the two agree to
 * within [DRIFT_MS]. After a restart of the phone the monotonic clock starts over, so the wall clock is taken again,
 * but never earlier than the last time that was seen.
 *
 * If you correct a wrong clock by hand, restart the phone once.
 */
object TrustedClock {
    private const val DRIFT_MS = 120_000L
    private const val SAVE_EVERY_MS = 60_000L

    private var prefs: SharedPreferences? = null
    private var anchorWall = 0L
    private var anchorElapsed = 0L
    private var savedAtElapsed = 0L
    private var bootId = -1

    @Synchronized
    fun attach(ctx: Context, p: SharedPreferences) {
        if (prefs != null) return
        val wall = System.currentTimeMillis()
        val el = SystemClock.elapsedRealtime()
        val boot = try { Settings.Global.getInt(ctx.contentResolver, "boot_count", -1) } catch (_: Exception) { -1 }
        val sw = p.getLong("tc_wall", 0L)
        val se = p.getLong("tc_elapsed", 0L)
        val sb = p.getInt("tc_boot", -2)
        if (sw > 0L && boot >= 0 && sb == boot && se in 1L..el) {
            // same boot: the monotonic clock only grows, so the saved point still tells the real time
            anchorWall = sw; anchorElapsed = se
        } else {
            // a new boot, or no way to tell: the wall clock, but never earlier than the last time we saw
            anchorWall = maxOf(wall, sw); anchorElapsed = el
        }
        bootId = boot
        savedAtElapsed = el
        prefs = p
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putLong("tc_wall", anchorWall)?.putLong("tc_elapsed", anchorElapsed)?.putInt("tc_boot", bootId)?.apply()
    }

    @Synchronized
    fun now(): Long {
        val wall = System.currentTimeMillis()
        if (prefs == null) return wall
        val el = SystemClock.elapsedRealtime()
        val trusted = anchorWall + (el - anchorElapsed)
        if (kotlin.math.abs(wall - trusted) <= DRIFT_MS) {
            if (el - savedAtElapsed >= SAVE_EVERY_MS) { anchorWall = wall; anchorElapsed = el; savedAtElapsed = el; persist() }
            return wall
        }
        return trusted
    }
}
