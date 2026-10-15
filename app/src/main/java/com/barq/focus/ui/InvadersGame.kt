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
import kotlin.math.sin

// Space Invasion. Game idea from GuillaumeFalourd/formulas-games (Apache-2.0): a fleet that drops at every edge,
// a few bullets on screen at once, game over when the fleet reaches you. Drawn in code, no sprites.

private const val IV_W = 360f
private const val IV_H = 560f
private const val IV_SHIP_Y = 510f
private const val IV_SW = 34f
private const val IV_COLS = 8
private const val IV_ROWS = 4
private const val IV_DX = 38f
private const val IV_DY = 30f
private const val IV_AW = 26f
private const val IV_AH = 18f

private class IvBullet(var x: Float, var y: Float)

private class IvState {
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var wave by mutableIntStateOf(1)
    var shipX = IV_W / 2f
    var left = false
    var right = false
    var firing = false
    var fx = 0f
    var fy = 0f
    var dir = 1
    val alive = BooleanArray(IV_COLS * IV_ROWS)
    val bullets = ArrayList<IvBullet>()
    var cool = 0f
    var t = 0f
    val stars = FloatArray(80)

    init {
        val r = java.util.Random(7L)
        for (i in 0 until 40) { stars[i * 2] = r.nextFloat() * IV_W; stars[i * 2 + 1] = r.nextFloat() * IV_H }
        reset()
    }

    fun reset() {
        score = 0; wave = 1; shipX = IV_W / 2f; paused = false
        left = false; right = false; firing = false; cool = 0f
        newFleet()
        phase = 0
    }

    private fun newFleet() {
        java.util.Arrays.fill(alive, true)
        fx = (IV_W - (IV_COLS - 1) * IV_DX - IV_AW) / 2f
        fy = 50f + minOf(wave - 1, 5) * 10f
        dir = 1
        bullets.clear()
    }

    fun start() { if (phase == 0) phase = 1 }

    fun moveTo(x: Float) { shipX = x.coerceIn(IV_SW / 2f, IV_W - IV_SW / 2f) }

    fun fire() {
        if (phase != 1 || paused || bullets.size >= 3 || cool > 0f) return
        bullets.add(IvBullet(shipX, IV_SHIP_Y - 10f))
        cool = 0.2f
    }

    private fun hitAlien(b: IvBullet): Boolean {
        for (idx in alive.indices) {
            if (!alive[idx]) continue
            val x0 = fx + (idx % IV_COLS) * IV_DX
            val y0 = fy + (idx / IV_COLS) * IV_DY
            if (b.x + 3f > x0 && b.x - 3f < x0 + IV_AW && b.y > y0 && b.y < y0 + IV_AH) {
                alive[idx] = false
                score += 10
                return true
            }
        }
        return false
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        t += dt
        if (left) moveTo(shipX - 260f * dt)
        if (right) moveTo(shipX + 260f * dt)
        if (cool > 0f) cool -= dt
        if (firing) fire()

        var i = 0
        while (i < bullets.size) {
            val b = bullets[i]
            b.y -= 480f * dt
            if (b.y < -10f || hitAlien(b)) { bullets.removeAt(i); continue }
            i++
        }

        var count = 0
        var minC = IV_COLS
        var maxC = -1
        var maxRow = -1
        for (idx in alive.indices) {
            if (!alive[idx]) continue
            count++
            val c = idx % IV_COLS
            val r = idx / IV_COLS
            if (c < minC) minC = c
            if (c > maxC) maxC = c
            if (r > maxRow) maxRow = r
        }
        if (count == 0) { wave++; newFleet(); return }

        // the fewer aliens are left, the faster the fleet moves
        val speed = (30f + 9f * (wave - 1)) * (1f + 1.6f * (1f - count / (IV_COLS * IV_ROWS).toFloat()))
        fx += dir * speed * dt
        val lx = fx + minC * IV_DX
        val rx = fx + maxC * IV_DX + IV_AW
        if (dir > 0 && rx > IV_W - 6f) { dir = -1; fy += 14f; fx -= rx - (IV_W - 6f) }
        else if (dir < 0 && lx < 6f) { dir = 1; fy += 14f; fx += 6f - lx }
        if (fy + maxRow * IV_DY + IV_AH >= IV_SHIP_Y) phase = 2
    }
}

