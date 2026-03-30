'use strict';

/**
 * Channel Livestream API
 *
 * HTTP endpoints:
 *   POST  /api/node/channels/:channel_id/livestream/start   — start stream (admin/owner)
 *   POST  /api/node/channels/:channel_id/livestream/end     — end stream
 *   GET   /api/node/channels/:channel_id/livestream/active  — get active stream info
 *   POST  /api/node/channels/:channel_id/livestream/join    — viewer joins (returns ICE + room)
 *   POST  /api/node/channels/:channel_id/livestream/leave   — viewer leaves
 *
 * Socket.IO events (streamer → server):
 *   stream:offer      { roomName, toUserId, sdpOffer }   — streamer sends offer to viewer
 *   stream:ice        { roomName, toUserId, candidate }  — ICE candidate exchange
 *
 * Socket.IO events (viewer → server):
 *   stream:viewer_join  { roomName, userId, channelId }  — viewer ready for WebRTC
 *   stream:answer       { roomName, toUserId, sdpAnswer } — viewer answer to streamer
 *   stream:ice          { roomName, toUserId, candidate } — ICE candidate exchange
 *   stream:quality      { roomName, userId, bandwidth, rtt, loss } — adaptive quality feedback
 *   stream:viewer_leave { roomName, userId }             — viewer disconnecting
 *
 * Socket.IO events (server → client):
 *   channel:stream_started  { channelId, streamId, roomName, quality, title, hostUserId, hostName, hostAvatar, isPremium }
 *   channel:stream_ended    { channelId, streamId, roomName }
 *   stream:viewer_joined    { roomName, userId, userName, userAvatar } → to streamer
 *   stream:offer            { roomName, fromUserId, sdpOffer }          → to viewer
 *   stream:answer           { roomName, fromUserId, sdpAnswer }         → to streamer
 *   stream:ice              { roomName, fromUserId, candidate }         → to peer
 *   stream:viewer_left      { roomName, userId }                        → to streamer
 *   stream:quality_adjust   { bitrate, quality }                        → to streamer
 */

const crypto     = require('crypto');
const turnHelper = require('../../helpers/turn-credentials');

const REGULAR_QUALITIES = ['240p', '360p', '480p', '720p'];
const PREMIUM_QUALITIES = ['360p', '480p', '720p', '1080p', '1080p60'];

// Bitrate targets (kbps) per quality tier
const QUALITY_BITRATE = {
    '240p':   400,
    '360p':   800,
    '480p':  1500,
    '720p':  2500,
    '1080p': 4500,
    '1080p60': 6000,
};

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
    const cnt = await ctx.wo_pageadmins.count({ where: { page_id: channelId, user_id: userId } });
    return cnt > 0;
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

async function getHostInfo(ctx, userId) {
    try {
        const u = await ctx.wo_users.findOne({
            where: { user_id: userId },
            attributes: ['first_name', 'last_name', 'username', 'avatar'],
            raw: true,
        });
        if (!u) return { name: 'Unknown', avatar: '' };
        const name = (u.first_name || u.last_name)
            ? `${u.first_name || ''} ${u.last_name || ''}`.trim()
            : u.username;
        return { name, avatar: u.avatar || '' };
    } catch { return { name: 'Unknown', avatar: '' }; }
}

