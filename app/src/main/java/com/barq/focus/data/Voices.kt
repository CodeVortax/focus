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
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One recorded voice note attached to a note. [file] is a name inside the app's private voice folder. */
data class Voice(val file: String, val ms: Long, val at: Long = System.currentTimeMillis())

object Voices {
    fun dir(c: Context): File = File(c.applicationContext.filesDir, "voice").also { it.mkdirs() }
    fun file(c: Context, name: String): File = File(dir(c), name)
    fun delete(c: Context, name: String) {
        if (name.isEmpty()) return
        try { file(c, name).delete() } catch (_: Exception) {}
    }

    fun toJson(l: List<Voice>): JSONArray {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("f", it.file).put("ms", it.ms).put("at", it.at)) }
        return a
    }

    fun fromJson(a: JSONArray?): List<Voice> =
        if (a == null) emptyList()
        else (0 until a.length()).map { a.getJSONObject(it).let { o -> Voice(o.getString("f"), o.optLong("ms", 0L), o.optLong("at", 0L)) } }

    fun clock(ms: Long): String {
        val s = (ms + 500) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/** Records AAC audio from the microphone into the private voice folder. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var name = ""
    private var t0 = 0L
    val active: Boolean get() = rec != null

    fun start(): Boolean {
        cancel()
        return try {
            val n = "v_" + System.currentTimeMillis() + ".m4a"
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx.applicationContext) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(Voices.file(ctx, n).absolutePath)
            r.prepare()
            r.start()
            rec = r; name = n; t0 = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            try { rec?.release() } catch (_: Exception) {}
            rec = null
            false
        }
    }

    /** Stops and keeps the clip. Returns (file name, length ms), or null if it was too short to save. */
    fun stop(): Pair<String, Long>? {
        val r = rec ?: return null
        rec = null
        val n = name
        val ms = System.currentTimeMillis() - t0
        return try {
            r.stop(); r.release()
            if (ms < 600L) { Voices.delete(ctx, n); null } else n to ms
        } catch (_: Exception) {
            try { r.release() } catch (_: Exception) {}
            Voices.delete(ctx, n)
            null
        }
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        Voices.delete(ctx, name)
    }
}

/** One clip plays at a time; [playing] is the file name currently audible (UI reads it). */
object VoicePlayer {
    var playing by mutableStateOf("")
        private set
    private var mp: MediaPlayer? = null

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        playing = ""
    }

    fun toggle(c: Context, name: String) {
        if (playing == name) { stop(); return }
        stop()
        try {
            val m = MediaPlayer()
            m.setDataSource(Voices.file(c, name).absolutePath)
            m.setOnCompletionListener { stop() }
            m.prepare()
            m.start()
            mp = m
            playing = name
        } catch (_: Exception) { stop() }
    }
}
