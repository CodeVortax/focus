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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

// Yahtzee. Rules from GuillaumeFalourd/formulas-games (Apache-2.0), including its extra "FibonYahtzee" row (1,1,2,3,5).

private val YZ_NAMES = listOf(
    "Aces", "Twos", "Threes", "Fours", "Fives", "Sixes",
    "Three of a kind", "Four of a kind", "Full house", "Small straight", "Large straight",
    "Yahtzee", "FibonYahtzee", "Chance"
)

/** Longest run of consecutive faces present in [c] (c[1]..c[6] are the counts). */
private fun yzRun(c: IntArray): Int {
    var best = 0
    var cur = 0
    for (v in 1..6) {
        if (c[v] > 0) { cur++; if (cur > best) best = cur } else cur = 0
    }
    return best
}

private fun yzPoints(cat: Int, d: List<Int>): Int {
    val counts = IntArray(7)
    for (v in d) counts[v]++
    val sum = d.fold(0) { a, v -> a + v }
    return when (cat) {
        0, 1, 2, 3, 4, 5 -> counts[cat + 1] * (cat + 1)
        6 -> if ((1..6).any { counts[it] >= 3 }) sum else 0
        7 -> if ((1..6).any { counts[it] >= 4 }) sum else 0
        8 -> if ((1..6).any { counts[it] == 3 } && (1..6).any { counts[it] == 2 }) 25 else 0
        9 -> if (yzRun(counts) >= 4) 30 else 0
        10 -> if (yzRun(counts) >= 5) 40 else 0
        11 -> if ((1..6).any { counts[it] == 5 }) 50 else 0
        12 -> if (d.sorted() == listOf(1, 1, 2, 3, 5)) 100 else 0
        else -> sum
    }
}

private val YZ_PIPS = listOf(
    listOf(4), listOf(0, 8), listOf(0, 4, 8), listOf(0, 2, 6, 8), listOf(0, 2, 4, 6, 8), listOf(0, 2, 3, 5, 6, 8)
)

@Composable
private fun YzDie(face: Int, held: Boolean, live: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.aspectRatio(1f).alpha(if (live) 1f else 0.35f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFDFDFD))
            .border(
                if (held) BorderStroke(4.dp, Color(0xFFFFB300)) else BorderStroke(1.dp, Color(0xFFB0B0B0)),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pips = YZ_PIPS[(face - 1).coerceIn(0, 5)]
            for (idx in pips) {
                drawCircle(
                    Color(0xFF222222), size.minDimension * 0.085f,
                    Offset(size.width * (0.25f + 0.25f * (idx % 3)), size.height * (0.25f + 0.25f * (idx / 3)))
                )
            }
        }
    }
}

@Composable
fun YahtzeeGame(store: Store, onBack: () -> Unit) {
    val dice = remember { mutableStateListOf(1, 1, 1, 1, 1) }
    val held = remember { mutableStateListOf(false, false, false, false, false) }
    val scores = remember { mutableStateListOf<Int?>().apply { repeat(14) { add(null) } } }
    var rollsLeft by remember { mutableIntStateOf(3) }
    var rolled by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var best by remember { mutableLongStateOf(store.getLong("hs_yahtzee")) }

    val total = scores.fold(0) { a, v -> a + (v ?: 0) }
    val rounds = scores.count { it != null }

    fun roll() {
        if (rollsLeft <= 0 || over) return
        if (!rolled) for (i in 0 until 5) held[i] = false
        for (i in 0 until 5) if (!rolled || !held[i]) dice[i] = (1..6).random()
        rolled = true
        rollsLeft--
    }

    fun score(cat: Int) {
        if (!rolled || over || scores[cat] != null) return
        scores[cat] = yzPoints(cat, dice.toList())
        rollsLeft = 3
        rolled = false
        for (i in 0 until 5) held[i] = false
        if (scores.all { it != null }) {
            over = true
            val t = scores.fold(0) { a, v -> a + (v ?: 0) }
            if (t > best) { best = t.toLong(); store.putLong("hs_yahtzee", best) }
        }
    }

    fun restart() {
        for (i in 0 until 14) scores[i] = null
        for (i in 0 until 5) { held[i] = false; dice[i] = 1 }
        rollsLeft = 3; rolled = false; over = false
    }

    GameScaffold(title = tr("Yahtzee"), onBack = onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(trf("Rounds: %d/14", rounds), modifier = Modifier.weight(1f))
                    Text(trf("Total: %d", total), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until 5) {
                        YzDie(dice[i], held[i], rolled, Modifier.weight(1f)) {
                            if (rolled && rollsLeft > 0 && !over) held[i] = !held[i]
                        }
                    }
                }
                Button(
                    onClick = { roll() }, enabled = rollsLeft > 0 && !over,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(trf("Roll (%d left)", rollsLeft), fontSize = 18.sp) }
                Text(
                    tr("Tap dice to keep them. Tap a row to score."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (cat in 0 until 14) {
                    val used = scores[cat]
                    val maybe = if (rolled && used == null) yzPoints(cat, dice.toList()) else null
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = rolled && used == null && !over) { score(cat) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (maybe != null && maybe > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr(YZ_NAMES[cat]), modifier = Modifier.weight(1f),
                                fontWeight = if (used != null) FontWeight.Normal else FontWeight.Medium
                            )
                            when {
                                used != null -> Text("$used", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                maybe != null -> Text("+$maybe", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                else -> Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (over) GameOverlay(
                tr("Game finished!"), listOf(trf("Total: %d", total), trf("Best: %d", best)),
                tr("Play again"), { restart() }, onBack, tr("Back to games")
            )
        }
    }
}
