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

/** Where one gated app's current visit stands. */
enum class GatePhase {
    /** No gate is waiting on this app. */
    IDLE,
    /** The gate is on screen and no valid choice has been made yet. */
    SHOWN,
    /** The gate was taken down because the app went out of view for a moment (Recents, a system window) while it was still unanswered. */
    AWAY,
    /** A valid choice was made. The pass itself lives in WatchService.allowedUntil; this only records that the visit was answered. */
    PASSED,
}

/**
 * Remembers, per gated app, how far an unanswered gate had got, so that a trip to Recents does not start the
 * gate over from scratch.
 *
 * What it does NOT do: it never skips the gate. An unanswered gate always comes back (otherwise opening Recents
 * would be a free bypass). It only keeps the countdown that had already run, and only for a bounded time
 * ([returnWindowMs]); after that the visit counts as a new one and the gate starts fresh. There is no
 * "suppressed forever" flag anywhere.
 *
 * Pure Kotlin on purpose: no Android classes, the clock is passed in, so it can be unit-tested on the JVM.
 */
class GateSessions(private val returnWindowMs: Long = RETURN_WINDOW_MS) {
    private class S(var phase: GatePhase, var since: Long, var delayLeft: Int)

    private val map = HashMap<String, S>()

    /** Called with a short human-readable line on every phase change (never with package names). */
    var log: ((String) -> Unit)? = null

    fun phase(pkg: String): GatePhase = map[pkg]?.phase ?: GatePhase.IDLE

    /**
     * The countdown (seconds) the next gate for [pkg] should start with. A gate that was taken down unanswered
     * keeps the seconds it still had to run, as long as the app comes back within the window.
     */
    fun delayFor(pkg: String, now: Long, normal: Int): Int {
        val s = map[pkg] ?: return normal
        if (s.phase != GatePhase.AWAY) return normal
        if (now - s.since > returnWindowMs) {
            // a different visit now: forget the old one
            map.remove(pkg)
            note("AWAY -> IDLE (window over)")
            return normal
        }
        return s.delayLeft.coerceIn(0, normal)
    }

    /** The gate went up for [pkg]. */
    fun onShown(pkg: String, now: Long) {
        val from = phase(pkg)
        map[pkg] = S(GatePhase.SHOWN, now, 0)
        if (from != GatePhase.SHOWN) note("$from -> SHOWN")
    }

    /** The gate came down without an answer because the app left the screen; [delayLeft] is what was still counting. */
    fun onAway(pkg: String, now: Long, delayLeft: Int) {
        val s = map[pkg] ?: return
        if (s.phase != GatePhase.SHOWN) return
        s.phase = GatePhase.AWAY; s.since = now; s.delayLeft = delayLeft.coerceAtLeast(0)
        note("SHOWN -> AWAY (${s.delayLeft}s of delay left)")
    }

    /** A valid choice was made for [pkg]. */
    fun onPassed(pkg: String, now: Long) {
        val from = phase(pkg)
        map[pkg] = S(GatePhase.PASSED, now, 0)
        note("$from -> PASSED")
    }

    /** The user left the app on purpose (Leave, Back to work, Back out). The next visit is a fresh one. */
    fun onLeft(pkg: String) {
        if (map.remove(pkg) != null) note("-> IDLE (left)")
    }

    /** Forget everything (service restarted, settings changed). */
    fun reset() { map.clear() }

    private fun note(m: String) { log?.invoke(m) }

    companion object {
        /** How long an unanswered gate remembers its countdown after the app went out of view. */
        const val RETURN_WINDOW_MS = 90_000L
    }
}
