/**
 * WebRTC Calls Handler для WorldMates Messenger
 * Node.js + Socket.IO обработчик для аудио/видео вызовов
 * 
 * Установка:
 * npm install socket.io socket.io-client express mysql2 cors
 */

const express = require('express');
const http = require('http');
const socketIO = require('socket.io');
const mysql = require('mysql2/promise');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// Настройка БД
const dbConfig = {
    host: 'localhost',
    user: 'root',
    password: 'your_password',
    database: 'socialhub',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

const pool = mysql.createPool(dbConfig);

// Хранилище активных пользователей и вызовов
const users = new Map(); // userId -> { socket, roomName, callType }
const calls = new Map(); // roomName -> { initiator, recipient, callType, createdAt }
const groupCalls = new Map(); // roomName -> { groupId, initiator, participants, callType }

/**
 * Socket.IO обработчики
 */
io.on('connection', (socket) => {
    console.log(`User connected: ${socket.id}`);
    
    /**
     * Пользователь подключается и регистрируется
     */
    socket.on('user:register', (data) => {
        const userId = data.userId;
        users.set(userId, {
            socket: socket,
            status: 'online'
        });
        console.log(`User registered: ${userId} (socket: ${socket.id})`);
    });
    
    /**
     * Инициатор вызова отправляет offer
     * Данные:
     * - fromId: ID инициатора
     * - toId: ID получателя (для 1-на-1) ИЛИ
     * - groupId: ID группы (для группового)
     * - callType: 'audio' или 'video'
     * - roomName: уникальное имя кімнати
     * - sdpOffer: SDP предложение
     */
    socket.on('call:initiate', async (data) => {
        try {
            const { fromId, toId, groupId, callType, roomName, sdpOffer } = data;
            
            console.log(`Call initiated: ${fromId} -> ${toId || groupId} (room: ${roomName})`);
            
            // Сохранить в БД
            if (toId) {
                // 1-на-1 вызов
                const conn = await pool.getConnection();
                await conn.execute(
                    `INSERT INTO wo_calls (from_id, to_id, call_type, status, room_name, created_at) 
                     VALUES (?, ?, ?, ?, ?, NOW())`,
                    [fromId, toId, callType, 'ringing', roomName]
                );
                conn.release();
                
                // Найти сокет получателя
                const recipientUser = users.get(toId);
                if (recipientUser) {
                    // Получить данные о инициаторе
                    const initiatorData = await getUserData(fromId);
                    
                    // Отправить incoming call
                    recipientUser.socket.emit('call:incoming', {
                        fromId: fromId,
                        fromName: initiatorData.first_name + ' ' + initiatorData.last_name,
                        fromAvatar: initiatorData.avatar,
                        callType: callType,
                        roomName: roomName,
                        sdpOffer: sdpOffer
                    });
                    
                    // Отправить push notification (если есть device_id)
                    await sendPushNotification(toId, {
                        title: initiatorData.first_name + ' ' + initiatorData.last_name,
                        body: `Incoming ${callType} call`,
                        callData: {
                            callType: callType,
                            roomName: roomName,
                            fromId: fromId,
                            fromName: initiatorData.first_name + ' ' + initiatorData.last_name,
                            fromAvatar: initiatorData.avatar
                        }
                    });
                } else {
                    // Получатель офлайн - обновить статус
                    const conn = await pool.getConnection();
                    await conn.execute(
                        `UPDATE wo_calls SET status = ? WHERE room_name = ?`,
                        ['missed', roomName]
                    );
                    conn.release();
                }
            } else if (groupId) {
                // Групповой вызов
                const conn = await pool.getConnection();
                await conn.execute(
                    `INSERT INTO wo_group_calls (group_id, initiated_by, call_type, status, room_name, created_at) 
                     VALUES (?, ?, ?, ?, ?, NOW())`,
                    [groupId, fromId, callType, 'ringing', roomName]
                );
                conn.release();
                
                // Получить членов группы
                const members = await getGroupMembers(groupId);
                const initiatorData = await getUserData(fromId);
                
                members.forEach(member => {
                    if (member.user_id !== fromId) {
                        const memberUser = users.get(member.user_id);
                        if (memberUser) {
                            memberUser.socket.emit('group_call:incoming', {
                                groupId: groupId,
                                initiatedBy: fromId,
                                initiatorName: initiatorData.first_name + ' ' + initiatorData.last_name,
                                callType: callType,
                                roomName: roomName,
                                sdpOffer: sdpOffer
                            });
                        }
                    }
                });
            }
            
        } catch (error) {
            console.error('Error in call:initiate:', error);
            socket.emit('error', { message: 'Failed to initiate call' });
        }
    });
    
    /**
     * Получатель принимает вызов
     * Данные:
     * - roomName: имя кімнати
     * - fromId: ID того, кто примет вызов
     * - sdpAnswer: SDP ответ
     */
    socket.on('call:accept', async (data) => {
        try {
            const { roomName, fromId, sdpAnswer } = data;
            
            console.log(`Call accepted: ${fromId} in room ${roomName}`);
            
            // Обновить статус
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ?, accepted_at = NOW() WHERE room_name = ?`,
                ['connected', roomName]
            );
            conn.release();
            
            // Получить инициатора из БД
            const callInfo = await getCallInfo(roomName);
            if (callInfo) {
                const initiatorUser = users.get(callInfo.from_id);
                if (initiatorUser) {
                    // Отправить answer инициатору
                    initiatorUser.socket.emit('call:answer', {
                        roomName: roomName,
                        sdpAnswer: sdpAnswer
                    });
                }
            }
            
        } catch (error) {
            console.error('Error in call:accept:', error);
        }
    });
    
    /**
     * Обмен ICE candidates
     */
    socket.on('ice:candidate', (data) => {
        try {
            const { roomName, candidate, sdpMLineIndex, sdpMid } = data;
            
            // Отправить ICE candidate всем в комнате кроме отправителя
            socket.broadcast.emit('ice:candidate', {
                roomName: roomName,
                candidate: candidate,
                sdpMLineIndex: sdpMLineIndex,
                sdpMid: sdpMid
            });
            
        } catch (error) {
            console.error('Error in ice:candidate:', error);
        }
    });
    
    /**
     * Завершить вызов
     */
    socket.on('call:end', async (data) => {
        try {
            const { roomName, reason } = data;
            
            console.log(`Call ended: ${roomName} (reason: ${reason})`);
            
            // Обновить в БД
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ?, ended_at = NOW() WHERE room_name = ?`,
                ['ended', roomName]
            );
            conn.release();
            
            // Оповестить других пользователей
            io.to(roomName).emit('call:ended', {
                reason: reason
            });
            
            // Удалить из памяти
            calls.delete(roomName);
            
        } catch (error) {
            console.error('Error in call:end:', error);
        }
    });
    
    /**
     * Отклонить вызов
     */
    socket.on('call:reject', async (data) => {
        try {
            const { roomName } = data;
            
            console.log(`Call rejected: ${roomName}`);
            
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ? WHERE room_name = ?`,
                ['rejected', roomName]
            );
            conn.release();
            
            io.to(roomName).emit('call:rejected', {
                roomName: roomName
            });
            
            calls.delete(roomName);
            
        } catch (error) {
            console.error('Error in call:reject:', error);
        }
    });
    
    /**
     * Отключение соединения
     */
    socket.on('disconnect', () => {
        // Найти и удалить пользователя
        let disconnectedUserId = null;
        for (const [userId, userData] of users.entries()) {
            if (userData.socket.id === socket.id) {
                disconnectedUserId = userId;
                users.delete(userId);
                break;
            }
        }
        
        console.log(`User disconnected: ${disconnectedUserId} (socket: ${socket.id})`);
    });
});

/**
 * Вспомогательные функции
 */

async function getUserData(userId) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT first_name, last_name, avatar FROM wo_users WHERE user_id = ?',
            [userId]
        );
        conn.release();
        return rows[0] || {};
    } catch (error) {
        console.error('Error getting user data:', error);
        return {};
    }
}

async function getGroupMembers(groupId) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT user_id FROM wo_group_members WHERE group_id = ? AND active = 1',
            [groupId]
        );
        conn.release();
        return rows;
    } catch (error) {
        console.error('Error getting group members:', error);
        return [];
    }
}

async function getCallInfo(roomName) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT * FROM wo_calls WHERE room_name = ? LIMIT 1',
            [roomName]
        );
        conn.release();
        return rows[0] || null;
    } catch (error) {
        console.error('Error getting call info:', error);
        return null;
    }
}

async function sendPushNotification(userId, notificationData) {
    // Integacija с Firebase Cloud Messaging
    // TODO: Имплементировать FCM
    console.log(`Push notification would be sent to user ${userId}:`, notificationData);
}

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`WebRTC Signaling Server running on port ${PORT}`);
});

module.exports = { io, server };

EOF
cat /home/claude/socket-calls-handler.js
Output

/**
 * WebRTC Calls Handler для WorldMates Messenger
 * Node.js + Socket.IO обработчик для аудио/видео вызовов
 * 
 * Установка:
 * npm install socket.io socket.io-client express mysql2 cors
 */

const express = require('express');
const http = require('http');
const socketIO = require('socket.io');
const mysql = require('mysql2/promise');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// Настройка БД
const dbConfig = {
    host: 'localhost',
    user: 'root',
    password: 'your_password',
    database: 'socialhub',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

const pool = mysql.createPool(dbConfig);

// Хранилище активных пользователей и вызовов
const users = new Map(); // userId -> { socket, roomName, callType }
const calls = new Map(); // roomName -> { initiator, recipient, callType, createdAt }
const groupCalls = new Map(); // roomName -> { groupId, initiator, participants, callType }

/**
 * Socket.IO обработчики
 */
io.on('connection', (socket) => {
    console.log(`User connected: ${socket.id}`);
    
    /**
     * Пользователь подключается и регистрируется
     */
    socket.on('user:register', (data) => {
        const userId = data.userId;
        users.set(userId, {
            socket: socket,
            status: 'online'
        });
        console.log(`User registered: ${userId} (socket: ${socket.id})`);
    });
    
    /**
     * Инициатор вызова отправляет offer
     * Данные:
     * - fromId: ID инициатора
     * - toId: ID получателя (для 1-на-1) ИЛИ
     * - groupId: ID группы (для группового)
     * - callType: 'audio' или 'video'
     * - roomName: уникальное имя кімнати
     * - sdpOffer: SDP предложение
     */
    socket.on('call:initiate', async (data) => {
        try {
            const { fromId, toId, groupId, callType, roomName, sdpOffer } = data;
            
            console.log(`Call initiated: ${fromId} -> ${toId || groupId} (room: ${roomName})`);
            
            // Сохранить в БД
            if (toId) {
                // 1-на-1 вызов
                const conn = await pool.getConnection();
                await conn.execute(
                    `INSERT INTO wo_calls (from_id, to_id, call_type, status, room_name, created_at) 
                     VALUES (?, ?, ?, ?, ?, NOW())`,
                    [fromId, toId, callType, 'ringing', roomName]
                );
                conn.release();
                
                // Найти сокет получателя
                const recipientUser = users.get(toId);
                if (recipientUser) {
                    // Получить данные о инициаторе
                    const initiatorData = await getUserData(fromId);
                    
                    // Отправить incoming call
                    recipientUser.socket.emit('call:incoming', {
                        fromId: fromId,
                        fromName: initiatorData.first_name + ' ' + initiatorData.last_name,
                        fromAvatar: initiatorData.avatar,
                        callType: callType,
                        roomName: roomName,
                        sdpOffer: sdpOffer
                    });
                    
                    // Отправить push notification (если есть device_id)
                    await sendPushNotification(toId, {
                        title: initiatorData.first_name + ' ' + initiatorData.last_name,
                        body: `Incoming ${callType} call`,
                        callData: {
                            callType: callType,
                            roomName: roomName,
                            fromId: fromId,
                            fromName: initiatorData.first_name + ' ' + initiatorData.last_name,
                            fromAvatar: initiatorData.avatar
                        }
                    });
                } else {
                    // Получатель офлайн - обновить статус
                    const conn = await pool.getConnection();
                    await conn.execute(
                        `UPDATE wo_calls SET status = ? WHERE room_name = ?`,
                        ['missed', roomName]
                    );
                    conn.release();
                }
            } else if (groupId) {
                // Групповой вызов
                const conn = await pool.getConnection();
                await conn.execute(
                    `INSERT INTO wo_group_calls (group_id, initiated_by, call_type, status, room_name, created_at) 
                     VALUES (?, ?, ?, ?, ?, NOW())`,
                    [groupId, fromId, callType, 'ringing', roomName]
                );
                conn.release();
                
                // Получить членов группы
                const members = await getGroupMembers(groupId);
                const initiatorData = await getUserData(fromId);
                
                members.forEach(member => {
                    if (member.user_id !== fromId) {
                        const memberUser = users.get(member.user_id);
                        if (memberUser) {
                            memberUser.socket.emit('group_call:incoming', {
                                groupId: groupId,
                                initiatedBy: fromId,
                                initiatorName: initiatorData.first_name + ' ' + initiatorData.last_name,
                                callType: callType,
                                roomName: roomName,
                                sdpOffer: sdpOffer
                            });
                        }
                    }
                });
            }
            
        } catch (error) {
            console.error('Error in call:initiate:', error);
            socket.emit('error', { message: 'Failed to initiate call' });
        }
    });
    
    /**
     * Получатель принимает вызов
     * Данные:
     * - roomName: имя кімнати
     * - fromId: ID того, кто примет вызов
     * - sdpAnswer: SDP ответ
     */
    socket.on('call:accept', async (data) => {
        try {
            const { roomName, fromId, sdpAnswer } = data;
            
            console.log(`Call accepted: ${fromId} in room ${roomName}`);
            
            // Обновить статус
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ?, accepted_at = NOW() WHERE room_name = ?`,
                ['connected', roomName]
            );
            conn.release();
            
            // Получить инициатора из БД
            const callInfo = await getCallInfo(roomName);
            if (callInfo) {
                const initiatorUser = users.get(callInfo.from_id);
                if (initiatorUser) {
                    // Отправить answer инициатору
                    initiatorUser.socket.emit('call:answer', {
                        roomName: roomName,
                        sdpAnswer: sdpAnswer
                    });
                }
            }
            
        } catch (error) {
            console.error('Error in call:accept:', error);
        }
    });
    
    /**
     * Обмен ICE candidates
     */
    socket.on('ice:candidate', (data) => {
        try {
            const { roomName, candidate, sdpMLineIndex, sdpMid } = data;
            
            // Отправить ICE candidate всем в комнате кроме отправителя
            socket.broadcast.emit('ice:candidate', {
                roomName: roomName,
                candidate: candidate,
                sdpMLineIndex: sdpMLineIndex,
                sdpMid: sdpMid
            });
            
        } catch (error) {
            console.error('Error in ice:candidate:', error);
        }
    });
    
    /**
     * Завершить вызов
     */
    socket.on('call:end', async (data) => {
        try {
            const { roomName, reason } = data;
            
            console.log(`Call ended: ${roomName} (reason: ${reason})`);
            
            // Обновить в БД
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ?, ended_at = NOW() WHERE room_name = ?`,
                ['ended', roomName]
            );
            conn.release();
            
            // Оповестить других пользователей
            io.to(roomName).emit('call:ended', {
                reason: reason
            });
            
            // Удалить из памяти
            calls.delete(roomName);
            
        } catch (error) {
            console.error('Error in call:end:', error);
        }
    });
    
    /**
     * Отклонить вызов
     */
    socket.on('call:reject', async (data) => {
        try {
            const { roomName } = data;
            
            console.log(`Call rejected: ${roomName}`);
            
            const conn = await pool.getConnection();
            await conn.execute(
                `UPDATE wo_calls SET status = ? WHERE room_name = ?`,
                ['rejected', roomName]
            );
            conn.release();
            
            io.to(roomName).emit('call:rejected', {
                roomName: roomName
            });
            
            calls.delete(roomName);
            
        } catch (error) {
            console.error('Error in call:reject:', error);
        }
    });
    
    /**
     * Отключение соединения
     */
    socket.on('disconnect', () => {
        // Найти и удалить пользователя
        let disconnectedUserId = null;
        for (const [userId, userData] of users.entries()) {
            if (userData.socket.id === socket.id) {
                disconnectedUserId = userId;
                users.delete(userId);
                break;
            }
        }
        
        console.log(`User disconnected: ${disconnectedUserId} (socket: ${socket.id})`);
    });
});

/**
 * Вспомогательные функции
 */

async function getUserData(userId) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT first_name, last_name, avatar FROM wo_users WHERE user_id = ?',
            [userId]
        );
        conn.release();
        return rows[0] || {};
    } catch (error) {
        console.error('Error getting user data:', error);
        return {};
    }
}

async function getGroupMembers(groupId) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT user_id FROM wo_group_members WHERE group_id = ? AND active = 1',
            [groupId]
        );
        conn.release();
        return rows;
    } catch (error) {
        console.error('Error getting group members:', error);
        return [];
    }
}

async function getCallInfo(roomName) {
    try {
        const conn = await pool.getConnection();
        const [rows] = await conn.execute(
            'SELECT * FROM wo_calls WHERE room_name = ? LIMIT 1',
            [roomName]
        );
        conn.release();
        return rows[0] || null;
    } catch (error) {
        console.error('Error getting call info:', error);
        return null;
    }
}

async function sendPushNotification(userId, notificationData) {
    // Integacija с Firebase Cloud Messaging
    // TODO: Имплементировать FCM
    console.log(`Push notification would be sent to user ${userId}:`, notificationData);
}

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`WebRTC Signaling Server running on port ${PORT}`);
});

module.exports = { io, server };