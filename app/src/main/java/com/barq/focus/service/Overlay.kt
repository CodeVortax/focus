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

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.barq.focus.data.I18n
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf

class Choice(
    val label: String,
    val color: Int,
    val enabled: Boolean = true,
    val wait: Int = 0,           // > 0: "breathe" countdown before onClick runs
    val onClick: () -> Unit,
)

/** Colours of the gate screen. Follows the app's own theme choice (system / light / dark + accent). */
private class Pal(
    val bg: Int, val on: Int, val dim: Int, val track: Int, val primary: Int, val onPrimary: Int,
)

/** The countdown ring: a track plus an arc that drains clockwise from the top. */
private class RingView(c: Context, private val track: Int, private val color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val box = RectF()
    private var anim: ValueAnimator? = null
    var fraction = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    fun animateTo(target: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 900
            addUpdateListener { fraction = it.animatedValue as Float }
            start()
        }
    }

    fun stop() { anim?.cancel(); anim = null }

    override fun onDraw(cv: Canvas) {
        val w = 8f * resources.displayMetrics.density
        p.strokeWidth = w
        val m = w / 2f
        box.set(m, m, width - m, height - m)
        p.color = track
        cv.drawOval(box, p)
        if (fraction > 0f) {
            p.color = color
            cv.drawArc(box, -90f, 360f * fraction, false, p)
        }
    }
}

