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

package com.barq.focus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore

data class AppInfo(val pkg: String, val label: String)

object Apps {
    fun launchables(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    private fun pkgsFor(ctx: Context, intent: Intent): List<String> =
        ctx.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }

    /** "camera", "phone", ... or a package name or an app label -> package names. */
    fun resolve(ctx: Context, word: String): List<String> = when (word.lowercase().trim()) {
        "camera" -> pkgsFor(ctx, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        "phone", "dialer", "calls" -> pkgsFor(ctx, Intent(Intent.ACTION_DIAL))
        "clock", "alarm" -> pkgsFor(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        "messages", "sms" -> pkgsFor(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        "browser" -> pkgsFor(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        "maps" -> pkgsFor(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))
        "calculator" -> launchables(ctx).filter { it.pkg.contains("calculator", true) }.map { it.pkg }
        else ->
            if (word.contains('.')) listOf(word.trim())
            else launchables(ctx).filter { it.label.equals(word.trim(), true) }.map { it.pkg }
    }

    fun defaultAllowed(ctx: Context): Set<String> =
        (resolve(ctx, "phone") + resolve(ctx, "camera") + resolve(ctx, "clock")).toSet()

    /** Never locked out: Focus itself, the launcher, system UI, and anything call-related. */
    fun alwaysAllowed(ctx: Context): Set<String> {
        val homes = pkgsFor(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        return (homes + ctx.packageName + listOf(
            "android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller"
        )).toSet()
    }

    fun callRelated(pkg: String) =
        pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("dialer") || pkg.contains("emergency")
}
