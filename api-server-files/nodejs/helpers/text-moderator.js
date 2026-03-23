'use strict';

/**
 * Text Moderator — WorldMates Messenger
 * ======================================
 * Проверяет текстовые сообщения через Detoxify сервис (localhost:5002).
 *
 * Используется только для cipher_version=1/2 (сервер видит plaintext).
 * cipher_version=3 (Signal E2EE) — пропускается, сервер не имеет plaintext.
 *
 * Решения:
 *   ALLOW — текст чистый
 *   WARN  — логируем, продолжаем (score >= WARN_THRESHOLD)
 *   BLOCK — блокируем сообщение (score >= BLOCK_THRESHOLD)
 *
 * Конфиг через .env:
 *   DETOXIFY_ENABLED=true|false
 *   DETOXIFY_SERVICE_URL=http://localhost:5002
 *   DETOXIFY_TIMEOUT_MS=3000
 *   DETOXIFY_BLOCK_THRESHOLD=0.85
 *   DETOXIFY_WARN_THRESHOLD=0.60
 *   DETOXIFY_MIN_LENGTH=3     — не проверяем короткие сообщения
 */

const http  = require('http');
const https = require('https');

// ─── Конфиг ───────────────────────────────────────────────────────────────────

const ENABLED          = process.env.DETOXIFY_ENABLED !== 'false';
const SERVICE_URL      = (process.env.DETOXIFY_SERVICE_URL || 'http://127.0.0.1:5002').replace(/\/$/, '');
const TIMEOUT_MS       = parseInt(process.env.DETOXIFY_TIMEOUT_MS      || '3000', 10);
const BLOCK_THRESHOLD  = parseFloat(process.env.DETOXIFY_BLOCK_THRESHOLD || '0.85');
const WARN_THRESHOLD   = parseFloat(process.env.DETOXIFY_WARN_THRESHOLD  || '0.60');
const MIN_TEXT_LENGTH  = parseInt(process.env.DETOXIFY_MIN_LENGTH        || '3',   10);

// ─── Решения ──────────────────────────────────────────────────────────────────

const TEXT_DECISION = Object.freeze({
    ALLOW: 'allow',
    WARN:  'warn',
    BLOCK: 'block'
});

// ─── HTTP запрос к Detoxify ───────────────────────────────────────────────────

function callDetoxify(text) {
    return new Promise((resolve) => {
        if (!ENABLED) { resolve(null); return; }

        const body    = JSON.stringify({ text });
        const url     = new URL(`${SERVICE_URL}/analyze`);
        const isHttps = url.protocol === 'https:';
        const transport = isHttps ? https : http;

        const options = {
            method:   'POST',
            hostname: url.hostname,
            port:     url.port || (isHttps ? 443 : 80),
            path:     url.pathname,
            headers:  {
                'Content-Type':   'application/json',
                'Content-Length': Buffer.byteLength(body),
            },
            timeout: TIMEOUT_MS
        };

        const req = transport.request(options, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                try { resolve(JSON.parse(data)); }
                catch { console.warn('[TextModerator] Detoxify: невалидный JSON'); resolve(null); }
            });
        });

        req.on('timeout', () => {
            console.warn(`[TextModerator] Detoxify timeout (${TIMEOUT_MS}ms) — пропускаю`);
            req.destroy();
            resolve(null); // fail-open: при недоступности сервиса не блокируем
        });
        req.on('error', (e) => {
            console.warn(`[TextModerator] Detoxify недоступен: ${e.message}`);
            resolve(null); // fail-open
        });

        req.write(body);
        req.end();
    });
}

// ─── Главная функция ──────────────────────────────────────────────────────────

/**
 * Проверяет текст сообщения на токсичность.
 *
 * @param {string}  text          — plaintext сообщения (НЕ зашифрованный)
 * @param {number}  cipherVersion — если 3 (Signal E2EE) — пропускаем
 * @param {object}  meta          — { senderId, chatType, entityId } для логов
 * @param {object}  [ctx]         — контекст приложения для записи в wm_text_violations (опционально)
 * @returns {Promise<{ decision, score, category, scores }>}
 */
async function checkText(text, cipherVersion = 1, meta = {}, ctx = null) {
    // E2EE — сервер не имеет plaintext, не проверяем
    if (cipherVersion === 3) {
        return { decision: TEXT_DECISION.ALLOW, score: 0, category: 'e2ee_skip', scores: null };
    }

    if (!text || text.length < MIN_TEXT_LENGTH) {
        return { decision: TEXT_DECISION.ALLOW, score: 0, category: 'too_short', scores: null };
    }

    const result = await callDetoxify(text);

    if (!result || !result.ok) {
        // Сервис недоступен — fail-open, не блокируем
        return { decision: TEXT_DECISION.ALLOW, score: 0, category: 'service_unavailable', scores: null };
    }

    const { max_score, top_category, scores, action } = result;

    let decision;
    if (action === 'block') {
        decision = TEXT_DECISION.BLOCK;
        console.warn(
            `[TextModerator] BLOCK: sender=${meta.senderId || '?'} ` +
            `chat=${meta.chatType || '?'}/${meta.entityId || '?'} ` +
            `score=${max_score} category=${top_category}`
        );
    } else if (action === 'warn') {
        decision = TEXT_DECISION.WARN;
        console.warn(
            `[TextModerator] WARN: sender=${meta.senderId || '?'} ` +
            `score=${max_score} category=${top_category}`
        );
    } else {
        decision = TEXT_DECISION.ALLOW;
    }

    // Логируем warn/block в wm_text_violations (если ctx передан и таблица доступна)
    if (decision !== TEXT_DECISION.ALLOW && ctx && ctx.wm_text_violations) {
        setImmediate(async () => {
            try {
                await ctx.wm_text_violations.create({
                    sender_id:    meta.senderId  || 0,
                    chat_type:    meta.chatType  || 'private',
                    entity_id:    meta.entityId  || 0,
                    action:       decision,
                    top_category: top_category   || '',
                    max_score:    max_score,
                    text_preview: text.slice(0, 200),
                    created_at:   new Date()
                });
            } catch (e) {
                // Не критично — лог не должен ломать сообщения
                console.warn('[TextModerator] wm_text_violations write error:', e.message);
            }
        });
    }

    return { decision, score: max_score, category: top_category, scores };
}

// ─── Экспорт ──────────────────────────────────────────────────────────────────

module.exports = {
    TEXT_DECISION,
    checkText
};
