/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGuardTest {
    @Test fun recognizesKnownMusicPlayersAndExcludesVideoFeeds() {
        assertTrue(MusicGuard.isKnownMusicPlayer("com.spotify.music"))
        assertTrue(MusicGuard.isKnownMusicPlayer("com.google.android.apps.youtube.music"))
        assertTrue(MusicGuard.isKnownMusicPlayer("app.revanced.android.apps.youtube.music"))
        assertTrue(MusicGuard.isKnownMusicPlayer("org.videolan.vlc"))
        assertTrue(MusicGuard.isKnownMusicPlayer("com.apple.android.music"))
        assertTrue(MusicGuard.isKnownMusicPlayer("com.maxmpz.audioplayer"))
        assertTrue(MusicGuard.isKnownMusicPlayer("in.krosbits.musicolet"))
        assertTrue(MusicGuard.isKnownMusicPlayer("com.sec.android.app.music"))

        assertFalse(MusicGuard.isKnownMusicPlayer("com.google.android.youtube"))
        assertFalse(MusicGuard.isKnownMusicPlayer("com.zhiliaoapp.musically"))
        assertFalse(MusicGuard.isKnownMusicPlayer("com.android.chrome"))
        assertFalse(MusicGuard.isKnownMusicPlayer("org.telegram.messenger"))
        assertFalse(MusicGuard.isKnownMusicPlayer(null))
    }

    @Test fun overlayNeverGrabsAudioWhenAnyMusicPlays() {
        // Focus internal music or voice note playing
        assertFalse(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "com.google.android.youtube",
                focusMusicPlaying = true,
                focusNotePlaying = false,
                activePlayingMediaPackages = emptySet(),
                audioMusicActive = true,
            )
        )
        assertFalse(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "com.google.android.youtube",
                focusMusicPlaying = false,
                focusNotePlaying = true,
                activePlayingMediaPackages = emptySet(),
                audioMusicActive = true,
            )
        )
        // Spotify / YT Music / VLC actively playing in background while gating YouTube
        assertFalse(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "com.google.android.youtube",
                focusMusicPlaying = false,
                focusNotePlaying = false,
                activePlayingMediaPackages = setOf("com.spotify.music"),
                audioMusicActive = true,
            )
        )
        // Target itself is a music player
        assertFalse(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "org.videolan.vlc",
                focusMusicPlaying = false,
                focusNotePlaying = false,
                activePlayingMediaPackages = setOf("org.videolan.vlc"),
                audioMusicActive = true,
            )
        )
        // Listener off but system audio active while opening Chrome: keep audio safe
        assertFalse(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "com.android.chrome",
                focusMusicPlaying = false,
                focusNotePlaying = false,
                activePlayingMediaPackages = emptySet(),
                audioMusicActive = true,
            )
        )
        // Idle visual distraction with no music player active: overlay grabs focus
        assertTrue(
            MusicGuard.shouldGrabOverlayAudio(
                targetPkg = "com.google.android.youtube",
                focusMusicPlaying = false,
                focusNotePlaying = false,
                activePlayingMediaPackages = emptySet(),
                audioMusicActive = false,
            )
        )
    }

    @Test fun neverKillsFocusOrPlayingMusicPackages() {
        assertFalse(MusicGuard.shouldKillProcess("com.barq.focus", "com.barq.focus", emptySet()))
        assertFalse(MusicGuard.shouldKillProcess("com.spotify.music", "com.barq.focus", emptySet()))
        assertFalse(MusicGuard.shouldKillProcess("org.videolan.vlc", "com.barq.focus", emptySet()))
        assertFalse(MusicGuard.shouldKillProcess("com.google.android.apps.youtube.music", "com.barq.focus", emptySet()))
        assertFalse(MusicGuard.shouldKillProcess("com.example.podcast", "com.barq.focus", setOf("com.example.podcast")))

        assertTrue(MusicGuard.shouldKillProcess("com.google.android.youtube", "com.barq.focus", setOf("com.spotify.music")))
    }
}
