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
import kotlin.math.sqrt

data class Badge(val id: String, val emoji: String, val name: String, val desc: String, val shame: Boolean = false)

data class Quest(val text: String, val done: Boolean)

/** Streaks, XP, levels and badges. Everything is derived from the per-day numbers Focus already keeps. */
object Game {
    val BADGES = listOf(
        Badge("first", "🩸", K("First Blood"), K("Tick off your first task. Every legend starts somewhere.")),
        Badge("wiper", "🧹", K("Plate Wiper"), K("Clear every task in a single day.")),
        Badge("slayer", "🗡️", K("Subject Slayer"), K("Earn a full subject's reward in one day.")),
        Badge("speed", "⚡", K("Speedrunner"), K("Clear the whole plate before 18:00.")),
        Badge("week", "🔥", K("Week Warrior"), K("7 work days in a row.")),
        Badge("two", "📨", K("Two Weeks Notice"), K("14 work days in a row. Procrastination just quit.")),
        Badge("month", "🧘", K("Monk Mode"), K("30 work days in a row.")),
        Badge("diet50", "🥗", K("Shorts Diet"), K("A full day with 50 Shorts or fewer.")),
        Badge("diet20", "🎛️", K("Thumb Control"), K("A full day with 20 Shorts or fewer.")),
        Badge("diet10", "☯️", K("Zen Thumb"), K("A full day with 10 Shorts or fewer.")),
        Badge("free", "🕊️", K("Free Shorts Day"), K("A full day with zero Shorts. Your thumb is free.")),
        Badge("detox", "🛡️", K("Detox Week"), K("7 days in a row with 50 Shorts or fewer.")),
        Badge("retire", "🏖️", K("Thumb Retirement"), K("7 days in a row with zero Shorts. Legendary.")),
        Badge("door", "🚪", K("Door Slammer"), K("Say Leave or Back out 10 times.")),
        Badge("scribe", "✍️", K("Scribe"), K("Keep 10 notes.")),
        Badge("dumper", "🧠", K("Brain Dumper"), K("Park 5 thoughts instead of chasing them.")),
        Badge("worm", "📚", K("Bookworm"), K("Add 3 PDFs or read 100 pages.")),
        Badge("poly", "🌍", K("Polyglot"), K("Use Focus in another language.")),
        Badge("dare", "🎲", K("Dare Taker"), K("Accept 5 challenges from the Challenge me button.")),
        Badge("treat", "🍿", K("Treat Yourself"), K("Earn 40 minutes of treats in one day.")),
        // hall of shame: hidden until you earn them
        Badge("goat_neglect", "🐐", K("Goat Neglecter"), K("3 days in a row with tasks on the plate and none done. Come back before it's too late."), shame = true),
        Badge("zombie", "🧟", K("Shorts Zombie"), K("300 Shorts or more in a single day."), shame = true),
        Badge("midnight", "🌙", K("Midnight Scroller"), K("Shorts between 1 and 5 in the morning."), shame = true),
        Badge("marathon", "🏁", K("Shorts Marathon"), K("One single Shorts visit of an hour or more."), shame = true),
        Badge("overtime", "👑", K("Overtime King"), K("Let a timer run 30 minutes over its budget."), shame = true),
        Badge("abuser", "🚨", K("Emergency Abuser"), K("Use the locked-block emergency button 5 times."), shame = true),
        Badge("honesty", "💬", K("Honesty Award"), K("Press \"I'm distracted\" 20 times."), shame = true),
        Badge("hoarder", "🍽️", K("Plate Hoarder"), K("Let 30 or more unfinished tasks pile up."), shame = true),
        Badge("ghosted", "👻", K("Ghosted"), K("Disappear from Focus for 3 days in a row."), shame = true),
        Badge("funeral", "⚰️", K("Streak Funeral"), K("Lose a work streak of 7 days or more."), shame = true),
        Badge("comeback", "🔥", K("Comeback Kid"), K("Clear the whole plate after earning a shame badge.")),
    )

