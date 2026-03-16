'use strict';

/**
 * Stickers & Emoji Packs REST API
 *
 * Replaces PHP:
 *   GET  ?type=get_sticker_packs
 *   GET  ?type=get_sticker_pack&pack_id=
 *   POST ?type=activate_sticker_pack
 *   POST ?type=deactivate_sticker_pack
 *   GET  ?type=get_emoji_packs
 *   GET  ?type=get_emoji_pack&pack_id=
 *   POST ?type=activate_emoji_pack
 *   POST ?type=deactivate_emoji_pack
 *
 * Node.js endpoints:
 *   GET  /api/node/stickers            — list all sticker packs
 *   GET  /api/node/stickers/:packId    — get pack with stickers
 *   POST /api/node/stickers/:packId/activate
 *   POST /api/node/stickers/:packId/deactivate
 *   GET  /api/node/emoji               — list all emoji packs
 *   GET  /api/node/emoji/:packId       — get emoji pack
 *   POST /api/node/emoji/:packId/activate
 *   POST /api/node/emoji/:packId/deactivate
 *
 * DB: Wo_Stickers is a flat table (id, name, media_file).
 * All stickers are grouped into a single default pack (id=1).
 * User's active packs are stored in wo_users.details JSON.
 */

const { requireAuth } = require('../helpers/validate-token');

const DEFAULT_PACK_ID   = 1;
const DEFAULT_PACK_NAME = 'WorldMates Stickers';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function mediaUrl(ctx, path) {
    if (!path || path === '' || path === '0') return '';
    if (/^https?:\/\//.test(path)) return path;
    const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
    return `${base}/${path.replace(/^\//, '')}`;
}

function parseDetails(raw) {
    try { return typeof raw === 'string' ? JSON.parse(raw) : (raw || {}); }
    catch { return {}; }
}

async function isPackActive(ctx, userId, packId) {
    const user = await ctx.wo_users.findOne({
        where: { user_id: userId }, attributes: ['details'], raw: true,
    });
    if (!user) return true;
    const d = parseDetails(user.details);
    // If no preference set, default to active
    if (!d.sticker_packs_inactive) return true;
    return !d.sticker_packs_inactive.includes(Number(packId));
}

async function setPackActive(ctx, userId, packId, active) {
    const user = await ctx.wo_users.findOne({
        where: { user_id: userId }, attributes: ['details'], raw: true,
    });
    if (!user) return;
    const d = parseDetails(user.details);
    let inactive = (d.sticker_packs_inactive || []).map(Number);
    if (active) {
        inactive = inactive.filter(id => id !== Number(packId));
    } else {
        if (!inactive.includes(Number(packId))) inactive.push(Number(packId));
    }
    d.sticker_packs_inactive = inactive;
    await ctx.wo_users.update({ details: JSON.stringify(d) }, { where: { user_id: userId } });
}

function buildPack(ctx, stickers, isActive, withStickers = false) {
    const pack = {
        id:            DEFAULT_PACK_ID,
        name:          DEFAULT_PACK_NAME,
        description:   null,
        icon_url:      stickers.length > 0 ? mediaUrl(ctx, stickers[0].media_file) : null,
        author:        'WorldMates',
        is_active:     isActive,
        is_animated:   false,
        sticker_count: stickers.length,
    };
    if (withStickers) {
        pack.stickers = stickers.map(s => ({
            id:            s.id,
            pack_id:       DEFAULT_PACK_ID,
            file_url:      mediaUrl(ctx, s.media_file),
            thumbnail_url: mediaUrl(ctx, s.media_file),
            emoji:         null,
            format:        'webp',
        }));
    }
    return pack;
}

// ─── GET /api/node/stickers ───────────────────────────────────────────────────

function getStickerPacks(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const stickers = await ctx.wo_stickers.findAll({ raw: true });
            const active   = await isPackActive(ctx, req.userId, DEFAULT_PACK_ID);
            const pack     = buildPack(ctx, stickers, active, false);
            return res.json({ api_status: 200, packs: [pack] });
        } catch (err) {
            console.error('[Stickers/getPacks]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── GET /api/node/stickers/:packId ──────────────────────────────────────────

function getStickerPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const stickers = await ctx.wo_stickers.findAll({ raw: true });
            const active   = await isPackActive(ctx, req.userId, DEFAULT_PACK_ID);
            const pack     = buildPack(ctx, stickers, active, true);
            return res.json({ api_status: 200, pack, stickers: pack.stickers });
        } catch (err) {
            console.error('[Stickers/getPack]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/stickers/:packId/activate ────────────────────────────────

function activateStickerPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            await setPackActive(ctx, req.userId, DEFAULT_PACK_ID, true);
            const stickers = await ctx.wo_stickers.findAll({ raw: true });
            const pack     = buildPack(ctx, stickers, true, false);
            return res.json({ api_status: 200, pack });
        } catch (err) {
            console.error('[Stickers/activate]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/stickers/:packId/deactivate ──────────────────────────────

function deactivateStickerPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            await setPackActive(ctx, req.userId, DEFAULT_PACK_ID, false);
            const stickers = await ctx.wo_stickers.findAll({ raw: true });
            const pack     = buildPack(ctx, stickers, false, false);
            return res.json({ api_status: 200, pack });
        } catch (err) {
            console.error('[Stickers/deactivate]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Emoji packs — no custom emoji table; return empty lists ─────────────────

function getEmojiPacks(ctx) {
    return [requireAuth(ctx), (req, res) => {
        res.json({ api_status: 200, packs: [] });
    }];
}

function getEmojiPack(ctx) {
    return [requireAuth(ctx), (req, res) => {
        res.json({ api_status: 404, error_message: 'Emoji pack not found' });
    }];
}

function emojiPackNoop(ctx) {
    return [requireAuth(ctx), (req, res) => {
        res.json({ api_status: 200, pack: null });
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerStickerRoutes(app, ctx) {
    app.get ('/api/node/stickers',                       getStickerPacks(ctx));
    app.get ('/api/node/stickers/:packId',               getStickerPack(ctx));
    app.post('/api/node/stickers/:packId/activate',      activateStickerPack(ctx));
    app.post('/api/node/stickers/:packId/deactivate',    deactivateStickerPack(ctx));

    app.get ('/api/node/emoji',                          getEmojiPacks(ctx));
    app.get ('/api/node/emoji/:packId',                  getEmojiPack(ctx));
    app.post('/api/node/emoji/:packId/activate',         emojiPackNoop(ctx));
    app.post('/api/node/emoji/:packId/deactivate',       emojiPackNoop(ctx));

    console.log('[Stickers] Routes registered:');
    console.log('  GET  /api/node/stickers');
    console.log('  GET  /api/node/stickers/:packId');
    console.log('  POST /api/node/stickers/:packId/activate');
    console.log('  POST /api/node/stickers/:packId/deactivate');
    console.log('  GET  /api/node/emoji');
    console.log('  GET  /api/node/emoji/:packId');
}

module.exports = { registerStickerRoutes };
