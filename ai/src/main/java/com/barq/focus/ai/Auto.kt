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

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoResult(val reply: String?, val model: String, val error: Throwable?, val note: String?)

/** Makes a request survive the usual trouble: wait when asked to slow down, and move to another model when one is not usable. */
object Auto {
    private const val MAX_TRIES = 5

    suspend fun chat(base: String, key: String, first: String, system: String, msgs: List<ChatMsg>, known: List<String>): AutoResult {
        var model = first
        val tried = ArrayList<String>()
        var alts: List<String>? = null
        var last: Throwable? = null
        repeat(MAX_TRIES) {
            tried.add(model)
            var r = Api.chat(base, key, model, system, msgs)
            var waits = 0
            while (r.isFailure && kind(r) == ErrKind.RATE && waits < 2) {
                val asked = (r.exceptionOrNull() as? ApiException)?.retryAfterSec
                delay(((asked ?: (2 * (waits + 1))).coerceIn(1, 8)) * 1000L)
                waits++
                r = Api.chat(base, key, model, system, msgs)
            }
            if (r.isSuccess) {
                val note = if (model != first) "Switched to $model because $first was not usable." else null
                return AutoResult(r.getOrNull(), model, null, note)
            }
            last = r.exceptionOrNull()
            val k = kind(r) ?: return AutoResult(null, model, last, null)          // no internet and the like: another model will not help
            val switchable = k == ErrKind.MODEL || k == ErrKind.RATE || (k == ErrKind.QUOTA && base.contains("openrouter.ai"))
            if (!switchable) return AutoResult(null, model, last, null)
            if (alts == null) alts = candidates(base, key, known, k)
            val next = alts!!.firstOrNull { it !in tried } ?: return AutoResult(null, model, last, null)
            model = next
        }
        return AutoResult(null, model, last, null)
    }

    private fun kind(r: Result<String>): ErrKind? = (r.exceptionOrNull() as? ApiException)?.kind

    /** Other models to try, best first. With no credit on OpenRouter, only its free models. */
    private suspend fun candidates(base: String, key: String, known: List<String>, why: ErrKind): List<String> {
        val ids = if (known.isNotEmpty()) known else Models.chatOnly(Api.listModels(base, key).getOrDefault(emptyList()))
        val ranked = Models.ranked(ids)
        return if (why == ErrKind.QUOTA) ranked.filter { it.endsWith(":free") }.take(4) else ranked.take(4)
    }

    /** Tries the best few models with a tiny request and returns the first that answers. */
    suspend fun findWorking(base: String, key: String, current: String, known: List<String>, progress: (String) -> Unit): Result<String> {
        val ids = if (known.isNotEmpty()) known else Models.chatOnly(Api.listModels(base, key).getOrDefault(emptyList()))
        val order = (listOf(current) + Models.ranked(ids)).filter { it.isNotBlank() }.distinct().take(6)
        if (order.isEmpty()) return Result.failure(Exception("No models to try. Type a model name in Settings."))
        var last: Throwable? = null
        for (m in order) {
            progress("Trying $m...")
            val r = Api.chat(base, key, m, "Reply with the single word OK.", listOf(ChatMsg("user", "Test")))
            if (r.isSuccess) return Result.success(m)
            last = r.exceptionOrNull()
            val k = (last as? ApiException)?.kind
            if (k == ErrKind.KEY || k == ErrKind.QUOTA || k == null) break   // a different model will not fix these
            if (k == ErrKind.RATE) delay(2000)
        }
        return Result.failure(last ?: Exception("No model answered."))
    }
}

/** Optional: asks a public status page whether the provider is having trouble. Nothing about you or your chat is sent. */
object Status {
    private const val URL_STATUS = "https://prismix.dev/api/v1/statuses"
    private var cache: Pair<Long, String>? = null

    /** One short line for this provider, or null if unknown. */
    suspend fun line(base: String): String? {
        val id = Providers.statusId(base) ?: return null
        val now = System.currentTimeMillis()
        val body = cache?.takeIf { now - it.first < 5 * 60_000L }?.second ?: fetch()?.also { cache = now to it } ?: return null
        return summarize(body, id)
    }

    private suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(URL_STATUS).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "GET"
                c.connectTimeout = 8_000
                c.readTimeout = 8_000
                if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
            } finally { c.disconnect() }
        } catch (_: Exception) { null }
    }

    fun summarize(body: String, id: String): String? = try {
        val a = JSONObject(body).getJSONArray("services")
        var out: String? = null
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("id") == id) {
                val n = o.optInt("activeIncidents", 0)
                val d = o.optString("description").ifBlank { "status unknown" }
                out = o.optString("name", id) + ": " + d + if (n > 0) " ($n active incident${if (n == 1) "" else "s"})" else ""
            }
        }
        out
    } catch (_: Exception) { null }
}
