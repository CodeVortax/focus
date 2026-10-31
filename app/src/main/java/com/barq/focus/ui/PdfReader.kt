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

import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.barq.focus.data.InkStore
import com.barq.focus.data.InkStroke
import com.barq.focus.data.PdfEntry
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.CardSwatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Tool { PEN, HIGHLIGHT, ERASER }

private sealed class Act {
    class Add(val s: InkStroke) : Act()
    class Remove(val s: InkStroke) : Act()
}

private val PEN_W = floatArrayOf(0.0022f, 0.0045f, 0.009f)   // fraction of the page width
private val HI_W = floatArrayOf(0.018f, 0.030f, 0.046f)

private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
)
private val SEPIA = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
)

private fun snapScale(s: Float) = when {
    s < 1.25f -> 1f
    s < 1.75f -> 1.5f
    else -> 2f
}

/**
 * Our reader: continuous scroll, real two-axis pinch/pan zoom (works diagonally, like any normal viewer),
 * pen / highlighter / eraser that stay on the page (saved per PDF), bookmarks, day / sepia / night.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(store: Store, entry: PdfEntry, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val doc = remember { try { PdfDoc(ctx, Uri.parse(entry.uri)) } catch (_: Exception) { null } }
    var ready by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = entry.lastPage.coerceAtLeast(0))

    // camera: read only in layout/draw phases, so pinching never recomposes the pages
    val scaleS = remember { mutableFloatStateOf(1f) }
    val txS = remember { mutableFloatStateOf(0f) }
    val viewW = remember { mutableFloatStateOf(0f) }
    var renderScale by remember { mutableFloatStateOf(1f) }

    var mode by remember { mutableIntStateOf(store.pdfMode) }
    var annot by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.values()[store.pdfTool.coerceIn(0, 2)]) }
    var penColor by remember { mutableIntStateOf(store.penColor) }
    var hiColor by remember { mutableIntStateOf(store.hiColor) }
    var penSize by remember { mutableIntStateOf(store.penSize.coerceIn(0, 2)) }
    var hiSize by remember { mutableIntStateOf(store.hiSize.coerceIn(0, 2)) }
    var pencil by remember { mutableStateOf(store.penPencil) }
    var straight by remember { mutableStateOf(store.hiStraight) }
    val color = if (tool == Tool.HIGHLIGHT) hiColor else penColor
    val sizeIdx = if (tool == Tool.HIGHLIGHT) hiSize else penSize
    var chrome by remember { mutableStateOf(true) }          // the top bar: hides while you read, tap the page to bring it back
    var sliderShown by remember { mutableStateOf(false) }
    var sliderDrag by remember { mutableStateOf(false) }
    var stylusOnly by remember { mutableStateOf(store.penStylusOnly) }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val undo = remember { mutableStateListOf<Act>() }
    val redo = remember { mutableStateListOf<Act>() }
    var inkRev by remember { mutableIntStateOf(0) }
    var bookmarks by remember { mutableStateOf(store.bookmarks(entry.uri)) }
    var jump by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val page by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // reading is full screen: no gray gesture/navigation bar. A swipe from the edge brings the bars back for a moment.
    val window = remember(view) { var c = view.context; while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext; (c as? android.app.Activity)?.window }
    DisposableEffect(window) {
        val ctrl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose { ctrl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(chrome, window) {
        val ctrl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) } ?: return@LaunchedEffect
        ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        if (chrome) ctrl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars()) else ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    }
    // scrolling hides the top bar; the page slider shows while you move and for a moment after
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) { chrome = false; sliderShown = true } else { delay(1500); sliderShown = false }
    }

    LaunchedEffect(Unit) {
        doc?.loadRatios()
        strokes.addAll(withContext(Dispatchers.IO) { InkStore.load(ctx, entry.uri) })
        ready = true
    }
    // sharper pages after you stop pinching (re-render at the snapped zoom)
    LaunchedEffect(Unit) {
        snapshotFlow { scaleS.floatValue }.collectLatest { s -> delay(280); renderScale = snapScale(s) }
    }
    LaunchedEffect(inkRev) {
        if (inkRev > 0) {
            delay(700)
            val snap = strokes.toList()
            withContext(Dispatchers.IO) { InkStore.save(ctx, entry.uri, snap) }
        }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            InkStore.save(ctx, entry.uri, strokes.toList())
            doc?.let { store.upsertPdf(entry.copy(lastPage = listState.firstVisibleItemIndex, pages = it.count)); it.close() }
        }
    }

    fun clampTx() {
        val min = viewW.floatValue * (1f - scaleS.floatValue)
        txS.floatValue = txS.floatValue.coerceIn(min, 0f)
    }
    /** Zoom by [f] around the point (fx, fy): the spot under your fingers stays under your fingers. */
    fun zoomBy(f: Float, fx: Float, fy: Float) {
        val s0 = scaleS.floatValue
        val s1 = (s0 * f).coerceIn(1f, 4f)
        if (s1 == s0) return
        txS.floatValue = fx - (fx - txS.floatValue) * s1 / s0
        listState.dispatchRawDelta(fy / s0 - fy / s1)
        scaleS.floatValue = s1
        clampTx()
    }
    fun panBy(dx: Float, dy: Float) {
        txS.floatValue += dx
        clampTx()
        listState.dispatchRawDelta(-dy / scaleS.floatValue)
    }
    fun resetZoom() { scaleS.floatValue = 1f; txS.floatValue = 0f }

    fun addStroke(s: InkStroke) { strokes.add(s); undo.add(Act.Add(s)); redo.clear(); inkRev++ }
    fun eraseStroke(s: InkStroke) { if (strokes.remove(s)) { undo.add(Act.Remove(s)); redo.clear(); inkRev++ } }
    fun doUndo() {
        if (undo.isEmpty()) return
        val a = undo.removeAt(undo.lastIndex)
        when (a) { is Act.Add -> strokes.remove(a.s); is Act.Remove -> strokes.add(a.s) }
        redo.add(a); inkRev++
    }
    fun doRedo() {
        if (redo.isEmpty()) return
        val a = redo.removeAt(redo.lastIndex)
        when (a) { is Act.Add -> strokes.add(a.s); is Act.Remove -> strokes.remove(a.s) }
        undo.add(a); inkRev++
    }
    fun clearPage() {
        strokes.filter { it.page == page }.forEach { s -> if (strokes.remove(s)) undo.add(Act.Remove(s)) }
        redo.clear(); inkRev++
    }

    val bg = when (mode) { 2 -> Color.Black; 1 -> Color(0xFFD9CBB0); else -> MaterialTheme.colorScheme.surfaceVariant }

    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Box(Modifier.fillMaxSize().background(bg)) {
            when {
                doc == null || doc.count == 0 -> Text(tr("Could not open this PDF."), Modifier.align(Alignment.Center))
                !ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wPx = constraints.maxWidth
                        val hPx = constraints.maxHeight
                        SideEffect { viewW.floatValue = wPx.toFloat() }
                        Box(
                            Modifier.fillMaxSize().clipToBounds()
                                // pinch (zoom + pan together) with two fingers; with one finger the list scrolls vertically
                                // and we add the horizontal movement ourselves, so panning works in both axes at once
                                .pointerInput(annot, stylusOnly) {
                                    awaitEachGesture {
                                        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val penFirst = first.type == PointerType.Stylus || (annot && !stylusOnly)
                                        var multi = false
                                        var prevC = first.position
                                        var prevSpan = 0f
                                        do {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val pressed = ev.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                val cx = pressed.map { it.position.x }.average().toFloat()
                                                val cy = pressed.map { it.position.y }.average().toFloat()
                                                val span = pressed.map { (it.position - Offset(cx, cy)).getDistance() }.average().toFloat()
                                                if (multi && prevSpan > 0f) {
                                                    zoomBy(span / prevSpan, cx, cy)
                                                    panBy(cx - prevC.x, cy - prevC.y)
                                                }
                                                multi = true; prevC = Offset(cx, cy); prevSpan = span
                                                ev.changes.forEach { it.consume() }
                                            } else if (pressed.size == 1) {
                                                if (multi) { multi = false; prevSpan = 0f }
                                                else if (scaleS.floatValue > 1f && !penFirst) {
                                                    val dx = pressed[0].positionChange().x
                                                    if (dx != 0f) { txS.floatValue += dx; clampTx() }
                                                }
                                            }
                                        } while (ev.changes.any { it.pressed })
                                    }
                                }
                                .pointerInput(annot) {
                                    if (!annot) detectTapGestures(onTap = { chrome = !chrome }, onDoubleTap = { o ->
                                        scope.launch {
                                            val from = scaleS.floatValue
                                            val to = if (from > 1.05f) 1f else 2.5f
                                            animate(from, to, animationSpec = tween(260)) { v, _ -> zoomBy(v / scaleS.floatValue, o.x, o.y) }
                                            if (to == 1f) resetZoom()
                                        }
                                    })
                                }
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .layout { m, _ ->
                                        val h = (hPx / scaleS.floatValue).roundToInt().coerceAtLeast(1)
                                        val p = m.measure(Constraints.fixed(wPx, h))
                                        layout(wPx, h) { p.place(0, 0) }
                                    }
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = scaleS.floatValue; scaleY = scaleS.floatValue
                                        translationX = txS.floatValue
                                    },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(doc.count) { i ->
                                    PdfPageItem(
                                        doc, i, (wPx * renderScale).roundToInt(), mode,
                                        strokes.filter { it.page == i },
                                        annot, tool, color,
                                        if (tool == Tool.HIGHLIGHT) HI_W[sizeIdx] else PEN_W[sizeIdx],
                                        stylusOnly, pencil, straight, { st -> addStroke(st) }, { st -> eraseStroke(st) }
                                    )
                                }
                            }
                        }

                        // page slider on the edge: appears while you scroll, drag it to jump
                        val trackTop = with(androidx.compose.ui.platform.LocalDensity.current) { 90.dp.toPx() }
                        val trackH = (hPx - 2 * trackTop).coerceAtLeast(1f)
                        AnimatedVisibility(
                            visible = sliderShown || sliderDrag, modifier = Modifier.align(Alignment.CenterEnd),
                            enter = fadeIn(), exit = fadeOut()
                        ) {
                            val frac = if (doc.count <= 1) 0f else page.toFloat() / (doc.count - 1)
                            Box(
                                Modifier.fillMaxHeight().width(56.dp)
                                    .pointerInput(doc.count) {
                                        detectDragGestures(
                                            onDragStart = { sliderDrag = true },
                                            onDragEnd = { sliderDrag = false }, onDragCancel = { sliderDrag = false }
                                        ) { c, _ ->
                                            c.consume()
                                            val f = ((c.position.y - trackTop) / trackH).coerceIn(0f, 1f)
                                            scope.launch { listState.scrollToItem((f * (doc.count - 1)).roundToInt()) }
                                        }
                                    }
                            ) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 90.dp).fillMaxHeight().padding(bottom = 90.dp).width(4.dp)
                                        .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                                )
                                val thumbY = (trackTop + frac * trackH) - with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
                                Row(
                                    Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbY.roundToInt()) }.padding(end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${page + 1} / ${doc.count}",
                                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
                                    )
                                    Box(Modifier.padding(start = 6.dp).size(width = 12.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }

                        if (annot) FloatingInkBar(
                            tool, { tool = it; store.pdfTool = it.ordinal },
                            color, { c -> if (tool == Tool.HIGHLIGHT) { hiColor = c; store.hiColor = c } else { penColor = c; store.penColor = c } },
                            sizeIdx, { i -> if (tool == Tool.HIGHLIGHT) { hiSize = i; store.hiSize = i } else { penSize = i; store.penSize = i } },
                            pencil, { pencil = it; store.penPencil = it }, straight, { straight = it; store.hiStraight = it },
                            undo.isNotEmpty(), redo.isNotEmpty(), { doUndo() }, { doRedo() }, { clearPage() },
                            stylusOnly, { stylusOnly = it; store.penStylusOnly = it }, { annot = false },
                            wPx.toFloat(), hPx.toFloat(), store.inkBarX, store.inkBarY, { x, y -> store.inkBarX = x; store.inkBarY = y }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = chrome, modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
            ) {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        if (doc != null && doc.count > 0) Text("${page + 1} / ${doc.count}", style = MaterialTheme.typography.labelLarge)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { bookmarks = store.toggleBookmark(entry.uri, page) }) {
                        Icon(if (page in bookmarks) Icons.Default.Star else Icons.Default.StarBorder, "Bookmark")
                    }
                    IconButton(onClick = { annot = !annot; if (annot) chrome = false }) {
                        Icon(Icons.Default.Edit, "Pen", tint = if (annot) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = {
                        mode = (mode + 1) % 3
                        store.pdfMode = mode
                        Toast.makeText(ctx, tr(when (mode) { 1 -> "Sepia"; 2 -> "Night"; else -> "Day" }), Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Brightness4, "Reading mode") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("Go to")) }, onClick = { menu = false; jump = true })
                            DropdownMenuItem(text = { Text(tr("Bookmarks")) }, onClick = { menu = false; showBookmarks = true })
                            DropdownMenuItem(text = { Text(tr("Reset zoom")) }, onClick = { menu = false; resetZoom() })
                        }
                    }
                }
            )
            }
        }
    }

    if (jump && doc != null) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { jump = false },
            title = { Text(tr("Go to page")) },
            text = {
                OutlinedTextField(
                    t, { t = it.filter(Char::isDigit).take(5) }, singleLine = true,
                    label = { Text("1 - ${doc.count}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = (t.toIntOrNull() ?: 1).coerceIn(1, doc.count)
                    scope.launch { listState.scrollToItem(n - 1) }
                    jump = false
                }) { Text(tr("Go")) }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text(tr("Cancel")) } }
        )
    }

    if (showBookmarks) AlertDialog(
        onDismissRequest = { showBookmarks = false },
        title = { Text(tr("Bookmarks")) },
        text = {
            if (bookmarks.isEmpty()) Text(tr("No bookmarks yet. Tap the star on a page."))
            else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                bookmarks.forEach { b ->
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { listState.scrollToItem(b) }; showBookmarks = false }.padding(vertical = 10.dp)) {
                        Text(trf("Page %d", b + 1), Modifier.weight(1f))
                        Text("✕", Modifier.clickable { bookmarks = store.toggleBookmark(entry.uri, b) }.padding(horizontal = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text(tr("Done")) } }
    )
}

