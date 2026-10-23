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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.barq.focus.data.tr
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Minutes per full turn of the dial. */
const val DIAL_MIN_PER_TURN = 30

/**
 * Draw circles with your finger: clockwise raises the minutes, anticlockwise lowers them, one full turn is 30 minutes.
 * The ring shows the part of the current turn; the number in the middle is the total.
 */
@Composable
fun MinutesDial(minutes: Int, color: Color, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cur by rememberUpdatedState(minutes)
    val cb by rememberUpdatedState(onChange)
    var last by remember { mutableStateOf(0.0) }
    var acc by remember { mutableStateOf(0.0) }
    var base by remember { mutableStateOf(0) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.size(180.dp).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p ->
                    last = atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
                    acc = 0.0; base = cur
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
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(180.dp)) {
            val w = 16.dp.toPx()
            val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            val frac = (minutes % DIAL_MIN_PER_TURN) / DIAL_MIN_PER_TURN.toFloat()
            val sweep = if (minutes > 0 && frac == 0f) 360f else frac * 360f
            drawArc(color, -90f, sweep, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
            val ang = -PI / 2 + sweep / 180.0 * PI
            val r = (size.width - w) / 2
            drawCircle(Color.White, w * 0.32f, Offset((center.x + r * cos(ang)).toFloat(), (center.y + r * sin(ang)).toFloat()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(minutes.toString(), style = MaterialTheme.typography.displayMedium)
            Text(tr("min") + "  ·  " + tr("draw circles"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
