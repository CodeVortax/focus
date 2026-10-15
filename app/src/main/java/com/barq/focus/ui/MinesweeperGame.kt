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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.delay

private const val MINE_N = 9
private const val MINE_COUNT = 10
private val MINE_NUM_COLORS = listOf(
    Color.Transparent, Color(0xFF1E88E5), Color(0xFF2E9E5B), Color(0xFFE53935), Color(0xFF3949AB),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFF424242), Color(0xFF757575)
)

private class MineState {
    val st = mutableStateListOf(*Array(MINE_N * MINE_N) { 0 })     // 0 hidden, 1 open, 2 flag
    var mine = BooleanArray(MINE_N * MINE_N)
    var adj = IntArray(MINE_N * MINE_N)
    var phase by mutableIntStateOf(0)                               // 0 ready, 1 playing, 2 won, 3 lost
    var seconds by mutableIntStateOf(0)
    var flags by mutableIntStateOf(0)
    var flagMode by mutableStateOf(false)

    fun reset() {
        for (i in st.indices) st[i] = 0
        mine = BooleanArray(MINE_N * MINE_N); adj = IntArray(MINE_N * MINE_N)
        phase = 0; seconds = 0; flags = 0; flagMode = false
    }

    private fun neighbors(i: Int): List<Int> {
        val r = i / MINE_N
        val c = i % MINE_N
        val out = ArrayList<Int>(8)
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until MINE_N && nc in 0 until MINE_N) out.add(nr * MINE_N + nc)
        }
        return out
    }

    /** Mines go down on the first tap, never on or next to it. */
    private fun place(safe: Int) {
        val banned = neighbors(safe) + safe
        val picks = (0 until MINE_N * MINE_N).filter { it !in banned }.shuffled().take(MINE_COUNT)
        for (p in picks) mine[p] = true
        for (i in mine.indices) adj[i] = neighbors(i).count { mine[it] }
    }

    fun toggleFlag(i: Int) {
        if (phase >= 2 || st[i] == 1) return
        if (st[i] == 2) { st[i] = 0; flags-- } else { st[i] = 2; flags++ }
    }

    fun tap(i: Int) {
        if (phase >= 2) return
        if (flagMode) { toggleFlag(i); return }
        if (st[i] == 2) return
        if (st[i] == 1) { chord(i); return }
        if (phase == 0) { place(i); phase = 1 }
        reveal(i)
    }

    private fun chord(i: Int) {
        if (adj[i] == 0) return
        val nb = neighbors(i)
        if (nb.count { st[it] == 2 } != adj[i]) return
        for (j in nb) if (st[j] == 0) { reveal(j); if (phase >= 2) return }
    }

    private fun reveal(start: Int) {
        if (mine[start]) { lose(); return }
        val stack = ArrayList<Int>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeAt(stack.size - 1)
            if (st[i] != 0) continue
            st[i] = 1
            if (adj[i] == 0) for (j in neighbors(i)) if (st[j] == 0) stack.add(j)
        }
        if (st.count { it == 1 } == MINE_N * MINE_N - MINE_COUNT) {
            for (i in mine.indices) if (mine[i]) st[i] = 2
            flags = MINE_COUNT
            phase = 2
        }
    }

    private fun lose() { phase = 3 }
}

@Composable
fun MinesGame(store: Store, onBack: () -> Unit) {
    val s = remember { MineState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_mines")) }
    LaunchedEffect(s.phase) {
        while (s.phase == 1) { delay(1000); if (s.phase == 1) s.seconds++ }
        if (s.phase == 2) {
            val t = maxOf(1, s.seconds).toLong()
            if (best == 0L || t < best) { best = t; store.putLong("hs_mines", best) }
        }
    }
    GameScaffold(
        title = tr("Minesweeper"), onBack = onBack,
        actions = { TextButton(onClick = { s.reset() }) { Text(tr("New game")) } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreBox(tr("Mines"), "${MINE_COUNT - s.flags}", Modifier.weight(1f))
                    ScoreBox(tr("Time"), fmtTime(s.seconds), Modifier.weight(1f))
                    ScoreBox(tr("Best"), if (best > 0L) fmtTime(best.toInt()) else "-", Modifier.weight(1f))
                }
                Text(tr("Tap to dig. Long-press or use flag mode to mark a mine. Tap a number to clear around it."), textAlign = TextAlign.Center)
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0 until MINE_N) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (c in 0 until MINE_N) {
                                val i = r * MINE_N + c
                                val v = s.st[i]
                                val showMine = s.phase == 3 && s.mine[i]
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when {
                                                showMine -> MaterialTheme.colorScheme.errorContainer
                                                v == 1 -> MaterialTheme.colorScheme.surface
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            }
                                        )
                                        .pointerInput(i) {
                                            detectTapGestures(onTap = { s.tap(i) }, onLongPress = { s.toggleFlag(i) })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        showMine && v != 2 -> Text("💣", fontSize = 16.sp)
                                        v == 2 -> Text("🚩", fontSize = 16.sp)
                                        v == 1 && s.adj[i] > 0 -> Text(
                                            "${s.adj[i]}", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                            color = MINE_NUM_COLORS[s.adj[i]]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    if (s.flagMode) Button(onClick = { s.flagMode = false }) { Text("🚩 " + tr("Flag mode")) }
                    else OutlinedButton(onClick = { s.flagMode = true }) { Text("🚩 " + tr("Flag mode")) }
                }
            }
            if (s.phase == 2) GameOverlay(
                tr("Cleared!"), listOf(trf("Time: %s", fmtTime(s.seconds)), trf("Best time: %s", fmtTime(best.toInt()))),
                tr("Play again"), { s.reset() }, onBack, tr("Back to games")
            )
            if (s.phase == 3) GameOverlay(
                tr("Boom!"), listOf(tr("You hit a mine.")),
                tr("Try again"), { s.reset() }, onBack, tr("Back to games")
            )
        }
    }
}
