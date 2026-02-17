const funcs = require('../functions/functions')
const compiledTemplates = require('../compiledTemplates/compiledTemplates')
const socketEvents = require('../events/events')
const { Sequelize, Op, DataTypes } = require("sequelize");
const striptags = require('striptags');
const moment = require("moment")

const JoinController = async (ctx, data, io, socket, callback) => {
    console.log("🔥 JoinController START:", {
        session_id: data.user_id ? data.user_id.substring(0, 10) + '...' : 'empty',
        socket_id: socket.id
    });

    if (data.user_id === '') {
        console.log("❌ Killing connection: user_id not received")
        socket.disconnect(true)
        return
    }

    // Знайти user_id за session_id (access_token)
    let user_id = await ctx.wo_appssessions.findOne({
        attributes: ["user_id"],
        where: {
            session_id: data.user_id
        }
    })

    if (user_id == null) {
        console.log("❌ User is not found! Session:", data.user_id.substring(0, 10) + '...')
        socket.disconnect(true)
        return;
    }

    user_id = user_id.user_id;
    console.log("✅ User found: numeric user_id =", user_id);

    let user_status = await ctx.wo_users.findOne({
        attributes: ["status"],
        where: {
            user_id: user_id
        }
    })
    user_status = user_status.status;

    ctx.socketIdUserHash[socket.id] = data.user_id;
    ctx.userIdSocket[user_id] ? ctx.userIdSocket[user_id].push(socket) : ctx.userIdSocket[user_id] = [socket]
    ctx.userHashUserId[data.user_id] = user_id;
    ctx.userIdCount[user_id] = ctx.userIdCount[user_id] ? ctx.userIdCount[user_id] + 1 : 1;

    if (data.recipient_ids && data.recipient_ids.length) {
        for (let recipient_id of data.recipient_ids) {
            ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]].length ? ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]].push(recipient_id) : ctx.userIdChatOpen[ctx.userHashUserId[data.user_id]] = [recipient_id]
        }
    }

    if (data.recipient_group_ids && data.recipient_group_ids.length) {
        for (let recipient_id of data.recipient_group_ids) {
            ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] && ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]].length ? ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]].push(recipient_id) : ctx.userIdGroupChatOpen[ctx.userHashUserId[data.user_id]] = [recipient_id]
        }
    }

    await socketEvents.emitUserStatus(ctx, socket, ctx.userHashUserId[data.user_id])

    if (user_status == 0) {
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

        for (let follow of followers) {
            await io.to(follow.following_id).emit("on_user_loggedin", { user_id: user_id })
        }
    }

    // 🔥 КРИТИЧНО: Приєднатися до room з РЯДКОВИМ user_id
    // Redis емітує в String(user_id), тому room має бути рядком!
    const roomName = String(user_id);
    socket.join(roomName);
    console.log(`✅ Socket joined room: "${roomName}" (type: ${typeof roomName})`);

    // ДОДАТКОВО: Приєднатися також до числового варіанту (для сумісності)
    socket.join(user_id);
    console.log(`✅ Socket joined room: ${user_id} (type: ${typeof user_id})`);

    // Зберегти user_id в socket для подальшого використання
    socket.userId = user_id;
    socket.userSessionId = data.user_id;

    // Підписка на групи
    let groupIds = await funcs.getAllGroupsForUser(ctx, user_id)
    for (let groupId of groupIds) {
        const groupRoom = "group" + groupId.group_id;
        socket.join(groupRoom);
        console.log(`✅ Socket joined group room: ${groupRoom}`);
    }

    console.log("✅ JoinController SUCCESS for user_id:", user_id);

    // Безопасний вызов callback
    if (callback && typeof callback === 'function') {
        callback({ status: 200, user_id: user_id });
    }
};

module.exports = { JoinController };