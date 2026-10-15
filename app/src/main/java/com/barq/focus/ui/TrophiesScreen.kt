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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.Game
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.ui.theme.FocusBrandFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophiesScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data = remember {
        object {
            val xp = Game.xp(store)
            val work = Game.workStreak(store)
            val light = Game.lightStreak(store)
            val have = store.badges()
            val quests = Game.quests(store, ctx)
        }
    }
    val normal = Game.BADGES.filter { !it.shame }
    val shame = Game.BADGES.filter { it.shame }
    val level = Game.level(data.xp)
    val (emoji, title) = Game.titleOf(level)
    val from = Game.xpForLevel(level)
    val to = Game.xpForLevel(level + 1)
    val frac = ((data.xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Trophies"), fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(emoji, fontSize = 44.sp)
                            Column {
                                Text(tr(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(trf("Level %d  ·  %d XP", level, data.xp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Text(trf("%d XP to the next level", to - data.xp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreakCard("🔥", data.work, tr("work-day streak"), Modifier.weight(1f))
                    StreakCard("🛡️", data.light, tr("light-Shorts streak (50 or fewer)"), Modifier.weight(1f))
                }
            }
            item(span = { GridItemSpan(2) }) { Text(tr("Today's quests"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(data.quests, span = { GridItemSpan(2) }) { q ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (q.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (q.done) "✅" else "⬜", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr(q.text), fontWeight = if (q.done) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            item(span = { GridItemSpan(2) }) {
                Text(trf("Badges  %d / %d", normal.count { it.id in data.have }, normal.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(normal) { b ->
                val got = b.id in data.have
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(if (got) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BadgeArt(b.id, b.emoji, 88.dp, !got)
                    Text(tr(b.name), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        tr(b.desc), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                        color = if (got) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shame.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        trf("Hall of Shame  %d / %d", shame.count { it.id in data.have }, shame.size),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                }
                items(shame) { b ->
                    val got = b.id in data.have
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (got) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (got) {
                            BadgeArt(b.id, b.emoji, 88.dp, false)
                            Text(tr(b.name), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(tr(b.desc), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        } else {
                            Text("❓", fontSize = 40.sp)
                            Text(tr("Hidden until you earn it"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCard(emoji: String, days: Int, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 30.sp)
        Text("$days", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Badge picture from res/drawable-nodpi/badge_<id>.webp, or the emoji on a dark tile when there's no art yet. */
@Composable
fun BadgeArt(id: String, emoji: String, size: Dp, locked: Boolean) {
    val ctx = LocalContext.current
    val res = remember(id) { ctx.resources.getIdentifier("badge_$id", "drawable", ctx.packageName) }
    val shape = RoundedCornerShape(size / 5)
    if (res != 0) {
        Image(
            painterResource(res), null, Modifier.size(size).clip(shape),
            colorFilter = if (locked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            alpha = if (locked) 0.35f else 1f
        )
    } else {
        Box(Modifier.size(size).clip(shape).background(Color(0xFF1E1F24)), contentAlignment = Alignment.Center) {
            Text(if (locked) "🔒" else emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}
