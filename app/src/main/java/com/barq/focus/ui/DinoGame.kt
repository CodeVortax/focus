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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

// Dino Run. Game idea from GuillaumeFalourd/formulas-games (Apache-2.0); all drawing is done in code, no sprites.

private const val DN_W = 640f
private const val DN_H = 260f
private const val DN_GROUND = 200f
private const val DN_X = 60f

/** [bottom] is the height of the lowest edge above the ground. kind: 0 small cactus, 1 large cactus, 2 bird. */
private class DnObs(val kind: Int, var x: Float, val w: Float, val h: Float, val bottom: Float, val n: Int)

private class DnState {
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var dist = 0f
    var t = 0f
    var hgt = 0f                                // feet above the ground
    var vy = 0f
    var duck = false
    val obs = ArrayList<DnObs>()
    private var nextGap = 380f

    val standing: Boolean get() = !(duck && hgt <= 0f)
    val dw: Float get() = if (standing) 34f else 46f
    val dh: Float get() = if (standing) 46f else 26f

    init { reset() }

    fun reset() {
        dist = 0f; t = 0f; hgt = 0f; vy = 0f; duck = false
        obs.clear(); nextGap = 380f; score = 0; paused = false; phase = 0
    }

    fun start() { if (phase == 0) phase = 1 }

    fun jump() {
        if (phase == 0) phase = 1
        if (phase == 1 && !paused && hgt <= 0f && vy <= 0f) vy = 620f
    }

    private fun spawn() {
        val r = Math.random()
        if (score > 60 && r < 0.22) {
            val low = Math.random() < 0.5
            obs.add(DnObs(2, DN_W + 20f, 42f, 30f, if (low) 8f else 32f, 1))
        } else if (r < 0.6) {
            val n = 1 + (Math.random() * 3).toInt().coerceAtMost(2)
            obs.add(DnObs(0, DN_W + 20f, 16f * n + 3f * (n - 1), 34f, 0f, n))
        } else {
            val n = 1 + (Math.random() * 2).toInt().coerceAtMost(1)
            obs.add(DnObs(1, DN_W + 20f, 22f * n + 4f * (n - 1), 52f, 0f, n))
        }
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        t += dt
        val speed = minOf(640f, 270f + score * 0.45f)
        dist += speed * dt
        score = (dist / 14f).toInt()
        if (hgt > 0f || vy > 0f) {
            val g = if (duck) 4200f else 1900f      // holding duck in the air drops you faster
            vy -= g * dt
            hgt += vy * dt
            if (hgt <= 0f) { hgt = 0f; vy = 0f }
        }
        nextGap -= speed * dt
        if (nextGap <= 0f) {
            spawn()
            nextGap = 300f + Math.random().toFloat() * 260f + speed * 0.4f
        }
        var i = 0
        while (i < obs.size) {
            val o = obs[i]
            o.x -= speed * dt
            if (o.x + o.w < -10f) { obs.removeAt(i); continue }
            // slightly forgiving hit boxes
            val dx0 = DN_X + 5f
            val dx1 = DN_X + dw - 5f
            val dy0 = hgt + 3f
            val dy1 = hgt + dh - 5f
            val ox0 = o.x + 4f
            val ox1 = o.x + o.w - 4f
            val oy0 = o.bottom + 3f
            val oy1 = o.bottom + o.h - 3f
            if (dx1 > ox0 && dx0 < ox1 && dy1 > oy0 && dy0 < oy1) phase = 2
            i++
        }
    }
}

@Composable
fun DinoGame(store: Store, onBack: () -> Unit) {
    val s = remember { DnState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_dino")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) { s.paused = true; s.duck = false } }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_dino", best) }
    }
    GameScaffold(
        title = tr("Dino Run"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true; s.duck = false }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF1B1F2A))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { s.jump() }) }
                    ) {
                        if (tick.longValue < 0) return@Canvas
                        drawDino(s)
                    }
                    Text(
                        "HI ${best}   ${s.score}", color = Color(0xFFB8C0D8), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                    if (s.phase == 0) GameOverlay(
                        tr("Dino Run"), listOf(tr("Tap to jump. Hold the down button to duck."), trf("Best: %d", best)),
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldButton("▼", Modifier.weight(1f), onDown = { s.duck = true }, onUp = { s.duck = false }, height = 84.dp, fontSize = 34.sp)
                    HoldButton("▲", Modifier.weight(1f), onDown = { s.jump() }, height = 84.dp, fontSize = 34.sp)
                }
            }
        }
    }
}

