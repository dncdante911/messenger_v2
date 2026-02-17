/**
 * BotTypingController - Bot typing indicator
 *
 * Event: bot_typing
 * Data: { bot_id, chat_id, is_typing }
 *
 * Sends typing indicator from bot to user in real-time.
 */

const BotTypingController = async (ctx, data, io, socket) => {
    const { bot_id, chat_id, is_typing } = data;

    if (!bot_id || !chat_id) return;

    // Verify authenticated bot
    if (socket.botId !== bot_id) return;

    const payload = {
        event: 'bot_typing',
        bot_id: bot_id,
        is_typing: is_typing !== false
    };

    // Send to user's room
    const targetUserId = String(chat_id);
    io.to(targetUserId).emit('bot_typing', payload);
    io.to(`user_bot_${chat_id}_${bot_id}`).emit('bot_typing', payload);
};

module.exports = { BotTypingController };
