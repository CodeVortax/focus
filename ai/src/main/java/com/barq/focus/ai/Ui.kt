/*
 * Focus Study Mate: the optional AI helper for Focus
 * Copyright (C) 2026 MR.English2008
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import java.net.URL

@Composable
fun AiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiApp(s: Session) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Mate") },
                actions = {
                    if (s.screen == "chat") TextButton(onClick = { s.clear() }) { Text("New") }
                    TextButton(onClick = { s.screen = if (s.screen == "chat") "settings" else "chat" }) {
                        Text(if (s.screen == "chat") "Settings" else "Chat")
                    }
                }
            )
        }
    ) { pad ->
        Surface(Modifier.padding(pad).fillMaxSize()) {
            if (s.screen == "settings") SettingsScreen(s, scope) else ChatScreen(s, scope)
        }
    }
}

private fun host(base: String): String = try { URL(base).host } catch (_: Exception) { base }
private fun tokens(chars: Int): Int = (chars + 3) / 4

@Composable
private fun ChatScreen(s: Session, scope: CoroutineScope) {
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size, s.busy) {
        val last = s.messages.size - 1 + (if (s.busy) 1 else 0)
        if (last >= 0) list.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize()) {
        s.incoming?.let { SharedTextCard(s, it) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (s.messages.isEmpty()) item {
                Text(
                    "Ask about what you are studying, or share text from any app (Share, or select text and choose Study Mate) " +
                        "and pick Explain, Quiz me, Summary or Flashcards.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(s.messages.size) { i -> Bubble(s.messages[i]) }
            if (s.busy) item { Text("Thinking...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = { Text("Ask or paste what you are studying") }, maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { s.send(scope, input); input = "" }, enabled = input.isNotBlank() && !s.busy) { Text("Send") }
        }
    }
    s.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { s.pending = null },
            title = { Text("Send this?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "To: ${host(s.prefs.base)}, model ${s.prefs.model}\nAbout ${tokens(p.text.length)} tokens (your provider may charge for this).",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(p.text.take(1500) + if (p.text.length > 1500) "..." else "")
                    Text("Plus a short fixed instruction for \"${p.mode.label}\". Nothing else is sent.", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { TextButton(onClick = { val x = p; s.pending = null; s.startMaterial(scope, x) }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { s.pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedTextCard(s: Session, text: String) {
    Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text received (${text.length} characters${if (s.cut) ", cut to the first ${Session.MAX_MATERIAL}" else ""})", style = MaterialTheme.typography.labelLarge)
            Text(text.take(160) + if (text.length > 160) "..." else "", style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mode.EXPLAIN, Mode.QUIZ, Mode.SUMMARY, Mode.CARDS).forEach { m ->
                    OutlinedButton(onClick = { s.pending = Pending(m, text) }) { Text(m.label) }
                }
                TextButton(onClick = { s.incoming = null }) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 40.dp)
        ) {
            Text(
                m.text, Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
            )
        }
    }
}

@Composable
private fun SettingsScreen(s: Session, scope: CoroutineScope) {
    val p = s.prefs
    var base by rememberSaveable { mutableStateOf(p.base) }
    var model by rememberSaveable { mutableStateOf(p.model) }
    var key by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(p.consent) }
    var hasKey by remember { mutableStateOf(p.hasKey()) }
    var note by remember { mutableStateOf("") }

    fun save() {
        p.base = base; p.model = model; p.consent = consent
        if (key.isNotBlank()) { p.setKey(key); key = ""; hasKey = p.hasKey() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Provider", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Providers.list.forEach { pr ->
                FilterChip(selected = base == pr.base, onClick = { base = pr.base; if (pr.model.isNotEmpty()) model = pr.model }, label = { Text(pr.name) })
            }
        }
        OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Address (https only)") }, singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(),
            label = { Text(if (hasKey) "API key (saved, leave empty to keep)" else "API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation()
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What is sent", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Only what you type, or text you choose to send (you see it first), plus a short fixed instruction, to the address above. " +
                        "Your key is encrypted on this phone. There is no Focus server. Nothing is saved: the chat is cleared when you tap New or close the app. " +
                        "The provider you chose handles the data under its own terms. Answers can be wrong: check important facts.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I understand and agree", Modifier.weight(1f))
                    Switch(consent, { consent = it })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save(); note = "Saved." }) { Text("Save") }
            OutlinedButton(onClick = { save(); note = "Testing..."; s.test(scope) { note = it } }) { Text("Test") }
            TextButton(onClick = { p.setKey(null); hasKey = false; key = ""; note = "Key deleted." }) { Text("Delete key") }
        }
        if (note.isNotEmpty()) Text(note)
        Spacer(Modifier.height(8.dp))
        Text(
            "If you are ever in a dark place, please talk to someone you trust or a local helpline. This app is a study helper, not a doctor or a therapist.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
