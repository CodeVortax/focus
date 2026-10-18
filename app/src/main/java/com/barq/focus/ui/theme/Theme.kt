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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ColorScheme
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
        accent != 0 -> accentScheme(androidx.compose.ui.graphics.Color(accent), darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context).let { it.copy(surface = it.background) }
            else dynamicLightColorScheme(context).let { creamy(it, it.primary) }
        darkTheme -> DarkColors
        else -> creamy(LightColors, GoogleBlue)
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
                // the XML window background follows the phone's night mode; follow the app's choice instead
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorScheme.background.toArgb()))
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        typography = FocusTypography.withFont(AppFont.family),
        content = content
    )
}

private val Cream = Color(0xFFF6F1E7)      // warm, anti-glare page
private val CardCream = Color(0xFFFBF8F2)  // soft cards, never pure white
private val WarmGray = Color(0xFF8C8577)

/**
 * Light mode for a calm, low-stimulation look: a soft warm page, off-white cards (no pure white anywhere)
 * and an accent muted toward warm grey. The wallpaper colour only tints it, it never sets the surfaces.
 */
private fun creamy(s: ColorScheme, a: Color): ColorScheme {
    val page = lerp(Cream, a, 0.04f)
    val card = lerp(CardCream, a, 0.02f)
    val m = lerp(a, WarmGray, 0.22f)
    fun over(alpha: Float) = m.copy(alpha = alpha).compositeOver(page)
    val onCont = lerp(m, Color.Black, 0.6f)
    return s.copy(
        primary = m, secondary = m, tertiary = m,
        primaryContainer = over(0.20f), secondaryContainer = over(0.16f), tertiaryContainer = over(0.12f),
        onPrimaryContainer = onCont, onSecondaryContainer = onCont, onTertiaryContainer = onCont,
        background = page, surface = page,
        surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, surfaceContainerHighest = card,
        surfaceVariant = lerp(page, m, 0.10f),
        onBackground = Color(0xFF2B2A27), onSurface = Color(0xFF2B2A27),
    )
}

/** A full, consistent scheme around one accent colour, for both light and dark, so no surface keeps a stray default tint. */
private fun accentScheme(a: Color, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val page = lerp(if (dark) SurfaceDark else SurfaceLight, a, if (dark) 0.05f else 0.04f)
    fun over(alpha: Float) = a.copy(alpha = alpha).compositeOver(page)
    val onAcc = if (dark) Color(0xFF1B1B1F) else Color.White
    val onCont = if (dark) lerp(a, Color.White, 0.8f) else lerp(a, Color.Black, 0.65f)
    val out = base.copy(
        primary = a, secondary = a, tertiary = a, onPrimary = onAcc, onSecondary = onAcc, onTertiary = onAcc,
        primaryContainer = over(if (dark) 0.30f else 0.22f),
        secondaryContainer = over(if (dark) 0.24f else 0.18f),
        tertiaryContainer = over(if (dark) 0.20f else 0.14f),
        onPrimaryContainer = onCont, onSecondaryContainer = onCont, onTertiaryContainer = onCont,
        background = page, surface = page,
        surfaceVariant = over(0.10f),
        surfaceContainerLowest = page,
        surfaceContainerLow = over(0.04f), surfaceContainer = over(0.07f),
        surfaceContainerHigh = over(0.10f), surfaceContainerHighest = over(0.13f),
        onBackground = if (dark) OnSurfaceDark else OnSurfaceLight,
        onSurface = if (dark) OnSurfaceDark else OnSurfaceLight,
        onSurfaceVariant = lerp(if (dark) OnSurfaceDark else OnSurfaceLight, a, 0.25f),
        outline = lerp(if (dark) OnSurfaceDark else OnSurfaceLight, a, 0.4f).copy(alpha = 0.6f),
        outlineVariant = a.copy(alpha = 0.25f),
    )
    return if (dark) out else creamy(out, a)
}
