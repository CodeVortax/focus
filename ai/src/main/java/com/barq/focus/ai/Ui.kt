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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import java.net.URL

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D50), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8DF), onPrimaryContainer = Color(0xFF0F2A22),
    secondaryContainer = Color(0xFFF1E8CF), onSecondaryContainer = Color(0xFF2B2410),
    background = Color(0xFFFBF8F2), surface = Color(0xFFFBF8F2), surfaceVariant = Color(0xFFEFEADF),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7B2), onPrimary = Color(0xFF0B2A20),
    primaryContainer = Color(0xFF1F4A3C), onPrimaryContainer = Color(0xFFD6E8DF),
    secondaryContainer = Color(0xFF3A3318), onSecondaryContainer = Color(0xFFF1E8CF),
    background = Color(0xFF121513), surface = Color(0xFF121513), surfaceVariant = Color(0xFF232925),
)

@Composable
fun AiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiApp(s: Session) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study Mate", fontWeight = FontWeight.Bold)
                        if (s.ready && s.modelName.isNotBlank()) Text(s.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
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

/** Light formatting: **bold**, "- " bullets and # headings, so answers read well without a full Markdown engine. */
fun lightMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { idx, raw ->
        var line = raw.trimEnd()
        var heading = false
        when {
            line.startsWith("### ") -> { line = line.drop(4); heading = true }
            line.startsWith("## ") -> { line = line.drop(3); heading = true }
            line.startsWith("# ") -> { line = line.drop(2); heading = true }
            line.startsWith("- ") || line.startsWith("* ") -> line = "\u2022 " + line.drop(2)
        }
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var i = 0
        while (i < line.length) {
            val a = line.indexOf("**", i)
            val b = if (a < 0) -1 else line.indexOf("**", a + 2)
            if (a < 0 || b < 0) { append(line.substring(i)); break }
            append(line.substring(i, a))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(line.substring(a + 2, b)); pop()
            i = b + 2
        }
        if (heading) pop()
        if (idx < lines.lastIndex) append("\n")
    }
}

/** Reads "Q: ... / A: ..." lines (the flashcard format) into pairs. */
fun parseCards(text: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    var q: String? = null
    for (raw in text.lines()) {
        val l = raw.replace("*", "").trim().removePrefix("-").trim()
        val isQ = Regex("^Q\\d*[:.]").containsMatchIn(l)
        val isA = Regex("^A\\d*[:.]").containsMatchIn(l)
        if (isQ) q = l.substringAfter(':').substringAfter('.').trim().ifEmpty { l }
        else if (isA && q != null) { out.add(q to l.substringAfter(':').trim()); q = null }
    }
    return out
}

