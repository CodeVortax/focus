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


package com.barq.focus.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Anything attached to a note.
 * [kind]: image, video, audio, pdf, file, link, note.
 * [uri]: a content address, "voice:<file>" for a recording kept in the app, a web address, or a note id.
 * [temp]: a recording that still has its temporary name.
 */
data class Attach(
    val kind: String,
    val uri: String,
    val name: String,
    val ms: Long = 0L,
    val temp: Boolean = false,
) {
    val isLocal get() = uri.startsWith("voice:")
    fun localFile(c: Context): File? = if (isLocal) Voices.file(c, uri.removePrefix("voice:")) else null
    fun playUri(c: Context): Uri = localFile(c)?.let { Uri.fromFile(it) } ?: Uri.parse(uri)

    /** ".ogg", ".m4a" ... of the real file, so an exported copy keeps a working extension. */
    fun ext(): String {
        val f = if (isLocal) uri.removePrefix("voice:") else name
        val i = f.lastIndexOf('.')
        return if (i in 1 until f.length - 1 && f.length - i <= 6) f.substring(i) else if (isLocal) ".m4a" else ""
    }

    companion object {
        fun toJson(l: List<Attach>): JSONArray {
            val a = JSONArray()
            l.forEach { a.put(JSONObject().put("k", it.kind).put("u", it.uri).put("n", it.name).put("ms", it.ms).put("t", it.temp)) }
            return a
        }

        fun fromJson(a: JSONArray?): List<Attach> =
            if (a == null) emptyList()
            else (0 until a.length()).map {
                a.getJSONObject(it).let { o -> Attach(o.getString("k"), o.getString("u"), o.optString("n", ""), o.optLong("ms", 0L), o.optBoolean("t", false)) }
            }

        fun kindOf(mime: String?, name: String): String {
            val m = (mime ?: "").lowercase()
            val n = name.lowercase()
            return when {
                m.startsWith("image/") -> "image"
                m.startsWith("video/") -> "video"
                m.startsWith("audio/") -> "audio"
                m == "application/pdf" || n.endsWith(".pdf") -> "pdf"
                n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".ogg") || n.endsWith(".opus") || n.endsWith(".wav") || n.endsWith(".flac") -> "audio"
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov") -> "video"
                n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif") -> "image"
                else -> "file"
            }
        }

        /** Temporary name for a fresh recording, e.g. "Recording 15 Oct 16:48". */
        fun tempName(): String =
            "Recording " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

        /** A name typed by the user, made safe for a file name, without any extension. */
        fun cleanName(s: String): String =
            s.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trimEnd('.').ifEmpty { "Recording" }
    }
}

/** Small helpers for media files picked from the phone. */
object MediaInfo {
    fun nameOf(c: Context, uri: Uri): String {
        try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { q ->
                if (q.moveToFirst()) return q.getString(0) ?: "file"
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "file"
    }

    fun durationMs(c: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(c, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) { 0L } finally { try { r.release() } catch (_: Throwable) {} }
    }
}

/** Plays one audio attachment at a time and reports where it is (the mini player reads these). */
object NotePlayer {
    var current by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var pos by mutableLongStateOf(0L)
        private set
    var dur by mutableLongStateOf(0L)
        private set
    var title by mutableStateOf("")
        private set
    private var mp: MediaPlayer? = null
    private var app: Context? = null

    /** Tells the media notification (lock screen, status-bar capsules) what changed. */
    private fun sync() { app?.let { com.barq.focus.service.NoteAudio.sync(it) } }

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        current = ""; isPlaying = false; pos = 0L; dur = 0L; title = ""
        sync()
    }

    fun toggle(c: Context, key: String, uri: Uri, name: String = "") {
        app = c.applicationContext
        if (current == key) { if (isPlaying) pause() else resume(); return }
        stop()
        try {
            com.barq.focus.service.Music.player?.pause()      // one thing at a time
            val m = MediaPlayer()
            m.setDataSource(c.applicationContext, uri)
            m.setOnCompletionListener { stop() }
            m.prepare()
            m.start()
            mp = m
            current = key; isPlaying = true; dur = m.duration.toLong().coerceAtLeast(0L); pos = 0L; title = name
            sync()
        } catch (_: Exception) { stop() }
    }

    fun pause() {
        val m = mp ?: return
        try { if (m.isPlaying) m.pause(); isPlaying = false; pos = m.currentPosition.toLong() } catch (_: Exception) { stop(); return }
        sync()
    }

    fun resume() {
        val m = mp ?: return
        try { com.barq.focus.service.Music.player?.pause(); m.start(); isPlaying = true } catch (_: Exception) { stop(); return }
        sync()
    }

    fun refresh() {
        val m = mp ?: return
        try { pos = m.currentPosition.toLong(); dur = m.duration.toLong().coerceAtLeast(0L); isPlaying = m.isPlaying } catch (_: Exception) {}
    }

    fun seekToMs(ms: Long) {
        val m = mp ?: return
        try { m.seekTo(ms.coerceIn(0L, dur).toInt()); pos = m.currentPosition.toLong() } catch (_: Exception) {}
        sync()
    }

    fun seekTo(frac: Float) = seekToMs((dur * frac.coerceIn(0f, 1f)).toLong())
}
