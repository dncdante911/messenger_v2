package com.worldmates.messenger.utils.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * TOTP (Time-based One-Time Password) Generator для Google Authenticator
 *
 * Реализует RFC 6238 стандарт для двухфакторной аутентификации.
 * Использует только стандартные JVM-библиотеки — без Apache Commons Codec,
 * чтобы избежать конфликта с org.apache.http.legacy.jar в системных прошивках Android.
 */
object TOTPGenerator {

    private const val TIME_STEP = 30 // секунды
    private const val DIGITS = 6 // длина кода
    private const val SECRET_LENGTH = 20 // байты для Base32

    // RFC 4648 Base32 алфавит
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    // ─── Base32 helpers (inline, no external deps) ────────────────────────────

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            buffer = buffer shl (5 - bitsLeft)
            sb.append(BASE32_ALPHABET[buffer and 0x1F])
        }
        return sb.toString()
    }

    private fun base32Decode(input: String): ByteArray {
        val clean = input.trimEnd('=').uppercase()
        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        for (ch in clean) {
            val v = BASE32_ALPHABET.indexOf(ch)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output[index++] = (buffer shr bitsLeft).toByte()
            }
        }
        return output.copyOf(index)
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Генерирует случайный секретный ключ для пользователя
     * @return Base32 закодированный секретный ключ
     */
    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH)
        SecureRandom().nextBytes(bytes)
        return base32Encode(bytes)
    }

    /**
     * Генерирует TOTP код на основе секретного ключа
     * @param secret Base32 секретный ключ
     * @param time текущее время в миллисекундах (по умолчанию System.currentTimeMillis())
     * @return 6-значный TOTP код
     */
    fun generateTOTP(secret: String, time: Long = System.currentTimeMillis()): String {
        val key = base32Decode(secret)
        val timeCounter = time / 1000 / TIME_STEP

        val data = ByteArray(8)
        var value = timeCounter
        for (i in 7 downTo 0) {
            data[i] = value.toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)

        val offset = (hash[hash.size - 1] and 0x0f).toInt()
        val truncatedHash = hash.copyOfRange(offset, offset + 4)

        var code = 0
        for (i in truncatedHash.indices) {
            code = code shl 8
            code = code or (truncatedHash[i].toInt() and 0xFF)
        }
        code = (code and 0x7FFFFFFF) % 1000000

        return code.toString().padStart(DIGITS, '0')
    }

    /**
     * Проверяет валидность TOTP кода
     * @param secret секретный ключ
     * @param code введенный пользователем код
     * @param window количество временных окон для проверки (±1 = 90 секунд допуска)
     * @return true если код верный
     */
    fun verifyTOTP(secret: String, code: String, window: Int = 1): Boolean {
        val currentTime = System.currentTimeMillis()
        for (i in -window..window) {
            if (generateTOTP(secret, currentTime + (i * TIME_STEP * 1000L)) == code) return true
        }
        return false
    }

    /**
     * Генерирует QR-код для Google Authenticator
     */
    fun generateQRCode(
        secret: String,
        accountName: String,
        issuer: String = "WallyMates",
        size: Int = 512
    ): Bitmap {
        val qrContent = "otpauth://totp/$issuer:$accountName?secret=$secret&issuer=$issuer&digits=$DIGITS&period=$TIME_STEP"

        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Генерирует Recovery Codes для восстановления доступа
     */
    fun generateRecoveryCodes(count: Int = 10): List<String> {
        val random = SecureRandom()
        return List(count) {
            val code = random.nextInt(100000000).toString().padStart(8, '0')
            "${code.substring(0, 4)}-${code.substring(4, 8)}"
        }
    }

    /**
     * Вычисляет оставшееся время до смены TOTP кода
     */
    fun getRemainingSeconds(): Int {
        val currentTime = System.currentTimeMillis() / 1000
        return TIME_STEP - (currentTime % TIME_STEP).toInt()
    }
}
