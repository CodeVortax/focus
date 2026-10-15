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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Milliseconds as seconds with one decimal, e.g. 12.3s. */
internal fun fmtMs(ms: Long) = String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)

// =====================================================================================
// Memory Match
// =====================================================================================

private val MEMORY_SYMBOLS = listOf("🍎", "🚀", "🎧", "🌵", "🦊", "⚽", "🎲", "🍕", "🌙", "🔥")

private class MemoryState {
    val face = mutableStateListOf<String>()
    val open = mutableStateListOf<Boolean>()
    val done = mutableStateListOf<Boolean>()
    var moves by mutableIntStateOf(0)
    var won by mutableStateOf(false)
    var first = -1
    var busy = false

    init { reset() }

    fun reset() {
        val syms = MEMORY_SYMBOLS.shuffled().take(8)
        val deck = (syms + syms).shuffled()
        face.clear(); face.addAll(deck)
        open.clear(); done.clear()
        for (i in 0 until 16) { open.add(false); done.add(false) }
        moves = 0; won = false; first = -1; busy = false
    }
}

@Composable
fun MemoryGame(store: Store, onBack: () -> Unit) {
    val s = remember { MemoryState() }
    val scope = rememberCoroutineScope()
    var best by remember { mutableLongStateOf(store.getLong("hs_memory")) }
    LaunchedEffect(s.won) {
        if (s.won && (best == 0L || s.moves < best)) { best = s.moves.toLong(); store.putLong("hs_memory", best) }
    }
    fun tap(i: Int) {
        if (s.busy || s.open[i] || s.done[i] || s.won) return
        s.open[i] = true
        if (s.first == -1) { s.first = i; return }
        val a = s.first
        s.first = -1
        s.moves++
        if (s.face[a] == s.face[i]) {
            s.done[a] = true; s.done[i] = true
            if (s.done.all { it }) s.won = true
        } else {
            s.busy = true
            scope.launch {
                delay(650)
                s.open[a] = false; s.open[i] = false
                s.busy = false
            }
        }
    }
    GameScaffold(
        title = tr("Memory Match"), onBack = onBack,
        actions = { TextButton(onClick = { s.reset() }) { Text(tr("New game")) } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreBox(tr("Moves"), "${s.moves}", Modifier.weight(1f))
                    ScoreBox(tr("Best"), if (best > 0L) "$best" else "-", Modifier.weight(1f))
                }
                Text(tr("Flip two cards at a time and find all the pairs."), textAlign = TextAlign.Center)
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until 4) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until 4) {
                                val i = r * 4 + c
                                val shown = s.open[i] || s.done[i]
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                s.done[i] -> MaterialTheme.colorScheme.tertiaryContainer
                                                shown -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                        .clickable { tap(i) },
                                    contentAlignment = Alignment.Center
                                ) { if (shown) Text(s.face[i], fontSize = 34.sp) }
                            }
                        }
                    }
                }
            }
            if (s.won) GameOverlay(
                tr("All pairs found!"), listOf(trf("Moves: %d", s.moves), trf("Best: %d", best)),
                tr("Play again"), { s.reset() }, onBack, tr("Back to games")
            )
        }
    }
}

// =====================================================================================
// Schulte Table
// =====================================================================================

private class SchulteState {
    val nums = mutableStateListOf<Int>()
    var next by mutableIntStateOf(1)
    var phase by mutableIntStateOf(0)           // 0 ready, 1 playing, 2 done
    var elapsed by mutableLongStateOf(0L)       // ms
    var wrong by mutableIntStateOf(-1)
    var errors by mutableIntStateOf(0)
    var startNs = 0L

    init { reset() }

    fun reset() {
        nums.clear(); nums.addAll((1..25).shuffled())
        next = 1; phase = 0; elapsed = 0L; wrong = -1; errors = 0
    }

    fun begin() { startNs = System.nanoTime(); phase = 1 }
}

