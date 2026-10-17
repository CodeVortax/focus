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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Attach
import com.barq.focus.data.MediaInfo
import com.barq.focus.data.NotePlayer
import com.barq.focus.data.NotesDb
import com.barq.focus.data.Store
import com.barq.focus.data.Voices
import com.barq.focus.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------- the wavy progress line

/** A progress line that ripples while the music plays and goes flat when it stops. Tap or drag to seek. */
@Composable
fun WavyBar(frac: Float, playing: Boolean, color: Color, track: Color, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase"
    )
    val amp by animateFloatAsState(if (playing) 1f else 0f, label = "amp")
    var drag by remember { mutableStateOf<Float?>(null) }
    Canvas(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null }
                )
            }
    ) {
        val w = size.width
        val cy = size.height / 2f
        val stroke = 4.dp.toPx()
        val x = (w * (drag ?: frac)).coerceIn(0f, w)
        drawLine(track, Offset(x, cy), Offset(w, cy), stroke, StrokeCap.Round)
        val a = 4.dp.toPx() * amp
        val len = 22.dp.toPx()
        val path = Path()
        var px = 0f
        path.moveTo(0f, cy + a * sin(-phase))
        while (px < x) {
            px = minOf(px + 3f, x)
            path.lineTo(px, cy + a * sin(px / len * 2f * PI.toFloat() - phase))
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(x, cy - 10.dp.toPx()), Offset(x, cy + 10.dp.toPx()), stroke, StrokeCap.Round)
    }
}

/** The small music box: play button, name, the wavy line and the time. */
@Composable
fun MiniPlayer(a: Attach, accent: Color, onRename: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val mine = NotePlayer.current == a.uri
    val playing = mine && NotePlayer.isPlaying
    val dur = if (mine && NotePlayer.dur > 0L) NotePlayer.dur else a.ms
    val pos = if (mine) NotePlayer.pos else 0L
    LaunchedEffect(mine, playing) {
        while (mine && playing) { NotePlayer.refresh(); delay(120) }
    }
    DisposableEffect(a.uri) { onDispose { if (NotePlayer.current == a.uri) NotePlayer.stop() } }

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = { NotePlayer.toggle(ctx, a.uri, a.playUri(ctx)) }, Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
        ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, tr(if (playing) "Pause" else "Play")) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.name, Modifier.clickable(onClick = onRename), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                fontStyle = if (a.temp) FontStyle.Italic else FontStyle.Normal
            )
            WavyBar(
                frac = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, playing = playing,
                color = accent, track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                onSeek = { f ->
                    if (!mine) NotePlayer.toggle(ctx, a.uri, a.playUri(ctx))
                    NotePlayer.seekTo(f)
                }
            )
            Text(
                Voices.clock(pos) + " / " + Voices.clock(dur) + if (a.temp) "   · " + tr("temporary name, tap to rename") else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- thumbnails and icons

private fun iconFor(kind: String): ImageVector = when (kind) {
    "image" -> Icons.Default.Image
    "video" -> Icons.Default.Movie
    "audio" -> Icons.Default.MusicNote
    "pdf" -> Icons.Default.PictureAsPdf
    "link" -> Icons.Default.Link
    "note" -> Icons.Default.Description
    else -> Icons.Default.InsertDriveFile
}

private fun loadThumb(ctx: Context, a: Attach): ImageBitmap? = try {
    val uri = Uri.parse(a.uri)
    if (a.kind == "video") {
        var b: android.graphics.Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            b = try { ctx.contentResolver.loadThumbnail(uri, Size(320, 320), null) } catch (_: Throwable) { null }
        }
        if (b == null) {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(ctx, uri); b = r.getFrameAtTime(1_000_000L) } finally { try { r.release() } catch (_: Throwable) {} }
        }
        b?.asImageBitmap()
    } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= 320 && o.outHeight / (s * 2) >= 320) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
    }
} catch (_: Throwable) { null }

@Composable
private fun Thumb(a: Attach, tint: Color) {
    val ctx = LocalContext.current
    var bmp by remember(a.uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(a.uri) { bmp = withContext(Dispatchers.IO) { loadThumb(ctx, a) } }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(iconFor(a.kind), null, tint = tint)
        if (b != null && a.kind == "video") Icon(Icons.Default.PlayArrow, null, tint = Color.White)
    }
}