@Composable
private fun PdfPageItem(
    doc: PdfDoc, index: Int, renderW: Int, mode: Int, strokes: List<InkStroke>,
    annot: Boolean, tool: Tool, color: Int, widthFrac: Float, stylusOnly: Boolean, pencil: Boolean, straight: Boolean,
    onStroke: (InkStroke) -> Unit, onErase: (InkStroke) -> Unit,
) {
    val ratio = doc.ratios[index]
    // keep a page bitmap under ~8 megapixels so zooming can't run the phone out of memory
    val w = minOf(renderW, sqrt(8_000_000f / ratio).toInt()).coerceAtLeast(1)
    var bmp by remember(index) { mutableStateOf<Bitmap?>(doc.cached(index, w)) }
    LaunchedEffect(index, w) {
        val c = doc.cached(index, w)
        if (c != null) bmp = c else doc.render(index, w)?.let { bmp = it }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f / ratio)) {
        val b = bmp
        if (b != null) Image(
            b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
            colorFilter = when (mode) { 2 -> INVERT; 1 -> SEPIA; else -> null }
        ) else CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
        InkLayer(index, strokes, annot, tool, color, widthFrac, stylusOnly, pencil, straight, onStroke, onErase)
    }
}

@Composable
private fun InkLayer(
    page: Int, strokes: List<InkStroke>, annot: Boolean, tool: Tool, color: Int, widthFrac: Float, stylusOnly: Boolean, pencil: Boolean, straight: Boolean,
    onStroke: (InkStroke) -> Unit, onErase: (InkStroke) -> Unit,
) {
    val live = remember { mutableStateListOf<Float>() }       // the stroke being drawn, normalised x,y pairs
    var box by remember { mutableStateOf(IntSize.Zero) }
    val strokesNow by rememberUpdatedState(strokes)
    val colorNow by rememberUpdatedState(color)
    val widthNow by rememberUpdatedState(widthFrac)
    val toolNow by rememberUpdatedState(tool)
    val pencilNow by rememberUpdatedState(pencil)
    val straightNow by rememberUpdatedState(straight)
    val onStrokeNow by rememberUpdatedState(onStroke)
    val onEraseNow by rememberUpdatedState(onErase)

    Canvas(
        Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput(annot, stylusOnly) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val drawing = down.type == PointerType.Stylus || (annot && !stylusOnly)
                    if (!drawing || box.width == 0 || box.height == 0) return@awaitEachGesture
                    down.consume()
                    val t = toolNow
                    val bw = box.width.toFloat()
                    val bh = box.height.toFloat()

                    fun erase(p: Offset) {
                        val r = 24f
                        for (s in strokesNow) {
                            var k = 0
                            var hit = false
                            while (k < s.pts.size - 1) {
                                val dx = s.pts[k] * bw - p.x
                                val dy = s.pts[k + 1] * bh - p.y
                                if (dx * dx + dy * dy < r * r) { hit = true; break }
                                k += 2
                            }
                            if (hit) onEraseNow(s)
                        }
                    }

                    live.clear()
                    var last = down.position
                    if (t == Tool.ERASER) erase(down.position) else { live.add(last.x / bw); live.add(last.y / bh) }
                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        // a second finger (or the pinch handler taking over) cancels the stroke
                        if (ev.changes.count { it.pressed } > 1 || ev.changes.any { it.isConsumed }) { cancelled = true; break }
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        val p = c.position
                        if ((p - last).getDistance() < 1.5f) continue
                        last = p
                        if (t == Tool.ERASER) erase(p)
                        else if (t == Tool.HIGHLIGHT && straightNow) {
                            // a straight highlighter keeps the first point and follows the finger with the last one
                            val fx = live[0]; val fy = live[1]
                            live.clear(); live.add(fx); live.add(fy); live.add(p.x / bw); live.add(p.y / bh)
                        } else { live.add(p.x / bw); live.add(p.y / bh) }
                    }
                    if (!cancelled && t != Tool.ERASER && live.isNotEmpty()) {
                        onStrokeNow(InkStroke(page, colorNow, widthNow, t == Tool.HIGHLIGHT, live.toFloatArray(), t == Tool.PEN && pencilNow))
                    }
                    live.clear()
                }
            }
    ) {
        for (s in strokes) drawInk(s.pts, size.width, size.height, Color(s.color), s.w * size.width, s.highlighter, s.pencil)
        if (live.isNotEmpty() && toolNow != Tool.ERASER) {
            drawInk(live.toFloatArray(), size.width, size.height, Color(colorNow), widthNow * size.width, toolNow == Tool.HIGHLIGHT, toolNow == Tool.PEN && pencilNow)
        }
    }
}

