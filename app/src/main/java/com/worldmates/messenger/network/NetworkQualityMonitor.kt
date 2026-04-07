package com.worldmates.messenger.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

/**
 * 📡 NetworkQualityMonitor - Моніторинг якості з'єднання
 *
 * Визначає якість інтернету та рекомендує режим роботи:
 * - EXCELLENT: Socket.IO + повне завантаження медіа
 * - GOOD: Socket.IO + завантаження превью
 * - POOR: HTTP fallback + тільки текст
 * - OFFLINE: Офлайн режим
 */
class NetworkQualityMonitor(private val context: Context) {

    companion object {
        private const val TAG = "NetworkQualityMonitor"
        private const val PING_INTERVAL_MS = 10000L // Перевірка кожні 10 секунд
        private const val PING_TIMEOUT_MS = 5000L // Таймаут пінгу 5 секунд
        private const val PING_URL = "https://worldmates.club:449/api/health"

        // Пороги для визначення якості (в мілісекундах)
        private const val EXCELLENT_THRESHOLD_MS = 200L // < 200ms = відмінно
        private const val GOOD_THRESHOLD_MS = 500L      // < 500ms = добре
        private const val POOR_THRESHOLD_MS = 2000L     // < 2000ms = погано
    }

    /**
     * Якість з'єднання
     */
    enum class ConnectionQuality {
        EXCELLENT,  // Швидке з'єднання: Socket.IO + full media
        GOOD,       // Нормальне з'єднання: Socket.IO + thumbnails
        POOR,       // Погане з'єднання: HTTP fallback + text only
        OFFLINE     // Немає з'єднання: offline mode
    }

    /**
     * Режим завантаження медіа
     */
    enum class MediaLoadMode {
        FULL,       // Завантажувати все (фото, відео повністю)
        THUMBNAILS, // Тільки превью
        NONE        // Тільки текст
    }

    data class ConnectionState(
        val quality: ConnectionQuality,
        val mediaLoadMode: MediaLoadMode,
        val latencyMs: Long,
        val isMetered: Boolean, // Мобільний інтернет (тарифікується)
        val bandwidthKbps: Int
    )

    private val _connectionState = MutableStateFlow(
        ConnectionState(
            quality = ConnectionQuality.OFFLINE,
            mediaLoadMode = MediaLoadMode.NONE,
            latencyMs = Long.MAX_VALUE,
            isMetered = false,
            bandwidthKbps = 0
        )
    )
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    // Зберігаємо посилання на callback для коректного unregister (запобігає витоку пам'яті)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Історія останніх пінгів для згладжування (thread-safe)
    private val pingHistory = java.util.Collections.synchronizedList(mutableListOf<Long>())
    private val maxHistorySize = 5

    init {
        startMonitoring()
    }

