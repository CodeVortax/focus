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
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.barq.focus.MainActivity
import com.barq.focus.data.NotePlayer
import com.barq.focus.data.Store
import com.barq.focus.data.clockText
import com.barq.focus.data.tr
import kotlin.math.abs
import kotlin.math.min

/** Cutout geometry modes for Focus's top island pill. */
enum class PillShape(val code: Int, val label: String) {
    CENTER(0, "Center"),
    WATERDROP(1, "Waterdrop"),
    V_SHAPE(2, "V shape"),
    SIDE_LEFT(3, "Left"),
    SIDE_RIGHT(4, "Right");

    val isFlushTop: Boolean get() = this == WATERDROP || this == V_SHAPE
    val hasCenterCameraGap: Boolean get() = this == WATERDROP || this == V_SHAPE

    companion object {
        fun fromCode(code: Int): PillShape = entries.firstOrNull { it.code == code } ?: CENTER
    }
}

enum class PillKind { NOTICE, TIMER, PROJECT, FOCUS_MUSIC, FOCUS_NOTE }

data class PillModel(
    val key: String,
    val kind: PillKind,
    val icon: String,
    val label: String,
    val trailing: String,
    val accentColor: Int,
    val progress: Float? = null,
    val goTarget: String = "home",
    val paused: Boolean = false,
)

/**
 * Pure Focus-only selector for what the top pill displays.
 * Never inspects other apps' media sessions, battery state, ringer mode, or external notifications.
 */
object PillSelector {
    data class Input(
        val now: Long,
        val flashText: String,
        val flashColor: Int,
        val flashUntil: Long,
        val activeCardId: Long,
        val cardTitle: String,
        val cardColor: Int,
        val cardMinutes: Int,
        val activeEnd: Long,
        val activePaused: Boolean,
        val activeRemaining: Long,
        val activeProjectId: Long,
        val projectName: String,
        val bankText: String,
        val focusMusicPlaying: Boolean,
        val focusMusicTitle: String?,
        val focusNotePlaying: Boolean,
        val focusNoteTitle: String?,
    )

    fun select(input: Input): PillModel? {
        if (input.now < input.flashUntil && input.flashText.isNotBlank()) {
            return PillModel(
                key = "f:${input.flashText}",
                kind = PillKind.NOTICE,
                icon = "",
                label = input.flashText,
                trailing = "",
                accentColor = input.flashColor,
                goTarget = "home"
            )
        }
        if (input.activeCardId != -1L) {
            val left = if (input.activePaused) input.activeRemaining else input.activeEnd - input.now
            val secs = (abs(left) + 999L) / 1000L
            val clock = (if (left < 0) "+" else "") + clockText(secs)
            val totalMs = (input.cardMinutes.coerceAtLeast(1) * 60_000L).toFloat()
            val frac = when {
                left <= 0L -> 1f
                else -> (1f - (left.toFloat() / totalMs)).coerceIn(0f, 1f)
            }
            return PillModel(
                key = "t:${input.activeCardId}:${input.activePaused}",
                kind = PillKind.TIMER,
                icon = if (input.activePaused) "⏸" else "⏱",
                label = input.cardTitle.ifBlank { "Focus" },
                trailing = clock,
                accentColor = input.cardColor,
                progress = frac,
                goTarget = "card:active",
                paused = input.activePaused
            )
        }
        if (input.activeProjectId != -1L && input.projectName.isNotBlank()) {
            return PillModel(
                key = "p:${input.activeProjectId}",
                kind = PillKind.PROJECT,
                icon = "◈",
                label = input.projectName,
                trailing = "${input.bankText}m",
                accentColor = 0xFF34A853.toInt(),
                goTarget = "projects"
            )
        }
        if (input.focusMusicPlaying) {
            val title = input.focusMusicTitle?.takeIf { it.isNotBlank() } ?: "Focus Music"
            return PillModel(
                key = "fm:$title",
                kind = PillKind.FOCUS_MUSIC,
                icon = "♪",
                label = title,
                trailing = "▶",
                accentColor = 0xFFAB47BC.toInt(),
                goTarget = "music"
            )
        }
        if (input.focusNotePlaying) {
            val title = input.focusNoteTitle?.takeIf { it.isNotBlank() } ?: "Voice note"
            return PillModel(
                key = "fn:$title",
                kind = PillKind.FOCUS_NOTE,
                icon = "🎙",
                label = title,
                trailing = "▶",
                accentColor = 0xFF4285F4.toInt(),
                goTarget = "notes"
            )
        }
        return null
    }
}

