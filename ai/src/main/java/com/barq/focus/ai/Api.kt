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

/** What went wrong, so the app can react: switch model, wait and retry, or tell you plainly. */
enum class ErrKind { KEY, MODEL, RATE, QUOTA, SERVER, OTHER }

class ApiException(val kind: ErrKind, message: String, val retryAfterSec: Int? = null) : Exception(message)

class Provider(val name: String, val base: String, val model: String, val keyHelp: String = "")

object Providers {
    val list = listOf(
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "platform.openai.com/api-keys"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openrouter.ai/keys (has free models)"),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", "aistudio.google.com/apikey (has a free tier)"),
        Provider("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-5-5", "console.anthropic.com"),
        Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "platform.deepseek.com/api_keys"),
        Provider("Custom", "https://", ""),
    )

    fun byBase(base: String): Provider? = list.firstOrNull { it.base == base.trim().trimEnd('/') }

    private fun named(n: String): Provider = list.first { it.name == n }

    /** Guesses the provider from the shape of the key, so pasting it is enough. */
    fun detect(key: String): Provider? {
        val k = key.trim()
        return when {
            k.startsWith("sk-or-") -> named("OpenRouter")
            k.startsWith("sk-ant-") -> named("Anthropic")
            k.startsWith("AIza") -> named("Gemini")
            Regex("^sk-[0-9a-f]{32}$").matches(k) -> named("DeepSeek")   // DeepSeek keys are sk- plus 32 hex characters
            k.startsWith("sk-") -> named("OpenAI")
            else -> null
        }
    }

    /** The id this provider has on the status page, or null for a custom address. */
    fun statusId(base: String): String? = when {
        base.contains("api.openai.com") -> "openai"
        base.contains("openrouter.ai") -> "openrouter"
        base.contains("generativelanguage.googleapis.com") -> "gemini-api"
        base.contains("api.anthropic.com") -> "anthropic"
        base.contains("api.deepseek.com") -> "deepseek"
        else -> null
    }
}

/** Picks and filters the model list the provider returns, so nobody has to type a model name. */
object Models {
    private val NOT_CHAT = listOf(
        "embed", "whisper", "tts", "dall-e", "moderation", "image", "audio", "realtime", "transcribe", "davinci", "babbage", "rerank", "imagen", "veo",
    )
    private val PREFER = listOf(
        "gpt-4o-mini", "gpt-4.1-mini", "gpt-5-mini", "deepseek-chat", "gemini-2.0-flash", "gemini-2.5-flash", "gemini-flash", "claude-haiku", "haiku", "flash", "mini",
    )

    fun chatOnly(ids: List<String>): List<String> =
        ids.filter { id -> val l = id.lowercase(); NOT_CHAT.none { l.contains(it) } }.distinct().sorted()

    private fun penalty(id: String): Int = if (id.contains("preview") || id.contains("exp") || id.contains(":free")) 1 else 0

    /** Best first: fast and cheap models ahead, stable ones ahead of previews and free-tier ones. */
    fun ranked(ids: List<String>): List<String> = ids.sortedWith(
        compareBy<String>(
            { id -> PREFER.indexOfFirst { id.lowercase().contains(it) }.let { if (it < 0) PREFER.size else it } },
            { id -> penalty(id) },
            { id -> if (PREFER.any { id.lowercase().contains(it) }) id.length else 0 },
        )
    )

    /** A fast, cheap model if there is one (good for studying), otherwise the first in the list. */
    fun best(ids: List<String>): String? = ranked(ids).firstOrNull()
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
                    } else Result.failure(ApiException(kindOf(code, text), errorText(code, text), c.getHeaderField("Retry-After")?.trim()?.toIntOrNull()))
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
                if (code in 200..299) Result.success(parseModels(text)) else Result.failure(ApiException(kindOf(code, text), errorText(code, text)))
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

    fun kindOf(code: Int, body: String): ErrKind {
        val b = body.lowercase()
        return when {
            code == 401 || code == 403 -> ErrKind.KEY
            code == 402 -> ErrKind.QUOTA
            // "limit: 0" is a model with no free allowance (a Gemini case): another model may still work
            code == 429 -> if (b.contains("limit: 0")) ErrKind.MODEL
                else if (b.contains("insufficient_quota") || b.contains("billing") || b.contains("exceeded your current quota")) ErrKind.QUOTA
                else ErrKind.RATE
            code == 404 -> ErrKind.MODEL
            code == 400 || code == 422 -> if (b.contains("model")) ErrKind.MODEL else ErrKind.OTHER
            code in 500..599 -> ErrKind.SERVER
            else -> ErrKind.OTHER
        }
    }

    fun errorText(code: Int, body: String): String {
        val detail = try { JSONObject(body).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
        val base = when (kindOf(code, body)) {
            ErrKind.KEY -> "The provider refused the key. Check it in Settings."
            ErrKind.MODEL -> "This model is not available for your key."
            ErrKind.RATE -> "Too many requests right now."
            ErrKind.QUOTA -> "No credit or quota left on your account."
            ErrKind.SERVER -> "The provider had a problem. Try again."
            ErrKind.OTHER -> "The request failed ($code)."
        }
        return if (detail.isNullOrBlank()) base else base + "\n" + detail.take(200)
    }
}
