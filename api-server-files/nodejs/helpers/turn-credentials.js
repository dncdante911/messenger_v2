/**
 * TURN Credentials Helper for WorldMates
 * Generates dynamic TURN credentials using HMAC-SHA1
 */

const crypto = require('crypto');

// ВАЖНО: Этот секрет совпадает с static-auth-secret в /etc/turnserver.conf
const TURN_SECRET = 'ad8a76d057d6ba0d6fd79bbc84504e320c8538b92db5c9b84fc3bd18d1c511b9';

// Конфигурация серверов (внешние IP вашего шлюза с HAProxy)
const TURN_SERVER_URL = 'worldmates.club';
const TURN_IPS = ['195.22.131.11', '46.232.232.38'];
const TURN_PORT = 3478;
const TURN_TLS_PORT = 5349;

/**
 * Генерирует временные учетные данные TURN для пользователя
 * @param {string|number} userId - ID пользователя
 * @param {number} ttl - Время жизни в секундах (по умолчанию 24 часа)
 * @returns {Object} - { username, password, expiresAt }
 */
function generateTurnCredentials(userId, ttl = 86400) {
    // Unix timestamp срока действия
    const expirationTimestamp = Math.floor(Date.now() / 1000) + ttl;

    // Формат имени пользователя для Coturn: timestamp:userId
    const username = `${expirationTimestamp}:${userId}`;

    // Генерация пароля: base64(HMAC-SHA1(secret, username))
    const hmac = crypto.createHmac('sha1', TURN_SECRET);
    hmac.update(username);
    const password = hmac.digest('base64');

    return {
        username: username,
        password: password,
        expiresAt: new Date(expirationTimestamp * 1000)
    };
}

/**
 * Получает полную конфигурацию ICE серверов для WebRTC
 * @param {string|number} userId - ID пользователя
 * @param {number} ttl - TTL учетных данных
 * @returns {Array} - Массив конфигураций ICE серверов
 */
function getIceServers(userId, ttl = 86400) {
    const turnCredentials = generateTurnCredentials(userId, ttl);

    // Базовые STUN серверы Google
    const iceServers = [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' }
    ];

    // Добавляем TURN серверы для каждого внешнего IP
    TURN_IPS.forEach(ip => {
        // Стандартный TURN (UDP и TCP)
        iceServers.push({
            urls: [
                `turn:${ip}:${TURN_PORT}?transport=udp`,
                `turn:${ip}:${TURN_PORT}?transport=tcp`
            ],
            username: turnCredentials.username,
            credential: turnCredentials.password
        });

        // Защищенный TURN over TLS (через TCP)
        iceServers.push({
            urls: `turns:${ip}:${TURN_TLS_PORT}?transport=tcp`,
            username: turnCredentials.username,
            credential: turnCredentials.password
        });
    });

    return iceServers;
}

/**
 * Проверка валидности учетных данных (если потребуется на бэкенде)
 */
function validateTurnCredentials(username, password) {
    try {
        const parts = username.split(':');
        if (parts.length !== 2) return false;

        const expirationTimestamp = parseInt(parts[0]);
        const now = Math.floor(Date.now() / 1000);

        if (now > expirationTimestamp) return false;

        const hmac = crypto.createHmac('sha1', TURN_SECRET);
        hmac.update(username);
        const expectedPassword = hmac.digest('base64');

        return password === expectedPassword;
    } catch (error) {
        console.error('[TURN] Validation error:', error);
        return false;
    }
}

/**
 * Формат ответа специально для Android/iOS и API
 */
function getIceConfigForAndroid(userId) {
    const iceServers = getIceServers(userId);

    return {
        success: true,
        iceServers: iceServers,
        timestamp: Date.now()
    };
}

module.exports = {
    generateTurnCredentials,
    getIceServers,
    validateTurnCredentials,
    getIceConfigForAndroid,
    TURN_SERVER_URL,
    TURN_PORT,
    TURN_TLS_PORT
};
