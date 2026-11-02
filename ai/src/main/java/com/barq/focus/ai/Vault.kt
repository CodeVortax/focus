/*
 * Focus Study Mate: the optional AI helper for Focus
 * Copyright (C) 2026 MR.English2008
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the API key encrypted with a key that never leaves the phone's secure hardware store (Android Keystore). */
object Vault {
    private const val ALIAS = "focus_study_mate_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Null when the text is damaged or the secure key is gone (for example after a restore to another phone). */
    fun open(sealed: String): String? = try {
        val b = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
        String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    } catch (_: Exception) { null }
}

/** Settings. Nothing here is sent anywhere except the key, which goes only to the address you set, with each request. */
class AiPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("study_mate", Context.MODE_PRIVATE)

    var base: String
        get() = p.getString("base", Providers.list.first().base) ?: Providers.list.first().base
        set(v) { p.edit().putString("base", v.trim()).apply() }
    var model: String
        get() = p.getString("model", Providers.list.first().model) ?: Providers.list.first().model
        set(v) { p.edit().putString("model", v.trim()).apply() }
    var consent: Boolean
        get() = p.getBoolean("consent", false)
        set(v) { p.edit().putBoolean("consent", v).apply() }

    /** When a request fails, ask a public status page whether the provider has trouble. On by default; it sends nothing about you. */
    var statusCheck: Boolean
        get() = p.getBoolean("status", true)
        set(x) { p.edit().putBoolean("status", x).apply() }

    private var sealed: String?
        get() = p.getString("key", null)
        set(v) { p.edit().putString("key", v).apply() }

    fun hasKey(): Boolean = sealed != null
    fun key(): String? = sealed?.let { Vault.open(it) }
    fun setKey(k: String?) { sealed = if (k.isNullOrBlank()) null else Vault.seal(k.trim()) }

    val ready: Boolean get() = consent && base.startsWith("https://") && model.isNotBlank() && key() != null
}
