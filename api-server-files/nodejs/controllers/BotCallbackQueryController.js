/**
 * BotCallbackQueryController - User clicks an inline keyboard button
 *
 * Event: bot_callback_query
 * Data: { user_id (session), bot_id, message_id, callback_data }
 *
 * Called from main namespace when user taps an inline button in a bot message.
 * Creates a callback query record and forwards to the bot.
 */

const { Op } = require("sequelize");

const BotCallbackQueryController = async (ctx, data, io, socket) => {
    const { bot_id, message_id, callback_data } = data;

    // Resolve user_id
    let user_id;
    if (data.user_id && ctx.userHashUserId[data.user_id]) {
        user_id = ctx.userHashUserId[data.user_id];
    } else if (socket.userId) {
        user_id = socket.userId;
    } else {
        console.log('[Bot] ❌ bot_callback_query: cannot resolve user_id');
        return;
    }

    if (!bot_id || !callback_data) {
        console.log('[Bot] ❌ bot_callback_query: bot_id and callback_data required');
        return;
    }

    try {
        // Store callback query
        const callbackQuery = await ctx.wo_bot_callbacks.create({
            bot_id: bot_id,
            user_id: user_id,
            message_id: message_id || null,
            callback_data: callback_data,
            answered: 0
        });

        // Build payload for bot
        const payload = {
            event: 'callback_query',
            callback_query_id: callbackQuery.id,
            user_id: user_id,
            message_id: message_id,
            data: callback_data,
            timestamp: Date.now()
        };

        // Forward to bot socket if connected
        if (ctx.botSockets && ctx.botSockets.has(bot_id)) {
            const botSocket = ctx.botSockets.get(bot_id);
            botSocket.emit('callback_query', payload);
            console.log(`[Bot] 🔘 Callback query from user ${user_id} -> bot ${bot_id}: ${callback_data}`);
        } else {
            console.log(`[Bot] 🔘 Callback query from user ${user_id} -> bot ${bot_id} (offline, queued)`);
        }

    } catch (err) {
        console.error(`[Bot] ❌ Callback query error:`, err.message);
    }
};

module.exports = { BotCallbackQueryController };