private fun DrawScope.drawInk(p: FloatArray, w: Float, h: Float, color: Color, widthPx: Float, highlighter: Boolean, pencil: Boolean = false) {
    if (p.size < 2) return
    val c = if (highlighter) color.copy(alpha = 0.38f) else if (pencil) color.copy(alpha = 0.55f) else color
    if (p.size < 4) { drawCircle(c, widthPx / 2f, Offset(p[0] * w, p[1] * h)); return }
    val path = Path()
    path.moveTo(p[0] * w, p[1] * h)
    var k = 2
    while (k < p.size - 1) {
        val ax = p[k - 2] * w; val ay = p[k - 1] * h
        val bx = p[k] * w; val by = p[k + 1] * h
        path.quadraticBezierTo(ax, ay, (ax + bx) / 2f, (ay + by) / 2f)   // smooth the curve between samples
        k += 2
    }
    path.lineTo(p[p.size - 2] * w, p[p.size - 1] * h)
    if (pencil) {
        // pencil: a soft thin base with a grainy dashed line over it
        drawPath(path, c, style = Stroke(width = widthPx * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = widthPx * 0.55f, cap = StrokeCap.Butt, join = StrokeJoin.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(widthPx * 0.9f, widthPx * 0.5f, widthPx * 0.3f, widthPx * 0.6f))))
    } else drawPath(path, c, style = Stroke(width = widthPx, cap = if (highlighter) StrokeCap.Butt else StrokeCap.Round, join = StrokeJoin.Round))
}
