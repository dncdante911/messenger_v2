package com.worldmates.messenger.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Listens for a Russian voice trigger phrase while the app is in the foreground.
 * When the phrase is recognised it emits [Command.StartVoiceMessage].
 *
 * SpeechRecognizer MUST be created and used on the main thread — this is
 * guaranteed because all scheduling goes through [mainHandler].
 *
 * Trigger phrases:
 *   "голосовое сообщение"  ← primary (most distinctive)
 *   "голосовое"            ← shorter variant
 *   "записать"             ← alternative
 *   "запись"               ← alternative
 */
class SpeechCommandManager(private val context: Context) {

    sealed class Command {
        object StartVoiceMessage : Command()
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)
    val commands: SharedFlow<Command> = _commands

    private var recognizer: SpeechRecognizer? = null
    private var active = false      // manager is started by the caller
    private var micBusy = false     // mic is occupied by an active recording
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SpeechCommandManager"
        private const val RESTART_DELAY_MS = 700L

        val TRIGGER_PHRASES = listOf(
            "голосовое сообщение",
            "голосовое",
            "записать",
            "запись"
        )
    }

    /** Call when the screen becomes visible and RECORD_AUDIO permission is granted. */
    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }
        active = true
        Log.d(TAG, "started")
        scheduleListening(0)
    }

    /** Call when the screen is paused or destroyed. */
    fun stop() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        Log.d(TAG, "stopped")
    }

    /**
     * Pause listening while [busy] == true (mic is recording).
     * Automatically resumes when called with [busy] == false.
     */
    fun setMicBusy(busy: Boolean) {
        if (micBusy == busy) return
        micBusy = busy
        if (busy) {
            mainHandler.removeCallbacksAndMessages(null)
            destroyRecognizer()
            Log.d(TAG, "paused (mic busy)")
        } else {
            Log.d(TAG, "resuming after mic released")
            scheduleListening(RESTART_DELAY_MS)
        }
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private fun scheduleListening(delayMs: Long) {
        mainHandler.postDelayed({
            if (active && !micBusy) startListeningNow()
        }, delayMs)
    }

    private fun startListeningNow() {
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {

                override fun onResults(results: Bundle?) {
                    handleResults(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    scheduleListening(RESTART_DELAY_MS)
                }

                override fun onPartialResults(partial: Bundle?) {
                    // Act on partials for a faster response
                    handleResults(partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }

                override fun onError(error: Int) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RESTART_DELAY_MS
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                        else -> 1200L
                    }
                    scheduleListening(delay)
                }

                // Unused required callbacks
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            sr.startListening(intent)
        }
    }

    private fun handleResults(results: List<String>?) {
        if (results.isNullOrEmpty() || micBusy) return
        val lower = results.map { it.lowercase() }
        val triggered = lower.any { text ->
            TRIGGER_PHRASES.any { phrase -> text.contains(phrase) }
        }
        if (triggered) {
            Log.d(TAG, "Trigger detected in: $results")
            _commands.tryEmit(Command.StartVoiceMessage)
        }
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }
}
