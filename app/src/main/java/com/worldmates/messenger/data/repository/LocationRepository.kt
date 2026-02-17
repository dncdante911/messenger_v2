package com.worldmates.messenger.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

/**
 * 📍 LocationRepository - управление геолокацией
 *
 * Функции:
 * - Получение текущей геолокации
 * - Continuous location updates (Live Location)
 * - Reverse geocoding (координаты → адрес)
 * - Проверка разрешений
 */
class LocationRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocationRepository"

        @Volatile
        private var instance: LocationRepository? = null

        fun getInstance(context: Context): LocationRepository {
            return instance ?: synchronized(this) {
                instance ?: LocationRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // FusedLocationProviderClient для получения геолокации
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Geocoder для получения адреса по координатам
    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    // Live Location state
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation

    // Location callback для continuous updates
    private var locationCallback: LocationCallback? = null

    /**
     * Проверить, есть ли разрешения на геолокацию
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Получить текущую геолокацию (один раз)
     */
    suspend fun getCurrentLocation(): Result<LatLng> = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }

            Log.d(TAG, "📍 Requesting current location...")

            val location = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            Log.d(TAG, "✅ Got location from cache: ${location.latitude}, ${location.longitude}")
                            continuation.resume(location)
                        } else {
                            // Если нет кэшированной локации, запросим свежую
                            Log.d(TAG, "⚠️ No cached location, requesting fresh location...")
                            requestFreshLocation(continuation)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Failed to get location", exception)
                        continuation.resume(null)
                    }
            }

            if (location != null) {
                _currentLocation.value = location
                Result.success(LatLng(location.latitude, location.longitude))
            } else {
                Result.failure(Exception("Unable to get current location"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception getting location", e)
            Result.failure(e)
        }
    }

    /**
     * Запросить свежую локацию (если нет в кэше)
     */
    private fun requestFreshLocation(
        continuation: kotlinx.coroutines.CancellableContinuation<Location?>
    ) {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L
            ).build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    fusedLocationClient.removeLocationUpdates(this)
                    continuation.resume(location)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                context.mainLooper
            )
        } catch (e: SecurityException) {
            continuation.resume(null)
        }
    }

    /**
     * Начать отслеживание геолокации (Live Location)
     * @param intervalMs Интервал обновления в миллисекундах
     */
    suspend fun startLocationTracking(intervalMs: Long = 5000L): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                return@withContext Result.failure(SecurityException("Location permission not granted"))
            }

            if (_isTrackingLocation.value) {
                Log.w(TAG, "⚠️ Location tracking already started")
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "🎯 Starting live location tracking (interval: ${intervalMs}ms)...")

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs
            )
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        Log.d(TAG, "📍 Live location update: ${location.latitude}, ${location.longitude}")
                        _currentLocation.value = location
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                context.mainLooper
            )

            _isTrackingLocation.value = true
            Log.d(TAG, "✅ Live location tracking started")

            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception starting location tracking", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception starting location tracking", e)
            Result.failure(e)
        }
    }

    /**
     * Остановить отслеживание геолокации
     */
    fun stopLocationTracking() {
        if (!_isTrackingLocation.value) {
            Log.w(TAG, "⚠️ Location tracking not active")
            return
        }

        Log.d(TAG, "🛑 Stopping live location tracking...")

        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        locationCallback = null
        _isTrackingLocation.value = false

        Log.d(TAG, "✅ Live location tracking stopped")
    }

    /**
     * Получить адрес по координатам (Reverse Geocoding)
     */
    suspend fun getAddressFromLocation(latLng: LatLng): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌍 Reverse geocoding: ${latLng.latitude}, ${latLng.longitude}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ - новый асинхронный API
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latLng.latitude,
                        latLng.longitude,
                        1
                    ) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val addressText = buildAddressString(address)
                            Log.d(TAG, "✅ Address: $addressText")
                            continuation.resume(Result.success(addressText))
                        } else {
                            Log.w(TAG, "⚠️ No address found")
                            continuation.resume(
                                Result.success("${latLng.latitude}, ${latLng.longitude}")
                            )
                        }
                    }
                }
            } else {
                // Android 12 и ниже - старый синхронный API
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val addressText = buildAddressString(address)
                    Log.d(TAG, "✅ Address: $addressText")
                    Result.success(addressText)
                } else {
                    Log.w(TAG, "⚠️ No address found")
                    Result.success("${latLng.latitude}, ${latLng.longitude}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Geocoding failed", e)
            // Возвращаем координаты как fallback
            Result.success("${latLng.latitude}, ${latLng.longitude}")
        }
    }

    /**
     * Построить строку адреса из объекта Address
     */
    private fun buildAddressString(address: android.location.Address): String {
        val parts = mutableListOf<String>()

        // Улица и номер дома
        address.thoroughfare?.let { parts.add(it) }
        address.subThoroughfare?.let { parts.add(it) }

        // Город
        address.locality?.let { parts.add(it) }

        // Регион/область
        address.adminArea?.let { parts.add(it) }

        // Страна
        address.countryName?.let { parts.add(it) }

        return parts.joinToString(", ")
    }

    /**
     * Вычислить расстояние между двумя точками (в метрах)
     */
    fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        return results[0]
    }
}

/**
 * Data class для локации с адресом
 */
data class LocationData(
    val latLng: LatLng,
    val address: String,
    val timestamp: Long = System.currentTimeMillis()
)
