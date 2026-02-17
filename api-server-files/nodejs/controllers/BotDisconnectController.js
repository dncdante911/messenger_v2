/**
 * BotDisconnectController - Handles bot or user disconnect for bot cleanup
 *
 * Called from the bots listener when a socket disconnects.
 * Cleans up bot connections and user subscriptions.
 */

const BotDisconnectController = async (ctx, reason, io, socket) => {
    // Clean up bot socket connection
    if (socket.botId && ctx.botSockets) {
        ctx.botSockets.delete(socket.botId);
        console.log(`[Bot] ❌ Bot disconnected: ${socket.botId} (${reason})`);
    }

    // Clean up user bot subscriptions
    if (socket.userId && ctx.userBotSubscriptions) {
        const subs = ctx.userBotSubscriptions.get(socket.userId);
        if (subs) {
            subs.clear();
            ctx.userBotSubscriptions.delete(socket.userId);
        }
    }
};

module.exports = { BotDisconnectController };