    /**
     * Запустити моніторинг
     */
    fun startMonitoring() {
        Log.d(TAG, "🔍 Starting network quality monitoring")

        // Початкова перевірка
        checkConnectionQuality()

        // Періодичний пінг
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                checkConnectionQuality()
                delay(PING_INTERVAL_MS)
            }
        }

        // Знімаємо старий callback перед реєстрацією нового
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }

        // Слухаємо зміни мережі (зберігаємо посилання для unregister в stopMonitoring)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "📶 Network available")
                checkConnectionQuality()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📵 Network lost")
                _connectionState.value = _connectionState.value.copy(
                    quality = ConnectionQuality.OFFLINE,
                    mediaLoadMode = MediaLoadMode.NONE,
                    latencyMs = Long.MAX_VALUE
                )
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val downKbps = capabilities.linkDownstreamBandwidthKbps

                Log.d(TAG, "📊 Connection: metered=$isMetered, bandwidth=$downKbps Kbps")

                _connectionState.value = _connectionState.value.copy(
                    isMetered = isMetered,
                    bandwidthKbps = downKbps
                )
            }
        }.also { connectivityManager.registerDefaultNetworkCallback(it) }
    }

    /**
     * Перевірити якість з'єднання
     */
    private fun checkConnectionQuality() {
        scope.launch {
            try {
                // Перевіряємо чи є інтернет взагалі
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork == null) {
                    updateConnectionState(ConnectionQuality.OFFLINE, Long.MAX_VALUE)
                    return@launch
                }

                // Пінгуємо сервер для вимірювання затримки
                val latency = measureLatency()

                // Додаємо до історії (thread-safe)
                synchronized(pingHistory) {
                    pingHistory.add(latency)
                    if (pingHistory.size > maxHistorySize) {
                        pingHistory.removeAt(0)
                    }
                }

                // Середня затримка (згладжування)
                val avgLatency = synchronized(pingHistory) {
                    if (pingHistory.isNotEmpty()) {
                        pingHistory.toList().average().toLong()
                    } else {
                        latency
                    }
                }

                // Визначаємо якість
                val quality = when {
                    avgLatency < EXCELLENT_THRESHOLD_MS -> ConnectionQuality.EXCELLENT
                    avgLatency < GOOD_THRESHOLD_MS -> ConnectionQuality.GOOD
                    avgLatency < POOR_THRESHOLD_MS -> ConnectionQuality.POOR
                    else -> ConnectionQuality.OFFLINE
                }

                updateConnectionState(quality, avgLatency)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking connection quality", e)
                updateConnectionState(ConnectionQuality.POOR, Long.MAX_VALUE)
            }
        }
    }

    /**
     * Виміряти затримку (ping)
     */
    private suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
        try {
            val latency = measureTimeMillis {
                val url = URL(PING_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = PING_TIMEOUT_MS.toInt()
                connection.readTimeout = PING_TIMEOUT_MS.toInt()
                connection.requestMethod = "HEAD" // Тільки заголовки, без контенту
                connection.connect()

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode !in 200..299) {
                    throw Exception("Server returned $responseCode")
                }
            }

            Log.d(TAG, "⏱️ Latency: ${latency}ms")
            return@withContext latency

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ping failed: ${e.message}")
            return@withContext Long.MAX_VALUE
        }
    }

    /**
     * Оновити стан з'єднання
     */
    private fun updateConnectionState(quality: ConnectionQuality, latency: Long) {
        // Визначаємо режим завантаження медіа
        val mediaLoadMode = when (quality) {
            ConnectionQuality.EXCELLENT -> {
                // Відмінне з'єднання - завантажуємо все
                // Але якщо мобільний інтернет - тільки превью
                if (_connectionState.value.isMetered) {
                    MediaLoadMode.THUMBNAILS
                } else {
                    MediaLoadMode.FULL
                }
            }
            ConnectionQuality.GOOD -> MediaLoadMode.THUMBNAILS
            ConnectionQuality.POOR -> MediaLoadMode.NONE
            ConnectionQuality.OFFLINE -> MediaLoadMode.NONE
        }

        val newState = _connectionState.value.copy(
            quality = quality,
            mediaLoadMode = mediaLoadMode,
            latencyMs = latency
        )

        // Логування змін
        if (newState.quality != _connectionState.value.quality) {
            Log.i(TAG, "🔄 Connection quality changed: ${_connectionState.value.quality} → ${newState.quality}")
            Log.i(TAG, "📦 Media load mode: ${newState.mediaLoadMode}")
        }

        _connectionState.value = newState
    }

    /**
     * Примусово перевірити якість
     */
    fun forceCheck() {
        Log.d(TAG, "🔄 Force checking connection quality")
        scope.launch {
            checkConnectionQuality()
        }
    }

    /**
     * Чи можна використовувати Socket.IO?
     */
    fun canUseSocketIO(): Boolean {
        return _connectionState.value.quality in listOf(
            ConnectionQuality.EXCELLENT,
            ConnectionQuality.GOOD
        )
    }

    /**
     * Чи можна завантажувати медіа?
     */
    fun canLoadMedia(): Boolean {
        return _connectionState.value.mediaLoadMode != MediaLoadMode.NONE
    }

    /**
     * Чи можна завантажувати повне медіа?
     */
    fun canLoadFullMedia(): Boolean {
        return _connectionState.value.mediaLoadMode == MediaLoadMode.FULL
    }

    /**
     * Отримати рекомендований розмір пакету для завантаження повідомлень
     */
    fun getRecommendedBatchSize(): Int {
        return when (_connectionState.value.quality) {
            ConnectionQuality.EXCELLENT -> 50  // Багато повідомлень за раз
            ConnectionQuality.GOOD -> 30       // Середньо
            ConnectionQuality.POOR -> 10       // Мало
            ConnectionQuality.OFFLINE -> 0     // Нічого
        }
    }

    /**
     * Зупинити моніторинг і звільнити ресурси
     */
    fun stopMonitoring() {
        Log.d(TAG, "⏹️ Stopping network quality monitoring")
        pingJob?.cancel()
        // Знімаємо реєстрацію callback — запобігаємо витоку пам'яті
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
        }
        scope.cancel()
    }

    /**
     * Отримати текстовий опис якості з'єднання
     */
    fun getQualityDescription(): String {
        return when (_connectionState.value.quality) {
            ConnectionQuality.EXCELLENT -> "Відмінне з'єднання (${_connectionState.value.latencyMs}ms)"
            ConnectionQuality.GOOD -> "Добре з'єднання (${_connectionState.value.latencyMs}ms)"
            ConnectionQuality.POOR -> "Погане з'єднання (${_connectionState.value.latencyMs}ms)"
            ConnectionQuality.OFFLINE -> "Немає з'єднання"
        }
    }

    /**
     * Отримати emoji індикатор якості
     */
    fun getQualityEmoji(): String {
        return when (_connectionState.value.quality) {
            ConnectionQuality.EXCELLENT -> "🟢"
            ConnectionQuality.GOOD -> "🟡"
            ConnectionQuality.POOR -> "🟠"
            ConnectionQuality.OFFLINE -> "🔴"
        }
    }
}