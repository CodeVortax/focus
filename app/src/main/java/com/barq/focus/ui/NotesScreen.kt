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

import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Note
import com.barq.focus.data.NotesDb
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.FocusBrandFont
import com.barq.focus.ui.theme.FocusNoteFont

@Composable
fun noteTitleStyle() = MaterialTheme.typography.titleMedium.copy(
    fontFamily = FocusNoteFont, fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content
)

@Composable
fun noteBodyStyle() = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = FocusNoteFont, fontWeight = FontWeight.Normal, textDirection = TextDirection.Content
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(store: Store, onOpen: (Long, Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { NotesDb.get(ctx) }
    var rev by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf("notes") }          // notes | archive | trash
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    var grid by remember { mutableStateOf(store.notesGrid) }
    var sort by remember { mutableIntStateOf(store.notesSort) }
    var actions by remember { mutableStateOf<Note?>(null) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }       // selection mode: ids of the selected notes
    val selecting = picked.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val all = remember(rev) {
        db.purge(System.currentTimeMillis() - 7L * 24 * 3600_000L)
        db.all()
    }
    val inMode = remember(all, mode) {
        all.filter {
            when (mode) {
                "trash" -> it.trashedAt > 0
                "archive" -> it.trashedAt == 0L && it.archived
                else -> it.trashedAt == 0L && !it.archived
            }
        }
    }
    val labels = remember(inMode) { inMode.flatMap { it.labels }.distinct().sorted() }
    val shown = remember(inMode, query, label, sort, mode) {
        val q = query.trim().lowercase()
        val byPin = compareByDescending<Note> { it.pinned && mode == "notes" }
        val bySort = when (sort) {
            1 -> compareByDescending<Note> { it.created }
            2 -> compareBy<Note> { it.title.lowercase() }
            else -> compareByDescending<Note> { it.modified }
        }
        inMode.filter { n ->
            (label == null || label in n.labels) &&
                (q.isEmpty() || n.title.lowercase().contains(q) || n.body.lowercase().contains(q) ||
                    n.items.any { it.text.lowercase().contains(q) } || n.labels.any { it.lowercase().contains(q) })
        }.sortedWith(byPin.then(bySort))
    }

    LaunchedEffect(mode) { picked = emptySet() }
    LaunchedEffect(all) { picked = picked.filter { id -> all.any { it.id == id } }.toSet() }
    LaunchedEffect(labels) { if (label != null && label !in labels) label = null }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun toggleItem(n: Note, idx: Int) {
        if (mode == "trash") return
        db.save(n.copy(items = n.items.toMutableList().also { l -> l[idx] = l[idx].copy(done = !l[idx].done) }))
        rev++
    }

    BackHandler(selecting) { picked = emptySet() }
    fun chosen() = shown.filter { it.id in picked }
    fun each(f: (Note) -> Note) { chosen().forEach { db.save(f(it)) }; picked = emptySet(); rev++ }

    Scaffold(
        topBar = {
            if (selecting) TopAppBar(
                title = { Text(trf("%d selected", picked.size)) },
                navigationIcon = { IconButton(onClick = { picked = emptySet() }) { Icon(Icons.Default.Close, "Cancel") } },
                actions = {
                    IconButton(onClick = { picked = if (picked.size == shown.size) emptySet() else shown.map { it.id }.toSet() }) { Icon(Icons.Default.SelectAll, tr("Select all")) }
                    if (mode == "trash") {
                        IconButton(onClick = { each { it.copy(trashedAt = 0L) } }) { Icon(Icons.Default.RestoreFromTrash, tr("Restore")) }
                        IconButton(onClick = { chosen().forEach { db.delete(it.id) }; picked = emptySet(); rev++ }) { Icon(Icons.Default.DeleteForever, tr("Delete forever")) }
                    } else {
                        IconButton(onClick = {
                            val text = chosen().joinToString("\n\n") { n ->
                                (if (n.title.isNotBlank()) n.title + "\n" else "") + (if (n.type == 1) n.items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text } else n.body)
                            }
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Share")))
                        }) { Icon(Icons.Default.Share, tr("Share")) }
                        IconButton(onClick = { val all = chosen().all { it.pinned }; each { it.copy(pinned = !all) } }) { Icon(Icons.Default.Star, tr("Pin")) }
                        IconButton(onClick = { val un = mode == "archive"; each { it.copy(archived = !un) } }) {
                            Icon(if (mode == "archive") Icons.Default.Unarchive else Icons.Default.Archive, tr("Archive"))
                        }
                        IconButton(onClick = { each { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) } }) { Icon(Icons.Default.Delete, tr("Move to trash")) }
                    }
                }
            ) else TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus),
                            placeholder = { Text(tr("Search notes")) }, singleLine = true,
                            textStyle = noteBodyStyle().copy(fontSize = 17.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            tr(when (mode) { "archive" -> "Archive"; "trash" -> "Trash"; else -> "Notes" }),
                            fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            searching -> { searching = false; query = "" }
                            mode != "notes" -> { mode = "notes"; label = null }
                            else -> onBack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { grid = !grid; store.notesGrid = grid }) {
                        Icon(if (grid) Icons.Default.ViewAgenda else Icons.Default.ViewModule, "Layout")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("Last edited", "Created", "Title A-Z").forEachIndexed { i, s ->
                                DropdownMenuItem(
                                    text = { Text((if (sort == i) "✓  " else "     ") + tr(s)) },
                                    onClick = { sort = i; store.notesSort = i; sortMenu = false }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (mode != "archive") DropdownMenuItem(text = { Text(tr("Archive")) }, onClick = { mode = "archive"; label = null; menu = false })
                            if (mode != "trash") DropdownMenuItem(text = { Text(tr("Trash")) }, onClick = { mode = "trash"; label = null; menu = false })
                            if (mode == "trash" && inMode.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Empty trash")) }, onClick = {
                                inMode.forEach { db.delete(it.id) }; rev++; menu = false
                            })
                            DropdownMenuItem(text = { Text(tr("Export backup (share)")) }, onClick = {
                                menu = false
                                ctx.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_SUBJECT, "Focus notes backup")
                                            .putExtra(Intent.EXTRA_TEXT, db.exportJson()),
                                        "Export notes"
                                    )
                                )
                            })
                            DropdownMenuItem(text = { Text(tr("Import backup (from clipboard)")) }, onClick = {
                                menu = false
                                try {
                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                    val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    val n = db.importJson(t)
                                    rev++
                                    toast(trf("Imported %d notes", n))
                                } catch (_: Exception) { toast(tr("Clipboard has no notes backup")) }
                            })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (mode == "notes") Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { onOpen(0L, 1) }) { Icon(Icons.Default.CheckBox, "New checklist") }
                FloatingActionButton(onClick = { onOpen(0L, 0) }) { Icon(Icons.Default.Add, "New note") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (mode == "trash") Text(
                tr("Notes here are deleted for good after 7 days. Tap one to restore it."),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium
            )
            if (labels.isNotEmpty()) LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = label == null, onClick = { label = null }, label = { Text(tr("All")) }) }
                items(labels) { l -> FilterChip(selected = label == l, onClick = { label = if (label == l) null else l }, label = { Text(l) }) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shown.isEmpty()) {
                    Text(
                        tr(when {
                            query.isNotBlank() -> "Nothing found."
                            mode == "trash" -> "Trash is empty."
                            mode == "archive" -> "Nothing archived."
                            else -> "No notes yet. Tap + to start one."
                        }),
                        Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (grid) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp
                    ) {
                        items(shown, key = { it.id }) { n ->
                            NoteCard(n, n.id in picked, { if (selecting) picked = if (n.id in picked) picked - n.id else picked + n.id else if (mode == "trash") actions = n else onOpen(n.id, n.type) }, { picked = picked + n.id }) { idx -> toggleItem(n, idx) }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(shown, key = { it.id }) { n ->
                            NoteCard(n, n.id in picked, { if (selecting) picked = if (n.id in picked) picked - n.id else picked + n.id else if (mode == "trash") actions = n else onOpen(n.id, n.type) }, { picked = picked + n.id }) { idx -> toggleItem(n, idx) }
                        }
                    }
                }
            }
        }
    }

    actions?.let { n ->
        AlertDialog(
            onDismissRequest = { actions = null },
            confirmButton = {},
            title = { Text(n.title.ifBlank { tr("Note") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (n.trashedAt > 0) {
                        TextButton(onClick = { db.save(n.copy(trashedAt = 0L)); actions = null; rev++ }) {
                            Icon(Icons.Default.RestoreFromTrash, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore"))
                        }
                        TextButton(onClick = { db.delete(n.id); actions = null; rev++ }) {
                            Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text(tr("Delete forever"))
                        }
                    } else {
                        TextButton(onClick = { db.save(n.copy(pinned = !n.pinned)); actions = null; rev++ }) {
                            Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp)); Text(tr(if (n.pinned) "Unpin" else "Pin"))
                        }
                        TextButton(onClick = { db.save(n.copy(archived = !n.archived)); actions = null; rev++ }) {
                            Icon(if (n.archived) Icons.Default.Unarchive else Icons.Default.Archive, null)
                            Spacer(Modifier.width(8.dp)); Text(tr(if (n.archived) "Unarchive" else "Archive"))
                        }
                        TextButton(onClick = { db.save(n.copy(trashedAt = System.currentTimeMillis(), pinned = false)); actions = null; rev++ }) {
                            Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(tr("Move to trash"))
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(n: Note, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, onToggle: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (n.color == 0) MaterialTheme.colorScheme.surfaceVariant else Color(n.color).copy(alpha = 0.22f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tint)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (n.title.isNotBlank() || n.pinned) Row(verticalAlignment = Alignment.Top) {
            Text(
                n.title, Modifier.weight(1f), style = noteTitleStyle(),
                textAlign = TextAlign.Start, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (n.pinned) Icon(Icons.Default.Star, "Pinned", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (n.type == 0) {
            if (n.body.isNotBlank()) Text(
                remember(n.body, n.spans) { buildRich(n.body, n.spans) },
                style = noteBodyStyle(), textAlign = TextAlign.Start, maxLines = 8, overflow = TextOverflow.Ellipsis
            )
        } else {
            val vis = n.items.withIndex().filter { it.value.text.isNotBlank() }
            (if (expanded) vis else vis.take(6)).forEach { (idx, item) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (item.done) "●" else "○",
                        Modifier.clickable { onToggle(idx) }.padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
                        style = noteBodyStyle()
                    )
                    Text(
                        item.text, Modifier.weight(1f).clickable { expanded = !expanded },
                        style = noteBodyStyle().copy(textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None),
                        textAlign = TextAlign.Start,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (vis.size > 6 || vis.any { it.value.text.length > 40 }) Text(
                if (expanded) tr("Show less") else if (vis.size > 6) trf("+%d more", vis.size - 6) else tr("Show full text"),
                Modifier.clickable { expanded = !expanded }, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (n.labels.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            n.labels.take(3).forEach {
                Text(
                    it, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
