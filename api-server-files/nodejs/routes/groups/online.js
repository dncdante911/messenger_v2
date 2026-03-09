'use strict';

/**
 * Group Online Status — REST + Socket.IO
 *
 * REST endpoints:
 *   GET  /api/node/group/:group_id/online-members   — список онлайн-учасників
 *
 * Socket events (сервер → клієнт):
 *   group:online_members  { group_id, user_ids }     — початкова синхронізація при вході
 *   group:member_online   { group_id, user_id }      — учасник з'явився онлайн
 *   group:member_offline  { group_id, user_id }      — учасник пішов офлайн
 *
 * Socket events (клієнт → сервер):
 *   group:enter           { group_id }               — користувач відкрив груповий чат
 *   group:leave           { group_id }               — користувач закрив груповий чат
 *
 * Дані зберігаються в пам'яті (Map) — достатньо для real-time.
 * При рестарті сервера вони відновлюються через group:enter від активних клієнтів.
 */

// groupId (string) -> Set<userId (string)>
const groupOnlineMembers = new Map();

// userId -> Set<groupId>  (щоб при disconnect прибирати зі всіх груп)
const userActiveGroups = new Map();

// ─── helpers ────────────────────────────────────────────────────────────────

function getOnlineSet(groupId) {
    const key = String(groupId);
    if (!groupOnlineMembers.has(key)) groupOnlineMembers.set(key, new Set());
    return groupOnlineMembers.get(key);
}

function addUserToGroup(groupId, userId) {
    getOnlineSet(groupId).add(String(userId));
    const gKey = String(groupId);
    if (!userActiveGroups.has(String(userId))) userActiveGroups.set(String(userId), new Set());
    userActiveGroups.get(String(userId)).add(gKey);
}

function removeUserFromGroup(groupId, userId) {
    const set = groupOnlineMembers.get(String(groupId));
    if (set) set.delete(String(userId));
    const groups = userActiveGroups.get(String(userId));
    if (groups) groups.delete(String(groupId));
}

function removeUserFromAllGroups(userId, io) {
    const groups = userActiveGroups.get(String(userId));
    if (!groups) return;
    for (const groupId of groups) {
        const set = groupOnlineMembers.get(groupId);
        if (set) {
            set.delete(String(userId));
            io.to(`group_${groupId}`).emit('group:member_offline', {
                group_id: parseInt(groupId),
                user_id:  parseInt(userId),
            });
        }
    }
    userActiveGroups.delete(String(userId));
}

// ─── REST: GET /api/node/group/:group_id/online-members ─────────────────────

function registerOnlineMembersRoute(app, ctx) {
    app.get('/api/node/group/:group_id/online-members', async (req, res) => {
        const token = req.headers['access-token'] || req.query.access_token;
        if (!token) return res.json({ api_status: 401, error_message: 'access_token required' });

        try {
            const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
            if (!session) return res.json({ api_status: 401, error_message: 'Invalid token' });

            const groupId = parseInt(req.params.group_id);
            if (!groupId) return res.json({ api_status: 400, error_message: 'Invalid group_id' });

            // Перевіряємо членство
            const member = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: session.user_id },
                raw: true,
            }).catch(() => null);

            if (!member) {
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });
            }

            const onlineIds = Array.from(getOnlineSet(groupId)).map(Number);

            res.json({
                api_status: 200,
                group_id: groupId,
                online_count: onlineIds.length,
                user_ids: onlineIds,
            });
        } catch (err) {
            console.error('[GroupOnline/REST]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    });
}

// ─── Socket.IO handlers ──────────────────────────────────────────────────────

/**
 * Реєструємо socket-обробники для групового онлайн-статусу.
 * Викликати після підключення сокету з авторизованим userId.
 *
 * @param {Socket} socket     — поточний сокет
 * @param {number} userId     — авторизований userId
 * @param {Server} io         — Socket.IO server instance
 * @param {object} ctx        — контекст з Sequelize моделями
 */
function attachGroupOnlineHandlers(socket, userId, io, ctx) {

    // Клієнт відкрив груповий чат
    socket.on('group:enter', async (data) => {
        try {
            const groupId = parseInt(data?.group_id);
            if (!groupId || !userId) return;

            // Перевіряємо членство
            const member = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId },
                raw: true,
            }).catch(() => null);

            if (!member) return;

            addUserToGroup(groupId, userId);

            // Приєднуємося до socket room групи
            socket.join(`group_${groupId}`);

            // Надсилаємо поточний список онлайн-учасників цьому клієнту
            const onlineIds = Array.from(getOnlineSet(groupId)).map(Number);
            socket.emit('group:online_members', {
                group_id: groupId,
                user_ids: onlineIds,
            });

            // Сповіщаємо всіх інших учасників групи
            socket.to(`group_${groupId}`).emit('group:member_online', {
                group_id: groupId,
                user_id:  userId,
            });

        } catch (err) {
            console.error('[GroupOnline/enter]', err.message);
        }
    });

    // Клієнт закрив груповий чат
    socket.on('group:leave', (data) => {
        try {
            const groupId = parseInt(data?.group_id);
            if (!groupId || !userId) return;

            removeUserFromGroup(groupId, userId);
            socket.leave(`group_${groupId}`);

            io.to(`group_${groupId}`).emit('group:member_offline', {
                group_id: groupId,
                user_id:  userId,
            });
        } catch (err) {
            console.error('[GroupOnline/leave]', err.message);
        }
    });

    // При відключенні сокету прибираємо зі всіх груп
    socket.on('disconnect', () => {
        removeUserFromAllGroups(userId, io);
    });
}

module.exports = { registerOnlineMembersRoute, attachGroupOnlineHandlers };
