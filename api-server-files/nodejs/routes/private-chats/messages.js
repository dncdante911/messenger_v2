/**
 * Private Chats — Messages
 * GET, SEND, LOADMORE, EDIT, SEARCH, SEEN, TYPING
 *
 * Endpoints:
 *   POST /api/node/chat/get          – fetch message history
 *   POST /api/node/chat/send         – send text message
 *   POST /api/node/chat/loadmore     – paginated load more (older messages)
 *   POST /api/node/chat/edit         – edit a sent message
 *   POST /api/node/chat/search       – search messages in conversation
 *   POST /api/node/chat/seen         – mark messages as read
 *   POST /api/node/chat/typing       – typing indicator
 *
 * Encryption: гибридная система
 *   ► text хранится как AES-256-GCM (WorldMates Android)
 *   ► text_ecb хранится как AES-128-ECB (WoWonder браузер)
 *   ► text_preview — plaintext[:100] для поиска
 *   ► Сервер шифрует при записи, дешифрует при чтении
 *   ► Android получает зашифрованные данные + iv + tag + cipher_version
 *   ► WoWonder получает text_ecb через PHP get_messages.php
 */

'use strict';

const { Op }   = require('sequelize');
const funcs    = require('../../functions/functions');
const crypto   = require('../../helpers/crypto');

// ─── helpers ─────────────────────────────────────────────────────────────────

function fmtTime(ts) {
    if (!ts) return '';
    const now = Math.floor(Date.now() / 1000);
    const d   = new Date(ts * 1000);
    if (ts < now - 86400) {
        return String(d.getMonth() + 1).padStart(2, '0') + '.' +
               String(d.getDate()).padStart(2, '0') + '.' +
               String(d.getFullYear()).slice(-2);
    }
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}

function resolveType(msg, userId) {
    const pos = msg.from_id === userId ? 'right' : 'left';
    let type = '';
    if (msg.media)                                                 type = 'file';
    if (msg.stickers && msg.stickers.includes('.gif'))            type = 'gif';
    if (msg.type_two === 'contact')                               type = 'contact';
    if (msg.lng && msg.lat && msg.lng !== '0' && msg.lat !== '0') type = 'map';
    if (msg.product_id && msg.product_id > 0)                    type = 'product';
    return { position: pos, type: pos + '_' + type };
}

async function getUserBasicData(ctx, userId) {
    try {
        const u = await ctx.wo_users.findOne({
            attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'lastseen', 'status'],
            where: { user_id: userId },
            raw: true,
        });
        if (!u) return null;
        u.name = (u.first_name && u.last_name)
            ? u.first_name + ' ' + u.last_name
            : u.username;
        return u;
    } catch { return null; }
}

/**
 * Собирает объект сообщения для ответа клиенту.
 * Поле text возвращается зашифрованным (GCM) + iv, tag, cipher_version.
 * Android расшифрует на стороне клиента с помощью EncryptionUtility.kt.
 */
async function buildMessage(ctx, msg, userId) {
    const { position, type } = resolveType(msg, userId);

    // Reply data
    let replyData = null;
    if (msg.reply_id && msg.reply_id > 0) {
        const r = await ctx.wo_messages.findOne({
            attributes: ['id', 'from_id', 'text', 'iv', 'tag', 'cipher_version', 'media', 'time'],
            where: { id: msg.reply_id },
            raw: true,
        });
        if (r) {
            // Дешифруем текст реплая для превью
            replyData = {
                id:      r.id,
                from_id: r.from_id,
                text:    r.text ? crypto.decryptMessage(r) : '',
                media:   r.media || '',
                time:    r.time,
            };
        }
    }

    const sender = await getUserBasicData(ctx, msg.from_id);

    return {
        id:             msg.id,
        from_id:        msg.from_id,
        to_id:          msg.to_id,
        // Зашифрованный текст (GCM) — Android расшифрует локально
        text:           msg.text           || '',
        iv:             msg.iv             || null,
        tag:            msg.tag            || null,
        cipher_version: msg.cipher_version != null ? Number(msg.cipher_version) : 1,
        // Остальные поля
        media:          msg.media          || '',
        mediaFileName:  msg.mediaFileName  || '',
        stickers:       msg.stickers       || '',
        time:           msg.time,
        time_text:      fmtTime(msg.time),
        seen:           msg.seen,
        position,
        type,
        type_two:       msg.type_two       || '',
        lat:            msg.lat            || '0',
        lng:            msg.lng            || '0',
        reply_id:       msg.reply_id       || 0,
        reply:          replyData,
        story_id:       msg.story_id       || 0,
        product_id:     msg.product_id     || 0,
        forward:        msg.forward        || 0,
        edited:         msg.edited         || 0,
        user_data:      sender,
    };
}

