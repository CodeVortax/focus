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

package com.barq.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.barq.focus.data.I18n
import com.barq.focus.service.Shared
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.barq.focus.service.Perms
import com.barq.focus.ui.FocusApp
import com.barq.focus.ui.theme.FocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        I18n.init(this)
        com.barq.focus.data.Store(this).let {
            com.barq.focus.ui.theme.AppTheme.accent = it.accent
            com.barq.focus.ui.theme.AppTheme.mode = it.themeMode
            com.barq.focus.ui.theme.AppTheme.checkShape = it.checkShape
        }
        com.barq.focus.ui.theme.AppFont.load(this)
        handleIntent(intent)
        setContent {
            FocusTheme {
                val dir = if (I18n.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FocusApp() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // a .foc file opened with Focus, or text shared to it from an AI app
    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_VIEW -> i.data?.let { Shared.pendingUri = it; Shared.pendingTick++ }
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { Shared.pendingText = it; Shared.pendingTick++ }
        }
    }

    override fun onResume() {
        super.onResume()
        Perms.ensureService(this)
        com.barq.focus.service.Reminders.rescheduleAll(this)
    }
}
