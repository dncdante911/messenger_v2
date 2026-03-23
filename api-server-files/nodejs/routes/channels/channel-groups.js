'use strict';

/**
 * Channel Sub-Groups
 *
 * Allows attaching up to 5 private groups to a private premium channel.
 * Subscribers of the channel automatically receive membership in those groups.
 *
 * Rules:
 *   - Channel must be private (active = '0')
 *   - Channel owner must have an active premium subscription
 *   - Max 5 sub-groups per channel
 *   - Only channel owner/admin can create or detach sub-groups
 *   - Only channel subscribers can view sub-groups
 *   - On channel subscribe   → auto-join all attached groups
 *   - On channel unsubscribe → auto-leave all attached groups
 *
 * Endpoints (all POST, auth required):
 *   POST /api/node/channel/groups/list     – list sub-groups (subscribers only)
 *   POST /api/node/channel/groups/create   – create + attach new group (admin + premium)
 *   POST /api/node/channel/groups/attach   – attach existing group (admin + premium)
 *   POST /api/node/channel/groups/detach   – detach sub-group from channel (admin only)
 */

// ─── helpers ─────────────────────────────────────────────────────────────────

/** Check if channel has an active premium subscription. */
async function hasPremium(ctx, channelId) {
    const sub = await ctx.sequelize.query(
        `SELECT is_active, expires_at FROM wm_channel_subscriptions
         WHERE channel_id = ? LIMIT 1`,
        { replacements: [channelId], type: ctx.sequelize.QueryTypes.SELECT }
    );
    if (!sub.length) return false;
    const row = sub[0];
    if (!row.is_active) return false;
    if (row.expires_at && new Date(row.expires_at) <= new Date()) return false;
    return true;
}

/** Check if userId is owner or admin of the channel. */
async function isChannelAdmin(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({
        where: { page_id: channelId },
        attributes: ['user_id'],
        raw: true
    });
    if (!page) return false;
    // eslint-disable-next-line eqeqeq
    if (page.user_id == userId) return true;
    const cnt = await ctx.wo_pageadmins.count({ where: { page_id: channelId, user_id: userId } });
    return cnt > 0;
}

/** Check if userId is an active subscriber of the channel. */
async function isSubscriber(ctx, channelId, userId) {
    const row = await ctx.wo_pages_likes.findOne({
        where: { page_id: channelId, user_id: userId, active: '1' },
        raw: true
    });
    return !!row;
}

/** Add userId to a group as a member (idempotent). */
async function addUserToGroup(ctx, groupId, userId) {
    const existing = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId },
        raw: true
    });
    if (existing) {
        if (existing.active === '1') return; // already active member
        await ctx.wo_groupchatusers.update(
            { active: '1' },
            { where: { group_id: groupId, user_id: userId } }
        );
    } else {
        await ctx.wo_groupchatusers.create({
            user_id: userId,
            group_id: groupId,
            role: 'member',
            active: '1',
            last_seen: String(Math.floor(Date.now() / 1000))
        });
    }
}

/** Remove userId from a group (soft delete, idempotent). */
async function removeUserFromGroup(ctx, groupId, userId) {
    await ctx.wo_groupchatusers.update(
        { active: '0' },
        { where: { group_id: groupId, user_id: userId } }
    );
}

/** Return all group_ids attached to a channel. */
async function getAttachedGroupIds(ctx, channelId) {
    const rows = await ctx.sequelize.query(
        `SELECT group_id FROM wm_channel_groups WHERE channel_id = ? ORDER BY sort_order ASC`,
        { replacements: [channelId], type: ctx.sequelize.QueryTypes.SELECT }
    );
    return rows.map(r => r.group_id);
}

// ─── format a group row for API response ─────────────────────────────────────
async function formatSubGroup(ctx, groupId, requesterId) {
    const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
    if (!group) return null;
    const membersCount = await ctx.wo_groupchatusers.count({
        where: { group_id: groupId, active: '1' }
    });
    const membership = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: requesterId, active: '1' },
        raw: true
    });
    return {
        id:            group.group_id,
        name:          group.group_name,
        avatar:        group.avatar || null,
        description:   group.description || null,
        members_count: membersCount,
        is_member:     !!membership,
        created_time:  group.time
    };
}

