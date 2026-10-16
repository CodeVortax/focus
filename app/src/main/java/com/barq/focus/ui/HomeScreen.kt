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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import com.barq.focus.data.Badge
import com.barq.focus.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.MoreVert
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import com.barq.focus.data.Weekly
import com.barq.focus.data.dayItems
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.barq.focus.data.CardType
import com.barq.focus.data.Config
import com.barq.focus.data.Db
import com.barq.focus.data.FocusCard
import com.barq.focus.data.Item
import com.barq.focus.data.Store
import com.barq.focus.data.startOfDay
import com.barq.focus.service.Perms
import com.barq.focus.ui.theme.CardSwatches
import com.barq.focus.ui.theme.FocusBrandFont

fun typeLabel(t: CardType) = tr(when (t) { CardType.STUDY -> "Study"; CardType.TREAT -> "Treat"; CardType.FREE -> "Free" })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: Store, onOpen: (FocusCard) -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose {} }

    var cards by remember(rev) { mutableStateOf(store.also { it.rollover() }.cards()) }
    val bank = remember(rev) { store.bank() }
    val setupOk = remember(rev) { Perms.ready(ctx) && Perms.accessibility(ctx) }
    val running = remember(rev) { cards.firstOrNull { it.id == store.activeCardId } }
    val (done, total) = remember(rev, cards) { store.progress(cards) }
    val swipes = remember(rev) { store.shortsSwipes() }
    val shortsMin = remember(rev) { Db.get(ctx).totalsSince("shorts", startOfDay()).values.sum() / 60_000 }

    var showAdd by remember { mutableStateOf(false) }
    var fresh by remember { mutableStateOf<List<Badge>>(emptyList()) }
    val workStreak = remember(rev) { Game.workStreak(store) }
    val lightStreak = remember(rev) { Game.lightStreak(store) }
    val xp = remember(rev) { Game.xp(store) }
    LaunchedEffect(rev) {
        val got = withContext(Dispatchers.IO) { Game.evaluate(ctx, store) }
        if (got.isNotEmpty()) fresh = got
    }
    var showAddMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pendingTreat by remember { mutableStateOf<FocusCard?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val c = pendingTreat
        pendingTreat = null
        if (uri != null && c != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val updated = c.copy(media = uri.toString())
            store.updateCard(updated)
            cards = store.cards()
            onOpen(updated)
        }
    }
    val open: (FocusCard) -> Unit = { c ->
        if (c.type == CardType.TREAT && c.media.isEmpty()) {
            pendingTreat = c
            mediaPicker.launch(arrayOf("video/*", "audio/*"))
        } else onOpen(c)
    }
    var showEnd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FocusCard?>(null) }
    var menu by remember { mutableStateOf<FocusCard?>(null) }
    var editing by remember { mutableStateOf<FocusCard?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Focus"), fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { go("notes") }) { Icon(Icons.Default.Description, "Notes") }
                    IconButton(onClick = { go("pdfs") }) { Icon(Icons.Default.MenuBook, "Library") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Music")) }, leadingIcon = { Icon(Icons.Default.LibraryMusic, null) },
                                onClick = { menu = false; go("music") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Games")) }, leadingIcon = { Icon(Icons.Default.SportsEsports, null) },
                                onClick = { menu = false; go("games") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Trophies")) }, leadingIcon = { Icon(Icons.Default.Star, null) },
                                onClick = { menu = false; go("trophies") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Log")) }, leadingIcon = { Icon(Icons.Default.History, null) },
                                onClick = { menu = false; go("log") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Events & meetings")) }, leadingIcon = { Icon(Icons.Default.Event, null) },
                                onClick = { menu = false; go("events") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Not-free blocks")) }, leadingIcon = { Icon(Icons.Default.Schedule, null) },
                                onClick = { menu = false; go("schedule") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Import / Export (.foc)")) }, leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { menu = false; go("import") }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Setup")) }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menu = false; go("setup") }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!setupOk) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().clickable { go("setup") }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Setup needed"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Usage access, overlay and accessibility. Tap here."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            running?.let {
                Card(Modifier.fillMaxWidth().clickable { onOpen(it) }) {
                    Text(trf("Timer running: %s. Tap to open.", it.title), Modifier.padding(16.dp))
                }
            }
            Card(Modifier.fillMaxWidth().clickable { go("log") }) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            total == 0 -> tr(Config.freeWord(startOfDay() / 86_400_000L)) + "  ·  " + tr("nothing on your plate")
                            done == total -> tr("Plate cleared  ·  nothing left")
                            else -> trf("%d left on your plate (of %d)", total - done, total)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        trf("Treat bank: %s min  ·  Shorts today: %d swiped, %d min", store.bankText(), swipes, shortsMin),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val (weekLeft, weekAll) = Weekly.progress(cards)
                    if (weekAll > 0) Text(
                        trf("This week: %d of %d tasks left", weekLeft, weekAll),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary
                    )
                    val lvl = Game.level(xp)
                    Text(
                        "🔥 $workStreak   🛡️ $lightStreak   " + Game.titleOf(lvl).first + " " +
                            trf("Lv %d", lvl) + " " + tr(Game.titleOf(lvl).second),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                    )
                    if (total > 0 && done < total) {
                        TextButton(onClick = { showEnd = true }, contentPadding = PaddingValues(0.dp)) { Text(tr("End day")) }
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
            ) {
                items(cards, key = { it.id }) { c -> CardTile(c, store.spent(c.id) / 60_000, { open(c) }, { menu = c }) }
                item {
                    Box(
                        Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showAddMenu = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "Add card") }
                }
            }
        }
    }

    if (fresh.isNotEmpty()) AlertDialog(
        onDismissRequest = { fresh = emptyList() },
        title = { Text(tr("Badge unlocked!")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fresh.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeArt(b.id, b.emoji, 56.dp, false); Spacer(Modifier.width(12.dp))
                        Column { Text(tr(b.name), fontWeight = FontWeight.Bold); Text(tr(b.desc), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { fresh = emptyList() }) { Text(tr("Nice")) } }
    )
    if (showAddMenu) AlertDialog(
        onDismissRequest = { showAddMenu = false },
        confirmButton = {},
        title = { Text(tr("Add")) },
        text = {
            Column {
                TextButton(onClick = { showAddMenu = false; showAdd = true }) { Text(tr("New card")) }
                TextButton(onClick = { showAddMenu = false; go("import") }) { Text(tr("Import / paste (.foc)")) }
            }
        }
    )
    if (showAdd) AddCardDialog({ showAdd = false }) { c ->
        store.saveCards(cards + c); cards = store.cards(); showAdd = false
    }
    menu?.let { m ->
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(m.title) },
            text = {
                Column {
                    TextButton(onClick = { editing = m; menu = null }) { Text(tr("Edit")) }
                    if (m.type == CardType.TREAT) TextButton(onClick = {
                        menu = null; pendingTreat = m; mediaPicker.launch(arrayOf("video/*", "audio/*"))
                    }) { Text(tr("Change media file")) }
                    TextButton(onClick = { deleting = m; menu = null }) { Text(tr("Delete")) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menu = null }) { Text(tr("Cancel")) } }
        )
    }
    editing?.let { e ->
        AddCardDialog({ editing = null }, initial = e) { c ->
            store.saveCards(store.cards().map { if (it.id == c.id) c else it }); cards = store.cards(); editing = null
        }
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(trf("Delete \"%s\"?", d.title)) },
            text = {
                if (d.type == CardType.TREAT) TextButton(onClick = {
                    deleting = null; pendingTreat = d; mediaPicker.launch(arrayOf("video/*", "audio/*"))
                }) { Text(tr("Change media file")) }
            },
            confirmButton = {
                TextButton(onClick = { store.saveCards(cards.filter { it.id != d.id }); cards = store.cards(); deleting = null }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel")) } }
        )
    }
    if (showEnd) {
        val eligible = store.partialEligible(cards)
        AlertDialog(
            onDismissRequest = { showEnd = false },
            title = { Text(tr("End day")) },
            text = {
                Text(
                    if (eligible) trf("You did %d of %d tasks. Take a half episode (+%d min)?", done, total, Config.PARTIAL_BONUS)
                    else trf("You did %d of %d tasks. Under half, so nothing to cash out. Tomorrow's a new day.", done, total)
                )
            },
            confirmButton = {
                if (eligible) TextButton(onClick = { store.cashOutPartial(cards); rev++; showEnd = false }) { Text(trf("Take +%d min", Config.PARTIAL_BONUS)) }
                else TextButton(onClick = { showEnd = false }) { Text(tr("OK")) }
            },
            dismissButton = { if (eligible) TextButton(onClick = { showEnd = false }) { Text(tr("Not now")) } }
        )
    }
}

