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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.AppInfo
import com.barq.focus.data.Apps
import com.barq.focus.data.Block
import com.barq.focus.data.Config
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

private fun parse(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}
private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var blocks by remember { mutableStateOf(store.blocks()) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Not-free blocks")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    trf("Repeats daily. A plain block only slows the gate (%d s). A locked block blocks every app except the ones you allow.", Config.BLOCK_DELAY_SECONDS),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (blocks.isEmpty()) item { Text(tr("No blocks yet.")) }
            items(blocks, key = { it.id }) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium)
                            Text("${fmt(b.startMin)} – ${fmt(b.endMin)}", style = MaterialTheme.typography.bodyMedium)
                            if (b.lock) Text(
                                trf("Locked · %d apps allowed", b.allowed.size) + if (b.strict) " · " + tr("strict") else "",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { blocks = blocks.filter { it.id != b.id }; store.saveBlocks(blocks) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var s by remember { mutableStateOf("16:00") }
        var e by remember { mutableStateOf("17:00") }
        var lock by remember { mutableStateOf(false) }
        var strict by remember { mutableStateOf(false) }
        var allowed by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pick by remember { mutableStateOf(false) }
        val apps = remember { Apps.launchables(ctx) }
        val ps = parse(s); val pe = parse(e)

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(tr("New block")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Name (Study, Eat…)")) }, singleLine = true)
                    OutlinedTextField(s, { s = it }, label = { Text(tr("From (HH:mm)")) }, singleLine = true, isError = ps == null)
                    OutlinedTextField(e, { e = it }, label = { Text(tr("To (HH:mm)")) }, singleLine = true, isError = pe == null)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Lock the phone (only allowed apps)"), Modifier.weight(1f))
                        Switch(checked = lock, onCheckedChange = {
                            lock = it
                            if (it && allowed.isEmpty()) allowed = Apps.defaultAllowed(ctx)
                        })
                    }
                    if (lock) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Strict (no emergency override)"), Modifier.weight(1f))
                            Switch(checked = strict, onCheckedChange = { strict = it })
                        }
                        OutlinedButton(onClick = { pick = true }) { Text(trf("Allowed apps (%d)", allowed.size)) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && ps != null && pe != null, onClick = {
                    blocks = blocks + Block(System.currentTimeMillis(), name.trim(), ps!!, pe!!, lock, strict, if (lock) allowed else emptySet())
                    store.saveBlocks(blocks)
                    showAdd = false
                }) { Text(tr("Add")) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(tr("Cancel")) } }
        )

        if (pick) AppPickerDialog(apps, allowed, { allowed = it; pick = false }, { pick = false })
    }
}

@Composable
internal fun AppPickerDialog(apps: List<AppInfo>, initial: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit, title: String = tr("Allowed apps")) {
    var sel by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RoundCheck(a.pkg in sel, MaterialTheme.colorScheme.primary) { v -> sel = if (v) sel + a.pkg else sel - a.pkg }
                        Text(a.label, fontWeight = if (a.pkg in sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text(tr("Done")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
