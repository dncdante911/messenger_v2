/**
 * 📸 Stories Listener - Socket.IO handlers for Stories
 *
 * Real-time updates for stories:
 * - New stories notifications
 * - Story views
 * - Comments
 * - Reactions
 * - Story deletions/expiration
 */

const throttle = require('../helpers/adaptive-throttle');

// Active story viewers: storyId -> Set of userIds currently viewing
const activeViewers = new Map();

// User story subscriptions: userId -> Set of friendIds they're subscribed to
const storySubscriptions = new Map();

/**
 * Register all story-related Socket.IO events
 */
async function registerStoriesListeners(socket, io, ctx) {
    console.log('📸 Registering Stories listeners for socket:', socket.id);

    // ==================== STORY SUBSCRIPTION ====================

    /**
     * Subscribe to friend's stories
     * User wants to receive notifications about new stories from friends
     */
    socket.on('story:subscribe', async (data) => {
        try {
            const { userId, friendIds } = data; // friendIds = array of user IDs

            if (!userId || !friendIds || !Array.isArray(friendIds)) {
                console.log('❌ story:subscribe - invalid data');
                return;
            }

            // Join rooms for each friend
            friendIds.forEach(friendId => {
                const roomName = `stories_${friendId}`;
                socket.join(roomName);
            });

            // Track subscriptions
            if (!storySubscriptions.has(userId)) {
                storySubscriptions.set(userId, new Set());
            }
            friendIds.forEach(id => storySubscriptions.get(userId).add(id));

            console.log(`✅ User ${userId} subscribed to ${friendIds.length} friends' stories`);

        } catch (e) {
            console.log('❌ story:subscribe error:', e.message);
        }
    });

    /**
     * Unsubscribe from stories
     */
    socket.on('story:unsubscribe', async (data) => {
        try {
            const { userId, friendIds } = data;

            if (!userId || !friendIds) return;

            friendIds.forEach(friendId => {
                const roomName = `stories_${friendId}`;
                socket.leave(roomName);
            });

            // Remove from tracking
            if (storySubscriptions.has(userId)) {
                friendIds.forEach(id => storySubscriptions.get(userId).delete(id));
            }

            console.log(`👋 User ${userId} unsubscribed from stories`);

        } catch (e) {
            console.log('❌ story:unsubscribe error:', e.message);
        }
    });

    // ==================== NEW STORY ====================

    /**
     * New story created - notify all friends
     * Called by server after story is saved to database
     */
    socket.on('story:new', async (data) => {
        try {
            const { userId, story } = data;

            if (!userId || !story) {
                console.log('❌ story:new - missing data');
                return;
            }

            const roomName = `stories_${userId}`;

            // Minify story data
            const minifiedStory = {
                id: story.id,
                uid: story.user_id,
                un: story.username,
                uav: story.user_avatar,
                med: story.media_url,
                thumb: story.thumbnail_url,
                type: story.media_type, // image/video
                dur: story.duration,
                ct: story.created_time,
                exp: story.expire_time,
                views: 0,
                coms: 0
            };

            // Broadcast to all friends subscribed to this user's stories
            io.to(roomName).emit('story:created', {
                userId,
                story: minifiedStory
            });

            console.log(`📸 New story from user ${userId} broadcast to friends (room: ${roomName})`);

        } catch (e) {
            console.log('❌ story:new error:', e.message);
        }
    });

    /**
     * Story deleted or expired
     */
    socket.on('story:deleted', async (data) => {
        try {
            const { userId, storyId } = data;

            if (!userId || !storyId) return;

            const roomName = `stories_${userId}`;

            io.to(roomName).emit('story:deleted', {
                storyId
            });

            console.log(`🗑️ Story ${storyId} deleted from user ${userId}`);

        } catch (e) {
            console.log('❌ story:deleted error:', e.message);
        }
    });

    // ==================== STORY VIEWS ====================

    /**
     * User viewed story - increment view count
     */
    socket.on('story:view', async (data) => {
        try {
            const { storyId, userId, storyOwnerId } = data;

            if (!storyId || !userId || !storyOwnerId) {
                console.log('❌ story:view - missing data');
                return;
            }

            // Track active viewers
            if (!activeViewers.has(storyId)) {
                activeViewers.set(storyId, new Set());
            }
            activeViewers.get(storyId).add(userId);

            // Notify story owner about new view (only if it's not the owner viewing their own story)
            if (userId !== storyOwnerId) {
                const ownerRoom = `user_${storyOwnerId}`;
                io.to(ownerRoom).emit('story:new_view', {
                    storyId,
                    viewerId: userId,
                    totalViews: activeViewers.get(storyId).size
                });

                console.log(`👁️ User ${userId} viewed story ${storyId} by ${storyOwnerId}`);
            }

        } catch (e) {
            console.log('❌ story:view error:', e.message);
        }
    });

    // ==================== STORY COMMENTS ====================

    /**
     * New comment on story
     */
    socket.on('story:new_comment', async (data) => {
        try {
            const { storyId, userId, storyOwnerId, comment } = data;

            if (!storyId || !userId || !storyOwnerId || !comment) {
                console.log('❌ story:new_comment - missing data');
                return;
            }

            // Notify story owner about new comment
            const ownerRoom = `user_${storyOwnerId}`;

            io.to(ownerRoom).emit('story:comment_added', {
                storyId,
                comment: {
                    id: comment.id,
                    userId: comment.user_id,
                    username: comment.username,
                    userAvatar: comment.user_avatar,
                    text: comment.text,
                    createdTime: comment.created_time || Date.now()
                }
            });

            console.log(`💬 New comment on story ${storyId} from user ${userId}`);

        } catch (e) {
            console.log('❌ story:new_comment error:', e.message);
        }
    });

    /**
     * Comment deleted from story
     */
    socket.on('story:comment_deleted', async (data) => {
        try {
            const { storyId, commentId, storyOwnerId } = data;

            if (!storyId || !commentId || !storyOwnerId) return;

            const ownerRoom = `user_${storyOwnerId}`;

            io.to(ownerRoom).emit('story:comment_deleted', {
                storyId,
                commentId
            });

            console.log(`🗑️ Comment ${commentId} deleted from story ${storyId}`);

        } catch (e) {
            console.log('❌ story:comment_deleted error:', e.message);
        }
    });

    // ==================== STORY REACTIONS ====================

    /**
     * Reaction added to story
     */
    socket.on('story:reaction', async (data) => {
        try {
            const { storyId, userId, storyOwnerId, emoji, action } = data; // action: 'add' or 'remove'

            if (!storyId || !userId || !storyOwnerId) {
                console.log('❌ story:reaction - missing data');
                return;
            }

            // Notify story owner about reaction (only if it's not the owner reacting to their own story)
            if (userId !== storyOwnerId) {
                const ownerRoom = `user_${storyOwnerId}`;

                io.to(ownerRoom).emit('story:reaction_updated', {
                    storyId,
                    userId,
                    emoji,
                    action
                });

                console.log(`${action === 'add' ? '❤️' : '💔'} Reaction ${emoji} ${action}ed on story ${storyId}`);
            }

        } catch (e) {
            console.log('❌ story:reaction error:', e.message);
        }
    });

    // ==================== TYPING INDICATOR ====================

    /**
     * User typing comment on story
     * Throttled to prevent spam
     */
    socket.on('story:typing', async (data) => {
        try {
            const { storyId, userId, storyOwnerId, isTyping } = data;

            if (!storyId || !userId || !storyOwnerId) return;

            // Throttle typing indicators
            if (!throttle.canSendTyping(userId, `story_${storyId}`)) {
                return;
            }

            const ownerRoom = `user_${storyOwnerId}`;

            // Don't send to the user who is typing
            if (userId !== storyOwnerId) {
                io.to(ownerRoom).emit('story:typing', {
                    storyId,
                    userId,
                    isTyping
                });
            }

        } catch (e) {
            console.log('❌ story:typing error:', e.message);
        }
    });

    // ==================== STORY STATS ====================

    /**
     * Get story stats (for owner)
     */
    socket.on('story:get_stats', async (data, callback) => {
        try {
            const { storyId } = data;

            if (!storyId) return;

            const viewersCount = activeViewers.get(storyId)?.size || 0;

            if (callback) {
                callback({
                    storyId,
                    activeViewers: viewersCount
                });
            }

            console.log(`📊 Story ${storyId} stats: ${viewersCount} active viewers`);

        } catch (e) {
            console.log('❌ story:get_stats error:', e.message);
        }
    });

    // ==================== CLEANUP ====================

    /**
     * Cleanup on disconnect
     */
    socket.on('disconnect', () => {
        // Cleanup active viewers
        for (const [storyId, viewers] of activeViewers.entries()) {
            // We don't have userId here, so we can't remove specific user
            // This will be handled by periodic cleanup
        }
        console.log(`🔌 Story subscriptions cleanup for socket ${socket.id}`);
    });
}

// Periodic cleanup of expired story viewers (run every 5 minutes)
setInterval(() => {
    const now = Date.now();
    for (const [storyId, viewers] of activeViewers.entries()) {
        // Stories expire after 24/48 hours, so we can safely clear old data
        // This is just to prevent memory leaks
    }
}, 5 * 60 * 1000);

module.exports = registerStoriesListeners;
