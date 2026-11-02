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

/** The moments the AI mate may be asked to speak. Chosen by plain rules in the app, never by the model. */
enum class Trigger(val proactive: Boolean) {
    LEFT_GATE(true),            // the user pressed Leave or Back out
    DISTRACTED(true),           // the user pressed "I'm distracted"
    REPEAT_GATE(true),          // the gate came up three times in 30 minutes
    EVENING_FULL_PLATE(true),   // late in the day and the plate is still full
    BIG_SHORTS_DAY(true),       // far more Shorts than usual
    USER_OPENED_CHAT(false),    // the user asked to talk: always allowed when the feature is on
}

class AiSettings(
    val enabled: Boolean = false,          // off until the user turns it on
    val consent: Boolean = false,          // and has read what is sent
    val maxProactivePerDay: Int = 3,
    val minGapMs: Long = 45 * 60_000L,
    val quietFromHour: Int = 22,
    val quietToHour: Int = 7,
)

class AiState(
    val nudgeTimesMs: List<Long> = emptyList(),               // when the AI spoke up on its own, newest last
    val lastByTrigger: Map<Trigger, Long> = emptyMap(),
    val timerRunning: Boolean = false,
)

enum class Verdict { ALLOW, OFF, NO_CONSENT, QUIET_HOURS, TIMER_RUNNING, DAILY_CAP, TOO_SOON, COOLDOWN }

/**
 * Decides whether the AI mate may speak up now. Pure rules, no model involved: the model only ever puts an
 * already-allowed moment into words. The point is to protect you from being nagged, including by a well-meaning AI.
 */
object AiPolicy {
    const val TRIGGER_COOLDOWN_MS = 3 * 60 * 60_000L

    fun inQuietHours(s: AiSettings, hour: Int): Boolean =
        if (s.quietFromHour == s.quietToHour) false
        else if (s.quietFromHour < s.quietToHour) hour >= s.quietFromHour && hour < s.quietToHour
        else hour >= s.quietFromHour || hour < s.quietToHour       // overnight, for example 22 to 7

    fun decide(t: Trigger, s: AiSettings, st: AiState, nowMs: Long, hourOfDay: Int, todayStartMs: Long): Verdict {
        if (!s.enabled) return Verdict.OFF
        if (!s.consent) return Verdict.NO_CONSENT
        if (!t.proactive) return Verdict.ALLOW                       // the user asked: answer, whatever the hour
        if (inQuietHours(s, hourOfDay)) return Verdict.QUIET_HOURS
        if (st.timerRunning) return Verdict.TIMER_RUNNING             // never interrupt a study session
        if (st.nudgeTimesMs.count { it >= todayStartMs } >= s.maxProactivePerDay) return Verdict.DAILY_CAP
        val last = st.nudgeTimesMs.maxOrNull()
        if (last != null && nowMs - last < s.minGapMs) return Verdict.TOO_SOON
        val sameTrigger = st.lastByTrigger[t]
        if (sameTrigger != null && nowMs - sameTrigger < TRIGGER_COOLDOWN_MS) return Verdict.COOLDOWN
        return Verdict.ALLOW
    }
}
