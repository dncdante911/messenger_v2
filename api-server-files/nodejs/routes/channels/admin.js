/**
 * Channels — Admin (admin management, settings, statistics, QR, mute, avatar)
 *
 * Endpoints handled:
 *   type=add_channel_admin      – add admin to channel
 *   type=remove_channel_admin   – remove admin from channel
 *   type=update_settings        – update channel settings (JSON)
 *   type=get_channel_statistics – get channel statistics
 *
 * Separate endpoints:
 *   /api/v2/endpoints/generate_channel_qr.php      – generate QR code
 *   /api/v2/endpoints/subscribe_channel_by_qr.php   – subscribe via QR
 *   /api/v2/endpoints/mute_channel.php              – mute notifications
 *   /api/v2/endpoints/unmute_channel.php            – unmute notifications
 *   /api/v2/endpoints/upload_channel_avatar.php     – upload avatar image
 */

'use strict';

const { Op, Sequelize } = require('sequelize');
const crypto = require('crypto');
const path = require('path');
const funcs = require('../../functions/functions');
const { formatChannel } = require('./management');

// ─── helpers ────────────────────────────────────────────────────────────────

async function isChannelOwner(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({
        where: { page_id: channelId },
        attributes: ['user_id'],
        raw: true
    });
    // Use == (loose equality) to safely compare across potential int/string type differences
    // eslint-disable-next-line eqeqeq
    return page && page.user_id == userId;
}

async function isChannelAdmin(ctx, channelId, userId) {
    if (await isChannelOwner(ctx, channelId, userId)) return true;
    const admin = await ctx.wo_pageadmins.count({
        where: { page_id: channelId, user_id: userId }
    });
    return admin > 0;
}

// ─── add_channel_admin ──────────────────────────────────────────────────────

function addAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            let targetUserId = req.body.user_id ? parseInt(req.body.user_id) : null;
            const userSearch = req.body.user_search || null;
            const role = req.body.role || 'admin';

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            // Only owner or admin with 'admins' permission can add admins
            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found' });
            }

            const isOwner = page.user_id === userId;
            if (!isOwner) {
                const myAdmin = await ctx.wo_pageadmins.findOne({
                    where: { page_id: channelId, user_id: userId },
                    raw: true
                });
                if (!myAdmin || myAdmin.admins !== 1) {
                    return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to add admins' });
                }
            }

            // Resolve user by search if user_id not provided
            if (!targetUserId && userSearch) {
                const user = await ctx.wo_users.findOne({
                    where: {
                        [Op.or]: [
                            { username: userSearch },
                            { first_name: { [Op.like]: `%${userSearch}%` } }
                        ]
                    },
                    attributes: ['user_id'],
                    raw: true
                });
                if (!user) {
                    return res.json({ api_status: 404, error_code: 404, error_message: 'User not found' });
                }
                targetUserId = user.user_id;
            }

            if (!targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'user_id or user_search is required' });
            }

            // Check if already admin
            const existing = await ctx.wo_pageadmins.findOne({
                where: { page_id: channelId, user_id: targetUserId },
                raw: true
            });

            if (existing) {
                return res.json({ api_status: 200, error_code: null, error_message: 'User is already an admin' });
            }

            // Create admin record with default permissions
            await ctx.wo_pageadmins.create({
                page_id: channelId,
                user_id: targetUserId,
                general: 1,
                info: role === 'admin' ? 1 : 0,
                social: 1,
                avatar: role === 'admin' ? 1 : 0,
                design: 0,
                admins: 0,
                analytics: role === 'admin' ? 1 : 0,
                delete_page: 0
            });

            // Also ensure user is subscribed
            const subExists = await ctx.wo_pages_likes.findOne({
                where: { page_id: channelId, user_id: targetUserId },
                raw: true
            });
            if (!subExists) {
                await ctx.wo_pages_likes.create({
                    page_id: channelId,
                    user_id: targetUserId,
                    active: '1'
                });
            } else if (subExists.active !== '1') {
                await ctx.wo_pages_likes.update(
                    { active: '1' },
                    { where: { page_id: channelId, user_id: targetUserId } }
                );
            }

            console.log(`[Channels] Admin added: user ${targetUserId} to channel ${channelId} (role: ${role})`);

            const channel = await formatChannel(ctx, page, userId);

            return res.json({
                api_status: 200,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/addAdmin]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── remove_channel_admin ───────────────────────────────────────────────────

function removeAdmin(ctx, io) {
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

            // Cannot remove the owner
            if (page.user_id === targetUserId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Cannot remove the channel owner' });
            }

            // Only owner or admin with 'admins' permission
            const isOwner = page.user_id === userId;
            if (!isOwner) {
                const myAdmin = await ctx.wo_pageadmins.findOne({
                    where: { page_id: channelId, user_id: userId },
                    raw: true
                });
                if (!myAdmin || myAdmin.admins !== 1) {
                    return res.json({ api_status: 403, error_code: 403, error_message: 'No permission to remove admins' });
                }
            }

            await ctx.wo_pageadmins.destroy({
                where: { page_id: channelId, user_id: targetUserId }
            });

            console.log(`[Channels] Admin removed: user ${targetUserId} from channel ${channelId}`);

            const channel = await formatChannel(ctx, page, userId);

            return res.json({
                api_status: 200,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/removeAdmin]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── update_settings ────────────────────────────────────────────────────────

function updateSettings(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const settingsJson = req.body.settings_json || '{}';

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can update settings' });
            }

            // Parse settings and apply what we can to Wo_Pages
            let settings;
            try {
                settings = JSON.parse(settingsJson);
            } catch (e) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Invalid settings JSON' });
            }

            // Store settings as page_description suffix (JSON encoded) or in a separate mechanism
            // For now, we update the users_post field to control posting permissions
            const updateData = {};
            if (settings.allow_comments !== undefined) {
                // This would be applied per-post, stored as metadata
            }

            // We store channel settings in the page_description or a custom field
            // Since Wo_Pages doesn't have a dedicated settings column, we use
            // the address field (which isn't used for channels) as JSON storage
            updateData.address = JSON.stringify(settings).substring(0, 100);

            await ctx.wo_pages.update(updateData, { where: { page_id: channelId } });

            console.log(`[Channels] Settings updated for channel ${channelId}`);

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });
            const channel = await formatChannel(ctx, page, userId);

            return res.json({
                api_status: 200,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/updateSettings]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── get_channel_statistics ─────────────────────────────────────────────────

function getStatistics(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            if (!await isChannelAdmin(ctx, channelId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can view statistics' });
            }

            const now     = Math.floor(Date.now() / 1000);
            const day1    = now - 86400;
            const week1   = now - 7  * 86400;
            const month1  = now - 30 * 86400;

            // ── Subscribers ────────────────────────────────────────────────────
            const subscribersCount = await ctx.wo_pages_likes.count({
                where: { page_id: channelId, active: '1' }
            });

            // New subscribers this week (wo_pages_likes.time if column exists)
            let newSubscribersWeek = 0;
            let newSubscribersToday = 0;
            let leftSubscribersWeek = 0;
            let subscribersByDay = [0, 0, 0, 0, 0, 0, 0];
            try {
                newSubscribersWeek = await ctx.wo_pages_likes.count({
                    where: { page_id: channelId, active: '1', time: { [Op.gte]: week1 } }
                });
                newSubscribersToday = await ctx.wo_pages_likes.count({
                    where: { page_id: channelId, active: '1', time: { [Op.gte]: day1 } }
                });
                // unsubscribes tracked as active='0' with recent time
                leftSubscribersWeek = await ctx.wo_pages_likes.count({
                    where: { page_id: channelId, active: '0', time: { [Op.gte]: week1 } }
                });
                // subscribers per day for last 7 days
                const subRows = await ctx.wo_pages_likes.findAll({
                    where: { page_id: channelId, active: '1', time: { [Op.gte]: week1 } },
                    attributes: ['time'],
                    raw: true
                });
                subRows.forEach(r => {
                    const daysAgo = Math.floor((now - r.time) / 86400);
                    if (daysAgo >= 0 && daysAgo < 7) subscribersByDay[6 - daysAgo]++;
                });
            } catch (_) { /* time column may not exist */ }

            // Growth rate: (newSubscribersWeek - leftSubscribersWeek) / max(subscribersCount, 1) * 100
            const netGrowth = newSubscribersWeek - leftSubscribersWeek;
            const growthRate = subscribersCount > 0
                ? parseFloat(((netGrowth / subscribersCount) * 100).toFixed(1))
                : 0;

            // Active subscribers (lastseen >= 24h ago)
            const subscriberIds = await ctx.wo_pages_likes.findAll({
                where: { page_id: channelId, active: '1' },
                attributes: ['user_id'],
                raw: true,
                limit: 5000 // cap for performance
            });
            const subUserIds = subscriberIds.map(s => s.user_id);
            let activeSubscribers24h = 0;
            if (subUserIds.length > 0) {
                activeSubscribers24h = await ctx.wo_users.count({
                    where: { user_id: { [Op.in]: subUserIds }, lastseen: { [Op.gte]: day1 } }
                });
            }

            // ── Posts ──────────────────────────────────────────────────────────
            const postsCount = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1 }
            });
            const postsToday = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1, time: { [Op.gte]: day1 } }
            });
            const postsLastWeek = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1, time: { [Op.gte]: week1 } }
            });
            const postsThisMonth = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1, time: { [Op.gte]: month1 } }
            });
            // Media vs text posts breakdown
            const mediaPostsCount = await ctx.wo_posts.count({
                where: { page_id: channelId, active: 1, postFile: { [Op.ne]: '' } }
            });
            const textPostsCount = postsCount - mediaPostsCount;

            // ── Views ──────────────────────────────────────────────────────────
            const viewsTotalRow = await ctx.wo_posts.findAll({
                where: { page_id: channelId, active: 1 },
                attributes: [[Sequelize.fn('SUM', Sequelize.col('videoViews')), 'total']],
                raw: true
            });
            const viewsTotal = parseInt(viewsTotalRow[0]?.total || 0, 10);

            const viewsWeekRow = await ctx.wo_posts.findAll({
                where: { page_id: channelId, active: 1, time: { [Op.gte]: week1 } },
                attributes: [[Sequelize.fn('SUM', Sequelize.col('videoViews')), 'total']],
                raw: true
            });
            const viewsLastWeek = parseInt(viewsWeekRow[0]?.total || 0, 10);

            const avgViewsPerPost = postsCount > 0 ? Math.round(viewsTotal / postsCount) : 0;

            // Views per day for last 7 days
            const viewsByDay = [0, 0, 0, 0, 0, 0, 0];
            try {
                const recentPosts = await ctx.wo_posts.findAll({
                    where: { page_id: channelId, active: 1, time: { [Op.gte]: week1 } },
                    attributes: ['time', 'videoViews'],
                    raw: true
                });
                recentPosts.forEach(p => {
                    const daysAgo = Math.floor((now - p.time) / 86400);
                    if (daysAgo >= 0 && daysAgo < 7) viewsByDay[6 - daysAgo] += (p.videoViews || 0);
                });
            } catch (_) {}

            // ── Engagement ─────────────────────────────────────────────────────
            let reactionsTotal = 0;
            let commentsTotal = 0;
            try {
                const reactRow = await ctx.wo_post_reactions?.findAll?.({
                    where: { page_id: channelId },
                    attributes: [[Sequelize.fn('COUNT', Sequelize.col('id')), 'total']],
                    raw: true
                });
                reactionsTotal = parseInt(reactRow?.[0]?.total || 0, 10);
            } catch (_) {}
            try {
                const comRow = await ctx.wo_post_comments?.findAll?.({
                    where: { post_id: { [Op.in]: (await ctx.wo_posts.findAll({
                        where: { page_id: channelId, active: 1 },
                        attributes: ['id'], raw: true, limit: 1000
                    })).map(p => p.id) } },
                    attributes: [[Sequelize.fn('COUNT', Sequelize.col('id')), 'total']],
                    raw: true
                });
                commentsTotal = parseInt(comRow?.[0]?.total || 0, 10);
            } catch (_) {}
            const engagementRate = viewsTotal > 0
                ? parseFloat((((reactionsTotal + commentsTotal) / viewsTotal) * 100).toFixed(2))
                : 0;

            // ── Peak hours (by post publish time) ─────────────────────────────
            const allPostTimes = await ctx.wo_posts.findAll({
                where: { page_id: channelId, active: 1 },
                attributes: ['time'],
                raw: true,
                limit: 2000
            });
            const hourCounts = Array(24).fill(0);
            const hourlyViews24 = Array(24).fill(0);
            allPostTimes.forEach(p => {
                const h = new Date(p.time * 1000).getHours();
                hourCounts[h]++;
            });
            // top peak hours (indices where activity is above average)
            const avgHourCount = hourCounts.reduce((a, b) => a + b, 0) / 24;
            const peakHours = hourCounts
                .map((cnt, h) => ({ h, cnt }))
                .filter(x => x.cnt > avgHourCount)
                .sort((a, b) => b.cnt - a.cnt)
                .slice(0, 6)
                .map(x => x.h);

            // ── Top posts by views ─────────────────────────────────────────────
            const topPostRows = await ctx.wo_posts.findAll({
                where: { page_id: channelId, active: 1 },
                attributes: ['id', 'postText', 'videoViews', 'time', 'postFile'],
                order: [['videoViews', 'DESC']],
                limit: 10,
                raw: true
            });
            // Gather reactions+comments counts per post
            const topPostIds = topPostRows.map(p => p.id);
            const reactByPost = {};
            const commentByPost = {};
            try {
                const rRows = await ctx.wo_post_reactions?.findAll?.({
                    where: { post_id: { [Op.in]: topPostIds } },
                    attributes: ['post_id', [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt']],
                    group: ['post_id'],
                    raw: true
                });
                (rRows || []).forEach(r => { reactByPost[r.post_id] = parseInt(r.cnt, 10); });
            } catch (_) {}
            try {
                const cRows = await ctx.wo_post_comments?.findAll?.({
                    where: { post_id: { [Op.in]: topPostIds } },
                    attributes: ['post_id', [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt']],
                    group: ['post_id'],
                    raw: true
                });
                (cRows || []).forEach(c => { commentByPost[c.post_id] = parseInt(c.cnt, 10); });
            } catch (_) {}

            const topPostStats = topPostRows.map(p => ({
                id: p.id,
                text: (p.postText || '').substring(0, 120),
                views: p.videoViews || 0,
                reactions: reactByPost[p.id] || 0,
                comments: commentByPost[p.id] || 0,
                published_time: p.time || 0,
                has_media: !!(p.postFile && p.postFile !== '')
            }));

            return res.json({
                api_status: 200,
                statistics: {
                    // Subscribers
                    subscribers_count:        subscribersCount,
                    new_subscribers_today:    newSubscribersToday,
                    new_subscribers_week:     newSubscribersWeek,
                    left_subscribers_week:    leftSubscribersWeek,
                    growth_rate:              growthRate,
                    active_subscribers_24h:   activeSubscribers24h,
                    subscribers_by_day:       subscribersByDay,
                    // Posts
                    posts_count:              postsCount,
                    posts_today:              postsToday,
                    posts_last_week:          postsLastWeek,
                    posts_this_month:         postsThisMonth,
                    media_posts_count:        mediaPostsCount,
                    text_posts_count:         textPostsCount,
                    // Views
                    views_total:              viewsTotal,
                    views_last_week:          viewsLastWeek,
                    avg_views_per_post:       avgViewsPerPost,
                    views_by_day:             viewsByDay,
                    // Engagement
                    reactions_total:          reactionsTotal,
                    comments_total:           commentsTotal,
                    engagement_rate:          engagementRate,
                    // Activity heatmap
                    peak_hours:               peakHours,
                    hourly_views:             hourCounts,
                    // Top content
                    top_posts:                topPostStats
                },
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/getStatistics]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── generate_channel_qr ────────────────────────────────────────────────────

function generateQr(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, message: 'channel_id is required' });
            }

            const page = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, message: 'Channel not found' });
            }

            // Generate unique QR code
            const qrCode = crypto.randomBytes(16).toString('hex');
            const joinUrl = `${ctx.globalconfig.site_url}/join/channel/${qrCode}`;

            // Store QR code in pages_invites or use address field
            // Using pages_invites table with special inviter_id = 0 to mark as QR invite
            await ctx.wo_pages_invites.create({
                page_id: channelId,
                inviter_id: userId,
                invited_id: 0 // 0 = QR code (not specific user)
            });

            // Store the QR code mapping (using the invite id and the hash)
            // We store the QR in the page's call_action_type_url field
            await ctx.wo_pages.update(
                { call_action_type_url: qrCode },
                { where: { page_id: channelId } }
            );

            console.log(`[Channels] QR generated for channel ${channelId}: ${qrCode}`);

            return res.json({
                api_status: 200,
                qr_code: qrCode,
                join_url: joinUrl,
                message: null
            });
        } catch (err) {
            console.error('[Channels/generateQr]', err.message);
            return res.json({ api_status: 500, message: 'Server error' });
        }
    };
}

