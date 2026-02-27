/**
 * Groups — Management (CRUD, list, search, details)
 *
 * REST Endpoints:
 *   POST /api/node/group/list          – list groups for current user
 *   POST /api/node/group/details       – get group details by id
 *   POST /api/node/group/create        – create a new group
 *   POST /api/node/group/update        – update name / description
 *   POST /api/node/group/delete        – delete group (owner only)
 *   POST /api/node/group/leave         – leave group
 *   POST /api/node/group/search        – search groups by name
 */

'use strict';

const { Op } = require('sequelize');
const funcs  = require('../../functions/functions');

// ─── helpers ─────────────────────────────────────────────────────────────────

async function formatGroup(ctx, group, userId) {
    const membersCount = await ctx.wo_groupchatusers.count({
        where: { group_id: group.group_id, active: '1' }
    });

    const membership = userId ? await ctx.wo_groupchatusers.findOne({
        where: { group_id: group.group_id, user_id: userId, active: '1' },
        raw: true
    }) : null;

    const isAdmin = membership
        ? membership.role === 'owner' || membership.role === 'admin'
        : false;

    const avatarUrl = group.avatar ? await funcs.Wo_GetMedia(ctx, group.avatar) : '';

    // Get pinned message if any
    let pinnedMessage = null;
    let settings = null;
    if (group.settings) {
        try {
            settings = typeof group.settings === 'string'
                ? JSON.parse(group.settings)
                : group.settings;
            if (settings && settings.pinned_message_id) {
                const pm = await ctx.wo_messages.findOne({
                    where: { id: settings.pinned_message_id },
                    raw: true
                });
                if (pm) {
                    pinnedMessage = { id: pm.id, text: pm.text, time: pm.time };
                }
            }
        } catch (e) { settings = null; }
    }

    // Calculate unread message count based on user's last_seen timestamp
    const lastSeen = membership ? (parseInt(membership.last_seen) || 0) : 0;
    const unreadCount = lastSeen > 0
        ? await ctx.wo_messages.count({
            where: { group_id: group.group_id, page_id: 0, time: { [Op.gt]: lastSeen } }
        })
        : 0;

    // Get owner info
    const owner = await ctx.wo_users.findOne({
        where: { user_id: group.user_id },
        attributes: ['user_id', 'username', 'first_name', 'last_name'],
        raw: true
    });
    const adminName = owner
        ? (owner.first_name ? owner.first_name + ' ' + (owner.last_name || '') : owner.username)
        : '';

    return {
        id: group.group_id,
        name: group.group_name,
        avatar: avatarUrl,
        description: group.description || null,
        members_count: membersCount,
        admin_id: group.user_id,
        admin_name: adminName.trim(),
        is_private: group.is_private === '1',
        is_admin: isAdmin,
        is_moderator: membership ? membership.role === 'moderator' : false,
        is_member: !!membership,
        is_muted: settings?.muted_users?.includes(userId) || false,
        unread_count: unreadCount,
        created_time: parseInt(group.time) || 0,
        pinned_message_id: settings?.pinned_message_id || null,
        pinned_message: pinnedMessage,
        settings: settings || null,
        type: group.type || 'group'
    };
}

// ─── list ─────────────────────────────────────────────────────────────────────

