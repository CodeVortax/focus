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

private const val COLS = 10
private const val ROWS = 20

// colors: I, O, T, S, Z, J, L
private val PIECE_COLORS = listOf(
    Color(0xFF3DDCFF), Color(0xFFFFD60A), Color(0xFFB57CFF), Color(0xFF4DD4AC),
    Color(0xFFFF4D6D), Color(0xFF4D7CFF), Color(0xFFFF9F43)
)

// each piece is a flat list of (row, col) pairs inside an n x n box
private val SHAPES = arrayOf(
    intArrayOf(1, 0, 1, 1, 1, 2, 1, 3),   // I
    intArrayOf(0, 0, 0, 1, 1, 0, 1, 1),   // O
    intArrayOf(0, 1, 1, 0, 1, 1, 1, 2),   // T
    intArrayOf(0, 1, 0, 2, 1, 0, 1, 1),   // S
    intArrayOf(0, 0, 0, 1, 1, 1, 1, 2),   // Z
    intArrayOf(0, 0, 1, 0, 1, 1, 1, 2),   // J
    intArrayOf(0, 2, 1, 0, 1, 1, 1, 2)    // L
)
private val SIZES = intArrayOf(4, 2, 3, 3, 3, 3, 3)
private val LINE_POINTS = intArrayOf(0, 100, 300, 500, 800)

private fun rotatedCw(cells: IntArray, n: Int): IntArray {
    val out = IntArray(8)
    for (i in 0 until 4) {
        val r = cells[i * 2]
        val c = cells[i * 2 + 1]
        out[i * 2] = c
        out[i * 2 + 1] = n - 1 - r
    }
    return out
}

private class TetState {
    val board = Array(ROWS) { IntArray(COLS) }       // 0 empty, 1..7 = piece color + 1
    var phase by mutableIntStateOf(0)                 // 0 ready, 1 playing, 2 over
    var paused by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    var lines by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var next by mutableIntStateOf(0)

    var type = 0
    var cells = IntArray(8)
    var px = 0
    var py = 0
    private var fall = 0f
    var left = false
    var right = false
    var down = false
    private var rep = 0f
    private val bag = ArrayList<Int>()

    private fun draw(): Int {
        if (bag.isEmpty()) bag.addAll((0..6).shuffled())
        return bag.removeAt(bag.size - 1)
    }

    fun reset() {
        for (r in 0 until ROWS) java.util.Arrays.fill(board[r], 0)
        score = 0; lines = 0; level = 1
        fall = 0f; left = false; right = false; down = false; rep = 0f
        paused = false
        bag.clear()
        next = draw()
        spawn()
        phase = 0
    }

    fun start() { if (phase == 0) phase = 1 }

    private fun spawn() {
        type = next
        next = draw()
        cells = SHAPES[type].copyOf()
        px = (COLS - SIZES[type]) / 2
        py = if (type == 0) -1 else 0
        if (!fits(cells, px, py)) phase = 2
    }

    fun fits(c: IntArray, x: Int, y: Int): Boolean {
        for (i in 0 until 4) {
            val r = y + c[i * 2]
            val col = x + c[i * 2 + 1]
            if (col < 0 || col >= COLS || r >= ROWS) return false
            if (r >= 0 && board[r][col] != 0) return false
        }
        return true
    }

    fun tryMove(dx: Int, dy: Int): Boolean {
        if (fits(cells, px + dx, py + dy)) { px += dx; py += dy; return true }
        return false
    }

    fun rotate() {
        if (phase != 1 || paused || type == 1) return
        val rc = rotatedCw(cells, SIZES[type])
        for (k in intArrayOf(0, -1, 1, -2, 2)) {
            if (fits(rc, px + k, py)) { cells = rc; px += k; return }
        }
    }

    fun move(d: Int) { if (phase == 1 && !paused) tryMove(d, 0) }

    fun softStep() {
        if (phase != 1 || paused) return
        if (tryMove(0, 1)) score++
    }

    fun hardDrop() {
        if (phase != 1 || paused) return
        var d = 0
        while (tryMove(0, 1)) d++
        score += d * 2
        lock()
    }

    fun pressLeft() { left = true; rep = 0.15f; move(-1) }
    fun pressRight() { right = true; rep = 0.15f; move(1) }

    fun ghostDrop(): Int {
        var g = 0
        while (fits(cells, px, py + g + 1)) g++
        return g
    }

    private fun lock() {
        for (i in 0 until 4) {
            val r = py + cells[i * 2]
            val c = px + cells[i * 2 + 1]
            if (r in 0 until ROWS) board[r][c] = type + 1
        }
        val kept = board.filter { row -> row.any { it == 0 } }
        val n = ROWS - kept.size
        if (n > 0) {
            for (i in 0 until n) board[i] = IntArray(COLS)
            for (i in kept.indices) board[n + i] = kept[i]
            score += LINE_POINTS[n] * level
            lines += n
            level = lines / 10 + 1
        }
        fall = 0f
        spawn()
    }

    fun step(dt: Float) {
        if (phase != 1 || paused) return
        if (left != right) {
            rep -= dt
            if (rep <= 0f) { tryMove(if (left) -1 else 1, 0); rep += 0.06f }
        }
        var interval = (0.8f * Math.pow(0.82, (level - 1).toDouble()).toFloat()).coerceAtLeast(0.06f)
        if (down) interval = minOf(interval, 0.045f)
        fall += dt
        while (fall >= interval && phase == 1) {
            fall -= interval
            if (!tryMove(0, 1)) lock() else if (down) score++
        }
    }
}

