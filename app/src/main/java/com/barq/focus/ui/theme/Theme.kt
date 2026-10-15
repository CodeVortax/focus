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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = GoogleBlue,
    secondary = GoogleGreen,
    tertiary = GoogleYellow,
    error = GoogleRed,
    background = SurfaceLight,
    surface = SurfaceContainerLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = GoogleBlue,
    secondary = GoogleGreen,
    tertiary = GoogleYellow,
    error = GoogleRed,
    background = SurfaceDark,
    surface = SurfaceContainerDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
)

// Pixel/Material-You leans on big, soft corner radii everywhere
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FocusTheme(
    darkTheme: Boolean = when (AppTheme.mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() },
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val accent = AppTheme.accent
    val colorScheme = when {
        accent != 0 -> {
            val a = androidx.compose.ui.graphics.Color(accent)
            val base = if (darkTheme) DarkColors else LightColors
            val bg = base.surface
            base.copy(
                primary = a, secondary = a, tertiary = a, onPrimary = androidx.compose.ui.graphics.Color.White,
                primaryContainer = a.copy(alpha = 0.28f).compositeOver(bg),
                secondaryContainer = a.copy(alpha = 0.22f).compositeOver(bg),
                surfaceVariant = a.copy(alpha = 0.12f).compositeOver(bg),
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    // keep status/navigation bar icons readable when the app theme differs from the system one
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val ctl = androidx.core.view.WindowCompat.getInsetsController(window, view)
                ctl.isAppearanceLightStatusBars = !darkTheme
                ctl.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        typography = FocusTypography,
        content = content
    )
}
