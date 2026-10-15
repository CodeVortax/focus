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

package com.barq.focus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.barq.focus.data.CardType
import com.barq.focus.data.FocusCard
import com.barq.focus.data.I18n
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.data.Store
import java.util.Locale

/** Plays the ring and/or speaks a message. Everything runs on the main thread. */
object Alerts {
    private val h = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var gen = 0

    fun stop() {
        gen++
        h.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
    }

    fun run(c: Context, sound: String, ringSec: Int, text: String?, speakFirst: Boolean) {
        stop()
        val my = gen
        val app = c.applicationContext

        fun ring(next: () -> Unit) {
            if (sound == "silent" || ringSec <= 0) { next(); return }
            try {
                val uri = if (sound.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(sound)
                val r = RingtoneManager.getRingtone(app, uri)
                if (r == null) { next(); return }
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
                r.play()
                ringtone = r
            } catch (_: Exception) { next(); return }
            h.postDelayed({
                if (gen == my) {
                    try { ringtone?.stop() } catch (_: Exception) {}
                    ringtone = null
                    next()
                }
            }, ringSec * 1000L)
        }

        fun say(next: () -> Unit) {
            if (text == null) { next(); return }
            speak(app, text, my, next)
        }

        if (speakFirst) say { ring { } } else ring { say { } }
    }

    private fun speak(app: Context, text: String, my: Int, next: () -> Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(app) { status ->
            if (gen != my) {
                try { t?.shutdown() } catch (_: Exception) {}
                return@TextToSpeech
            }
            if (status != TextToSpeech.SUCCESS) { next(); return@TextToSpeech }
            t?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            t?.setLanguage(I18n.speakLocale())
            t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { h.post { if (gen == my) { try { t?.shutdown() } catch (_: Exception) {}; next() } } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { h.post { if (gen == my) { try { t?.shutdown() } catch (_: Exception) {}; next() } } }
            })
            t?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus")
        }
        tts = t
    }
}

/** Decides when to alert for the running timer. Called by the alarm AND by the watcher tick; safe to call often. */
object Alerter {
    private fun nagText(n: Int) = when {
        n < 10 -> trf("%d minutes over time. Go faster, bro!", n)
        n < 20 -> trf("%d minutes over. Come on buddy, wrap it up.", n)
        else -> trf("%d minutes over. Add time or stop the timer, bro.", n)
    }

    fun check(c: Context, store: Store) {
        if (store.activeCardId == -1L || store.activePaused) return
        val now = System.currentTimeMillis()
        val end = store.activeEnd
        if (now < end) return
        val nagMs = store.nagMin * 60_000L
        if (store.alertedEnd == end && (nagMs <= 0L || now - store.lastNag < nagMs - 500)) return

        val card = store.cards().firstOrNull { it.id == store.activeCardId } ?: return
        if (store.alertedEnd != end) {
            store.alertedEnd = end
            store.lastNag = now
            post(c, card, trf("%s: time's up", card.title), tr("Add time or wrap up."))
            Alerts.run(c, card.sound, store.ringSec, if (store.speak) trf("Time's up, buddy! %s.", card.title) else null, speakFirst = true)
        } else if (card.type == CardType.STUDY) {
            store.lastNag = now
            val n = ((now - end) / 60_000L).toInt().coerceAtLeast(1)
            post(c, card, trf("%s: %d min over", card.title, n), tr("Add time or stop the timer."))
            Alerts.run(c, card.sound, store.nagRingSec, if (store.speak) nagText(n) else null, speakFirst = false)
        } else return
        TimerAlarm.reschedule(c, store)
    }

    private fun post(c: Context, card: FocusCard, title: String, text: String) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(TimerAlarm.CHANNEL, tr("Timer done"), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(true)
                }
            )
            nm.notify(
                TimerAlarm.NOTIF_ID,
                NotificationCompat.Builder(c, TimerAlarm.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(TimerAlarm.openApp(c))
                    .addAction(0, tr("+10 min"), TimerAlarm.broadcast(c, TimerAlarm.ACTION_ADD))
                    .addAction(0, tr("Hush"), TimerAlarm.broadcast(c, TimerAlarm.ACTION_STOP))
                    .build()
            )
        } catch (_: Exception) {}
    }
}
