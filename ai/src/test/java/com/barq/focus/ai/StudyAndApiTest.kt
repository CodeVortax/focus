/*
 * Focus Study Mate: the optional AI helper for Focus
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAndApiTest {
    @Test fun readsAnOpenAiStyleAnswer() {
        assertEquals("Hello", Api.parseContent("""{"choices":[{"message":{"role":"assistant","content":"Hello"}}]}"""))
    }

    @Test fun badOrEmptyAnswersGiveNull() {
        listOf("", "not json", "{}", """{"choices":[]}""", """{"choices":[{"message":{"content":null}}]}""").forEach {
            assertNull(it, Api.parseContent(it))
        }
    }

    @Test fun errorsAreFriendlyAndShort() {
        assertTrue(Api.errorText(401, "{}").contains("key"))
        assertTrue(Api.errorText(429, "{}").contains("credit"))
        val long = Api.errorText(400, """{"error":{"message":"${"x".repeat(500)}"}}""")
        assertTrue(long.length < 300)
        assertTrue(Api.errorText(404, "").contains("model"))
    }

    @Test fun everyModeBuildsAPromptWithTheLanguageRule() {
        Mode.values().forEach { m ->
            val p = StudyPrompts.system(m, false, "Arabic")
            assertTrue("$m", p.contains("Reply in the language")); assertTrue(p.contains("Arabic"))
            assertTrue(p.startsWith(StudyPrompts.BASE))
        }
    }

    @Test fun modesHaveDifferentTasks() {
        val tasks = Mode.values().map { StudyPrompts.system(it, false, "English") }.toSet()
        assertEquals(Mode.values().size, tasks.size)
    }

    @Test fun careNoteIsOnlyAddedAfterAWorryingMessage() {
        assertFalse(StudyPrompts.system(Mode.TUTOR, false, "English").contains("worried"))
        assertTrue(StudyPrompts.system(Mode.TUTOR, true, "English").contains("worried"))
    }

    @Test fun quizAsksOneQuestionAtATime() {
        assertTrue(StudyPrompts.system(Mode.QUIZ, false, "English").contains("ONE question at a time"))
    }

    @Test fun providersAreHttpsOnly() {
        Providers.list.forEach { assertTrue(it.name, it.base.startsWith("https://")) }
    }

    @Test fun longRepliesAreCutAtTheGivenLength() {
        val r = AiReplyParser.clean("a".repeat(9000), 4000)
        assertTrue(r.length <= 4001)
    }
}
