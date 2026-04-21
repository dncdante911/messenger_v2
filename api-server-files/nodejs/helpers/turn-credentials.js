/**
 * TURN Credentials Helper for WorldMates
 * Generates dynamic TURN credentials using HMAC-SHA1
 */

const crypto = require('crypto');

// ВАЖНО: Цей секрет збігається з static-auth-secret у /etc/turnserver.conf
// Значення береться з .env (TURN_SECRET). Ніколи не хардкодити тут!
const TURN_SECRET = process.env.TURN_SECRET || (() => {
    console.error('[TURN] УВАГА: TURN_SECRET не задано в .env! Використовується небезпечне значення за замовчуванням.');
    return 'change_me_in_env_file';
})();

// Конфигурация серверов (внешние IP вашего шлюза с HAProxy)
const TURN_SERVER_URL = 'worldmates.club';
const TURN_IPS = ['195.22.131.11', '46.232.232.38', '93.171.188.229'];
const TURN_PORT = 3478;
const TURN_TLS_PORT = 5349;
// Для TURNS (TLS) используем доменное имя — SSL сертификат привязан к домену, а не к IP.
// Если указать IP, TLS проверка hostname провалится → ошибка 701 (Unauthorized).
const TURNS_DOMAIN = 'worldmates.club';

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
        // Стандартный TURN (UDP и TCP) — по IP, TLS не нужен
        iceServers.push({
            urls: [
                `turn:${ip}:${TURN_PORT}?transport=udp`,
                `turn:${ip}:${TURN_PORT}?transport=tcp`
            ],
            username: turnCredentials.username,
            credential: turnCredentials.password
        });
    });

    // Защищенный TURNS over TLS — по ДОМЕНУ (сертификат привязан к домену, не к IP)
    iceServers.push({
        urls: `turns:${TURNS_DOMAIN}:${TURN_TLS_PORT}?transport=tcp`,
        username: turnCredentials.username,
        credential: turnCredentials.password
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
