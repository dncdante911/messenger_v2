/**
 * Group Chats — Admin
 * AVATAR, SETTINGS, MUTE/UNMUTE, QR, STATISTICS, ADD/REMOVE ADMIN
 *
 * Endpoints (all registered in index.js):
 *   POST /api/node/group/upload-avatar  – upload group avatar (multer)
 *   POST /api/node/group/settings       – update group settings / meta
 *   POST /api/node/group/mute           – mute group notifications
 *   POST /api/node/group/unmute         – unmute group notifications
 *   POST /api/node/group/qr-generate    – generate invite QR code
 *   POST /api/node/group/qr-join        – join group via QR invite code
 *   POST /api/node/group/statistics     – group statistics (admin only)
 *   POST /api/node/group/add-admin      – add/update group admin record
 *   POST /api/node/group/remove-admin   – remove group admin record
 */

'use strict';

const { Op }  = require('sequelize');
const crypto  = require('crypto');
const funcs   = require('../../functions/functions');
const { formatGroup } = require('./management');

// ─── helpers ─────────────────────────────────────────────────────────────────

async function isGroupOwner(ctx, groupId, userId) {
    const g = await ctx.wo_groupchat.findOne({
        attributes: ['user_id'],
        where: { group_id: groupId },
        raw: true,
    });
    return g && Number(g.user_id) === Number(userId);
}

async function isGroupAdmin(ctx, groupId, userId) {
    if (await isGroupOwner(ctx, groupId, userId)) return true;
    const m = await ctx.wo_groupchatusers.findOne({
        attributes: ['role'],
        where: {
            group_id: groupId,
            user_id:  userId,
            active:   '1',
            role:     { [Op.in]: ['admin', 'moderator'] },
        },
        raw: true,
    });
    return !!m;
}

function parseSettings(group) {
    if (!group || !group.settings) return {};
    try {
        return typeof group.settings === 'string'
            ? JSON.parse(group.settings)
            : group.settings;
    } catch { return {}; }
}

// ─── UPLOAD AVATAR ───────────────────────────────────────────────────────────

