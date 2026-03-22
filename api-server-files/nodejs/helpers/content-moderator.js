'use strict';

/**
 * Content Moderator — WorldMates Messenger
 * =========================================
 * Слой автоматической модерации контента.
 *
 * Что делает:
 *   1. Вычисляет SHA-256 хэш файла
 *   2. Проверяет хэш против таблицы wm_content_hash_blacklist
 *   3. Для изображений: отправляет в NudeNet сервис (localhost:5001)
 *   4. На основе результата и политики канала/группы принимает решение:
 *      - ALLOW  : файл чист, доставлять
 *      - BLUR   : файл чувствительный, доставить с флагом is_sensitive=true
 *      - HOLD   : сохранить файл, положить в очередь модерации
 *      - BLOCK  : отклонить загрузку, файл не сохранять
 *
 * Конфиг через .env:
 *   NUDENET_ENABLED=true|false
 *   NUDENET_SERVICE_URL=http://localhost:5001
 *   NUDENET_TIMEOUT_MS=8000
 *   NUDENET_BLOCK_THRESHOLD=0.85
 *   NUDENET_BLUR_THRESHOLD=0.60
 *   NUDENET_QUEUE_THRESHOLD=0.45
 *
 * Использование моделей:
 *   ctx.wm_content_hash_blacklist  — хэши запрещённого контента
 *   ctx.wm_moderation_queue        — очередь на проверку
 *   ctx.wm_content_policy          — политики каналов/групп
 */

const crypto = require('crypto');
const http   = require('http');
const https  = require('https');

// ─── Конфиг из окружения ──────────────────────────────────────────────────────

const NUDENET_ENABLED = process.env.NUDENET_ENABLED !== 'false';
const NUDENET_URL     = (process.env.NUDENET_SERVICE_URL || 'http://127.0.0.1:5001').replace(/\/$/, '');
const NUDENET_TIMEOUT = parseInt(process.env.NUDENET_TIMEOUT_MS   || '8000',  10);
const THRESHOLD_BLOCK = parseFloat(process.env.NUDENET_BLOCK_THRESHOLD || '0.85');
const THRESHOLD_BLUR  = parseFloat(process.env.NUDENET_BLUR_THRESHOLD  || '0.60');
const THRESHOLD_QUEUE = parseFloat(process.env.NUDENET_QUEUE_THRESHOLD || '0.45');

// ─── Решения ─────────────────────────────────────────────────────────────────

const DECISION = Object.freeze({
    ALLOW: 'allow',
    BLUR:  'blur',
    HOLD:  'hold',
    BLOCK: 'block'
});

// ─── SHA-256 хэш ─────────────────────────────────────────────────────────────

function sha256hex(buffer) {
    return crypto.createHash('sha256').update(buffer).digest('hex');
}

// ─── Проверка блэклиста ───────────────────────────────────────────────────────

async function checkHashBlacklist(ctx, hash) {
    try {
        const entry = await ctx.wm_content_hash_blacklist.findOne({
            where:      { sha256_hash: hash },
            attributes: ['reason'],
            raw:        true
        });
        if (entry) return { blocked: true, reason: entry.reason };
    } catch (e) {
        console.error('[ContentModerator] checkHashBlacklist error:', e.message);
    }
    return { blocked: false, reason: null };
}

async function addToHashBlacklist(ctx, hash, reason, addedBy = 0) {
    try {
        await ctx.wm_content_hash_blacklist.upsert({
            sha256_hash: hash,
            reason:      reason || 'explicit',
            added_by:    addedBy,
            created_at:  new Date()
        });
    } catch (e) {
        console.error('[ContentModerator] addToHashBlacklist error:', e.message);
    }
}

// ─── NudeNet HTTP запрос ──────────────────────────────────────────────────────

function callNudeNet(buffer) {
    return new Promise((resolve) => {
        if (!NUDENET_ENABLED) { resolve(null); return; }

        const url       = new URL(`${NUDENET_URL}/analyze`);
        const isHttps   = url.protocol === 'https:';
        const transport = isHttps ? https : http;

        const options = {
            method:   'POST',
            hostname: url.hostname,
            port:     url.port || (isHttps ? 443 : 80),
            path:     url.pathname,
            headers:  {
                'Content-Type':   'application/octet-stream',
                'Content-Length': buffer.length,
            },
            timeout: NUDENET_TIMEOUT
        };

        const req = transport.request(options, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                try { resolve(JSON.parse(data)); }
                catch { console.warn('[ContentModerator] NudeNet: невалидный JSON'); resolve(null); }
            });
        });

        req.on('timeout', () => {
            console.warn(`[ContentModerator] NudeNet timeout (${NUDENET_TIMEOUT}ms) — пропускаю`);
            req.destroy();
            resolve(null); // fail-open
        });
        req.on('error', (e) => {
            console.warn(`[ContentModerator] NudeNet недоступен: ${e.message}`);
            resolve(null); // fail-open
        });

        req.write(buffer);
        req.end();
    });
}

// ─── Политика контента ────────────────────────────────────────────────────────

async function getContentLevel(ctx, chatType, entityId) {
    if (chatType === 'private' || !entityId) {
        return 'adult_verified';
    }
    try {
        const policy = await ctx.wm_content_policy.findOne({
            where:      { entity_type: chatType, entity_id: entityId },
            attributes: ['content_level'],
            raw:        true
        });
        return policy ? policy.content_level : 'all_ages';
    } catch (e) {
        console.error('[ContentModerator] getContentLevel error:', e.message);
        return 'all_ages';
    }
}

