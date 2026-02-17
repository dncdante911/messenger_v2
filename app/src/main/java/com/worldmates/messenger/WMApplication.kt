package com.worldmates.messenger

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.firebase.FirebaseApp
import timber.log.Timber
import com.worldmates.messenger.update.AppUpdateManager

/**
 * Главный Application класс WorldMates Messenger
 */
class WMApplication : MultiDexApplication(), ImageLoaderFactory {

    companion object {
        private const val TAG = "WMApplication"
        lateinit var instance: WMApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
        }

        AppUpdateManager.startPeriodicChecks(intervalMinutes = 30)

        Log.d(TAG, "WorldMates Messenger Application started")
    }

    /**
     * Налаштування Coil ImageLoader з підтримкою GIF
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // Підтримка GIF для анімованих стікерів
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
