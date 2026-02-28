/**
 * Group Chats — Messages
 * GET, SEND, LOADMORE, EDIT, DELETE, PIN, UNPIN, SEARCH, SEEN, TYPING
 *
 * Endpoints:
 *   POST /api/node/group/messages/get        – fetch message history
 *   POST /api/node/group/messages/send       – send text message
 *   POST /api/node/group/messages/loadmore   – paginated load more (older messages)
 *   POST /api/node/group/messages/edit       – edit own message
 *   POST /api/node/group/messages/delete     – delete message (own or admin)
 *   POST /api/node/group/messages/pin        – pin message (admin/owner only)
 *   POST /api/node/group/messages/unpin      – unpin message (admin/owner only)
 *   POST /api/node/group/messages/search     – search messages by text_preview
 *   POST /api/node/group/messages/seen       – mark messages as read
 *   POST /api/node/group/messages/typing     – typing indicator
 *
 * Encryption: hybrid system (same as private-chats/messages.js)
 *   ► text  — AES-256-GCM (WorldMates Android)
 *   ► text_ecb — AES-128-ECB (WoWonder browser)
 *   ► text_preview — plaintext[:100] for search
 *   ► Android receives encrypted data + iv + tag + cipher_version
 */

'use strict';

const { Op } = require('sequelize');
const funcs  = require('../../functions/functions');
const crypto = require('../../helpers/crypto');

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
    if (msg.media)                                                  type = 'file';
    if (msg.type_two === 'audio' || msg.type_two === 'voice')       type = msg.type_two;
    if (msg.type_two === 'video')                                   type = 'video';
    if (msg.stickers && msg.stickers.includes('.gif'))              type = 'gif';
    if (msg.type_two === 'contact')                                 type = 'contact';
    if (msg.lng && msg.lat && msg.lng !== '0' && msg.lat !== '0')  type = 'map';
    if (msg.product_id && msg.product_id > 0)                      type = 'product';
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
        u.avatar = funcs.Wo_GetMedia ? funcs.Wo_GetMedia(ctx, u.avatar) : u.avatar;
        return u;
    } catch { return null; }
}

/**
 * Check whether userId is a member of groupId.
 * Returns the membership row (raw) or null.
 */
async function getGroupMembership(ctx, groupId, userId) {
    return ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1' },
        raw: true,
    });
}

/**
 * Returns true if userId is owner, admin, or moderator of groupId.
 */
async function isGroupAdmin(ctx, groupId, userId) {
    const m = await ctx.wo_groupchatusers.findOne({
        attributes: ['role'],
        where: {
            group_id: groupId,
            user_id:  userId,
            active:   '1',
            role:     { [Op.in]: ['owner', 'admin', 'moderator'] },
        },
        raw: true,
    });
    return !!m;
}

/**
 * Parse settings JSON field safely from a group row.
 */
function parseSettings(group) {
    if (!group || !group.settings) return {};
    try {
        return typeof group.settings === 'string'
            ? JSON.parse(group.settings)
            : group.settings;
    } catch { return {}; }
}

