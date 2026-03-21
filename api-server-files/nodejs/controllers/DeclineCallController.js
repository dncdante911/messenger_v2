const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const DeclineCallController = async (ctx, data, io, socket) => {
    if (!data || !data.user_id) return;
    try {
        const user_id = ctx.userHashUserId[data.user_id];
        if (!user_id) return;
        await io.to(String(user_id)).emit('decline_call', {});
    } catch (err) {
        console.error('[DeclineCallController] error:', err.message)
    }
};

module.exports = { DeclineCallController };