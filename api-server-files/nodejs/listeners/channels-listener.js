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
