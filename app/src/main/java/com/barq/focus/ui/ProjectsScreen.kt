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

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Apps
import com.barq.focus.data.Project
import com.barq.focus.data.Store
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.delay

private fun hm(sec: Long): String {
    val m = sec / 60
    return if (m >= 60) "%dh %02dm".format(m / 60, m % 60) else "%d min".format(m)
}

/** Projects: name the apps you will use (Chrome, Termux, YouTube...) and start a session. It spends the treat bank while they are on screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { Apps.launchables(ctx) }
    val labels = remember(apps) { apps.associate { it.pkg to it.label } }
    var projects by remember { mutableStateOf(store.projects()) }
    var active by remember { mutableLongStateOf(store.activeProject) }
    var bankSec by remember { mutableIntStateOf(store.bankSec()) }
    var edit by remember { mutableStateOf<Project?>(null) }        // id 0 = a new one
    var rev by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { active = store.activeProject; bankSec = store.bankSec(); rev++; delay(1000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Projects")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { edit = Project(0L, "", emptyList()) }) { Icon(Icons.Default.Add, "Add") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trf("Treat bank: %s min", minText(bankSec)), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("A project is a list of the apps you will use for it. While a session runs, those apps skip the gate and the treat bank counts down, but only while one of them is on screen. You get a warning at 5 and at 3 minutes left."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (projects.isEmpty()) item { Text(tr("No projects yet. Tap + and add one, for example \"ESP IR project\" with Chrome, Termux and YouTube.")) }
            items(projects, key = { it.id }) { pr ->
                val running = active == pr.id
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pr.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    pr.apps.joinToString(" · ") { labels[it] ?: it }.ifEmpty { tr("No apps picked") },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { edit = pr }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = {
                                if (running) store.stopProject()
                                projects = projects.filter { it.id != pr.id }; store.saveProjects(projects)
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        // rev makes this line refresh every second while a session runs
                        Text(
                            trf("Time on it: %s", hm(store.projectSec(pr.id) + 0 * rev)),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                        )
                        if (running) {
                            Text(trf("Running. %s min of treat left.", minText(bankSec)), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { store.stopProject(); active = -1L }) { Text(tr("Stop the session")) }
                        } else {
                            Button(enabled = pr.apps.isNotEmpty(), onClick = {
                                if (store.startProject(pr.id)) {
                                    active = pr.id
                                    Toast.makeText(ctx, tr("Session started. Open one of your apps."), Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(ctx, tr("The treat bank is empty. Finish some tasks first."), Toast.LENGTH_LONG).show()
                            }) { Text(tr("Start a session")) }
                        }
                    }
                }
            }
        }
    }

    edit?.let { cur ->
        var name by remember(cur.id) { mutableStateOf(cur.name) }
        var picked by remember(cur.id) { mutableStateOf(cur.apps.toSet()) }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(tr(if (cur.id == 0L) "New project" else "Edit project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Project name")) }, singleLine = true)
                    OutlinedButton(onClick = { pick = true }) { Text(trf("Apps I will use (%d)", picked.size)) }
                    Text(picked.joinToString(" · ") { labels[it] ?: it }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && picked.isNotEmpty(), onClick = {
                    val order = apps.map { it.pkg }
                    val list = picked.sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    projects = if (cur.id == 0L) projects + Project(System.currentTimeMillis(), name.trim(), list)
                    else projects.map { if (it.id == cur.id) it.copy(name = name.trim(), apps = list) else it }
                    store.saveProjects(projects)
                    edit = null
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(tr("Cancel")) } }
        )
        if (pick) AppPickerDialog(apps, picked, { picked = it; pick = false }, { pick = false }, tr("Apps I will use"))
    }
}
