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

import android.graphics.Color.HSVToColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** The five main colours of each tool; the sixth button opens a picker for any colour. */
val PEN_MAIN = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt())
val HI_MAIN = listOf(0xFFFDD835.toInt(), 0xFF66BB6A.toInt(), 0xFFF06292.toInt(), 0xFFFFA726.toInt(), 0xFF29B6F6.toInt())

/** A hold of [holdMs] picks the bar up; after that it follows the finger. A quick tap or a small move is left to the buttons. */
private fun Modifier.holdToDrag(holdMs: Long, onPickUp: () -> Unit, onMove: (Offset) -> Unit, onDrop: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var aborted = false
            withTimeoutOrNull(holdMs) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) { aborted = true; break }
                }
            }
            if (aborted) return@awaitEachGesture
            onPickUp()
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                val d = c.positionChange()
                if (d != Offset.Zero) { onMove(d); c.consume() }
            }
            onDrop()
        }
    }

private class SidePositioner(val above: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val y = (if (above) anchorBounds.top - popupContentSize.height - 8 else anchorBounds.bottom + 8)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

@Composable
private fun EraserIcon(tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        rotateAroundCenter(-35f) {
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.30f), Size(w * 0.76f, h * 0.40f), androidx.compose.ui.geometry.CornerRadius(w * 0.10f), style = Stroke(w * 0.09f))
            drawRect(tint, Offset(w * 0.12f, h * 0.30f), Size(w * 0.30f, h * 0.40f))
        }
        drawLine(tint, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.88f, h * 0.88f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateAroundCenter(deg: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    rotate(deg, center, block)

@Composable
private fun ToolIcon(tool: Tool, tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    when (tool) {
        Tool.PEN -> Icon(Icons.Default.Edit, tr("Pen"), modifier, tint = tint)
        Tool.HIGHLIGHT -> Icon(Icons.Default.BorderColor, tr("Highlighter"), modifier, tint = tint)
        Tool.ERASER -> EraserIcon(tint, modifier)
    }
}

/** A round icon button that can show it is the selected one. */
@Composable
private fun BarButton(selected: Boolean, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * The drawing bar: one line of icons that floats. Hold it briefly (or use the grip) to pick it up and move it anywhere;
 * left alone for 2.5 s it shrinks to one circle showing the tool in use (tap the circle to open it again).
 * Scrolls horizontally cleanly on narrow screens and re-clamps safely on rotation/resize.
 */
@Composable
fun FloatingInkBar(
    tool: Tool, onTool: (Tool) -> Unit,
    color: Int, onColor: (Int) -> Unit, sizeIdx: Int, onSize: (Int) -> Unit,
    pencil: Boolean, onPencil: (Boolean) -> Unit, straight: Boolean, onStraight: (Boolean) -> Unit,
    canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onClearPage: () -> Unit,
    stylusOnly: Boolean, onStylus: (Boolean) -> Unit, onDone: () -> Unit,
    areaW: Float, areaH: Float, savedX: Float, savedY: Float, onMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf<Offset?>(null) }
    var normX by remember { mutableFloatStateOf(savedX) }
    var normY by remember { mutableFloatStateOf(savedY) }
    var collapsed by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    var popup by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var confirm by remember { mutableIntStateOf(0) }          // 0 none, 1 first question, 2 second question
    var held by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val marginPx = with(density) { 8.dp.toPx() }
    val bottomDefaultPx = with(density) { 24.dp.toPx() }

    fun clampPos(o: Offset): Offset {
        if (areaW <= 0f || areaH <= 0f || size.width <= 0 || size.height <= 0) return o
        val minX = if (size.width + marginPx * 2f <= areaW) marginPx else ((areaW - size.width) / 2f).coerceAtLeast(0f)
        val maxX = if (size.width + marginPx * 2f <= areaW) (areaW - size.width - marginPx) else minX
        val minY = if (size.height + marginPx * 2f <= areaH) marginPx else ((areaH - size.height) / 2f).coerceAtLeast(0f)
        val maxY = if (size.height + marginPx * 2f <= areaH) (areaH - size.height - marginPx) else minY
        return Offset(o.x.coerceIn(minX, maxX.coerceAtLeast(minX)), o.y.coerceIn(minY, maxY.coerceAtLeast(minY)))
    }

    // Re-place & re-clamp whenever view dimensions, orientation, or bar collapse state change.
    LaunchedEffect(size, areaW, areaH, collapsed) {
        if (size.width > 0 && size.height > 0 && areaW > 0f && areaH > 0f) {
            val raw = if (normX >= 0f && normY >= 0f) {
                val spanX = (areaW - size.width - marginPx * 2f).coerceAtLeast(0f)
                val spanY = (areaH - size.height - marginPx * 2f).coerceAtLeast(0f)
                val minX = if (spanX > 0f) marginPx else ((areaW - size.width) / 2f).coerceAtLeast(0f)
                val minY = if (spanY > 0f) marginPx else ((areaH - size.height) / 2f).coerceAtLeast(0f)
                Offset(minX + normX.coerceIn(0f, 1f) * spanX, minY + normY.coerceIn(0f, 1f) * spanY)
            } else {
                Offset((areaW - size.width) / 2f, areaH - size.height - bottomDefaultPx)
            }
            pos = clampPos(raw)
        }
    }

    // idle for 2.5 s (nothing touched, not scrolling, nothing open, not being moved): shrink to the circle
    LaunchedEffect(touch, collapsed, popup, picker, confirm, held, scrollState.isScrollInProgress) {
        if (!collapsed && !popup && !picker && confirm == 0 && !held && !scrollState.isScrollInProgress) {
            delay(2500)
            collapsed = true
        }
    }
    fun poke() { touch++ }

    val cur = pos
    val lower = (cur?.y ?: 0f) + size.height / 2f > areaH / 2f
    val maxBarWidth = with(density) { ((areaW - marginPx * 2f).coerceAtLeast(180f)).toDp() }

    Box(
        modifier
            .offset { IntOffset((cur?.x ?: -10000f).roundToInt(), (cur?.y ?: 0f).roundToInt()) }
            .onSizeChanged { size = it }
            .holdToDrag(
                420L,
                onPickUp = { held = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onMove = { d -> pos = clampPos((pos ?: Offset.Zero) + d) },
                onDrop = {
                    held = false; poke()
                    val p = pos
                    if (p != null && areaW > size.width && areaH > size.height) {
                        val spanX = (areaW - size.width - marginPx * 2f).coerceAtLeast(1f)
                        val spanY = (areaH - size.height - marginPx * 2f).coerceAtLeast(1f)
                        normX = ((p.x - marginPx) / spanX).coerceIn(0f, 1f)
                        normY = ((p.y - marginPx) / spanY).coerceIn(0f, 1f)
                        onMoved(normX, normY)
                    }
                }
            )
    ) {
        if (collapsed) {
            Surface(
                Modifier.size(52.dp)
                    .then(if (held) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .clickable { collapsed = false; poke() },
                shape = CircleShape,
                tonalElevation = if (held) 14.dp else 8.dp,
                shadowElevation = if (held) 16.dp else 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (tool != Tool.ERASER) Box(Modifier.size(44.dp).border(3.dp, Color(color), CircleShape))
                    ToolIcon(tool, MaterialTheme.colorScheme.onSurface)
                }
            }
        } else {
            Surface(
                Modifier.widthIn(max = maxBarWidth)
                    .then(if (held) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)) else Modifier),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = if (held) 14.dp else 8.dp,
                shadowElevation = if (held) 16.dp else 10.dp
            ) {
                Row(
                    Modifier.horizontalScroll(scrollState).padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragIndicator,
                        tr("Hold to move"),
                        Modifier.padding(start = 2.dp, end = 2.dp).size(18.dp),
                        tint = if (held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    listOf(Tool.PEN, Tool.HIGHLIGHT, Tool.ERASER).forEach { t ->
                        BarButton(tool == t, onClick = {
                            poke()
                            if (tool == t && t != Tool.ERASER) popup = !popup else { onTool(t); popup = false }
                        }) { ToolIcon(t, if (tool == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                    BarButton(false, onClick = { poke(); picker = false; popup = !popup }, enabled = tool != Tool.ERASER) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(if (tool == Tool.ERASER) Color.Transparent else Color(color)).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                    BarButton(false, canUndo, { poke(); onUndo() }) { Icon(Icons.AutoMirrored.Filled.Undo, tr("Undo"), Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canUndo) 1f else 0.3f)) }
                    BarButton(false, canRedo, { poke(); onRedo() }) { Icon(Icons.AutoMirrored.Filled.Redo, tr("Redo"), Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canRedo) 1f else 0.3f)) }
                    BarButton(false, onClick = { poke(); confirm = 1 }) { Icon(Icons.Default.DeleteSweep, tr("Clear page"), Modifier.size(21.dp)) }
                    // the hand: pen works with the stylus only. A red hand with a line through it while it is on.
                    BarButton(stylusOnly, onClick = { poke(); onStylus(!stylusOnly) }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PanTool, tr("Stylus only"), Modifier.size(20.dp), tint = if (stylusOnly) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface)
                            if (stylusOnly) Canvas(Modifier.size(24.dp)) {
                                drawLine(Color(0xFFE53935), Offset(size.width * 0.1f, size.height * 0.9f), Offset(size.width * 0.9f, size.height * 0.1f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                    BarButton(false, onClick = onDone) { Icon(Icons.Default.Check, tr("Done"), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (popup && tool != Tool.ERASER) Popup(SidePositioner(!lower), onDismissRequest = { popup = false }, properties = PopupProperties(focusable = true)) {
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 10.dp, shadowElevation = 12.dp) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tool == Tool.PEN) {
                                FilterChip(!pencil, { onPencil(false) }, { Text(tr("Normal")) })
                                FilterChip(pencil, { onPencil(true) }, { Text(tr("Pencil")) })
                            } else {
                                FilterChip(!straight, { onStraight(false) }, { Text(tr("Normal")) })
                                FilterChip(straight, { onStraight(true) }, { Text(tr("Straight line")) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(10, 16, 22).forEachIndexed { i, d ->
                                Box(Modifier.size(36.dp).clip(CircleShape).clickable { onSize(i) }, contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(d.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == sizeIdx) 1f else 0.3f)))
                                }
                            }
                        }
                        val main = if (tool == Tool.PEN) PEN_MAIN else HI_MAIN
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            main.forEach { c ->
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                        .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                        .clickable { onColor(c) }
                                )
                            }
                            // the sixth: any colour. Shows the colour you picked when it is not one of the five.
                            val custom = color !in main
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (custom) Brush.linearGradient(listOf(Color(color), Color(color))) else Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                                    .border(if (custom) 3.dp else 1.dp, if (custom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable { picker = true },
                                contentAlignment = Alignment.Center
                            ) { if (!custom) Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
    if (picker) ColorPickerDialog(color, { picker = false }) { onColor(it); picker = false }
    if (confirm == 1) AlertDialog(
        onDismissRequest = { confirm = 0 },
        title = { Text(tr("Clear this page?")) },
        text = { Text(tr("This removes every pen and highlighter mark on this page.")) },
        confirmButton = { TextButton(onClick = { confirm = 2 }) { Text(tr("Continue")) } },
        dismissButton = { TextButton(onClick = { confirm = 0 }) { Text(tr("Cancel")) } }
    )
    if (confirm == 2) AlertDialog(
        onDismissRequest = { confirm = 0 },
        title = { Text(tr("Are you really sure?")) },
        text = { Text(tr("Last check. You can still undo it right after with the undo arrow.")) },
        confirmButton = { TextButton(onClick = { confirm = 0; onClearPage() }) { Text(tr("Yes, clear the page"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = 0 }) { Text(tr("Keep it")) } }
    )
}

/** Any colour: a hue bar and a square for saturation and brightness. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    val argb = HSVToColor(floatArrayOf(hue, sat, v))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pick a colour")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { p ->
                                sat = (p.x / size.width).coerceIn(0f, 1f); v = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            }) { c, _ -> c.consume(); sat = (c.position.x / size.width).coerceIn(0f, 1f); v = 1f - (c.position.y / size.height).coerceIn(0f, 1f) }
                        }
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color(HSVToColor(floatArrayOf(hue, 1f, 1f))))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawCircle(Color.White, 10.dp.toPx(), Offset(sat * size.width, (1f - v) * size.height), style = Stroke(3.dp.toPx()))
                }
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { p -> hue = (p.x / size.width).coerceIn(0f, 1f) * 360f }) { c, _ -> c.consume(); hue = (c.position.x / size.width).coerceIn(0f, 1f) * 360f }
                        }
                ) {
                    drawRect(Brush.horizontalGradient((0..6).map { Color(HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
                    drawCircle(Color.White, 12.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2f), style = Stroke(3.dp.toPx()))
                }
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(argb)))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text(tr("Use this colour")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
