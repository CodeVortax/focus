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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Brick Breaker. Game idea and brick effects from GuillaumeFalourd/formulas-games (Apache-2.0); rewritten for touch.
// red (3 hits) > orange (2) > yellow (1); green = wide paddle, blue = shield, purple = fire ball, teal = big ball.

private const val BB_W = 320f
private const val BB_H = 520f
private const val BB_COLS = 10
private const val BB_ROWS = 12
private const val BB_BW = 32f
private const val BB_BH = 16f
private const val BB_TOP = 24f
private const val BB_PY = 490f
private const val BB_PH = 12f

private val BB_LEVELS = listOf(
    listOf("..........", "yyyyyyyyyy", "yygyyyygyy", "oooooooooo", "..........", "..yyyyyy.."),
    listOf("rrrrrrrrrr", "yyyyyyyyyy", "y.y.bb.y.y", "oooooooooo", "..tt..tt.."),
    listOf("....yy....", "...oooo...", "..oyyyyo..", ".rrrppprr.", "rrrrrrrrrr", "y.y.y.y.y."),
    listOf("g........g", "rbrbrbrbrb", "ypypypypyp", "tototototo", "..rrrrrr..", "...gyyg...")
)

private fun bbColor(ch: Char): Color = when (ch) {
    'r' -> Color(0xFFE74C3C)
    'g' -> Color(0xFF2ECC71)
    'b' -> Color(0xFF3498DB)
    't' -> Color(0xFF1ABC9C)
    'p' -> Color(0xFF9B59B6)
    'y' -> Color(0xFFF1C40F)
    else -> Color(0xFFE67E22)
}

private class BbState {
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 over, 3 level cleared
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var level by mutableIntStateOf(1)
    val kind = CharArray(BB_COLS * BB_ROWS) { '.' }
    val hp = IntArray(BB_COLS * BB_ROWS)
    var px = BB_W / 2f
    var pw = 64f
    var bx = BB_W / 2f
    var by = BB_PY - 7f
    var vx = 0f
    var vy = 0f
    var rad = 6f
    var launched = false
    var fire = 0f
    var tall = 0f
    var big = 0f
    var shield = false

    init { loadLevel(1) }

    private fun speedNow(): Float = minOf(420f, 250f + 22f * (level - 1))

    fun reset() {
        score = 0; lives = 3; paused = false
        px = BB_W / 2f
        loadLevel(1)
        phase = 0
    }

    fun loadLevel(n: Int) {
        level = n
        java.util.Arrays.fill(kind, '.')
        java.util.Arrays.fill(hp, 0)
        val rows = BB_LEVELS[(n - 1) % BB_LEVELS.size]
        for (r in rows.indices) {
            val row = rows[r].padEnd(BB_COLS, '.').take(BB_COLS)
            for (c in 0 until BB_COLS) {
                val ch = row[c]
                if (ch != '.') {
                    kind[r * BB_COLS + c] = ch
                    hp[r * BB_COLS + c] = when (ch) { 'r' -> 3; 'o' -> 2; else -> 1 }
                }
            }
        }
        resetBall()
    }

    private fun resetBall() {
        launched = false
        fire = 0f; tall = 0f; big = 0f; shield = false
        pw = 64f; rad = 6f
        bx = px; by = BB_PY - rad - 1f
        vx = 0f; vy = 0f
    }

    fun start() { if (phase == 0) phase = 1 }
    fun nextLevel() { loadLevel(level + 1); phase = 1 }

    fun moveTo(x: Float) { px = x.coerceIn(pw / 2f, BB_W - pw / 2f) }

    fun launch() {
        if (phase != 1 || paused || launched) return
        launched = true
        val a = (Math.random().toFloat() - 0.5f) * 0.5f
        val sp = speedNow()
        vx = sin(a) * sp
        vy = -cos(a) * sp
    }

    private fun remaining(): Int {
        var n = 0
        for (k in kind) if (k != '.') n++
        return n
    }

    private fun loseBall() {
        lives--
        if (lives <= 0) phase = 2 else resetBall()
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        if (fire > 0f) fire = maxOf(0f, fire - dt)
        if (tall > 0f) tall = maxOf(0f, tall - dt)
        if (big > 0f) big = maxOf(0f, big - dt)
        pw = if (tall > 0f) 104f else 64f
        rad = if (big > 0f) 11f else 6f
        px = px.coerceIn(pw / 2f, BB_W - pw / 2f)
        if (!launched) { bx = px; by = BB_PY - rad - 1f; return }
        val sp = speedNow()
        val len = sqrt(vx * vx + vy * vy)
        if (len > 0.001f) { vx = vx / len * sp; vy = vy / len * sp }
        val n = maxOf(1, ceil(sp * dt / 3f).toInt())
        for (i in 0 until n) {
            bx += vx * dt / n
            by += vy * dt / n
            collide()
            if (phase != 1 || !launched) return
        }
    }

