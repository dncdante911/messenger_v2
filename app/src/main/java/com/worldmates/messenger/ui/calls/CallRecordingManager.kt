package com.worldmates.messenger.ui.calls

import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.VideoTrack
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Менеджер запису дзвінків
 * Записує аудіо дзвінка у файл
 * Сповіщає всіх учасників про запис
 */
class CallRecordingManager(private val context: Context) {

    private val TAG = "CallRecordingManager"

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0

    var isRecording: Boolean = false
        private set

    /**
     * Почати запис аудіо дзвінка
     * @param callId ID дзвінка для ідентифікації файлу
     * @param calleeName Ім'я співрозмовника
     * @return true якщо запис розпочато успішно
     */
    fun startRecording(callId: String, calleeName: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        return try {
            // Create output directory
            val recordingsDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "call_recordings"
            )
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            // Create output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val sanitizedName = calleeName.replace(Regex("[^a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9]"), "_")
            outputFile = File(recordingsDir, "call_${sanitizedName}_$timestamp.m4a")

            // Configure MediaRecorder
            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Recording started: ${outputFile?.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanupRecorder()
            false
        }
    }

    /**
     * Зупинити запис
     * @return Шлях до файлу запису, або null якщо помилка
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }

        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            isRecording = false

            val duration = (System.currentTimeMillis() - recordingStartTime) / 1000
            Log.d(TAG, "Recording stopped. Duration: ${duration}s, File: ${outputFile?.absolutePath}")

            // Save to MediaStore for visibility in file managers
            saveToMediaStore()

            outputFile?.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            cleanupRecorder()
            null
        }
    }

    /**
     * Зберегти запис у MediaStore
     */
    private fun saveToMediaStore() {
        val file = outputFile ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WorldMates/Recordings")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                    Log.d(TAG, "Recording saved to MediaStore: $it")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to MediaStore", e)
            }
        }
    }

    /**
     * Отримати тривалість поточного запису в секундах
     */
    fun getRecordingDuration(): Int {
        if (!isRecording) return 0
        return ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
    }

    /**
     * Перевірити чи доступний запис
     */
    fun isRecordingAvailable(): Boolean {
        return true // MediaRecorder always available on Android
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up recorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    /**
     * Очистити ресурси
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        cleanupRecorder()
    }
}
