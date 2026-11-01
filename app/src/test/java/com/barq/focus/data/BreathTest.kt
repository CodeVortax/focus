/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathTest {
    private val calm = Breath.PATTERNS[0]      // in 4, out 6
    private val box = Breath.PATTERNS[1]       // in 4, hold 4, out 4, hold 4

    @Test fun startsSmallAndEndsTheInBreathFull() {
        assertEquals(Breath.MIN_SCALE, Breath.at(calm, 0f).scale, 0.001f)
        assertEquals(Breath.MAX_SCALE, Breath.at(calm, 3.999f).scale, 0.01f)
    }

    @Test fun growsWhileBreathingInAndShrinksWhileBreathingOut() {
        var prev = Breath.at(calm, 0f).scale
        for (i in 1..39) { val s = Breath.at(calm, i / 10f).scale; assertTrue(s >= prev - 0.0001f); prev = s }
        for (i in 41..99) { val s = Breath.at(calm, i / 10f).scale; assertTrue(s <= prev + 0.0001f); prev = s }
    }

    @Test fun aHoldKeepsTheSizeTheBreathEndedOn() {
        assertEquals(Breath.MAX_SCALE, Breath.at(box, 5f).scale, 0.001f)       // hold after breathing in
        assertEquals(Breath.MIN_SCALE, Breath.at(box, 13f).scale, 0.001f)      // hold after breathing out
    }

    @Test fun stepsAndCountdownFollowThePattern() {
        assertEquals(0, Breath.at(calm, 0.5f).index)
        assertEquals(1, Breath.at(calm, 4.5f).index)
        assertEquals(4, Breath.at(calm, 0.0f).secLeft)
        assertEquals(1, Breath.at(calm, 3.5f).secLeft)
        assertEquals(6, Breath.at(calm, 4.0f).secLeft)
    }

    @Test fun theCycleRepeats() {
        val a = Breath.at(calm, 2.5f); val b = Breath.at(calm, 12.5f)
        assertEquals(a.index, b.index); assertEquals(a.scale, b.scale, 0.001f)
    }

    @Test fun nothingBreaksAtTheEdges() {
        listOf(-5f, 0f, 10f, 10.0001f, 1_000_000f).forEach { t ->
            val p = Breath.at(calm, t)
            assertTrue(p.scale in Breath.MIN_SCALE - 0.001f..Breath.MAX_SCALE + 0.001f)
            assertTrue(p.secLeft in 1..6); assertTrue(p.index in 0..1)
        }
    }

    @Test fun patternsAreWhatTheyClaim() {
        assertEquals(10, calm.cycleSec); assertEquals(16, box.cycleSec)
        Breath.PATTERNS.forEach { p -> assertTrue(p.steps.all { it.sec in 1..10 }) }
    }
}