    private fun collide() {
        if (bx - rad < 0f) { bx = rad; vx = abs(vx) }
        if (bx + rad > BB_W) { bx = BB_W - rad; vx = -abs(vx) }
        if (by - rad < 0f) { by = rad; vy = abs(vy) }
        // paddle: the hit position decides the bounce angle
        if (vy > 0f && by + rad >= BB_PY && by - rad <= BB_PY + BB_PH &&
            bx >= px - pw / 2f - rad * 0.5f && bx <= px + pw / 2f + rad * 0.5f
        ) {
            val rel = ((bx - px) / (pw / 2f)).coerceIn(-1f, 1f)
            val a = rel * 1.22f
            val sp = sqrt(vx * vx + vy * vy)
            vx = sin(a) * sp
            vy = -cos(a) * sp
            by = BB_PY - rad
        }
        if (vy > 0f && by + rad >= BB_H - 4f && shield) {
            shield = false
            vy = -abs(vy)
            by = BB_H - 4f - rad
        } else if (by - rad > BB_H) {
            loseBall()
            return
        }
        for (i in kind.indices) {
            if (kind[i] == '.') continue
            val x0 = (i % BB_COLS) * BB_BW
            val y0 = BB_TOP + (i / BB_COLS) * BB_BH
            val x1 = x0 + BB_BW
            val y1 = y0 + BB_BH
            val dx = bx - bx.coerceIn(x0, x1)
            val dy = by - by.coerceIn(y0, y1)
            if (dx * dx + dy * dy > rad * rad) continue
            hit(i, x0, y0, x1, y1)
            break
        }
    }

    private fun hit(i: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
        val ch = kind[i]
        when (ch) {
            'g' -> tall = 5f
            'b' -> shield = true
            'p' -> fire = 5f
            't' -> big = 5f
        }
        if (fire <= 0f) {
            val ox = minOf(bx + rad - x0, x1 - (bx - rad))
            val oy = minOf(by + rad - y0, y1 - (by - rad))
            if (ox < oy) {
                vx = if (bx < (x0 + x1) / 2f) -abs(vx) else abs(vx)
            } else {
                vy = if (by < (y0 + y1) / 2f) -abs(vy) else abs(vy)
            }
        }
        hp[i] = hp[i] - 1
        if (hp[i] <= 0) {
            kind[i] = '.'
            score += 10
            if (remaining() == 0) { score += 50; phase = 3 }
        } else if (ch == 'r' || ch == 'o') {
            kind[i] = if (hp[i] == 2) 'o' else 'y'
        }
    }
}

@Composable
fun BrickGame(store: Store, onBack: () -> Unit) {
    val s = remember { BbState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_brick")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.paused = true }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if ((s.phase == 2 || s.phase == 3) && s.score > best) { best = s.score.toLong(); store.putLong("hs_brick", best) }
    }
    GameScaffold(
        title = tr("Brick Breaker"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F1224))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.score}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(trf("Level %d", s.level), color = Color(0xFFB8C0D8), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("♥".repeat(maxOf(0, s.lives)), color = Color(0xFFFF6B6B), fontSize = 18.sp)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures(onTap = { s.launch() }) }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { o -> s.moveTo(fieldX(o.x, size.width.toFloat(), size.height.toFloat(), BB_W, BB_H)) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        s.moveTo(fieldX(change.position.x, size.width.toFloat(), size.height.toFloat(), BB_W, BB_H))
                                    }
                                )
                            }
                    ) {
                        if (tick.longValue < 0) return@Canvas
                        drawBricks(s)
                    }
                    if (s.phase == 0) GameOverlay(
                        tr("Brick Breaker"), listOf(tr("Drag to move the paddle, tap to launch."), trf("Best: %d", best)),
                        tr("Start"), { s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 2) GameOverlay(
                        tr("Game over!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                        tr("Play again"), { s.reset(); s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 3) GameOverlay(
                        tr("Level cleared!"), listOf(trf("Score: %d", s.score)),
                        tr("Next level"), { s.nextLevel() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 1 && s.paused) GameOverlay(
                        tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit")
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBricks(s: BbState) {
    virtualField(BB_W, BB_H) {
        drawRect(Color(0xFF181B33), Offset.Zero, Size(BB_W, BB_H))
        for (i in s.kind.indices) {
            val ch = s.kind[i]
            if (ch == '.') continue
            val x = (i % BB_COLS) * BB_BW
            val y = BB_TOP + (i / BB_COLS) * BB_BH
            drawRoundRect(bbColor(ch), Offset(x + 1f, y + 1f), Size(BB_BW - 2f, BB_BH - 2f), CornerRadius(3f))
            drawRoundRect(Color(0x33FFFFFF), Offset(x + 2f, y + 2f), Size(BB_BW - 4f, 4f), CornerRadius(2f))
        }
        if (s.shield) drawRect(Color(0xFF3498DB), Offset(0f, BB_H - 4f), Size(BB_W, 4f))
        drawRoundRect(Color(0xFF9AA5C4), Offset(s.px - s.pw / 2f, BB_PY), Size(s.pw, BB_PH), CornerRadius(6f))
        val ballColor = if (s.fire > 0f) Color(0xFFB36BFF) else Color.White
        drawCircle(ballColor, s.rad, Offset(s.bx, s.by))
        if (s.fire > 0f) drawCircle(Color(0x66B36BFF), s.rad * 1.8f, Offset(s.bx, s.by), style = Stroke(width = 2f))
        drawRect(Color(0x33FFFFFF), Offset.Zero, Size(BB_W, BB_H), style = Stroke(width = 2f))
    }
}
