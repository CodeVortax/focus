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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// level index: 0 easy, 1 medium, 2 hard, 3 beginner (shown first in the menu)
private val MISTAKE_LIMIT = intArrayOf(5, 4, 3, 0)     // 0 = mistakes never end the game
private val HINT_MAX = intArrayOf(3, 3, 3, 9)
private val CLUES = intArrayOf(40, 32, 27, 55)          // hard may end up with a few more
private val MENU_ORDER = intArrayOf(3, 0, 1, 2)

// ---------- puzzle generation ----------

private fun boxOf(i: Int) = (i / 27) * 3 + (i % 9) / 3

/** Backtracking solver with the fewest-candidates-first rule. Counts solutions up to [limit]; with [rnd] it fills a random grid. */
private fun sdSearch(g: IntArray, r: IntArray, c: IntArray, b: IntArray, rnd: java.util.Random?, cnt: IntArray, limit: Int): Boolean {
    var best = -1
    var bestMask = 0
    var bestN = 10
    for (i in 0 until 81) {
        if (g[i] != 0) continue
        val m = (r[i / 9] or c[i % 9] or b[boxOf(i)]).inv() and 0x1FF
        val n = Integer.bitCount(m)
        if (n < bestN) {
            best = i; bestMask = m; bestN = n
            if (n <= 1) break
        }
    }
    if (best == -1) { cnt[0]++; return cnt[0] >= limit }
    if (bestN == 0) return false
    var digits = (0..8).filter { ((bestMask shr it) and 1) == 1 }
    if (rnd != null) digits = digits.shuffled(rnd)
    val row = best / 9
    val col = best % 9
    val box = boxOf(best)
    for (d in digits) {
        val bit = 1 shl d
        g[best] = d + 1
        r[row] = r[row] or bit; c[col] = c[col] or bit; b[box] = b[box] or bit
        if (sdSearch(g, r, c, b, rnd, cnt, limit)) return true
        g[best] = 0
        r[row] = r[row] and bit.inv(); c[col] = c[col] and bit.inv(); b[box] = b[box] and bit.inv()
    }
    return false
}

private fun sdRun(grid: IntArray, rnd: java.util.Random?, limit: Int): Int {
    val r = IntArray(9); val c = IntArray(9); val b = IntArray(9)
    for (i in 0 until 81) {
        val v = grid[i]
        if (v == 0) continue
        val bit = 1 shl (v - 1)
        r[i / 9] = r[i / 9] or bit; c[i % 9] = c[i % 9] or bit; b[boxOf(i)] = b[boxOf(i)] or bit
    }
    val cnt = IntArray(1)
    sdSearch(grid, r, c, b, rnd, cnt, limit)
    return cnt[0]
}

private class SdPuzzle(val given: IntArray, val solution: IntArray)

private fun makePuzzle(diff: Int): SdPuzzle {
    val rnd = java.util.Random()
    val sol = IntArray(81)
    sdRun(sol, rnd, 1)
    val puz = sol.copyOf()
    var clues = 81
    for (idx in (0 until 81).shuffled(rnd)) {
        if (clues <= CLUES[diff]) break
        val keep = puz[idx]
        puz[idx] = 0
        // the puzzle must keep exactly one solution
        if (sdRun(puz.copyOf(), null, 2) != 1) puz[idx] = keep else clues--
    }
    return SdPuzzle(puz, sol)
}

// ---------- game state ----------

private class SdState {
    var phase by mutableIntStateOf(0)           // 0 menu, 2 playing, 3 solved, 4 failed
    var diff by mutableIntStateOf(0)
    var paused by mutableStateOf(false)
    var seconds by mutableIntStateOf(0)
    var mistakes by mutableIntStateOf(0)
    var hints by mutableIntStateOf(0)
    var notesMode by mutableStateOf(false)
    var sel by mutableIntStateOf(-1)
    var rev by mutableIntStateOf(0)             // bumped on every board change so cells redraw
    var given = IntArray(81)
    var sol = IntArray(81)
    val cur = IntArray(81)
    val notes = IntArray(81)                    // bit d-1 set = digit d pencilled in

    fun load(p: SdPuzzle, d: Int) {
        given = p.given; sol = p.solution; diff = d
        for (i in 0 until 81) { cur[i] = given[i]; notes[i] = 0 }
        seconds = 0; mistakes = 0; hints = 0; sel = -1; notesMode = false; paused = false
        rev++
        phase = 2
    }

