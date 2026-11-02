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

/** What the student is doing right now. */
enum class Mode(val label: String) {
    TUTOR("Tutor"), EXPLAIN("Explain"), QUIZ("Quiz me"), SUMMARY("Summary"), CARDS("Flashcards")
}

/** The words given to the model, in one place. The "what will be sent" box shows the student's text; these are the fixed instructions. */
object StudyPrompts {
    const val BASE = "You are Focus Study Mate, a patient, kind tutor for a student. " +
        "Keep answers short and clear: short paragraphs, plain text, no tables. Do not invent facts; if you are not sure, say so. " +
        "Help the student think: for a question, prefer a hint or one guiding question first, and give the full answer when they ask for it or are stuck. " +
        "Never shame or pressure. If the student seems tired or stressed, suggest a short break. " +
        "You can only talk: you cannot change anything on the phone."

    const val CARE = "The student earlier said something that worried the app. Stay gentle. No study pressure or tasks. " +
        "Encourage them to talk to someone they trust or a local helpline or emergency service. Keep it short."

    private fun task(m: Mode): String = when (m) {
        Mode.TUTOR -> ""
        Mode.EXPLAIN -> "Task: explain the material the student sends. Start with the main idea in one line, then the key points simply, then one small example, then ask one question to check understanding."
        Mode.QUIZ -> "Task: quiz the student on the material they send. Ask ONE question at a time, starting now with question 1. Wait for the answer, say if it is right, explain briefly, then ask the next. After 5 questions, give a short score and what to review."
        Mode.SUMMARY -> "Task: summarize the material the student sends in at most 8 short bullet points, then list the 3 most important terms."
        Mode.CARDS -> "Task: make 8 flashcards from the material the student sends. Format each as two lines: \"Q: ...\" then \"A: ...\". No other text."
    }

    fun system(mode: Mode, care: Boolean, language: String): String = buildString {
        append(BASE)
        val t = task(mode)
        if (t.isNotEmpty()) append("\n\n").append(t)
        if (care) append("\n\n").append(CARE)
        append("\n\nReply in the language of the student's latest message. If unsure, use $language.")
    }
}
