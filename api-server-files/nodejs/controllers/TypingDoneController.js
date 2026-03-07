const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const TypingDoneController = async (ctx, data, io,socket) => {
  const userId = ctx.userHashUserId[data.user_id]
  // Cancel the server-side auto-stop timer so it doesn't fire a second time
  if (ctx.userIdExtra[userId] && ctx.userIdExtra[userId].typingTimeout) {
      clearTimeout(ctx.userIdExtra[userId].typingTimeout)
      ctx.userIdExtra[userId].typingTimeout = null
  }
  await socketEvents.typingDone(ctx, io, data, userId)
};

module.exports = { TypingDoneController };