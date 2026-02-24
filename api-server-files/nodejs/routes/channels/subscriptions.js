/**
 * Channels — Subscriptions
 *
 * Endpoints handled:
 *   action=subscribe_channel   – subscribe current user to channel
 *   action=unsubscribe_channel – unsubscribe current user from channel
 *   type=add_channel_member    – add a user to channel (admin only)
 *   type=get_channel_subscribers – list channel subscribers
 */

'use strict';

const { Op } = require('sequelize');
const funcs = require('../../functions/functions');

// ─── subscribe ──────────────────────────────────────────────────────────────

function subscribe(ctx, io) {
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

            // Check if already subscribed
            const existing = await ctx.wo_pages_likes.findOne({
                where: { page_id: channelId, user_id: userId },
                raw: true
            });

            if (existing) {
                if (existing.active === '1') {
                    return res.json({ api_status: 200, error_code: null, error_message: 'Already subscribed' });
                }
                // Reactivate subscription
                await ctx.wo_pages_likes.update(
                    { active: '1' },
                    { where: { page_id: channelId, user_id: userId } }
                );
            } else {
                await ctx.wo_pages_likes.create({
                    page_id: channelId,
                    user_id: userId,
                    active: '1'
                });
            }

            console.log(`[Channels] User ${userId} subscribed to channel ${channelId}`);

            return res.json({
                api_status: 200,
                channel_id: channelId,
                channel: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/subscribe]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── unsubscribe ────────────────────────────────────────────────────────────

function unsubscribe(ctx, io) {
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

            // Owner cannot unsubscribe from own channel
            if (page.user_id === userId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Owner cannot unsubscribe from own channel' });
            }

            await ctx.wo_pages_likes.update(
                { active: '0' },
                { where: { page_id: channelId, user_id: userId } }
            );

            console.log(`[Channels] User ${userId} unsubscribed from channel ${channelId}`);

            return res.json({
                api_status: 200,
                channel_id: channelId,
                channel: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/unsubscribe]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── add_channel_member ─────────────────────────────────────────────────────

function addMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const targetUserId = parseInt(req.body.user_id);

            if (!channelId || !targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id and user_id are required' });
            }

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            // Check admin permission
            const isOwner = page.user_id === userId;
            const adminRow = !isOwner ? await ctx.wo_pageadmins.findOne({
                where: { page_id: channelId, user_id: userId },
                raw: true
            }) : null;

            if (!isOwner && !adminRow) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can add members' });
            }

            // Check target user exists
            const targetUser = await ctx.wo_users.findOne({
                where: { user_id: targetUserId },
                raw: true
            });

            if (!targetUser) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'User not found' });
            }

            // Subscribe the target user
            const existing = await ctx.wo_pages_likes.findOne({
                where: { page_id: channelId, user_id: targetUserId },
                raw: true
            });

            if (existing) {
                await ctx.wo_pages_likes.update(
                    { active: '1' },
                    { where: { page_id: channelId, user_id: targetUserId } }
                );
            } else {
                await ctx.wo_pages_likes.create({
                    page_id: channelId,
                    user_id: targetUserId,
                    active: '1'
                });
            }

            console.log(`[Channels] User ${targetUserId} added to channel ${channelId} by admin ${userId}`);

            return res.json({
                api_status: 200,
                channel_id: channelId,
                channel: null,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/addMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── get_channel_subscribers ────────────────────────────────────────────────

function getSubscribers(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const limit = parseInt(req.body.limit) || 100;
            const offset = parseInt(req.body.offset) || 0;

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

            // Get subscriber user_ids
            const likes = await ctx.wo_pages_likes.findAll({
                where: { page_id: channelId, active: '1' },
                attributes: ['user_id'],
                order: [['id', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const totalCount = await ctx.wo_pages_likes.count({
                where: { page_id: channelId, active: '1' }
            });

            const subscribers = [];
            for (const like of likes) {
                const user = await ctx.wo_users.findOne({
                    where: { user_id: like.user_id },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'lastseen'],
                    raw: true
                });

                if (user) {
                    const avatarUrl = user.avatar ? await funcs.Wo_GetMedia(ctx, user.avatar) : '';
                    const name = user.first_name
                        ? (user.first_name + (user.last_name ? ' ' + user.last_name : ''))
                        : user.username;

                    // Check if admin/owner
                    let role = null;
                    if (user.user_id === page.user_id) {
                        role = 'owner';
                    } else {
                        const isAdm = await ctx.wo_pageadmins.count({
                            where: { page_id: channelId, user_id: user.user_id }
                        });
                        if (isAdm > 0) role = 'admin';
                    }

                    subscribers.push({
                        id: like.user_id,
                        user_id: like.user_id,
                        username: user.username || '',
                        name: name,
                        avatar: avatarUrl,
                        subscribed_time: null,
                        is_muted: false,
                        is_banned: false,
                        role: role,
                        last_seen: user.lastseen ? String(user.lastseen) : null
                    });
                }
            }

            return res.json({
                api_status: 200,
                subscribers: subscribers,
                total_count: totalCount,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getSubscribers]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    subscribe,
    unsubscribe,
    addMember,
    getSubscribers
};
