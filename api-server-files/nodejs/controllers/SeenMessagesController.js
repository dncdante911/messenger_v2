const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const SeenMessagesController = async (ctx, data, io, socket) => {
    var current_user_id = 0;
    if (ctx.userHashUserId[data.user_id]) {
        current_user_id = ctx.userHashUserId[data.user_id];
    } else if (data.current_user_id) {
        current_user_id = data.current_user_id;
    }
    if (data.user_id && data.recipient_id && current_user_id > 0) {
        try {
            var seen = Math.floor(Date.now() / 1000);
            await ctx.wo_messages.update({ seen: seen }, {
                where: { from_id: data.recipient_id, to_id: current_user_id }
            })
            let seenMsg = funcs.Wo_Time_Elapsed_String(ctx, seen)
            await io.to(String(data.recipient_id)).emit("lastseen", {
                can_seen: 1,
                time: seenMsg,
                seen: seenMsg,
                message_id: data.message_id,
                user_id: current_user_id,
            })
        } catch (err) {
            console.error('[SeenMessagesController] error:', err.message)
        }
    }
};

module.exports = { SeenMessagesController };