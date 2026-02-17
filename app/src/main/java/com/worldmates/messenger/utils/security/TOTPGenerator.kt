package com.worldmates.messenger.utils.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.apache.commons.codec.binary.Base32
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * TOTP (Time-based One-Time Password) Generator для Google Authenticator
 *
 * Реализует RFC 6238 стандарт для двухфакторной аутентификации
 */
object TOTPGenerator {

    private const val TIME_STEP = 30 // секунды
    private const val DIGITS = 6 // длина кода
    private const val SECRET_LENGTH = 20 // байты для Base32

    /**
     * Генерирует случайный секретный ключ для пользователя
     * @return Base32 закодированный секретный ключ
     */
    fun generateSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(SECRET_LENGTH)
        random.nextBytes(bytes)
        return Base32().encodeToString(bytes).replace("=", "")
    }

    /**
     * Генерирует TOTP код на основе секретного ключа
     * @param secret Base32 секретный ключ
     * @param time текущее время в миллисекундах (по умолчанию System.currentTimeMillis())
     * @return 6-значный TOTP код
     */
    fun generateTOTP(secret: String, time: Long = System.currentTimeMillis()): String {
        val key = Base32().decode(secret)
        val timeCounter = time / 1000 / TIME_STEP

        val data = ByteArray(8)
        var value = timeCounter
        for (i in 7 downTo 0) {
            data[i] = value.toByte()
            value = value shr 8
        }

        val signKey = SecretKeySpec(key, "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(signKey)
        val hash = mac.doFinal(data)

        val offset = (hash[hash.size - 1] and 0x0f).toInt()
        val truncatedHash = hash.copyOfRange(offset, offset + 4)

        var code = 0
        for (i in truncatedHash.indices) {
            code = code shl 8
            code = code or (truncatedHash[i].toInt() and 0xFF)
        }

        code = code and 0x7FFFFFFF
        code = code % 1000000

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

        // Проверяем текущее время ± window
        for (i in -window..window) {
            val time = currentTime + (i * TIME_STEP * 1000)
            val generatedCode = generateTOTP(secret, time)
            if (generatedCode == code) {
                return true
            }
        }

        return false
    }

    /**
     * Генерирует QR-код для Google Authenticator
     * @param secret секретный ключ
     * @param accountName имя аккаунта (username или email)
     * @param issuer имя приложения
     * @param size размер QR-кода в пикселях
     * @return Bitmap с QR-кодом
     */
    fun generateQRCode(
        secret: String,
        accountName: String,
        issuer: String = "WorldMates",
        size: Int = 512
    ): Bitmap {
        // Формат: otpauth://totp/Issuer:Account?secret=SECRET&issuer=Issuer
        val qrContent = "otpauth://totp/$issuer:$accountName?secret=$secret&issuer=$issuer&digits=$DIGITS&period=$TIME_STEP"

        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints)

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
     * @param count количество кодов (по умолчанию 10)
     * @return список recovery кодов
     */
    fun generateRecoveryCodes(count: Int = 10): List<String> {
        val random = SecureRandom()
        return List(count) {
            // Генерируем 8-значные коды в формате XXXX-XXXX
            val code = random.nextInt(100000000).toString().padStart(8, '0')
            "${code.substring(0, 4)}-${code.substring(4, 8)}"
        }
    }

    /**
     * Вычисляет оставшееся время до смены TOTP кода
     * @return количество секунд до следующего кода
     */
    fun getRemainingSeconds(): Int {
        val currentTime = System.currentTimeMillis() / 1000
        return TIME_STEP - (currentTime % TIME_STEP).toInt()
    }
}
