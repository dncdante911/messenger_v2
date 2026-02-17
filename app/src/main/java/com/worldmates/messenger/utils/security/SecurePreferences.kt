package com.worldmates.messenger.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.worldmates.messenger.WMApplication
import java.security.MessageDigest

/**
 * Безопасное хранилище для конфиденциальных данных
 * Использует EncryptedSharedPreferences для шифрования данных
 */
object SecurePreferences {

    private const val TAG = "SecurePreferences"
    private const val PREFS_NAME = "worldmates_secure_prefs"

    // Ключи для хранения
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
    private const val KEY_LAST_UNLOCK_TIME = "last_unlock_time"

    // 2FA ключи
    private const val KEY_2FA_ENABLED = "2fa_enabled"
    private const val KEY_2FA_SECRET = "2fa_secret"
    private const val KEY_2FA_BACKUP_CODES = "2fa_backup_codes"
    private const val KEY_2FA_BACKUP_CODES_USED = "2fa_backup_codes_used"

    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    /**
     * Створює EncryptedSharedPreferences з обробкою помилок.
     * Якщо Android Keystore пошкоджений (AEADBadTagException),
     * видаляє старі дані та створює нові.
     * У крайньому випадку використовує звичайні SharedPreferences.
     */
    private fun createEncryptedPrefs(): SharedPreferences {
        val context = WMApplication.instance
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, clearing and retrying: ${e.message}")
            // Видаляємо пошкоджений файл
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/${PREFS_NAME}.xml")
                if (prefsFile.exists()) prefsFile.delete()
            } catch (cleanupError: Exception) {
                Log.e(TAG, "Cleanup error: ${cleanupError.message}")
            }
            // Спробуємо ще раз з новим ключем
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Second attempt failed, falling back to regular SharedPreferences: ${e2.message}")
                // Крайній випадок: звичайні SharedPreferences (без шифрування)
                return context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    // ==================== PIN-КОД ====================

