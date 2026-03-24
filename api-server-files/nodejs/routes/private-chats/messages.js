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
 * Encryption:
 *   ► text хранится как AES-256-GCM (cipher_version=2) или Signal DR (cipher_version=3)
 *   ► text_ecb оставлен пустым (AES-128-ECB удалён, сайт переходит на modern E2EE)
 *   ► text_preview — plaintext[:100] для поиска
 *   ► Сервер шифрует при записи, дешифрует при чтении (только GCM/Signal)
 */

'use strict';

const { Op }   = require('sequelize');
const funcs    = require('../../functions/functions');
const crypto   = require('../../helpers/crypto');
const { cleanupExpiredMessages } = require('./secret');
const { TEXT_DECISION, checkText } = require('../../helpers/text-moderator');

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
    // type_two overrides generic 'file' for specific media types
    if (msg.type_two === 'audio' || msg.type_two === 'voice')      type = msg.type_two;
    if (msg.type_two === 'video')                                   type = 'video';
    if (msg.stickers && msg.stickers.includes('.gif'))             type = 'gif';
    if (msg.type_two === 'contact')                                type = 'contact';
    if (msg.lng && msg.lat && msg.lng !== '0' && msg.lat !== '0') type = 'map';
    if (msg.product_id && msg.product_id > 0)                     type = 'product';
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
 *
 * cipher_version=1 (ECB) / 2 (GCM):
 *   – text: зашифрованный текст (Android расшифрует локально).
 * cipher_version=3 (Signal Double Ratchet):
 *   – text + iv + tag: клиентский шифротекст (сервер не расшифровывает).
 *   – signal_header: JSON DR-заголовок для Double Ratchet decrypt на устройстве.
 */
async function buildMessage(ctx, msg, userId) {
    const { position, type } = resolveType(msg, userId);
    const cipherVersion = Number(msg.cipher_version) || 1;
    const isSignal      = cipherVersion === 3;

    // Reply data
    let replyData = null;
    if (msg.reply_id && msg.reply_id > 0) {
        const r = await ctx.wo_messages.findOne({
            attributes: ['id', 'from_id', 'text', 'iv', 'tag', 'cipher_version',
                         'signal_header', 'media', 'time'],
            where: { id: msg.reply_id },
            raw:   true,
        });
        if (r) {
            const replyCv = Number(r.cipher_version) || 1;
            // For Signal replies the server cannot decrypt — show placeholder
            const replyText = replyCv === 3
                ? ''
                : (r.text ? crypto.decryptMessage(r) : '');
            replyData = {
                id:             r.id,
                from_id:        r.from_id,
                text:           replyText,
                cipher_version: replyCv,
                signal_header:  r.signal_header || null,
                media:          r.media || '',
                time:           r.time,
            };
        }
    }

    const sender = await getUserBasicData(ctx, msg.from_id);

    return {
        id:             msg.id,
        from_id:        msg.from_id,
        to_id:          msg.to_id,
        // Encrypted text — client decrypts locally for both GCM and Signal
        text:           msg.text           || '',
        iv:             msg.iv             || null,
        tag:            msg.tag            || null,
        cipher_version: cipherVersion,
        // Signal Double Ratchet header (null for version 1/2)
        signal_header:  isSignal ? (msg.signal_header || null) : null,
        // Other fields
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
        remove_at:      msg.remove_at      || 0,
        album_id:       msg.album_id       || null,
        media_deleted:  msg.media_deleted  || 0,
        user_data:      sender,
    };
}

// ─── Album grouping ───────────────────────────────────────────────────────────

/**
 * Groups consecutive media messages (image/video) from the same sender within
 * 60 seconds into albums.  Mutates the `messages` array in place by setting
 * `album_id` to the id of the first message in each group.
 */
