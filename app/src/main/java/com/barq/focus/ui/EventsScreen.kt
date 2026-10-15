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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.barq.focus.data.EV_DONE
import com.barq.focus.data.EV_MISSED
import com.barq.focus.data.EV_PENDING
import com.barq.focus.data.EventRules
import com.barq.focus.data.EventStore
import com.barq.focus.data.FocusEvent
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Reminders
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun levelName(l: Int) = tr(when (l) { 0 -> "Low"; 1 -> "Medium"; else -> "High" })

private fun dayText(ms: Long) = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

private fun inText(ms: Long): String {
    val m = ((ms + 30_000L) / 60_000L).toInt()
    return when {
        m >= 24 * 60 -> trf("in %d d %d h", m / (24 * 60), (m % (24 * 60)) / 60)
        m >= 60 -> trf("in %d h %d min", m / 60, m % 60)
        m >= 1 -> trf("in %d min", m)
        else -> tr("now")
    }
}

/** Meetings and anything with a set time: reminders before it, ringing if you let it pass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { EventStore.get(ctx) }
    var rev by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(15_000) } }
    LaunchedEffect(Unit) { Reminders.rescheduleAll(ctx); rev++ }
    val events = remember(rev) { db.all() }
    val open = events.filter { it.state == EV_PENDING }.sortedBy { it.at }
    val closed = events.filter { it.state != EV_PENDING }.sortedByDescending { it.at }
    var editing by remember { mutableStateOf<FocusEvent?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Events & meetings")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, tr("Add event")) } },
        contentWindowInsets = WindowInsets.systemBars
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (open.isEmpty() && closed.isEmpty()) item {
                Text(
                    tr("Nothing scheduled. Tap + to add a meeting or anything with a set time. Focus reminds you before it and rings if you let it pass."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)
                )
            }
            items(open, key = { it.id }) { e ->
                val overdue = clock >= e.at
                Card(
                    Modifier.fillMaxWidth().clickable { editing = e },
                    colors = if (overdue) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.SemiBold)
                        Text(
                            dayText(e.at) + "  ·  " + Reminders.clock(e.at) + "  ·  " + (if (overdue) tr("it's time!") else inText(e.at - clock)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(trf("Reminders: %s", levelName(e.level)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { Reminders.markDone(ctx, e.id); rev++ }) { Text(tr("Done")) }
                            if (overdue) OutlinedButton(onClick = { Reminders.snooze(ctx, e.id); rev++ }) {
                                Text(trf("Snooze %d min", (EventRules.SNOOZE / EventRules.M).toInt()))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { db.remove(e.id); Reminders.rescheduleEvents(ctx); rev++ }) { Icon(Icons.Default.Delete, tr("Delete")) }
                        }
                    }
                }
            }
            if (closed.isNotEmpty()) {
                item { Text(tr("Finished"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(closed, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                                Text(
                                    dayText(e.at) + "  ·  " + Reminders.clock(e.at) + "  ·  " + tr(if (e.state == EV_DONE) "done" else "missed"),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            IconButton(onClick = { db.remove(e.id); rev++ }) { Icon(Icons.Default.Delete, tr("Delete")) }
                        }
                    }
                }
            }
        }
    }

    if (adding) EventDialog(null, "", 0L, { adding = false }) { adding = false; rev++ }
    editing?.let { e -> EventDialog(e, e.title, e.noteId, { editing = null }) { editing = null; rev++ } }
}

/** Create or edit one event: title, date, time, how loudly to remind. */
@Composable
fun EventDialog(initial: FocusEvent?, defaultTitle: String, noteId: Long, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(defaultTitle) }
    var at by remember {
        mutableLongStateOf(
            initial?.at ?: Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 2); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var level by remember { mutableIntStateOf(initial?.level ?: 1) }
    var windowText by remember { mutableStateOf((initial?.windowH ?: 3).toString()) }
    val now = System.currentTimeMillis()
    val inPast = at <= now
    val cal = Calendar.getInstance().apply { timeInMillis = at }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (initial == null) "New event" else "Edit event")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text(tr("What is it? (meeting, call...)")) }, singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        android.app.DatePickerDialog(
                            ctx,
                            { _, y, m, d ->
                                val c = Calendar.getInstance().apply { timeInMillis = at; set(y, m, d) }
                                at = c.timeInMillis
                            },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) { Text(dayText(at)) }
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(
                            ctx,
                            { _, h, min ->
                                val c = Calendar.getInstance().apply {
                                    timeInMillis = at; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                }
                                at = c.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
                        ).show()
                    }) { Text(Reminders.clock(at)) }
                }
                if (inPast) Text(tr("Pick a time in the future."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                Text(tr("How hard should it nag?"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(levelName(l)) }) }
                }
                val w = (windowText.toIntOrNull() ?: 3).coerceIn(1, 12)
                Text(
                    when (level) {
                        0 -> tr("One reminder, 1 hour before.")
                        1 -> trf("A reminder every hour, starting %d h before.", w)
                        else -> trf("Every hour starting %d h before, then every 10 min during the last hour.", w)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (level != 0) OutlinedTextField(
                    windowText, { windowText = it.filter(Char::isDigit).take(2) },
                    label = { Text(tr("Start reminding (hours before)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(tr("If the time passes and you haven't tapped Done, Focus rings until you do."), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !inPast, onClick = {
                val t = System.currentTimeMillis()
                val e = FocusEvent(
                    id = initial?.id ?: t, title = title.trim(), at = at, level = level,
                    windowH = (windowText.toIntOrNull() ?: 3).coerceIn(1, 12), created = t,
                    noteId = if (noteId != 0L) noteId else (initial?.noteId ?: 0L)
                )
                EventStore.get(ctx).upsert(e)
                Reminders.rescheduleAll(ctx)
                onSaved()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