/**
 * Build a message object for API response.
 * text field is returned encrypted (GCM) — Android decrypts locally.
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
        group_id:       msg.group_id,
        to_id:          msg.to_id || 0,
        // Encrypted text (GCM) — Android decrypts locally
        text:           msg.text           || '',
        iv:             msg.iv             || null,
        tag:            msg.tag            || null,
        cipher_version: msg.cipher_version != null ? Number(msg.cipher_version) : 1,
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
        user_data:      sender,
        messageUser:    sender,
    };
}

// ─── GET messages ─────────────────────────────────────────────────────────────

function getMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            const limit           = Math.min(parseInt(req.body.limit) || 30, 100);
            const afterMessageId  = parseInt(req.body.after_message_id)  || 0;
            const beforeMessageId = parseInt(req.body.before_message_id) || 0;
            const messageId       = parseInt(req.body.message_id)        || 0;

            // Per-user clear-history timestamp: skip messages sent before the user cleared
            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            const clearTs  = settings.clear_history?.[String(userId)] || 0;

            const where = { group_id: groupId, page_id: 0 };
            if (clearTs > 0) where.time = { [Op.gt]: clearTs };

            if (messageId > 0)            where.id = messageId;
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

            // Fetch pinned message if set in group settings
            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            let pinnedMessage = null;
            if (settings.pinned_message_id) {
                const pm = await ctx.wo_messages.findOne({
                    where: { id: settings.pinned_message_id },
                    raw: true,
                });
                if (pm) pinnedMessage = await buildMessage(ctx, pm, userId);
            }

            res.json({ api_status: 200, messages, pinned_message: pinnedMessage });
        } catch (err) {
            console.error('[Node/group/messages/get]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch messages' });
        }
    };
}

// ─── SEND message ─────────────────────────────────────────────────────────────

function sendMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const plaintext = (req.body.text || '').trim();
            const replyId   = parseInt(req.body.reply_id) || 0;
            const stickers  = req.body.stickers || '';
            const lat       = req.body.lat      || '0';
            const lng       = req.body.lng      || '0';
            const contact   = req.body.contact  || '';

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            const hasContent = plaintext || stickers || (lat !== '0' && lng !== '0') || contact;
            if (!hasContent)
                return res.status(400).json({ api_status: 400, error_message: 'Message has no content' });

            const now = Math.floor(Date.now() / 1000);

            // Encrypt text (AES-256-GCM + AES-128-ECB for WoWonder compatibility)
            const enc = plaintext
                ? crypto.encryptForStorage(plaintext, now)
                : { text: '', text_ecb: '', text_preview: '', iv: null, tag: null, cipher_version: 1 };

            const row = await ctx.wo_messages.create({
                from_id:        userId,
                to_id:          0,
                group_id:       groupId,
                page_id:        0,
                text:           enc.text,
                text_ecb:       enc.text_ecb,
                text_preview:   enc.text_preview,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                stickers,
                media:          '',
                mediaFileName:  '',
                time:           now,
                seen:           now,   // group messages marked seen immediately on send
                reply_id:       replyId,
                story_id:       0,
                lat,
                lng,
                type_two:       contact ? 'contact' : '',
                forward:        0,
                edited:         0,
            });

            // Update group's last activity timestamp
            await ctx.wo_groupchat.update({ time: String(now) }, { where: { group_id: groupId } });

            const msgData = await buildMessage(ctx, row.toJSON ? row.toJSON() : row, userId);

            // Broadcast to group room — all members who are subscribed to this room receive it
            io.to('group_' + groupId).emit('group_message', msgData);

            // Also emit to sender's personal room so they see it reflected
            const senderSockets = ctx.userIdSocket ? ctx.userIdSocket[userId] : null;
            if (senderSockets && Array.isArray(senderSockets)) {
                senderSockets.forEach(sid => {
                    const sock = io.sockets.sockets.get(sid);
                    if (sock) sock.emit('group_message', { ...msgData, self: true });
                });
            }

            console.log(`[Node/group/messages/send] user=${userId} group=${groupId} msg=${row.id}`);
            res.json({ api_status: 200, message_data: msgData });
        } catch (err) {
            console.error('[Node/group/messages/send]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send message' });
        }
    };
}

// ─── LOADMORE (older messages) ────────────────────────────────────────────────

function loadMore(ctx, io) {
    return async (req, res) => {
        try {
            const userId          = req.userId;
            const groupId         = parseInt(req.body.group_id);
            const beforeMessageId = parseInt(req.body.before_message_id) || 0;
            const limit           = Math.min(parseInt(req.body.limit) || 15, 50);

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            // Respect per-user clear timestamp
            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            const clearTs  = settings.clear_history?.[String(userId)] || 0;

            const where = { group_id: groupId, page_id: 0 };
            if (clearTs > 0) where.time = { [Op.gt]: clearTs };
            if (beforeMessageId > 0) {
                where.id = { [Op.lt]: beforeMessageId };
            }

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
            console.error('[Node/group/messages/loadmore]', err.message);
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
            if (!msg || !msg.group_id)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });
            if (msg.from_id !== userId)
                return res.status(403).json({ api_status: 403, error_message: 'Cannot edit someone else\'s message' });

            const membership = await getGroupMembership(ctx, msg.group_id, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            // Re-encrypt with the ORIGINAL timestamp (key doesn't change)
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

            const editPayload = {
                message_id:     messageId,
                group_id:       msg.group_id,
                text:           enc.text,
                iv:             enc.iv,
                tag:            enc.tag,
                cipher_version: enc.cipher_version,
                time:           msg.time,
                edited:         1,
            };

            // Notify all group members
            io.to('group_' + msg.group_id).emit('group_message_edited', editPayload);

            res.json({ api_status: 200, ...editPayload });
        } catch (err) {
            console.error('[Node/group/messages/edit]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to edit message' });
        }
    };
}

// ─── DELETE message ───────────────────────────────────────────────────────────

function deleteMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });

            const msg = await ctx.wo_messages.findOne({ where: { id: messageId }, raw: true });
            if (!msg || !msg.group_id)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found' });

            const isOwner = msg.from_id === userId;
            const admin   = await isGroupAdmin(ctx, msg.group_id, userId);

            if (!isOwner && !admin)
                return res.status(403).json({ api_status: 403, error_message: 'Not allowed to delete this message' });

            // If this message is pinned, unpin it first
            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: msg.group_id }, raw: true });
            const settings = parseSettings(group);
            if (settings.pinned_message_id === messageId) {
                delete settings.pinned_message_id;
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: msg.group_id } }
                );
                io.to('group_' + msg.group_id).emit('group_message_unpinned', { group_id: msg.group_id });
            }

            await ctx.wo_messages.destroy({ where: { id: messageId } });

            io.to('group_' + msg.group_id).emit('group_message_deleted', {
                message_id: messageId,
                group_id:   msg.group_id,
            });

            res.json({ api_status: 200, message: 'Message deleted' });
        } catch (err) {
            console.error('[Node/group/messages/delete]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to delete message' });
        }
    };
}

// ─── PIN message ──────────────────────────────────────────────────────────────

function pinMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            const groupId   = parseInt(req.body.group_id);

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const admin = await isGroupAdmin(ctx, groupId, userId);
            if (!admin)
                return res.status(403).json({ api_status: 403, error_message: 'Only admins can pin messages' });

            const msg = await ctx.wo_messages.findOne({ where: { id: messageId, group_id: groupId }, raw: true });
            if (!msg)
                return res.status(404).json({ api_status: 404, error_message: 'Message not found in this group' });

            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            settings.pinned_message_id = messageId;

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            const pinnedData = await buildMessage(ctx, msg, userId);

            io.to('group_' + groupId).emit('group_message_pinned', {
                group_id:       groupId,
                message_id:     messageId,
                pinned_message: pinnedData,
            });

            res.json({ api_status: 200, pinned_message: pinnedData });
        } catch (err) {
            console.error('[Node/group/messages/pin]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to pin message' });
        }
    };
}

// ─── UNPIN message ────────────────────────────────────────────────────────────

function unpinMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const admin = await isGroupAdmin(ctx, groupId, userId);
            if (!admin)
                return res.status(403).json({ api_status: 403, error_message: 'Only admins can unpin messages' });

            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            delete settings.pinned_message_id;

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group_' + groupId).emit('group_message_unpinned', { group_id: groupId });

            res.json({ api_status: 200, message: 'Message unpinned' });
        } catch (err) {
            console.error('[Node/group/messages/unpin]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to unpin message' });
        }
    };
}

// ─── SEARCH messages ──────────────────────────────────────────────────────────
// Search by text_preview (plaintext[:100]), not encrypted text.

function searchMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const query   = (req.body.query || '').trim();
            const limit   = Math.min(parseInt(req.body.limit) || 50, 100);
            const offset  = parseInt(req.body.offset) || 0;

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });
            if (query.length < 2)
                return res.status(400).json({ api_status: 400, error_message: 'Query must be at least 2 characters' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            const rows = await ctx.wo_messages.findAll({
                where: {
                    group_id:     groupId,
                    page_id:      0,
                    text_preview: { [Op.like]: `%${query}%` },
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
            console.error('[Node/group/messages/search]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to search messages' });
        }
    };
}

// ─── SEEN messages ────────────────────────────────────────────────────────────

function seenMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            const seen = Math.floor(Date.now() / 1000);

            // Update last_seen for this member in the group
            await ctx.wo_groupchatusers.update(
                { last_seen: seen },
                { where: { group_id: groupId, user_id: userId } }
            );

            // Notify other group members that this user has seen messages
            io.to('group_' + groupId).emit('group_seen', {
                group_id: groupId,
                user_id:  userId,
                seen,
            });

            res.json({ api_status: 200, message: 'Messages marked as seen' });
        } catch (err) {
            console.error('[Node/group/messages/seen]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to mark messages as seen' });
        }
    };
}

// ─── TYPING ───────────────────────────────────────────────────────────────────

function typing(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const isTyping = req.body.typing === 'true' || req.body.typing === true;

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            io.to('group_' + groupId).emit(isTyping ? 'group_typing' : 'group_typing_done', {
                group_id: groupId,
                user_id:  userId,
            });

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Node/group/messages/typing]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send typing status' });
        }
    };
}

// ─── CLEAR HISTORY (for self) ─────────────────────────────────────────────────
// Stores a per-user "clear timestamp" in wo_groupchatusers.
// On next getMessages / loadMore calls, messages older than that timestamp
// are excluded from results for this user.

function clearHistorySelf(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            const clearTs = Math.floor(Date.now() / 1000);

            // Store the clear timestamp in wo_groupchatusers as JSON in an extra field.
            // We use the "data" column if it exists, otherwise fall back to a separate key
            // stored in group settings keyed by userId.
            try {
                await ctx.wo_groupchatusers.update(
                    { clear_history_ts: clearTs },
                    { where: { group_id: groupId, user_id: userId } }
                );
            } catch (_) {
                // Column may not exist yet — store in group settings JSON as fallback
                const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
                const settings = parseSettings(group);
                if (!settings.clear_history) settings.clear_history = {};
                settings.clear_history[String(userId)] = clearTs;
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: groupId } }
                );
            }

            return res.json({ api_status: 200, message: 'History cleared for you', clear_ts: clearTs });
        } catch (err) {
            console.error('[Node/group/messages/clear-self]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── CLEAR HISTORY (for all — admin only) ─────────────────────────────────────
// Permanently deletes ALL messages in the group. Emits group_history_cleared
// via Socket.IO so all online members clear their local list immediately.

function clearHistoryAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const membership = await getGroupMembership(ctx, groupId, userId);
            if (!membership)
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can clear history for all' });

            // Delete all messages of this group
            const deleted = await ctx.wo_messages.destroy({
                where: { group_id: groupId }
            });

            // Unpin message if any
            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const settings = parseSettings(group);
            if (settings.pinned_message_id) {
                delete settings.pinned_message_id;
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: groupId } }
                );
            }

            // Notify all members in real-time
            io.to('group_' + groupId).emit('group_history_cleared', { group_id: groupId });

            return res.json({ api_status: 200, message: 'All messages deleted', deleted_count: deleted });
        } catch (err) {
            console.error('[Node/group/messages/clear-all]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GROUP USER ACTION ────────────────────────────────────────────────────────
// Relays an activity status to all group members (listening, viewing, etc.)

function groupUserAction(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const action  = req.body.action || '';

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });
            if (!action)
                return res.status(400).json({ api_status: 400, error_message: 'action is required' });

            io.to('group_' + groupId).emit('group_user_action', {
                group_id: groupId,
                user_id:  userId,
                action:   action,
            });

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Node/group/messages/user-action]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to send group user action' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = {
    getMessages,
    sendMessage,
    loadMore,
    editMessage,
    deleteMessage,
    pinMessage,
    unpinMessage,
    searchMessages,
    seenMessages,
    typing,
    groupUserAction,
    clearHistorySelf,
    clearHistoryAdmin,
};