    private val TITLES = listOf(
        "🥔" to K("Couch Potato"), "📎" to K("Distracted Intern"), "🎯" to K("Focus Apprentice"),
        "👺" to K("Task Goblin"), "🧹" to K("Plate Wiper"), "🛡️" to K("Shorts Survivor"),
        "🧙" to K("Deep Work Wizard"), "🧘" to K("Thumb Monk"), "⚔️" to K("Study Samurai"),
        "👑" to K("Final Boss of Focus"),
    )

    private const val LIGHT = 50

    fun workDay(d: DayStat) = d.allDone || d.partial

    /** Consecutive days (ending today, or yesterday if today isn't done yet) that pass [ok]. */
    fun streak(store: Store, includeToday: Boolean, todayBreaks: Boolean = false, ok: (DayStat) -> Boolean): Int {
        var n = 0
        var off: Int
        val t = store.dayStat(0)
        if (includeToday && t.active && ok(t)) { n = 1; off = 1 }
        else if (includeToday && todayBreaks && t.active) return 0     // e.g. you already blew today's Shorts limit
        else off = 1
        while (off < 800) {
            val d = store.dayStat(off)
            if (d.active && ok(d)) { n++; off++ } else break
        }
        return n
    }

    fun workStreak(store: Store) = streak(store, true) { workDay(it) }
    fun lightStreak(store: Store) = streak(store, true, true) { it.swipes <= LIGHT }

    fun xp(store: Store): Int {
        var xp = 0
        for (off in 0..365) {
            val d = store.dayStat(off)
            if (!d.active) continue
            xp += d.earnedSec / 6
            if (off >= 1) xp += when {
                d.swipes == 0 -> 250
                d.swipes <= 10 -> 150
                d.swipes <= 20 -> 100
                d.swipes <= 50 -> 50
                else -> 0
            }
        }
        return xp
    }

    fun level(xp: Int) = sqrt(xp / 60.0).toInt() + 1
    fun xpForLevel(level: Int) = 60 * (level - 1) * (level - 1)
    fun titleOf(level: Int) = TITLES[minOf(level - 1, TITLES.size - 1)]

    fun quests(store: Store, ctx: Context): List<Quest> {
        val cards = store.cards()
        val (done, _) = store.progress(cards)
        val parkedToday = NotesDb.get(ctx).all().count { it.labels.contains("Parked") && it.created >= startOfDay() }
        val subjectDone = cards.any { it.type == CardType.STUDY && it.dayItems.isNotEmpty() && it.dayItems.all { i -> i.done } }
        return listOf(
            Quest(K("Tick off 3 tasks"), done >= 3),
            Quest(K("Finish a whole subject"), subjectDone),
            Quest(K("Keep Shorts under 20 today"), store.shortsSwipes() <= 20),
            Quest(K("Park a thought instead of chasing it"), parkedToday >= 1),
        )
    }

