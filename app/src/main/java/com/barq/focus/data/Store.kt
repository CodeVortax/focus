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
import java.util.Calendar

enum class CardType { STUDY, TREAT, FREE }

data class Item(
    val text: String, val done: Boolean,
    val weekly: Boolean = false,      // a this-week task: any day, faster = bigger reward
    val reward: Int = 0,              // weekly: base treat minutes
    val created: Long = 0L,           // weekly: when it was added
    val deadline: Long = 0L,          // weekly: end of its week
    val paid: Boolean = false,        // weekly: reward already given (no double dipping)
    val group: String = "",           // name of the TaskGroup it sits in ("" = none)
    val voice: String = "",           // voice note file name ("" = none)
    val voiceMs: Long = 0L,
    val subs: List<SubTask> = emptyList(),   // small steps inside a big task
)

/** One small step of a task. */
data class SubTask(val text: String, val done: Boolean = false)

/** One video of a treat series. [orig] is the real file name, [locked] means it is renamed so no other app lists it. */
data class Episode(
    val uri: String, val name: String, val ms: Long = 0L, val done: Boolean = false,
    val locked: Boolean = false, val orig: String = ""
)

data class FocusCard(
    val id: Long, val title: String, val type: CardType, val color: Int,
    val minutes: Int, val items: List<Item> = emptyList(),
    val sound: String = "",           // "" = default alarm, "silent", or a ringtone uri
    val reward: Int = 10,             // treat minutes for finishing the WHOLE subject (split across its tasks)
    val media: String = "",           // treat cards: uri of the video/audio to play in the app
    val groups: List<TaskGroup> = emptyList(),
    val eps: List<Episode> = emptyList(),   // treat cards: a series, one episode per token
    val folder: String = ""                 // treat cards: the folder (tree uri) the episodes live in, for locking
)

data class Block(
    val id: Long, val name: String, val startMin: Int, val endMin: Int,
    val lock: Boolean = false,                 // true: only the allowed apps work during this block
    val strict: Boolean = false,               // true: no emergency override
    val allowed: Set<String> = emptySet(),
) {
    fun contains(m: Int) = if (startMin <= endMin) m in startMin until endMin else (m >= startMin || m < endMin)
}

fun minText(sec: Int): String =
    if (sec % 60 == 0) "${sec / 60}" else String.format(java.util.Locale.US, "%.1f", sec / 60f)

