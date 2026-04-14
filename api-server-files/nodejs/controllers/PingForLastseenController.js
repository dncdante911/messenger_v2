const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const PingForLastseenController = async (ctx, data, io) => {
    if (!data || !data.user_id) return;
    const userId = ctx.userHashUserId[data.user_id];
    if (!userId) return;
    try {
        // Always update lastseen on every ping — the old `status == 0` guard
        // prevented updates whenever the REST login had set status=1 in the DB,
        // causing lastseen to stay frozen at the last time the user properly
        // disconnected (sometimes months old).  If the socket is alive and the
        // client is pinging, the timestamp must be refreshed unconditionally.
        await funcs.Wo_LastSeen(ctx, userId);
    } catch (err) {
        console.error('[PingForLastseenController] error:', err.message)
    }
};

module.exports = { PingForLastseenController };