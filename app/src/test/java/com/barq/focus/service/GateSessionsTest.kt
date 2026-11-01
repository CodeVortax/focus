/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GateSessionsTest {
    private val yt = "com.google.android.youtube"
    private val chrome = "com.android.chrome"
    private val window = GateSessions.RETURN_WINDOW_MS

    @Test fun freshAppUsesTheNormalDelay() {
        val s = GateSessions()
        assertEquals(3, s.delayFor(yt, 1_000L, 3))
        assertEquals(GatePhase.IDLE, s.phase(yt))
    }

    @Test fun recentsTripKeepsTheCountdownThatAlreadyRan() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 2_000L, delayLeft = 1)          // 2 of 3 s had run when Recents opened
        assertEquals(GatePhase.AWAY, s.phase(yt))
        assertEquals(1, s.delayFor(yt, 6_000L, 3))   // back four seconds later: does not start over at 3
    }

    @Test fun anUnansweredGateAlwaysComesBack() {
        // the delay may shrink to 0, but the session is never "dismissed": the gate is still shown, just without a new wait
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 3_000L, delayLeft = 0)
        assertEquals(0, s.delayFor(yt, 4_000L, 3))
        assertEquals(GatePhase.AWAY, s.phase(yt))
    }

    @Test fun returningAfterTheWindowIsANewVisit() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 1)
        assertEquals(3, s.delayFor(yt, 1_000L + window + 1, 3))
        assertEquals(GatePhase.IDLE, s.phase(yt))
    }

    @Test fun atTheEdgeOfTheWindowItIsStillTheSameVisit() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 2)
        assertEquals(2, s.delayFor(yt, 1_000L + window, 3))
    }

    @Test fun aValidChoiceEndsTheAwayMemory() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 2)
        s.onPassed(yt, 2_000L)
        assertEquals(GatePhase.PASSED, s.phase(yt))
        assertEquals(3, s.delayFor(yt, 3_000L, 3))   // a later "Time's up" gate gets the normal delay
    }

    @Test fun leavingOnPurposeMakesTheNextVisitFresh() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 2)
        s.onLeft(yt)
        assertEquals(GatePhase.IDLE, s.phase(yt))
        assertEquals(3, s.delayFor(yt, 2_000L, 3))
    }

    @Test fun rememberedDelayNeverExceedsTheCurrentDelay() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 15)         // was a block gate (15 s)
        assertEquals(3, s.delayFor(yt, 2_000L, 3))   // a normal gate now: capped at 3
    }

    @Test fun appsDoNotShareMemory() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 1)
        assertEquals(3, s.delayFor(chrome, 2_000L, 3))
        assertEquals(1, s.delayFor(yt, 2_000L, 3))
    }

    @Test fun awayIsIgnoredWhenNoGateWasShown() {
        val s = GateSessions()
        s.onAway(yt, 1_000L, delayLeft = 1)
        assertEquals(GatePhase.IDLE, s.phase(yt))
    }

    @Test fun awayIsIgnoredOnceAnswered() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onPassed(yt, 1_000L)
        s.onAway(yt, 2_000L, delayLeft = 2)
        assertEquals(GatePhase.PASSED, s.phase(yt))
    }

    @Test fun showingAgainReplacesTheAwayState() {
        val s = GateSessions()
        s.onShown(yt, 0L)
        s.onAway(yt, 1_000L, delayLeft = 1)
        s.onShown(yt, 5_000L)
        assertEquals(GatePhase.SHOWN, s.phase(yt))
    }

    @Test fun logLinesNeverContainThePackageName() {
        val s = GateSessions()
        val lines = ArrayList<String>()
        s.log = { lines += it }
        s.onShown(yt, 0L); s.onAway(yt, 1_000L, 1); s.onPassed(yt, 2_000L); s.onLeft(yt)
        assertEquals(true, lines.isNotEmpty())
        assertEquals(false, lines.any { it.contains("youtube") })
    }
}
