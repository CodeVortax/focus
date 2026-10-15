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

// World units: the screen is 1.0 tall, and (viewW) wide.
private const val FL_GRAVITY = 3.0f
private const val FL_FLAP = -0.9f
private const val FL_SPEED = 0.42f
private const val FL_PIPE_W = 0.11f
private const val FL_GAP = 0.27f
private const val FL_SPACING = 0.55f
private const val FL_RADIUS = 0.03f
private const val FL_GROUND = 0.9f

private class FlapState {
    var phase by mutableIntStateOf(0)          // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var y = 0.45f
    var vy = 0f
    var viewW = 0.56f                          // updated from the canvas
    val px = ArrayList<Float>()                // left edge of each pipe
    val gy = ArrayList<Float>()                // centre of each pipe's gap
    val passed = ArrayList<Boolean>()
    var wing = 0f
    private val rnd = java.util.Random()

    init { reset() }

    fun reset() {
        y = 0.45f; vy = 0f; score = 0; paused = false; wing = 0f
        px.clear(); gy.clear(); passed.clear()
        addPipe(viewW + 0.35f)
        phase = 0
    }

    private fun addPipe(x: Float) {
        px.add(x); gy.add(0.25f + rnd.nextFloat() * 0.4f); passed.add(false)
    }

    fun flap() {
        if (paused) return
        if (phase == 0) phase = 1
        if (phase == 1) { vy = FL_FLAP; wing = 1f }
    }

    fun bx() = viewW * 0.25f

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        vy += FL_GRAVITY * dt
        y += vy * dt
        if (wing > 0f) wing = (wing - dt * 4f).coerceAtLeast(0f)
        for (i in px.indices) px[i] = px[i] - FL_SPEED * dt
        if (px.isNotEmpty() && px[0] < -FL_PIPE_W - 0.1f) { px.removeAt(0); gy.removeAt(0); passed.removeAt(0) }
        if (px.isEmpty() || px[px.size - 1] < viewW + 0.35f - FL_SPACING) addPipe(viewW + 0.35f)
        val bx = bx()
        if (y - FL_RADIUS < 0f || y + FL_RADIUS > FL_GROUND) { phase = 2; return }
        for (i in px.indices) {
            if (!passed[i] && px[i] + FL_PIPE_W < bx - FL_RADIUS) { passed[i] = true; score++ }
            if (bx + FL_RADIUS > px[i] && bx - FL_RADIUS < px[i] + FL_PIPE_W) {
                if (y - FL_RADIUS < gy[i] - FL_GAP / 2f || y + FL_RADIUS > gy[i] + FL_GAP / 2f) { phase = 2; return }
            }
        }
    }
}

@Composable
fun FlapGame(store: Store, onBack: () -> Unit) {
    val s = remember { FlapState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_flap")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.paused = true }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_flap", best) }
    }
    GameScaffold(
        title = tr("Sky Flap"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF6EC6F5))) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { s.flap() }) }) {
                if (tick.longValue < 0) return@Canvas
                drawFlap(s)
            }
            Text(
                "${s.score}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
            )
            when {
                s.phase == 0 -> Text(
                    tr("Tap to flap. Fly through the gaps."), color = Color(0xFF12324A),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                )
                s.phase == 2 -> GameOverlay(
                    tr("Splat!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                    tr("Play again"), { s.reset() }, onBack, tr("Back to games")
                )
                s.paused -> GameOverlay(tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit"))
            }
        }
    }
}

private fun DrawScope.drawFlap(s: FlapState) {
    val k = size.height                       // one world unit in pixels
    s.viewW = size.width / size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4FB3F0), Color(0xFFCDEEFF))))
    // pipes
    for (i in s.px.indices) {
        val x = s.px[i] * k
        val top = (s.gy[i] - FL_GAP / 2f) * k
        val bottom = (s.gy[i] + FL_GAP / 2f) * k
        val pw = FL_PIPE_W * k
        val lip = 0.03f * k
        drawRect(Color(0xFF3FA34D), Offset(x, 0f), Size(pw, top))
        drawRect(Color(0xFF2E7D3A), Offset(x - lip * 0.4f, top - lip), Size(pw + lip * 0.8f, lip))
        drawRect(Color(0xFF3FA34D), Offset(x, bottom), Size(pw, FL_GROUND * k - bottom))
        drawRect(Color(0xFF2E7D3A), Offset(x - lip * 0.4f, bottom), Size(pw + lip * 0.8f, lip))
    }
    // ground
    drawRect(Color(0xFFDEB66B), Offset(0f, FL_GROUND * k), Size(size.width, size.height - FL_GROUND * k))
    drawRect(Color(0xFF6BBF59), Offset(0f, FL_GROUND * k), Size(size.width, 0.015f * k))
    // bird
    val bx = s.bx() * k
    val by = s.y * k
    val r = FL_RADIUS * k
    drawCircle(Color(0xFFFFD43B), r, Offset(bx, by))
    drawCircle(Color.White, r * 0.38f, Offset(bx + r * 0.35f, by - r * 0.3f))
    drawCircle(Color(0xFF222222), r * 0.16f, Offset(bx + r * 0.45f, by - r * 0.3f))
    drawRect(Color(0xFFFF8C42), Offset(bx + r * 0.7f, by - r * 0.1f), Size(r * 0.8f, r * 0.45f))
    drawCircle(Color(0xFFF0B429), r * 0.5f, Offset(bx - r * 0.35f, by + r * (0.1f + 0.4f * s.wing)))
}