    fun isGiven(i: Int) = given[i] != 0
    fun isWrong(i: Int) = cur[i] != 0 && cur[i] != sol[i]
    fun remaining(d: Int): Int { var n = 9; for (i in 0 until 81) if (cur[i] == d && sol[i] == d) n--; return n }

    private fun clearPeerNotes(i: Int, d: Int) {
        val bit = 1 shl (d - 1)
        for (j in 0 until 81) {
            if (j / 9 == i / 9 || j % 9 == i % 9 || boxOf(j) == boxOf(i)) notes[j] = notes[j] and bit.inv()
        }
    }

    private fun checkSolved() {
        for (i in 0 until 81) if (cur[i] != sol[i]) return
        phase = 3
    }

    fun enter(d: Int) {
        if (phase != 2 || paused) return
        val i = sel
        if (i < 0 || isGiven(i) || cur[i] == sol[i]) return
        if (notesMode) {
            if (cur[i] == 0) notes[i] = notes[i] xor (1 shl (d - 1))
        } else {
            cur[i] = d
            notes[i] = 0
            if (d == sol[i]) { clearPeerNotes(i, d); checkSolved() }
            else { mistakes++; val lim = MISTAKE_LIMIT[diff]; if (lim > 0 && mistakes >= lim) phase = 4 }
        }
        rev++
    }

    fun erase() {
        if (phase != 2 || paused) return
        val i = sel
        if (i < 0 || isGiven(i) || cur[i] == sol[i]) return
        cur[i] = 0; notes[i] = 0
        rev++
    }

    fun hint() {
        if (phase != 2 || paused || hints >= HINT_MAX[diff]) return
        var i = sel
        if (i < 0 || isGiven(i) || cur[i] == sol[i]) {
            i = (0 until 81).firstOrNull { cur[it] != sol[it] } ?: return
            sel = i
        }
        cur[i] = sol[i]; notes[i] = 0
        hints++
        clearPeerNotes(i, sol[i])
        checkSolved()
        rev++
    }
}

// ---------- UI ----------

