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

/**
 * Calm Breathing: the maths of the breathing circle, with no Android or Compose in it (so it is unit-tested).
 * There is no score and nothing can be failed; the circle just follows the pattern.
 */
object Breath {
    val IN = K("Breathe in")
    val OUT = K("Breathe out")
    val HOLD = K("Hold")

    const val MIN_SCALE = 0.45f
    const val MAX_SCALE = 1.0f

    class Step(val label: String, val sec: Int)
    class Pattern(val name: String, val steps: List<Step>) { val cycleSec: Int = steps.sumOf { it.sec } }

    /** Where a breath is: which step, how far through it (0..1), how many whole seconds of the step are left, and the circle size (MIN_SCALE..MAX_SCALE). */
    class Pos(val index: Int, val progress: Float, val secLeft: Int, val scale: Float)

    val PATTERNS: List<Pattern> = listOf(
        Pattern(K("Calm: in 4, out 6"), listOf(Step(IN, 4), Step(OUT, 6))),
        Pattern(K("Box: 4, 4, 4, 4"), listOf(Step(IN, 4), Step(HOLD, 4), Step(OUT, 4), Step(HOLD, 4))),
    )

    val LENGTHS_MIN = listOf(1, 3, 5)

    private fun ease(x: Float): Float = x * x * (3f - 2f * x)      // slow start, slow end
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun at(p: Pattern, seconds: Float): Pos {
        val n = p.steps.size
        val x = if (seconds <= 0f) 0f else seconds % p.cycleSec
        var i = 0
        var start = 0
        while (i < n - 1 && x >= start + p.steps[i].sec) { start += p.steps[i].sec; i++ }
        val step = p.steps[i]
        val prog = ((x - start) / step.sec).coerceIn(0f, 1f)
        val e = ease(prog)
        val scale = when (step.label) {
            IN -> lerp(MIN_SCALE, MAX_SCALE, e)
            OUT -> lerp(MAX_SCALE, MIN_SCALE, e)
            else -> if (p.steps[(i - 1 + n) % n].label == IN) MAX_SCALE else MIN_SCALE      // a hold keeps the size the breath ended on
        }
        val left = (step.sec - (x - start)).let { kotlin.math.ceil(it.toDouble()).toInt() }.coerceIn(1, step.sec)
        return Pos(i, prog, left, scale)
    }
}