// ─── subscribe_channel_by_qr ────────────────────────────────────────────────

function subscribeByQr(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const qrCode = (req.body.qr_code || '').trim();

            if (!qrCode) {
                return res.json({ api_status: 400, message: 'qr_code is required' });
            }

            // Find channel by QR code (stored in call_action_type_url)
            const page = await ctx.wo_pages.findOne({
                where: { call_action_type_url: qrCode },
                raw: true
            });

            if (!page) {
                return res.json({ api_status: 404, message: 'Invalid or expired QR code' });
            }

            // Subscribe user
            const existing = await ctx.wo_pages_likes.findOne({
                where: { page_id: page.page_id, user_id: userId },
                raw: true
            });

            if (existing) {
                await ctx.wo_pages_likes.update(
                    { active: '1' },
                    { where: { page_id: page.page_id, user_id: userId } }
                );
            } else {
                await ctx.wo_pages_likes.create({
                    page_id: page.page_id,
                    user_id: userId,
                    active: '1'
                });
            }

            const channel = await formatChannel(ctx, page, userId);

            console.log(`[Channels] User ${userId} subscribed to channel ${page.page_id} via QR`);

            return res.json({
                api_status: 200,
                channel: channel,
                message: null
            });
        } catch (err) {
            console.error('[Channels/subscribeByQr]', err.message);
            return res.json({ api_status: 500, message: 'Server error' });
        }
    };
}

