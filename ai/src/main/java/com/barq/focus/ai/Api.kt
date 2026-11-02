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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatMsg(val role: String, val text: String)

class Provider(val name: String, val base: String, val model: String, val keyHelp: String = "")

object Providers {
    val list = listOf(
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "platform.openai.com/api-keys"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openrouter.ai/keys (has free models)"),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", "aistudio.google.com/apikey (has a free tier)"),
        Provider("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-5-5", "console.anthropic.com"),
        Provider("Custom", "https://", ""),
    )

    fun byBase(base: String): Provider? = list.firstOrNull { it.base == base.trim().trimEnd('/') }

    /** Guesses the provider from the shape of the key, so pasting it is enough. */
    fun detect(key: String): Provider? {
        val k = key.trim()
        return when {
            k.startsWith("sk-or-") -> list[1]
            k.startsWith("sk-ant-") -> list[3]
            k.startsWith("AIza") -> list[2]
            k.startsWith("sk-") -> list[0]
            else -> null
        }
    }
}

/** Picks and filters the model list the provider returns, so nobody has to type a model name. */
object Models {
    private val NOT_CHAT = listOf(
        "embed", "whisper", "tts", "dall-e", "moderation", "image", "audio", "realtime", "transcribe", "davinci", "babbage", "rerank", "imagen", "veo",
    )
    private val PREFER = listOf(
        "gpt-4o-mini", "gpt-4.1-mini", "gpt-5-mini", "gemini-2.0-flash", "gemini-2.5-flash", "gemini-flash", "claude-haiku", "haiku", "flash", "mini",
    )

    fun chatOnly(ids: List<String>): List<String> =
        ids.filter { id -> val l = id.lowercase(); NOT_CHAT.none { l.contains(it) } }.distinct().sorted()

    /** A fast, cheap model if there is one (good for studying), otherwise the first in the list. */
    fun best(ids: List<String>): String? {
        for (p in PREFER) {
            val found = ids.filter { it.lowercase().contains(p) }
            if (found.isNotEmpty()) {
                val stable = found.filter { !it.contains("preview") && !it.contains("exp") && !it.contains(":free") }.ifEmpty { found }
                return stable.minByOrNull { it.length }
            }
        }
        return ids.firstOrNull()
    }
}

/** One call to any "OpenAI-compatible" chat endpoint. No streaming, no tools, nothing stored. */
object Api {
    suspend fun chat(base: String, key: String, model: String, system: String, msgs: List<ChatMsg>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!base.startsWith("https://")) return@withContext Result.failure(Exception("The address must start with https://"))
                val arr = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                msgs.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
                val body = JSONObject().put("model", model).put("messages", arr).toString()

                val c = URL(base.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 15_000
                    c.readTimeout = 90_000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    auth(c, base, key)
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = c.responseCode
                    val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code in 200..299) {
                        val reply = parseContent(text)
                        if (reply.isNullOrBlank()) Result.failure(Exception("The model sent an empty answer. Try again."))
                        else Result.success(reply)
                    } else Result.failure(Exception(errorText(code, text)))
                } finally { c.disconnect() }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("No internet, or the address is wrong."))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("The provider took too long. Try again."))
            } catch (e: Exception) {
                Result.failure(Exception("Could not reach the provider."))
            }
        }

    private fun auth(c: HttpURLConnection, base: String, key: String) {
        c.setRequestProperty("Authorization", "Bearer $key")
        if (base.contains("anthropic.com")) { c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01") }
    }

    /** Asks the provider which models this key can use. */
    suspend fun listModels(base: String, key: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (!base.startsWith("https://")) return@withContext Result.failure(Exception("The address must start with https://"))
            val c = URL(base.trimEnd('/') + "/models").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "GET"
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                auth(c, base, key)
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code in 200..299) Result.success(parseModels(text)) else Result.failure(Exception(errorText(code, text)))
            } finally { c.disconnect() }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("No internet, or the address is wrong."))
        } catch (e: Exception) {
            Result.failure(Exception("Could not reach the provider."))
        }
    }

    fun parseModels(body: String): List<String> = try {
        val a = JSONObject(body).getJSONArray("data")
        (0 until a.length()).mapNotNull { i -> a.getJSONObject(i).optString("id").takeIf { id -> id.isNotBlank() }?.removePrefix("models/") }
    } catch (_: Exception) { emptyList() }

    fun parseContent(body: String): String? = try {
        val m = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        if (m.isNull("content")) null else m.getString("content")
    } catch (_: Exception) { null }

    fun errorText(code: Int, body: String): String {
        val detail = try { JSONObject(body).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
        val base = when (code) {
            401, 403 -> "The provider refused the key. Check it in Settings."
            404 -> "Wrong address or model name. Check Settings."
            429 -> "Too many requests, or no credit left. Try again later."
            in 500..599 -> "The provider had a problem. Try again."
            else -> "The request failed ($code)."
        }
        return if (detail.isNullOrBlank()) base else base + "\n" + detail.take(200)
    }
}
