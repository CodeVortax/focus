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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Foc
import com.barq.focus.data.PdfEntry
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PdfRenderer wrapper: one page open at a time, renders off the main thread, small bitmap cache. */
class PdfDoc(ctx: Context, uri: Uri) {
    private val pfd: ParcelFileDescriptor? = ctx.contentResolver.openFileDescriptor(uri, "r")
    private val renderer: PdfRenderer? = pfd?.let { PdfRenderer(it) }
    private val lock = Any()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    val count: Int = renderer?.pageCount ?: 0
    var ratios = FloatArray(count) { 1.414f }
        private set

    suspend fun loadRatios() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val r = renderer ?: return@withContext
            for (i in 0 until count) {
                r.openPage(i).use { p -> if (p.width > 0) ratios[i] = p.height.toFloat() / p.width }
            }
        }
    }

    fun cached(i: Int, w: Int): Bitmap? = cache.get("$i@$w")

    suspend fun render(i: Int, w: Int): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            cache.get("$i@$w") ?: try {
                val r = renderer ?: return@synchronized null
                val h = (w * ratios[i]).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AColor.WHITE)
                r.openPage(i).use { p -> p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                cache.put("$i@$w", bmp)
                bmp
            } catch (_: Throwable) { null }
        }
    }

    fun close() {
        synchronized(lock) {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PdfLibraryScreen(store: Store, onOpen: (PdfEntry) -> Unit, onOpenBuiltin: (PdfEntry) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(store.pdfs()) }
    var removing by remember { mutableStateOf<PdfEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { Foc.applyUri(ctx, uri); list = store.pdfs() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Library")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.Add, "Add PDF") }
        }
    ) { pad ->
        if (list.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("No PDFs yet. Tap + to add one."), Modifier.padding(24.dp))
        } else LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.uri }) { e ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(onClick = { onOpen(e) }, onLongClick = { removing = e }).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    if (e.pages > 0) {
                        Text(trf("Page %d of %d", e.lastPage + 1, e.pages), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { (e.lastPage + 1f) / e.pages }, modifier = Modifier.fillMaxWidth())
                    } else Text(tr("Not opened yet"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    removing?.let { e ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(trf("Remove \"%s\" from the library?", e.title)) },
            text = {
                Column {
                    TextButton(onClick = { removing = null; onOpenBuiltin(e) }) { Text(tr("Open in built-in reader")) }
                    Text(tr("The file itself is not deleted."), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { store.savePdfs(store.pdfs().filter { it.uri != e.uri }); list = store.pdfs(); removing = null }) { Text(tr("Remove")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Cancel")) } }
        )
    }
}

