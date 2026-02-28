/**
 * Groups — Member Management
 *
 * REST Endpoints:
 *   POST /api/node/group/members       – list members
 *   POST /api/node/group/add-member    – add member(s) by user_ids (comma-separated)
 *   POST /api/node/group/remove-member – remove member
 *   POST /api/node/group/set-role      – set role (admin/moderator/member)
 *   POST /api/node/group/join          – join public group
 *   POST /api/node/group/request-join  – request to join private group
 *   POST /api/node/group/approve-join  – approve join request (admin only)
 *   POST /api/node/group/reject-join   – reject join request (admin only)
 *   POST /api/node/group/join-requests – get pending join requests (admin only)
 */

'use strict';

const { Op } = require('sequelize');
const funcs  = require('../../functions/functions');

// ─── helpers ─────────────────────────────────────────────────────────────────

async function isGroupAdmin(ctx, groupId, userId) {
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1' },
        raw: true
    });
    return m && (m.role === 'owner' || m.role === 'admin');
}

async function formatMember(ctx, membership, mutedMembers = [], bannedUsers = []) {
    const u = await ctx.wo_users.findOne({
        where: { user_id: membership.user_id },
        attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'lastseen'],
        raw: true
    });
    if (!u) return null;
    const avatarUrl = u.avatar ? await funcs.Wo_GetMedia(ctx, u.avatar) : '';
    const name = u.first_name
        ? (u.first_name + ' ' + (u.last_name || '')).trim()
        : u.username;
    return {
        id: membership.user_id,
        user_id: membership.user_id,
        username: u.username || '',
        name,
        avatar: avatarUrl,
        role: membership.role,
        is_muted: mutedMembers.includes(Number(membership.user_id)),
        is_blocked: bannedUsers.includes(Number(membership.user_id)),
        last_seen: u.lastseen ? String(u.lastseen) : null
    };
}

// ─── members ──────────────────────────────────────────────────────────────────

