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

import com.barq.focus.data.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.FocusCard
import com.barq.focus.data.Store
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(store: Store, card: FocusCard, onExit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val total = card.minutes * 60_000L
    var endAt by remember { mutableLongStateOf(if (store.activeCardId == card.id) store.activeEnd else 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(endAt) {
        while (endAt > 0L && !done) {
            now = System.currentTimeMillis()
            if (now >= endAt) {
                done = true
                store.stopActive()
            }
            delay(500)
        }
    }

    val remaining = when {
        done -> 0L
        endAt == 0L -> total
        else -> (endAt - now).coerceIn(0L, total)
    }
    val progress = if (endAt == 0L) 0f else 1f - remaining.toFloat() / total
    val secs = (remaining + 999) / 1000
    val col = Color(card.color)
    val track = MaterialTheme.colorScheme.surfaceVariant

    var compact by remember { mutableStateOf(store.timerCompact) }
    val running = endAt != 0L && !done

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(card.title) },
            navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TimerCardUi(
                timeText = "%02d:%02d".format(secs / 60, secs % 60),
                caption = tr(if (done) "Done." else if (running) "Time left" else "Ready"),
                fraction = if (endAt == 0L) 1f else (remaining.toFloat() / total).coerceIn(0f, 1f),
                over = false, running = running, paused = false, color = col, compact = compact,
                showPlay = !running && !done,
                onPlayPause = {
                    endAt = System.currentTimeMillis() + total
                    now = System.currentTimeMillis()
                    store.startActive(card.id, endAt)
                },
                onStop = { store.stopActive(); onExit() },
                onToggleCompact = { compact = !compact; store.timerCompact = compact }
            )
            if (done) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onExit) { Text(tr("Back")) }
            }
        }
    }
}
