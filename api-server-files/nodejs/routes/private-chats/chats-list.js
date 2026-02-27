/**
 * Private Chats — Chats List & Chat Settings
 * GET_CHATS, ARCHIVE, MUTE, PIN_CHAT, COLOR, DELETE_CONVERSATION, READ_CHATS
 *
 * Endpoints:
 *   POST /api/node/chat/chats               – get conversations list
 *   POST /api/node/chat/delete-conversation – delete/clear conversation
 *   POST /api/node/chat/archive             – archive / unarchive chat
 *   POST /api/node/chat/mute                – mute / unmute notifications
 *   POST /api/node/chat/pin-chat            – pin / unpin conversation
 *   POST /api/node/chat/color               – change chat accent color
 *   POST /api/node/chat/read                – mark all messages in chat as read
 */

'use strict';

const { Op } = require('sequelize');

// ─── helpers ──────────────────────────────────────────────────────────────────

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

async function getMuteSettings(ctx, userId, chatId) {
    try {
        const m = await ctx.wo_mute.findOne({
            where: { user_id: userId, chat_id: chatId, type: 'user' },
            raw: true,
        });
        return m || { notify: 'yes', call_chat: 'yes', archive: 'no', fav: 'no', pin: 'no' };
    } catch {
        return { notify: 'yes', call_chat: 'yes', archive: 'no', fav: 'no', pin: 'no' };
    }
}

// ─── GET CHATS (conversation list) ───────────────────────────────────────────

function getChats(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const limit     = Math.min(parseInt(req.body.limit  || req.body.user_limit)  || 30, 100);
            const offset    = parseInt(req.body.offset || req.body.user_offset) || 0;
            const showArchived = req.body.show_archived === 'true';

            // base: user's direct conversations
            const chatRows = await ctx.wo_userschat.findAll({
                where: {
                    user_id:              userId,
                    conversation_user_id: { [Op.gt]: 0 },
                },
                order: [['time', 'DESC']],
                limit,
                offset,
                raw: true,
            });

            const result = [];
            for (const chat of chatRows) {
                const partnerId = chat.conversation_user_id;

                // archive filter
                const muteRow = await getMuteSettings(ctx, userId, partnerId);
                const isArchived = muteRow.archive === 'yes';
                if (isArchived && !showArchived) continue;
                if (!isArchived && showArchived)  continue;

                const partner = await getUserBasicData(ctx, partnerId);
                if (!partner) continue;

                // last message
                const last = await ctx.wo_messages.findOne({
                    where: {
                        page_id: 0,
                        [Op.or]: [
                            { from_id: userId,     to_id: partnerId, deleted_one: '0' },
                            { from_id: partnerId,  to_id: userId,    deleted_two: '0' },
                        ],
                    },
                    order: [['id', 'DESC']],
                    raw: true,
                });

                // unread count
                const unread = await ctx.wo_messages.count({
                    where: {
                        from_id:     partnerId,
                        to_id:       userId,
                        seen:        0,
                        deleted_two: '0',
                        page_id:     0,
                    },
                });

                // chat color (stored in wo_userschat)
                const chatColor = chat.color || '';

                let lastMsg = null;
                if (last) {
                    const pos  = last.from_id === userId ? 'right' : 'left';
                    lastMsg = {
                        id:        last.id,
                        from_id:   last.from_id,
                        to_id:     last.to_id,
                        text:      last.text     || '',
                        media:     last.media    || '',
                        stickers:  last.stickers || '',
                        time:      last.time,
                        time_text: fmtTime(last.time),
                        seen:      last.seen,
                        position:  pos,
                    };
                }

                result.push({
                    chat_id:       chat.id,
                    chat_time:     chat.time,
                    chat_type:     'user',
                    user_id:       partnerId,
                    chat_color:    chatColor,
                    mute:          muteRow,
                    user_data:     partner,
                    last_message:  lastMsg,
                    message_count: unread,
                });
            }

            res.json({ api_status: 200, data: result });
        } catch (err) {
            console.error('[Node/chat/chats]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch chats' });
        }
    };
}

// ─── DELETE CONVERSATION ─────────────────────────────────────────────────────

function deleteConversation(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.user_id || req.body.recipient_id);
            const deleteType  = req.body.delete_type || 'me'; // 'me' or 'all'

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'user_id is required' });

            if (deleteType === 'all') {
                // hard soft-delete for both sides
                await ctx.wo_messages.update(
                    { deleted_one: '1', deleted_two: '1' },
                    {
                        where: {
                            [Op.or]: [
                                { from_id: userId, to_id: recipientId },
                                { from_id: recipientId, to_id: userId },
                            ],
                        },
                    }
                );
            } else {
                // soft-delete only for caller
                await ctx.wo_messages.update(
                    { deleted_one: '1' },
                    { where: { from_id: userId, to_id: recipientId } }
                );
                await ctx.wo_messages.update(
                    { deleted_two: '1' },
                    { where: { from_id: recipientId, to_id: userId } }
                );
            }

            // remove conversation entry
            await ctx.wo_userschat.destroy({
                where: { user_id: userId, conversation_user_id: recipientId },
            });

            res.json({ api_status: 200, message: 'Conversation deleted' });
        } catch (err) {
            console.error('[Node/chat/delete-conversation]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to delete conversation' });
        }
    };
}

