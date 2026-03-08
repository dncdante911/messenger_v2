'use strict';

/**
 * Anonymous Admin in Groups
 *
 * An anonymous admin sends messages under the group's name/avatar
 * instead of their own, similar to Telegram channels/supergroups.
 *
 * Implementation: per-user flag stored in Wo_GroupAdmins.settings JSON
 * (no DB migration needed). When anonymous_admin=true, the server
 * replaces from_id with a virtual "group author" entry in message delivery.
 *
 * Endpoints:
 *   POST /api/node/group/admin/set-anonymous  – toggle anonymous mode for self
 *   POST /api/node/group/admin/get-anonymous  – get current anonymous status
 */

const { Op } = require('sequelize');

async function getAdminRecord(ctx, groupId, userId) {
    return ctx.wo_groupadmins.findOne({
        where: { group_id: groupId, user_id: userId },
    });
}

async function isGroupAdmin(ctx, groupId, userId) {
    const g = await ctx.wo_groupchat.findOne({ attributes: ['user_id'], where: { group_id: groupId }, raw: true });
    if (g && Number(g.user_id) === Number(userId)) return true;
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1', role: { [Op.in]: ['admin', 'owner', 'moderator'] } },
        raw: true,
    });
    return !!m;
}

function parseAdminSettings(admin) {
    if (!admin?.settings) return {};
    try {
        return typeof admin.settings === 'string'
            ? JSON.parse(admin.settings)
            : admin.settings;
    } catch { return {}; }
}

// ─── SET ANONYMOUS ────────────────────────────────────────────────────────────

function setAnonymousAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const groupId   = parseInt(req.body.group_id);
            const anonymous = req.body.anonymous === true || req.body.anonymous === '1' || req.body.anonymous === 'true';

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can enable anonymous mode' });

            // Upsert Wo_GroupAdmins (create entry if not exists for owner)
            let adminRecord = await getAdminRecord(ctx, groupId, userId);
            if (!adminRecord) {
                adminRecord = await ctx.wo_groupadmins.create({
                    group_id: groupId,
                    user_id:  userId,
                    general:  1,
                    privacy:  1,
                    avatar:   1,
                    members:  0,
                    analytics: 1,
                    delete_group: 0,
                });
            }

            const settings     = parseAdminSettings(adminRecord);
            settings.anonymous = anonymous;

            await adminRecord.update({ settings: JSON.stringify(settings) });

            // Notify the admin's own sockets so UI can reflect the change immediately
            if (ctx.userIdSocket?.[userId]) {
                ctx.userIdSocket[userId].forEach(sock => {
                    sock.emit('anonymous_admin_changed', { group_id: groupId, anonymous });
                });
            }

            return res.json({ api_status: 200, anonymous, message: anonymous ? 'Anonymous mode enabled' : 'Anonymous mode disabled' });
        } catch (err) {
            console.error('[Node/group/admin/set-anonymous]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET ANONYMOUS ────────────────────────────────────────────────────────────

function getAnonymousAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const adminRecord  = await getAdminRecord(ctx, groupId, userId);
            const settings     = parseAdminSettings(adminRecord);
            const anonymous    = !!settings.anonymous;

            return res.json({ api_status: 200, anonymous });
        } catch (err) {
            console.error('[Node/group/admin/get-anonymous]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { setAnonymousAdmin, getAnonymousAdmin };