@Composable
fun TetrisGame(store: Store, onBack: () -> Unit) {
    val s = remember { TetState().also { it.reset() } }
    var best by remember { mutableLongStateOf(store.getLong("hs_tetris")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.paused = true }
    val tick = GameLoop(running = s.phase == 1 && !s.paused, step = { s.step(it) })
    LaunchedEffect(s.phase) {
        if (s.phase == 2 && s.score > best) { best = s.score.toLong(); store.putLong("hs_tetris", best) }
    }
    GameScaffold(
        title = tr("Blocks"), onBack = onBack,
        actions = { if (s.phase == 1) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F1420))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.score}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(
                            trf("Lines: %d", s.lines) + "   " + trf("Level: %d", s.level),
                            color = Color(0xFFB8C0D8), fontSize = 14.sp
                        )
                    }
                    Canvas(Modifier.size(56.dp)) { drawNext(s) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { s.rotate() } }
                            .pointerInput(Unit) {
                                var ax = 0f
                                var ay = 0f
                                val cell = minOf(size.width / COLS.toFloat(), size.height / ROWS.toFloat())
                                detectDragGestures(
                                    onDragStart = { ax = 0f; ay = 0f },
                                    onDrag = { change, d ->
                                        change.consume()
                                        ax += d.x
                                        ay += d.y
                                        while (ax >= cell) { s.move(1); ax -= cell }
                                        while (ax <= -cell) { s.move(-1); ax += cell }
                                        while (ay >= cell) { s.softStep(); ay -= cell }
                                        if (ay < 0f) ay = 0f
                                    }
                                )
                            }
                    ) {
                        if (tick.longValue < 0) return@Canvas
                        drawBoard(s)
                    }
                    if (s.phase == 0) GameOverlay(
                        tr("Blocks"),
                        listOf(tr("Tap to rotate, drag to move. Clear lines and find the rhythm."), trf("Best: %d", best)),
                        tr("Start"), { s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 2) GameOverlay(
                        tr("Stacked out!"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                        tr("Play again"), { s.reset(); s.start() }, onBack, tr("Back to games")
                    )
                    if (s.phase == 1 && s.paused) GameOverlay(
                        tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit")
                    )
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoldButton("◀", Modifier.weight(1f), onDown = { s.pressLeft() }, onUp = { s.left = false }, height = 80.dp, fontSize = 34.sp)
                        HoldButton("▶", Modifier.weight(1f), onDown = { s.pressRight() }, onUp = { s.right = false }, height = 80.dp, fontSize = 34.sp)
                        HoldButton("⟳", Modifier.weight(1f), onDown = { s.rotate() }, height = 80.dp, fontSize = 34.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoldButton("▼", Modifier.weight(1f), onDown = { s.down = true }, onUp = { s.down = false }, height = 54.dp, fontSize = 26.sp)
                        HoldButton("⤓", Modifier.weight(1f), onDown = { s.hardDrop() }, height = 54.dp, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCell(x: Float, y: Float, size: Float, color: Color) {
    val pad = size * 0.06f
    drawRoundRect(color, Offset(x + pad, y + pad), Size(size - 2 * pad, size - 2 * pad), CornerRadius(size * 0.18f))
    drawRoundRect(
        Color(0x40FFFFFF), Offset(x + pad, y + pad), Size(size - 2 * pad, (size - 2 * pad) * 0.28f),
        CornerRadius(size * 0.18f)
    )
}

private fun DrawScope.drawBoard(s: TetState) {
    val cell = minOf(size.width / COLS, size.height / ROWS)
    val ox = (size.width - cell * COLS) / 2f
    val oy = (size.height - cell * ROWS) / 2f
    drawRoundRect(Color(0xFF171E30), Offset(ox, oy), Size(cell * COLS, cell * ROWS), CornerRadius(cell * 0.2f))
    for (i in 1 until COLS) drawLine(Color(0x14FFFFFF), Offset(ox + i * cell, oy), Offset(ox + i * cell, oy + cell * ROWS), 1f)
    for (i in 1 until ROWS) drawLine(Color(0x14FFFFFF), Offset(ox, oy + i * cell), Offset(ox + cell * COLS, oy + i * cell), 1f)
    for (r in 0 until ROWS) for (c in 0 until COLS) {
        val v = s.board[r][c]
        if (v != 0) drawCell(ox + c * cell, oy + r * cell, cell, PIECE_COLORS[v - 1])
    }
    if (s.phase == 2) return
    val col = PIECE_COLORS[s.type]
    val g = s.ghostDrop()
    for (i in 0 until 4) {
        val r = s.py + s.cells[i * 2]
        val c = s.px + s.cells[i * 2 + 1]
        val gr = r + g
        if (g > 0 && gr >= 0) {
            drawRoundRect(
                col.copy(alpha = 0.45f), Offset(ox + c * cell + cell * 0.1f, oy + gr * cell + cell * 0.1f),
                Size(cell * 0.8f, cell * 0.8f), CornerRadius(cell * 0.15f), style = Stroke(width = 2f)
            )
        }
    }
    for (i in 0 until 4) {
        val r = s.py + s.cells[i * 2]
        val c = s.px + s.cells[i * 2 + 1]
        if (r >= 0) drawCell(ox + c * cell, oy + r * cell, cell, col)
    }
}

private fun DrawScope.drawNext(s: TetState) {
    val unit = size.width / 4f
    val shape = SHAPES[s.next]
    val n = SIZES[s.next]
    val off = (4 - n) / 2f * unit
    for (i in 0 until 4) {
        drawCell(off + shape[i * 2 + 1] * unit, off + shape[i * 2] * unit, unit, PIECE_COLORS[s.next])
    }
}