// ─── mute_channel ───────────────────────────────────────────────────────────

function muteChannel(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, message: 'channel_id is required' });
            }

            // Check if mute record exists (wo_mute uses chat_id + type='channel')
            const existing = await ctx.wo_mute.findOne({
                where: { user_id: userId, chat_id: channelId, type: 'channel' },
                raw: true
            });

            if (!existing) {
                await ctx.wo_mute.create({
                    user_id: userId,
                    chat_id: channelId,
                    notify: 'no',
                    type: 'channel',
                    time: Math.floor(Date.now() / 1000)
                });
            } else {
                await ctx.wo_mute.update(
                    { notify: 'no' },
                    { where: { user_id: userId, chat_id: channelId, type: 'channel' } }
                );
            }

            console.log(`[Channels] User ${userId} muted channel ${channelId}`);

            return res.json({ api_status: 200, message: null });
        } catch (err) {
            console.error('[Channels/muteChannel]', err.message);
            return res.json({ api_status: 500, message: 'Server error' });
        }
    };
}

// ─── unmute_channel ─────────────────────────────────────────────────────────

function unmuteChannel(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId) {
                return res.json({ api_status: 400, message: 'channel_id is required' });
            }

            await ctx.wo_mute.destroy({
                where: { user_id: userId, chat_id: channelId, type: 'channel' }
            });

            console.log(`[Channels] User ${userId} unmuted channel ${channelId}`);

            return res.json({ api_status: 200, message: null });
        } catch (err) {
            console.error('[Channels/unmuteChannel]', err.message);
            return res.json({ api_status: 500, message: 'Server error' });
        }
    };
}

