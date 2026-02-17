/**
 * BotUpdateMarkupController - Bot updates inline keyboard on a message
 *
 * Event: update_markup
 * Data: { bot_id, chat_id, message_id, reply_markup }
 *
 * Used when a bot needs to update the inline keyboard of an existing message
 * (e.g., after a vote, toggle buttons, pagination).
 */

const { Op } = require("sequelize");

const BotUpdateMarkupController = async (ctx, data, io, socket) => {
    const { bot_id, chat_id, message_id, reply_markup } = data;

    if (!bot_id || !chat_id || !message_id) return;

    // Verify authenticated bot
    if (socket.botId !== bot_id) {
        socket.emit('bot_error', { error: 'Unauthorized: bot_id mismatch' });
        return;
    }

    try {
        // Update reply_markup in database
        await ctx.wo_bot_messages.update(
            { reply_markup: reply_markup ? JSON.stringify(reply_markup) : null },
            { where: { id: message_id, bot_id: bot_id } }
        );

        const payload = {
            event: 'update_markup',
            bot_id: bot_id,
            message_id: message_id,
            reply_markup: reply_markup
        };

        // Deliver to user
        const targetUserId = String(chat_id);
        io.to(targetUserId).emit('update_markup', payload);
        io.to(`user_bot_${chat_id}_${bot_id}`).emit('update_markup', payload);

        console.log(`[Bot] ⌨️ Markup update from ${bot_id} for message ${message_id}`);

    } catch (err) {
        console.error(`[Bot] ❌ Update markup error:`, err.message);
    }
};

module.exports = { BotUpdateMarkupController };
