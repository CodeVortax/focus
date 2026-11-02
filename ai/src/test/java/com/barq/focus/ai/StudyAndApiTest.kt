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
        assertTrue(Api.errorText(429, "{}").contains("Too many"))
        assertTrue(Api.errorText(402, "{}").contains("credit"))
        assertTrue(Api.errorText(429, """{"error":{"message":"You exceeded your current quota"}}""").contains("credit"))
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

    @Test fun readsTheModelListAndStripsGeminiPrefixes() {
        val body = """{"data":[{"id":"gpt-4o-mini"},{"id":"models/gemini-2.0-flash"},{"id":""},{"object":"x"}]}"""
        assertEquals(listOf("gpt-4o-mini", "gemini-2.0-flash"), Api.parseModels(body))
        assertEquals(emptyList<String>(), Api.parseModels("nope"))
    }

    @Test fun nonChatModelsAreHidden() {
        val ids = listOf("gpt-4o-mini", "text-embedding-3-small", "whisper-1", "dall-e-3", "tts-1", "gpt-4o-audio-preview", "gpt-4o")
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), Models.chatOnly(ids))
    }

    @Test fun picksACheapFastModelAutomatically() {
        assertEquals("gpt-4o-mini", Models.best(listOf("gpt-4o", "gpt-4o-mini-2024-07-18", "gpt-4o-mini", "o1")))
        assertEquals("gemini-2.0-flash", Models.best(listOf("gemini-1.5-pro", "gemini-2.0-flash-exp", "gemini-2.0-flash")))
        assertEquals("claude-haiku-5-5", Models.best(listOf("claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-5-5")))
        assertEquals("alpha", Models.best(listOf("alpha", "beta")))
        assertNull(Models.best(emptyList()))
    }

    @Test fun guessesTheProviderFromTheKey() {
        assertEquals("OpenRouter", Providers.detect("sk-or-v1-abc")?.name)
        assertEquals("Anthropic", Providers.detect("sk-ant-api03-abc")?.name)
        assertEquals("Gemini", Providers.detect("AIzaSyABC")?.name)
        assertEquals("OpenAI", Providers.detect("sk-proj-abc")?.name)
        assertNull(Providers.detect("hello"))
    }

    @Test fun flashcardsAreReadIntoPairs() {
        val t = "**Q: What is a cell?**\nA: The basic unit of life.\n\nQ: What is DNA?\nA: The genetic code.\nSome other line"
        assertEquals(listOf("What is a cell?" to "The basic unit of life.", "What is DNA?" to "The genetic code."), parseCards(t))
        assertEquals(0, parseCards("no cards here").size)
    }

    @Test fun lightMarkdownMakesBulletsAndHidesStars() {
        val r = lightMarkdown("# Title\n- one\n**bold** text").text
        assertEquals("Title\n\u2022 one\nbold text", r)
    }

    @Test fun errorKindsDecideWhatTheAppDoes() {
        assertEquals(ErrKind.KEY, Api.kindOf(401, ""))
        assertEquals(ErrKind.MODEL, Api.kindOf(404, ""))
        assertEquals(ErrKind.MODEL, Api.kindOf(400, """{"error":{"message":"The model does not exist"}}"""))
        assertEquals(ErrKind.OTHER, Api.kindOf(400, """{"error":{"message":"bad json"}}"""))
        assertEquals(ErrKind.RATE, Api.kindOf(429, """{"error":{"message":"Rate limit reached"}}"""))
        assertEquals(ErrKind.QUOTA, Api.kindOf(402, ""))
        assertEquals(ErrKind.QUOTA, Api.kindOf(429, """{"error":{"code":"insufficient_quota"}}"""))
        assertEquals(ErrKind.MODEL, Api.kindOf(429, """{"error":{"message":"Quota exceeded, limit: 0, model: gemini-2.0-flash"}}"""))
        assertEquals(ErrKind.SERVER, Api.kindOf(503, ""))
    }

    @Test fun deepSeekKeysAreToldApartFromOpenAiKeys() {
        assertEquals("DeepSeek", Providers.detect("sk-" + "a1b2c3d4".repeat(4))?.name)
        assertEquals("OpenAI", Providers.detect("sk-proj-" + "x".repeat(60))?.name)
        assertEquals("https://api.deepseek.com/v1", Providers.list.first { it.name == "DeepSeek" }.base)
    }

    @Test fun deepSeekChatIsPreferredOverTheReasoner() {
        assertEquals("deepseek-chat", Models.best(listOf("deepseek-reasoner", "deepseek-chat")))
    }

    @Test fun rankingPutsStableAheadOfPreviewAndFreeTier() {
        val r = Models.ranked(listOf("meta/llama-3:free", "openai/gpt-4o-mini:free", "openai/gpt-4o-mini", "x/other"))
        assertEquals("openai/gpt-4o-mini", r.first())
        assertEquals(4, r.size)
    }

    @Test fun statusLineForAProvider() {
        val body = """{"services":[{"id":"openai","name":"OpenAI","description":"All Systems Operational","activeIncidents":0},{"id":"deepseek","name":"DeepSeek","description":"Degraded","activeIncidents":2}]}"""
        assertEquals("OpenAI: All Systems Operational", Status.summarize(body, "openai"))
        assertEquals("DeepSeek: Degraded (2 active incidents)", Status.summarize(body, "deepseek"))
        assertNull(Status.summarize(body, "nope"))
        assertNull(Status.summarize("broken", "openai"))
    }

    @Test fun statusIdsFollowTheAddress() {
        assertEquals("openai", Providers.statusId("https://api.openai.com/v1"))
        assertEquals("gemini-api", Providers.statusId("https://generativelanguage.googleapis.com/v1beta/openai"))
        assertEquals("deepseek", Providers.statusId("https://api.deepseek.com/v1"))
        assertNull(Providers.statusId("https://example.com/v1"))
    }
}