private fun Modifier.neon(color: Color, pulse: State<Float>): Modifier = this.drawBehind {
    val p = pulse.value
    val corner = 24.dp.toPx()
    val reach = 14.dp.toPx()
    val steps = 6
    for (i in steps downTo 1) {
        val d = reach * i / steps
        drawRoundRect(
            color = color.copy(alpha = (0.33f * p * (1f - (i - 1f) / steps)).coerceIn(0f, 1f)),
            topLeft = Offset(-d, -d),
            size = Size(size.width + 2 * d, size.height + 2 * d),
            cornerRadius = CornerRadius(corner + d),
            style = Stroke(width = reach / steps + 1f)
        )
    }
    drawRoundRect(color = color.copy(alpha = p), size = size, cornerRadius = CornerRadius(corner), style = Stroke(2.dp.toPx()))
}

@Composable
private fun glowModifier(color: Color, on: Boolean): Modifier {
    if (!on) return Modifier
    val pulse = rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse"
    )
    return Modifier.neon(color, pulse)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(c: FocusCard, spentMin: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val col = Color(c.color)
    val t = c.dayItems.size
    val d = c.dayItems.count { it.done }
    val left = t - d
    val glow = c.type == CardType.STUDY && left > 0
    val timeText = if (spentMin > 0) fmtMin(spentMin) else ""
    Column(
        Modifier.fillMaxWidth().height(136.dp)
            .then(glowModifier(col, glow))
            .clip(RoundedCornerShape(24.dp))
            .background(col.copy(alpha = 0.18f))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            c.title, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
            textAlign = TextAlign.Start, maxLines = 1
        )
        if (c.type == CardType.STUDY) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    t == 0 -> Text(Config.freeWord(c.id), fontWeight = FontWeight.Bold, color = col, style = MaterialTheme.typography.titleMedium)
                    left == 0 -> Text(tr("Plate cleared ✓"), fontWeight = FontWeight.Bold, color = col, style = MaterialTheme.typography.titleMedium)
                    else -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("$left", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = col)
                        Text(
                            "  " + trf("left of %d", t), style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                // bottom row: progress bar + time in the bottom-right corner
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t > 0) LinearProgressIndicator(
                        progress = { d / t.toFloat() },
                        modifier = Modifier.weight(1f), color = col, trackColor = col.copy(alpha = 0.25f)
                    ) else Spacer(Modifier.weight(1f))
                    if (timeText.isNotEmpty()) Text(
                        timeText, Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = col, maxLines = 1
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(typeLabel(c.type) + if (c.media.isNotEmpty()) "  ▶" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = col)
                Text("${c.minutes}m", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = col)
            }
        }
    }
}