// ─── ARCHIVE / UNARCHIVE ─────────────────────────────────────────────────────

function archiveChat(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const chatId  = parseInt(req.body.chat_id || req.body.user_id);
            const archive = req.body.archive === 'yes' ? 'yes' : 'no';

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            await upsertMute(ctx, userId, chatId, { archive });

            res.json({ api_status: 200, archive });
        } catch (err) {
            console.error('[Node/chat/archive]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to archive chat' });
        }
    };
}

// ─── MUTE notifications ──────────────────────────────────────────────────────

function muteChat(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const chatId  = parseInt(req.body.chat_id || req.body.user_id);
            const notify  = req.body.notify    === 'no'  ? 'no'  : 'yes';
            const calls   = req.body.call_chat === 'no'  ? 'no'  : 'yes';

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            await upsertMute(ctx, userId, chatId, { notify, call_chat: calls });

            res.json({ api_status: 200, notify, call_chat: calls });
        } catch (err) {
            console.error('[Node/chat/mute]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to mute chat' });
        }
    };
}

// ─── PIN / UNPIN chat ─────────────────────────────────────────────────────────

function pinChat(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const chatId = parseInt(req.body.chat_id || req.body.user_id);
            const pin    = req.body.pin === 'yes' ? 'yes' : 'no';

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            await upsertMute(ctx, userId, chatId, { pin });

            res.json({ api_status: 200, pin });
        } catch (err) {
            console.error('[Node/chat/pin-chat]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to pin chat' });
        }
    };
}

// ─── CHANGE chat color ────────────────────────────────────────────────────────
// Stores in wo_userschat.color

function changeChatColor(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.user_id || req.body.chat_id);
            const color       = (req.body.color || '').replace(/[^a-fA-F0-9]/g, '').slice(0, 7);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'user_id is required' });
            if (!color)
                return res.status(400).json({ api_status: 400, error_message: 'color is required' });

            // update both sides of conversation
            await ctx.wo_userschat.update(
                { color },
                { where: { user_id: userId, conversation_user_id: recipientId } }
            );
            await ctx.wo_userschat.update(
                { color },
                { where: { user_id: recipientId, conversation_user_id: userId } }
            );

            // notify both participants
            io.to(String(recipientId)).emit('chat_color_changed', { user_id: userId, color });
            io.to(String(userId)).emit('chat_color_changed',      { user_id: recipientId, color });

            res.json({ api_status: 200, color });
        } catch (err) {
            console.error('[Node/chat/color]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to change chat color' });
        }
    };
}

// ─── READ ALL (mark chat as fully read) ──────────────────────────────────────

function readChats(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id || req.body.user_id);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            const seen = Math.floor(Date.now() / 1000);
            await ctx.wo_messages.update(
                { seen },
                { where: { from_id: recipientId, to_id: userId, seen: 0 } }
            );

            io.to(String(recipientId)).emit('lastseen', { can_seen: 1, seen, user_id: userId });

            res.json({ api_status: 200, message: 'Chat marked as read' });
        } catch (err) {
            console.error('[Node/chat/read]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to mark chat as read' });
        }
    };
}

// ─── internal: upsert mute row ───────────────────────────────────────────────

async function upsertMute(ctx, userId, chatId, fields) {
    const existing = await ctx.wo_mute.findOne({
        where: { user_id: userId, chat_id: chatId, type: 'user' },
    });
    if (existing) {
        await existing.update(fields);
    } else {
        await ctx.wo_mute.create({
            user_id:   userId,
            chat_id:   chatId,
            type:      'user',
            time:      Math.floor(Date.now() / 1000),
            notify:    'yes',
            call_chat: 'yes',
            archive:   'no',
            fav:       'no',
            pin:       'no',
            ...fields,
        });
    }
}

// ─── CLEAR CHAT HISTORY ───────────────────────────────────────────────────────
// Soft-deletes all messages between caller and recipient on caller's side only.
// The conversation entry remains in the list (unlike deleteConversation).

function clearHistory(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id || req.body.user_id);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            // Soft-delete: messages sent by caller → deleted_one='1'
            await ctx.wo_messages.update(
                { deleted_one: '1' },
                { where: { from_id: userId, to_id: recipientId, page_id: 0 } }
            );
            // Soft-delete: messages received by caller → deleted_two='1'
            await ctx.wo_messages.update(
                { deleted_two: '1' },
                { where: { from_id: recipientId, to_id: userId, page_id: 0 } }
            );

            res.json({ api_status: 200, message: 'Chat history cleared' });
        } catch (err) {
            console.error('[Node/chat/clear-history]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to clear history' });
        }
    };
}

// ─── GET MUTE STATUS for a single chat ───────────────────────────────────────

function getMuteStatus(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const chatId  = parseInt(req.body.chat_id || req.body.recipient_id);

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            const m = await getMuteSettings(ctx, userId, chatId);
            res.json({
                api_status: 200,
                notify:    m.notify    ?? 'yes',
                call_chat: m.call_chat ?? 'yes',
                archive:   m.archive   ?? 'no',
                pin:       m.pin       ?? 'no',
            });
        } catch (err) {
            console.error('[Node/chat/mute-status]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get mute status' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = {
    getChats,
    deleteConversation,
    clearHistory,
    getMuteStatus,
    archiveChat,
    muteChat,
    pinChat,
    changeChatColor,
    readChats,
};
