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

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barq.focus.data.EpLock
import com.barq.focus.data.Episode
import com.barq.focus.data.FocusCard
import com.barq.focus.data.MediaInfo
import com.barq.focus.data.Store
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Minutes of bank one episode costs: its length rounded up (40 if the length is unknown). */
private fun costMin(e: Episode): Int = if (e.ms > 0L) ((e.ms + 59_999L) / 60_000L).toInt().coerceAtLeast(1) else 40

const val REWIND_SEC = 300          // tokens bought before 0.24 had this many extra seconds
const val WATCH_FACTOR = 1.5f       // a token now lasts 1.5 times the episode, so you can seek back and rewatch
const val REFUND_MAX = 0.20f        // an episode can be returned only while you have used at most this share of it

private fun hms(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** The episodes of a series: one token (the episode's length from the bank) = one episode, with 1.5 times its length to watch and rewind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(store: Store, card0: FocusCard, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val card = remember(rev) { store.cards().firstOrNull { it.id == card0.id } ?: card0 }
    val bankSec = remember(rev) { store.bankSec() }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableIntStateOf(-1) }
    var giveBack by remember { mutableIntStateOf(-1) }
    val next = card.eps.indexOfFirst { !it.done }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun setLock(on: Boolean) {
        if (card.folder.isEmpty()) { toast(tr("These episodes were not picked from a folder, so they can't be locked.")); return }
        busy = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                val tree = Uri.parse(card.folder)
                if (on) EpLock.lock(ctx, tree, card.eps) else EpLock.unlock(ctx, tree, card.eps)
            }
            store.updateCard(card.copy(eps = res))
            busy = false; rev++
            toast(tr(if (on) "Locked: no other app can see these files" else "Unlocked: files have their real names back"))
        }
    }

    fun tryPlay(i: Int) {
        val e = card.eps[i]
        if (e.done) { toast(tr("Already watched")); return }
        if (store.token(card.id, i) > 0) { onPlay(i); return }
        confirm = i
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val anyLocked = card.eps.any { it.locked }
                            if (card.folder.isNotEmpty()) {
                                if (card.eps.any { !it.locked }) DropdownMenuItem(text = { Text(tr("Lock the files")) }, onClick = { menu = false; setLock(true) })
                                if (anyLocked) DropdownMenuItem(text = { Text(tr("Unlock the files")) }, onClick = { menu = false; setLock(false) })
                            }
                            DropdownMenuItem(text = { Text(tr("Mark all as not watched")) }, onClick = {
                                menu = false
                                store.updateCard(card.copy(eps = card.eps.map { it.copy(done = false) })); rev++
                            })
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trf("Treat bank: %s min", minText(bankSec)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("One episode costs its length from the bank. You then get that episode, with up to 1.5 times its length to watch and seek back. Barely started it? Return it and get the minutes back."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(card.eps) { i, e ->
                    val left = store.token(card.id, i)
                    val isNext = i == next
                    Card(
                        Modifier.fillMaxWidth().clickable { tryPlay(i) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    trf("%d min", costMin(e)) + when {
                                        e.done -> "  ·  " + tr("watched")
                                        left > 0 -> "  ·  " + trf("%s left on the token", minText(left))
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                                val posMs = store.getLong("media_pos_${card.id}_$i")
                                if (!e.done && posMs > 0L && e.ms > 0L) {
                                    LinearProgressIndicator(progress = { (posMs.toFloat() / e.ms).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp))
                                    Text(trf("Stopped at %s of %s", hms(posMs), hms(e.ms)), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (left > 0 && !e.done) TextButton(onClick = { giveBack = i }) { Text(tr("Return")) }
                            Text(if (e.done) "✓" else if (e.locked) "🔒" else if (left > 0) "▶" else "", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }

    if (giveBack >= 0) {
        val i = giveBack
        val e = card.eps[i]
        val lenSec = if (e.ms > 0L) (e.ms / 1000L).toInt() else costMin(e) * 60
        val total = store.tokenTotal(card.id, i).takeIf { it > 0 } ?: (lenSec + REWIND_SEC)
        val used = (total - store.token(card.id, i)).coerceAtLeast(0)
        val pct = (used * 100f / lenSec).toInt()
        val ok = used <= lenSec * REFUND_MAX
        val back = store.tokenCost(card.id, i).takeIf { it > 0 } ?: (costMin(e) * 60)
        AlertDialog(
            onDismissRequest = { giveBack = -1 },
            title = { Text(e.name, maxLines = 2) },
            text = {
                Text(
                    if (ok) trf("You used %d%% of it. Return it and get %d min back in the bank?", pct, back / 60)
                    else trf("You already used %d%% of it. Only episodes you barely started (up to %d%%) can be returned.", pct, (REFUND_MAX * 100).toInt())
                )
            },
            confirmButton = {
                if (ok) TextButton(onClick = {
                    giveBack = -1
                    val sec = store.refundToken(card.id, i, costMin(e) * 60)
                    rev++
                    toast(trf("Returned: %d min are back in the bank", sec / 60))
                }) { Text(tr("Return it")) }
            },
            dismissButton = { TextButton(onClick = { giveBack = -1 }) { Text(tr(if (ok) "Keep it" else "OK")) } }
        )
    }

    if (confirm >= 0) {
        val e = card.eps[confirm]
        val need = costMin(e)
        val enough = bankSec >= need * 60
        AlertDialog(
            onDismissRequest = { confirm = -1 },
            title = { Text(e.name, maxLines = 2) },
            text = {
                Text(
                    if (enough) trf("Spend %d min of the bank on this episode? You get up to 1.5 times its length to watch and seek back.", need)
                    else trf("You need %d min in the bank for this episode and have %s. Finish more tasks first.", need, minText(bankSec))
                )
            },
            confirmButton = {
                if (enough) TextButton(onClick = {
                    val i = confirm
                    confirm = -1
                    val lenSec = if (e.ms > 0L) (e.ms / 1000L).toInt() else need * 60
                    store.buyToken(card.id, i, (lenSec * WATCH_FACTOR).toInt(), need * 60)
                    onPlay(i)
                }) { Text(tr("Watch it")) }
            },
            dismissButton = { TextButton(onClick = { confirm = -1 }) { Text(tr(if (enough) "Not now" else "OK")) } }
        )
    }
}

// ---------------------------------------------------------------- choosing what a treat card plays

/** Asks whether the treat is one file or a whole series from a folder (with the lock option). */
@Composable
fun TreatSetupDialog(card: FocusCard, onDismiss: () -> Unit, onSingle: () -> Unit, onSaved: (FocusCard) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tree by remember { mutableStateOf<Uri?>(null) }
    var files by remember { mutableStateOf<List<EpLock.Child>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>() }
    var lock by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u == null) { onDismiss(); return@rememberLauncherForActivityResult }
        try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        busy = true
        scope.launch {
            val l = withContext(Dispatchers.IO) { EpLock.list(ctx, u).filter { EpLock.isMedia(it) }.sortedWith { a, b -> EpLock.natural(a.name, b.name) } }
            files = l; tree = u; busy = false
            if (l.isEmpty()) Toast.makeText(ctx, tr("No videos or audio files in that folder."), Toast.LENGTH_LONG).show()
        }
    }

    if (tree == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(card.title) },
            text = {
                Column {
                    Text(tr("What should this treat play?"), Modifier.padding(bottom = 8.dp))
                    TextButton(onClick = onSingle) { Text(tr("One video or audio file")) }
                    TextButton(enabled = !busy, onClick = { treePicker.launch(null) }) { Text(tr("A series: pick a folder, then the episodes")) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Pick the episodes")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picked.clear(); picked.addAll(files.map { it.docId }) }) { Text(tr("All")) }
                    TextButton(onClick = { picked.clear() }) { Text(tr("None")) }
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    files.forEach { f ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (f.docId in picked) picked.remove(f.docId) else picked.add(f.docId)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(f.docId in picked, { if (it) picked.add(f.docId) else picked.remove(f.docId) })
                            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Lock these files"))
                        Text(
                            tr("Renames them so no other app can open them. A hidden config in the same folder lets Focus restore them."),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Switch(lock, { lock = it })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() && !busy, onClick = {
                val t = tree ?: return@TextButton
                busy = true
                scope.launch {
                    val chosen = files.filter { it.docId in picked }
                    var eps = withContext(Dispatchers.IO) {
                        chosen.map { f ->
                            Episode(f.uri.toString(), f.name.substringBeforeLast('.'), MediaInfo.durationMs(ctx, f.uri), orig = f.name)
                        }
                    }
                    if (lock) eps = withContext(Dispatchers.IO) { EpLock.lock(ctx, t, eps) }
                    busy = false
                    onSaved(card.copy(eps = eps, folder = t.toString(), media = ""))
                }
            }) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
