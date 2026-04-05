'use strict';

/**
 * Channel Backup — Export
 *
 * POST /api/node/channel/backup/export
 *   Returns a JSON export of all channel posts with metadata.
 *   Admin/owner only.
 *
 * Response JSON shape:
 * {
 *   api_status: 200,
 *   channel_id: 123,
 *   channel_name: "...",
 *   exported_at: 1714000000,
 *   posts_count: 47,
 *   posts: [
 *     {
 *       id, text, media_url, media_type, time,
 *       views, reactions_count, comments_count, is_pinned
 *     }, ...
 *   ]
 * }
 */

const { Op, Sequelize } = require('sequelize');

async function isChannelAdmin(ctx, channelId, userId) {
    // Check ownership first
    const page = await ctx.wo_pages.findOne({
        where:      { page_id: channelId },
        attributes: ['user_id'],
        raw:        true,
    });
    if (page && String(page.user_id) === String(userId)) return true;

    // Check admin list (stored as JSON in page settings or a separate table)
    try {
        const adminEntry = await ctx.wo_page_admins?.findOne?.({
            where: { page_id: channelId, user_id: userId, active: '1' },
            raw:   true,
        });
        if (adminEntry) return true;
    } catch (_) { /* table may not exist — fall through */ }

    return false;
}

// ─── exportChannel ─────────────────────────────────────────────────────────────

function exportChannel(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId || isNaN(channelId))
                return res.json({ api_status: 400, error_message: 'channel_id required' });

            if (!await isChannelAdmin(ctx, channelId, userId))
                return res.json({ api_status: 403, error_message: 'Not an admin' });

            // Channel metadata
            const channel = await ctx.wo_pages.findOne({
                where:      { page_id: channelId },
                attributes: ['page_id', 'page_name', 'page_description', 'page_cover'],
                raw:        true,
            });
            if (!channel)
                return res.json({ api_status: 404, error_message: 'Channel not found' });

            // Fetch all active posts (in chronological order)
            const posts = await ctx.wo_posts.findAll({
                where:   { page_id: channelId, active: 1 },
                order:   [['id', 'ASC']],
                attributes: [
                    'id', 'postText', 'postFile', 'media_type',
                    'videoViews', 'time', 'is_page_notify',
                ],
                raw: true,
            });

            const postIds = posts.map(p => p.id);

            // Reactions counts per post
            const reactByPost = {};
            try {
                const rRows = await ctx.wo_post_reactions?.findAll?.({
                    where:      { post_id: { [Op.in]: postIds } },
                    attributes: ['post_id', [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt']],
                    group:      ['post_id'],
                    raw:        true,
                });
                (rRows || []).forEach(r => { reactByPost[r.post_id] = parseInt(r.cnt); });
            } catch (_) { /* wo_post_reactions optional */ }

            // Comment counts per post
            const commentByPost = {};
            try {
                const cRows = await ctx.wo_comments.findAll({
                    where:      { post_id: { [Op.in]: postIds } },
                    attributes: ['post_id', [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt']],
                    group:      ['post_id'],
                    raw:        true,
                });
                cRows.forEach(c => { commentByPost[c.post_id] = parseInt(c.cnt); });
            } catch (_) { /* ignore */ }

            // Pinned post IDs (from wo_page_pins or page settings)
            const pinnedIds = new Set();
            try {
                const pins = await ctx.wo_page_pins?.findAll?.({
                    where: { page_id: channelId },
                    attributes: ['post_id'],
                    raw: true,
                });
                (pins || []).forEach(p => pinnedIds.add(p.post_id));
            } catch (_) { /* optional */ }

            const exportedPosts = posts.map(p => ({
                id:              p.id,
                text:            p.postText || '',
                media_url:       p.postFile || null,
                media_type:      p.media_type || null,
                time:            p.time,
                views:           p.videoViews || 0,
                reactions_count: reactByPost[p.id] || 0,
                comments_count:  commentByPost[p.id] || 0,
                is_pinned:       pinnedIds.has(p.id),
            }));

            const now = Math.floor(Date.now() / 1000);

            // Set headers so Android can detect JSON response
            res.setHeader('Content-Type', 'application/json; charset=utf-8');
            res.setHeader(
                'Content-Disposition',
                `attachment; filename="channel_${channelId}_backup_${now}.json"`
            );

            return res.json({
                api_status:   200,
                channel_id:   channelId,
                channel_name: channel.page_name || '',
                exported_at:  now,
                posts_count:  exportedPosts.length,
                posts:        exportedPosts,
            });

        } catch (err) {
            console.error('[Channels/backup/export]', err.message);
            return res.json({ api_status: 500, error_message: 'Export failed' });
        }
    };
}

module.exports = { exportChannel };
