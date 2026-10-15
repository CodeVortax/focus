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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlin.math.abs

private fun tile2048Bg(v: Int): Color = when (v) {
    0 -> Color(0xFFCDC1B4)
    2 -> Color(0xFFEEE4DA)
    4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    else -> Color(0xFFEDC22E)
}

private class State2048 {
    val cells = mutableStateListOf(*Array(16) { 0 })
    var score by mutableIntStateOf(0)
    var over by mutableStateOf(false)
    var won by mutableStateOf(false)
    var keepGoing = false
    private val rnd = java.util.Random()

    init { reset() }

    fun reset() {
        for (i in 0 until 16) cells[i] = 0
        score = 0; over = false; won = false; keepGoing = false
        addTile(); addTile()
    }

    private fun addTile() {
        val empty = (0 until 16).filter { cells[it] == 0 }
        if (empty.isEmpty()) return
        cells[empty[rnd.nextInt(empty.size)]] = if (rnd.nextInt(10) == 0) 4 else 2
    }

    private fun canMove(): Boolean {
        for (i in 0 until 16) {
            if (cells[i] == 0) return true
            if (i % 4 < 3 && cells[i] == cells[i + 1]) return true
            if (i < 12 && cells[i] == cells[i + 4]) return true
        }
        return false
    }

    /** dir: 0 left, 1 right, 2 up, 3 down */
    fun move(dir: Int) {
        if (over) return
        var moved = false
        var gained = 0
        for (k in 0..3) {
            val idx = IntArray(4) { j ->
                when (dir) {
                    0 -> k * 4 + j
                    1 -> k * 4 + (3 - j)
                    2 -> j * 4 + k
                    else -> (3 - j) * 4 + k
                }
            }
            val vals = idx.map { cells[it] }.filter { it != 0 }
            val out = ArrayList<Int>()
            var p = 0
            while (p < vals.size) {
                if (p + 1 < vals.size && vals[p] == vals[p + 1]) { out.add(vals[p] * 2); gained += vals[p] * 2; p += 2 }
                else { out.add(vals[p]); p++ }
            }
            for (j in 0..3) {
                val nv = if (j < out.size) out[j] else 0
                if (cells[idx[j]] != nv) { cells[idx[j]] = nv; moved = true }
            }
        }
        if (!moved) return
        score += gained
        addTile()
        if (!keepGoing && cells.any { it >= 2048 }) won = true
        if (!canMove()) over = true
    }
}

@Composable
fun Game2048(store: Store, onBack: () -> Unit) {
    val s = remember { State2048() }
    var best by remember { mutableLongStateOf(store.getLong("hs_2048")) }
    LaunchedEffect(s.score) {
        if (s.score > best) { best = s.score.toLong(); store.putLong("hs_2048", best) }
    }
    GameScaffold(
        title = "2048", onBack = onBack,
        actions = { TextButton(onClick = { s.reset() }) { Text(tr("New game")) } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreBox(tr("Score"), "${s.score}", Modifier.weight(1f))
                    ScoreBox(tr("Best"), "$best", Modifier.weight(1f))
                }
                Text(tr("Swipe to slide the tiles. Merge equal numbers to reach 2048."), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Box(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xFFBBADA0)).padding(6.dp)
                        .pointerInput(Unit) {
                            var ax = 0f
                            var ay = 0f
                            var fired = false
                            val th = 32.dp.toPx()
                            detectDragGestures(
                                onDragStart = { ax = 0f; ay = 0f; fired = false },
                                onDrag = { change, d ->
                                    change.consume()
                                    if (!fired) {
                                        ax += d.x; ay += d.y
                                        if (abs(ax) > th || abs(ay) > th) {
                                            fired = true
                                            if (abs(ax) >= abs(ay)) s.move(if (ax > 0) 1 else 0) else s.move(if (ay > 0) 3 else 2)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in 0 until 4) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until 4) {
                                    val v = s.cells[r * 4 + c]
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(tile2048Bg(v)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (v != 0) Text(
                                            "$v", fontWeight = FontWeight.Bold,
                                            fontSize = when { v < 100 -> 32.sp; v < 1000 -> 26.sp; else -> 20.sp },
                                            color = if (v <= 4) Color(0xFF776E65) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (s.won) GameOverlay(
                tr("You made 2048!"), listOf(trf("Score: %d", s.score)),
                tr("Keep going"), { s.won = false; s.keepGoing = true }, { s.reset() }, tr("New game")
            ) else if (s.over) GameOverlay(
                tr("No moves left"), listOf(trf("Score: %d", s.score), trf("Best: %d", best)),
                tr("Play again"), { s.reset() }, onBack, tr("Back to games")
            )
        }
    }
}

@Composable
internal fun ScoreBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
