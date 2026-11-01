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

import org.json.JSONObject

/** The eleven colours of a theme, as opaque ARGB ints. */
data class ThemeColors(
    val primary: Int, val secondary: Int, val accent: Int,
    val background: Int, val surface: Int, val card: Int,
    val textPrimary: Int, val textSecondary: Int,
    val success: Int, val warning: Int, val error: Int,
)

/**
 * One declarative description of how Focus looks. Design values only: colours, radii, a spacing step, a text scale
 * and a few on/off choices. It can never change what the app does (the gate, the bank and the blocks ignore it).
 * Pure Kotlin on purpose (no Compose, no Android classes) so it can be validated and unit-tested on the JVM.
 */
data class ThemeConfig(
    val name: String,
    val appearance: String,                 // system | light | dark
    val colors: ThemeColors,
    val darkColors: ThemeColors?,           // used when the dark look is active; null = always [colors]
    val cardRadius: Int,                    // dp, 0..40
    val buttonRadius: Int,                  // dp, 0..40
    val spacing: String,                    // compact | normal | comfortable
    val typographyScale: Float,             // 0.85..1.3
    val homeShowGreeting: Boolean,
    val homeShowProgress: Boolean,
    val homePrimaryAction: String,          // subtle | normal | high
    val gateSupportiveMessage: Boolean,
    val gateStyle: String,                  // calm (breathing orb) | plain (still)
) {
    val spacingDp: Int get() = when (spacing) { "compact" -> 8; "comfortable" -> 16; else -> 12 }

    fun isDark(systemDark: Boolean): Boolean = appearance == "dark" || (appearance == "system" && systemDark)

    fun palette(systemDark: Boolean): ThemeColors = if (isDark(systemDark)) darkColors ?: colors else colors
}

sealed class ThemeParse {
    class Ok(val config: ThemeConfig) : ThemeParse()
    class Bad(val problems: List<String>) : ThemeParse()
}

object ThemeJson {
    const val SCHEMA_VERSION = 1
    const val MAX_CHARS = 16_000

    private val COLOR_KEYS = listOf("primary", "secondary", "accent", "background", "surface", "card", "textPrimary", "textSecondary", "success", "warning", "error")
    private val TOP_KEYS = setOf("schemaVersion", "themeName", "appearance", "colors", "darkColors", "shapes", "spacing", "typography", "components")
    private val APPEARANCE = setOf("system", "light", "dark")
    private val SPACING = setOf("compact", "normal", "comfortable")
    private val EMPHASIS = setOf("subtle", "normal", "high")
    private val GATE_STYLE = setOf("calm", "plain")
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val NAME = Regex("^[\\p{L}\\p{N} _'.,&-]{1,40}$")

    // ---- contrast (WCAG 2.x relative luminance) ----
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Dark or white text for a filled button of colour [bg], whichever reads better. */
    fun onColor(bg: Int): Int = if (contrast(bg, 0xFFFFFFFF.toInt()) >= contrast(bg, 0xFF1B1B1F.toInt())) 0xFFFFFFFF.toInt() else 0xFF1B1B1F.toInt()