function uploadAvatar(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            console.log(`[Node/group/upload-avatar] userId=${userId} groupId=${groupId} file=${req.file ? req.file.filename : 'MISSING'}`);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!req.file)
                return res.json({ api_status: 400, error_message: 'Avatar file is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can change the group avatar' });

            const relativePath = 'upload/photos/groups/' + req.file.filename;

            const [affectedCount] = await ctx.wo_groupchat.update(
                { avatar: relativePath },
                { where: { group_id: groupId } }
            );

            if (affectedCount === 0)
                return res.json({ api_status: 404, error_message: 'Group not found or update failed' });

            const avatarUrl = await funcs.Wo_GetMedia(ctx, relativePath);

            // Notify all group members about the new avatar
            io.to('group_' + groupId).emit('group_updated', {
                group_id: groupId,
                avatar:   avatarUrl,
            });

            const updatedGroup = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const group = updatedGroup ? await formatGroup(ctx, updatedGroup, userId) : null;

            console.log(`[Node/group/upload-avatar] Success: group=${groupId} avatar=${avatarUrl}`);

            return res.json({ api_status: 200, url: avatarUrl, group });
        } catch (err) {
            console.error('[Node/group/upload-avatar]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── UPDATE SETTINGS ─────────────────────────────────────────────────────────

function updateSettings(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can update settings' });

            const group    = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group)
                return res.json({ api_status: 404, error_message: 'Group not found' });

            const settings = parseSettings(group);
            const updates  = {};

            // Top-level group fields
            if (req.body.group_name  !== undefined) updates.group_name  = String(req.body.group_name).trim().substring(0, 100);
            if (req.body.description !== undefined) updates.description = String(req.body.description).trim().substring(0, 500);
            if (req.body.is_private  !== undefined) updates.is_private  = req.body.is_private === '1' || req.body.is_private === true ? '1' : '0';

            // If a full settings JSON blob is provided, merge it in
            if (req.body.settings_json !== undefined) {
                try {
                    const incoming = typeof req.body.settings_json === 'string'
                        ? JSON.parse(req.body.settings_json)
                        : req.body.settings_json;
                    Object.assign(settings, incoming);
                } catch (_) {}
            }

            // Individual settings fields (stored in Wo_GroupChat.settings JSON column)
            const boolFields = [
                'allow_members_invite', 'allow_members_pin', 'allow_members_delete_messages',
                'allow_voice_calls', 'allow_video_calls', 'history_visible_for_new_members',
                'allow_members_send_media', 'allow_members_send_stickers', 'allow_members_send_gifs',
                'allow_members_send_links', 'allow_members_send_polls',
                'anti_spam_enabled', 'auto_mute_spammers', 'block_new_users_media',
                'join_requests_enabled'
            ];
            const intFields = ['slow_mode_seconds', 'history_messages_count', 'max_messages_per_minute', 'new_user_restriction_hours'];
            for (const f of boolFields) {
                if (req.body[f] !== undefined) settings[f] = req.body[f] === true || req.body[f] === 'true' || req.body[f] === '1';
            }
            for (const f of intFields) {
                if (req.body[f] !== undefined) settings[f] = parseInt(req.body[f]) || 0;
            }
            if (req.body.who_can_send_messages  !== undefined) settings.who_can_send_messages  = req.body.who_can_send_messages;
            if (req.body.formatting_permissions !== undefined) settings.formatting_permissions = req.body.formatting_permissions;
            if (req.body.destruct_at            !== undefined) updates.destruct_at             = parseInt(req.body.destruct_at) || 0;

            updates.settings = JSON.stringify(settings);

            await ctx.wo_groupchat.update(updates, { where: { group_id: groupId } });

            const updatedGroup = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const groupData    = await formatGroup(ctx, updatedGroup, userId);

            // Notify members about settings change
            io.to('group_' + groupId).emit('group_updated', { group_id: groupId, ...groupData });

            return res.json({ api_status: 200, group: groupData });
        } catch (err) {
            console.error('[Node/group/settings]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── MUTE GROUP ───────────────────────────────────────────────────────────────

function muteGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const existing = await ctx.wo_mute.findOne({
                where: { user_id: userId, chat_id: groupId, type: 'group' },
                raw: true,
            });

            if (!existing) {
                await ctx.wo_mute.create({
                    user_id:  userId,
                    chat_id:  groupId,
                    notify:   'no',
                    type:     'group',
                    time:     Math.floor(Date.now() / 1000),
                });
            } else {
                await ctx.wo_mute.update(
                    { notify: 'no' },
                    { where: { user_id: userId, chat_id: groupId, type: 'group' } }
                );
            }

            return res.json({ api_status: 200, message: 'Group muted' });
        } catch (err) {
            console.error('[Node/group/mute]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── UNMUTE GROUP ─────────────────────────────────────────────────────────────

function unmuteGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            await ctx.wo_mute.destroy({
                where: { user_id: userId, chat_id: groupId, type: 'group' },
            });

            return res.json({ api_status: 200, message: 'Group unmuted' });
        } catch (err) {
            console.error('[Node/group/unmute]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GENERATE QR ─────────────────────────────────────────────────────────────

function generateQr(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can generate invite QR' });

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group)
                return res.json({ api_status: 404, error_message: 'Group not found' });

            // Generate a unique invite code and store it in settings JSON
            const inviteCode = crypto.randomBytes(16).toString('hex');
            const settings   = parseSettings(group);
            settings.invite_code = inviteCode;

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            const joinUrl = `${ctx.globalconfig.site_url}/join/group/${inviteCode}`;

            return res.json({ api_status: 200, invite_code: inviteCode, join_url: joinUrl });
        } catch (err) {
            console.error('[Node/group/qr-generate]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── JOIN BY QR ───────────────────────────────────────────────────────────────

function joinByQr(ctx, io) {
    return async (req, res) => {
        try {
            const userId     = req.userId;
            const inviteCode = (req.body.invite_code || req.body.qr_code || '').trim();

            if (!inviteCode)
                return res.json({ api_status: 400, error_message: 'invite_code is required' });

            // Find group with matching invite code in settings JSON
            // We search for the invite_code substring in the settings column
            const groups = await ctx.wo_groupchat.findAll({
                where: {
                    settings: { [Op.like]: `%${inviteCode}%` },
                },
                raw: true,
            });

            // Verify by parsing settings (LIKE gives false positives on substrings)
            const group = groups.find(g => {
                const s = parseSettings(g);
                return s.invite_code === inviteCode;
            });

            if (!group)
                return res.json({ api_status: 404, error_message: 'Invalid or expired invite code' });

            const groupId = group.group_id;

            // Check if already a member
            const existing = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId },
                raw: true,
            });

            if (existing) {
                if (existing.active === '1') {
                    const groupData = await formatGroup(ctx, group, userId);
                    return res.json({ api_status: 200, message: 'Already a member', group: groupData });
                }
                // Reactivate
                await ctx.wo_groupchatusers.update(
                    { active: '1' },
                    { where: { group_id: groupId, user_id: userId } }
                );
            } else {
                await ctx.wo_groupchatusers.create({
                    group_id: groupId,
                    user_id:  userId,
                    role:     'member',
                    active:   '1',
                });
            }

            const groupData = await formatGroup(ctx, group, userId);

            // Notify group members
            const userBasic = await ctx.wo_users.findOne({
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                where: { user_id: userId },
                raw: true,
            });
            io.to('group_' + groupId).emit('group_member_added', {
                group_id: groupId,
                user_id:  userId,
                user:     userBasic,
                via_qr:   true,
            });

            return res.json({ api_status: 200, group: groupData });
        } catch (err) {
            console.error('[Node/group/qr-join]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── STATISTICS ───────────────────────────────────────────────────────────────

function getStatistics(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can view statistics' });

            const membersCount = await ctx.wo_groupchatusers.count({
                where: { group_id: groupId, active: '1' },
            });

            const messagesCount = await ctx.wo_messages.count({
                where: { group_id: groupId, page_id: 0 },
            });

            const oneWeekAgo      = Math.floor(Date.now() / 1000) - 7 * 24 * 60 * 60;
            const messagesLastWeek = await ctx.wo_messages.count({
                where: { group_id: groupId, page_id: 0, time: { [Op.gte]: oneWeekAgo } },
            });

            const oneDayAgo      = Math.floor(Date.now() / 1000) - 24 * 60 * 60;
            const membersLastDay = await ctx.wo_groupchatusers.count({
                where: { group_id: groupId, active: '1', last_seen: { [Op.gte]: oneDayAgo } },
            });

            // Top active senders (last 7 days)
            const recentMessages = await ctx.wo_messages.findAll({
                attributes: ['from_id'],
                where: { group_id: groupId, page_id: 0, time: { [Op.gte]: oneWeekAgo } },
                raw: true,
            });
            const senderCount = {};
            for (const m of recentMessages) {
                senderCount[m.from_id] = (senderCount[m.from_id] || 0) + 1;
            }
            const topSenderIds = Object.entries(senderCount)
                .sort((a, b) => b[1] - a[1])
                .slice(0, 5)
                .map(([id]) => parseInt(id));

            const topSenders = [];
            for (const sid of topSenderIds) {
                const u = await ctx.wo_users.findOne({
                    attributes: ['user_id', 'username', 'first_name', 'last_name'],
                    where: { user_id: sid },
                    raw: true,
                });
                if (u) {
                    topSenders.push({
                        user_id: u.user_id,
                        name: (u.first_name && u.last_name) ? `${u.first_name} ${u.last_name}` : u.username,
                        messages: senderCount[sid],
                    });
                }
            }

            return res.json({
                api_status: 200,
                statistics: {
                    members_count:         membersCount,
                    messages_count:        messagesCount,
                    messages_last_week:    messagesLastWeek,
                    active_members_24h:    membersLastDay,
                    top_senders:           topSenders,
                },
            });
        } catch (err) {
            console.error('[Node/group/statistics]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── ADD GROUP ADMIN ──────────────────────────────────────────────────────────

function addGroupAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId       = req.userId;
            const groupId      = parseInt(req.body.group_id);
            const targetUserId = parseInt(req.body.user_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });
            if (!targetUserId || isNaN(targetUserId))
                return res.json({ api_status: 400, error_message: 'user_id is required' });

            // Only owner can promote admins
            if (!await isGroupOwner(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only the group owner can add admins' });

            // Target must be a group member
            const membership = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: targetUserId, active: '1' },
                raw: true,
            });
            if (!membership)
                return res.json({ api_status: 404, error_message: 'User is not a member of this group' });

            // Parse permissions from request (defaults: general/privacy/avatar/analytics = 1, members/delete = 0)
            const permissions = {
                general:      req.body.perm_general      !== undefined ? parseInt(req.body.perm_general)      : 1,
                privacy:      req.body.perm_privacy       !== undefined ? parseInt(req.body.perm_privacy)      : 1,
                avatar:       req.body.perm_avatar        !== undefined ? parseInt(req.body.perm_avatar)       : 1,
                members:      req.body.perm_members       !== undefined ? parseInt(req.body.perm_members)      : 0,
                analytics:    req.body.perm_analytics     !== undefined ? parseInt(req.body.perm_analytics)    : 1,
                delete_group: req.body.perm_delete_group  !== undefined ? parseInt(req.body.perm_delete_group) : 0,
            };

            // Upsert Wo_GroupAdmins record
            const existingAdmin = await ctx.wo_groupadmins.findOne({
                where: { group_id: groupId, user_id: targetUserId },
            });
            if (existingAdmin) {
                await existingAdmin.update(permissions);
            } else {
                await ctx.wo_groupadmins.create({
                    group_id: groupId,
                    user_id:  targetUserId,
                    ...permissions,
                });
            }

            // Promote role in Wo_GroupChatUsers to 'admin'
            await ctx.wo_groupchatusers.update(
                { role: 'admin' },
                { where: { group_id: groupId, user_id: targetUserId } }
            );

            // Notify target user
            const sockets = ctx.userIdSocket ? ctx.userIdSocket[targetUserId] : null;
            if (sockets && Array.isArray(sockets)) {
                sockets.forEach(sid => {
                    const sock = io.sockets.sockets.get(sid);
                    if (sock) sock.emit('group_role_changed', { group_id: groupId, user_id: targetUserId, role: 'admin' });
                });
            }

            return res.json({ api_status: 200, message: 'Admin added', permissions });
        } catch (err) {
            console.error('[Node/group/add-admin]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── REMOVE GROUP ADMIN ───────────────────────────────────────────────────────

function removeGroupAdmin(ctx, io) {
    return async (req, res) => {
        try {
            const userId       = req.userId;
            const groupId      = parseInt(req.body.group_id);
            const targetUserId = parseInt(req.body.user_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });
            if (!targetUserId || isNaN(targetUserId))
                return res.json({ api_status: 400, error_message: 'user_id is required' });

            // Only owner can demote admins
            if (!await isGroupOwner(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only the group owner can remove admins' });

            // Cannot demote the owner themselves
            if (targetUserId === userId)
                return res.json({ api_status: 400, error_message: 'Cannot remove yourself as owner' });

            await ctx.wo_groupadmins.destroy({
                where: { group_id: groupId, user_id: targetUserId },
            });

            // Downgrade role to 'member'
            await ctx.wo_groupchatusers.update(
                { role: 'member' },
                { where: { group_id: groupId, user_id: targetUserId } }
            );

            // Notify target user
            const sockets = ctx.userIdSocket ? ctx.userIdSocket[targetUserId] : null;
            if (sockets && Array.isArray(sockets)) {
                sockets.forEach(sid => {
                    const sock = io.sockets.sockets.get(sid);
                    if (sock) sock.emit('group_role_changed', { group_id: groupId, user_id: targetUserId, role: 'member' });
                });
            }

            return res.json({ api_status: 200, message: 'Admin removed' });
        } catch (err) {
            console.error('[Node/group/remove-admin]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── exports ─────────────────────────────────────────────────────────────────

module.exports = {
    uploadAvatar,
    updateSettings,
    muteGroup,
    unmuteGroup,
    generateQr,
    joinByQr,
    getStatistics,
    addGroupAdmin,
    removeGroupAdmin,
};
