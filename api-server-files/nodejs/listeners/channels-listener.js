/**
 * 📢 Channels Listener - Socket.IO handlers for Channels
 *
 * Real-time updates for channels:
 * - Subscribe/Unsubscribe to channels
 * - New posts broadcasting
 * - Post updates/deletions
 * - Comments in real-time
 * - Reactions
 * - Typing indicators
 */

const throttle = require('../helpers/adaptive-throttle');
const { minifyMessage } = require('../helpers/message-minifier');

// Active channel subscriptions: channelId -> Set of userIds
const channelSubscriptions = new Map();

/**
 * Register all channel-related Socket.IO events
 */
async function registerChannelsListeners(socket, io, ctx) {
    console.log('📢 Registering Channels listeners for socket:', socket.id);

    // ==================== CHANNEL SUBSCRIPTION ====================

    /**
     * Subscribe to channel updates
     * Client joins a channel room to receive real-time updates
     */
    socket.on('channel:subscribe', async (data) => {
        try {
            const { channelId, userId } = data;

            if (!channelId || !userId) {
                console.log('❌ channel:subscribe - missing channelId or userId');
                return;
            }

            const roomName = `channel_${channelId}`;
            socket.join(roomName);

            // Track subscription
            if (!channelSubscriptions.has(channelId)) {
                channelSubscriptions.set(channelId, new Set());
            }
            channelSubscriptions.get(channelId).add(userId);

            console.log(`✅ User ${userId} subscribed to channel ${channelId} (room: ${roomName})`);
            console.log(`   Total subscribers: ${channelSubscriptions.get(channelId).size}`);

        } catch (e) {
            console.log('❌ channel:subscribe error:', e.message);
        }
    });

    /**
     * Unsubscribe from channel
     */
    socket.on('channel:unsubscribe', async (data) => {
        try {
            const { channelId, userId } = data;

            if (!channelId) return;

            const roomName = `channel_${channelId}`;
            socket.leave(roomName);

            // Remove from tracking
            if (channelSubscriptions.has(channelId)) {
                channelSubscriptions.get(channelId).delete(userId);
                if (channelSubscriptions.get(channelId).size === 0) {
                    channelSubscriptions.delete(channelId);
                }
            }

            console.log(`👋 User ${userId} unsubscribed from channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:unsubscribe error:', e.message);
        }
    });

    // ==================== CHANNEL POSTS ====================

    /**
     * New post created - broadcast to all channel subscribers
     * Called by server after saving post to database
     */
    socket.on('channel:new_post', async (data) => {
        try {
            const { channelId, post } = data;

            if (!channelId || !post) {
                console.log('❌ channel:new_post - missing data');
                return;
            }

            const roomName = `channel_${channelId}`;

            // Minify post data to reduce bandwidth
            const minifiedPost = minifyChannelPost(post);

            // Broadcast to all subscribers
            io.to(roomName).emit('channel:post_created', {
                channelId,
                post: minifiedPost
            });

            console.log(`📝 New post broadcast to channel ${channelId}, subscribers: ${io.sockets.adapter.rooms.get(roomName)?.size || 0}`);

        } catch (e) {
            console.log('❌ channel:new_post error:', e.message);
        }
    });

    /**
     * Post updated - notify subscribers
     */
    socket.on('channel:post_updated', async (data) => {
        try {
            const { channelId, postId, text, media } = data;

            if (!channelId || !postId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:post_updated', {
                postId,
                text,
                media,
                updatedAt: Date.now()
            });

            console.log(`✏️ Post ${postId} updated in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:post_updated error:', e.message);
        }
    });

    /**
     * Post deleted - notify subscribers
     */
    socket.on('channel:post_deleted', async (data) => {
        try {
            const { channelId, postId } = data;

            if (!channelId || !postId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:post_deleted', {
                postId
            });

            console.log(`🗑️ Post ${postId} deleted from channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:post_deleted error:', e.message);
        }
    });

    /**
     * Post pinned/unpinned
     */
    socket.on('channel:post_pinned', async (data) => {
        try {
            const { channelId, postId, isPinned } = data;

            if (!channelId || !postId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:post_pinned', {
                postId,
                isPinned
            });

            console.log(`📌 Post ${postId} ${isPinned ? 'pinned' : 'unpinned'} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:post_pinned error:', e.message);
        }
    });

    // ==================== COMMENTS ====================

    /**
     * New comment on post - broadcast to channel
     */
    socket.on('channel:new_comment', async (data) => {
        try {
            const { channelId, postId, comment } = data;

            if (!channelId || !postId || !comment) {
                console.log('❌ channel:new_comment - missing data');
                return;
            }

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:comment_added', {
                postId,
                comment: {
                    id: comment.id,
                    userId: comment.user_id,
                    username: comment.username,
                    userAvatar: comment.user_avatar,
                    text: comment.text,
                    createdTime: comment.created_time || Date.now()
                }
            });

            console.log(`💬 New comment on post ${postId} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:new_comment error:', e.message);
        }
    });

    /**
     * Comment deleted
     */
    socket.on('channel:comment_deleted', async (data) => {
        try {
            const { channelId, postId, commentId } = data;

            if (!channelId || !postId || !commentId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:comment_deleted', {
                postId,
                commentId
            });

            console.log(`🗑️ Comment ${commentId} deleted from post ${postId} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:comment_deleted error:', e.message);
        }
    });

    // ==================== REACTIONS ====================

    /**
     * Reaction added to post
     */
    socket.on('channel:post_reaction', async (data) => {
        try {
            const { channelId, postId, userId, emoji, action } = data; // action: 'add' or 'remove'

            if (!channelId || !postId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:post_reaction', {
                postId,
                userId,
                emoji,
                action
            });

            console.log(`${action === 'add' ? '❤️' : '💔'} Reaction ${emoji} ${action}ed on post ${postId} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:post_reaction error:', e.message);
        }
    });

    /**
     * Reaction on comment
     */
    socket.on('channel:comment_reaction', async (data) => {
        try {
            const { channelId, postId, commentId, userId, emoji, action } = data;

            if (!channelId || !commentId) return;

            const roomName = `channel_${channelId}`;

            io.to(roomName).emit('channel:comment_reaction', {
                postId,
                commentId,
                userId,
                emoji,
                action
            });

            console.log(`❤️ Reaction on comment ${commentId} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:comment_reaction error:', e.message);
        }
    });

    // ==================== TYPING INDICATOR ====================

    /**
     * User typing in channel (comments section)
     * Throttled to prevent spam
     */
    socket.on('channel:typing', async (data) => {
        try {
            const { channelId, postId, userId, isTyping } = data;

            if (!channelId || !userId) return;

            // Throttle typing indicators (max 1 per 3 seconds)
            if (!throttle.canSendTyping(userId, `channel_${channelId}_${postId}`)) {
                // console.log(`⏱️ Throttled typing from user ${userId} in channel ${channelId}`);
                return;
            }

            const roomName = `channel_${channelId}`;

            // Don't send typing to the user who is typing
            socket.to(roomName).emit('channel:typing', {
                postId,
                userId,
                isTyping
            });

            // console.log(`⌨️ User ${userId} ${isTyping ? 'typing' : 'stopped typing'} in channel ${channelId}`);

        } catch (e) {
            console.log('❌ channel:typing error:', e.message);
        }
    });

    // ==================== CHANNEL STATS ====================

    /**
     * Get channel subscribers count (for admin dashboard)
     */
    socket.on('channel:get_stats', async (data, callback) => {
        try {
            const { channelId } = data;

            if (!channelId) return;

            const roomName = `channel_${channelId}`;
            const activeSubscribers = io.sockets.adapter.rooms.get(roomName)?.size || 0;
            const totalSubscribers = channelSubscriptions.get(channelId)?.size || 0;

            if (callback) {
                callback({
                    channelId,
                    activeSubscribers,
                    totalSubscribers
                });
            }

            console.log(`📊 Channel ${channelId} stats: ${activeSubscribers} active / ${totalSubscribers} total`);

        } catch (e) {
            console.log('❌ channel:get_stats error:', e.message);
        }
    });

    // ==================== LIVESTREAM WebRTC SIGNALING ====================

    /**
     * Viewer is ready for WebRTC — notifies the streamer to send an offer.
     * Data: { roomName, userId, channelId }
     */
    socket.on('stream:viewer_join', async (data) => {
        try {
            const { roomName, userId, channelId } = data;
            if (!roomName || !userId) return;

            socket.join(roomName);

            // Get viewer display info
            let userName = 'Viewer', userAvatar = '';
            try {
                const u = await ctx.wo_users.findOne({
                    where: { user_id: userId },
                    attributes: ['first_name', 'last_name', 'username', 'avatar'],
                    raw: true,
                });
                if (u) {
                    userName = (u.first_name || u.last_name)
                        ? `${u.first_name || ''} ${u.last_name || ''}`.trim()
                        : (u.username || 'Viewer');
                    userAvatar = u.avatar || '';
                }
            } catch (_) {}

            // Find host socket and notify them to create an offer for this viewer
            let hostUserId = null;
            if (channelId) {
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live', room_name: roomName },
                    attributes: ['host_user_id'],
                    raw: true,
                }).catch(() => null);

                if (stream) {
                    hostUserId = stream.host_user_id;
                    const hostSockets = ctx.userIdSocket[hostUserId];
                    if (hostSockets && hostSockets.length > 0) {
                        hostSockets.forEach(s => s.emit('stream:viewer_joined', {
                            roomName,
                            userId,
                            userName,
                            userAvatar,
                        }));
                    }
                }
            }

            // Keep ctx.activeStreams in sync so stream:leave (calls-listener) can notify host.
            if (!ctx.activeStreams) ctx.activeStreams = new Map();
            let room = ctx.activeStreams.get(roomName);
            if (!room) {
                room = { hostSocketId: null, hostUserId, viewers: new Map() };
                ctx.activeStreams.set(roomName, room);
            }
            room.viewers.set(userId, socket.id);
            if (hostUserId && !room.hostUserId) room.hostUserId = hostUserId;

            console.log(`[Livestream] Viewer ${userId} joined stream room ${roomName}`);
        } catch (e) {
            console.error('[Livestream] stream:viewer_join error:', e.message);
        }
    });

    /**
     * Relay WebRTC offer (streamer → specific viewer).
     * Data: { roomName, toUserId, sdpOffer }
     */
    socket.on('stream:offer', (data) => {
        const { roomName, toUserId, sdpOffer } = data;
        if (!toUserId || !sdpOffer) return;

        // socket.userId is set by JoinController on auth and is always the numeric user ID.
        const fromUserId = socket.userId || null;

        const recipientSockets = ctx.userIdSocket[toUserId];
        if (recipientSockets && recipientSockets.length > 0) {
            recipientSockets.forEach(s => s.emit('stream:offer', {
                roomName,
                fromUserId,
                sdpOffer,
            }));
        } else {
            // Fallback: viewer joined socket room via stream:viewer_join handler
            socket.to(roomName).emit('stream:offer', { roomName, fromUserId, sdpOffer });
        }
    });

    /**
     * Relay WebRTC answer (viewer → streamer).
     * Data: { roomName, toUserId, sdpAnswer }
     */
    socket.on('stream:answer', (data) => {
        const { roomName, toUserId, sdpAnswer } = data;
        if (!toUserId || !sdpAnswer) return;

        const fromUserId = socket.userId || null;

        const recipientSockets = ctx.userIdSocket[toUserId];
        if (recipientSockets && recipientSockets.length > 0) {
            recipientSockets.forEach(s => s.emit('stream:answer', {
                roomName,
                fromUserId,
                sdpAnswer,
            }));
        } else {
            socket.to(roomName).emit('stream:answer', { roomName, fromUserId, sdpAnswer });
        }
    });

    /**
     * Relay ICE candidate between streamer and viewer.
     * Data: { roomName, toUserId, candidate }
     */
    socket.on('stream:ice', (data) => {
        const { roomName, toUserId, candidate, sdpMid, sdpMLineIndex } = data;
        if (!candidate) return;

        const fromUserId = socket.userId || null;

        const payload = { roomName, fromUserId, candidate, sdpMid, sdpMLineIndex };

        if (toUserId) {
            const recipientSockets = ctx.userIdSocket[toUserId];
            if (recipientSockets && recipientSockets.length > 0) {
                recipientSockets.forEach(s => s.emit('stream:ice', payload));
            } else {
                // Fallback: use the socket room the viewer joined in stream:viewer_join
                socket.to(roomName).emit('stream:ice', payload);
            }
        } else {
            socket.to(roomName).emit('stream:ice', payload);
        }
    });

    /**
     * Adaptive quality feedback from viewer.
     * Viewer reports its connection stats → server decides if streamer should lower bitrate.
     * Data: { roomName, userId, bandwidth (kbps), rtt (ms), loss (0-1) }
     */
    socket.on('stream:quality', async (data) => {
        try {
            const { roomName, userId, bandwidth, rtt, loss } = data;
            if (!roomName) return;

            // Simple adaptive logic:
            // Good:   bandwidth > 2000 && rtt < 150 && loss < 0.02
            // Medium: bandwidth > 800  && rtt < 300 && loss < 0.05
            // Poor:   otherwise
            let suggestedQuality = null;
            let suggestedBitrate = null;

            if (bandwidth > 0) {
                if (bandwidth < 500 || loss > 0.05 || rtt > 500) {
                    suggestedQuality = '240p';
                    suggestedBitrate = 300;
                } else if (bandwidth < 1000 || rtt > 300) {
                    suggestedQuality = '360p';
                    suggestedBitrate = 700;
                } else if (bandwidth < 2000) {
                    suggestedQuality = '480p';
                    suggestedBitrate = 1200;
                }
                // otherwise keep current quality — don't downgrade unnecessarily
            }

            if (suggestedBitrate !== null) {
                // Find the streamer for this room and tell them to adjust
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { room_name: roomName, status: 'live' },
                    attributes: ['host_user_id'],
                    raw: true,
                }).catch(() => null);

                if (stream) {
                    const hostSockets = ctx.userIdSocket[stream.host_user_id];
                    if (hostSockets && hostSockets.length > 0) {
                        hostSockets.forEach(s => s.emit('stream:quality_adjust', {
                            roomName,
                            reportedBy: userId,
                            suggestedQuality,
                            suggestedBitrate,
                            viewerStats: { bandwidth, rtt, loss },
                        }));
                    }
                }
            }
        } catch (e) {
            console.error('[Livestream] stream:quality error:', e.message);
        }
    });

    /**
     * Viewer leaves the stream.
     * Data: { roomName, userId, channelId }
     */
    socket.on('stream:viewer_leave', async (data) => {
        try {
            const { roomName, userId, channelId } = data;
            if (!roomName || !userId) return;

            socket.leave(roomName);

            // Notify streamer
            if (channelId) {
                const stream = await ctx.wm_channel_livestreams.findOne({
                    where: { channel_id: channelId, status: 'live', room_name: roomName },
                    attributes: ['host_user_id', 'id', 'viewer_count'],
                    raw: true,
                }).catch(() => null);

                if (stream) {
                    if (stream.viewer_count > 0) {
                        await ctx.wm_channel_livestreams.decrement('viewer_count', { where: { id: stream.id } });
                    }
                    const hostSockets = ctx.userIdSocket[stream.host_user_id];
                    if (hostSockets && hostSockets.length > 0) {
                        hostSockets.forEach(s => s.emit('stream:viewer_left', { roomName, userId }));
                    }
                }
            }

            console.log(`[Livestream] Viewer ${userId} left stream room ${roomName}`);
        } catch (e) {
            console.error('[Livestream] stream:viewer_leave error:', e.message);
        }
    });

    // ==================== CLEANUP ====================

    /**
     * Cleanup on disconnect
     */
    socket.on('disconnect', () => {
        // Cleanup is handled by socket.io automatically
        // Rooms are left when socket disconnects
        console.log(`🔌 Channel subscriptions cleanup for socket ${socket.id}`);
    });
}

/**
 * Helper: Minify channel post data
 */
function minifyChannelPost(post) {
    return {
        id: post.id,
        cid: post.channel_id,      // channel_id
        uid: post.user_id,         // user_id
        un: post.username,         // username
        uname: post.user_name,     // user_name
        uav: post.user_avatar,     // user_avatar
        txt: post.text,            // text
        med: post.media,           // media
        ct: post.created_time,     // created_time
        pin: post.is_pinned,       // is_pinned
        views: post.views_count,   // views_count
        coms: post.comments_count, // comments_count
        reacts: post.reactions     // reactions (already grouped)
    };
}

module.exports = registerChannelsListeners;
