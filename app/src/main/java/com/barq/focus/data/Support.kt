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

import kotlin.random.Random

/** The moments the companion can speak at. [transient] ones are short toasts and obey the frequency setting; the rest are quiet lines inside a screen. */
enum class Moment(val transient: Boolean) {
    GATE_FIRST(false), GATE_EARNED(false), GATE_EMPTY(false), GATE_REPEAT(false), GATE_RETURN(false), BLOCK(false),
    LEAVE(true), EMERGENCY(true),
    STUDY_START(false), STUDY_DURING(false), TASK_DONE(true),
    HOME_GREETING(false), HOME_EMPTY(false),
}

/** 1 calm, 2 warm, 3 playful. The setting 0 means "any". */
enum class Tone(val code: Int) { CALM(1), WARM(2), PLAYFUL(3) }

class Msg(val text: String, val tone: Tone)

/**
 * Short, kind lines for the gate, the study screen and the home screen. The text is the English key: show it with tr(),
 * so every language (Arabic and RTL included) goes through the same table as the rest of the app.
 * Pure Kotlin on purpose (no Android classes) so the selection rules can be unit-tested.
 *
 * Rules the lines follow: encourage a step instead of demanding perfection, praise returning, never blame, never say
 * the user must earn rest, never pressure them to keep the app open.
 */
object Support {
    private fun m(t: String, tone: Tone) = Msg(K(t), tone)

    val POOL: Map<Moment, List<Msg>> = mapOf(
        Moment.GATE_FIRST to listOf(
            m("Hey, what brought you here? Let's make this a conscious choice.", Tone.WARM),
            m("Quick check-in: what are you here for?", Tone.CALM),
            m("Hi. Let's pick this on purpose.", Tone.CALM),
        ),
        Moment.GATE_EARNED to listOf(
            m("You worked for this. Enjoy your break.", Tone.WARM),
            m("Your treat time is ready. Enjoy it.", Tone.CALM),
            m("Break time is yours. Go enjoy it.", Tone.PLAYFUL),
        ),
        Moment.GATE_EMPTY to listOf(
            m("One small task can get you started.", Tone.WARM),
            m("No treat time yet. Want to begin with something small?", Tone.CALM),
            m("A tiny first step is enough to begin.", Tone.PLAYFUL),
        ),
        Moment.GATE_REPEAT to listOf(
            m("Starting can be hard. Want to try just 5 minutes?", Tone.WARM),
            m("No judgment. Want to try a tiny 5-minute start?", Tone.CALM),
            m("Five minutes together? Just to get the wheels turning.", Tone.PLAYFUL),
        ),
        Moment.GATE_RETURN to listOf(
            m("Welcome back. Let's pick up where you left off.", Tone.WARM),
            m("Good to see you again. Take your time.", Tone.CALM),
        ),
        Moment.BLOCK to listOf(
            m("Let's make the next 10 minutes count.", Tone.WARM),
            m("One thing at a time. You can do this.", Tone.CALM),
        ),
        Moment.LEAVE to listOf(
            m("That's okay. You can come back when you're ready.", Tone.WARM),
            m("Good call stepping away. See you soon.", Tone.CALM),
        ),
        Moment.EMERGENCY to listOf(
            m("No worries. Sometimes you need your phone. Let's continue when you're ready.", Tone.WARM),
            m("Sometimes you just need your phone. That's fine.", Tone.CALM),
        ),
        Moment.STUDY_START to listOf(
            m("I'm here. Let's focus on this one thing.", Tone.WARM),
            m("One minute at a time.", Tone.CALM),
            m("Let's begin. No need to rush.", Tone.CALM),
        ),
        Moment.STUDY_DURING to listOf(
            m("You're doing better than you think.", Tone.WARM),
            m("Keep going. You don't need to rush.", Tone.CALM),
            m("Take a breath. Then continue.", Tone.CALM),
        ),
        Moment.TASK_DONE to listOf(
            m("Nice. That's one step done.", Tone.WARM),
            m("Done. That counts.", Tone.CALM),
            m("One more off the plate. Take a breath.", Tone.PLAYFUL),
        ),
        Moment.HOME_GREETING to listOf(
            m("Hey, welcome back.", Tone.WARM),
            m("Ready to take one small step?", Tone.CALM),
            m("Let's make today a little easier.", Tone.WARM),
        ),
        Moment.HOME_EMPTY to listOf(
            m("Let's make today easier. Add one small thing you want to work on.", Tone.WARM),
        ),
    )

    /** Chance (percent) that a transient line is shown, for the frequency setting 0 rarely, 1 sometimes, 2 always. */
    fun chance(freq: Int): Int = when (freq) { 0 -> 25; 1 -> 60; else -> 100 }

    fun shouldShow(moment: Moment, freq: Int, rnd: Random = Random.Default): Boolean =
        !moment.transient || rnd.nextInt(100) < chance(freq)

    /**
     * Picks a line for [moment]. [tone] 0 means any; if no line has the asked tone, any tone is used rather than nothing.
     * The same line is never picked twice in a row for one moment (when there is more than one to choose from).
     */
    fun pick(moment: Moment, tone: Int, rnd: Random = Random.Default, last: MutableMap<Moment, String> = LAST): String? {
        val all = POOL[moment] ?: return null
        val tonal = if (tone == 0) all else all.filter { it.tone.code == tone }.ifEmpty { all }
        val options = tonal.filter { it.text != last[moment] }.ifEmpty { tonal }
        val chosen = options[rnd.nextInt(options.size)].text
        last[moment] = chosen
        return chosen
    }

    private val LAST = HashMap<Moment, String>()
}
