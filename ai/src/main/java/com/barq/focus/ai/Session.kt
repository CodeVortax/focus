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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/** Text waiting for the student's OK before it is sent. */
class Pending(val mode: Mode, val text: String)

/** Everything the screen shows. The chat lives in memory only: closing the app or tapping New clears it. */
class Session(ctx: Context) {
    val prefs = AiPrefs(ctx)
    val messages = mutableStateListOf<ChatMsg>()
    var mode by mutableStateOf(Mode.TUTOR)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var careMode by mutableStateOf(false)
    var incoming by mutableStateOf<String?>(null)
    var cut by mutableStateOf(false)
    var pending by mutableStateOf<Pending?>(null)
    var screen by mutableStateOf("chat")
    var notice by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var modelName by mutableStateOf("")
    var models by mutableStateOf<List<String>>(emptyList())
    var modelsBusy by mutableStateOf(false)

    init { refresh() }

    /** Re-reads the settings (after they change) so the screens show the right state. */
    fun refresh() { ready = prefs.ready; modelName = prefs.model }

    companion object { const val MAX_MATERIAL = 12_000 }

    private fun language(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    /** Text shared or selected from another app. */
    fun receive(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        cut = t.length > MAX_MATERIAL
        incoming = t.take(MAX_MATERIAL)
        screen = "chat"
    }

    fun clear() {
        messages.clear(); error = null; careMode = false; busy = false; mode = Mode.TUTOR
    }

    /** What the student typed. A worrying message gets a fixed kind answer and is not sent anywhere. */
    fun send(scope: CoroutineScope, text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        error = null
        if (AiSafety.needsCare(t)) {
            messages.add(ChatMsg("user", t))
            careMode = true
            messages.add(ChatMsg("assistant", AiSafety.careMessage(t.any { it in '؀'..'ۿ' })))
            return
        }
        if (!checkReady()) return
        messages.add(ChatMsg("user", t))
        ask(scope)
    }

    /** Study material the student chose to send (after the preview). It starts a fresh session in the chosen mode. */
    fun startMaterial(scope: CoroutineScope, p: Pending) {
        if (busy || !checkReady()) return
        clear()
        mode = p.mode
        messages.add(ChatMsg("user", p.text))
        incoming = null
        ask(scope)
    }

    private fun checkReady(): Boolean {
        if (prefs.ready) return true
        error = "Open Settings first: add your key and agree to what is sent."
        return false
    }

    private fun ask(scope: CoroutineScope) {
        val key = prefs.key() ?: run { error = "The key could not be read. Add it again in Settings."; return }
        busy = true
        notice = null
        val system = StudyPrompts.system(mode, careMode, language())
        val history = messages.takeLast(20).toList()
        val base = prefs.base
        scope.launch {
            val r = Auto.chat(base, key, prefs.model, system, history, models)
            if (r.reply != null) {
                messages.add(ChatMsg("assistant", AiReplyParser.clean(r.reply, 4000)))
                if (r.model != prefs.model) { prefs.model = r.model; refresh() }   // keep the model that worked
                notice = r.note
            } else error = explain(base, r.error)
            busy = false
        }
    }

    /** The error text, plus the provider's status when that could be the cause. */
    private suspend fun explain(base: String, e: Throwable?): String {
        val text = e?.message ?: "Something went wrong."
        val kind = (e as? ApiException)?.kind
        val hint = when (kind) {
            ErrKind.MODEL -> "\nTry Settings, then Find a working model."
            ErrKind.RATE -> "\nWait a minute, or pick another model in Settings."
            ErrKind.QUOTA -> "\nTo use free credit, pick a provider with a free tier in Settings (Groq, Gemini or OpenRouter free models)."
            else -> ""
        }
        val worthChecking = kind == null || kind == ErrKind.RATE || kind == ErrKind.SERVER || kind == ErrKind.OTHER
        val status = if (prefs.statusCheck && worthChecking) Status.line(base)?.let { "\nProvider status: $it" } ?: "" else ""
        return text + hint + status
    }

    /** Tries the best few models with a tiny request and keeps the first that works. */
    fun findWorking(scope: CoroutineScope, done: (model: String?, note: String) -> Unit) {
        val key = prefs.key() ?: run { done(null, "Add a key first."); return }
        modelsBusy = true
        scope.launch {
            val r = Auto.findWorking(prefs.base, key, prefs.model, models) { done(null, it) }
            modelsBusy = false
            r.onSuccess { m -> prefs.model = m; refresh(); done(m, "Works. Using $m.") }
                .onFailure { done(null, explain(prefs.base, it)) }
        }
    }

    /** A tiny request to check the address, model and key. */
    fun test(scope: CoroutineScope, done: (String) -> Unit) {
        val key = prefs.key() ?: run { done("Add a key first."); return }
        scope.launch {
            val r = Api.chat(prefs.base, key, prefs.model, "Reply with the single word OK.", listOf(ChatMsg("user", "Test")))
            done(r.fold({ "Works." }, { explain(prefs.base, it) }))
        }
    }

    /** Saves the address and key, asks the provider for its models and picks a good one, so no model name has to be typed. */
    fun connect(scope: CoroutineScope, base: String, typedKey: String, model: String, verify: Boolean, done: (chosen: String, note: String) -> Unit) {
        prefs.base = base
        if (typedKey.isNotBlank()) prefs.setKey(typedKey)
        val key = prefs.key() ?: run { done(model, "Paste your key first."); return }
        modelsBusy = true
        scope.launch {
            val r = Api.listModels(base, key)
            modelsBusy = false
            r.onSuccess { ids ->
                val list = Models.chatOnly(ids)
                models = list
                val chosen = if (model.isNotBlank() && model in list) model else Models.best(list) ?: model
                prefs.model = chosen
                refresh()
                if (list.isEmpty()) { done(chosen, "Connected, but the provider listed no models. Type a model name below."); return@onSuccess }
                if (!verify) { done(chosen, "Connected. ${list.size} models found. Using $chosen."); return@onSuccess }
                // test the best few with a tiny request, so the one we keep really works for this key
                modelsBusy = true
                val w = Auto.findWorking(base, key, chosen, list) { done(chosen, it) }
                modelsBusy = false
                w.onSuccess { m -> prefs.model = m; refresh(); done(m, "Connected and tested. Using $m.") }
                    .onFailure { e -> done(chosen, explain(base, e)) }
            }.onFailure {
                refresh()
                done(model, "Saved, but the model list could not be loaded (${it.message}). Type a model name below.")
            }
        }
    }
}
