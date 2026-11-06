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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text pictures of today, for an AI chat. Built only when the person taps a button (or Study Mate asks and the
 * person confirms); nothing here leaves the phone by itself. English labels on purpose: the AI answers in the person's language.
 */
object DayExport {
    private const val MAX_TASKS = 40

    private fun clip(s: String, n: Int): String = s.replace('\n', ' ').trim().let { if (it.length > n) it.take(n - 1) + "…" else it }

    private fun dayLabel(): String = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date())

    /** Today's tasks, study time, usage and exams. */
    fun build(ctx: Context, store: Store): String = try { day(ctx, store) } catch (e: Exception) { "Could not read today's data." }

    /** Today's usage only (the Usage screen). */
    fun usage(ctx: Context, store: Store): String = try {
        val sb = StringBuilder("Usage today in Focus (${dayLabel()})\n")
        usageLines(ctx, store, sb)
        sb.toString().trimEnd()
    } catch (e: Exception) { "Could not read today's usage." }

    /** One subject: its tasks and exam. */
    fun subject(store: Store, c: FocusCard): String = try {
        val sb = StringBuilder("Subject: ${clip(c.title, 60)} (${dayLabel()})\n")
        examLine(c)?.let { sb.append(it).append('\n') }
        val mins = (store.spent(c.id) / 60_000).toLong()
        if (mins > 0) sb.append("Study time today: ").append(mins).append(" min\n")
        val day = c.dayItems
        if (day.isNotEmpty()) {
            sb.append("Today's tasks:\n")
            day.take(60).forEach { sb.append(if (it.done) "- [done] " else "- [ ] ").append(clip(it.text, 80)).append('\n') }
        }
        val week = c.items.filter { it.weekly }
        if (week.isNotEmpty()) {
            sb.append("This week's tasks:\n")
            week.take(30).forEach { sb.append(if (it.done) "- [done] " else "- [ ] ").append(clip(it.text, 80)).append('\n') }
        }
        sb.toString().trimEnd()
    } catch (e: Exception) { "Could not read this subject." }

    private fun examLine(c: FocusCard): String? {
        if (c.examDate <= 0L) return null
        val left = c.examDate - System.currentTimeMillis()
        if (left <= 0L) return null
        val days = Math.ceil(left / 86_400_000.0).toInt()
        return "Exam: ${clip(c.title, 40)} in $days day" + (if (days == 1) "" else "s")
    }

    private fun usageLines(ctx: Context, store: Store, sb: StringBuilder) {
        val db = Db.get(ctx)
        val since = startOfDay()
        val shortsMin = db.totalsSince("shorts", since).values.sum() / 60_000
        sb.append("Shorts/Reels: ").append(store.shortsSwipes()).append(" swiped, ").append(shortsMin).append(" min\n")
        val apps = db.totalsSince("app", since).entries
            .map { (Config.TRACKED[it.key] ?: it.key.substringAfterLast('.')) to it.value / 60_000 }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        if (apps.isNotEmpty()) sb.append("Time in apps: ").append(apps.joinToString(", ") { "${it.first} ${it.second} min" }).append('\n')
        sb.append("Treat bank: ").append(store.bankText()).append(" min\n")
    }

    private fun day(ctx: Context, store: Store): String {
        store.rollover()
        val cards = store.cards()
        val (done, total) = store.progress(cards)
        val sb = StringBuilder("Today in Focus (${dayLabel()})\n")
        sb.append("Tasks: ").append(done).append(" done, ").append(total - done).append(" left (of ").append(total).append(")\n")
        val (weekLeft, weekAll) = Weekly.progress(cards)
        if (weekAll > 0) sb.append("This week: ").append(weekLeft).append(" of ").append(weekAll).append(" tasks left\n")
        var shown = 0
        for (c in cards) {
            if (c.type != CardType.STUDY || c.dayItems.isEmpty()) continue
            sb.append('\n').append(clip(c.title, 40)).append(":\n")
            for (i in c.dayItems) {
                if (shown >= MAX_TASKS) { sb.append("- (more not shown)\n"); break }
                sb.append(if (i.done) "- [done] " else "- [ ] ").append(clip(i.text, 80)).append('\n')
                shown++
            }
        }
        val spent = cards.filter { it.type == CardType.STUDY }
            .mapNotNull { c -> (store.spent(c.id) / 60_000).toLong().takeIf { it > 0 }?.let { c.title to it } }
        if (spent.isNotEmpty()) sb.append("\nStudy time today: ").append(spent.joinToString(", ") { "${clip(it.first, 30)} ${it.second} min" }).append('\n')
        val exams = cards.mapNotNull { examLine(it) }
        if (exams.isNotEmpty()) sb.append(exams.joinToString("; ")).append('\n')
        sb.append('\n')
        usageLines(ctx, store, sb)
        val streak = Game.workStreak(store)
        if (streak > 0) sb.append("Work streak: ").append(streak).append(" days\n")
        return sb.toString().trimEnd()
    }
}
