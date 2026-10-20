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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.barq.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/* ---------- shared bits used by the games ---------- */

/** m:ss */
fun fmtTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/** Calls [step] once per frame with the elapsed seconds while [running]. Returns a tick counter to read inside Canvas so it redraws. */
@Composable
fun GameLoop(running: Boolean, step: (Float) -> Unit): MutableLongState {
    val tick = remember { mutableLongStateOf(0L) }
    val cur by rememberUpdatedState(step)
    LaunchedEffect(running) {
        if (running) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                cur(dt)
                tick.longValue++
            }
        }
    }
    return tick
}

@Composable
fun GameScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
                actions = actions,
            )
        },
        content = content,
    )
}

/** Full-screen card shown for start / paused / game over. */
@Composable
fun GameOverlay(
    title: String,
    lines: List<String>,
    primary: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            lines.forEach {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFDDDDEE), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPrimary) { Text(primary) }
            TextButton(onClick = onBack) { Text(backLabel, color = Color.White) }
        }
    }
}

/** Draws [block] in a virtual [vw] x [vh] field centred in the canvas and clipped to it. */
fun DrawScope.virtualField(vw: Float, vh: Float, block: DrawScope.() -> Unit) {
    val sc = minOf(size.width / vw, size.height / vh)
    val ox = (size.width - vw * sc) / 2f
    val oy = (size.height - vh * sc) / 2f
    withTransform({ translate(ox, oy); scale(sc, sc, Offset.Zero); clipRect(0f, 0f, vw, vh) }) { block() }
}

/** Screen x (pixels) to virtual-field x, matching [virtualField]. */
fun fieldX(px: Float, w: Float, h: Float, vw: Float, vh: Float): Float {
    val sc = minOf(w / vw, h / vh)
    return (px - (w - vw * sc) / 2f) / sc
}

/** Press-and-hold button: [onDown] on touch, [onUp] on release or cancel. */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
    height: Dp = 52.dp,
    fontSize: TextUnit = 22.sp,
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.height(height).clip(RoundedCornerShape(16.dp))
            .background(if (pressed) Color(0xFF6A70A8) else Color(0xFF2A2E4A))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    down()
                    try { awaitRelease() } finally { pressed = false; up() }
                })
            },
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold) }
}

/* ---------- the games hub ---------- */

@Composable
fun GamesScreen(store: Store, onBack: () -> Unit) {
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    val hubList = rememberLazyListState()          // survives playing a game, so the list comes back where you left it
    BackHandler(enabled = game != null) { game = null }
    when (game) {
        "tetris" -> TetrisGame(store) { game = null }
        "dash" -> DashGame(store) { game = null }
        "sudoku" -> SudokuGame(store) { game = null }
        "snake" -> SnakeGame(store) { game = null }
        "brick" -> BrickGame(store) { game = null }
        "flappy" -> FlappyGame(store) { game = null }
        "flap" -> FlapGame(store) { game = null }
        "dino" -> DinoGame(store) { game = null }
        "yahtzee" -> YahtzeeGame(store) { game = null }
        "invaders" -> InvadersGame(store) { game = null }
        "stack" -> StackGame(store) { game = null }
        "2048" -> Game2048(store) { game = null }
        "mines" -> MinesGame(store) { game = null }
        "memory" -> MemoryGame(store) { game = null }
        "schulte" -> SchulteGame(store) { game = null }
        "nback" -> NBackGame(store) { game = null }
        "react" -> ReactionGame(store) { game = null }
        else -> GamesHub(store, hubList, onBack) { game = it }
    }
}

private fun bestOf(store: Store, key: String): String {
    val v = store.getLong(key)
    return if (v > 0L) trf("Best: %d", v.toInt()) else ""
}

