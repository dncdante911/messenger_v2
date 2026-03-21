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
        let userlastseen_status = await ctx.wo_users.findOne({
            attributes: ["status"],
            where: { user_id: userId }
        })
        if (userlastseen_status && userlastseen_status.status == 0) {
            await funcs.Wo_LastSeen(ctx, userId)
        }
    } catch (err) {
        console.error('[PingForLastseenController] error:', err.message)
    }
};

module.exports = { PingForLastseenController };