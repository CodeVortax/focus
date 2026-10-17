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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/** In-app language: built-in Arabic, Russian, German and French + any language pack imported as a .foc file. */
object I18n {
    var version by mutableIntStateOf(0)       // snapshot state: UI recomposes when the language changes
    var rtl by mutableStateOf(false)
    var code = "en"
        private set
    internal var table: Map<String, String> = emptyMap()

    fun apply(ctx: Context, newCode: String) {
        val store = Store(ctx)
        var map: Map<String, String> = emptyMap()
        var isRtl = false
        when (newCode) {
            "en" -> {}
            "ar" -> { map = AR + AR_EXTRA; isRtl = true }
            in BUILTIN -> map = loadAsset(ctx, newCode)
            else -> {
                val json = store.langPack(newCode)
                if (json != null) {
                    try {
                        val o = JSONObject(json)
                        val s = o.optJSONObject("strings")
                        if (s != null) map = s.keys().asSequence().associateWith { k -> s.getString(k) }
                        isRtl = o.optBoolean("rtl", false)
                    } catch (_: Exception) {}
                }
            }
        }
        code = newCode
        table = map
        rtl = isRtl
        store.lang = newCode
        version++
    }

    /** Languages that ship inside the app as assets/lang/<code>.json (same format as an imported pack). */
    val BUILTIN = setOf("ru", "de", "fr")
    private val BUILTIN_NAMES = listOf("ru" to "Русский", "de" to "Deutsch", "fr" to "Français")

    private fun loadAsset(ctx: Context, code: String): Map<String, String> = try {
        val text = ctx.assets.open("lang/$code.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val s = JSONObject(text).optJSONObject("strings")
        if (s == null) emptyMap() else s.keys().asSequence().associateWith { k -> s.getString(k) }
    } catch (_: Exception) { emptyMap() }

    fun init(ctx: Context) = apply(ctx, Store(ctx).lang)

    fun names(ctx: Context): List<Pair<String, String>> {
        val store = Store(ctx)
        val extra = store.langCodes().filter { it != "ar" && it != "en" && it !in BUILTIN }.map { c ->
            val n = try { JSONObject(store.langPack(c) ?: "{}").optString("name", c) } catch (_: Exception) { c }
            c to n
        }
        return listOf("en" to "English", "ar" to "العربية") + BUILTIN_NAMES + extra
    }

    fun speakLocale(): Locale = if (code == "en") Locale.US else Locale(code)
}

fun tr(s: String): String {
    I18n.version
    return I18n.table[s] ?: s
}

fun trf(fmt: String, vararg a: Any?): String =
    try { String.format(Locale.US, tr(fmt), *a) } catch (_: Exception) { String.format(Locale.US, fmt, *a) }

/** Marks a string in a static table as translatable; show it with tr(). */
fun K(s: String): String = s
