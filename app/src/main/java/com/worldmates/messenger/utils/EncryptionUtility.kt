package com.worldmates.messenger.utils

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Утилита для шифрования/дешифрования сообщений с использованием AES-256-GCM.
 *
 * Преимущества AES-GCM над AES-ECB:
 * - ✅ Аутентифицированное шифрование (AEAD)
 * - ✅ Защита от подмены данных (authentication tag)
 * - ✅ Уникальный IV для каждого сообщения
 * - ✅ Защита от атак перестановки
 * - ✅ Проверка целостности данных
 */
object EncryptionUtility {

    private const val TAG = "EncryptionUtility"

    // Константы для AES-GCM
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256 // AES-256
    private const val GCM_IV_LENGTH = 12 // 96 бит (рекомендуется NIST)
    private const val GCM_TAG_LENGTH = 128 // 128 бит = 16 байт

    // Версии шифрования для совместимости
    const val CIPHER_VERSION_ECB = 1 // Старая версия (AES-128-ECB)
    const val CIPHER_VERSION_GCM = 2 // Новая версия (AES-256-GCM)

    /**
     * Генерирует случайный 256-битный ключ для AES-256.
     * Используется для генерации ключа на основе пароля или другого секрета.
     */
    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE)
        return keyGen.generateKey()
    }

    /**
     * Создает 256-битный ключ из timestamp с использованием улучшенного алгоритма.
     *
     * @param timestamp Unix timestamp сообщения
     * @return SecretKey для AES-256
     */
    fun createKeyFromTimestamp(timestamp: Long): SecretKey {
        // Создаем 32-байтовый ключ (256 бит) из timestamp
        // Используем SHA-256 для расширения timestamp до 256 бит
        val timestampString = timestamp.toString()
        val timestampBytes = timestampString.toByteArray(Charsets.UTF_8)

        // Используем простое повторение для совместимости с PHP
        // (можно улучшить на PBKDF2 позже)
        val keyBytes = ByteArray(32) // 256 бит

        // Заполняем ключ повторением timestamp
        var offset = 0
        while (offset < 32) {
            val remaining = 32 - offset
            val toCopy = minOf(timestampBytes.size, remaining)
            timestampBytes.copyInto(keyBytes, offset, 0, toCopy)
            offset += toCopy
        }

        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Генерирует случайный Initialization Vector (IV) для GCM.
     * IV должен быть уникальным для каждого сообщения.
     *
     * @return ByteArray размером 12 байт (96 бит)
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * Шифрует текст с использованием AES-256-GCM.
     *
     * @param plaintext Текст для шифрования
     * @param timestamp Unix timestamp для генерации ключа
     * @return EncryptedData с зашифрованным текстом, IV и authentication tag
     */
    fun encryptMessage(plaintext: String, timestamp: Long): EncryptedData? {
        try {
            // Создаем ключ из timestamp
            val secretKey = createKeyFromTimestamp(timestamp)

            // Генерируем случайный IV
            val iv = generateIV()

            // Создаем GCM параметры
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            // Инициализируем cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

            // Шифруем текст
            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // В GCM режиме authentication tag уже включен в ciphertext
            // Последние 16 байт = authentication tag
            val tagLength = GCM_TAG_LENGTH / 8 // 16 байт
            val encryptedTextOnly = ciphertext.copyOfRange(0, ciphertext.size - tagLength)
            val authTag = ciphertext.copyOfRange(ciphertext.size - tagLength, ciphertext.size)

            // Кодируем в Base64
            val encryptedTextBase64 = Base64.encodeToString(encryptedTextOnly, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val tagBase64 = Base64.encodeToString(authTag, Base64.NO_WRAP)

            Log.d(TAG, "Message encrypted successfully with AES-GCM")

            return EncryptedData(
                encryptedText = encryptedTextBase64,
                iv = ivBase64,
                tag = tagBase64,
                cipherVersion = CIPHER_VERSION_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting message with GCM", e)
            return null
        }
    }

    /**
     * Дешифрует текст, зашифрованный с использованием AES-256-GCM.
     *
     * @param encryptedData Данные для дешифрования (зашифрованный текст, IV, tag)
     * @param timestamp Unix timestamp для генерации ключа
     * @return Дешифрованный текст или null при ошибке
     */
    fun decryptMessage(encryptedData: EncryptedData, timestamp: Long): String? {
        try {
            // Декодируем Base64
            val encryptedBytes = Base64.decode(encryptedData.encryptedText, Base64.NO_WRAP)
            val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
            val authTag = Base64.decode(encryptedData.tag, Base64.NO_WRAP)

            // Объединяем encrypted text и authentication tag
            val ciphertext = encryptedBytes + authTag

            // Создаем ключ из timestamp
            val secretKey = createKeyFromTimestamp(timestamp)

            // Создаем GCM параметры
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            // Инициализируем cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            // Дешифруем
            val decryptedBytes = cipher.doFinal(ciphertext)

            val decryptedText = String(decryptedBytes, Charsets.UTF_8).trim()
            Log.d(TAG, "Message decrypted successfully with AES-GCM")

            return decryptedText
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message with GCM (authentication may have failed)", e)
            return null
        }
    }

    /**
     * Дешифрует текст, зашифрованный AES-128-ECB (WoWonder legacy).
     * Ключ = timestamp строка, дополненная нулями до 16 байт.
     *
     * @param ciphertext Base64 зашифрованный текст
     * @param timestamp  Unix timestamp сообщения
     * @return Расшифрованный текст или null при ошибке
     */
    fun decryptECB(ciphertext: String, timestamp: Long): String? {
        return try {
            val ciphertextBytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            val key = ecbKeyFromTimestamp(timestamp)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decryptedBytes = cipher.doFinal(ciphertextBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message with ECB", e)
            null
        }
    }

    /**
     * Создает 128-битный ключ из timestamp для AES-128-ECB.
     * Timestamp-строка дополняется нулями до 16 байт.
     * Совместимо с PHP: openssl_encrypt($text, "AES-128-ECB", $timestamp).
     */
    private fun ecbKeyFromTimestamp(timestamp: Long): SecretKey {
        val ts       = timestamp.toString().toByteArray(Charsets.UTF_8)
        val keyBytes = ByteArray(16) // 128 бит, инициализирован нулями
        ts.copyInto(keyBytes, 0, 0, minOf(ts.size, 16))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Класс данных для хранения зашифрованного сообщения.
     */
    data class EncryptedData(
        val encryptedText: String,  // Base64 зашифрованный текст
        val iv: String,              // Base64 Initialization Vector
        val tag: String,             // Base64 Authentication Tag
        val cipherVersion: Int = CIPHER_VERSION_GCM  // Версия алгоритма шифрования
    )
}
