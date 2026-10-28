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
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.LauncherActivity
import com.barq.focus.data.Apps
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/** Settings for the study launcher: which apps it shows, how it looks, and the study lock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(store.launcherApps()) }
    var cols by remember { mutableIntStateOf(store.launcherCols) }
    var tasks by remember { mutableStateOf(store.launcherTasks) }
    var onTimer by remember { mutableStateOf(store.launcherLockOnTimer) }
    var picker by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val locked = store.studyLock(now) != null

    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Study launcher")) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }, contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                tr("A small home screen with only what you choose, and a study lock that blocks every other app (and the phone's settings) so you can't cheat. It is optional: use it as your home screen or just open it when you study."),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startActivity(Intent(ctx, LauncherActivity::class.java)) }) { Text(tr("Open it now")) }
                OutlinedButton(enabled = !locked, onClick = {
                    try { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {}
                }) { Text(tr("Home app settings")) }
            }
            Text(tr("Pick it as your home app there, and switch back any time. During a study lock the phone's settings are blocked."), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()

            Text(tr("Apps on the launcher"), fontWeight = FontWeight.SemiBold)
            Text(trf("%d apps chosen. These are also the only apps that work during a study lock.", apps.size), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { picker = true }) { Text(tr("Choose apps")) }

            Text(tr("Columns"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { n -> FilterChip(selected = cols == n, onClick = { cols = n; store.launcherCols = n }, label = { Text("$n") }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Show what is left on my plate"), Modifier.weight(1f))
                Switch(checked = tasks, onCheckedChange = { tasks = it; store.launcherTasks = it })
            }
            HorizontalDivider()

            Text(tr("Study lock"), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Lock while a subject timer runs"))
                    Text(tr("Other apps are blocked for as long as a timer is running. Pause or stop the timer to get out."), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = onTimer, onCheckedChange = { onTimer = it; store.launcherLockOnTimer = it })
            }
            if (store.launcherLockUntil > now) Text(
                trf("Locked until %s", java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(store.launcherLockUntil))),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
            ) else {
                Text(tr("Start a lock now:"), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60, 90).forEach { m ->
                        OutlinedButton(onClick = { store.launcherLockUntil = System.currentTimeMillis() + m * 60_000L; now = System.currentTimeMillis() }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("$m") }
                    }
                }
                Text(tr("Needs usage access, overlay and accessibility (Settings). Ending a time lock early means typing a sentence on the launcher."), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picker) AppPickerDialog(
        remember { Apps.launchables(ctx) }, apps,
        onDone = { apps = it; store.setLauncherApps(it); picker = false }, onDismiss = { picker = false },
        title = tr("Launcher apps")
    )
}
