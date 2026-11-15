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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.barq.focus.data.tr

object NotePaper {
    const val PLAIN = 0
    const val LINED = 1
    const val GRID = 2
    const val DOTS = 3
    const val CORNELL = 4
    const val PARCHMENT = 5

    val NAMES = listOf("Plain", "Lined", "Grid", "Dots", "Cornell", "Paper")
}

private fun DrawScope.drawPaperPattern(
    paper: Int,
    dark: Boolean,
    accent: Color,
    rtl: Boolean,
    compact: Boolean,
) {
    if (paper == NotePaper.PLAIN) return
    val lineColor = if (dark) Color.White.copy(alpha = if (compact) 0.14f else 0.10f)
    else Color(0xFF475569).copy(alpha = if (compact) 0.18f else 0.13f)
    val marginColor = if (dark) Color(0xFFFB7185).copy(alpha = 0.28f) else Color(0xFFE11D48).copy(alpha = 0.24f)

    if (paper == NotePaper.PARCHMENT) {
        val wash = if (dark) Color(0xFF3E3524).copy(alpha = 0.36f) else Color(0xFFFAF1D6).copy(alpha = 0.72f)
        drawRect(wash)
    }

    when (paper) {
        NotePaper.LINED, NotePaper.PARCHMENT, NotePaper.CORNELL -> {
            val step = (if (compact) 11.dp else 28.dp).toPx()
            var y = step
            while (y < size.height) {
                drawLine(
                    if (paper == NotePaper.PARCHMENT) lineColor.copy(alpha = lineColor.alpha * 0.75f) else lineColor,
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx()
                )
                y += step
            }
            if (paper == NotePaper.CORNELL) {
                val marginX = if (rtl) size.width - (if (compact) 14.dp else 44.dp).toPx() else (if (compact) 14.dp else 44.dp).toPx()
                drawLine(marginColor, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = 1.4.dp.toPx())
            }
        }
        NotePaper.GRID -> {
            val step = (if (compact) 10.dp else 22.dp).toPx()
            var x = step
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.9.dp.toPx())
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.9.dp.toPx())
                y += step
            }
        }
        NotePaper.DOTS -> {
            val step = (if (compact) 9.dp else 18.dp).toPx()
            val dotR = (if (compact) 1.0.dp else 1.4.dp).toPx()
            val dotColor = accent.copy(alpha = if (dark) 0.28f else 0.24f)
            var y = step * 0.7f
            while (y < size.height) {
                var x = step * 0.7f
                while (x < size.width) {
                    drawCircle(dotColor, radius = dotR, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}

@Composable
fun Modifier.notePaper(paper: Int, accentColor: Int, compact: Boolean = false): Modifier {
    if (paper == NotePaper.PLAIN) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (accentColor == 0) MaterialTheme.colorScheme.primary else Color(accentColor)
    return drawBehind {
        drawPaperPattern(paper, dark, accent, layoutDirection == LayoutDirection.Rtl, compact)
    }
}

@Composable
fun PaperPickerRow(selected: Int, accentColor: Int, onPick: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (accentColor == 0) MaterialTheme.colorScheme.primary else Color(accentColor)
    val baseBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotePaper.NAMES.forEachIndexed { idx, name ->
            val active = idx == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(idx) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(baseBg)
                        .border(
                            if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawPaperPattern(idx, dark, accent, layoutDirection == LayoutDirection.Rtl, compact = true)
                    }
                }
                Text(
                    tr(name),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
