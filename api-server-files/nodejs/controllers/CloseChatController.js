const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const CloseChatController = async (ctx, data, io, socket) => {
    if (!data || !data.user_id) return;
    try {
        const userId = ctx.userHashUserId[data.user_id];
        if (!userId) return;
        if (data.group) {
            if (ctx.userIdGroupChatOpen[userId] && ctx.userIdGroupChatOpen[userId].length) {
                ctx.userIdGroupChatOpen[userId] = ctx.userIdGroupChatOpen[userId].filter(d => d != data.recipient_id)
            }
        } else if (ctx.userIdChatOpen[userId] && ctx.userIdChatOpen[userId].length) {
            ctx.userIdChatOpen[userId] = ctx.userIdChatOpen[userId].filter(d => d != data.recipient_id);
        }
    } catch (err) {
        console.error('[CloseChatController] error:', err.message)
    }
};

module.exports = { CloseChatController };