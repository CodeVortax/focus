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

package com.barq.focus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barq.focus.data.tr

/** Thick rounded progress ring: [fraction] of the circle is filled, starting at 12 o'clock. */
@Composable
private fun Ring(fraction: Float, color: Color, stroke: Dp, modifier: Modifier) {
    val track = color.copy(alpha = 0.16f)
    Canvas(modifier) {
        val s = stroke.toPx()
        val tl = Offset(s / 2, s / 2)
        val sz = Size(size.width - s, size.height - s)
        drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        if (fraction > 0.001f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
    }
}

@Composable
private fun RoundBtn(color: Color, filled: Boolean, size: Dp, desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * The timer card: a thick ring around the time with a round play button bottom-left and a round stop button
 * bottom-right, or a compact one-line version. Tap the time to switch between the two.
 */
@Composable
fun TimerCardUi(
    timeText: String, caption: String, fraction: Float, over: Boolean,
    running: Boolean, paused: Boolean, color: Color, compact: Boolean,
    showPlay: Boolean = true,
    dialMinutes: Int = 0, onDial: ((Int) -> Unit)? = null,
    onPlayPause: () -> Unit, onStop: () -> Unit, onToggleCompact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (over) MaterialTheme.colorScheme.error else color
    val showPause = running && !paused
    // not running: turn the ring itself to set the minutes. Drawing shows the part of the turn, letting go fills it.
    var dragging by remember { mutableStateOf(false) }
    val part = (dialMinutes % DIAL_MIN_PER_TURN) / DIAL_MIN_PER_TURN.toFloat()
    val target = if (onDial != null && dragging && dialMinutes > 0) (if (part == 0f) 1f else part) else fraction
    val shown by androidx.compose.animation.core.animateFloatAsState(
        target, if (dragging) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(450), label = "ring"
    )
    val ringMod = Modifier.turnDial(dialMinutes, onDial != null && !running, { dragging = it }, { onDial?.invoke(it) })
    val src = remember { MutableInteractionSource() }
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 3.dp
    ) {
        if (!compact) {
            Box(Modifier.fillMaxWidth().height(310.dp).padding(18.dp)) {
                Box(Modifier.align(Alignment.Center).size(236.dp).then(ringMod), contentAlignment = Alignment.Center) {
                    Ring(shown, ink, 26.dp, Modifier.fillMaxSize())
                    Column(
                        Modifier.clickable(interactionSource = src, indication = null, onClick = onToggleCompact).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(timeText, fontSize = 44.sp, fontWeight = FontWeight.Medium, color = if (over) ink else MaterialTheme.colorScheme.onSurface)
                        Text(
                            caption, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 150.dp)
                        )
                    }
                }
                if (showPlay) Box(Modifier.align(Alignment.BottomStart)) {
                    RoundBtn(ink, true, 44.dp, tr(if (showPause) "Pause" else "Start"), onPlayPause) {
                        Icon(if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                if (running) Box(Modifier.align(Alignment.BottomEnd)) {
                    RoundBtn(ink, false, 44.dp, tr("Stop"), onStop) { Icon(Icons.Default.Stop, null, tint = ink, modifier = Modifier.size(22.dp)) }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ring(shown, ink, 12.dp, Modifier.size(64.dp).then(ringMod))
                Spacer(Modifier.width(14.dp))
                Column(
                    Modifier.weight(1f).clickable(interactionSource = src, indication = null, onClick = onToggleCompact),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(timeText, fontSize = 40.sp, fontWeight = FontWeight.Medium, color = if (over) ink else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showPlay) RoundBtn(ink, true, 38.dp, tr(if (showPause) "Pause" else "Start"), onPlayPause) {
                        Icon(if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    if (running) RoundBtn(ink, false, 38.dp, tr("Stop"), onStop) { Icon(Icons.Default.Stop, null, tint = ink, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

/** The subject timer. This is the only place that reads the clock, so typing elsewhere never fights the ticking. */
@Composable
fun SubjectTimerCard(
    running: Boolean, paused: Boolean, endAt: Long, pausedLeft: Long, budgetMs: Long, nowState: State<Long>,
    color: Color, compact: Boolean, onPlayPause: () -> Unit, onStop: () -> Unit, onToggleCompact: () -> Unit,
    onDial: ((Int) -> Unit)? = null
) {
    val remaining = when {
        paused -> pausedLeft
        running -> endAt - nowState.value
        else -> budgetMs
    }
    val over = running && remaining < 0
    val secs = (kotlin.math.abs(remaining) + 999) / 1000
    val timeText = (if (over) "+" else "") + "%02d:%02d".format(secs / 60, secs % 60)
    val fraction = when {
        over || !running -> 1f
        budgetMs <= 0L -> 1f
        else -> (remaining.toFloat() / budgetMs).coerceIn(0f, 1f)
    }
    TimerCardUi(
        timeText = timeText,
        caption = tr(
            when {
                paused -> "Paused. Go do your thing, then resume."
                !running -> "Turn the ring to set the minutes. Add more any time."
                over -> "Over budget. Add time or wrap up."
                else -> "Time left"
            }
        ),
        fraction = fraction, over = over, running = running, paused = paused, color = color, compact = compact,
        dialMinutes = (budgetMs / 60_000L).toInt(), onDial = onDial,
        onPlayPause = onPlayPause, onStop = onStop, onToggleCompact = onToggleCompact
    )
}