@Composable
private fun AddCardDialog(onDismiss: () -> Unit, initial: FocusCard? = null, onAdd: (FocusCard) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: CardType.STUDY) }
    var minutes by remember { mutableStateOf((initial?.minutes ?: 60).toString()) }
    var reward by remember { mutableStateOf((initial?.reward ?: 10).toString()) }
    var lines by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(initial?.let { Color(it.color) } ?: CardSwatches[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (initial == null) "New card" else "Edit card")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text(tr("Title")) }, singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
                if (initial == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t; minutes = when (t) { CardType.STUDY -> "60"; CardType.TREAT -> "40"; CardType.FREE -> "25" } },
                            label = { Text(typeLabel(t)) }
                        )
                    }
                }
                OutlinedTextField(
                    minutes, { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(tr(if (type == CardType.STUDY) "Time budget (min, be generous)" else "Minutes")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (type == CardType.STUDY && initial != null) {
                    OutlinedTextField(
                        reward, { reward = it.filter(Char::isDigit).take(3) },
                        label = { Text(tr("Treat reward (min) for finishing the subject")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (type == CardType.STUDY && initial == null) {
                    OutlinedTextField(
                        lines, { lines = it }, label = { Text(tr("Tasks (one per line, optional)")) }, minLines = 3, maxLines = 6,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardSwatches.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    if (initial != null) {
                        onAdd(initial.copy(
                            title = title.trim(), color = color.toArgb(),
                            minutes = (minutes.toIntOrNull() ?: initial.minutes).coerceIn(1, 600),
                            reward = (reward.toIntOrNull() ?: initial.reward).coerceIn(0, 600)
                        ))
                    } else {
                        val items = if (type == CardType.STUDY)
                            lines.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { Item(it, false) } else emptyList()
                        onAdd(FocusCard(System.currentTimeMillis(), title.trim(), type, color.toArgb(), (minutes.toIntOrNull() ?: 60).coerceIn(1, 600), items))
                    }
                }
            ) { Text(tr(if (initial == null) "Add" else "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

private fun fmtMin(m: Long) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
