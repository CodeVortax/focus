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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Breath
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/** Total seconds ever spent breathing here (shown on the Games list). */
const val BREATH_TOTAL_KEY = "breath_total_sec"

/**
 * Calm Breathing. A circle grows as you breathe in and shrinks as you breathe out. No score, no failing, no fast
 * movement; "Still" swaps the motion for a plain countdown. Finishing early is fine and says so kindly.
 */
@Composable
fun BreathGame(store: Store, onBack: () -> Unit) {
    var patternIx by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(1) }
    var still by remember { mutableStateOf(false) }
    var phase by remember { mutableIntStateOf(0) }            // 0 pick, 1 breathing, 2 paused, 3 finished
    var elapsed by remember { mutableFloatStateOf(0f) }
    var saved by remember { mutableStateOf(false) }
    val pattern = Breath.PATTERNS[patternIx]
    val total = minutes * 60f

    fun finish() {
        if (!saved) {
            saved = true
            store.putLong(BREATH_TOTAL_KEY, store.getLong(BREATH_TOTAL_KEY) + elapsed.toLong())
        }
        phase = 3
    }
    GameLoop(phase == 1) { dt ->
        elapsed += dt
        if (elapsed >= total) { elapsed = total; finish() }
    }

    val ink = MaterialTheme.colorScheme.primary
    GameScaffold(title = tr("Calm Breathing"), onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            when (phase) {
                0 -> {
                    Text(tr("Breathe with the circle. There is no score. Stop whenever you like."), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Pattern"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Breath.PATTERNS.forEachIndexed { i, p -> FilterChip(selected = patternIx == i, onClick = { patternIx = i }, label = { Text(tr(p.name)) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("How long"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Breath.LENGTHS_MIN.forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(trf("%d min", m)) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    FilterChip(selected = still, onClick = { still = !still }, label = { Text(tr("Still (no motion)")) })
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { elapsed = 0f; saved = false; phase = 1 }) { Text(tr("Start")) }
                }
                1, 2 -> {
                    val pos = Breath.at(pattern, elapsed)
                    val label = tr(pattern.steps[pos.index].label)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = minOf(size.width, size.height) / 2f * (if (still) 0.7f else pos.scale)
                            drawCircle(ink.copy(alpha = 0.12f), r * 1.18f)
                            drawCircle(ink.copy(alpha = 0.22f), r * 1.02f)
                            drawCircle(ink.copy(alpha = 0.40f), r * 0.82f)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                            if (still) Text("${pos.secLeft}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ink)
                        }
                    }
                    Text(trf("%s left", fmtTime((total - elapsed).toInt().coerceAtLeast(0))), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { phase = if (phase == 1) 2 else 1 }) { Text(tr(if (phase == 1) "Pause" else "Resume")) }
                        TextButton(onClick = { finish() }) { Text(tr("Finish")) }
                    }
                }
                else -> {
                    Text(tr("Well done. Take a moment before you go back."), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(trf("You breathed for %s.", fmtTime(elapsed.toInt())), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { phase = 0 }) { Text(tr("Once more")) }
                    TextButton(onClick = onBack) { Text(tr("Back to Games")) }
                }
            }
        }
    }
}
