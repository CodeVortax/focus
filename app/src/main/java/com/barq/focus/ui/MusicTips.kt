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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import com.barq.focus.data.trf
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.barq.focus.data.NotePlayer
import com.barq.focus.data.tr
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Which phone maker's status-bar media feature the user most likely has, from Build.MANUFACTURER. */
fun oemTip(): Pair<String, String> {
    val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        "realme" in m || "oppo" in m || "oneplus" in m -> tr("Realme / OPPO / OnePlus") to tr(
            "Turn on Settings > realme lab (or Notifications and status bar) > Mini Capsule, and allow Music in it. If it never appears, enable the system app \"Ubiquitous Manager Service\" (Settings > Apps > App management > Show system apps). Set Focus's battery to Unrestricted."
        )
        "samsung" in m -> "Samsung" to tr(
            "One UI shows playing audio on the lock screen and, on newer versions, in the Now Bar. Check Settings > Lock screen and AOD, and take Focus out of Battery > Background usage limits > Sleeping apps."
        )
        "xiaomi" in m || "redmi" in m || "poco" in m -> tr("Xiaomi / Redmi / POCO") to tr(
            "In Settings > Notifications, allow Focus and its media notification (HyperOS can show it in the Dynamic Island). Turn on Autostart for Focus and set its battery saver to No restrictions."
        )
        "google" in m -> "Google Pixel" to tr(
            "Pixel shows it in Quick Settings and on the lock screen. Just allow Focus's notifications."
        )
        "huawei" in m || "honor" in m -> tr("Huawei / Honor") to tr(
            "Allow Focus's notifications, and in Battery > App launch set Focus to manage manually with Run in background on."
        )
        "vivo" in m || "iqoo" in m -> "vivo / iQOO" to tr(
            "Allow Focus's notifications, then in Battery > Background power consumption allow Focus to run in the background."
        )
        else -> tr("Your phone") to tr(
            "Playing audio shows as a standard media notification, which lock screens and status-bar capsules pick up. Allow Focus's notifications and set its battery to Unrestricted."
        )
    }
}

/** A few seconds of soft tone in the cache folder, to check that the media notification appears. */
private fun testTone(ctx: android.content.Context): File {
    val f = File(ctx.cacheDir, "focus_test_tone.wav")
    if (!f.exists()) {
        val rate = 8000
        val n = rate * 8
        val bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + n * 2); bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(rate); bb.putInt(rate * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(n * 2)
        for (i in 0 until n) bb.putShort((sin(2.0 * PI * 440.0 * i / rate) * 2500.0).toInt().toShort())
        f.writeBytes(bb.array())
    }
    return f
}

/** Phones that only draw capsules for a whitelist of music apps (BBK family and Xiaomi). */
private fun whitelistPhone(): Boolean {
    val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return listOf("realme", "oppo", "oneplus", "xiaomi", "redmi", "poco", "vivo", "iqoo").any { it in m }
}

/** How to make the music notification and the phone's mini capsule appear. Shown in Settings and in the first-run guide. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MusicCapsuleHelp() {
    val ctx = LocalContext.current
    val (name, tip) = remember { oemTip() }
    var rev by remember { mutableIntStateOf(0) }
    val notifOk = remember(rev) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rev++ }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val store = remember { com.barq.focus.data.Store(ctx) }
        var picked by remember { mutableStateOf(store.notifBtns.split(",").filter { it.isNotBlank() }) }
        val all = listOf(
            Triple("prev", "Previous", androidx.compose.material.icons.Icons.Default.SkipPrevious),
            Triple("next", "Next", androidx.compose.material.icons.Icons.Default.SkipNext),
            Triple("shuffle", "Shuffle", androidx.compose.material.icons.Icons.Default.Shuffle),
            Triple("repeat", "Repeat", androidx.compose.material.icons.Icons.Default.Repeat),
            Triple("heart", "Favorite", androidx.compose.material.icons.Icons.Default.Favorite),
            Triple("back10", "Back 10 s", androidx.compose.material.icons.Icons.Default.Replay10),
            Triple("fwd10", "Forward 10 s", androidx.compose.material.icons.Icons.Default.Forward10),
            Triple("sleep", "Sleep timer", androidx.compose.material.icons.Icons.Default.Bedtime),
            Triple("speed", "Speed", androidx.compose.material.icons.Icons.Default.Speed),
            Triple("close", "Close player", androidx.compose.material.icons.Icons.Default.Close),
        )
        fun save(v: List<String>) { picked = v; store.notifBtns = v.joinToString(","); com.barq.focus.service.MusicService.refresh() }
        Text(tr("Notification player buttons"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        // live preview of what the notification will show: play/pause is always there
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val order = all.map { it.first }
                val shown = picked.sortedBy { order.indexOf(it) }
                val (before, after) = shown.partition { it == "prev" }
                (before + listOf("play") + after.filter { it == "next" } + after.filter { it != "next" }).forEach { id ->
                    val icon = if (id == "play") androidx.compose.material.icons.Icons.Default.PlayArrow else all.first { it.first == id }.third
                    Icon(icon, null, tint = if (id == "play") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { (id, label, icon) ->
                FilterChip(
                    selected = id in picked,
                    onClick = { save(if (id in picked) picked - id else (picked + id).takeLast(4)) },
                    label = { Text(tr(label)) },
                    leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(trf("%d of 4 buttons", picked.size), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { save(listOf("prev", "next", "shuffle", "repeat")) }) { Text(tr("Reset")) }
        }
        Text(tr("Android shows at most five buttons in total: play/pause is always there, plus up to four of these. Picking a fifth replaces the oldest."), style = MaterialTheme.typography.bodyMedium)
        Text(
            tr("Music and audio from Focus show as a media notification with play, pause and a seek bar. Phones with a status-bar capsule (Realme Mini Capsule, Samsung Now Bar, Xiaomi Dynamic Island, Pixel) show it there too."),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(tip, style = MaterialTheme.typography.bodyMedium)
        if (whitelistPhone()) {
            Text(
                tr("If your phone only shows capsules for whitelisted music apps, install the free Omnibridge app. It mirrors Focus's media session as a supported player, so the capsule appears. Focus itself needs no change."),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTaysonLab/Omnibridge")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }) { Text(tr("Open Omnibridge on GitHub")) }
        }
        OutlinedButton(onClick = { NotePlayer.toggle(ctx, "focus-test", Uri.fromFile(testTone(ctx)), tr("Focus test tone")) }) { Text(tr("Play a test sound")) }
        Text(tr("A short tone plays. Pull down Quick Settings: Focus should show in the media player."), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!notifOk) Button(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Allow notifications")) }
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                } catch (_: Exception) {}
            }) { Text(tr("Notification settings")) }
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                catch (_: Exception) {
                    try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) } catch (_: Exception) {}
                }
            }) { Text(tr("Battery settings")) }
        }
    }
}
