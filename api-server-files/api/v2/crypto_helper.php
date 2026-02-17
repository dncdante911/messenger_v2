<?php
// +------------------------------------------------------------------------+
// | WorldMates Crypto Helper - AES-256-GCM Encryption
// | Enhanced encryption with authentication and dynamic IV
// | Гибридная система: GCM для WorldMates ↔ ECB для WoWonder
// +------------------------------------------------------------------------+

/**
 * Класс для работы с усиленным шифрованием AES-256-GCM.
 */
class CryptoHelper {

    // Константы для AES-GCM
    const CIPHER_METHOD = 'aes-256-gcm';
    const IV_LENGTH = 12;  // 96 бит (рекомендуется NIST для GCM)
    const TAG_LENGTH = 16; // 128 бит
    const KEY_LENGTH = 32; // 256 бит

    // Версии шифрования
    const CIPHER_VERSION_ECB = 1; // Старая версия (AES-128-ECB)
    const CIPHER_VERSION_GCM = 2; // Новая версия (AES-256-GCM)

    /**
     * Генерирует случайный IV для GCM шифрования.
     */
    public static function generateIV() {
        return openssl_random_pseudo_bytes(self::IV_LENGTH);
    }

    /**
     * Создает 256-битный ключ из timestamp.
     */
    public static function createKeyFromTimestamp($timestamp) {
        $timestampString = (string)$timestamp;
        $key = '';

        // Повторяем timestamp пока не получим 32 байта
        while (strlen($key) < self::KEY_LENGTH) {
            $remaining = self::KEY_LENGTH - strlen($key);
            $key .= substr($timestampString, 0, $remaining);
        }

        return $key;
    }

    /**
     * Шифрует текст с использованием AES-256-GCM.
     */
    public static function encryptGCM($plaintext, $timestamp) {
        try {
            // Генерируем случайный IV
            $iv = self::generateIV();
            $key = self::createKeyFromTimestamp($timestamp);

            // Шифруем с помощью GCM
            $tag = null;
            $ciphertext = openssl_encrypt(
                $plaintext,
                self::CIPHER_METHOD,
                $key,
                OPENSSL_RAW_DATA,
                $iv,
                $tag,
                '',
                self::TAG_LENGTH
            );

            if ($ciphertext === false) {
                return false;
            }

            return [
                'text' => base64_encode($ciphertext),
                'iv' => base64_encode($iv),
                'tag' => base64_encode($tag),
                'cipher_version' => self::CIPHER_VERSION_GCM
            ];

        } catch (Exception $e) {
            error_log("CryptoHelper: encryptGCM error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Дешифрует текст, зашифрованный с использованием AES-256-GCM.
     */
    public static function decryptGCM($ciphertext, $timestamp, $iv, $tag) {
        try {
            // Декодируем Base64
            $ciphertextRaw = base64_decode($ciphertext);
            $ivRaw = base64_decode($iv);
            $tagRaw = base64_decode($tag);

            if ($ciphertextRaw === false || $ivRaw === false || $tagRaw === false) {
                return false;
            }

            $key = self::createKeyFromTimestamp($timestamp);
            
            // Дешифруем
            $plaintext = openssl_decrypt(
                $ciphertextRaw,
                self::CIPHER_METHOD,
                $key,
                OPENSSL_RAW_DATA,
                $ivRaw,
                $tagRaw
            );

            return $plaintext !== false ? trim($plaintext) : false;

        } catch (Exception $e) {
            error_log("CryptoHelper: decryptGCM error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Дешифрует текст, зашифрованный старым методом AES-128-ECB.
     */
    public static function decryptECB($ciphertext, $timestamp) {
        try {
            $plaintext = openssl_decrypt($ciphertext, "AES-128-ECB", $timestamp);
            return $plaintext !== false ? trim($plaintext) : false;
        } catch (Exception $e) {
            error_log("CryptoHelper: decryptECB error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Шифрует текст старым методом AES-128-ECB (для совместимости).
     */
    public static function encryptECB($plaintext, $timestamp) {
        return openssl_encrypt($plaintext, "AES-128-ECB", $timestamp);
    }

    /**
     * Универсальная функция дешифрования с автоопределением версии.
     */
    public static function decrypt($ciphertext, $timestamp, $iv = null, $tag = null, $cipherVersion = null) {
        if ($cipherVersion === self::CIPHER_VERSION_GCM || ($iv !== null && $tag !== null)) {
            return self::decryptGCM($ciphertext, $timestamp, $iv, $tag);
        } else {
            return self::decryptECB($ciphertext, $timestamp);
        }
    }

    /**
     * Конвертирует GCM-шифрованный текст в ECB (для браузера WoWonder).
     */
    public static function convertGCMtoECB($gcmCiphertext, $timestamp, $iv, $tag) {
        try {
            // Дешифруем GCM
            $decrypted = self::decryptGCM($gcmCiphertext, $timestamp, $iv, $tag);
            if ($decrypted === false) {
                return false;
            }
            
            // Шифруем в ECB
            return self::encryptECB($decrypted, $timestamp);
            
        } catch (Exception $e) {
            error_log("CryptoHelper: convertGCMtoECB error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Конвертирует ECB-шифрованный текст в GCM (для WorldMates приложения).
     */
    public static function convertECBtoGCM($ecbCiphertext, $timestamp) {
        try {
            // Дешифруем ECB
            $decrypted = self::decryptECB($ecbCiphertext, $timestamp);
            if ($decrypted === false) {
                return false;
            }
            
            // Шифруем в GCM
            return self::encryptGCM($decrypted, $timestamp);
            
        } catch (Exception $e) {
            error_log("CryptoHelper: convertECBtoGCM error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Определяет тип шифрования по данным сообщения.
     */
    public static function detectCipherType($ciphertext, $iv = null, $tag = null, $cipherVersion = null) {
        if ($cipherVersion === self::CIPHER_VERSION_GCM || ($iv !== null && $tag !== null)) {
            return self::CIPHER_VERSION_GCM;
        } else {
            return self::CIPHER_VERSION_ECB;
        }
    }
}

/**
 * Глобальные функции для удобства.
 */

function wo_encrypt_message_gcm($text, $timestamp) {
    return CryptoHelper::encryptGCM($text, $timestamp);
}

function wo_decrypt_message($ciphertext, $timestamp, $iv = null, $tag = null, $cipherVersion = null) {
    return CryptoHelper::decrypt($ciphertext, $timestamp, $iv, $tag, $cipherVersion);
}

function wo_convert_gcm_to_ecb($gcmCiphertext, $timestamp, $iv, $tag) {
    return CryptoHelper::convertGCMtoECB($gcmCiphertext, $timestamp, $iv, $tag);
}

function wo_convert_ecb_to_gcm($ecbCiphertext, $timestamp) {
    return CryptoHelper::convertECBtoGCM($ecbCiphertext, $timestamp);
}