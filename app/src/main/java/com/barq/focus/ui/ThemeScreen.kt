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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.AppTheme
import com.barq.focus.ui.theme.ThemeConfig
import com.barq.focus.ui.theme.ThemeJson
import com.barq.focus.ui.theme.ThemeParse
import com.barq.focus.ui.theme.ThemePresets

/**
 * Look & theme: pick a preset, try it, apply it; export it as JSON, change it with any editor or AI, import it back.
 * An imported theme is checked first (see [ThemeJson.parse]); nothing is applied until you press Apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var pasted by remember { mutableStateOf("") }
    // leaving the screen drops a preview that was never applied
    DisposableEffect(Unit) { onDispose { AppTheme.preview = null } }
    val pending = AppTheme.preview
    val saved = AppTheme.saved

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun copyText(label: String, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        toast(tr("Copied"))
    }
    fun tryImport(text: String) {
        when (val r = ThemeJson.parse(text)) {
            is ThemeParse.Ok -> { problems = emptyList(); AppTheme.preview = r.config }
            is ThemeParse.Bad -> { problems = r.problems.take(8); AppTheme.preview = null }
        }
    }
    val current: ThemeConfig = pending ?: saved ?: ThemePresets.DEFAULT

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Look & theme")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pending != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(trf("Previewing: %s", pending.name), fontWeight = FontWeight.SemiBold)
                    Text(tr("This is only a preview. Nothing changes until you apply it."), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            store.themeJson = ThemeJson.toJson(pending)
                            AppTheme.saved = pending; AppTheme.preview = null
                            toast(tr("Theme applied"))
                        }) { Text(tr("Apply")) }
                        OutlinedButton(onClick = { AppTheme.preview = null }) { Text(tr("Cancel")) }
                    }
                }
            }

            Text(tr("Presets"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.clip(MaterialTheme.shapes.medium).clickable {
                AppTheme.preview = null; AppTheme.saved = null; store.themeJson = ""; problems = emptyList()
            }.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = saved == null && pending == null, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tr("Focus default"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Your accent colour and light or dark choice from Setup."), style = MaterialTheme.typography.bodySmall)
                }
            }
            ThemePresets.ALL.forEach { p ->
                PresetRow(p, selected = saved?.name == p.name && pending == null, previewing = pending?.name == p.name) {
                    problems = emptyList(); AppTheme.preview = p
                }
            }

            HorizontalDivider()
            Text(tr("Export and import"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Copy the theme as JSON, change it in any editor or with an AI, and paste it back. Only design values are read: colours, shapes, spacing, text size and a few on/off choices. It can never change how the gate or the treat bank works."),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyText("Focus theme", ThemeJson.toJson(current)) }) { Text(tr("Copy theme")) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ThemeJson.toJson(current))
                    try { ctx.startActivity(Intent.createChooser(send, tr("Share theme")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }) { Text(tr("Share")) }
            }
            OutlinedButton(onClick = { copyText("Focus theme prompt", ThemeJson.AI_PROMPT) }) { Text(tr("Copy AI prompt")) }

            OutlinedTextField(
                pasted, { pasted = it.take(ThemeJson.MAX_CHARS + 1) }, Modifier.fillMaxWidth(),
                label = { Text(tr("Paste a theme here")) }, minLines = 4, maxLines = 8
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    if (t.isBlank()) toast(tr("Clipboard is empty")) else { pasted = t.take(ThemeJson.MAX_CHARS + 1); tryImport(t) }
                }) { Text(tr("Paste from clipboard")) }
                Button(onClick = { tryImport(pasted) }, enabled = pasted.isNotBlank()) { Text(tr("Preview")) }
            }
            if (problems.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("This theme can't be used yet"), fontWeight = FontWeight.SemiBold)
                    problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(tr("Nothing was changed."), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetRow(p: ThemeConfig, selected: Boolean, previewing: Boolean, onClick: () -> Unit) {
    val c = p.colors
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected || previewing) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text(
                    tr(when { selected -> "In use"; previewing -> "Previewing"; else -> "Tap to preview" }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            listOf(c.background, c.card, c.primary, c.accent, c.textPrimary).forEach {
                Box(Modifier.padding(start = 4.dp).size(22.dp).clip(CircleShape).background(Color(it)))
            }
        }
    }
}
