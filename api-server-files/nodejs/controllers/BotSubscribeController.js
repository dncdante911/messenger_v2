/**
 * BotSubscribeController - User subscribes/unsubscribes to bot events
 *
 * Event: subscribe_bot / unsubscribe_bot
 * Data: { user_id (session), bot_id }
 *
 * Called when a user opens or closes a bot chat.
 * Joins/leaves a room specific to the user-bot pair for targeted delivery.
 */

const BotSubscribeController = async (ctx, data, io, socket) => {
    const { bot_id } = data;

    // Resolve user_id
    let user_id;
    if (data.user_id && ctx.userHashUserId[data.user_id]) {
        user_id = ctx.userHashUserId[data.user_id];
    } else if (socket.userId) {
        user_id = socket.userId;
    } else {
        return;
    }

    if (!bot_id) return;

    // Track user-bot subscription
    if (!ctx.userBotSubscriptions) ctx.userBotSubscriptions = new Map();

    if (!ctx.userBotSubscriptions.has(user_id)) {
        ctx.userBotSubscriptions.set(user_id, new Set());
    }
    ctx.userBotSubscriptions.get(user_id).add(bot_id);

    // Join user-specific bot room for targeted delivery
    const roomName = `user_bot_${user_id}_${bot_id}`;
    socket.join(roomName);

    console.log(`[Bot] 👤 User ${user_id} subscribed to bot ${bot_id}`);
};

const BotUnsubscribeController = async (ctx, data, io, socket) => {
    const { bot_id } = data;

    let user_id;
    if (data.user_id && ctx.userHashUserId[data.user_id]) {
        user_id = ctx.userHashUserId[data.user_id];
    } else if (socket.userId) {
        user_id = socket.userId;
    } else {
        return;
    }

    if (!bot_id) return;

    // Remove subscription
    if (ctx.userBotSubscriptions && ctx.userBotSubscriptions.has(user_id)) {
        ctx.userBotSubscriptions.get(user_id).delete(bot_id);
    }

    const roomName = `user_bot_${user_id}_${bot_id}`;
    socket.leave(roomName);

    console.log(`[Bot] 👤 User ${user_id} unsubscribed from bot ${bot_id}`);
};

module.exports = { BotSubscribeController, BotUnsubscribeController };