    /**
     * Сохраняет PIN-код (хранится в виде хеша)
     */
    fun savePIN(pin: String) {
        val hash = hashPIN(pin)
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    /**
     * Проверяет правильность PIN-кода
     */
    fun verifyPIN(pin: String): Boolean {
        val savedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPIN(pin)
        return savedHash == inputHash
    }

    /**
     * Удаляет PIN-код
     */
    fun removePIN() {
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .apply()
    }

    /**
     * Проверяет, установлен ли PIN-код
     */
    fun isPINEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PIN_ENABLED, false)
    }

    /**
     * Хеширует PIN-код с использованием SHA-256
     */
    private fun hashPIN(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ==================== БИОМЕТРИЯ ====================

    /**
     * Включает/выключает биометрическую аутентификацию
     */
    var isBiometricEnabled: Boolean
        get() = encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = encryptedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    // ==================== БЛОКИРОВКА ПРИЛОЖЕНИЯ ====================

    /**
     * Таймаут блокировки приложения в миллисекундах
     * 0 = сразу
     * -1 = никогда
     */
    var appLockTimeout: Long
        get() = encryptedPrefs.getLong(KEY_APP_LOCK_TIMEOUT, 0L)
        set(value) = encryptedPrefs.edit().putLong(KEY_APP_LOCK_TIMEOUT, value).apply()

    /**
     * Время последней разблокировки
     */
    var lastUnlockTime: Long
        get() = encryptedPrefs.getLong(KEY_LAST_UNLOCK_TIME, 0L)
        set(value) = encryptedPrefs.edit().putLong(KEY_LAST_UNLOCK_TIME, value).apply()

    /**
     * Проверяет, нужно ли показывать экран блокировки
     */
    fun shouldShowLockScreen(): Boolean {
        if (!isPINEnabled() && !isBiometricEnabled) {
            return false
        }

        if (appLockTimeout == -1L) {
            // Блокировка отключена
            return false
        }

        if (appLockTimeout == 0L) {
            // Блокировать сразу
            return true
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUnlock = currentTime - lastUnlockTime

        return timeSinceLastUnlock > appLockTimeout
    }

    /**
     * Обновляет время последней разблокировки
     */
    fun updateUnlockTime() {
        lastUnlockTime = System.currentTimeMillis()
    }

    // ==================== 2FA (TOTP) ====================

    /**
     * Включает/выключает 2FA
     */
    var is2FAEnabled: Boolean
        get() = encryptedPrefs.getBoolean(KEY_2FA_ENABLED, false)
        set(value) = encryptedPrefs.edit().putBoolean(KEY_2FA_ENABLED, value).apply()

    /**
     * Сохраняет секретный ключ 2FA
     */
    var twoFASecret: String?
        get() = encryptedPrefs.getString(KEY_2FA_SECRET, null)
        set(value) = encryptedPrefs.edit().putString(KEY_2FA_SECRET, value).apply()

    /**
     * Сохраняет резервные коды для восстановления
     */
    fun saveBackupCodes(codes: List<String>) {
        val codesString = codes.joinToString(",")
        encryptedPrefs.edit()
            .putString(KEY_2FA_BACKUP_CODES, codesString)
            .putString(KEY_2FA_BACKUP_CODES_USED, "")
            .apply()
    }

    /**
     * Получает резервные коды
     */
    fun getBackupCodes(): List<String> {
        val codesString = encryptedPrefs.getString(KEY_2FA_BACKUP_CODES, "") ?: ""
        return if (codesString.isEmpty()) emptyList() else codesString.split(",")
    }

    /**
     * Получает использованные резервные коды
     */
    fun getUsedBackupCodes(): List<String> {
        val usedString = encryptedPrefs.getString(KEY_2FA_BACKUP_CODES_USED, "") ?: ""
        return if (usedString.isEmpty()) emptyList() else usedString.split(",")
    }

    /**
     * Проверяет резервный код и отмечает его как использованный
     */
    fun verifyBackupCode(code: String): Boolean {
        val allCodes = getBackupCodes()
        val usedCodes = getUsedBackupCodes()

        if (!allCodes.contains(code)) {
            return false
        }

        if (usedCodes.contains(code)) {
            return false
        }

        // Отмечаем код как использованный
        val newUsedCodes = usedCodes + code
        encryptedPrefs.edit()
            .putString(KEY_2FA_BACKUP_CODES_USED, newUsedCodes.joinToString(","))
            .apply()

        return true
    }

    /**
     * Получает количество оставшихся резервных кодов
     */
    fun getRemainingBackupCodesCount(): Int {
        val allCodes = getBackupCodes()
        val usedCodes = getUsedBackupCodes()
        return allCodes.size - usedCodes.size
    }

    /**
     * Удаляет все данные 2FA
     */
    fun clear2FAData() {
        encryptedPrefs.edit()
            .remove(KEY_2FA_ENABLED)
            .remove(KEY_2FA_SECRET)
            .remove(KEY_2FA_BACKUP_CODES)
            .remove(KEY_2FA_BACKUP_CODES_USED)
            .apply()
    }

    // ==================== УТИЛИТЫ ====================

    /**
     * Очищает все безопасные данные
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Получает таймаут в удобочитаемом формате
     */
    fun getAppLockTimeoutLabel(): String {
        return when (appLockTimeout) {
            -1L -> "Ніколи"
            0L -> "Одразу"
            60_000L -> "1 хвилина"
            300_000L -> "5 хвилин"
            600_000L -> "10 хвилин"
            1_800_000L -> "30 хвилин"
            3_600_000L -> "1 година"
            else -> "Одразу"
        }
    }
}

/**
 * Константы для таймаутов блокировки
 */
object AppLockTimeout {
    const val NEVER = -1L
    const val IMMEDIATELY = 0L
    const val ONE_MINUTE = 60_000L
    const val FIVE_MINUTES = 300_000L
    const val TEN_MINUTES = 600_000L
    const val THIRTY_MINUTES = 1_800_000L
    const val ONE_HOUR = 3_600_000L

    fun getAll() = listOf(
        IMMEDIATELY to "Одразу",
        ONE_MINUTE to "1 хвилина",
        FIVE_MINUTES to "5 хвилин",
        TEN_MINUTES to "10 хвилин",
        THIRTY_MINUTES to "30 хвилин",
        ONE_HOUR to "1 година",
        NEVER to "Ніколи"
    )
}
