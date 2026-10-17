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
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.barq.focus.data.Rich
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Attach
import com.barq.focus.data.CardType
import com.barq.focus.data.Item
import com.barq.focus.data.Note
import com.barq.focus.data.NotesDb
import com.barq.focus.data.Store
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.CardSwatches
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(store: Store, noteId: Long, newType: Int, onOpenNote: (Long) -> Unit = {}, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { NotesDb.get(ctx) }
    val initial = remember { (if (noteId != 0L) db.get(noteId) else null) ?: Note(type = newType) }

    var id by remember { mutableLongStateOf(initial.id) }
    var type by remember { mutableIntStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var body by remember { mutableStateOf(initial.body) }
    var tfv by remember { mutableStateOf(TextFieldValue(initial.body)) }
    var spans by remember { mutableStateOf(initial.spans) }
    val items = remember {
        mutableStateListOf<Item>().also {
            it.addAll(initial.items.ifEmpty { if (initial.type == 1) listOf(Item("", false)) else emptyList() })
        }
    }
    var color by remember { mutableIntStateOf(initial.color) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var archived by remember { mutableStateOf(initial.archived) }
    var labels by remember { mutableStateOf(initial.labels) }
    // old voice notes join the attachment list (and are saved there from now on)
    val atts = remember {
        mutableStateListOf<Attach>().also { l ->
            l.addAll(initial.attachments)
            l.addAll(initial.voices.map { Attach("audio", "voice:" + it.file, tr("Voice note"), it.ms) })
        }
    }
    var arrange by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showRemind by remember { mutableStateOf(false) }
    var modified by remember { mutableLongStateOf(initial.modified) }
    val created = remember { initial.created }
    var lastSaved by remember { mutableStateOf(if (initial.id != 0L) initial else null) }
    var gone by remember { mutableStateOf(false) }

    var showColors by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var focusIdx by remember { mutableIntStateOf(-1) }

    fun snapshot() = Note(
        id = id, type = type, title = title, body = body, items = items.toList(), color = color, pinned = pinned,
        archived = archived, labels = labels, spans = if (type == 0) spans else emptyList(),
        attachments = atts.toList(), created = created, modified = modified
    )

    fun save() {
        if (gone) return
        val n = snapshot()
        if (n.isEmpty && id == 0L) return
        if (lastSaved != null && n.copy(modified = 0L) == lastSaved!!.copy(modified = 0L)) return
        val now = System.currentTimeMillis()
        val saved = n.copy(modified = now)
        id = db.save(saved)
        modified = now
        lastSaved = saved.copy(id = id)
    }

    // the debounced save runs off the main thread (typing never waits for the disk) and can't be cancelled half-way
    suspend fun saveAsync() = withContext(NonCancellable) {
        if (gone) return@withContext
        val n = snapshot()
        if (n.isEmpty && id == 0L) return@withContext
        if (lastSaved != null && n.copy(modified = 0L) == lastSaved!!.copy(modified = 0L)) return@withContext
        val now = System.currentTimeMillis()
        val saved = n.copy(modified = now)
        val newId = withContext(Dispatchers.IO) { db.save(saved) }
        id = newId; modified = now; lastSaved = saved.copy(id = newId)
    }

    // autosave shortly after you stop typing, and once more when you leave
    LaunchedEffect(title, body, spans, color, pinned, archived, labels, type, atts.toList(), items.toList()) { delay(700); saveAsync() }
    DisposableEffect(Unit) { onDispose { save() } }

    fun toggle(i: Int, v: Boolean) {
        items[i] = items[i].copy(done = v)
        if (store.notesAutoSort) {
            val x = items.removeAt(i)
            val at = if (v) items.size else items.indexOfFirst { it.done }.let { if (it == -1) items.size else it }
            items.add(at, x)
        }
    }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun applyOp(op: RichOp) {
        val sel = tfv.selection
        if (sel.collapsed) { toast(tr("Select some text first")); return }
        val a = minOf(sel.start, sel.end)
        val b = maxOf(sel.start, sel.end)
        val arr = Rich.toArray(tfv.text.length, spans)
        val all = (a until b).all { op.has(arr[it]) }
        spans = Rich.apply(tfv.text.length, spans, a, b) { f -> if (all) op.remove(f) else op.apply(f) }
    }

    val surface = MaterialTheme.colorScheme.background
    val bg = if (color == 0) surface else Color(color).copy(alpha = 0.14f).compositeOver(surface)
    val accent = if (color == 0) MaterialTheme.colorScheme.primary else Color(color)
    val baseBody = noteBodyStyle()
    val bodyStyle = remember(baseBody) { baseBody.copy(fontSize = 17.sp) }
    val richTransform = remember(spans) {
        VisualTransformation { t -> TransformedText(buildRich(t.text, spans), OffsetMapping.Identity) }
    }
    val clear = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
    )

    Scaffold(
        containerColor = bg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, "Pin")
                    }
                    IconButton(onClick = { showColors = !showColors }) { Icon(Icons.Default.Palette, "Color") }
                    TextButton(onClick = { showLabels = true }) { Text(tr("Labels")) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("Share")) }, onClick = {
                                menu = false
                                val text = buildString {
                                    if (title.isNotBlank()) appendLine(title)
                                    if (type == 0) append(body)
                                    else items.filter { it.text.isNotBlank() }.forEach { appendLine((if (it.done) "[x] " else "[ ] ") + it.text) }
                                }.trim()
                                ctx.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share note")
                                )
                            })
                            DropdownMenuItem(text = { Text(tr("Send tasks to a subject")) }, onClick = { menu = false; showSend = true })
                            DropdownMenuItem(text = { Text(tr("Remind me about this note")) }, onClick = { menu = false; save(); showRemind = true })
                            DropdownMenuItem(
                                text = { Text(tr(if (type == 0) "Turn into checklist" else "Turn into text")) },
                                onClick = {
                                    menu = false
                                    if (type == 0) {
                                        val l = body.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { Item(it, false) }
                                        items.clear(); items.addAll(l.ifEmpty { listOf(Item("", false)) }); body = ""; tfv = TextFieldValue(""); spans = emptyList(); type = 1
                                    } else {
                                        body = items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
                                        items.clear(); tfv = TextFieldValue(body); spans = emptyList(); type = 0
                                    }
                                }
                            )
                            if (type == 1) {
                                DropdownMenuItem(text = { Text(tr("Remove checked items")) }, onClick = {
                                    menu = false
                                    val keep = items.filter { !it.done }
                                    items.clear(); items.addAll(keep.ifEmpty { listOf(Item("", false)) })
                                })
                                DropdownMenuItem(
                                    text = { Text((if (store.notesAutoSort) "✓  " else "     ") + tr("Checked items sink to the bottom")) },
                                    onClick = { menu = false; store.notesAutoSort = !store.notesAutoSort }
                                )
                            }
                            DropdownMenuItem(text = { Text(tr(if (archived) "Unarchive" else "Archive")) }, onClick = {
                                menu = false; archived = !archived; save(); toast(tr(if (archived) "Archived" else "Unarchived")); onBack()
                            })
                            DropdownMenuItem(text = { Text(tr("Move to trash")) }, onClick = {
                                menu = false
                                gone = true
                                if (id != 0L) db.save(snapshot().copy(trashedAt = System.currentTimeMillis(), pinned = false))
                                onBack()
                            })
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextField(
                title, { title = it }, Modifier.fillMaxWidth(),
                placeholder = { Text(tr("Title"), style = noteTitleStyle().copy(fontSize = 26.sp)) },
                textStyle = noteTitleStyle().copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                colors = clear,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            )

            if (labels.isNotEmpty()) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                labels.forEach {
                    Text(
                        it, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showLabels = true }
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (showColors) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .border(if (color == 0) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { color = 0 }
                )
                CardSwatches.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (c.toArgb() == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c.toArgb() }
                    )
                }
            }

            if (type == 0) {
                TextField(
                    tfv,
                    { nv ->
                        if (nv.text != tfv.text) { spans = Rich.adjust(tfv.text, nv.text, spans); body = nv.text }
                        tfv = nv
                    },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("Note"), style = bodyStyle) },
                    textStyle = bodyStyle, visualTransformation = richTransform,
                    colors = clear, minLines = 12,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            } else {
                val rs = remember { ReorderState() }
                Column {
                items.forEachIndexed { i, it0 ->
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(focusIdx) {
                        if (focusIdx == i) {
                            delay(60)
                            try { fr.requestFocus() } catch (_: Exception) {}
                            focusIdx = -1
                        }
                    }
                    Row(Modifier.reorderRow(rs, i).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (arrange) Icon(
                            Icons.Default.DragHandle, tr("Drag to move"),
                            Modifier.reorderHandle(rs, i, items.size) { f, t -> items.add(t, items.removeAt(f)) }.padding(10.dp).size(24.dp)
                        )
                        ShapeCheck(it0.done, accent, i) { v -> toggle(i, v) }
                        TextField(
                            it0.text,
                            { v ->
                                if ('\n' in v) {
                                    items[i] = it0.copy(text = v.substringBefore('\n'))
                                    items.add(i + 1, Item(v.substringAfter('\n'), false))
                                    focusIdx = i + 1
                                } else items[i] = it0.copy(text = v)
                            },
                            Modifier.weight(1f).focusRequester(fr),
                            singleLine = false, maxLines = Int.MAX_VALUE, colors = clear,
                            textStyle = noteBodyStyle().copy(
                                fontSize = 17.sp,
                                textDecoration = if (it0.done) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (it0.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        IconButton(onClick = { if (items.size > 1) items.removeAt(i) else items[i] = Item("", false) }) {
                            Icon(Icons.Default.Close, "Remove item")
                        }
                    }
                }
                }
                TextButton(onClick = { items.add(Item("", false)); focusIdx = items.lastIndex }) { Text(tr("+  Add item")) }
            }

            AttachSection(atts, arrange, accent, onOpenNote = { n -> save(); onOpenNote(n) })

            if (id != 0L) Text(
                tr("Edited ") + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(modified)),
                Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(48.dp))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { showAttach = true }) { Icon(Icons.Default.Add, tr("Attach")) }
            Box(Modifier.weight(1f)) { if (type == 0) RichBar(onOp = { op -> applyOp(op) }) }
            IconButton(onClick = { arrange = !arrange }) {
                Icon(if (arrange) Icons.Default.Check else Icons.Default.SwapVert, tr(if (arrange) "Done arranging" else "Arrange"))
            }
        }
        }
    }

    AttachAdder(showAttach, { showAttach = false }, atts, store, id)

    if (showRemind) EventDialog(
        initial = null, defaultTitle = title.ifBlank { tr("Note") }, noteId = id,
        onDismiss = { showRemind = false },
        onSaved = { showRemind = false; toast(tr("Reminder set")) }
    )

    if (showLabels) {
        var newLabel by remember { mutableStateOf("") }
        var extra by remember { mutableStateOf(listOf<String>()) }
        val known = remember { (db.all().flatMap { it.labels } + labels).distinct().sorted() }
        val shown = (known + extra).distinct()
        AlertDialog(
            onDismissRequest = { showLabels = false },
            title = { Text(tr("Labels")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundCheck(l in labels, accent) { v -> labels = if (v) labels + l else labels - l }
                            Text(l, Modifier.weight(1f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newLabel, { newLabel = it }, Modifier.weight(1f),
                            label = { Text(tr("New label")) }, singleLine = true,
                            textStyle = noteBodyStyle()
                        )
                        TextButton(enabled = newLabel.isNotBlank(), onClick = {
                            val l = newLabel.trim()
                            extra = extra + l
                            if (l !in labels) labels = labels + l
                            newLabel = ""
                        }) { Text(tr("Add")) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLabels = false }) { Text(tr("Done")) } }
        )
    }

    if (showSend) {
        val subjects = remember { store.cards().filter { it.type == CardType.STUDY } }
        AlertDialog(
            onDismissRequest = { showSend = false },
            title = { Text(tr("Send tasks to…")) },
            text = {
                Column {
                    if (subjects.isEmpty()) Text(tr("No subject cards yet."))
                    subjects.forEach { c ->
                        TextButton(onClick = {
                            val lines = if (type == 1) items.filter { !it.done && it.text.isNotBlank() }.map { it.text.trim() }
                            else body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            store.updateCard(c.copy(items = c.items + lines.map { Item(it, false) }))
                            showSend = false
                            toast(trf("Sent %d tasks to %s", lines.size, c.title))
                        }) { Text(c.title) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSend = false }) { Text(tr("Cancel")) } }
        )
    }
}
