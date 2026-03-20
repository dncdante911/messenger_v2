const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const TypingController = async (ctx, data, io, socket) => {
  if (!data || !data.user_id || !data.recipient_id) {
      return;
  }
  try {
      let fromUser = await ctx.wo_users.findOne({
          where: {
              user_id: {
                  [Op.eq]: ctx.userHashUserId[data.user_id]
              }
          }
      })
      if (!fromUser) {
          console.log("Skipping no from_user")
          return
      }
      const userId = ctx.userHashUserId[data.user_id];
      if (ctx.userIdExtra[userId]) {
          if (ctx.userIdExtra[userId].typingTimeout) {
              clearTimeout(ctx.userIdExtra[userId].typingTimeout)
          }
          ctx.userIdExtra[userId].typingTimeout = setTimeout(async () => {
              if (ctx.userIdExtra[userId]) ctx.userIdExtra[userId].typingTimeout = null
              try { await socketEvents.typingDone(ctx, io, data, userId) } catch (e) {
                  console.error('[TypingController] typingDone error:', e.message)
              }
          }, 7000)
      } else {
          ctx.userIdExtra[userId] = {
              typingTimeout: setTimeout(async () => {
                  if (ctx.userIdExtra[userId]) ctx.userIdExtra[userId].typingTimeout = null
                  try { await socketEvents.typingDone(ctx, io, data, userId) } catch (e) {
                      console.error('[TypingController] typingDone error:', e.message)
                  }
              }, 7000)
          }
      }
      // await funcs.Wo_RegisterTyping(data.user_id, data.recipient_id, 1)
      await socketEvents.typing(ctx, io, fromUser.avatar, data.recipient_id, userId)
  } catch (err) {
      console.error('[TypingController] error:', err.message)
  }
};

module.exports = { TypingController };