'use strict';

/**
 * Chat Export
 *
 * Endpoints:
 *   POST /api/node/chat/export  – export private chat history as JSON or HTML
 *
 * Body params:
 *   recipient_id  – the other user
 *   format        – 'json' | 'html' (default 'json')
 *   limit         – max messages to export (default 500, max 5000)
 */

const { Op } = require('sequelize');
const crypto = require('../../helpers/crypto');

function fmtDate(ts) {
    if (!ts) return '';
    const d = new Date(ts * 1000);
    return d.toISOString().replace('T', ' ').substring(0, 19);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function buildHtml(messages, myId, myName, recipientName) {
    const rows = messages.map(m => {
        const isMine = Number(m.from_id) === Number(myId);
        const author  = isMine ? escapeHtml(myName) : escapeHtml(recipientName);
        const text    = escapeHtml(m.text_plain || '');
        const media   = m.media ? `<a href="${escapeHtml(m.media)}">[attachment]</a>` : '';
        const cls     = isMine ? 'mine' : 'theirs';
        return `<div class="msg ${cls}"><span class="author">${author}</span>` +
               `<span class="time">${escapeHtml(fmtDate(m.time))}</span>` +
               `<p>${text}${media}</p></div>`;
    }).join('\n');

    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Chat: ${escapeHtml(myName)} ↔ ${escapeHtml(recipientName)}</title>
<style>
  body{font-family:sans-serif;max-width:800px;margin:0 auto;padding:16px;background:#f5f5f5}
  h1{font-size:18px;color:#333}
  .msg{padding:8px 12px;margin:6px 0;border-radius:10px;max-width:70%}
  .mine{background:#dcf8c6;margin-left:auto}
  .theirs{background:#fff}
  .author{font-weight:bold;font-size:12px;color:#555;display:block}
  .time{font-size:11px;color:#999;display:block;margin-bottom:4px}
  p{margin:0;word-break:break-word}
</style>
</head>
<body>
<h1>Chat: ${escapeHtml(myName)} ↔ ${escapeHtml(recipientName)}</h1>
<p>Exported: ${new Date().toISOString()}</p>
${rows}
</body>
</html>`;
}

function exportChat(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const recipientId = parseInt(req.body.recipient_id);
            const format      = (req.body.format || 'json').toLowerCase();
            const limit       = Math.min(parseInt(req.body.limit) || 500, 5000);

            if (!recipientId || isNaN(recipientId))
                return res.status(400).json({ api_status: 400, error_message: 'recipient_id is required' });

            if (format !== 'json' && format !== 'html')
                return res.status(400).json({ api_status: 400, error_message: 'format must be json or html' });

            const messages = await ctx.wo_messages.findAll({
                attributes: ['id', 'from_id', 'to_id', 'text', 'iv', 'tag', 'cipher_version',
                             'media', 'mediaFileName', 'stickers', 'time', 'seen', 'type_two',
                             'lat', 'lng', 'reply_id', 'edited'],
                where: {
                    page_id: 0,
                    [Op.or]: [
                        { from_id: recipientId, to_id: userId,      deleted_two: '0' },
                        { from_id: userId,      to_id: recipientId, deleted_one: '0' },
                    ],
                },
                order: [['id', 'ASC']],
                limit,
                raw: true,
            });

            const me = await ctx.wo_users.findOne({
                attributes: ['first_name', 'last_name', 'username'],
                where: { user_id: userId },
                raw: true,
            });
            const recipient = await ctx.wo_users.findOne({
                attributes: ['first_name', 'last_name', 'username'],
                where: { user_id: recipientId },
                raw: true,
            });

            const myName        = me ? (me.first_name + ' ' + me.last_name).trim() || me.username : String(userId);
            const recipientName = recipient ? (recipient.first_name + ' ' + recipient.last_name).trim() || recipient.username : String(recipientId);

            // Decrypt server-side encrypted messages (cipher_version 1/2), leave Signal (3) as-is
            const enriched = messages.map(m => {
                const cv = Number(m.cipher_version) || 1;
                let text_plain = '';
                if (cv === 1 || cv === 2) {
                    try { text_plain = crypto.decryptMessage(m) || ''; } catch { text_plain = ''; }
                } else {
                    text_plain = '[E2EE message]';
                }
                return { ...m, text_plain };
            });

            if (format === 'html') {
                const html = buildHtml(enriched, userId, myName, recipientName);
                res.setHeader('Content-Type', 'text/html; charset=utf-8');
                res.setHeader('Content-Disposition', `attachment; filename="chat_${userId}_${recipientId}.html"`);
                return res.send(html);
            }

            // JSON format
            const jsonPayload = {
                exported_at:    new Date().toISOString(),
                me:             { id: userId, name: myName },
                recipient:      { id: recipientId, name: recipientName },
                messages_count: enriched.length,
                messages: enriched.map(m => ({
                    id:          m.id,
                    from_id:     m.from_id,
                    text:        m.text_plain,
                    media:       m.media || null,
                    stickers:    m.stickers || null,
                    time:        m.time,
                    time_iso:    fmtDate(m.time),
                    seen:        m.seen,
                    type:        m.type_two || 'text',
                    edited:      !!m.edited,
                    reply_id:    m.reply_id || null,
                })),
            };

            res.setHeader('Content-Type', 'application/json');
            res.setHeader('Content-Disposition', `attachment; filename="chat_${userId}_${recipientId}.json"`);
            return res.json(jsonPayload);
        } catch (err) {
            console.error('[Node/chat/export]', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { exportChat };