function list(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const limit  = parseInt(req.body.limit) || 50;
            const offset = parseInt(req.body.offset) || 0;

            // Get group IDs the user is a member of
            const memberships = await ctx.wo_groupchatusers.findAll({
                where: { user_id: userId, active: '1' },
                attributes: ['group_id'],
                raw: true
            });

            const groupIds = memberships.map(m => m.group_id);

            if (groupIds.length === 0) {
                return res.json({ api_status: 200, groups: [], total_count: 0, error_code: null, error_message: null });
            }

            const groups = await ctx.wo_groupchat.findAll({
                where: { group_id: { [Op.in]: groupIds } },
                order: [['time', 'DESC']],
                limit,
                offset,
                raw: true
            });

            const formatted = [];
            for (const g of groups) {
                formatted.push(await formatGroup(ctx, g, userId));
            }

            return res.json({
                api_status: 200,
                groups: formatted,
                total_count: groupIds.length,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/list]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── details ─────────────────────────────────────────────────────────────────

function details(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({
                where: { group_id: groupId },
                raw: true
            });

            if (!group) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });
            }

            // Check access for private groups
            const membership = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId, active: '1' },
                raw: true
            });

            if (group.is_private === '1' && !membership) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'This is a private group' });
            }

            const formatted = await formatGroup(ctx, group, userId);

            // Append members list
            const members = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                raw: true
            });

            const memberUserIds = members.map(m => m.user_id);
            const users = await ctx.wo_users.findAll({
                where: { user_id: { [Op.in]: memberUserIds } },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'lastseen'],
                raw: true
            });

            const membersFormatted = [];
            for (const m of members) {
                const u = users.find(x => x.user_id === m.user_id);
                if (!u) continue;
                const avatarUrl = u.avatar ? await funcs.Wo_GetMedia(ctx, u.avatar) : '';
                const name = u.first_name
                    ? (u.first_name + ' ' + (u.last_name || '')).trim()
                    : u.username;
                membersFormatted.push({
                    id: m.user_id,
                    user_id: m.user_id,
                    username: u.username || '',
                    name,
                    avatar: avatarUrl,
                    role: m.role,
                    last_seen: u.lastseen ? String(u.lastseen) : null
                });
            }

            return res.json({
                api_status: 200,
                group: { ...formatted, members: membersFormatted },
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/details]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── create ───────────────────────────────────────────────────────────────────

function create(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const name        = (req.body.name || req.body.group_name || '').trim();
            const description = req.body.description || null;
            const isPrivate   = req.body.is_private === '1' || req.body.is_private === true;
            const memberIds   = req.body.member_ids
                ? String(req.body.member_ids).split(',').map(Number).filter(Boolean)
                : [];

            if (!name) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Group name is required' });
            }
            if (name.length > 255) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Group name is too long' });
            }

            const newGroup = await ctx.wo_groupchat.create({
                user_id: userId,
                group_name: name,
                description,
                is_private: isPrivate ? '1' : '0',
                avatar: 'upload/photos/d-group.jpg',
                time: Math.floor(Date.now() / 1000).toString(),
                type: 'group'
            });

            // Add creator as owner
            await ctx.wo_groupchatusers.create({
                user_id: userId,
                group_id: newGroup.group_id,
                role: 'owner',
                active: '1',
                last_seen: Math.floor(Date.now() / 1000).toString()
            });

            // Add initial members
            const uniqueIds = [...new Set(memberIds)].filter(id => id !== userId);
            for (const memberId of uniqueIds) {
                await ctx.wo_groupchatusers.create({
                    user_id: memberId,
                    group_id: newGroup.group_id,
                    role: 'member',
                    active: '1',
                    last_seen: '0'
                }).catch(() => {});

                // Notify member via Socket.IO if online
                const socketId = ctx.userIdSocket ? ctx.userIdSocket[memberId] : null;
                if (socketId) {
                    io.to(socketId).emit('group_created', { group_id: newGroup.group_id });
                }
            }

            const formatted = await formatGroup(ctx, { ...newGroup.dataValues }, userId);

            console.log(`[Groups] Group ${newGroup.group_id} created by ${userId}`);

            return res.json({
                api_status: 200,
                group_id: newGroup.group_id,
                group: formatted,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/create]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── update ───────────────────────────────────────────────────────────────────

function update(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const groupId     = parseInt(req.body.group_id);
            const name        = (req.body.name || req.body.group_name || '').trim();
            const description = req.body.description;

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });
            }

            // Only owner or admin can update
            const membership = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId, active: '1' },
                raw: true
            });
            if (!membership || !['owner', 'admin'].includes(membership.role)) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only admins can update the group' });
            }

            const updates = {};
            if (name) updates.group_name = name;
            if (description !== undefined) updates.description = description;

            await ctx.wo_groupchat.update(updates, { where: { group_id: groupId } });

            const updatedGroup = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            const formatted = await formatGroup(ctx, updatedGroup, userId);

            // Notify group members via Socket.IO
            const members = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            members.forEach(m => {
                const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                if (s) io.to(s).emit('group_updated', { group_id: groupId, name: formatted.name });
            });

            console.log(`[Groups] Group ${groupId} updated by ${userId}`);

            return res.json({
                api_status: 200,
                group: formatted,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/update]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── delete ───────────────────────────────────────────────────────────────────

function deleteGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });
            }

            // eslint-disable-next-line eqeqeq
            if (group.user_id != userId) {
                return res.json({ api_status: 403, error_code: 403, error_message: 'Only the group owner can delete it' });
            }

            // Notify all members before deleting
            const members = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId },
                attributes: ['user_id'],
                raw: true
            });
            members.forEach(m => {
                const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                if (s) io.to(s).emit('group_deleted', { group_id: groupId });
            });

            // Delete members, messages, and group
            await ctx.wo_groupchatusers.destroy({ where: { group_id: groupId } });
            await ctx.wo_messages.destroy({ where: { group_id: groupId } });
            await ctx.wo_groupchat.destroy({ where: { group_id: groupId } });

            console.log(`[Groups] Group ${groupId} deleted by owner ${userId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/delete]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── leave ────────────────────────────────────────────────────────────────────

function leave(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'group_id is required' });
            }

            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group) {
                return res.json({ api_status: 404, error_code: 404, error_message: 'Group not found' });
            }

            // eslint-disable-next-line eqeqeq
            if (group.user_id == userId) {
                return res.json({ api_status: 400, error_code: 400, error_message: 'Owner cannot leave the group. Transfer ownership or delete it.' });
            }

            await ctx.wo_groupchatusers.update(
                { active: '0' },
                { where: { group_id: groupId, user_id: userId } }
            );

            // Notify remaining members
            const members = await ctx.wo_groupchatusers.findAll({
                where: { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            members.forEach(m => {
                const s = ctx.userIdSocket ? ctx.userIdSocket[m.user_id] : null;
                if (s) io.to(s).emit('group_member_left', { group_id: groupId, user_id: userId });
            });

            console.log(`[Groups] User ${userId} left group ${groupId}`);

            return res.json({ api_status: 200, error_code: null, error_message: null });
        } catch (err) {
            console.error('[Groups/leave]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

// ─── search ───────────────────────────────────────────────────────────────────

function search(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim();
            const limit  = parseInt(req.body.limit) || 20;

            if (!query || query.length < 2) {
                return res.json({ api_status: 200, groups: [], error_code: null, error_message: null });
            }

            const groups = await ctx.wo_groupchat.findAll({
                where: {
                    group_name: { [Op.like]: `%${query}%` },
                    is_private: '0'
                },
                limit,
                raw: true
            });

            const formatted = [];
            for (const g of groups) {
                formatted.push(await formatGroup(ctx, g, userId));
            }

            return res.json({
                api_status: 200,
                groups: formatted,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Groups/search]', err.message);
            return res.json({ api_status: 500, error_code: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { list, details, create, update, deleteGroup, leave, search, formatGroup };
