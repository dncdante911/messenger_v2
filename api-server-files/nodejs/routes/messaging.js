/**
 * Node.js REST API Routes for Messaging
 * Replaces PHP polling endpoints (get_user_messages.php, insert_new_message.php)
 * Provides direct HTTP API for mobile clients alongside Socket.IO real-time
 */

const express = require('express');
const router = express.Router();
const { Op } = require('sequelize');

/**
 * Middleware: Validate access_token and resolve user_id
 */
async function authMiddleware(ctx, req, res, next) {
    const accessToken = req.headers['access-token'] || req.query.access_token || req.body.access_token;

    if (!accessToken) {
        return res.status(401).json({
            api_status: 401,
            error_message: 'access_token is required'
        });
    }

    try {
        const session = await ctx.wo_appssessions.findOne({
            where: { session_id: accessToken }
        });

        if (!session) {
            return res.status(401).json({
                api_status: 401,
                error_message: 'Invalid or expired access_token'
            });
        }

        req.userId = session.user_id;
        req.accessToken = accessToken;
        next();
    } catch (err) {
        console.error('[Auth] Error:', err.message);
        return res.status(500).json({
            api_status: 500,
            error_message: 'Authentication error'
        });
    }
}

/**
 * Helper: Get user basic data
 */
async function getUserBasicData(ctx, userId) {
    try {
        const user = await ctx.wo_users.findOne({
            attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'lastseen', 'status'],
            where: { user_id: userId }
        });
        if (!user) return null;
        const data = user.toJSON();
        data.name = (data.first_name && data.last_name)
            ? data.first_name + ' ' + data.last_name
            : data.username;
        return data;
    } catch (e) {
        return null;
    }
}

/**
 * POST /api/chat/get
 * Fetch message history between current user and a recipient
 * Replaces: get_user_messages.php polling endpoint
 *
 * Body params:
 *   - recipient_id: (required) the other user's ID
 *   - limit: (optional) max messages, default 20, max 100
 *   - after_message_id: (optional) fetch messages after this ID (newer)
 *   - before_message_id: (optional) fetch messages before this ID (older)
 *   - message_id: (optional) fetch a specific message
 */
function getMessagesRoute(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const recipientId = parseInt(req.body.recipient_id);

            if (!recipientId || isNaN(recipientId)) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'recipient_id is required'
                });
            }

            // Verify recipient exists
            const recipient = await ctx.wo_users.findOne({
                attributes: ['user_id'],
                where: { user_id: recipientId }
            });
            if (!recipient) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'Recipient user not found'
                });
            }

            const limit = Math.min(parseInt(req.body.limit) || 20, 100);
            const afterMessageId = parseInt(req.body.after_message_id) || 0;
            const beforeMessageId = parseInt(req.body.before_message_id) || 0;
            const messageId = parseInt(req.body.message_id) || 0;

            // Build where clause
            let whereClause = {
                page_id: 0,
                [Op.or]: [
                    {
                        from_id: recipientId,
                        to_id: userId,
                        deleted_two: '0'
                    },
                    {
                        from_id: userId,
                        to_id: recipientId,
                        deleted_one: '0'
                    }
                ]
            };

            if (messageId > 0) {
                whereClause.id = messageId;
            } else if (afterMessageId > 0) {
                whereClause.id = { [Op.gt]: afterMessageId };
            } else if (beforeMessageId > 0) {
                whereClause.id = { [Op.lt]: beforeMessageId };
            }

            const messages = await ctx.wo_messages.findAll({
                where: whereClause,
                order: [['id', 'DESC']],
                limit: limit,
                raw: true
            });

            // Process messages (reverse for chronological order)
            const processedMessages = [];
            for (const msg of messages.reverse()) {
                const position = msg.from_id === userId ? 'right' : 'left';

                let type = '';
                if (msg.media) type = 'file';
                if (msg.stickers && msg.stickers.includes('.gif')) type = 'gif';
                if (msg.type_two === 'contact') type = 'contact';
                if (msg.lng && msg.lat && msg.lng !== '0' && msg.lat !== '0') type = 'map';
                type = position + '_' + type;

                // Time formatting
                let timeText = '';
                if (msg.time) {
                    const now = Math.floor(Date.now() / 1000);
                    const dayAgo = now - 86400;
                    if (msg.time < dayAgo) {
                        const d = new Date(msg.time * 1000);
                        timeText = String(d.getMonth() + 1).padStart(2, '0') + '.' +
                                   String(d.getDate()).padStart(2, '0') + '.' +
                                   String(d.getFullYear()).slice(-2);
                    } else {
                        const d = new Date(msg.time * 1000);
                        timeText = String(d.getHours()).padStart(2, '0') + ':' +
                                   String(d.getMinutes()).padStart(2, '0');
                    }
                }

                // Get sender data
                const senderData = await getUserBasicData(ctx, msg.from_id);

                processedMessages.push({
                    id: msg.id,
                    from_id: msg.from_id,
                    to_id: msg.to_id,
                    text: msg.text || '',
                    media: msg.media || '',
                    mediaFileName: msg.mediaFileName || '',
                    stickers: msg.stickers || '',
                    time: msg.time,
                    time_text: timeText,
                    seen: msg.seen,
                    position: position,
                    type: type,
                    type_two: msg.type_two || '',
                    lat: msg.lat || '0',
                    lng: msg.lng || '0',
                    reply_id: msg.reply_id || 0,
                    story_id: msg.story_id || 0,
                    product_id: msg.product_id || 0,
                    user_data: senderData
                });
            }

            res.json({
                api_status: 200,
                messages: processedMessages
            });

        } catch (err) {
            console.error('[Messages/Get] Error:', err.message);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to fetch messages'
            });
        }
    };
}