// ─── GET messages ─────────────────────────────────────────────────────────────

function getMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const limit           = Math.min(parseInt(req.body.limit) || 30, 100);
            const afterMessageId  = parseInt(req.body.after_message_id)  || 0;
            const beforeMessageId = parseInt(req.body.before_message_id) || 0;
            const messageId       = parseInt(req.body.message_id)         || 0;

            const where = {
                page_id: 0,
                [Op.or]: [
                    { from_id: recipientId, to_id: userId,      deleted_two: '0' },
                    { from_id: userId,      to_id: recipientId, deleted_one: '0' },
                ],
            };

            if (messageId > 0)        where.id = messageId;
            else if (afterMessageId  > 0) where.id = { [Op.gt]: afterMessageId };
            else if (beforeMessageId > 0) where.id = { [Op.lt]: beforeMessageId };

            const rows = await ctx.wo_messages.findAll({
                where,
                order: [['id', 'DESC']],
                limit,
                raw: true,
            });

            const messages = [];
            for (const m of rows.reverse()) {
                messages.push(await buildMessage(ctx, m, userId));
            }

            res.json({ api_status: 200, messages });
        } catch (err) {
            console.error('[Node/chat/get]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch messages' });
        }
    };
}

// ─── SEND message ─────────────────────────────────────────────────────────────

function sendMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const plaintext   = (req.body.text || '').trim();
            const replyId     = parseInt(req.body.reply_id) || 0;
            const storyId     = parseInt(req.body.story_id) || 0;
            const lat         = req.body.lat      || '0';
            const lng         = req.body.lng      || '0';
            const stickers    = req.body.stickers || '';
            const contact     = req.body.contact  || '';

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const hasContent = plaintext || stickers || (lat !== '0' && lng !== '0') || contact;
            if (!hasContent)
                return res.status(400).json({ api_status: 400, error_message: 'Message has no content' });

            const now = Math.floor(Date.now() / 1000);

            // Шифруем текст (AES-256-GCM + AES-128-ECB для совместимости с WoWonder)
            const enc = plaintext
                ? crypto.encryptForStorage(plaintext, now)
                : { text: '', text_ecb: '', text_preview: '', iv: null, tag: null, cipher_version: 1 };

            const row = await ctx.wo_messages.create({
                from_id:        userId,
                to_id:          recipientId,
                // Зашифрованные поля
                text:           enc.text,
                text_ecb:       enc.text_ecb,
                text_preview:   enc.text_preview,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                // Остальные поля
                stickers,
                media:         '',
                mediaFileName: '',
                time:          now,
                seen:          0,
                reply_id:      replyId,
                story_id:      storyId,
                lat,
                lng,
                page_id:       0,
                type_two:      contact ? 'contact' : '',
                forward:       0,
                edited:        0,
            });

            // Обновляем метаданные переписки
            await funcs.updateOrCreate(ctx.wo_userschat,
                { user_id: userId,      conversation_user_id: recipientId },
                { time: now, user_id: userId, conversation_user_id: recipientId });
            await funcs.updateOrCreate(ctx.wo_userschat,
                { user_id: recipientId, conversation_user_id: userId },
                { time: now, user_id: recipientId, conversation_user_id: userId });

            const sender  = await getUserBasicData(ctx, userId);
            const msgData = await buildMessage(ctx, row.toJSON ? row.toJSON() : row, userId);

            // Real-time доставка через Socket.IO
            io.to(String(recipientId)).emit('new_message',     msgData);
            io.to(String(recipientId)).emit('private_message', msgData);
            io.to(String(userId)).emit('new_message', { ...msgData, self: true });

            // Уведомление (для отображения в списке чатов)
            io.to(String(recipientId)).emit('notification', {
                id:       String(recipientId),
                username: sender ? sender.name   : 'User',
                avatar:   sender ? sender.avatar : '',
                message:  plaintext || (stickers ? '[sticker]' : '[media]'),
                status:   200,
            });

            console.log(`[Node/chat/send] ${userId} -> ${recipientId} msg=${row.id} gcm=${enc.cipher_version === 2}`);
            res.json({ api_status: 200, message_data: msgData });
        } catch (err) {
            console.error('[Node/chat/send]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send message' });
        }
    };
}

// ─── LOADMORE (older messages) ────────────────────────────────────────────────

