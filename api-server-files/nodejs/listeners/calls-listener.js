/**
 * WebRTC Calls Listener для WorldMates Messenger
 * Интегрируется в существующий Node.js проект
 *
 * Использует:
 * - Sequelize модели из ctx
 * - Существующие структуры ctx (userIdSocket, socketIdUserHash и т.д.)
 * - Паттерн registerListeners(socket, io, ctx)
 */

// Импорт TURN credentials helper
const turnHelper = require('../helpers/turn-credentials');

/**
 * Регистрация обработчиков звонков
 * @param {Object} socket - Socket.IO socket объект
 * @param {Object} io - Socket.IO server instance
 * @param {Object} ctx - Контекст приложения с моделями и состоянием
 */
async function registerCallsListeners(socket, io, ctx) {

    // Хранилище активных звонков (можно добавить в ctx если нужно)
    if (!ctx.activeCalls) {
        ctx.activeCalls = new Map(); // roomName -> { initiator, recipient, callType }
    }

    // ===== Хранилище участников групповых звонков =====
    // roomName -> Map<userId, { userName, userAvatar }>
    if (!ctx.activeGroupCalls) {
        ctx.activeGroupCalls = new Map();
    }

    /**
     * Регистрация пользователя для звонков
     * Data: { userId }
     */
    socket.on('call:register', (data) => {
        const userId = data.userId || data.user_id;
        console.log(`[CALLS] 📝 User registered for calls: ${userId}, socket: ${socket.id}`);

        // Добавить в существующую структуру если нужно
        if (!ctx.userIdSocket[userId]) {
            ctx.userIdSocket[userId] = [];
        }
        if (!ctx.userIdSocket[userId].includes(socket)) {
            ctx.userIdSocket[userId].push(socket);
            console.log(`[CALLS] ✅ Added socket to user ${userId}, total sockets: ${ctx.userIdSocket[userId].length}`);
        } else {
            console.log(`[CALLS] ⚠️ Socket already registered for user ${userId}`);
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

                // Найти сокеты получателя
                const recipientSockets = ctx.userIdSocket[toId];
                console.log(`[CALLS] 🔍 Looking for recipient ${toId}, found: ${recipientSockets ? recipientSockets.length : 0} sockets`);

                if (recipientSockets && recipientSockets.length > 0) {
                    // Получить ICE servers с TURN credentials для получателя
                    const iceServers = turnHelper.getIceServers(toId);

                    // ✅ Формируем имя с проверками
                    let fromName = 'Unknown';
                    if (initiator) {
                        const firstName = initiator.first_name || '';
                        const lastName = initiator.last_name || '';
                        fromName = `${firstName} ${lastName}`.trim() || 'Unknown';
                    }

                    // Отправить уведомление о входящем звонке на все устройства
                    const callData = {
                        fromId: fromId,
                        fromName: fromName,
                        fromAvatar: initiator ? (initiator.avatar || '') : '',
                        callType: callType,
                        roomName: roomName,
                        sdpOffer: sdpOffer,
                        iceServers: iceServers  // ✅ Добавлены TURN credentials
                    };

                    // ✅ DEBUG: Логируем что отправляем
                    console.log(`[CALLS] 📤 Sending call:incoming with fromName="${fromName}", fromId=${fromId}, toId=${toId}`);

                    recipientSockets.forEach(recipientSocket => {
                        recipientSocket.emit('call:incoming', callData);
                    });

                    console.log(`[CALLS] Incoming call sent to user ${toId} with TURN credentials (${recipientSockets.length} devices)`);

                } else {
                    // Получатель оффлайн
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

                // Отправить всем участникам кроме инициатора
                members.forEach(member => {
                    if (member.user_id !== fromId) {
                        const memberSockets = ctx.userIdSocket[member.user_id];
                        if (memberSockets && memberSockets.length > 0) {
                            // Получить ICE servers для каждого участника
                            const iceServers = turnHelper.getIceServers(member.user_id);

                            const callData = {
                                groupId: groupId,
                                initiatedBy: fromId,
                                initiatorName: initiatorName,
                                callType: callType,
                                roomName: roomName,
                                sdpOffer: sdpOffer,
                                iceServers: iceServers,
                                maxParticipants: maxParticipants,
                                isPremiumCall: isPro
                            };

                            memberSockets.forEach(memberSocket => {
                                memberSocket.emit('group_call:incoming', callData);
                            });
                        }
                    }
                });

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
                const initiatorSockets = ctx.userIdSocket[initiatorId];

                if (initiatorSockets && initiatorSockets.length > 0) {
                    // Получить ICE servers с TURN credentials для инициатора
                    const iceServers = turnHelper.getIceServers(initiatorId);

                    // Отправить SDP answer инициатору
                    const answerData = {
                        roomName: roomName,
                        sdpAnswer: sdpAnswer,
                        acceptedBy: userId,
                        iceServers: iceServers  // ✅ Добавлены TURN credentials
                    };

                    initiatorSockets.forEach(initiatorSocket => {
                        initiatorSocket.emit('call:answer', answerData);
                    });

                    console.log(`[CALLS] Answer sent to initiator ${initiatorId} with TURN credentials`);
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

            // Опционально: сохранить в БД для восстановления (может не работать если нет таблицы)
            if (ctx.wo_ice_candidates) {
                try {
                    await ctx.wo_ice_candidates.create({
                        room_name: roomName,
                        candidate: JSON.stringify(candidate),
                        sdp_mid: sdpMid,
                        sdp_m_line_index: sdpMLineIndex,
                        created_at: new Date()
                    });
                } catch (dbError) {
                    // ✅ Если таблица не существует или нет нужных колонок - игнорируем
                    console.warn('[CALLS] Could not save ICE candidate to DB (not critical):', dbError.message);
                }
            }

            if (toUserId) {
                // Отправить конкретному пользователю
                const recipientSockets = ctx.userIdSocket[toUserId];
                if (recipientSockets && recipientSockets.length > 0) {
                    const candidateData = {
                        roomName: roomName,
                        fromUserId: fromUserId,
                        candidate: candidate,
                        sdpMLineIndex: sdpMLineIndex,
                        sdpMid: sdpMid
                    };

                    recipientSockets.forEach(recipientSocket => {
                        recipientSocket.emit('ice:candidate', candidateData);
                    });
                }
            } else {
                // Broadcast в комнату (для групповых звонков)
                socket.to(roomName).emit('ice:candidate', {
                    fromUserId: fromUserId,
                    candidate: candidate,
                    sdpMLineIndex: sdpMLineIndex,
                    sdpMid: sdpMid
                });
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

                // Оповестить обоих участников
                const participants = [call.from_id, call.to_id];
                participants.forEach(participantId => {
                    if (participantId !== userId) {
                        const participantSockets = ctx.userIdSocket[participantId];
                        if (participantSockets && participantSockets.length > 0) {
                            participantSockets.forEach(participantSocket => {
                                participantSocket.emit('call:ended', {
                                    roomName: roomName,
                                    reason: reason,
                                    endedBy: userId
                                });
                            });
                        }
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
                // Уведомить инициатора
                const initiatorSockets = ctx.userIdSocket[call.from_id];
                if (initiatorSockets && initiatorSockets.length > 0) {
                    initiatorSockets.forEach(initiatorSocket => {
                        initiatorSocket.emit('call:rejected', {
                            roomName: roomName,
                            rejectedBy: userId
                        });
                    });
                }
            }

            ctx.activeCalls.delete(roomName);

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
                // Отправить конкретному пользователю
                const recipientSockets = ctx.userIdSocket[toUserId];
                if (recipientSockets && recipientSockets.length > 0) {
                    const renegotiateData = {
                        roomName: roomName,
                        fromUserId: fromUserId,
                        sdpOffer: sdpOffer,
                        type: 'renegotiate'
                    };

                    recipientSockets.forEach(recipientSocket => {
                        recipientSocket.emit('call:renegotiate', renegotiateData);
                    });

                    console.log(`[CALLS] ✅ Renegotiation offer sent to user ${toUserId}`);
                } else {
                    console.warn(`[CALLS] ⚠️ No sockets found for user ${toUserId}`);
                }
            } else {
                // Broadcast в комнату (для групповых звонков)
                socket.to(roomName).emit('call:renegotiate', {
                    fromUserId: fromUserId,
                    sdpOffer: sdpOffer,
                    type: 'renegotiate'
                });
                console.log(`[CALLS] ✅ Renegotiation offer broadcast to room ${roomName}`);
            }

        } catch (error) {
            console.error('[CALLS] Error in call:renegotiate:', error);
        }
    });

    /**
     * 🔄 Renegotiation Answer - ответ на renegotiation offer
     * Data: { roomName, fromUserId, toUserId, sdpAnswer, type }
     */
    socket.on('call:renegotiate_answer', async (data) => {
        try {
            const { roomName, fromUserId, toUserId, sdpAnswer } = data;

            console.log(`[CALLS] 🔄 Renegotiation answer from ${fromUserId} to ${toUserId} in room ${roomName}`);

            if (toUserId) {
                // Отправить конкретному пользователю
                const recipientSockets = ctx.userIdSocket[toUserId];
                if (recipientSockets && recipientSockets.length > 0) {
                    const answerData = {
                        roomName: roomName,
                        fromUserId: fromUserId,
                        sdpAnswer: sdpAnswer,
                        type: 'renegotiate_answer'
                    };

                    recipientSockets.forEach(recipientSocket => {
                        recipientSocket.emit('call:renegotiate_answer', answerData);
                    });

                    console.log(`[CALLS] ✅ Renegotiation answer sent to user ${toUserId}`);
                } else {
                    console.warn(`[CALLS] ⚠️ No sockets found for user ${toUserId}`);
                }
            } else {
                // Broadcast в комнату (для групповых звонков)
                socket.to(roomName).emit('call:renegotiate_answer', {
                    fromUserId: fromUserId,
                    sdpAnswer: sdpAnswer,
                    type: 'renegotiate_answer'
                });
                console.log(`[CALLS] ✅ Renegotiation answer broadcast to room ${roomName}`);
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
    socket.on('call:noise_cancellation', (data) => {
        try {
            const { roomName, userId, enabled } = data;
            console.log(`[CALLS] 🔇 Noise cancellation ${enabled ? 'ON' : 'OFF'} for user ${userId} in room ${roomName}`);

            socket.to(roomName).emit('call:noise_cancellation', {
                userId: userId,
                enabled: enabled,
                roomName: roomName
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

            // Зарегистрировать участника в комнате
            if (!ctx.activeGroupCalls.has(roomName)) {
                ctx.activeGroupCalls.set(roomName, new Map());
            }
            const roomParticipants = ctx.activeGroupCalls.get(roomName);
            const existingParticipantIds = [...roomParticipants.keys()];

            roomParticipants.set(userId, { userName, userAvatar, socketId: socket.id });
            socket.join(roomName);

            console.log(`[CALLS] 👥 Room ${roomName} now has ${roomParticipants.size} participants: ${[...roomParticipants.keys()].join(', ')}`);

            // Отправить новому участнику список уже подключённых участников
            const existingList = existingParticipantIds.map(uid => ({
                userId: uid,
                userName: roomParticipants.get(uid)?.userName || 'Unknown',
                userAvatar: roomParticipants.get(uid)?.userAvatar || ''
            }));

            socket.emit('group_call:current_participants', {
                roomName,
                participants: existingList
            });

            // Уведомить каждого существующего участника о новом — чтобы создал offer
            for (const existingUserId of existingParticipantIds) {
                const existingSockets = ctx.userIdSocket[existingUserId];
                if (existingSockets && existingSockets.length > 0) {
                    const iceServers = turnHelper.getIceServers(existingUserId);
                    existingSockets.forEach(s => {
                        s.emit('group_call:participant_joined', {
                            roomName,
                            userId,
                            userName,
                            userAvatar,
                            iceServers,
                            shouldCreateOffer: true // существующий участник создаёт offer
                        });
                    });
                }
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

            const recipientSockets = ctx.userIdSocket[toUserId];
            if (recipientSockets && recipientSockets.length > 0) {
                const iceServers = turnHelper.getIceServers(toUserId);
                recipientSockets.forEach(s => {
                    s.emit('group_call:offer', {
                        roomName,
                        fromUserId,
                        sdpOffer,
                        iceServers
                    });
                });
                console.log(`[CALLS] ✅ Offer relayed to user ${toUserId}`);
            } else {
                console.warn(`[CALLS] ⚠️ No sockets found for user ${toUserId} to relay offer`);
            }
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

            const recipientSockets = ctx.userIdSocket[toUserId];
            if (recipientSockets && recipientSockets.length > 0) {
                recipientSockets.forEach(s => {
                    s.emit('group_call:answer', {
                        roomName,
                        fromUserId,
                        sdpAnswer
                    });
                });
                console.log(`[CALLS] ✅ Answer relayed to user ${toUserId}`);
            } else {
                console.warn(`[CALLS] ⚠️ No sockets found for user ${toUserId} to relay answer`);
            }
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

            if (ctx.activeGroupCalls.has(roomName)) {
                ctx.activeGroupCalls.get(roomName).delete(userId);
                if (ctx.activeGroupCalls.get(roomName).size === 0) {
                    ctx.activeGroupCalls.delete(roomName);
                }
            }

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

            // Очистить комнату
            ctx.activeGroupCalls.delete(roomName);

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
                // Отправить конкретному участнику
                const recipientSockets = ctx.userIdSocket[toUserId];
                if (recipientSockets && recipientSockets.length > 0) {
                    recipientSockets.forEach(s => {
                        s.emit('group_call:ice_candidate', {
                            roomName,
                            fromUserId,
                            candidate,
                            sdpMLineIndex,
                            sdpMid
                        });
                    });
                }
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
                // Fallback: look up via userIdSocket
                const sockets = ctx.userIdSocket?.[toUserId];
                if (sockets?.length) {
                    sockets[0].emit('stream:offer', { roomName, fromUserId, sdpOffer, iceServers });
                }
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
                const sockets = ctx.userIdSocket?.[toUserId];
                if (sockets?.length) {
                    sockets[0].emit('stream:answer', { roomName, fromUserId, sdpAnswer });
                }
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
                // Fallback via userIdSocket
                const sockets = ctx.userIdSocket?.[toUserId];
                if (sockets?.length) sockets[0].emit('stream:ice', payload);
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
