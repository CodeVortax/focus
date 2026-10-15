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
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.barq.focus.data.Voice
import com.barq.focus.data.VoicePlayer
import com.barq.focus.data.VoiceRecorder
import com.barq.focus.data.Voices
import com.barq.focus.data.tr
import kotlinx.coroutines.delay

/** Records right away (asks for the microphone first), then saves and closes when you press stop. */
@Composable
fun VoiceRecordDialog(title: String, onDismiss: () -> Unit, onSaved: (file: String, ms: Long) -> Unit) {
    val ctx = LocalContext.current
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var t0 by remember { mutableLongStateOf(0L) }

    fun startRec() {
        VoicePlayer.stop()
        recording = recorder.start()
        t0 = System.currentTimeMillis()
        if (!recording) {
            Toast.makeText(ctx, tr("Couldn't start recording"), Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else {
            Toast.makeText(ctx, tr("Microphone permission is needed for voice notes"), Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(recording) {
        while (recording) { elapsed = System.currentTimeMillis() - t0; delay(200) }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    AlertDialog(
        onDismissRequest = { recorder.cancel(); recording = false; onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Voices.clock(elapsed), fontSize = 40.sp, fontWeight = FontWeight.Medium)
                Text(
                    tr(if (recording) "Recording… tap stop when you're done" else "Getting the microphone ready…"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                        .clickable(enabled = recording) {
                            recording = false
                            val r = recorder.stop()
                            if (r != null) onSaved(r.first, r.second)
                            else Toast.makeText(ctx, tr("Too short, nothing saved"), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Stop, tr("Stop"), tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { recorder.cancel(); recording = false; onDismiss() }) { Text(tr("Cancel")) } }
    )
}

/** Play / stop button for one clip. */
@Composable
fun VoicePlayButton(file: String, tint: Color) {
    val ctx = LocalContext.current
    val on = VoicePlayer.playing == file
    DisposableEffect(file) { onDispose { if (VoicePlayer.playing == file) VoicePlayer.stop() } }
    IconButton(onClick = { VoicePlayer.toggle(ctx, file) }) {
        Icon(if (on) Icons.Default.Stop else Icons.Default.PlayArrow, tr(if (on) "Stop" else "Play voice note"), tint = tint)
    }
}

/** The voice notes of a note: a record button and the list of clips. */
@Composable
fun VoiceSection(voices: List<Voice>, onChange: (List<Voice>) -> Unit, tint: Color) {
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        voices.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoicePlayButton(v.file, tint)
                Text("🎤  " + Voices.clock(v.ms), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = {
                    if (VoicePlayer.playing == v.file) VoicePlayer.stop()
                    Voices.delete(ctx, v.file)
                    onChange(voices.filter { it.file != v.file })
                }) { Icon(Icons.Default.Delete, tr("Delete voice note")) }
            }
        }
        TextButton(onClick = { rec = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.Default.Mic, null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Text(tr("Record a voice note"))
        }
    }
    if (rec) VoiceRecordDialog(tr("Voice note"), { rec = false }) { f, ms -> onChange(voices + Voice(f, ms)) }
}