// ─── list sub-groups ─────────────────────────────────────────────────────────
function listGroups(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const channelId = parseInt(req.body.channel_id);

            if (!channelId)
                return res.json({ api_status: 400, error_message: 'channel_id is required' });

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page)
                return res.json({ api_status: 404, error_message: 'Channel not found' });

            // Must be subscriber OR admin to view sub-groups
            const isAdmin = await isChannelAdmin(ctx, channelId, userId);
            const isSub   = await isSubscriber(ctx, channelId, userId);
            if (!isAdmin && !isSub)
                return res.json({ api_status: 403, error_message: 'Subscribe to the channel to view its groups' });

            const groupIds = await getAttachedGroupIds(ctx, channelId);
            const groups   = [];
            for (const gid of groupIds) {
                const formatted = await formatSubGroup(ctx, gid, userId);
                if (formatted) groups.push(formatted);
            }

            return res.json({ api_status: 200, groups, error_message: null });
        } catch (err) {
            console.error('[ChannelGroups/list]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── create a new private group and attach it to the channel ─────────────────
function createGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const groupName = (req.body.group_name || '').trim();

            if (!channelId || !groupName)
                return res.json({ api_status: 400, error_message: 'channel_id and group_name are required' });

            if (groupName.length > 255)
                return res.json({ api_status: 400, error_message: 'group_name is too long (max 255)' });

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page)
                return res.json({ api_status: 404, error_message: 'Channel not found' });

            // Must be admin
            if (!(await isChannelAdmin(ctx, channelId, userId)))
                return res.json({ api_status: 403, error_message: 'Only channel admins can create sub-groups' });

            // Channel must be private (active='0' means private/draft)
            if (page.active !== '0')
                return res.json({ api_status: 403, error_message: 'Sub-groups are only available for private channels' });

            // Channel owner must have premium subscription
            if (!(await hasPremium(ctx, channelId)))
                return res.json({ api_status: 403, error_message: 'An active channel premium subscription is required to create sub-groups' });

            // Max 5 sub-groups
            const [countRow] = await ctx.sequelize.query(
                `SELECT COUNT(*) AS cnt FROM wm_channel_groups WHERE channel_id = ?`,
                { replacements: [channelId], type: ctx.sequelize.QueryTypes.SELECT }
            );
            if (countRow.cnt >= 5)
                return res.json({ api_status: 400, error_message: 'Maximum 5 sub-groups per channel reached' });

            const description = (req.body.description || '').trim() || null;
            const now         = String(Math.floor(Date.now() / 1000));

            // Create the group as private
            const newGroup = await ctx.wo_groupchat.create({
                user_id:    userId,
                group_name: groupName,
                description,
                is_private: '1',
                avatar:     'upload/photos/d-group.jpg',
                time:       now,
                type:       'group'
            });

            const groupId = newGroup.group_id;

            // Owner becomes member with role 'owner'
            await ctx.wo_groupchatusers.create({
                user_id:   userId,
                group_id:  groupId,
                role:      'owner',
                active:    '1',
                last_seen: now
            });

            // Attach group to channel
            const [attachRow] = await ctx.sequelize.query(
                `SELECT COUNT(*) AS cnt FROM wm_channel_groups WHERE channel_id = ?`,
                { replacements: [channelId], type: ctx.sequelize.QueryTypes.SELECT }
            );
            const sortOrder = attachRow.cnt; // 0-based position
            await ctx.sequelize.query(
                `INSERT INTO wm_channel_groups (channel_id, group_id, sort_order) VALUES (?, ?, ?)`,
                { replacements: [channelId, groupId, sortOrder], type: ctx.sequelize.QueryTypes.INSERT }
            );

            // Auto-add all current channel subscribers to this new group
            const subscribers = await ctx.wo_pages_likes.findAll({
                where: { page_id: channelId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            for (const sub of subscribers) {
                if (sub.user_id !== userId) { // owner already added above
                    await addUserToGroup(ctx, groupId, sub.user_id);
                }
            }

            // Notify existing group sockets
            if (io) io.to(`channel_${channelId}`).emit('channel_subgroup_created', { channel_id: channelId, group_id: groupId, group_name: groupName });

            return res.json({
                api_status: 200,
                group_id:   groupId,
                group_name: groupName,
                error_message: null
            });
        } catch (err) {
            console.error('[ChannelGroups/create]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── attach an existing group to the channel ─────────────────────────────────
function attachGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const groupId   = parseInt(req.body.group_id);

            if (!channelId || !groupId)
                return res.json({ api_status: 400, error_message: 'channel_id and group_id are required' });

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page)
                return res.json({ api_status: 404, error_message: 'Channel not found' });

            if (!(await isChannelAdmin(ctx, channelId, userId)))
                return res.json({ api_status: 403, error_message: 'Only channel admins can attach sub-groups' });

            if (page.active !== '0')
                return res.json({ api_status: 403, error_message: 'Sub-groups are only available for private channels' });

            if (!(await hasPremium(ctx, channelId)))
                return res.json({ api_status: 403, error_message: 'An active channel premium subscription is required' });

            // Verify group exists and is private
            const group = await ctx.wo_groupchat.findOne({ where: { group_id: groupId }, raw: true });
            if (!group)
                return res.json({ api_status: 404, error_message: 'Group not found' });
            if (group.is_private !== '1')
                return res.json({ api_status: 400, error_message: 'Only private groups can be attached to a channel' });

            // Caller must be owner of the group
            const groupOwnership = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: userId, role: 'owner', active: '1' },
                raw: true
            });
            if (!groupOwnership)
                return res.json({ api_status: 403, error_message: 'You must be the owner of the group to attach it' });

            // Already attached?
            const [already] = await ctx.sequelize.query(
                `SELECT id FROM wm_channel_groups WHERE channel_id = ? AND group_id = ? LIMIT 1`,
                { replacements: [channelId, groupId], type: ctx.sequelize.QueryTypes.SELECT }
            );
            if (already)
                return res.json({ api_status: 200, error_message: 'Group already attached' });

            // Max 5
            const [countRow] = await ctx.sequelize.query(
                `SELECT COUNT(*) AS cnt FROM wm_channel_groups WHERE channel_id = ?`,
                { replacements: [channelId], type: ctx.sequelize.QueryTypes.SELECT }
            );
            if (countRow.cnt >= 5)
                return res.json({ api_status: 400, error_message: 'Maximum 5 sub-groups per channel reached' });

            await ctx.sequelize.query(
                `INSERT INTO wm_channel_groups (channel_id, group_id, sort_order) VALUES (?, ?, ?)`,
                { replacements: [channelId, groupId, countRow.cnt], type: ctx.sequelize.QueryTypes.INSERT }
            );

            // Auto-add all current channel subscribers to the group
            const subscribers = await ctx.wo_pages_likes.findAll({
                where: { page_id: channelId, active: '1' },
                attributes: ['user_id'],
                raw: true
            });
            for (const sub of subscribers) {
                await addUserToGroup(ctx, groupId, sub.user_id);
            }

            if (io) io.to(`channel_${channelId}`).emit('channel_subgroup_attached', { channel_id: channelId, group_id: groupId });

            return res.json({ api_status: 200, group_id: groupId, error_message: null });
        } catch (err) {
            console.error('[ChannelGroups/attach]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── detach a sub-group from the channel ─────────────────────────────────────
function detachGroup(ctx, io) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const groupId   = parseInt(req.body.group_id);

            if (!channelId || !groupId)
                return res.json({ api_status: 400, error_message: 'channel_id and group_id are required' });

            const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, raw: true });
            if (!page)
                return res.json({ api_status: 404, error_message: 'Channel not found' });

            if (!(await isChannelAdmin(ctx, channelId, userId)))
                return res.json({ api_status: 403, error_message: 'Only channel admins can detach sub-groups' });

            // Remove link
            await ctx.sequelize.query(
                `DELETE FROM wm_channel_groups WHERE channel_id = ? AND group_id = ?`,
                { replacements: [channelId, groupId], type: ctx.sequelize.QueryTypes.DELETE }
            );

            if (io) io.to(`channel_${channelId}`).emit('channel_subgroup_detached', { channel_id: channelId, group_id: groupId });

            return res.json({ api_status: 200, error_message: null });
        } catch (err) {
            console.error('[ChannelGroups/detach]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── exported sync helpers (used by subscriptions.js) ────────────────────────

/**
 * Called when a user subscribes to a channel.
 * Adds the user to all attached sub-groups.
 */
async function onChannelSubscribe(ctx, channelId, userId) {
    try {
        const groupIds = await getAttachedGroupIds(ctx, channelId);
        for (const gid of groupIds) {
            await addUserToGroup(ctx, gid, userId);
        }
    } catch (err) {
        console.error('[ChannelGroups/onSubscribe]', err.message);
    }
}

/**
 * Called when a user unsubscribes from a channel.
 * Removes the user from all attached sub-groups (unless they are the group owner).
 */
async function onChannelUnsubscribe(ctx, channelId, userId) {
    try {
        const groupIds = await getAttachedGroupIds(ctx, channelId);
        for (const gid of groupIds) {
            // Don't remove the group owner
            const ownership = await ctx.wo_groupchatusers.findOne({
                where: { group_id: gid, user_id: userId, role: 'owner' },
                raw: true
            });
            if (!ownership) {
                await removeUserFromGroup(ctx, gid, userId);
            }
        }
    } catch (err) {
        console.error('[ChannelGroups/onUnsubscribe]', err.message);
    }
}

module.exports = {
    listGroups,
    createGroup,
    attachGroup,
    detachGroup,
    onChannelSubscribe,
    onChannelUnsubscribe
};
