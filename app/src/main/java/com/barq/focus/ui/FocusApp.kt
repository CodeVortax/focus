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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.barq.focus.data.CardType
import com.barq.focus.data.Db
import com.barq.focus.data.FocusCard
import com.barq.focus.data.I18n
import com.barq.focus.data.PdfEntry
import com.barq.focus.service.Episteme
import com.barq.focus.service.Shared
import com.barq.focus.data.tr
import com.barq.focus.data.Store

/** One place in the app. The card/note/pdf it shows travel with it, so a screen that is animating out keeps showing its own thing. */
private data class Dest(
    val name: String,
    val card: FocusCard? = null,
    val pdf: PdfEntry? = null,
    val noteId: Long = 0L,
    val noteType: Int = 0,
    val ep: Int = -1,
) {
    /** Identity for transitions and for remembering scroll positions. */
    val key: String get() = when (name) {
        "subject", "timer", "series", "player" -> "$name:${card?.id}:$ep"
        "note" -> "note:$noteId"
        "pdf" -> "pdf:${pdf?.uri}"
        else -> name
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FocusApp() {
    val ctx = LocalContext.current
    val fresh = remember { Store(ctx).isFresh() }      // asked before rollover() writes the first cards
    val store = remember { Store(ctx).also { it.rollover(); com.barq.focus.data.Vault.process(ctx, it) } }
    val db = remember { Db.get(ctx) }

    // a real back stack: back returns to where you were, and each screen remembers its scroll position
    val stack = remember { mutableStateListOf(Dest(if (fresh) "welcome" else "home")) }
    var forward by remember { mutableStateOf(true) }
    val saveable = rememberSaveableStateHolder()

    fun push(d: Dest) { if (stack.last().key == d.key) return; forward = true; stack.add(d) }
    fun push(name: String) = push(Dest(name))
    fun replaceTop(d: Dest) { stack[stack.lastIndex] = d }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }
    fun reset(name: String) { forward = false; stack.clear(); stack.add(Dest(name)) }

    fun open(c: FocusCard) {
        val name = when {
            c.type == CardType.STUDY -> "subject"
            c.type == CardType.TREAT && c.eps.isNotEmpty() -> "series"
            c.type == CardType.TREAT && c.media.isNotEmpty() -> "player"
            else -> "timer"
        }
        push(Dest(name, card = c))
    }

    // where a notification or widget tap wants to land: "music", "trophies", "card:<id>", "card:active" ...
    fun route(go: String) {
        when {
            go.startsWith("card:") -> {
                val id = if (go == "card:active") store.activeCardId else go.removePrefix("card:").toLongOrNull() ?: -1L
                val c = store.cards().firstOrNull { it.id == id }
                if (c != null) { if (stack.size > 1) reset("home"); open(c) }
            }
            go == "home" -> reset("home")
            else -> { if (stack.size > 1) reset("home"); push(go) }
        }
    }

    LaunchedEffect(Unit) {
        val go = Shared.pendingGo
        if (go != null) { Shared.pendingGo = null; route(go) }
        else {
            val id = store.activeCardId
            if (id != -1L) store.cards().firstOrNull { it.id == id }?.let { open(it) }
        }
    }
    LaunchedEffect(Shared.goTick) {
        Shared.pendingGo?.let { Shared.pendingGo = null; route(it) }
    }
    LaunchedEffect(Shared.pendingTick) {
        if (Shared.pendingUri != null || Shared.pendingText != null) push("import")
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    val sign = if (I18n.rtl) -1 else 1
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = stack.last(),
            contentKey = { it.key },
            transitionSpec = {
                if (forward)
                    (slideInHorizontally(tween(280)) { sign * it / 5 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(280)) { -sign * it / 8 } + fadeOut(tween(160)))
                else
                    (slideInHorizontally(tween(280)) { -sign * it / 8 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(280)) { sign * it / 5 } + fadeOut(tween(160)))
            },
            label = "screens"
        ) { d ->
            saveable.SaveableStateProvider(d.key) {
                val back = { pop() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (d.name) {
                        "subject" -> d.card?.let { SubjectScreen(store, it, back) }
                        "timer" -> d.card?.let { TimerScreen(store, it, back) }
                        "notes" -> NotesScreen(store, onOpen = { id, type -> push(Dest("note", noteId = id, noteType = type)) }, onBack = back)
                        "note" -> NoteEditorScreen(store, d.noteId, d.noteType, onOpenNote = { n -> replaceTop(Dest("note", noteId = n)) }) { pop() }
                        "series" -> d.card?.let { c -> SeriesScreen(store, c, back) { idx -> push(Dest("player", card = c, ep = idx)) } }
                        "player" -> d.card?.let { c0 ->
                            val c = remember(c0.id, d.ep) { store.cards().firstOrNull { it.id == c0.id } ?: c0 }
                            PlayerScreen(store, c, back, d.ep)
                        }
                        "pdfs" -> PdfLibraryScreen(
                            store,
                            onOpen = { e ->
                                val viaEpisteme = store.pdfReader == "episteme"
                                if (viaEpisteme && Episteme.openPdf(ctx, android.net.Uri.parse(e.uri))) {
                                    store.upsertPdf(e)
                                } else {
                                    if (viaEpisteme) android.widget.Toast.makeText(ctx, tr("Episteme isn't installed. Opening the built-in reader."), android.widget.Toast.LENGTH_LONG).show()
                                    push(Dest("pdf", pdf = e))
                                }
                            },
                            onOpenBuiltin = { push(Dest("pdf", pdf = it)) },
                            onBack = back
                        )
                        "pdf" -> d.pdf?.let { PdfReaderScreen(store, it) { pop() } }
                        "import" -> ImportScreen(back)
                        "music" -> MusicScreen(store, back)
                        "projects" -> ProjectsScreen(store, back)
                        "piggy" -> PiggyScreen(store, back)
                        "games" -> GamesScreen(store, back)
                        "trophies" -> TrophiesScreen(store, back)
                        "log" -> LogScreen(db, back)
                        "schedule" -> ScheduleScreen(store, back)
                        "events" -> EventsScreen(back)
                        "setup" -> SetupScreen(store, back, onTour = { push("welcome") })
                        "welcome" -> OnboardingScreen(store) { reset("home") }
                        else -> HomeScreen(store, onOpen = { open(it) }, go = { push(it) })
                    }
                }
            }
        }
    }
}

/** Game controls and player buttons keep their physical left/right, even in Arabic, so "left" always means left. */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr, content = content
    )
}
