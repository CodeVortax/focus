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

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.barq.focus.MainActivity
import com.barq.focus.R
import com.barq.focus.data.Store
import com.barq.focus.data.tr

/** Quick Settings tile for the study timer: a tap pauses or resumes it, and with no timer running it opens Focus. */
class FocusTile : TileService() {
    override fun onStartListening() { refresh() }

    override fun onClick() {
        val store = Store(this)
        if (store.activeCardId == -1L) { openFocus(); return }
        if (store.activePaused) store.resumeActive() else store.pauseActive()
        TimerAlarm.reschedule(this, store)
        Notifier.syncTimer(this, store)
        FocusWidgets.refresh(this)
        refresh()
    }

    private fun openFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(TimerAlarm.openApp(this))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Exception) {}
    }

    private fun refresh() {
        val t = qsTile ?: return
        val store = Store(this)
        val running = store.activeCardId != -1L
        val paused = running && store.activePaused
        t.label = tr("Study timer")
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile_timer)
        t.state = if (running && !paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = tr(if (!running) "Open Focus" else if (paused) "Paused" else "Running")
        t.updateTile()
    }
}
