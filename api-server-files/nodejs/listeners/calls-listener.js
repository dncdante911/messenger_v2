/**
 * WebRTC Calls Listener для WorldMates Messenger
 *
 * ✅ CLUSTER-SAFE: Все emit() идут через io.to(String(userId)) вместо
 * ctx.userIdSocket[userId], что работает через Redis adapter между воркерами.
 * JoinController уже помещает каждый сокет в room = String(user_id).
 *
 * ✅ Group call participant tracking хранится в Redis (hashes), а не в in-memory Map.
 * Это критично для PM2 cluster mode — каждый воркер видит одних и тех же участников.
 */

const turnHelper = require('../helpers/turn-credentials');
const { createClient } = require('redis');

// ── Redis client (shared for ICE buffer + group call participants) ────────
let _redis = null;
async function getRedis() {
    if (_redis && _redis.isOpen) return _redis;
    try {
        const redisPass = process.env.REDIS_PASSWORD || '';
        _redis = createClient({
            socket: {
                host: process.env.REDIS_HOST || '127.0.0.1',
                port: parseInt(process.env.REDIS_PORT) || 6379,
                connectTimeout: 3000,
                reconnectStrategy: (retries) => Math.min(retries * 200, 5000),
            },
            ...(redisPass ? { password: redisPass } : {}),
        });
        _redis.on('error', () => {}); // suppress unhandled errors
        await _redis.connect();
    } catch { _redis = null; }
    return _redis;
}

// ── ICE candidate buffer (Redis list with TTL) ──────────────────────────
const ICE_BUFFER_TTL = 120; // seconds
async function bufferIceCandidate(roomName, candidateData) {
    try {
        const r = await getRedis();
        if (!r) return;
        const key = `ice_buf:${roomName}`;
        await r.rPush(key, JSON.stringify(candidateData));
        await r.expire(key, ICE_BUFFER_TTL);
    } catch { /* non-fatal */ }
}
async function getBufferedIceCandidates(roomName) {
    try {
        const r = await getRedis();
        if (!r) return [];
        const key = `ice_buf:${roomName}`;
        const items = await r.lRange(key, 0, -1);
        await r.del(key);
        return items.map(i => JSON.parse(i));
    } catch { return []; }
}
async function clearIceBuffer(roomName) {
    try {
        const r = await getRedis();
        if (r) await r.del(`ice_buf:${roomName}`);
    } catch { /* non-fatal */ }
}

// ── Group call participant tracking (Redis hash, cluster-safe) ──────────
// Key: `gcall:{roomName}` → hash { "userId1": JSON({userName, userAvatar}), ... }
const GCALL_TTL = 3600; // 1 hour auto-cleanup for stale rooms

async function addGroupCallParticipant(roomName, userId, userName, userAvatar) {
    try {
        const r = await getRedis();
        if (!r) return;
        const key = `gcall:${roomName}`;
        await r.hSet(key, String(userId), JSON.stringify({ userName, userAvatar }));
        await r.expire(key, GCALL_TTL);
    } catch (e) { console.warn('[CALLS] Redis addGroupCallParticipant error:', e.message); }
}

async function removeGroupCallParticipant(roomName, userId) {
    try {
        const r = await getRedis();
        if (!r) return;
        const key = `gcall:${roomName}`;
        await r.hDel(key, String(userId));
        // Clean up empty rooms
        const remaining = await r.hLen(key);
        if (remaining === 0) await r.del(key);
    } catch (e) { console.warn('[CALLS] Redis removeGroupCallParticipant error:', e.message); }
}

async function getGroupCallParticipants(roomName) {
    try {
        const r = await getRedis();
        if (!r) return new Map();
        const key = `gcall:${roomName}`;
        const all = await r.hGetAll(key);
        const map = new Map();
        for (const [uid, json] of Object.entries(all)) {
            try { map.set(parseInt(uid), JSON.parse(json)); } catch { /* skip bad entry */ }
        }
        return map;
    } catch (e) {
        console.warn('[CALLS] Redis getGroupCallParticipants error:', e.message);
        return new Map();
    }
}

async function deleteGroupCallRoom(roomName) {
    try {
        const r = await getRedis();
        if (r) await r.del(`gcall:${roomName}`);
    } catch { /* non-fatal */ }
}

// ── Noise cancellation state (Redis hash, cluster-safe) ─────────────────
async function setNoiseCancellation(roomName, userId, enabled) {
    try {
        const r = await getRedis();
        if (!r) return;
        const key = `gcall_nc:${roomName}`;
        await r.hSet(key, String(userId), enabled ? '1' : '0');
        await r.expire(key, GCALL_TTL);
    } catch { /* non-fatal */ }
}

async function getNoiseCancellationStates(roomName) {
    try {
        const r = await getRedis();
        if (!r) return {};
        const all = await r.hGetAll(`gcall_nc:${roomName}`);
        const result = {};
        for (const [uid, val] of Object.entries(all)) result[uid] = val === '1';
        return result;
    } catch { return {}; }
}

async function deleteNoiseCancellationRoom(roomName) {
    try {
        const r = await getRedis();
        if (r) await r.del(`gcall_nc:${roomName}`);
    } catch { /* non-fatal */ }
}

