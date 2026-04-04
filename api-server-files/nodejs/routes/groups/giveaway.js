'use strict';

/**
 * Group Giveaway
 *
 * POST /api/node/group/giveaway/run
 *   Randomly selects winners from active group members.
 *   Eligibility filter: min_messages sent within the period_days window.
 *
 * Requires: admin / owner role.
 */

const { Op, Sequelize } = require('sequelize');
const funcs             = require('../../functions/functions');

// ─── helpers ────────────────────────────────────────────────────────────────

async function isGroupAdmin(ctx, groupId, userId) {
    const m = await ctx.wo_groupchatusers.findOne({
        attributes: ['role'],
        where: {
            group_id: groupId,
            user_id:  userId,
            active:   '1',
            role:     { [Op.in]: ['owner', 'admin', 'moderator'] },
        },
        raw: true,
    });
    return !!m;
}

// ─── runGiveaway ─────────────────────────────────────────────────────────────

function runGiveaway(ctx) {
    return async (req, res) => {
        try {
            const userId       = req.userId;
            const groupId      = parseInt(req.body.group_id);
            const winnersCount = Math.min(parseInt(req.body.winners_count) || 1, 20);
            const minMessages  = parseInt(req.body.min_messages)  || 0;
            const periodDays   = Math.min(parseInt(req.body.period_days) || 30, 365);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id required' });

            if (!await isGroupAdmin(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Not an admin' });

            const now   = Math.floor(Date.now() / 1000);
            const since = now - periodDays * 86400;

            // All active members
            const members = await ctx.wo_groupchatusers.findAll({
                where:      { group_id: groupId, active: '1' },
                attributes: ['user_id'],
                raw:        true,
            });

            if (!members.length)
                return res.json({ api_status: 200, winners: [], total_participants: 0, period_days: periodDays });

            const memberIds = members.map(m => m.user_id);

            let eligibleIds = memberIds;

            // Optional filter: at least min_messages messages sent in the period
            if (minMessages > 0) {
                const msgCounts = await ctx.wo_messages.findAll({
                    where: {
                        group_id: groupId,
                        from_id:  { [Op.in]: memberIds },
                        time:     { [Op.gte]: since },
                        page_id:  0,
                    },
                    attributes: [
                        'from_id',
                        [Sequelize.fn('COUNT', Sequelize.col('id')), 'cnt'],
                    ],
                    group: ['from_id'],
                    raw:   true,
                });

                const countMap = {};
                for (const r of msgCounts) countMap[r.from_id] = parseInt(r.cnt);

                eligibleIds = memberIds.filter(uid => (countMap[uid] || 0) >= minMessages);
            }

            if (!eligibleIds.length)
                return res.json({ api_status: 200, winners: [], total_participants: 0, period_days: periodDays });

            // Fisher-Yates shuffle
            const shuffled = [...eligibleIds];
            for (let i = shuffled.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
            }
            const winnerIds = shuffled.slice(0, Math.min(winnersCount, shuffled.length));

            const users = await ctx.wo_users.findAll({
                where:      { user_id: { [Op.in]: winnerIds } },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                raw:        true,
            });
            const userMap = {};
            for (const u of users) userMap[u.user_id] = u;

            const winners = await Promise.all(winnerIds.map(async (uid, idx) => {
                const u = userMap[uid] || {};
                const avatarUrl = u.avatar ? await funcs.Wo_GetMedia(ctx, u.avatar) : null;
                return {
                    place:      idx + 1,
                    user_id:    uid,
                    username:   u.username || null,
                    name:       [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username || null,
                    avatar_url: avatarUrl,
                };
            }));

            return res.json({
                api_status:         200,
                winners,
                total_participants: eligibleIds.length,
                period_days:        periodDays,
            });

        } catch (err) {
            console.error('[Groups/runGiveaway]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { runGiveaway };
