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

// ─── GET /api/node/stickers/strapi-meta ──────────────────────────────────────
// Returns PRO metadata for all Strapi sticker packs for the current user.
// Response: { api_status: 200, packs: [{slug, is_pro, stars_price, is_purchased}] }

function getStrapiMeta(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const [proPacks] = await ctx.sequelize.query(
                'SELECT slug, stars_price FROM wm_sticker_pro_packs',
            );
            const proMap = {};
            for (const p of proPacks) proMap[p.slug] = p.stars_price;

            const [purchases] = await ctx.sequelize.query(
                'SELECT slug FROM wm_sticker_purchases WHERE user_id = ?',
                { replacements: [req.userId] }
            );
            const purchasedSet = new Set(purchases.map(p => p.slug));

            const result = proPacks.map(p => ({
                slug:         p.slug,
                is_pro:       true,
                stars_price:  p.stars_price,
                is_purchased: purchasedSet.has(p.slug),
            }));

            return res.json({ api_status: 200, packs: result });
        } catch (err) {
            console.error('[Stickers/getStrapiMeta]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/stickers/strapi-buy ──────────────────────────────────────
// Purchase a PRO sticker pack with WorldStars.
// Body: { slug }

function buyStraPiPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const { slug } = req.body;
            if (!slug) return res.status(400).json({ api_status: 400, error_message: 'slug required' });

            // Get PRO pack info
            const [proPacks] = await ctx.sequelize.query(
                'SELECT id, stars_price FROM wm_sticker_pro_packs WHERE slug = ?',
                { replacements: [slug] }
            );
            if (!proPacks.length) {
                return res.status(404).json({ api_status: 404, error_message: 'Pack not found or not a PRO pack' });
            }
            const starsPrice = proPacks[0].stars_price;

            // Check if already purchased
            const [existing] = await ctx.sequelize.query(
                'SELECT id FROM wm_sticker_purchases WHERE user_id = ? AND slug = ?',
                { replacements: [req.userId, slug] }
            );
            if (existing.length) {
                return res.json({ api_status: 200, message: 'Already owned', already_owned: true });
            }

            // Check stars balance
            const [balRows] = await ctx.sequelize.query(
                'SELECT balance FROM wm_stars_balance WHERE user_id = ?',
                { replacements: [req.userId] }
            );
            const balance = balRows.length ? balRows[0].balance : 0;
            if (balance < starsPrice) {
                return res.status(400).json({ api_status: 400, error_message: 'Not enough WorldStars', balance, required: starsPrice });
            }

            // Deduct stars and record purchase in transaction
            await ctx.sequelize.query(
                'UPDATE wm_stars_balance SET balance = balance - ?, total_sent = total_sent + ? WHERE user_id = ?',
                { replacements: [starsPrice, starsPrice, req.userId] }
            );
            await ctx.sequelize.query(
                `INSERT INTO wm_stars_transactions (from_user_id, to_user_id, amount, type, ref_type, note)
                 VALUES (?, ?, ?, 'send', 'sticker_pack', ?)`,
                { replacements: [req.userId, req.userId, starsPrice, `Sticker pack: ${slug}`] }
            );
            await ctx.sequelize.query(
                'INSERT INTO wm_sticker_purchases (user_id, slug, stars_paid) VALUES (?, ?, ?)',
                { replacements: [req.userId, slug, starsPrice] }
            );

            const newBalance = balance - starsPrice;
            return res.json({ api_status: 200, new_balance: newBalance });
        } catch (err) {
            console.error('[Stickers/buyPack]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/stickers/admin/pro ───────────────────────────────────────
// Admin-only: register a Strapi slug as a PRO pack with a WorldStars price.
// Body: { slug, stars_price }

function adminSetProPack(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            // Only admins can call this
            const user = await ctx.wo_users.findOne({ where: { user_id: req.userId }, attributes: ['admin'], raw: true });
            if (!user || user.admin !== '1') {
                return res.status(403).json({ api_status: 403, error_message: 'Admin only' });
            }

            const { slug, stars_price } = req.body;
            if (!slug || !stars_price) {
                return res.status(400).json({ api_status: 400, error_message: 'slug and stars_price required' });
            }
            const price = parseInt(stars_price, 10);
            if (isNaN(price) || price <= 0) {
                return res.status(400).json({ api_status: 400, error_message: 'stars_price must be a positive integer' });
            }

            await ctx.sequelize.query(
                `INSERT INTO wm_sticker_pro_packs (slug, stars_price)
                 VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE stars_price = VALUES(stars_price)`,
                { replacements: [slug, price] }
            );
            return res.json({ api_status: 200, slug, stars_price: price });
        } catch (err) {
            console.error('[Stickers/adminSetPro]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerStickerRoutes(app, ctx) {
    // PRO sticker routes must be registered BEFORE /:packId to avoid slug capture
    app.get ('/api/node/stickers/strapi-meta',           getStrapiMeta(ctx));
    app.post('/api/node/stickers/strapi-buy',            buyStraPiPack(ctx));
    app.post('/api/node/stickers/admin/pro',             adminSetProPack(ctx));

    app.get ('/api/node/stickers',                       getStickerPacks(ctx));
    app.get ('/api/node/stickers/:packId',               getStickerPack(ctx));
    app.post('/api/node/stickers/:packId/activate',      activateStickerPack(ctx));
    app.post('/api/node/stickers/:packId/deactivate',    deactivateStickerPack(ctx));

    app.get ('/api/node/emoji',                          getEmojiPacks(ctx));
    app.get ('/api/node/emoji/:packId',                  getEmojiPack(ctx));
    app.post('/api/node/emoji/:packId/activate',         emojiPackNoop(ctx));
    app.post('/api/node/emoji/:packId/deactivate',       emojiPackNoop(ctx));

    console.log('[Stickers] Routes registered (incl. Strapi PRO packs)');
}

module.exports = { registerStickerRoutes };