function assignAlbumIds(messages) {
    const ALBUM_WINDOW_SECONDS = 60;
    const ALBUM_MEDIA_TYPES    = new Set(['image', 'video']);

    /**
     * Returns true when a formatted message object should participate in an album.
     * We detect media type from `type_two` (server field) or from the resolved
     * `type` string that already contains the position prefix ("left_video", etc.)
     */
    function isAlbumMedia(msg) {
        if (ALBUM_MEDIA_TYPES.has(msg.type_two)) return true;
        if (msg.type_two === 'image')             return true;
        if (msg.type && msg.type.endsWith('_video')) return true;
        // Generic file that looks like an image/video in the type field
        if (msg.type && (msg.type.endsWith('_image') || msg.type.includes('image'))) return true;
        return false;
    }

    let albumStart   = null;   // index in messages[] where current group started
    let albumSender  = null;
    let albumTime    = null;

    for (let i = 0; i < messages.length; i++) {
        const msg = messages[i];
        const eligible = isAlbumMedia(msg);

        if (
            eligible &&
            albumSender === msg.from_id &&
            albumTime   !== null &&
            (msg.time - albumTime) <= ALBUM_WINDOW_SECONDS
        ) {
            // Extend current album
            const albumId = messages[albumStart].id;
            msg.album_id  = albumId;
            messages[albumStart].album_id = albumId;
            albumTime = msg.time;
        } else if (eligible) {
            // Start a new potential album group
            albumStart  = i;
            albumSender = msg.from_id;
            albumTime   = msg.time;
            // album_id will be assigned once a second item joins the group
            msg.album_id = null;
        } else {
            // Non-media message — reset album tracking
            albumStart  = null;
            albumSender = null;
            albumTime   = null;
        }
    }
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

            // Автоматично видаляємо прострочені секретні повідомлення перед поверненням
            await cleanupExpiredMessages(ctx, userId, recipientId);

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

            // Group consecutive image/video messages into albums
            assignAlbumIds(messages);

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
            const stickers    = req.body.stickers  || '';
            const contact     = req.body.contact   || '';
            // remove_at: Unix timestamp (seconds) для самознищення, 0 = без таймеру
            const removeAt    = parseInt(req.body.remove_at) || 0;

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const hasContent = plaintext || stickers || (lat !== '0' && lng !== '0') || contact;
            if (!hasContent)
                return res.status(400).json({ api_status: 400, error_message: 'Message has no content' });

            // ── Текстовая модерация (только для cipher_version=1/2, не E2EE) ──
            if (plaintext) {
                const clientCv = parseInt(req.body.cipher_version) || 0;
                const textCheck = await checkText(plaintext, clientCv, {
                    senderId: userId, chatType: 'private', entityId: recipientId
                }, ctx);
                if (textCheck.decision === TEXT_DECISION.BLOCK) {
                    return res.status(400).json({
                        api_status:    400,
                        error_message: 'Message blocked by content policy',
                        moderation:    { blocked: true, category: textCheck.category, score: textCheck.score }
                    });
                }
            }

            const now           = Math.floor(Date.now() / 1000);
            const clientVersion = parseInt(req.body.cipher_version) || 0;
            const isSignal      = clientVersion === 3;

            let enc;
            let signalHeader = null;

            if (isSignal) {
                // ── cipher_version=3: client pre-encrypted via Double Ratchet ──
                // Server stores the payload as-is — NO server-side encryption.
                // True E2EE: server never sees plaintext for Signal messages.
                const clientIv  = req.body.iv    || null;
                const clientTag = req.body.tag   || null;
                signalHeader    = req.body.signal_header || null;

                if (!plaintext || !clientIv || !clientTag || !signalHeader) {
                    return res.status(400).json({
                        api_status:    400,
                        error_message: 'cipher_version=3 requires text, iv, tag, signal_header',
                    });
                }

                enc = {
                    text:           plaintext,   // Base64(ciphertext) from client
                    text_ecb:       '',          // Web cannot read Signal messages
                    text_preview:   '',          // No plaintext preview for E2EE
                    iv:             clientIv,
                    tag:            clientTag,
                    cipher_version: 3,
                };
            } else {
                // ── cipher_version=1/2: server encrypts (existing behaviour) ──
                enc = plaintext
                    ? crypto.encryptForStorage(plaintext, now)
                    : { text: '', text_ecb: '', text_preview: '', iv: null, tag: null, cipher_version: crypto.CIPHER_VERSION_GCM };
            }

            const row = await ctx.wo_messages.create({
                from_id:        userId,
                to_id:          recipientId,
                // Encrypted fields
                text:           enc.text,
                text_ecb:       enc.text_ecb,
                text_preview:   enc.text_preview,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                signal_header:  signalHeader,
                // Other fields
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
                remove_at:     removeAt,
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
            // Emit only ONE event per recipient — previously both 'new_message' and
            // 'private_message' were emitted, causing the Android client to call
            // decryptIncoming() twice concurrently for the same E2EE message,
            // which could corrupt the Double Ratchet chain state.
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

            // ── Бот-детекция (не блокирует ответ) ──────────────────────────
            setImmediate(async () => {
                try {
                    const toUser = await ctx.wo_users.findOne({
                        where:      { user_id: recipientId },
                        attributes: ['user_id', 'username', 'type'],
                        raw: true,
                    });
                    if (!toUser || toUser.type !== 'bot') return;

                    const isCmd   = plaintext.startsWith('/');
                    const parts   = isCmd ? plaintext.slice(1).trim().split(/\s+/) : [];
                    const cmdName = isCmd ? (parts[0] || null) : null;
                    const cmdArgs = isCmd && parts.length > 1 ? parts.slice(1).join(' ') : null;

                    const bot = await ctx.wo_bots.findOne({ where: { username: toUser.username }, raw: true });
                    if (!bot) return;

                    const botMsg = await ctx.wo_bot_messages.create({
                        bot_id:       bot.bot_id,
                        chat_id:      String(userId),
                        chat_type:    'private',
                        direction:    'incoming',
                        text:         plaintext || null,
                        is_command:   isCmd ? 1 : 0,
                        command_name: cmdName,
                        command_args: cmdArgs,
                        processed:    0,
                    });

                    await ctx.wo_bots.increment('messages_received', { where: { bot_id: bot.bot_id } });

                    const [, created] = await ctx.wo_bot_users.findOrCreate({
                        where:    { bot_id: bot.bot_id, user_id: userId },
                        defaults: { bot_id: bot.bot_id, user_id: userId, messages_count: 1, last_interaction_at: new Date() },
                    });
                    if (created) await ctx.wo_bots.increment('total_users', { where: { bot_id: bot.bot_id } });
                    else         await ctx.wo_bot_users.increment('messages_count', { where: { bot_id: bot.bot_id, user_id: userId } });

                    if (ctx.botSockets && ctx.botSockets.has(bot.bot_id)) {
                        ctx.botSockets.get(bot.bot_id).emit('user_message', {
                            event:         'user_message',
                            user_id:       userId,
                            message_id:    botMsg.id,
                            text:          plaintext,
                            is_command:    isCmd,
                            command_name:  cmdName,
                            command_args:  cmdArgs,
                            callback_data: null,
                            timestamp:     Date.now(),
                        });
                    }
                    console.log(`[Bot] DM → @${bot.username} from user ${userId}: ${plaintext.substring(0, 60)}`);
                } catch (botErr) {
                    console.error('[Bot/DM-route]', botErr.message);
                }
            });
            // ── Конец бот-детекции ──────────────────────────────────────────

            // ── Business auto-reply / greeting (non-blocking) ───────────────
            if (ctx.handleBusinessAutoReply && ctx.wm_business_profile) {
                setImmediate(async () => {
                    try {
                        // Determine if this is the very first message in the conversation
                        const prevCount = await ctx.wo_messages.count({
                            where: { from_id: userId, to_id: recipientId },
                        });
                        const isFirstMessage = prevCount <= 1; // <=1 because current msg is already saved
                        await ctx.handleBusinessAutoReply(ctx, io, {
                            senderId:      userId,
                            recipientId,
                            isFirstMessage,
                        });
                    } catch (bizErr) {
                        console.error('[Business/auto-reply]', bizErr.message);
                    }
                });
            }
            // ── End business auto-reply ─────────────────────────────────────

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
            const limit           = Math.min(parseInt(req.body.limit) || 30, 100);

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

// ─── NOTIFY MEDIA (called by Android after PHP saves voice/video/audio) ───────
// Android sends this after a successful PHP send_message for media types.
// Node.js fetches the saved message from DB and broadcasts it via Socket.IO
// so both sender and recipient see it in real-time without polling.

function notifyMediaMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const messageId   = parseInt(req.body.message_id) || 0;

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            let msg = null;
            if (messageId > 0) {
                msg = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            }
            // Fallback: fetch the latest message between these two users
            if (!msg) {
                const { Op } = require('sequelize');
                msg = await ctx.wo_messages.findOne({
                    where: {
                        page_id: 0,
                        [Op.or]: [
                            { from_id: userId,      to_id: recipientId },
                            { from_id: recipientId, to_id: userId },
                        ],
                    },
                    order: [['id', 'DESC']],
                    raw: true,
                });
            }

            if (msg) {
                const msgData = await buildMessage(ctx, msg, userId);
                io.to(String(recipientId)).emit('private_message', msgData);
                io.to(String(userId)).emit('new_message', { ...msgData, self: true });
                console.log(`[Node/chat/notify-media] ${userId} -> ${recipientId} msg=${msg.id}`);
            }

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Node/chat/notify-media]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to notify' });
        }
    };
}