@Composable
private fun GamesHub(store: Store, listState: androidx.compose.foundation.lazy.LazyListState, onBack: () -> Unit, onPick: (String) -> Unit) {
    // show the record of the hardest Sudoku level played (beginner times would always look "best")
    val sudokuBest = listOf(2, 1, 0, 3).map { store.getLong("sudoku_best_$it") }.firstOrNull { it > 0L }
    GameScaffold(title = tr("Games"), onBack = onBack) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    tr("Short breaks for a busy brain. Play a round, then get back to your plate."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // arcade
            item { GameCard("🧱", tr("Blocks"), tr("Falling blocks. Find the rhythm and let the noise go quiet."), trf("Best: %d", store.getLong("hs_tetris"))) { onPick("tetris") } }
            item { GameCard("🔷", tr("Beat Dash"), tr("Hold to jump in time with the beat. One touch."), trf("Best: %d%%", store.getLong("hs_dash"))) { onPick("dash") } }
            item { GameCard("🐍", tr("Snake"), tr("Eat, grow, and don't hit the walls or yourself."), bestOf(store, "hs_snake")) { onPick("snake") } }
            item { GameCard("🏓", tr("Brick Breaker"), tr("Smash every brick. Colours give power-ups."), bestOf(store, "hs_brick")) { onPick("brick") } }
            item { GameCard("🐤", tr("Flappy Bird"), tr("Hold to rise, release to sink. Squeeze through the pipes."), bestOf(store, "hs_flappy")) { onPick("flappy") } }
            item { GameCard("🐦", tr("Sky Flap"), tr("Tap to flap through the gaps. Simple, tense, addictive."), bestOf(store, "hs_flap")) { onPick("flap") } }
            item { GameCard("🦖", tr("Dino Run"), tr("Jump the cactus, duck the birds. Endless."), bestOf(store, "hs_dino")) { onPick("dino") } }
            item { GameCard("👾", tr("Space Invasion"), tr("Shoot the fleet before it reaches you."), bestOf(store, "hs_invaders")) { onPick("invaders") } }
            item { GameCard("🗼", tr("Stack Tower"), tr("Tap to drop blocks and build the tallest tower. Precision wins."), bestOf(store, "hs_stack")) { onPick("stack") } }
            // puzzles
            item { GameCard("🔢", tr("Sudoku"), tr("Classic number logic. Three difficulty levels."), if (sudokuBest != null) trf("Best time: %s", fmtTime(sudokuBest.toInt())) else "") { onPick("sudoku") } }
            item { GameCard("🔲", tr("2048"), tr("Slide and merge tiles. A calm number puzzle."), bestOf(store, "hs_2048")) { onPick("2048") } }
            item { GameCard("💣", tr("Minesweeper"), tr("Clear the field without hitting a mine."), store.getLong("hs_mines").let { if (it > 0L) trf("Best time: %s", fmtTime(it.toInt())) else "" }) { onPick("mines") } }
            item { GameCard("🎲", tr("Yahtzee"), tr("Roll five dice, three tries a turn, fill the scoreboard."), bestOf(store, "hs_yahtzee")) { onPick("yahtzee") } }
            // brain training
            item { GameCard("🃏", tr("Memory Match"), tr("Flip cards and find the pairs in as few moves as you can."), store.getLong("hs_memory").let { if (it > 0L) trf("Best: %d moves", it.toInt()) else "" }) { onPick("memory") } }
            item { GameCard("🎯", tr("Schulte Table"), tr("Find 1 to 25 in order. A classic attention drill."), store.getLong("hs_schulte").let { if (it > 0L) trf("Best time: %s", fmtMs(it)) else "" }) { onPick("schulte") } }
            item { GameCard("🧠", tr("N-Back"), tr("A working-memory workout. Spot the square from N steps ago."), store.getLong("hs_nback").let { if (it > 0L) trf("Best: %d-back", it.toInt()) else "" }) { onPick("nback") } }
            item { GameCard("⚡", tr("Reaction Test"), tr("Tap the moment the screen turns green."), store.getLong("hs_react").let { if (it > 0L) trf("Best average: %d ms", it.toInt()) else "" }) { onPick("react") } }
        }
    }
}

@Composable
private fun GameCard(emoji: String, name: String, blurb: String, best: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 38.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(blurb, style = MaterialTheme.typography.bodyMedium)
                if (best.isNotEmpty()) Text(best, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
