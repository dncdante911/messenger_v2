/**
 * WorldMates Crypto Helper — Node.js
 *
 * AES-256-GCM  — для WorldMates Android (аутентифицированное шифрование)
 *
 * Ключ GCM: timestamp-строка повторяется до 32 байт.
 * IV: 12 байт случайных (NIST recommendation)
 * Tag: 16 байт (128 бит)
 * Кодировка: Base64
 *
 * NOTE: AES-128-ECB (WoWonder legacy) удалён. Сайт переходит на современное шифрование.
 */

'use strict';

const crypto = require('crypto');

const GCM_KEY_LEN = 32; // 256 бит
const IV_LEN      = 12; // 96 бит (NIST для GCM)
const TAG_LEN     = 16; // 128 бит

const CIPHER_VERSION_GCM = 2;

// ─── Key derivation ──────────────────────────────────────────────────────────

/**
 * GCM ключ: повторяем timestamp до 32 байт.
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

// ─── Высокоуровневые функции ─────────────────────────────────────────────────

/**
 * Автодешифрование строки сообщения из БД.
 * Поддерживает cipher_version=2 (GCM).
 * cipher_version=1 (ECB legacy) — возвращает raw, клиент должен обработать сам.
 * cipher_version=3 (Signal) — сервер не дешифрует, возвращает raw.
 * @param {object} msg — строка из wo_messages (с полями text, iv, tag, cipher_version, time)
 * @returns {string} расшифрованный текст или '' при ошибке
 */
function decryptMessage(msg) {
    if (!msg.text) return '';

    const version = Number(msg.cipher_version) || CIPHER_VERSION_GCM;

    if (version === CIPHER_VERSION_GCM && msg.iv && msg.tag) {
        const plain = decryptGCM(msg.text, msg.time, msg.iv, msg.tag);
        return plain !== null ? plain : msg.text;
    }

    // version=1 (old ECB legacy) or version=3 (Signal E2EE) — return raw
    return msg.text;
}

/**
 * Шифрует plaintext для записи в БД (AES-256-GCM only).
 *
 * @param {string} plaintext
 * @param {number} timestamp — Unix-timestamp (поле time сообщения)
 * @returns {{
 *   text: string,           — GCM-зашифрованный текст
 *   text_ecb: string,       — пустая строка (ECB удалён)
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
            cipher_version: CIPHER_VERSION_GCM,
        };
    }

    const gcm     = encryptGCM(plaintext, timestamp);
    const preview = plaintext.slice(0, 100);

    if (gcm) {
        return {
            text:           gcm.text,
            text_ecb:       '',
            text_preview:   preview,
            iv:             gcm.iv,
            tag:            gcm.tag,
            cipher_version: CIPHER_VERSION_GCM,
        };
    }

    // GCM failed (should never happen) — store plaintext as fallback
    console.error('[crypto] encryptGCM failed for timestamp', timestamp);
    return {
        text:           plaintext,
        text_ecb:       '',
        text_preview:   preview,
        iv:             null,
        tag:            null,
        cipher_version: CIPHER_VERSION_GCM,
    };
}

module.exports = {
    encryptGCM,
    decryptGCM,
    decryptMessage,
    encryptForStorage,
    CIPHER_VERSION_GCM,
};
