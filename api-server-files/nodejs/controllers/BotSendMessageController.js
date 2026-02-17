/**
 * BotSendMessageController - Bot sends a message to a user
 *
 * Event: bot_message
 * Data: { bot_id, chat_id, text, message_id, reply_markup, media }
 *
 * Called when a bot sends a message. Stores in Wo_Bot_Messages and delivers
 * to the user in real-time via their socket room.
 */

const { Op } = require("sequelize");

const BotSendMessageController = async (ctx, data, io, socket) => {
    const { bot_id, chat_id, text, message_id, reply_markup, media } = data;

    if (!bot_id || !chat_id) {
        socket.emit('bot_error', { error: 'bot_id and chat_id are required' });
        return;
    }

    // Verify this socket is the authenticated bot
    if (socket.botId !== bot_id) {
        socket.emit('bot_error', { error: 'Unauthorized: bot_id mismatch' });
        return;
    }

    try {
        // Store bot message in database
        const botMessage = await ctx.wo_bot_messages.create({
            bot_id: bot_id,
            chat_id: String(chat_id),
            chat_type: 'private',
            direction: 'outgoing',
            message_id: message_id || null,
            text: text || null,
            media_type: media ? media.type : null,
            media_url: media ? media.url : null,
            reply_markup: reply_markup ? JSON.stringify(reply_markup) : null,
            processed: 1,
            processed_at: new Date()
        });

        const payload = {
            event: 'bot_message',
            bot_id: bot_id,
            message_id: botMessage.id,
            text: text,
            media: media || null,
            reply_markup: reply_markup || null,
            timestamp: Date.now()
        };

        // Deliver to user via their room (user joins String(user_id) room in JoinController)
        const targetUserId = String(chat_id);
        io.to(targetUserId).emit('bot_message', payload);

        // Also deliver to bot-specific user room (for users subscribed to this bot)
        io.to(`user_bot_${chat_id}_${bot_id}`).emit('bot_message', payload);

        // Update bot stats
        await ctx.wo_bots.increment('messages_sent', { where: { bot_id: bot_id } });

        // Update bot-user interaction
        await ctx.wo_bot_users.findOrCreate({
            where: { bot_id: bot_id, user_id: parseInt(chat_id) },
            defaults: {
                bot_id: bot_id,
                user_id: parseInt(chat_id),
                last_interaction_at: new Date()
            }
        });
        await ctx.wo_bot_users.update(
            { last_interaction_at: new Date() },
            { where: { bot_id: bot_id, user_id: parseInt(chat_id) } }
        );

        console.log(`[Bot] 📤 Message from ${bot_id} to user ${chat_id}: ${(text || '').substring(0, 50)}`);

    } catch (err) {
        console.error(`[Bot] ❌ Send message error:`, err.message);
        socket.emit('bot_error', { error: 'Failed to send message' });
    }
};

module.exports = { BotSendMessageController };
