'use strict';

/**
 * Channel Livestream API
 *
 * Endpoints:
 *   POST   /api/node/channels/:channel_id/livestream/start  — start a stream (admin/owner only)
 *   POST   /api/node/channels/:channel_id/livestream/end    — end the stream
 *   GET    /api/node/channels/:channel_id/livestream/active — get current active stream (if any)
 *   POST   /api/node/channels/:channel_id/livestream/join   — viewer joins (returns ICE config)
 *   POST   /api/node/channels/:channel_id/livestream/leave  — viewer leaves
 *
 * Quality limits:
 *   regular channel: 240p | 360p | 480p | 720p
 *   premium channel: 360p | 480p | 720p | 1080p | 1080p60
 */

const crypto = require('crypto');
const turnHelper = require('../../helpers/turn-credentials');

// Allowed quality values per tier
const REGULAR_QUALITIES = ['240p', '360p', '480p', '720p'];
const PREMIUM_QUALITIES = ['360p', '480p', '720p', '1080p', '1080p60'];

function generateRoomName(channelId) {
    return `stream_ch${channelId}_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
}

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token'] || req.query.access_token || req.body.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid access_token' });
        req.userId = session.user_id;
        next();
    } catch (e) {
        return res.status(500).json({ api_status: 500, error_message: 'Auth error' });
    }
}

async function isChannelAdmin(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, attributes: ['user_id'], raw: true });
    if (!page) return false;
    if (page.user_id == userId) return true;
    const adminCount = await ctx.wo_pageadmins.count({ where: { page_id: channelId, user_id: userId } });
    return adminCount > 0;
}

async function channelIsPremium(ctx, channelId) {
    if (!ctx.wm_channel_subscriptions) return false;
    const sub = await ctx.wm_channel_subscriptions.findOne({
        where: { channel_id: channelId, is_active: 1 },
        attributes: ['expires_at'],
        raw: true
    });
    if (!sub) return false;
    return !sub.expires_at || new Date(sub.expires_at) > new Date();
}

module.exports = function registerLivestreamRoutes(app, ctx) {

    // ── Start stream ─────────────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/livestream/start',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const { quality, title } = req.body;
            const userId = req.userId;
            try {
                // Only admin or owner can start
                if (!await isChannelAdmin(ctx, channelId, userId)) {
                    return res.status(403).json({ api_status: 403, error_message: 'Not a channel admin' });
                }

                const isPremium = await channelIsPremium(ctx, channelId);
                const allowedQualities = isPremium ? PREMIUM_QUALITIES : REGULAR_QUALITIES;
                const chosenQuality = allowedQualities.includes(quality) ? quality : allowedQualities[allowedQualities.length - 1];

                // Only one active stream per channel allowed
                if (ctx.wm_channel_livestreams) {
                    const existing = await ctx.wm_channel_livestreams.findOne({
                        where: { channel_id: channelId, status: 'live' },
                        raw: true
                    });
                    if (existing) {
                        return res.status(409).json({ api_status: 409, error_message: 'Stream already active', stream: existing });
                    }
                }

                const roomName = generateRoomName(channelId);
                const iceServers = turnHelper.getIceServers(userId);

                let streamId = null;
                if (ctx.wm_channel_livestreams) {
                    const stream = await ctx.wm_channel_livestreams.create({
                        channel_id:   channelId,
                        host_user_id: userId,
                        room_name:    roomName,
                        title:        title || null,
                        quality:      chosenQuality,
                        is_premium:   isPremium ? 1 : 0,
                        status:       'live',
                        started_at:   new Date()
                    });
                    streamId = stream.id;
                }

                // Notify channel subscribers via Socket.IO (if available)
                if (ctx.io) {
                    ctx.io.to(`channel_${channelId}`).emit('channel:stream_started', {
                        channelId,
                        streamId,
                        roomName,
                        quality: chosenQuality,
                        isPremium,
                        hostUserId: userId
                    });
                }

                return res.json({
                    api_status: 200,
                    stream_id:  streamId,
                    room_name:  roomName,
                    quality:    chosenQuality,
                    is_premium: isPremium,
                    ice_servers: iceServers,
                    allowed_qualities: allowedQualities
                });
            } catch (e) {
                console.error('[Livestream] start error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── End stream ───────────────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/livestream/end',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const userId = req.userId;
            try {
                if (!await isChannelAdmin(ctx, channelId, userId)) {
                    return res.status(403).json({ api_status: 403, error_message: 'Not a channel admin' });
                }

                if (ctx.wm_channel_livestreams) {
                    const stream = await ctx.wm_channel_livestreams.findOne({
                        where: { channel_id: channelId, status: 'live', host_user_id: userId },
                        raw: true
                    });
                    if (stream) {
                        await ctx.wm_channel_livestreams.update(
                            { status: 'ended', ended_at: new Date() },
                            { where: { id: stream.id } }
                        );

                        if (ctx.io) {
                            ctx.io.to(`channel_${channelId}`).emit('channel:stream_ended', {
                                channelId, streamId: stream.id, roomName: stream.room_name
                            });
                        }
                    }
                }

                return res.json({ api_status: 200 });
            } catch (e) {
                console.error('[Livestream] end error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── Get active stream ────────────────────────────────────────────────────
    app.get('/api/node/channels/:channel_id/livestream/active',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            try {
                let stream = null;
                if (ctx.wm_channel_livestreams) {
                    stream = await ctx.wm_channel_livestreams.findOne({
                        where: { channel_id: channelId, status: 'live' },
                        raw: true
                    });
                }
                return res.json({ api_status: 200, stream: stream || null });
            } catch (e) {
                console.error('[Livestream] active error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── Viewer joins ─────────────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/livestream/join',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const userId = req.userId;
            try {
                let stream = null;
                if (ctx.wm_channel_livestreams) {
                    stream = await ctx.wm_channel_livestreams.findOne({
                        where: { channel_id: channelId, status: 'live' },
                        raw: true
                    });
                }
                if (!stream) {
                    return res.status(404).json({ api_status: 404, error_message: 'No active stream' });
                }

                // Increment viewer count
                if (ctx.wm_channel_livestreams) {
                    await ctx.wm_channel_livestreams.increment('viewer_count', { where: { id: stream.id } });
                    // Update peak if needed - we'll do a simple check
                    const current = await ctx.wm_channel_livestreams.findOne({ where: { id: stream.id }, raw: true });
                    if (current && current.viewer_count > current.peak_viewers) {
                        await ctx.wm_channel_livestreams.update(
                            { peak_viewers: current.viewer_count },
                            { where: { id: stream.id } }
                        );
                    }
                }

                const iceServers = turnHelper.getIceServers(userId);

                return res.json({
                    api_status:  200,
                    stream_id:   stream.id,
                    room_name:   stream.room_name,
                    quality:     stream.quality,
                    is_premium:  stream.is_premium,
                    ice_servers: iceServers
                });
            } catch (e) {
                console.error('[Livestream] join error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── Viewer leaves ────────────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/livestream/leave',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            try {
                if (ctx.wm_channel_livestreams) {
                    const stream = await ctx.wm_channel_livestreams.findOne({
                        where: { channel_id: channelId, status: 'live' },
                        raw: true
                    });
                    if (stream && stream.viewer_count > 0) {
                        await ctx.wm_channel_livestreams.decrement('viewer_count', { where: { id: stream.id } });
                    }
                }
                return res.json({ api_status: 200 });
            } catch (e) {
                console.error('[Livestream] leave error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );
};
