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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.barq.focus.MainActivity
import com.barq.focus.data.NotePlayer
import com.barq.focus.data.Store
import com.barq.focus.data.clockText
import com.barq.focus.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    val artist: String = "",
    val mediaPlaying: Boolean = false,
    val mediaId: Long = -1L,
    val artworkUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class PillDualState(
    val primary: PillModel,
    val secondary: PillModel? = null,
)

/**
 * Pure Focus-only selector for what the top island pill displays.
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
        val focusMusicArtist: String? = null,
        val focusMusicActive: Boolean = focusMusicPlaying,
        val focusMusicId: Long = -1L,
        val focusMusicArtworkUri: String? = null,
        val focusMusicPosMs: Long = 0L,
        val focusMusicDurMs: Long = 0L,
    )

    fun select(input: Input): PillModel? = selectDual(input)?.primary

    fun selectDual(input: Input): PillDualState? {
        val noticeModel = if (input.now < input.flashUntil && input.flashText.isNotBlank()) {
            PillModel(
                key = "f:${input.flashText}",
                kind = PillKind.NOTICE,
                icon = "notice",
                label = input.flashText,
                trailing = "",
                accentColor = input.flashColor,
                goTarget = "home"
            )
        } else null

        val timerModel = if (input.activeCardId != -1L) {
            val left = if (input.activePaused) input.activeRemaining else input.activeEnd - input.now
            val secs = (abs(left) + 999L) / 1000L
            val clock = (if (left < 0) "+" else "") + clockText(secs)
            val totalMs = (input.cardMinutes.coerceAtLeast(1) * 60_000L).toFloat()
            val frac = when {
                left <= 0L -> 1f
                else -> (1f - (left.toFloat() / totalMs)).coerceIn(0f, 1f)
            }
            PillModel(
                key = "t:${input.activeCardId}:${input.activePaused}",
                kind = PillKind.TIMER,
                icon = if (input.activePaused) "paused" else "timer",
                label = input.cardTitle.ifBlank { "Focus" },
                trailing = clock,
                accentColor = input.cardColor,
                progress = frac,
                goTarget = "card:active",
                paused = input.activePaused
            )
        } else null

        val projectModel = if (input.activeProjectId != -1L && input.projectName.isNotBlank()) {
            PillModel(
                key = "p:${input.activeProjectId}",
                kind = PillKind.PROJECT,
                icon = "project",
                label = input.projectName,
                trailing = "${input.bankText}m",
                accentColor = 0xFF34A853.toInt(),
                goTarget = "projects"
            )
        } else null

        val musicActive = input.focusMusicPlaying || (input.focusMusicActive && input.focusMusicId != -1L && timerModel == null)
        val musicModel = if (input.focusMusicPlaying || musicActive) {
            val title = input.focusMusicTitle?.takeIf { it.isNotBlank() } ?: "Focus Music"
            val artist = input.focusMusicArtist?.takeIf { it.isNotBlank() } ?: "Focus Audio"
            PillModel(
                key = "fm:${input.focusMusicId}:$title",
                kind = PillKind.FOCUS_MUSIC,
                icon = "music",
                label = title,
                trailing = clockText((input.focusMusicPosMs.coerceAtLeast(0L)) / 1000L),
                accentColor = 0xFFAB47BC.toInt(),
                progress = if (input.focusMusicDurMs > 0L) (input.focusMusicPosMs.toFloat() / input.focusMusicDurMs).coerceIn(0f, 1f) else null,
                goTarget = "music",
                paused = !input.focusMusicPlaying,
                artist = artist,
                mediaPlaying = input.focusMusicPlaying,
                mediaId = input.focusMusicId,
                artworkUri = input.focusMusicArtworkUri,
                positionMs = input.focusMusicPosMs,
                durationMs = input.focusMusicDurMs,
            )
        } else null

        val noteModel = if (input.focusNotePlaying) {
            val title = input.focusNoteTitle?.takeIf { it.isNotBlank() } ?: "Voice note"
            PillModel(
                key = "fn:$title",
                kind = PillKind.FOCUS_NOTE,
                icon = "note",
                label = title,
                trailing = "REC",
                accentColor = 0xFF4285F4.toInt(),
                goTarget = "notes",
                mediaPlaying = true
            )
        } else null

        val ordered = listOfNotNull(noticeModel, timerModel, projectModel, musicModel, noteModel)
        if (ordered.isEmpty()) return null
        val primary = ordered[0]
        val secondary = when (primary.kind) {
            PillKind.NOTICE -> timerModel ?: musicModel ?: noteModel
            PillKind.TIMER -> musicModel ?: noteModel
            PillKind.PROJECT -> musicModel ?: noteModel
            PillKind.FOCUS_MUSIC, PillKind.FOCUS_NOTE -> null
        }
        return PillDualState(primary = primary, secondary = secondary)
    }
}

private class PillLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onAttach() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDetach() {
        try {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (_: Exception) {}
        store.clear()
    }
}

private class PillOverlayUiState {
    var dual by mutableStateOf<PillDualState?>(null)
    var shape by mutableStateOf(PillShape.CENTER)
    var widthDp by mutableIntStateOf(180)
    var heightDp by mutableIntStateOf(34)
    var topOffsetDp by mutableIntStateOf(0)
    var expanded by mutableStateOf(false)
    var focusSecondaryOnExpand by mutableStateOf(false)
    var strictLocked by mutableStateOf(false)
}

/**
 * Focus-only Dynamic Island overlay inspired by droidtools, using Jetpack Compose springs,
 * album art / vinyl music player, detached secondary circle bubble for dual Timer + Audio,
 * and accessibility/system overlay placement above the status bar.
 */
