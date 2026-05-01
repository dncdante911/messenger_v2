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
const fs     = require('fs');
const http   = require('http');
const https  = require('https');

// ─── Конфиг из окружения ──────────────────────────────────────────────────────

const NUDENET_ENABLED  = process.env.NUDENET_ENABLED !== 'false';
const NUDENET_URL      = (process.env.NUDENET_SERVICE_URL || 'http://127.0.0.1:5001').replace(/\/$/, '');
const NUDENET_TIMEOUT  = parseInt(process.env.NUDENET_TIMEOUT_MS    || '8000',  10);
const THRESHOLD_BLOCK  = parseFloat(process.env.NUDENET_BLOCK_THRESHOLD || '0.85');
const THRESHOLD_BLUR   = parseFloat(process.env.NUDENET_BLUR_THRESHOLD  || '0.60');
const THRESHOLD_QUEUE  = parseFloat(process.env.NUDENET_QUEUE_THRESHOLD || '0.45');
// pHash: максимальное расстояние Хэмминга (0-64). ≤10 — очень похоже, ≤20 — похоже
const PHASH_MAX_DIST   = parseInt(process.env.PHASH_HAMMING_THRESHOLD || '10', 10);

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

// ─── Расстояние Хэмминга для pHash (BigInt) ──────────────────────────────────

function hammingDistanceBigInt(a, b) {
    // XOR двух BigInt, считаем количество единичных бит (popcount)
    let xor = BigInt(a) ^ BigInt(b);
    let dist = 0;
    while (xor > 0n) {
        dist += Number(xor & 1n);
        xor >>= 1n;
    }
    return dist;
}

// ─── Проверка блэклиста ───────────────────────────────────────────────────────

async function checkHashBlacklist(ctx, sha256, phashInt = null) {
    try {
        // 1. Точное совпадение SHA-256
        const exact = await ctx.wm_content_hash_blacklist.findOne({
            where:      { sha256_hash: sha256 },
            attributes: ['reason'],
            raw:        true
        });
        if (exact) return { blocked: true, reason: exact.reason, matchType: 'sha256' };

        // 2. Perceptual hash — ищем похожие изображения (только если pHash передан)
        if (phashInt !== null && PHASH_MAX_DIST >= 0) {
            const withPhash = await ctx.wm_content_hash_blacklist.findAll({
                where:      { phash_int: { [require('sequelize').Op.ne]: null } },
                attributes: ['phash_int', 'reason'],
                raw:        true
            });
            for (const entry of withPhash) {
                if (entry.phash_int === null) continue;
                const dist = hammingDistanceBigInt(phashInt, entry.phash_int);
                if (dist <= PHASH_MAX_DIST) {
                    console.warn(`[ContentModerator] pHash hit: dist=${dist} reason=${entry.reason}`);
                    return { blocked: true, reason: entry.reason, matchType: 'phash', distance: dist };
                }
            }
        }
    } catch (e) {
        console.error('[ContentModerator] checkHashBlacklist error:', e.message);
    }
    return { blocked: false, reason: null };
}

