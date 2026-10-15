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

import org.json.JSONArray
import org.json.JSONObject

/** One run of formatting. size: 0 normal, 1/2/3 = H1/H2/H3. color 0 = default. */
data class Fmt(
    val size: Int = 0, val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strike: Boolean = false, val color: Int = 0
)

data class Span(val s: Int, val e: Int, val f: Fmt)

/** Formatting is kept per character while editing (simple and hard to break), stored as runs. */
object Rich {
    fun toArray(len: Int, spans: List<Span>): Array<Fmt> {
        val a = Array(len) { Fmt() }
        spans.forEach { sp -> for (i in sp.s.coerceAtLeast(0) until sp.e.coerceAtMost(len)) a[i] = sp.f }
        return a
    }

    fun toRuns(a: Array<Fmt>): List<Span> {
        val out = ArrayList<Span>()
        val plain = Fmt()
        var i = 0
        while (i < a.size) {
            var j = i + 1
            while (j < a.size && a[j] == a[i]) j++
            if (a[i] != plain) out.add(Span(i, j, a[i]))
            i = j
        }
        return out
    }

    /** Keep styles attached to the right characters when the text changes. */
    fun adjust(old: String, new: String, spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return spans
        val oa = toArray(old.length, spans)
        var p = 0
        val maxP = minOf(old.length, new.length)
        while (p < maxP && old[p] == new[p]) p++
        var s = 0
        val maxS = minOf(old.length, new.length) - p
        while (s < maxS && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val removed = old.length - p - s
        val inserted = new.length - p - s
        // typing inside a styled run keeps its style
        val fill = if (removed == 0 && p > 0 && p < oa.size && oa[p - 1] == oa[p]) oa[p - 1] else Fmt()
        val out = ArrayList<Fmt>(new.length)
        for (i in 0 until p) out.add(oa[i])
        repeat(inserted) { out.add(fill) }
        for (i in (old.length - s) until old.length) out.add(oa[i])
        return toRuns(out.toTypedArray())
    }

    fun apply(len: Int, spans: List<Span>, a: Int, b: Int, f: (Fmt) -> Fmt): List<Span> {
        val arr = toArray(len, spans)
        for (i in a.coerceAtLeast(0) until b.coerceAtMost(len)) arr[i] = f(arr[i])
        return toRuns(arr)
    }

    fun toJson(spans: List<Span>): JSONArray {
        val a = JSONArray()
        spans.forEach {
            a.put(
                JSONObject().put("s", it.s).put("e", it.e).put("z", it.f.size).put("b", it.f.bold).put("i", it.f.italic)
                    .put("u", it.f.underline).put("x", it.f.strike).put("c", it.f.color)
            )
        }
        return a
    }

    fun fromJson(a: JSONArray?): List<Span> {
        if (a == null) return emptyList()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Span(
                o.getInt("s"), o.getInt("e"),
                Fmt(o.optInt("z"), o.optBoolean("b"), o.optBoolean("i"), o.optBoolean("u"), o.optBoolean("x"), o.optInt("c"))
            )
        }
    }
}
