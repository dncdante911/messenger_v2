package com.worldmates.messenger.utils.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Менеджер для работы с биометрической аутентификацией
 * Поддерживает Face ID, Fingerprint и другие биометрические методы
 */
class BiometricAuthManager(private val activity: FragmentActivity) {

    private val biometricManager = BiometricManager.from(activity)

    /**
     * Проверяет доступность биометрической аутентификации на устройстве
     */
    fun isBiometricAvailable(): BiometricAvailability {
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricAvailability.AVAILABLE

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NO_HARDWARE

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.HARDWARE_UNAVAILABLE

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NONE_ENROLLED

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SECURITY_UPDATE_REQUIRED

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricAvailability.UNSUPPORTED

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                BiometricAvailability.UNKNOWN

            else -> BiometricAvailability.UNKNOWN
        }
    }

    /**
     * Проверяет, можно ли использовать только биометрию (без PIN/Pattern)
     */
    fun isBiometricOnlyAvailable(): Boolean {
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Показывает биометрический промпт для аутентификации
     * @param title заголовок промпта
     * @param subtitle подзаголовок
     * @param description описание
     * @param onSuccess callback при успешной аутентификации
     * @param onError callback при ошибке
     * @param onFailed callback при неудачной попытке
     * @param allowDeviceCredential разрешить использование PIN/Pattern
     */
    fun authenticate(
        title: String = "Біометрична аутентифікація",
        subtitle: String = "Підтвердіть свою особу",
        description: String = "Використайте відбиток пальця або розпізнавання обличчя",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {},
        allowDeviceCredential: Boolean = true
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .apply {
                if (allowDeviceCredential) {
                    // Разрешаем использование PIN/Pattern/Password
                    setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                } else {
                    // Только биометрия
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText("Скасувати")
                }
            }
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Получает тип биометрии, доступный на устройстве
     */
    fun getBiometricType(): BiometricType {
        return when {
            // Face ID доступен на Android 10+
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> {
                // Проверяем, есть ли Face ID
                // Это приблизительная проверка, так как Android не предоставляет точного способа
                BiometricType.FACE_OR_FINGERPRINT
            }
            biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> {
                BiometricType.FACE_OR_FINGERPRINT
            }
            biometricManager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS -> {
                BiometricType.PIN_PATTERN_PASSWORD
            }
            else -> BiometricType.NONE
        }
    }

    /**
     * Получает человекочитаемое название биометрического метода
     */
    fun getBiometricTypeName(): String {
        return when (getBiometricType()) {
            BiometricType.FACE_OR_FINGERPRINT -> "Відбиток пальця / Розпізнавання обличчя"
            BiometricType.PIN_PATTERN_PASSWORD -> "PIN / Графічний ключ"
            BiometricType.NONE -> "Недоступно"
        }
    }
}

/**
 * Доступность биометрии на устройстве
 */
enum class BiometricAvailability {
    AVAILABLE,                  // Доступна и настроена
    NO_HARDWARE,               // Нет аппаратного обеспечения
    HARDWARE_UNAVAILABLE,      // Аппаратура недоступна
    NONE_ENROLLED,             // Не зарегистрирована (не настроена)
    SECURITY_UPDATE_REQUIRED,  // Требуется обновление безопасности
    UNSUPPORTED,               // Не поддерживается
    UNKNOWN                    // Неизвестно
}

/**
 * Тип биометрической аутентификации
 */
enum class BiometricType {
    FACE_OR_FINGERPRINT,  // Face ID или Fingerprint
    PIN_PATTERN_PASSWORD, // PIN, графический ключ или пароль
    NONE                  // Нет
}

/**
 * Extension функции для Context
 */
fun Context.isBiometricAvailable(): Boolean {
    val biometricManager = BiometricManager.from(this)
    return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
}
