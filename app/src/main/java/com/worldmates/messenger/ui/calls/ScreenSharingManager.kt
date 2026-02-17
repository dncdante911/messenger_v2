package com.worldmates.messenger.ui.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.webrtc.*

/**
 * Менеджер демонстрації екрану
 * Використовує MediaProjection API для захоплення екрану
 * та додає відео трек до PeerConnection
 */
class ScreenSharingManager(
    private val context: Context,
    private val eglBase: EglBase
) {
    private val TAG = "ScreenSharingManager"

    private var mediaProjection: MediaProjection? = null
    private var screenCapturer: VideoCapturer? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    var isSharing: Boolean = false
        private set

    /**
     * Запросити дозвіл на демонстрацію екрану
     * Повертає Intent який треба запустити через ActivityResultLauncher
     */
    fun createScreenCaptureIntent(): Intent? {
        return try {
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionManager.createScreenCaptureIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create screen capture intent", e)
            null
        }
    }

    /**
     * Почати демонстрацію екрану
     * @param resultCode Код результату з ActivityResult
     * @param data Intent дані з ActivityResult
     * @param peerConnectionFactory PeerConnectionFactory для створення відео треку
     * @param onTrackCreated Callback з VideoTrack для додавання до PeerConnection
     */
    fun startScreenSharing(
        resultCode: Int,
        data: Intent,
        peerConnectionFactory: PeerConnectionFactory,
        onTrackCreated: (VideoTrack) -> Unit
    ) {
        try {
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null")
                return
            }

            // Get screen dimensions
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            val width = displayMetrics.widthPixels.coerceAtMost(1280)
            val height = displayMetrics.heightPixels.coerceAtMost(720)

            // Create screen capturer
            screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopScreenSharing()
                }
            })

            // Create video source and track
            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCapture", eglBase.eglBaseContext)
            screenVideoSource = peerConnectionFactory.createVideoSource(screenCapturer!!.isScreencast)
            screenCapturer!!.initialize(surfaceTextureHelper, context, screenVideoSource!!.capturerObserver)
            screenCapturer!!.startCapture(width, height, 15) // 15 fps for screen

            screenVideoTrack = peerConnectionFactory.createVideoTrack("screen_track", screenVideoSource)
            screenVideoTrack?.setEnabled(true)

            isSharing = true
            Log.d(TAG, "Screen sharing started: ${width}x${height}")

            screenVideoTrack?.let { onTrackCreated(it) }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen sharing", e)
            stopScreenSharing()
        }
    }

    /**
     * Зупинити демонстрацію екрану
     */
    fun stopScreenSharing() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null

            screenVideoTrack?.setEnabled(false)
            screenVideoTrack?.dispose()
            screenVideoTrack = null

            screenVideoSource?.dispose()
            screenVideoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            mediaProjection?.stop()
            mediaProjection = null

            isSharing = false
            Log.d(TAG, "Screen sharing stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen sharing", e)
        }
    }

    /**
     * Отримати поточний screen video track
     */
    fun getScreenTrack(): VideoTrack? = screenVideoTrack

    /**
     * Очистити ресурси
     */
    fun release() {
        stopScreenSharing()
    }
}
