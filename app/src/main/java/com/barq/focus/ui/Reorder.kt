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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged

/**
 * Drag-to-arrange for a plain Column of rows. The list is only changed when you let go, so rows never jump
 * under your finger: while dragging, the held row follows the finger and the rows it passes slide out of the way.
 * Put every row of one list in the same Column (no spacing), give each row [reorderRow], and give its handle [reorderHandle].
 */
@Stable
class ReorderState {
    var from by mutableIntStateOf(-1)
    var dy by mutableFloatStateOf(0f)
    var count = 0
    val heights = mutableStateMapOf<Int, Int>()

    fun h(i: Int): Int = heights[i] ?: 0

    /** Where the held row would land if dropped now. */
    fun target(): Int {
        if (from < 0) return -1
        var t = from
        var rem = dy
        while (rem > 0 && t + 1 < count && rem > h(t + 1) / 2f) { rem -= h(t + 1); t++ }
        while (rem < 0 && t - 1 >= 0 && -rem > h(t - 1) / 2f) { rem += h(t - 1); t-- }
        return t
    }
}

fun Modifier.reorderRow(rs: ReorderState, i: Int): Modifier = this
    .onSizeChanged { rs.heights[i] = it.height }
    .zIndex(if (rs.from == i) 1f else 0f)
    .graphicsLayer {
        val f = rs.from
        translationY = when {
            f < 0 -> 0f
            i == f -> rs.dy
            else -> {
                val t = rs.target()
                when {
                    t > f && i in (f + 1)..t -> -rs.h(f).toFloat()
                    t < f && i in t until f -> rs.h(f).toFloat()
                    else -> 0f
                }
            }
        }
    }

fun Modifier.reorderHandle(rs: ReorderState, i: Int, count: Int, onMove: (from: Int, to: Int) -> Unit): Modifier =
    pointerInput(i, count) {
        detectDragGestures(
            onDragStart = { rs.count = count; rs.from = i; rs.dy = 0f },
            onDrag = { change, d -> change.consume(); rs.dy += d.y },
            onDragEnd = {
                val t = rs.target()
                val f = rs.from
                rs.from = -1; rs.dy = 0f
                if (t >= 0 && f >= 0 && t != f) onMove(f, t)
            },
            onDragCancel = { rs.from = -1; rs.dy = 0f }
        )
    }
