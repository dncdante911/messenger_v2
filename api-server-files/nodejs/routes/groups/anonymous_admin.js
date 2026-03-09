'use strict';

/**
 * Anonymous Admin in Groups
 *
 * When enabled, the admin sends messages under the group's name/avatar.
 * Flag stored in Wo_GroupAdmins.is_anonymous_admin (TINYINT, default 0).
 *
 * DB migration (run once on server):
 *   ALTER TABLE Wo_GroupAdmins ADD COLUMN is_anonymous_admin TINYINT NOT NULL DEFAULT 0;
 *
 * Endpoints:
 *   POST /api/node/group/admin/set-anonymous  – toggle anonymous mode for self
 *   POST /api/node/group/admin/get-anonymous  – get current anonymous status
 */

const { Op } = require('sequelize');

async function isGroupAdmin(ctx, groupId, userId) {
    const g = await ctx.wo_groupchat.findOne({
        attributes: ['user_id'], where: { group_id: groupId }, raw: true,
    });
    if (g && Number(g.user_id) === Number(userId)) return true;
    const m = await ctx.wo_groupchatusers.findOne({
        where: {
            group_id: groupId,
            user_id: userId,
            active: '1',
            role: { [Op.in]: ['admin', 'owner', 'moderator'] },
        },
        raw: true,
    });
    return !!m;
}

// ─── SET ANONYMOUS ─────────────────────────────────────────────────────────────

function setAnonymousAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const groupId   = parseInt(req.body.group_id);
            const anonymous = req.body.anonymous === true
                           || req.body.anonymous === '1'
                           || req.body.anonymous === 'true';

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can enable anonymous mode' });

            // Upsert the admin record, set is_anonymous_admin flag
            const [adminRecord] = await ctx.wo_groupadmins.findOrCreate({
                where: { group_id: groupId, user_id: userId },
                defaults: {
                    group_id:           groupId,
                    user_id:            userId,
                    general:            1,
                    privacy:            1,
                    avatar:             1,
                    members:            0,
                    analytics:          1,
                    delete_group:       0,
                    is_anonymous_admin: anonymous ? 1 : 0,
                },
            });

            await adminRecord.update({ is_anonymous_admin: anonymous ? 1 : 0 });

            // Notify admin's own sockets so UI reflects change immediately
            if (ctx.userIdSocket?.[userId]) {
                ctx.userIdSocket[userId].forEach(sock => {
                    sock.emit('anonymous_admin_changed', { group_id: groupId, anonymous });
                });
            }

            return res.json({
                api_status: 200,
                anonymous,
                message: anonymous ? 'Anonymous mode enabled' : 'Anonymous mode disabled',
            });
        } catch (err) {
            console.error('[Node/group/admin/set-anonymous]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET ANONYMOUS ─────────────────────────────────────────────────────────────

function getAnonymousAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const adminRecord = await ctx.wo_groupadmins.findOne({
                where: { group_id: groupId, user_id: userId },
                raw: true,
            });
            const anonymous = adminRecord ? !!adminRecord.is_anonymous_admin : false;

            return res.json({ api_status: 200, anonymous });
        } catch (err) {
            console.error('[Node/group/admin/get-anonymous]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { setAnonymousAdmin, getAnonymousAdmin };
