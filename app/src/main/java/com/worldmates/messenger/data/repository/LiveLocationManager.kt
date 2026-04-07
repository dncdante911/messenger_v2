package com.worldmates.messenger.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.network.SocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

/**
 * Менеджер геолокації в реальному часі (Live Location).
 *
 * Відповідальності:
 *   • Запуск / зупинка GPS-відстеження через [LocationRepository]
 *   • Надсилання оновлень позиції через Socket.IO кожні [UPDATE_INTERVAL_MS] мс
 *   • Зберігання останніх координат інших учасників чату/групи
 *
 * Використання (в MessagesViewModel):
 *   liveLocationManager.startSharing(toId, isGroup)
 *   liveLocationManager.stopSharing(toId, isGroup)
 *   liveLocationManager.liveLocations.collect { map -> ... }  // Map<userId, LatLng>
 */
class LiveLocationManager(
    private val locationRepo: LocationRepository,
    private val socketManager: SocketManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var trackingJob: Job? = null

    // userId → остання відома позиція інших учасників
    private val _liveLocations = MutableStateFlow<Map<Long, LatLng>>(emptyMap())
    val liveLocations: StateFlow<Map<Long, LatLng>> = _liveLocations.asStateFlow()

    private val _isSharingLocation = MutableStateFlow(false)
    val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

    companion object {
        private const val TAG              = "LiveLocationManager"
        private const val UPDATE_INTERVAL_MS = 5_000L   // посилати апдейт не частіше ніж раз на 5 с
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Почати ділитись геолокацією з [toId] (userId або groupId).
     * Якщо відстеження вже активне — ігнорується.
     */
    fun startSharing(toId: Long, isGroup: Boolean) {
        if (_isSharingLocation.value) {
            Log.w(TAG, "Already sharing — ignored")
            return
        }
        if (!locationRepo.hasLocationPermission()) {
            Log.w(TAG, "No location permission — cannot share")
            return
        }

        _isSharingLocation.value = true

        // Повідомляємо одержувача що ми почали ділитись
        socketManager.emitRaw(Constants.SOCKET_EVENT_LIVE_LOCATION_START, JSONObject().apply {
            put("to_id",   toId)
            put("is_group", isGroup)
        })

        trackingJob = scope.launch {
            locationRepo.startLocationTracking(UPDATE_INTERVAL_MS)

            locationRepo.currentLocation
                .filterNotNull()
                // Фільтруємо дублікати (той самий округлений ~1 м пробіг)
                // Використовуємо Pair щоб уникнути помилкових збігів при строковій конкатенації
                // (наприклад: lat=12.3456, lng=7.89 і lat=123.456, lng=0.789 давали однаковий рядок)
                .distinctUntilChangedBy { loc ->
                    Pair(
                        (loc.latitude  * 10_000).toLong(),
                        (loc.longitude * 10_000).toLong()
                    )
                }
                .collect { location ->
                    val payload = JSONObject().apply {
                        put("to_id",    toId)
                        put("is_group", isGroup)
                        put("lat",      location.latitude)
                        put("lng",      location.longitude)
                        put("accuracy", location.accuracy)
                    }
                    socketManager.emitRaw(Constants.SOCKET_EVENT_LIVE_LOCATION_UPDATE, payload)
                    Log.d(TAG, "📍 Sent update → ${if (isGroup) "group" else "user"}=$toId " +
                               "(${location.latitude}, ${location.longitude})")
                }
        }
    }

    /**
     * Зупинити передачу геолокації.
     */
    fun stopSharing(toId: Long, isGroup: Boolean) {
        trackingJob?.cancel()
        trackingJob = null
        locationRepo.stopLocationTracking()
        _isSharingLocation.value = false

        socketManager.emitRaw(Constants.SOCKET_EVENT_LIVE_LOCATION_STOP, JSONObject().apply {
            put("to_id",   toId)
            put("is_group", isGroup)
        })
        Log.d(TAG, "🛑 Stopped sharing with ${if (isGroup) "group" else "user"}=$toId")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Incoming events (called from MessagesViewModel socket callbacks)
    // ──────────────────────────────────────────────────────────────────────────

    /** Оновити координати для [fromId] після приходу live_location_update. */
    fun onIncomingUpdate(fromId: Long, lat: Double, lng: Double) {
        _liveLocations.value = _liveLocations.value + (fromId to LatLng(lat, lng))
    }

    /** Видалити маркер [fromId] після live_location_stop. */
    fun onRemoteStop(fromId: Long) {
        _liveLocations.value = _liveLocations.value - fromId
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    fun destroy() {
        trackingJob?.cancel()
        if (_isSharingLocation.value) locationRepo.stopLocationTracking()
        scope.cancel()
    }
}
