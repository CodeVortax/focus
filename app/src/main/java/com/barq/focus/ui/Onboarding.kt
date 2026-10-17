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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.barq.focus.data.CardType
import com.barq.focus.data.FocusCard
import com.barq.focus.data.I18n
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Perms
import com.barq.focus.service.Notifier
import com.barq.focus.ui.theme.CardSwatches

private const val STEPS = 6

/** First-run guide: language, how Focus works, permissions, your subjects, music/notification help, done. */
@Composable
fun OnboardingScreen(store: Store, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose {} }

    fun finish() { store.onboarded = true; onDone() }

    Scaffold(contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(STEPS) { i ->
                        Box(
                            Modifier.size(if (i == step) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                                .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                    }
                }
                TextButton(onClick = { finish() }) { Text(tr("Skip")) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (step) {
                    0 -> {
                        Text(tr("Welcome to Focus"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(tr("A study and phone-discipline app: plan your day, earn treats, and keep Shorts and distractions in check."), style = MaterialTheme.typography.bodyLarge)
                        Text(tr("Language"), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            I18n.names(ctx).forEach { (c, n) ->
                                FilterChip(selected = I18n.code == c, onClick = { I18n.apply(ctx, c); Notifier.refreshAll(ctx) }, label = { Text(n) })
                            }
                        }
                    }
                    1 -> {
                        Text(tr("How it works"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Tip("1", tr("Plan"), tr("Make a card for each subject and add its tasks for today."))
                        Tip("2", tr("Earn"), tr("Every task you finish adds minutes to your treat bank."))
                        Tip("3", tr("Spend"), tr("Watch an episode or open YouTube with the minutes you earned. The gate asks why before an app opens."))
                        Tip("4", tr("Stay in control"), tr("Focus counts Shorts and Reels and lets you leave with one tap."))
                        Tip("5", tr("Keep notes"), tr("Notes with checklists, photos, files, voice recordings and links."))
                    }
                    2 -> {
                        Text(tr("Permissions"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Focus needs these to see which app is open and to show the gate. Nothing leaves your phone."), style = MaterialTheme.typography.bodyMedium)
                        val usage = remember(rev) { Perms.usage(ctx) }
                        val overlay = remember(rev) { Perms.overlay(ctx) }
                        val a11y = remember(rev) { Perms.accessibility(ctx) }
                        val notif = remember(rev) {
                            Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rev++ }
                        PermRow("Usage access", "Needed to see which app is in front.", usage) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        PermRow("Display over other apps", "Needed for the gate overlay.", overlay) {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        }
                        PermRow("Accessibility", "Detects Shorts/Reels, counts them, and lets Leave close apps properly. On Android 13+: App info > ⋮ > Allow restricted settings first.", a11y) {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        if (Build.VERSION.SDK_INT >= 33) PermRow("Notifications", "Lets the \"Focus is watching\" notice show.", notif) { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        Text(tr("Come back here after each one. You can also do this later in Settings."), style = MaterialTheme.typography.bodyMedium)
                    }
                    3 -> SubjectsStep(store)
                    4 -> {
                        Text(tr("Music and notifications"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        MusicCapsuleHelp()
                    }
                    else -> {
                        Text(tr("You're set"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Add tasks to a subject, finish them, then spend your treat. Widgets, games, notes and the Usage screen are all on the home screen menu."), style = MaterialTheme.typography.bodyLarge)
                        Text(tr("You can replay this guide any time from Settings > About."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = step > 0, onClick = { step-- }) { Text(tr("Back")) }
                Button(onClick = { if (step >= STEPS - 1) finish() else step++ }) { Text(tr(if (step >= STEPS - 1) "Start" else "Next")) }
            }
        }
    }
}

@Composable
private fun Tip(n: String, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(n, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubjectsStep(store: Store) {
    var cards by remember { mutableStateOf(store.cards()) }
    var name by remember { mutableStateOf("") }
    val subjects = cards.filter { it.type == CardType.STUDY }
    Text(tr("Your subjects"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(tr("These are the cards on your home screen. Remove the ones you don't need and add your own."), style = MaterialTheme.typography.bodyMedium)
    subjects.forEach { c ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(c.color)))
            Text(c.title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { store.saveCards(store.cards().filter { it.id != c.id }); cards = store.cards() }) { Icon(Icons.Default.Close, tr("Delete")) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("New subject")) })
        TextButton(enabled = name.isNotBlank(), onClick = {
            val all = store.cards()
            val col = CardSwatches[all.size % CardSwatches.size].toArgb()
            store.saveCards(all + FocusCard(System.currentTimeMillis(), name.trim(), CardType.STUDY, col, 60))
            cards = store.cards(); name = ""
        }) { Text(tr("Add")) }
    }
}