private fun DrawScope.drawDino(s: DnState) {
    virtualField(DN_W, DN_H) {
        val ink = Color(0xFF535353)
        drawRect(Color(0xFFF7F7F7), Offset.Zero, Size(DN_W, DN_H))
        // clouds
        for (i in 0 until 3) {
            val cx = (((i * 300f - s.dist * 0.15f) % 900f) + 900f) % 900f - 120f
            val cy = 30f + i * 22f
            drawRoundRect(Color(0xFFDDDDDD), Offset(cx, cy), Size(64f, 16f), CornerRadius(8f))
            drawCircle(Color(0xFFDDDDDD), 11f, Offset(cx + 24f, cy + 3f))
        }
        // ground
        drawRect(ink, Offset(0f, DN_GROUND), Size(DN_W, 2f))
        val off = s.dist % 47f
        for (i in 0 until 15) {
            drawRect(Color(0xFF9A9A9A), Offset(i * 47f - off + (i * 13 % 20), DN_GROUND + 8f + (i % 3) * 5f), Size(8f + (i % 4) * 3f, 2f))
        }
        // obstacles
        for (o in s.obs) {
            if (o.kind == 2) {
                val top = DN_GROUND - o.bottom - o.h
                drawOval(ink, Offset(o.x, top + 9f), Size(o.w, 14f))
                val wingUp = (s.t * 10f).toInt() % 2 == 0
                val wing = Path().apply {
                    moveTo(o.x + 14f, top + 12f)
                    lineTo(o.x + 28f, top + 12f)
                    lineTo(o.x + 21f, if (wingUp) top else top + 28f)
                    close()
                }
                drawPath(wing, ink)
                val beak = Path().apply {
                    moveTo(o.x, top + 14f)
                    lineTo(o.x - 9f, top + 17f)
                    lineTo(o.x, top + 20f)
                    close()
                }
                drawPath(beak, Color(0xFFE08A00))
                drawCircle(Color.White, 2.5f, Offset(o.x + 7f, top + 14f))
            } else {
                val sw = if (o.kind == 0) 16f else 22f
                val top = DN_GROUND - o.h
                for (k in 0 until o.n) {
                    val sx = o.x + k * (sw + (if (o.kind == 0) 3f else 4f))
                    drawRoundRect(Color(0xFF3F7D3A), Offset(sx + sw * 0.3f, top), Size(sw * 0.4f, o.h), CornerRadius(sw * 0.2f))
                    drawRoundRect(Color(0xFF3F7D3A), Offset(sx, top + o.h * 0.35f), Size(sw * 0.3f, o.h * 0.14f), CornerRadius(3f))
                    drawRoundRect(Color(0xFF3F7D3A), Offset(sx, top + o.h * 0.25f), Size(sw * 0.14f, o.h * 0.24f), CornerRadius(3f))
                    drawRoundRect(Color(0xFF3F7D3A), Offset(sx + sw * 0.7f, top + o.h * 0.5f), Size(sw * 0.3f, o.h * 0.14f), CornerRadius(3f))
                    drawRoundRect(Color(0xFF3F7D3A), Offset(sx + sw * 0.86f, top + o.h * 0.4f), Size(sw * 0.14f, o.h * 0.24f), CornerRadius(3f))
                }
            }
        }
        // dino
        val top = DN_GROUND - s.hgt - s.dh
        val x = DN_X
        if (s.standing) {
            drawRoundRect(ink, Offset(x + 12f, top), Size(22f, 18f), CornerRadius(4f))          // head
            drawRect(Color.White, Offset(x + 26f, top + 4f), Size(4f, 4f))
            drawRect(Color(0xFF7A7A7A), Offset(x + 26f, top + 13f), Size(8f, 3f))                  // mouth
            drawRoundRect(ink, Offset(x + 4f, top + 14f), Size(24f, 22f), CornerRadius(5f))       // body
            drawRect(ink, Offset(x - 4f, top + 16f), Size(10f, 8f))                               // tail
            drawRect(ink, Offset(x + 24f, top + 26f), Size(7f, 3f))                               // arm
            val stride = s.hgt <= 0f && (s.t * 12f).toInt() % 2 == 0
            drawRect(ink, Offset(x + 8f, top + 36f), Size(6f, if (stride) 6f else 10f))
            drawRect(ink, Offset(x + 18f, top + 36f), Size(6f, if (stride) 10f else 6f))
        } else {
            drawRoundRect(ink, Offset(x + 30f, top), Size(16f, 14f), CornerRadius(4f))            // head
            drawRect(Color.White, Offset(x + 38f, top + 3f), Size(3f, 3f))
            drawRoundRect(ink, Offset(x, top + 8f), Size(38f, 14f), CornerRadius(5f))             // body
            drawRect(ink, Offset(x - 6f, top + 10f), Size(8f, 6f))                                // tail
            val stride = (s.t * 12f).toInt() % 2 == 0
            drawRect(ink, Offset(x + 8f, top + 22f), Size(6f, if (stride) 2f else 4f))
            drawRect(ink, Offset(x + 22f, top + 22f), Size(6f, if (stride) 4f else 2f))
        }
    }
}
