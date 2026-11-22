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

package com.barq.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.barq.focus.data.Apps
import com.barq.focus.data.CardType
import com.barq.focus.data.I18n
import com.barq.focus.data.Store
import com.barq.focus.data.SysUsage
import com.barq.focus.data.dayItems
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.FocusTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small, quiet home screen: the time, what is running, what is left on your plate and only the apps you chose.
 * It is a normal launcher: pick it (or another) in the phone's home settings whenever you like. During a study
 * lock, every other app is blocked and the phone's settings can't be opened.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        I18n.init(this)
        Store(this).let {
            com.barq.focus.ui.theme.AppTheme.accent = it.accent
            com.barq.focus.ui.theme.AppTheme.mode = it.themeMode
            com.barq.focus.ui.theme.AppTheme.checkShape = it.checkShape
            com.barq.focus.ui.theme.AppTheme.loadSaved(it.themeJson)
        }
        com.barq.focus.ui.theme.AppFont.load(this)
        setContent {
            FocusTheme {
                val dir = if (I18n.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LauncherHome() }
                }
            }
        }
    }
}

private const val UNLOCK_PHRASE = "I choose to stop studying now."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherHome() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose {} }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    BackHandler(true) {}                                   // a launcher has nowhere to go back to

    val studyLock = remember(now) { store.studyLock(now) }
    val onlyAllowed = store.launcherOnlyAllowed || studyLock != null
    val apps = remember(rev) { store.launcherApps().mapNotNull { p -> Apps.launchables(ctx).firstOrNull { it.pkg == p } } }
    val cards = remember(rev, now / 5000) { store.cards().filter { it.type == CardType.STUDY } }
    val activeCard = cards.firstOrNull { it.id == store.activeCardId }
    var showAll by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    fun launch(pkg: String) {
        if (onlyAllowed && pkg !in store.launcherApps()) return
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun openFocus(go: String? = null) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).also { if (go != null) it.putExtra("go", go) })
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        if (studyLock != null) {
            val lockStatus = when {
                store.launcherLockUntil > now -> {
                    val leftSec = ((store.launcherLockUntil - now) / 1000L).coerceAtLeast(0L)
                    trf("Study lock · %s left", com.barq.focus.data.clockText(leftSec))
                }
                else -> tr("Study lock · timer running")
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("\uD83D\uDD12 $lockStatus", Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        } else if (onlyAllowed) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("\uD83D\uDEE1\uFE0F " + tr("Allowed apps only"), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
        activeCard?.let { c ->
            val left = if (store.activePaused) store.activeRemaining else store.activeEnd - now
            Card(Modifier.fillMaxWidth().clickable { openFocus("card:${c.id}") }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(c.color))
                        val s = kotlin.math.abs(left) / 1000
                        Text(
                            (if (left < 0) "+" else "") + "%d:%02d".format(s / 60, s % 60) + "  ·  " + tr(if (store.activePaused) "Paused" else if (left < 0) "Over budget" else "left"),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    if (!(store.launcherLockOnTimer && store.launcherStrict)) {
                        FilledTonalButton(onClick = {
                            if (store.activePaused) store.resumeActive() else store.pauseActive()
                            com.barq.focus.service.TimerAlarm.reschedule(ctx, store)
                            com.barq.focus.service.Notifier.syncTimer(ctx, store)
                            rev++
                        }) {
                            Text(tr(if (store.activePaused) "Resume" else "Pause"))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (store.launcherTasks) {
            val open = cards.flatMap { c -> c.dayItems.filter { !it.done }.map { c to it } }
            if (open.isNotEmpty()) {
                Text(trf("%d left on your plate", open.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                open.take(4).forEach { (c, it) ->
                    Text("•  " + it.text, Modifier.clickable { openFocus("card:${c.id}") }.padding(vertical = 3.dp), maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (apps.isEmpty()) Column(
                Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    tr("No apps yet. Open Focus → menu → Study launcher and choose the apps you want here."),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (studyLock == null) OutlinedButton(onClick = { openFocus("launcher") }) { Text(tr("Choose apps")) }
            } else LazyVerticalGrid(GridCells.Fixed(store.launcherCols), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { launch(a.pkg) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val bmp = remember(a.pkg) { SysUsage.icon(ctx, a.pkg) }
                        if (bmp != null) Image(bmp.asImageBitmap(), a.label, Modifier.size(52.dp))
                        Text(a.label, Modifier.padding(top = 4.dp), maxLines = 1, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { openFocus() }, Modifier.weight(1f)) { Text(tr("Focus")) }
            if (studyLock == null) {
                if (!onlyAllowed) OutlinedButton(onClick = { showAll = true }, Modifier.weight(1f)) { Text(tr("All apps")) }
                OutlinedButton(onClick = { showLock = true }, Modifier.weight(1f)) { Text(tr("Study lock")) }
            } else if (store.launcherLockUntil > now && !store.launcherStrict) {
                OutlinedButton(onClick = { showUnlock = true }, Modifier.weight(2f)) { Text(tr("Unlock early")) }
            }
        }
    }

    if (showAll && !onlyAllowed) {
        var q by remember { mutableStateOf("") }
        val all = remember { Apps.launchables(ctx) }
        val hits = all.filter { q.isBlank() || it.label.contains(q, true) }
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(tr("All apps")) },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, singleLine = true, label = { Text(tr("Search")) }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(hits, key = { it.pkg }) { a ->
                            Text(a.label, Modifier.fillMaxWidth().clickable { showAll = false; launch(a.pkg) }.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text(tr("Close")) } }
        )
    }
    if (showLock) AlertDialog(
        onDismissRequest = { showLock = false },
        title = { Text(tr("Study lock")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Only the apps on this screen, Focus, the phone and the system work. Phone settings can't be opened. It ends by itself; ending it early means typing a sentence."), style = MaterialTheme.typography.bodyMedium)
                listOf(25, 45, 60, 90, 120).forEach { m ->
                    OutlinedButton(onClick = { store.launcherLockUntil = System.currentTimeMillis() + m * 60_000L; showLock = false }, Modifier.fillMaxWidth()) { Text(trf("%d min", m)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showLock = false }) { Text(tr("Cancel")) } }
    )
    if (showUnlock) {
        var typed by remember { mutableStateOf("") }
        val phrase = tr(UNLOCK_PHRASE)
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text(tr("Unlock early")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Type this sentence exactly:"), style = MaterialTheme.typography.bodyMedium)
                    Text(phrase, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        typed,
                        { next -> if (next.length - typed.length <= 2) typed = next },
                        label = { Text(tr("Type the sentence above, letter by letter")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals(phrase.trim(), ignoreCase = false), onClick = { store.launcherLockUntil = 0L; showUnlock = false }) { Text(tr("Unlock")) }
            },
            dismissButton = { TextButton(onClick = { showUnlock = false }) { Text(tr("Cancel")) } }
        )
    }
}
