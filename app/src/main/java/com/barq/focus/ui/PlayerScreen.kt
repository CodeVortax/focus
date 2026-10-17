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

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.barq.focus.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.barq.focus.data.FocusCard
import com.barq.focus.data.Store
import com.barq.focus.data.minText
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class Hud(val icon: String, val text: String, val frac: Float?)

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/**
 * VLC-style player. Left half: swipe up/down = brightness. Right half: swipe up/down = volume.
 * Swipe sideways = seek. Double-tap the middle = pause, the sides = skip 10 s. The screen turns to match the video.
 * Every second of playback is paid from the treat bank.
 */
@Composable
fun PlayerScreen(store: Store, card0: FocusCard, onExit: () -> Unit, ep: Int = -1) {
    // an episode plays from its own token; a plain treat file is paid for second by second from the bank
    val card = remember(card0, ep) { if (ep >= 0 && ep < card0.eps.size) card0.copy(media = card0.eps[ep].uri, title = card0.eps[ep].name) else card0 }
    val ctx = LocalContext.current
    val view = LocalView.current
    val activity = remember { ctx.findActivity() }
    val window = activity?.window
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)
            .build()
    }
    val posKey = if (ep >= 0) "media_pos_${card.id}_$ep" else "media_pos_" + card.media.hashCode()

    var bankSec by remember { mutableIntStateOf(store.bankSec()) }
    var tokenSec by remember { mutableIntStateOf(if (ep >= 0) store.token(card.id, ep) else 0) }
    var finished by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(if (ep >= 0) tokenSec <= 0 else store.bankSec() <= 0) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var vw by remember { mutableIntStateOf(0) }
    var vh by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var textOff by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) }      // 0 fit, 1 fill, 2 zoom
    var orient by remember { mutableIntStateOf(0) }      // 0 follow the video, 1 auto-rotate, 2 portrait, 3 landscape
    var speed by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var volF by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var briF by remember {
        mutableFloatStateOf(
            (window?.attributes?.screenBrightness ?: -1f).let { w ->
                if (w >= 0f) w else try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
            }
        )
    }

    fun showHud(icon: String, text: String, frac: Float?) { hud = Hud(icon, text, frac); hudTick++ }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    val listener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val swap = videoSize.unappliedRotationDegrees % 180 != 0
                vw = if (swap) videoSize.height else videoSize.width
                vh = if (swap) videoSize.width else videoSize.height
            }
            override fun onTracksChanged(tracks2: Tracks) { tracks = tracks2 }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) dur = exo.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED && ep >= 0) {
                    store.markEpisodeDone(card.id, ep)
                    tokenSec = 0; finished = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val wic = window?.let { WindowCompat.getInsetsController(it, view) }
        wic?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        wic?.hide(WindowInsetsCompat.Type.systemBars())
        exo.addListener(listener)
        onDispose {
            view.keepScreenOn = false
            wic?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w -> val lp = w.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp }
            try {
                if (ep >= 0) {
                    store.setToken(card.id, ep, tokenSec)
                    val d = exo.duration
                    if (d > 0 && exo.currentPosition >= d - 60_000L) store.markEpisodeDone(card.id, ep)
                    else store.putLong(posKey, exo.currentPosition)
                } else store.putLong(posKey, exo.currentPosition)
            } catch (_: Exception) {}
            exo.removeListener(listener)
            exo.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }

    // the screen follows the video: wider than tall (or square) = landscape, taller than wide = portrait
    LaunchedEffect(vw, vh, orient) {
        activity?.requestedOrientation = when (orient) {
            0 -> when {
                vw == 0 || vh == 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                vw >= vh -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            1 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(card.media) {
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(card.media)))
        exo.prepare()
        val p = store.getLong(posKey)
        if (p > 0) exo.seekTo(p)
        exo.playWhenReady = !empty
    }
    LaunchedEffect(Unit) {                      // the bank pays for playback, one second at a time
        while (true) {
            delay(1000)
            if (exo.isPlaying) {
                if (ep >= 0) {
                    tokenSec = (tokenSec - 1).coerceAtLeast(0)
                    if (tokenSec % 5 == 0) store.setToken(card.id, ep, tokenSec)
                    if (tokenSec <= 0) { exo.pause(); empty = true }
                } else {
                    store.spendSec(1)
                    bankSec = store.bankSec()
                    if (bankSec <= 0) { exo.pause(); empty = true }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (dragPos == null) pos = exo.currentPosition
            if (dur <= 0L) dur = exo.duration.coerceAtLeast(0L)
            delay(300)
        }
    }
    LaunchedEffect(controls, playing, touch, locked) { if (controls && playing && !locked) { delay(3500); controls = false } }
    LaunchedEffect(hudTick) { delay(900); hud = null }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = exo; useController = false } },
            update = {
                it.resizeMode = when (resize) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // gesture layer
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controls = !controls; touch++ },
                        onDoubleTap = { o ->
                            if (!locked) {
                                val third = size.width / 3f
                                when {
                                    o.x < third -> { exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L)); showHud("⏪", "-10 s", null) }
                                    o.x > 2 * third -> { exo.seekTo(exo.currentPosition + 10_000L); showHud("⏩", "+10 s", null) }
                                    else -> { if (exo.isPlaying) exo.pause() else exo.play(); showHud(if (exo.isPlaying) "⏸" else "▶", "", null) }
                                }
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    var mode = 0            // 1 volume, 2 brightness, 3 seek
                    var acc = 0f
                    var startPos = 0L
                    var target = 0L
                    detectDragGestures(
                        onDragStart = { mode = 0; acc = 0f; startPos = exo.currentPosition; target = startPos },
                        onDrag = { change, drag ->
                            if (locked) return@detectDragGestures
                            change.consume()
                            if (mode == 0) mode = if (abs(drag.x) > abs(drag.y)) 3 else if (change.position.x < size.width / 2f) 2 else 1
                            when (mode) {
                                1 -> {
                                    volF = (volF - drag.y / (size.height * 0.75f)).coerceIn(0f, 1f)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volF * maxVol).roundToInt(), 0)
                                    showHud("🔊", "${(volF * 100).roundToInt()}%", volF)
                                }
                                2 -> {
                                    briF = (briF - drag.y / (size.height * 0.75f)).coerceIn(0.02f, 1f)
                                    window?.let { w -> val lp = w.attributes; lp.screenBrightness = briF; w.attributes = lp }
                                    showHud("☀️", "${(briF * 100).roundToInt()}%", briF)
                                }
                                3 -> {
                                    acc += drag.x
                                    target = (startPos + acc / size.width * 120_000f).toLong().coerceIn(0L, max(dur, 1L))
                                    val d = (target - startPos) / 1000
                                    showHud(if (d >= 0) "⏩" else "⏪", clock(target) + "  (" + (if (d >= 0) "+" else "") + d + " s)", null)
                                }
                            }
                        },
                        onDragEnd = { if (mode == 3) exo.seekTo(target); mode = 0 },
                        onDragCancel = { mode = 0 }
                    )
                }
        )

        // transient HUD (volume / brightness / seek / double-tap)
        hud?.let { h ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xCC000000)).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(h.icon, fontSize = 30.sp)
                if (h.text.isNotEmpty()) Text(h.text, color = Color.White, fontWeight = FontWeight.SemiBold)
                h.frac?.let { LinearProgressIndicator(progress = { it }, Modifier.width(120.dp), color = Color.White, trackColor = Color(0x44FFFFFF)) }
            }
        }

        // controls
        AnimatedVisibility(controls && !locked, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(card.title, Modifier.weight(1f), color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    orient = (orient + 1) % 4; touch++
                    toast(tr(when (orient) { 1 -> "Auto-rotate"; 2 -> "Portrait only"; 3 -> "Landscape only"; else -> "Fit the screen to the video" }))
                }) { Icon(Icons.Default.ScreenRotation, "Orientation", tint = Color.White) }
                IconButton(onClick = {
                    resize = (resize + 1) % 3; touch++
                    toast(tr(when (resize) { 1 -> "Stretch"; 2 -> "Zoom to fill"; else -> "Fit" }))
                }) { Icon(Icons.Default.AspectRatio, "Aspect ratio", tint = Color.White) }
                TextButton(onClick = { showSpeed = true; touch++ }) { Text("${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×", color = Color.White) }
                IconButton(onClick = { showTracks = true; touch++ }) { Icon(Icons.Default.Subtitles, "Audio and subtitles", tint = Color.White) }
            }
        }
        AnimatedVisibility(controls && !locked, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(dragPos?.toLong() ?: pos), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Slider(
                        dragPos ?: pos.toFloat().coerceAtMost(max(dur, 1L).toFloat()),
                        { dragPos = it; touch++ }, Modifier.weight(1f).padding(horizontal = 8.dp),
                        valueRange = 0f..max(dur, 1L).toFloat(),
                        onValueChangeFinished = { dragPos?.let { exo.seekTo(it.toLong()) }; dragPos = null }
                    )
                    Text(clock(dur), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { locked = true; controls = false }) { Icon(Icons.Default.LockOpen, "Lock", tint = Color.White) }
                    IconButton(onClick = { exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L)); touch++ }) { Icon(Icons.Default.Replay10, "Back 10 s", tint = Color.White) }
                    FilledIconButton(onClick = { if (exo.isPlaying) exo.pause() else exo.play(); touch++ }, Modifier.size(60.dp)) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause", Modifier.size(34.dp))
                    }
                    IconButton(onClick = { exo.seekTo(exo.currentPosition + 10_000L); touch++ }) { Icon(Icons.Default.Forward10, "Forward 10 s", tint = Color.White) }
                    Text(if (ep >= 0) trf("Token: %s left", clock(tokenSec * 1000L)) else trf("Bank: %s min", minText(bankSec)), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (locked) AnimatedVisibility(controls, Modifier.align(Alignment.CenterEnd).padding(24.dp), enter = fadeIn(), exit = fadeOut()) {
            FilledIconButton(onClick = { locked = false; controls = true }) { Icon(Icons.Default.Lock, "Unlock") }
        }

        if (empty || finished) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(if (finished) "Episode finished" else if (ep >= 0) "This episode's token is used up" else "Treat bank is empty"), style = MaterialTheme.typography.titleMedium)
                Text(tr(if (ep >= 0) "Another episode needs another token from the bank." else "Finish tasks to earn more watch time."), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onExit) { Text(tr(if (ep >= 0) "Back to the episodes" else "Back to work")) }
            }
        }
    }

    if (showSpeed) AlertDialog(
        onDismissRequest = { showSpeed = false },
        title = { Text(tr("Playback speed")) },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { speed = s; exo.setPlaybackSpeed(s); showSpeed = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(speed == s, { speed = s; exo.setPlaybackSpeed(s); showSpeed = false })
                        Text("${s}×")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSpeed = false }) { Text(tr("Done")) } }
    )

    if (showTracks) {
        val audioG = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val textG = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        fun label(f: Format, i: Int) = f.label ?: f.language?.let { Locale(it).displayLanguage } ?: trf("Track %d", i + 1)
        fun pick(g: Tracks.Group, i: Int) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).setTrackTypeDisabled(g.type, false).build()
            if (g.type == C.TRACK_TYPE_TEXT) textOff = false
        }
        AlertDialog(
            onDismissRequest = { showTracks = false },
            title = { Text(tr("Audio and subtitles")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(tr("Audio"), fontWeight = FontWeight.SemiBold)
                    if (audioG.isEmpty()) Text(tr("No other tracks"), style = MaterialTheme.typography.bodyMedium)
                    audioG.forEach { g ->
                        for (i in 0 until g.length) Row(Modifier.fillMaxWidth().clickable { pick(g, i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(g.isTrackSelected(i), { pick(g, i) }); Text(label(g.getTrackFormat(i), i))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Subtitles"), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().clickable {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); textOff = true
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(textOff || textG.isEmpty(), {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); textOff = true
                        }); Text(tr("Off"))
                    }
                    textG.forEach { g ->
                        for (i in 0 until g.length) Row(Modifier.fillMaxWidth().clickable { pick(g, i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(g.isTrackSelected(i) && !textOff, { pick(g, i) }); Text(label(g.getTrackFormat(i), i))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTracks = false }) { Text(tr("Done")) } }
        )
    }
}
