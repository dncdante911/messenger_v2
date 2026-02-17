<?php
// +------------------------------------------------------------------------+
// | WorldMates Crypto Helper - AES-256-GCM Encryption
// | Enhanced encryption with authentication and dynamic IV
// +------------------------------------------------------------------------+

/**
 * Класс для работы с усиленным шифрованием AES-256-GCM.
 *
 * Преимущества над AES-128-ECB:
 * - ✅ Аутентифицированное шифрование (AEAD)
 * - ✅ Защита от подмены данных (authentication tag)
 * - ✅ Уникальный IV для каждого сообщения
 * - ✅ Защита от атак перестановки
 * - ✅ Проверка целостности данных
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
     *
     * @return string Бинарный IV длиной 12 байт
     */
    public static function generateIV() {
        return openssl_random_pseudo_bytes(self::IV_LENGTH);
    }

    /**
     * Создает 256-битный ключ из timestamp.
     * Расширяет timestamp до 32 байт путем повторения.
     *
     * @param int|string $timestamp Unix timestamp
     * @return string Бинарный ключ длиной 32 байта
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
     *
     * @param string $plaintext Текст для шифрования
     * @param int|string $timestamp Unix timestamp (используется как ключ)
     * @return array|false Массив с зашифрованными данными или false при ошибке
     *                     ['text' => base64, 'iv' => base64, 'tag' => base64, 'cipher_version' => 2]
     */
    public static function encryptGCM($plaintext, $timestamp) {
        try {
            // Генерируем случайный IV
            $iv = self::generateIV();

            // Создаем ключ из timestamp
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
                error_log("CryptoHelper: GCM encryption failed");
                return false;
            }

            // Возвращаем результат в формате Base64
            return [
                'text' => base64_encode($ciphertext),
                'iv' => base64_encode($iv),
                'tag' => base64_encode($tag),
                'cipher_version' => self::CIPHER_VERSION_GCM
            ];

        } catch (Exception $e) {
            error_log("CryptoHelper: Exception in encryptGCM: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Дешифрует текст, зашифрованный с использованием AES-256-GCM.
     *
     * @param string $ciphertext Base64 зашифрованный текст
     * @param string $iv Base64 Initialization Vector
     * @param string $tag Base64 Authentication Tag
     * @param int|string $timestamp Unix timestamp (используется как ключ)
     * @return string|false Дешифрованный текст или false при ошибке
     */
public static function decryptGCM($ciphertext, $timestamp, $iv, $tag) {
    try {
        // Декодируем Base64
        $ciphertextRaw = base64_decode($ciphertext);
        $ivRaw = base64_decode($iv);
        $tagRaw = base64_decode($tag);

        if ($ciphertextRaw === false || $ivRaw === false || $tagRaw === false) {
            error_log("CryptoHelper: Base64 decode failed");
            error_log("Ciphertext: " . substr($ciphertext, 0, 50) . "...");
            error_log("IV: " . substr($iv, 0, 20) . "...");
            error_log("Tag: " . substr($tag, 0, 20) . "...");
            return false;
        }

        // Создаем ключ из timestamp
        $key = self::createKeyFromTimestamp($timestamp);
        
        error_log("=== GCM DECRYPTION DEBUG ===");
        error_log("Timestamp: $timestamp");
        error_log("Key (hex): " . bin2hex($key));
        error_log("Key length: " . strlen($key));
        error_log("Ciphertext length: " . strlen($ciphertextRaw));
        error_log("IV length: " . strlen($ivRaw));
        error_log("Tag length: " . strlen($tagRaw));

        // Дешифруем с проверкой authentication tag
        $plaintext = openssl_decrypt(
            $ciphertextRaw,
            self::CIPHER_METHOD,
            $key,
            OPENSSL_RAW_DATA,
            $ivRaw,
            $tagRaw
        );

        if ($plaintext === false) {
            $error = openssl_error_string();
            error_log("CryptoHelper: GCM decryption failed: $error");
            error_log("Cipher method: " . self::CIPHER_METHOD);
            return false;
        }

        error_log("Decryption successful!");
        return trim($plaintext);

    } catch (Exception $e) {
        error_log("CryptoHelper: Exception in decryptGCM: " . $e->getMessage());
        return false;
    }
}

    /**
     * Дешифрует текст, зашифрованный старым методом AES-128-ECB.
     * Используется для обратной совместимости.
     *
     * @param string $ciphertext Base64 зашифрованный текст
     * @param int|string $timestamp Unix timestamp (используется как ключ)
     * @return string|false Дешифрованный текст или false при ошибке
     */
    public static function decryptECB($ciphertext, $timestamp) {
        try {
            // Старый метод - совместимость с openssl_encrypt($text, "AES-128-ECB", $time)
            $plaintext = openssl_decrypt($ciphertext, "AES-128-ECB", $timestamp);

            if ($plaintext === false) {
                error_log("CryptoHelper: ECB decryption failed");
                return false;
            }

            return trim($plaintext);

        } catch (Exception $e) {
            error_log("CryptoHelper: Exception in decryptECB: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Универсальная функция дешифрования с автоопределением версии.
     *
     * @param string $ciphertext Base64 зашифрованный текст
     * @param int|string $timestamp Unix timestamp
     * @param string|null $iv Base64 IV (только для GCM)
     * @param string|null $tag Base64 Authentication Tag (только для GCM)
     * @param int|null $cipherVersion Версия шифрования (1=ECB, 2=GCM)
     * @return string|false Дешифрованный текст или false при ошибке
     */
    public static function decrypt($ciphertext, $timestamp, $iv = null, $tag = null, $cipherVersion = null) {
        // Определяем версию шифрования
    if ($cipherVersion === self::CIPHER_VERSION_GCM || ($iv !== null && $tag !== null)) {
        // GCM режим
        if ($iv !== null && $tag !== null) {
            return self::decryptGCM($ciphertext, $timestamp, $iv, $tag);  // Исправлен порядок!
        } else {
            error_log("CryptoHelper: GCM decryption requested but IV or TAG is missing");
            return false;
        }
    } else {
        // ECB режим (обратная совместимость)
        return self::decryptECB($ciphertext, $timestamp);
    }
}

    /**
     * Старая функция шифрования (для обратной совместимости).
     * НЕ РЕКОМЕНДУЕТСЯ для новых реализаций.
     *
     * @param string $plaintext Текст для шифрования
     * @param int|string $timestamp Unix timestamp
     * @return string Base64 зашифрованный текст
     * @deprecated Используйте encryptGCM() вместо этого
     */
    public static function encryptECB($plaintext, $timestamp) {
        // Старый метод - AES-128-ECB (НЕ БЕЗОПАСНО)
        return openssl_encrypt($plaintext, "AES-128-ECB", $timestamp);
    }

    /**
     * Проверяет, поддерживается ли AES-GCM на данном сервере.
     *
     * @return bool True если GCM поддерживается
     */
    public static function isGCMSupported() {
        return in_array(self::CIPHER_METHOD, openssl_get_cipher_methods());
    }
    // В функции decryptGCM в crypto_helper.php добавьте логирование:

}

/**
 * Глобальная функция для быстрого шифрования (GCM).
 *
 * @param string $text Текст для шифрования
 * @param int|string $timestamp Unix timestamp
 * @return array|false Массив с зашифрованными данными
 */
function wo_encrypt_message_gcm($text, $timestamp) {
    return CryptoHelper::encryptGCM($text, $timestamp);
}

/**
 * Глобальная функция для быстрого дешифрования (автоопределение версии).
 *
 * @param string $ciphertext Base64 зашифрованный текст
 * @param int|string $timestamp Unix timestamp
 * @param string|null $iv Base64 IV
 * @param string|null $tag Base64 Tag
 * @param int|null $cipherVersion Версия шифрования
 * @return string|false Дешифрованный текст
 */
function wo_decrypt_message($ciphertext, $timestamp, $iv = null, $tag = null, $cipherVersion = null) {
    return CryptoHelper::decrypt($ciphertext, $timestamp, $iv, $tag, $cipherVersion);
}

?>