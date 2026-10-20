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
import android.os.Bundle
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSessionService
import com.barq.focus.data.Song
import com.barq.focus.data.Store

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
        val app = ctx.applicationContext
        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { Eq.attach(app, audioSessionId) }
        })
        Eq.attach(app, p.audioSessionId)
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
        Eq.release()
        player?.run { removeListener(listener); release() }
        player = null
        isPlaying = false; currentId = -1L
    }
}

class MusicService : MediaSessionService() {
    private var session: MediaSession? = null
    private val cmds = listOf("shuffle", "repeat", "heart", "back10", "fwd10", "sleep", "speed", "close").associateWith { SessionCommand("focus.$it", Bundle.EMPTY) }

    private fun button(id: String): CommandButton? {
        val p = Music.get(this)
        val c = cmds[id] ?: return null
        val (name, icon) = when (id) {
            "shuffle" -> com.barq.focus.R.string.music_shuffle to
                (if (p.shuffleModeEnabled) com.barq.focus.R.drawable.ic_shuffle_on else com.barq.focus.R.drawable.ic_shuffle_off)
            "repeat" -> com.barq.focus.R.string.music_repeat to when (p.repeatMode) {
                Player.REPEAT_MODE_ONE -> com.barq.focus.R.drawable.ic_repeat_one
                Player.REPEAT_MODE_ALL -> com.barq.focus.R.drawable.ic_repeat_all
                else -> com.barq.focus.R.drawable.ic_repeat_off
            }
            "heart" -> com.barq.focus.R.string.music_love to
                (if (Store(this).favorites().contains(Music.currentId)) com.barq.focus.R.drawable.ic_heart_on else com.barq.focus.R.drawable.ic_heart_off)
            "back10" -> com.barq.focus.R.string.music_back10 to com.barq.focus.R.drawable.ic_back10
            "fwd10" -> com.barq.focus.R.string.music_fwd10 to com.barq.focus.R.drawable.ic_fwd10
            "sleep" -> com.barq.focus.R.string.music_sleep to com.barq.focus.R.drawable.ic_sleep
            "speed" -> com.barq.focus.R.string.music_speed to com.barq.focus.R.drawable.ic_speed
            else -> com.barq.focus.R.string.music_close to com.barq.focus.R.drawable.ic_close
        }
        return CommandButton.Builder().setDisplayName(getString(name)).setIconResId(icon).setSessionCommand(c).build()
    }

    /** The extra buttons you chose in Settings. Previous and next are player commands, so they are shown or hidden below. */
    private fun layout(): ImmutableList<CommandButton> =
        ImmutableList.copyOf(Store(this).notifBtns.split(",").map { it.trim() }.filter { it != "prev" && it != "next" }.mapNotNull { button(it) }.take(4))

    private fun sessionCommands(): SessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().also { b -> cmds.values.forEach { b.add(it) } }.build()

    /** What the notification may show: previous and next only when you picked them. Other apps (Bluetooth, lock screen) keep everything. */
    private fun notificationCommands(): Player.Commands {
        val ids = Store(this).notifBtns.split(",").map { it.trim() }
        val b = Player.Commands.Builder().addAllCommands()
        if ("prev" !in ids) b.remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if ("next" !in ids) b.remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        return b.build()
    }

    private fun applyButtons() {
        val s = session ?: return
        s.setCustomLayout(layout())
        try {
            s.connectedControllers.filter { it.packageName == packageName }.forEach { s.setAvailableCommands(it, sessionCommands(), notificationCommands()) }
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile private var live: MusicService? = null
        /** Rebuild the extra buttons (after the favourite or the setting changed). */
        fun refresh() { live?.applyButtons() }
    }

    private val modeListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { session?.setCustomLayout(layout()) }
        override fun onRepeatModeChanged(repeatMode: Int) { session?.setCustomLayout(layout()) }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { session?.setCustomLayout(layout()) }
    }

    override fun onCreate() {
        super.onCreate()
        // a clear channel name and icon, so the notification (and the phone makers' status-bar capsules built on it) look right
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).setChannelName(com.barq.focus.R.string.music_channel).build()
                .also { it.setSmallIcon(com.barq.focus.R.drawable.ic_music_note) }
        )
        val player = Music.get(this)
        session = MediaSession.Builder(this, player).setSessionActivity(TimerAlarm.openApp(this, "music"))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(s: MediaSession, c: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                        .setAvailableSessionCommands(sessionCommands())
                        .also { if (c.packageName == packageName) it.setAvailablePlayerCommands(notificationCommands()) }
                        .setCustomLayout(layout())
                        .build()

                override fun onCustomCommand(s: MediaSession, c: MediaSession.ControllerInfo, cmd: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    val pl = s.player
                    when (cmd.customAction) {
                        "focus.shuffle" -> pl.shuffleModeEnabled = !pl.shuffleModeEnabled
                        "focus.repeat" -> pl.repeatMode = when (pl.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        "focus.heart" -> if (Music.currentId >= 0L) Store(this@MusicService).toggleFavorite(Music.currentId)
                        "focus.back10" -> pl.seekTo((pl.currentPosition - 10_000L).coerceAtLeast(0L))
                        "focus.fwd10" -> pl.seekTo(pl.currentPosition + 10_000L)
                        "focus.sleep" -> Music.setSleep(when {
                            Music.sleepAt <= 0L -> 15
                            else -> when (((Music.sleepAt - System.currentTimeMillis()) / 60_000L).toInt()) { in 0..15 -> 30; in 16..30 -> 60; else -> 0 }
                        })
                        "focus.speed" -> pl.setPlaybackSpeed(when {
                            pl.playbackParameters.speed < 0.9f -> 1f
                            pl.playbackParameters.speed < 1.1f -> 1.25f
                            pl.playbackParameters.speed < 1.4f -> 1.5f
                            pl.playbackParameters.speed < 1.9f -> 2f
                            else -> 0.75f
                        })
                        "focus.close" -> Music.stopAll(this@MusicService)
                    }
                    try { s.setCustomLayout(layout()) } catch (_: Exception) {}
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
        // the screen drives the player directly (no MediaController), so register the session or Media3 never posts the notification
        addSession(session!!)
        session?.setCustomLayout(layout())
        player.addListener(modeListener)
        live = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        live = null
        Music.player?.removeListener(modeListener)
        session?.release()
        session = null
        Music.release()
        super.onDestroy()
    }
}
