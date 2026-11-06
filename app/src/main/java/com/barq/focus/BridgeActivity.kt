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

package com.barq.focus

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import com.barq.focus.data.AiShare
import com.barq.focus.data.DayExport
import com.barq.focus.data.Store
import com.barq.focus.data.tr

/**
 * Study Mate asks for today's tasks and usage. This screen shows exactly what would be sent; only "Send" returns it,
 * and it goes straight back to Study Mate as the activity result (no clipboard, no server). Only Study Mate may ask.
 */
class BridgeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (callingPackage != AiShare.STUDY_MATE_PKG) { setResult(RESULT_CANCELED); finish(); return }
        val text = DayExport.build(this, Store(this))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(tr("Send today to Study Mate?"))
            .setMessage(text)
            .setPositiveButton(tr("Send")) { _, _ -> setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_TEXT, text)); finish() }
            .setNegativeButton(tr("Cancel")) { _, _ -> setResult(RESULT_CANCELED); finish() }
            .setOnCancelListener { setResult(RESULT_CANCELED); finish() }
            .show()
    }
}
