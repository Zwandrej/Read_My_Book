package com.ajz.ereader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.ajz.ereader.R
import java.util.Locale

class TtsService : Service(), TextToSpeech.OnInitListener {
    private val binder = TtsBinder()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sentenceIndexListener: ((Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Read My Book")
            .setContentText("Playback ready")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TTS Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = parseSentenceIndex(utteranceId)
                    if (index >= 0) {
                        mainHandler.post { sentenceIndexListener?.invoke(index) }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    // No-op for now.
                }

                override fun onError(utteranceId: String?) {
                    // No-op for now.
                }
            })
        }
    }

    fun playAll(sentences: List<String>, rate: Float, pitch: Float, startIndex: Int) {
        if (!ttsReady) return
        if (sentences.isEmpty()) return
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
        tts?.stop()

        var first = true
        val clampedStart = startIndex.coerceIn(0, sentences.lastIndex)
        for (i in clampedStart until sentences.size) {
            val sentence = sentences[i]
            if (sentence.isBlank()) continue
            val chunks = splitLongSentence(sentence)
            for (chunkIndex in chunks.indices) {
                val chunk = chunks[chunkIndex]
                if (chunk.isBlank()) continue
                val mode = if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utteranceId = if (chunkIndex == 0) {
                    "sentence_$i"
                } else {
                    "sentence_${i}_part_$chunkIndex"
                }
                tts?.speak(chunk, mode, null, utteranceId)
                first = false
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun setSentenceIndexListener(listener: ((Int) -> Unit)?) {
        sentenceIndexListener = listener
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun parseSentenceIndex(utteranceId: String?): Int {
        if (utteranceId == null) return -1
        if (!utteranceId.startsWith("sentence_")) return -1
        val trimmed = utteranceId.removePrefix("sentence_")
        val digits = trimmed.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: -1
    }

    private fun splitLongSentence(sentence: String, maxLength: Int = 400): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        val words = sentence.split(Regex("\\s+"))
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() &&
                current.length + 1 + word.length > maxLength
            ) {
                parts.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    companion object {
        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
