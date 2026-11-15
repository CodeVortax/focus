/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAndVaultTest {
    @Test fun interestRateScalesWithLevelAndCapsAtTwenty() {
        assertEquals(10, Game.interestPct(1))
        assertEquals(10, Game.interestPct(2))
        assertEquals(12, Game.interestPct(3))
        assertEquals(15, Game.interestPct(5))
        assertEquals(18, Game.interestPct(7))
        assertEquals(20, Game.interestPct(9))
        assertEquals(20, Game.interestPct(25))
    }

    @Test fun nextInterestTierMatchesProgression() {
        assertEquals(3 to 12, Game.nextInterestTier(1))
        assertEquals(5 to 15, Game.nextInterestTier(3))
        assertEquals(7 to 18, Game.nextInterestTier(6))
        assertEquals(9 to 20, Game.nextInterestTier(8))
        assertNull(Game.nextInterestTier(9))
    }

    @Test fun xpBreakdownTotalsAllSources() {
        val b = Game.XpBreakdown(workXp = 120, dietXp = 150, questXp = 260)
        assertEquals(530, b.total)
        val lv = Game.level(b.total)
        assertTrue(lv >= 3)
        assertTrue(Game.xpForLevel(lv + 1) > b.total)
    }

    @Test fun noteDefaultsPaperToPlain() {
        val n = Note(title = "T", body = "B")
        assertEquals(0, n.paper)
    }
}
