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

class Provider(val name: String, val base: String, val model: String)

object Providers {
    val list = listOf(
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        Provider("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-5-5"),
        Provider("Custom", "https://", ""),
    )
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
                    c.setRequestProperty("Authorization", "Bearer $key")
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
