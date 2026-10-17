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

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.barq.focus.service.Alerts
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Config
import com.barq.focus.data.FocusCard
import com.barq.focus.data.Item
import com.barq.focus.data.Note
import com.barq.focus.data.NotesDb
import com.barq.focus.data.Store
import com.barq.focus.data.minText
import com.barq.focus.data.TaskGroup
import com.barq.focus.data.Voices
import com.barq.focus.data.VoicePlayer
import com.barq.focus.data.Weekly
import com.barq.focus.data.SubTask
import com.barq.focus.data.dayItems
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Notifier
import com.barq.focus.service.TimerAlarm
import com.barq.focus.ui.theme.CardSwatches
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.delay

/** One subject: today's checklist + an extendable, pausable timer with its own alarm sound. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubjectScreen(store: Store, initial: FocusCard, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val addRequester = remember { BringIntoViewRequester() }
    var card by remember { mutableStateOf(initial) }
    var msg by remember { mutableStateOf("") }
    var newItem by remember { mutableStateOf("") }
    var budgetText by remember { mutableStateOf(initial.minutes.toString()) }
    var showPark by remember { mutableStateOf(false) }
    var rewardText by remember { mutableStateOf(initial.reward.toString()) }
    var compact by remember { mutableStateOf(store.timerCompact) }
    var addWeekly by remember { mutableStateOf(false) }
    var newDue by remember { mutableLongStateOf(0L) }       // 0 = end of this week
    var dueDlg by remember { mutableStateOf<Int?>(null) }   // -1 = the new task, else the task index
    var weekReward by remember { mutableStateOf(Config.WEEKLY_BASE_MIN.toString()) }
    var addGroup by remember { mutableStateOf("") }
    var groupMenu by remember { mutableStateOf(false) }
    var groupDlg by remember { mutableStateOf<GroupDlg?>(null) }
    var recordFor by remember { mutableIntStateOf(-1) }

    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var endAt by remember { mutableLongStateOf(0L) }
    var pausedLeft by remember { mutableLongStateOf(0L) }
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis()) }

    // the store is the single source of truth (the alarm receiver can change it too)
    fun sync() {
        val here = store.activeCardId == card.id
        running = here
        paused = here && store.activePaused
        endAt = if (here) store.activeEnd else 0L
        pausedLeft = store.activeRemaining
        nowState.longValue = System.currentTimeMillis()
    }
    fun refresh() { Alerts.stop(); TimerAlarm.reschedule(ctx, store); Notifier.syncTimer(ctx, store); sync() }

    LaunchedEffect(Unit) { while (true) { sync(); delay(500) } }

    fun stopTimer() { store.stopActive(); refresh() }

    fun commit(items: List<Item>, extraSec: Int = 0) {
        card = card.copy(items = items)
        store.updateCard(card)
        val gained = store.settle(store.cards()) + extraSec
        if (gained > 0) msg = trf("+%s min added to your treat bank", minText(gained))
        val day = card.dayItems
        if (day.isNotEmpty() && day.all { it.done } && running) stopTimer()
    }

    /** Ticks a task. A weekly task pays once, scaled by how early in the week you finish it. */
    fun setDone(i: Int, v: Boolean) {
        val x = card.items[i]
        var next = x.copy(done = v)
        var award = 0
        if (v && x.weekly && !x.paid) {
            award = Weekly.rewardSec(x, System.currentTimeMillis())
            next = next.copy(paid = true)
            if (award > 0) { store.earnSec(award); store.touchDay() }
        }
        commit(card.items.toMutableList().also { l -> l[i] = next }, award)
    }

    fun setSubs(i: Int, l: List<SubTask>) {
        val x = card.items[i]
        commit(card.items.toMutableList().also { it[i] = x.copy(subs = l) })
        if (l.isNotEmpty() && l.all { it.done } && !x.done) setDone(i, true)          // last step ticked: the task is done
        else if (x.done && l.any { !it.done }) commit(card.items.toMutableList().also { it[i] = it[i].copy(done = false) })
    }

    fun moveTo(i: Int, g: String) = commit(card.items.toMutableList().also { l -> l[i] = l[i].copy(group = g) })

    fun saveGroups(g: List<TaskGroup>) { card = card.copy(groups = g); store.updateCard(card) }

    fun addMinutes(m: Int) {
        if (running) {
            store.addActiveTime(m * 60_000L); refresh()
        } else {
            card = card.copy(minutes = card.minutes + m)
            store.updateCard(card)
            budgetText = card.minutes.toString()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            card = card.copy(sound = u?.toString() ?: "silent")
            store.updateCard(card)
        }
    }
    val soundName = remember(card.sound) {
        when (card.sound) {
            "" -> tr("Default alarm")
            "silent" -> tr("Silent")
            else -> try { RingtoneManager.getRingtone(ctx, Uri.parse(card.sound))?.getTitle(ctx) ?: "Custom" } catch (_: Exception) { "Custom" }
        }
    }

    val dayList = card.dayItems
    val total = dayList.size
    val done = dayList.count { it.done }
    val col = Color(card.color)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(card.title) },
            navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }, contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SubjectTimerCard(
                running, paused, endAt, pausedLeft, card.minutes * 60_000L, nowState, col, compact,
                onPlayPause = {
                    when {
                        !running -> { store.startActive(card.id, System.currentTimeMillis() + card.minutes * 60_000L); refresh() }
                        paused -> { store.resumeActive(); refresh() }
                        else -> { store.pauseActive(); refresh() }
                    }
                },
                onStop = { stopTimer() },
                onToggleCompact = { compact = !compact; store.timerCompact = compact }
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10).forEach { m -> OutlinedButton(onClick = { addMinutes(m) }) { Text("+${m}m") } }
                    }
                    if (!running) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            budgetText,
                            { s ->
                                budgetText = s.filter(Char::isDigit).take(3)
                                val v = budgetText.toIntOrNull()
                                if (v != null && v > 0) { card = card.copy(minutes = v); store.updateCard(card) }
                            },
                            Modifier.width(170.dp),
                            label = { Text(tr("Budget (min)")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            rewardText,
                            { s ->
                                rewardText = s.filter(Char::isDigit).take(3)
                                val v = rewardText.toIntOrNull()
                                if (v != null) { card = card.copy(reward = v); store.updateCard(card) }
                            },
                            Modifier.width(170.dp),
                            label = { Text(tr("Reward (min, whole subject)")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    val spentMin = store.spent(card.id) / 60_000
                    if (spentMin > 0) Text(trf("Logged today: %dm", spentMin), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Timer sound"), style = MaterialTheme.typography.titleMedium)
                        Text(soundName + "  ·  " + tr("rings + notifies when time is up"), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = {
                        picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer sound")
                            val cur: Uri? = if (card.sound.isEmpty() || card.sound == "silent") null else Uri.parse(card.sound)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur)
                        })
                    }) { Text(tr("Change")) }
                }
            }

            TextButton(onClick = { showPark = true }, contentPadding = PaddingValues(0.dp)) { Text(tr("Park a thought (saves to Notes)")) }
            if (showPark) {
                var thought by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showPark = false },
                    title = { Text(tr("Park a thought")) },
                    text = {
                        OutlinedTextField(
                            thought, { thought = it }, minLines = 3,
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                            label = { Text(tr("Get it out of your head")) }
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = thought.isNotBlank(), onClick = {
                            NotesDb.get(ctx).save(Note(title = "Parked · ${card.title}", body = thought.trim(), labels = listOf("Parked")))
                            android.widget.Toast.makeText(ctx, tr("Parked. Back to it."), android.widget.Toast.LENGTH_SHORT).show()
                            showPark = false
                        }) { Text(tr("Park it")) }
                    },
                    dismissButton = { TextButton(onClick = { showPark = false }) { Text(tr("Cancel")) } }
                )
            }
            if (msg.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(msg, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                when {
                    total == 0 -> tr(Config.freeWord(card.id)) + "  ·  " + tr("no tasks yet")
                    done == total -> trf("Plate cleared (%d/%d)", total, total)
                    else -> trf("%d left on the plate  ·  %d of %d done", total - done, done, total)
                },
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.fillMaxWidth(), color = col, trackColor = col.copy(alpha = 0.2f)
            )

            @Composable
            fun RowFor(i: Int) {
                val x = card.items[i]
                TaskRow(
                    item = x, seed = i, color = col, groups = card.groups,
                    onToggle = { v -> setDone(i, v) },
                    onDelete = {
                        if (VoicePlayer.playing == x.voice) VoicePlayer.stop()
                        Voices.delete(ctx, x.voice)
                        commit(card.items.filterIndexed { j, _ -> j != i })
                    },
                    onSubs = { l -> setSubs(i, l) },
                    onDue = { dueDlg = i },
                    onMoveToGroup = { g -> moveTo(i, g) },
                    onNewGroup = { groupDlg = GroupDlg(null, i) },
                    onRecord = { recordFor = i },
                    onDeleteVoice = {
                        if (VoicePlayer.playing == x.voice) VoicePlayer.stop()
                        Voices.delete(ctx, x.voice)
                        commit(card.items.toMutableList().also { l -> l[i] = x.copy(voice = "", voiceMs = 0L) })
                    }
                )
            }

            val weekIdx = card.items.indices.filter { card.items[it].weekly }
            if (weekIdx.isNotEmpty()) {
                Text(tr("Planned"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Do these any day before the date. The sooner you finish, the bigger the reward."), style = MaterialTheme.typography.bodyMedium)
                weekIdx.forEach { RowFor(it) }
            }

            val dayIdx = card.items.indices.filter { !card.items[it].weekly }
            val known = card.groups.map { it.name }.toSet()
            dayIdx.filter { card.items[it].group !in known }.forEach { RowFor(it) }
            card.groups.forEach { g ->
                val idx = dayIdx.filter { card.items[it].group == g.name }
                GroupHeader(
                    g, idx.count { card.items[it].done }, idx.size,
                    onToggle = { saveGroups(card.groups.map { x -> if (x.name == g.name) x.copy(collapsed = !x.collapsed) else x }) },
                    onEdit = { groupDlg = GroupDlg(g, -1) }
                )
                if (!g.collapsed) {
                    Column(
                        Modifier.padding(start = 6.dp).drawBehind {
                            drawRect(Color(g.color), size = Size(3.dp.toPx(), size.height))
                        }.padding(start = 4.dp)
                    ) {
                        if (idx.isEmpty()) Text(tr("Empty. Use the ⋮ menu on a task to move it here."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                        idx.forEach { RowFor(it) }
                    }
                }
            }

            val addTask: () -> Unit = {
                if (newItem.isNotBlank()) {
                    val t = newItem.trim()
                    val x = if (addWeekly) Weekly.make(t, weekReward.toIntOrNull() ?: Config.WEEKLY_BASE_MIN, due = newDue)
                    else Item(t, false, group = if (card.groups.any { g -> g.name == addGroup }) addGroup else "")
                    commit(card.items + x)
                    newItem = ""; newDue = 0L
                }
            }
            Row(
                Modifier.bringIntoViewRequester(addRequester),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    newItem, { newItem = it },
                    Modifier.weight(1f).onFocusEvent { f ->
                        if (f.isFocused) scope.launch { delay(300); addRequester.bringIntoView() }
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    label = { Text(tr(if (addWeekly) "Add a planned task" else "Add a task")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTask() })
                )
                Button(enabled = newItem.isNotBlank(), onClick = addTask) { Text(tr("Add")) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = addWeekly, onClick = { addWeekly = !addWeekly }, label = { Text(tr("Planned")) })
                if (addWeekly) {
                    AssistChip(
                        onClick = { dueDlg = -1 },
                        label = {
                            Text(
                                if (newDue > 0L) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(newDue - 1L))
                                else tr("End of this week")
                            )
                        }
                    )
                    OutlinedTextField(
                        weekReward, { s2 -> weekReward = s2.filter(Char::isDigit).take(3) },
                        Modifier.width(150.dp), label = { Text(tr("Reward (min)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Box {
                        AssistChip(
                            onClick = { groupMenu = true },
                            label = { Text(tr("Group") + ": " + (if (card.groups.any { it.name == addGroup }) addGroup else tr("none"))) }
                        )
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            DropdownMenuItem(text = { Text(tr("No group")) }, onClick = { groupMenu = false; addGroup = "" })
                            card.groups.forEach { g ->
                                DropdownMenuItem(text = { Text("●  " + g.name, color = Color(g.color)) }, onClick = { groupMenu = false; addGroup = g.name })
                            }
                            DropdownMenuItem(text = { Text(tr("New group…")) }, onClick = { groupMenu = false; groupDlg = GroupDlg(null, -2) })
                        }
                    }
                }
            }
            Text(
                if (total > 0) trf(
                    "Reward: %d min for this subject, split over %d tasks = %s min each. Finish every subject today: +%d min (the episode).",
                    card.reward, total, minText(card.reward * 60 / total), Config.DAY_BONUS
                ) else trf(
                    "Reward: %d min for this subject, split over its tasks. Finish every subject today: +%d min (the episode).",
                    card.reward, Config.DAY_BONUS
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    dueDlg?.let { di ->
        DueDialog(
            initial = if (di >= 0) card.items[di].deadline else newDue,
            onDismiss = { dueDlg = null },
            onPick = { d -> if (di >= 0) commit(card.items.toMutableList().also { it[di] = it[di].copy(deadline = d) }) else newDue = d; dueDlg = null },
            onEndOfWeek = {
                val e = Weekly.endOfWeek(System.currentTimeMillis())
                if (di >= 0) commit(card.items.toMutableList().also { it[di] = it[di].copy(deadline = e) }) else newDue = 0L
                dueDlg = null
            }
        )
    }
    groupDlg?.let { dlg ->
        val e = dlg.edit
        GroupDialog(
            initial = e,
            defaultColor = CardSwatches[card.groups.size % CardSwatches.size].toArgb(),
            onDismiss = { groupDlg = null },
            onSave = { name, color ->
                if (e == null) {
                    if (card.groups.none { it.name == name }) saveGroups(card.groups + TaskGroup(name, color))
                    if (dlg.moveIndex >= 0 && dlg.moveIndex < card.items.size) moveTo(dlg.moveIndex, name)
                    else if (dlg.moveIndex == -2) addGroup = name
                } else {
                    val newName = if (name != e.name && card.groups.any { it.name == name }) e.name else name
                    saveGroups(card.groups.map { g -> if (g.name == e.name) g.copy(name = newName, color = color) else g })
                    if (newName != e.name) commit(card.items.map { x -> if (x.group == e.name) x.copy(group = newName) else x })
                    if (addGroup == e.name) addGroup = newName
                }
                groupDlg = null
            },
            onDelete = if (e == null) null else ({
                commit(card.items.map { x -> if (x.group == e.name) x.copy(group = "") else x })
                saveGroups(card.groups.filter { g -> g.name != e.name })
                if (addGroup == e.name) addGroup = ""
                groupDlg = null
            })
        )
    }

    if (recordFor >= 0) VoiceRecordDialog(tr("Voice note"), { recordFor = -1 }) { f, ms ->
        val i = recordFor
        if (i in card.items.indices) {
            Voices.delete(ctx, card.items[i].voice)
            commit(card.items.toMutableList().also { l -> l[i] = l[i].copy(voice = f, voiceMs = ms) })
        } else Voices.delete(ctx, f)
    }
}

private data class GroupDlg(val edit: TaskGroup?, val moveIndex: Int)

/** Round, filled check circle instead of the stock square checkbox. */
@Composable
fun RoundCheck(checked: Boolean, color: Color, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier.padding(8.dp).size(28.dp).clip(CircleShape)
            .background(if (checked) color else Color.Transparent)
            .border(2.dp, if (checked) color else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { onToggle(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
