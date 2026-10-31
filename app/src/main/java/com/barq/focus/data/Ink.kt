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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One pen/highlighter stroke. Points are x,y pairs normalised to the page (0..1); width is a fraction of the page width. */
class InkStroke(val page: Int, val color: Int, val w: Float, val highlighter: Boolean, val pts: FloatArray, val pencil: Boolean = false)

/** Annotations live next to the app's data, one small JSON file per PDF. */
object InkStore {
    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun file(ctx: Context, key: String): File {
        val d = File(ctx.filesDir, "ink")
        d.mkdirs()
        return File(d, md5(key) + ".json")
    }

    fun load(ctx: Context, key: String): List<InkStroke> {
        val f = file(ctx, key)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val p = o.getJSONArray("pts")
                InkStroke(o.getInt("p"), o.getInt("c"), o.getDouble("w").toFloat(), o.optBoolean("h"), FloatArray(p.length()) { k -> p.getDouble(k).toFloat() }, o.optBoolean("pc"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(ctx: Context, key: String, list: List<InkStroke>) {
        try {
            val f = file(ctx, key)
            if (list.isEmpty()) { f.delete(); return }
            val a = JSONArray()
            list.forEach { s ->
                val p = JSONArray()
                s.pts.forEach { v -> p.put(Math.round(v * 10000.0) / 10000.0) }
                a.put(JSONObject().put("p", s.page).put("c", s.color).put("w", s.w.toDouble()).put("h", s.highlighter).put("pc", s.pencil).put("pts", p))
            }
            f.writeText(a.toString())
        } catch (_: Exception) {}
    }
}