class Pill(private val ctx: Context) {
    private val h = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * d).toInt()

    private var activeWm: WindowManager? = null
    private var root: OutsideAwareHost? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: PillLifecycleOwner? = null
    private var lp: WindowManager.LayoutParams? = null
    private var usingA11yOverlay = false
    private var lastWindowExpanded = false
    private var lastWindowSig = ""
    private var expandedUntil = 0L

    private val uiState = PillOverlayUiState()

    private var flashText = ""
    private var flashColor = 0xFFFBBC04.toInt()
    private var flashUntil = 0L

    private var cachedCardId = -2L
    private var cachedCardTitle = ""
    private var cachedCardColor = 0xFF4285F4.toInt()
    private var cachedCardMinutes = 25

    private var cachedProjId = -2L
    private var cachedProjName = ""
    private var lastStore: Store? = null

    private val collapseResetRunnable = Runnable {
        if (!uiState.expanded) {
            updateWindowGeometry(force = true)
        }
    }

    /** Called every second by WatchService. Never touches external media or system state. */
    fun update(store: Store, @Suppress("UNUSED_PARAMETER") fg: String?, gateUp: Boolean, screenOn: Boolean) {
        lastStore = store
        try {
            val orientation = ctx.resources.configuration.orientation
            if (!store.pillOn || !screenOn || gateUp || !Settings.canDrawOverlays(ctx) ||
                (!store.pillLandscape && orientation != Configuration.ORIENTATION_PORTRAIT)
            ) {
                hide()
                return
            }
            val now = System.currentTimeMillis()
            if (uiState.expanded && now > expandedUntil) {
                uiState.expanded = false
                h.removeCallbacks(collapseResetRunnable)
                h.postDelayed(collapseResetRunnable, 340L)
            }

            val dual = buildDualState(store, now)
            if (dual == null) {
                hide()
                return
            }

            val a11ySvc = FocusAccessibility.instance
            val wantA11y = a11ySvc != null
            if (root != null && wantA11y != usingA11yOverlay) {
                removeWindowImmediate()
            }

            uiState.dual = dual
            uiState.shape = PillShape.fromCode(store.pillShape)
            uiState.widthDp = store.pillWidthDp
            uiState.heightDp = store.pillHeightDp
            uiState.topOffsetDp = store.pillTopOffsetDp
            uiState.strictLocked = store.launcherLockOnTimer && store.launcherStrict

            if (root == null) {
                attachWindow(a11ySvc)
            } else {
                updateWindowGeometry(force = false)
            }
        } catch (_: Exception) {
            hide()
        }
    }

    fun hide() {
        h.removeCallbacks(collapseResetRunnable)
        uiState.expanded = false
        uiState.focusSecondaryOnExpand = false
        uiState.dual = null
        removeWindowImmediate()
    }

    /** Show a Focus alert (break warning, session ends soon) on the pill while enabled. */
    fun notice(text: String, color: Int, ms: Long = 4000L) {
        flashText = text
        flashColor = color
        flashUntil = System.currentTimeMillis() + ms
    }

    private fun buildDualState(store: Store, now: Long): PillDualState? {
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

        val player = Music.player
        val musicLoaded = player != null && Music.currentId != -1L && player.mediaItemCount > 0
        val musicPlaying = Music.isPlaying && Music.currentId != -1L
        val meta = player?.currentMediaItem?.mediaMetadata
        val musicTitle = if (musicLoaded) meta?.title?.toString() else null
        val musicArtist = if (musicLoaded) meta?.artist?.toString() else null
        val musicArt = if (musicLoaded) meta?.artworkUri?.toString() else null
        val musicPos = if (musicLoaded) player?.currentPosition?.coerceAtLeast(0L) ?: 0L else 0L
        val musicDur = if (musicLoaded) player?.duration?.coerceAtLeast(0L) ?: 0L else 0L

        val notePlaying = NotePlayer.isPlaying && NotePlayer.current.isNotEmpty()
        val noteTitle = if (notePlaying) NotePlayer.title else null

        return PillSelector.selectDual(
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
                focusMusicArtist = musicArtist,
                focusMusicActive = musicPlaying || (uiState.expanded && musicLoaded),
                focusMusicId = Music.currentId,
                focusMusicArtworkUri = musicArt,
                focusMusicPosMs = musicPos,
                focusMusicDurMs = musicDur,
            )
        )
    }

    private fun attachWindow(a11ySvc: FocusAccessibility?) {
        val overlayCtx = a11ySvc ?: ctx
        val wm = overlayCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usingA11yOverlay = a11ySvc != null
        activeWm = wm

        val owner = PillLifecycleOwner().also { it.onAttach() }
        val host = OutsideAwareHost(overlayCtx) {
            if (uiState.expanded) collapseFromUser()
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }
        val cv = ComposeView(overlayCtx).apply {
            fitsSystemWindows = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    FocusDynamicIslandRoot(
                        state = uiState,
                        onExpandPrimary = { expandWithFocus(secondary = false) },
                        onExpandSecondary = { expandWithFocus(secondary = true) },
                        onCollapse = { collapseFromUser() },
                        onOpenApp = { target ->
                            collapseFromUser()
                            openFocus(target)
                        },
                        onToggleTimer = {
                            val s = lastStore ?: return@FocusDynamicIslandRoot
                            if (s.activePaused) s.resumeActive() else s.pauseActive()
                            TimerAlarm.reschedule(ctx, s)
                            Notifier.syncTimer(ctx, s)
                            bumpExpandTimeout()
                            refreshUiNow(s)
                        },
                        onAddTimer5m = {
                            val s = lastStore ?: return@FocusDynamicIslandRoot
                            s.addActiveTime(5 * 60_000L)
                            TimerAlarm.reschedule(ctx, s)
                            Notifier.syncTimer(ctx, s)
                            bumpExpandTimeout()
                            refreshUiNow(s)
                        },
                        onMediaPlayPause = {
                            val p = Music.player
                            if (p != null) {
                                if (p.isPlaying) p.pause() else p.play()
                            }
                            bumpExpandTimeout()
                            lastStore?.let(::refreshUiNow)
                        },
                        onMediaPrev = {
                            Music.player?.seekToPreviousMediaItem()
                            bumpExpandTimeout()
                            lastStore?.let(::refreshUiNow)
                        },
                        onMediaNext = {
                            Music.player?.seekToNextMediaItem()
                            bumpExpandTimeout()
                            lastStore?.let(::refreshUiNow)
                        },
                        onMediaSeek = { ms ->
                            Music.player?.seekTo(ms)
                            bumpExpandTimeout()
                            lastStore?.let(::refreshUiNow)
                        },
                        onToggleNote = {
                            if (NotePlayer.isPlaying) NotePlayer.pause() else NotePlayer.resume()
                            bumpExpandTimeout()
                            lastStore?.let(::refreshUiNow)
                        },
                    )
                }
            }
        }
        host.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val params = createLayoutParams(usingA11yOverlay)
        applyGeometryToParams(params)
        disableMoveAnimation(params)

        try {
            wm.addView(host, params)
            activeWm = wm
        } catch (e: Exception) {
            if (usingA11yOverlay) {
                val appWm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val fallbackParams = createLayoutParams(false).also {
                    applyGeometryToParams(it)
                    disableMoveAnimation(it)
                }
                usingA11yOverlay = false
                appWm.addView(host, fallbackParams)
                activeWm = appWm
                lp = fallbackParams
            } else {
                owner.onDetach()
                throw e
            }
        }
        if (lp == null) lp = params
        root = host
        composeView = cv
        lifecycleOwner = owner
        lastWindowExpanded = uiState.expanded
    }

    private fun createLayoutParams(a11y: Boolean): WindowManager.LayoutParams {
        val type = if (a11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        return WindowManager.LayoutParams(0, 0, type, flags, PixelFormat.TRANSLUCENT).apply {
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun applyGeometryToParams(p: WindowManager.LayoutParams): String {
        val screenW = ctx.resources.displayMetrics.widthPixels
        val expanded = uiState.expanded
        val shape = uiState.shape
        val hasDual = uiState.dual?.secondary != null
        val bubbleExtraDp = if (hasDual) uiState.heightDp + 16 else 0

        val compactW = min((screenW * 0.96f).toInt(), dp(uiState.widthDp + bubbleExtraDp + 44))
        val compactH = dp(maxOf(uiState.heightDp, 34) + 18)
        val expW = min(dp(360), (screenW * 0.94f).toInt())
        val expH = dp(236)

        val targetW = if (expanded) expW else compactW
        val targetH = if (expanded) expH else compactH
        val targetGravity = when {
            expanded -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            shape == PillShape.SIDE_LEFT -> Gravity.TOP or Gravity.START
            shape == PillShape.SIDE_RIGHT -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val targetX = if (!expanded && (shape == PillShape.SIDE_LEFT || shape == PillShape.SIDE_RIGHT)) dp(8) else 0
        val baseY = if (expanded) maxOf(uiState.topOffsetDp, 12) else uiState.topOffsetDp
        val targetY = dp(baseY)

        p.width = targetW
        p.height = targetH
        p.gravity = targetGravity
        p.x = targetX
        p.y = targetY
        return "$targetW|$targetH|$targetGravity|$targetX|$targetY|$expanded"
    }

    private fun updateWindowGeometry(force: Boolean) {
        val wm = activeWm ?: return
        val host = root ?: return
        val p = lp ?: return
        val sig = applyGeometryToParams(p)
        if (force || sig != lastWindowSig) {
            lastWindowSig = sig
            try { wm.updateViewLayout(host, p) } catch (_: Exception) {}
        }
    }

    private fun expandWithFocus(secondary: Boolean) {
        val cur = uiState.dual ?: return
        if (cur.primary.kind == PillKind.NOTICE && !secondary) {
            openFocus(cur.primary.goTarget)
            return
        }
        h.removeCallbacks(collapseResetRunnable)
        uiState.focusSecondaryOnExpand = secondary
        uiState.expanded = true
        expandedUntil = System.currentTimeMillis() + 8500L
        updateWindowGeometry(force = true)
    }

    private fun collapseFromUser() {
        uiState.expanded = false
        h.removeCallbacks(collapseResetRunnable)
        h.postDelayed(collapseResetRunnable, 340L)
    }

    private fun bumpExpandTimeout() {
        if (uiState.expanded) expandedUntil = System.currentTimeMillis() + 8500L
    }

    private fun refreshUiNow(store: Store) {
        uiState.dual = buildDualState(store, System.currentTimeMillis())
    }

    private fun removeWindowImmediate() {
        val wm = activeWm
        val host = root
        val owner = lifecycleOwner
        root = null
        composeView = null
        lifecycleOwner = null
        lp = null
        activeWm = null
        lastWindowSig = ""
        if (host != null && wm != null) {
            try { wm.removeViewImmediate(host) } catch (_: Exception) {
                try { wm.removeView(host) } catch (_: Exception) {}
            }
        }
        owner?.onDetach()
    }

    private fun disableMoveAnimation(params: WindowManager.LayoutParams) {
        try {
            val field = params.javaClass.getDeclaredField("privateFlags")
            field.isAccessible = true
            val current = field.getInt(params)
            field.setInt(params, current or 0x00000040)
        } catch (_: Exception) {}
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

    private class OutsideAwareHost(ctx: Context, val onOutside: () -> Unit) : FrameLayout(ctx) {
        init {
            clipChildren = false
            clipToPadding = false
        }

        override fun onTouchEvent(event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_OUTSIDE) {
                onOutside()
                return true
            }
            return super.onTouchEvent(event)
        }
    }
}

private class WaterdropIslandShape(
    private val expandProgress: Float,
    private val cornerRadiusDp: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val t = (expandProgress / 0.25f).coerceIn(0f, 1f)
        val earMax = min(with(density) { 12.dp.toPx() }, min(w * 0.08f, h * 0.34f))
        val ear = earMax * (1f - t)
        val botR = min(
            with(density) { cornerRadiusDp.toPx() },
            min((w - 2f * ear).coerceAtLeast(2f) / 2f, h / 2f)
        ).coerceAtLeast(1f)
        val topR = with(density) { cornerRadiusDp.toPx() } * t

        val path = Path().apply {
            if (t < 0.98f) {
                moveTo(0f, 0f)
                quadraticBezierTo(ear, 0f, ear, ear)
                lineTo(ear, (h - botR).coerceAtLeast(ear))
                quadraticBezierTo(ear, h, ear + botR, h)
                lineTo((w - ear - botR).coerceAtLeast(ear + botR), h)
                quadraticBezierTo(w - ear, h, w - ear, (h - botR).coerceAtLeast(ear))
                lineTo(w - ear, ear)
                quadraticBezierTo(w - ear, 0f, w, 0f)
                close()
            } else {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(topR, topR)
                    )
                )
            }
        }
        return Outline.Generic(path)
    }
}