function getMembers(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const limit   = parseInt(req.body.limit) || 100;
            const offset  = parseInt(req.body.offset) || 0;

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });

            let groupSettings = {};
            try { groupSettings = group.settings ? (typeof group.settings === 'string' ? JSON.parse(group.settings) : group.settings) : {}; } catch (_) {}
            const mutedMembers = (groupSettings.muted_members || []).map(Number);
            const bannedUsers  = (groupSettings.banned_users  || []).map(Number);

            const memberships = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                order: [['role', 'ASC']],
                limit,
                offset,
                raw: true
            });

            const totalCount = await ctx.wo_groupchatusers.count({
                where: { group_id: groupId, active: '1' }
            });

            const members = [];
            for (const m of memberships) {
                const fmt = await formatMember(ctx, m, mutedMembers, bannedUsers);
                if (fmt) members.push(fmt);
            }

            return res.json({
                api_status: 200,
                members,
                total_count: totalCount,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/getMembers]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── add-member ───────────────────────────────────────────────────────────────

function addMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const groupId     = parseInt(req.body.group_id);
            // Support both "user_id" (single) and "user_ids" / "parts" (comma-separated)
            const rawIds = req.body.user_ids || req.body.parts || String(req.body.user_id || '');
            const targetIds = rawIds.split(',').map(Number).filter(Boolean);

            if (!groupId || targetIds.length === 0) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id and user_id(s) are required' });
            }

            if (!await isGroupAdmin(ctx, groupId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can add members' });
            }

            const added = [];
            for (const targetId of targetIds) {
                const existing = await ctx.wo_groupchatusers.findOne({
                    where: { group_id: groupId, user_id: targetId },
                    raw: true
                });

                if (existing) {
                    if (existing.active !== '1') {
                        await ctx.wo_groupchatusers.update(
                            { active: '1' },
                            { where: { group_id: groupId, user_id: targetId } }
                        );
                        added.push(targetId);
                    }
                } else {
                    await ctx.wo_groupchatusers.create({
                        user_id: targetId,
                        group_id: groupId,
                        role: 'member',
                        active: '1',
                        last_seen: '0'
                    });
                    added.push(targetId);
                }

                // Notify member via Socket.IO
                const s = ctx.userIdSocket ? ctx.userIdSocket[targetId] : null;
                if (s) io.to(s).emit('added_to_group', { group_id: groupId });
            }

            // Notify existing members
            const allMembers = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            allMembers.forEach(m => {
                if (!added.includes(m.user_id)) {
                    const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                    if (s) io.to(s).emit('group_member_added', { group_id: groupId, new_user_ids: added });
                }
            });

            console.log(`[Groups] Added ${added.length} member(s) to group ${groupId} by ${userId}`);

            return res.json({ api_status: 200, added_count: added.length, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/addMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── remove-member ────────────────────────────────────────────────────────────

function removeMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId     = req.userId;
            const groupId    = parseInt(req.body.group_id);
            const targetId   = parseInt(req.body.user_id);
            const targetIds  = req.body.parts
                ? String(req.body.parts).split(',').map(Number).filter(Boolean)
                : [targetId];

            if (!groupId || targetIds.some(isNaN)) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id and user_id are required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });

            if (!await isGroupAdmin(ctx, groupId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can remove members' });
            }

            for (const tid of targetIds) {
                // Cannot remove owner
                // eslint-disable-next-line eqeqeq
                if (group.user_id == tid) continue;

                await ctx.wo_groupchatusers.update(
                    { active: '0' },
                    { where: { group_id: groupId, user_id: tid } }
                );

                const s = ctx.userIdSocket ? ctx.userIdSocket[tid] : null;
                if (s) io.to(s).emit('removed_from_group', { group_id: groupId });
            }

            // Notify remaining members
            const remaining = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            remaining.forEach(m => {
                const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                if (s) io.to(s).emit('group_member_removed', { group_id: groupId, removed_user_ids: targetIds });
            });

            console.log(`[Groups] Removed ${targetIds.length} member(s) from group ${groupId} by ${userId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/removeMember]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── set-role ─────────────────────────────────────────────────────────────────

function setRole(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);
            const role     = req.body.role || 'member';

            if (!groupId || !targetId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id and user_id are required' });
            }

            const validRoles = ['admin', 'moderator', 'member'];
            if (!validRoles.includes(role)) {
                return res.json({ api_status: 400, error_code: 400, error_message: `role must be one of: ${validRoles.join(', ')}` });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });

            // Only owner can set roles
            // eslint-disable-next-line eqeqeq
            if (group.user_id != userId) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only the owner can change member roles' });
            }

            // eslint-disable-next-line eqeqeq
            if (group.user_id == targetId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Cannot change the owner role' });
            }

            const [updated] = await ctx.wo_groupchatusers.update(
                { role },
                { where: { group_id: groupId, user_id: targetId, active: '1' } }
            );

            if (updated === 0) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Member not found in this group' });
            }

            const s = ctx.userIdSocket ? ctx.userIdSocket[targetId] : null;
            if (s) io.to(s).emit('group_role_changed', { group_id: groupId, role });

            console.log(`[Groups] Member ${targetId} role set to '${role}' in group ${groupId} by ${userId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/setRole]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── join (public groups) ─────────────────────────────────────────────────────

function join(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });

            if (group.is_private === '1') {
                return res.json({ api_status: 403, error_code: 403, error_message: 'This is a private group. Use request-join instead.' });
            }

            const existing = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId },
                raw: true
            });

            if (existing && existing.active === '1') {
                const fmt = await formatMember(ctx, { ...existing, user_id: userId });
                return res.json({ api_status: 200, member: fmt, error_code: null, error_message: 'Already a member' });
            }

            if (existing) {
                await ctx.wo_groupchatusers.update({ active: '1' }, { where: { group_id: groupId, user_id: userId } });
            } else {
                await ctx.wo_groupchatusers.create({
                    user_id: userId, group_id: groupId,
                    role: 'member', active: '1',
                    last_seen: Math.floor(Date.now() / 1000).toString()
                });
            }

            // Notify existing members
            const members = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            members.forEach(m => {
                if (m.user_id === userId) return;
                const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                if (s) io.to(s).emit('group_member_added', { group_id: groupId, new_user_ids: [userId] });
            });

            console.log(`[Groups] User ${userId} joined group ${groupId}`);

            return res.json({ api_status: 200, group_id: groupId, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/join]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── join-requests ────────────────────────────────────────────────────────────
// Using group.settings JSON field to store pending requests array

function requestJoin(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });

            if (group.is_private !== '1') {
                // For public groups just join directly
                req.body.group_id = groupId;
                return join(ctx, io)(req, res);
            }

            // Add to pending requests in settings JSON
            let settings = {};
            if (group.settings) {
                try { settings = JSON.parse(group.settings); } catch (e) { settings = {}; }
            }
            if (!settings.join_requests) settings.join_requests = [];

            if (!settings.join_requests.includes(userId)) {
                settings.join_requests.push(userId);
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: groupId } }
                );
            }

            // Notify group admins
            const admins = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1', role: { [Op.in]: ['owner', 'admin'] } },
                attributes: ['user_id'],
                raw: true
            });
            admins.forEach(a => {
                const s = ctx.userIdSocket ? ctx.userIdSocket[a.user_id] : null;
                if (s) io.to(s).emit('group_join_request', { group_id: groupId, user_id: userId });
            });

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/requestJoin]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

