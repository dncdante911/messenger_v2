/**
 * WorldMates Crypto Helper — Node.js
 *
 * Гибридная система шифрования:
 *   ► AES-256-GCM  — для WorldMates Android (аутентифицированное шифрование)
 *   ► AES-128-ECB  — для совместимости с WoWonder браузером
 *
 * Полностью совместимо с PHP CryptoHelper (crypto_helper.php):
 *   - Ключ GCM: timestamp-строка повторяется до 32 байт
 *   - Ключ ECB: timestamp-строка дополняется \0 до 16 байт (поведение PHP openssl)
 *   - IV: 12 байт случайных (NIST recommendation)
 *   - Tag: 16 байт (128 бит)
 *   - Кодировка: Base64
 */

'use strict';

const crypto = require('crypto');

const GCM_KEY_LEN = 32; // 256 бит
const ECB_KEY_LEN = 16; // 128 бит
const IV_LEN      = 12; // 96 бит (NIST для GCM)
const TAG_LEN     = 16; // 128 бит

const CIPHER_VERSION_ECB = 1;
const CIPHER_VERSION_GCM = 2;

// ─── Key derivation ──────────────────────────────────────────────────────────

/**
 * GCM ключ: повторяем timestamp до 32 байт.
 * Совместимо с PHP CryptoHelper::createKeyFromTimestamp().
 */
function gcmKey(timestamp) {
    const ts  = String(timestamp);
    const src = Buffer.from(ts, 'utf8');
    const buf = Buffer.alloc(GCM_KEY_LEN, 0);
    let offset = 0;
    while (offset < GCM_KEY_LEN) {
        const toCopy = Math.min(src.length, GCM_KEY_LEN - offset);
        src.copy(buf, offset, 0, toCopy);
        offset += toCopy;
    }
    return buf;
}

/**
 * ECB ключ: timestamp-строка + null-padding до 16 байт.
 * Совместимо с PHP: openssl_encrypt($text, "AES-128-ECB", $timestamp).
 * PHP OpenSSL дополняет ключ нулями, если он короче 16 байт.
 */
function ecbKey(timestamp) {
    const ts  = String(timestamp);
    const buf = Buffer.alloc(ECB_KEY_LEN, 0); // инициализирован нулями
    const src = Buffer.from(ts, 'utf8');
    src.copy(buf, 0, 0, Math.min(src.length, ECB_KEY_LEN));
    return buf;
}

// ─── AES-256-GCM ─────────────────────────────────────────────────────────────

/**
 * Шифрует текст с AES-256-GCM.
 * @param {string} plaintext
 * @param {number} timestamp — Unix-timestamp сообщения (используется как ключевой материал)
 * @returns {{ text: string, iv: string, tag: string, cipher_version: number } | null}
 */
function encryptGCM(plaintext, timestamp) {
    try {
        const key    = gcmKey(timestamp);
        const iv     = crypto.randomBytes(IV_LEN);
        const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);

        const enc = Buffer.concat([
            cipher.update(Buffer.from(plaintext, 'utf8')),
            cipher.final(),
        ]);
        const tag = cipher.getAuthTag(); // 16 байт

        return {
            text:           enc.toString('base64'),
            iv:             iv.toString('base64'),
            tag:            tag.toString('base64'),
            cipher_version: CIPHER_VERSION_GCM,
        };
    } catch (e) {
        console.error('[crypto] encryptGCM error:', e.message);
        return null;
    }
}

/**
 * Дешифрует AES-256-GCM.
 * @returns {string | null}
 */
function decryptGCM(ciphertext, timestamp, iv, tag) {
    try {
        const key     = gcmKey(timestamp);
        const ivBuf   = Buffer.from(iv,         'base64');
        const tagBuf  = Buffer.from(tag,        'base64');
        const ctBuf   = Buffer.from(ciphertext, 'base64');

        const decipher = crypto.createDecipheriv('aes-256-gcm', key, ivBuf);
        decipher.setAuthTag(tagBuf);

        const dec = Buffer.concat([
            decipher.update(ctBuf),
            decipher.final(),
        ]);
        return dec.toString('utf8');
    } catch (e) {
        console.error('[crypto] decryptGCM error:', e.message);
        return null;
    }
}