private class VNotchIslandShape(
    private val expandProgress: Float,
    private val cornerRadiusDp: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val t = (expandProgress / 0.25f).coerceIn(0f, 1f)
        val ear = min(with(density) { 6.dp.toPx() }, w * 0.07f) * (1f - t)
        val inset = min(with(density) { 14.dp.toPx() }, w * 0.13f) * (1f - t)
        val cornerR = min(with(density) { cornerRadiusDp.toPx() }, (w - 2f * inset).coerceAtLeast(2f) * 0.42f)
        val midY = h * 0.54f

        val path = Path().apply {
            if (t < 0.98f) {
                moveTo(0f, 0f)
                quadraticBezierTo(ear, 0f, inset, midY)
                quadraticBezierTo(inset + with(density) { 2.dp.toPx() }, h, inset + cornerR, h)
                lineTo((w - inset - cornerR).coerceAtLeast(inset + cornerR), h)
                quadraticBezierTo(w - inset - with(density) { 2.dp.toPx() }, h, w - inset, midY)
                quadraticBezierTo(w - ear, 0f, w, 0f)
                close()
            } else {
                val r = with(density) { cornerRadiusDp.toPx() }
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                )
            }
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusDynamicIslandRoot(
    state: PillOverlayUiState,
    onExpandPrimary: () -> Unit,
    onExpandSecondary: () -> Unit,
    onCollapse: () -> Unit,
    onOpenApp: (String) -> Unit,
    onToggleTimer: () -> Unit,
    onAddTimer5m: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaPrev: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaSeek: (Long) -> Unit,
    onToggleNote: () -> Unit,
) {
    val dual = state.dual ?: return
    val primary = dual.primary
    val secondary = dual.secondary
    val expanded = state.expanded
    val shapeMode = state.shape
    val compactW = state.widthDp.dp
    val compactH = (if (shapeMode.isFlushTop) maxOf(state.heightDp, 34) else state.heightDp).dp

    var showSecondaryInExpanded by remember(expanded, state.focusSecondaryOnExpand, secondary?.kind) {
        mutableStateOf(state.focusSecondaryOnExpand && secondary != null)
    }
    val activeExpandedModel = if (showSecondaryInExpanded && secondary != null) secondary else primary

    val targetW = when {
        expanded -> 338.dp
        else -> compactW
    }
    val targetH = when {
        expanded && activeExpandedModel.kind == PillKind.FOCUS_MUSIC -> 184.dp
        expanded && activeExpandedModel.kind == PillKind.TIMER -> 148.dp
        expanded -> 124.dp
        else -> compactH
    }
    val targetCorner = if (expanded) 28.dp else (compactH / 2f)

    val widthAnim by animateDpAsState(
        targetValue = targetW,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "islandWidth"
    )
    val heightAnim by animateDpAsState(
        targetValue = targetH,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "islandHeight"
    )
    val cornerAnim by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "islandCorner"
    )
    val expandProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 380f),
        label = "expandProgress"
    )

    // Overshoot bounce feel when appearing / minimizing
    val bounceScale = remember { Animatable(0.84f) }
    var wasExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded, primary.key) {
        if (wasExpanded != expanded) {
            wasExpanded = expanded
            bounceScale.snapTo(if (expanded) 0.94f else 1.06f)
            bounceScale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 390f))
        }
    }
    LaunchedEffect(Unit) {
        bounceScale.animateTo(1f, spring(dampingRatio = 0.60f, stiffness = 380f))
    }

    val bubbleSize = compactH
    val bubbleGap = 6.dp
    val showDualBubble = secondary != null && !expanded
    val restingBubbleOffsetX = (compactW / 2f) + bubbleGap + (bubbleSize / 2f)
    val bubbleOffsetX by animateDpAsState(
        targetValue = if (showDualBubble) restingBubbleOffsetX else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "bubbleOffsetX"
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (showDualBubble) 1f else 0f,
        animationSpec = tween(220),
        label = "bubbleAlpha"
    )

    val activeAccent by animateColorAsState(
        targetValue = Color((if (expanded) activeExpandedModel else primary).accentColor),
        animationSpec = tween(280),
        label = "accent"
    )

    val pillShape: Shape = when (shapeMode) {
        PillShape.WATERDROP -> WaterdropIslandShape(expandProgress, cornerAnim)
        PillShape.V_SHAPE -> VNotchIslandShape(expandProgress, cornerAnim)
        else -> RoundedCornerShape(cornerAnim)
    }

    val containerAlign = when {
        expanded -> Alignment.TopCenter
        shapeMode == PillShape.SIDE_LEFT -> Alignment.TopStart
        shapeMode == PillShape.SIDE_RIGHT -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp),
        contentAlignment = containerAlign
    ) {
        // 1. Detached Secondary Circle Bubble (sits behind main pill on Z-order and springs out horizontally)
        if (bubbleAlpha > 0.02f && secondary != null) {
            val dir = if (shapeMode == PillShape.SIDE_RIGHT) -1f else 1f
            val offsetPx = with(LocalDensity.current) {
                val baseX = when (shapeMode) {
                    PillShape.SIDE_LEFT -> (widthAnim + bubbleGap).toPx()
                    PillShape.SIDE_RIGHT -> -(widthAnim + bubbleGap).toPx()
                    else -> ((widthAnim / 2f) + bubbleGap).toPx() * dir
                }
                val frac = (bubbleOffsetX / restingBubbleOffsetX.coerceAtLeast(1.dp)).coerceIn(0f, 1f)
                (baseX * frac).toInt()
            }
            val secAccent = Color(secondary.accentColor)
            val secArt = rememberMediaArt(secondary.artworkUri, secondary.mediaId)

            Box(
                modifier = Modifier
                    .zIndex(0f)
                    .offset { IntOffset(x = offsetPx, y = 0) }
                    .size(bubbleSize)
                    .graphicsLayer {
                        alpha = bubbleAlpha * (1f - expandProgress * 3f).coerceIn(0f, 1f)
                        scaleX = 0.78f + 0.22f * bubbleAlpha
                        scaleY = 0.78f + 0.22f * bubbleAlpha
                    }
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.2.dp, secAccent.copy(alpha = 0.65f), CircleShape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpandSecondary,
                        onLongClick = { onOpenApp(secondary.goTarget) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                SecondaryCircleContent(model = secondary, art = secArt, accent = secAccent)
            }
        }

        // 2. Main Morphing Island
        Box(
            modifier = Modifier
                .zIndex(1f)
                .size(width = widthAnim, height = heightAnim)
                .graphicsLayer {
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                }
                .clip(pillShape)
                .background(Color.Black)
                .border(1.25.dp, activeAccent.copy(alpha = if (expanded) 0.55f else 0.45f), pillShape)
                .then(
                    if (expanded) {
                        Modifier.pointerInput(Unit) {
                            var dyAcc = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dyAcc = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    dyAcc += dragAmount
                                    if (dyAcc < -36f) onCollapse()
                                }
                            )
                        }
                    } else {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExpandPrimary,
                            onLongClick = { onOpenApp(primary.goTarget) }
                        )
                    }
                )
        ) {
            if (expandProgress < 0.35f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha((1f - expandProgress / 0.35f).coerceIn(0f, 1f))
                ) {
                    CompactIslandContent(model = primary, shape = shapeMode, accent = Color(primary.accentColor))
                }
            }
            if (expandProgress >= 0.2f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(((expandProgress - 0.2f) / 0.8f).coerceIn(0f, 1f))
                ) {
                    AnimatedContent(
                        targetState = activeExpandedModel,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                        label = "expandedSwap"
                    ) { model ->
                        ExpandedIslandContent(
                            model = model,
                            hasOtherActivity = secondary != null,
                            showingSecondary = showSecondaryInExpanded,
                            otherActivityLabel = if (showSecondaryInExpanded) primary.label else secondary?.label,
                            strictLocked = state.strictLocked,
                            onSwitchActivity = { showSecondaryInExpanded = !showSecondaryInExpanded },
                            onToggleTimer = onToggleTimer,
                            onAddTimer5m = onAddTimer5m,
                            onMediaPlayPause = onMediaPlayPause,
                            onMediaPrev = onMediaPrev,
                            onMediaNext = onMediaNext,
                            onMediaSeek = onMediaSeek,
                            onToggleNote = onToggleNote,
                            onOpenApp = { onOpenApp(model.goTarget) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryCircleContent(model: PillModel, art: ImageBitmap?, accent: Color) {
    val rot = rememberInfiniteSpin(model.kind == PillKind.FOCUS_MUSIC && model.mediaPlaying)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (model.kind) {
            PillKind.FOCUS_MUSIC -> {
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(CircleShape)
                            .graphicsLayer { rotationZ = rot }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = lerp(Color.White, accent, 0.45f),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (model.mediaPlaying) rot else 0f }
                    )
                }
            }
            PillKind.TIMER -> {
                val p = model.progress ?: 0f
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.16f), style = Stroke(2.dp.toPx()))
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = if (model.paused) Icons.Rounded.Pause else Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactIslandContent(model: PillModel, shape: PillShape, accent: Color) {
    val art = if (model.kind == PillKind.FOCUS_MUSIC) rememberMediaArt(model.artworkUri, model.mediaId) else null
    val startPad = when (shape) {
        PillShape.SIDE_LEFT -> 30.dp
        PillShape.WATERDROP, PillShape.V_SHAPE -> 14.dp
        else -> 12.dp
    }
    val endPad = when (shape) {
        PillShape.SIDE_RIGHT -> 30.dp
        PillShape.WATERDROP, PillShape.V_SHAPE -> 14.dp
        else -> 12.dp
    }

    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = startPad, end = endPad, top = if (shape.isFlushTop) 2.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Slot (Album art or sharp vector icon + marquee title)
            when {
                model.kind == PillKind.FOCUS_MUSIC && art != null -> {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp).clip(CircleShape)
                    )
                }
                else -> {
                    Icon(
                        imageVector = pillVectorFor(model),
                        contentDescription = null,
                        tint = lerp(Color.White, accent, 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = model.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, velocity = 28.dp)
            )

            if (shape.hasCenterCameraGap) {
                Spacer(Modifier.width(26.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }

            // Right Slot (Equalizer bars for audio, or live clock/progress)
            when (model.kind) {
                PillKind.FOCUS_MUSIC, PillKind.FOCUS_NOTE -> {
                    EqualizerVisualizer(
                        isPlaying = model.mediaPlaying,
                        barColor = lerp(Color(0xFFE5E5EA), accent, 0.45f)
                    )
                }
                else -> if (model.trailing.isNotEmpty()) {
                    Text(
                        text = model.trailing,
                        color = lerp(Color.White, accent, 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Sleek bottom progress accent on timer pills
        val prog = model.progress
        if (prog != null && model.kind == PillKind.TIMER && prog > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(prog.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(accent.copy(alpha = 0.85f))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedIslandContent(
    model: PillModel,
    hasOtherActivity: Boolean,
    showingSecondary: Boolean,
    otherActivityLabel: String?,
    strictLocked: Boolean,
    onSwitchActivity: () -> Unit,
    onToggleTimer: () -> Unit,
    onAddTimer5m: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaPrev: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaSeek: (Long) -> Unit,
    onToggleNote: () -> Unit,
    onOpenApp: () -> Unit,
) {
    when (model.kind) {
        PillKind.FOCUS_MUSIC -> ExpandedMusicIsland(
            model = model,
            hasOtherActivity = hasOtherActivity,
            showingSecondary = showingSecondary,
            otherLabel = otherActivityLabel,
            onSwitchActivity = onSwitchActivity,
            onPlayPause = onMediaPlayPause,
            onPrev = onMediaPrev,
            onNext = onMediaNext,
            onSeek = onMediaSeek,
            onOpen = onOpenApp,
        )
        PillKind.TIMER -> ExpandedTimerIsland(
            model = model,
            hasOtherActivity = hasOtherActivity,
            otherLabel = otherActivityLabel,
            strictLocked = strictLocked,
            onSwitchActivity = onSwitchActivity,
            onToggleTimer = onToggleTimer,
            onAddTimer5m = onAddTimer5m,
            onOpen = onOpenApp,
        )
        else -> ExpandedCompactActionIsland(
            model = model,
            onToggleNote = onToggleNote,
            onOpen = onOpenApp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedMusicIsland(
    model: PillModel,
    hasOtherActivity: Boolean,
    showingSecondary: Boolean,
    otherLabel: String?,
    onSwitchActivity: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit,
) {
    val art = rememberMediaArt(model.artworkUri, model.mediaId)
    val accent = Color(model.accentColor)
    val playing = Music.isPlaying
    val duration = Music.player?.duration?.coerceAtLeast(0L) ?: model.durationMs
    val hasValidDuration = duration > 0L

    var livePos by remember(model.mediaId) { mutableLongStateOf(Music.player?.currentPosition?.coerceAtLeast(0L) ?: model.positionMs) }
    var isSeeking by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, model.mediaId) {
        while (isActive) {
            if (!isSeeking) {
                livePos = Music.player?.currentPosition?.coerceAtLeast(0L) ?: model.positionMs
            }
            delay(if (playing) 250L else 600L)
        }
    }

    val currentPos = if (isSeeking && hasValidDuration) (scrubFrac * duration).toLong().coerceIn(0L, duration) else livePos
    val sliderProg = when {
        isSeeking -> scrubFrac
        hasValidDuration -> (livePos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    val vinylFraction by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "vinylFraction"
    )
    val artRadius = 12.dp + (27.dp - 12.dp) * vinylFraction
    val vinylRot = rememberInfiniteSpin(playing)

    val playBtnBg = lerp(Color.White, accent, 0.25f)
    val playBtnTint = Color.Black
    val eqTint = lerp(Color(0xFFE5E5EA), accent, 0.48f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Row: Album art + Marquee Title/Artist + EqualizerVisualizer
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer { rotationZ = vinylRot * vinylFraction }
                    .clip(RoundedCornerShape(artRadius))
                    .background(Color(0xFF1F1F24))
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Album, null, tint = eqTint, modifier = Modifier.size(30.dp))
                }
                if (vinylFraction > 0.04f) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(Color.White.copy(alpha = 0.22f * vinylFraction), r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                        drawCircle(Color(0xFF141416).copy(alpha = 0.9f * vinylFraction), 5.5.dp.toPx() * vinylFraction)
                        drawCircle(Color.White.copy(alpha = 0.65f * vinylFraction), 2.dp.toPx() * vinylFraction)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800, velocity = 30.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = model.artist.ifBlank { "Focus Audio" },
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, velocity = 26.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            if (hasOtherActivity) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.clickable(onClick = onSwitchActivity)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (showingSecondary) Icons.Rounded.Timer else Icons.Rounded.MusicNote,
                            null,
                            tint = eqTint,
                            modifier = Modifier.size(13.dp)
                        )
                        if (!otherLabel.isNullOrBlank()) {
                            Text(
                                otherLabel.take(8),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            EqualizerVisualizer(isPlaying = playing, barColor = eqTint)
        }

        // Scrubber Row
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clockText(currentPos / 1000L), color = Color(0xFFA1A1AA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = sliderProg,
                enabled = hasValidDuration,
                onValueChange = { frac -> isSeeking = true; scrubFrac = frac },
                onValueChangeFinished = {
                    if (hasValidDuration) {
                        val tgt = (scrubFrac * duration).toLong().coerceIn(0L, duration)
                        livePos = tgt
                        onSeek(tgt)
                    }
                    isSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = lerp(Color(0xFFE5E5EA), accent, 0.55f),
                    inactiveTrackColor = Color(0xFF2C2C2E)
                ),
                modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 6.dp)
            )
            Text(clockText(duration / 1000L), color = Color(0xFFA1A1AA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.SkipPrevious, tr("Previous"), tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(playBtnBg)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = tr(if (playing) "Pause" else "Play"),
                    tint = playBtnTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.SkipNext, tr("Next"), tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, tr("Open"), tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedTimerIsland(
    model: PillModel,
    hasOtherActivity: Boolean,
    otherLabel: String?,
    strictLocked: Boolean,
    onSwitchActivity: () -> Unit,
    onToggleTimer: () -> Unit,
    onAddTimer5m: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(model.accentColor)
    val prog = (model.progress ?: 0f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.14f), style = Stroke(3.dp.toPx()))
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * prog,
                        useCenter = false,
                        style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = if (model.paused) Icons.Rounded.Pause else Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1000, velocity = 28.dp)
                )
                Text(
                    text = model.trailing + "  ·  " + tr(if (model.paused) "Paused" else "Studying"),
                    color = lerp(Color.White, accent, 0.45f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (hasOtherActivity) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.clickable(onClick = onSwitchActivity)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(14.dp))
                        if (!otherLabel.isNullOrBlank()) {
                            Text(otherLabel.take(10), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { prog },
            color = accent,
            trackColor = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!strictLocked) {
                IslandActionChip(
                    icon = if (model.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    text = tr(if (model.paused) "Resume" else "Pause"),
                    container = accent.copy(alpha = 0.24f),
                    modifier = Modifier.weight(1f),
                    onClick = onToggleTimer
                )
            }
            IslandActionChip(
                icon = Icons.Rounded.Add,
                text = tr("5 min"),
                container = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
                onClick = onAddTimer5m
            )
            IslandActionChip(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                text = tr("Open"),
                container = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.weight(0.9f),
                onClick = onOpen
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedCompactActionIsland(
    model: PillModel,
    onToggleNote: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(model.accentColor)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(pillVectorFor(model), null, tint = lerp(Color.White, accent, 0.5f), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1000, velocity = 28.dp)
                )
                if (model.trailing.isNotEmpty()) {
                    Text(model.trailing, color = Color(0xFFA1A1AA), fontSize = 12.sp, maxLines = 1)
                }
            }
            if (model.kind == PillKind.FOCUS_NOTE) {
                EqualizerVisualizer(isPlaying = NotePlayer.isPlaying, barColor = accent)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.kind == PillKind.FOCUS_NOTE) {
                IslandActionChip(
                    icon = if (NotePlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    text = tr(if (NotePlayer.isPlaying) "Pause" else "Resume"),
                    container = accent.copy(alpha = 0.24f),
                    modifier = Modifier.weight(1f),
                    onClick = onToggleNote
                )
            }
            IslandActionChip(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                text = tr("Open"),
                container = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f),
                onClick = onOpen
            )
        }
    }
}

@Composable
private fun IslandActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EqualizerVisualizer(isPlaying: Boolean, barColor: Color = Color(0xFFE5E5EA)) {
    if (!isPlaying) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(14.dp)
        ) {
            listOf(0.32f, 0.54f, 0.24f).forEach { frac ->
                Box(
                    Modifier
                        .width(2.8.dp)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(barColor.copy(alpha = 0.65f))
                )
            }
        }
        return
    }
    val tr = rememberInfiniteTransition(label = "eq")
    val h1 by tr.animateFloat(0.22f, 0.92f, infiniteRepeatable(tween(430, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1")
    val h2 by tr.animateFloat(0.85f, 0.28f, infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2")
    val h3 by tr.animateFloat(0.30f, 1.00f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(14.dp)
    ) {
        listOf(h1, h2, h3).forEach { frac ->
            Box(
                Modifier
                    .width(2.8.dp)
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun rememberInfiniteSpin(active: Boolean): Float {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (isActive) {
                val start = rot.value % 360f
                rot.snapTo(start)
                rot.animateTo(start + 360f, tween(durationMillis = 8500, easing = LinearEasing))
            }
        }
    }
    return rot.value
}

@Composable
private fun rememberMediaArt(artUriStr: String?, mediaId: Long): ImageBitmap? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var bmp by remember(artUriStr, mediaId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artUriStr, mediaId) {
        bmp = withContext(Dispatchers.IO) {
            loadArtworkBitmap(ctx, artUriStr)?.asImageBitmap()
        }
    }
    return bmp
}

private fun loadArtworkBitmap(ctx: Context, uriStr: String?): Bitmap? {
    if (uriStr.isNullOrBlank()) return null
    return try {
        val u = Uri.parse(uriStr)
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, u)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > 220) {
                    val scale = 220f / maxSide
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } else {
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Exception) { null }
}

private fun pillVectorFor(model: PillModel) = when (model.kind) {
    PillKind.TIMER -> if (model.paused) Icons.Rounded.Pause else Icons.Rounded.Timer
    PillKind.PROJECT -> Icons.Rounded.Layers
    PillKind.FOCUS_MUSIC -> Icons.Rounded.MusicNote
    PillKind.FOCUS_NOTE -> Icons.Rounded.GraphicEq
    PillKind.NOTICE -> Icons.Rounded.NotificationsActive
}
