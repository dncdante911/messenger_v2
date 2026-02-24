/**
 * Channels — Post Reactions (add/remove)
 *
 * Endpoints handled:
 *   type=add_post_reaction    – add emoji reaction to post
 *   type=remove_post_reaction – remove emoji reaction from post
 */

'use strict';

const { Op } = require('sequelize');

// ─── add_post_reaction ──────────────────────────────────────────────────────

function addReaction(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);
            const reaction = (req.body.reaction || req.body.emoji || '').trim();

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            if (!reaction) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'reaction/emoji is required' });
            }

            // Verify post exists
            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            const now = Math.floor(Date.now() / 1000);

            // Check if user already reacted to this post with same emoji
            const existing = await ctx.wo_reactions.findOne({
                where: { post_id: postId, user_id: userId },
                raw: true
            });

            if (existing) {
                // Update to new reaction
                await ctx.wo_reactions.update(
                    { reaction: reaction, time: String(now) },
                    { where: { post_id: postId, user_id: userId } }
                );
            } else {
                // Create new reaction
                await ctx.wo_reactions.create({
                    user_id: userId,
                    post_id: postId,
                    comment_id: 0,
                    reply_id: 0,
                    message_id: 0,
                    reaction: reaction,
                    time: String(now)
                });
            }

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:post_reaction', {
                    postId: postId,
                    userId: userId,
                    emoji: reaction,
                    action: 'add'
                });
            }

            console.log(`[Channels] Reaction "${reaction}" added to post ${postId} by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/addReaction]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── remove_post_reaction ───────────────────────────────────────────────────

function removeReaction(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);
            const reaction = (req.body.reaction || req.body.emoji || '').trim();

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            // Verify post exists
            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            // Remove the reaction
            const whereClause = { post_id: postId, user_id: userId };
            if (reaction) {
                whereClause.reaction = reaction;
            }

            await ctx.wo_reactions.destroy({ where: whereClause });

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:post_reaction', {
                    postId: postId,
                    userId: userId,
                    emoji: reaction,
                    action: 'remove'
                });
            }

            console.log(`[Channels] Reaction removed from post ${postId} by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/removeReaction]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    addReaction,
    removeReaction
};
