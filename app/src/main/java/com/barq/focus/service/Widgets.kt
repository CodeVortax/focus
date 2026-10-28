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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.barq.focus.MainActivity
import com.barq.focus.R
import com.barq.focus.data.CardType
import com.barq.focus.data.Db
import com.barq.focus.data.Game
import com.barq.focus.data.Store
import com.barq.focus.data.dayItems
import com.barq.focus.data.startOfDay
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/** Home-screen widgets: treat bank, streak, Shorts, and today's subjects. Tapping any opens Focus. */
object FocusWidgets {
    private var last = 0L

    private val BLOCKS = listOf(
        intArrayOf(R.id.b1, R.id.b1_bg, R.id.b1_name, R.id.b1_count, R.id.b1_bar),
        intArrayOf(R.id.b2, R.id.b2_bg, R.id.b2_name, R.id.b2_count, R.id.b2_bar),
        intArrayOf(R.id.b3, R.id.b3_bg, R.id.b3_name, R.id.b3_count, R.id.b3_bar),
        intArrayOf(R.id.b4, R.id.b4_bg, R.id.b4_name, R.id.b4_count, R.id.b4_bar),
    )

    private fun open(ctx: Context, go: String? = null) = PendingIntent.getActivity(
        ctx, if (go == null) 0 else 200 + go.hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).also { if (go != null) it.putExtra("go", go) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun accent(s: Store) = if (s.accent != 0) s.accent else 0xFF4285F4.toInt()

    private fun days(n: Int) = trf(if (n == 1) "%d day" else "%d days", n)

    fun treat(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_treat)
        v.setTextViewText(R.id.w_label, tr("Treat bank"))
        v.setTextViewText(R.id.w_big, s.bankText())
        v.setTextViewText(R.id.w_small, tr("min"))
        v.setTextViewText(R.id.w_line, "\uD83D\uDD25 " + days(Game.workStreak(s)))
        v.setTextColor(R.id.w_big, accent(s))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, "home"))
        return v
    }

    /** The running subject timer: a real countdown (the system ticks it, no refresh needed). */
    fun timer(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_timer)
        val card = s.cards().firstOrNull { it.id == s.activeCardId }
        v.setTextViewText(R.id.w_label, tr("Timer"))
        if (card == null) {
            v.setViewVisibility(R.id.w_chrono, android.view.View.GONE)
            v.setViewVisibility(R.id.w_big, android.view.View.VISIBLE)
            v.setTextViewText(R.id.w_big, "\u23F1")
            v.setTextViewText(R.id.w_small, tr("No timer running"))
            v.setTextViewText(R.id.w_line, tr("Tap to pick a subject"))
            v.setOnClickPendingIntent(R.id.w_root, open(ctx))
            return v
        }
        val left = if (s.activePaused) s.activeRemaining else s.activeEnd - System.currentTimeMillis()
        v.setTextViewText(R.id.w_label, card.title)
        v.setViewVisibility(R.id.w_big, android.view.View.GONE)
        v.setViewVisibility(R.id.w_chrono, android.view.View.VISIBLE)
        v.setChronometerCountDown(R.id.w_chrono, true)
        v.setChronometer(R.id.w_chrono, android.os.SystemClock.elapsedRealtime() + left, null, !s.activePaused)
        v.setTextColor(R.id.w_chrono, card.color)
        v.setTextViewText(R.id.w_small, tr(if (s.activePaused) "Paused" else if (left < 0) "Over budget" else "left"))
        v.setTextViewText(R.id.w_line, "\uD83C\uDF7F " + trf("%s min", s.bankText()))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, "card:${card.id}"))
        return v
    }

    fun piggy(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_treat)
        val bal = com.barq.focus.data.Vault.balanceSec(s)
        val next = com.barq.focus.data.Vault.lots(s).minByOrNull { it.release }
        v.setTextViewText(R.id.w_label, tr("Piggy bank"))
        v.setTextViewText(R.id.w_big, com.barq.focus.data.minText(bal))
        v.setTextViewText(R.id.w_small, tr("min saved"))
        v.setTextViewText(R.id.w_line, if (next == null) tr("Nothing saved yet") else trf("Pays out in %d d", com.barq.focus.data.Vault.daysLeft(next)))
        v.setTextColor(R.id.w_big, accent(s))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, "piggy"))
        return v
    }

    fun streak(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
        v.setTextViewText(R.id.w_label, tr("Work streak"))
        v.setTextViewText(R.id.w_big, "\uD83D\uDD25 " + Game.workStreak(s))
        v.setTextViewText(R.id.w_small, tr("work-day streak"))
        v.setTextViewText(R.id.w_line, tr("Light Shorts") + ": " + days(Game.lightStreak(s)))
        v.setTextColor(R.id.w_big, accent(s))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, "trophies"))
        return v
    }

    fun shorts(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val mins = try { Db.get(ctx).totalsSince("shorts", startOfDay()).values.sum() / 60_000 } catch (_: Exception) { 0L }
        val v = RemoteViews(ctx.packageName, R.layout.widget_shorts)
        v.setTextViewText(R.id.w_label, tr("Shorts / Reels"))
        v.setTextViewText(R.id.w_big, s.shortsSwipes().toString())
        v.setTextViewText(R.id.w_small, tr("swiped today"))
        v.setTextViewText(R.id.w_line, "$mins min")
        v.setTextColor(R.id.w_big, if (s.shortsSwipes() <= 50) accent(s) else 0xFFEA4335.toInt())
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, "log"))
        return v
    }

    /** A thin rounded progress bar as a bitmap, so it can use each subject's own colour. */
    private fun bar(frac: Float, color: Int): Bitmap {
        val w = 240; val h = 10
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color; p.alpha = 70
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        if (frac > 0f) {
            p.color = color; p.alpha = 255
            c.drawRoundRect(RectF(0f, 0f, maxOf(h.toFloat(), w * frac.coerceAtMost(1f)), h.toFloat()), h / 2f, h / 2f, p)
        }
        return b
    }

    fun today(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val cards = try { s.cards() } catch (_: Exception) { emptyList() }
        val subjects = cards.filter { it.type == CardType.STUDY && it.dayItems.isNotEmpty() }
            .sortedBy { c -> if (c.dayItems.all { it.done }) 1 else 0 }
            .take(BLOCKS.size)
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        val (done, total) = try { s.progress(cards) } catch (_: Exception) { 0 to 0 }
        v.setTextViewText(R.id.w_label, tr("Today") + "  ·  " + trf("%d of %d tasks", done, total))
        BLOCKS.forEachIndexed { i, ids ->
            val c = subjects.getOrNull(i)
            if (c == null) { v.setViewVisibility(ids[0], android.view.View.GONE); return@forEachIndexed }
            val t = c.dayItems.size
            val d = c.dayItems.count { it.done }
            v.setViewVisibility(ids[0], android.view.View.VISIBLE)
            v.setOnClickPendingIntent(ids[0], open(ctx, "card:${c.id}"))
            v.setInt(ids[1], "setColorFilter", c.color)
            v.setInt(ids[1], "setImageAlpha", 60)
            v.setTextViewText(ids[2], c.title)
            v.setTextViewText(ids[3], if (d == t) "\u2713" else "$d/$t")
            v.setTextColor(ids[3], c.color)
            v.setImageViewBitmap(ids[4], bar(d / t.toFloat(), c.color))
        }
        v.setViewVisibility(R.id.w_empty, if (subjects.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
        v.setTextViewText(R.id.w_empty, tr("Add a subject with tasks in Focus"))
        v.setTextViewText(
            R.id.w_line,
            "\uD83C\uDF7F " + trf("%s min", s.bankText()) + "   \uD83D\uDD25 " + days(Game.workStreak(s)) +
                "   \uD83D\uDCF1 " + trf("%d swiped", s.shortsSwipes())
        )
        val nowMs = System.currentTimeMillis()
        cards.filter { it.examDate > nowMs }.minByOrNull { it.examDate }?.let { c ->
            val d = ((c.examDate - nowMs) / 86_400_000L).toInt() + 1
            v.setTextViewText(R.id.w_line, "\uD83D\uDCC5 ${c.title} " + trf("%d d", d) + "   " + "\uD83C\uDF7F " + trf("%s min", s.bankText()))
        }
        v.setOnClickPendingIntent(R.id.w_root, open(ctx))
        return v
    }

    /** Redraw every placed widget. Throttled so the watch loop can call it freely. */
    fun refresh(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - last < 20_000L) return
        last = now
        try {
            val m = AppWidgetManager.getInstance(ctx)
            fun each(cls: Class<*>, make: (Context) -> RemoteViews) {
                for (id in m.getAppWidgetIds(ComponentName(ctx, cls))) m.updateAppWidget(id, make(ctx))
            }
            each(TreatWidget::class.java) { treat(it) }
            each(StreakWidget::class.java) { streak(it) }
            each(ShortsWidget::class.java) { shorts(it) }
            each(TodayWidget::class.java) { today(it) }
            each(TimerWidget::class.java) { timer(it) }
            each(PiggyWidget::class.java) { piggy(it) }
        } catch (_: Exception) {}
    }
}

private fun update(ctx: Context, m: AppWidgetManager, ids: IntArray, make: (Context) -> RemoteViews) {
    for (id in ids) try { m.updateAppWidget(id, make(ctx)) } catch (_: Exception) {}
}

class TreatWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::treat)
}

class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::streak)
}

class ShortsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::shorts)
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::today)
}

class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::timer)
}

class PiggyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = update(ctx, m, ids, FocusWidgets::piggy)
}