function loadMore(ctx, io) {
    return async (req, res) => {
        try {
            const userId          = req.userId;
            const recipientId     = parseInt(req.body.recipient_id);
            const beforeMessageId = parseInt(req.body.before_message_id) || 0;
            const limit           = Math.min(parseInt(req.body.limit) || 15, 50);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const where = {
                page_id: 0,
                [Op.or]: [
                    { from_id: recipientId, to_id: userId,      deleted_two: '0' },
                    { from_id: userId,      to_id: recipientId, deleted_one: '0' },
                ],
            };
            if (beforeMessageId > 0) where.id = { [Op.lt]: beforeMessageId };

            const rows = await ctx.wo_messages.findAll({
                where,
                order: [['id', 'DESC']],
                limit,
                raw: true,
            });

            const messages = [];
            for (const m of rows.reverse()) {
                messages.push(await buildMessage(ctx, m, userId));
            }

            res.json({ api_status: 200, messages });
        } catch (err) {
            console.error('[Node/chat/loadmore]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to load more messages' });
        }
    };
}

// ─── EDIT message ─────────────────────────────────────────────────────────────

function editMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            const newText   = (req.body.text || '').trim();

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!newText)
                return res.status(400).json({ api_status: 400, error_message: 'text is required' });

            const msg = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            if (!msg)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });
            if (msg.from_id !== userId)
                return res.status(403).json({ api_status: 403, error_message: 'Cannot edit someone else\'s message' });

            // Перешифровываем с ОРИГИНАЛЬНЫМ timestamp (ключ не меняется)
            const enc = crypto.encryptForStorage(newText, msg.time);

            await ctx.wo_messages.update(
                {
                    text:           enc.text,
                    text_ecb:       enc.text_ecb,
                    text_preview:   enc.text_preview,
                    iv:             enc.iv,
                    tag:            enc.tag,
                    cipher_version: enc.cipher_version,
                    edited:         1,
                },
                { where: { id: messageId } }
            );

            // Уведомляем обе стороны
            const editPayload = {
                message_id:     messageId,
                text:           enc.text,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                time:           msg.time,
                edited:         1,
            };
            io.to(String(msg.to_id)).emit('message_edited', editPayload);
            io.to(String(userId)).emit('message_edited',    editPayload);

            res.json({ api_status: 200, ...editPayload });
        } catch (err) {
            console.error('[Node/chat/edit]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to edit message' });
        }
    };
}

// ─── SEARCH messages ──────────────────────────────────────────────────────────
// Поиск по text_preview (plaintext[:100]), а не по зашифрованному text.

function searchMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id || req.body.chat_id);
            const query       = (req.body.query || '').trim();
            const limit       = Math.min(parseInt(req.body.limit) || 50, 100);
            const offset      = parseInt(req.body.offset) || 0;

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });
            if (query.length < 2)
                return res.status(400).json({ api_status: 400, error_message: 'Query must be at least 2 characters' });

            const rows = await ctx.wo_messages.findAll({
                where: {
                    page_id: 0,
                    // Ищем по text_preview (plaintext) а не по зашифрованному text
                    text_preview: { [Op.like]: `%${query}%` },
                    [Op.or]: [
                        { from_id: userId,      to_id: recipientId, deleted_one: '0' },
                        { from_id: recipientId, to_id: userId,      deleted_two: '0' },
                    ],
                },
                order:  [['id', 'DESC']],
                limit,
                offset,
                raw:    true,
            });

            const messages = [];
            for (const m of rows) {
                messages.push(await buildMessage(ctx, m, userId));
            }

            res.json({ api_status: 200, messages, count: messages.length });
        } catch (err) {
            console.error('[Node/chat/search]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to search messages' });
        }
    };
}

// ─── SEEN messages ────────────────────────────────────────────────────────────

function seenMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const seen = Math.floor(Date.now() / 1000);
            await ctx.wo_messages.update(
                { seen },
                { where: { from_id: recipientId, to_id: userId, seen: 0 } }
            );

            io.to(String(recipientId)).emit('lastseen', { can_seen: 1, seen, user_id: userId });

            res.json({ api_status: 200, message: 'Messages marked as seen' });
        } catch (err) {
            console.error('[Node/chat/seen]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to mark messages as seen' });
        }
    };
}

// ─── TYPING ───────────────────────────────────────────────────────────────────

function typing(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const isTyping    = req.body.typing === 'true' || req.body.typing === true;

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            io.to(String(recipientId)).emit(isTyping ? 'typing' : 'typing_done', {
                from_id: userId,
                to_id:   recipientId,
            });

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Node/chat/typing]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send typing status' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { getMessages, sendMessage, loadMore, editMessage, searchMessages, seenMessages, typing };
