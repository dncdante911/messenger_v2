const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const ActiveMessageUserChangeController = async (ctx, data, io, socket) => {
    if (!data || !data.from_id) return;
    const userId = ctx.userHashUserId[data.from_id];
    if (!userId) return;
    try {
        if (data.group) {
            if (ctx.userIdExtra[userId]) {
                ctx.userIdExtra[userId].active_message_group_id = data.group_id;
                await socketEvents.updateMessageGroupsList(ctx, io, userId)
                return
            }
            ctx.userIdExtra[userId] = { active_message_group_id: data.group_id };
        } else {
            if (ctx.userIdExtra[userId]) {
                ctx.userIdExtra[userId].active_message_user_id = data.user_id;
                await socketEvents.updateMessageGroupsList(ctx, io, userId)
                return
            }
            ctx.userIdExtra[userId] = { active_message_user_id: data.user_id };
        }
        await socketEvents.updateMessageGroupsList(ctx, io, userId)
    } catch (err) {
        console.error('[ActiveMessageUserChangeController] error:', err.message)
    }
};

module.exports = { ActiveMessageUserChangeController };