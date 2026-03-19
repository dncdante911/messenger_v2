const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const DisconnectController = async (ctx, reason, io,socket) => {
  console.log('a user disconnected ' + socket.id + " " + reason);
  let hash = ctx.socketIdUserHash[socket.id]
  let user_id = ctx.userHashUserId[hash]
  ctx.userIdCount[user_id] > 0 ? ctx.userIdCount[user_id] = ctx.userIdCount[user_id] - 1 : delete ctx.userIdCount[user_id]
  if (ctx.userIdCount[user_id] === 0) {
      delete ctx.userIdCount[user_id]
      delete ctx.userHashUserId[hash]

      // Cancel pending typing/recording timers for this user to prevent
      // orphaned setTimeout handles in the event loop (memory leak fix).
      // Previously `ctx.userIdExtra = {}` wiped ALL users' timers on every
      // disconnect without calling clearTimeout — handles leaked until they fired.
      const extra = ctx.userIdExtra[user_id]
      if (extra) {
          if (extra.typingTimeout)    clearTimeout(extra.typingTimeout)
          if (extra.recordingTimeout) clearTimeout(extra.recordingTimeout)
          delete ctx.userIdExtra[user_id]
      }

      // Clean up open-chat tracking for the disconnected user so stale entries
      // don't accumulate in ctx.userIdChatOpen indefinitely.
      delete ctx.userIdChatOpen[user_id]

      // emit user logged off
      let followers = await ctx.wo_followers.findAll({
          attributes: ["following_id"],
          where: {
              follower_id: user_id,
              following_id: {
                  [Op.not]: user_id
              }
          },
          raw: true
      })

      const notifySet = new Set(followers.map(f => f.following_id));

      // Also notify users who have an open private chat with this user
      for (const [viewerId, openChats] of Object.entries(ctx.userIdChatOpen)) {
          if (Array.isArray(openChats) && openChats.includes(user_id) && Number(viewerId) !== user_id) {
              notifySet.add(Number(viewerId));
          }
      }

      for (const recipientId of notifySet) {
          await io.to(String(recipientId)).emit("on_user_loggedoff", { user_id: user_id })
      }
  }
  if (ctx.userIdSocket[user_id]) {
      ctx.userIdSocket[user_id] = ctx.userIdSocket[user_id].filter(d => d.id != socket.id)
      // Remove the entry entirely when no sockets remain — avoids accumulating
      // empty arrays for every user that has ever connected (memory leak fix).
      if (ctx.userIdSocket[user_id].length === 0) {
          delete ctx.userIdSocket[user_id]
      }
  }
  delete ctx.socketIdUserHash[socket.id]
};

module.exports = { DisconnectController };
