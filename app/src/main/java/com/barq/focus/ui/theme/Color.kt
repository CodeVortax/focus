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

package com.barq.focus.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Google product colors, used as the fallback (non-dynamic-color) palette
val GoogleBlue = Color(0xFF4285F4)
val GoogleRed = Color(0xFFEA4335)
val GoogleYellow = Color(0xFFFBBC04)
val GoogleGreen = Color(0xFF34A853)

val SurfaceLight = Color(0xFFF7F9FC)
val SurfaceDark = Color(0xFF131318)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val SurfaceContainerDark = Color(0xFF1E1F24)
val OnSurfaceLight = Color(0xFF1A1C1E)
val OnSurfaceDark = Color(0xFFE3E2E6)

// Swatches offered when the user picks a card's accent color
val CardSwatches = listOf(
    GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen,
    Color(0xFF8E5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFF14B8A6), // teal
    Color(0xFFFF7A1A), // orange
    Color(0xFF84CC16), // lime
    Color(0xFF06B6D4), // cyan
    Color(0xFF6366F1), // indigo
    Color(0xFFF43F5E), // rose
    Color(0xFFF59E0B), // amber
    Color(0xFF34D399), // mint
)

/** The app-wide accent: 0 = follow the wallpaper (Material You), otherwise an ARGB colour. */
object AppTheme {
    var accent by androidx.compose.runtime.mutableIntStateOf(0)

    /** 0 = follow the system, 1 = always light, 2 = always dark. */
    var mode by androidx.compose.runtime.mutableIntStateOf(0)

    /** Shape of the task check: 0 circle, 1..6 triangle..octagon, 7 = mixed. */
    var checkShape by androidx.compose.runtime.mutableIntStateOf(0)
}
