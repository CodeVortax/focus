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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Foc
import com.barq.focus.data.NotesDb
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Shared
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun share(body: String, subject: String) = ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body), subject
        )
    )
    fun saveFoc(prefix: String, body: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val ok = Foc.saveToDownloads(ctx, "$prefix-$stamp.foc", body)
        if (ok) toast(trf("Saved %s to Downloads", "$prefix-$stamp.foc")) else share(body, "$prefix.foc")
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) result = Foc.applyUri(ctx, uri)
    }

    // something was opened with / shared to Focus
    LaunchedEffect(Shared.pendingTick) {
        val u = Shared.pendingUri
        val t = Shared.pendingText
        Shared.pendingUri = null; Shared.pendingText = null
        if (u != null) result = Foc.applyUri(ctx, u)
        else if (t != null) { text = t; result = Foc.applyText(ctx, t) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Import / Export (.foc)")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                tr("A .foc (or .fcs) file can be anything: the header decides. A video/audio becomes a treat, a PDF goes to the library, JSON with a \"focus\" header can be today's work, config, notes or a language."),
                style = MaterialTheme.typography.bodyMedium
            )
            if (result.isNotEmpty()) Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) { Text(result, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold) }

            Text(tr("Add from a file"), fontWeight = FontWeight.SemiBold)
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text(tr("Choose .foc / .fcs / video / PDF")) }

            Spacer(Modifier.height(6.dp))
            Text(tr("Paste (from any AI)"), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), minLines = 5, maxLines = 10,
                label = { Text(tr("Paste the JSON here")) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = text.isNotBlank(), onClick = { result = Foc.applyText(ctx, text) }) { Text(tr("Import pasted")) }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (t.isNotBlank()) text = t else toast(tr("Clipboard is empty"))
                }) { Text(tr("Paste")) }
            }
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("prompt", Foc.aiPrompt(ctx)))
                toast(tr("Prompt copied. Give it to any AI, then paste its answer here."))
            }) { Text(tr("Copy AI prompt")) }

            Spacer(Modifier.height(6.dp))
            Text(tr("Export"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveFoc("focus-today", Foc.exportToday(ctx)) }) { Text(tr("Today .foc")) }
                OutlinedButton(onClick = { saveFoc("focus-config", Foc.exportConfig(ctx)) }) { Text(tr("Config .foc")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveFoc("focus-notes", NotesDb.get(ctx).exportJson()) }) { Text(tr("Notes .foc")) }
                OutlinedButton(onClick = { saveFoc("focus-language", Foc.langTemplate()) }) { Text(tr("Language template")) }
            }
            Text(
                tr("Language template: give it to an AI to translate the values, then import the result to add that language."),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
