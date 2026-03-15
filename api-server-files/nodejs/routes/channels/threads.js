'use strict';

/**
 * Channel Post Threads (Discussion)
 *
 * Threads = discussion attached to a channel post (like Telegram comments on posts).
 * Thread messages are stored as channel comments but with a richer structure.
 *
 * Endpoints (registered via channels/index.js):
 *   GET  /api/node/channel/post/:postId/thread          – get thread messages
 *   POST /api/node/channel/post/:postId/thread/send     – post a thread message
 *   POST /api/node/channel/post/:postId/thread/reply    – reply to specific thread message
 *   DELETE /api/node/channel/post/:postId/thread/:msgId – delete thread message
 *   GET  /api/node/channel/post/:postId/thread/count    – get reply count (for post list)
 */

const { Op } = require('sequelize');

// ─── helpers ─────────────────────────────────────────────────────────────────

async function getUser(ctx, userId) {
    try {
        const u = await ctx.wo_users.findOne({
            attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
            where: { user_id: userId },
            raw: true,
        });
        if (!u) return null;
        const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
        const avatar = u.avatar && !u.avatar.startsWith('http')
            ? `${siteUrl}/${u.avatar}`
            : (u.avatar || '');
        return {
            user_id:  u.user_id,
            username: u.username || '',
            name:     [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username,
            avatar,
        };
    } catch { return null; }
}

async function canDeleteThread(ctx, userId, channelId, commentId) {
    const comment = await ctx.wo_channel_comments?.findOne({
        where: { id: commentId },
        raw: true,
    }).catch(() => null);
    if (!comment) return false;
    if (comment.user_id === userId) return true;
    // Check if channel admin
    const admin = await ctx.wo_pageadmins?.findOne({
        where: { page_id: channelId, user_id: userId },
        raw: true,
    }).catch(() => null);
    return !!admin;
}

// ─── GET thread messages ──────────────────────────────────────────────────────

async function getThreadMessages(ctx, req, res) {
    try {
        const postId = parseInt(req.params.postId);
        const limit  = Math.min(parseInt(req.query.limit  || req.body?.limit)  || 50, 100);
        const offset = parseInt(req.query.offset || req.body?.offset) || 0;

        // Use wo_channel_comments as thread storage (field: post_id)
        const rows = await ctx.sequelize.query(
            `SELECT c.*,
                    u.username, u.first_name, u.last_name, u.avatar,
                    c.reply_to_id
             FROM wo_channel_comments c
             LEFT JOIN wo_users u ON u.user_id = c.user_id
             WHERE c.post_id = :postId
             ORDER BY c.time ASC
             LIMIT :limit OFFSET :offset`,
            {
                replacements: { postId, limit, offset },
                type: ctx.sequelize.QueryTypes.SELECT,
            }
        );

        const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');

        const messages = rows.map(r => {
            const avatar = r.avatar && !r.avatar.startsWith('http')
                ? `${siteUrl}/${r.avatar}` : (r.avatar || '');
            return {
                id:           r.id,
                post_id:      r.post_id,
                user_id:      r.user_id,
                text:         r.text || '',
                time:         r.time,
                reply_to_id:  r.reply_to_id || null,
                author: {
                    user_id:  r.user_id,
                    name:     [r.first_name, r.last_name].filter(Boolean).join(' ') || r.username,
                    username: r.username || '',
                    avatar,
                },
            };
        });

        // Total count
        const [[{ total }]] = await ctx.sequelize.query(
            'SELECT COUNT(*) AS total FROM wo_channel_comments WHERE post_id = :postId',
            { replacements: { postId }, type: ctx.sequelize.QueryTypes.SELECT }
        );

        res.json({ api_status: 200, messages, total: Number(total), offset, limit });
    } catch (err) {
        console.error('[Thread/get]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── GET thread message count ─────────────────────────────────────────────────

async function getThreadCount(ctx, req, res) {
    try {
        const postId = parseInt(req.params.postId);
        const [[{ count }]] = await ctx.sequelize.query(
            'SELECT COUNT(*) AS count FROM wo_channel_comments WHERE post_id = :postId',
            { replacements: { postId }, type: ctx.sequelize.QueryTypes.SELECT }
        );
        res.json({ api_status: 200, count: Number(count), post_id: postId });
    } catch (err) {
        console.error('[Thread/count]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST send thread message ─────────────────────────────────────────────────

async function sendThreadMessage(ctx, io, req, res) {
    try {
        const userId    = req.userId;
        const postId    = parseInt(req.params.postId);
        const text      = (req.body.text || '').trim();
        const replyToId = parseInt(req.body.reply_to_id) || null;

        if (!text) return res.status(400).json({ api_status: 400, error_message: 'text required' });

        const now = Math.floor(Date.now() / 1000);

        // Verify post exists and get channel_id
        const [[post]] = await ctx.sequelize.query(
            'SELECT id, page_id FROM wo_channel_posts WHERE id = :postId',
            { replacements: { postId }, type: ctx.sequelize.QueryTypes.SELECT }
        ).catch(() => [[null]]);

        if (!post) return res.status(404).json({ api_status: 404, error_message: 'Post not found' });

        const channelId = post.page_id;

        // Insert thread message (using wo_channel_comments)
        const [result] = await ctx.sequelize.query(
            `INSERT INTO wo_channel_comments (post_id, user_id, text, time, reply_to_id)
             VALUES (:postId, :userId, :text, :time, :replyToId)`,
            {
                replacements: { postId, userId, text, time: now, replyToId },
                type: ctx.sequelize.QueryTypes.INSERT,
            }
        );

        const commentId = result;
        const author = await getUser(ctx, userId);

        const message = {
            id:          commentId,
            post_id:     postId,
            channel_id:  channelId,
            user_id:     userId,
            text,
            time:        now,
            reply_to_id: replyToId,
            author,
        };

        // Broadcast to channel room
        if (io) {
            io.to(`channel_${channelId}`).emit('channel_thread_message', message);
        }

        res.json({ api_status: 200, message });
    } catch (err) {
        console.error('[Thread/send]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── DELETE thread message ────────────────────────────────────────────────────

async function deleteThreadMessage(ctx, io, req, res) {
    try {
        const userId  = req.userId;
        const postId  = parseInt(req.params.postId);
        const msgId   = parseInt(req.params.msgId);

        // Get post to find channel
        const [[post]] = await ctx.sequelize.query(
            'SELECT id, page_id FROM wo_channel_posts WHERE id = :postId',
            { replacements: { postId }, type: ctx.sequelize.QueryTypes.SELECT }
        ).catch(() => [[null]]);

        if (!post) return res.status(404).json({ api_status: 404, error_message: 'Post not found' });

        const allowed = await canDeleteThread(ctx, userId, post.page_id, msgId);
        if (!allowed) return res.status(403).json({ api_status: 403, error_message: 'Not allowed' });

        await ctx.sequelize.query(
            'DELETE FROM wo_channel_comments WHERE id = :msgId AND post_id = :postId',
            { replacements: { msgId, postId }, type: ctx.sequelize.QueryTypes.DELETE }
        );

        if (io) {
            io.to(`channel_${post.page_id}`).emit('channel_thread_deleted', { post_id: postId, message_id: msgId });
        }

        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Thread/delete]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── Batch counts for a list of post IDs ─────────────────────────────────────
// POST /api/node/channel/threads/counts  body: { post_ids: [1,2,3] }

async function batchCounts(ctx, req, res) {
    try {
        const postIds = Array.isArray(req.body.post_ids)
            ? req.body.post_ids.map(Number).filter(n => n > 0)
            : [];

        if (!postIds.length) return res.json({ api_status: 200, counts: {} });

        const rows = await ctx.sequelize.query(
            `SELECT post_id, COUNT(*) AS cnt
             FROM wo_channel_comments
             WHERE post_id IN (:postIds)
             GROUP BY post_id`,
            { replacements: { postIds }, type: ctx.sequelize.QueryTypes.SELECT }
        );

        const counts = {};
        for (const r of rows) counts[r.post_id] = Number(r.cnt);
        res.json({ api_status: 200, counts });
    } catch (err) {
        console.error('[Thread/batch-counts]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── Export handlers ──────────────────────────────────────────────────────────

module.exports = {
    getThreadMessages,
    getThreadCount,
    sendThreadMessage,
    deleteThreadMessage,
    batchCounts,
};