/**
 * POST /api/chat/send
 * Send a message via Node.js (creates in DB + emits via Socket.IO)
 * Replaces: insert_new_message.php
 *
 * Body params:
 *   - recipient_id: (required) recipient user ID
 *   - text: (required if no media) message text
 *   - reply_id: (optional) ID of message being replied to
 *   - story_id: (optional) shared story ID
 */
function sendMessageRoute(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const text = req.body.text || '';
            const replyId = parseInt(req.body.reply_id) || 0;
            const storyId = parseInt(req.body.story_id) || 0;
            const lat = req.body.lat || '0';
            const lng = req.body.lng || '0';

            if (!recipientId || isNaN(recipientId)) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'recipient_id is required'
                });
            }

            if (!text && lat === '0' && lng === '0') {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'text is required (or lat/lng for location)'
                });
            }

            // Verify recipient exists
            const recipient = await ctx.wo_users.findOne({
                attributes: ['user_id'],
                where: { user_id: recipientId }
            });
            if (!recipient) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'Recipient user not found'
                });
            }

            const now = Math.floor(Date.now() / 1000);

            // Create message in database
            const newMessage = await ctx.wo_messages.create({
                from_id: userId,
                to_id: recipientId,
                text: text,
                media: '',
                mediaFileName: '',
                time: now,
                seen: 0,
                reply_id: replyId,
                story_id: storyId,
                lat: lat,
                lng: lng,
                type_two: (req.body.contact ? 'contact' : '')
            });

            // Update chat metadata (conversation)
            const funcs = require('../functions/functions');
            await funcs.updateOrCreate(ctx.wo_userschat, {
                user_id: userId,
                conversation_user_id: recipientId,
            }, {
                time: now,
                user_id: userId,
                conversation_user_id: recipientId,
            });
            await funcs.updateOrCreate(ctx.wo_userschat, {
                user_id: recipientId,
                conversation_user_id: userId,
            }, {
                time: now,
                user_id: recipientId,
                conversation_user_id: userId,
            });

            // Get sender data for Socket.IO emission
            const senderData = await getUserBasicData(ctx, userId);

            const messageData = {
                id: newMessage.id,
                from_id: userId,
                to_id: recipientId,
                text: text,
                media: '',
                mediaFileName: '',
                stickers: '',
                time: now,
                seen: 0,
                reply_id: replyId,
                story_id: storyId,
                lat: lat,
                lng: lng,
                type_two: newMessage.type_two || '',
                user_data: senderData
            };

            // Emit via Socket.IO to recipient (real-time delivery)
            const targetRoom = String(recipientId);
            io.to(targetRoom).emit('new_message', messageData);
            io.to(targetRoom).emit('private_message', messageData);

            // Also emit to sender's other tabs/devices
            const senderRoom = String(userId);
            io.to(senderRoom).emit('new_message', {
                ...messageData,
                self: true
            });

            // Send notification event
            io.to(targetRoom).emit('notification', {
                id: targetRoom,
                username: senderData ? senderData.name : 'User',
                avatar: senderData ? senderData.avatar : '',
                message: text,
                status: 200
            });

            console.log(`[Messages/Send] ${userId} -> ${recipientId}: msg_id=${newMessage.id}`);

            res.json({
                api_status: 200,
                message_data: messageData
            });

        } catch (err) {
            console.error('[Messages/Send] Error:', err.message);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to send message'
            });
        }
    };
}

/**
 * POST /api/chat/seen
 * Mark messages from a user as seen
 * Replaces: seen_messages Socket.IO event for HTTP clients
 *
 * Body params:
 *   - recipient_id: (required) the user whose messages to mark as read
 */
function seenMessagesRoute(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const recipientId = parseInt(req.body.recipient_id);

            if (!recipientId || isNaN(recipientId)) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'recipient_id is required'
                });
            }

            const seen = Math.floor(Date.now() / 1000);

            // Mark all messages from recipient to current user as seen
            await ctx.wo_messages.update(
                { seen: seen },
                {
                    where: {
                        from_id: recipientId,
                        to_id: userId,
                        seen: 0
                    }
                }
            );

            // Notify sender via Socket.IO that messages were read
            const targetRoom = String(recipientId);
            io.to(targetRoom).emit('lastseen', {
                can_seen: 1,
                seen: seen,
                user_id: userId
            });

            res.json({
                api_status: 200,
                message: 'Messages marked as seen'
            });

        } catch (err) {
            console.error('[Messages/Seen] Error:', err.message);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to mark messages as seen'
            });
        }
    };
}

