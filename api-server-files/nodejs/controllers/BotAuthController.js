/**
 * BotAuthController - Authenticates a bot via Socket.IO
 *
 * Event: bot_auth
 * Data: { bot_id, bot_token }
 * Response: { status, bot_id, display_name } or { error }
 *
 * Called when a bot connects to the /bots namespace and needs to authenticate.
 * Validates bot_token against Wo_Bots table.
 */

const { Op } = require("sequelize");

const BotAuthController = async (ctx, data, io, socket, callback) => {
    const { bot_id, bot_token } = data;

    if (!bot_id || !bot_token) {
        socket.emit('auth_error', { error: 'bot_id and bot_token are required' });
        return;
    }

    try {
        // Verify bot token
        const bot = await ctx.wo_bots.findOne({
            where: {
                bot_id: bot_id,
                bot_token: bot_token,
                status: 'active'
            }
        });

        if (!bot) {
            console.log(`[Bot] ❌ Auth failed for bot_id=${bot_id}`);
            socket.emit('auth_error', { error: 'Invalid bot_id or bot_token, or bot is not active' });
            return;
        }

        // Store bot connection in context
        if (!ctx.botSockets) ctx.botSockets = new Map();
        ctx.botSockets.set(bot_id, socket);

        // Store bot info on socket for later use
        socket.botId = bot_id;
        socket.botOwnerId = bot.owner_id;
        socket.isBot = true;

        // Join bot-specific room
        socket.join(`bot_${bot_id}`);

        // Update last active
        await ctx.wo_bots.update(
            { last_active_at: new Date() },
            { where: { bot_id: bot_id } }
        );

        console.log(`[Bot] ✅ Authenticated: ${bot_id} (${bot.display_name})`);
        socket.emit('auth_success', {
            status: 200,
            bot_id: bot_id,
            display_name: bot.display_name,
            username: bot.username
        });

    } catch (err) {
        console.error(`[Bot] ❌ Auth error for ${bot_id}:`, err.message);
        socket.emit('auth_error', { error: 'Internal server error' });
    }
};

module.exports = { BotAuthController };
