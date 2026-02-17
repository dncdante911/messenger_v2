const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const IsChatOnController = async (ctx, data, io, socket) => {
    console.log("📖 IsChatOnController START:", {
        user_id: data.user_id ? data.user_id.substring(0, 10) + '...' : 'empty',
        recipient_id: data.recipient_id,
        isGroup: data.isGroup
    });

    let last_message = {}

    if (data.message_id) {
        last_message = await ctx.wo_messages.findOne({
            where: {
                id: data.message_id
            }
        })
    }

    let toUser = await ctx.wo_users.findOne({
        where: {
            user_id: {
                [Op.eq]: ctx.userHashUserId[data.user_id]
            }
        }
    })

    if (toUser == null) {
        console.log("⚠️ toUser not found");
        return;
    }

    if (data.isGroup) {
        ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]].length ? ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]].push(data.recipient_id) : ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] = [data.recipient_id]
    }
    else {
        ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]].length ? ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]].push(data.recipient_id) : ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] = [data.recipient_id]
    }

    if (ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]].length) {
        let arr = new Set(ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]])
        ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] = Array.from(arr)
    }

    if (ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]].length) {
        let arr = new Set(ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]])
        ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] = Array.from(arr)
    }

    if (last_message.seen == 0) {
        var seen = Math.floor(Date.now() / 1000);
        let seenMsg = funcs.Wo_Time_Elapsed_String(ctx, seen)

        // 🔥 КРИТИЧНО: Емітити в РЯДКОВИЙ room
        const recipientRoom = String(data.recipient_id);
        await io.to(recipientRoom).emit("lastseen", {
            can_seen: 1,
            time: seenMsg,
            seen: seenMsg,
            message_id: data.message_id,
            user_id: ctx.userHashUserId[data.user_id]
        })
        console.log(`📤 Emitted lastseen to room: "${recipientRoom}"`);
    }

    if (last_message.seen > 0) {
        let seenMsg = funcs.Wo_Time_Elapsed_String(ctx, last_message.seen)

        // 🔥 КРИТИЧНО: Емітити в РЯДКОВИЙ room
        const recipientRoom = String(data.recipient_id);
        await io.to(recipientRoom).emit("lastseen", {
            can_seen: 1,
            time: seenMsg,
            seen: seenMsg,
            message_id: last_message.id,
            user_id: ctx.userHashUserId[data.user_id]
        })
        console.log(`📤 Emitted lastseen to room: "${recipientRoom}"`);
    }
    else {
        await socketEvents.unseen(ctx, socket)
    }

    let user_id = ctx.userHashUserId[data.user_id]
    let unseenmessages = await funcs.Wo_CountUnseenMessages(ctx, user_id);

    // 🔥 КРИТИЧНО: Емітити в РЯДКОВИЙ room
    const userRoom = String(user_id);
    await io.to(userRoom).emit("messages_count", { count: unseenmessages })
    console.log(`📤 Emitted messages_count to room: "${userRoom}", count: ${unseenmessages}`);
};

module.exports = { IsChatOnController };