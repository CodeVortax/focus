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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.barq.focus.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 0 = circle, otherwise the number of corners. Mode 7 cycles through the shapes using [seed]. */
fun shapeSides(mode: Int, seed: Int): Int = when (mode) {
    0 -> 0
    in 1..6 -> mode + 2
    else -> listOf(3, 4, 5, 6, 7, 8)[abs(seed) % 6]
}

/** A softly rounded regular polygon (triangle, square, pentagon...) or a circle when [sides] is 0. */
internal fun shapePath(c: Offset, r: Float, sides: Int): Path {
    if (sides < 3) return Path().apply { addOval(Rect(c, r)) }
    // similar visual weight for every shape: bigger corners get a slightly bigger radius
    val eq = sqrt(PI / (0.5 * sides * sin(2 * PI / sides))).toFloat()
    val rr = r * (1f + 0.5f * (eq - 1f))
    val start = if (sides % 2 == 0) -PI / 2 + PI / sides else -PI / 2
    val pts = List(sides) { i ->
        val a = start + 2 * PI * i / sides
        Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
    }
    val path = Path()
    val round = 0.3f
    for (i in 0 until sides) {
        val prev = pts[(i + sides - 1) % sides]
        val cur = pts[i]
        val next = pts[(i + 1) % sides]
        val a = lerp(cur, prev, round)
        val b = lerp(cur, next, round)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticBezierTo(cur.x, cur.y, b.x, b.y)
    }
    path.close()
    return path
}

/**
 * The task check. Tapping it pops (springy bounce + an expanding ring) and a finished task keeps a soft glow.
 * The shape comes from Setup > Check shape.
 */
@Composable
fun ShapeCheck(checked: Boolean, color: Color, seed: Int = 0, onToggle: (Boolean) -> Unit) {
    val sides = shapeSides(AppTheme.checkShape, seed)
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val glow by animateFloatAsState(if (checked) 1f else 0f, tween(380), label = "glow")
    val outline = MaterialTheme.colorScheme.outline
    val src = remember { MutableInteractionSource() }

    Box(Modifier.padding(8.dp)) {
        Canvas(
            Modifier.size(28.dp)
                .clickable(interactionSource = src, indication = null) {
                    val becoming = !checked
                    scope.launch {
                        if (becoming) {
                            burst.snapTo(0f)
                            launch { burst.animateTo(1f, tween(650)) }
                            pop.snapTo(0.55f)
                            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
                        } else {
                            pop.snapTo(0.8f)
                            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
                        }
                    }
                    onToggle(becoming)
                }
        ) {
            val c = center
            val r = size.minDimension / 2f * 0.8f * pop.value
            val path = shapePath(c, r, sides)
            if (glow > 0.01f) {
                for (i in 5 downTo 1) {
                    drawPath(
                        path, color.copy(alpha = 0.085f * glow),
                        style = Stroke(width = 2.6.dp.toPx() * i, join = StrokeJoin.Round, cap = StrokeCap.Round)
                    )
                }
            }
            val b = burst.value
            if (b > 0f && b < 1f) {
                withTransform({ scale(1f + 0.9f * b, 1f + 0.9f * b, pivot = c) }) {
                    drawPath(path, color.copy(alpha = 0.65f * (1f - b)), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                }
            }
            if (checked) {
                drawPath(path, color)
                val tick = Path().apply {
                    moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.10f, c.y + r * 0.34f)
                    lineTo(c.x + r * 0.45f, c.y - r * 0.28f)
                }
                drawPath(tick, Color.White, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}

/** Static little preview used in Setup to pick the shape. */
@Composable
fun ShapePreview(mode: Int, selected: Boolean, color: Color, size: Dp = 36.dp) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f * 0.8f
        if (mode == 7) {
            // "mixed": a triangle and a hexagon overlapping
            val a = shapePath(Offset(c.x - r * 0.35f, c.y), r * 0.7f, 3)
            val b = shapePath(Offset(c.x + r * 0.4f, c.y), r * 0.6f, 6)
            drawPath(a, if (selected) color else outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            drawPath(b, if (selected) color else outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        } else {
            val path = shapePath(c, r, shapeSides(mode, 0))
            if (selected) drawPath(path, color) else drawPath(path, outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}