// ─── Decision Engine ──────────────────────────────────────────────────────────

function makeDecision(contentLevel, nudeNetResult) {
    if (!nudeNetResult || !nudeNetResult.ok) {
        return { decision: DECISION.ALLOW, reason: 'nudenet_unavailable', isSensitive: false };
    }

    const summary     = nudeNetResult.summary || {};
    const hasExplicit = summary.has_explicit || false;
    const hasPartial  = summary.has_partial  || false;
    const maxScore    = summary.max_score    || 0;

    if (!hasExplicit && !hasPartial) {
        return { decision: DECISION.ALLOW, reason: 'clean', isSensitive: false };
    }

    if (contentLevel === 'all_ages') {
        if (hasExplicit) {
            return { decision: DECISION.BLOCK, reason: 'explicit_in_public', isSensitive: false };
        }
        if (hasPartial && maxScore >= THRESHOLD_BLUR) {
            return { decision: DECISION.BLOCK, reason: 'partial_nudity_in_public', isSensitive: false };
        }
        return { decision: DECISION.ALLOW, reason: 'low_score', isSensitive: false };
    }

    if (contentLevel === 'mature') {
        if (hasExplicit && maxScore >= THRESHOLD_BLOCK) {
            return { decision: DECISION.BLOCK, reason: 'explicit_too_high_for_mature', isSensitive: false };
        }
        if (hasExplicit || (hasPartial && maxScore >= THRESHOLD_BLUR)) {
            return { decision: DECISION.BLUR, reason: 'sensitive_in_mature', isSensitive: true };
        }
        return { decision: DECISION.ALLOW, reason: 'clean_in_mature', isSensitive: false };
    }

    if (contentLevel === 'adult_verified') {
        if (hasExplicit && maxScore >= THRESHOLD_QUEUE) {
            return { decision: DECISION.HOLD, reason: 'explicit_adult_queue', isSensitive: true };
        }
        if (hasPartial || hasExplicit) {
            return { decision: DECISION.BLUR, reason: 'adult_content_allowed', isSensitive: true };
        }
        return { decision: DECISION.ALLOW, reason: 'clean_adult', isSensitive: false };
    }

    return { decision: DECISION.ALLOW, reason: 'unknown_policy', isSensitive: false };
}

// ─── Добавление в очередь ────────────────────────────────────────────────────

async function addToModerationQueue(ctx, params) {
    try {
        await ctx.wm_moderation_queue.create({
            file_path:      params.filePath,
            file_url:       params.fileUrl,
            media_type:     params.mediaType  || 'image',
            sender_id:      params.senderId   || 0,
            channel_id:     params.channelId  || 0,
            group_id:       params.groupId    || 0,
            chat_type:      params.chatType   || 'private',
            content_level:  params.contentLevel || 'all_ages',
            sha256_hash:    params.sha256      || '',
            nudenet_labels: params.nudeNetResult
                ? JSON.stringify(params.nudeNetResult.detections || [])
                : null,
            nudenet_score:  params.nudeNetResult?.summary?.max_score || 0,
            trigger_reason: params.reason      || '',
            status:         'pending',
            created_at:     new Date()
        });
    } catch (e) {
        console.error('[ContentModerator] addToModerationQueue error:', e.message);
    }
}

// ─── Главная функция ──────────────────────────────────────────────────────────

/**
 * @param {object} ctx        — контекст приложения (ctx.wm_*, ctx.sequelize и т.д.)
 * @param {Buffer} buffer     — байты файла
 * @param {string} mediaType  — 'image' | 'video' | 'audio' | 'file'
 * @param {object} context    — { senderId, chatType, entityId }
 * @returns {Promise<{decision, sha256, isSensitive, reason, nudeNet}>}
 */
async function checkContent(ctx, buffer, mediaType, context = {}) {
    const { senderId = 0, chatType = 'private', entityId = 0 } = context;

    const sha256 = sha256hex(buffer);

    // 1. Блэклист — мгновенная проверка
    const blacklistCheck = await checkHashBlacklist(ctx, sha256);
    if (blacklistCheck.blocked) {
        console.warn(`[ContentModerator] Блэклист: sha256=${sha256.slice(0, 12)}... reason=${blacklistCheck.reason}`);
        return {
            decision:    DECISION.BLOCK,
            sha256,
            isSensitive: false,
            reason:      `blacklisted:${blacklistCheck.reason}`,
            nudeNet:     null
        };
    }

    // Видео/аудио/файлы — только хэш-проверка
    if (mediaType !== 'image') {
        return {
            decision:    DECISION.ALLOW,
            sha256,
            isSensitive: false,
            reason:      `non_image_type:${mediaType}`,
            nudeNet:     null
        };
    }

    // 2. Политика контента
    const contentLevel = await getContentLevel(ctx, chatType, entityId);

    // 3. NudeNet анализ
    const nudeNetResult = await callNudeNet(buffer);

    // 4. Решение
    const { decision, reason, isSensitive } = makeDecision(contentLevel, nudeNetResult);

    console.log(
        `[ContentModerator] sha256=${sha256.slice(0, 12)}... ` +
        `level=${contentLevel} decision=${decision} reason=${reason}`
    );

    return { decision, sha256, isSensitive, reason, nudeNet: nudeNetResult };
}

// ─── Экспорт ──────────────────────────────────────────────────────────────────

module.exports = {
    DECISION,
    checkContent,
    addToHashBlacklist,
    addToModerationQueue,
    getContentLevel,
    sha256hex
};
