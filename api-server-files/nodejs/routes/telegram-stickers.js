'use strict';

const https = require('https');
const { requireAuth } = require('../helpers/validate-token');

const TELEGRAM_API = 'https://api.telegram.org';
const PACK_CACHE_TTL = 60 * 60 * 1000; // 1 hour

// In-memory cache: { [setName]: { data, timestamp } }
const packCache = new Map();

// File-id → file_path cache to reduce Telegram API calls
const filePathCache = new Map();
const FILE_PATH_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours

function getBotToken() {
    const token = process.env.TELEGRAM_BOT_TOKEN;
    if (!token) throw new Error('TELEGRAM_BOT_TOKEN is not configured on the server');
    return token;
}

function telegramGet(method, params = {}) {
    return new Promise((resolve, reject) => {
        const token = getBotToken();
        const query = new URLSearchParams(params).toString();
        const url = `${TELEGRAM_API}/bot${token}/${method}?${query}`;

        https.get(url, (res) => {
            let raw = '';
            res.on('data', chunk => { raw += chunk; });
            res.on('end', () => {
                try { resolve(JSON.parse(raw)); }
                catch (e) { reject(new Error('Invalid JSON from Telegram API')); }
            });
        }).on('error', reject);
    });
}

function buildProxyUrl(ctx, fileId, format) {
    const nodeBase = (
        process.env.NODE_WEBHOOK_BASE_URL ||
        ctx.globalconfig?.node_url ||
        'https://worldmates.club:449'
    ).replace(/\/$/, '');
    // Append the format as a file extension so AnimatedStickerView can detect it by URL
    const ext = format === 'tgs' ? '.tgs' : format === 'webm' ? '.webm' : '.webp';
    return `${nodeBase}/api/node/telegram/sticker-file/${encodeURIComponent(fileId)}${ext}`;
}

async function fetchStickerSet(setName) {
    const cached = packCache.get(setName);
    if (cached && Date.now() - cached.timestamp < PACK_CACHE_TTL) {
        return cached.data;
    }

    const result = await telegramGet('getStickerSet', { name: setName });
    if (!result.ok) throw new Error(result.description || 'Telegram API error');

    packCache.set(setName, { data: result.result, timestamp: Date.now() });
    return result.result;
}

function stickerSetToPackResponse(ctx, set, packId) {
    const stickers = set.stickers.map((s, i) => {
        const fmt = s.is_video ? 'webm' : (s.is_animated ? 'tgs' : 'webp');
        return {
        id:            packId * 1000 + i,
        pack_id:       packId,
        file_url:      buildProxyUrl(ctx, s.file_id, fmt),
        thumbnail_url: s.thumbnail
            ? buildProxyUrl(ctx, s.thumbnail.file_id, 'webp')
            : buildProxyUrl(ctx, s.file_id, fmt),
        emoji:         s.emoji || null,
        format:        fmt,
        width:         s.width  || null,
        height:        s.height || null,
        };
    });

    const pack = {
        id:            packId,
        name:          set.title || set.name,
        description:   `Telegram: @${set.name}`,
        icon_url:      stickers[0]?.thumbnail_url || null,
        author:        'Telegram',
        is_active:     true,
        is_animated:   set.stickers.some(s => s.is_animated),
        sticker_count: stickers.length,
        stickers,
    };

    return { api_status: 200, pack, stickers };
}

// ─── GET /api/node/telegram/animated-emoji ────────────────────────────────────
// Returns the Telegram AnimatedEmojies sticker set in our StickerPack format.

function getAnimatedEmoji(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const set = await fetchStickerSet('AnimatedEmojies');
            return res.json(stickerSetToPackResponse(ctx, set, 9001));
        } catch (err) {
            console.error('[TelegramStickers/animatedEmoji]', err.message);
            return res.json({ api_status: 500, error_message: err.message });
        }
    }];
}

// ─── GET /api/node/telegram/sticker-pack/:setName ─────────────────────────────
// Returns any public Telegram sticker set by its short name.

function getStickerPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const { setName } = req.params;
            if (!setName || !/^[a-zA-Z0-9_]{1,64}$/.test(setName)) {
                return res.status(400).json({ api_status: 400, error_message: 'Invalid sticker set name' });
            }
            const set = await fetchStickerSet(setName);
            return res.json(stickerSetToPackResponse(ctx, set, 9002));
        } catch (err) {
            console.error('[TelegramStickers/getStickerPack]', err.message);
            return res.json({ api_status: 500, error_message: err.message });
        }
    }];
}

// ─── GET /api/node/telegram/sticker-file/:fileId ─────────────────────────────
// PUBLIC — no auth. Proxies .tgs / .webp / .webm file download from Telegram CDN.
// No auth because AnimatedStickerView uses raw java.net.URL without custom headers.

function getStickerFile(ctx) {
    return [async (req, res) => {
        try {
            const rawParam = req.params.fileId || '';
            // Strip the format hint extension we appended in buildProxyUrl
            const fileId = decodeURIComponent(rawParam).replace(/\.(tgs|webp|webm)$/i, '');
            if (!fileId || fileId.length > 200) {
                return res.status(400).end();
            }

            let filePath = null;
            const cachedPath = filePathCache.get(fileId);
            if (cachedPath && Date.now() - cachedPath.timestamp < FILE_PATH_CACHE_TTL) {
                filePath = cachedPath.path;
            } else {
                const fileInfo = await telegramGet('getFile', { file_id: fileId });
                if (!fileInfo.ok || !fileInfo.result?.file_path) {
                    return res.status(404).end();
                }
                filePath = fileInfo.result.file_path;
                filePathCache.set(fileId, { path: filePath, timestamp: Date.now() });
            }

            const token = getBotToken();
            const fileUrl = `${TELEGRAM_API}/file/bot${token}/${filePath}`;

            const contentType = filePath.endsWith('.tgs')  ? 'application/octet-stream'
                              : filePath.endsWith('.webp') ? 'image/webp'
                              : filePath.endsWith('.webm') ? 'video/webm'
                              : 'application/octet-stream';

            https.get(fileUrl, (telegramRes) => {
                res.setHeader('Content-Type', contentType);
                res.setHeader('Cache-Control', 'public, max-age=86400');
                telegramRes.pipe(res);
            }).on('error', (err) => {
                console.error('[TelegramStickers/getFile pipe]', err.message);
                if (!res.headersSent) res.status(502).end();
            });
        } catch (err) {
            console.error('[TelegramStickers/getStickerFile]', err.message);
            if (!res.headersSent) res.status(500).end();
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerTelegramStickerRoutes(app, ctx) {
    app.get('/api/node/telegram/animated-emoji',        getAnimatedEmoji(ctx));
    app.get('/api/node/telegram/sticker-pack/:setName', getStickerPack(ctx));
    app.get('/api/node/telegram/sticker-file/:fileId',  getStickerFile(ctx));
    console.log('[TelegramStickers] Routes registered');
}

module.exports = { registerTelegramStickerRoutes };
