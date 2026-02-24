/**
 * Channels — Comments (CRUD, comment reactions)
 *
 * Endpoints handled:
 *   type=get_comments        – list comments for a post
 *   type=add_comment         – add comment to post
 *   type=delete_comment      – delete a comment
 *   type=add_comment_reaction – add reaction to a comment
 */

'use strict';

const { Op, Sequelize } = require('sequelize');
const funcs = require('../../functions/functions');

// ─── helpers ────────────────────────────────────────────────────────────────

async function isChannelAdmin(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({
        where: { page_id: channelId },
        attributes: ['user_id'],
        raw: true
    });
    if (!page) return false;
    if (page.user_id === userId) return true;

    const admin = await ctx.wo_pageadmins.count({
        where: { page_id: channelId, user_id: userId }
    });
    return admin > 0;
}

async function formatComment(ctx, comment) {
    const user = await ctx.wo_users.findOne({
        where: { user_id: comment.user_id },
        attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
        raw: true
    });

    const userAvatar = user && user.avatar ? await funcs.Wo_GetMedia(ctx, user.avatar) : '';
    const userName = user
        ? (user.first_name ? (user.first_name + (user.last_name ? ' ' + user.last_name : '')) : user.username)
        : 'Unknown';

    // Count reactions on this comment
    const reactionsCount = await ctx.wo_reactions.count({
        where: { comment_id: comment.id }
    });

    return {
        id: comment.id,
        user_id: comment.user_id,
        username: user ? user.username : null,
        user_name: userName,
        user_avatar: userAvatar,
        text: comment.text || '',
        time: comment.time || 0,
        edited_time: null,
        reply_to_comment_id: null,
        reactions_count: reactionsCount
    };
}

// ─── get_comments ───────────────────────────────────────────────────────────

function getComments(ctx, io) {
    return async (req, res) => {
        try {
            const postId = parseInt(req.body.post_id);
            const limit = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

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

            const rawComments = await ctx.wo_comments.findAll({
                where: { post_id: postId },
                order: [['id', 'ASC']],
                limit,
                offset,
                raw: true
            });

            const totalCount = await ctx.wo_comments.count({
                where: { post_id: postId }
            });

            const comments = [];
            for (const rawComment of rawComments) {
                comments.push(await formatComment(ctx, rawComment));
            }

            return res.json({
                api_status: 200,
                comments: comments,
                total_count: totalCount,
                has_more: rawComments.length === limit,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getComments]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── add_comment ────────────────────────────────────────────────────────────

function addComment(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);
            const text = (req.body.text || '').trim();

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            if (!text) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Comment text is required' });
            }

            // Verify post exists
            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            // Check if comments are enabled
            if (post.comments_status === 0) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Comments are disabled for this post' });
            }

            const now = Math.floor(Date.now() / 1000);
            const sanitizedText = await funcs.sanitizeJS(text);

            const newComment = await ctx.wo_comments.create({
                user_id: userId,
                page_id: post.page_id || 0,
                post_id: postId,
                text: sanitizedText,
                time: now
            });

            const formatted = await formatComment(ctx, newComment);

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:comment_added', {
                    postId: postId,
                    comment: {
                        id: formatted.id,
                        userId: formatted.user_id,
                        username: formatted.username,
                        userAvatar: formatted.user_avatar,
                        text: formatted.text,
                        createdTime: formatted.time
                    }
                });
            }

            console.log(`[Channels] Comment added to post ${postId} by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/addComment]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── delete_comment ─────────────────────────────────────────────────────────

function deleteComment(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const commentId = parseInt(req.body.comment_id);

            if (!commentId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'comment_id is required' });
            }

            const comment = await ctx.wo_comments.findOne({
                where: { id: commentId },
                raw: true
            });

            if (!comment) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Comment not found' });
            }

            // Check permissions: comment author or channel admin
            const post = await ctx.wo_posts.findOne({
                where: { id: comment.post_id },
                raw: true
            });

            const channelId = post ? post.page_id : comment.page_id;

            if (comment.user_id !== userId && !await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to delete' });
            }

            // Delete comment reactions
            await ctx.wo_reactions.destroy({ where: { comment_id: commentId } });

            // Delete the comment
            await ctx.wo_comments.destroy({ where: { id: commentId } });

            // Broadcast via Socket.IO
            if (io && post) {
                const roomName = `channel_${channelId}`;
                io.to(roomName).emit('channel:comment_deleted', {
                    postId: comment.post_id,
                    commentId: commentId
                });
            }

            console.log(`[Channels] Comment ${commentId} deleted by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: comment.post_id,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/deleteComment]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── add_comment_reaction ───────────────────────────────────────────────────

function addCommentReaction(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const commentId = parseInt(req.body.comment_id);
            const reaction = (req.body.reaction || '').trim();

            if (!commentId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'comment_id is required' });
            }

            if (!reaction) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'reaction is required' });
            }

            // Verify comment exists
            const comment = await ctx.wo_comments.findOne({
                where: { id: commentId },
                raw: true
            });

            if (!comment) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Comment not found' });
            }

            const now = Math.floor(Date.now() / 1000);

            // Check if user already reacted to this comment
            const existing = await ctx.wo_reactions.findOne({
                where: { comment_id: commentId, user_id: userId },
                raw: true
            });

            if (existing) {
                // Update reaction
                await ctx.wo_reactions.update(
                    { reaction: reaction, time: String(now) },
                    { where: { comment_id: commentId, user_id: userId } }
                );
            } else {
                // Create new reaction
                await ctx.wo_reactions.create({
                    user_id: userId,
                    comment_id: commentId,
                    post_id: comment.post_id || 0,
                    reaction: reaction,
                    time: String(now)
                });
            }

            // Broadcast via Socket.IO
            if (io) {
                const post = await ctx.wo_posts.findOne({
                    where: { id: comment.post_id },
                    attributes: ['page_id'],
                    raw: true
                });
                if (post) {
                    const roomName = `channel_${post.page_id}`;
                    io.to(roomName).emit('channel:comment_reaction', {
                        postId: comment.post_id,
                        commentId: commentId,
                        userId: userId,
                        emoji: reaction,
                        action: 'add'
                    });
                }
            }

            return res.json({
                api_status: 200,
                post_id: comment.post_id,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/addCommentReaction]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    getComments,
    addComment,
    deleteComment,
    addCommentReaction
};