// ─── SEND MEDIA MESSAGE ──────────────────────────────────────────────────────
// Creates a message in DB with the media URL (already uploaded via PHP xhr).
// Replaces the broken PHP send_message endpoint for media types.
//
// Supports both private chats (recipient_id) and group chats (group_id).
//
// Body params:
//   - recipient_id: (required for private) recipient user ID
//   - group_id:     (required for group) group chat ID
//   - media_url:    (required) URL of the uploaded media file
//   - media_type:   (required) one of: voice, audio, video, image, file
//   - message_hash_id: (optional) client-side dedup hash
//   - reply_id:     (optional) ID of message being replied to

function sendMediaMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const recipientId = parseInt(req.body.recipient_id) || 0;
            const groupId     = parseInt(req.body.group_id)     || 0;
            const mediaUrl      = (req.body.media_url || '').trim();
            const mediaType     = (req.body.media_type || '').trim();
            const mediaFileName = (req.body.media_file_name || '').trim();
            const hashId        = req.body.message_hash_id || '';
            const replyId       = parseInt(req.body.reply_id) || 0;
            // Optional caption/text attached to the media message
            const captionRaw    = (req.body.caption || '').trim();

            if (!recipientId && !groupId)
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id or group_id is required' });
            if (!mediaUrl)
                return res.status(400).json({ api_status: 400, error_message: 'media_url is required' });
            if (!mediaType)
                return res.status(400).json({ api_status: 400, error_message: 'media_type is required' });

            // Map media_type to type_two (DB column that indicates the media kind)
            const typeTwoMap = {
                'voice': 'voice',
                'audio': 'audio',
                'video': 'video',
                'image': 'image',
                'file':  'file',
            };
            const typeTwo = typeTwoMap[mediaType] || '';

            const now = Math.floor(Date.now() / 1000);

            // Encrypt caption if provided (same GCM+ECB hybrid as sendMessage)
            const enc = captionRaw
                ? crypto.encryptForStorage(captionRaw, now)
                : { text: '', text_ecb: '', text_preview: '', iv: null, tag: null, cipher_version: crypto.CIPHER_VERSION_GCM };

            // Check if the sender has media auto-delete enabled for this chat
            let mediaDeleteAt = null;
            if (recipientId > 0) {
                try {
                    const [settingRows] = await ctx.sequelize.query(
                        `SELECT media_auto_delete_seconds FROM wm_chat_media_settings
                         WHERE user_id = :uid AND chat_id = :cid LIMIT 1`,
                        {
                            replacements: { uid: userId, cid: recipientId },
                            type: ctx.sequelize.constructor.QueryTypes.SELECT,
                        }
                    );
                    const setting = Array.isArray(settingRows[0]) ? settingRows[0][0] : settingRows[0];
                    const secs = setting ? (setting.media_auto_delete_seconds || 0) : 0;
                    if (secs > 0) {
                        mediaDeleteAt = new Date(Date.now() + secs * 1000);
                    }
                } catch (settingErr) {
                    // Non-fatal: if the table doesn't exist yet (pre-migration), skip
                    console.warn('[Node/chat/send-media] Could not fetch media-auto-delete setting:', settingErr.message);
                }
            }

            // Create message in database
            const row = await ctx.wo_messages.create({
                from_id:        userId,
                to_id:          recipientId,
                group_id:       groupId,
                page_id:        0,
                text:           enc.text,
                text_ecb:       enc.text_ecb,
                text_preview:   enc.text_preview,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                stickers:       '',
                media:          mediaUrl,
                mediaFileName:  mediaFileName || (mediaUrl.split('/').pop() || '').split('?')[0],
                time:           now,
                seen:           0,
                reply_id:       replyId,
                lat:            '0',
                lng:            '0',
                type_two:       typeTwo,
                forward:        0,
                edited:         0,
                media_delete_at: mediaDeleteAt,
                media_deleted:   0,
            });

            const sender = await getUserBasicData(ctx, userId);

            if (recipientId > 0) {
                // ── Private chat ──────────────────────────────────────────────
                await funcs.updateOrCreate(ctx.wo_userschat,
                    { user_id: userId, conversation_user_id: recipientId },
                    { time: now, user_id: userId, conversation_user_id: recipientId });
                await funcs.updateOrCreate(ctx.wo_userschat,
                    { user_id: recipientId, conversation_user_id: userId },
                    { time: now, user_id: recipientId, conversation_user_id: userId });

                const msgData = await buildMessage(ctx, row.toJSON ? row.toJSON() : row, userId);

                // Real-time delivery via Socket.IO
                io.to(String(recipientId)).emit('private_message', msgData);
                io.to(String(userId)).emit('new_message', { ...msgData, self: true });

                io.to(String(recipientId)).emit('notification', {
                    id:       String(recipientId),
                    username: sender ? sender.name   : 'User',
                    avatar:   sender ? sender.avatar : '',
                    message:  `[${mediaType}]`,
                    status:   200,
                });

                console.log(`[Node/chat/send-media] ${userId} -> ${recipientId} msg=${row.id} type=${mediaType}`);
                res.json({ api_status: 200, message_data: msgData });

            } else if (groupId > 0) {
                // ── Group chat ────────────────────────────────────────────────
                const rawMsg = row.toJSON ? row.toJSON() : row;
                const { position, type } = resolveType(rawMsg, userId);

                const msgData = {
                    id:            rawMsg.id,
                    from_id:       userId,
                    group_id:      groupId,
                    to_id:         0,
                    text:          '',
                    media:         mediaUrl,
                    mediaFileName: '',
                    stickers:      '',
                    time:          now,
                    time_text:     fmtTime(now),
                    seen:          0,
                    position,
                    type,
                    type_two:      typeTwo,
                    lat:           '0',
                    lng:           '0',
                    reply_id:      replyId,
                    reply:         null,
                    story_id:      0,
                    product_id:    0,
                    forward:       0,
                    edited:        0,
                    user_data:     sender,
                    messageUser:   sender,
                };

                // Broadcast to the group room (Socket.IO group room = "group_<id>")
                io.to('group_' + groupId).emit('group_message', msgData);

                console.log(`[Node/chat/send-media] ${userId} -> group ${groupId} msg=${row.id} type=${mediaType}`);
                res.json({
                    api_status: 200,
                    message_data: msgData,
                    message_id:  rawMsg.id,
                });
            }

        } catch (err) {
            console.error('[Node/chat/send-media]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send media message' });
        }
    };
}

