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

import android.content.ComponentName
import android.content.Intent

/**
 * How Study Mate asks Focus for the day. Explicit component, Focus shows the text and asks first, and the answer comes
 * straight back to this app as the activity result: no clipboard, no server, no new permission.
 */
object FocusBridge {
    const val FOCUS_PKG = "com.barq.focus"
    const val ASK_DAY = "com.barq.focus.BridgeActivity"

    /** Focus can also open Study Mate with its day text (the "Ask Study Mate" button there). */
    const val ACTION_CONTEXT = "com.barq.focus.ai.action.CONTEXT"
    const val MAX_DAY = 6000

    fun askDayIntent(): Intent = Intent().setComponent(ComponentName(FOCUS_PKG, ASK_DAY))

    fun clean(text: String): String = text.trim().take(MAX_DAY)
}
