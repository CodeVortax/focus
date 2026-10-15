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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Note(
    val id: Long = 0,
    val type: Int = 0,                    // 0 = text, 1 = checklist
    val title: String = "",
    val body: String = "",
    val items: List<Item> = emptyList(),
    val color: Int = 0,                   // 0 = none, else ARGB
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long = 0L,             // 0 = not in trash
    val labels: List<String> = emptyList(),
    val spans: List<Span> = emptyList(),   // rich-text runs over [body]
    val voices: List<Voice> = emptyList(), // recorded voice notes
    val created: Long = System.currentTimeMillis(),
    val modified: Long = System.currentTimeMillis(),
) {
    val isEmpty get() = title.isBlank() && body.isBlank() && items.all { it.text.isBlank() } && voices.isEmpty()
}

/** Plain SQLite (no Room/KSP needed) so it builds the same way in Termux and GitHub Actions. */
class NotesDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "notes.db", null, 4) {

    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL(
            "CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER, title TEXT, body TEXT, items TEXT, " +
                "color INTEGER, pinned INTEGER, archived INTEGER, trashed INTEGER, labels TEXT, created INTEGER, modified INTEGER, " +
                "spans TEXT, voices TEXT)"
        )
    }
    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {
        // each is safe to repeat: the column may already exist from an earlier fix
        try { d.execSQL("ALTER TABLE notes ADD COLUMN spans TEXT") } catch (_: Exception) {}
        try { d.execSQL("ALTER TABLE notes ADD COLUMN voices TEXT") } catch (_: Exception) {}
    }
    // A newer build may have left a higher version on disk: keep the data, just make sure our columns exist.
    override fun onDowngrade(d: SQLiteDatabase, o: Int, n: Int) {
        try { d.execSQL("ALTER TABLE notes ADD COLUMN spans TEXT") } catch (_: Exception) {}
        try { d.execSQL("ALTER TABLE notes ADD COLUMN voices TEXT") } catch (_: Exception) {}
    }

    private fun itemsToJson(l: List<Item>): JSONArray {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.text).put("d", it.done)) }
        return a
    }
    private fun itemsFromJson(a: JSONArray): List<Item> =
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Item(o.getString("t"), o.getBoolean("d")) } }
    private fun labelsToJson(l: List<String>): JSONArray {
        val a = JSONArray()
        l.forEach { a.put(it) }
        return a
    }
    private fun labelsFromJson(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }

    private fun values(n: Note) = ContentValues().apply {
        put("type", n.type); put("title", n.title); put("body", n.body)
        put("items", itemsToJson(n.items).toString())
        put("color", n.color); put("pinned", if (n.pinned) 1 else 0); put("archived", if (n.archived) 1 else 0)
        put("trashed", n.trashedAt); put("labels", labelsToJson(n.labels).toString())
        put("spans", Rich.toJson(n.spans).toString())
        put("voices", Voices.toJson(n.voices).toString())
        put("created", n.created); put("modified", n.modified)
    }

    private fun read(c: Cursor): Note {
        fun i(n: String) = c.getColumnIndexOrThrow(n)
        return Note(
            id = c.getLong(i("id")), type = c.getInt(i("type")),
            title = c.getString(i("title")) ?: "", body = c.getString(i("body")) ?: "",
            items = itemsFromJson(JSONArray(c.getString(i("items")) ?: "[]")),
            color = c.getInt(i("color")), pinned = c.getInt(i("pinned")) == 1, archived = c.getInt(i("archived")) == 1,
            trashedAt = c.getLong(i("trashed")),
            labels = labelsFromJson(JSONArray(c.getString(i("labels")) ?: "[]")),
            spans = try { Rich.fromJson(JSONArray(c.getString(i("spans")) ?: "[]")) } catch (_: Exception) { emptyList() },
            voices = try { Voices.fromJson(JSONArray(c.getString(i("voices")) ?: "[]")) } catch (_: Exception) { emptyList() },
            created = c.getLong(i("created")), modified = c.getLong(i("modified")),
        )
    }

    fun save(n: Note): Long {
        val d = writableDatabase
        return if (n.id == 0L) d.insert("notes", null, values(n))
        else { d.update("notes", values(n), "id=?", arrayOf(n.id.toString())); n.id }
    }

    fun get(id: Long): Note? =
        readableDatabase.rawQuery("SELECT * FROM notes WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) read(c) else null
        }

    fun all(): List<Note> =
        readableDatabase.rawQuery("SELECT * FROM notes", null).use { c ->
            val out = ArrayList<Note>()
            while (c.moveToNext()) out.add(read(c))
            out
        }

    fun delete(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())) }

    /** Trash is emptied automatically after 7 days. */
    fun purge(olderThan: Long) {
        writableDatabase.delete("notes", "trashed>0 AND trashed<?", arrayOf(olderThan.toString()))
    }

    // ---- backup (JSON text: share it anywhere, import it back from the clipboard) ----
    fun exportJson(): String {
        val a = JSONArray()
        all().forEach { n ->
            a.put(
                JSONObject().put("type", n.type).put("title", n.title).put("body", n.body)
                    .put("items", itemsToJson(n.items)).put("color", n.color).put("pinned", n.pinned)
                    .put("archived", n.archived).put("trashed", n.trashedAt).put("labels", labelsToJson(n.labels)).put("spans", Rich.toJson(n.spans))
                    .put("created", n.created).put("modified", n.modified)
            )
        }
        return JSONObject().put("focus", "notes").put("app", "focus-notes").put("v", 1).put("notes", a).toString(2)
    }

    fun importJson(s: String): Int {
        val a = JSONObject(s).getJSONArray("notes")
        var count = 0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            save(
                Note(
                    type = o.optInt("type", 0), title = o.optString("title", ""), body = o.optString("body", ""),
                    items = itemsFromJson(o.optJSONArray("items") ?: JSONArray()),
                    color = o.optInt("color", 0), pinned = o.optBoolean("pinned", false),
                    archived = o.optBoolean("archived", false), trashedAt = o.optLong("trashed", 0L),
                    labels = labelsFromJson(o.optJSONArray("labels") ?: JSONArray()),
                    spans = Rich.fromJson(o.optJSONArray("spans")),
                    created = o.optLong("created", System.currentTimeMillis()),
                    modified = o.optLong("modified", System.currentTimeMillis()),
                )
            )
            count++
        }
        return count
    }

    companion object {
        @Volatile private var inst: NotesDb? = null
        fun get(ctx: Context): NotesDb = inst ?: synchronized(this) { inst ?: NotesDb(ctx).also { inst = it } }
    }
}
