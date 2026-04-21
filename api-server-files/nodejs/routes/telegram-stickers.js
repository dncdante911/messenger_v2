'use strict';

/**
 * Telegram Sticker Proxy
 *
 * Fetches sticker sets from the Telegram Bot API using the bot token
 * stored in .env (TG_BOT_TOKEN). Files are downloaded once and cached
 * on the worldmates.club CDN so the Android app never needs the token.
 *
 * Endpoint:
 *   GET /api/telegram/sticker-set/:setName
 *
 * Response:
 *   { api_status, name, title, is_animated, is_video,
 *     stickers: [{ file_url, thumb_url, emoji, is_animated, is_video }] }
 *
 * TG_BOT_TOKEN must be set in .env.
 * Cached files are written to upload/tg-stickers/ under the site root.
 */

const fs      = require('fs');
const path    = require('path');
const https   = require('https');
const { requireAuth } = require('../helpers/validate-token');

const TG_TOKEN   = process.env.TG_BOT_TOKEN || '';
const TG_API     = `https://api.telegram.org/bot${TG_TOKEN}`;
const TG_FILES   = `https://api.telegram.org/file/bot${TG_TOKEN}`;

// Site root = the directory that serves static files (same as media-upload.js)
const SITE_ROOT  = path.resolve(__dirname, '..', '..', '..');
const CACHE_DIR  = path.join(SITE_ROOT, 'upload', 'tg-stickers');

// ─── Helpers ──────────────────────────────────────────────────────────────────

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function siteUrl(ctx) {
    return (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
}

/** Simple HTTPS GET → Buffer */
function httpsGet(url) {
    return new Promise((resolve, reject) => {
        https.get(url, res => {
            const chunks = [];
            res.on('data', c => chunks.push(c));
            res.on('end', () => {
                if (res.statusCode !== 200) {
                    return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
                }
                resolve(Buffer.concat(chunks));
            });
        }).on('error', reject);
    });
}

/** Call Telegram Bot API and return parsed JSON body. */
async function tgCall(method, params = {}) {
    const qs  = new URLSearchParams(params).toString();
    const url = `${TG_API}/${method}${qs ? '?' + qs : ''}`;
    const buf = await httpsGet(url);
    const json = JSON.parse(buf.toString('utf8'));
    if (!json.ok) throw new Error(`TG API error: ${json.description || 'unknown'}`);
    return json.result;
}

/**
 * Download a TG file by file_id and cache it locally.
 * Returns the CDN URL, or null on error.
 */
async function cacheFile(ctx, fileId, ext) {
    const filename = `${fileId}${ext}`;
    const absPath  = path.join(CACHE_DIR, filename);
    const relPath  = `upload/tg-stickers/${filename}`;
    const cdnUrl   = `${siteUrl(ctx)}/${relPath}`;

    // Already cached — skip download
    if (fs.existsSync(absPath)) return cdnUrl;

    try {
        const fileInfo = await tgCall('getFile', { file_id: fileId });
        const filePath = fileInfo.file_path;
        const fileUrl  = `${TG_FILES}/${filePath}`;
        const data     = await httpsGet(fileUrl);
        ensureDir(CACHE_DIR);
        fs.writeFileSync(absPath, data);
        return cdnUrl;
    } catch (e) {
        console.error(`[TgStickers] cacheFile ${fileId}: ${e.message}`);
        return null;
    }
}

// ─── GET /api/telegram/sticker-set/:setName ───────────────────────────────────

function getTelegramStickerSet(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        if (!TG_TOKEN) {
            return res.json({ api_status: 503, error_message: 'TG_BOT_TOKEN not configured on server' });
        }

        const { setName } = req.params;
        if (!setName || !/^[\w]+$/.test(setName)) {
            return res.json({ api_status: 400, error_message: 'Invalid sticker set name' });
        }

        try {
            const set = await tgCall('getStickerSet', { name: setName });

            const stickers = await Promise.all(
                set.stickers.map(async s => {
                    const isAnimated = s.is_animated || false;
                    const isVideo    = s.is_video    || false;

                    // Main file
                    const ext      = isAnimated ? '.tgs' : isVideo ? '.webm' : '.webp';
                    const fileUrl  = await cacheFile(ctx, s.file_id, ext);

                    // Thumbnail (always webp)
                    const thumbId  = s.thumbnail?.file_id || s.thumb?.file_id || null;
                    const thumbUrl = thumbId ? await cacheFile(ctx, thumbId, '.webp') : fileUrl;

                    return {
                        file_url:    fileUrl,
                        thumb_url:   thumbUrl,
                        emoji:       s.emoji || '',
                        is_animated: isAnimated,
                        is_video:    isVideo,
                    };
                })
            );

            return res.json({
                api_status:  200,
                name:        set.name,
                title:       set.title,
                is_animated: set.is_animated || false,
                is_video:    set.is_video    || false,
                stickers:    stickers.filter(s => s.file_url),
            });

        } catch (e) {
            console.error(`[TgStickers] getStickerSet ${setName}: ${e.message}`);
            return res.json({ api_status: 500, error_message: e.message });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerTelegramStickerRoutes(app, ctx) {
    app.get('/api/telegram/sticker-set/:setName', getTelegramStickerSet(ctx));
    console.log('[TgStickers] Routes registered');
}

module.exports = { registerTelegramStickerRoutes };
