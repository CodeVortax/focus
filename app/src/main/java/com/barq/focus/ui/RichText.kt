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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Fmt
import com.barq.focus.data.Span
import com.barq.focus.data.tr
import com.barq.focus.ui.theme.CardSwatches

fun buildRich(text: String, spans: List<Span>): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { sp ->
        val f = sp.f
        val deco = ArrayList<TextDecoration>()
        if (f.underline) deco.add(TextDecoration.Underline)
        if (f.strike) deco.add(TextDecoration.LineThrough)
        addStyle(
            SpanStyle(
                fontSize = when (f.size) { 1 -> 30.sp; 2 -> 24.sp; 3 -> 20.sp; else -> TextUnit.Unspecified },
                fontWeight = if (f.bold || f.size in 1..3) FontWeight.Bold else null,
                fontStyle = if (f.italic) FontStyle.Italic else null,
                textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
                color = if (f.color != 0) Color(f.color) else Color.Unspecified
            ),
            sp.s.coerceAtLeast(0), sp.e.coerceAtMost(text.length)
        )
    }
}

/** What a toolbar button does to the selected text. Pressing it again on text that already has it removes it. */
class RichOp(val apply: (Fmt) -> Fmt, val remove: (Fmt) -> Fmt, val has: (Fmt) -> Boolean)

@Composable
fun RichBar(onOp: (RichOp) -> Unit, modifier: Modifier = Modifier) {
    var colors by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (colors) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onOp(RichOp({ it.copy(color = 0) }, { it.copy(color = 0) }) { false }); colors = false }
            )
            CardSwatches.forEach { c ->
                val argb = c.toArgb()
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c)
                        .clickable { onOp(RichOp({ it.copy(color = argb) }, { it.copy(color = 0) }) { f -> f.color == argb }); colors = false }
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            for (n in 1..3) Btn("H$n") { onOp(RichOp({ it.copy(size = n) }, { it.copy(size = 0) }) { f -> f.size == n }) }
            Btn("B", bold = true) { onOp(RichOp({ it.copy(bold = true) }, { it.copy(bold = false) }) { f -> f.bold }) }
            Btn("I", italic = true) { onOp(RichOp({ it.copy(italic = true) }, { it.copy(italic = false) }) { f -> f.italic }) }
            Btn("U", underline = true) { onOp(RichOp({ it.copy(underline = true) }, { it.copy(underline = false) }) { f -> f.underline }) }
            Btn("S", strike = true) { onOp(RichOp({ it.copy(strike = true) }, { it.copy(strike = false) }) { f -> f.strike }) }
            Btn(tr("Color")) { colors = !colors }
            Btn(tr("Clear")) { onOp(RichOp({ Fmt() }, { Fmt() }) { false }) }
        }
    }
}

@Composable
private fun Btn(
    t: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, strike: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        t,
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = when {
            underline -> TextDecoration.Underline
            strike -> TextDecoration.LineThrough
            else -> null
        }
    )
}
