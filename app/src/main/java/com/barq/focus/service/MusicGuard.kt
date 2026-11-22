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

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.barq.focus.data.NotePlayer

/**
 * Protects music playback in Focus's own player and dedicated music apps (Spotify, YouTube Music, VLC, etc.)
 * so Focus overlays, gates, and leaving blocked apps never silence active music.
 */
object MusicGuard {
    val KNOWN_MUSIC_PLAYERS = setOf(
        "com.spotify.music",
        "com.spotify.lite",
        "com.google.android.apps.youtube.music",
        "app.revanced.android.apps.youtube.music",
        "app.rvx.android.apps.youtube.music",
        "com.vanced.android.apps.youtube.music",
        "org.videolan.vlc",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.aspiro.tidal",
        "com.amazon.mp3",
        "com.pandora.android",
        "com.anghami",
        "com.maxmpz.audioplayer",
        "com.aimp.player",
        "in.krosbits.musicolet",
        "com.piyush.music",
        "com.kodarkooperativet.blackplayerfree",
        "com.musicplayer.blackplayerpro",
        "com.rhmsoft.pulsar",
        "code.name.monkey.retromusic",
        "io.github.muntashirakon.Music",
        "ch.blinkenlights.android.vanilla",
        "com.kabouzeid.gramophone",
        "com.sec.android.app.music",
        "com.miui.player",
        "com.heytap.music",
        "com.oppo.music",
        "com.realme.music",
        "com.sonyericsson.music",
        "com.foobar2000.foobar2000",
        "org.lineageos.eleven",
        "app.symfonik.music.player",
        "au.com.shiftyjelly.pocketcasts",
        "de.danoeh.antennapod",
        "com.audible.application",
    )

    private val VIDEO_FEED_APPS = setOf(
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.instagram.lite",
        "com.facebook.katana",
        "com.ss.android.ugc.aweme",
    )

    fun isKnownMusicPlayer(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val p = pkg.lowercase()
        return p in KNOWN_MUSIC_PLAYERS ||
            (p.contains("music") && !p.contains("musically")) ||
            p.endsWith(".vlc")
    }

    /**
     * Only allow the overlay to request transient audio focus when gating a visual video distraction
     * AND no Focus or external music player is currently playing.
     */
    fun shouldGrabOverlayAudio(
        targetPkg: String?,
        focusMusicPlaying: Boolean,
        focusNotePlaying: Boolean,
        activePlayingMediaPackages: Set<String>,
        audioMusicActive: Boolean,
    ): Boolean {
        if (focusMusicPlaying || focusNotePlaying) return false
        if (isKnownMusicPlayer(targetPkg)) return false
        if (activePlayingMediaPackages.any { isKnownMusicPlayer(it) }) return false
        // If another app is already playing audio when no session info is available (listener permission off),
        // don't risk killing a music player unless the target itself is a video feed and has active audio.
        if (audioMusicActive && activePlayingMediaPackages.isEmpty() && targetPkg !in VIDEO_FEED_APPS) return false
        return targetPkg != null && (targetPkg in VIDEO_FEED_APPS || activePlayingMediaPackages.contains(targetPkg))
    }

    /** Never background-kill Focus or an app that is a music player / currently playing audio. */
    fun shouldKillProcess(pkg: String, focusPkg: String, activePlayingMediaPackages: Set<String>): Boolean {
        if (pkg == focusPkg) return false
        if (isKnownMusicPlayer(pkg)) return false
        if (pkg in activePlayingMediaPackages) return false
        return true
    }

    fun activePlayingPackages(ctx: Context): Set<String> {
        return try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return emptySet()
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
                .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .mapNotNull { it.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun shouldGrabOverlayAudio(ctx: Context, targetPkg: String?): Boolean {
        val am = try { ctx.getSystemService(AudioManager::class.java) } catch (_: Exception) { null }
        val active = activePlayingPackages(ctx)
        val musicActive = try { am?.isMusicActive == true } catch (_: Exception) { false }
        return shouldGrabOverlayAudio(
            targetPkg = targetPkg,
            focusMusicPlaying = Music.isPlaying,
            focusNotePlaying = NotePlayer.isPlaying,
            activePlayingMediaPackages = active,
            audioMusicActive = musicActive
        )
    }

    fun canSafelyKill(ctx: Context, pkg: String): Boolean =
        shouldKillProcess(pkg, ctx.packageName, activePlayingPackages(ctx))
}
