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
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Config
import com.barq.focus.data.Db
import com.barq.focus.data.Row as UsageRow
import com.barq.focus.data.UsageSpan
import com.barq.focus.data.Store
import com.barq.focus.data.SysUsage
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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


private enum class Metric { TIME, SHORTS, OPENS }

private val Palette = listOf(
    Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853), Color(0xFFAB47BC), Color(0xFF26C6DA),
)
private val OtherColor = Color(0xFF78909C)

private fun overlap(a0: Long, a1: Long, b0: Long, b1: Long) = (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0L)

/** Digital-Wellbeing style: "2 hr, 49 min". */
private fun longDur(ms: Long): String {
    val m = ms / 60_000
    return when {
        m >= 60 -> trf("%d hr, %d min", m / 60, m % 60)
        m >= 1 -> trf("%d min", m)
        else -> trf("%d sec", ms / 1000)
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
private fun WeekChart(values: List<Long>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, color: Color) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (i == selected) color else color.copy(alpha = 0.28f))
                    )
                }
                Text(
                    labels[i], Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (i == selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun Donut(slices: List<Pair<Float, Color>>, top: String, bottom: String) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 22.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { (f, c) ->
                val sweep = f * 360f
                drawArc(c, start, (sweep - gap).coerceAtLeast(0.5f), false, tl, sz, style = Stroke(sw))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(bottom, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(top, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HourStrip(values: List<Long>, color: Color) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column {
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { v ->
                Box(Modifier.weight(1f).fillMaxHeight(if (v <= 0L) 0.03f else (v.toFloat() / max).coerceAtLeast(0.08f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (v <= 0L) color.copy(alpha = 0.12f) else color))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AppRow(name: String, icon: Bitmap?, value: String, frac: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            else Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = color, trackColor = color.copy(alpha = 0.15f)
                )
            }
            Text(value, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(db: Db, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val now = remember { System.currentTimeMillis() }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val df = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val wf = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val rows = remember { db.recent(6000) }
    val entries = remember { build(rows, now) }
    val primary = MaterialTheme.colorScheme.primary

    var metric by remember { mutableStateOf(Metric.TIME) }
    var sel by remember { mutableIntStateOf(6) }          // position in the week: 6 = today, 0 = six days ago
    val off = 6 - sel
    val hasSys = remember { Perms.usage(ctx) }

    // all-app screen time (system) when allowed, else what Focus itself logged
    var spans by remember { mutableStateOf<List<UsageSpan>?>(null) }
    LaunchedEffect(Unit) {
        spans = withContext(Dispatchers.Default) {
            val sys = if (hasSys) SysUsage.spans(ctx, SysUsage.dayStart(6), now) else emptyList()
            sys.ifEmpty {
                rows.filter { it.kind == "app" && it.t0 >= SysUsage.dayStart(6) }.map { UsageSpan(it.pkg, it.t0, it.t1 ?: now) }
            }
        }
    }

    val dayStart = remember(off) { SysUsage.dayStart(off) }
    val dayEnd = remember(off) { if (off == 0) now else SysUsage.dayStart(off - 1) }
    val labels = remember { (6 downTo 0).map { wf.format(Date(SysUsage.dayStart(it) + 3_600_000L)) } }

    val shortsRows = remember { rows.filter { it.kind == "shorts" } }

    // one number per day for the chart
    val week = remember(spans, metric) {
        val sp = spans ?: emptyList()
        (6 downTo 0).map { o ->
            val a = SysUsage.dayStart(o); val b = if (o == 0) now else SysUsage.dayStart(o - 1)
            when (metric) {
                Metric.TIME -> sp.sumOf { overlap(it.t0, it.t1, a, b) }
                Metric.OPENS -> sp.count { it.t0 in a until b && it.t1 - it.t0 >= 3000L }.toLong()
                Metric.SHORTS -> store.dayStat(o).swipes.toLong()
            }
        }
    }

    // per-app numbers for the selected day
    val perApp = remember(spans, metric, off) {
        val sp = spans ?: emptyList()
        val m = HashMap<String, Long>()
        when (metric) {
            Metric.TIME -> sp.forEach { val d = overlap(it.t0, it.t1, dayStart, dayEnd); if (d > 0) m.merge(it.pkg, d, Long::plus) }
            Metric.OPENS -> sp.forEach { if (it.t0 in dayStart until dayEnd && it.t1 - it.t0 >= 3000L) m.merge(it.pkg, 1L, Long::plus) }
            Metric.SHORTS -> shortsRows.forEach { if (it.t0 in dayStart until dayEnd && it.n > 0) m.merge(it.pkg, it.n.toLong(), Long::plus) }
        }
        m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val total = remember(perApp, week) { if (metric == Metric.SHORTS) week[sel] else perApp.sumOf { it.second } }

    val hours = remember(spans, metric, off) {
        val sp = spans ?: emptyList()
        LongArray(24).also { h ->
            val c = java.util.Calendar.getInstance()
            when (metric) {
                Metric.OPENS -> sp.forEach { if (it.t0 in dayStart until dayEnd && it.t1 - it.t0 >= 3000L) { c.timeInMillis = it.t0; h[c.get(java.util.Calendar.HOUR_OF_DAY)]++ } }
                Metric.SHORTS -> shortsRows.forEach { r ->
                    for (x in 0 until 24) h[x] += overlap(r.t0, r.t1 ?: now, dayStart + x * 3_600_000L, dayStart + (x + 1) * 3_600_000L)
                }
                Metric.TIME -> sp.forEach { s ->
                    for (x in 0 until 24) h[x] += overlap(s.t0, s.t1, dayStart + x * 3_600_000L, dayStart + (x + 1) * 3_600_000L)
                }
            }
        }.toList()
    }

    fun fmt(v: Long) = when (metric) {
        Metric.TIME -> longDur(v)
        Metric.SHORTS -> trf("%d swiped", v)
        Metric.OPENS -> trf("%d opens", v)
    }

    val dayLabel = when (off) { 0 -> tr("Today"); 1 -> tr("Yesterday"); else -> df.format(Date(dayStart + 3_600_000L)) }
    val dayEntries = remember(off) { entries.filter { it.t0 in dayStart until dayEnd } }
    val shortsMs = remember(off) { shortsRows.sumOf { overlap(it.t0, it.t1 ?: now, dayStart, dayEnd) } }
    val playMs = remember(off) {
        rows.filter { it.kind == "music" }.sumOf { overlap(it.t0, it.t1 ?: now, dayStart, dayEnd) }
    }
    val groups = remember {
        val since = SysUsage.dayStart(0); val yesterday = since - 86_400_000L
        entries.groupBy { when { it.t0 >= since -> "Today"; it.t0 >= yesterday -> "Yesterday"; else -> df.format(Date(it.t0)) } }
    }

    fun line(e: Entry): String {
        val end = e.t1?.let { tf.format(Date(it)) } ?: "now"
        val tag = when (e.kind) { "shorts" -> " (Shorts, ${e.n} swiped)"; "music" -> " (playing)"; else -> "" }
        return (Config.TRACKED[e.pkg] ?: e.pkg) + tag + "  ${tf.format(Date(e.t0))}-$end  " + dur((e.t1 ?: now) - e.t0) +
            (if (e.reasons.isNotEmpty()) "  [" + e.reasons.joinToString(" > ") + "]" else "")
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Usage")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = {
                    val text = buildString {
                        appendLine("Focus log  ${df.format(Date())}")
                        appendLine("Shorts today: ${store.shortsSwipes()} swiped")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FilterChip(metric == Metric.TIME, { metric = Metric.TIME }, label = { Text(tr("Screen time")) })
                    FilterChip(metric == Metric.SHORTS, { metric = Metric.SHORTS }, label = { Text(tr("Shorts / Reels")) })
                    FilterChip(metric == Metric.OPENS, { metric = Metric.OPENS }, label = { Text(tr("Times opened")) })
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(dayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Section {
                if (spans == null) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else WeekChart(week, labels, sel, { sel = it }, primary)
                if (!hasSys && metric != Metric.SHORTS) Text(
                    tr("Allow usage access to see every app, not just the tracked ones."),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } }
            item { Section {
                Text(tr("Apps"), fontWeight = FontWeight.SemiBold)
                if (perApp.isEmpty()) Text(tr("Nothing yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val top = perApp.take(5)
                    val rest = perApp.drop(5).sumOf { it.second }
                    val sum = perApp.sumOf { it.second }.coerceAtLeast(1L).toFloat()
                    val slices = top.mapIndexed { i, p -> p.second / sum to Palette[i % Palette.size] } +
                        (if (rest > 0) listOf(rest / sum to OtherColor) else emptyList())
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(slices, fmt(sum.toLong()), dayLabel)
                    }
                    val max = (perApp.first().second).coerceAtLeast(1L)
                    perApp.take(8).forEachIndexed { i, (pkg, v) ->
                        val icon = remember(pkg) { SysUsage.icon(ctx, pkg) }
                        AppRow(SysUsage.label(ctx, pkg), icon, fmt(v), v.toFloat() / max, Palette[i % Palette.size].takeIf { i < 5 } ?: OtherColor)
                    }
                }
            } }
            item { Section {
                Text(tr("By hour"), fontWeight = FontWeight.SemiBold)
                HourStrip(hours, primary)
            } }
            item { Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ShortsColor))
                    Text(
                        "  " + tr("Shorts / Reels") + ": " + trf("%d swiped", store.dayStat(off).swipes) + "  ·  " + dur(shortsMs),
                        color = ShortsColor, fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(PlayColor))
                    Text("  " + tr("Playing") + ": " + dur(playMs), color = PlayColor, fontWeight = FontWeight.SemiBold)
                }
            } }
            item {
                Text(
                    tr("History") + "  ·  " + dayLabel, Modifier.padding(top = 4.dp, start = 4.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (dayEntries.isEmpty()) item { Text(tr("Nothing yet."), Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(dayEntries.size, key = { dayEntries[it].let { e -> "${e.kind}${e.pkg}${e.t0}" } }) { i -> EntryCard(dayEntries[i], now, tf) }
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
