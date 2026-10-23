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
import com.barq.focus.service.Notifier
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar

/**
 * The piggy bank. Each finished day, the treat time you did not spend goes into the piggy; a week later it is paid
 * into your treat bank with a little interest. A day with tasks planned and nothing done costs a (funny) fine,
 * taken from the piggy. Everything is settled lazily when the app or the watcher runs, so no alarm is needed.
 */
object Vault {
    class Lot(val release: Long, val sec: Int, val dep: Int)
    class Entry(val t: Long, val kind: String, val sec: Int, val title: String, val text: String)
    class Fine(val title: String, val text: String, val min: Int)

    const val INTEREST_PCT = 10
    const val HOLD_DAYS = 7
    private const val MIN_DEPOSIT_SEC = 60

    val FINES = listOf(
        Fine(K("Speeding ticket"), K("You flew past your homework at 140 km/h. The officer says: slow down and open a book."), 10),
        Fine(K("Parking ticket"), K("Your tasks sat parked in a no-progress zone all day."), 5),
        Fine(K("Red light violation"), K("You ran straight through the big red STUDY light."), 8),
        Fine(K("Homework tax"), K("The Ministry of Procrastination has collected its share."), 10),
        Fine(K("Library overdue fee"), K("Your tasks are a whole day overdue. Late fee applied."), 6),
        Fine(K("Couch parking permit"), K("Premium couch parking is not free, my friend."), 7),
        Fine(K("Noise complaint"), K("Your homework was suspiciously quiet all day. The neighbours called it in."), 5),
        Fine(K("Seat belt fine"), K("You didn't buckle up for today's tasks."), 6),
        Fine(K("Goat tax"), K("The goat ate your plan. You owe it lunch."), 9),
        Fine(K("Snooze button surcharge"), K("The snooze button was pressed on your whole day."), 7),
    )
    private val PITY = listOf(
        K("The officer found your piggy bank empty and let you off with a warning."),
        K("Nothing to fine: the piggy is empty. The officer shook his head sadly."),
    )

    private var lastRun = 0L

