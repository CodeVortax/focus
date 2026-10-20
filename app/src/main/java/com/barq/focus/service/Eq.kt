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
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.barq.focus.data.Store

/** The in-app equalizer: the phone's own Equalizer and BassBoost effects, attached to the music player's audio session. */
object Eq {
    private var eq: Equalizer? = null
    private var bb: BassBoost? = null
    private var session = 0
    private var store: Store? = null

    var available by mutableStateOf(false)
    var on by mutableStateOf(false)
    var bass by mutableIntStateOf(0)                      // 0..1000
    var preset by mutableIntStateOf(-1)                   // -1 = custom
    val levels = mutableStateListOf<Int>()                // millibels per band
    var minLevel = -1500; var maxLevel = 1500
    var freqs: List<Int> = emptyList()                    // Hz per band
    var presets: List<String> = emptyList()

    /** Called whenever the player's audio session appears or changes. */
    fun attach(ctx: Context, sessionId: Int) {
        if (sessionId <= 0 || sessionId == session && eq != null) return
        val s = store ?: Store(ctx.applicationContext).also { store = it }
        release()
        try {
            val e = Equalizer(0, sessionId)
            eq = e; session = sessionId
            minLevel = e.bandLevelRange[0].toInt(); maxLevel = e.bandLevelRange[1].toInt()
            freqs = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
            val saved = s.eqLevels.split(",").mapNotNull { it.toIntOrNull() }
            levels.clear()
            for (b in 0 until e.numberOfBands) levels.add((saved.getOrNull(b) ?: 0).coerceIn(minLevel, maxLevel))
            on = s.eqOn; bass = s.eqBass; preset = s.eqPreset
            try { bb = BassBoost(0, sessionId).also { if (it.strengthSupported) it.setStrength(bass.toShort()) } } catch (_: Exception) { bb = null }
            apply()
            available = true
        } catch (_: Exception) { available = false; eq = null }
    }

    private fun apply() {
        val e = eq ?: return
        try {
            levels.forEachIndexed { b, v -> e.setBandLevel(b.toShort(), v.toShort()) }
            e.setEnabled(on)
            bb?.let { it.setEnabled(on && bass > 0); if (it.strengthSupported) it.setStrength(bass.toShort()) }
        } catch (_: Exception) {}
        store?.let { it.eqOn = on; it.eqLevels = levels.joinToString(","); it.eqBass = bass; it.eqPreset = preset }
    }

    fun setOn(v: Boolean) { on = v; apply() }
    fun setBand(b: Int, v: Int) { levels[b] = v.coerceIn(minLevel, maxLevel); preset = -1; apply() }
    fun setBass(v: Int) { bass = v.coerceIn(0, 1000); apply() }
    fun usePreset(i: Int) {
        val e = eq ?: return
        try {
            e.usePreset(i.toShort())
            for (b in levels.indices) levels[b] = e.getBandLevel(b.toShort()).toInt()
            preset = i; on = true; apply()
        } catch (_: Exception) {}
    }
    fun reset() { for (b in levels.indices) levels[b] = 0; bass = 0; preset = -1; apply() }

    fun release() {
        try { eq?.release() } catch (_: Exception) {}
        try { bb?.release() } catch (_: Exception) {}
        eq = null; bb = null; session = 0; available = false
    }
}
