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
package com.barq.focus.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.barq.focus.data.Song

/** The one music player. The UI talks to it directly; [MusicService] hands it to Android for the notification and lock screen. */
object Music {
    var player: ExoPlayer? = null
        private set
    var version by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(false)
    var currentId by mutableLongStateOf(-1L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var pitch by mutableFloatStateOf(1f)
    var abA by mutableLongStateOf(-1L)
    var abB by mutableLongStateOf(-1L)
    var sleepAt by mutableLongStateOf(0L)
    private val h = Handler(Looper.getMainLooper())

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Music.isPlaying = isPlaying; version++ }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
            abA = -1L; abB = -1L
            version++
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { version++ }
        override fun onRepeatModeChanged(repeatMode: Int) { Music.repeatMode = repeatMode }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            speed = playbackParameters.speed; pitch = playbackParameters.pitch
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                if (abA >= 0 && abB > abA && p.currentPosition >= abB) p.seekTo(abA)         // A-B repeat
                if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) { p.pause(); sleepAt = 0L }  // sleep timer
            }
            if ((abA >= 0 && abB > abA) || sleepAt > 0) h.postDelayed(this, 250)
        }
    }
    fun kick() { h.removeCallbacks(tick); h.post(tick) }

    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(listener)
        player = p
        return p
    }

    fun item(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString()).setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).setArtworkUri(s.artUri).build()
        ).build()

    fun playSongs(ctx: Context, list: List<Song>, index: Int) {
        if (list.isEmpty()) return
        com.barq.focus.data.NotePlayer.stop()          // one thing at a time
        val p = get(ctx)
        p.setMediaItems(list.map { item(it) }, index.coerceIn(0, list.lastIndex), 0L)
        p.prepare()
        p.play()
        ensureService(ctx)
    }

    fun ensureService(ctx: Context) {
        try { ctx.applicationContext.startService(Intent(ctx.applicationContext, MusicService::class.java)) } catch (_: Exception) {}
    }

    fun setSleep(minutes: Int) {
        sleepAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        kick()
    }

    fun stopAll(ctx: Context) {
        player?.run { stop(); clearMediaItems() }
        currentId = -1L; abA = -1L; abB = -1L; sleepAt = 0L
        try { ctx.applicationContext.stopService(Intent(ctx.applicationContext, MusicService::class.java)) } catch (_: Exception) {}
    }

    fun release() {
        h.removeCallbacks(tick)
        player?.run { removeListener(listener); release() }
        player = null
        isPlaying = false; currentId = -1L
    }
}

class MusicService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // a clear channel name and icon, so the notification (and the phone makers' status-bar capsules built on it) look right
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).setChannelName(com.barq.focus.R.string.music_channel).build()
                .also { it.setSmallIcon(com.barq.focus.R.drawable.ic_music_note) }
        )
        session = MediaSession.Builder(this, Music.get(this)).setSessionActivity(TimerAlarm.openApp(this)).build()
        // the screen drives the player directly (no MediaController), so register the session or Media3 never posts the notification
        addSession(session!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        Music.release()
        super.onDestroy()
    }
}
