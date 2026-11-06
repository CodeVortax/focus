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

package com.barq.focus.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** The small buttons that hand text to an AI: copy it, or open Study Mate with it in the message box. Always started by a tap. */
object AiShare {
    const val STUDY_MATE_PKG = "com.barq.focus.ai"
    const val ACTION_CONTEXT = "com.barq.focus.ai.action.CONTEXT"

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Focus", text))
        Toast.makeText(ctx, tr("Copied. Paste it into any AI chat."), Toast.LENGTH_SHORT).show()
    }

    /** Opens Study Mate with the text waiting in its message box (the person edits it and presses Send there). Without Study Mate, copies it. */
    fun ask(ctx: Context, text: String) {
        val i = Intent(ACTION_CONTEXT).setPackage(STUDY_MATE_PKG)
            .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) }
        catch (_: Exception) {
            copy(ctx, text)
            Toast.makeText(ctx, tr("Study Mate is not installed, so the text was copied."), Toast.LENGTH_LONG).show()
        }
    }
}
