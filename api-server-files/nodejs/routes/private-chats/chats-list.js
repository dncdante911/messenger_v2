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
// Optimised: 5 DB queries total regardless of chat count (was 4N+1).
//   1. chatRows        — paginated conversation list
//   2. muteRows        — batch mute settings for all partners
//   3. userRows        — batch user data for archive-filtered partners
//   4. lastMsgRows     — last message per conversation (raw SQL MAX(id))
//   5. unreadRows      — unread count per partner (raw SQL GROUP BY)

function getChats(ctx, io) {
    return async (req, res) => {
        try {
            const userId       = req.userId;
            const limit        = Math.min(parseInt(req.body.limit  || req.body.user_limit)  || 30, 100);
            const offset       = parseInt(req.body.offset || req.body.user_offset) || 0;
            const showArchived = req.body.show_archived === 'true';
            const siteUrl      = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');

            // 1 ── paginated conversation rows
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

            if (chatRows.length === 0) {
                return res.json({ api_status: 200, data: [] });
            }

            const allPartnerIds = chatRows.map(c => c.conversation_user_id);

            // 2 ── batch mute / archive settings
            const muteRows = await ctx.wo_mute.findAll({
                where: { user_id: userId, chat_id: { [Op.in]: allPartnerIds }, type: 'user' },
                raw: true,
            });
            const defaultMute = { notify: 'yes', call_chat: 'yes', archive: 'no', fav: 'no', pin: 'no' };
            const muteMap = new Map();
            for (const m of muteRows) muteMap.set(m.chat_id, m);

            // apply archive filter in memory
            const filteredChats = chatRows.filter(chat => {
                const archived = (muteMap.get(chat.conversation_user_id) || defaultMute).archive === 'yes';
                return showArchived ? archived : !archived;
            });

            if (filteredChats.length === 0) {
                return res.json({ api_status: 200, data: [] });
            }

            const partnerIds = filteredChats.map(c => c.conversation_user_id);
            const ph         = partnerIds.map(() => '?').join(',');

            // 3 ── batch user data
            const userRows = await ctx.wo_users.findAll({
                attributes: ['user_id', 'username', 'first_name', 'last_name',
                             'avatar', 'lastseen', 'status', 'last_avatar_mod'],
                where: { user_id: { [Op.in]: partnerIds } },
                raw: true,
            });
            const userMap = new Map();
            for (const u of userRows) {
                u.name = (u.first_name && u.last_name)
                    ? u.first_name + ' ' + u.last_name
                    : u.username;
                userMap.set(u.user_id, u);
            }

            // 4 ── last message per conversation partner (MAX id per partner)
            //      Union both directions so we get the true last message regardless
            //      of who sent it, then join to fetch full row.
            const lastMsgRows = await ctx.sequelize.query(
                `SELECT m.* FROM Wo_Messages m
                 INNER JOIN (
                   SELECT partner_id, MAX(id) AS max_id FROM (
                     SELECT to_id   AS partner_id, id
                     FROM Wo_Messages
                     WHERE from_id = ? AND to_id IN (${ph}) AND page_id = 0
                       AND is_business_chat = 0 AND deleted_one = '0'
                     UNION ALL
                     SELECT from_id AS partner_id, id
                     FROM Wo_Messages
                     WHERE to_id = ? AND from_id IN (${ph}) AND page_id = 0
                       AND is_business_chat = 0 AND deleted_two = '0'
                   ) combined
                   GROUP BY partner_id
                 ) latest ON m.id = latest.max_id`,
                {
                    replacements: [userId, ...partnerIds, userId, ...partnerIds],
                    type: ctx.sequelize.QueryTypes.SELECT,
                }
            );
            const lastMsgMap = new Map();
            for (const msg of lastMsgRows) {
                const pid = msg.from_id === userId ? msg.to_id : msg.from_id;
                lastMsgMap.set(pid, msg);
            }

            // 5 ── unread counts per partner (messages sent to me, unseen, personal only)
            const unreadRows = await ctx.sequelize.query(
                `SELECT from_id, COUNT(*) AS cnt
                 FROM Wo_Messages
                 WHERE to_id = ? AND from_id IN (${ph})
                   AND seen = 0 AND deleted_two = '0' AND page_id = 0
                   AND is_business_chat = 0
                 GROUP BY from_id`,
                {
                    replacements: [userId, ...partnerIds],
                    type: ctx.sequelize.QueryTypes.SELECT,
                }
            );
            const unreadMap = new Map();
            for (const r of unreadRows) unreadMap.set(r.from_id, Number(r.cnt));

            // 6 ── assemble response
            const result = [];
            for (const chat of filteredChats) {
                const partnerId = chat.conversation_user_id;
                const partner   = userMap.get(partnerId);
                if (!partner) continue;

                const muteRow = muteMap.get(partnerId) || defaultMute;
                const last    = lastMsgMap.get(partnerId) || null;
                const unread  = unreadMap.get(partnerId)  || 0;

                let lastMsg = null;
                if (last) {
                    const pos = last.from_id === userId ? 'right' : 'left';
                    lastMsg = {
                        id:             last.id,
                        from_id:        last.from_id,
                        to_id:          last.to_id,
                        text:           last.text           || '',
                        iv:             last.iv             || null,
                        tag:            last.tag            || null,
                        cipher_version: last.cipher_version ?? null,
                        signal_header:  last.signal_header  || null,
                        type_two:       last.type_two       || '',
                        media:          last.media          || '',
                        stickers:       last.stickers       || '',
                        time:           last.time,
                        time_text:      fmtTime(last.time),
                        seen:           last.seen,
                        position:       pos,
                    };
                }

                let avatarUrl = partner.avatar || '';
                if (avatarUrl && !avatarUrl.startsWith('http')) {
                    avatarUrl = siteUrl + '/' + avatarUrl;
                }
                if (partner.last_avatar_mod) {
                    avatarUrl += '?cache=' + partner.last_avatar_mod;
                }

                result.push({
                    id:            chat.id,
                    chat_id:       chat.id,
                    chat_time:     chat.time,
                    chat_type:     'user',
                    user_id:       partnerId,
                    username:      partner.username || partner.name || '',
                    avatar:        avatarUrl,
                    chat_color:    chat.color || '',
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
            // "just_me" (default) — hide from caller only; "everyone" — wipe for both parties
            const clearType   = req.body.clear_type || 'just_me';

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            if (clearType === 'everyone') {
                // Wipe every message in the conversation for both sides
                await ctx.wo_messages.update(
                    { deleted_one: '1', deleted_two: '1' },
                    { where: { from_id: userId,      to_id: recipientId, page_id: 0 } }
                );
                await ctx.wo_messages.update(
                    { deleted_one: '1', deleted_two: '1' },
                    { where: { from_id: recipientId, to_id: userId,      page_id: 0 } }
                );
                // Notify both parties so their UI updates in real-time
                const payload = { from_id: userId, recipient_id: recipientId };
                io.to(String(recipientId)).emit('private_history_cleared', payload);
                io.to(String(userId)).emit('private_history_cleared', payload);
            } else {
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
            }

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

// ─── ARCHIVED CHATS COUNT (for badge in UI) ───────────────────────────────────

function archivedCount(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const archived = await ctx.wo_mute.count({
                where: { user_id: userId, type: 'user', archive: 'yes' },
            });
            res.json({ api_status: 200, count: archived });
        } catch (err) {
            console.error('[Node/chat/archive-count]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get archive count' });
        }
    };
}

// ─── GET BUSINESS CHATS ───────────────────────────────────────────────────────
// Returns business-chat conversations from wm_business_chats.
// Uses same 5-query optimised pattern as getChats().

function getBusinessChats(ctx, io) {
    return async (req, res) => {
        try {
            if (!ctx.wm_business_chats) {
                return res.json({ api_status: 200, data: [] });
            }

            const userId  = req.userId;
            const limit   = Math.min(parseInt(req.body.limit  || req.body.user_limit)  || 30, 100);
            const offset  = parseInt(req.body.offset || req.body.user_offset) || 0;
            const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');

            // 1 ── paginated business-chat rows (where this user is the customer)
            const chatRows = await ctx.wm_business_chats.findAll({
                where: { user_id: userId },
                order: [['last_time', 'DESC']],
                limit,
                offset,
                raw: true,
            });

            if (chatRows.length === 0) {
                return res.json({ api_status: 200, data: [] });
            }

            const partnerIds = chatRows.map(c => c.business_user_id);
            const ph         = partnerIds.map(() => '?').join(',');

            // 2 ── batch user data for business owners
            const userRows = await ctx.wo_users.findAll({
                attributes: ['user_id', 'username', 'first_name', 'last_name',
                             'avatar', 'lastseen', 'status', 'last_avatar_mod'],
                where: { user_id: { [Op.in]: partnerIds } },
                raw: true,
            });
            const userMap = new Map();
            for (const u of userRows) {
                u.name = (u.first_name && u.last_name)
                    ? u.first_name + ' ' + u.last_name
                    : u.username;
                userMap.set(u.user_id, u);
            }

            // 3 ── business profile names/avatars (override user data if present)
            let bizProfileMap = new Map();
            if (ctx.wm_business_profile) {
                const bizRows = await ctx.wm_business_profile.findAll({
                    attributes: ['user_id', 'business_name', 'avatar'],
                    where: { user_id: { [Op.in]: partnerIds } },
                    raw: true,
                });
                for (const b of bizRows) bizProfileMap.set(b.user_id, b);
            }

            // 4 ── last message per business conversation (is_business_chat=1)
            const lastMsgRows = await ctx.sequelize.query(
                `SELECT m.* FROM Wo_Messages m
                 INNER JOIN (
                   SELECT partner_id, MAX(id) AS max_id FROM (
                     SELECT to_id   AS partner_id, id
                     FROM Wo_Messages
                     WHERE from_id = ? AND to_id IN (${ph}) AND page_id = 0
                       AND is_business_chat = 1 AND deleted_one = '0'
                     UNION ALL
                     SELECT from_id AS partner_id, id
                     FROM Wo_Messages
                     WHERE to_id = ? AND from_id IN (${ph}) AND page_id = 0
                       AND is_business_chat = 1 AND deleted_two = '0'
                   ) combined
                   GROUP BY partner_id
                 ) latest ON m.id = latest.max_id`,
                {
                    replacements: [userId, ...partnerIds, userId, ...partnerIds],
                    type: ctx.sequelize.QueryTypes.SELECT,
                }
            );
            const lastMsgMap = new Map();
            for (const msg of lastMsgRows) {
                const pid = msg.from_id === userId ? msg.to_id : msg.from_id;
                lastMsgMap.set(pid, msg);
            }

            // 5 ── unread counts (business messages sent to me, unseen)
            const unreadRows = await ctx.sequelize.query(
                `SELECT from_id, COUNT(*) AS cnt
                 FROM Wo_Messages
                 WHERE to_id = ? AND from_id IN (${ph})
                   AND is_business_chat = 1 AND seen = 0 AND deleted_two = '0' AND page_id = 0
                 GROUP BY from_id`,
                {
                    replacements: [userId, ...partnerIds],
                    type: ctx.sequelize.QueryTypes.SELECT,
                }
            );
            const unreadMap = new Map();
            for (const r of unreadRows) unreadMap.set(r.from_id, Number(r.cnt));

            // 6 ── assemble response
            const result = [];
            for (const chat of chatRows) {
                const partnerId  = chat.business_user_id;
                const partner    = userMap.get(partnerId);
                if (!partner) continue;

                const bizProfile = bizProfileMap.get(partnerId);
                const last       = lastMsgMap.get(partnerId) || null;
                const unread     = unreadMap.get(partnerId)  || 0;

                let lastMsg = null;
                if (last) {
                    const pos = last.from_id === userId ? 'right' : 'left';
                    lastMsg = {
                        id:             last.id,
                        from_id:        last.from_id,
                        to_id:          last.to_id,
                        text:           last.text           || '',
                        iv:             last.iv             || null,
                        tag:            last.tag            || null,
                        cipher_version: last.cipher_version ?? null,
                        signal_header:  last.signal_header  || null,
                        type_two:       last.type_two       || '',
                        media:          last.media          || '',
                        stickers:       last.stickers       || '',
                        time:           last.time,
                        time_text:      fmtTime(last.time),
                        seen:           last.seen,
                        position:       pos,
                    };
                }

                let avatarUrl = (bizProfile?.avatar || partner.avatar || '');
                if (avatarUrl && !avatarUrl.startsWith('http')) {
                    avatarUrl = siteUrl + '/' + avatarUrl;
                }
                if (!bizProfile?.avatar && partner.last_avatar_mod) {
                    avatarUrl += '?cache=' + partner.last_avatar_mod;
                }

                result.push({
                    id:               chat.id,
                    chat_id:          chat.id,
                    chat_time:        chat.last_time,
                    chat_type:        'business',
                    user_id:          partnerId,
                    username:         bizProfile?.business_name || partner.name || partner.username || '',
                    avatar:           avatarUrl,
                    chat_color:       '',
                    mute:             { notify: 'yes', call_chat: 'yes', archive: 'no', fav: 'no', pin: 'no' },
                    user_data:        partner,
                    business_profile: bizProfile || null,
                    last_message:     lastMsg,
                    message_count:    unread,
                });
            }

            res.json({ api_status: 200, data: result });
        } catch (err) {
            console.error('[Node/chat/business-chats]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch business chats' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = {
    getChats,
    getBusinessChats,
    deleteConversation,
    clearHistory,
    getMuteStatus,
    archiveChat,
    archivedCount,
    muteChat,
    pinChat,
    changeChatColor,
    readChats,
};