@Composable
fun SchulteGame(store: Store, onBack: () -> Unit) {
    val s = remember { SchulteState() }
    val scope = rememberCoroutineScope()
    var best by remember { mutableLongStateOf(store.getLong("hs_schulte")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.reset() }
    LaunchedEffect(s.phase) {
        while (s.phase == 1) {
            s.elapsed = (System.nanoTime() - s.startNs) / 1_000_000L
            delay(50)
        }
        if (s.phase == 2 && (best == 0L || s.elapsed < best)) { best = s.elapsed; store.putLong("hs_schulte", best) }
    }
    GameScaffold(title = tr("Schulte Table"), onBack = onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreBox(tr("Find"), if (s.next <= 25) "${s.next}" else "✓", Modifier.weight(1f))
                    ScoreBox(tr("Time"), fmtMs(s.elapsed), Modifier.weight(1f))
                    ScoreBox(tr("Best"), if (best > 0L) fmtMs(best) else "-", Modifier.weight(1f))
                }
                Text(tr("Tap 1 to 25 in order, as fast as you can. Keep your eyes on the centre."), textAlign = TextAlign.Center)
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until 5) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0 until 5) {
                                val n = s.nums[r * 5 + c]
                                val found = n < s.next
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                s.wrong == n -> MaterialTheme.colorScheme.errorContainer
                                                found -> MaterialTheme.colorScheme.surface
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        )
                                        .clickable {
                                            if (s.phase != 1) return@clickable
                                            if (n == s.next) {
                                                s.next++
                                                if (s.next > 25) {
                                                    s.elapsed = (System.nanoTime() - s.startNs) / 1_000_000L
                                                    s.phase = 2
                                                }
                                            } else {
                                                s.errors++
                                                s.wrong = n
                                                scope.launch { delay(250); if (s.wrong == n) s.wrong = -1 }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (s.phase != 0) Text(
                                        "$n", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                        color = if (found) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (s.phase == 0) GameOverlay(
                tr("Schulte Table"), listOf(tr("A classic attention drill. The clock starts when you tap Start.")),
                tr("Start"), { s.begin() }, onBack, tr("Back to games")
            )
            if (s.phase == 2) GameOverlay(
                tr("Done!"), listOf(trf("Time: %s", fmtMs(s.elapsed)), trf("Mistakes: %s", "${s.errors}"), trf("Best time: %s", fmtMs(best))),
                tr("Play again"), { s.reset() }, onBack, tr("Back to games")
            )
        }
    }
}

// =====================================================================================
// N-Back
// =====================================================================================

private class NBackState {
    var phase by mutableIntStateOf(0)       // 0 menu, 1 running, 2 result
    var n by mutableIntStateOf(1)
    var trial by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var lit by mutableIntStateOf(-1)
    var hits by mutableIntStateOf(0)
    var misses by mutableIntStateOf(0)
    var falseAlarms by mutableIntStateOf(0)
    var rejections by mutableIntStateOf(0)
    var responded = false
    var flash by mutableIntStateOf(0)       // 1 good, 2 bad: brief feedback on the Match button

    fun start(level: Int) {
        n = level; total = 20 + level; trial = 0; lit = -1
        hits = 0; misses = 0; falseAlarms = 0; rejections = 0; responded = false; flash = 0
        phase = 1
    }

    fun accuracy(): Int = if (total == 0) 0 else ((hits + rejections) * 100) / total
}

