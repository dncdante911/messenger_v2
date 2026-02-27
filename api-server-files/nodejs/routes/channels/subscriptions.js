/**
 * Channels — Subscriptions & Member Management
 *
 * Endpoints handled:
 *   action=subscribe_channel        – subscribe current user to channel
 *   action=unsubscribe_channel      – unsubscribe current user from channel
 *   type=add_channel_member         – add a user to channel (admin only)
 *   type=get_channel_subscribers    – list channel subscribers (admin only shows ban status)
 *   type=ban_channel_member         – ban a member (admin only)
 *   type=unban_channel_member       – unban a member (admin only)
 *   type=kick_channel_member        – remove member without ban (admin only)
 *   type=get_banned_members         – list banned members (admin only)
 */

'use strict';

const { Op } = require('sequelize');
const funcs = require('../../functions/functions');

// ─── helpers ─────────────────────────────────────────────────────────────────

async function isChannelAdmin(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({
        where: { page_id: channelId },
        attributes: ['user_id'],
        raw: true
    });
    if (!page) return false;
    // eslint-disable-next-line eqeqeq
    if (page.user_id == userId) return true;
    const count = await ctx.wo_pageadmins.count({
        where: { page_id: channelId, user_id: userId }
    });
    return count > 0;
}

async function isActiveBan(ctx, channelId, userId) {
    const now = Math.floor(Date.now() / 1000);
    const ban = await ctx.wo_channel_bans.findOne({
        where: {
            channel_id: channelId,
            user_id: userId,
            [Op.or]: [
                { expire_time: 0 },
                { expire_time: { [Op.gt]: now } }
            ]
        },
        raw: true
    });
    return !!ban;
}

// ─── subscribe ───────────────────────────────────────────────────────────────

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

            // Check if user is banned
            if (await isActiveBan(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'You are banned from this channel' });
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

