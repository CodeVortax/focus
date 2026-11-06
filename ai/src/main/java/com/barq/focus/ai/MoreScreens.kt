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

package com.barq.focus.ai

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Starts Focus's "send today" screen and puts the answer into the message box. Returns the function the buttons call. */
@Composable
fun rememberDayLauncher(s: Session): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringExtra(Intent.EXTRA_TEXT)
            if (!t.isNullOrBlank()) s.receiveDay(t) else s.notice = "Focus sent nothing."
        }
    }
    return {
        try { launcher.launch(FocusBridge.askDayIntent()) }
        catch (_: Exception) { s.notice = "Could not reach Focus. Is it installed and up to date?" }
    }
}

@Composable
fun BottomNav(s: Session) {
    val tabs = listOf(
        Triple("chat", "💬", "Chat"),
        Triple("day", "📅", "My day"),
        Triple("memory", "🧠", "Memory"),
        Triple("settings", "⚙️", "Settings"),
    )
    NavigationBar {
        tabs.forEach { (id, icon, label) ->
            NavigationBarItem(selected = s.screen == id, onClick = { s.screen = id }, icon = { Text(icon) }, label = { Text(label) })
        }
    }
}

@Composable
fun DayScreen(s: Session, askDay: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Pull today's tasks and usage from Focus. Focus shows you exactly what it will send and asks first. " +
                "Nothing goes to the AI until you press Send in the chat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = askDay, modifier = Modifier.fillMaxWidth()) { Text(if (s.dayText == null) "Get my day from Focus" else "Refresh from Focus") }
        s.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        val t = s.dayText
        if (t != null) {
            Card(Modifier.fillMaxWidth()) { Text(t, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { s.paste = "My day from Focus:\n$t\n\n"; s.pasteTick++; s.screen = "chat" }) { Text("Ask about it") }
                OutlinedButton(onClick = { s.dayText = null }) { Text("Clear") }
            }
            Text(
                "Try: \"Plan the rest of my day\", \"What should I do first?\", \"Why was today hard?\"",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val PRESETS = listOf(
    MemKind.STYLE to "Keep answers short",
    MemKind.STYLE to "Explain with a real example first",
    MemKind.STYLE to "Talk to me like a friend, not a teacher",
    MemKind.HELP to "Give me a hint before the answer",
    MemKind.HELP to "Break big tasks into 5-minute steps",
    MemKind.HELP to "Quiz me one question at a time",
    MemKind.CAREFUL to "Don't scold me if I did little today",
    MemKind.CAREFUL to "Don't mention streaks or comparisons",
    MemKind.CAREFUL to "Don't give long lists",
)

@Composable
fun MemoryScreen(s: Session) {
    var kind by remember { mutableStateOf(MemKind.STYLE) }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    val tokens = (MemoryCodec.promptBlock(s.memory.toList()).length + 3) / 4
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Notes about you that are sent with every message (when switched on), so the AI knows how to help you. " +
                "They are stored encrypted on this phone, and you can read, edit and delete every one. " +
                "They are not used while the app is in its gentle care mode, and they never switch off its safety rules.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("${s.memory.count { it.on }} notes on, about $tokens tokens per message", style = MaterialTheme.typography.labelMedium)

        MemKind.values().forEach { k ->
            val items = s.memory.filter { it.kind == k }
            if (items.isNotEmpty()) {
                Text(k.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                items.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(m.on, { s.toggleMemory(m.id) })
                        Text(m.text, Modifier.weight(1f).padding(horizontal = 10.dp))
                        TextButton(onClick = { editing = m }) { Text("Edit") }
                        TextButton(onClick = { s.removeMemory(m.id) }) { Text("Delete") }
                    }
                }
            }
        }
        if (s.memory.isEmpty()) Text("Nothing yet. Add a note below, or tap a suggestion.", style = MaterialTheme.typography.bodyMedium)

        Text("Add a note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemKind.values().forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) }) }
        }
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(), maxLines = 3,
            placeholder = { Text(when (kind) {
                MemKind.STYLE -> "e.g. Short answers, simple words"
                MemKind.HELP -> "e.g. I learn best from examples"
                MemKind.CAREFUL -> "e.g. Don't pressure me when I'm tired"
                MemKind.ABOUT -> "e.g. I'm preparing for my physics exam"
            }) }
        )
        Button(onClick = { if (s.addMemory(kind, text)) text = "" }, enabled = text.trim().length >= 3) { Text("Add") }

        val left = PRESETS.filter { (k, t) -> s.memory.none { it.kind == k && it.text.equals(t, ignoreCase = true) } }
        if (left.isNotEmpty()) {
            Text("Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                left.forEach { (k, t) -> SuggestionChip(onClick = { s.addMemory(k, t) }, label = { Text(t) }) }
            }
        }
    }
    editing?.let { m ->
        var draft by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(m.kind.title) },
            text = { OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), maxLines = 4) },
            confirmButton = { TextButton(onClick = { s.editMemory(m.id, draft); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

/** A note the model proposed. Nothing is saved unless a button is tapped. */
@Composable
fun MemorySuggestCard(s: Session) {
    val n = s.suggest ?: return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Remember this about you?", style = MaterialTheme.typography.labelLarge)
            Text("“$n”")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemKind.values().forEach { k -> OutlinedButton(onClick = { s.addMemory(k, n); s.suggest = null }) { Text(k.title) } }
                TextButton(onClick = { s.suggest = null }) { Text("No") }
            }
        }
    }
}