module.exports = function registerLivestreamRoutes(app, ctx, io) {

    // ── Start stream ─────────────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/livestream/start',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const { quality, title } = req.body;
            const userId = req.userId;
            try {
                if (!await isChannelAdmin(ctx, channelId, userId))
                    return res.status(403).json({ api_status: 403, error_message: 'Not a channel admin' });

                const isPremium = await channelIsPremium(ctx, channelId);
                const allowedQualities = isPremium ? PREMIUM_QUALITIES : REGULAR_QUALITIES;
                const chosenQuality = allowedQualities.includes(quality)
                    ? quality
                    : allowedQualities[allowedQualities.length - 1];

                // Only one active stream per channel
                const existing = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live' },
                    raw: true,
                });
                if (existing) {
                    // Re-emit channel:stream_started so any subscribers who missed the
                    // original event (e.g. joined the channel after the stream began) see
                    // the live banner immediately.
                    const existingHost = await getHostInfo(ctx, existing.host_user_id);
                    io.to(`channel_${channelId}`).emit('channel:stream_started', {
                        channelId,
                        streamId:     existing.id,
                        roomName:     existing.room_name,
                        quality:      existing.quality,
                        title:        existing.title,
                        isPremium:    existing.is_premium === 1,
                        hostUserId:   existing.host_user_id,
                        hostName:     existingHost.name,
                        hostAvatar:   existingHost.avatar,
                        startedAt:    Math.floor(new Date(existing.started_at).getTime() / 1000),
                        targetBitrate: QUALITY_BITRATE[existing.quality] || 2500,
                    });
                    return res.status(409).json({
                        api_status:    409,
                        error_message: 'Stream already active',
                        stream:        existing,
                        ice_servers:   turnHelper.getIceServers(userId),
                    });
                }

                const roomName  = generateRoomName(channelId);
                const iceServers = turnHelper.getIceServers(userId);

                const stream = await ctx.wm_channel_livestreams.create({
                    channel_id:   channelId,
                    host_user_id: userId,
                    room_name:    roomName,
                    title:        title || null,
                    quality:      chosenQuality,
                    is_premium:   isPremium ? 1 : 0,
                    status:       'live',
                    started_at:   new Date(),
                });
                const streamId = stream.id;

                // Notify all channel subscribers (Android shows pinned LIVE banner)
                const host = await getHostInfo(ctx, userId);
                const startedAt = Math.floor(Date.now() / 1000);
                io.to(`channel_${channelId}`).emit('channel:stream_started', {
                    channelId,
                    streamId,
                    roomName,
                    quality:      chosenQuality,
                    title:        title || null,
                    isPremium,
                    hostUserId:   userId,
                    hostName:     host.name,
                    hostAvatar:   host.avatar,
                    startedAt,
                    targetBitrate: QUALITY_BITRATE[chosenQuality] || 2500,
                });

                // Push notification to channel subscribers who are NOT currently online.
                // We insert Wo_Notifications rows — the PHP push daemon picks them up and sends FCM.
                // Fire-and-forget: don't block the response if this fails.
                setImmediate(async () => {
                    try {
                        const streamTitle = title || host.name;
                        // Get all channel subscribers (paginate to avoid huge IN clauses)
                        const BATCH = 500;
                        let offset = 0;
                        while (true) {
                            const followers = await ctx.wo_pages_likes.findAll({
                                where: { page_id: channelId, active: '1' },
                                attributes: ['user_id'],
                                limit: BATCH,
                                offset,
                                raw: true,
                            });
                            if (!followers.length) break;
                            offset += BATCH;

                            const rows = followers
                                .filter(f => f.user_id !== userId)  // skip the streamer themselves
                                .map(f => ({
                                    notifier_id:  userId,
                                    recipient_id: f.user_id,
                                    post_id:      0,
                                    page_id:      channelId,
                                    group_id:     0,
                                    group_chat_id: 0,
                                    event_id:     0,
                                    thread_id:    0,
                                    blog_id:      0,
                                    story_id:     0,
                                    reply_id:     null,
                                    comment_id:   null,
                                    seen_pop:     0,
                                    type:         'channel_live',
                                    type2:        'channel_live',
                                    text:         streamTitle,
                                    url:          `channel?id=${channelId}`,
                                    full_link:    `channel?id=${channelId}&stream=${streamId}`,
                                    seen:         0,
                                    sent_push:    0,
                                    time:         startedAt,
                                }));

                            if (rows.length) {
                                await ctx.wo_notification.bulkCreate(rows, { ignoreDuplicates: true });
                            }
                            if (followers.length < BATCH) break;
                        }
                        console.log(`[Livestream] Queued push notifications for channel ${channelId} stream ${streamId}`);
                    } catch (pushErr) {
                        console.error('[Livestream] push notification insert error:', pushErr.message);
                    }
                });

                console.log(`[Livestream] Stream started: channel=${channelId} room=${roomName} quality=${chosenQuality}`);

                return res.json({
                    api_status:        200,
                    stream_id:         streamId,
                    room_name:         roomName,
                    quality:           chosenQuality,
                    is_premium:        isPremium,
                    ice_servers:       iceServers,
                    allowed_qualities: allowedQualities,
                    target_bitrate:    QUALITY_BITRATE[chosenQuality] || 2500,
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
                if (!await isChannelAdmin(ctx, channelId, userId))
                    return res.status(403).json({ api_status: 403, error_message: 'Not a channel admin' });

                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live', host_user_id: userId },
                    raw: true,
                });
                if (stream) {
                    await ctx.wm_channel_livestreams.update(
                        { status: 'ended', ended_at: new Date() },
                        { where: { id: stream.id } }
                    );
                    io.to(`channel_${channelId}`).emit('channel:stream_ended', {
                        channelId,
                        streamId:  stream.id,
                        roomName:  stream.room_name,
                    });
                    // Kick all viewers from the stream room
                    io.in(stream.room_name).disconnectSockets(false);
                    console.log(`[Livestream] Stream ended: channel=${channelId} room=${stream.room_name}`);
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
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live' },
                    raw: true,
                });
                if (!stream) return res.json({ api_status: 200, stream: null });

                const host = await getHostInfo(ctx, stream.host_user_id);
                return res.json({
                    api_status: 200,
                    stream: {
                        ...stream,
                        host_name:    host.name,
                        host_avatar:  host.avatar,
                        target_bitrate: QUALITY_BITRATE[stream.quality] || 2500,
                    }
                });
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
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live' },
                    raw: true,
                });
                if (!stream)
                    return res.status(404).json({ api_status: 404, error_message: 'No active stream' });

                // Increment viewer count + update peak
                await ctx.wm_channel_livestreams.increment('viewer_count', { where: { id: stream.id } });
                const current = await ctx.wm_channel_livestreams.findOne({ where: { id: stream.id }, raw: true });
                if (current && current.viewer_count > current.peak_viewers) {
                    await ctx.wm_channel_livestreams.update(
                        { peak_viewers: current.viewer_count },
                        { where: { id: stream.id } }
                    );
                }

                const iceServers = turnHelper.getIceServers(userId);

                return res.json({
                    api_status:     200,
                    stream_id:      stream.id,
                    room_name:      stream.room_name,
                    quality:        stream.quality,
                    is_premium:     stream.is_premium,
                    ice_servers:    iceServers,
                    host_user_id:   stream.host_user_id,
                    target_bitrate: QUALITY_BITRATE[stream.quality] || 2500,
                    viewer_count:   current ? current.viewer_count : stream.viewer_count,
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
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live' },
                    raw: true,
                });
                if (stream && stream.viewer_count > 0) {
                    await ctx.wm_channel_livestreams.decrement('viewer_count', { where: { id: stream.id } });
                }
                return res.json({ api_status: 200 });
            } catch (e) {
                console.error('[Livestream] leave error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    console.log('[Livestream API] Endpoints registered on /api/node/channels/:id/livestream/*');
};
