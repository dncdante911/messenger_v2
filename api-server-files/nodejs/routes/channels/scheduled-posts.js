'use strict';

/**
 * Channel Scheduled Posts API
 *
 * Endpoints:
 *   GET    /api/node/channels/:id/posts/scheduled        — list scheduled posts (owner/admin)
 *   POST   /api/node/channels/:id/posts/scheduled        — create scheduled post
 *   DELETE /api/node/channels/:id/posts/scheduled/:postId — cancel scheduled post
 *
 * Background publishing (runs every 60s from cronJobs.js or inline):
 *   Checks wm_channel_scheduled_posts WHERE status='pending' AND scheduled_at <= NOW()
 *   Calls internal publish logic and marks status='published'
 */

// ─── Auth + channel-admin check ───────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token'] || req.query.access_token || req.body?.access_token;
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

/** Checks that user is owner or admin of the channel. */
async function requireChannelAdmin(ctx, req, res, next) {
    const channelId = parseInt(req.params.id, 10);
    if (!channelId) return res.status(400).json({ api_status: 400, error_message: 'Invalid channel_id' });

    try {
        const [rows] = await ctx.sequelize.query(
            `SELECT p.uid, p.owner, l.role AS user_role
             FROM wo_pages p
             LEFT JOIN wo_pages_likes l ON l.page_id = p.uid AND l.user_id = ? AND l.role IN ('admin','owner')
             WHERE p.uid = ?`,
            { replacements: [req.userId, channelId] }
        );
        const page = rows[0];
        if (!page) return res.status(404).json({ api_status: 404, error_message: 'Channel not found' });

        const isOwner = String(page.owner) === String(req.userId);
        const isAdmin = !!rows[0]?.user_role;
        if (!isOwner && !isAdmin) {
            return res.status(403).json({ api_status: 403, error_message: 'Only channel admins can manage scheduled posts' });
        }
        req.channelId = channelId;
        next();
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

// ─── Publish a single scheduled post (called by cron) ────────────────────────
async function publishScheduledPost(ctx, post) {
    try {
        const now = Math.floor(Date.now() / 1000);
        const [result] = await ctx.sequelize.query(
            `INSERT INTO wo_posts
             (page_uid, author_id, post_text, media_url, media_type, is_pinned, created_time, updated_time)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            { replacements: [
                post.channel_id, post.author_id,
                post.text || '', post.media_url || null, post.media_type || null,
                post.is_pinned ? 1 : 0, now, now,
            ] }
        );
        const postId = result.insertId || result;

        await ctx.sequelize.query(
            'UPDATE wm_channel_scheduled_posts SET status = ?, published_post_id = ? WHERE id = ?',
            { replacements: ['published', postId, post.id] }
        );

        console.info(`[ScheduledPosts] Published post ${post.id} → channel ${post.channel_id}, new post_id=${postId}`);
        return postId;
    } catch (e) {
        console.error(`[ScheduledPosts] Failed to publish post ${post.id}:`, e.message);
    }
}

// ─── Cron tick — called from cronJobs.js or startup ──────────────────────────
async function publishDuePosts(ctx) {
    try {
        const [due] = await ctx.sequelize.query(
            `SELECT * FROM wm_channel_scheduled_posts
             WHERE status = 'pending' AND scheduled_at <= NOW()
             ORDER BY scheduled_at ASC LIMIT 50`
        );
        for (const post of due) {
            await publishScheduledPost(ctx, post);
        }
    } catch (e) {
        console.error('[ScheduledPosts] cron error:', e.message);
    }
}

// ─── Route handlers ───────────────────────────────────────────────────────────

/** GET /api/node/channels/:id/posts/scheduled */
async function listScheduled(ctx, req, res) {
    try {
        const [rows] = await ctx.sequelize.query(
            `SELECT sp.*, u.fname AS author_name, u.avatar AS author_avatar
             FROM wm_channel_scheduled_posts sp
             LEFT JOIN wo_users u ON u.uid = sp.author_id
             WHERE sp.channel_id = ? AND sp.status = 'pending'
             ORDER BY sp.scheduled_at ASC`,
            { replacements: [req.channelId] }
        );
        res.json({ api_status: 200, scheduled_posts: rows });
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

/** POST /api/node/channels/:id/posts/scheduled */
async function createScheduled(ctx, req, res) {
    try {
        const { text, media_url, media_type, scheduled_at, is_pinned } = req.body;

        if (!text && !media_url) {
            return res.status(400).json({ api_status: 400, error_message: 'text or media_url required' });
        }
        if (!scheduled_at) {
            return res.status(400).json({ api_status: 400, error_message: 'scheduled_at required (ISO 8601 or MySQL datetime)' });
        }

        const scheduledDate = new Date(scheduled_at);
        if (isNaN(scheduledDate.getTime()) || scheduledDate <= new Date()) {
            return res.status(400).json({ api_status: 400, error_message: 'scheduled_at must be a future datetime' });
        }

        const [result] = await ctx.sequelize.query(
            `INSERT INTO wm_channel_scheduled_posts
             (channel_id, author_id, text, media_url, media_type, scheduled_at, is_pinned)
             VALUES (?, ?, ?, ?, ?, ?, ?)`,
            { replacements: [
                req.channelId, req.userId,
                text || null, media_url || null, media_type || null,
                scheduledDate.toISOString().slice(0, 19).replace('T', ' '),
                is_pinned ? 1 : 0,
            ] }
        );
        const insertId = result.insertId || result;
        const [rows] = await ctx.sequelize.query(
            'SELECT * FROM wm_channel_scheduled_posts WHERE id = ?',
            { replacements: [insertId] }
        );
        res.json({ api_status: 200, scheduled_post: rows[0] });
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

/** DELETE /api/node/channels/:id/posts/scheduled/:postId */
async function cancelScheduled(ctx, req, res) {
    try {
        const postId = parseInt(req.params.postId, 10);
        const deleted = await ctx.sequelize.query(
            `UPDATE wm_channel_scheduled_posts SET status = 'cancelled'
             WHERE id = ? AND channel_id = ? AND status = 'pending'`,
            { replacements: [postId, req.channelId] }
        );
        res.json({ api_status: 200 });
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

// ─── Registration ─────────────────────────────────────────────────────────────
function registerChannelScheduledPostRoutes(app, ctx) {
    const auth  = (req, res, next) => authMiddleware(ctx, req, res, next);
    const admin = (req, res, next) => requireChannelAdmin(ctx, req, res, next);

    app.get   ('/api/node/channels/:id/posts/scheduled',          auth, admin, (req, res) => listScheduled(ctx, req, res));
    app.post  ('/api/node/channels/:id/posts/scheduled',          auth, admin, (req, res) => createScheduled(ctx, req, res));
    app.delete('/api/node/channels/:id/posts/scheduled/:postId',  auth, admin, (req, res) => cancelScheduled(ctx, req, res));

    // Start cron ticker (every 60s)
    setInterval(function() { publishDuePosts(ctx); }, 60000);
}

module.exports = { registerChannelScheduledPostRoutes, publishDuePosts };
