/**
 * Channels — Management (CRUD, list, search, details)
 *
 * Endpoints handled:
 *   type=get_list       – list all channels
 *   type=get_subscribed – list subscribed channels
 *   type=search         – search channels by query
 *   type=get_by_id      – get channel details + admins
 *   action=create_channel  – create new channel
 *   action=update_channel  – update channel info
 *   action=delete_channel  – delete channel
 */

'use strict';

const { Op } = require('sequelize');
const funcs = require('../../functions/functions');

// ─── helpers ────────────────────────────────────────────────────────────────

async function formatChannel(ctx, page, userId) {
    const subscribersCount = await ctx.wo_pages_likes.count({
        where: { page_id: page.page_id, active: '1' }
    });

    const postsCount = await ctx.wo_posts.count({
        where: { page_id: page.page_id, active: 1 }
    });

    const isSubscribed = userId ? await ctx.wo_pages_likes.count({
        where: { page_id: page.page_id, user_id: userId, active: '1' }
    }) > 0 : false;

    const isAdmin = userId ? (
        page.user_id === userId ||
        await ctx.wo_pageadmins.count({
            where: { page_id: page.page_id, user_id: userId }
        }) > 0
    ) : false;

    const avatarUrl = page.avatar ? await funcs.Wo_GetMedia(ctx, page.avatar) : '';

    return {
        id: page.page_id,
        name: page.page_title || page.page_name || '',
        username: page.page_name || null,
        avatar_url: avatarUrl,
        description: page.page_description || null,
        subscribers_count: subscribersCount,
        posts_count: postsCount,
        owner_id: page.user_id,
        is_private: page.active === '0' ? true : false,
        is_verified: page.verified === '1',
        is_admin: isAdmin,
        is_subscribed: isSubscribed,
        created_time: parseInt(page.registered) || 0,
        category: page.page_category ? String(page.page_category) : null,
        settings: null
    };
}

async function formatChannelList(ctx, pages, userId) {
    const result = [];
    for (const page of pages) {
        result.push(await formatChannel(ctx, page, userId));
    }
    return result;
}

// ─── get_list ───────────────────────────────────────────────────────────────