    /** Unlocks whatever is newly earned and returns it (so the UI can celebrate). */
    fun evaluate(ctx: Context, store: Store): List<Badge> {
        val have = store.badges()
        val fresh = ArrayList<Badge>()
        fun unlock(id: String) {
            if (id in have) return
            store.saveBadge(id, store.today)
            BADGES.firstOrNull { it.id == id }?.let { fresh.add(it) }
        }

        var anyEarned = false; var wiped = false; var bigDay = false; var treatDay = false
        var diet50 = false; var diet20 = false; var diet10 = false; var free = false
        for (off in 0..120) {
            val d = store.dayStat(off)
            if (!d.active) continue
            if (d.earnedSec > 0) anyEarned = true
            if (d.allDone) wiped = true
            if (d.earnedSec >= 600) bigDay = true
            if (d.earnedSec >= 2400) treatDay = true
            if (off >= 1) {          // only finished days count for the Shorts badges
                if (d.swipes <= 50) diet50 = true
                if (d.swipes <= 20) diet20 = true
                if (d.swipes <= 10) diet10 = true
                if (d.swipes == 0) free = true
            }
        }
        if (anyEarned) unlock("first")
        if (wiped) unlock("wiper")
        if (bigDay) unlock("slayer")
        if (store.flagSet("speedrun")) unlock("speed")
        val ws = workStreak(store)
        if (ws >= 7) unlock("week"); if (ws >= 14) unlock("two"); if (ws >= 30) unlock("month")
        if (diet50) unlock("diet50"); if (diet20) unlock("diet20"); if (diet10) unlock("diet10"); if (free) unlock("free")
        if (streak(store, false) { it.swipes <= LIGHT } >= 7) unlock("detox")
        if (streak(store, false) { it.swipes == 0 } >= 7) unlock("retire")
        if (Db.get(ctx).countReasons("left", "blocked") >= 10) unlock("door")
        val notes = NotesDb.get(ctx).all().filter { it.trashedAt == 0L }
        if (notes.size >= 10) unlock("scribe")
        if (notes.count { it.labels.contains("Parked") } >= 5) unlock("dumper")
        val pdfs = store.pdfs()
        if (pdfs.size >= 3 || pdfs.sumOf { it.lastPage } >= 100) unlock("worm")
        if (store.lang != "en") unlock("poly")
        if (treatDay) unlock("treat")
        if (store.challengesAccepted() >= 5) unlock("dare")

        // shame: tasks were planned, nothing got done, three days running
        var idle = 0; var worstIdle = 0
        for (off in 1..120) {
            val d = store.dayStat(off)
            if (d.active && d.planned > 0 && d.earnedSec == 0 && !d.allDone) { idle++; worstIdle = maxOf(worstIdle, idle) } else idle = 0
        }
        if (worstIdle >= 3) unlock("goat_neglect")

        // more shame, all derived from data we already keep
        var zombie = false
        for (off in 0..120) if (store.dayStat(off).swipes >= 300) zombie = true
        if (zombie) unlock("zombie")

        val db = Db.get(ctx)
        val shortsRows = db.recent(3000).filter { it.kind == "shorts" }.sortedBy { it.t0 }
        val cal = java.util.Calendar.getInstance()
        if (shortsRows.any { cal.timeInMillis = it.t0; cal.get(java.util.Calendar.HOUR_OF_DAY) in 1..4 }) unlock("midnight")
        var curStart = 0L; var curEnd = 0L; var longest = 0L
        for (r in shortsRows) {
            val end = r.t1 ?: r.t0
            if (curEnd != 0L && r.t0 - curEnd <= 90_000L) curEnd = maxOf(curEnd, end)
            else { curStart = r.t0; curEnd = end }
            longest = maxOf(longest, curEnd - curStart)
        }
        if (longest >= 3_600_000L) unlock("marathon")

        if (store.flagSet("overtime30")) unlock("overtime")
        if (db.countReasons("Emergency") >= 5) unlock("abuser")
        if (db.countReasons("I'm distracted") >= 20) unlock("honesty")
        val (doneNow, totalNow) = store.progress(store.cards())
        if (totalNow - doneNow >= 30) unlock("hoarder")

        var gone = 0; var seenActive = false; var ghost = false
        for (off in 120 downTo 0) {
            val d = store.dayStat(off)
            if (d.active) { if (seenActive && gone >= 3) ghost = true; gone = 0; seenActive = true }
            else if (seenActive) gone++
        }
        if (ghost) unlock("ghosted")

        var run = 0; var funeral = false
        for (off in 120 downTo 1) {
            val d = store.dayStat(off)
            if (d.active && workDay(d)) run++
            else { if (run >= 7 && d.active) funeral = true; run = 0 }
        }
        if (funeral) unlock("funeral")

        val shameDays = BADGES.filter { it.shame }.mapNotNull { store.badges()[it.id] }
        if (shameDays.isNotEmpty()) {
            val first = shameDays.min()
            for (off in 0..120) { val d = store.dayStat(off); if (d.allDone && d.key > first) unlock("comeback") }
        }
        return fresh
    }
}