    // ---- parse and validate ----
    /** Reads a theme. Anything unknown, out of range, unreadable or too faint is rejected with a plain-language reason; nothing is applied. */
    fun parse(input: String): ThemeParse {
        if (input.length > MAX_CHARS) return ThemeParse.Bad(listOf("This is too long to be a Focus theme."))
        val text = input.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = try { JSONObject(text) } catch (_: Exception) { return ThemeParse.Bad(listOf("This is not valid JSON. Paste the whole theme, starting with { and ending with }.")) }
        val bad = ArrayList<String>()

        for (k in root.keys()) if (k !in TOP_KEYS && !k.startsWith("_")) bad += "Unknown field \"$k\". Only the fields in the exported theme are allowed."
        for (k in root.keys()) if (k.startsWith("_") && !isNoteValue(root.opt(k))) bad += "\"$k\" may only hold plain text."

        val ver = (root.opt("schemaVersion") as? Number)?.toDouble()
        if (ver == null || ver != SCHEMA_VERSION.toDouble()) bad += "schemaVersion must be $SCHEMA_VERSION (this Focus does not know other versions)."

        val name = root.opt("themeName") as? String
        if (name == null || !NAME.matches(name)) bad += "themeName must be 1 to 40 letters, digits or simple punctuation."

        val appearance = enumOf(root, "appearance", APPEARANCE, bad)
        val colors = colorsOf(root, "colors", true, bad)
        val dark = colorsOf(root, "darkColors", false, bad)

        val shapes = objOf(root, "shapes", bad)
        val cardR = intOf(shapes, "shapes.cardRadius", "cardRadius", 0, 40, 20, bad)
        val btnR = intOf(shapes, "shapes.buttonRadius", "buttonRadius", 0, 40, 14, bad)
        val spacing = if (root.has("spacing")) enumOf(root, "spacing", SPACING, bad) else "normal"
        val typo = objOf(root, "typography", bad)
        val scale = numOf(typo, "typography.scale", "scale", 0.85, 1.3, 1.0, bad)

        val comps = objOf(root, "components", bad)
        val home = objOf(comps, "components.home", "home", bad)
        val gate = objOf(comps, "components.gate", "gate", bad)
        comps?.keys()?.forEach { if (it != "home" && it != "gate") bad += "Unknown field \"components.$it\"." }
        home?.keys()?.forEach { if (it !in setOf("showGreeting", "showProgress", "primaryAction")) bad += "Unknown field \"components.home.$it\"." }
        gate?.keys()?.forEach { if (it !in setOf("supportiveMessage", "visualStyle")) bad += "Unknown field \"components.gate.$it\"." }
        shapes?.keys()?.forEach { if (it !in setOf("cardRadius", "buttonRadius")) bad += "Unknown field \"shapes.$it\"." }
        typo?.keys()?.forEach { if (it != "scale") bad += "Unknown field \"typography.$it\"." }
        val showGreeting = boolOf(home, "components.home.showGreeting", "showGreeting", true, bad)
        val showProgress = boolOf(home, "components.home.showProgress", "showProgress", true, bad)
        val emphasis = if (home != null && home.has("primaryAction")) enumOf(home, "primaryAction", EMPHASIS, bad, "components.home.primaryAction") else "high"
        val supportive = boolOf(gate, "components.gate.supportiveMessage", "supportiveMessage", true, bad)
        val style = if (gate != null && gate.has("visualStyle")) enumOf(gate, "visualStyle", GATE_STYLE, bad, "components.gate.visualStyle") else "calm"

        if (colors != null) checkContrast("colors", colors, bad)
        if (dark != null) checkContrast("darkColors", dark, bad)

        if (bad.isNotEmpty() || name == null || appearance == null || colors == null || spacing == null || emphasis == null || style == null)
            return ThemeParse.Bad(bad.ifEmpty { listOf("The theme is incomplete.") })
        return ThemeParse.Ok(ThemeConfig(name, appearance, colors, dark, cardR, btnR, spacing, scale.toFloat(), showGreeting, showProgress, emphasis, supportive, style))
    }

    fun parseOrNull(input: String): ThemeConfig? = if (input.isBlank()) null else (parse(input) as? ThemeParse.Ok)?.config

    private fun isNoteValue(v: Any?): Boolean = when (v) {
        is String -> v.length <= 2000
        is JSONObject -> v.keys().asSequence().all { v.opt(it) is String && (v.opt(it) as String).length <= 400 }
        else -> false
    }

    private fun objOf(parent: JSONObject?, key: String, bad: MutableList<String>): JSONObject? = objOf(parent, key, key, bad)
    private fun objOf(parent: JSONObject?, path: String, key: String, bad: MutableList<String>): JSONObject? {
        if (parent == null || !parent.has(key)) return null
        val o = parent.opt(key)
        if (o is JSONObject) return o
        bad += "\"$path\" must be an object."
        return null
    }

    private fun enumOf(o: JSONObject, key: String, allowed: Set<String>, bad: MutableList<String>, path: String = key): String? {
        val v = o.opt(key) as? String
        if (v == null || v !in allowed) { bad += "$path must be one of: ${allowed.joinToString(", ")}."; return null }
        return v
    }

    private fun intOf(o: JSONObject?, path: String, key: String, lo: Int, hi: Int, def: Int, bad: MutableList<String>): Int {
        if (o == null || !o.has(key)) return def
        val n = o.opt(key) as? Number
        if (n == null || n.toDouble() != Math.floor(n.toDouble()) || n.toInt() < lo || n.toInt() > hi) { bad += "$path must be a whole number from $lo to $hi."; return def }
        return n.toInt()
    }

    private fun numOf(o: JSONObject?, path: String, key: String, lo: Double, hi: Double, def: Double, bad: MutableList<String>): Double {
        if (o == null || !o.has(key)) return def
        val n = (o.opt(key) as? Number)?.toDouble()
        if (n == null || n.isNaN() || n < lo || n > hi) { bad += "$path must be a number from $lo to $hi."; return def }
        return n
    }

