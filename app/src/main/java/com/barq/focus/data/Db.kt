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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Row(val id: Long, val kind: String, val pkg: String, val t0: Long, val t1: Long?, val reason: String?, val n: Int = 0)

/** kind = "app" (foreground time) or "music" (media session playing). */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "focus.db", null, 3) {

    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE usage(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, pkg TEXT, t0 INTEGER, t1 INTEGER, reason TEXT, n INTEGER DEFAULT 0)")
    }
    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {
        try { d.execSQL("ALTER TABLE usage ADD COLUMN n INTEGER DEFAULT 0") } catch (_: Exception) {}
    }
    // A newer build may have left a higher version on disk: keep the data, just make sure our column exists.
    override fun onDowngrade(d: SQLiteDatabase, o: Int, n: Int) {
        try { d.execSQL("ALTER TABLE usage ADD COLUMN n INTEGER DEFAULT 0") } catch (_: Exception) {}
    }

    fun open(kind: String, pkg: String, t0: Long): Long =
        writableDatabase.compileStatement("INSERT INTO usage(kind,pkg,t0) VALUES(?,?,?)").use {
            it.bindString(1, kind); it.bindString(2, pkg); it.bindLong(3, t0); it.executeInsert()
        }

    fun close(id: Long, t1: Long) {
        writableDatabase.execSQL("UPDATE usage SET t1=? WHERE id=?", arrayOf<Any>(t1, id))
    }

    fun setReason(id: Long, r: String) {
        writableDatabase.execSQL(
            "UPDATE usage SET reason = CASE WHEN reason IS NULL THEN ? ELSE reason || ' > ' || ? END WHERE id=?",
            arrayOf<Any>(r, r, id)
        )
    }

    /** Rows left open by a killed service get a zero-length end. */
    /** Rows left open by a killed service end at the last heartbeat (never before they began). */
    fun fixOpen(beat: Long) {
        writableDatabase.execSQL("UPDATE usage SET t1=CASE WHEN ?>t0 THEN ? ELSE t0 END WHERE t1 IS NULL", arrayOf<Any>(beat, beat))
    }

    fun recent(limit: Int): List<Row> {
        val out = ArrayList<Row>()
        readableDatabase.rawQuery("SELECT id,kind,pkg,t0,t1,reason,COALESCE(n,0) FROM usage ORDER BY t0 DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(1); val t0 = c.getLong(3)
                var t1: Long? = if (c.isNull(4)) null else c.getLong(4)
                // a row a dead service left open must not keep growing for ever
                if (t1 == null && System.currentTimeMillis() - t0 > staleCap(k)) t1 = t0 + staleCap(k)
                out.add(Row(c.getLong(0), k, c.getString(2), t0, t1, if (c.isNull(5)) null else c.getString(5), c.getInt(6)))
            }
        }
        return out
    }

    /** Newest row of this kind+app: used to glue a new burst of use onto the previous one. */
    fun lastRow(kind: String, pkg: String): Row? =
        readableDatabase.rawQuery(
            "SELECT id,kind,pkg,t0,t1,reason,COALESCE(n,0) FROM usage WHERE kind=? AND pkg=? ORDER BY t0 DESC LIMIT 1",
            arrayOf(kind, pkg)
        ).use { c ->
            if (c.moveToFirst()) Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                if (c.isNull(4)) null else c.getLong(4), if (c.isNull(5)) null else c.getString(5), c.getInt(6))
            else null
        }

    fun reopen(id: Long) { writableDatabase.execSQL("UPDATE usage SET t1=NULL WHERE id=?", arrayOf<Any>(id)) }

    fun addCount(id: Long, d: Int) {
        writableDatabase.execSQL("UPDATE usage SET n=COALESCE(n,0)+? WHERE id=?", arrayOf<Any>(d, id))
    }

    fun countReasons(vararg like: String): Int {
        var total = 0
        like.forEach { l ->
            readableDatabase.rawQuery("SELECT COUNT(*) FROM usage WHERE reason LIKE ?", arrayOf("%$l%")).use { c ->
                if (c.moveToFirst()) total += c.getInt(0)
            }
        }
        return total
    }

    /**
     * Time per package since [since]. Rows are clipped to [since, now] (a visit over midnight counts only its part
     * of today), and a row that is still open counts at most 3 hours: a service that died leaves rows open forever.
     */
    fun totalsSince(kind: String, since: Long): Map<String, Long> {
        val now = System.currentTimeMillis()
        val out = LinkedHashMap<String, Long>()
        readableDatabase.rawQuery(
            "SELECT pkg, t0, t1 FROM usage WHERE kind=? AND (t1 IS NULL OR t1>$since) AND t0<$now",
            arrayOf(kind)
        ).use { c ->
            while (c.moveToNext()) {
                val t0 = c.getLong(1)
                val t1 = if (c.isNull(2)) minOf(now, t0 + staleCap(kind)) else c.getLong(2)
                val d = minOf(t1, now) - maxOf(t0, since)
                if (d > 0) out[c.getString(0)] = (out[c.getString(0)] ?: 0L) + d
            }
        }
        return out.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    private fun staleCap(kind: String) = if (kind == "music") 12 * 3_600_000L else 3 * 3_600_000L

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) { inst ?: Db(ctx).also { inst = it } }
    }
}