function getJoinRequests(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            if (!await isGroupAdmin(ctx, groupId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can view join requests' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            let settings = {};
            try { settings = group.settings ? JSON.parse(group.settings) : {}; } catch (e) { settings = {}; }

            const requestIds = settings.join_requests || [];
            const requests = [];
            for (const uid of requestIds) {
                const u = await ctx.wo_users.findOne({
                    where: { user_id: uid },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                    raw: true
                });
                if (u) {
                    const avatarUrl = u.avatar ? await funcs.Wo_GetMedia(ctx, u.avatar) : '';
                    const name = u.first_name ? (u.first_name + ' ' + (u.last_name || '')).trim() : u.username;
                    requests.push({ user_id: uid, username: u.username, name, avatar: avatarUrl });
                }
            }

            return res.json({
                api_status: 200,
                join_requests: requests,
                total_count: requests.length,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/getJoinRequests]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

function approveJoinRequest(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id and user_id are required' });
            }

            if (!await isGroupAdmin(ctx, groupId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can approve requests' });
            }

            // Remove from pending requests
            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            let settings = {};
            try { settings = group.settings ? JSON.parse(group.settings) : {}; } catch (e) { settings = {}; }
            if (settings.join_requests) {
                settings.join_requests = settings.join_requests.filter(id => id !== targetId);
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: groupId } }
                );
            }

            // Add as member
            const existing = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: targetId },
                raw: true
            });
            if (existing) {
                await ctx.wo_groupchatusers.update({ active: '1' }, { where: { group_id: groupId, user_id: targetId } });
            } else {
                await ctx.wo_groupchatusers.create({
                    user_id: targetId, group_id: groupId,
                    role: 'member', active: '1', last_seen: '0'
                });
            }

            const s = ctx.userIdSocket ? ctx.userIdSocket[targetId] : null;
            if (s) io.to(s).emit('group_join_approved', { group_id: groupId });

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/approveJoinRequest]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