    private fun boolOf(o: JSONObject?, path: String, key: String, def: Boolean, bad: MutableList<String>): Boolean {
        if (o == null || !o.has(key)) return def
        val b = o.opt(key) as? Boolean
        if (b == null) { bad += "$path must be true or false."; return def }
        return b
    }

    private fun colorsOf(root: JSONObject, key: String, required: Boolean, bad: MutableList<String>): ThemeColors? {
        if (!root.has(key)) { if (required) bad += "\"$key\" is missing."; return null }
        val o = root.opt(key) as? JSONObject
        if (o == null) { bad += "\"$key\" must be an object."; return null }
        for (k in o.keys()) if (k !in COLOR_KEYS) bad += "Unknown colour \"$key.$k\"."
        val v = HashMap<String, Int>()
        for (k in COLOR_KEYS) {
            val s = o.opt(k) as? String
            if (s == null) { bad += "$key.$k is missing."; continue }
            if (!HEX.matches(s)) { bad += "$key.$k must look like #RRGGBB (for example #4A7360)."; continue }
            v[k] = 0xFF000000.toInt() or s.substring(1).toLong(16).toInt()
        }
        if (v.size != COLOR_KEYS.size) return null
        return ThemeColors(v["primary"]!!, v["secondary"]!!, v["accent"]!!, v["background"]!!, v["surface"]!!, v["card"]!!,
            v["textPrimary"]!!, v["textSecondary"]!!, v["success"]!!, v["warning"]!!, v["error"]!!)
    }

    /** Text must stay readable: that is part of the schema, not a suggestion. */
    private fun checkContrast(path: String, c: ThemeColors, bad: MutableList<String>) {
        fun need(a: Int, b: Int, min: Double, what: String) {
            val r = contrast(a, b)
            if (r < min) bad += "$path: $what is too faint (contrast ${"%.1f".format(r)}, needs ${"%.1f".format(min)} or more)."
        }
        need(c.textPrimary, c.background, 4.5, "textPrimary on background")
        need(c.textPrimary, c.surface, 4.5, "textPrimary on surface")
        need(c.textPrimary, c.card, 4.5, "textPrimary on card")
        need(c.textSecondary, c.background, 4.5, "textSecondary on background")
        need(c.textSecondary, c.card, 4.5, "textSecondary on card")
        need(c.primary, c.background, 3.0, "primary on background")
    }

