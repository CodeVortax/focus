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

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.barq.focus.data.Config
import com.barq.focus.data.I18n
import com.barq.focus.data.tr
import com.barq.focus.data.trf

class Choice(
    val label: String,
    val color: Int,
    val enabled: Boolean = true,
    val wait: Int = 0,           // > 0: "breathe" countdown before onClick runs
    val onClick: () -> Unit,
)

/** Full-screen gate. Plain Views so it works from any Service. One instance per process. */
class Overlay private constructor(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val h = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var focus: AudioFocusRequest? = null

    var tag: String = ""
        private set
    val showing get() = root != null

    companion object {
        private var inst: Overlay? = null
        fun get(c: Context): Overlay = inst ?: Overlay(c.applicationContext).also { inst = it }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun btn(t: String, color: Int): Button = Button(ctx).apply {
        text = t
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(color) }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
    }

    fun show(
        tag: String, title: String, subtitle: String, delaySec: Int, choices: List<Choice>,
        typedLabel: String? = null, onTyped: ((String) -> Unit)? = null,
        leaveLabel: String = "Leave", onLeave: () -> Unit,
    ) {
        if (root != null || !Settings.canDrawOverlays(ctx)) return

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(72), dp(28), dp(48))
        }
        fun tv(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        col.addView(tv(title, 30f, 0xFFFFFFFF.toInt(), true))
        col.addView(tv(subtitle, 15f, 0xFFB0B4C0.toInt()))
        val status = tv("", 16f, 0xFFFBBC04.toInt()).apply { setPadding(0, dp(14), 0, dp(4)) }
        col.addView(status)

        val locked = ArrayList<View>()
        fun setLocked(on: Boolean) = locked.forEach { it.isEnabled = !on; it.alpha = if (on) 0.4f else 1f }

        choices.forEach { ch ->
            val b = btn(ch.label, ch.color)
            col.addView(b)
            if (!ch.enabled) {
                b.isEnabled = false; b.alpha = 0.4f
            } else {
                locked.add(b)
                b.setOnClickListener {
                    if (ch.wait > 0) {
                        setLocked(true)
                        var n = ch.wait
                        val r = object : Runnable {
                            override fun run() {
                                if (root == null) return
                                if (n <= 0) { hide(); ch.onClick(); return }
                                status.text = trf("Breathe… %ds", n)
                                n--
                                h.postDelayed(this, 1000)
                            }
                        }
                        h.post(r)
                    } else {
                        hide(); ch.onClick()
                    }
                }
            }
        }

        if (onTyped != null) {
            val input = EditText(ctx).apply {
                hint = tr("Or type why…")
                setHintTextColor(0xFF80848F.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            }
            col.addView(input); locked.add(input)
            val go = btn(typedLabel ?: "Go", 0xFF3C4043.toInt())
            go.setOnClickListener {
                val r = input.text.toString().trim()
                if (r.isNotEmpty()) { hide(); onTyped(r) }
            }
            col.addView(go); locked.add(go)
        }

        val leave = btn(tr(leaveLabel), 0xFF1E1F24.toInt())
        // hide first so the BACK/HOME actions land on the app underneath, not on this window
        leave.setOnClickListener { hide(); onLeave() }
        col.addView(leave)

        var left = delaySec
        setLocked(left > 0)
        val countdown = object : Runnable {
            override fun run() {
                if (root == null) return
                if (left <= 0) { status.text = ""; setLocked(false); return }
                status.text = trf("Wait %ds…", left)
                left--
                h.postDelayed(this, 1000)
            }
        }

        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(0xF2101018.toInt())
            layoutDirection = if (I18n.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            isFillViewport = true
            addView(col)
        }
        val lp = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }

        try {
            wm.addView(scroll, lp)
            root = scroll
            this.tag = tag
            grabAudio()
            h.post(countdown)
        } catch (_: Exception) {
            root = null
        }
    }

    fun hide() {
        h.removeCallbacksAndMessages(null)
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        dropAudio()
    }

    // Pause whatever is playing behind the gate (YouTube keeps playing under an overlay otherwise).
    private fun grabAudio() {
        try {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            am.requestAudioFocus(r)
            focus = r
        } catch (_: Exception) {}
    }

    private fun dropAudio() {
        try { focus?.let { am.abandonAudioFocusRequest(it) } } catch (_: Exception) {}
        focus = null
    }
}
