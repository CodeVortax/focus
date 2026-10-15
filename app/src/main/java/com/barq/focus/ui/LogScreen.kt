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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Config
import com.barq.focus.data.Db
import com.barq.focus.data.Row as UsageRow
import com.barq.focus.data.Store
import com.barq.focus.data.startOfDay
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Entry(
    val kind: String, val pkg: String, val t0: Long, val t1: Long?, val n: Int,
    val reasons: List<String>, val kids: List<Entry> = emptyList(), val bursts: Int = 1
)

private val ShortsColor = Color(0xFFFF7A1A)
private val PlayColor = Color(0xFF8E5CF6)

private fun appColor(pkg: String): Color = Color(
    when (pkg) {
        "com.google.android.youtube" -> 0xFFEA4335
        "com.android.chrome" -> 0xFF4285F4
        "com.google.android.apps.youtube.music" -> 0xFFAB47BC
        "org.videolan.vlc" -> 0xFFFF9800
        "org.telegram.messenger" -> 0xFF29B6F6
        else -> 0xFF78909C
    }.toInt()
)

private fun label(pkg: String) = Config.TRACKED[pkg] ?: pkg

private fun dur(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

/** Glue bursts of the same kind+app (gap <= [gap]) into single entries: one visit = one entry. */
private fun merge(rows: List<UsageRow>, gapFor: (String) -> Long): List<Entry> {
    val out = ArrayList<Entry>()
    for ((_, list) in rows.groupBy { it.kind to it.pkg }) {
        var cur: Entry? = null
        for (r in list.sortedBy { it.t0 }) {
            val reasons = r.reason?.split(" > ")?.filter { it.isNotBlank() } ?: emptyList()
            val c = cur
            val cEnd = c?.t1
            if (c != null && cEnd != null && r.t0 - cEnd <= gapFor(r.kind)) {
                cur = c.copy(
                    t1 = r.t1?.let { maxOf(it, cEnd) }, n = c.n + r.n,
                    reasons = (c.reasons + reasons).distinct(), bursts = c.bursts + 1
                )
            } else {
                if (c != null) out.add(c)
                cur = Entry(r.kind, r.pkg, r.t0, r.t1, r.n, reasons.distinct())
            }
        }
        cur?.let { out.add(it) }
    }
    return out
}

private fun build(rows: List<UsageRow>, now: Long): List<Entry> {
    val merged = merge(rows) { if (it == "music") 30_000L else 90_000L }
    val apps = merged.filter { it.kind == "app" && ((it.t1 ?: now) - it.t0) >= 3000L }
    val others = merged.filter { it.kind != "app" && (it.n > 0 || ((it.t1 ?: now) - it.t0) >= 3000L) }
    val tops = apps.map { a ->
        val end = a.t1 ?: now
        a.copy(kids = others.filter { it.pkg == a.pkg && it.t0 >= a.t0 - 5000 && it.t0 <= end + 5000 }.sortedBy { it.t0 })
    }
    val used = tops.flatMap { it.kids }.toSet()
    return (tops + others.filter { it !in used }).sortedByDescending { it.t0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(db: Db, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val entries = remember { build(db.recent(1500), now) }
    val since = remember { startOfDay() }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val df = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val swipes = remember { Store(ctx).shortsSwipes() }

    val today = remember { entries.filter { it.t0 >= since } }
    val appTotals = remember {
        today.filter { it.kind == "app" }.groupBy { it.pkg }
            .mapValues { (_, l) -> l.sumOf { (it.t1 ?: now) - it.t0 } }.entries.sortedByDescending { it.value }
    }
    val shortsToday = remember {
        val l = (today.filter { it.kind == "shorts" } + today.flatMap { it.kids }.filter { it.kind == "shorts" }).distinct()
        Triple(l.sumOf { (it.t1 ?: now) - it.t0 }, l.sumOf { it.bursts }, l.size)
    }
    val playToday = remember {
        val l = (today.filter { it.kind == "music" } + today.flatMap { it.kids }.filter { it.kind == "music" }).distinct()
        l.sumOf { (it.t1 ?: now) - it.t0 }
    }
    val groups = remember {
        val yesterday = since - 86_400_000L
        entries.groupBy {
            when {
                it.t0 >= since -> "Today"
                it.t0 >= yesterday -> "Yesterday"
                else -> df.format(Date(it.t0))
            }
        }
    }

    fun line(e: Entry): String {
        val end = e.t1?.let { tf.format(Date(it)) } ?: "now"
        val tag = when (e.kind) { "shorts" -> " (Shorts, ${e.n} swiped)"; "music" -> " (playing)"; else -> "" }
        return label(e.pkg) + tag + "  ${tf.format(Date(e.t0))}-$end  " + dur((e.t1 ?: now) - e.t0) +
            (if (e.reasons.isNotEmpty()) "  [" + e.reasons.joinToString(" > ") + "]" else "")
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Log")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = {
                    val text = buildString {
                        appendLine("Focus log  ${df.format(Date())}")
                        appendLine("Shorts today: $swipes swiped, ${dur(shortsToday.first)}")
                        groups.forEach { (day, list) ->
                            appendLine("== $day ==")
                            list.forEach { e ->
                                appendLine(line(e))
                                e.kids.forEach { appendLine("    " + line(it)) }
                            }
                        }
                    }
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("focus log", text))
                    Toast.makeText(ctx, tr("Log copied"), Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, "Copy log") }
            }
        )
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tr("Today · screen time"), fontWeight = FontWeight.SemiBold)
                    if (appTotals.isEmpty()) Text(tr("Nothing yet."), style = MaterialTheme.typography.bodyMedium)
                    val max = (appTotals.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
                    appTotals.forEach { (pkg, ms) ->
                        val c = appColor(pkg)
                        Column {
                            Row {
                                Text(label(pkg), Modifier.weight(1f), color = c, fontWeight = FontWeight.SemiBold)
                                Text(dur(ms), style = MaterialTheme.typography.labelLarge)
                            }
                            LinearProgressIndicator(
                                progress = { ms.toFloat() / max }, modifier = Modifier.fillMaxWidth(),
                                color = c, trackColor = c.copy(alpha = 0.15f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(ShortsColor))
                        Text(
                            "  " + tr("Shorts / Reels") + ": " + trf("%d swiped  ·  %s  ·  %d visits", swipes, dur(shortsToday.first), shortsToday.third),
                            color = ShortsColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(PlayColor))
                        Text("  " + tr("Playing") + ": " + dur(playToday), color = PlayColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            groups.forEach { (day, list) ->
                item(key = "h$day") {
                    Text(
                        tr(day), Modifier.padding(top = 10.dp, start = 4.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { "${it.kind}${it.pkg}${it.t0}" }) { e -> EntryCard(e, now, tf) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EntryCard(e: Entry, now: Long, tf: SimpleDateFormat) {
    val color = when (e.kind) { "shorts" -> ShortsColor; "music" -> PlayColor; else -> appColor(e.pkg) }
    val end = e.t1?.let { tf.format(Date(it)) } ?: tr("now")
    val title = when (e.kind) {
        "shorts" -> tr("Shorts") + "  ·  " + label(e.pkg)
        "music" -> tr("Playing") + "  ·  " + label(e.pkg)
        else -> label(e.pkg)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = color)
                Text(dur((e.t1 ?: now) - e.t0), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${tf.format(Date(e.t0))} – $end" + if (e.kind == "shorts") "  ·  " + trf("%d swiped", e.n) else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (e.reasons.isNotEmpty()) Text(
                e.reasons.joinToString("  ›  "), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            e.kids.forEach { k ->
                val kc = if (k.kind == "shorts") ShortsColor else PlayColor
                val kEnd = k.t1?.let { tf.format(Date(it)) } ?: tr("now")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(kc.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(kc))
                    Text(
                        "  " + (if (k.kind == "shorts") tr("Shorts") else tr("Playing")) + "  ${tf.format(Date(k.t0))}–$kEnd" +
                            (if (k.kind == "shorts") "  ·  " + trf("%d swiped", k.n) else ""),
                        Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = kc, fontWeight = FontWeight.SemiBold
                    )
                    Text(dur((k.t1 ?: now) - k.t0), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
