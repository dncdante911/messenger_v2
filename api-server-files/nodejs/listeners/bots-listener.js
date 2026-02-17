/**
 * WorldMates Bot API - Socket.IO Listener
 *
 * Handles two types of bot socket communication:
 *
 * 1. BOT-SIDE (via /bots namespace):
 *    - bot_auth: Bot authenticates with bot_token
 *    - bot_message: Bot sends message to user
 *    - bot_typing: Bot typing indicator
 *    - callback_answer: Bot answers inline button click
 *    - update_markup: Bot updates inline keyboard
 *
 * 2. USER-SIDE (via main namespace, registered in listeners.js):
 *    - subscribe_bot: User opens bot chat
 *    - unsubscribe_bot: User leaves bot chat
 *    - user_to_bot: User sends message/command to bot
 *    - bot_callback_query: User clicks inline button
 *    - bot_poll_vote: User votes in a poll
 *
 * Integration in listeners.js:
 *   const registerBotsListeners = require('./bots-listener');
 *   await registerBotsListeners(socket, io, ctx);
 *
 * Integration in main.js (for /bots namespace):
 *   const { initializeBotNamespace } = require('./listeners/bots-listener');
 *   initializeBotNamespace(io, ctx);
 */

const { BotAuthController } = require('../controllers/BotAuthController');
const { BotSendMessageController } = require('../controllers/BotSendMessageController');
const { BotUserMessageController } = require('../controllers/BotUserMessageController');
const { BotTypingController } = require('../controllers/BotTypingController');
const { BotCallbackAnswerController } = require('../controllers/BotCallbackAnswerController');
const { BotCallbackQueryController } = require('../controllers/BotCallbackQueryController');
const { BotUpdateMarkupController } = require('../controllers/BotUpdateMarkupController');
const { BotSubscribeController, BotUnsubscribeController } = require('../controllers/BotSubscribeController');
const { BotPollVoteController } = require('../controllers/BotPollVoteController');
const { BotDisconnectController } = require('../controllers/BotDisconnectController');

/**
 * Register user-side bot event handlers on the MAIN namespace.
 * Called once per socket connection from listeners.js.
 *
 * @param {Object} socket - Socket.IO socket (main namespace)
 * @param {Object} io - Socket.IO server instance
 * @param {Object} ctx - Application context with models and state
 */
async function registerBotsListeners(socket, io, ctx) {
    // Initialize bot data structures in ctx if needed
    if (!ctx.botSockets) ctx.botSockets = new Map();
    if (!ctx.userBotSubscriptions) ctx.userBotSubscriptions = new Map();

    // ==================== USER-SIDE BOT EVENTS ====================

    // User opens a bot chat (subscribe to real-time updates)
    socket.on('subscribe_bot', async (data) => {
        BotSubscribeController(ctx, data, io, socket);
    });

    // User leaves a bot chat
    socket.on('unsubscribe_bot', async (data) => {
        BotUnsubscribeController(ctx, data, io, socket);
    });

    // User sends message or command to bot
    socket.on('user_to_bot', async (data) => {
        BotUserMessageController(ctx, data, io, socket);
    });

    // User clicks inline keyboard button in bot message
    socket.on('bot_callback_query', async (data) => {
        BotCallbackQueryController(ctx, data, io, socket);
    });

    // User votes in a bot poll
    socket.on('bot_poll_vote', async (data) => {
        BotPollVoteController(ctx, data, io, socket);
    });
}

/**
 * Initialize the /bots namespace for bot-side connections.
 * Called once from main.js after io is created.
 *
 * @param {Object} io - Socket.IO server instance
 * @param {Object} ctx - Application context with models and state
 */
