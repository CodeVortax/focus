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

package com.barq.focus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.barq.focus.R

// Rounded, friendly display font used only for the "Barq" wordmark itself
// (top bar + drawer header) — everything else stays in the system font for
// maximum legibility.
@OptIn(ExperimentalTextApi::class)
private val BalooBrand = FontFamily(
    Font(
        R.font.baloo2_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

val FocusBrandFont: FontFamily get() = AppFont.family ?: BalooBrand
val FocusNoteFont: FontFamily get() = AppFont.family ?: BalooNote

// Same Baloo 2, at reading weights, for the notes.
@OptIn(ExperimentalTextApi::class)
private val BalooNote = FontFamily(
    Font(R.font.baloo2_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.baloo2_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.baloo2_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Swap FontFamily.Default for a bundled Google Sans / Product Sans font later
// (add the .ttf under res/font and reference it here) to get closer to true Pixel styling.
val FocusTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

/**
 * Optional app-wide font. Priority: a font file you picked in Settings (stored in the app's files),
 * then a font bundled in the source at app/src/main/assets/fonts/default.ttf (or .otf), else the system font.
 */
object AppFont {
    var family by androidx.compose.runtime.mutableStateOf<FontFamily?>(null)
        private set
    var name by androidx.compose.runtime.mutableStateOf("")
        private set

    /** The same font for classic Views (the gate overlay). Null = system font. */
    @Volatile var typeface: android.graphics.Typeface? = null
        private set

    private fun file(c: android.content.Context) = java.io.File(java.io.File(c.filesDir, "fonts").also { it.mkdirs() }, "custom.font")

    fun load(c: android.content.Context) {
        var f: FontFamily? = null; var n = ""
        typeface = null
        try {
            val file = file(c)
            if (file.exists()) {
                val t = android.graphics.Typeface.createFromFile(file)
                typeface = t
                f = FontFamily(t)
                n = c.getSharedPreferences("focus", 0).getString("font_name", "") ?: ""
            }
        } catch (_: Exception) { f = null }
        if (f == null) {
            for (a in listOf("fonts/default.ttf", "fonts/default.otf")) {
                try {
                    val t = android.graphics.Typeface.createFromAsset(c.assets, a)
                    typeface = t; f = FontFamily(t); n = "default"; break
                } catch (_: Exception) {}
            }
        }
        family = f; name = n
    }

    /** Copies the picked file in and applies it. Returns false if it isn't a usable font. */
    fun import(c: android.content.Context, uri: android.net.Uri, display: String): Boolean {
        val tmp = java.io.File(c.cacheDir, "font.tmp")
        return try {
            c.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            android.graphics.Typeface.Builder(tmp).build() ?: return false
            tmp.copyTo(file(c), overwrite = true)
            c.getSharedPreferences("focus", 0).edit().putString("font_name", display).apply()
            load(c); true
        } catch (_: Exception) { false } finally { tmp.delete() }
    }

    fun reset(c: android.content.Context) {
        file(c).delete()
        c.getSharedPreferences("focus", 0).edit().remove("font_name").apply()
        load(c)
    }
}

/** The same typography with every style set in [f] (null keeps the system font). */
fun Typography.withFont(f: FontFamily?): Typography = if (f == null) this else Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)
