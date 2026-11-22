/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillSelectorTest {
    private fun baseInput(now: Long = 10_000L) = PillSelector.Input(
        now = now,
        flashText = "",
        flashColor = 0,
        flashUntil = 0L,
        activeCardId = -1L,
        cardTitle = "",
        cardColor = 0xFF4285F4.toInt(),
        cardMinutes = 25,
        activeEnd = 0L,
        activePaused = false,
        activeRemaining = 0L,
        activeProjectId = -1L,
        projectName = "",
        bankText = "0",
        focusMusicPlaying = false,
        focusMusicTitle = null,
        focusNotePlaying = false,
        focusNoteTitle = null,
    )

    @Test fun returnsNullWhenFocusIsIdle() {
        assertNull(PillSelector.select(baseInput()))
    }

    @Test fun prioritizesNoticeThenTimerThenProjectThenFocusAudio() {
        val allActive = baseInput(now = 1_000L).copy(
            flashText = "30 s left",
            flashColor = 0xFFFBBC04.toInt(),
            flashUntil = 5_000L,
            activeCardId = 7L,
            cardTitle = "Physics",
            activeEnd = 1_000L + 10 * 60_000L,
            activeProjectId = 9L,
            projectName = "Thesis",
            bankText = "12",
            focusMusicPlaying = true,
            focusMusicTitle = "Lofi",
            focusNotePlaying = true,
            focusNoteTitle = "Lecture 1",
        )
        assertEquals(PillKind.NOTICE, PillSelector.select(allActive)?.kind)

        val timer = PillSelector.select(allActive.copy(flashUntil = 500L))
        assertNotNull(timer)
        assertEquals(PillKind.TIMER, timer?.kind)
        assertEquals("Physics", timer?.label)
        assertEquals("10:00", timer?.trailing)
        assertEquals(0.6f, timer?.progress ?: 0f, 0.01f)

        val proj = PillSelector.select(allActive.copy(flashUntil = 500L, activeCardId = -1L))
        assertEquals(PillKind.PROJECT, proj?.kind)
        assertEquals("Thesis", proj?.label)
        assertEquals("12m", proj?.trailing)

        val music = PillSelector.select(allActive.copy(flashUntil = 500L, activeCardId = -1L, activeProjectId = -1L))
        assertEquals(PillKind.FOCUS_MUSIC, music?.kind)
        assertEquals("Lofi", music?.label)

        val note = PillSelector.select(
            allActive.copy(flashUntil = 500L, activeCardId = -1L, activeProjectId = -1L, focusMusicPlaying = false)
        )
        assertEquals(PillKind.FOCUS_NOTE, note?.kind)
        assertEquals("Lecture 1", note?.label)
    }

    @Test fun supportsAllFiveCutoutShapes() {
        assertEquals(PillShape.CENTER, PillShape.fromCode(0))
        assertEquals(PillShape.WATERDROP, PillShape.fromCode(1))
        assertEquals(PillShape.V_SHAPE, PillShape.fromCode(2))
        assertEquals(PillShape.SIDE_LEFT, PillShape.fromCode(3))
        assertEquals(PillShape.SIDE_RIGHT, PillShape.fromCode(4))
        assertEquals(PillShape.CENTER, PillShape.fromCode(99))

        assertTrue(PillShape.WATERDROP.isFlushTop)
        assertTrue(PillShape.V_SHAPE.isFlushTop)
        assertFalse(PillShape.CENTER.isFlushTop)
        assertFalse(PillShape.SIDE_LEFT.isFlushTop)
        assertFalse(PillShape.SIDE_RIGHT.isFlushTop)
    }

    @Test fun emitsSecondaryCircleWhenTimerAndFocusMusicRunTogether() {
        val input = baseInput(now = 5_000L).copy(
            activeCardId = 3L,
            cardTitle = "Chemistry",
            cardMinutes = 30,
            activeEnd = 5_000L + 15 * 60_000L,
            focusMusicPlaying = true,
            focusMusicActive = true,
            focusMusicId = 42L,
            focusMusicTitle = "Rain Lo-Fi",
            focusMusicArtist = "Focus Tapes",
            focusMusicPosMs = 65_000L,
            focusMusicDurMs = 180_000L,
        )
        val dual = PillSelector.selectDual(input)
        assertNotNull(dual)
        assertEquals(PillKind.TIMER, dual?.primary?.kind)
        assertEquals("Chemistry", dual?.primary?.label)
        assertNotNull(dual?.secondary)
        assertEquals(PillKind.FOCUS_MUSIC, dual?.secondary?.kind)
        assertEquals("Rain Lo-Fi", dual?.secondary?.label)
        assertEquals("Focus Tapes", dual?.secondary?.artist)
        assertTrue(dual?.secondary?.mediaPlaying == true)
    }
}