async function addToHashBlacklist(ctx, sha256, reason, addedBy = 0, phashInt = null) {
    try {
        await ctx.wm_content_hash_blacklist.upsert({
            sha256_hash: sha256,
            phash_int:   phashInt !== null ? String(phashInt) : null,
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

    // 1. Блэклист SHA-256 — до NudeNet (дёшево и быстро)
    const quickCheck = await checkHashBlacklist(ctx, sha256, null);
    if (quickCheck.blocked) {
        console.warn(`[ContentModerator] SHA-256 блэклист: sha256=${sha256.slice(0, 12)}... reason=${quickCheck.reason}`);
        return {
            decision:    DECISION.BLOCK,
            sha256,
            isSensitive: false,
            reason:      `blacklisted:${quickCheck.reason}`,
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

    // 3. NudeNet анализ (возвращает sha256 + phash_int)
    const nudeNetResult = await callNudeNet(buffer);

    // 4. pHash блэклист — после NudeNet (у нас уже есть phash_int из ответа)
    const phashInt = nudeNetResult?.phash_int ?? null;
    if (phashInt !== null) {
        const phashCheck = await checkHashBlacklist(ctx, sha256, phashInt);
        if (phashCheck.blocked) {
            console.warn(
                `[ContentModerator] pHash блэклист: dist=${phashCheck.distance ?? '?'} ` +
                `sha256=${sha256.slice(0, 12)}... reason=${phashCheck.reason}`
            );
            return {
                decision:    DECISION.BLOCK,
                sha256,
                phashInt,
                isSensitive: false,
                reason:      `phash_blacklisted:${phashCheck.reason}`,
                nudeNet:     nudeNetResult
            };
        }
    }

    // 5. Решение по контенту
    const { decision, reason, isSensitive } = makeDecision(contentLevel, nudeNetResult);

    console.log(
        `[ContentModerator] sha256=${sha256.slice(0, 12)}... ` +
        `level=${contentLevel} decision=${decision} reason=${reason}`
    );

    return { decision, sha256, phashInt, isSensitive, reason, nudeNet: nudeNetResult };
}

// ─── Stream-based SHA-256 (не читает весь файл в память) ─────────────────────

function sha256hexFromFile(filePath) {
    return new Promise((resolve, reject) => {
        const hash   = crypto.createHash('sha256');
        const stream = fs.createReadStream(filePath);
        stream.on('error', reject);
        stream.on('data',  chunk => hash.update(chunk));
        stream.on('end',   ()    => resolve(hash.digest('hex')));
    });
}

/**
 * Версия checkContent для дисковых файлов — не загружает весь файл в RAM.
 * Для не-изображений (video/audio/file) SHA-256 считается потоком.
 * Для изображений (≤25 MB) файл читается в Buffer и передаётся в NudeNet.
 *
 * @param {object} ctx
 * @param {string} filePath   — абсолютный путь к файлу на диске
 * @param {string} mediaType  — 'image' | 'video' | 'audio' | 'file'
 * @param {object} context    — { senderId, chatType, entityId }
 */
async function checkContentFromFile(ctx, filePath, mediaType, context = {}) {
    const { senderId = 0, chatType = 'private', entityId = 0 } = context;

    // SHA-256 через стрим — не тратим RAM на большие файлы
    const sha256 = await sha256hexFromFile(filePath);

    // Блэклист — быстрая проверка до NudeNet
    const quickCheck = await checkHashBlacklist(ctx, sha256, null);
    if (quickCheck.blocked) {
        console.warn(`[ContentModerator] SHA-256 блэклист: sha256=${sha256.slice(0, 12)}... reason=${quickCheck.reason}`);
        return {
            decision:    DECISION.BLOCK,
            sha256,
            isSensitive: false,
            reason:      `blacklisted:${quickCheck.reason}`,
            nudeNet:     null
        };
    }

    // Видео/аудио/файлы — только хэш, NudeNet не нужен
    if (mediaType !== 'image') {
        return {
            decision:    DECISION.ALLOW,
            sha256,
            isSensitive: false,
            reason:      `non_image_type:${mediaType}`,
            nudeNet:     null
        };
    }

    // Изображения (≤25 MB): читаем в Buffer для NudeNet
    const buffer       = await fs.promises.readFile(filePath);
    const contentLevel = await getContentLevel(ctx, chatType, entityId);
    const nudeNetResult = await callNudeNet(buffer);

    const phashInt = nudeNetResult?.phash_int ?? null;
    if (phashInt !== null) {
        const phashCheck = await checkHashBlacklist(ctx, sha256, phashInt);
        if (phashCheck.blocked) {
            console.warn(
                `[ContentModerator] pHash блэклист: dist=${phashCheck.distance ?? '?'} ` +
                `sha256=${sha256.slice(0, 12)}...`
            );
            return {
                decision:    DECISION.BLOCK,
                sha256,
                phashInt,
                isSensitive: false,
                reason:      `phash_blacklisted:${phashCheck.reason}`,
                nudeNet:     nudeNetResult
            };
        }
    }

    const { decision, reason, isSensitive } = makeDecision(contentLevel, nudeNetResult);
    console.log(
        `[ContentModerator] sha256=${sha256.slice(0, 12)}... ` +
        `level=${contentLevel} decision=${decision} reason=${reason}`
    );
    return { decision, sha256, phashInt, isSensitive, reason, nudeNet: nudeNetResult };
}

// ─── Экспорт ──────────────────────────────────────────────────────────────────

module.exports = {
    DECISION,
    checkContent,
    checkContentFromFile,
    addToHashBlacklist,
    addToModerationQueue,
    getContentLevel,
    sha256hex,
    sha256hexFromFile,
    hammingDistanceBigInt
};
