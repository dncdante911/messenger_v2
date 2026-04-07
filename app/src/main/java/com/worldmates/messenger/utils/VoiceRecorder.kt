package com.worldmates.messenger.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Утилита для записи голосовых сообщений
 *
 * Fixes:
 * - updateDuration/updatePosition: прибрано зайве withContext(Dispatchers.Main) —
 *   delay() є suspend-функцією і не потребує Main-потоку
 * - startRecording/stopRecording: IO-операції на Dispatchers.IO
 * - Додано ліміт тривалості запису (MAX_DURATION_MS)
 * - Додано amplitude StateFlow для відображення waveform під час запису
 */
class VoiceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    // Поточна амплітуда мікрофону (0..32767) для відображення waveform під час запису
    private val _currentAmplitude = MutableStateFlow(0)
    val currentAmplitude: StateFlow<Int> = _currentAmplitude

    private var recordingStartTime = 0L
    private var pausedTime = 0L

    // Якість запису
    var audioQuality: AudioQuality = AudioQuality.STANDARD

    // Максимальна тривалість запису (2 хвилини)
    private val MAX_DURATION_MS = 2 * 60 * 1000L

    enum class AudioQuality(val bitRate: Int, val sampleRate: Int, val label: String) {
        COMPRESSED(64000, 22050, "Стиснутий (64 kbps)"),
        STANDARD(128000, 44100, "Стандартний (128 kbps)"),
        HIGH(256000, 48000, "Високий (256 kbps)")
    }

    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        object Paused : RecordingState()
        data class Completed(val filePath: String, val duration: Long) : RecordingState()
        data class MaxDurationReached(val filePath: String, val duration: Long) : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    companion object {
        private const val TAG = "VoiceRecorder"
    }

    /**
     * Начинает запись голосового сообщения
     */
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            currentRecordingFile = File(
                context.cacheDir,
                "VOICE_${System.currentTimeMillis()}.m4a"
            )

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(audioQuality.bitRate)
                setAudioSamplingRate(audioQuality.sampleRate)
                setOutputFile(currentRecordingFile!!.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            pausedTime = 0
            _recordingState.value = RecordingState.Recording
            Log.d(TAG, "Recording started: ${currentRecordingFile!!.absolutePath}")

            // Оновлюємо тривалість і амплітуду кожні 100мс
            updateDuration()

            true
        } catch (e: IOException) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Не вдалося почати запис: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Помилка: ${e.message}")
            false
        }
    }

    /**
     * Паузирует запись
     */
    suspend fun pauseRecording(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaRecorder != null && _recordingState.value == RecordingState.Recording) {
                mediaRecorder?.pause()
                pausedTime = System.currentTimeMillis()
                _currentAmplitude.value = 0
                _recordingState.value = RecordingState.Paused
                Log.d(TAG, "Recording paused")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recording: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Не вдалося паузирати: ${e.message}")
            false
        }
    }

    /**
     * Возобновляет запись
     */
    suspend fun resumeRecording(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaRecorder != null && _recordingState.value == RecordingState.Paused) {
                mediaRecorder?.resume()
                recordingStartTime -= (System.currentTimeMillis() - pausedTime)
                pausedTime = 0
                _recordingState.value = RecordingState.Recording
                Log.d(TAG, "Recording resumed")

                updateDuration()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming recording: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Не вдалося відновити: ${e.message}")
            false
        }
    }

    /**
     * Завершает запись и возвращает путь к файлу
     */
    suspend fun stopRecording(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Stop recording error: ${e.message}")
                }

                mediaRecorder?.release()
                mediaRecorder = null

                val duration = System.currentTimeMillis() - recordingStartTime
                _currentAmplitude.value = 0
                _recordingState.value = RecordingState.Completed(
                    currentRecordingFile!!.absolutePath,
                    duration
                )
                Log.d(TAG, "Recording stopped: $duration ms")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Не вдалося завершити: ${e.message}")
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    /**
     * Отменяет запись
     */
    suspend fun cancelRecording(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping recorder on cancel: ${e.message}")
                }
                mediaRecorder?.release()
                mediaRecorder = null
            }

            currentRecordingFile?.let {
                if (it.exists()) it.delete()
            }

            _currentAmplitude.value = 0
            _recordingState.value = RecordingState.Idle
            _recordingDuration.value = 0
            Log.d(TAG, "Recording cancelled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Помилка скасування: ${e.message}")
            false
        }
    }

    /**
     * Оновлює тривалість і амплітуду кожні 100мс.
     * delay() є suspend-функцією — withContext(Main) не потрібен.
     * При досягненні MAX_DURATION_MS запис зупиняється автоматично.
     */
    private suspend fun updateDuration() {
        while (_recordingState.value == RecordingState.Recording) {
            val duration = System.currentTimeMillis() - recordingStartTime
            _recordingDuration.value = duration

            // Знімаємо амплітуду для waveform-анімації під час запису
            _currentAmplitude.value = try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (_: Exception) { 0 }

            // Авто-завершення при досягненні ліміту
            if (duration >= MAX_DURATION_MS) {
                Log.d(TAG, "Max duration reached ($MAX_DURATION_MS ms), auto-stopping")
                mediaRecorder?.let {
                    try { it.stop() } catch (_: Exception) {}
                    it.release()
                }
                mediaRecorder = null
                _currentAmplitude.value = 0
                _recordingState.value = RecordingState.MaxDurationReached(
                    currentRecordingFile!!.absolutePath,
                    duration
                )
                break
            }

            delay(100)
        }
    }

    /**
     * Получает текущую длительность в секундах
     */
    fun getDurationInSeconds(): Float {
        return _recordingDuration.value / 1000f
    }

    /**
     * Форматирует длительность в MM:SS
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Утилита для воспроизведения голосовых сообщений
 */
class VoicePlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _currentPlayingUrl = MutableStateFlow<String?>(null)
    val currentPlayingUrl: StateFlow<String?> = _currentPlayingUrl

    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Playing : PlaybackState()
        object Paused : PlaybackState()
        object Stopped : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }

    companion object {
        private const val TAG = "VoicePlayer"
    }

    /**
     * Начинает воспроизведение файла или URL
     */
    suspend fun play(filePathOrUrl: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            stop()

            mediaPlayer = MediaPlayer().apply {
                if (filePathOrUrl.startsWith("http://") || filePathOrUrl.startsWith("https://")) {
                    Log.d(TAG, "Playing from URL: $filePathOrUrl")
                    setDataSource(context, android.net.Uri.parse(filePathOrUrl))
                } else {
                    Log.d(TAG, "Playing from file: $filePathOrUrl")
                    setDataSource(filePathOrUrl)
                }

                // При завершенні відтворення — скидаємо стан
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.Stopped
                    _currentPosition.value = 0
                    _currentPlayingUrl.value = null
                }

                prepare()
                start()

                _duration.value = duration.toLong()
                _playbackState.value = PlaybackState.Playing
                _currentPlayingUrl.value = filePathOrUrl
            }

            updatePosition()
            Log.d(TAG, "Playback started: $filePathOrUrl")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
            _playbackState.value = PlaybackState.Error("Не вдалося відтворити: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            _playbackState.value = PlaybackState.Error("Помилка: ${e.message}")
            false
        }
    }

    /**
     * Паузирует воспроизведение
     */
    fun pause(): Boolean {
        return try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _playbackState.value = PlaybackState.Paused
                Log.d(TAG, "Playback paused")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing: ${e.message}", e)
            false
        }
    }

    /**
     * Возобновляет воспроизведение
     */
    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
                _playbackState.value = PlaybackState.Playing
                updatePosition()
                Log.d(TAG, "Playback resumed")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming: ${e.message}", e)
            false
        }
    }

    /**
     * Останавливает воспроизведение
     */
    fun stop(): Boolean {
        return try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
            _playbackState.value = PlaybackState.Stopped
            _currentPosition.value = 0
            _currentPlayingUrl.value = null
            Log.d(TAG, "Playback stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping: ${e.message}", e)
            false
        }
    }

    /**
     * Устанавливает позицию воспроизведения
     */
    fun seek(positionMs: Long): Boolean {
        return try {
            mediaPlayer?.seekTo(positionMs.toInt())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking: ${e.message}", e)
            false
        }
    }

    /**
     * Оновлює позицію кожні 100мс.
     * delay() є suspend-функцією — withContext(Main) не потрібен.
     */
    private suspend fun updatePosition() {
        while (mediaPlayer?.isPlaying == true) {
            _currentPosition.value = mediaPlayer?.currentPosition?.toLong() ?: 0
            delay(100)
        }
    }

    /**
     * Форматирует позицию в MM:SS
     */
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun release() {
        stop()
    }
}