// ─── upload_channel_avatar ──────────────────────────────────────────────────

function uploadAvatar(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const channelId = parseInt(req.body.channel_id);

            console.log(`[Channels/uploadAvatar] userId=${userId}, channelId=${channelId}, file=${req.file ? req.file.filename : 'MISSING'}`);

            if (!channelId || isNaN(channelId)) {
                console.warn('[Channels/uploadAvatar] channel_id missing or invalid in req.body:', req.body);
                return res.json({ api_status: 400, error_code: 400, error_message: 'channel_id is required' });
            }

            if (!req.file) {
                console.warn('[Channels/uploadAvatar] No file received');
                return res.json({ api_status: 400, error_code: 400, error_message: 'Avatar file is required' });
            }

            // Check admin permission (use == for type-safe comparison between MySQL INT and JS number)
            const isAdmin = await isChannelAdmin(ctx, channelId, userId);
            console.log(`[Channels/uploadAvatar] isAdmin=${isAdmin} for userId=${userId}, channelId=${channelId}`);
            if (!isAdmin) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can change avatar' });
            }

            // Build the relative path for storage
            const relativePath = 'upload/photos/channels/' + req.file.filename;

            // Update page avatar and verify result
            const [affectedCount] = await ctx.wo_pages.update(
                { avatar: relativePath },
                { where: { page_id: channelId } }
            );

            console.log(`[Channels/uploadAvatar] DB update affected ${affectedCount} rows for channel ${channelId}, path=${relativePath}`);

            if (affectedCount === 0) {
                console.error(`[Channels/uploadAvatar] ERROR: 0 rows updated for channel ${channelId}. Channel may not exist.`);
                return res.json({ api_status: 404, error_code: 404, error_message: 'Channel not found or update failed' });
            }

            const avatarUrl = await funcs.Wo_GetMedia(ctx, relativePath);

            // Return updated channel data so client can update state immediately
            const updatedPage = await ctx.wo_pages.findOne({
                where: { page_id: channelId },
                raw: true
            });
            const channel = updatedPage ? await formatChannel(ctx, updatedPage, userId) : null;

            console.log(`[Channels/uploadAvatar] Success: channel ${channelId} avatar=${avatarUrl}`);

            return res.json({
                api_status: 200,
                url: avatarUrl,
                channel: channel,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/uploadAvatar] Exception:', err.message, err.stack);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    addAdmin,
    removeAdmin,
    updateSettings,
    getStatistics,
    generateQr,
    subscribeByQr,
    muteChannel,
    unmuteChannel,
    uploadAvatar
};
