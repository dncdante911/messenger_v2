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

    if (!data.user_id || data.user_id === '') {
        console.log("❌ Killing connection: user_id not received")
        socket.disconnect(true)
        return
    }

    try {
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

        let user_status_row = await ctx.wo_users.findOne({
            attributes: ["status"],
            where: {
                user_id: user_id
            }
        })

        // Null-check: user row might not exist (deleted account)
        if (!user_status_row) {
            console.log("❌ User record not found for user_id:", user_id)
            socket.disconnect(true)
            return;
        }
        const user_status = user_status_row.status;

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

        try {
            await socketEvents.emitUserStatus(ctx, socket, ctx.userHashUserId[data.user_id])
        } catch (statusErr) {
            console.error('[JoinController] emitUserStatus error:', statusErr.message)
        }

        if (user_status == 0) {
            try {
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

                // Collect unique recipient IDs: followers + users who have an open chat with this user
                const notifySet = new Set(followers.map(f => f.following_id));

                // Also notify users who have an open private chat with user_id (via is_chat_on)
                for (const [viewerId, openChats] of Object.entries(ctx.userIdChatOpen)) {
                    if (Array.isArray(openChats) && openChats.includes(user_id) && Number(viewerId) !== user_id) {
                        notifySet.add(Number(viewerId));
                    }
                }

                for (const recipientId of notifySet) {
                    await io.to(String(recipientId)).emit("on_user_loggedin", { user_id: user_id })
                }
            } catch (loginNotifyErr) {
                console.error('[JoinController] login notify error:', loginNotifyErr.message)
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
        try {
            let groupIds = await funcs.getAllGroupsForUser(ctx, user_id)
            for (let groupId of groupIds) {
                const groupRoom = "group" + groupId.group_id;
                socket.join(groupRoom);
                console.log(`✅ Socket joined group room: ${groupRoom}`);
            }
        } catch (groupErr) {
            console.error('[JoinController] group rooms error:', groupErr.message)
        }

        // Stamp lastseen immediately on connect so the REST /api/node/user/status
        // endpoint returns a fresh timestamp right away, even before the first
        // ping_for_lastseen event arrives.
        try {
            await funcs.Wo_LastSeen(ctx, user_id);
        } catch (lsErr) {
            console.error('[JoinController] lastseen update error:', lsErr.message)
        }

        console.log("✅ JoinController SUCCESS for user_id:", user_id);

        // Безопасний вызов callback
        if (callback && typeof callback === 'function') {
            try { callback({ status: 200, user_id: user_id }); } catch (cbErr) {
                console.error('[JoinController] callback error:', cbErr.message)
            }
        }
    } catch (err) {
        console.error('[JoinController] Unhandled error:', err.message, err.stack)
        try { socket.disconnect(true) } catch (_) {}
        if (callback && typeof callback === 'function') {
            try { callback({ status: 500, error: 'Internal server error' }); } catch (_) {}
        }
    }
};

module.exports = { JoinController };