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

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import com.barq.focus.ui.theme.AppTheme
import com.barq.focus.ui.theme.CardSwatches
import com.barq.focus.data.I18n
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.barq.focus.service.Alerts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.barq.focus.data.Config
import com.barq.focus.data.Store
import com.barq.focus.service.Episteme
import com.barq.focus.service.Perms
import com.barq.focus.service.Reminders
import com.barq.focus.service.TouchCounter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose {} }
    val usage = remember(rev) { Perms.usage(ctx) }
    val overlay = remember(rev) { Perms.overlay(ctx) }
    val a11y = remember(rev) { Perms.accessibility(ctx) }
    val listener = remember(rev) { Perms.listener(ctx) }
    val notif = remember(rev) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rev++ }
    var gated by remember { mutableStateOf(store.gated()) }
    var debug by remember { mutableStateOf(store.debug) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Setup")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }, contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PermRow("Usage access", "Needed to see which app is in front.", usage) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermRow("Display over other apps", "Needed for the gate overlay.", overlay) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow(
                "Accessibility",
                "Detects Shorts/Reels, counts them, and lets Leave close apps properly. On Android 13+: App info > ⋮ > Allow restricted settings first.",
                a11y
            ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            PermRow(
                "Notification access",
                "Optional: logs music playing in the background (VLC, YouTube Music). Same restricted-settings step applies.",
                listener
            ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            if (Build.VERSION.SDK_INT >= 33) {
                PermRow("Notifications", "Lets the \"Focus is watching\" notice show.", notif) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Picture-in-picture"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Switch YouTube off here so it can't float over your screen."), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        try { ctx.startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS")) }
                        catch (_: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.youtube")))
                        }
                    }) { Text(tr("Open")) }
                }
            }
            Text(
                tr("Set Focus to \"Unrestricted\" in the phone's battery settings, otherwise the ROM may kill the watcher."),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))
            Text(tr("PDF reader"), fontWeight = FontWeight.SemiBold)
            var reader by remember { mutableStateOf(store.pdfReader) }
            val epiOk = remember(rev) { Episteme.installed(ctx) != null }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reader == "episteme", onClick = { reader = "episteme"; store.pdfReader = "episteme" },
                    label = { Text("Episteme" + if (epiOk) "" else "  " + tr("(not installed)")) }
                )
                FilterChip(
                    selected = reader == "builtin", onClick = { reader = "builtin"; store.pdfReader = "builtin" },
                    label = { Text(tr("Built-in")) }
                )
            }
            Text(
                tr("Episteme opens your PDFs in the Episteme Reader app (install it separately). It keeps its own look; the built-in reader keeps Focus's."),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))
            var precise by remember { mutableStateOf(store.preciseSwipes) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Precise swipe counting (root)"))
                    Text(
                        tr("Reads real touchscreen swipes with getevent, so each Short is counted once. Needs root; without it Focus keeps the scroll-based count."),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (precise && TouchCounter.status.isNotEmpty()) Text(tr(TouchCounter.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = precise, onCheckedChange = { precise = it; store.preciseSwipes = it })
            }

            Spacer(Modifier.height(8.dp))
            Text(tr("App colour"), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = AppTheme.accent == 0,
                    onClick = { AppTheme.accent = 0; store.accent = 0 },
                    label = { Text(tr("System")) }
                )
                CardSwatches.forEach { c ->
                    val argb = c.toArgb()
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (AppTheme.accent == argb) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { AppTheme.accent = argb; store.accent = argb }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(tr("Theme"), fontWeight = FontWeight.SemiBold)
            var themeMode by remember { mutableStateOf(store.themeMode) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Follow system", 1 to "Light", 2 to "Dark").forEach { (m, n) ->
                    FilterChip(
                        selected = themeMode == m,
                        onClick = { themeMode = m; store.themeMode = m; AppTheme.mode = m },
                        label = { Text(tr(n)) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(tr("Check shape"), fontWeight = FontWeight.SemiBold)
            var shape by remember { mutableStateOf(store.checkShape) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                (0..7).forEach { m ->
                    Box(
                        Modifier.clip(CircleShape).clickable { shape = m; store.checkShape = m; AppTheme.checkShape = m }.padding(4.dp)
                    ) { ShapePreview(m, shape == m, MaterialTheme.colorScheme.primary) }
                }
            }
            var demo by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeCheck(demo, MaterialTheme.colorScheme.primary, 0) { demo = it }
                Text(tr("Tap to preview the pop and glow"), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Text(tr("Language"), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                I18n.names(ctx).forEach { (c, n) ->
                    FilterChip(selected = I18n.code == c, onClick = { I18n.apply(ctx, c); com.barq.focus.service.Notifier.refreshAll(ctx) }, label = { Text(n) })
                }
            }
            Text(
                tr("More languages: Import / Export > Language template, translate it with any AI, import it back."),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))
            Text(tr("Timer alerts"), fontWeight = FontWeight.SemiBold)
            NumField("Ring length when time is up (sec)", store.ringSec) { store.ringSec = it }
            NumField("Over-time reminder every (min, 0 = off)", store.nagMin) { store.nagMin = it }
            NumField("Reminder bell length (sec)", store.nagRingSec) { store.nagRingSec = it }
            var speak by remember { mutableStateOf(store.speak) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Speak (\"time's up buddy\", \"go faster bro\")"), Modifier.weight(1f))
                Switch(checked = speak, onCheckedChange = { speak = it; store.speak = it })
            }
            OutlinedButton(onClick = {
                Alerts.run(ctx, "", minOf(store.ringSec, 4), if (store.speak) tr("Time's up, buddy! This is a test.") else null, true)
            }) { Text(tr("Test alert")) }

            Spacer(Modifier.height(8.dp))
            Text(tr("Start reminders"), fontWeight = FontWeight.SemiBold)
            var sr by remember { mutableStateOf(store.startRemind) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Nudge me if I haven't started any work"), Modifier.weight(1f))
                Switch(checked = sr, onCheckedChange = { sr = it; store.startRemind = it; Reminders.rescheduleNudge(ctx) })
            }
            if (sr) {
                var from by remember { mutableStateOf(store.startRemindFrom) }
                OutlinedButton(onClick = {
                    android.app.TimePickerDialog(
                        ctx,
                        { _, h, m -> from = h * 60 + m; store.startRemindFrom = from; Reminders.rescheduleNudge(ctx) },
                        from / 60, from % 60, true
                    ).show()
                }) { Text(trf("Start nudging from %s", "%02d:%02d".format(from / 60, from % 60))) }
                NumField("Then every (min)", store.startRemindEvery) { store.startRemindEvery = it; Reminders.rescheduleNudge(ctx) }
            }

            Spacer(Modifier.height(8.dp))
            Text(tr("Gate these apps"), fontWeight = FontWeight.SemiBold)
            Text(tr("All listed apps are logged. Only the ones switched on show the overlay."), style = MaterialTheme.typography.bodyMedium)
            Config.TRACKED.forEach { (pkg, name) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    Switch(checked = pkg in gated, onCheckedChange = { on ->
                        gated = if (on) gated + pkg else gated - pkg
                        store.setGated(gated)
                    })
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Collect YouTube screen IDs (to tune Shorts detection)"), Modifier.weight(1f))
                Switch(checked = debug, onCheckedChange = { debug = it; store.debug = it })
            }
            if (debug) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("ids", store.debugIds.ifEmpty { "nothing captured yet" }))
                }) { Text(tr("Copy last captured IDs")) }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("About & licenses"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Focus is free software under the GNU Affero General Public License v3.0 only (AGPL-3.0-only)."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        tr("Notes follow the feature set of NotallyX (Crustack, forked from Notally by OmGodse; GPL-3.0). PDFs can be opened in Episteme Reader (Aryan-Raj3112; AGPL-3.0-only), a separate app that is not bundled. The code here is an independent implementation."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${com.barq.focus.BuildConfig.LICENSE_ID}  ·  v${com.barq.focus.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("crash", store.lastCrash.ifEmpty { "no crash recorded" }))
            }) { Text(tr("Copy last crash")) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumField(label: String, initial: Int, onValue: (Int) -> Unit) {
    var t by remember { mutableStateOf(initial.toString()) }
    OutlinedTextField(
        t, { s -> t = s.filter(Char::isDigit).take(4); t.toIntOrNull()?.let(onValue) },
        Modifier.fillMaxWidth(),
        label = { Text(tr(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(title), style = MaterialTheme.typography.titleMedium)
                Text(tr(desc), style = MaterialTheme.typography.bodyMedium)
            }
            if (ok) Icon(Icons.Default.Check, "Granted", tint = MaterialTheme.colorScheme.secondary)
            else Button(onClick = onGrant) { Text(tr("Grant")) }
        }
    }
}
