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

// Snake. Game idea from GuillaumeFalourd/formulas-games (Apache-2.0); rewritten for touch.

private const val SN_COLS = 16
private const val SN_ROWS = 24

private class SnState {
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    val body = ArrayList<Int>()                 // cell = y * SN_COLS + x, head first
    var food = 0
    private var dir = 1                         // 0 up, 1 right, 2 down, 3 left
    private val queue = ArrayList<Int>()        // turns waiting for the next steps
    private var acc = 0f

    init { reset() }

    fun reset() {
        body.clear()
        val y = SN_ROWS / 2
        for (i in 0 until 3) body.add(y * SN_COLS + (SN_COLS / 2 - i))
        dir = 1; queue.clear(); acc = 0f; score = 0; paused = false
        placeFood()
        phase = 0
    }

    fun start() { if (phase == 0) phase = 1 }

    private fun placeFood() {
        val used = BooleanArray(SN_COLS * SN_ROWS)
        for (c in body) used[c] = true
        val free = ArrayList<Int>()
        for (c in used.indices) if (!used[c]) free.add(c)
        if (free.isEmpty()) { phase = 2; return }
        food = free[(Math.random() * free.size).toInt()]
    }

    fun turn(d: Int) {
        if (phase == 0) start()
        if (phase != 1 || paused) return
        val last = if (queue.isEmpty()) dir else queue[queue.size - 1]
        if (d == last || (d + 2) % 4 == last) return
        if (queue.size < 2) queue.add(d)
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        acc += dt
        val interval = maxOf(0.075f, 0.22f - 0.006f * score)
        while (acc >= interval && phase == 1) {
            acc -= interval
            if (queue.isNotEmpty()) dir = queue.removeAt(0)
            val hx = body[0] % SN_COLS
            val hy = body[0] / SN_COLS
            val nx = hx + (if (dir == 1) 1 else if (dir == 3) -1 else 0)
            val ny = hy + (if (dir == 2) 1 else if (dir == 0) -1 else 0)
            if (nx < 0 || nx >= SN_COLS || ny < 0 || ny >= SN_ROWS) { phase = 2; return }
            val cell = ny * SN_COLS + nx
            val eating = cell == food
            // the tail leaves its cell this step unless we are eating, so it is not a hit
            val limit = if (eating) body.size else body.size - 1
            for (i in 0 until limit) if (body[i] == cell) { phase = 2; return }
            body.add(0, cell)
            if (eating) { score++; placeFood() } else body.removeAt(body.size - 1)
        }
    }
}

@Composable
fun SnakeGame(store: Store, onBack: () -> Unit) {
    val s = remember { SnState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_snake")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.paused = true }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_snake", best) }
    }
    GameScaffold(
        title = tr("Snake"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0E1913))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.score}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(trf("Best: %d", best), color = Color(0xFFB8D8C0), fontSize = 14.sp)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            var ax = 0f
                            var ay = 0f
                            detectDragGestures(
                                onDragStart = { ax = 0f; ay = 0f },
                                onDrag = { change, d ->
                                    change.consume()
                                    ax += d.x
                                    ay += d.y
                                    if (abs(ax) > 36f || abs(ay) > 36f) {
                                        if (abs(ax) > abs(ay)) s.turn(if (ax > 0f) 1 else 3) else s.turn(if (ay > 0f) 2 else 0)
                                        ax = 0f; ay = 0f
                                    }
                                }
                            )
                        }
                    ) {
                        if (tick.longValue < 0) return@Canvas
                        drawSnake(s)
                    }
                    if (s.phase == 0) GameOverlay(
                        tr("Snake"), listOf(tr("Swipe or use the arrows to steer."), trf("Best: %d", best)),
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
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.weight(1f))
                        HoldButton("▲", Modifier.weight(1f), onDown = { s.turn(0) }, height = 60.dp, fontSize = 28.sp)
                        Spacer(Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HoldButton("◀", Modifier.weight(1f), onDown = { s.turn(3) }, height = 60.dp, fontSize = 28.sp)
                        HoldButton("▼", Modifier.weight(1f), onDown = { s.turn(2) }, height = 60.dp, fontSize = 28.sp)
                        HoldButton("▶", Modifier.weight(1f), onDown = { s.turn(1) }, height = 60.dp, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSnake(s: SnState) {
    val cell = minOf(size.width / SN_COLS, size.height / SN_ROWS)
    val ox = (size.width - cell * SN_COLS) / 2f
    val oy = (size.height - cell * SN_ROWS) / 2f
    drawRoundRect(Color(0xFF16291F), Offset(ox, oy), Size(cell * SN_COLS, cell * SN_ROWS), CornerRadius(cell * 0.3f))
    for (r in 0 until SN_ROWS) for (c in 0 until SN_COLS) {
        if ((r + c) % 2 == 0) drawRect(Color(0x0DFFFFFF), Offset(ox + c * cell, oy + r * cell), Size(cell, cell))
    }
    val fx = s.food % SN_COLS
    val fy = s.food / SN_COLS
    drawCircle(Color(0xFFE5484D), cell * 0.38f, Offset(ox + (fx + 0.5f) * cell, oy + (fy + 0.5f) * cell))
    val pad = cell * 0.07f
    for (i in s.body.indices.reversed()) {
        val c = s.body[i]
        val x = c % SN_COLS
        val y = c / SN_COLS
        val col = if (i == 0) Color(0xFF7CFF8A) else Color(0xFF2FBF5B)
        drawRoundRect(col, Offset(ox + x * cell + pad, oy + y * cell + pad), Size(cell - 2 * pad, cell - 2 * pad), CornerRadius(cell * 0.3f))
    }
}
