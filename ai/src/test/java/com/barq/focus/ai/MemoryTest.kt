/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTest {
    private val a = MemoryItem(1, MemKind.STYLE, "Short answers", true)
    private val b = MemoryItem(2, MemKind.CAREFUL, "Don't scold me", true)
    private val c = MemoryItem(3, MemKind.HELP, "Hints first", false)

    @Test fun roundTrip() {
        val back = MemoryCodec.decode(MemoryCodec.encode(listOf(a, b, c)))
        assertEquals(listOf(a, b, c), back)
    }

    @Test fun separatorsAndNewlinesCannotBreakTheFormat() {
        val evil = MemoryItem(9, MemKind.ABOUT, "line one\nline|two\r\nthree", true)
        val back = MemoryCodec.decode(MemoryCodec.encode(listOf(evil)))
        assertEquals(1, back.size)
        assertFalse(back[0].text.contains('\n'))
        assertFalse(back[0].text.contains('|'))
    }

    @Test fun badLinesAreSkipped() {
        assertTrue(MemoryCodec.decode(null).isEmpty())
        assertTrue(MemoryCodec.decode("").isEmpty())
        assertEquals(1, MemoryCodec.decode("nonsense\nx|style|1|hi\n5|style|1|ok fine").size)
    }

    @Test fun textIsCapped() {
        assertEquals(MemoryCodec.MAX_CHARS, MemoryCodec.clean("x".repeat(500)).length)
    }

    @Test fun promptHasOnlyNotesThatAreOnAndGroupsThem() {
        val p = MemoryCodec.promptBlock(listOf(a, b, c))
        assertTrue(p.contains("Short answers"))
        assertTrue(p.contains("How to talk to me:"))
        assertTrue(p.contains("Be careful with:"))
        assertFalse(p.contains("Hints first"))
        assertFalse(p.contains("How you can help"))
        assertTrue(p.contains("never override your safety rules"))
    }

    @Test fun noNotesMeansNoBlock() {
        assertEquals("", MemoryCodec.promptBlock(emptyList()))
        assertEquals("", MemoryCodec.promptBlock(listOf(c)))
    }

    @Test fun suggestionIsTakenOutOfTheReply() {
        val (shown, note) = MemorySuggest.split("Sure, I will keep it short.\nREMEMBER: Prefers very short answers")
        assertEquals("Sure, I will keep it short.", shown)
        assertEquals("Prefers very short answers", note)
    }

    @Test fun replyWithoutSuggestionIsUntouched() {
        val (shown, note) = MemorySuggest.split("Just a normal answer.")
        assertEquals("Just a normal answer.", shown)
        assertNull(note)
    }

    @Test fun onlyTheLastSuggestionCountsAndAllAreRemoved() {
        val (shown, note) = MemorySuggest.split("Hi\nREMEMBER: first one\nmore text\nREMEMBER: second one")
        assertEquals("Second one".lowercase(), note!!.lowercase())
        assertFalse(shown.contains("REMEMBER"))
    }

    @Test fun promptsCarryMemoryOnlyOutsideCareMode() {
        val block = MemoryCodec.promptBlock(listOf(a))
        assertTrue(StudyPrompts.system(Mode.TUTOR, false, "English", block).contains("Short answers"))
        assertFalse(StudyPrompts.system(Mode.TUTOR, true, "English", block).contains("Short answers"))
        assertTrue(StudyPrompts.system(Mode.TUTOR, false, "English").contains("REMEMBER:"))
        assertFalse(StudyPrompts.system(Mode.TUTOR, true, "English").contains("REMEMBER:"))
    }
}