fun startOfDay(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

object Config {
    // package -> label. All of these are logged; only the "gated" ones get the overlay.
    val TRACKED = linkedMapOf(
        "com.google.android.youtube" to "YouTube",
        "com.android.chrome" to "Chrome",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "org.videolan.vlc" to "VLC",
        "org.telegram.messenger" to "Telegram",
    )
    val DEFAULT_GATED = setOf("com.google.android.youtube", "com.android.chrome")

    // free passes at the gate
    const val SEARCH_MINUTES = 10
    const val LINK_MINUTES = 5
    const val CUSTOM_MINUTES = 10
    const val DISTRACTED_MINUTES = 5
    const val DISTRACTED_WAIT = 15
    const val DELAY_SECONDS = 3
    const val BLOCK_DELAY_SECONDS = 15
    const val SHORTS_GRACE_MIN = 2

    // treat bank (minutes), resets every day
    const val SUBJECT_BONUS = 10          // every subject whose checklist is fully done
    const val DAY_BONUS = 40              // the episode: every subject done
    const val PARTIAL_BONUS = 20          // "half episode" if you end the day with >= half done
    const val PARTIAL_MIN_FRACTION = 0.5f
    const val TREAT_STEP = 10             // size of one treat purchase at the gate
    const val ADD_TIME_MIN = 10
    const val WEEKLY_BASE_MIN = 15        // default reward of a this-week task (x2 if done at once, x1 at the deadline)

    // reminders
    const val REMIND_EVERY_MIN = 90
    const val REMIND_FROM_HOUR = 9
    const val REMIND_TO_HOUR = 22

    val FREE_WORDS = listOf(
        "Free prisoner", "Good to go", "Plate's empty", "Chill mode on",
        "Nothing owed", "Zero tasks, full hero", "Off the hook", "Vibes only"
    )
    fun freeWord(seed: Long): String = FREE_WORDS[(Math.abs(seed) % FREE_WORDS.size).toInt()]
}

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("focus", Context.MODE_PRIVATE)

    // ---- cards ----
    fun cards(): List<FocusCard> {
        val s = p.getString("cards", null) ?: return defaultCards().also { saveCards(it) }
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val items = o.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).map { i ->
                    arr.getJSONObject(i).let { x ->
                        Item(
                            x.getString("t"), x.getBoolean("d"), x.optBoolean("w", false), x.optInt("r", 0), x.optLong("c", 0L),
                            x.optLong("dl", 0L), x.optBoolean("p", false), x.optString("g", ""), x.optString("v", ""), x.optLong("vm", 0L),
                            x.optJSONArray("sb")?.let { sa -> (0 until sa.length()).map { k -> sa.getJSONObject(k).let { so -> SubTask(so.optString("t", ""), so.optBoolean("d", false)) } } } ?: emptyList()
                        )
                    }
                }
            } ?: emptyList()
            val groups = o.optJSONArray("groups")?.let { ga ->
                (0 until ga.length()).map { i -> ga.getJSONObject(i).let { g -> TaskGroup(g.getString("n"), g.getInt("c"), g.optBoolean("x", false)) } }
            } ?: emptyList()
            val eps = o.optJSONArray("eps")?.let { ea ->
                (0 until ea.length()).map { i ->
                    ea.getJSONObject(i).let { e ->
                        Episode(e.getString("u"), e.optString("n", ""), e.optLong("ms", 0L), e.optBoolean("d", false), e.optBoolean("l", false), e.optString("o", ""))
                    }
                }
            } ?: emptyList()
            FocusCard(o.getLong("id"), o.getString("title"), CardType.valueOf(o.getString("type")), o.getInt("color"), o.getInt("minutes"), items, o.optString("sound", ""), o.optInt("reward", 10), o.optString("media", ""), groups, eps, o.optString("folder", ""))
        }
    }

    fun saveCards(l: List<FocusCard>) {
        val a = JSONArray()
        l.forEach { c ->
            val items = JSONArray()
            c.items.forEach {
                items.put(
                    JSONObject().put("t", it.text).put("d", it.done).put("w", it.weekly).put("r", it.reward).put("c", it.created)
                        .put("dl", it.deadline).put("p", it.paid).put("g", it.group).put("v", it.voice).put("vm", it.voiceMs)
                        .put("sb", JSONArray().also { sa -> it.subs.forEach { sx -> sa.put(JSONObject().put("t", sx.text).put("d", sx.done)) } })
                )
            }
            val groups = JSONArray()
            c.groups.forEach { groups.put(JSONObject().put("n", it.name).put("c", it.color).put("x", it.collapsed)) }
            a.put(JSONObject().put("id", c.id).put("title", c.title).put("type", c.type.name)
                .put("color", c.color).put("minutes", c.minutes).put("items", items).put("sound", c.sound).put("reward", c.reward).put("media", c.media).put("groups", groups)
                .put("folder", c.folder).put("eps", JSONArray().also { ea ->
                    c.eps.forEach { e -> ea.put(JSONObject().put("u", e.uri).put("n", e.name).put("ms", e.ms).put("d", e.done).put("l", e.locked).put("o", e.orig)) }
                }))
        }
        p.edit().putString("cards", a.toString()).apply()
    }

    fun updateCard(c: FocusCard) = saveCards(cards().map { if (it.id == c.id) c else it })

    private fun defaultCards(): List<FocusCard> {
        val t = System.currentTimeMillis()
        return listOf(
            FocusCard(t + 1, "Chemistry", CardType.STUDY, 0xFF34A853.toInt(), 60),
            FocusCard(t + 2, "Math", CardType.STUDY, 0xFF4285F4.toInt(), 60),
            FocusCard(t + 3, "Physics", CardType.STUDY, 0xFFEA4335.toInt(), 60),
            FocusCard(t + 4, "Friv project", CardType.STUDY, 0xFFFBBC04.toInt(), 60),
            FocusCard(t + 5, "Mentalist", CardType.TREAT, 0xFF4285F4.toInt(), 40),
        )
    }

    /** New day: finished tasks disappear, unfinished ones carry over. */
    fun rollover() {
        touchDay()
        val d = day()
        if (p.getInt("last_day", 0) != d) {
            p.edit().putInt("last_day", d).apply()
            saveCards(cards().map { c -> c.copy(items = c.items.filter { !it.done }) })
        }
        notePlanned(cards())
    }

    /** Remembers the biggest plate you had today. */
    fun notePlanned(cards: List<FocusCard>) {
        val total = cards.filter { it.type == CardType.STUDY }.sumOf { it.dayItems.size }
        if (total > p.getInt("planned_${day()}", 0)) p.edit().putInt("planned_${day()}", total).apply()
    }

    fun progress(cards: List<FocusCard>): Pair<Int, Int> {
        val plan = cards.filter { it.type == CardType.STUDY }
        return plan.sumOf { c -> c.dayItems.count { it.done } } to plan.sumOf { it.dayItems.size }
    }

    // ---- not-free blocks ----
    fun blocks(): List<Block> {
        val s = p.getString("blocks", null) ?: return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Block(
                o.getLong("id"), o.getString("name"), o.getInt("s"), o.getInt("e"),
                o.optBoolean("lock", false), o.optBoolean("strict", false),
                o.optJSONArray("allowed")?.let { a -> (0 until a.length()).map { i -> a.getString(i) }.toSet() } ?: emptySet()
            )
        }
    }

    fun saveBlocks(l: List<Block>) {
        val a = JSONArray()
        l.forEach {
            val al = JSONArray(); it.allowed.forEach { p -> al.put(p) }
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("s", it.startMin).put("e", it.endMin)
                .put("lock", it.lock).put("strict", it.strict).put("allowed", al))
        }
        p.edit().putString("blocks", a.toString()).apply()
    }

    fun activeBlock(): Block? {
        val c = Calendar.getInstance()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return blocks().firstOrNull { it.contains(m) }
    }

    // ---- gated apps ----
    fun gated(): Set<String> = p.getStringSet("gated", null)?.toSet() ?: Config.DEFAULT_GATED
    fun setGated(s: Set<String>) { p.edit().putStringSet("gated", HashSet(s)).apply() }

    // ---- treat bank ----
    private fun day(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
    private fun flag(n: String) = p.getBoolean("${n}_${day()}", false)
    private fun setFlag(n: String) { p.edit().putBoolean("${n}_${day()}", true).apply() }

    private fun earnedSec() = p.getInt("earnedS_${day()}", 0)
    private fun spentSec() = p.getInt("spentS_${day()}", 0)
    fun earnSec(s: Int) { p.edit().putInt("earnedS_${day()}", earnedSec() + s).apply() }
    fun spendSec(s: Int) { p.edit().putInt("spentS_${day()}", spentSec() + s).apply() }
    fun earn(min: Int) = earnSec(min * 60)
    fun spend(min: Int) = spendSec(min * 60)
    fun bankSec() = (earnedSec() - spentSec()).coerceAtLeast(0)
    fun bank() = bankSec() / 60                    // whole minutes (what the gate can sell you)
    fun bankText() = minText(bankSec())

    /**
     * Each subject's reward is split across its tasks: finish 1 of 4 tasks of a 10-min subject = 2.5 min.
     * Never takes anything back. Returns seconds gained now.
     */
    fun settle(cards: List<FocusCard>): Int {
        var gained = 0
        val plan = cards.filter { it.type == CardType.STUDY && it.dayItems.isNotEmpty() }
        for (c in plan) {
            val target = c.reward * 60 * c.dayItems.count { it.done } / c.dayItems.size
            val key = "subs_${day()}_${c.id}"
            val have = p.getInt(key, 0)
            if (target > have) { gained += target - have; p.edit().putInt(key, target).apply() }
        }
        if (plan.isNotEmpty() && plan.all { c -> c.dayItems.all { it.done } } && !flag("day")) {
            setFlag("day")
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 18) p.edit().putBoolean("speedrun", true).apply()
            gained += (Config.DAY_BONUS - (if (flag("partial")) Config.PARTIAL_BONUS else 0)) * 60
        }
        if (gained > 0) earnSec(gained)
        return gained
    }

    fun partialEligible(cards: List<FocusCard>): Boolean {
        val (d, t) = progress(cards)
        return t > 0 && d < t && d.toFloat() / t >= Config.PARTIAL_MIN_FRACTION && !flag("day") && !flag("partial")
    }

    fun cashOutPartial(cards: List<FocusCard>): Int {
        if (!partialEligible(cards)) return 0
        setFlag("partial"); earn(Config.PARTIAL_BONUS)
        return Config.PARTIAL_BONUS
    }

    // ---- study timer (persisted: survives leaving the screen / closing the app) ----
    var activeCardId: Long
        get() = p.getLong("act_id", -1L)
        set(v) { p.edit().putLong("act_id", v).apply() }
    var activeEnd: Long
        get() = p.getLong("act_end", 0L)
        set(v) { p.edit().putLong("act_end", v).apply() }
    var activeStart: Long
        get() = p.getLong("act_start", 0L)
        set(v) { p.edit().putLong("act_start", v).apply() }
    var activePaused: Boolean
        get() = p.getBoolean("act_paused", false)
        set(v) { p.edit().putBoolean("act_paused", v).apply() }
    var activeRemaining: Long                     // ms left while paused (can be negative = over budget)
        get() = p.getLong("act_left", 0L)
        set(v) { p.edit().putLong("act_left", v).apply() }

    fun spent(cardId: Long) = p.getLong("spent_${day()}_$cardId", 0L)
    private fun addSpent(cardId: Long, ms: Long) { p.edit().putLong("spent_${day()}_$cardId", spent(cardId) + ms).apply() }

    fun startActive(cardId: Long, end: Long) {
        stopActive()
        activeCardId = cardId; activeEnd = end; activeStart = System.currentTimeMillis(); activePaused = false
    }

    fun pauseActive() {
        if (activeCardId == -1L || activePaused) return
        val now = System.currentTimeMillis()
        addSpent(activeCardId, (now - activeStart).coerceAtLeast(0L))
        activeRemaining = activeEnd - now
        activePaused = true
    }

    fun resumeActive() {
        if (activeCardId == -1L || !activePaused) return
        val now = System.currentTimeMillis()
        activeEnd = now + activeRemaining
        activeStart = now
        activePaused = false
    }

    /** Adds time; if already over budget, the extra time counts from now. */
    fun addActiveTime(ms: Long) {
        if (activeCardId == -1L) return
        if (activePaused) activeRemaining = maxOf(activeRemaining, 0L) + ms
        else activeEnd = maxOf(activeEnd, System.currentTimeMillis()) + ms
    }

    fun stopActive() {
        val id = activeCardId
        if (id != -1L && !activePaused && System.currentTimeMillis() - activeEnd >= 30 * 60_000L) setFlagNamed("overtime30")
        if (id != -1L && !activePaused) addSpent(id, (System.currentTimeMillis() - activeStart).coerceAtLeast(0L))
        p.edit().remove("act_id").remove("act_end").remove("act_start").remove("act_paused").remove("act_left").apply()
    }

    // ---- reminders ----
    var lastRemind: Long
        get() = p.getLong("last_remind", 0L)
        set(v) { p.edit().putLong("last_remind", v).apply() }

    /** (tasks left, "Math 2 · Physics 3") */
    fun leftSummary(cards: List<FocusCard>): Pair<Int, String> {
        val parts = cards.filter { it.type == CardType.STUDY }
            .map { it to it.dayItems.count { i -> !i.done } }
            .filter { it.second > 0 }
        return parts.sumOf { it.second } to parts.joinToString(" · ") { "${it.first.title} ${it.second}" }
    }

    // ---- "you haven't started" nudges ----
    var startRemind: Boolean
        get() = p.getBoolean("start_remind", true)
        set(v) { p.edit().putBoolean("start_remind", v).apply() }
    var startRemindFrom: Int                      // minute of the day, default 16:00
        get() = p.getInt("start_remind_from", 16 * 60)
        set(v) { p.edit().putInt("start_remind_from", v).apply() }
    var startRemindEvery: Int                     // minutes between nudges, default 60
        get() = p.getInt("start_remind_every", 60)
        set(v) { p.edit().putInt("start_remind_every", v.coerceAtLeast(5)).apply() }
    var lastStartRemind: Long
        get() = p.getLong("last_start_remind", 0L)
        set(v) { p.edit().putLong("last_start_remind", v).apply() }

    /** True once you ticked a task, ran a timer or have one running today. */
    fun workedToday(cards: List<FocusCard>): Boolean =
        activeCardId != -1L || progress(cards).first > 0 ||
            cards.any { it.type == CardType.STUDY && spent(it.id) > 0L }

    // ---- look & feel ----
    var themeMode: Int                            // 0 follow system, 1 light, 2 dark
        get() = p.getInt("theme_mode", 0)
        set(v) { p.edit().putInt("theme_mode", v).apply() }
    var checkShape: Int                           // 0 circle, 1..6 triangle..octagon, 7 mixed
        get() = p.getInt("check_shape", 0)
        set(v) { p.edit().putInt("check_shape", v).apply() }
    var timerCompact: Boolean
        get() = p.getBoolean("timer_compact", false)
        set(v) { p.edit().putBoolean("timer_compact", v).apply() }

    // ---- shorts counter ----
    fun shortsSwipes() = p.getInt("shorts_${day()}", 0)
    fun addShortsSwipe() { p.edit().putInt("shorts_${day()}", shortsSwipes() + 1).apply() }

    // ---- treat series: one episode = one token ----
    /** Seconds of watching left on this episode's token (its length plus the free rewind minutes). */
    fun token(cardId: Long, idx: Int): Int = p.getInt("tok_${cardId}_$idx", 0)
    fun setToken(cardId: Long, idx: Int, sec: Int) { p.edit().putInt("tok_${cardId}_$idx", sec.coerceAtLeast(0)).apply() }
    fun markEpisodeDone(cardId: Long, idx: Int) {
        val c = cards().firstOrNull { it.id == cardId } ?: return
        if (idx !in c.eps.indices) return
        updateCard(c.copy(eps = c.eps.mapIndexed { i, e -> if (i == idx) e.copy(done = true) else e }))
        setToken(cardId, idx, 0)
        p.edit().remove("media_pos_${cardId}_$idx").apply()
    }

    // ---- first-run guide ----
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }
    /** True on a fresh install: nothing saved yet. (Existing users skip the guide; Settings can replay it.) */
    fun isFresh(): Boolean = !onboarded && !p.contains("cards")

    // ---- recordings ----
    var askRecName: Boolean                       // pop up a name box when a recording stops
        get() = p.getBoolean("rec_ask_name", true)
        set(v) { p.edit().putBoolean("rec_ask_name", v).apply() }
    var recKbps: Int                              // Opus bit rate for voice: 24 small, 32 balanced, 64 high
        get() = p.getInt("rec_kbps", 32)
        set(v) { p.edit().putInt("rec_kbps", v).apply() }

    // ---- hidden developer options (tap the version 7 times) ----
    var devOptions: Boolean
        get() = p.getBoolean("dev_options", false)
        set(v) { p.edit().putBoolean("dev_options", v).apply() }

    // ---- detection tuning ----
    var debug: Boolean
        get() = p.getBoolean("debug", false)
        set(v) { p.edit().putBoolean("debug", v).apply() }
    var debugIds: String
        get() = p.getString("debug_ids", "") ?: ""
        set(v) { p.edit().putString("debug_ids", v).apply() }

    // ---- timer alert settings ----
    var ringSec: Int
        get() = p.getInt("ring_sec", 30)
        set(v) { p.edit().putInt("ring_sec", v).apply() }
    var nagMin: Int
        get() = p.getInt("nag_min", 5)
        set(v) { p.edit().putInt("nag_min", v).apply() }
    var nagRingSec: Int
        get() = p.getInt("nag_ring_sec", 5)
        set(v) { p.edit().putInt("nag_ring_sec", v).apply() }
    var speak: Boolean
        get() = p.getBoolean("speak", true)
        set(v) { p.edit().putBoolean("speak", v).apply() }
    var alertedEnd: Long
        get() = p.getLong("alerted_end", 0L)
        set(v) { p.edit().putLong("alerted_end", v).apply() }
    var lastNag: Long
        get() = p.getLong("last_nag", 0L)
        set(v) { p.edit().putLong("last_nag", v).apply() }
    val lastCrash: String get() = p.getString("last_crash", "") ?: ""

    // ---- notes prefs ----
    var notesGrid: Boolean
        get() = p.getBoolean("notes_grid", true)
        set(v) { p.edit().putBoolean("notes_grid", v).apply() }
    var notesSort: Int                            // 0 last edited, 1 created, 2 title
        get() = p.getInt("notes_sort", 0)
        set(v) { p.edit().putInt("notes_sort", v).apply() }
    var notesAutoSort: Boolean                    // checked items drop to the bottom
        get() = p.getBoolean("notes_autosort", true)
        set(v) { p.edit().putBoolean("notes_autosort", v).apply() }

    // ---- pdf library ----
    fun pdfs(): List<PdfEntry> {
        val a = JSONArray(p.getString("pdfs", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            PdfEntry(o.getString("uri"), o.getString("title"), o.optInt("page", 0), o.optInt("pages", 0))
        }
    }
    fun savePdfs(l: List<PdfEntry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("uri", it.uri).put("title", it.title).put("page", it.lastPage).put("pages", it.pages)) }
        p.edit().putString("pdfs", a.toString()).apply()
    }
    fun upsertPdf(e: PdfEntry) {
        val l = pdfs()
        savePdfs(if (l.any { it.uri == e.uri }) l.map { if (it.uri == e.uri) e else it } else l + e)
    }
    var pdfNight: Boolean
        get() = p.getBoolean("pdf_night", false)
        set(v) { p.edit().putBoolean("pdf_night", v).apply() }

    // ---- language ----
    var lang: String
        get() = p.getString("lang", null) ?: java.util.Locale.getDefault().language.takeIf { it in setOf("ar", "ru", "de", "fr") } ?: "en"
        set(v) { p.edit().putString("lang", v).apply() }
    fun langPack(code: String): String? = p.getString("langpack_$code", null)
    fun saveLangPack(code: String, json: String) {
        val codes = langCodes().toMutableSet().also { it.add(code) }
        p.edit().putString("langpack_$code", json).putStringSet("langcodes", codes).apply()
    }
    fun langCodes(): Set<String> = p.getStringSet("langcodes", null)?.toSet() ?: emptySet()

    fun putLong(k: String, v: Long) { p.edit().putLong(k, v).apply() }
    fun getLong(k: String): Long = p.getLong(k, 0L)

    // ---- history, streaks, badges ----
    fun touchDay() {
        if (!p.getBoolean("act_${day()}", false)) p.edit().putBoolean("act_${day()}", true).apply()
    }

    fun dayStat(offset: Int): DayStat {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -offset)
        val d = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        return DayStat(
            d,
            p.getBoolean("act_$d", false) || p.contains("shorts_$d") || p.contains("earnedS_$d"),
            p.getBoolean("day_$d", false), p.getBoolean("partial_$d", false),
            p.getInt("shorts_$d", 0), p.getInt("earnedS_$d", 0), p.getInt("planned_$d", 0)
        )
    }

    fun badges(): Map<String, Int> {
        val o = JSONObject(p.getString("badges", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }
    fun saveBadge(id: String, dayKey: Int) {
        val o = JSONObject(p.getString("badges", "{}") ?: "{}")
        if (!o.has(id)) { o.put(id, dayKey); p.edit().putString("badges", o.toString()).apply() }
    }
    fun flagSet(name: String) = p.getBoolean(name, false)
    fun setFlagNamed(name: String) { p.edit().putBoolean(name, true).apply() }
    val today: Int get() = day()

    var accent: Int
        get() = p.getInt("accent", 0)
        set(v) { p.edit().putInt("accent", v).apply() }

    var preciseSwipes: Boolean
        get() = p.getBoolean("precise_swipes", false)
        set(v) { p.edit().putBoolean("precise_swipes", v).apply() }

    var pdfReader: String                          // "episteme" or "builtin"
        get() = p.getString("pdf_reader", "episteme") ?: "episteme"
        set(v) { p.edit().putString("pdf_reader", v).apply() }

    var pdfMode: Int                               // 0 day, 1 sepia, 2 night
        get() = p.getInt("pdf_mode", if (pdfNight) 2 else 0)
        set(v) { p.edit().putInt("pdf_mode", v).apply() }
    var penStylusOnly: Boolean
        get() = p.getBoolean("pen_stylus_only", false)
        set(v) { p.edit().putBoolean("pen_stylus_only", v).apply() }

    fun bookmarks(uri: String): List<Int> =
        (p.getString("bm_${uri.hashCode()}", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.sorted()

    fun toggleBookmark(uri: String, page: Int): List<Int> {
        val cur = bookmarks(uri).toMutableList()
        if (!cur.remove(page)) cur.add(page)
        cur.sort()
        p.edit().putString("bm_${uri.hashCode()}", cur.joinToString(",")).apply()
        return cur
    }

    // ---- music: playlists + favourites ----
    fun playlists(): Map<String, List<Long>> {
        val o = JSONObject(p.getString("playlists", "{}") ?: "{}")
        val out = LinkedHashMap<String, List<Long>>()
        o.keys().forEach { k -> val a = o.getJSONArray(k); out[k] = (0 until a.length()).map { a.getLong(it) } }
        return out
    }
    fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        p.edit().putString("playlists", o.toString()).apply()
    }
    fun favorites(): Set<Long> = (p.getString("fav_ids", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
    fun toggleFavorite(id: Long): Set<Long> {
        val f = favorites().toMutableSet()
        if (!f.remove(id)) f.add(id)
        p.edit().putString("fav_ids", f.joinToString(",")).apply()
        return f
    }
}

data class PdfEntry(val uri: String, val title: String, val lastPage: Int, val pages: Int)

data class DayStat(
    val key: Int, val active: Boolean, val allDone: Boolean, val partial: Boolean,
    val swipes: Int, val earnedSec: Int, val planned: Int = 0
)
