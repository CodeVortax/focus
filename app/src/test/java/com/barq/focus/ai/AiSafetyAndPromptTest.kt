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

class AiSafetyAndPromptTest {
    @Test fun detectsEnglishAndArabic() {
        assertTrue(AiSafety.needsCare("I want to kill myself"))
        assertTrue(AiSafety.needsCare("sometimes i WANT TO DIE"))
        assertTrue(AiSafety.needsCare("أريد أن أموت"))
        assertTrue(AiSafety.needsCare("افكر في الانتحار"))
    }

    @Test fun ordinaryTalkAboutStudyIsNotFlagged() {
        listOf("I can't start studying", "this exam is killing my motivation", "I wasted the whole day on Shorts", "أشعر بالتعب ولا أستطيع البدء").forEach {
            assertFalse(it, AiSafety.needsCare(it))
        }
    }

    @Test fun theCareMessagesAreKindAndPointToRealPeople() {
        assertTrue(AiSafety.CARE_EN.contains("someone you trust")); assertTrue(AiSafety.CARE_EN.contains("helpline"))
        assertTrue(AiSafety.careMessage(true).isNotBlank()); assertEquals(AiSafety.CARE_EN, AiSafety.careMessage(false))
    }

    @Test fun theSystemPromptStatesTheRules() {
        val p = AiPrompt.SYSTEM
        listOf("Never shame", "at most one question", "cannot change limits", "five_minute_start", "helpline", "Only the short numbers summary", "JSON only").forEach {
            assertTrue("missing: $it", p.contains(it))
        }
        assertTrue(p.length < 4000)
    }

    @Test fun everyTriggerHasAnInstruction() {
        Trigger.values().forEach { assertTrue("$it", AiPrompt.forTrigger(it).isNotBlank()) }
    }

    @Test fun noInstructionAsksTheModelToShame() {
        val banned = listOf("lazy", "guilt", "disappoint", "you must", "you should be ashamed")
        Trigger.values().forEach { t -> banned.forEach { assertFalse("$t/$it", AiPrompt.forTrigger(t).lowercase().contains(it)) } }
    }

    @Test fun theSummaryHasNoFreeTextAndStaysInRange() {
        val s = DaySummary.of(tasksDone = 99, tasksTotal = 3, shortsSwipes = -4, gatesShown = 2, lastTimerMinutes = 999999, workStreakDays = 4, hourOfDay = 31)
        val t = s.toPromptText()
        assertTrue(t.contains("Tasks done: 3 of 3")); assertTrue(t.contains("Shorts swiped: 0"))
        assertTrue(t.contains("Longest recent study timer: 1440 minutes")); assertTrue(t.contains("Local hour: 23"))
        assertEquals(7, t.lines().filter { it.isNotBlank() }.size)   // a header and six numbers, nothing else
    }
}
