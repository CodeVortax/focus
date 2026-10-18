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
    val icon: String = "",       // emoji shown on the choice tile
    val onClick: () -> Unit,
)

/** Colours of the gate screen. Follows the app's own theme choice (system / light / dark + accent). */
private class Pal(
    val bg: Int, val on: Int, val dim: Int, val track: Int, val primary: Int, val onPrimary: Int,
)

/** A calm "breathing" orb with a thin countdown arc around it. Shows a tick when [done]. */
private class RingView(c: Context, private val track: Int, private val color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val box = RectF()
    private var anim: ValueAnimator? = null
    private var breath: ValueAnimator? = null
    private var phase = 0f
    var fraction = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var done = false
        set(v) { field = v; invalidate() }

    fun animateTo(target: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 900
            addUpdateListener { fraction = it.animatedValue as Float }
            start()
        }
    }

    fun stop() { anim?.cancel(); anim = null; breath?.cancel(); breath = null }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startBreathing() }

    fun startBreathing() {
        if (breath == null) breath = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f
        // soft layered orb that swells and settles (about 4 s in, 4 s out)
        val base = r * 0.70f * (0.90f + 0.10f * phase)
        f.color = ColorUtils.setAlphaComponent(color, 26); cv.drawCircle(cx, cy, base * 1.18f, f)
        f.color = ColorUtils.setAlphaComponent(color, 48); cv.drawCircle(cx, cy, base * 1.02f, f)
        f.color = ColorUtils.setAlphaComponent(color, 84); cv.drawCircle(cx, cy, base * 0.82f, f)
        // thin countdown arc
        val w = 4f * d
        p.strokeWidth = w
        val m = w / 2f
        box.set(m, m, width - m, height - m)
        p.color = ColorUtils.setAlphaComponent(track, 160)
        cv.drawOval(box, p)
        if (fraction > 0f) { p.color = color; cv.drawArc(box, -90f, 360f * fraction, false, p) }
        if (done) {
            p.color = color; p.strokeWidth = 6f * d; p.strokeJoin = Paint.Join.ROUND
            val path = android.graphics.Path()
            path.moveTo(width * 0.34f, height * 0.52f); path.lineTo(width * 0.46f, height * 0.64f); path.lineTo(width * 0.68f, height * 0.38f)
            cv.drawPath(path, p)
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
        val raw = when {
            accent != 0 -> accent
            dynamic -> sys(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600, 0xFF4285F4.toInt())
            else -> 0xFF4285F4.toInt()
        }
        if (night) {
            val bg = if (dynamic) sys(android.R.color.system_neutral1_900, 0xFF131318.toInt()) else 0xFF131318.toInt()
            val on = if (dynamic) sys(android.R.color.system_neutral1_100, 0xFFE3E2E6.toInt()) else 0xFFE3E2E6.toInt()
            val dim = if (dynamic) sys(android.R.color.system_neutral2_200, 0xFFB0B4C0.toInt()) else 0xFFB0B4C0.toInt()
            val track = if (dynamic) sys(android.R.color.system_neutral2_700, 0xFF2A2B31.toInt()) else 0xFF2A2B31.toInt()
            val onP = if (dynamic) sys(android.R.color.system_accent1_800, 0xFFFFFFFF.toInt()) else 0xFFFFFFFF.toInt()
            return Pal(bg, on, dim, track, raw, onP)
        }
        // light: the same calm beige-grey as the app, never pure white; accent muted toward warm grey
        return Pal(0xFFD9D2C4.toInt(), 0xFF2B2A27.toInt(), 0xFF5E594F.toInt(), 0xFFC7BFAE.toInt(),
            ColorUtils.blendARGB(raw, 0xFF7A7467.toInt(), 0.22f), 0xFFFFFFFF.toInt())
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

    private fun face(bold: Boolean): Typeface? {
        val t = com.barq.focus.ui.theme.AppFont.typeface ?: return null
        return if (bold) Typeface.create(t, Typeface.BOLD) else t
    }

    fun show(
        tag: String, title: String, subtitle: String, delaySec: Int, choices: List<Choice>,
        typedLabel: String? = null, onTyped: ((String) -> Unit)? = null,
        leaveLabel: String = "Leave", onLeave: () -> Unit,
    ) {
        if (root != null || !Settings.canDrawOverlays(ctx)) return

        val pal = palette()
        val gap = dp(10)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(56), dp(22), dp(24))
        }
        fun tv(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t; textSize = sp; setTextColor(c)
            val f = face(bold)
            if (f != null) typeface = f else if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        // header: big title and the app as a soft chip
        col.addView(tv(title, 34f, pal.on, true))
        val chip = tv(subtitle, 14f, pal.dim).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(pal.track) }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) }
        }
        col.addView(chip)

        // the breathing orb with the countdown number (hidden until there is something to count)
        val ringBox = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply { topMargin = dp(20); bottomMargin = dp(4); gravity = Gravity.CENTER_HORIZONTAL }
            visibility = if (delaySec > 0) View.VISIBLE else View.GONE
        }
        val rv = RingView(ctx, pal.track, pal.primary)
        val num = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            setTextColor(pal.primary); gravity = Gravity.CENTER
            face(true)?.let { typeface = it } ?: setTypeface(typeface, Typeface.BOLD)
            text = delaySec.toString()
        }
        ringBox.addView(rv, FrameLayout.LayoutParams(-1, -1))
        ringBox.addView(num, FrameLayout.LayoutParams(-1, -1))
        col.addView(ringBox)
        ring = rv

        val status = tv("", 13f, pal.dim).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(10)) }
        col.addView(status, LinearLayout.LayoutParams(-1, -2))

        val locked = ArrayList<View>()
        fun setLocked(on: Boolean) = locked.forEach { it.isEnabled = !on; it.alpha = if (on) 0.4f else 1f }

        fun pick(ch: Choice) {
            if (ch.wait > 0) {
                setLocked(true)
                ringBox.visibility = View.VISIBLE
                rv.stop(); rv.done = false; rv.fraction = 1f
                rv.startBreathing()
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

        // reasons as tiles (two per row): emoji, name, and the minutes below
        fun tile(ch: Choice): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(96)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(ColorUtils.setAlphaComponent(ch.color, 40))
                setStroke(dp(1), ColorUtils.setAlphaComponent(ch.color, 120))
            }
            val parts = ch.label.split("  ·  ", limit = 2)
            if (ch.icon.isNotEmpty()) addView(tv(ch.icon, 24f, pal.on))
            addView(tv(parts[0], 16f, pal.on, true).apply { setPadding(0, dp(4), 0, 0) })
            val sub = parts.getOrNull(1) ?: if (ch.wait > 0) trf("Breathe… %ds", ch.wait) else ""
            if (sub.isNotEmpty()) addView(tv(sub, 13f, pal.dim))
            if (!ch.enabled) alpha = 0.4f
            else { locked.add(this); setOnClickListener { pick(ch) } }
        }

        fun addRow(items: List<Choice>) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEachIndexed { i, ch ->
                row.addView(tile(ch), LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = gap })
            }
            col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        }
        val pending = ArrayList<Choice>()
        choices.forEach { ch ->
            if (ch.label.contains("  ·  ")) {
                pending += ch
                if (pending.size == 2) { addRow(ArrayList(pending)); pending.clear() }
            } else {
                if (pending.isNotEmpty()) { addRow(ArrayList(pending)); pending.clear() }
                addRow(listOf(ch))
            }
        }
        if (pending.isNotEmpty()) addRow(ArrayList(pending))

        if (onTyped != null) {
            val input = EditText(ctx).apply {
                hint = tr("Or type why…")
                setHintTextColor(pal.dim)
                setTextColor(pal.on)
                face(false)?.let { typeface = it }
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 2
                setPadding(dp(20), 0, dp(20), 0)
                background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(pal.track) }
            }
            val go = Button(ctx).apply {
                text = "\u2192"; textSize = 22f; isAllCaps = false; stateListAnimator = null
                setTextColor(pal.onPrimary)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pal.primary) }
                setPadding(0, 0, 0, 0)
            }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
            row.addView(go, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = gap })
            col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
            if (typedLabel != null) col.addView(tv(typedLabel, 12f, pal.dim).apply { setPadding(dp(20), dp(6), 0, 0) })
            locked.add(input); locked.add(go)
            go.setOnClickListener {
                val r = input.text.toString().trim()
                if (r.isNotEmpty()) { hide(); onTyped(r) }
            }
        }

        var left = delaySec
        setLocked(left > 0)
        val countdown = object : Runnable {
            override fun run() {
                if (root == null) return
                if (left <= 0) {
                    status.text = ""; setLocked(false)
                    if (delaySec > 0) { rv.animateTo(0f); num.text = ""; rv.done = true }
                    return
                }
                status.text = trf("Wait %ds…", left)
                num.text = left.toString()
                rv.animateTo((left - 1).toFloat() / delaySec)
                left--
                h.postDelayed(this, 1000)
            }
        }

        // the way out is pinned to the bottom edge: wide, filled, always in reach
        val leave = Button(ctx).apply {
            text = tr(leaveLabel); isAllCaps = false; textSize = 17f; stateListAnimator = null
            setTextColor(pal.onPrimary)
            face(true)?.let { typeface = it } ?: setTypeface(typeface, Typeface.BOLD)
            background = pill(pal.primary)
            // hide first so the BACK/HOME actions land on the app underneath, not on this window
            setOnClickListener { hide(); onLeave() }
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(104))
            addView(col, FrameLayout.LayoutParams(-1, -2))
        }
        // opaque, full-bleed: the screen behind must not show through
        val frame = FrameLayout(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pal.bg, ColorUtils.blendARGB(pal.bg, pal.primary, 0.12f))
            )
            layoutDirection = if (I18n.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(leave, FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM).apply { setMargins(dp(22), 0, dp(22), dp(28)) })
        }
        val lp = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }

        try {
            wm.addView(frame, lp)
            root = frame
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