// ---------------------------------------------------------------- small dialogs

@Composable
fun NameDialog(title: String, initial: String, hint: String, note: String, onSave: (String) -> Unit, onCancel: () -> Unit, cancelText: String = tr("Cancel")) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(hint) })
                if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { onSave(t.trim()) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancelText) } }
    )
}

private class CreateAny : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun mimeFor(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}

private fun openAttach(ctx: Context, a: Attach, onOpenNote: (Long) -> Unit) {
    try {
        when (a.kind) {
            "note" -> a.uri.toLongOrNull()?.let(onOpenNote)
            "link" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> {
                val u = Uri.parse(a.uri)
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(u, ctx.contentResolver.getType(u) ?: "*/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    } catch (_: Exception) {
        Toast.makeText(ctx, tr("No app can open this"), Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------- the list inside a note

/** All attachments of a note, in order. In arrange mode every row gets a drag handle and a remove button. */
@Composable
fun AttachSection(atts: SnapshotStateList<Attach>, arrange: Boolean, accent: Color, onOpenNote: (Long) -> Unit) {
    if (atts.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rs = remember { ReorderState() }
    var renameIdx by remember { mutableIntStateOf(-1) }
    var exportIdx by remember { mutableIntStateOf(-1) }

    val exporter = rememberLauncherForActivityResult(CreateAny()) { dest: Uri? ->
        val a = atts.getOrNull(exportIdx)
        exportIdx = -1
        if (dest != null && a != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(a.playUri(ctx))?.use { i ->
                        ctx.contentResolver.openOutputStream(dest)?.use { o -> i.copyTo(o) }
                    }
                    true
                } catch (_: Exception) { false }
            }
            Toast.makeText(ctx, tr(if (ok) "Saved a copy" else "Couldn't save the copy"), Toast.LENGTH_SHORT).show()
        }
    }

    fun remove(i: Int) {
        val a = atts.getOrNull(i) ?: return
        if (NotePlayer.current == a.uri) NotePlayer.stop()
        a.localFile(ctx)?.let { try { it.delete() } catch (_: Exception) {} }   // only our own recordings; picked files stay where they are
        atts.removeAt(i)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            tr("Attachments") + "  ·  " + atts.size, Modifier.padding(start = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            atts.forEachIndexed { i, a ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.reorderRow(rs, i).fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (arrange) Icon(
                        Icons.Default.DragHandle, tr("Drag to move"),
                        Modifier.reorderHandle(rs, i, atts.size) { f, t -> atts.add(t, atts.removeAt(f)) }.padding(10.dp).size(24.dp)
                    )
                    Box(Modifier.weight(1f)) {
                        if (a.kind == "audio") MiniPlayer(a, accent, onRename = { renameIdx = i })
                        else Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { openAttach(ctx, a, onOpenNote) }.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (a.kind == "image" || a.kind == "video") Thumb(a, accent)
                            else Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Icon(iconFor(a.kind), null, tint = accent) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.name.ifBlank { a.uri }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                val sub = when (a.kind) {
                                    "link" -> a.uri
                                    "note" -> tr("Linked note")
                                    "video" -> tr("Video") + if (a.ms > 0) "  ·  " + Voices.clock(a.ms) else ""
                                    "image" -> tr("Photo")
                                    "pdf" -> "PDF"
                                    else -> tr("File")
                                }
                                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (arrange) IconButton(onClick = { remove(i) }) { Icon(Icons.Default.Delete, tr("Remove")) }
                    else Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (a.kind != "audio") DropdownMenuItem(text = { Text(tr("Open")) }, onClick = { menu = false; openAttach(ctx, a, onOpenNote) })
                            DropdownMenuItem(text = { Text(tr("Rename")) }, onClick = { menu = false; renameIdx = i })
                            if (a.kind != "link" && a.kind != "note") DropdownMenuItem(text = { Text(tr("Save a copy…")) }, onClick = {
                                menu = false
                                exportIdx = i
                                val base = if (a.isLocal) Attach.cleanName(a.name) + a.ext() else a.name
                                exporter.launch(base to mimeFor(base))
                            })
                            DropdownMenuItem(text = { Text(tr("Remove")) }, onClick = { menu = false; remove(i) })
                        }
                    }
                }
            }
        }
    }

    atts.getOrNull(renameIdx)?.let { a ->
        NameDialog(
            title = tr("Rename"), initial = if (a.temp) "" else a.name, hint = a.name,
            note = if (a.isLocal) tr("Type the name without an extension; the right one is added when you save a copy.") else "",
            onSave = { n -> atts[renameIdx] = a.copy(name = n, temp = false); renameIdx = -1 },
            onCancel = { renameIdx = -1 }
        )
    }
}

// ---------------------------------------------------------------- adding things

/** The "+" sheet: photos, any file, a recording, a link, a link to another note. Keep it composed even when hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachAdder(show: Boolean, onDismiss: () -> Unit, atts: SnapshotStateList<Attach>, store: Store, currentNoteId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkDlg by remember { mutableStateOf(false) }
    var noteDlg by remember { mutableStateOf(false) }
    var recDlg by remember { mutableStateOf(false) }
    var nameIdx by remember { mutableIntStateOf(-1) }

    fun addUris(uris: List<Uri>) {
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.map { u ->
                    try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    val name = MediaInfo.nameOf(ctx, u)
                    val kind = Attach.kindOf(ctx.contentResolver.getType(u), name)
                    val ms = if (kind == "audio" || kind == "video") MediaInfo.durationMs(ctx, u) else 0L
                    Attach(kind, u.toString(), name, ms)
                }
            }
            atts.addAll(added)
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> addUris(l) }
    val pickAny = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> addUris(l) }

    if (show) ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 28.dp)) {
            SheetRow(Icons.Default.Image, tr("Photos and videos")) { onDismiss(); pickMedia.launch(arrayOf("image/*", "video/*")) }
            SheetRow(Icons.Default.AttachFile, tr("Attach file (PDF, music, anything)")) { onDismiss(); pickAny.launch(arrayOf("*/*")) }
            SheetRow(Icons.Default.Mic, tr("Record audio")) { onDismiss(); recDlg = true }
            SheetRow(Icons.Default.Link, tr("Add a link")) { onDismiss(); linkDlg = true }
            SheetRow(Icons.Default.Description, tr("Link a note")) { onDismiss(); noteDlg = true }
        }
    }

    if (recDlg) VoiceRecordDialog(tr("Voice note"), { recDlg = false }) { f, ms ->
        atts.add(Attach("audio", "voice:$f", Attach.tempName(), ms, true))
        if (store.askRecName) nameIdx = atts.lastIndex
    }

    atts.getOrNull(nameIdx)?.let { a ->
        NameDialog(
            title = tr("Name this recording"), initial = "", hint = a.name,
            note = tr("Type just the name. The file type is added for you. Skip to keep the temporary name."),
            onSave = { n -> atts[nameIdx] = a.copy(name = n, temp = false); nameIdx = -1 },
            onCancel = { nameIdx = -1 }, cancelText = tr("Skip")
        )
    }

    if (linkDlg) {
        var url by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkDlg = false },
            title = { Text(tr("Add a link")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Web address")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Title (optional)")) })
                }
            },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = {
                    val u = url.trim().let { if ("://" in it) it else "https://$it" }
                    atts.add(Attach("link", u, label.trim().ifEmpty { url.trim() }))
                    linkDlg = false
                }) { Text(tr("Add")) }
            },
            dismissButton = { TextButton(onClick = { linkDlg = false }) { Text(tr("Cancel")) } }
        )
    }

    if (noteDlg) {
        val others = remember { NotesDb.get(ctx).all().filter { it.trashedAt == 0L && it.id != currentNoteId }.sortedByDescending { it.modified } }
        AlertDialog(
            onDismissRequest = { noteDlg = false },
            title = { Text(tr("Link a note")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (others.isEmpty()) Text(tr("No other notes yet."))
                    others.forEach { n ->
                        val label = n.title.ifBlank { n.body.lineSequence().firstOrNull { it.isNotBlank() } ?: n.items.firstOrNull { it.text.isNotBlank() }?.text ?: tr("Untitled") }
                        TextButton(onClick = {
                            atts.add(Attach("note", n.id.toString(), label))
                            noteDlg = false
                        }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { noteDlg = false }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
