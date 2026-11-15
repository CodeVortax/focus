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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Mistake
import com.barq.focus.data.Mistakes
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

private fun dateText(ms: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))

/** The mistake log: write down what went wrong and why, and it comes back after 1, 7 and 30 days to be solved again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(store: Store, onBack: () -> Unit) {
    var list by remember { mutableStateOf(store.mistakes()) }
    var adding by remember { mutableStateOf(false) }
    var onlyDue by remember { mutableStateOf(true) }
    val now = System.currentTimeMillis()
    fun save(l: List<Mistake>) { store.saveMistakes(l); list = l }
    fun replace(m: Mistake) = save(list.map { if (it.id == m.id) m else it })
    val shown = list.filter { !it.done && (!onlyDue || it.due <= now) }.sortedBy { it.due }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Mistake log")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, tr("Add a mistake")) } }
        )
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyDue, onClick = { onlyDue = true }, label = { Text(tr("Due now")) })
                    FilterChip(selected = !onlyDue, onClick = { onlyDue = false }, label = { Text(tr("All")) })
                }
            }
            if (shown.isEmpty()) item {
                Text(tr(if (onlyDue) "Nothing due. Well done." else "No mistakes saved yet."), style = MaterialTheme.typography.bodyLarge)
            }
            items(shown, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val kind = when (m.kind) { 1 -> tr("Review"); 2 -> tr("Explain again"); else -> "" }
                        Text(
                            listOf(m.subject, kind).filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                        )
                        Text(m.text, fontWeight = FontWeight.SemiBold)
                        if (m.mine.isNotBlank()) Text(tr("What I answered") + ": " + m.mine, style = MaterialTheme.typography.bodyMedium)
                        if (m.correct.isNotBlank()) Text(tr("The right answer") + ": " + m.correct, style = MaterialTheme.typography.bodyMedium)
                        if (m.cause in Mistakes.CAUSES.indices) Text(tr("Why it went wrong") + ": " + tr(Mistakes.CAUSES[m.cause]), style = MaterialTheme.typography.bodyMedium)
                        if (m.avoid.isNotBlank()) Text(tr("How I will avoid it") + ": " + m.avoid, style = MaterialTheme.typography.bodyMedium)
                        Text(trf("Try again on %s", dateText(m.due)), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { replace(Mistakes.solved(m, System.currentTimeMillis())) }) { Text(tr("Solved it")) }
                            OutlinedButton(onClick = { replace(Mistakes.missed(m, System.currentTimeMillis())) }) { Text(tr("Missed again")) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { save(list.filter { it.id != m.id }) }) { Icon(Icons.Default.Delete, tr("Delete")) }
                        }
                    }
                }
            }
        }
    }

    if (adding) MistakeDialog(onSave = { m -> save(list + m); adding = false }, onClose = { adding = false })
}

@Composable
private fun MistakeDialog(onSave: (Mistake) -> Unit, onClose: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf("") }
    var right by remember { mutableStateOf("") }
    var avoid by remember { mutableStateOf("") }
    var cause by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Add a mistake")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it.take(60) }, Modifier.fillMaxWidth(), label = { Text(tr("Subject")) }, singleLine = true)
                OutlinedTextField(q, { q = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("The question or idea")) })
                OutlinedTextField(mine, { mine = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("What I answered")) })
                OutlinedTextField(right, { right = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("The right answer")) })
                Text(tr("Why it went wrong"), fontWeight = FontWeight.SemiBold)
                Mistakes.CAUSES.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().clickable { cause = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = cause == i, onClick = { cause = i })
                        Text(tr(c))
                    }
                }
                OutlinedTextField(avoid, { avoid = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("How I will avoid it")) })
            }
        },
        confirmButton = {
            TextButton(enabled = q.isNotBlank(), onClick = {
                val now = System.currentTimeMillis()
                onSave(
                    Mistake(
                        id = now, subject = subject.trim(), text = q.trim(), mine = mine.trim(), correct = right.trim(),
                        cause = cause, avoid = avoid.trim(), kind = 0, created = now, step = 0, due = Mistakes.firstDue(now)
                    )
                )
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } }
    )
}

/** Shown when a study timer ends: three taps say how it went, and a lesson that is not clear comes back for review. */
@Composable
fun SessionCheckDialog(store: Store, subject: String, onClose: () -> Unit) {
    var hardest by remember { mutableStateOf("") }
    fun later(kind: Int) {
        val now = System.currentTimeMillis()
        store.addMistake(
            Mistake(
                id = now, subject = subject, text = hardest.trim().ifBlank { subject }, kind = kind, created = now,
                due = if (kind == 2) now else Mistakes.firstDue(now)
            )
        )
        onClose()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("How did the session go?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Did you understand it?"))
                OutlinedTextField(hardest, { hardest = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(tr("The hardest point (optional)")) })
                Button(onClick = onClose, Modifier.fillMaxWidth()) { Text(tr("Understood fully")) }
                OutlinedButton(onClick = { later(1) }, Modifier.fillMaxWidth()) { Text(tr("Needs a small review")) }
                OutlinedButton(onClick = { later(2) }, Modifier.fillMaxWidth()) { Text(tr("Needs the hard part explained again")) }
            }
        },
        confirmButton = {}
    )
}
