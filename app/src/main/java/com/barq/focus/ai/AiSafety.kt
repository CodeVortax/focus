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
    private val PHRASES = listOf(
        "kill myself", "want to die", "end my life", "suicide", "self harm", "self-harm", "hurt myself", "no reason to live",
        "أريد أن أموت", "اريد ان اموت", "انتحار", "أقتل نفسي", "اقتل نفسي", "أؤذي نفسي", "اؤذي نفسي",
    )

    fun needsCare(text: String): Boolean {
        val t = text.lowercase()
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