function getList(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const limit = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

            const pages = await ctx.wo_pages.findAll({
                where: { active: '1' },
                order: [['page_id', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const channels = await formatChannelList(ctx, pages, userId);

            return res.json({
                api_status: 200,
                channels: channels,
                data: channels,
                total_count: channels.length,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getList]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── get_subscribed ─────────────────────────────────────────────────────────

function getSubscribed(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const limit = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

            // Get page_ids the user is subscribed to
            const likes = await ctx.wo_pages_likes.findAll({
                where: { user_id: userId, active: '1' },
                attributes: ['page_id'],
                raw: true
            });

            const pageIds = likes.map(l => l.page_id);

            // Also include pages the user owns
            const ownedPages = await ctx.wo_pages.findAll({
                where: { user_id: userId },
                attributes: ['page_id'],
                raw: true
            });
            const ownedIds = ownedPages.map(p => p.page_id);

            const allIds = [...new Set([...pageIds, ...ownedIds])];

            if (allIds.length === 0) {
                return res.json({
                    api_status: 200,
                    channels: [],
                    data: [],
                    total_count: 0,
                    error_code: null,
                    error_message: null
                });
            }

            const pages = await ctx.wo_pages.findAll({
                where: { page_id: { [Op.in]: allIds } },
                order: [['page_id', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const channels = await formatChannelList(ctx, pages, userId);

            return res.json({
                api_status: 200,
                channels: channels,
                data: channels,
                total_count: channels.length,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getSubscribed]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── search ─────────────────────────────────────────────────────────────────

function search(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query = (req.body.query || '').trim();
            const limit = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

            if (!query) {
                return res.json({
                    api_status: 200,
                    channels: [],
                    data: [],
                    total_count: 0,
                    error_code: null,
                    error_message: null
                });
            }

            const pages = await ctx.wo_pages.findAll({
                where: {
                    [Op.or]: [
                        { page_name: { [Op.like]: `%${query}%` } },
                        { page_title: { [Op.like]: `%${query}%` } },
                        { page_description: { [Op.like]: `%${query}%` } }
                    ]
                },
                order: [['page_id', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const channels = await formatChannelList(ctx, pages, userId);

            return res.json({
                api_status: 200,
                channels: channels,
                data: channels,
                total_count: channels.length,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/search]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── get_by_id (details) ────────────────────────────────────────────────────

function getDetails(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            const channel = await formatChannel(ctx, page, userId);

            // Get admins
            const adminRows = await ctx.wo_pageadmins.findAll({
                where: { page_id: channelId },
                raw: true
            });

            const admins = [];
            // Add owner as first admin
            const owner = await ctx.wo_users.findOne({
                where: { user_id: page.user_id },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                raw: true
            });
            if (owner) {
                const ownerAvatar = owner.avatar ? await funcs.Wo_GetMedia(ctx, owner.avatar) : '';
                admins.push({
                    user_id: owner.user_id,
                    username: owner.username || '',
                    avatar: ownerAvatar,
                    role: 'owner',
                    added_time: parseInt(page.registered) || 0,
                    permissions: null
                });
            }

            // Add other admins
            for (const adminRow of adminRows) {
                if (adminRow.user_id === page.user_id) continue; // skip owner
                const user = await ctx.wo_users.findOne({
                    where: { user_id: adminRow.user_id },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                    raw: true
                });
                if (user) {
                    const userAvatar = user.avatar ? await funcs.Wo_GetMedia(ctx, user.avatar) : '';
                    admins.push({
                        user_id: user.user_id,
                        username: user.username || '',
                        avatar: userAvatar,
                        role: 'admin',
                        added_time: 0,
                        permissions: {
                            can_post: adminRow.general === 1,
                            can_edit_posts: adminRow.general === 1,
                            can_delete_posts: adminRow.general === 1,
                            can_pin_posts: adminRow.general === 1,
                            can_edit_info: adminRow.info === 1,
                            can_delete_channel: adminRow.delete_page === 1,
                            can_add_admins: adminRow.admins === 1,
                            can_remove_admins: adminRow.admins === 1,
                            can_ban_users: adminRow.admins === 1,
                            can_view_statistics: adminRow.analytics === 1,
                            can_manage_comments: adminRow.general === 1
                        }
                    });
                }
            }

            return res.json({
                api_status: 200,
                channel: channel,
                admins: admins,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getDetails]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── create_channel ─────────────────────────────────────────────────────────

function createChannel(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const { name, username, description, avatar_url, is_private, category } = req.body;

            if (!name || !name.trim()) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Channel name is required' });
            }

            const pageName = (username || name).replace(/[^a-zA-Z0-9_]/g, '_').substring(0, 32);

            // Check if page_name already exists
            const existing = await ctx.wo_pages.findOne({
                where: { page_name: pageName },
                raw: true
            });
            if (existing) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Channel username already taken' });
            }

            const now = Math.floor(Date.now() / 1000);
            const month = new Date().getMonth() + 1;
            const year = new Date().getFullYear();

            const newPage = await ctx.wo_pages.create({
                user_id: userId,
                page_name: pageName,
                page_title: name.trim().substring(0, 32),
                page_description: (description || '').substring(0, 1000),
                avatar: avatar_url || 'upload/photos/d-page.jpg',
                page_category: parseInt(category) || 1,
                active: is_private === 1 || is_private === '1' ? '0' : '1',
                registered: `${month}/${year}`
            });

            // Auto-subscribe the creator
            await ctx.wo_pages_likes.create({
                page_id: newPage.page_id,
                user_id: userId,
                active: '1'
            });

            const channel = await formatChannel(ctx, newPage, userId);

            console.log(`[Channels] Created channel "${name}" (id: ${newPage.page_id}) by user ${userId}`);

            return res.json({
                api_status: 200,
                channel_id: newPage.page_id,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/create]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── update_channel ─────────────────────────────────────────────────────────

function updateChannel(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const { name, description, username, category } = req.body;

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            // Check permissions: owner or admin with info permission
            const isOwner = page.user_id === userId;
            const adminRow = !isOwner ? await ctx.wo_pageadmins.findOne({
                where: { page_id: channelId, user_id: userId },
                raw: true
            }) : null;

            if (!isOwner && (!adminRow || adminRow.info !== 1)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to edit this channel' });
            }

            const updateData = {};
            if (name) updateData.page_title = name.trim().substring(0, 32);
            if (description !== undefined) updateData.page_description = (description || '').substring(0, 1000);
            if (username) {
                const newPageName = username.replace(/[^a-zA-Z0-9_]/g, '_').substring(0, 32);
                const existingName = await ctx.wo_pages.findOne({
                    where: { page_name: newPageName, page_id: { [Op.ne]: channelId } },
                    raw: true
                });
                if (existingName) {
                    return res.json({ api_status: 400, error_code: 400, error_message: 'Username already taken' });
                }
                updateData.page_name = newPageName;
            }
            if (category) updateData.page_category = parseInt(category) || 1;

            await ctx.wo_pages.update(updateData, { where: { page_id: channelId } });

            const updatedPage = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            const channel = await formatChannel(ctx, updatedPage, userId);

            console.log(`[Channels] Updated channel ${channelId} by user ${userId}`);

            return res.json({
                api_status: 200,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/update]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── delete_channel ─────────────────────────────────────────────────────────

function deleteChannel(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            // Only owner or admin with delete_page permission
            const isOwner = page.user_id === userId;
            if (!isOwner) {
                const adminRow = await ctx.wo_pageadmins.findOne({
                    where: { page_id: channelId, user_id: userId },
                    raw: true
                });
                if (!adminRow || adminRow.delete_page !== 1) {
                    return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to delete' });
                }
            }

            // Delete related data
            await ctx.wo_pages_likes.destroy({ where: { page_id: channelId } });
            await ctx.wo_pageadmins.destroy({ where: { page_id: channelId } });
            await ctx.wo_pinnedposts.destroy({ where: { page_id: channelId } });
            await ctx.wo_pages_invites.destroy({ where: { page_id: channelId } });

            // Delete posts and their comments/reactions
            const postIds = await ctx.wo_posts.findAll({
                where: { page_id: channelId },
                attributes: ['id'],
                raw: true
            });
            const pIds = postIds.map(p => p.id);
            if (pIds.length > 0) {
                await ctx.wo_comments.destroy({ where: { post_id: { [Op.in]: pIds } } });
                await ctx.wo_reactions.destroy({ where: { post_id: { [Op.in]: pIds } } });
            }
            await ctx.wo_posts.destroy({ where: { page_id: channelId } });

            // Delete the page itself
            await ctx.wo_pages.destroy({ where: { page_id: channelId } });

            console.log(`[Channels] Deleted channel ${channelId} by user ${userId}`);

            return res.json({
                api_status: 200,
                channel_id: channelId,
                channel: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/delete]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    getList,
    getSubscribed,
    search,
    getDetails,
    createChannel,
    updateChannel,
    deleteChannel,
    formatChannel
};
