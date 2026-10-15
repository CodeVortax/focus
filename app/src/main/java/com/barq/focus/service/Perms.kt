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

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Perms {
    @Suppress("DEPRECATION")
    fun usage(c: Context): Boolean {
        val a = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return if (m == AppOpsManager.MODE_DEFAULT)
            c.checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED
        else m == AppOpsManager.MODE_ALLOWED
    }

    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun listener(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun accessibility(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.startsWith(c.packageName + "/") && it.endsWith("FocusAccessibility") }
    }

    fun ready(c: Context) = usage(c) && overlay(c)

    fun ensureService(c: Context) {
        if (!ready(c)) return
        try { ContextCompat.startForegroundService(c, Intent(c, WatchService::class.java)) } catch (_: Exception) {}
    }
}

/**
 * Leaving an app: back out of it (so YouTube can't drop into picture-in-picture), then kill leftovers.
 * Browsers are the exception: BACK walks through (and closes) the tab history, so they just go Home.
 */
object Leaver {
    private val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser", "com.opera.browser",
        "com.opera.mini.native", "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "com.android.browser", "com.mi.globalbrowser", "com.UCMobile.intl", "com.yandex.browser",
        "org.torproject.torbrowser", "app.vanadium.browser",
    )

    private var browserCache: Set<String>? = null

    fun isBrowser(ctx: Context, pkg: String): Boolean {
        if (pkg in BROWSERS) return true
        val found = browserCache ?: try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        browserCache = found
        return pkg in found
    }

    fun leave(ctx: Context, pkg: String) {
        if (isBrowser(ctx, pkg)) { home(ctx); return }   // no BACK presses, no kill: tabs and history stay
        val a = FocusAccessibility.instance
        if (a != null) a.exitApp(pkg) else { home(ctx); kill(ctx, pkg, 700) }
    }

    fun home(ctx: Context) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    fun kill(ctx: Context, pkg: String, delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { ctx.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg) } catch (_: Exception) {}
        }, delayMs)
    }
}
