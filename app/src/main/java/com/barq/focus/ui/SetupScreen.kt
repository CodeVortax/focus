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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import com.barq.focus.service.Shared
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
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

import com.barq.focus.data.EpLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(store: Store, onBack: () -> Unit, onTour: () -> Unit = {}) {
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
    var dev by remember { mutableStateOf(store.devOptions) }
    var taps by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val missing = listOf(usage, overlay, a11y).count { !it }
    val focusSound = remember { Shared.settingsFocus?.takeIf { it.startsWith("sound:") }?.removePrefix("sound:")?.toLongOrNull().also { Shared.settingsFocus = null } }
    val soundBring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var soundCards by remember { mutableStateOf(store.cards()) }
    var pickFor by remember { mutableStateOf(-1L) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val u = r.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            soundCards.firstOrNull { it.id == pickFor }?.let { c -> store.updateCard(c.copy(sound = u?.toString() ?: "silent")) }
            soundCards = store.cards()
        }
    }
    LaunchedEffect(focusSound) { if (focusSound != null) { kotlinx.coroutines.delay(350); soundBring.bringIntoView() } }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            scope.launch {
                val n = withContext(Dispatchers.IO) { EpLock.restoreFolder(ctx, u, store) }
                Toast.makeText(ctx, trf("Restored %d files", n), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Settings")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }, contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Group("perms", Icons.Default.Security, "Permissions", if (missing == 0) "All set" else trf("%d still needed", missing), missing > 0) {
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
                Text(
                    tr("Set Focus to \"Unrestricted\" in the phone's battery settings, otherwise the ROM may kill the watcher."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Group("gate", Icons.Default.Tune, "Gate and Shorts", "Apps, YouTube picture-in-picture, swipe counting") {
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
                var shortsLim by remember { mutableStateOf(store.shortsLimit) }
                Column {
                    Text(tr("Daily Shorts limit"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("How many Shorts/Reels you allow yourself per day. At the gate you pick how many to watch, up to what is left. 0 means no limit."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { shortsLim = (shortsLim - 5).coerceAtLeast(0); store.shortsLimit = shortsLim }) { Text("−5") }
                        Text(
                            if (shortsLim == 0) tr("No limit") else shortsLim.toString(),
                            Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = { shortsLim = (shortsLim + 5).coerceAtMost(500); store.shortsLimit = shortsLim }) { Text("+5") }
                    }
                }
                var strict by remember { mutableStateOf(store.strictShorts) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Strict Shorts mode"))
                        Text(
                            tr("The gate opens at once with no \"just finish this one\", no treat button and no swipe count. To keep watching you must type a sentence by hand (paste is blocked), every time. Leaving closes the app. Optional gray screen: run  adb shell pm grant com.barq.focus android.permission.WRITE_SECURE_SETTINGS  once."),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Switch(checked = strict, onCheckedChange = { strict = it; store.strictShorts = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
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

            Group(
                "sound", Icons.Default.MusicNote, "Timer sound per subject", "Which sound rings when each subject's time is up",
                open = focusSound != null, modifier = Modifier.bringIntoViewRequester(soundBring), highlight = focusSound != null
            ) {
                soundCards.filter { it.type == com.barq.focus.data.CardType.STUDY }.forEach { c ->
                    val name = when (c.sound) {
                        "" -> tr("Default alarm")
                        "silent" -> tr("Silent")
                        else -> try { android.media.RingtoneManager.getRingtone(ctx, Uri.parse(c.sound))?.getTitle(ctx) ?: "Custom" } catch (_: Exception) { "Custom" }
                    }
                    val hot = c.id == focusSound
                    Row(
                        Modifier.fillMaxWidth().then(if (hot) Modifier.background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else Modifier).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).background(Color(c.color), androidx.compose.foundation.shape.CircleShape))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            pickFor = c.id
                            soundPicker.launch(Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM or android.media.RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer sound")
                                val cur: Uri? = if (c.sound.isEmpty() || c.sound == "silent") null else Uri.parse(c.sound)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur)
                            })
                        }) { Text(tr("Change")) }
                    }
                }
            }

            Group("timer", Icons.Default.Timer, "Timer and reminders", "Alerts, over-time nudges, start reminders") {
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
                HorizontalDivider()
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
            }

            Group("look", Icons.Default.Palette, "Colour and theme", "App colour, light or dark") {
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
                Text(tr("Menu side"), fontWeight = FontWeight.SemiBold)
                var menuSide by remember { mutableStateOf(store.menuSide) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Follow language", 1 to "Left", 2 to "Right").forEach { (m, n) ->
                        FilterChip(selected = menuSide == m, onClick = { menuSide = m; store.menuSide = m }, label = { Text(tr(n)) })
                    }
                }
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
            }
            Group("shape", Icons.Default.Check, "Check shape", "The tick you tap on tasks") {
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
            }
            Group("font", Icons.Default.TextFields, "Font", "Use your own font file") {
                Text(tr("Font"), fontWeight = FontWeight.SemiBold)
                val fontPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
                    if (u != null) {
                        val nm = try { ctx.contentResolver.query(u, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null } } catch (_: Exception) { null } ?: "custom"
                        Toast.makeText(ctx, tr(if (com.barq.focus.ui.theme.AppFont.import(ctx, u, nm)) "Font applied" else "That file isn't a usable font"), Toast.LENGTH_SHORT).show()
                    }
                }
                Text(
                    if (com.barq.focus.ui.theme.AppFont.name.isEmpty()) tr("System font") else com.barq.focus.ui.theme.AppFont.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fontPick.launch(arrayOf("*/*")) }) { Text(tr("Choose a font file")) }
                    if (com.barq.focus.ui.theme.AppFont.name.isNotEmpty() && com.barq.focus.ui.theme.AppFont.name != "default")
                        TextButton(onClick = { com.barq.focus.ui.theme.AppFont.reset(ctx) }) { Text(tr("Reset")) }
                }
                Text(
                    tr("Pick a .ttf or .otf file to try a font. To make one permanent, put it at app/src/main/assets/fonts/default.ttf and rebuild."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Group("lang", Icons.Default.Language, "Language", "App language and your own translation") {
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
            }

            Group("notes", Icons.Default.Description, "Notes, PDF and recordings", "PDF reader, voice recording, checklists") {
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
                HorizontalDivider()
                Text(tr("Voice recordings"), fontWeight = FontWeight.SemiBold)
                var ask by remember { mutableStateOf(store.askRecName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Ask for a name when I stop recording"))
                        Text(tr("Recordings keep a temporary name until you give one. The file type is added for you."), style = MaterialTheme.typography.labelLarge)
                    }
                    Switch(checked = ask, onCheckedChange = { ask = it; store.askRecName = it })
                }
                var kbps by remember { mutableStateOf(store.recKbps) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24 to "Smallest", 32 to "Balanced", 64 to "Best").forEach { (k, n) ->
                        FilterChip(selected = kbps == k, onClick = { kbps = k; store.recKbps = k }, label = { Text(tr(n)) })
                    }
                }
                Text(
                    tr("Opus voice quality. Balanced is about 14 MB an hour and sounds clean; Best doubles that."),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                var sink by remember { mutableStateOf(store.notesAutoSort) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Checked checklist items sink to the bottom"), Modifier.weight(1f))
                    Switch(checked = sink, onCheckedChange = { sink = it; store.notesAutoSort = it })
                }
            }

            Group("music", Icons.Default.MusicNote, "Music notification and capsule", "Lock screen, Mini Capsule, Dynamic Island") {
                MusicCapsuleHelp()
            }

            Group("about", Icons.Default.Info, "About", "Version and licenses") {
                Text(tr("Focus is free software under the GNU Affero General Public License v3.0 only (AGPL-3.0-only)."), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("Notes follow the feature set of NotallyX (Crustack, forked from Notally by OmGodse; GPL-3.0). PDFs can be opened in Episteme Reader (Aryan-Raj3112; AGPL-3.0-only), a separate app that is not bundled. The code here is an independent implementation."),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onTour) { Text(tr("Replay the first-run guide")) }
                Text(
                    "${com.barq.focus.BuildConfig.LICENSE_ID}  ·  v${com.barq.focus.BuildConfig.VERSION_NAME}",
                    Modifier.clickable {
                        if (dev) { Toast.makeText(ctx, tr("Developer options are already on"), Toast.LENGTH_SHORT).show(); return@clickable }
                        taps++
                        if (taps >= 7) { dev = true; store.devOptions = true; Toast.makeText(ctx, tr("Developer options unlocked"), Toast.LENGTH_SHORT).show() }
                        else if (taps >= 3) Toast.makeText(ctx, trf("%d more taps for developer options", 7 - taps), Toast.LENGTH_SHORT).show()
                    },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                )
            }

            Group("me", Icons.Default.Person, "About me", "Who made Focus, and how to support it") {
                val age = remember { com.barq.focus.data.Author.age() }
                Text(com.barq.focus.data.Author.NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@" + com.barq.focus.data.Author.HANDLE, color = MaterialTheme.colorScheme.primary)
                Text(trf("From %s", tr(com.barq.focus.data.Author.COUNTRY)) + "  ·  " + trf("%d years old", age.years))
                age.nextMonthTurns?.let { Text(trf("Turns %d next month", it), style = MaterialTheme.typography.bodyMedium) }
                Text(
                    tr("I build Focus on my own, as a student. If it helps you, a star or a coffee keeps it going. Thank you."),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { openLink(ctx, com.barq.focus.data.Author.REPO) }) {
                    Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp)); Text(tr("Star Focus on GitHub"))
                }
                OutlinedButton(
                    enabled = com.barq.focus.data.Author.COFFEE.isNotEmpty(),
                    onClick = { openLink(ctx, com.barq.focus.data.Author.COFFEE) }
                ) { Text(if (com.barq.focus.data.Author.COFFEE.isEmpty()) tr("Buy me a coffee (coming soon)") else tr("Buy me a coffee")) }
                OutlinedButton(
                    enabled = com.barq.focus.data.Author.CRYPTO.isNotEmpty(),
                    onClick = {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("crypto", com.barq.focus.data.Author.CRYPTO))
                        Toast.makeText(ctx, tr("Address copied"), Toast.LENGTH_SHORT).show()
                    }
                ) { Text(if (com.barq.focus.data.Author.CRYPTO.isEmpty()) tr("Crypto (coming soon)") else tr("Copy my crypto address")) }
            }

            if (dev) Group("dev", Icons.Default.Code, "Developer options", "Rescue tools and debug capture") {
                Text(tr("Restore locked episode files"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr("If locked treat videos ever stay hidden, pick their folder. Focus reads the hidden config there (or the hidden names) and gives every file its real name back."),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { restorePicker.launch(null) }) { Text(tr("Pick the folder and restore")) }
                HorizontalDivider()
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
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("crash", store.lastCrash.ifEmpty { "no crash recorded" }))
                }) { Text(tr("Copy last crash")) }
                TextButton(onClick = { dev = false; store.devOptions = false; taps = 0 }) { Text(tr("Hide developer options")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One collapsible block of settings. Opens by itself when [open] (e.g. a permission is missing). */
@Composable
private fun Group(id: String, icon: ImageVector, title: String, subtitle: String, open: Boolean = false, modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(id, open) { mutableStateOf(open) }
    Card(modifier.fillMaxWidth(), border = if (highlight) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(title), style = MaterialTheme.typography.titleMedium)
                    Text(if (subtitle.startsWith("%") || subtitle[0].isDigit()) subtitle else tr(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
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
internal fun PermRow(title: String, desc: String, ok: Boolean, onGrant: () -> Unit) {
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


private fun openLink(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}
