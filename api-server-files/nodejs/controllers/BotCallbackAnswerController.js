/**
 * BotCallbackAnswerController - Bot answers an inline button click
 *
 * Event: callback_answer
 * Data: { bot_id, callback_query_id, user_id, text, show_alert }
 *
 * When user clicks an inline keyboard button, bot sends back an answer
 * (toast notification or alert). Stores in Wo_Bot_Callbacks and delivers to user.
 */

const { Op } = require("sequelize");

const BotCallbackAnswerController = async (ctx, data, io, socket) => {
    const { bot_id, callback_query_id, user_id, text, show_alert } = data;

    if (!bot_id || !user_id) return;

    // Verify authenticated bot
    if (socket.botId !== bot_id) {
        socket.emit('bot_error', { error: 'Unauthorized: bot_id mismatch' });
        return;
    }

    try {
        // Update callback query as answered if we have the ID
        if (callback_query_id) {
            await ctx.wo_bot_callbacks.update(
                {
                    answered: 1,
                    answer_text: text || null,
                    answer_show_alert: show_alert ? 1 : 0
                },
                { where: { id: callback_query_id, bot_id: bot_id } }
            );
        }

        const payload = {
            event: 'callback_answer',
            callback_query_id: callback_query_id,
            text: text || '',
            show_alert: show_alert || false
        };

        // Deliver to user
        const targetUserId = String(user_id);
        io.to(targetUserId).emit('callback_answer', payload);
        io.to(`user_bot_${user_id}_${bot_id}`).emit('callback_answer', payload);

        console.log(`[Bot] 🔘 Callback answer from ${bot_id} to user ${user_id}`);

    } catch (err) {
        console.error(`[Bot] ❌ Callback answer error:`, err.message);
    }
};

module.exports = { BotCallbackAnswerController };