function rejectJoinRequest(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id and user_id are required' });
            }

            if (!await isGroupAdmin(ctx, groupId, userId)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can reject requests' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            let settings = {};
            try { settings = group.settings ? JSON.parse(group.settings) : {}; } catch (e) { settings = {}; }
            if (settings.join_requests) {
                settings.join_requests = settings.join_requests.filter(id => id !== targetId);
                await ctx.wo_groupchat.update(
                    { settings: JSON.stringify(settings) },
                    { where: { group_id: groupId } }
                );
            }

            const s = ctx.userIdSocket ? ctx.userIdSocket[targetId] : null;
            if (s) io.to(s).emit('group_join_rejected', { group_id: groupId });

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/rejectJoinRequest]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── ban-member ───────────────────────────────────────────────────────────────

function banMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId)
                return res.json({ api_status: 400, error_message: 'group_id and user_id are required' });
            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Admin only' });

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            let settings = {};
            try { settings = group.settings ? (typeof group.settings === 'string' ? JSON.parse(group.settings) : group.settings) : {}; } catch (_) {}

            const banned = (settings.banned_users || []).map(Number);
            if (!banned.includes(Number(targetId))) banned.push(Number(targetId));
            settings.banned_users = banned;

            // Remove them from the group (kick)
            await ctx.wo_groupchatusers.update(
                { active: '0' },
                { where: { group_id: groupId, user_id: targetId } }
            );
            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group_' + groupId).emit('group_member_banned', { group_id: groupId, user_id: targetId, banned_by: userId });
            return res.json({ api_status: 200, message: 'User banned' });
        } catch (err) {
            console.error('[Groups/banMember]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── unban-member ─────────────────────────────────────────────────────────────

function unbanMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId)
                return res.json({ api_status: 400, error_message: 'group_id and user_id are required' });
            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Admin only' });

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            let settings = {};
            try { settings = group.settings ? (typeof group.settings === 'string' ? JSON.parse(group.settings) : group.settings) : {}; } catch (_) {}

            settings.banned_users = (settings.banned_users || []).map(Number).filter(id => id !== Number(targetId));
            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group_' + groupId).emit('group_member_unbanned', { group_id: groupId, user_id: targetId });
            return res.json({ api_status: 200, message: 'User unbanned' });
        } catch (err) {
            console.error('[Groups/unbanMember]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── mute-member ──────────────────────────────────────────────────────────────

function muteMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId)
                return res.json({ api_status: 400, error_message: 'group_id and user_id are required' });
            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Admin only' });

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            let settings = {};
            try { settings = group.settings ? (typeof group.settings === 'string' ? JSON.parse(group.settings) : group.settings) : {}; } catch (_) {}

            const muted = (settings.muted_members || []).map(Number);
            if (!muted.includes(Number(targetId))) muted.push(Number(targetId));
            settings.muted_members = muted;

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group_' + groupId).emit('group_member_muted', { group_id: groupId, user_id: targetId });
            return res.json({ api_status: 200, message: 'User muted in group' });
        } catch (err) {
            console.error('[Groups/muteMember]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── unmute-member ────────────────────────────────────────────────────────────

function unmuteMember(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const targetId = parseInt(req.body.user_id);

            if (!groupId || !targetId)
                return res.json({ api_status: 400, error_message: 'group_id and user_id are required' });
            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Admin only' });

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            let settings = {};
            try { settings = group.settings ? (typeof group.settings === 'string' ? JSON.parse(group.settings) : group.settings) : {}; } catch (_) {}

            settings.muted_members = (settings.muted_members || []).map(Number).filter(id => id !== Number(targetId));
            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group_' + groupId).emit('group_member_unmuted', { group_id: groupId, user_id: targetId });
            return res.json({ api_status: 200, message: 'User unmuted in group' });
        } catch (err) {
            console.error('[Groups/unmuteMember]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = {
    getMembers,
    addMember,
    removeMember,
    setRole,
    join,
    requestJoin,
    getJoinRequests,
    approveJoinRequest,
    rejectJoinRequest,
    banMember,
    unbanMember,
    muteMember,
    unmuteMember
};
