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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.abs
import kotlin.math.roundToInt

// All distances are in tiles. One beat = 4 tiles = 0.5 s at 8 tiles/s (120 BPM), so obstacles sit on the beat grid.
private const val DASH_SPEED = 8f
private const val DASH_GRAVITY = 80f
private const val DASH_JUMP = 17f
private const val DASH_BEAT = 4f
private const val DASH_LENGTH = 576f          // 36 bars of 16 tiles, about 72 seconds

/** kind: 0 spike, 1 block (landable). [x] is the left edge, [w] the width, [h] the height of a block. */
private class DashOb(val kind: Int, val x: Float, val w: Float, val h: Float)

/** The level is generated from a fixed seed so everyone plays the same track and "best %" means something. */
private fun buildLevel(): List<DashOb> {
    val rnd = java.util.Random(2008L)
    val out = ArrayList<DashOb>()
    var pos = 24f
    while (pos < DASH_LENGTH - 20f) {
        val p = pos / DASH_LENGTH
        val maxPattern = when {
            p < 0.15f -> 2
            p < 0.4f -> 4
            p < 0.7f -> 5
            else -> 6
        }
        var width = 1f
        when (rnd.nextInt(maxPattern + 1)) {
            0 -> out.add(DashOb(0, pos, 1f, 0.5f))
            1 -> { out.add(DashOb(0, pos, 1f, 0.5f)); out.add(DashOb(0, pos + 1f, 1f, 0.5f)); width = 2f }
            2 -> { out.add(DashOb(1, pos, 1f, 1f)); width = 1f }
            3 -> { out.add(DashOb(1, pos, 3f, 1f)); width = 3f }
            4 -> { out.add(DashOb(0, pos, 1f, 0.5f)); out.add(DashOb(0, pos + 4f, 1f, 0.5f)); width = 5f }
            5 -> { out.add(DashOb(1, pos, 2f, 1f)); out.add(DashOb(0, pos + 4f, 1f, 0.5f)); width = 5f }
            else -> { out.add(DashOb(1, pos, 2f, 1f)); out.add(DashOb(1, pos + 3f, 2f, 2f)); width = 5f }
        }
        // spacing snaps to half beats; gets a little tighter as the track goes on
        val extra = 2f * rnd.nextInt(if (p < 0.4f) 4 else 3)
        pos += width + 4f + extra
        pos = (pos / 2f).roundToInt() * 2f
    }
    return out
}

private class DashState {
    val level = buildLevel()
    var phase by mutableIntStateOf(0)         // 0 ready, 1 playing, 2 crashed, 3 finished
    var paused by mutableStateOf(false)
    var pct by mutableIntStateOf(0)
    var attempt by mutableIntStateOf(1)
    var held = false
    var wx = 0f          // distance travelled (left edge of the cube)
    var y = 0f           // height of the cube's bottom above the ground
    var vy = 0f
    var grounded = true
    var rot = 0f

    fun reset() {
        phase = 0; paused = false; pct = 0
        wx = 0f; y = 0f; vy = 0f; grounded = true; rot = 0f
    }

    fun press() {
        held = true
        if (phase == 0 && !paused) phase = 1
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        if (held && grounded) { vy = DASH_JUMP; grounded = false }
        val prevY = y
        vy -= DASH_GRAVITY * dt
        y += vy * dt
        wx += DASH_SPEED * dt
        var dead = false
        var support = 0f
        val l = wx + 0.1f
        val r = wx + 0.7f
        for (o in level) {
            if (o.x > wx + 3f) break               // sorted by x; nothing further ahead matters
            if (o.x + o.w < wx - 1f) continue
            if (o.kind == 0) {
                // spike: small box in the middle of its tile
                if (r > o.x + 0.3f && l < o.x + 0.7f && y + 0.1f < 0.5f) dead = true
            } else if (r > o.x + 0.05f && l < o.x + o.w - 0.05f && y < o.h - 0.001f) {
                if (prevY >= o.h - 0.15f && vy <= 0f) { y = o.h; vy = 0f } else dead = true
            }
        }
        if (y <= 0f) { y = 0f; if (vy < 0f) vy = 0f }
        // standing on the ground or on top of a block?
        var standing = y <= 0.001f
        if (!standing && vy <= 0f) {
            for (o in level) {
                if (o.kind != 1) continue
                if (r > o.x + 0.05f && l < o.x + o.w - 0.05f && abs(y - o.h) < 0.002f) { standing = true; break }
            }
        }
        grounded = standing
        if (grounded) rot = (rot / 90f).roundToInt() * 90f else rot += 420f * dt
        pct = ((wx / DASH_LENGTH) * 100f).toInt().coerceIn(0, 100)
        if (dead) { phase = 2; return }
        if (wx >= DASH_LENGTH) { pct = 100; phase = 3 }
    }
}

