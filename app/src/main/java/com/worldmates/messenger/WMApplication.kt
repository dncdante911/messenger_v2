package com.worldmates.messenger

import android.util.Log
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import timber.log.Timber
import com.worldmates.messenger.data.AccountManager
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

        // Ініціалізація keep-alive механізмів для push-сповіщень (без Firebase)
        // Перезапускає MessageNotificationService якщо користувач залогінений
        NotificationKeepAliveManager.initialize(this)

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
