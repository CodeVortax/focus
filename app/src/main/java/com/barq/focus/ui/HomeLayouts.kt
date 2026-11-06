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

@file:OptIn(ExperimentalFoundationApi::class)

package com.barq.focus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.CardType
import com.barq.focus.data.Config
import com.barq.focus.data.FocusCard
import com.barq.focus.data.dayItems
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/** The ways the subject cards can be laid out on the home screen. The first is the original grid. */
object HomeLayouts {
    val ALL = listOf(
        "grid" to "Grid (default)",
        "list" to "List",
        "big" to "Big cards",
        "carousel" to "Carousel",
        "agenda" to "Agenda",
        "rings" to "Rings",
        "mosaic" to "Mosaic",
    )
}

private class Stat(val col: Color, val study: Boolean, val total: Int, val done: Int, val left: Int, val time: String)

private fun lenText(m: Long) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

private fun stat(c: FocusCard, spentMin: Long): Stat {
    val t = c.dayItems.size
    val d = c.dayItems.count { it.done }
    return Stat(Color(c.color), c.type == CardType.STUDY, t, d, t - d, if (spentMin > 0) lenText(spentMin) else "")
}

private fun statusText(c: FocusCard, s: Stat): String = when {
    !s.study -> typeLabel(c.type) + "  ·  ${c.minutes}m"
    s.total == 0 -> Config.freeWord(c.id)
    s.left == 0 -> tr("Plate cleared ✓")
    else -> "${s.left} " + trf("left of %d", s.total)
}

private fun Modifier.tile(col: Color, radius: Int, onOpen: () -> Unit, onMenu: () -> Unit): Modifier =
    this.clip(RoundedCornerShape(radius.dp)).background(col.copy(alpha = 0.18f)).combinedClickable(onClick = onOpen, onLongClick = onMenu)

@Composable
private fun AddRow(onAdd: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onAdd() },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Add, "Add card") }
}

@Composable
private fun AddBox(w: Int, h: Int, radius: Int, onAdd: () -> Unit) {
    Box(
        Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(radius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onAdd() },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Add, "Add card") }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ListCard(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val s = stat(c, spentMin)
    Row(
        Modifier.fillMaxWidth().tile(s.col, 18, onClick, onLong).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(6.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(s.col))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(statusText(c, s), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (s.study && s.total > 0) LinearProgressIndicator(
                progress = { s.done / s.total.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = s.col, trackColor = s.col.copy(alpha = 0.25f)
            )
        }
        if (s.time.isNotEmpty()) Text(
            s.time, Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = s.col
        )
    }
}

@Composable
private fun BigCard(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val s = stat(c, spentMin)
    Column(
        Modifier.fillMaxWidth().height(190.dp).tile(s.col, 28, onClick, onLong).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            c.title, style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (s.study && s.total > 0 && s.left > 0) Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.left}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = s.col)
            Text("  " + trf("left of %d", s.total), Modifier.padding(bottom = 14.dp), style = MaterialTheme.typography.labelLarge)
        } else Text(statusText(c, s), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = s.col)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.study && s.total > 0) LinearProgressIndicator(
                progress = { s.done / s.total.toFloat() },
                modifier = Modifier.weight(1f), color = s.col, trackColor = s.col.copy(alpha = 0.25f)
            ) else Spacer(Modifier.weight(1f))
            if (s.time.isNotEmpty()) Text(
                s.time, Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = s.col
            )
        }
    }
}

@Composable
private fun CarouselCard(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val s = stat(c, spentMin)
    Column(
        Modifier.width(280.dp).height(360.dp).tile(s.col, 32, onClick, onLong).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            c.title, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Content),
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.study && s.total > 0 && s.left > 0) {
                Text("${s.left}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = s.col)
                Text(trf("left of %d", s.total), style = MaterialTheme.typography.titleMedium)
            } else Text(statusText(c, s), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = s.col)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.study && s.total > 0) LinearProgressIndicator(
                progress = { s.done / s.total.toFloat() },
                modifier = Modifier.weight(1f), color = s.col, trackColor = s.col.copy(alpha = 0.25f)
            ) else Spacer(Modifier.weight(1f))
            if (s.time.isNotEmpty()) Text(
                s.time, Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = s.col
            )
        }
    }
}