    fun lots(s: Store): List<Lot> = try {
        val a = JSONArray(s.vaultLots)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Lot(o.getLong("r"), o.getInt("s"), o.getInt("d")) } }
    } catch (_: Exception) { emptyList() }

    private fun saveLots(s: Store, l: List<Lot>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("r", it.release).put("s", it.sec).put("d", it.dep)) }
        s.vaultLots = a.toString()
    }

    fun ledger(s: Store): List<Entry> = try {
        val a = JSONArray(s.vaultLedger)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Entry(o.getLong("t"), o.getString("k"), o.getInt("s"), o.getString("n"), o.optString("x", "")) } }
    } catch (_: Exception) { emptyList() }

    private fun saveLedger(s: Store, l: List<Entry>) {
        val a = JSONArray()
        l.takeLast(80).forEach { a.put(JSONObject().put("t", it.t).put("k", it.kind).put("s", it.sec).put("n", it.title).put("x", it.text)) }
        s.vaultLedger = a.toString()
    }

    fun balanceSec(s: Store) = lots(s).sumOf { it.sec }

    private fun keyOf(offset: Int): Int {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -offset)
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
    private fun epochOf(key: Int) = LocalDate.of(key / 10000, (key / 100) % 100, key % 100).toEpochDay()

    /** Days until a lot is paid out (0 = today). */
    fun daysLeft(l: Lot): Int = (l.release - LocalDate.now().toEpochDay()).toInt().coerceAtLeast(0)

    val RULE_FINES = listOf(
        Fine(K("Homework evasion notice"), K("The plate was not empty when new work arrived. The teacher has been informed, and so has your piggy bank."), 5),
        Fine(K("Unpaid homework invoice"), K("Your unfinished tasks were billed to you. Interest applies."), 5),
        Fine(K("Class-day jaywalking"), K("You walked into class with a full plate. That is jaywalking."), 6),
        Fine(K("Procrastination levy"), K("Delay is a lifestyle, and lifestyles are taxed."), 5),
        Fine(K("Late slip"), K("The tasks arrived late to their own party. Sign here."), 4),
    )

    /** Misses recorded at the daily rollover (a class day with open tasks, or a task past its due day) become fines. */
    private fun ruleFines(ctx: Context, s: Store, now: Long) {
        val arr = try { JSONArray(s.ruleMissPending) } catch (_: Exception) { return }
        if (arr.length() == 0) return
        s.ruleMissPending = "[]"
        if (!s.vaultOn) return
        var lots = lots(s)
        val entries = ArrayList(ledger(s))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.optInt("n", 1); val title = o.optString("c", "")
            val f = RULE_FINES[((o.optInt("d", 0) + i) % RULE_FINES.size + RULE_FINES.size) % RULE_FINES.size]
            var due = minOf(f.min * maxOf(n, 1), 20) * 60
            val kept = ArrayList<Lot>()
            for (l in lots.sortedBy { it.release }) {
                if (due <= 0) { kept.add(l); continue }
                val take = minOf(due, l.sec); due -= take
                if (l.sec - take > 0) kept.add(Lot(l.release, l.sec - take, l.dep))
            }
            val total = minOf(f.min * maxOf(n, 1), 20) * 60
            val paid = total - due
            lots = kept
            val text = "$title: " + tr(f.text)
            if (paid > 0) { s.vaultFines = s.vaultFines + 1; entries.add(Entry(now, "fine", -paid, f.title, text)) }
            else { s.setFlagNamed("fine_empty"); entries.add(Entry(now, "warn", 0, K("Warning"), text)) }
            Notifier.piggy(ctx, tr(f.title), text)
        }
        saveLots(s, lots); saveLedger(s, entries)
    }

    fun process(ctx: Context, s: Store) {
        val now = System.currentTimeMillis()
        if (now - lastRun < 30_000L) return
        lastRun = now
        try { ruleFines(ctx, s, now) } catch (_: Exception) {}
        if (!s.vaultOn) return
        try { settle(ctx, s, now) } catch (_: Exception) {}
    }

    private fun settle(ctx: Context, s: Store, now: Long) {
        val yesterday = keyOf(1)
        if (s.vaultLast == 0) { s.vaultLast = yesterday; return }       // counting starts today
        var lots = lots(s)
        val entries = ArrayList(ledger(s))
        var announceFine: Pair<String, String>? = null
        var changed = false

        for (off in 30 downTo 1) {
            val k = keyOf(off)
            if (k <= s.vaultLast) continue
            changed = true
            val st = s.dayStat(off)
            val dep = s.unusedSecOn(k)
            if (dep >= MIN_DEPOSIT_SEC) {
                lots = lots + Lot(epochOf(k) + HOLD_DAYS, dep, k)
                s.vaultDepositedSec += dep
                entries.add(Entry(now, "dep", dep, K("Saved in the piggy bank"), K("Unused treat time. It is paid out in a week, with interest.")))
            }
            if (st.planned > 0 && st.earnedSec == 0 && !st.allDone) {
                val fine = FINES[(k % FINES.size + FINES.size) % FINES.size]
                var due = fine.min * 60
                val sorted = lots.sortedBy { it.release }
                val kept = ArrayList<Lot>()
                for (l in sorted) {
                    if (due <= 0) { kept.add(l); continue }
                    val take = minOf(due, l.sec)
                    due -= take
                    if (l.sec - take > 0) kept.add(Lot(l.release, l.sec - take, l.dep))
                }
                lots = kept
                val paid = fine.min * 60 - due
                if (paid > 0) {
                    s.vaultFines = s.vaultFines + 1
                    entries.add(Entry(now, "fine", -paid, fine.title, fine.text))
                    if (k == yesterday) announceFine = fine.title to fine.text
                } else {
                    s.setFlagNamed("fine_empty")
                    val pity = PITY[(k % PITY.size + PITY.size) % PITY.size]
                    entries.add(Entry(now, "warn", 0, K("Warning"), pity))
                    if (k == yesterday) announceFine = K("Warning") to pity
                }
            }
            s.vaultLast = k
        }

        // payouts
        val today = LocalDate.now().toEpochDay()
        val due = lots.filter { it.release <= today }
        if (due.isNotEmpty()) {
            changed = true
            var base = 0; var extra = 0
            due.forEach { base += it.sec; extra += it.sec * INTEREST_PCT / 100 }
            s.addBonusSec(base + extra)
            s.vaultPayouts = s.vaultPayouts + 1
            s.vaultInterestSec += extra
            entries.add(Entry(now, "rel", base + extra, K("Piggy bank paid out"), K("A week of saved treat time, plus interest, is in your bank.")))
            lots = lots.filter { it.release > today }
            Notifier.piggy(ctx, K("Piggy bank paid out"), "+" + minText(base + extra) + " " + tr("min") + "  (" + tr("includes interest") + ")")
        }
        announceFine?.let { (t, x) -> Notifier.piggy(ctx, t, x) }

        if (changed) {
            saveLots(s, lots); saveLedger(s, entries)
            val bal = lots.sumOf { it.sec }.toLong()
            if (bal > s.vaultPeakSec) s.vaultPeakSec = bal
        }
    }
}
