package com.barq.focus.service

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Optional: turns the whole screen gray (the system "monochromacy" colour filter) while a short-video feed is open.
 * Needs a one-time `adb shell pm grant com.barq.focus android.permission.WRITE_SECURE_SETTINGS`; without it, this does nothing.
 */
object Gray {
    private const val EN = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    private var saved: Pair<Int, Int>? = null

    fun can(ctx: Context) = ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private fun flag(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences("focus", Context.MODE_PRIVATE).edit().putBoolean("gray_on", on).apply()

    fun on(ctx: Context) {
        if (!can(ctx) || saved != null) return
        try {
            val cr = ctx.contentResolver
            saved = Settings.Secure.getInt(cr, EN, 0) to Settings.Secure.getInt(cr, MODE, -1)
            flag(ctx, true)
            Settings.Secure.putInt(cr, MODE, 0)
            Settings.Secure.putInt(cr, EN, 1)
        } catch (_: Exception) { saved = null }
    }

    fun off(ctx: Context) {
        val s = saved ?: return
        saved = null
        try {
            val cr = ctx.contentResolver
            Settings.Secure.putInt(cr, EN, s.first)
            if (s.second != -1) Settings.Secure.putInt(cr, MODE, s.second)
        } catch (_: Exception) {}
        flag(ctx, false)
    }

    /** If the app was killed while gray, put the colours back the next time the service starts. */
    fun recover(ctx: Context) {
        val p = ctx.getSharedPreferences("focus", Context.MODE_PRIVATE)
        if (!p.getBoolean("gray_on", false) || saved != null) return
        try { if (can(ctx)) Settings.Secure.putInt(ctx.contentResolver, EN, 0) } catch (_: Exception) {}
        flag(ctx, false)
    }
}
