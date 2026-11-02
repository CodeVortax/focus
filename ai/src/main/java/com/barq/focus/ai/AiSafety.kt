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

/**
 * A check that runs on the phone, before anything is sent. If a message sounds like the user may be in danger, the
 * app answers with a fixed, kind message and does not leave it to a model. It is a short keyword list, so it will
 * miss things; it is a safety net, not a guarantee, and the prompt also tells the model what to do.
 */
object AiSafety {
    private val RAW = listOf(
        // English
        "kill myself", "want to die", "end my life", "end it all", "suicide", "suicidal", "self harm", "self-harm", "hurt myself",
        "no reason to live", "don't want to live", "dont want to live", "better off dead", "wish i was dead", "wish i were dead",
        // Arabic (standard)
        "أريد أن أموت", "انتحار", "أقتل نفسي", "أؤذي نفسي", "لا أريد أن أعيش", "لا اريد ان اعيش",
        // Arabic (Egyptian and other dialects)
        "عايز اموت", "عاوز اموت", "عايزة اموت", "عاوزة اموت", "نفسي اموت", "مش عايز اعيش", "مش عاوز اعيش", "مش عايزة اعيش",
        "هقتل نفسي", "هنتحر", "هنتحر", "اخلص من حياتي", "بدي موت", "ابغى اموت",
    )

    /** Same spelling on both sides: lower case, no diacritics or tatweel, and the alef/yeh variants folded together. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            when {
                ch == '\u2019' || ch == '\u2018' -> sb.append('\'')
                ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' -> {}
                ch == '\u0623' || ch == '\u0625' || ch == '\u0622' || ch == '\u0671' -> sb.append('\u0627')
                ch == '\u0649' -> sb.append('\u064A')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }

    private val PHRASES = RAW.map { normalize(it) }.distinct()

    fun needsCare(text: String): Boolean {
        val t = normalize(text)
        return PHRASES.any { t.contains(it) }
    }

    const val CARE_EN = "I'm really glad you told me. This sounds bigger than studying, and you deserve real support. " +
        "Please talk to someone you trust, or to a local helpline or emergency service, right now. " +
        "Nothing on your task list matters more than you. I'll be here too."

    const val CARE_AR = "سعيد لأنك أخبرتني. هذا يبدو أكبر من الدراسة، وأنت تستحق دعمًا حقيقيًا. " +
        "من فضلك تحدّث الآن مع شخص تثق به، أو مع خط مساعدة أو جهة طوارئ في بلدك. " +
        "لا شيء في قائمة مهامك أهم منك. وسأبقى هنا أيضًا."

    fun careMessage(arabic: Boolean): String = if (arabic) CARE_AR else CARE_EN
}
