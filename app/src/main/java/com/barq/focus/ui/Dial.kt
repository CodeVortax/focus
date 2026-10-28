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

package com.barq.focus.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Minutes per full turn. */
const val DIAL_MIN_PER_TURN = 30

/**
 * Turn the ring with your finger: clockwise raises the minutes, anticlockwise lowers them, one full turn is 30.
 * Put it on the timer's own ring, so there is one ring, not two. [onDragging] tells the ring when to show the part
 * of the turn (while drawing) and when to fill up to a full circle (after you let go).
 */
@Composable
fun Modifier.turnDial(minutes: Int, enabled: Boolean, onDragging: (Boolean) -> Unit, onChange: (Int) -> Unit): Modifier {
    if (!enabled) return this
    val cur by rememberUpdatedState(minutes)
    val cb by rememberUpdatedState(onChange)
    val drag by rememberUpdatedState(onDragging)
    var last by remember { mutableStateOf(0.0) }
    var acc by remember { mutableStateOf(0.0) }
    var base by remember { mutableStateOf(0) }
    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { p ->
                last = atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
                acc = 0.0; base = cur; drag(true)
            },
            onDrag = { change, _ ->
                change.consume()
                val a = atan2((change.position.y - size.height / 2f).toDouble(), (change.position.x - size.width / 2f).toDouble())
                var d = a - last
                if (d > PI) d -= 2 * PI
                if (d < -PI) d += 2 * PI
                last = a
                acc += d
                val v = (base + acc / (2 * PI) * DIAL_MIN_PER_TURN).roundToInt().coerceIn(1, 600)
                if (v != cur) cb(v)
            },
            onDragEnd = { drag(false) },
            onDragCancel = { drag(false) }
        )
    }
}