class Overlay private constructor(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val h = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var ring: RingView? = null
    private var focus: AudioFocusRequest? = null

    var tag: String = ""
        private set
    val showing get() = root != null

    companion object {
        private var inst: Overlay? = null
        fun get(c: Context): Overlay = inst ?: Overlay(c.applicationContext).also { inst = it }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun sys(id: Int, fallback: Int): Int = try { ctx.getColor(id) } catch (_: Exception) { fallback }

    /** Same rules as FocusTheme: accent wins, then Material-You on Android 12+, then the plain palette. */
    private fun palette(): Pal {
        val s = Store(ctx)
        val night = when (s.themeMode) {
            1 -> false
            2 -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val accent = s.accent
        val dynamic = accent == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val bg = if (dynamic) sys(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10, if (night) 0xFF131318.toInt() else 0xFFF7F9FC.toInt())
                 else if (night) 0xFF131318.toInt() else 0xFFF7F9FC.toInt()
        val on = if (dynamic) sys(if (night) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900, if (night) 0xFFE3E2E6.toInt() else 0xFF1A1C1E.toInt())
                 else if (night) 0xFFE3E2E6.toInt() else 0xFF1A1C1E.toInt()
        val dim = if (dynamic) sys(if (night) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700, if (night) 0xFFB0B4C0.toInt() else 0xFF5F6368.toInt())
                  else if (night) 0xFFB0B4C0.toInt() else 0xFF5F6368.toInt()
        val track = if (dynamic) sys(if (night) android.R.color.system_neutral2_700 else android.R.color.system_neutral2_100, if (night) 0xFF2A2B31.toInt() else 0xFFE1E4EA.toInt())
                    else if (night) 0xFF2A2B31.toInt() else 0xFFE1E4EA.toInt()
        val primary = when {
            accent != 0 -> accent
            dynamic -> sys(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600, 0xFF4285F4.toInt())
            else -> 0xFF4285F4.toInt()
        }
        val onPrimary = if (dynamic) sys(if (night) android.R.color.system_accent1_800 else android.R.color.system_accent1_0, 0xFFFFFFFF.toInt()) else 0xFFFFFFFF.toInt()
        return Pal(bg, on, dim, track, primary, onPrimary)
    }

    private fun pill(color: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(28).toFloat(); setColor(color)
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    private fun btn(t: String, bg: Int, fg: Int, w: Int, stroke: Int = 0, h: Int = 52): Button = Button(ctx).apply {
        text = t
        isAllCaps = false
        setTextColor(fg)
        textSize = 16f
        stateListAnimator = null
        background = pill(bg, stroke)
        layoutParams = LinearLayout.LayoutParams(w, dp(h)).apply { topMargin = dp(10) }
    }

    fun show(
        tag: String, title: String, subtitle: String, delaySec: Int, choices: List<Choice>,
        typedLabel: String? = null, onTyped: ((String) -> Unit)? = null,
        leaveLabel: String = "Leave", onLeave: () -> Unit,
    ) {
        if (root != null || !Settings.canDrawOverlays(ctx)) return

        val pal = palette()
        // buttons are centred and capped at 320 dp, like the rest of the screen
        val bw = minOf(dp(320), ctx.resources.displayMetrics.widthPixels - dp(56))

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(48), dp(28), dp(40))
        }
        fun tv(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        col.addView(tv(title, 28f, pal.on, true))
        col.addView(tv(subtitle, 15f, pal.dim).apply { setPadding(0, dp(8), 0, 0) })

        // countdown ring (hidden when there is nothing to count until a "breathe" wait starts)
        val ringBox = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(180)).apply { topMargin = dp(28); bottomMargin = dp(8) }
            visibility = if (delaySec > 0) View.VISIBLE else View.GONE
        }
        val rv = RingView(ctx, pal.track, pal.primary)
        val num = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            setTextColor(pal.primary); gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            text = delaySec.toString()
        }
        ringBox.addView(rv, FrameLayout.LayoutParams(-1, -1))
        ringBox.addView(num, FrameLayout.LayoutParams(-1, -1))
        col.addView(ringBox)
        ring = rv

        // the way out sits right under the ring: big, filled, easiest thing to hit
        val leave = btn(tr(leaveLabel), pal.primary, pal.onPrimary, bw, h = 56)
        // hide first so the BACK/HOME actions land on the app underneath, not on this window
        leave.setOnClickListener { hide(); onLeave() }
        col.addView(leave)

        val status = tv("", 13f, pal.dim).apply { setPadding(0, dp(12), 0, dp(2)) }
        col.addView(status)

        val locked = ArrayList<View>()
        fun setLocked(on: Boolean) = locked.forEach { it.isEnabled = !on; it.alpha = if (on) 0.4f else 1f }

        choices.forEach { ch ->
            // tonal: a light wash of the choice colour with a coloured outline, readable in both themes
            val b = btn(ch.label, ColorUtils.setAlphaComponent(ch.color, 38), pal.on, bw, stroke = ch.color)
            col.addView(b)
            if (!ch.enabled) {
                b.isEnabled = false; b.alpha = 0.4f
            } else {
                locked.add(b)
                b.setOnClickListener {
                    if (ch.wait > 0) {
                        setLocked(true)
                        ringBox.visibility = View.VISIBLE
                        rv.stop(); rv.fraction = 1f
                        num.text = ch.wait.toString()
                        var n = ch.wait
                        val r = object : Runnable {
                            override fun run() {
                                if (root == null) return
                                if (n <= 0) { hide(); ch.onClick(); return }
                                status.text = trf("Breathe… %ds", n)
                                num.text = n.toString()
                                rv.animateTo((n - 1).toFloat() / ch.wait)
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
                setHintTextColor(pal.dim)
                setTextColor(pal.on)
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(bw, -2).apply { topMargin = dp(16) }
            }
            col.addView(input); locked.add(input)
            val go = btn(typedLabel ?: "Go", pal.track, pal.on, bw)
            go.setOnClickListener {
                val r = input.text.toString().trim()
                if (r.isNotEmpty()) { hide(); onTyped(r) }
            }
            col.addView(go); locked.add(go)
        }

        var left = delaySec
        setLocked(left > 0)
        val countdown = object : Runnable {
            override fun run() {
                if (root == null) return
                if (left <= 0) {
                    status.text = ""; setLocked(false)
                    if (delaySec > 0) { rv.animateTo(0f); num.text = "\u2713" }
                    return
                }
                status.text = trf("Wait %ds…", left)
                num.text = left.toString()
                rv.animateTo((left - 1).toFloat() / delaySec)
                left--
                h.postDelayed(this, 1000)
            }
        }

        // opaque, full-bleed: the screen behind must not show through
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(pal.bg)
            layoutDirection = if (I18n.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            isFillViewport = true
            addView(col, FrameLayout.LayoutParams(-1, -2))
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
            ring = null
        }
    }

    fun hide() {
        h.removeCallbacksAndMessages(null)
        ring?.stop(); ring = null
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
