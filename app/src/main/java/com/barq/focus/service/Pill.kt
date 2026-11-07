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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.barq.focus.MainActivity
import com.barq.focus.data.Store
import com.barq.focus.data.clockText
import com.barq.focus.data.tr

/**
 * A small "dynamic island" pill at the top of the screen, built from one plain TextView (no Compose, no extra library, so
 * it adds almost nothing to the APK). It only exists while there is something to say: the running subject timer, the
 * song that is playing, or a short flash for charging and ringer changes. Everything else leaves the camera alone.
 * Driven once a second by [WatchService]; tap opens Focus (or pauses/plays the song when music is showing).
 */
class Pill(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var view: TextView? = null
    private var lastKey = ""
    private var tick = 0

    // transient flash (charging / ringer)
    private var flashText = ""
    private var flashColor = 0
    private var flashUntil = 0L
    private var wasCharging: Boolean? = null
    private var lastRinger = -1

    // the subject the timer belongs to, looked up once per subject rather than every second
    private var cardId = -2L
    private var cardTitle = ""
    private var cardColor = 0

    private var controller: MediaController? = null
    private var mediaTitle: String? = null

    private class Show(val key: String, val text: String, val color: Int)

    /** Called every second. [gateUp] is true while one of Focus's own full-screen overlays is showing. */
    fun update(store: Store, fg: String?, gateUp: Boolean, screenOn: Boolean) {
        try {
            if (!store.pillOn || !screenOn || gateUp || !Settings.canDrawOverlays(ctx) ||
                ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT
            ) { hide(); return }
            val now = System.currentTimeMillis()
            if (tick++ % 2 == 0) watchSystem(now)
            val s = pick(store, now)
            if (s == null) { hide(); return }
            show(s)
        } catch (_: Exception) {
            hide()
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        lastKey = ""
        try { v.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(120).withEndAction { try { wm.removeView(v) } catch (_: Exception) {} }.start() }
        catch (_: Exception) { try { wm.removeView(v) } catch (_: Exception) {} }
    }

    // ---- what to show ----

    private fun pick(store: Store, now: Long): Show? {
        if (now < flashUntil) return Show("f$flashText", flashText, flashColor)

        val id = store.activeCardId
        if (id != -1L) {
            if (id != cardId) {
                val c = store.cards().firstOrNull { it.id == id }
                cardId = id; cardTitle = c?.title ?: ""; cardColor = c?.color ?: 0xFF4285F4.toInt()
            }
            val left = if (store.activePaused) store.activeRemaining else store.activeEnd - now
            val secs = (kotlin.math.abs(left) + 999) / 1000
            val text = "⏱ " + cardTitle + "  " + (if (left < 0) "+" else "") + clockText(secs) + (if (store.activePaused) "  ⏸" else "")
            return Show("t$id${store.activePaused}", text, cardColor)
        }
        cardId = -2L

        readMedia()
        val title = mediaTitle
        if (title != null) return Show("m$title", "♪ " + title, 0xFFAB47BC.toInt())
        return null
    }

    private fun readMedia() {
        try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val c = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            controller = c
            mediaTitle = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            controller = null; mediaTitle = null
        }
    }

    /** Short flashes when the charger is plugged in or the ringer mode changes. */
    private fun watchSystem(now: Long) {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (b != null) {
            val charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (wasCharging == false && charging) flash("⚡ " + (lvl * 100 / scale) + "%", 0xFF34A853.toInt(), now)
            wasCharging = charging
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val rm = am.ringerMode
        if (lastRinger != -1 && rm != lastRinger) {
            val t = when (rm) {
                AudioManager.RINGER_MODE_SILENT -> "🔕 " + tr("Silent")
                AudioManager.RINGER_MODE_VIBRATE -> "📳 " + tr("Vibrate")
                else -> "🔔 " + tr("Ring")
            }
            flash(t, 0xFFFBBC04.toInt(), now)
        }
        lastRinger = rm
    }

    private fun flash(text: String, color: Int, now: Long, ms: Long = 3000L) {
        flashText = text; flashColor = color; flashUntil = now + ms
    }

    /** Show [text] on the pill for a few seconds (used for the "session ends soon" warning). Only visible while the pill is switched on. */
    fun notice(text: String, color: Int, ms: Long = 4000L) = flash(text, color, System.currentTimeMillis(), ms)

    // ---- drawing ----

    private fun show(s: Show) {
        val existing = view
        if (existing != null) {
            existing.text = s.text
            if (s.key != lastKey) { lastKey = s.key; style(existing, s.color); pop(existing, 0.9f) }
            return
        }
        val tv = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            maxWidth = (ctx.resources.displayMetrics.widthPixels * 0.72f).toInt()
            text = s.text
            setOnClickListener { onTap() }
        }
        style(tv, s.color)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(tv, lp)
        view = tv
        lastKey = s.key
        tv.alpha = 0f
        pop(tv, 0.6f)
    }

    private fun style(tv: TextView, accent: Int) {
        tv.background = GradientDrawable().apply {
            setColor(0xF2000000.toInt())
            cornerRadius = dp(30).toFloat()
            setStroke(dp(1), (accent and 0x00FFFFFF) or 0x66000000)
        }
    }

    private fun pop(v: View, from: Float) {
        v.scaleX = from; v.scaleY = from
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun onTap() {
        val c = controller
        if (c != null && mediaTitle != null && Store(ctx).activeCardId == -1L) {
            try {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                return
            } catch (_: Exception) {}
        }
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {}
    }
}