// ─── USER ACTION ──────────────────────────────────────────────────────────────
// Relays an activity status to the recipient (listening, viewing, choosing_sticker, recording_video, etc.)

function userAction(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const action      = req.body.action || '';

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });
            if (!action)
                return res.status(400).json({ api_status: 400, error_message: 'action is required' });

            io.to(String(recipientId)).emit('user_action', {
                user_id: userId,
                action:  action,
            });

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Node/chat/user-action]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send user action' });
        }
    };
}

// ─── USER PRESENCE STATUS ─────────────────────────────────────────────────────
// Returns online status + last_seen for any user.
// Android calls this when opening a chat to initialise the header bar.
//
// POST /api/node/user/status  { user_id }
// Response: { api_status:200, online:bool, last_seen:unix_seconds }

function userStatus(ctx, io) {
    return async (req, res) => {
        try {
            const targetUserId = parseInt(req.body.user_id);
            if (!targetUserId || isNaN(targetUserId)) {
                return res.status(400).json({ api_status: 400, error_message: 'user_id is required' });
            }

            // Check Socket.IO presence map: user is online if they have ≥1 active socket
            const sockets = ctx.userIdSocket[targetUserId];
            const isOnline = Array.isArray(sockets) && sockets.length > 0 &&
                             (ctx.userIdCount[targetUserId] || 0) > 0;

            // Fetch last_seen from DB (stored as Unix seconds in wo_users.lastseen)
            const user = await ctx.wo_users.findOne({
                attributes: ['lastseen'],
                where: { user_id: targetUserId }
            });

            res.json({
                api_status: 200,
                online:     isOnline,
                last_seen:  user ? (user.lastseen || 0) : 0
            });
        } catch (err) {
            console.error('[Node/user/status]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get user status' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

// ─── COUNT messages in a private chat ────────────────────────────────────────

function countMessages(ctx) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            if (!recipientId || isNaN(recipientId)) {
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });
            }
            const total = await ctx.wo_messages.count({
                where: {
                    page_id: 0,
                    [Op.or]: [
                        { from_id: userId,      to_id: recipientId, deleted_one: '0' },
                        { from_id: recipientId, to_id: userId,      deleted_two: '0' },
                    ],
                },
            });
            res.json({ api_status: 200, total_messages: total });
        } catch (err) {
            console.error('[Node/chat/count]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to count messages' });
        }
    };
}

module.exports = { getMessages, sendMessage, loadMore, editMessage, searchMessages, seenMessages, typing, userAction, notifyMediaMessage, sendMediaMessage, userStatus, countMessages };
