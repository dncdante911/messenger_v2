/**
 * Private Chats — Actions
 * DELETE, REACT, PIN (message), FORWARD
 *
 * Endpoints:
 *   POST /api/node/chat/delete          – delete one or both sides of a message
 *   POST /api/node/chat/react           – add / toggle reaction on a message
 *   POST /api/node/chat/pin             – pin / unpin a message in a conversation
 *   POST /api/node/chat/forward         – forward message to user(s)
 */

'use strict';

const { Op } = require('sequelize');
const crypto  = require('../../helpers/crypto');

// ─── DELETE message ──────────────────────────────────────────────────────────

function deleteMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            // "just_me" – soft-delete only for caller; "everyone" – both sides (only own messages)
            const deleteType = req.body.delete_type || 'just_me';

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });

            const msg = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            if (!msg)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });

            const isSender = msg.from_id === userId;

            if (deleteType === 'everyone' && !isSender)
                return res.status(403).json({ api_status: 403, error_message: 'Can only delete own messages for everyone' });

            if (deleteType === 'everyone') {
                await ctx.wo_messages.update(
                    { deleted_one: '1', deleted_two: '1' },
                    { where: { id: messageId } }
                );
                // notify both sides
                io.to(String(msg.to_id)).emit('message_deleted',  { message_id: messageId, delete_type: 'everyone' });
                io.to(String(msg.from_id)).emit('message_deleted',{ message_id: messageId, delete_type: 'everyone' });
            } else {
                // soft-delete for the calling user only
                const field = isSender ? 'deleted_one' : 'deleted_two';
                await ctx.wo_messages.update({ [field]: '1' }, { where: { id: messageId } });
                io.to(String(userId)).emit('message_deleted', { message_id: messageId, delete_type: 'just_me' });
            }

            res.json({ api_status: 200, message: 'Message deleted' });
        } catch (err) {
            console.error('[Node/chat/delete]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to delete message' });
        }
    };
}

// ─── REACT to message ────────────────────────────────────────────────────────
// Uses wo_reactions table: user_id, message_id, reaction
// Toggle: same reaction = remove; different = replace

function reactMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id || req.body.id);
            const reaction  = (req.body.reaction || '').trim();

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!reaction)
                return res.status(400).json({ api_status: 400, error_message: 'reaction is required' });

            const msg = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            if (!msg)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });

            // check existing reaction
            const existing = await ctx.wo_reactions.findOne({
                where: { user_id: userId, message_id: messageId },
            });

            let action;
            if (existing) {
                if (existing.reaction === reaction) {
                    // same reaction → remove
                    await existing.destroy();
                    action = 'removed';
                } else {
                    // different reaction → replace
                    await existing.update({ reaction });
                    action = 'updated';
                }
            } else {
                await ctx.wo_reactions.create({ user_id: userId, message_id: messageId, reaction });
                action = 'added';
            }

            // notify both participants
            const payload = { message_id: messageId, user_id: userId, reaction, action };
            const otherId = msg.from_id === userId ? msg.to_id : msg.from_id;
            io.to(String(otherId)).emit('message_reaction', payload);
            io.to(String(userId)).emit('message_reaction',  payload);

            res.json({ api_status: 200, action, reaction });
        } catch (err) {
            console.error('[Node/chat/react]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to react to message' });
        }
    };
}

// ─── PIN / UNPIN message ──────────────────────────────────────────────────────
// Stores pins in wm_pinned_messages table (max MAX_PINS per conversation).
// Falls back to legacy wo_mute.pin for backwards compat read on old clients.

const MAX_PINS_FREE = 5;
const MAX_PINS_PRO  = 15;

function pinMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            const chatId    = parseInt(req.body.chat_id);
            const pin       = req.body.pin === 'yes' ? 'yes' : 'no';
            const chatType  = req.body.chat_type || 'user'; // 'user' | 'group'

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            const seq = ctx.sequelize;

            if (pin === 'yes') {
                // Check premium status for limit
                const userRow = await ctx.wo_users.findOne({ attributes: ['is_pro'], where: { user_id: userId }, raw: true });
                const maxPins = userRow?.is_pro ? MAX_PINS_PRO : MAX_PINS_FREE;

                // Check current count for this conversation
                const [[countRow]] = await seq.query(
                    `SELECT COUNT(*) AS cnt FROM wm_pinned_messages WHERE user_id = :uid AND chat_id = :cid AND chat_type = :ct`,
                    { replacements: { uid: userId, cid: chatId, ct: chatType }, type: seq.constructor.QueryTypes.SELECT }
                );
                if (parseInt(countRow.cnt) >= maxPins) {
                    return res.status(400).json({
                        api_status: 400,
                        error_message: `Maximum ${maxPins} pinned messages reached.${userRow?.is_pro ? '' : ' Upgrade to Premium for up to 15 pins.'}`,
                        limit: maxPins,
                    });
                }
                // Upsert: ignore duplicate (already pinned)
                await seq.query(
                    `INSERT IGNORE INTO wm_pinned_messages (user_id, chat_id, chat_type, message_id, pinned_at)
                     VALUES (:uid, :cid, :ct, :mid, :now)`,
                    { replacements: { uid: userId, cid: chatId, ct: chatType, mid: messageId, now: Math.floor(Date.now() / 1000) } }
                );
            } else {
                await seq.query(
                    `DELETE FROM wm_pinned_messages WHERE user_id = :uid AND chat_id = :cid AND chat_type = :ct AND message_id = :mid`,
                    { replacements: { uid: userId, cid: chatId, ct: chatType, mid: messageId } }
                );
            }

            // Notify both participants (socket rooms)
            const otherId = chatType === 'user' ? chatId : null;
            const payload = { message_id: messageId, pin, chat_id: chatId, chat_type: chatType };
            io.to(String(userId)).emit('message_pinned', payload);
            if (otherId) io.to(String(otherId)).emit('message_pinned', { ...payload, chat_id: userId });

            res.json({ api_status: 200, pin });
        } catch (err) {
            console.error('[Node/chat/pin]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to pin message' });
        }
    };
}

// ─── GET PINNED messages ─────────────────────────────────────────────────────

function getPinnedMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const chatId   = parseInt(req.body.chat_id || req.query.chat_id);
            const chatType = req.body.chat_type || req.query.chat_type || 'user';

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            const seq = ctx.sequelize;

            // Get pinned message ids from new table
            const rows = await seq.query(
                `SELECT message_id FROM wm_pinned_messages
                 WHERE user_id = :uid AND chat_id = :cid AND chat_type = :ct
                 ORDER BY pinned_at DESC`,
                { replacements: { uid: userId, cid: chatId, ct: chatType }, type: seq.constructor.QueryTypes.SELECT }
            );

            if (!rows.length) return res.json({ api_status: 200, messages: [] });

            const ids = rows.map(r => r.message_id);
            const msgs = await ctx.wo_messages.findAll({
                where: { id: { [Op.in]: ids } },
                order: [['id', 'DESC']],
                raw: true,
            });

            const messages = msgs.map(m => {
                const { position, type } = resolveType(m, userId);
                return {
                    id:       m.id,
                    from_id:  m.from_id,
                    to_id:    m.to_id,
                    text:     m.text     || '',
                    media:    m.media    || '',
                    stickers: m.stickers || '',
                    time:     m.time,
                    position,
                    type,
                };
            });

            res.json({ api_status: 200, messages });
        } catch (err) {
            console.error('[Node/chat/pinned]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get pinned messages' });
        }
    };
}

function resolveType(msg, userId) {
    const pos = msg.from_id === userId ? 'right' : 'left';
    let type = '';
    if (msg.media)                                         type = 'file';
    if (msg.stickers && msg.stickers.includes('.gif'))    type = 'gif';
    if (msg.type_two === 'contact')                       type = 'contact';
    if (msg.lng && msg.lat && msg.lng !== '0')            type = 'map';
    if (msg.product_id && msg.product_id > 0)             type = 'product';
    return { position: pos, type: pos + '_' + type };
}

// ─── FORWARD message ─────────────────────────────────────────────────────────
// Forward a message to one or multiple recipients

function forwardMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id || req.body.id);
            // supports single recipient_id or array recipient_ids[]
            let recipientIds = [];
            if (req.body.recipient_ids) {
                const raw = req.body.recipient_ids;
                recipientIds = Array.isArray(raw)
                    ? raw.map(Number).filter(Boolean)
                    : String(raw).split(',').map(Number).filter(Boolean);
            } else if (req.body.recipient_id) {
                const r = parseInt(req.body.recipient_id);
                if (r) recipientIds = [r];
            }

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!recipientIds.length)
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id(s) required' });

            const original = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            if (!original)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });

            const now      = Math.floor(Date.now() / 1000);
            const created  = [];

            // Decrypt original text so we can re-encrypt with each new message's timestamp.
            // Without this, Android would try to decrypt with the new timestamp as key and fail.
            const originalPlaintext = original.text
                ? crypto.decryptMessage(original)
                : '';

            for (const toId of recipientIds) {
                // Re-encrypt plaintext with the new message's timestamp (key derivation uses time).
                const enc = originalPlaintext
                    ? crypto.encryptForStorage(originalPlaintext, now)
                    : { text: '', text_ecb: '', text_preview: '', iv: null, tag: null, cipher_version: 1 };

                const newMsg = await ctx.wo_messages.create({
                    from_id:        userId,
                    to_id:          toId,
                    text:           enc.text,
                    text_ecb:       enc.text_ecb,
                    text_preview:   enc.text_preview,
                    iv:             enc.iv,
                    tag:            enc.tag,
                    cipher_version: enc.cipher_version,
                    media:          original.media         || '',
                    mediaFileName:  original.mediaFileName || '',
                    stickers:       original.stickers      || '',
                    lat:            original.lat           || '0',
                    lng:            original.lng           || '0',
                    type_two:       original.type_two      || '',
                    time:           now,
                    seen:           0,
                    forward:        1,
                    page_id:        0,
                });

                // update chat metadata
                const funcs = require('../../functions/functions');
                await funcs.updateOrCreate(ctx.wo_userschat,
                    { user_id: userId, conversation_user_id: toId },
                    { time: now, user_id: userId, conversation_user_id: toId });
                await funcs.updateOrCreate(ctx.wo_userschat,
                    { user_id: toId, conversation_user_id: userId },
                    { time: now, user_id: toId, conversation_user_id: userId });

                const msgPayload = {
                    id:             newMsg.id,
                    from_id:        userId,
                    to_id:          toId,
                    text:           enc.text           || '',
                    iv:             enc.iv             || null,
                    tag:            enc.tag            || null,
                    cipher_version: enc.cipher_version || 1,
                    media:          original.media     || '',
                    stickers:       original.stickers  || '',
                    type_two:       original.type_two  || '',
                    forward:        1,
                    time:           now,
                };

                io.to(String(toId)).emit('new_message',     msgPayload);
                io.to(String(toId)).emit('private_message', msgPayload);
                io.to(String(userId)).emit('new_message',   { ...msgPayload, self: true });

                created.push(newMsg.id);
            }

            res.json({ api_status: 200, forwarded_ids: created });
        } catch (err) {
            console.error('[Node/chat/forward]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to forward message' });
        }
    };
}

// ─── MEDIA AUTO-DELETE SETTING ───────────────────────────────────────────────
// GET  /api/node/chat/media-auto-delete-setting?chat_id=X
// POST /api/node/chat/media-auto-delete-setting  { chat_id, seconds }
//
// Stores/retrieves the per-user per-chat media auto-delete interval.
// seconds: 0=never, 86400=1day, 259200=3days, 604800=1week, 1209600=2weeks, 2592000=1month

const VALID_SECONDS = new Set([0, 86400, 259200, 604800, 1209600, 2592000]);

function getMediaAutoDeleteSetting(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const chatId = parseInt(req.query.chat_id || req.body.chat_id);

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            let seconds = 0;
            try {
                const seq = ctx.sequelize;
                const [rows] = await seq.query(
                    `SELECT media_auto_delete_seconds FROM wm_chat_media_settings
                     WHERE user_id = :uid AND chat_id = :cid LIMIT 1`,
                    { replacements: { uid: userId, cid: chatId }, type: seq.constructor.QueryTypes.SELECT }
                );
                const setting = Array.isArray(rows[0]) ? rows[0][0] : rows[0];
                seconds = setting ? (setting.media_auto_delete_seconds || 0) : 0;
            } catch (dbErr) {
                // Table may not exist yet (migration pending) — return default 0
                console.warn('[Node/chat/media-auto-delete-setting] DB fallback:', dbErr.message);
            }

            res.json({ api_status: 200, seconds, chat_id: chatId });
        } catch (err) {
            console.error('[Node/chat/media-auto-delete-setting GET]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get media auto-delete setting' });
        }
    };
}

function setMediaAutoDeleteSetting(ctx) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const chatId  = parseInt(req.body.chat_id);
            const seconds = parseInt(req.body.seconds);

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });
            if (isNaN(seconds) || !VALID_SECONDS.has(seconds))
                return res.status(400).json({ api_status: 400, error_message: 'Invalid seconds value. Use: 0, 86400, 259200, 604800, 1209600, or 2592000' });

            const seq = ctx.sequelize;
            await seq.query(
                `INSERT INTO wm_chat_media_settings (user_id, chat_id, media_auto_delete_seconds)
                 VALUES (:uid, :cid, :sec)
                 ON DUPLICATE KEY UPDATE media_auto_delete_seconds = :sec`,
                { replacements: { uid: userId, cid: chatId, sec: seconds } }
            );

            res.json({ api_status: 200, seconds, chat_id: chatId });
        } catch (err) {
            console.error('[Node/chat/media-auto-delete-setting POST]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to set media auto-delete setting' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = {
    deleteMessage,
    reactMessage,
    pinMessage,
    getPinnedMessages,
    forwardMessage,
    getMediaAutoDeleteSetting,
    setMediaAutoDeleteSetting,
};
