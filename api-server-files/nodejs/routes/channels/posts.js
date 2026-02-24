/**
 * Channels — Posts (CRUD, pin/unpin, views)
 *
 * Endpoints handled:
 *   action=get_channel_posts – list posts for a channel
 *   action=create_post       – create new post in channel
 *   type=update_post         – update post text/media
 *   type=delete_post         – delete a post
 *   type=pin_post            – pin a post
 *   type=unpin_post          – unpin a post
 *   type=register_post_view  – register a view on a post
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

async function formatPost(ctx, post, io) {
    const author = await ctx.wo_users.findOne({
        where: { user_id: post.user_id },
        attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
        raw: true
    });

    const authorAvatar = author && author.avatar
        ? await funcs.Wo_GetMedia(ctx, author.avatar)
        : '';
    const authorName = author
        ? (author.first_name ? (author.first_name + (author.last_name ? ' ' + author.last_name : '')) : author.username)
        : 'Unknown';

    // Build media list from post fields
    const media = [];
    if (post.postPhoto && post.postPhoto !== '') {
        const photos = post.postPhoto.split(',');
        for (const photo of photos) {
            if (photo.trim()) {
                media.push({
                    url: await funcs.Wo_GetMedia(ctx, photo.trim()),
                    type: 'image',
                    filename: null
                });
            }
        }
    }
    if (post.postFile && post.postFile !== '') {
        const fileUrl = await funcs.Wo_GetMedia(ctx, post.postFile);
        const ext = (post.postFile || '').split('.').pop().toLowerCase();
        let type = 'file';
        if (['mp4', 'mkv', 'avi', 'webm', 'mov'].includes(ext)) type = 'video';
        else if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) type = 'audio';
        else if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) type = 'image';
        media.push({
            url: fileUrl,
            type: type,
            filename: post.postFileName || null
        });
    }
    if (post.postRecord && post.postRecord !== '') {
        media.push({
            url: await funcs.Wo_GetMedia(ctx, post.postRecord),
            type: 'audio',
            filename: null
        });
    }

    // Count comments
    const commentsCount = await ctx.wo_comments.count({
        where: { post_id: post.id }
    });

    // Count reactions
    const reactionsCount = await ctx.wo_reactions.count({
        where: { post_id: post.id }
    });

    // Get reaction details (grouped by emoji)
    const reactionRows = await ctx.wo_reactions.findAll({
        where: { post_id: post.id },
        attributes: ['reaction', [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt']],
        group: ['reaction'],
        raw: true
    });

    const reactions = reactionRows.map(r => ({
        emoji: r.reaction,
        count: parseInt(r.cnt),
        user_reacted: false,
        recent_users: null
    }));

    // Check if post is pinned
    const isPinned = await ctx.wo_pinnedposts.count({
        where: { post_id: post.id, page_id: post.page_id, active: '1' }
    }) > 0;

    return {
        id: post.id,
        author_id: post.user_id,
        author_username: author ? author.username : null,
        author_name: authorName,
        author_avatar: authorAvatar,
        text: post.postText || '',
        media: media.length > 0 ? media : null,
        created_time: post.time || 0,
        is_edited: false,
        is_pinned: isPinned,
        views_count: post.videoViews || 0,
        reactions_count: reactionsCount,
        comments_count: commentsCount,
        reactions: reactions.length > 0 ? reactions : null
    };
}

// ─── get_channel_posts ──────────────────────────────────────────────────────

function getPosts(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const limit = parseInt(req.body.limit) || 20;
            const beforePostId = req.body.before_post_id ? parseInt(req.body.before_post_id) : null;

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            // Verify channel exists
            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            const whereClause = {
                page_id: channelId,
                active: 1
            };

            if (beforePostId) {
                whereClause.id = { [Op.lt]: beforePostId };
            }

            const rawPosts = await ctx.wo_posts.findAll({
                where: whereClause,
                order: [['id', 'DESC']],
                limit,
                raw: true
            });

            const posts = [];
            for (const rawPost of rawPosts) {
                const formatted = await formatPost(ctx, rawPost, io);
                // Set user_reacted for current user
                if (formatted.reactions) {
                    for (const r of formatted.reactions) {
                        const userReacted = await ctx.wo_reactions.count({
                            where: { post_id: rawPost.id, user_id: userId, reaction: r.emoji }
                        });
                        r.user_reacted = userReacted > 0;
                    }
                }
                posts.push(formatted);
            }

            const totalCount = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1 }
            });

            return res.json({
                api_status: 200,
                posts: posts,
                total_count: totalCount,
                has_more: rawPosts.length === limit,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getPosts]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── create_post ────────────────────────────────────────────────────────────

function createPost(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const text = (req.body.text || '').trim();
            const mediaUrls = req.body.media_urls || null;
            const disableComments = req.body.disable_comments === 1 || req.body.disable_comments === '1';

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            if (!text && !mediaUrls) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Text or media is required' });
            }

            // Verify admin permission
            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can post' });
            }

            const now = Math.floor(Date.now() / 1000);
            const month = new Date().getMonth() + 1;
            const year = new Date().getFullYear();

            // Parse media from JSON if provided
            let postPhoto = '';
            let postFile = '';
            let postFileName = '';
            if (mediaUrls) {
                try {
                    const mediaArray = JSON.parse(mediaUrls);
                    const imageUrls = [];
                    for (const m of mediaArray) {
                        if (m.type === 'image') {
                            imageUrls.push(m.url);
                        } else if (m.type === 'video' || m.type === 'audio' || m.type === 'file') {
                            postFile = m.url;
                            postFileName = m.filename || '';
                        }
                    }
                    if (imageUrls.length > 0) {
                        postPhoto = imageUrls.join(',');
                    }
                } catch (e) {
                    // mediaUrls might be a single URL string
                    postPhoto = mediaUrls;
                }
            }

            // Sanitize text
            const sanitizedText = await funcs.sanitizeJS(text);

            const newPost = await ctx.wo_posts.create({
                user_id: userId,
                page_id: channelId,
                postText: sanitizedText,
                postPhoto: postPhoto,
                postFile: postFile,
                postFileName: postFileName,
                time: now,
                registered: `${month}/${year}`,
                comments_status: disableComments ? 0 : 1,
                active: 1
            });

            const formatted = await formatPost(ctx, newPost, io);

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${channelId}`;
                io.to(roomName).emit('channel:post_created', {
                    channelId: channelId,
                    post: formatted
                });
            }

            console.log(`[Channels] Post created in channel ${channelId} by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: newPost.id,
                post: formatted,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/createPost]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── update_post ────────────────────────────────────────────────────────────

function updatePost(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);
            const text = (req.body.text || '').trim();
            const mediaUrls = req.body.media_urls || null;

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            // Check permissions: post author or channel admin
            if (post.user_id !== userId && !await isChannelAdmin(ctx, post.page_id, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to edit' });
            }

            const updateData = {};
            if (text) {
                updateData.postText = await funcs.sanitizeJS(text);
            }
            if (mediaUrls) {
                try {
                    const mediaArray = JSON.parse(mediaUrls);
                    const imageUrls = [];
                    for (const m of mediaArray) {
                        if (m.type === 'image') {
                            imageUrls.push(m.url);
                        } else if (m.type === 'video' || m.type === 'audio' || m.type === 'file') {
                            updateData.postFile = m.url;
                            updateData.postFileName = m.filename || '';
                        }
                    }
                    if (imageUrls.length > 0) {
                        updateData.postPhoto = imageUrls.join(',');
                    }
                } catch (e) {
                    updateData.postPhoto = mediaUrls;
                }
            }

            await ctx.wo_posts.update(updateData, { where: { id: postId } });

            const updatedPost = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });
            const formatted = await formatPost(ctx, updatedPost, io);

            // Broadcast update via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:post_updated', {
                    postId: postId,
                    text: formatted.text,
                    media: formatted.media,
                    updatedAt: Date.now()
                });
            }

            console.log(`[Channels] Post ${postId} updated by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: formatted,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/updatePost]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── delete_post ────────────────────────────────────────────────────────────

function deletePost(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            // Check permissions: post author or channel admin
            if (post.user_id !== userId && !await isChannelAdmin(ctx, post.page_id, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to delete' });
            }

            const channelId = post.page_id;

            // Delete related data
            await ctx.wo_comments.destroy({ where: { post_id: postId } });
            await ctx.wo_reactions.destroy({ where: { post_id: postId } });
            await ctx.wo_pinnedposts.destroy({ where: { post_id: postId } });

            // Delete the post
            await ctx.wo_posts.destroy({ where: { id: postId } });

            // Broadcast deletion via Socket.IO
            if (io) {
                const roomName = `channel_${channelId}`;
                io.to(roomName).emit('channel:post_deleted', { postId: postId });
            }

            console.log(`[Channels] Post ${postId} deleted from channel ${channelId} by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/deletePost]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── pin_post ───────────────────────────────────────────────────────────────

function pinPost(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            if (!await isChannelAdmin(ctx, post.page_id, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can pin posts' });
            }

            // Check if already pinned
            const existing = await ctx.wo_pinnedposts.findOne({
                where: { post_id: postId, page_id: post.page_id },
                raw: true
            });

            if (existing) {
                await ctx.wo_pinnedposts.update(
                    { active: '1' },
                    { where: { post_id: postId, page_id: post.page_id } }
                );
            } else {
                await ctx.wo_pinnedposts.create({
                    user_id: userId,
                    page_id: post.page_id,
                    post_id: postId,
                    active: '1'
                });
            }

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:post_pinned', { postId, isPinned: true });
            }

            console.log(`[Channels] Post ${postId} pinned by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/pinPost]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── unpin_post ─────────────────────────────────────────────────────────────

function unpinPost(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const postId = parseInt(req.body.post_id);

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            const post = await ctx.wo_posts.findOne({
                where: { id: postId },
                raw: true
            });

            if (!post) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Post not found' });
            }

            if (!await isChannelAdmin(ctx, post.page_id, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can unpin posts' });
            }

            await ctx.wo_pinnedposts.update(
                { active: '0' },
                { where: { post_id: postId, page_id: post.page_id } }
            );

            // Broadcast via Socket.IO
            if (io) {
                const roomName = `channel_${post.page_id}`;
                io.to(roomName).emit('channel:post_pinned', { postId, isPinned: false });
            }

            console.log(`[Channels] Post ${postId} unpinned by user ${userId}`);

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/unpinPost]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── register_post_view ─────────────────────────────────────────────────────

function registerView(ctx, io) {
    return async (req, res) => {
        try {
            const postId = parseInt(req.body.post_id);

            if (!postId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'post_id is required' });
            }

            // Increment videoViews (used as general view counter)
            await ctx.wo_posts.increment('videoViews', {
                by: 1,
                where: { id: postId }
            });

            return res.json({
                api_status: 200,
                post_id: postId,
                post: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/registerView]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    getPosts,
    createPost,
    updatePost,
    deletePost,
    pinPost,
    unpinPost,
    registerView,
    formatPost
};
