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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

/**
 * .foc / .fcs: one container, the HEADER decides what it is.
 *  - binary header: video/audio -> treat card, %PDF -> library
 *  - JSON with {"focus": "today" | "config" | "notes" | "lang"} -> applied accordingly
 */
object Foc {
    enum class Kind { TODAY, CONFIG, NOTES, LANG, MEDIA, PDF, UNKNOWN }

    private val PALETTE = listOf(0xFF34A853, 0xFF4285F4, 0xFFEA4335, 0xFFFBBC04).map { it.toInt() }

    fun sniffBytes(b: ByteArray): Kind {
        fun s(off: Int, str: String) = b.size >= off + str.length && String(b, off, str.length, Charsets.ISO_8859_1) == str
        val mkv = b.size >= 4 && b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() && b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()
        val mp3 = b.size >= 2 && b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0
        return when {
            s(0, "%PDF") -> Kind.PDF
            s(4, "ftyp") || mkv || s(0, "OggS") || s(0, "fLaC") || s(0, "ID3") || s(0, "FLV") ||
                (s(0, "RIFF") && (s(8, "AVI ") || s(8, "WAVE"))) || mp3 -> Kind.MEDIA
            else -> Kind.UNKNOWN
        }
    }

    fun kindOfText(t: String): Kind {
        val s = t.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!s.startsWith("{")) return Kind.UNKNOWN
        return try {
            val o = JSONObject(s)
            when (o.optString("focus")) {
                "today" -> Kind.TODAY
                "config" -> Kind.CONFIG
                "notes" -> Kind.NOTES
                "lang" -> Kind.LANG
                else -> if (o.optString("app") == "focus-notes") Kind.NOTES else Kind.UNKNOWN
            }
        } catch (_: Exception) { Kind.UNKNOWN }
    }

    // ---------------------------------------------------------------- import
    fun applyText(ctx: Context, raw: String): String {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            when (kindOfText(text)) {
                Kind.TODAY -> applyToday(ctx, JSONObject(text.trimStart('\uFEFF')), "today")
                Kind.CONFIG -> applyConfig(ctx, JSONObject(text.trimStart('\uFEFF')))
                Kind.NOTES -> trf("Imported %d notes", NotesDb.get(ctx).importJson(text.trimStart('\uFEFF')))
                Kind.LANG -> applyLang(ctx, JSONObject(text.trimStart('\uFEFF')))
                else -> tr("Not a Focus file. It must be JSON with a \"focus\" header (today, config, notes or lang).")
            }
        } catch (e: Exception) {
            trf("Could not read it: %s", e.message ?: "?")
        }
    }

    fun applyUri(ctx: Context, uri: Uri): String {
        val cr = ctx.contentResolver
        val head = ByteArray(16)
        val n = try { cr.openInputStream(uri)?.use { it.read(head) } ?: -1 } catch (_: Exception) { -1 }
        if (n <= 0) return tr("Could not read that file.")
        val name = displayName(ctx, uri)
        return when (sniffBytes(head.copyOf(n))) {
            Kind.PDF -> {
                keep(ctx, uri)
                Store(ctx).upsertPdf(PdfEntry(uri.toString(), name.removeSuffix(".pdf"), 0, 0))
                trf("Added PDF to your library: %s", name)
            }
            Kind.MEDIA -> {
                keep(ctx, uri)
                val store = Store(ctx)
                val cards = store.cards()
                store.saveCards(
                    cards + FocusCard(
                        System.currentTimeMillis(), name.substringBeforeLast('.'), CardType.TREAT,
                        PALETTE[cards.size % 4], 40, media = uri.toString()
                    )
                )
                trf("Added a treat card: %s", name)
            }
            else -> {
                val bytes = try { cr.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
                if (bytes == null || bytes.size > 2_000_000) tr("Could not read that file.")
                else applyText(ctx, String(bytes, Charsets.UTF_8))
            }
        }
    }

    private fun keep(ctx: Context, uri: Uri) {
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "file"
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "file"
    }

    private fun parseColor(s: String, def: Int): Int =
        try { android.graphics.Color.parseColor(s) } catch (_: Exception) { def }

    private fun parseHm(s: String): Int? {
        val p = s.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    private fun applyBlocks(ctx: Context, arr: JSONArray?): Int {
        if (arr == null) return 0
        val store = Store(ctx)
        val out = ArrayList<Block>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = parseHm(o.optString("from")) ?: continue
            val e = parseHm(o.optString("to")) ?: continue
            val allow = HashSet<String>()
            o.optJSONArray("allow")?.let { a -> for (k in 0 until a.length()) allow.addAll(Apps.resolve(ctx, a.getString(k))) }
            val lock = o.optBoolean("lock", false)
            out.add(
                Block(
                    System.currentTimeMillis() + i, o.optString("name", "Block"), s, e, lock, o.optBoolean("strict", false),
                    if (lock && allow.isEmpty()) Apps.defaultAllowed(ctx) else allow
                )
            )
        }
        store.saveBlocks(store.blocks() + out)
        return out.size
    }

    private fun applyToday(ctx: Context, o: JSONObject, what: String): String {
        val store = Store(ctx)
        val cards = store.cards().toMutableList()
        var newCards = 0
        var tasks = 0
        val subs = o.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subs.length()) {
            val s = subs.getJSONObject(i)
            val name = s.optString("name").trim()
            if (name.isEmpty()) continue
            val tl = s.optJSONArray("tasks") ?: JSONArray()
            val newItems = (0 until tl.length()).map { tl.optString(it).trim() }.filter { it.isNotEmpty() }
            val idx = cards.indexOfFirst { it.type == CardType.STUDY && it.title.equals(name, true) }
            if (idx >= 0) {
                val c = cards[idx]
                val base = if (o.optBoolean("replace", false)) emptyList() else c.items
                val have = base.map { it.text.lowercase() }.toSet()
                val add = newItems.filter { it.lowercase() !in have }.map { Item(it, false) }
                cards[idx] = c.copy(items = base + add, minutes = s.optInt("minutes", c.minutes), reward = s.optInt("reward", c.reward))
                tasks += add.size
            } else {
                cards.add(
                    FocusCard(
                        System.currentTimeMillis() + i, name, CardType.STUDY,
                        parseColor(s.optString("color"), PALETTE[cards.size % 4]), s.optInt("minutes", 60),
                        newItems.map { Item(it, false) }, reward = s.optInt("reward", 10)
                    )
                )
                newCards++; tasks += newItems.size
            }
        }
        store.saveCards(cards)
        val blocks = applyBlocks(ctx, o.optJSONArray("blocks"))
        var notes = 0
        val na = o.optJSONArray("notes")
        if (na != null) {
            val db = NotesDb.get(ctx)
            for (i in 0 until na.length()) {
                val n = na.getJSONObject(i)
                val cl = n.optJSONArray("checklist")
                val labels = n.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                db.save(
                    Note(
                        type = if (cl != null) 1 else 0, title = n.optString("title"), body = n.optString("text"),
                        items = cl?.let { a -> (0 until a.length()).map { Item(a.getString(it), false) } } ?: emptyList(),
                        labels = labels
                    )
                )
                notes++
            }
        }
        return trf("Imported for %s: %d new subjects, %d tasks, %d blocks, %d notes", tr(what), newCards, tasks, blocks, notes)
    }

    private fun applyConfig(ctx: Context, o: JSONObject): String {
        val store = Store(ctx)
        val s = o.optJSONObject("settings")
        var changed = 0
        if (s != null) {
            if (s.has("ringSec")) { store.ringSec = s.getInt("ringSec"); changed++ }
            if (s.has("nagMin")) { store.nagMin = s.getInt("nagMin"); changed++ }
            if (s.has("nagRingSec")) { store.nagRingSec = s.getInt("nagRingSec"); changed++ }
            if (s.has("speak")) { store.speak = s.getBoolean("speak"); changed++ }
            if (s.has("notesAutoSort")) { store.notesAutoSort = s.getBoolean("notesAutoSort"); changed++ }
        }
        o.optJSONArray("gated")?.let { a ->
            val set = HashSet<String>()
            for (k in 0 until a.length()) set.addAll(Apps.resolve(ctx, a.getString(k)))
            store.setGated(set); changed++
        }
        val blocks = applyBlocks(ctx, o.optJSONArray("blocks"))
        if (o.has("lang")) { I18n.apply(ctx, o.getString("lang")); changed++ }
        return trf("Config applied: %d settings, %d blocks", changed, blocks)
    }

    private fun applyLang(ctx: Context, o: JSONObject): String {
        val code = o.optString("code").trim().lowercase()
        if (code.isEmpty() || o.optJSONObject("strings") == null) return tr("Language pack needs a \"code\" and \"strings\".")
        Store(ctx).saveLangPack(code, o.toString())
        I18n.apply(ctx, code)
        return trf("Language added and selected: %s", o.optString("name", code))
    }

    // ---------------------------------------------------------------- export
    fun exportToday(ctx: Context): String {
        val store = Store(ctx)
        val subs = JSONArray()
        store.cards().filter { it.type == CardType.STUDY }.forEach { c ->
            val t = JSONArray()
            c.items.filter { !it.done }.forEach { t.put(it.text) }
            subs.put(
                JSONObject().put("name", c.title).put("minutes", c.minutes).put("reward", c.reward)
                    .put("color", String.format("#%06X", 0xFFFFFF and c.color)).put("tasks", t)
            )
        }
        return JSONObject().put("focus", "today").put("v", 1).put("replace", true).put("subjects", subs).put("blocks", blocksJson(store)).toString(2)
    }

    fun exportConfig(ctx: Context): String {
        val store = Store(ctx)
        val g = JSONArray(); store.gated().forEach { g.put(it) }
        return JSONObject().put("focus", "config").put("v", 1)
            .put(
                "settings", JSONObject().put("ringSec", store.ringSec).put("nagMin", store.nagMin)
                    .put("nagRingSec", store.nagRingSec).put("speak", store.speak).put("notesAutoSort", store.notesAutoSort)
            )
            .put("gated", g).put("lang", store.lang).put("blocks", blocksJson(store)).toString(2)
    }

    private fun blocksJson(store: Store): JSONArray {
        val a = JSONArray()
        store.blocks().forEach { b ->
            val al = JSONArray(); b.allowed.forEach { al.put(it) }
            a.put(
                JSONObject().put("name", b.name).put("from", "%02d:%02d".format(b.startMin / 60, b.startMin % 60))
                    .put("to", "%02d:%02d".format(b.endMin / 60, b.endMin % 60)).put("lock", b.lock).put("strict", b.strict).put("allow", al)
            )
        }
        return a
    }

    /** Every UI string with its current translation: hand this to any AI to translate, import the result. */
    fun langTemplate(): String {
        val s = JSONObject()
        ALL_KEYS.forEach { s.put(it, AR.takeIf { _ -> I18n.code == "ar" }?.get(it) ?: it) }
        return JSONObject().put("focus", "lang").put("v", 1).put("code", "xx").put("name", "Language name").put("rtl", false)
            .put("strings", s).toString(2)
    }

    fun aiPrompt(ctx: Context): String {
        val names = Store(ctx).cards().filter { it.type == CardType.STUDY }.joinToString(", ") { it.title }
        return """You are helping me plan my study day for the Focus app. Reply with ONLY one JSON object (no markdown, no explanation).
{"focus":"today","v":1,
 "replace":false,
 "subjects":[{"name":"Math","minutes":90,"reward":10,"color":"#4285F4","tasks":["Solve equations 1-10","Review mistakes"]}],
 "blocks":[{"name":"Study","from":"16:00","to":"18:00","lock":true,"strict":false,"allow":["camera","phone","clock"]}],
 "notes":[{"title":"Idea","text":"...","checklist":["a","b"],"labels":["Parked"]}]}
Rules:
- minutes = a GENEROUS time budget for the subject (I can add more time later).
- reward = treat minutes earned for finishing the whole subject; it is split across the tasks.
- blocks: lock=true means only the allowed apps work during that time. allow accepts keywords (camera, phone, clock, messages, browser, calculator, maps) or package names. strict=true removes the emergency override. Times are 24h HH:mm.
- Every key except "focus" is optional. Reuse my existing subject names when they match.
My subjects: ${names.ifEmpty { "(none yet)" }}
What I need to do today: <write it here>"""
    }

    fun saveToDownloads(ctx: Context, name: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            true
        } catch (_: Exception) { false }
    }
}
