'use strict';

/**
 * Business Mode API
 *
 * Endpoints:
 *   GET    /api/node/business/profile             – get my business profile
 *   PUT    /api/node/business/profile             – create/update business profile
 *   DELETE /api/node/business/profile             – disable business mode
 *
 *   GET    /api/node/business/hours               – get working hours (7 rows)
 *   PUT    /api/node/business/hours               – save working hours (bulk)
 *
 *   GET    /api/node/business/quick-replies       – list quick replies
 *   POST   /api/node/business/quick-replies       – create quick reply
 *   PUT    /api/node/business/quick-replies/:id   – update quick reply
 *   DELETE /api/node/business/quick-replies/:id   – delete quick reply
 *
 *   GET    /api/node/business/links               – list business links
 *   POST   /api/node/business/links               – create business link
 *   PUT    /api/node/business/links/:id           – update business link
 *   DELETE /api/node/business/links/:id           – delete business link
 *
 *   GET    /api/node/business/users/:userId       – get public business profile for a user
 *   GET    /api/node/business/links/:slug/open    – resolve a business link (track + return prefilled)
 */

const crypto = require('crypto');

// ─── Auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body?.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid token' });
        req.userId = session.user_id;
        next();
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function now() {
    return Math.floor(Date.now() / 1000);
}

/**
 * Returns default 7-day working schedule (Mon–Fri open, Sat–Sun closed).
 */
function defaultHours() {
    return Array.from({ length: 7 }, (_, i) => ({
        weekday:    i,
        is_open:    i >= 1 && i <= 5 ? 1 : 0,
        open_time:  '09:00',
        close_time: '18:00',
    }));
}

/**
 * Check whether the current UTC time falls within the user's working hours.
 */
async function isInsideWorkingHours(ctx, userId) {
    const utcDate   = new Date();
    const weekday   = utcDate.getUTCDay(); // 0=Sun … 6=Sat
    const row       = await ctx.wm_business_hours.findOne({ where: { user_id: userId, weekday }, raw: true });
    if (!row || !row.is_open) return false;

    const [oh, om] = row.open_time.split(':').map(Number);
    const [ch, cm] = row.close_time.split(':').map(Number);
    const curMin   = utcDate.getUTCHours() * 60 + utcDate.getUTCMinutes();
    const openMin  = oh * 60 + om;
    const closeMin = ch * 60 + cm;
    return curMin >= openMin && curMin < closeMin;
}

/**
 * Determine if auto-reply should fire and return the reply text (or null).
 */
async function getAutoReplyText(ctx, recipientId) {
    const profile = await ctx.wm_business_profile.findOne({
        where: { user_id: recipientId },
        raw:   true,
    });
    if (!profile || !profile.auto_reply_enabled) return null;

    const mode = profile.auto_reply_mode;

    if (mode === 'always') {
        return profile.auto_reply_text || null;
    }

    if (mode === 'away') {
        return profile.away_enabled ? (profile.away_text || null) : null;
    }

    if (mode === 'outside_hours') {
        const inside = await isInsideWorkingHours(ctx, recipientId);
        if (!inside) return profile.auto_reply_text || null;
    }

    return null;
}

/**
 * Get greeting message for a new contact (fired when first message in conversation).
 */
async function getGreetingText(ctx, recipientId) {
    const profile = await ctx.wm_business_profile.findOne({
        where: { user_id: recipientId },
        raw:   true,
    });
    if (!profile || !profile.greeting_enabled) return null;
    return profile.greeting_text || null;
}

// ─── Public helper for auto-reply (called from messages.js) ──────────────────

/**
 * Called non-blocking after a private message is saved.
 * Fires greeting (first message) or auto-reply and delivers via Socket.IO.
 */
