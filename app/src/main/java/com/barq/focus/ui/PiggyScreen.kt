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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barq.focus.data.Store
import com.barq.focus.data.Vault
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The piggy bank: saved treat time, the payout date, and the ledger of deposits, payouts and funny fines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiggyScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(store.vaultOn) }
    val lots = remember { Vault.lots(store).sortedBy { it.release } }
    val ledger = remember { Vault.ledger(store).reversed() }
    val balance = lots.sumOf { it.sec }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Piggy bank")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("\uD83D\uDC37  " + trf("%s min in the piggy", minText(balance)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        lots.firstOrNull()?.let {
                            Text(trf("Next payout: %s min in %d days", minText(it.sec + it.sec * Vault.INTEREST_PCT / 100), Vault.daysLeft(it)), style = MaterialTheme.typography.bodyMedium)
                        } ?: Text(tr("Empty for now. Leave some treat time unspent and it lands here tonight."), style = MaterialTheme.typography.bodyMedium)
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
                        Vault.INTEREST_PCT
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
                    "fine" -> "\uD83D\uDEA8" to MaterialTheme.colorScheme.error
                    "warn" -> "\u26A0\uFE0F" to MaterialTheme.colorScheme.onSurfaceVariant
                    "rel" -> "\uD83D\uDCB0" to MaterialTheme.colorScheme.primary
                    else -> "\uD83D\uDC37" to MaterialTheme.colorScheme.onSurface
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
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