@Composable
fun InvadersGame(store: Store, onBack: () -> Unit) {
    val s = remember { IvState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_invaders")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (s.phase == 1) { s.paused = true; s.left = false; s.right = false; s.firing = false }
    }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_invaders", best) }
    }
    GameScaffold(
        title = tr("Space Invasion"), onBack = onBack,
        actions = {
            if (s.phase == 1) IconButton(onClick = { s.paused = true; s.left = false; s.right = false; s.firing = false }) {
                Icon(Icons.Default.Pause, "Pause")
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF070A1A))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.score}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(trf("Wave %d", s.wave), color = Color(0xFFB8C0D8), fontSize = 14.sp)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> s.moveTo(fieldX(o.x, size.width.toFloat(), size.height.toFloat(), IV_W, IV_H)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    s.moveTo(fieldX(change.position.x, size.width.toFloat(), size.height.toFloat(), IV_W, IV_H))
                                }
                            )
                        }
                    ) {
                        if (tick.longValue < 0) return@Canvas
                        drawInvaders(s)
                    }
                    if (s.phase == 0) GameOverlay(
                        tr("Space Invasion"),
                        listOf(tr("Drag or use the arrows to move. Hold FIRE to shoot."), trf("Best: %d", best)),
                        tr("Start"), { s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 2) GameOverlay(
                        tr("Invaded!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                        tr("Play again"), { s.reset(); s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 1 && s.paused) GameOverlay(
                        tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit")
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldButton("◀", Modifier.weight(1f), onDown = { s.left = true }, onUp = { s.left = false }, height = 80.dp, fontSize = 34.sp)
                    HoldButton("▶", Modifier.weight(1f), onDown = { s.right = true }, onUp = { s.right = false }, height = 80.dp, fontSize = 34.sp)
                    HoldButton(tr("FIRE"), Modifier.weight(1.6f), onDown = { s.firing = true; s.fire() }, onUp = { s.firing = false }, height = 80.dp, fontSize = 22.sp)
                }
            }
        }
    }
}

private val IV_ROW_COLORS = listOf(Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFF7CFF8A), Color(0xFF64D2FF))

private fun DrawScope.drawInvaders(s: IvState) {
    virtualField(IV_W, IV_H) {
        drawRect(Color(0xFF070A1A), Offset.Zero, Size(IV_W, IV_H))
        for (i in 0 until 40) {
            val a = 0.35f + 0.35f * sin(s.t * 2f + i)
            drawCircle(Color.White.copy(alpha = a.coerceIn(0.1f, 0.8f)), 1.2f, Offset(s.stars[i * 2], s.stars[i * 2 + 1]))
        }
        val step = (s.t * 2f).toInt() % 2
        for (idx in s.alive.indices) {
            if (!s.alive[idx]) continue
            val x = s.fx + (idx % IV_COLS) * IV_DX
            val y = s.fy + (idx / IV_COLS) * IV_DY
            val col = IV_ROW_COLORS[(idx / IV_COLS) % IV_ROW_COLORS.size]
            drawRoundRect(col, Offset(x + 2f, y + 2f), Size(IV_AW - 4f, IV_AH - 6f), CornerRadius(5f))
            drawRect(col, Offset(x + 5f, y - 2f), Size(2f, 5f))
            drawRect(col, Offset(x + IV_AW - 7f, y - 2f), Size(2f, 5f))
            drawRect(Color(0xFF0A0A14), Offset(x + 7f, y + 5f), Size(4f, 4f))
            drawRect(Color(0xFF0A0A14), Offset(x + IV_AW - 11f, y + 5f), Size(4f, 4f))
            val lo = if (step == 0) 0f else 3f
            drawRect(col, Offset(x + 4f + lo, y + IV_AH - 4f), Size(4f, 4f))
            drawRect(col, Offset(x + IV_AW - 8f - lo, y + IV_AH - 4f), Size(4f, 4f))
        }
        for (b in s.bullets) drawRoundRect(Color(0xFFFFE066), Offset(b.x - 1.5f, b.y - 7f), Size(3f, 14f), CornerRadius(1.5f))
        val ship = Path().apply {
            moveTo(s.shipX, IV_SHIP_Y - 14f)
            lineTo(s.shipX - IV_SW / 2f, IV_SHIP_Y + 8f)
            lineTo(s.shipX + IV_SW / 2f, IV_SHIP_Y + 8f)
            close()
        }
        drawPath(ship, Color(0xFF64D2FF))
        drawCircle(Color(0xFF0B3C5D), 4f, Offset(s.shipX, IV_SHIP_Y))
    }
}
