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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Game
import com.barq.focus.data.Store
import com.barq.focus.data.Vault
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The piggy bank: saved treat time, the payout date, streak shields, and the ledger. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiggyScreen(store: Store, onBack: () -> Unit) {
    var on by remember { mutableStateOf(store.vaultOn) }
    val lots = remember { Vault.lots(store).sortedBy { it.release } }
    val ledger = remember { Vault.ledger(store).reversed() }
    val balance = lots.sumOf { it.sec }
    val xp = remember { Game.xp(store) }
    val level = Game.level(xp)
    val rate = Game.interestPct(level)
    val nextTier = Game.nextInterestTier(level)
    val shields = remember { store.streakShields }
    val tonightSec = remember { store.todayUnusedSec() }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                title = { Text(tr("Piggy bank")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Text(trf("%s min in the piggy", minText(balance)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        lots.firstOrNull()?.let {
                            Text(trf("Next payout: %s min in %d days", minText(it.sec + it.sec * rate / 100), Vault.daysLeft(it)), style = MaterialTheme.typography.bodyMedium)
                        } ?: Text(tr("Empty for now. Leave some treat time unspent and it lands here tonight."), style = MaterialTheme.typography.bodyMedium)
                        if (tonightSec >= 60) {
                            val projected = tonightSec + tonightSec * rate / 100
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                Text(
                                    trf("Going in tonight: %s min (pays %s min in 7 days)", minText(tonightSec), minText(projected)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            if (nextTier != null) trf("Level %d perk: %d%% interest (next: %d%% at Lv %d)", level, rate, nextTier.second, nextTier.first)
                            else trf("Top perk active: %d%% piggy interest", rate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(trf("Piggy Shields: %d / 2", shields), fontWeight = FontWeight.Bold)
                            Text(
                                tr("Earned by finishing all 4 daily quests or every 3-day work streak. Blocks 1 fine automatically."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Piggy bank on"), Modifier.weight(1f))
                    Switch(on, { on = it; store.vaultOn = it })
                }
            }
            item {
                Text(
                    trf(
                        "Every night the treat time you did not spend goes into the piggy. A week later it is paid into your treat bank with %d%% interest. If you planned tasks and did none, you get a fine, taken from the piggy.",
                        rate
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (lots.isNotEmpty()) {
                item { Text(tr("Waiting in the piggy"), style = MaterialTheme.typography.titleSmall) }
                items(lots) { l ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(trf("%s min", minText(l.sec)), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (Vault.daysLeft(l) == 0) tr("Pays out today") else trf("Pays out in %d days", Vault.daysLeft(l)),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
            item { Text(tr("History"), style = MaterialTheme.typography.titleSmall) }
            if (ledger.isEmpty()) item { Text(tr("Nothing yet. The first deposit or fine shows up here.")) }
            items(ledger) { e ->
                val (icon, tint) = when (e.kind) {
                    "fine" -> Icons.Rounded.Report to MaterialTheme.colorScheme.error
                    "shield" -> Icons.Rounded.Shield to MaterialTheme.colorScheme.primary
                    "warn" -> Icons.Rounded.Warning to MaterialTheme.colorScheme.onSurfaceVariant
                    "rel" -> Icons.Rounded.Payments to MaterialTheme.colorScheme.primary
                    else -> Icons.Rounded.Savings to MaterialTheme.colorScheme.onSurface
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 12.dp).size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr(e.title), fontWeight = FontWeight.SemiBold)
                            if (e.text.isNotEmpty()) Text(tr(e.text), style = MaterialTheme.typography.bodyMedium)
                            Text(dateFmt.format(Date(e.t)), style = MaterialTheme.typography.labelSmall)
                        }
                        if (e.sec != 0) Text((if (e.sec > 0) "+" else "-") + minText(kotlin.math.abs(e.sec)), color = tint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
