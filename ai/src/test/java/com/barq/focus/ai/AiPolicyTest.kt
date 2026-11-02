/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPolicyTest {
    private val on = AiSettings(enabled = true, consent = true)
    private val noon = 12
    private val dayStart = 1_000_000_000L
    private val now = dayStart + 12 * 3_600_000L

    private fun decide(t: Trigger, s: AiSettings = on, st: AiState = AiState(), hour: Int = noon) = AiPolicy.decide(t, s, st, now, hour, dayStart)

    @Test fun offByDefault() = assertEquals(Verdict.OFF, decide(Trigger.LEFT_GATE, AiSettings()))
    @Test fun enabledButNotConsentedIsStillOff() = assertEquals(Verdict.NO_CONSENT, decide(Trigger.LEFT_GATE, AiSettings(enabled = true)))
    @Test fun allowedWhenEverythingIsFine() = assertEquals(Verdict.ALLOW, decide(Trigger.LEFT_GATE))

    @Test fun theUserCanAlwaysTalkWhenItIsOn() {
        val busy = AiState(nudgeTimesMs = List(10) { now - 1000L }, timerRunning = true)
        assertEquals(Verdict.ALLOW, decide(Trigger.USER_OPENED_CHAT, st = busy, hour = 3))
        assertEquals(Verdict.OFF, decide(Trigger.USER_OPENED_CHAT, AiSettings()))
    }

    @Test fun overnightQuietHoursWrapAround() {
        listOf(22, 23, 0, 3, 6).forEach { assertEquals("hour $it", Verdict.QUIET_HOURS, decide(Trigger.BIG_SHORTS_DAY, hour = it)) }
        listOf(7, 12, 21).forEach { assertEquals("hour $it", Verdict.ALLOW, decide(Trigger.BIG_SHORTS_DAY, hour = it)) }
    }

    @Test fun sameStartAndEndMeansNoQuietHours() {
        assertFalse(AiPolicy.inQuietHours(AiSettings(quietFromHour = 5, quietToHour = 5), 5))
    }

    @Test fun daytimeQuietWindowsWorkToo() {
        val s = AiSettings(quietFromHour = 13, quietToHour = 15)
        assertTrue(AiPolicy.inQuietHours(s, 13)); assertTrue(AiPolicy.inQuietHours(s, 14)); assertFalse(AiPolicy.inQuietHours(s, 15))
    }

    @Test fun neverInterruptsAStudyTimer() = assertEquals(Verdict.TIMER_RUNNING, decide(Trigger.REPEAT_GATE, st = AiState(timerRunning = true)))

    @Test fun dailyCapCountsOnlyToday() {
        val yesterday = List(5) { dayStart - 3_600_000L * (it + 1) }
        assertEquals(Verdict.ALLOW, decide(Trigger.LEFT_GATE, st = AiState(nudgeTimesMs = yesterday)))
        val today = listOf(dayStart + 1_000L, dayStart + 2 * 3_600_000L, dayStart + 4 * 3_600_000L)
        assertEquals(Verdict.DAILY_CAP, decide(Trigger.LEFT_GATE, st = AiState(nudgeTimesMs = today)))
    }

    @Test fun needsAGapBetweenNudges() {
        assertEquals(Verdict.TOO_SOON, decide(Trigger.LEFT_GATE, st = AiState(nudgeTimesMs = listOf(now - 10 * 60_000L))))
        assertEquals(Verdict.ALLOW, decide(Trigger.LEFT_GATE, st = AiState(nudgeTimesMs = listOf(now - 50 * 60_000L))))
    }

    @Test fun theSameReasonWaitsThreeHours() {
        val st = AiState(nudgeTimesMs = listOf(now - 60 * 60_000L), lastByTrigger = mapOf(Trigger.REPEAT_GATE to now - 60 * 60_000L))
        assertEquals(Verdict.COOLDOWN, decide(Trigger.REPEAT_GATE, st = st))
        assertEquals(Verdict.ALLOW, decide(Trigger.LEFT_GATE, st = st))
    }

    @Test fun userChatIsNotProactive() = assertFalse(Trigger.USER_OPENED_CHAT.proactive)
}
