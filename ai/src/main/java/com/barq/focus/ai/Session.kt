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
        val system = StudyPrompts.system(mode, careMode, language())
        val history = messages.takeLast(20).toList()
        scope.launch {
            Api.chat(prefs.base, key, prefs.model, system, history)
                .onSuccess { messages.add(ChatMsg("assistant", AiReplyParser.clean(it, 4000))) }
                .onFailure { error = it.message }
            busy = false
        }
    }

    /** A tiny request to check the address, model and key. */
    fun test(scope: CoroutineScope, done: (String) -> Unit) {
        val key = prefs.key() ?: run { done("Add a key first."); return }
        scope.launch {
            val r = Api.chat(prefs.base, key, prefs.model, "Reply with the single word OK.", listOf(ChatMsg("user", "Test")))
            done(r.fold({ "Works." }, { it.message ?: "Failed." }))
        }
    }
}
