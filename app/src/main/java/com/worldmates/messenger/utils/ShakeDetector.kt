package com.worldmates.messenger.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects a shake gesture via the accelerometer.
 * Emits [Unit] on [shakeEvents] no more often than once per [COOLDOWN_MS].
 *
 * Call [start] to register and [stop] to unregister the sensor listener.
 */
class ShakeDetector(context: Context) : SensorEventListener {

    private val _shakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeEvents: SharedFlow<Unit> = _shakeEvents

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var registered = false
    private var lastShakeMs = 0L

    companion object {
        private const val TAG = "ShakeDetector"
        /** Acceleration above gravity (m/s²) required to count as a shake. */
        private const val THRESHOLD = 11f
        /** Minimum time between two shake events. */
        private const val COOLDOWN_MS = 900L
    }

    fun start() {
        if (registered || accelerometer == null) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        registered = true
        Log.d(TAG, "started")
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        Log.d(TAG, "stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val acceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)

        if (acceleration > THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeMs > COOLDOWN_MS) {
                lastShakeMs = now
                Log.d(TAG, "shake detected (a=%.1f)".format(acceleration))
                _shakeEvents.tryEmit(Unit)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