@Composable
private fun ChatScreen(s: Session, scope: CoroutineScope) {
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size, s.busy) {
        val last = s.messages.size - 1 + (if (s.busy) 1 else 0)
        if (last >= 0) list.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize()) {
        if (!s.ready) SetupCard(s)
        s.incoming?.let { SharedTextCard(s, it) }
        if (s.mode != Mode.TUTOR && s.messages.isNotEmpty()) {
            Text("${s.mode.label} - tap New to go back to chat", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (s.messages.isEmpty()) item { Starters { input = it } }
            items(s.messages.size) { i -> Bubble(s, s.messages[i]) }
            if (s.busy) item { Text("Thinking...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!s.busy && s.messages.lastOrNull()?.role == "assistant") item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickReplies(s.mode).forEach { q -> SuggestionChip(onClick = { s.send(scope, q) }, label = { Text(q) }) }
                }
            }
        }
        s.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
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

private fun quickReplies(m: Mode): List<String> = when (m) {
    Mode.QUIZ -> listOf("Give me a hint", "I don't know", "Next question")
    Mode.CARDS -> listOf("Make 8 more", "Quiz me on these, one at a time")
    Mode.EXPLAIN -> listOf("Simpler, please", "Give an example", "Quiz me on this")
    Mode.SUMMARY -> listOf("Shorter", "Explain the key terms")
    Mode.TUTOR -> listOf("Give me a hint", "Explain it differently", "Test me on this")
}

@Composable
private fun Starters(fill: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What are you studying?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Ask anything, or share text from any app (Share, or select text and choose Study Mate) and pick Explain, Quiz me, Summary or Flashcards.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        listOf(
            "Help me start: what is one small step for 5 minutes?",
            "Explain simply: ",
            "Make me a revision plan for: ",
            "Test me on: ",
        ).forEach { t -> OutlinedButton(onClick = { fill(t) }, Modifier.fillMaxWidth()) { Text(t.trim().removeSuffix(":"), maxLines = 1) } }
    }
}

@Composable
private fun SetupCard(s: Session) {
    Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connect an AI to begin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Paste your API key in Settings. The provider and model are found for you.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { s.screen = "settings" }) { Text("Set up") }
        }
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
private fun Bubble(s: Session, m: ChatMsg) {
    val mine = m.role == "user"
    val clip = LocalClipboardManager.current
    val cards = if (!mine && s.mode == Mode.CARDS) parseCards(m.text) else emptyList()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (cards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEachIndexed { i, c -> FlashCard(i, c.first, c.second) } }
        } else Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 40.dp)
        ) {
            Text(
                if (mine) AnnotatedString(m.text) else lightMarkdown(m.text), Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
            )
        }
        if (!mine) TextButton(onClick = { clip.setText(AnnotatedString(m.text)) }) { Text("Copy", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun FlashCard(i: Int, q: String, a: String) {
    var shown by rememberSaveable(i, q) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { shown = !shown }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            if (shown) Text(a, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            else Text("Tap to show the answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$n. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingsScreen(s: Session, scope: CoroutineScope) {
    val p = s.prefs
    var base by rememberSaveable { mutableStateOf(p.base) }
    var model by rememberSaveable { mutableStateOf(p.model) }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(p.consent) }
    var hasKey by remember { mutableStateOf(p.hasKey()) }
    var note by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }

    fun persist() {
        p.base = base; p.model = model; p.consent = consent
        if (key.isNotBlank()) { p.setKey(key); key = ""; hasKey = p.hasKey() }
        s.refresh()
    }

    fun connect(verify: Boolean) {
        note = "Connecting..."
        s.connect(scope, base, key, model, verify) { chosen, msg -> model = chosen; note = msg; hasKey = p.hasKey() }
        key = ""
    }

    // Reopening Settings with a saved key and consent: load the model list again, without typing anything.
    LaunchedEffect(Unit) {
        if (consent && hasKey && s.models.isEmpty() && base.startsWith("https://")) connect(false)
    }

    val provider = Providers.byBase(base)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepCard(1, "Your key") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Providers.list.forEach { pr ->
                    FilterChip(selected = provider?.name == pr.name, onClick = { base = pr.base; if (pr.model.isNotEmpty()) model = pr.model; s.models = emptyList() }, label = { Text(pr.name) })
                }
            }
            if (provider != null && provider.note.isNotEmpty()) Text(provider.note, style = MaterialTheme.typography.bodyMedium)
            if (provider != null && provider.keyHelp.isNotEmpty()) Text("Get a key: ${provider.keyHelp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                key,
                { v ->
                    key = v
                    // pasting a key is enough: its shape tells which provider it is for
                    val d = Providers.detect(v)
                    if (d != null && (provider != null)) { base = d.base; if (d.model.isNotEmpty()) model = d.model; s.models = emptyList() }
                },
                Modifier.fillMaxWidth(),
                label = { Text(if (hasKey) "API key (saved, leave empty to keep)" else "Paste your API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
            )
            if (provider == null) OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Address (https only)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { persist(); connect(true) }, enabled = (key.isNotBlank() || hasKey) && !s.modelsBusy) { Text("Connect") }
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide key" else "Show key") }
                TextButton(onClick = { p.setKey(null); hasKey = false; key = ""; s.models = emptyList(); s.refresh(); note = "Key deleted." }) { Text("Delete key") }
            }
            if (s.modelsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodyMedium)
        }

        StepCard(2, "Model") {
            Text("Using: ${model.ifBlank { "none yet" }}", fontWeight = FontWeight.SemiBold)
            if (s.models.isNotEmpty()) {
                val searching = query.isNotBlank()
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search all ${s.models.size} models") }, singleLine = true)
                val shown = when {
                    searching -> s.models.filter { it.contains(query.trim(), ignoreCase = true) }
                    showAll -> s.models
                    else -> Models.ranked(s.models).take(8)
                }
                if (!searching) Text(if (showAll) "All models" else "Recommended for studying", style = MaterialTheme.typography.labelLarge)
                shown.take(60).forEach { id ->
                    Row(Modifier.fillMaxWidth().clickable { model = id; p.model = id; s.refresh() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = id == model, onClick = { model = id; p.model = id; s.refresh() })
                        Text(id, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (shown.size > 60) Text("${shown.size - 60} more: type in the search box to narrow it.", style = MaterialTheme.typography.bodySmall)
                if (!searching) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show recommended only" else "Show all ${s.models.size}") }
            }
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Or type a model name") }, singleLine = true)
            Text(
                "If a model says it is not available or gives \"too many requests\", Study Mate already tries other models for you. You can also test them now.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { persist(); note = "Testing models..."; s.findWorking(scope) { m, msg -> if (m != null) model = m; note = msg } },
                enabled = hasKey && !s.modelsBusy
            ) { Text("Find a working model") }
        }

        StepCard(3, "What is sent") {
            Text(
                "Only what you type, or text you choose to send (you see it first), plus a short fixed instruction, to ${host(base)}. " +
                    "Your key is encrypted on this phone. There is no Focus server. Nothing is saved: the chat is cleared when you tap New or close the app. " +
                    "The provider you chose handles the data under its own terms. Answers can be wrong: check important facts.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("I understand and agree", Modifier.weight(1f))
                Switch(consent, { consent = it; persist() })
            }
            var statusOn by rememberSaveable { mutableStateOf(p.statusCheck) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("When a request fails, check the provider's status online (asks prismix.dev; nothing about you or your chat is sent)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(statusOn, { statusOn = it; p.statusCheck = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(); note = "Saved." }) { Text("Save") }
                OutlinedButton(onClick = { persist(); note = "Testing..."; s.test(scope) { note = it } }) { Text("Test") }
            }
        }
        Text(
            "If you are ever in a dark place, please talk to someone you trust or a local helpline. This app is a study helper, not a doctor or a therapist.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
