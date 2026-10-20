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

import android.app.Application
import android.util.Log

/** Saves the last crash stack trace so it can be copied from Setup and pasted for debugging. */
class FocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                getSharedPreferences("focus", MODE_PRIVATE).edit()
                    .putString("last_crash", Log.getStackTraceString(e).take(8000)).commit()
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        com.barq.focus.data.I18n.init(this)
    }

    /** When the screen is hidden only the services stay alive, so give back the cached bitmaps. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            com.barq.focus.ui.trimMusicArt()
            System.gc()
        }
    }
}
