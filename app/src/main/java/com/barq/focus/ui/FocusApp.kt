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

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.barq.focus.data.CardType
import com.barq.focus.data.Db
import com.barq.focus.data.FocusCard
import com.barq.focus.data.PdfEntry
import com.barq.focus.service.Episteme
import com.barq.focus.service.Shared
import com.barq.focus.data.tr
import com.barq.focus.data.Store

@Composable
fun FocusApp() {
    val ctx = LocalContext.current
    val fresh = remember { Store(ctx).isFresh() }      // asked before rollover() writes the first cards
    val store = remember { Store(ctx).also { it.rollover() } }
    val db = remember { Db.get(ctx) }
    var screen by remember { mutableStateOf(if (fresh) "welcome" else "home") }
    var current by remember { mutableStateOf<FocusCard?>(null) }
    var pdf by remember { mutableStateOf<PdfEntry?>(null) }
    var noteId by remember { mutableLongStateOf(0L) }
    var noteType by remember { mutableIntStateOf(0) }
    var epIdx by remember { mutableIntStateOf(-1) }

    fun open(c: FocusCard) {
        current = c
        epIdx = -1
        screen = when {
            c.type == CardType.STUDY -> "subject"
            c.type == CardType.TREAT && c.eps.isNotEmpty() -> "series"
            c.type == CardType.TREAT && c.media.isNotEmpty() -> "player"
            else -> "timer"
        }
    }

    LaunchedEffect(Unit) {
        val id = store.activeCardId
        if (id != -1L) store.cards().firstOrNull { it.id == id }?.let { open(it) }
    }
    LaunchedEffect(Shared.pendingTick) {
        if (Shared.pendingUri != null || Shared.pendingText != null) screen = "import"
    }
    BackHandler(enabled = screen != "home" && screen != "welcome") {
        screen = when (screen) { "note" -> "notes"; "pdf" -> "pdfs"; "player" -> if (epIdx >= 0) "series" else "home"; else -> "home" }
    }

    val back = { screen = "home" }
    when (screen) {
        "subject" -> current?.let { SubjectScreen(store, it, back) }
        "timer" -> current?.let { TimerScreen(store, it, back) }
        "notes" -> NotesScreen(store, onOpen = { id, type -> noteId = id; noteType = type; screen = "note" }, onBack = back)
        "note" -> key(noteId) {
            NoteEditorScreen(store, noteId, noteType, onOpenNote = { n -> noteId = n; noteType = 0 }) { screen = "notes" }
        }
        "series" -> current?.let { SeriesScreen(store, it, back) { idx -> epIdx = idx; screen = "player" } }
        "player" -> current?.let { c0 ->
            val c = remember(c0.id, epIdx) { store.cards().firstOrNull { it.id == c0.id } ?: c0 }
            PlayerScreen(store, c, { screen = if (epIdx >= 0) "series" else "home" }, epIdx)
        }
        "pdfs" -> PdfLibraryScreen(
            store,
            onOpen = { e ->
                val viaEpisteme = store.pdfReader == "episteme"
                if (viaEpisteme && Episteme.openPdf(ctx, android.net.Uri.parse(e.uri))) {
                    store.upsertPdf(e)
                } else {
                    if (viaEpisteme) android.widget.Toast.makeText(ctx, tr("Episteme isn't installed. Opening the built-in reader."), android.widget.Toast.LENGTH_LONG).show()
                    pdf = e; screen = "pdf"
                }
            },
            onOpenBuiltin = { pdf = it; screen = "pdf" },
            onBack = back
        )
        "pdf" -> pdf?.let { PdfReaderScreen(store, it) { screen = "pdfs" } }
        "import" -> ImportScreen(back)
        "music" -> MusicScreen(store, back)
        "games" -> GamesScreen(store, back)
        "trophies" -> TrophiesScreen(store, back)
        "log" -> LogScreen(db, back)
        "schedule" -> ScheduleScreen(store, back)
        "events" -> EventsScreen(back)
        "setup" -> SetupScreen(store, back, onTour = { screen = "welcome" })
        "welcome" -> OnboardingScreen(store) { screen = "home" }
        else -> HomeScreen(store, onOpen = { open(it) }, go = { screen = it })
    }
}
