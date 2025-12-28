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
    private var currentSentences: List<String> = emptyList()
    private var currentSentenceIndex = 0
    private var isPlaying = false
    private var prebufferedIndex = -1

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
                        currentSentenceIndex = index
                        mainHandler.post { sentenceIndexListener?.invoke(index) }
                    }
                    if (!isPlaying) return
                    if (utteranceId == null || !utteranceId.endsWith("_last")) return
                    val nextIndex = index + 1
                    if (nextIndex < currentSentences.size && prebufferedIndex != nextIndex) {
                        enqueueSentence(nextIndex, TextToSpeech.QUEUE_ADD)
                        prebufferedIndex = nextIndex
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (!isPlaying) return
                    if (utteranceId == null || !utteranceId.endsWith("_last")) return
                    val finishedIndex = parseSentenceIndex(utteranceId)
                    if (finishedIndex < 0) return
                    val nextIndex = finishedIndex + 1
                    if (nextIndex >= currentSentences.size) {
                        isPlaying = false
                        return
                    }
                    if (prebufferedIndex == nextIndex) return
                    mainHandler.post { enqueueSentence(nextIndex, TextToSpeech.QUEUE_ADD) }
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

        val clampedStart = startIndex.coerceIn(0, sentences.lastIndex)
        currentSentences = sentences
        isPlaying = true
        prebufferedIndex = -1
        enqueueSentence(clampedStart, TextToSpeech.QUEUE_FLUSH)
    }

    fun stop() {
        tts?.stop()
        isPlaying = false
        prebufferedIndex = -1
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
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        val segments = sentence.split(Regex("(?<=[;:])\\s+|\\s+--\\s+|\\s+-\\s+"))
        for (segment in segments) {
            val piece = segment.trim()
            if (piece.isEmpty()) continue
            if (sentence.length <= maxLength) {
                parts.add(piece)
            } else {
                if (current.isNotEmpty() &&
                    current.length + 1 + piece.length > maxLength
                ) {
                    parts.add(current.toString())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        if (parts.size <= 1) {
            val words = sentence.split(Regex("\\s+"))
            val fallback = mutableListOf<String>()
            val fallbackCurrent = StringBuilder()
            for (word in words) {
                if (word.isBlank()) continue
                if (fallbackCurrent.isNotEmpty() &&
                    fallbackCurrent.length + 1 + word.length > maxLength
                ) {
                    fallback.add(fallbackCurrent.toString())
                    fallbackCurrent.clear()
                }
                if (fallbackCurrent.isNotEmpty()) fallbackCurrent.append(' ')
                fallbackCurrent.append(word)
            }
            if (fallbackCurrent.isNotEmpty()) fallback.add(fallbackCurrent.toString())
            return fallback
        }
        return parts
    }

    private fun enqueueSentence(index: Int, firstMode: Int) {
        if (!ttsReady) return
        if (index < 0 || index >= currentSentences.size) return
        val sentence = currentSentences[index]
        if (sentence.isBlank()) {
            val next = index + 1
            if (next < currentSentences.size) {
                enqueueSentence(next, firstMode)
            } else {
                isPlaying = false
            }
            return
        }
        val chunks = splitLongSentence(sentence)
        var first = true
        for (chunkIndex in chunks.indices) {
            val chunk = chunks[chunkIndex]
            if (chunk.isBlank()) continue
            val mode = if (first) firstMode else TextToSpeech.QUEUE_ADD
            val isLast = chunkIndex == chunks.lastIndex
            val utteranceId = if (isLast) {
                "sentence_${index}_last"
            } else {
                "sentence_${index}_part_$chunkIndex"
            }
            tts?.speak(chunk, mode, null, utteranceId)
            first = false
        }
    }

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    companion object {
        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