function initializeBotNamespace(io, ctx) {
    // Initialize bot data structures
    if (!ctx.botSockets) ctx.botSockets = new Map();
    if (!ctx.userBotSubscriptions) ctx.userBotSubscriptions = new Map();

    const botNamespace = io.of('/bots');

    botNamespace.on('connection', (socket) => {
        console.log(`[Bot] 🔌 Bot socket connected: ${socket.id}`);

        // Bot authenticates with token
        socket.on('bot_auth', async (data) => {
            BotAuthController(ctx, data, io, socket);
        });

        // Bot sends message to user
        socket.on('bot_message', async (data) => {
            BotSendMessageController(ctx, data, io, socket);
        });

        // Bot typing indicator
        socket.on('bot_typing', async (data) => {
            BotTypingController(ctx, data, io, socket);
        });

        // Bot answers callback query (inline button click)
        socket.on('callback_answer', async (data) => {
            BotCallbackAnswerController(ctx, data, io, socket);
        });

        // Bot updates inline keyboard on a message
        socket.on('update_markup', async (data) => {
            BotUpdateMarkupController(ctx, data, io, socket);
        });

        // Bot disconnect
        socket.on('disconnect', async (reason) => {
            BotDisconnectController(ctx, reason, io, socket);
        });
    });

    console.log('[Bot] ✅ Bot Socket.IO namespace /bots initialized');

    // ==================== Redis integration for bot messages ====================
    // Subscribe to 'bot_messages' Redis channel for PHP -> Node.js delivery
    initBotRedisSub(io, ctx);
}

/**
 * Redis subscriber for bot messages.
 * When PHP backend (bot_api.php) sends a bot message, it publishes to Redis.
 * This handler picks it up and delivers in real-time via Socket.IO.
 */
let botRedisSubscribed = false;

async function initBotRedisSub(io, ctx) {
    if (botRedisSubscribed) return;

    try {
        const redis = require("redis");
        const sub = redis.createClient({ url: 'redis://127.0.0.1:6379' });

        sub.on('error', (err) => {
            // Silently ignore Redis errors - bot messages will fall back to polling
            if (!err.message.includes('ECONNREFUSED')) {
                console.log('[Bot] Redis Sub Error:', err.message);
            }
        });

        await sub.connect();
        console.log('[Bot] ✅ Connected to Redis for bot messages');

        await sub.subscribe('bot_messages', async (message) => {
            try {
                const decoded = JSON.parse(message);
                if (!decoded || !decoded.bot_id || !decoded.chat_id) return;

                const payload = {
                    event: 'bot_message',
                    bot_id: decoded.bot_id,
                    message_id: decoded.message_id,
                    text: decoded.text,
                    media: decoded.media || null,
                    reply_markup: decoded.reply_markup || null,
                    timestamp: Date.now()
                };

                const targetUserId = String(decoded.chat_id);

                // Deliver to user via main namespace room
                io.to(targetUserId).emit('bot_message', payload);

                // Also deliver to bot-specific room
                io.to(`user_bot_${decoded.chat_id}_${decoded.bot_id}`).emit('bot_message', payload);

                console.log(`[Bot] 📨 Redis: delivered bot message from ${decoded.bot_id} to user ${decoded.chat_id}`);

            } catch (e) {
                console.log('[Bot] Redis message parse error:', e.message);
            }
        });

        botRedisSubscribed = true;
    } catch (e) {
        console.log('[Bot] Redis not available for bot messages (will use socket delivery only):', e.message);
    }
}

/**
 * Get bot connection statistics.
 * Used by health check / admin endpoints.
 */
function getBotStats(ctx) {
    return {
        connectedBots: ctx.botSockets ? ctx.botSockets.size : 0,
        activeSubscriptions: ctx.userBotSubscriptions
            ? Array.from(ctx.userBotSubscriptions.values()).reduce((sum, set) => sum + set.size, 0)
            : 0,
        botIds: ctx.botSockets ? Array.from(ctx.botSockets.keys()) : []
    };
}

/**
 * Push a bot message from the server side (used by REST API).
 * Called when PHP backend needs to push a bot message via Node.js.
 *
 * @param {Object} io - Socket.IO server instance
 * @param {string} botId - Bot identifier
 * @param {string} chatId - Target user/chat ID
 * @param {Object} message - Message object { text, media, reply_markup, message_id }
 */
function pushBotMessage(io, botId, chatId, message) {
    const payload = {
        event: 'bot_message',
        bot_id: botId,
        ...message,
        timestamp: Date.now()
    };

    io.to(String(chatId)).emit('bot_message', payload);
    io.to(`user_bot_${chatId}_${botId}`).emit('bot_message', payload);
}

module.exports = registerBotsListeners;
module.exports.registerBotsListeners = registerBotsListeners;
module.exports.initializeBotNamespace = initializeBotNamespace;
module.exports.getBotStats = getBotStats;
module.exports.pushBotMessage = pushBotMessage;