@Composable
fun SudokuGame(store: Store, onBack: () -> Unit) {
    val s = remember { SdState() }
    var generating by remember { mutableIntStateOf(-1) }
    var bests by remember { mutableStateOf((0..3).map { store.getLong("sudoku_best_$it") }) }
    var newBest by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 2) s.paused = true }
    LaunchedEffect(s.phase, s.paused) {
        while (s.phase == 2 && !s.paused) { delay(1000); if (s.phase == 2 && !s.paused) s.seconds++ }
    }
    LaunchedEffect(generating) {
        if (generating in 0..3) {
            val d = generating
            val p = withContext(Dispatchers.Default) { makePuzzle(d) }
            s.load(p, d)
            newBest = false
            generating = -1
        }
    }
    LaunchedEffect(s.phase) {
        if (s.phase == 3) {
            val old = store.getLong("sudoku_best_${s.diff}")
            if (old == 0L || s.seconds < old) {
                store.putLong("sudoku_best_${s.diff}", s.seconds.toLong())
                newBest = true
                bests = (0..3).map { store.getLong("sudoku_best_$it") }
            }
        }
    }

    val names = listOf(tr("Easy"), tr("Medium"), tr("Hard"), tr("Beginner"))
    GameScaffold(
        title = tr("Sudoku"), onBack = onBack,
        actions = { if (s.phase == 2 && !s.paused) IconButton(onClick = { s.paused = true }) { Icon(Icons.Default.Pause, "Pause") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (s.phase == 0) {
                SudokuMenu(names, bests, generating) { if (generating < 0) generating = it }
            } else {
                SudokuBoard(s, names[s.diff])
                when {
                    s.phase == 3 -> GameOverlay(
                        tr("Solved!"),
                        listOf(trf("Time: %s", fmtTime(s.seconds)), if (newBest) tr("New best time!") else trf("Best time: %s", fmtTime(bests[s.diff].toInt()))),
                        tr("New puzzle"), { generating = s.diff }, onBack, tr("Back to games")
                    )
                    s.phase == 4 -> GameOverlay(
                        tr("Too many mistakes"), listOf(trf("Mistakes: %s", "${MISTAKE_LIMIT[s.diff]}/${MISTAKE_LIMIT[s.diff]}")),
                        tr("Try a new puzzle"), { generating = s.diff }, onBack, tr("Back to games")
                    )
                    s.paused -> GameOverlay(tr("Paused"), emptyList(), tr("Resume"), { s.paused = false }, onBack, tr("Quit"))
                }
                if (generating >= 0) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SudokuMenu(names: List<String>, bests: List<Long>, generating: Int, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tr("Choose a level"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        for (d in MENU_ORDER) {
            Card(Modifier.fillMaxWidth().clickable { onPick(d) }) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(names[d], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (d == 3) Text(tr("Many numbers filled in. Mistakes never end the game."), style = MaterialTheme.typography.bodySmall)
                        if (bests[d] > 0L) Text(trf("Best time: %s", fmtTime(bests[d].toInt())), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (generating == d) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun SudokuBoard(s: SdState, levelName: String) {
    val hidden = s.paused
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(levelName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(trf("Mistakes: %s", if (MISTAKE_LIMIT[s.diff] > 0) "${s.mistakes}/${MISTAKE_LIMIT[s.diff]}" else "${s.mistakes}"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(fmtTime(s.seconds), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        BoxWithConstraints(Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1f)) {
            val digitSp = (maxWidth.value / 9f * 0.58f).sp
            val noteSp = (maxWidth.value / 9f * 0.25f).sp
            val scheme = MaterialTheme.colorScheme
            val rev = s.rev
            val selV = if (s.sel >= 0) s.cur[s.sel] else 0
            Column(Modifier.fillMaxSize().background(scheme.surface)) {
                for (row in 0 until 9) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 9) {
                            val i = row * 9 + col
                            val sel = s.sel
                            val peer = sel >= 0 && (sel / 9 == row || sel % 9 == col || boxOf(sel) == boxOf(i))
                            val same = selV != 0 && s.cur[i] == selV
                            val bg = when {
                                i == sel -> scheme.primaryContainer
                                same && !hidden -> scheme.tertiaryContainer
                                peer -> scheme.secondaryContainer.copy(alpha = 0.55f)
                                else -> Color.Transparent
                            }
                            Box(
                                Modifier.weight(1f).fillMaxHeight().background(bg).clickable { s.sel = i },
                                contentAlignment = Alignment.Center
                            ) {
                                if (rev >= 0 && !hidden) {
                                    val v = s.cur[i]
                                    if (v != 0) {
                                        Text(
                                            "$v", fontSize = digitSp,
                                            fontWeight = if (s.isGiven(i)) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                s.isWrong(i) -> scheme.error
                                                s.isGiven(i) -> scheme.onSurface
                                                else -> scheme.primary
                                            }
                                        )
                                    } else if (s.notes[i] != 0) {
                                        Column(Modifier.fillMaxSize()) {
                                            for (nr in 0 until 3) {
                                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                                    for (nc in 0 until 3) {
                                                        val d = nr * 3 + nc + 1
                                                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                            if ((s.notes[i] shr (d - 1)) and 1 == 1) {
                                                                Text("$d", fontSize = noteSp, color = scheme.onSurfaceVariant)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val lineColor = scheme.onSurface
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width / 9f
                val ch = size.height / 9f
                for (k in 0..9) {
                    val thick = k % 3 == 0
                    val sw = if (thick) 2.5.dp.toPx() else 0.6.dp.toPx()
                    val a = if (thick) 0.9f else 0.25f
                    drawLine(lineColor.copy(alpha = a), Offset(k * cw, 0f), Offset(k * cw, size.height), sw)
                    drawLine(lineColor.copy(alpha = a), Offset(0f, k * ch), Offset(size.width, k * ch), sw)
                }
            }
        }

        Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(
                "✏️ " + tr("Notes") + (if (s.notesMode) " ✓" else ""),
                Modifier.weight(1f), s.notesMode
            ) { s.notesMode = !s.notesMode }
            ToolButton("⌫ " + tr("Erase"), Modifier.weight(1f), false) { s.erase() }
            ToolButton("💡 " + tr("Hint") + " ${HINT_MAX[s.diff] - s.hints}", Modifier.weight(1f), false) { s.hint() }
        }

        Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 1..9) {
                val left = s.remaining(d)
                val enabled = left > 0 && s.rev >= 0
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
                        .clickable(enabled = enabled) { s.enter(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("$d", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (enabled) "$left" else "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, modifier: Modifier, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 13.sp, maxLines = 1) }
}
