package com.worldmates.messenger.ui.security

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.worldmates.messenger.ui.settings.security.PINLockScreen
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.security.BiometricAuthManager
import com.worldmates.messenger.utils.security.SecurePreferences
import com.worldmates.messenger.utils.LanguageManager

/**
 * Activity для проверки PIN-кода или биометрии при входе в приложение
 * Показывается поверх других экранов, если включена блокировка
 */
class AppLockActivity : AppCompatActivity() {

    private lateinit var biometricManager: BiometricAuthManager
    private var isUnlocked = false

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricManager = BiometricAuthManager(this)

        setContent {
            WorldMatesThemedApp {
                var showBiometric by remember { mutableStateOf(SecurePreferences.isBiometricEnabled) }

                // Автоматически показываем биометрию при запуске
                LaunchedEffect(Unit) {
                    if (SecurePreferences.isBiometricEnabled) {
                        showBiometricPrompt()
                    }
                }

                PINLockScreen(
                    onUnlocked = {
                        unlockApp()
                    },
                    onBiometricClick = if (SecurePreferences.isBiometricEnabled) {
                        { showBiometricPrompt() }
                    } else null,
                    showBiometricOption = SecurePreferences.isBiometricEnabled
                )
            }
        }
    }

    private fun showBiometricPrompt() {
        biometricManager.authenticate(
            title = "Розблокувати додаток",
            subtitle = "Підтвердіть свою особу",
            description = "Використайте відбиток пальця або розпізнавання обличчя",
            onSuccess = {
                unlockApp()
            },
            onError = { errorCode, _ ->
                // При ошибке биометрии показываем PIN
                // Пользователь может ввести PIN вручную
            },
            onFailed = {
                // Неудачная попытка биометрии
            },
            allowDeviceCredential = true
        )
    }

    private fun unlockApp() {
        isUnlocked = true
        SecurePreferences.updateUnlockTime()
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onBackPressed() {
        // Запрещаем выход из экрана блокировки кнопкой "Назад"
        // Пользователь должен ввести правильный PIN или использовать биометрию
        moveTaskToBack(true)
    }

    companion object {
        const val REQUEST_CODE_APP_LOCK = 1001

        /**
         * Проверяет, нужно ли показывать экран блокировки
         * @return true если нужно показать экран блокировки
         */
        fun shouldShowLockScreen(): Boolean {
            return SecurePreferences.shouldShowLockScreen()
        }

        /**
         * Запускает экран блокировки
         */
        fun launch(activity: Activity) {
            val intent = Intent(activity, AppLockActivity::class.java)
            activity.startActivityForResult(intent, REQUEST_CODE_APP_LOCK)
        }
    }
}
