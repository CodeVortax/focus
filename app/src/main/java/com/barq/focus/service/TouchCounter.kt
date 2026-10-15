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

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Optional, root only: reads the raw touchscreen events (`getevent`, like the Contre-Sozial project does)
 * and turns each real vertical swipe into exactly one Shorts count. Without root, Focus keeps using the
 * (less exact) accessibility scroll events.
 */
object TouchCounter {
    @Volatile var running = false
        private set
    @Volatile var status = ""
        private set

    private var proc: Process? = null
    private var thread: Thread? = null

    private class Dev(val path: String, val maxX: Int, val maxY: Int)

    private fun su(cmd: String): Process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))

    private fun readAll(p: Process): String = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }

    /** The touchscreen is the input device that reports multi-touch X and Y positions. */
    private fun findTouchscreen(): Dev? {
        val text = readAll(su("getevent -lp"))
        var path: String? = null
        var maxX = 0
        var maxY = 0
        val found = ArrayList<Dev>()
        fun flush() {
            if (path != null && maxX > 0 && maxY > 0) found.add(Dev(path!!, maxX, maxY))
        }
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("add device")) {
                flush()
                path = t.substringAfter(":").trim(); maxX = 0; maxY = 0
            } else if (t.contains("ABS_MT_POSITION_X") || t.contains("ABS_MT_POSITION_Y")) {
                val max = Regex("max\\s+(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (t.contains("POSITION_X")) maxX = max else maxY = max
            }
        }
        flush()
        return found.firstOrNull()
    }

    fun start(onSwipe: () -> Unit) {
        if (running) return
        running = true
        status = "Starting…"
        thread = Thread {
            try {
                val dev = findTouchscreen()
                if (dev == null) {
                    status = "Root granted but no touchscreen found"
                    running = false
                    return@Thread
                }
                val p = su("getevent -l ${dev.path}")
                proc = p
                status = "Running"
                val reader = BufferedReader(InputStreamReader(p.inputStream))

                var down = false
                var x = 0; var y = 0
                var sx = -1; var sy = -1
                var t0 = 0L
                val minDy = dev.maxY * 0.12

                fun finish() {
                    if (!down) return
                    down = false
                    val dy = abs(y - sy)
                    val dx = abs(x - sx)
                    val dt = System.currentTimeMillis() - t0
                    // a real vertical swipe: long enough, mostly vertical, not a slow drag or a tap
                    if (sy >= 0 && dy >= minDy && dy > dx * 1.4 && dt in 40..1500) onSwipe()
                }

                while (running) {
                    val line = reader.readLine() ?: break
                    val tok = line.trim().split(Regex("\\s+"))
                    // lines look like: EV_ABS ABS_MT_POSITION_Y 000006c9  (maybe prefixed by the device path)
                    val i = tok.indexOfFirst { it.startsWith("EV_") }
                    if (i < 0 || tok.size < i + 3) continue
                    val code = tok[i + 1]
                    val value = tok[i + 2]
                    when (code) {
                        "ABS_MT_POSITION_X" -> { x = value.toLong(16).toInt(); if (down && sx < 0) sx = x }
                        "ABS_MT_POSITION_Y" -> { y = value.toLong(16).toInt(); if (down && sy < 0) sy = y }
                        "BTN_TOUCH" -> {
                            if (value == "DOWN") { down = true; sx = -1; sy = -1; t0 = System.currentTimeMillis() }
                            else if (value == "UP") finish()
                        }
                        "ABS_MT_TRACKING_ID" -> {
                            if (value.equals("ffffffff", true)) finish()
                            else if (!down) { down = true; sx = -1; sy = -1; t0 = System.currentTimeMillis() }
                        }
                    }
                }
            } catch (e: Exception) {
                status = if (e.message?.contains("su", true) == true || e is java.io.IOException) "No root access" else "Stopped: ${e.message}"
            } finally {
                running = false
                proc?.destroy(); proc = null
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
        status = ""
        // the getevent child can outlive su; make sure it ends
        Thread { try { su("pkill -f 'getevent -l /dev/input'").waitFor() } catch (_: Exception) {} }.start()
    }
}
