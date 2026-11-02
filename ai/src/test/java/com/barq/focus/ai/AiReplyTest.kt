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

class AiReplyTest {
    @Test fun readsAGoodAnswer() {
        val r = AiReplyParser.parse("""{"message": "Okay. Want to try 5 minutes?", "action": "five_minute_start"}""")
        assertEquals("Okay. Want to try 5 minutes?", r.message); assertEquals(AiAction.FIVE_MINUTE_START, r.action)
    }

    @Test fun anUnknownActionIsIgnored() {
        listOf("disable_block", "start_timer", "OPEN_TIMER ", "", "none; rm -rf").forEach {
            val r = AiReplyParser.parse("""{"message": "Hi", "action": "$it"}""")
            assertEquals("'$it'", AiAction.NONE, r.action)
        }
    }

    @Test fun anActionOfTheWrongTypeIsIgnored() {
        assertEquals(AiAction.NONE, AiReplyParser.parse("""{"message": "Hi", "action": 5}""").action)
        assertEquals(AiAction.NONE, AiReplyParser.parse("""{"message": "Hi", "action": {"x": 1}}""").action)
    }

    @Test fun extraFieldsAreIgnored() {
        val r = AiReplyParser.parse("""{"message": "Hi", "action": "none", "setLimit": 9999, "unblock": true}""")
        assertEquals("Hi", r.message); assertEquals(AiAction.NONE, r.action)
    }

    @Test fun plainTextAnswerIsKeptWithNoAction() {
        val r = AiReplyParser.parse("Sure, let's start small.")
        assertEquals("Sure, let's start small.", r.message); assertEquals(AiAction.NONE, r.action)
    }

    @Test fun aCodeFenceIsRemoved() {
        assertEquals("Hi", AiReplyParser.parse("```json\n{\"message\": \"Hi\", \"action\": \"none\"}\n```").message)
    }

    @Test fun brokenJsonFallsBackToText() {
        assertEquals("{\"message\": \"Hi", AiReplyParser.parse("{\"message\": \"Hi").message)
    }

    @Test fun emptyOrMissingMessageUsesTheFallbackAndNoAction() {
        listOf("", "   ", """{"message": "", "action": "open_timer"}""", """{"action": "open_timer"}""", """{"message": 5, "action": "open_timer"}""").forEach {
            val r = AiReplyParser.parse(it)
            assertEquals("'$it'", AiReplyParser.FALLBACK, r.message); assertEquals(AiAction.NONE, r.action)
        }
    }

    @Test fun controlCharactersAreRemoved() {
        val r = AiReplyParser.clean("Hi\u0000 there\u0007\u001B[31m")
        assertFalse(r.any { it.isISOControl() && it != '\n' })
        assertTrue(r.startsWith("Hi there"))
    }

    @Test fun longAnswersAreCut() {
        val r = AiReplyParser.clean("a".repeat(5000))
        assertTrue(r.length <= AiReplyParser.MAX_CHARS + 1)
        assertTrue(r.endsWith("…"))
    }

    @Test fun arabicSurvives() {
        assertEquals("أهلًا، كيف حالك؟", AiReplyParser.parse("""{"message": "أهلًا، كيف حالك؟", "action": "none"}""").message)
    }
}
