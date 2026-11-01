/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportTest {
    private val allMsgs get() = Support.POOL.values.flatten()

    @Test fun everyMomentHasAtLeastOneLine() {
        Moment.values().forEach { assertTrue("$it", (Support.POOL[it] ?: emptyList()).isNotEmpty()) }
    }

    @Test fun neverTheSameLineTwiceInARow() {
        val last = HashMap<Moment, String>()
        val rnd = Random(1)
        var prev: String? = null
        repeat(200) {
            val t = Support.pick(Moment.GATE_EMPTY, 0, rnd, last)
            assertNotNull(t); assertTrue(t != prev); prev = t
        }
    }

    @Test fun aSingleLineMayRepeatBecauseThereIsNoChoice() {
        val last = HashMap<Moment, String>()
        val a = Support.pick(Moment.HOME_EMPTY, 0, Random(1), last)
        val b = Support.pick(Moment.HOME_EMPTY, 0, Random(2), last)
        assertEquals(a, b)
    }

    @Test fun toneFiltersAndFallsBackToAnyWhenNothingMatches() {
        val last = HashMap<Moment, String>()
        repeat(50) {
            val t = Support.pick(Moment.GATE_EARNED, Tone.PLAYFUL.code, Random(it), last)
            assertEquals(Support.POOL[Moment.GATE_EARNED]!!.first { m -> m.tone == Tone.PLAYFUL }.text, t)
        }
        // HOME_EMPTY has no calm line: it must still say something
        assertNotNull(Support.pick(Moment.HOME_EMPTY, Tone.CALM.code, Random(1), HashMap()))
    }

    @Test fun quietLinesAlwaysShowAndToastsFollowTheFrequency() {
        assertTrue(Support.shouldShow(Moment.GATE_FIRST, 0, Random(1)))
        var shown = 0
        val rnd = Random(7)
        repeat(1000) { if (Support.shouldShow(Moment.LEAVE, 0, rnd)) shown++ }
        assertTrue("rarely ~25%, was $shown", shown in 180..320)
        repeat(100) { assertTrue(Support.shouldShow(Moment.LEAVE, 2, rnd)) }
    }

    @Test fun linesNeverBlameOrPressure() {
        val banned = listOf("lazy", "fail", "worthless", "disappoint", "weak", "pathetic", "you must", "you have to", "earn the right", "stay", "don't leave", "waste")
        allMsgs.forEach { m -> banned.forEach { b -> assertFalse("'${m.text}' has '$b'", m.text.lowercase().contains(b)) } }
    }

    @Test fun linesAreShortEnoughForTheGate() {
        allMsgs.forEach { assertTrue("'${it.text}' is ${it.text.length} chars", it.text.length <= 80) }
    }

    @Test fun everyLineHasAnArabicTranslation() {
        allMsgs.forEach { assertTrue("missing Arabic: ${it.text}", AR_V30.containsKey(it.text)) }
    }

    @Test fun noLineIsListedTwiceInOnePool() {
        Support.POOL.forEach { (m, l) -> assertEquals("$m", l.size, l.map { it.text }.toSet().size) }
    }
}
