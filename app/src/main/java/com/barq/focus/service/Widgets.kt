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
import android.widget.RemoteViews
import com.barq.focus.MainActivity
import com.barq.focus.R
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

/** Home-screen widgets: the treat bank, and today at a glance. Tapping either opens Focus. */
object FocusWidgets {
    private var last = 0L

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun treat(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_treat)
        v.setTextViewText(R.id.w_label, tr("Treat bank"))
        v.setTextViewText(R.id.w_big, s.bankText())
        v.setTextViewText(R.id.w_small, tr("min"))
        v.setTextColor(R.id.w_big, accent(s))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx))
        return v
    }

    fun today(ctx: Context): RemoteViews {
        val s = Store(ctx)
        val (done, total) = try { s.progress(s.cards()) } catch (_: Exception) { 0 to 0 }
        val v = RemoteViews(ctx.packageName, R.layout.widget_today)
        v.setTextViewText(R.id.w_label, tr("Today"))
        v.setTextViewText(R.id.w_big, "$done/$total")
        v.setTextViewText(R.id.w_small, tr("tasks done"))
        v.setTextViewText(R.id.w_bank, trf("Treat bank  ·  %s min", s.bankText()))
        v.setTextViewText(R.id.w_swipes, trf("%d swiped today", s.shortsSwipes()))
        v.setTextColor(R.id.w_big, accent(s))
        v.setOnClickPendingIntent(R.id.w_root, open(ctx))
        return v
    }

    private fun accent(s: Store) = if (s.accent != 0) s.accent else 0xFF4285F4.toInt()

    /** Redraw every placed widget. Throttled so the watch loop can call it freely. */
    fun refresh(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - last < 20_000L) return
        last = now
        try {
            val m = AppWidgetManager.getInstance(ctx)
            for (id in m.getAppWidgetIds(ComponentName(ctx, TreatWidget::class.java))) m.updateAppWidget(id, treat(ctx))
            for (id in m.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))) m.updateAppWidget(id, today(ctx))
        } catch (_: Exception) {}
    }
}

class TreatWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, FocusWidgets.treat(ctx))
    }
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, FocusWidgets.today(ctx))
    }
}