@Composable
fun DashGame(store: Store, onBack: () -> Unit) {
    val s = remember { DashState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_dash")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) { s.paused = true; s.held = false } }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if ((s.phase == 2 || s.phase == 3) && s.pct > best) { best = s.pct.toLong(); store.putLong("hs_dash", best) }
    }
    GameScaffold(
        title = tr("Beat Dash"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true; s.held = false }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF10102A))) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        s.press()
                        try { tryAwaitRelease() } finally { s.held = false }
                    })
                }
            ) {
                if (tick.longValue < 0) return@Canvas
                drawDash(s)
            }
            Column(Modifier.align(Alignment.TopCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.pct}%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(trf("Attempt %d", s.attempt), color = Color(0xFFB8C0D8), fontSize = 13.sp)
            }
            when {
                s.phase == 0 -> Text(
                    tr("Hold to jump. Time it with the beat."), color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                )
                s.phase == 2 -> GameOverlay(
                    tr("Crashed!"), listOf(trf("Progress: %d%%", s.pct), trf("Best: %d%%", best)),
                    tr("Try again"), { s.attempt++; s.reset() }, onBack, tr("Back to games")
                )
                s.phase == 3 -> GameOverlay(
                    tr("Level complete!"), listOf(trf("Attempts: %d", s.attempt), trf("Best: %d%%", best)),
                    tr("Play again"), { s.attempt = 1; s.reset() }, onBack, tr("Back to games")
                )
                s.paused -> GameOverlay(tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit"))
            }
        }
    }
}

private fun DrawScope.drawDash(s: DashState) {
    val w = size.width
    val h = size.height
    val tile = h / 9f
    val groundY = h * 0.78f
    val playerScreenX = w * 0.25f
    val cam = s.wx - playerScreenX / tile          // world x at the left edge of the screen
    fun sx(x: Float) = (x - cam) * tile

    // the beat: 1 right on the beat, fading out over the next half beat
    val phaseInBeat = (s.wx / DASH_BEAT) % 1f
    val pulse = (1f - phaseInBeat * 2f).coerceAtLeast(0f)
    val prog = s.wx / DASH_LENGTH
    val hue = Color(
        red = 0.06f + 0.30f * prog,
        green = 0.06f + 0.05f * (1f - prog),
        blue = 0.20f + 0.20f * (1f - prog) + 0.10f * pulse,
        alpha = 1f
    )
    drawRect(hue)
    // slow parallax columns
    for (i in -1..6) {
        val bx = ((i * 4f - (cam * 0.35f) % 4f)) * tile + w * 0.05f
        drawRect(Color(0x10FFFFFF), Offset(bx, groundY - tile * (2f + (i and 3))), Size(tile * 1.6f, tile * (2f + (i and 3))))
    }
    // ground
    drawRect(Color(0xFF0A0A1C), Offset(0f, groundY), Size(w, h - groundY))
    drawLine(Color(0xFF3DDCFF).copy(alpha = 0.5f + 0.5f * pulse), Offset(0f, groundY), Offset(w, groundY), 3f + 4f * pulse)
    var gx = -(((cam * tile) % tile + tile) % tile)
    while (gx < w) {
        drawLine(Color(0x22FFFFFF), Offset(gx, groundY), Offset(gx - tile * 0.5f, h), 1f)
        gx += tile
    }

    // obstacles
    for (o in s.level) {
        val x0 = sx(o.x)
        if (x0 > w + tile) break
        if (x0 + o.w * tile < -tile) continue
        if (o.kind == 0) {
            val p = Path().apply {
                moveTo(x0 + 0.05f * tile, groundY)
                lineTo(x0 + 0.5f * tile, groundY - 0.85f * tile)
                lineTo(x0 + 0.95f * tile, groundY)
                close()
            }
            drawPath(p, Color(0xFFFF4D6D))
            drawPath(p, Color(0xFFFFB3C1), style = Stroke(width = 2f))
        } else {
            val top = groundY - o.h * tile
            drawRoundRect(Color(0xFF2B2F6B), Offset(x0, top), Size(o.w * tile, o.h * tile), CornerRadius(tile * 0.08f))
            drawRoundRect(
                Color(0xFF7C5CFF), Offset(x0, top), Size(o.w * tile, o.h * tile), CornerRadius(tile * 0.08f),
                style = Stroke(width = 3f)
            )
            drawRect(Color(0xFFB9A8FF), Offset(x0, top), Size(o.w * tile, tile * 0.1f))
        }
    }

    // finish line
    val fx = sx(DASH_LENGTH)
    if (fx < w) drawRect(Color(0xFFFFD60A), Offset(fx, 0f), Size(tile * 0.3f, groundY))

    // the cube
    val size0 = 0.8f * tile
    val cx = playerScreenX + 0.4f * tile
    val cy = groundY - s.y * tile - 0.4f * tile
    if (s.phase == 2) {
        // crash: burst of shards where the cube was
        for (i in 0 until 8) {
            val a = i * 0.785f
            val d = tile * 0.9f
            drawRect(
                Color(0xFF4DD4AC), Offset(cx + kotlin.math.cos(a) * d - 0.12f * tile, cy + kotlin.math.sin(a) * d - 0.12f * tile),
                Size(0.24f * tile, 0.24f * tile)
            )
        }
    } else {
        rotate(s.rot, Offset(cx, cy)) {
            drawRoundRect(Color(0xFF4DD4AC), Offset(cx - size0 / 2f, cy - size0 / 2f), Size(size0, size0), CornerRadius(tile * 0.1f))
            drawRoundRect(
                Color(0xFFD6FFF2), Offset(cx - size0 / 2f, cy - size0 / 2f), Size(size0, size0), CornerRadius(tile * 0.1f),
                style = Stroke(width = 3f)
            )
            drawRect(Color(0xFF0E0A1F), Offset(cx - 0.22f * tile, cy - 0.16f * tile), Size(0.12f * tile, 0.12f * tile))
            drawRect(Color(0xFF0E0A1F), Offset(cx + 0.10f * tile, cy - 0.16f * tile), Size(0.12f * tile, 0.12f * tile))
        }
    }
}
