package com.worldmates.messenger

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.worldmates.messenger.services.MessageNotificationService
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import timber.log.Timber
import com.worldmates.messenger.data.AccountManager
import com.worldmates.messenger.services.WMFirebaseMessagingService
import com.worldmates.messenger.update.AppUpdateManager
import com.worldmates.messenger.services.NotificationKeepAliveManager
import com.worldmates.messenger.services.SecretChatCleanupWorker
import com.worldmates.messenger.services.SubscriptionSyncWorker
import com.worldmates.messenger.utils.LanguageManager

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

        // Ініціалізація менеджера мови (до будь-чого іншого)
        LanguageManager.init(this)
        LanguageManager.applyToConfiguration(this)

        // Ініціалізація мультиаккаунтного менеджера
        AccountManager.init(this)

        // Запуск фонового очищення секретних повідомлень (кожні 15 хвилин)
        SecretChatCleanupWorker.schedule(this)

        // Синхронізація статусу підписки раз на 24 год
        SubscriptionSyncWorker.schedule(this)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Ініціалізація keep-alive механізмів для push-сповіщень
        // Перезапускає MessageNotificationService якщо користувач залогінений
        NotificationKeepAliveManager.initialize(this)

        // FCM fallback: register/refresh token so server can send push when
        // the Socket.IO service is killed by the OS (MIUI, OxygenOS, etc.)
        WMFirebaseMessagingService.registerToken(this)

        AppUpdateManager.startPeriodicChecks(intervalMinutes = 30)

        // Track foreground/background state for call notification dedup
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var activeCount = 0
            override fun onActivityStarted(activity: Activity) {
                if (activeCount++ == 0) {
                    MessageNotificationService.isAppInForeground = true
                    Log.d(TAG, "App entered foreground")
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (--activeCount <= 0) {
                    activeCount = 0
                    MessageNotificationService.isAppInForeground = false
                    Log.d(TAG, "App entered background")
                }
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })

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
