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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.barq.focus.R
import com.barq.focus.data.NotePlayer
import com.barq.focus.data.tr

/**
 * Gives audio played from a note a real Android media session and a MediaStyle notification.
 * That is what lock screens and the phone makers' status-bar capsules (Realme Mini Capsule, Samsung Now Bar,
 * Xiaomi Dynamic Island, ColorOS / OxygenOS live alerts, Pixel media controls) read, so the clip shows up there.
 */
object NoteAudio {
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        try {
            if (NotePlayer.current.isEmpty()) app.stopService(Intent(app, NoteAudioService::class.java))
            else ContextCompat.startForegroundService(app, Intent(app, NoteAudioService::class.java))
        } catch (_: Exception) {}
    }
}

class NoteAudioService : Service() {
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.note_audio_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        session = MediaSession(this, "FocusNoteAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { NotePlayer.resume() }
                override fun onPause() { NotePlayer.pause() }
                override fun onStop() { NotePlayer.stop() }
                override fun onSeekTo(pos: Long) { NotePlayer.seekToMs(pos) }
            })
            setSessionActivity(TimerAlarm.openApp(this@NoteAudioService))
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> if (NotePlayer.isPlaying) NotePlayer.pause() else NotePlayer.resume()
            ACT_STOP -> NotePlayer.stop()
        }
        show()
        return START_NOT_STICKY
    }

    private fun show() {
        val s = session ?: return
        if (NotePlayer.current.isEmpty()) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
            return
        }
        val playing = NotePlayer.isPlaying
        val title = NotePlayer.title.ifBlank { tr("Voice note") }
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Focus")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, NotePlayer.dur)
                .build()
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, NotePlayer.pos, if (playing) 1f else 0f)
                .build()
        )

        fun action(icon: Int, label: String, act: String, code: Int) = Notification.Action.Builder(
            Icon.createWithResource(this, icon), label,
            PendingIntent.getService(this, code, Intent(this, NoteAudioService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ).build()

        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(tr("Playing from a note"))
            .setContentIntent(TimerAlarm.openApp(this))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(action(if (playing) R.drawable.ic_pause else R.drawable.ic_play, tr(if (playing) "Pause" else "Play"), ACT_TOGGLE, 11))
            .addAction(action(R.drawable.ic_stop, tr("Stop"), ACT_STOP, 12))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "focus_note_audio"
        const val ID = 4107
        const val ACT_TOGGLE = "com.barq.focus.NOTE_TOGGLE"
        const val ACT_STOP = "com.barq.focus.NOTE_STOP"
    }
}