/**
 * Регистрация обработчиков звонков
 * @param {Object} socket - Socket.IO socket объект
 * @param {Object} io - Socket.IO server instance
 * @param {Object} ctx - Контекст приложения с моделями и состоянием
 */
async function registerCallsListeners(socket, io, ctx) {

    // Хранилище активных звонков (1-on-1 only, not critical for cluster)
    if (!ctx.activeCalls) {
        ctx.activeCalls = new Map();
    }

    // ✅ Group call participants + noise cancellation now stored in Redis.
    // ctx.activeGroupCalls and ctx.noiseCancellationState are NO LONGER USED.
    // See: addGroupCallParticipant(), getGroupCallParticipants(), etc.

    /**
     * Регистрация пользователя для звонков
     * Data: { userId }
     */
    socket.on('call:register', (data) => {
        const userId = data.userId || data.user_id;
        console.log(`[CALLS] 📝 User registered for calls: ${userId}, socket: ${socket.id}`);

        // ✅ Join user room (cluster-safe via Redis adapter).
        // JoinController usually does this, but call:register may fire
        // before join (e.g. from MessageNotificationService).
        socket.join(String(userId));

        // Local userIdSocket for backward compat with non-call code
        if (!ctx.userIdSocket[userId]) {
            ctx.userIdSocket[userId] = [];
        }
        if (!ctx.userIdSocket[userId].includes(socket)) {
            ctx.userIdSocket[userId].push(socket);
        }
    });

    /**
     * Запрос ICE servers перед инициацией звонка
     * Data: { userId }
     * Response: { success: true, iceServers: [...] }
     */
    socket.on('ice:request', (data, callback) => {
        try {
            const userId = data.userId || data.user_id;
            console.log(`[CALLS] 🧊 ICE servers requested by user ${userId}`);

            // Получить ICE servers с TURN credentials
            const iceServers = turnHelper.getIceServers(userId);

            const response = {
                success: true,
                iceServers: iceServers,
                timestamp: Date.now()
            };

            // Отправить ответ через callback
            if (typeof callback === 'function') {
                callback(response);
                console.log(`[CALLS] ✅ ICE servers sent to user ${userId}: ${iceServers.length} servers`);
            } else {
                // Fallback: emit event
                socket.emit('ice:response', response);
            }
        } catch (error) {
            console.error('[CALLS] Error generating ICE servers:', error);
            const errorResponse = {
                success: false,
                error: 'Failed to generate ICE servers'
            };
            if (typeof callback === 'function') {
                callback(errorResponse);
            } else {
                socket.emit('ice:response', errorResponse);
            }
        }
    });

    /**
     * Инициация звонка (1-на-1 или групповой)
     * Data: { fromId, toId?, groupId?, callType, roomName, sdpOffer }
     */
    socket.on('call:initiate', async (data) => {
        try {
            console.log('[CALLS] 📞 call:initiate received, raw data:', JSON.stringify(data).substring(0, 200));

            const { fromId, toId, groupId, callType, roomName, sdpOffer } = data;

            console.log(`[CALLS] Call initiated: ${fromId} -> ${toId || groupId} (${callType})`);

            if (toId) {
                // ===== 1-на-1 звонок =====

                // Сохранить в БД
                await ctx.wo_calls.create({
                    from_id: fromId,
                    to_id: toId,
                    call_type: callType,
                    status: 'ringing',
                    room_name: roomName,
                    sdp_offer: sdpOffer,
                    created_at: new Date()
                });

                // Получить данные инициатора
                const initiator = await ctx.wo_users.findOne({
                    where: { user_id: fromId },
                    attributes: ['user_id', 'first_name', 'last_name', 'avatar'],
                    raw: true
                });

                // ✅ DEBUG: Логируем что получили из БД
                console.log(`[CALLS] 🔍 Initiator data from DB:`, {
                    user_id: initiator?.user_id,
                    first_name: initiator?.first_name,
                    last_name: initiator?.last_name,
                    avatar: initiator?.avatar
                });

                // ✅ CLUSTER-SAFE: Используем io.to(room) вместо ctx.userIdSocket.
                // Redis adapter маршрутизирует emit между воркерами PM2.
                // Проверяем есть ли получатель в online через fetchSockets().
                const recipientRoom = String(toId);
                const recipientSids = await io.in(recipientRoom).fetchSockets();

                console.log(`[CALLS] 🔍 Looking for recipient ${toId}, found: ${recipientSids.length} socket(s) via room`);

                if (recipientSids.length > 0) {
                    const iceServers = turnHelper.getIceServers(toId);

                    let fromName = 'Unknown';
                    if (initiator) {
                        const firstName = initiator.first_name || '';
                        const lastName = initiator.last_name || '';
                        fromName = `${firstName} ${lastName}`.trim() || 'Unknown';
                    }

                    const callData = {
                        fromId: fromId,
                        fromName: fromName,
                        fromAvatar: initiator ? (initiator.avatar || '') : '',
                        callType: callType,
                        roomName: roomName,
                        sdpOffer: sdpOffer,
                        iceServers: iceServers
                    };

                    console.log(`[CALLS] 📤 Sending call:incoming to user ${toId} (${recipientSids.length} sockets)`);
                    io.to(recipientRoom).emit('call:incoming', callData);

                } else {
                    await ctx.wo_calls.update(
                        { status: 'missed' },
                        { where: { room_name: roomName } }
                    );

                    socket.emit('call:error', {
                        message: 'Recipient is offline',
                        status: 'missed'
                    });

                    console.log(`[CALLS] Recipient ${toId} is offline`);
                }

            } else if (groupId) {
                // ===== Групповий дзвінок =====

                // Check initiator's pro status and enforce participant limits
                const now = Math.floor(Date.now() / 1000);
                const initiatorProData = await ctx.wo_users.findOne({
                    where: { user_id: fromId },
                    attributes: ['is_pro', 'pro_time'],
                    raw: true
                });
                const isPro = initiatorProData &&
                    parseInt(initiatorProData.is_pro) === 1 &&
                    initiatorProData.pro_time > now;
                const maxParticipants = isPro ? 25 : 5;

                const memberCount = await ctx.wo_groupchatusers.count({
                    where: { group_id: groupId, active: 1 }
                });

                if (memberCount > maxParticipants) {
                    socket.emit('call:error', {
                        type: 'PARTICIPANT_LIMIT_EXCEEDED',
                        message: isPro
                            ? `Group calls support up to 25 participants`
                            : `Group calls support up to 5 participants. Upgrade to Premium for up to 25.`,
                        maxParticipants,
                        isPro
                    });
                    console.log(`[CALLS] ❌ Group call rejected: ${memberCount} members > limit ${maxParticipants} (isPro=${isPro})`);
                    return;
                }

                // Сохранить в БД
                await ctx.wo_group_calls.create({
                    group_id: groupId,
                    initiated_by: fromId,
                    call_type: callType,
                    status: 'ringing',
                    room_name: roomName,
                    max_participants: maxParticipants,
                    created_at: new Date()
                });

                // Получить участников группы
                const members = await ctx.wo_groupchatusers.findAll({
                    where: {
                        group_id: groupId,
                        active: 1
                    },
                    attributes: ['user_id'],
                    raw: true
                });

                // Получить данные инициатора
                const initiator = await ctx.wo_users.findOne({
                    where: { user_id: fromId },
                    attributes: ['first_name', 'last_name', 'avatar'],
                    raw: true
                });

                const initiatorName = initiator ?
                    `${initiator.first_name} ${initiator.last_name}` : 'Unknown';
                const initiatorAvatar = initiator?.avatar || '';

                // ✅ CLUSTER-SAFE: Register initiator in Redis so other workers
                // can see them when participants join from different PM2 workers
                await addGroupCallParticipant(roomName, fromId, initiatorName, initiatorAvatar);
                socket.join(roomName);
                console.log(`[CALLS] 👥 Initiator ${fromId} joined room ${roomName}`);

                // ✅ CLUSTER-SAFE: отправить всем участникам кроме инициатора
                for (const member of members) {
                    if (member.user_id !== fromId) {
                        const iceServers = turnHelper.getIceServers(member.user_id);
                        const callData = {
                            groupId: groupId,
                            initiatedBy: fromId,
                            initiatorName: initiatorName,
                            initiatorAvatar: initiatorAvatar,
                            callType: callType,
                            roomName: roomName,
                            sdpOffer: sdpOffer,
                            iceServers: iceServers,
                            maxParticipants: maxParticipants,
                            isPremiumCall: isPro
                        };
                        io.to(String(member.user_id)).emit('group_call:incoming', callData);
                    }
                }

                // ── Send a system message to the group so members see a call card in chat ──
                try {
                    const callMeta = JSON.stringify({
                        callType,
                        roomName,
                        initiatorName,
                        maxParticipants,
                        isPremiumCall: isPro
                    });

                    const sysMsg = await ctx.wo_messages.create({
                        from_id: fromId,
                        group_id: groupId,
                        text:     callMeta,
                        type_two: 'group_call',
                        time:     Math.floor(Date.now() / 1000),
                        seen:     0
                    });

                    // Broadcast the message to everyone in the group room
                    const groupRoom = `group_${groupId}`;
                    io.to(groupRoom).emit('group_message', {
                        id:          sysMsg.id,
                        from_id:     fromId,
                        group_id:    groupId,
                        text:        callMeta,
                        type_two:    'group_call',
                        time:        sysMsg.time,
                        sender_name: initiatorName,
                        sender_avatar: initiator?.avatar || ''
                    });
                } catch (msgErr) {
                    // Non-fatal: call still works even if the message fails
                    console.warn('[CALLS] Could not send group call system message:', msgErr.message);
                }

                console.log(`[CALLS] Group call initiated for group ${groupId} with TURN credentials`);
            }

        } catch (error) {
            console.error('[CALLS] Error in call:initiate:', error);
            socket.emit('call:error', {
                message: 'Failed to initiate call',
                error: error.message
            });
        }
    });

    /**
     * Принять звонок
     * Data: { roomName, userId, sdpAnswer }
     */
    socket.on('call:accept', async (data) => {
        try {
            const { roomName, userId, sdpAnswer } = data;

            console.log(`[CALLS] Call accepted: ${roomName} by user ${userId}`);

            // Обновить статус звонка
            await ctx.wo_calls.update(
                {
                    status: 'connected',
                    accepted_at: new Date(),
                    sdp_answer: sdpAnswer
                },
                { where: { room_name: roomName } }
            );

            // Получить инициатора звонка
            const callInfo = await ctx.wo_calls.findOne({
                where: { room_name: roomName },
                attributes: ['from_id', 'to_id'],
                raw: true
            });

            if (callInfo) {
                const initiatorId = callInfo.from_id;
                const iceServers = turnHelper.getIceServers(initiatorId);

                // ✅ CLUSTER-SAFE: отправить SDP answer через io.to(room)
                io.to(String(initiatorId)).emit('call:answer', {
                    roomName: roomName,
                    sdpAnswer: sdpAnswer,
                    acceptedBy: userId,
                    iceServers: iceServers
                });
                console.log(`[CALLS] Answer sent to initiator ${initiatorId} with TURN credentials`);

                // ✅ CLUSTER-SAFE: доставить буферизированные ICE кандидаты из Redis
                const bufferedCandidates = await getBufferedIceCandidates(roomName);
                if (bufferedCandidates.length > 0) {
                    console.log(`[CALLS] 📦 Delivering ${bufferedCandidates.length} buffered ICE candidates for room ${roomName}`);
                    for (const candidateData of bufferedCandidates) {
                        if (String(candidateData.fromUserId) === String(initiatorId)) {
                            // Кандидаты caller'а → receiver'у (через сокет который вызвал accept)
                            socket.emit('ice:candidate', candidateData);
                        }
                        if (String(candidateData.fromUserId) === String(userId)) {
                            // Кандидаты receiver'а → caller'у
                            io.to(String(initiatorId)).emit('ice:candidate', candidateData);
                        }
                    }
                }
            }

        } catch (error) {
            console.error('[CALLS] Error in call:accept:', error);
            socket.emit('call:error', { message: 'Failed to accept call' });
        }
    });

    /**
     * Обмен ICE candidates
     * Data: { roomName, toUserId, candidate, sdpMLineIndex, sdpMid }
     */
    socket.on('ice:candidate', async (data) => {
        try {
            const { roomName, toUserId, fromUserId, candidate, sdpMLineIndex, sdpMid } = data;

            const candidateData = {
                roomName, fromUserId, candidate, sdpMLineIndex, sdpMid
            };

            if (toUserId) {
                // Буферизируем в Redis (cluster-safe) + пытаемся доставить сразу
                if (roomName) bufferIceCandidate(roomName, candidateData);
                io.to(String(toUserId)).emit('ice:candidate', candidateData);
            } else {
                // Broadcast в комнату (групповые звонки)
                socket.to(roomName).emit('ice:candidate', candidateData);
            }
        } catch (error) {
            console.error('[CALLS] Error in ice:candidate:', error);
        }
    });

    /**
     * Завершить звонок
     * Data: { roomName, userId, reason }
     */
    socket.on('call:end', async (data) => {
        try {
            const { roomName, userId, reason } = data;

            // ✅ Валідація: якщо немає roomName, пропустити
            if (!roomName) {
                console.warn('[CALLS] call:end received without roomName, ignoring');
                return;
            }

            console.log(`[CALLS] Call ended: ${roomName} by ${userId} (${reason})`);

            // Очистить буфер ICE кандидатов в Redis
            clearIceBuffer(roomName);

            // Обновить в БД
            const call = await ctx.wo_calls.findOne({
                where: { room_name: roomName },
                raw: true
            });

            if (call) {
                // Вычислить длительность если звонок был принят
                let duration = null;
                if (call.accepted_at) {
                    duration = Math.floor((new Date() - new Date(call.accepted_at)) / 1000);
                }

                await ctx.wo_calls.update(
                    {
                        status: 'ended',
                        ended_at: new Date(),
                        duration: duration
                    },
                    { where: { room_name: roomName } }
                );

                // ✅ CLUSTER-SAFE: оповестить обоих участников
                const endPayload = { roomName, reason, endedBy: userId };
                [call.from_id, call.to_id].forEach(pid => {
                    if (pid !== userId) {
                        io.to(String(pid)).emit('call:ended', endPayload);
                    }
                });
            }

            // Удалить из активных звонков
            ctx.activeCalls.delete(roomName);

        } catch (error) {
            console.error('[CALLS] Error in call:end:', error);
        }
    });

    /**
     * Отклонить звонок
     * Data: { roomName, userId }
     */
    socket.on('call:reject', async (data) => {
        try {
            const { roomName, userId } = data;

            // ✅ Валідація
            if (!roomName) {
                console.warn('[CALLS] call:reject received without roomName, ignoring');
                return;
            }

            console.log(`[CALLS] Call rejected: ${roomName} by ${userId}`);

            await ctx.wo_calls.update(
                { status: 'rejected' },
                { where: { room_name: roomName } }
            );

            // Получить информацию о звонке
            const call = await ctx.wo_calls.findOne({
                where: { room_name: roomName },
                raw: true
            });

            if (call) {
                io.to(String(call.from_id)).emit('call:rejected', {
                    roomName, rejectedBy: userId
                });
            }

            ctx.activeCalls.delete(roomName);
            clearIceBuffer(roomName);

        } catch (error) {
            console.error('[CALLS] Error in call:reject:', error);
        }
    });

    /**
     * Присоединиться к комнате (для Socket.IO rooms)
     * Data: { roomName, userId }
     */
    socket.on('call:join_room', (data) => {
        const { roomName, userId } = data;
        socket.join(roomName);
        console.log(`[CALLS] User ${userId} joined room: ${roomName}`);

        // Уведомить других в комнате
        socket.to(roomName).emit('user:joined_call', {
            userId: userId,
            roomName: roomName
        });
    });

    /**
     * Покинуть комнату
     * Data: { roomName, userId }
     */
    socket.on('call:leave_room', (data) => {
        const { roomName, userId } = data;
        socket.leave(roomName);
        console.log(`[CALLS] User ${userId} left room: ${roomName}`);

        // Уведомить других в комнате
        socket.to(roomName).emit('user:left_call', {
            userId: userId,
            roomName: roomName
        });
    });

    /**
     * Переключение аудио/видео (для групповых звонков)
     * Data: { roomName, userId, audio, video }
     */
    socket.on('call:toggle_media', (data) => {
        const { roomName, userId, audio, video } = data;

        // Broadcast другим участникам
        socket.to(roomName).emit('user:media_changed', {
            userId: userId,
            audio: audio,
            video: video
        });

        console.log(`[CALLS] User ${userId} toggled media: audio=${audio}, video=${video}`);
    });

    /**
     * 🔄 Renegotiation - когда участник включает/выключает видео во время звонка
     * Data: { roomName, fromUserId, toUserId, sdpOffer, type }
     */
    socket.on('call:renegotiate', async (data) => {
        try {
            const { roomName, fromUserId, toUserId, sdpOffer } = data;

            console.log(`[CALLS] 🔄 Renegotiation from ${fromUserId} to ${toUserId} in room ${roomName}`);

            if (toUserId) {
                io.to(String(toUserId)).emit('call:renegotiate', {
                    roomName, fromUserId, sdpOffer, type: 'renegotiate'
                });
            } else {
                socket.to(roomName).emit('call:renegotiate', {
                    fromUserId, sdpOffer, type: 'renegotiate'
                });
            }

        } catch (error) {
            console.error('[CALLS] Error in call:renegotiate:', error);
        }
    });

    socket.on('call:renegotiate_answer', async (data) => {
        try {
            const { roomName, fromUserId, toUserId, sdpAnswer } = data;

            if (toUserId) {
                io.to(String(toUserId)).emit('call:renegotiate_answer', {
                    roomName, fromUserId, sdpAnswer, type: 'renegotiate_answer'
                });
            } else {
                socket.to(roomName).emit('call:renegotiate_answer', {
                    fromUserId, sdpAnswer, type: 'renegotiate_answer'
                });
            }

        } catch (error) {
            console.error('[CALLS] Error in call:renegotiate_answer:', error);
        }
    });

    // ==================== SCREEN SHARING ====================

    /**
     * Notify participants that screen sharing started/stopped
     * Data: { roomName, userId, action: 'start'|'stop' }
     */
    socket.on('call:screen_share', (data) => {
        try {
            const { roomName, userId, action } = data;
            console.log(`[CALLS] 🖥️ Screen share ${action} by user ${userId} in room ${roomName}`);

            // Broadcast to room
            socket.to(roomName).emit('call:screen_share', {
                userId: userId,
                action: action,
                roomName: roomName
            });
        } catch (error) {
            console.error('[CALLS] Error in call:screen_share:', error);
        }
    });

    // ==================== CALL RECORDING NOTIFICATION ====================

    /**
     * Notify participants that recording started/stopped
     * Data: { roomName, userId, userName, action: 'start'|'stop' }
     */
    socket.on('call:recording', (data) => {
        try {
            const { roomName, userId, userName, action } = data;
            console.log(`[CALLS] 🔴 Recording ${action} by user ${userId} (${userName}) in room ${roomName}`);

            // Broadcast to ALL in room (including sender for confirmation)
            io.in(roomName).emit('call:recording', {
                userId: userId,
                userName: userName || 'Учасник',
                action: action,
                roomName: roomName,
                timestamp: Date.now()
            });
        } catch (error) {
            console.error('[CALLS] Error in call:recording:', error);
        }
    });

    // ==================== NOISE CANCELLATION STATUS ====================

    /**
     * Share noise cancellation status with participants
     * Data: { roomName, userId, enabled: boolean }
     */
    socket.on('call:noise_cancellation', async (data) => {
        try {
            const { roomName, userId, enabled } = data;
            console.log(`[CALLS] 🔇 Noise cancellation ${enabled ? 'ON' : 'OFF'} for user ${userId} in room ${roomName}`);

            // ✅ CLUSTER-SAFE: Persist in Redis so late joiners on any worker see state
            if (roomName) {
                await setNoiseCancellation(roomName, userId, !!enabled);
            }

            socket.to(roomName).emit('call:noise_cancellation', {
                userId,
                enabled,
                roomName
            });
        } catch (error) {
            console.error('[CALLS] Error in call:noise_cancellation:', error);
        }
    });

    // ==================== GROUP CALL MESH SIGNALING ====================

    /**
     * Участник принимает групповой звонок и присоединяется к комнате.
     * Сервер уведомляет всех существующих участников, чтобы они создали
     * WebRTC offer для нового участника (mesh архитектура).
     *
     * Data: { roomName, userId, groupId }
     */
    socket.on('group_call:join', async (data) => {
        try {
            const { roomName, userId, groupId } = data;
            if (!roomName || !userId) return;

            console.log(`[CALLS] 👥 group_call:join — user ${userId} joining room ${roomName}`);

            // Получить данные пользователя
            let userName = 'Unknown';
            let userAvatar = '';
            try {
                const user = await ctx.wo_users.findOne({
                    where: { user_id: userId },
                    attributes: ['first_name', 'last_name', 'avatar'],
                    raw: true
                });
                if (user) {
                    userName = `${user.first_name || ''} ${user.last_name || ''}`.trim() || 'Unknown';
                    userAvatar = user.avatar || '';
                }
            } catch (e) { /* ignore */ }

            // ✅ CLUSTER-SAFE: Read existing participants from Redis (shared across all PM2 workers)
            const roomParticipants = await getGroupCallParticipants(roomName);
            const existingParticipantIds = [...roomParticipants.keys()];

            // Register new participant in Redis
            await addGroupCallParticipant(roomName, userId, userName, userAvatar);
            socket.join(roomName);

            console.log(`[CALLS] 👥 Room ${roomName} now has ${roomParticipants.size + 1} participants: ${[...existingParticipantIds, userId].join(', ')}`);

            // Отправить новому участнику список уже подключённых участников + ICE серверы
            const joinerIceServers = turnHelper.getIceServers(userId);
            const existingList = existingParticipantIds.map(uid => ({
                userId: uid,
                userName: roomParticipants.get(uid)?.userName || 'Unknown',
                userAvatar: roomParticipants.get(uid)?.userAvatar || '',
                shouldCreateOffer: true,
                iceServers: joinerIceServers,
            }));

            // Include noise-cancellation states from Redis
            const ncStates = await getNoiseCancellationStates(roomName);

            socket.emit('group_call:current_participants', {
                roomName,
                participants: existingList,
                iceServers: joinerIceServers,
                noiseCancellationStates: ncStates,
            });

            // ✅ CLUSTER-SAFE: уведомить каждого существующего участника
            for (const existingUserId of existingParticipantIds) {
                const iceServers = turnHelper.getIceServers(existingUserId);
                io.to(String(existingUserId)).emit('group_call:participant_joined', {
                    roomName, userId, userName, userAvatar, iceServers,
                    shouldCreateOffer: true,
                });
            }

            console.log(`[CALLS] ✅ Notified ${existingParticipantIds.length} existing participants about user ${userId}`);
        } catch (error) {
            console.error('[CALLS] Error in group_call:join:', error);
        }
    });

    /**
     * Передать WebRTC offer конкретному участнику групповогозвонка.
     * Отправляется существующим участником новому (при mesh соединении).
     *
     * Data: { roomName, fromUserId, toUserId, sdpOffer }
     */
    socket.on('group_call:offer', (data) => {
        try {
            const { roomName, fromUserId, toUserId, sdpOffer } = data;
            if (!toUserId || !sdpOffer) return;

            console.log(`[CALLS] 📤 group_call:offer from ${fromUserId} to ${toUserId} in room ${roomName}`);

            const iceServers = turnHelper.getIceServers(toUserId);
            io.to(String(toUserId)).emit('group_call:offer', {
                roomName, fromUserId, sdpOffer, iceServers
            });
            console.log(`[CALLS] ✅ Offer relayed to user ${toUserId}`);
        } catch (error) {
            console.error('[CALLS] Error in group_call:offer:', error);
        }
    });

    /**
     * Передать WebRTC answer обратно инициатору offer.
     *
     * Data: { roomName, fromUserId, toUserId, sdpAnswer }
     */
    socket.on('group_call:answer', (data) => {
        try {
            const { roomName, fromUserId, toUserId, sdpAnswer } = data;
            if (!toUserId || !sdpAnswer) return;

            console.log(`[CALLS] 📥 group_call:answer from ${fromUserId} to ${toUserId} in room ${roomName}`);

            io.to(String(toUserId)).emit('group_call:answer', {
                roomName, fromUserId, sdpAnswer
            });
            console.log(`[CALLS] ✅ Answer relayed to user ${toUserId}`);
        } catch (error) {
            console.error('[CALLS] Error in group_call:answer:', error);
        }
    });

    /**
     * Участник покидает групповой звонок.
     * Data: { roomName, userId }
     */
    socket.on('group_call:leave', async (data) => {
        try {
            const { roomName, userId } = data;
            if (!roomName || !userId) return;

            console.log(`[CALLS] 🚪 group_call:leave — user ${userId} leaving room ${roomName}`);

            socket.leave(roomName);

            // ✅ CLUSTER-SAFE: Remove from Redis
            await removeGroupCallParticipant(roomName, userId);

            // Уведомить остальных участников
            socket.to(roomName).emit('group_call:participant_left', {
                roomName,
                userId
            });

            console.log(`[CALLS] ✅ User ${userId} left group call room ${roomName}`);
        } catch (error) {
            console.error('[CALLS] Error in group_call:leave:', error);
        }
    });

    /**
     * Инициатор завершает весь групповой звонок.
     * Data: { roomName, userId }
     */
    socket.on('group_call:end', async (data) => {
        try {
            const { roomName, userId } = data;
            if (!roomName) return;

            console.log(`[CALLS] 🔴 group_call:end — user ${userId} ending room ${roomName}`);

            // Уведомить всех участников
            io.in(roomName).emit('group_call:ended', {
                roomName,
                endedBy: userId
            });

            // ✅ CLUSTER-SAFE: Clean up Redis
            await deleteGroupCallRoom(roomName);
            await deleteNoiseCancellationRoom(roomName);

            // Обновить БД
            try {
                await ctx.wo_group_calls.update(
                    { status: 'ended', ended_at: new Date() },
                    { where: { room_name: roomName } }
                );
            } catch (e) {
                console.warn('[CALLS] Could not update group call status in DB:', e.message);
            }

            console.log(`[CALLS] ✅ Group call ${roomName} ended by user ${userId}`);
        } catch (error) {
            console.error('[CALLS] Error in group_call:end:', error);
        }
    });

    /**
     * ICE candidate для конкретного участника группового звонка.
     * Дополняет существующий ice:candidate handler для групп.
     * Data: { roomName, fromUserId, toUserId, candidate, sdpMLineIndex, sdpMid }
     */
    socket.on('group_call:ice_candidate', (data) => {
        try {
            const { roomName, fromUserId, toUserId, candidate, sdpMLineIndex, sdpMid } = data;

            if (toUserId) {
                io.to(String(toUserId)).emit('group_call:ice_candidate', {
                    roomName, fromUserId, candidate, sdpMLineIndex, sdpMid
                });
            } else {
                // Broadcast всем в комнате
                socket.to(roomName).emit('group_call:ice_candidate', {
                    fromUserId,
                    candidate,
                    sdpMLineIndex,
                    sdpMid
                });
            }
        } catch (error) {
            console.error('[CALLS] Error in group_call:ice_candidate:', error);
        }
    });

    console.log(`[CALLS] Call listeners registered for socket ${socket.id}`);

    // ═══════════════════════════════════════════════════════════════════════
    // CHANNEL LIVESTREAM WebRTC Signaling
    // Room naming: stream_ch{channelId}_...  (generated by the REST endpoint)
    //
    // Flow:
    //   Viewer  → stream:join        → server notifies host
    //   Host    → stream:offer       → server forwards to viewer
    //   Viewer  → stream:answer      → server forwards to host
    //   Either  → stream:ice         → server forwards to other party
    //   Viewer  → stream:leave       → server notifies host
    // ═══════════════════════════════════════════════════════════════════════

    // Active stream rooms: roomName -> { hostSocketId, hostUserId, viewers: Map<userId, socketId> }
    if (!ctx.activeStreams) ctx.activeStreams = new Map();

    // ── stream:join ──────────────────────────────────────────────────────────
    // Viewer joins the stream room and tells the host to create an offer.
    socket.on('stream:join', async (data) => {
        try {
            const { roomName, viewerUserId } = data;
            if (!roomName || !viewerUserId) return;

            socket.join(roomName);

            let room = ctx.activeStreams.get(roomName);
            if (!room) {
                room = { hostSocketId: null, hostUserId: null, viewers: new Map() };
                ctx.activeStreams.set(roomName, room);
            }
            room.viewers.set(viewerUserId, socket.id);

            // Get ICE servers for this viewer
            const iceServers = turnHelper.getIceServers(viewerUserId);

            // Notify host to create an offer for this viewer
            if (room.hostSocketId) {
                io.to(room.hostSocketId).emit('stream:viewer_joined', {
                    roomName,
                    viewerUserId,
                    viewerSocketId: socket.id,
                    iceServers
                });
            }

            // Ack to viewer with ICE servers
            socket.emit('stream:join_ack', { roomName, iceServers });

            console.log(`[STREAM] Viewer ${viewerUserId} joined room ${roomName}`);
        } catch (e) {
            console.error('[STREAM] stream:join error:', e);
        }
    });

    // ── stream:host_ready ────────────────────────────────────────────────────
    // Host announces it is ready to receive viewer connections.
    socket.on('stream:host_ready', (data) => {
        try {
            const { roomName, hostUserId } = data;
            if (!roomName || !hostUserId) return;

            socket.join(roomName);

            let room = ctx.activeStreams.get(roomName);
            if (!room) {
                room = { hostSocketId: socket.id, hostUserId, viewers: new Map() };
                ctx.activeStreams.set(roomName, room);
            } else {
                room.hostSocketId = socket.id;
                room.hostUserId   = hostUserId;
            }
            console.log(`[STREAM] Host ${hostUserId} ready in room ${roomName}`);
        } catch (e) {
            console.error('[STREAM] stream:host_ready error:', e);
        }
    });

    // ── stream:offer ─────────────────────────────────────────────────────────
    // Host sends SDP offer to a specific viewer.
    socket.on('stream:offer', (data) => {
        try {
            const { roomName, toUserId, sdpOffer, iceServers } = data;
            const fromUserId = ctx.socketIdUserHash?.[socket.id];

            // Find the viewer's socket
            const room = ctx.activeStreams.get(roomName);
            const viewerSocketId = room?.viewers.get(toUserId);

            const target = viewerSocketId
                ? io.sockets.sockets.get(viewerSocketId)
                : null;

            if (target) {
                target.emit('stream:offer', { roomName, fromUserId, sdpOffer, iceServers });
            } else {
                io.to(String(toUserId)).emit('stream:offer', { roomName, fromUserId, sdpOffer, iceServers });
            }
        } catch (e) {
            console.error('[STREAM] stream:offer error:', e);
        }
    });

    // ── stream:answer ────────────────────────────────────────────────────────
    // Viewer sends SDP answer back to the host.
    socket.on('stream:answer', (data) => {
        try {
            const { roomName, toUserId, sdpAnswer } = data;
            const fromUserId = ctx.socketIdUserHash?.[socket.id];

            const room = ctx.activeStreams.get(roomName);
            const hostSocketId = room?.hostSocketId;
            const target = hostSocketId ? io.sockets.sockets.get(hostSocketId) : null;

            if (target) {
                target.emit('stream:answer', { roomName, fromUserId, sdpAnswer });
            } else {
                io.to(String(toUserId)).emit('stream:answer', { roomName, fromUserId, sdpAnswer });
            }
        } catch (e) {
            console.error('[STREAM] stream:answer error:', e);
        }
    });

    // ── stream:ice ───────────────────────────────────────────────────────────
    // Either side sends an ICE candidate to the other party.
    socket.on('stream:ice', (data) => {
        try {
            const { roomName, toUserId, candidate, sdpMid, sdpMLineIndex } = data;
            const fromUserId = ctx.socketIdUserHash?.[socket.id];

            const room = ctx.activeStreams.get(roomName);
            let targetSocketId = null;

            if (room) {
                // If sender is host → find viewer socket; otherwise find host socket
                if (room.hostUserId == fromUserId) {
                    targetSocketId = room.viewers.get(toUserId);
                } else {
                    targetSocketId = room.hostSocketId;
                }
            }

            const payload = { roomName, fromUserId, candidate, sdpMid, sdpMLineIndex };

            if (targetSocketId) {
                const target = io.sockets.sockets.get(targetSocketId);
                target?.emit('stream:ice', payload);
            } else {
                io.to(String(toUserId)).emit('stream:ice', payload);
            }
        } catch (e) {
            console.error('[STREAM] stream:ice error:', e);
        }
    });

    // ── stream:leave ─────────────────────────────────────────────────────────
    // Viewer voluntarily leaves the stream room.
    socket.on('stream:leave', (data) => {
        try {
            const { roomName, viewerUserId } = data;
            if (!roomName) return;

            socket.leave(roomName);
            const room = ctx.activeStreams.get(roomName);
            if (room) {
                room.viewers.delete(viewerUserId);
                if (room.hostSocketId) {
                    io.to(room.hostSocketId).emit('stream:viewer_left', {
                        roomName,
                        viewerUserId
                    });
                }
            }
            console.log(`[STREAM] Viewer ${viewerUserId} left room ${roomName}`);
        } catch (e) {
            console.error('[STREAM] stream:leave error:', e);
        }
    });

    // ── call:chat_message ─────────────────────────────────────────────────────
    // Relay text chat messages during a call to other participants in the room.
    socket.on('call:chat_message', (data) => {
        try {
            const { roomName, userId, userName, text } = data;
            if (!roomName || !text) return;

            socket.to(roomName).emit('call:chat_message', {
                roomName,
                userId,
                userName,
                text
            });
        } catch (e) {
            console.error('[CALL] call:chat_message error:', e);
        }
    });

    // ── call:reaction ────────────────────────────────────────────────────────
    // Relay call reactions (emoji) to other participants in the room.
    socket.on('call:reaction', (data) => {
        try {
            const { roomName, userId, userName, emoji } = data;
            if (!roomName || !emoji) return;

            // Broadcast to everyone else in the room
            socket.to(roomName).emit('call:reaction', {
                roomName,
                userId,
                userName,
                emoji
            });
            console.log(`[CALL] Reaction ${emoji} from ${userName} in room ${roomName}`);
        } catch (e) {
            console.error('[CALL] call:reaction error:', e);
        }
    });

    // ── stream:end ───────────────────────────────────────────────────────────
    // Host ends the stream; notify all viewers.
    socket.on('stream:end', (data) => {
        try {
            const { roomName } = data;
            if (!roomName) return;

            io.to(roomName).emit('stream:ended', { roomName });
            ctx.activeStreams.delete(roomName);
            socket.leave(roomName);
            console.log(`[STREAM] Stream ended in room ${roomName}`);
        } catch (e) {
            console.error('[STREAM] stream:end error:', e);
        }
    });
}

module.exports = registerCallsListeners;
