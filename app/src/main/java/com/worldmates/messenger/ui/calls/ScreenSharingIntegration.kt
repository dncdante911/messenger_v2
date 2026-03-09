package com.worldmates.messenger.ui.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.worldmates.messenger.R
import com.worldmates.messenger.network.WebRTCManager

/**
 * Інтеграція демонстрації екрану в CallsActivity.
 *
 * Відповідає за:
 *  1. Реєстрацію ActivityResultLauncher для запиту дозволу MediaProjection.
 *  2. Ініціалізацію [ScreenSharingManager] з правильним EglBase.
 *  3. Запуск/зупинку трансляції та додавання screen-треку до PeerConnection.
 *  4. Сповіщення ViewModel про стан трансляції (socket emit 'screen:start' / 'screen:stop').
 *
 * Використання в CallsActivity.onCreate():
 *   val screenSharingIntegration = ScreenSharingIntegration(this, callsViewModel)
 *   screenSharingIntegration.register()   // ← реєстрація launcher (до setContent)
 *
 * Використання в onToggleScreenShare callback:
 *   screenSharingIntegration.toggle()
 */
class ScreenSharingIntegration(
    private val activity: ComponentActivity,
    private val viewModel: CallsViewModel
) {
    private val TAG = "ScreenSharingIntegration"

    private var manager: ScreenSharingManager? = null
    private var launcher: ActivityResultLauncher<Intent>? = null

    /** Зареєструвати ActivityResultLauncher — викликати в onCreate() до setContent(). */
    fun register() {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startSharing(result.resultCode, result.data!!)
            } else {
                Log.w(TAG, "Screen capture permission denied or cancelled")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.screen_sharing_error),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setScreenSharingState(false)
            }
        }
    }

    /**
     * Перемкнути стан трансляції.
     * Якщо трансляція активна — зупинити, інакше запросити дозвіл.
     */
    fun toggle() {
        if (manager?.isSharing == true) {
            stop()
        } else {
            requestPermission()
        }
    }

    /** Поточний стан трансляції. */
    val isSharing: Boolean get() = manager?.isSharing == true

    // ─── private ──────────────────────────────────────────────────────────────

    private fun requestPermission() {
        val intent = buildManager().createScreenCaptureIntent()
        if (intent == null) {
            Toast.makeText(
                activity,
                activity.getString(R.string.screen_sharing_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        launcher?.launch(intent)
    }

    private fun startSharing(resultCode: Int, data: Intent) {
        val m = buildManager()
        val factory = WebRTCManager.getPeerConnectionFactory(activity) ?: run {
            Log.e(TAG, "PeerConnectionFactory is null — WebRTC not initialized")
            Toast.makeText(activity, activity.getString(R.string.screen_sharing_error), Toast.LENGTH_SHORT).show()
            return
        }

        m.startScreenSharing(resultCode, data, factory) { screenTrack ->
            Log.d(TAG, "Screen track created — adding to PeerConnection")
            viewModel.addScreenTrack(screenTrack)
            viewModel.setScreenSharingState(true)
            viewModel.emitScreenSharingEvent(started = true)
            Toast.makeText(activity, activity.getString(R.string.screen_sharing_active), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stop() {
        manager?.stopScreenSharing()
        viewModel.removeScreenTrack()
        viewModel.setScreenSharingState(false)
        viewModel.emitScreenSharingEvent(started = false)
        Toast.makeText(activity, activity.getString(R.string.screen_sharing_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun buildManager(): ScreenSharingManager {
        if (manager == null) {
            val eglBase = WebRTCManager.getEglBase()
            manager = ScreenSharingManager(activity, eglBase)
        }
        return manager!!
    }

    fun release() {
        manager?.release()
        manager = null
    }
}