/**
 * Focus-only top island pill supporting Center, Waterdrop, V-shape, Left, and Right cutouts.
 * Displays only Focus's own study timers, active projects, Focus audio/music, and Focus break alerts.
 */
class Pill(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * d).toInt()
    private fun dpf(v: Float): Float = v * d

    private var root: PillHostView? = null
    private var lastKey = ""
    private var lastGeoSig = ""
    private var lastModel: PillModel? = null
    private var expanded = false
    private var expandedUntil = 0L

    private var flashText = ""
    private var flashColor = 0xFFFBBC04.toInt()
    private var flashUntil = 0L

    private var cachedCardId = -2L
    private var cachedCardTitle = ""
    private var cachedCardColor = 0xFF4285F4.toInt()
    private var cachedCardMinutes = 25

    private var cachedProjId = -2L
    private var cachedProjName = ""

    /** Called every second by WatchService. Never touches external media or system state. */
    fun update(store: Store, @Suppress("UNUSED_PARAMETER") fg: String?, gateUp: Boolean, screenOn: Boolean) {
        try {
            val orientation = ctx.resources.configuration.orientation
            if (!store.pillOn || !screenOn || gateUp || !Settings.canDrawOverlays(ctx) ||
                (!store.pillLandscape && orientation != Configuration.ORIENTATION_PORTRAIT)
            ) {
                hide()
                return
            }
            val now = System.currentTimeMillis()
            if (expanded && now > expandedUntil) expanded = false

            val model = buildModel(store, now)
            if (model == null) {
                hide()
                return
            }
            val shape = PillShape.fromCode(store.pillShape)
            val widthDp = store.pillWidthDp
            val heightDp = store.pillHeightDp
            val topOffsetDp = store.pillTopOffsetDp
            render(store, model, shape, widthDp, heightDp, topOffsetDp)
        } catch (_: Exception) {
            hide()
        }
    }

    fun hide() {
        val v = root ?: return
        root = null
        lastKey = ""
        lastGeoSig = ""
        lastModel = null
        expanded = false
        try {
            v.animate().alpha(0f).scaleX(0.75f).scaleY(0.75f).setDuration(120)
                .withEndAction { try { wm.removeView(v) } catch (_: Exception) {} }.start()
        } catch (_: Exception) {
            try { wm.removeView(v) } catch (_: Exception) {}
        }
    }

    /** Show a Focus alert (break warning, session ends soon) on the pill while enabled. */
    fun notice(text: String, color: Int, ms: Long = 4000L) {
        flashText = text
        flashColor = color
        flashUntil = System.currentTimeMillis() + ms
    }

    private fun buildModel(store: Store, now: Long): PillModel? {
        val cid = store.activeCardId
        if (cid != -1L && cid != cachedCardId) {
            val c = store.cards().firstOrNull { it.id == cid }
            cachedCardId = cid
            cachedCardTitle = c?.title ?: ""
            cachedCardColor = c?.color ?: 0xFF4285F4.toInt()
            cachedCardMinutes = c?.minutes ?: 25
        } else if (cid == -1L) {
            cachedCardId = -2L
        }

        val pid = store.activeProject
        if (pid != -1L && pid != cachedProjId) {
            cachedProjId = pid
            cachedProjName = store.projects().firstOrNull { it.id == pid }?.name ?: ""
        } else if (pid == -1L) {
            cachedProjId = -2L
            cachedProjName = ""
        }

        val musicPlaying = Music.isPlaying && Music.currentId != -1L
        val musicTitle = if (musicPlaying) Music.player?.mediaMetadata?.title?.toString() else null
        val notePlaying = NotePlayer.isPlaying && NotePlayer.current.isNotEmpty()
        val noteTitle = if (notePlaying) NotePlayer.title else null

        return PillSelector.select(
            PillSelector.Input(
                now = now,
                flashText = flashText,
                flashColor = flashColor,
                flashUntil = flashUntil,
                activeCardId = cid,
                cardTitle = cachedCardTitle,
                cardColor = cachedCardColor,
                cardMinutes = cachedCardMinutes,
                activeEnd = store.activeEnd,
                activePaused = store.activePaused,
                activeRemaining = store.activeRemaining,
                activeProjectId = pid,
                projectName = cachedProjName,
                bankText = store.bankText(),
                focusMusicPlaying = musicPlaying,
                focusMusicTitle = musicTitle,
                focusNotePlaying = notePlaying,
                focusNoteTitle = noteTitle,
            )
        )
    }

    private fun render(
        store: Store,
        model: PillModel,
        shape: PillShape,
        widthDp: Int,
        heightDp: Int,
        topOffsetDp: Int,
    ) {
        val screenW = ctx.resources.displayMetrics.widthPixels
        val maxCompactPx = (screenW * 0.86f).toInt()
        val targetW = if (expanded) min(dp(256), (screenW * 0.88f).toInt()) else min(dp(widthDp), maxCompactPx)
        val targetH = if (expanded) dp(76) else dp(if (shape.isFlushTop) maxOf(heightDp, 34) else heightDp)
        val geoSig = "${shape.code}|$targetW|$targetH|$topOffsetDp|$expanded"

        lastModel = model
        val existing = root
        if (existing != null && geoSig == lastGeoSig) {
            existing.bind(store, model, shape, expanded)
            if (model.key != lastKey) {
                lastKey = model.key
                pop(existing, 0.92f)
            }
            return
        }

        if (existing != null) {
            try { wm.removeView(existing) } catch (_: Exception) {}
            root = null
        }

        val host = PillHostView(ctx).apply {
            setOnClickListener {
                val cur = lastModel ?: return@setOnClickListener
                if (expanded || cur.kind == PillKind.NOTICE) {
                    expanded = false
                    openFocus(cur.goTarget)
                } else {
                    expanded = true
                    expandedUntil = System.currentTimeMillis() + 5000L
                    render(store, cur, shape, widthDp, heightDp, topOffsetDp)
                }
            }
            setOnLongClickListener {
                expanded = false
                openFocus(lastModel?.goTarget ?: "home")
                true
            }
        }
        host.bind(store, model, shape, expanded)

        val gravity = when {
            expanded -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            shape == PillShape.SIDE_LEFT -> Gravity.TOP or Gravity.START
            shape == PillShape.SIDE_RIGHT -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val lp = WindowManager.LayoutParams(
            targetW,
            targetH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = if (!expanded && (shape == PillShape.SIDE_LEFT || shape == PillShape.SIDE_RIGHT)) dp(10) else 0
            this.y = if (shape.isFlushTop && !expanded) 0 else dp(topOffsetDp)
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(host, lp)
        root = host
        lastGeoSig = geoSig
        lastKey = model.key
        host.alpha = 0f
        pop(host, if (existing == null) 0.72f else 0.94f)
    }

    private fun pop(v: View, from: Float) {
        v.scaleX = from
        v.scaleY = from
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(OvershootInterpolator(1.35f)).start()
    }

    private fun openFocus(go: String) {
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("go", go)
            )
        } catch (_: Exception) {}
    }

    private inner class PillHostView(ctx: Context) : FrameLayout(ctx) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xF5000000.toInt()
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(1.5f)
        }
        private val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(1.2f)
            color = 0x44FFFFFF
        }
        private val outlinePath = Path()
        private val fillPath = Path()
        private val boundsRect = RectF()

        private var curShape = PillShape.CENTER
        private var curExpanded = false
        private var curAccent = 0xFF4285F4.toInt()
        private var curProgress: Float? = null

        private val compactRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        private val leftText = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        private val camSpacer = View(ctx)
        private val rightText = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(0xFFE8EAED.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        private val expandedBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        private val expTitle = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val expActionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        private val btnA = actionChip()
        private val btnB = actionChip()
        private val btnOpen = actionChip()

        init {
            setWillNotDraw(false)
            compactRow.addView(leftText, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            compactRow.addView(camSpacer, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT))
            compactRow.addView(rightText, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(compactRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            expActionRow.addView(btnA, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            expActionRow.addView(btnB, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            expActionRow.addView(btnOpen, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            expandedBox.addView(expTitle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            expandedBox.addView(expActionRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(expandedBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        private fun actionChip() = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }

        fun bind(store: Store, model: PillModel, shape: PillShape, isExpanded: Boolean) {
            curShape = shape
            curExpanded = isExpanded
            curAccent = model.accentColor
            curProgress = model.progress

            if (isExpanded) {
                compactRow.visibility = View.GONE
                expandedBox.visibility = View.VISIBLE
                expTitle.text = buildString {
                    if (model.icon.isNotEmpty()) append(model.icon).append(' ')
                    append(model.label)
                    if (model.trailing.isNotEmpty()) append("  ·  ").append(model.trailing)
                }
                bindExpandedActions(store, model)
            } else {
                expandedBox.visibility = View.GONE
                compactRow.visibility = View.VISIBLE
                val startPad = when (shape) {
                    PillShape.SIDE_LEFT -> dp(36)
                    PillShape.WATERDROP, PillShape.V_SHAPE -> dp(14)
                    else -> dp(12)
                }
                val endPad = when (shape) {
                    PillShape.SIDE_RIGHT -> dp(36)
                    PillShape.WATERDROP, PillShape.V_SHAPE -> dp(14)
                    else -> dp(12)
                }
                compactRow.setPadding(startPad, if (shape.isFlushTop) dp(2) else 0, endPad, if (shape.isFlushTop) dp(2) else 0)
                val showSplitGap = shape.hasCenterCameraGap && model.trailing.isNotEmpty()
                camSpacer.layoutParams = (camSpacer.layoutParams as LinearLayout.LayoutParams).apply {
                    width = if (showSplitGap) dp(34) else dp(6)
                }
                leftText.text = if (model.icon.isNotEmpty()) "${model.icon} ${model.label}" else model.label
                rightText.text = model.trailing
                rightText.visibility = if (model.trailing.isEmpty()) View.GONE else View.VISIBLE
                rightText.setTextColor(blendAccent(model.accentColor))
            }
            invalidate()
        }

        private fun bindExpandedActions(store: Store, model: PillModel) {
            btnA.visibility = View.GONE
            btnB.visibility = View.GONE
            btnOpen.visibility = View.VISIBLE
            btnOpen.text = tr("Open")
            btnOpen.setOnClickListener { expanded = false; openFocus(model.goTarget) }

            when (model.kind) {
                PillKind.TIMER -> {
                    val lockedStrict = store.launcherLockOnTimer && store.launcherStrict
                    if (!lockedStrict) {
                        btnA.visibility = View.VISIBLE
                        btnA.text = tr(if (model.paused) "Resume" else "Pause")
                        btnA.setOnClickListener {
                            if (store.activePaused) store.resumeActive() else store.pauseActive()
                            TimerAlarm.reschedule(ctx, store)
                            Notifier.syncTimer(ctx, store)
                            expandedUntil = System.currentTimeMillis() + 5000L
                            update(store, null, gateUp = false, screenOn = true)
                        }
                    }
                    btnB.visibility = View.VISIBLE
                    btnB.text = tr("+5 min")
                    btnB.setOnClickListener {
                        store.addActiveTime(5 * 60_000L)
                        TimerAlarm.reschedule(ctx, store)
                        Notifier.syncTimer(ctx, store)
                        expandedUntil = System.currentTimeMillis() + 5000L
                        update(store, null, gateUp = false, screenOn = true)
                    }
                }
                PillKind.FOCUS_MUSIC -> {
                    btnA.visibility = View.VISIBLE
                    btnA.text = tr(if (Music.isPlaying) "Pause" else "Play")
                    btnA.setOnClickListener {
                        val p = Music.player
                        if (p != null) { if (p.isPlaying) p.pause() else p.play() }
                        expandedUntil = System.currentTimeMillis() + 5000L
                        update(store, null, gateUp = false, screenOn = true)
                    }
                    btnB.visibility = View.VISIBLE
                    btnB.text = "⏭"
                    btnB.setOnClickListener {
                        Music.player?.seekToNextMediaItem()
                        expandedUntil = System.currentTimeMillis() + 5000L
                        update(store, null, gateUp = false, screenOn = true)
                    }
                }
                PillKind.FOCUS_NOTE -> {
                    btnA.visibility = View.VISIBLE
                    btnA.text = tr(if (NotePlayer.isPlaying) "Pause" else "Resume")
                    btnA.setOnClickListener {
                        if (NotePlayer.isPlaying) NotePlayer.pause() else NotePlayer.resume()
                        expandedUntil = System.currentTimeMillis() + 5000L
                        update(store, null, gateUp = false, screenOn = true)
                    }
                }
                else -> Unit
            }
        }

        private fun blendAccent(accent: Int): Int {
            val r = (Color.red(accent) + 255) / 2
            val g = (Color.green(accent) + 255) / 2
            val b = (Color.blue(accent) + 255) / 2
            return Color.rgb(r, g, b)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val sw = strokePaint.strokeWidth
            val halfSw = sw / 2f
            strokePaint.color = (curAccent and 0x00FFFFFF) or 0x66000000

            if (curExpanded || !curShape.isFlushTop) {
                val r = if (curExpanded) dpf(24f) else h / 2f
                boundsRect.set(halfSw, halfSw, w - halfSw, h - halfSw)
                fillPath.reset()
                fillPath.addRoundRect(boundsRect, r, r, Path.Direction.CW)
                canvas.drawPath(fillPath, bgPaint)
                canvas.drawPath(fillPath, strokePaint)

                if (!curExpanded && (curShape == PillShape.SIDE_LEFT || curShape == PillShape.SIDE_RIGHT)) {
                    val camR = dpf(8f)
                    val cx = if (curShape == PillShape.SIDE_LEFT) dpf(18f) else w - dpf(18f)
                    canvas.drawCircle(cx, h / 2f, camR, camPaint)
                }
            } else if (curShape == PillShape.WATERDROP) {
                buildWaterdropPaths(w, h, halfSw)
                canvas.drawPath(fillPath, bgPaint)
                canvas.drawPath(outlinePath, strokePaint)
                canvas.drawCircle(w / 2f, dpf(13f), dpf(6.5f), camPaint)
            } else {
                buildVShapePaths(w, h, halfSw)
                canvas.drawPath(fillPath, bgPaint)
                canvas.drawPath(outlinePath, strokePaint)
                canvas.drawCircle(w / 2f, dpf(12f), dpf(6f), camPaint)
            }

            val prog = curProgress
            if (prog != null && prog > 0f) {
                canvas.save()
                canvas.clipPath(fillPath)
                progPaint.color = (curAccent and 0x00FFFFFF) or 0xCC000000.toInt()
                val barH = dpf(2.5f)
                canvas.drawRect(0f, h - barH, w * prog.coerceIn(0f, 1f), h, progPaint)
                canvas.restore()
            }
        }

        private fun buildWaterdropPaths(w: Float, h: Float, halfSw: Float) {
            val ear = min(dpf(11f), h * 0.34f).coerceAtLeast(1f)
            val bottom = (h - halfSw).coerceAtLeast(ear + 2f)
            val botR = min(dpf(15f), min((w - 2f * ear) / 2f, (bottom - ear) * 0.85f)).coerceAtLeast(1f)

            fillPath.reset()
            fillPath.moveTo(0f, 0f)
            fillPath.quadTo(ear, 0f, ear, ear)
            fillPath.lineTo(ear, bottom - botR)
            fillPath.quadTo(ear, bottom, ear + botR, bottom)
            fillPath.lineTo(w - ear - botR, bottom)
            fillPath.quadTo(w - ear, bottom, w - ear, bottom - botR)
            fillPath.lineTo(w - ear, ear)
            fillPath.quadTo(w - ear, 0f, w, 0f)
            fillPath.close()

            outlinePath.reset()
            outlinePath.moveTo(halfSw, halfSw)
            outlinePath.quadTo(ear, halfSw, ear, ear)
            outlinePath.lineTo(ear, bottom - botR)
            outlinePath.quadTo(ear, bottom, ear + botR, bottom)
            outlinePath.lineTo(w - ear - botR, bottom)
            outlinePath.quadTo(w - ear, bottom, w - ear, bottom - botR)
            outlinePath.lineTo(w - ear, ear)
            outlinePath.quadTo(w - ear, halfSw, w - halfSw, halfSw)
        }

        private fun buildVShapePaths(w: Float, h: Float, halfSw: Float) {
            val ear = min(dpf(6f), w * 0.08f)
            val inset = min(dpf(14f), w * 0.14f)
            val bottom = (h - halfSw).coerceAtLeast(8f)
            val cornerR = min(dpf(14f), (w - 2f * inset) * 0.35f)
            val midY = bottom * 0.56f

            fillPath.reset()
            fillPath.moveTo(0f, 0f)
            fillPath.quadTo(ear, 0f, inset, midY)
            fillPath.quadTo(inset + dp(2), bottom, inset + cornerR, bottom)
            fillPath.lineTo(w - inset - cornerR, bottom)
            fillPath.quadTo(w - inset - dp(2), bottom, w - inset, midY)
            fillPath.quadTo(w - ear, 0f, w, 0f)
            fillPath.close()

            outlinePath.reset()
            outlinePath.moveTo(halfSw, halfSw)
            outlinePath.quadTo(ear, halfSw, inset, midY)
            outlinePath.quadTo(inset + dp(2), bottom, inset + cornerR, bottom)
            outlinePath.lineTo(w - inset - cornerR, bottom)
            outlinePath.quadTo(w - inset - dp(2), bottom, w - inset, midY)
            outlinePath.quadTo(w - ear, halfSw, w - halfSw, halfSw)
        }
    }
}
