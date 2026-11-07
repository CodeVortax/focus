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

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar

/** One stretch of an app being on screen. */
class UsageSpan(val pkg: String, val t0: Long, val t1: Long)

/**
 * Whole-phone screen time (every app, not just the tracked ones), rebuilt from the system's usage
 * events: foreground/background pairs per app, closed when the screen turns off. Needs usage access.
 */
object SysUsage {
    private const val FG = 1          // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val BG = 2          // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val SCREEN_ON = 15  // SCREEN_INTERACTIVE
    private const val SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE
    private const val KEYGUARD_SHOWN = 17   // lock screen up: the phone is not being used
    private const val KEYGUARD_HIDDEN = 18  // unlocked

    private fun skipped(ctx: Context): Set<String> {
        val s = hashSetOf("android", "com.android.systemui")
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.queryIntentActivities(i, 0).forEach { s += it.activityInfo.packageName }
        } catch (_: Exception) {}
        return s
    }

    fun spans(ctx: Context, from: Long, to: Long): List<UsageSpan> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val evs = try { usm.queryEvents(from, to) } catch (_: Exception) { null } ?: return emptyList()
        val skip = skipped(ctx)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val raw = ArrayList<UsageSpan>()
        // An app only counts while it is in front AND the screen is on and unlocked: a wake-up activity while the
        // screen is off (an alarm, a call screen on the lock screen) or behind the lock screen is not "using the phone".
        var screenOn = true
        var locked = false
        fun usable() = screenOn && !locked
        fun closeAll(at: Long) {
            open.forEach { (p, s) -> if (at > s) raw += UsageSpan(p, s, at) }
            open.clear()
        }
        // the app that was last in front when the screen woke up or unlocked starts counting again from that moment
        var lastFg: String? = null
        while (evs.hasNextEvent()) {
            evs.getNextEvent(e)
            when (e.eventType) {
                FG -> {
                    val p = e.packageName
                    if (p !in skip) { lastFg = p; if (usable() && p !in open) open[p] = e.timeStamp }
                }
                BG -> {
                    if (e.packageName == lastFg) lastFg = null
                    open.remove(e.packageName)?.let { s -> if (e.timeStamp > s) raw += UsageSpan(e.packageName, s, e.timeStamp) }
                }
                SCREEN_OFF -> { screenOn = false; closeAll(e.timeStamp) }
                SCREEN_ON -> { screenOn = true; if (usable()) lastFg?.let { open[it] = e.timeStamp } }
                KEYGUARD_SHOWN -> { locked = true; closeAll(e.timeStamp) }
                KEYGUARD_HIDDEN -> { locked = false; if (usable()) lastFg?.let { open[it] = e.timeStamp } }
            }
        }
        open.forEach { (p, s) -> if (to > s) raw += UsageSpan(p, s, to) }
        // activity A -> activity B inside one app is one visit, not two
        val out = ArrayList<UsageSpan>()
        for ((_, l) in raw.groupBy { it.pkg }) {
            var cur: UsageSpan? = null
            for (x in l.sortedBy { it.t0 }) {
                val c = cur
                if (c != null && x.t0 - c.t1 <= 2000L) cur = UsageSpan(c.pkg, c.t0, maxOf(c.t1, x.t1))
                else { if (c != null) out += c; cur = x }
            }
            cur?.let { out += it }
        }
        return out.sortedBy { it.t0 }
    }

    /** Midnight [offset] days ago. */
    fun dayStart(offset: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -offset)
        return c.timeInMillis
    }

    private val labels = HashMap<String, String>()

    fun label(ctx: Context, pkg: String): String {
        Config.TRACKED[pkg]?.let { return it }
        return labels.getOrPut(pkg) {
            try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun icon(ctx: Context, pkg: String): Bitmap? = try {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
    } catch (_: Exception) { null }
}
