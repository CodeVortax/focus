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

package com.barq.focus.service

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** In-process state shared between WatchService and FocusAccessibility. */
object Shared {
    @Volatile var fg: String? = null
    @Volatile var inShorts = false
    @Volatile var suppressUntil = 0L   // don't pop an overlay until then (we're closing an app)
    @Volatile var watchAlive = false
    @Volatile var treatUntil = 0L      // a bought treat is good in EVERY gate (app gate and Shorts gate) until then
    @Volatile var challengeCard = -1L   // "Challenge me" picked this subject: open it and pick a task

    // a .foc file opened with / text shared to Focus, waiting for the import screen
    var pendingUri: Uri? = null
    var pendingText: String? = null
    var pendingTick by mutableIntStateOf(0)
    @Volatile var settingsFocus: String? = null     // "sound:<cardId>": Settings opens that block and highlights the row
    @Volatile var pendingGo: String? = null       // a notification or widget asked to open a screen: "music", "card:12" ...
    var goTick by mutableIntStateOf(0)
}