/**
 * POST /api/chat/chats
 * Get list of conversations with last message
 * Replaces: get_chats.php polling for mobile
 *
 * Body params:
 *   - limit: (optional) max conversations, default 20, max 50
 *   - offset: (optional) pagination offset, default 0
 */
function getChatsRoute(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const limit = Math.min(parseInt(req.body.limit) || 20, 50);
            const offset = parseInt(req.body.offset) || 0;

            // Get user's conversations ordered by last activity
            const chats = await ctx.wo_userschat.findAll({
                where: {
                    user_id: userId,
                    conversation_user_id: { [Op.gt]: 0 }
                },
                order: [['time', 'DESC']],
                limit: limit,
                offset: offset,
                raw: true
            });

            const chatList = [];
            for (const chat of chats) {
                // Get conversation partner data
                const partnerData = await getUserBasicData(ctx, chat.conversation_user_id);
                if (!partnerData) continue;

                // Get last message in conversation
                const lastMessage = await ctx.wo_messages.findOne({
                    where: {
                        page_id: 0,
                        [Op.or]: [
                            { from_id: userId, to_id: chat.conversation_user_id, deleted_one: '0' },
                            { from_id: chat.conversation_user_id, to_id: userId, deleted_two: '0' }
                        ]
                    },
                    order: [['id', 'DESC']],
                    raw: true
                });

                // Count unread messages
                const unreadCount = await ctx.wo_messages.count({
                    where: {
                        from_id: chat.conversation_user_id,
                        to_id: userId,
                        seen: 0,
                        deleted_two: '0',
                        page_id: 0
                    }
                });

                let lastMessageData = null;
                if (lastMessage) {
                    const position = lastMessage.from_id === userId ? 'right' : 'left';
                    lastMessageData = {
                        id: lastMessage.id,
                        from_id: lastMessage.from_id,
                        to_id: lastMessage.to_id,
                        text: lastMessage.text || '',
                        media: lastMessage.media || '',
                        time: lastMessage.time,
                        seen: lastMessage.seen,
                        position: position
                    };

                    // Format time
                    if (lastMessage.time) {
                        const now = Math.floor(Date.now() / 1000);
                        const dayAgo = now - 86400;
                        if (lastMessage.time < dayAgo) {
                            const d = new Date(lastMessage.time * 1000);
                            lastMessageData.time_text = String(d.getMonth() + 1).padStart(2, '0') + '.' +
                                                        String(d.getDate()).padStart(2, '0') + '.' +
                                                        String(d.getFullYear()).slice(-2);
                        } else {
                            const d = new Date(lastMessage.time * 1000);
                            lastMessageData.time_text = String(d.getHours()).padStart(2, '0') + ':' +
                                                        String(d.getMinutes()).padStart(2, '0');
                        }
                    }
                }

                chatList.push({
                    chat_id: chat.id,
                    chat_type: 'user',
                    user_id: chat.conversation_user_id,
                    chat_time: chat.time,
                    color: chat.color || '',
                    user_data: partnerData,
                    last_message: lastMessageData,
                    message_count: unreadCount
                });
            }

            res.json({
                api_status: 200,
                data: chatList
            });

        } catch (err) {
            console.error('[Messages/Chats] Error:', err.message);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to fetch chats'
            });
        }
    };
}

/**
 * POST /api/chat/typing
 * Notify recipient that user is typing
 *
 * Body params:
 *   - recipient_id: (required) recipient user ID
 *   - typing: (required) 'true' or 'false'
 */
function typingRoute(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const isTyping = req.body.typing === 'true' || req.body.typing === true;

            if (!recipientId || isNaN(recipientId)) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'recipient_id is required'
                });
            }

            const targetRoom = String(recipientId);
            const event = isTyping ? 'typing' : 'typing_done';

            io.to(targetRoom).emit(event, {
                from_id: userId,
                to_id: recipientId
            });

            res.json({
                api_status: 200,
                message: isTyping ? 'Typing notification sent' : 'Typing done notification sent'
            });

        } catch (err) {
            console.error('[Messages/Typing] Error:', err.message);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to send typing notification'
            });
        }
    };
}

/**
 * Register all messaging routes on the Express app
 */
function registerMessagingRoutes(app, ctx, io) {
    // Auth middleware wrapper
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.post('/api/chat/get', auth, getMessagesRoute(ctx, io));
    app.post('/api/chat/send', auth, sendMessageRoute(ctx, io));
    app.post('/api/chat/seen', auth, seenMessagesRoute(ctx, io));
    app.post('/api/chat/chats', auth, getChatsRoute(ctx, io));
    app.post('/api/chat/typing', auth, typingRoute(ctx, io));

    console.log('[Messaging API] REST endpoints registered:');
    console.log('  POST /api/chat/get    - Fetch message history');
    console.log('  POST /api/chat/send   - Send message');
    console.log('  POST /api/chat/seen   - Mark messages as read');
    console.log('  POST /api/chat/chats  - Get conversations list');
    console.log('  POST /api/chat/typing - Typing indicator');
}

module.exports = { registerMessagingRoutes };