async function handleBusinessAutoReply(ctx, io, { senderId, recipientId, isFirstMessage, isBusinessChat = 1 }) {
    try {
        let replyText = null;

        if (isFirstMessage) {
            replyText = await getGreetingText(ctx, recipientId);
        }

        // Auto-reply takes precedence unless it's a greeting-only scenario
        if (!replyText) {
            replyText = await getAutoReplyText(ctx, recipientId);
        }

        if (!replyText) return;

        const ts = now();

        const autoRow = await ctx.wo_messages.create({
            from_id:          recipientId,
            to_id:            senderId,
            text:             replyText,
            text_ecb:         '',
            text_preview:     replyText.slice(0, 100),
            iv:               null,
            tag:              null,
            cipher_version:   1,
            signal_header:    null,
            stickers:         '',
            media:            '',
            mediaFileName:    '',
            time:             ts,
            seen:             0,
            reply_id:         0,
            story_id:         0,
            lat:              '0',
            lng:              '0',
            page_id:          0,
            is_business_chat: isBusinessChat,
            type_two:         'business_auto_reply',
            forward:          0,
            edited:           0,
            remove_at:        0,
        });

        const msgData = { ...(autoRow.toJSON ? autoRow.toJSON() : autoRow), is_business_chat: isBusinessChat };

        io.to(String(senderId)).emit('private_message', msgData);
        io.to(String(senderId)).emit('new_message',     { ...msgData, self: false });

        // Update conversation metadata in the correct table
        if (isBusinessChat === 1 && ctx.wm_business_chats) {
            const funcs = require('../functions/functions');
            await funcs.updateOrCreate(ctx.wm_business_chats,
                { user_id: recipientId, business_user_id: senderId },
                { user_id: recipientId, business_user_id: senderId, last_message_id: autoRow.id, last_time: ts });
            await funcs.updateOrCreate(ctx.wm_business_chats,
                { user_id: senderId, business_user_id: recipientId },
                { user_id: senderId, business_user_id: recipientId, last_message_id: autoRow.id, last_time: ts });
        } else if (ctx.wo_userschat) {
            await ctx.wo_userschat.upsert({ user_id: recipientId, conversation_user_id: senderId,    time: ts });
            await ctx.wo_userschat.upsert({ user_id: senderId,    conversation_user_id: recipientId, time: ts });
        }
    } catch (e) {
        console.error('[BusinessAutoReply] error:', e.message);
    }
}

// ─── Route registrar ──────────────────────────────────────────────────────────

function registerBusinessRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // ── Profile ──────────────────────────────────────────────────────────────

    app.get('/api/node/business/profile', auth, async (req, res) => {
        try {
            const profile = await ctx.wm_business_profile.findOne({
                where: { user_id: req.userId },
                raw:   true,
            });
            if (!profile) {
                return res.json({ api_status: 200, profile: null });
            }

            const hours = await ctx.wm_business_hours.findAll({
                where:   { user_id: req.userId },
                order:   [['weekday', 'ASC']],
                raw:     true,
            });

            res.json({ api_status: 200, profile, hours });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.put('/api/node/business/profile', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const {
                business_name, category, description, address, lat, lng,
                phone, email, website,
                auto_reply_enabled, auto_reply_text, auto_reply_mode,
                greeting_enabled,   greeting_text,
                away_enabled,       away_text,
                badge_enabled,
            } = req.body;

            const ts = now();

            const [profile, created] = await ctx.wm_business_profile.upsert({
                user_id:            userId,
                business_name:      business_name  || null,
                category:           category        || null,
                description:        description     || null,
                address:            address         || null,
                lat:                lat             || null,
                lng:                lng             || null,
                phone:              phone           || null,
                email:              email           || null,
                website:            website         || null,
                auto_reply_enabled: auto_reply_enabled ? 1 : 0,
                auto_reply_text:    auto_reply_text || null,
                auto_reply_mode:    ['always', 'outside_hours', 'away'].includes(auto_reply_mode)
                                        ? auto_reply_mode : 'always',
                greeting_enabled:   greeting_enabled ? 1 : 0,
                greeting_text:      greeting_text   || null,
                away_enabled:       away_enabled    ? 1 : 0,
                away_text:          away_text       || null,
                badge_enabled:      badge_enabled !== undefined ? (badge_enabled ? 1 : 0) : 1,
                updated_at:         ts,
                created_at:         ts,
            });

            const saved = await ctx.wm_business_profile.findOne({ where: { user_id: userId }, raw: true });
            res.json({ api_status: 200, profile: saved });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.delete('/api/node/business/profile', auth, async (req, res) => {
        try {
            await ctx.wm_business_profile.destroy({ where: { user_id: req.userId } });
            await ctx.wm_business_hours.destroy({ where: { user_id: req.userId } });
            res.json({ api_status: 200 });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Working hours ─────────────────────────────────────────────────────────

    app.get('/api/node/business/hours', auth, async (req, res) => {
        try {
            const rows = await ctx.wm_business_hours.findAll({
                where: { user_id: req.userId },
                order: [['weekday', 'ASC']],
                raw:   true,
            });

            // Fill in any missing weekdays with defaults
            const map = {};
            rows.forEach(r => { map[r.weekday] = r; });
            const def = defaultHours();
            const result = def.map(d => map[d.weekday] || { ...d, user_id: req.userId });

            res.json({ api_status: 200, hours: result });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.put('/api/node/business/hours', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const { hours } = req.body; // array of { weekday, is_open, open_time, close_time }
            if (!Array.isArray(hours) || hours.length === 0) {
                return res.status(400).json({ api_status: 400, error_message: 'hours array required' });
            }

            for (const h of hours) {
                const weekday = parseInt(h.weekday);
                if (isNaN(weekday) || weekday < 0 || weekday > 6) continue;
                await ctx.wm_business_hours.upsert({
                    user_id:    userId,
                    weekday,
                    is_open:    h.is_open    ? 1 : 0,
                    open_time:  h.open_time  || '09:00',
                    close_time: h.close_time || '18:00',
                });
            }

            const saved = await ctx.wm_business_hours.findAll({
                where: { user_id: userId },
                order: [['weekday', 'ASC']],
                raw:   true,
            });
            res.json({ api_status: 200, hours: saved });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Quick replies ─────────────────────────────────────────────────────────

    app.get('/api/node/business/quick-replies', auth, async (req, res) => {
        try {
            const rows = await ctx.wm_business_quick_replies.findAll({
                where:   { user_id: req.userId },
                order:   [['shortcut', 'ASC']],
                raw:     true,
            });
            res.json({ api_status: 200, quick_replies: rows });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.post('/api/node/business/quick-replies', auth, async (req, res) => {
        try {
            const { shortcut, text, media_url } = req.body;
            if (!shortcut || !text) {
                return res.status(400).json({ api_status: 400, error_message: 'shortcut and text required' });
            }
            const ts = now();
            const row = await ctx.wm_business_quick_replies.create({
                user_id:    req.userId,
                shortcut:   shortcut.replace(/^\//, '').trim().toLowerCase(),
                text:       text.trim(),
                media_url:  media_url || null,
                created_at: ts,
                updated_at: ts,
            });
            res.json({ api_status: 200, quick_reply: row.toJSON ? row.toJSON() : row });
        } catch (e) {
            if (e.name === 'SequelizeUniqueConstraintError') {
                return res.status(409).json({ api_status: 409, error_message: 'Shortcut already exists' });
            }
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.put('/api/node/business/quick-replies/:id', auth, async (req, res) => {
        try {
            const id  = parseInt(req.params.id);
            const row = await ctx.wm_business_quick_replies.findOne({ where: { id, user_id: req.userId } });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Not found' });

            const { shortcut, text, media_url } = req.body;
            await row.update({
                shortcut:   shortcut ? shortcut.replace(/^\//, '').trim().toLowerCase() : row.shortcut,
                text:       text     ? text.trim()                                       : row.text,
                media_url:  media_url !== undefined ? (media_url || null)               : row.media_url,
                updated_at: now(),
            });
            res.json({ api_status: 200, quick_reply: row.toJSON ? row.toJSON() : row });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.delete('/api/node/business/quick-replies/:id', auth, async (req, res) => {
        try {
            const id = parseInt(req.params.id);
            const deleted = await ctx.wm_business_quick_replies.destroy({ where: { id, user_id: req.userId } });
            if (!deleted) return res.status(404).json({ api_status: 404, error_message: 'Not found' });
            res.json({ api_status: 200 });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Business links ────────────────────────────────────────────────────────

    app.get('/api/node/business/links', auth, async (req, res) => {
        try {
            const rows = await ctx.wm_business_links.findAll({
                where:   { user_id: req.userId },
                order:   [['created_at', 'DESC']],
                raw:     true,
            });
            const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
            const result  = rows.map(r => ({
                ...r,
                url: `${siteUrl}/biz/${r.slug}`,
            }));
            res.json({ api_status: 200, links: result });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.post('/api/node/business/links', auth, async (req, res) => {
        try {
            const { title, prefilled_text } = req.body;
            if (!title) return res.status(400).json({ api_status: 400, error_message: 'title required' });

            const slug = crypto.randomBytes(6).toString('hex');
            const ts   = now();
            const row  = await ctx.wm_business_links.create({
                user_id:        req.userId,
                title:          title.trim(),
                prefilled_text: prefilled_text || null,
                slug,
                views:      0,
                created_at: ts,
            });

            const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
            const data    = row.toJSON ? row.toJSON() : row;
            res.json({ api_status: 200, link: { ...data, url: `${siteUrl}/biz/${slug}` } });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.put('/api/node/business/links/:id', auth, async (req, res) => {
        try {
            const id  = parseInt(req.params.id);
            const row = await ctx.wm_business_links.findOne({ where: { id, user_id: req.userId } });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Not found' });

            const { title, prefilled_text } = req.body;
            await row.update({
                title:          title          ? title.trim()       : row.title,
                prefilled_text: prefilled_text !== undefined ? (prefilled_text || null) : row.prefilled_text,
            });

            const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
            const data    = row.toJSON ? row.toJSON() : row;
            res.json({ api_status: 200, link: { ...data, url: `${siteUrl}/biz/${data.slug}` } });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.delete('/api/node/business/links/:id', auth, async (req, res) => {
        try {
            const id      = parseInt(req.params.id);
            const deleted = await ctx.wm_business_links.destroy({ where: { id, user_id: req.userId } });
            if (!deleted) return res.status(404).json({ api_status: 404, error_message: 'Not found' });
            res.json({ api_status: 200 });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Public business profile (visible to anyone) ───────────────────────────

    app.get('/api/node/business/users/:userId', auth, async (req, res) => {
        try {
            const targetId = parseInt(req.params.userId);
            const profile  = await ctx.wm_business_profile.findOne({
                where: { user_id: targetId },
                raw:   true,
            });
            if (!profile) return res.json({ api_status: 200, profile: null });

            const hours = await ctx.wm_business_hours.findAll({
                where: { user_id: targetId },
                order: [['weekday', 'ASC']],
                raw:   true,
            });

            // Strip private fields from public view
            const {
                auto_reply_enabled, auto_reply_text, auto_reply_mode,
                greeting_enabled,   greeting_text,
                away_enabled,       away_text,
                ...publicProfile
            } = profile;

            // Track profile view in stats (non-blocking, don't count self-views)
            if (req.userId !== targetId) {
                const today = new Date().toISOString().slice(0, 10);
                ctx.sequelize.query(
                    `INSERT INTO wm_business_stats (user_id, date, profile_views)
                     VALUES (?, ?, 1)
                     ON DUPLICATE KEY UPDATE profile_views = profile_views + 1`,
                    { replacements: [targetId, today] }
                ).catch(() => {});
            }

            res.json({ api_status: 200, profile: publicProfile, hours });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Business link open (track views + return prefilled text) ─────────────

    app.get('/api/node/business/links/:slug/open', async (req, res) => {
        try {
            const { slug } = req.params;
            const row = await ctx.wm_business_links.findOne({ where: { slug }, raw: false });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Link not found' });

            await row.increment('views', { by: 1 });

            // Track link_clicks in stats
            const today = new Date().toISOString().slice(0, 10);
            await ctx.sequelize.query(
                `INSERT INTO wm_business_stats (user_id, date, link_clicks)
                 VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE link_clicks = link_clicks + 1`,
                { replacements: [row.user_id, today] }
            );

            res.json({
                api_status:     200,
                user_id:        row.user_id,
                title:          row.title,
                prefilled_text: row.prefilled_text,
            });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Business stats (reach analytics) ─────────────────────────────────────

    app.get('/api/node/business/stats', auth, async (req, res) => {
        try {
            const days = Math.min(parseInt(req.query.days || '30', 10), 90);
            const [rows] = await ctx.sequelize.query(
                `SELECT date, profile_views, messages_received, link_clicks
                 FROM wm_business_stats
                 WHERE user_id = ? AND date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                 ORDER BY date ASC`,
                { replacements: [req.userId, days] }
            );

            const totals = rows.reduce((acc, r) => ({
                profile_views:     acc.profile_views     + (r.profile_views     || 0),
                messages_received: acc.messages_received + (r.messages_received || 0),
                link_clicks:       acc.link_clicks       + (r.link_clicks       || 0),
            }), { profile_views: 0, messages_received: 0, link_clicks: 0 });

            res.json({ api_status: 200, days, totals, daily: rows });
        } catch (e) {
            console.error('[Business] stats error:', e.message);
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Verification request ───────────────────────────────────────────────────

    app.post('/api/node/business/request-verification', auth, async (req, res) => {
        try {
            const profile = await ctx.wm_business_profile.findOne({ where: { user_id: req.userId } });
            if (!profile) {
                return res.json({ api_status: 400, error_message: 'Set up your business profile first' });
            }
            if (profile.verification_status === 'approved') {
                return res.json({ api_status: 200, verification_status: 'approved', message: 'Already verified' });
            }
            if (profile.verification_status === 'pending') {
                return res.json({ api_status: 200, verification_status: 'pending', message: 'Request already submitted' });
            }
            await profile.update({ verification_status: 'pending', verification_note: null });
            res.json({ api_status: 200, verification_status: 'pending' });
        } catch (e) {
            console.error('[Business] request-verification error:', e.message);
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── API key management ────────────────────────────────────────────────────

    app.get('/api/node/business/api-key', auth, async (req, res) => {
        try {
            const [rows] = await ctx.sequelize.query(
                'SELECT id, api_key, label, last_used_at, created_at FROM wm_business_api_keys WHERE user_id = ?',
                { replacements: [req.userId] }
            );
            res.json({ api_status: 200, api_key: rows[0] || null });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.post('/api/node/business/api-key', auth, async (req, res) => {
        try {
            // Revoke old key if exists
            await ctx.sequelize.query(
                'DELETE FROM wm_business_api_keys WHERE user_id = ?',
                { replacements: [req.userId] }
            );
            const label  = String(req.body.label || 'My API Key').slice(0, 128);
            const newKey = 'wmk_' + crypto.randomBytes(28).toString('hex');
            await ctx.sequelize.query(
                'INSERT INTO wm_business_api_keys (user_id, api_key, label) VALUES (?, ?, ?)',
                { replacements: [req.userId, newKey, label] }
            );
            res.json({ api_status: 200, api_key: { api_key: newKey, label, last_used_at: null } });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    app.delete('/api/node/business/api-key', auth, async (req, res) => {
        try {
            await ctx.sequelize.query(
                'DELETE FROM wm_business_api_keys WHERE user_id = ?',
                { replacements: [req.userId] }
            );
            res.json({ api_status: 200 });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Data export (via API key auth) ────────────────────────────────────────
    // Accessible with: Authorization: Bearer wmk_xxx  OR  access-token header

    app.get('/api/node/business/export', auth, async (req, res) => {
        try {
            const [profile] = await Promise.all([ctx.wm_business_profile.findOne({ where: { user_id: req.userId }, raw: true })]);
            const [hours]   = await ctx.sequelize.query('SELECT * FROM wm_business_hours WHERE user_id = ?', { replacements: [req.userId] });
            const [links]   = await ctx.sequelize.query('SELECT * FROM wm_business_links WHERE user_id = ?', { replacements: [req.userId] });
            const [stats]   = await ctx.sequelize.query(
                'SELECT * FROM wm_business_stats WHERE user_id = ? ORDER BY date DESC LIMIT 90',
                { replacements: [req.userId] }
            );
            res.json({ api_status: 200, exported_at: new Date().toISOString(), profile, hours, links, stats });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });
}

module.exports = { registerBusinessRoutes, handleBusinessAutoReply };