// ─── AES-128-ECB (WoWonder совместимость) ───────────────────────────────────

/**
 * Шифрует текст с AES-128-ECB (WoWonder).
 * @returns {string | null} Base64-строка
 */
function encryptECB(plaintext, timestamp) {
    try {
        const key    = ecbKey(timestamp);
        const cipher = crypto.createCipheriv('aes-128-ecb', key, null);
        cipher.setAutoPadding(true);

        const enc = Buffer.concat([
            cipher.update(Buffer.from(plaintext, 'utf8')),
            cipher.final(),
        ]);
        return enc.toString('base64');
    } catch (e) {
        console.error('[crypto] encryptECB error:', e.message);
        return null;
    }
}

/**
 * Дешифрует AES-128-ECB (WoWonder).
 * @returns {string | null}
 */
function decryptECB(ciphertext, timestamp) {
    try {
        const key      = ecbKey(timestamp);
        const decipher = crypto.createDecipheriv('aes-128-ecb', key, null);
        decipher.setAutoPadding(true);

        const dec = Buffer.concat([
            decipher.update(Buffer.from(ciphertext, 'base64')),
            decipher.final(),
        ]);
        return dec.toString('utf8');
    } catch (e) {
        console.error('[crypto] decryptECB error:', e.message);
        return null;
    }
}

// ─── Высокоуровневые функции ─────────────────────────────────────────────────

/**
 * Автодешифрование строки сообщения из БД.
 * Проверяет cipher_version и применяет нужный алгоритм.
 * @param {object} msg — строка из wo_messages (с полями text, iv, tag, cipher_version, time)
 * @returns {string} расшифрованный текст или '' при ошибке
 */
function decryptMessage(msg) {
    if (!msg.text) return '';

    const version = Number(msg.cipher_version) || CIPHER_VERSION_ECB;

    if (version === CIPHER_VERSION_GCM && msg.iv && msg.tag) {
        const plain = decryptGCM(msg.text, msg.time, msg.iv, msg.tag);
        return plain !== null ? plain : msg.text; // fallback на raw при ошибке тега
    }

    // ECB или plaintext без шифрования (старые сообщения)
    const plain = decryptECB(msg.text, msg.time);
    return plain !== null ? plain : msg.text;
}

/**
 * Шифрует plaintext для записи в БД.
 * Возвращает объект с полями для UPDATE/INSERT.
 *
 * @param {string} plaintext
 * @param {number} timestamp — Unix-timestamp (поле time сообщения)
 * @returns {{
 *   text: string,           — GCM-зашифрованный текст (основной)
 *   text_ecb: string,       — ECB-зашифрованный текст (для WoWonder сайта)
 *   text_preview: string,   — plaintext preview (первые 100 символов, для поиска)
 *   iv: string,             — Base64 IV для GCM
 *   tag: string,            — Base64 auth tag для GCM
 *   cipher_version: number  — 2 (GCM)
 * }}
 */
function encryptForStorage(plaintext, timestamp) {
    if (!plaintext) {
        return {
            text: '', text_ecb: '', text_preview: '',
            iv: null, tag: null,
            cipher_version: CIPHER_VERSION_ECB,
        };
    }

    const gcm     = encryptGCM(plaintext, timestamp);
    const ecb     = encryptECB(plaintext, timestamp);
    const preview = plaintext.slice(0, 100);

    if (gcm) {
        return {
            text:           gcm.text,
            text_ecb:       ecb  || '',
            text_preview:   preview,
            iv:             gcm.iv,
            tag:            gcm.tag,
            cipher_version: CIPHER_VERSION_GCM,
        };
    }

    // Fallback: только ECB если GCM сломался
    console.warn('[crypto] GCM failed, falling back to ECB for timestamp', timestamp);
    return {
        text:           ecb || plaintext,
        text_ecb:       ecb || plaintext,
        text_preview:   preview,
        iv:             null,
        tag:            null,
        cipher_version: CIPHER_VERSION_ECB,
    };
}

module.exports = {
    encryptGCM,
    decryptGCM,
    encryptECB,
    decryptECB,
    decryptMessage,
    encryptForStorage,
    CIPHER_VERSION_ECB,
    CIPHER_VERSION_GCM,
};
