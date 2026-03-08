'use strict';

/**
 * Group Chat Export
 *
 * Endpoints:
 *   POST /api/node/group/export  – export group chat history as JSON or HTML
 *
 * Body params:
 *   group_id  – the group
 *   format    – 'json' | 'html' (default 'json')
 *   limit     – max messages (default 500, max 5000)
 */

const { Op } = require('sequelize');
const crypto  = require('../../helpers/crypto');

function fmtDate(ts) {
    if (!ts) return '';
    return new Date(ts * 1000).toISOString().replace('T', ' ').substring(0, 19);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function buildGroupHtml(messages, groupName) {
    const rows = messages.map(m => {
        const name  = escapeHtml(m.sender_name || String(m.from_id));
        const text  = escapeHtml(m.text_plain || '');
        const media = m.media ? `<a href="${escapeHtml(m.media)}">[attachment]</a>` : '';
        return `<div class="msg"><span class="author">${name}</span>` +
               `<span class="time">${escapeHtml(fmtDate(m.time))}</span>` +
               `<p>${text}${media}</p></div>`;
    }).join('\n');

    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Group: ${escapeHtml(groupName)}</title>
<style>
  body{font-family:sans-serif;max-width:800px;margin:0 auto;padding:16px;background:#f5f5f5}
  h1{font-size:18px;color:#333}
  .msg{padding:8px 12px;margin:6px 0;border-radius:10px;background:#fff;max-width:80%}
  .author{font-weight:bold;font-size:12px;color:#555;display:block}
  .time{font-size:11px;color:#999;display:block;margin-bottom:4px}
  p{margin:0;word-break:break-word}
</style>
</head>
<body>
<h1>Group: ${escapeHtml(groupName)}</h1>
<p>Exported: ${new Date().toISOString()}</p>
${rows}
</body>
</html>`;
}

function exportGroupChat(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const format  = (req.body.format || 'json').toLowerCase();
            const limit   = Math.min(parseInt(req.body.limit) || 500, 5000);

            if (!groupId || isNaN(groupId))
                return res.status(400).json({ api_status: 400, error_message: 'group_id is required' });

            if (format !== 'json' && format !== 'html')
                return res.status(400).json({ api_status: 400, error_message: 'format must be json or html' });

            // Verify membership
            const member = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId, active: '1' },
                raw: true,
            });
            if (!member)
                return res.status(403).json({ api_status: 403, error_message: 'Not a member of this group' });

            const group = await ctx.wo_groupchat.findOne({
                attributes: ['group_id', 'group_name'],
                where: { group_id: groupId },
                raw: true,
            });
            if (!group)
                return res.status(404).json({ api_status: 404, error_message: 'Group not found' });

            const messages = await ctx.wo_messages.findAll({
                attributes: ['id', 'from_id', 'text', 'iv', 'tag', 'cipher_version',
                             'media', 'mediaFileName', 'stickers', 'time', 'type_two',
                             'lat', 'lng', 'reply_id', 'edited'],
                where: { group_id: groupId, page_id: 0 },
                order: [['id', 'ASC']],
                limit,
                raw: true,
            });

            // Collect sender names
            const senderIds = [...new Set(messages.map(m => m.from_id))];
            const users     = await ctx.wo_users.findAll({
                attributes: ['user_id', 'first_name', 'last_name', 'username'],
                where: { user_id: { [Op.in]: senderIds } },
                raw: true,
            });
            const nameMap = {};
            for (const u of users) {
                nameMap[u.user_id] = ((u.first_name + ' ' + u.last_name).trim()) || u.username;
            }

            const enriched = messages.map(m => {
                const cv = Number(m.cipher_version) || 1;
                let text_plain = '';
                if (cv === 1 || cv === 2) {
                    try { text_plain = crypto.decryptMessage(m) || ''; } catch { text_plain = ''; }
                } else {
                    text_plain = '[E2EE message]';
                }
                return { ...m, text_plain, sender_name: nameMap[m.from_id] || String(m.from_id) };
            });

            if (format === 'html') {
                const html = buildGroupHtml(enriched, group.group_name || String(groupId));
                res.setHeader('Content-Type', 'text/html; charset=utf-8');
                res.setHeader('Content-Disposition', `attachment; filename="group_${groupId}.html"`);
                return res.send(html);
            }

            const jsonPayload = {
                exported_at:    new Date().toISOString(),
                group:          { id: groupId, name: group.group_name },
                messages_count: enriched.length,
                messages: enriched.map(m => ({
                    id:          m.id,
                    from_id:     m.from_id,
                    sender_name: m.sender_name,
                    text:        m.text_plain,
                    media:       m.media || null,
                    stickers:    m.stickers || null,
                    time:        m.time,
                    time_iso:    fmtDate(m.time),
                    type:        m.type_two || 'text',
                    edited:      !!m.edited,
                    reply_id:    m.reply_id || null,
                })),
            };

            res.setHeader('Content-Type', 'application/json');
            res.setHeader('Content-Disposition', `attachment; filename="group_${groupId}.json"`);
            return res.json(jsonPayload);
        } catch (err) {
            console.error('[Node/group/export]', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { exportGroupChat };