@Composable
fun NBackGame(store: Store, onBack: () -> Unit) {
    val s = remember { NBackState() }
    var best by remember { mutableLongStateOf(store.getLong("hs_nback")) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (s.phase == 1) s.phase = 0 }
    LaunchedEffect(s.phase) {
        if (s.phase != 1) return@LaunchedEffect
        val rnd = java.util.Random()
        val seq = ArrayList<Int>()
        for (t in 0 until s.total) {
            val pos = if (t >= s.n && rnd.nextFloat() < 0.3f) seq[t - s.n] else rnd.nextInt(9)
            val isMatch = t >= s.n && pos == seq[t - s.n]
            seq.add(pos)
            s.trial = t + 1
            s.responded = false
            s.lit = pos
            delay(900)
            s.lit = -1
            delay(1100)
            if (isMatch && s.responded) s.hits++
            else if (isMatch) s.misses++
            else if (s.responded) s.falseAlarms++
            else s.rejections++
        }
        s.phase = 2
        if (s.accuracy() >= 80 && s.n > best) { best = s.n.toLong(); store.putLong("hs_nback", best) }
    }
    GameScaffold(title = tr("N-Back"), onBack = onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (s.phase == 1) Text(trf("Step %s", "${s.trial}/${s.total}") + "   " + trf("%d-back", s.n), fontWeight = FontWeight.Bold)
                else Text(" ")
                Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until 3) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until 3) {
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(
                                        if (s.lit == r * 3 + c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { if (s.phase == 1) s.responded = true },
                    contentAlignment = Alignment.Center
                ) { Text(tr("Match"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Text(tr("Tap Match when the lit square is in the same place as it was N steps ago."), textAlign = TextAlign.Center)
            }
            when (s.phase) {
                0 -> NBackMenu(best) { s.start(it) }
                2 -> GameOverlay(
                    if (s.accuracy() >= 80) tr("Nice focus!") else tr("Keep practising"),
                    listOf(
                        trf("Accuracy: %d%%", s.accuracy()),
                        trf("Hits: %d", s.hits) + "   " + trf("Missed: %d", s.misses) + "   " + trf("False alarms: %d", s.falseAlarms),
                        tr("80% or more unlocks the next level.")
                    ),
                    tr("Play again"), { s.start(s.n) }, { s.phase = 0 }, tr("Choose level")
                )
            }
        }
    }
}

@Composable
private fun NBackMenu(best: Long, onPick: (Int) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(Modifier.padding(32.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("N-Back"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tr("A working-memory workout. Pick a level."), textAlign = TextAlign.Center)
                for (lv in 1..3) {
                    val locked = lv > best + 1
                    Button(onClick = { onPick(lv) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text(trf("%d-back", lv)) }
                }
                if (best > 0L) Text(trf("Best: %d-back", best.toInt()), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// =====================================================================================
// Reaction Test
// =====================================================================================

@Composable
fun ReactionGame(store: Store, onBack: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }     // 0 intro, 1 wait, 2 go, 3 result, 4 too soon, 5 finished
    var startNs by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableLongStateOf(0L) }
    val times = remember { mutableStateListOf<Long>() }
    var best by remember { mutableLongStateOf(store.getLong("hs_react")) }
    val rounds = 5

    LaunchedEffect(phase) {
        if (phase == 1) {
            delay(1500L + kotlin.random.Random.nextLong(2500L))
            startNs = System.nanoTime()
            phase = 2
        }
        if (phase == 5) {
            val avg = times.sum() / times.size
            if (best == 0L || avg < best) { best = avg; store.putLong("hs_react", best) }
        }
    }
    fun tapScreen() {
        when (phase) {
            0, 3, 4 -> phase = 1
            1 -> phase = 4
            2 -> {
                lastMs = (System.nanoTime() - startNs) / 1_000_000L
                times.add(lastMs)
                phase = if (times.size >= rounds) 5 else 3
            }
        }
    }
    val bg = when (phase) {
        1 -> Color(0xFFC62828)
        2 -> Color(0xFF2E9E5B)
        4 -> Color(0xFFEF6C00)
        else -> Color(0xFF2B4A9B)
    }
    GameScaffold(title = tr("Reaction Test"), onBack = onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(bg).clickable(enabled = phase != 5) { tapScreen() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val big = when (phase) {
                        0 -> tr("Tap to start")
                        1 -> tr("Wait for green...")
                        2 -> tr("TAP!")
                        3 -> "$lastMs ms"
                        4 -> tr("Too soon!")
                        else -> ""
                    }
                    Text(big, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    val sub = when (phase) {
                        0 -> trf("%d rounds. Tap as soon as the screen turns green.", rounds)
                        3 -> trf("Round %s", "${times.size}/$rounds") + " · " + tr("Tap to continue")
                        4 -> tr("You tapped before green. Tap to try again.")
                        else -> ""
                    }
                    if (sub.isNotEmpty()) Text(sub, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            if (phase == 5) {
                val avg = if (times.isEmpty()) 0L else times.sum() / times.size
                GameOverlay(
                    tr("Your reflexes"), listOf(trf("Average: %d ms", avg.toInt()), trf("Best average: %d ms", best.toInt())),
                    tr("Play again"), { times.clear(); phase = 1 }, onBack, tr("Back to games")
                )
            }
        }
    }
}