    // ---- export ----
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    private fun colorsBlock(name: String, c: ThemeColors): String =
        "  \"$name\": {\n" +
            listOf("primary" to c.primary, "secondary" to c.secondary, "accent" to c.accent, "background" to c.background, "surface" to c.surface,
                "card" to c.card, "textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary, "success" to c.success, "warning" to c.warning, "error" to c.error)
                .joinToString(",\n") { "    \"${it.first}\": \"${hex(it.second)}\"" } +
            "\n  },\n"

    /** The theme as pretty JSON, with a short human- and AI-readable description of every field. */
    fun toJson(c: ThemeConfig): String = buildString {
        append("{\n")
        append("  \"_about\": \"A Focus theme. Design values only. Keep schemaVersion and every required field. Return valid JSON only. No code.\",\n")
        append("  \"_fields\": {\n")
        append("    \"schemaVersion\": \"Always 1.\",\n")
        append("    \"themeName\": \"1 to 40 letters, digits or simple punctuation.\",\n")
        append("    \"appearance\": \"system, light or dark. Decides when darkColors is used.\",\n")
        append("    \"colors\": \"#RRGGBB. primary: main buttons and highlights. secondary and accent: supporting colours. background: the page. surface: bars and sheets. card: cards. textPrimary and textSecondary: text. success, warning, error: states. Text must stay readable (contrast 4.5 or more on background and cards).\",\n")
        append("    \"darkColors\": \"Optional. The same eleven colours for the dark look.\",\n")
        append("    \"shapes\": \"cardRadius and buttonRadius, whole numbers 0 to 40 (dp).\",\n")
        append("    \"spacing\": \"compact, normal or comfortable. Space between blocks on the home screen.\",\n")
        append("    \"typography.scale\": \"0.85 to 1.3. Text size multiplier on top of the phone's own text size.\",\n")
        append("    \"components.home\": \"showGreeting and showProgress (true or false); primaryAction: subtle, normal or high for the Start button.\",\n")
        append("    \"components.gate\": \"supportiveMessage (true or false); visualStyle: calm (breathing orb) or plain (still).\"\n")
        append("  },\n")
        append("  \"schemaVersion\": $SCHEMA_VERSION,\n")
        append("  \"themeName\": \"${c.name}\",\n")
        append("  \"appearance\": \"${c.appearance}\",\n")
        append(colorsBlock("colors", c.colors))
        c.darkColors?.let { append(colorsBlock("darkColors", it)) }
        append("  \"shapes\": { \"cardRadius\": ${c.cardRadius}, \"buttonRadius\": ${c.buttonRadius} },\n")
        append("  \"spacing\": \"${c.spacing}\",\n")
        append("  \"typography\": { \"scale\": ${"%.2f".format(java.util.Locale.US, c.typographyScale)} },\n")
        append("  \"components\": {\n")
        append("    \"home\": { \"showGreeting\": ${c.homeShowGreeting}, \"showProgress\": ${c.homeShowProgress}, \"primaryAction\": \"${c.homePrimaryAction}\" },\n")
        append("    \"gate\": { \"supportiveMessage\": ${c.gateSupportiveMessage}, \"visualStyle\": \"${c.gateStyle}\" }\n")
        append("  }\n")
        append("}\n")
    }

    /** What to send to an AI together with the exported theme. */
    const val AI_PROMPT = "Modify this Focus theme configuration. Make it calmer and more supportive. Keep all required fields and the schema version unchanged. Return valid JSON only. Do not add executable code."
}

object ThemePresets {
    val ALL: List<ThemeConfig> = listOf(
        ThemeConfig(
            name = "Calm Sage", appearance = "system",
            colors = ThemeColors(
                primary = 0xFF4A7360.toInt(),
                secondary = 0xFF6F8F7A.toInt(),
                accent = 0xFFB9803A.toInt(),
                background = 0xFFF3F2EA.toInt(),
                surface = 0xFFFBFAF5.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF232A25.toInt(),
                textSecondary = 0xFF566159.toInt(),
                success = 0xFF3F7A55.toInt(),
                warning = 0xFF9A6A1F.toInt(),
                error = 0xFFA94848.toInt(),
            ),
            darkColors = ThemeColors(
                primary = 0xFF8FC0A5.toInt(),
                secondary = 0xFF7FA58E.toInt(),
                accent = 0xFFE0B070.toInt(),
                background = 0xFF141A16.toInt(),
                surface = 0xFF1B221D.toInt(),
                card = 0xFF222B25.toInt(),
                textPrimary = 0xFFE6ECE7.toInt(),
                textSecondary = 0xFFA9B5AD.toInt(),
                success = 0xFF79C497.toInt(),
                warning = 0xFFE0B070.toInt(),
                error = 0xFFE58A8A.toInt(),
            ),
            cardRadius = 20, buttonRadius = 14, spacing = "normal", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "high",
            gateSupportiveMessage = true, gateStyle = "calm",
        ),
        ThemeConfig(
            name = "Midnight", appearance = "dark",
            colors = ThemeColors(
                primary = 0xFF8AA4FF.toInt(),
                secondary = 0xFF7B8FD6.toInt(),
                accent = 0xFFF2C14E.toInt(),
                background = 0xFF0E1117.toInt(),
                surface = 0xFF151A23.toInt(),
                card = 0xFF1C2230.toInt(),
                textPrimary = 0xFFE8EBF2.toInt(),
                textSecondary = 0xFFA3ABBD.toInt(),
                success = 0xFF6FCF97.toInt(),
                warning = 0xFFF2C14E.toInt(),
                error = 0xFFF28B82.toInt(),
            ),
            darkColors = null,
            cardRadius = 22, buttonRadius = 16, spacing = "normal", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "high",
            gateSupportiveMessage = true, gateStyle = "calm",
        ),
        ThemeConfig(
            name = "Warm Paper", appearance = "system",
            colors = ThemeColors(
                primary = 0xFF8A5A33.toInt(),
                secondary = 0xFFA47B55.toInt(),
                accent = 0xFF2F6F73.toInt(),
                background = 0xFFF5EFE3.toInt(),
                surface = 0xFFFBF7EE.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF2B241C.toInt(),
                textSecondary = 0xFF5F5447.toInt(),
                success = 0xFF4A7A47.toInt(),
                warning = 0xFF8F6414.toInt(),
                error = 0xFFA64040.toInt(),
            ),
            darkColors = ThemeColors(
                primary = 0xFFE0B58A.toInt(),
                secondary = 0xFFC49A70.toInt(),
                accent = 0xFF6FC3C8.toInt(),
                background = 0xFF17130E.toInt(),
                surface = 0xFF1F1A14.toInt(),
                card = 0xFF272017.toInt(),
                textPrimary = 0xFFF0E8DA.toInt(),
                textSecondary = 0xFFB8AC9A.toInt(),
                success = 0xFF8CC48A.toInt(),
                warning = 0xFFE3B25A.toInt(),
                error = 0xFFE38A8A.toInt(),
            ),
            cardRadius = 16, buttonRadius = 12, spacing = "comfortable", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "high",
            gateSupportiveMessage = true, gateStyle = "calm",
        ),
        ThemeConfig(
            name = "Ocean", appearance = "system",
            colors = ThemeColors(
                primary = 0xFF1F6F8B.toInt(),
                secondary = 0xFF4C93A8.toInt(),
                accent = 0xFFE07A5F.toInt(),
                background = 0xFFEEF4F6.toInt(),
                surface = 0xFFF8FBFC.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF14262D.toInt(),
                textSecondary = 0xFF4B6069.toInt(),
                success = 0xFF2F7D5B.toInt(),
                warning = 0xFF99620F.toInt(),
                error = 0xFFB04545.toInt(),
            ),
            darkColors = ThemeColors(
                primary = 0xFF7CC4DB.toInt(),
                secondary = 0xFF5FA3B8.toInt(),
                accent = 0xFFF2A58E.toInt(),
                background = 0xFF0C161A.toInt(),
                surface = 0xFF121F25.toInt(),
                card = 0xFF18282F.toInt(),
                textPrimary = 0xFFE3EEF2.toInt(),
                textSecondary = 0xFF9DB3BC.toInt(),
                success = 0xFF74C9A2.toInt(),
                warning = 0xFFE3B25A.toInt(),
                error = 0xFFEE8F8F.toInt(),
            ),
            cardRadius = 22, buttonRadius = 16, spacing = "normal", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "high",
            gateSupportiveMessage = true, gateStyle = "calm",
        ),
        ThemeConfig(
            name = "Lavender", appearance = "system",
            colors = ThemeColors(
                primary = 0xFF6A55A8.toInt(),
                secondary = 0xFF8C7BC0.toInt(),
                accent = 0xFFC27A9E.toInt(),
                background = 0xFFF3F0F8.toInt(),
                surface = 0xFFFAF8FD.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF241F33.toInt(),
                textSecondary = 0xFF5A5370.toInt(),
                success = 0xFF3F7A5A.toInt(),
                warning = 0xFF946218.toInt(),
                error = 0xFFA64560.toInt(),
            ),
            darkColors = ThemeColors(
                primary = 0xFFB9A8F0.toInt(),
                secondary = 0xFF9C8CD6.toInt(),
                accent = 0xFFE5A3C4.toInt(),
                background = 0xFF15121E.toInt(),
                surface = 0xFF1D1928.toInt(),
                card = 0xFF262133.toInt(),
                textPrimary = 0xFFECE8F5.toInt(),
                textSecondary = 0xFFB0A8C6.toInt(),
                success = 0xFF7DC49C.toInt(),
                warning = 0xFFE3B25A.toInt(),
                error = 0xFFEE8FA6.toInt(),
            ),
            cardRadius = 24, buttonRadius = 18, spacing = "normal", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "high",
            gateSupportiveMessage = true, gateStyle = "calm",
        ),
        ThemeConfig(
            name = "Minimal Monochrome", appearance = "system",
            colors = ThemeColors(
                primary = 0xFF222222.toInt(),
                secondary = 0xFF555555.toInt(),
                accent = 0xFF888888.toInt(),
                background = 0xFFF4F4F2.toInt(),
                surface = 0xFFFAFAF9.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF1A1A1A.toInt(),
                textSecondary = 0xFF555555.toInt(),
                success = 0xFF2F6B3F.toInt(),
                warning = 0xFF8A5A00.toInt(),
                error = 0xFFA02020.toInt(),
            ),
            darkColors = ThemeColors(
                primary = 0xFFEDEDED.toInt(),
                secondary = 0xFFBDBDBD.toInt(),
                accent = 0xFF9A9A9A.toInt(),
                background = 0xFF0F0F0F.toInt(),
                surface = 0xFF171717.toInt(),
                card = 0xFF1F1F1F.toInt(),
                textPrimary = 0xFFEDEDED.toInt(),
                textSecondary = 0xFFA8A8A8.toInt(),
                success = 0xFF7CC08C.toInt(),
                warning = 0xFFD9AE5A.toInt(),
                error = 0xFFE88A8A.toInt(),
            ),
            cardRadius = 8, buttonRadius = 6, spacing = "normal", typographyScale = 1.0f,
            homeShowGreeting = true, homeShowProgress = true, homePrimaryAction = "normal",
            gateSupportiveMessage = true, gateStyle = "plain",
        ),
    )

    val DEFAULT: ThemeConfig get() = ALL.first()
}
