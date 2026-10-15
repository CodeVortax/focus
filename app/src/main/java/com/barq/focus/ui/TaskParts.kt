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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Item
import com.barq.focus.data.TaskGroup
import com.barq.focus.data.Weekly
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.CardSwatches
import java.util.Locale

/** One task: shape check, text, (weekly: multiplier line), voice play button and a ⋮ menu. */
@Composable
fun TaskRow(
    item: Item, seed: Int, color: Color, groups: List<TaskGroup>,
    onToggle: (Boolean) -> Unit, onDelete: () -> Unit,
    onMoveToGroup: (String) -> Unit, onNewGroup: () -> Unit,
    onRecord: () -> Unit, onDeleteVoice: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShapeCheck(item.done, color, seed, onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                item.text,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                textAlign = TextAlign.Start,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (item.weekly && !item.done) {
                val now = System.currentTimeMillis()
                val f = Weekly.factor(item, now)
                val late = now > item.deadline
                Text(
                    trf(
                        "x%s  ·  +%s min if done now  ·  %s",
                        String.format(Locale.US, "%.1f", f), minText(Weekly.rewardSec(item, now)),
                        if (late) tr("late") else trf("%d d left", Weekly.daysLeft(item, now))
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (late) MaterialTheme.colorScheme.error else color
                )
            }
        }
        if (item.voice.isNotEmpty()) VoicePlayButton(item.voice, color)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("More")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!item.weekly) {
                    if (item.group.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Remove from group")) }, onClick = { menu = false; onMoveToGroup("") })
                    groups.filter { it.name != item.group }.forEach { g ->
                        DropdownMenuItem(
                            text = { Text("●  " + g.name, color = Color(g.color)) },
                            onClick = { menu = false; onMoveToGroup(g.name) }
                        )
                    }
                    DropdownMenuItem(text = { Text(tr("New group…")) }, onClick = { menu = false; onNewGroup() })
                }
                if (item.voice.isEmpty()) DropdownMenuItem(text = { Text(tr("Record voice note")) }, onClick = { menu = false; onRecord() })
                else DropdownMenuItem(text = { Text(tr("Delete voice note")) }, onClick = { menu = false; onDeleteVoice() })
                DropdownMenuItem(text = { Text(tr("Delete task")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Chrome-style group tab: a coloured pill with the name. Tap folds the group, long-press edits it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupHeader(group: TaskGroup, done: Int, total: Int, onToggle: () -> Unit, onEdit: () -> Unit) {
    val col = Color(group.color)
    val ink = if (col.luminance() > 0.6f) Color.Black else Color.White
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).combinedClickable(onClick = onToggle, onLongClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(col).padding(start = 10.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (group.collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                null, tint = ink, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(group.name, color = ink, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text("$done/$total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Create / rename / recolour a group; [onDelete] (edit mode only) ungroups its tasks and removes it. */
@Composable
fun GroupDialog(initial: TaskGroup?, defaultColor: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: defaultColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (initial == null) "New group" else "Edit group")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text(tr("Group name")) }, singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardSwatches.forEach { c ->
                        val argb = c.toArgb()
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(if (argb == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = argb }
                        )
                    }
                }
                if (onDelete != null) TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                    Text(tr("Ungroup and delete this group"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
