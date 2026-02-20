/**
 * Private Chats — Favorites
 * FAV, FAV_LIST
 *
 * Endpoints:
 *   POST /api/node/chat/fav          – mark / unmark message as favorite
 *   POST /api/node/chat/fav-list     – get favorite messages for a chat
 *
 * Favorites are stored in wo_mute with fav = 'yes' / 'no'
 * (same pattern as WoWonder's pin/archive, per the existing PHP endpoints)
 */

'use strict';

const { Op } = require('sequelize');

// ─── FAV / UNFAV message ─────────────────────────────────────────────────────

function favMessage(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const messageId = parseInt(req.body.message_id || req.body.id);
            const chatId    = parseInt(req.body.chat_id);
            const fav       = req.body.fav === 'yes' ? 'yes' : 'no';

            if (!messageId || isNaN(messageId))
                return res.status(400).json({ api_status: 400, error_message: 'message_id is required' });
            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            const existing = await ctx.wo_mute.findOne({
                where: { user_id: userId, message_id: messageId, type: 'user' },
            });

            if (existing) {
                await existing.update({ fav });
            } else {
                await ctx.wo_mute.create({
                    user_id:    userId,
                    chat_id:    chatId,
                    type:       'user',
                    time:       Math.floor(Date.now() / 1000),
                    fav,
                    message_id: messageId,
                    notify:     'yes',
                    call_chat:  'yes',
                    archive:    'no',
                    pin:        'no',
                });
            }

            res.json({ api_status: 200, fav });
        } catch (err) {
            console.error('[Node/chat/fav]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to update favorite' });
        }
    };
}

// ─── GET FAV messages list ────────────────────────────────────────────────────

function getFavMessages(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const chatId = parseInt(req.body.chat_id || req.query.chat_id);
            const limit  = Math.min(parseInt(req.body.limit) || 50, 100);
            const offset = parseInt(req.body.offset) || 0;

            if (!chatId || isNaN(chatId))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id is required' });

            // get message ids marked as fav
            const favRows = await ctx.wo_mute.findAll({
                attributes: ['message_id'],
                where:      { user_id: userId, chat_id: chatId, fav: 'yes', type: 'user' },
                order:      [['time', 'DESC']],
                limit,
                offset,
                raw:        true,
            });

            if (!favRows.length) return res.json({ api_status: 200, messages: [] });

            const ids  = favRows.map(r => r.message_id).filter(Boolean);
            const rows = await ctx.wo_messages.findAll({
                where: { id: { [Op.in]: ids } },
                order: [['id', 'DESC']],
                raw:   true,
            });

            const messages = rows.map(m => {
                const pos  = m.from_id === userId ? 'right' : 'left';
                let type   = '';
                if (m.media)                                       type = 'file';
                if (m.stickers && m.stickers.includes('.gif'))    type = 'gif';
                if (m.type_two === 'contact')                     type = 'contact';
                if (m.lng && m.lat && m.lng !== '0')              type = 'map';
                return {
                    id:        m.id,
                    from_id:   m.from_id,
                    to_id:     m.to_id,
                    text:      m.text     || '',
                    media:     m.media    || '',
                    stickers:  m.stickers || '',
                    time:      m.time,
                    position:  pos,
                    type:      pos + '_' + type,
                };
            });

            res.json({ api_status: 200, messages });
        } catch (err) {
            console.error('[Node/chat/fav-list]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get favorite messages' });
        }
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { favMessage, getFavMessages };
