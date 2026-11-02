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

import org.json.JSONObject

/**
 * The only things the AI mate can ask the app to do. None takes a parameter: the app chooses the subject and the
 * length itself, and the user taps a button to accept. There is no action that changes a limit, a block, a pass or
 * the gate, and no way for the model to add one.
 */
enum class AiAction(val wire: String) {
    NONE("none"),
    FIVE_MINUTE_START("five_minute_start"),      // offers a 5-minute timer on the first subject with something to do
    OPEN_TIMER("open_timer"),                    // offers to open the running or next timer
}

class AiReply(val message: String, val action: AiAction)

object AiReplyParser {
    const val MAX_CHARS = 500

    /** Shown when the model returns nothing usable. */
    const val FALLBACK = "I'm here if you want to talk."

    /** Reads a model answer. Anything unexpected becomes plain text with no action. */
    fun parse(raw: String): AiReply {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = try { if (text.startsWith("{")) JSONObject(text) else null } catch (_: Exception) { null }
        val message = clean(if (obj != null) (obj.opt("message") as? String ?: "") else text)
        val action = obj?.let { o -> AiAction.values().firstOrNull { it.wire == (o.opt("action") as? String) } } ?: AiAction.NONE
        return AiReply(message.ifBlank { FALLBACK }, if (message.isBlank()) AiAction.NONE else action)
    }

    /** No control characters, no markup tricks, a sensible length. */
    fun clean(s: String, max: Int = MAX_CHARS): String {
        val noControl = s.filter { it == '\n' || !it.isISOControl() }
        val tidy = noControl.replace(Regex("\\n{3,}"), "\n\n").trim()
        return if (tidy.length <= max) tidy else tidy.take(max).trimEnd() + "…"
    }
}
