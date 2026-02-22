package com.worldmates.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.EncryptedMediaHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Фоновий сервіс для відтворення аудіо/музики.
 *
 * Можливості:
 * - Відтворення у фоні (при згорнутому додатку та вимкненому екрані)
 * - Управління з шторки сповіщень (play/pause/stop) — автоматично через Media3 MediaSession
 * - Управління з екрану блокування (lock screen) — автоматично через Media3 MediaSession
 * - Підтримка кнопок Bluetooth-гарнітур
 * - Розшифрування AES-256-GCM медіафайлів
 *
 * Архітектура:
 * - Extends MediaSessionService (Media3) — це забезпечує автоматичну
 *   нотифікацію з кнопками керування та lock screen controls.
 * - Немає потреби в ручному BuildNotification — Media3 робить це сам.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "MusicPlaybackService"

        // Канал нотифікації (Media3 сам управляє нотифікацією всередині цього каналу)
        const val CHANNEL_ID      = "worldmates_music_playback"
        const val NOTIFICATION_ID = 2001

        // Публічні стани для UI
        private val _playbackState = MutableStateFlow(MusicPlaybackState())
        val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

        private val _currentTrackInfo = MutableStateFlow(TrackInfo())
        val currentTrackInfo: StateFlow<TrackInfo> = _currentTrackInfo.asStateFlow()

        @Volatile private var serviceInstance: MusicPlaybackService? = null

        fun isRunning(): Boolean = serviceInstance != null

        // Intent actions
        private const val ACTION_PLAY   = "com.worldmates.messenger.action.PLAY"
        private const val ACTION_PAUSE  = "com.worldmates.messenger.action.PAUSE"
        private const val ACTION_RESUME = "com.worldmates.messenger.action.RESUME"
        private const val ACTION_STOP   = "com.worldmates.messenger.action.STOP"

        // Intent extras
        private const val EXTRA_AUDIO_URL  = "audio_url"
        private const val EXTRA_TITLE      = "title"
        private const val EXTRA_ARTIST     = "artist"
        private const val EXTRA_TIMESTAMP  = "timestamp"
        private const val EXTRA_IV         = "iv"
        private const val EXTRA_TAG        = "tag"

        /**
         * Запуск відтворення треку.
         */
        fun startPlayback(
            context: Context,
            audioUrl: String,
            title: String = "Аудіо",
            artist: String = "",
            timestamp: Long = 0L,
            iv: String? = null,
            tag: String? = null
        ) {
            context.startService(
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_AUDIO_URL, audioUrl)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_ARTIST, artist)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                    putExtra(EXTRA_IV, iv)
                    putExtra(EXTRA_TAG, tag)
                }
            )
        }

        fun pausePlayback(context: Context) {
            serviceInstance?.player?.pause()
                ?: context.startService(
                    Intent(context, MusicPlaybackService::class.java).apply { action = ACTION_PAUSE }
                )
        }

        fun resumePlayback(context: Context) {
            serviceInstance?.player?.play()
                ?: context.startService(
                    Intent(context, MusicPlaybackService::class.java).apply { action = ACTION_RESUME }
                )
        }

        fun stopPlayback(context: Context) {
            serviceInstance?.let {
                it.player.stop()
                it.player.clearMediaItems()
                _playbackState.value   = MusicPlaybackState()
                _currentTrackInfo.value = TrackInfo()
                it.stopSelf()
            } ?: context.startService(
                Intent(context, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun seekTo(context: Context, position: Long) {
            serviceInstance?.player?.seekTo(position)
        }

        fun setSpeed(speed: Float) {
            serviceInstance?.player?.setPlaybackSpeed(speed)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()

        // Налаштовуємо ExoPlayer з правильними аудіо-атрибутами
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // Пауза при від'єднанні навушників
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.value = _playbackState.value.copy(isBuffering = true)
                    }
                    Player.STATE_READY -> {
                        _playbackState.value = _playbackState.value.copy(
                            isBuffering = false,
                            duration    = player.duration.coerceAtLeast(0)
                        )
                    }
                    Player.STATE_ENDED -> {
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying       = false,
                            currentPosition = 0L
                        )
                    }
                    else -> {}
                }
            }
        })

        // PendingIntent — відкриває додаток при натисканні на нотифікацію плеєра
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let { intent ->
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // MediaSession — Media3 сам генерує нотифікацію з кнопками play/pause/stop
        // і забезпечує lock screen controls.
        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivity?.let { setSessionActivity(it) } }
            .build()

        // Оновлюємо позицію щосекунди для прогрес-бару в UI
        serviceScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = player.currentPosition,
                        duration        = player.duration.coerceAtLeast(0)
                    )
                }
                delay(200)
            }
        }

        Log.d(TAG, "MusicPlaybackService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PLAY -> {
                val audioUrl  = intent.getStringExtra(EXTRA_AUDIO_URL) ?: return START_NOT_STICKY
                val title     = intent.getStringExtra(EXTRA_TITLE)     ?: "Аудіо"
                val artist    = intent.getStringExtra(EXTRA_ARTIST)    ?: ""
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)
                val iv        = intent.getStringExtra(EXTRA_IV)
                val tag       = intent.getStringExtra(EXTRA_TAG)

                _currentTrackInfo.value = TrackInfo(url = audioUrl, title = title, artist = artist)

                serviceScope.launch {
                    playAudio(audioUrl, title, artist, timestamp, iv, tag)
                }
            }
            ACTION_PAUSE  -> player.pause()
            ACTION_RESUME -> player.play()
            ACTION_STOP   -> {
                player.stop()
                player.clearMediaItems()
                _playbackState.value    = MusicPlaybackState()
                _currentTrackInfo.value = TrackInfo()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Media3 викликає цей метод для кожного контролера (шторка, lock screen,
     * Bluetooth-гарнітури). Повертаємо нашу mediaSession.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // ------------------------------------------------------------------
    // Playback logic
    // ------------------------------------------------------------------

    private suspend fun playAudio(
        audioUrl: String,
        title: String,
        artist: String,
        timestamp: Long,
        iv: String?,
        tag: String?
    ) {
        try {
            val resolvedUrl: String

            // Розшифровуємо AES-256-GCM файл, якщо потрібно
            if (EncryptedMediaHandler.isEncryptedFile(audioUrl) && iv != null && tag != null) {
                Log.d(TAG, "Decrypting audio...")
                val decrypted = withContext(Dispatchers.IO) {
                    EncryptedMediaHandler.decryptMediaFile(
                        mediaUrl  = audioUrl,
                        timestamp = timestamp,
                        iv        = iv,
                        tag       = tag,
                        type      = "audio",
                        context   = this@MusicPlaybackService
                    )
                }
                resolvedUrl = decrypted
                    ?: EncryptedMediaHandler.getFullMediaUrl(audioUrl, "audio")
                    ?: audioUrl
            } else {
                resolvedUrl = EncryptedMediaHandler.getFullMediaUrl(audioUrl, "audio") ?: audioUrl
            }

            Log.d(TAG, "Playing: $resolvedUrl")

            withContext(Dispatchers.Main) {
                // Метадані відображаються в нотифікації плеєра і на lock screen
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.ifEmpty { "WorldMates" })
                    .setDisplayTitle(title)
                    // Передаємо загальну іконку сповіщень як обкладинку
                    .setArtworkData(
                        bitmapToByteArray(
                            BitmapFactory.decodeResource(resources, android.R.drawable.ic_media_play)
                        ),
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER
                    )
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(resolvedUrl)
                    .setMediaMetadata(metadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error", e)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun bitmapToByteArray(bitmap: android.graphics.Bitmap?): ByteArray? {
        if (bitmap == null) return null
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) { null }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Відтворення музики",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Управління відтворенням аудіо"
            setShowBadge(false)
            setSound(null, null) // Тиха нотифікація для плеєра
        }
        (getSystemService(NotificationManager::class.java))
            .createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------
    // Destroy
    // ------------------------------------------------------------------

    override fun onDestroy() {
        serviceInstance = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        _playbackState.value    = MusicPlaybackState()
        _currentTrackInfo.value = TrackInfo()
        Log.d(TAG, "MusicPlaybackService destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Public data classes
    // ------------------------------------------------------------------

    data class MusicPlaybackState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L
    )

    data class TrackInfo(
        val url: String = "",
        val title: String = "",
        val artist: String = ""
    )
}
