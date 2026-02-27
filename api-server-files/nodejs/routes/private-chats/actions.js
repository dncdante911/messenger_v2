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

// ─── PIN / UNPIN message ─────────────────────────────────────────────────────
// Uses wo_mute table as WoWonder stores pins there: pin = 'yes'|'no'

function pinMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            const chatId    = parseInt(req.body.chat_id);   // the other user's id
            const pin       = req.body.pin === 'yes' ? 'yes' : 'no';

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            const existing = await ctx.wo_mute.findOne({
                where: { user_id: userId, message_id: messageId },
            });

            if (existing) {
                await existing.update({ pin });
            } else {
                await ctx.wo_mute.create({
                    user_id:    userId,
                    type:       'user',
                    time:       Math.floor(Date.now() / 1000),
                    pin,
                    message_id: messageId,
                    chat_id:    chatId,
                    notify:     'yes',
                    call_chat:  'yes',
                    archive:    'no',
                    fav:        'no',
                });
            }

            io.to(String(userId)).emit('message_pinned', { message_id: messageId, pin, chat_id: chatId });
            io.to(String(chatId)).emit('message_pinned', { message_id: messageId, pin, chat_id: userId });

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
            const userId  = req.userId;
            const chatId  = parseInt(req.body.chat_id || req.query.chat_id);

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            // Get pinned entry ids for this user+chat
            const pins = await ctx.wo_mute.findAll({
                attributes: ['message_id'],
                where: { user_id: userId, chat_id: chatId, pin: 'yes', type: 'user' },
                raw: true,
            });

            if (!pins.length) return res.json({ api_status: 200, messages: [] });

            const ids = pins.map(p => p.message_id);
            const rows = await ctx.wo_messages.findAll({
                where: { id: { [Op.in]: ids } },
                order: [['id', 'DESC']],
                raw: true,
            });

            const messages = [];
            for (const m of rows) {
                const { position, type } = resolveType(m, userId);
                messages.push({
                    id:       m.id,
                    from_id:  m.from_id,
                    to_id:    m.to_id,
                    text:     m.text     || '',
                    media:    m.media    || '',
                    stickers: m.stickers || '',
                    time:     m.time,
                    position,
                    type,
                });
            }

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

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { deleteMessage, reactMessage, pinMessage, getPinnedMessages, forwardMessage };
