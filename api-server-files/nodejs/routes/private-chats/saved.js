/**
 * Private Chats — Saved Messages (server-side bookmarks)
 *
 * Endpoints:
 *   POST /api/node/chat/saved/save    – bookmark a message
 *   POST /api/node/chat/saved/unsave  – remove bookmark
 *   POST /api/node/chat/saved/list    – get all saved messages for the user
 *   POST /api/node/chat/saved/clear   – remove all bookmarks for the user
 *
 * Saved messages are stored in wm_saved_messages with full metadata
 * (text, chat name, sender) so they remain readable even if the original
 * message is later deleted.
 */

'use strict';

const { Op } = require('sequelize');

// ─── SAVE a message ───────────────────────────────────────────────────────────

function saveMessage(ctx) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const messageId   = parseInt(req.body.message_id);
            const chatType    = req.body.chat_type || 'chat';
            const chatId      = parseInt(req.body.chat_id)   || 0;
            const chatName    = (req.body.chat_name    || '').slice(0, 255);
            const senderName  = (req.body.sender_name  || '').slice(0, 255);
            const text        = req.body.text        || null;
            const mediaUrl    = req.body.media_url   || null;
            const mediaType   = req.body.media_type  || null;
            const originalTime = parseInt(req.body.original_time) || 0;

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });

            if (!['chat', 'group', 'channel'].includes(chatType))
                return res.status(400).json({ api_status: 400, error_message: 'chat_type must be chat | group | channel' });

            const savedAt = Math.floor(Date.now() / 1000);

            // upsert — ignore if already saved
            const [row, created] = await ctx.wm_saved_messages.findOrCreate({
                where:    { user_id: userId, message_id: messageId, chat_type: chatType },
                defaults: {
                    user_id:       userId,
                    message_id:    messageId,
                    chat_type:     chatType,
                    chat_id:       chatId,
                    chat_name:     chatName,
                    sender_name:   senderName,
                    text:          text,
                    media_url:     mediaUrl,
                    media_type:    mediaType,
                    saved_at:      savedAt,
                    original_time: originalTime,
                },
            });

            return res.json({
                api_status: 200,
                created,
                saved: {
                    id:            row.id,
                    message_id:    row.message_id,
                    chat_type:     row.chat_type,
                    chat_id:       row.chat_id,
                    chat_name:     row.chat_name,
                    sender_name:   row.sender_name,
                    text:          row.text,
                    media_url:     row.media_url,
                    media_type:    row.media_type,
                    saved_at:      row.saved_at,
                    original_time: row.original_time,
                },
            });
        } catch (err) {
            console.error('[Node/chat/saved/save]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to save message' });
        }
    };
}

// ─── UNSAVE a message ─────────────────────────────────────────────────────────

function unsaveMessage(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id);
            const chatType  = req.body.chat_type || 'chat';

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });

            const deleted = await ctx.wm_saved_messages.destroy({
                where: { user_id: userId, message_id: messageId, chat_type: chatType },
            });

            return res.json({ api_status: 200, deleted });
        } catch (err) {
            console.error('[Node/chat/saved/unsave]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to unsave message' });
        }
    };
}

// ─── LIST saved messages ──────────────────────────────────────────────────────

function listSaved(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const limit  = Math.min(parseInt(req.body.limit) || 200, 500);
            const offset = parseInt(req.body.offset) || 0;

            const rows = await ctx.wm_saved_messages.findAll({
                where:  { user_id: userId },
                order:  [['saved_at', 'DESC']],
                limit,
                offset,
                raw:    true,
            });

            return res.json({
                api_status: 200,
                saved:      rows,
                total:      rows.length,
            });
        } catch (err) {
            console.error('[Node/chat/saved/list]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch saved messages' });
        }
    };
}

// ─── CLEAR all saved messages ─────────────────────────────────────────────────

function clearSaved(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;

            const deleted = await ctx.wm_saved_messages.destroy({
                where: { user_id: userId },
            });

            return res.json({ api_status: 200, deleted });
        } catch (err) {
            console.error('[Node/chat/saved/clear]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to clear saved messages' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { saveMessage, unsaveMessage, listSaved, clearSaved };
