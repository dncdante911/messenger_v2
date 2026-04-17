'use strict';

/**
 * Group Topics (Supergroup threads / subgroups)
 *
 * Endpoints:
 *   POST /api/node/group/topics/list    – list all topics for a group
 *   POST /api/node/group/topics/create  – create a new topic (admin only)
 *   POST /api/node/group/topics/update  – rename / pin / archive a topic (admin only)
 *   POST /api/node/group/topics/delete  – delete a topic (admin only)
 *
 * The wo_messages.topic_id FK already exists in the DB/Sequelize model.
 * Topics are stored in a JSON column in Wo_GroupChat.settings under "topics" key.
 * Each topic: { id, name, created_at, created_by, is_pinned, is_archived }
 */

const { Op } = require('sequelize');

function parseSettings(group) {
    if (!group?.settings) return {};
    try {
        return typeof group.settings === 'string'
            ? JSON.parse(group.settings)
            : group.settings;
    } catch { return {}; }
}

async function isMember(ctx, groupId, userId) {
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1' },
        raw: true,
    });
    return !!m;
}

async function isAdmin(ctx, groupId, userId) {
    const g = await ctx.wo_groupchat.findOne({ attributes: ['user_id'], where: { group_id: groupId }, raw: true });
    if (g && Number(g.user_id) === Number(userId)) return true;
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1', role: { [Op.in]: ['admin', 'owner', 'moderator'] } },
        raw: true,
    });
    return !!m;
}

// ─── LIST ─────────────────────────────────────────────────────────────────────

function listTopics(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            if (!await isMember(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            const group = await ctx.wo_groupchat.findOne({
                attributes: ['group_id', 'settings'],
                where: { group_id: groupId },
                raw: true,
            });
            if (!group)
                return res.json({ api_status: 404, error_message: 'Group not found' });

            const settings = parseSettings(group);
            const topics   = Array.isArray(settings.topics) ? settings.topics : [];

            // Attach unread count per topic for this user
            const result = await Promise.all(topics.map(async t => {
                const msgCount = await ctx.wo_messages.count({
                    where: { group_id: groupId, topic_id: t.id },
                }).catch(() => 0);
                return { ...t, message_count: msgCount };
            }));

            return res.json({ api_status: 200, topics: result });
        } catch (err) {
            console.error('[Node/group/topics/list]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── CREATE ───────────────────────────────────────────────────────────────────

function createTopic(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const name    = (req.body.name || '').trim().substring(0, 128);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });
            if (!name)
                return res.json({ api_status: 400, error_message: 'name is required' });

            if (!await isAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can create topics' });

            const group = await ctx.wo_groupchat.findOne({
                attributes: ['group_id', 'settings'],
                where: { group_id: groupId },
                raw: true,
            });
            if (!group)
                return res.json({ api_status: 404, error_message: 'Group not found' });

            const settings = parseSettings(group);
            if (!Array.isArray(settings.topics)) settings.topics = [];

            if (settings.topics.length >= 30)
                return res.json({ api_status: 400, error_message: 'Maximum 30 topics per group' });

            const description = (req.body.description || '').trim() || null;
            const color       = (req.body.color || '#0088CC').trim();
            const isPrivate   = req.body.is_private === 'true' || req.body.is_private === '1'
                             || req.body.is_private === true   || req.body.is_private === 1;

            const newTopic = {
                id:          Date.now(),
                name,
                description,
                color,
                is_private:  isPrivate,
                created_at:  Math.floor(Date.now() / 1000),
                created_by:  userId,
                is_pinned:   false,
                is_archived: false,
            };
            settings.topics.push(newTopic);

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group' + groupId).emit('group_topic_created', { group_id: groupId, topic: newTopic });

            return res.json({ api_status: 200, topic: newTopic });
        } catch (err) {
            console.error('[Node/group/topics/create]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── UPDATE ───────────────────────────────────────────────────────────────────

function updateTopic(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const topicId  = Number(req.body.topic_id);

            if (!groupId || isNaN(groupId)) return res.json({ api_status: 400, error_message: 'group_id is required' });
            if (!topicId)                   return res.json({ api_status: 400, error_message: 'topic_id is required' });

            if (!await isAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can update topics' });

            const group = await ctx.wo_groupchat.findOne({
                attributes: ['group_id', 'settings'],
                where: { group_id: groupId },
                raw: true,
            });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            const settings = parseSettings(group);
            if (!Array.isArray(settings.topics)) return res.json({ api_status: 404, error_message: 'Topic not found' });

            const idx = settings.topics.findIndex(t => Number(t.id) === topicId);
            if (idx === -1) return res.json({ api_status: 404, error_message: 'Topic not found' });

            if (req.body.name        !== undefined) settings.topics[idx].name        = String(req.body.name).trim().substring(0, 128);
            if (req.body.description !== undefined) settings.topics[idx].description = String(req.body.description).trim() || null;
            if (req.body.color       !== undefined) settings.topics[idx].color       = String(req.body.color).trim();
            if (req.body.is_private  !== undefined) settings.topics[idx].is_private  = req.body.is_private === 'true' || req.body.is_private === '1' || !!req.body.is_private;
            if (req.body.is_pinned   !== undefined) settings.topics[idx].is_pinned   = !!req.body.is_pinned;
            if (req.body.is_archived !== undefined) settings.topics[idx].is_archived = !!req.body.is_archived;

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group' + groupId).emit('group_topic_updated', { group_id: groupId, topic: settings.topics[idx] });

            return res.json({ api_status: 200, topic: settings.topics[idx] });
        } catch (err) {
            console.error('[Node/group/topics/update]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── DELETE ───────────────────────────────────────────────────────────────────

function deleteTopic(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);
            const topicId = Number(req.body.topic_id);

            if (!groupId || isNaN(groupId)) return res.json({ api_status: 400, error_message: 'group_id is required' });
            if (!topicId)                   return res.json({ api_status: 400, error_message: 'topic_id is required' });

            if (!await isAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Only admins can delete topics' });

            const group = await ctx.wo_groupchat.findOne({
                attributes: ['group_id', 'settings'],
                where: { group_id: groupId },
                raw: true,
            });
            if (!group) return res.json({ api_status: 404, error_message: 'Group not found' });

            const settings = parseSettings(group);
            if (!Array.isArray(settings.topics)) return res.json({ api_status: 200, message: 'Topic not found (no-op)' });

            settings.topics = settings.topics.filter(t => Number(t.id) !== topicId);

            await ctx.wo_groupchat.update(
                { settings: JSON.stringify(settings) },
                { where: { group_id: groupId } }
            );

            io.to('group' + groupId).emit('group_topic_deleted', { group_id: groupId, topic_id: topicId });

            return res.json({ api_status: 200, message: 'Topic deleted' });
        } catch (err) {
            console.error('[Node/group/topics/delete]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { listTopics, createTopic, updateTopic, deleteTopic };