// ─── unsubscribe ─────────────────────────────────────────────────────────────

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
            // eslint-disable-next-line eqeqeq
            if (page.user_id == userId) {
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

// ─── add_channel_member ──────────────────────────────────────────────────────

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
            if (!await isChannelAdmin(ctx, channelId, userId)) {
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

            // Check if banned
            if (await isActiveBan(ctx, channelId, targetUserId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'User is banned from this channel' });
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

// ─── get_channel_subscribers ─────────────────────────────────────────────────

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

            const isAdmin = await isChannelAdmin(ctx, channelId, userId);

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

            const now = Math.floor(Date.now() / 1000);

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

                    // Check role
                    let role = null;
                    // eslint-disable-next-line eqeqeq
                    if (user.user_id == page.user_id) {
                        role = 'owner';
                    } else {
                        const isAdm = await ctx.wo_pageadmins.count({
                            where: { page_id: channelId, user_id: user.user_id }
                        });
                        if (isAdm > 0) role = 'admin';
                    }

                    // Check ban status (only for admins — to show info, should not happen
                    // in active list since ban removes subscription, but check anyway)
                    let isBanned = false;
                    if (isAdmin) {
                        const ban = await ctx.wo_channel_bans.findOne({
                            where: {
                                channel_id: channelId,
                                user_id: user.user_id,
                                [Op.or]: [
                                    { expire_time: 0 },
                                    { expire_time: { [Op.gt]: now } }
                                ]
                            },
                            raw: true
                        });
                        isBanned = !!ban;
                    }

                    subscribers.push({
                        id: like.user_id,
                        user_id: like.user_id,
                        username: user.username || '',
                        name: name,
                        avatar: avatarUrl,
                        subscribed_time: null,
                        is_muted: false,
                        is_banned: isBanned,
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

// ─── ban_channel_member ──────────────────────────────────────────────────────

function banMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const targetUserId = parseInt(req.body.user_id);
            const reason = req.body.reason || '';
            const durationSeconds = parseInt(req.body.duration_seconds) || 0; // 0 = permanent

            if (!channelId || !targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id and user_id are required' });
            }

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can ban members' });
            }

            // Cannot ban the owner
            // eslint-disable-next-line eqeqeq
            if (page.user_id == targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Cannot ban the channel owner' });
            }

            const now = Math.floor(Date.now() / 1000);
            const expireTime = durationSeconds > 0 ? now + durationSeconds : 0;

            // Upsert ban record
            const existing = await ctx.wo_channel_bans.findOne({
                where: { channel_id: channelId, user_id: targetUserId },
                raw: true
            });

            if (existing) {
                await ctx.wo_channel_bans.update(
                    { banned_by: userId, reason, ban_time: now, expire_time: expireTime },
                    { where: { channel_id: channelId, user_id: targetUserId } }
                );
            } else {
                await ctx.wo_channel_bans.create({
                    channel_id: channelId,
                    user_id: targetUserId,
                    banned_by: userId,
                    reason,
                    ban_time: now,
                    expire_time: expireTime
                });
            }

            // Remove from active subscribers
            await ctx.wo_pages_likes.update(
                { active: '0' },
                { where: { page_id: channelId, user_id: targetUserId } }
            );

            const expiryDesc = expireTime === 0 ? 'permanent' : `until ${expireTime}`;
            console.log(`[Channels] User ${targetUserId} banned from channel ${channelId} by ${userId} (${expiryDesc})`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Channels/banMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── unban_channel_member ────────────────────────────────────────────────────

function unbanMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const targetUserId = parseInt(req.body.user_id);

            if (!channelId || !targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id and user_id are required' });
            }

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can unban members' });
            }

            await ctx.wo_channel_bans.destroy({
                where: { channel_id: channelId, user_id: targetUserId }
            });

            console.log(`[Channels] User ${targetUserId} unbanned from channel ${channelId} by ${userId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Channels/unbanMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── kick_channel_member ─────────────────────────────────────────────────────

function kickMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const targetUserId = parseInt(req.body.user_id);

            if (!channelId || !targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id and user_id are required' });
            }

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can kick members' });
            }

            // Cannot kick the owner
            // eslint-disable-next-line eqeqeq
            if (page.user_id == targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Cannot kick the channel owner' });
            }

            // Remove from active subscribers (no ban record)
            await ctx.wo_pages_likes.update(
                { active: '0' },
                { where: { page_id: channelId, user_id: targetUserId } }
            );

            console.log(`[Channels] User ${targetUserId} kicked from channel ${channelId} by ${userId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Channels/kickMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── get_banned_members ──────────────────────────────────────────────────────

function getBanned(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const limit = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can view banned members' });
            }

            const now = Math.floor(Date.now() / 1000);

            // Get active bans (permanent or not yet expired)
            const bans = await ctx.wo_channel_bans.findAll({
                where: {
                    channel_id: channelId,
                    [Op.or]: [
                        { expire_time: 0 },
                        { expire_time: { [Op.gt]: now } }
                    ]
                },
                order: [['ban_time', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const bannedList = [];
            for (const ban of bans) {
                const user = await ctx.wo_users.findOne({
                    where: { user_id: ban.user_id },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                    raw: true
                });

                if (user) {
                    const avatarUrl = user.avatar ? await funcs.Wo_GetMedia(ctx, user.avatar) : '';
                    const name = user.first_name
                        ? (user.first_name + (user.last_name ? ' ' + user.last_name : ''))
                        : user.username;

                    bannedList.push({
                        user_id: ban.user_id,
                        username: user.username || '',
                        name,
                        avatar: avatarUrl,
                        reason: ban.reason || '',
                        ban_time: ban.ban_time,
                        expire_time: ban.expire_time,
                        banned_by: ban.banned_by
                    });
                }
            }

            return res.json({
                api_status: 200,
                banned_members: bannedList,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getBanned]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    subscribe,
    unsubscribe,
    addMember,
    getSubscribers,
    banMember,
    unbanMember,
    kickMember,
    getBanned
};
