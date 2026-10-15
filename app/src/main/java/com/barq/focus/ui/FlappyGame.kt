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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlin.math.sin

// Flappy Bird. Game idea from GuillaumeFalourd/formulas-games (Apache-2.0): hold to rise, let go to sink.

private const val FB_W = 360f
private const val FB_H = 640f
private const val FB_GROUND = 60f
private const val FB_PIPE_W = 56f
private const val FB_BX = 100f
private const val FB_R = 13f

private class FlPipe(var x: Float, val top: Float, val gap: Float, var passed: Boolean = false)

private class FlState {
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var held = false
    var y = FB_H / 2f
    var vy = 0f
    var t = 0f
    val pipes = ArrayList<FlPipe>()
    private val rnd = java.util.Random()

    init { reset() }

    fun reset() {
        y = FB_H / 2f - 40f; vy = 0f; t = 0f; held = false
        pipes.clear(); score = 0; paused = false; phase = 0
    }

    fun start() { if (phase == 0) phase = 1 }
    fun press() { if (phase == 0) phase = 1; held = true }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        t += dt
        // faster and tighter as the score grows (the original does it in steps of five)
        val speed = 120f + 6f * minOf(score, 15)
        val gapNow = maxOf(140f, 190f - 3f * score)
        val target = if (held) -230f else 230f
        vy += (target - vy) * minOf(1f, dt * 10f)
        y += vy * dt
        if (y - FB_R < 0f) { y = FB_R; vy = 0f }

        val last = if (pipes.isEmpty()) null else pipes[pipes.size - 1]
        if (last == null || last.x < FB_W - 210f) {
            val minTop = 70f
            val maxTop = FB_H - FB_GROUND - 70f - gapNow
            pipes.add(FlPipe(FB_W + 30f, minTop + rnd.nextFloat() * (maxTop - minTop), gapNow))
        }
        var i = 0
        while (i < pipes.size) {
            val p = pipes[i]
            p.x -= speed * dt
            if (!p.passed && p.x + FB_PIPE_W < FB_BX - FB_R) { p.passed = true; score++ }
            if (p.x + FB_PIPE_W < -10f) { pipes.removeAt(i); continue }
            if (FB_BX + FB_R - 3f > p.x && FB_BX - FB_R + 3f < p.x + FB_PIPE_W) {
                if (y - FB_R + 3f < p.top || y + FB_R - 3f > p.top + p.gap) phase = 2
            }
            i++
        }
        if (y + FB_R > FB_H - FB_GROUND) { y = FB_H - FB_GROUND - FB_R; phase = 2 }
    }
}

@Composable
fun FlappyGame(store: Store, onBack: () -> Unit) {
    val s = remember { FlState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_flappy")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) { s.paused = true; s.held = false } }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_flappy", best) }
    }
    GameScaffold(
        title = tr("Flappy Bird"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true; s.held = false }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0B1B2B))) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        s.press()
                        try { tryAwaitRelease() } finally { s.held = false }
                    })
                }
            ) {
                if (tick.longValue < 0) return@Canvas
                drawFlappy(s)
            }
            Text(
                "${s.score}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            )
            if (s.phase == 0) GameOverlay(
                tr("Flappy Bird"), listOf(tr("Hold to rise, release to sink."), trf("Best: %d", best)),
                tr("Start"), { s.start() }, onBack, tr("Back to games")
            )
            if (s.phase == 2) GameOverlay(
                tr("Game over!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                tr("Play again"), { s.reset(); s.start() }, onBack, tr("Back to games")
            )
            if (s.phase == 1 && s.paused) GameOverlay(
                tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit")
            )
        }
    }
}

private fun DrawScope.drawFlappy(s: FlState) {
    virtualField(FB_W, FB_H) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF4EC0F0), Color(0xFFB6E8FA)), 0f, FB_H),
            Offset.Zero, Size(FB_W, FB_H)
        )
        // slow clouds
        for (i in 0 until 4) {
            val cx = (((i * 140f - s.t * 14f) % 560f) + 560f) % 560f - 100f
            val cy = 70f + i * 55f
            drawRoundRect(Color(0xB3FFFFFF), Offset(cx, cy), Size(78f, 22f), CornerRadius(11f))
            drawCircle(Color(0xB3FFFFFF), 16f, Offset(cx + 26f, cy + 4f))
        }
        val pipeColor = Color(0xFF3FA34D)
        val capColor = Color(0xFF5CC46A)
        for (p in s.pipes) {
            drawRect(pipeColor, Offset(p.x, 0f), Size(FB_PIPE_W, p.top))
            drawRect(capColor, Offset(p.x - 4f, p.top - 22f), Size(FB_PIPE_W + 8f, 22f))
            val by = p.top + p.gap
            drawRect(pipeColor, Offset(p.x, by), Size(FB_PIPE_W, FB_H - FB_GROUND - by))
            drawRect(capColor, Offset(p.x - 4f, by), Size(FB_PIPE_W + 8f, 22f))
        }
        // ground
        drawRect(Color(0xFFDED895), Offset(0f, FB_H - FB_GROUND), Size(FB_W, FB_GROUND))
        drawRect(Color(0xFF73BF2E), Offset(0f, FB_H - FB_GROUND), Size(FB_W, 10f))
        val off = (s.t * 120f) % 24f
        for (i in 0 until 17) drawRect(Color(0x22000000), Offset(i * 24f - off, FB_H - FB_GROUND + 10f), Size(12f, 6f))
        // bird, tilted by its speed
        val tilt = (s.vy / 230f * 25f).coerceIn(-25f, 35f)
        rotate(tilt, Offset(FB_BX, s.y)) {
            drawCircle(Color(0xFFFFD43B), FB_R, Offset(FB_BX, s.y))
            drawCircle(Color(0xFFFFE98A), FB_R * 0.6f, Offset(FB_BX - 2f, s.y + 4f))
            drawOval(
                Color(0xFFF59F00), Offset(FB_BX - 11f, s.y + 1f + sin(s.t * 18f) * 3f), Size(14f, 9f)
            )
            drawCircle(Color.White, 5f, Offset(FB_BX + 6f, s.y - 4f))
            drawCircle(Color.Black, 2.4f, Offset(FB_BX + 7.5f, s.y - 4f))
            val beak = Path().apply {
                moveTo(FB_BX + 11f, s.y)
                lineTo(FB_BX + 22f, s.y + 3f)
                lineTo(FB_BX + 11f, s.y + 7f)
                close()
            }
            drawPath(beak, Color(0xFFFF8C1A))
        }
    }
}
