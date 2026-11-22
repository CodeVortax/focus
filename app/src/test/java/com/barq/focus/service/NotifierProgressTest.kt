/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifierProgressTest {
    @Test fun computesMidwayTimerProgressForNotificationDetectors() {
        val p = Notifier.computeTimerProgress(
            plannedMinutes = 25,
            leftMs = 10 * 60_000L,
            elapsedMs = 15 * 60_000L,
        )
        assertEquals(1500, p.maxSec)
        assertEquals(900, p.progressSec)
        assertEquals(60, p.percent)
    }

    @Test fun clampsOvertimeAndExpandedTimersWithinValidBounds() {
        val over = Notifier.computeTimerProgress(
            plannedMinutes = 25,
            leftMs = -45_000L,
            elapsedMs = 25 * 60_000L + 45_000L,
        )
        assertTrue(over.maxSec > 0)
        assertEquals(over.maxSec, over.progressSec)
        assertEquals(100, over.percent)

        val extended = Notifier.computeTimerProgress(
            plannedMinutes = 25,
            leftMs = 10 * 60_000L,
            elapsedMs = 20 * 60_000L,
        )
        assertEquals(1800, extended.maxSec)
        assertEquals(1200, extended.progressSec)
        assertEquals(66, extended.percent)
    }
}
