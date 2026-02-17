/**
 * BotUserMessageController - User sends a message/command to a bot
 *
 * Event: user_to_bot
 * Data: { user_id (session_id), bot_id, text, is_command, command_name, command_args, callback_data }
 *
 * Called from the main namespace when a user sends a message to a bot.
 * Stores the message, increments counters, and forwards to the bot's socket if connected.
 */

const { Op } = require("sequelize");

const BotUserMessageController = async (ctx, data, io, socket) => {
    const { bot_id, text, is_command, command_name, command_args, callback_data } = data;

    // Resolve numeric user_id from session
    let user_id;
    if (data.user_id && ctx.userHashUserId[data.user_id]) {
        user_id = ctx.userHashUserId[data.user_id];
    } else if (socket.userId) {
        user_id = socket.userId;
    } else {
        console.log('[Bot] ❌ user_to_bot: cannot resolve user_id');
        return;
    }

    if (!bot_id) {
        console.log('[Bot] ❌ user_to_bot: bot_id is required');
        return;
    }

    try {
        // Verify bot exists and is active
        const bot = await ctx.wo_bots.findOne({
            where: { bot_id: bot_id, status: 'active' }
        });

        if (!bot) {
            socket.emit('bot_error', { error: 'Bot not found or inactive', bot_id: bot_id });
            return;
        }

        // Store incoming message
        const botMessage = await ctx.wo_bot_messages.create({
            bot_id: bot_id,
            chat_id: String(user_id),
            chat_type: 'private',
            direction: 'incoming',
            text: text || null,
            callback_data: callback_data || null,
            is_command: is_command ? 1 : 0,
            command_name: command_name || null,
            command_args: command_args || null,
            processed: 0
        });

        // Update bot stats
        await ctx.wo_bots.increment('messages_received', { where: { bot_id: bot_id } });

        // Update or create bot-user record
        const [botUser, created] = await ctx.wo_bot_users.findOrCreate({
            where: { bot_id: bot_id, user_id: user_id },
            defaults: {
                bot_id: bot_id,
                user_id: user_id,
                last_interaction_at: new Date(),
                messages_count: 1
            }
        });

        if (!created) {
            await ctx.wo_bot_users.update({
                last_interaction_at: new Date(),
                messages_count: botUser.messages_count + 1
            }, {
                where: { bot_id: bot_id, user_id: user_id }
            });
        } else {
            // New user - increment total_users
            await ctx.wo_bots.increment('total_users', { where: { bot_id: bot_id } });
        }

        // Build payload for bot
        const payload = {
            event: 'user_message',
            user_id: user_id,
            message_id: botMessage.id,
            text: text,
            is_command: is_command || false,
            command_name: command_name || null,
            command_args: command_args || null,
            callback_data: callback_data || null,
            timestamp: Date.now()
        };

        // Forward to bot's socket if connected
        if (ctx.botSockets && ctx.botSockets.has(bot_id)) {
            const botSocket = ctx.botSockets.get(bot_id);
            botSocket.emit('user_message', payload);
            console.log(`[Bot] 📥 User ${user_id} -> Bot ${bot_id}: ${text || callback_data || '(empty)'}`);
        } else {
            // Bot not connected via socket - message stored for polling/webhook
            console.log(`[Bot] 📥 User ${user_id} -> Bot ${bot_id} (offline, queued): ${text || callback_data || '(empty)'}`);
        }

    } catch (err) {
        console.error(`[Bot] ❌ User message error:`, err.message);
    }
};

module.exports = { BotUserMessageController };