@Composable
private fun RingCard(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val s = stat(c, spentMin)
    val frac = when {
        s.study && s.total > 0 -> s.done / s.total.toFloat()
        s.study -> 0f
        else -> 1f
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { frac }, modifier = Modifier.fillMaxSize(),
                color = s.col, trackColor = s.col.copy(alpha = 0.2f), strokeWidth = 8.dp
            )
            Text(
                when { !s.study -> "${c.minutes}m"; s.total == 0 -> "·"; s.left == 0 -> "✓"; else -> "${s.left}" },
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = s.col
            )
        }
        Text(
            c.title, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content),
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MosaicCard(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val s = stat(c, spentMin)
    // the more is left to do, the taller the tile: the busy subjects stand out
    val h = 110 + 26 * s.left.coerceIn(0, 6)
    Column(
        Modifier.fillMaxWidth().height(h.dp).tile(s.col, 22, onClick, onLong).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            c.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (s.study && s.total > 0 && s.left > 0) Text("${s.left}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = s.col)
        else Text(statusText(c, s), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = s.col)
    }
}

/** The subject cards in the chosen [layout] ("grid" is the original). Tap opens, long-press opens the card menu. */
@Composable
internal fun HomeCards(
    layout: String,
    cards: List<FocusCard>,
    spentMin: (FocusCard) -> Long,
    onOpen: (FocusCard) -> Unit,
    onMenu: (FocusCard) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layout) {
        "list" -> LazyColumn(modifier, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards, key = { it.id }) { c -> ListCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item { AddRow(onAdd) }
        }
        "big" -> LazyColumn(modifier, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cards, key = { it.id }) { c -> BigCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item { AddRow(onAdd) }
        }
        "carousel" -> LazyRow(
            modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            items(cards, key = { it.id }) { c -> CarouselCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item { AddBox(110, 360, 32, onAdd) }
        }
        "agenda" -> {
            val todo = cards.filter { it.type == CardType.STUDY && it.dayItems.any { i -> !i.done } }
            val cleared = cards.filter { it.type == CardType.STUDY && it.dayItems.isNotEmpty() && it.dayItems.all { i -> i.done } }
            val rest = cards.filter { c -> c !in todo && c !in cleared }
            LazyColumn(modifier, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (todo.isNotEmpty()) {
                    item { Header(tr("Still to do")) }
                    items(todo, key = { it.id }) { c -> ListCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
                }
                if (cleared.isNotEmpty()) {
                    item { Header(tr("Cleared")) }
                    items(cleared, key = { it.id }) { c -> ListCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
                }
                if (rest.isNotEmpty()) {
                    item { Header(tr("Treats and free")) }
                    items(rest, key = { it.id }) { c -> ListCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
                }
                item { AddRow(onAdd) }
            }
        }
        "rings" -> LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp), modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)
        ) {
            items(cards, key = { it.id }) { c -> RingCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item {
                Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(92.dp).clip(RoundedCornerShape(46.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onAdd() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "Add card") }
                }
            }
        }
        "mosaic" -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2), modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)
        ) {
            items(cards, key = { it.id }) { c -> MosaicCard(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item { AddBox(150, 110, 22, onAdd) }
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp), modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
        ) {
            items(cards, key = { it.id }) { c -> CardTile(c, spentMin(c), { onOpen(c) }, { onMenu(c) }) }
            item {
                Box(
                    Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "Add card") }
            }
        }
    }
}

/** A small dialog to choose the home layout (for trying them out). */
@Composable
fun LayoutPicker(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Home layout")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HomeLayouts.ALL.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == id, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(tr(name))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } }
    )
}
