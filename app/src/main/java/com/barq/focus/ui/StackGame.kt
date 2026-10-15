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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlin.math.abs
import kotlin.math.sin

private const val STK_START_W = 0.4f      // widths and positions are fractions of the screen width

private class StackState {
    var phase by mutableIntStateOf(0)         // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    val xs = ArrayList<Float>()               // placed blocks, bottom to top
    val ws = ArrayList<Float>()
    var mx = 0f                               // the sliding block
    var mw = STK_START_W
    var dir = 1f
    var speed = 0.5f
    var streak = 0
    var cam = 0f                              // camera height in block units
    var flash = 0f

    init { reset() }

    fun reset() {
        xs.clear(); ws.clear()
        xs.add(0.3f); ws.add(STK_START_W)
        mx = 0f; mw = STK_START_W; dir = 1f; speed = 0.5f
        score = 0; streak = 0; cam = 0f; flash = 0f
        paused = false
        phase = 0
    }

    fun tap() {
        if (paused) return
        if (phase == 0) { phase = 1; return }
        if (phase != 1) return
        val tx = xs[xs.size - 1]
        val tw = ws[ws.size - 1]
        val left = maxOf(mx, tx)
        val right = minOf(mx + mw, tx + tw)
        if (right - left <= 0.004f) { phase = 2; return }
        if (abs(mx - tx) < 0.012f) {
            // perfect drop: keep the width, and a run of them grows the tower's width back
            streak++
            score += 2
            flash = 1f
            if (streak >= 3 && tw < STK_START_W) {
                val nw = minOf(STK_START_W, tw + 0.04f)
                xs.add((tx - 0.02f).coerceIn(0f, 1f - nw)); ws.add(nw)
            } else { xs.add(tx); ws.add(tw) }
        } else {
            streak = 0
            score += 1
            xs.add(left); ws.add(right - left)
        }
        mw = ws[ws.size - 1]
        if (xs.size % 2 == 0) { mx = 0f; dir = 1f } else { mx = 1f - mw; dir = -1f }
        speed = (0.5f + score * 0.012f).coerceAtMost(1.3f)
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        mx += dir * speed * dt
        if (mx <= 0f) { mx = 0f; dir = 1f }
        if (mx + mw >= 1f) { mx = 1f - mw; dir = -1f }
        if (flash > 0f) flash = (flash - dt * 3f).coerceAtLeast(0f)
        val target = (xs.size - 7).coerceAtLeast(0).toFloat()
        cam += (target - cam) * minOf(1f, dt * 5f)
    }
}

@Composable
fun StackGame(store: Store, onBack: () -> Unit) {
    val s = remember { StackState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_stack")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.paused = true }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_stack", best) }
    }
    GameScaffold(
        title = tr("Stack Tower"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0B1030))) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { s.tap() }) }) {
                if (tick.longValue < 0) return@Canvas
                drawStack(s)
            }
            Text(
                "${s.score}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
            )
            when {
                s.phase == 0 -> Text(
                    tr("Tap to drop the block. Line it up!"), color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                )
                s.phase == 2 -> GameOverlay(
                    tr("Tower fell!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                    tr("Play again"), { s.reset() }, onBack, tr("Back to games")
                )
                s.paused -> GameOverlay(tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit"))
            }
        }
    }
}

/** A slowly cycling pastel palette, one colour per layer. */
private fun stkColor(i: Int): Color {
    val a = i * 0.22f
    return Color(
        red = 0.62f + 0.30f * sin(a),
        green = 0.62f + 0.30f * sin(a + 2.1f),
        blue = 0.62f + 0.30f * sin(a + 4.2f),
        alpha = 1f
    )
}

private fun DrawScope.drawStack(s: StackState) {
    val w = size.width
    val h = size.height
    val bh = h / 14f
    val baseY = h - bh * 1.5f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF3A2A6B))))
    fun blockY(i: Int) = baseY - (i - s.cam) * bh
    val n = s.xs.size
    for (i in 0 until n) {
        val y = blockY(i)
        if (y > h || y < -bh) continue
        val c = stkColor(i)
        drawRect(c, Offset(s.xs[i] * w, y), Size(s.ws[i] * w, bh * 0.94f))
        if (i == n - 1 && s.flash > 0f) {
            drawRect(Color.White.copy(alpha = 0.6f * s.flash), Offset(s.xs[i] * w, y), Size(s.ws[i] * w, bh * 0.94f))
        }
    }
    if (s.phase != 2) {
        val y = blockY(n)
        drawRect(stkColor(n), Offset(s.mx * w, y), Size(s.mw * w, bh * 0.94f))
    }
}
