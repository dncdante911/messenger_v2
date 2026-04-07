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
        private const val PING_TIMEOUT_MS = 5000L
        private const val PING_URL = "https://worldmates.club:449/api/health"

        // Пороги за пропускною здатністю (Kbps) з NetworkCapabilities
        // Системні оцінки набагато дешевші ніж HTTP HEAD-запити кожні 10 с
        private const val EXCELLENT_KBPS = 5_000  // > 5 Mbps = відмінно
        private const val GOOD_KBPS      = 1_000  // > 1 Mbps = добре
        // < GOOD_KBPS = погано
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
     * Запустити моніторинг.
     *
     * Якість визначається через NetworkCapabilities (без HTTP HEAD-запитів кожні 10с):
     * - onCapabilitiesChanged: отримуємо bandwidthKbps + isMetered + NET_CAPABILITY_VALIDATED
     * - onAvailable/onLost: перемикаємо OFFLINE стан
     *
     * HTTP-пінг виконується лише при зміні мережі (не постійно) для точного вимірювання latency.
     */
    fun startMonitoring() {
        Log.d(TAG, "🔍 Starting network quality monitoring")

        // Початкова перевірка через NetworkCapabilities (без пінгу)
        evaluateFromCapabilities()

        // Знімаємо старий callback перед реєстрацією нового
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }

        // Слухаємо зміни мережі (зберігаємо посилання для unregister в stopMonitoring)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "📶 Network available")
                // При появі мережі — перевіряємо пінгом (один раз, не постійно)
                pingJob?.cancel()
                pingJob = scope.launch { checkLatencyOnce() }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📵 Network lost")
                pingJob?.cancel()
                _connectionState.value = _connectionState.value.copy(
                    quality = ConnectionQuality.OFFLINE,
                    mediaLoadMode = MediaLoadMode.NONE,
                    latencyMs = Long.MAX_VALUE,
                    bandwidthKbps = 0
                )
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val downKbps = capabilities.linkDownstreamBandwidthKbps
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                Log.d(TAG, "📊 Connection: metered=$isMetered, bandwidth=${downKbps}Kbps, validated=$isValidated")

                // Визначаємо якість за пропускною здатністю (системна оцінка, без HTTP-запитів)
                val quality = when {
                    !isValidated             -> ConnectionQuality.POOR
                    downKbps >= EXCELLENT_KBPS -> ConnectionQuality.EXCELLENT
                    downKbps >= GOOD_KBPS      -> ConnectionQuality.GOOD
                    downKbps > 0               -> ConnectionQuality.POOR
                    else                       -> ConnectionQuality.OFFLINE
                }

                _connectionState.value = _connectionState.value.copy(
                    quality = quality,
                    mediaLoadMode = computeMediaMode(quality, isMetered),
                    isMetered = isMetered,
                    bandwidthKbps = downKbps
                )
            }
        }.also { connectivityManager.registerDefaultNetworkCallback(it) }
    }

    /**
     * Оцінити якість з'єднання через поточні NetworkCapabilities (без мережевого запиту)
     */
    private fun evaluateFromCapabilities() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            updateConnectionState(ConnectionQuality.OFFLINE, Long.MAX_VALUE)
            return
        }
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (caps == null) {
            updateConnectionState(ConnectionQuality.POOR, Long.MAX_VALUE)
            return
        }

        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val downKbps = caps.linkDownstreamBandwidthKbps
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val quality = when {
            !isValidated             -> ConnectionQuality.POOR
            downKbps >= EXCELLENT_KBPS -> ConnectionQuality.EXCELLENT
            downKbps >= GOOD_KBPS      -> ConnectionQuality.GOOD
            downKbps > 0               -> ConnectionQuality.POOR
            else                       -> ConnectionQuality.OFFLINE
        }

        _connectionState.value = _connectionState.value.copy(
            quality = quality,
            mediaLoadMode = computeMediaMode(quality, isMetered),
            isMetered = isMetered,
            bandwidthKbps = downKbps
        )
        Log.d(TAG, "📡 Evaluated from capabilities: $quality (${downKbps}Kbps, metered=$isMetered)")
    }

    /**
     * Одноразовий HTTP-пінг — виконується тільки при зміні мережі (не кожні 10с)
     */
    private suspend fun checkLatencyOnce() {
        try {
            val latency = measureLatency()

            // Згладжуємо по останніх 5 вимірах
            synchronized(pingHistory) {
                pingHistory.add(latency)
                if (pingHistory.size > maxHistorySize) pingHistory.removeAt(0)
            }
            val avgLatency = synchronized(pingHistory) {
                pingHistory.toList().average().toLong()
            }

            // Уточнюємо якість з урахуванням реальної затримки
            val currentQuality = _connectionState.value.quality
            val latencyQuality = when {
                avgLatency < 200L  -> ConnectionQuality.EXCELLENT
                avgLatency < 500L  -> ConnectionQuality.GOOD
                avgLatency < 2000L -> ConnectionQuality.POOR
                else               -> ConnectionQuality.OFFLINE
            }
            // Беремо гірший з двох показників (bandwidth + latency)
            val finalQuality = if (latencyQuality.ordinal > currentQuality.ordinal) latencyQuality else currentQuality

            updateConnectionState(finalQuality, avgLatency)
            Log.d(TAG, "⏱️ Latency check: ${avgLatency}ms → $finalQuality")

        } catch (e: Exception) {
            Log.w(TAG, "Ping failed (network may be slow): ${e.message}")
        }
    }

    /**
     * Обчислити режим завантаження медіа
     */
    private fun computeMediaMode(quality: ConnectionQuality, isMetered: Boolean): MediaLoadMode {
        return when (quality) {
            ConnectionQuality.EXCELLENT -> if (isMetered) MediaLoadMode.THUMBNAILS else MediaLoadMode.FULL
            ConnectionQuality.GOOD      -> MediaLoadMode.THUMBNAILS
            ConnectionQuality.POOR      -> MediaLoadMode.NONE
            ConnectionQuality.OFFLINE   -> MediaLoadMode.NONE
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
     * Оновити стан з'єднання (latency-based, після пінгу)
     */
    private fun updateConnectionState(quality: ConnectionQuality, latency: Long) {
        val newState = _connectionState.value.copy(
            quality = quality,
            mediaLoadMode = computeMediaMode(quality, _connectionState.value.isMetered),
            latencyMs = latency
        )

        if (newState.quality != _connectionState.value.quality) {
            Log.i(TAG, "🔄 Connection quality changed: ${_connectionState.value.quality} → ${newState.quality}")
            Log.i(TAG, "📦 Media load mode: ${newState.mediaLoadMode}")
        }

        _connectionState.value = newState
    }

    /**
     * Примусово перевірити якість (через capabilities + одноразовий пінг)
     */
    fun forceCheck() {
        Log.d(TAG, "🔄 Force checking connection quality")
        evaluateFromCapabilities()
        pingJob?.cancel()
        pingJob = scope.launch { checkLatencyOnce() }
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