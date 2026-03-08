'use strict';

/**
 * Group Polls (user-initiated, not bot)
 *
 * Endpoints:
 *   POST /api/node/group/poll/create  – create a poll in a group chat
 *   POST /api/node/group/poll/get     – get poll details + vote counts
 *   POST /api/node/group/poll/vote    – vote on a poll option
 *   POST /api/node/group/poll/close   – close a poll (creator / admin only)
 *
 * Poll data is stored in wo_bot_polls (reused table, bot_id = 'user')
 * Options in wo_bot_poll_options, votes in wo_bot_poll_votes.
 */

const { Op } = require('sequelize');

async function isMember(ctx, groupId, userId) {
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1' },
        raw: true,
    });
    return !!m;
}

function creatorIdFromPoll(poll) {
    // bot_id is 'user_<id>' for user-created polls
    if (poll.bot_id && poll.bot_id.startsWith('user_')) {
        return parseInt(poll.bot_id.replace('user_', '')) || 0;
    }
    return 0;
}

async function isAdminOrCreator(ctx, groupId, userId, poll) {
    if (creatorIdFromPoll(poll) === Number(userId)) return true;
    const g = await ctx.wo_groupchat.findOne({ attributes: ['user_id'], where: { group_id: groupId }, raw: true });
    if (g && Number(g.user_id) === Number(userId)) return true;
    const m = await ctx.wo_groupchatusers.findOne({
        where: { group_id: groupId, user_id: userId, active: '1', role: { [Op.in]: ['admin', 'owner', 'moderator'] } },
        raw: true,
    });
    return !!m;
}

async function buildPollResponse(ctx, pollId, userId) {
    const poll = await ctx.wo_bot_polls.findOne({ where: { id: pollId }, raw: true });
    if (!poll) return null;

    const options = await ctx.wo_bot_poll_options.findAll({
        where: { poll_id: pollId },
        order: [['option_index', 'ASC']],
        raw: true,
    });

    const totalVotes = await ctx.wo_bot_poll_votes.count({ where: { poll_id: pollId } });

    const myVoteRows = await ctx.wo_bot_poll_votes.findAll({
        where: { poll_id: pollId, user_id: userId },
        raw: true,
    });
    const myOptionIds = myVoteRows.map(v => v.option_id);

    const optionsWithCounts = await Promise.all(options.map(async opt => {
        const voteCount = await ctx.wo_bot_poll_votes.count({ where: { poll_id: pollId, option_id: opt.id } });
        return {
            id:           opt.id,
            text:         opt.text,
            vote_count:   voteCount,
            percent:      totalVotes > 0 ? Math.round((voteCount / totalVotes) * 100) : 0,
            is_voted:     myOptionIds.includes(opt.id),
        };
    }));

    return {
        id:                      poll.id,
        question:                poll.question,
        poll_type:               poll.poll_type,
        is_anonymous:            !!poll.is_anonymous,
        allows_multiple_answers: !!poll.allows_multiple_answers,
        is_closed:               !!poll.is_closed,
        total_votes:             totalVotes,
        created_by:              creatorIdFromPoll(poll),
        options:                 optionsWithCounts,
    };
}

// ─── CREATE ───────────────────────────────────────────────────────────────────

function createPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId))
                return res.json({ api_status: 400, error_message: 'group_id is required' });

            const question = (req.body.question || '').trim();
            if (!question)
                return res.json({ api_status: 400, error_message: 'question is required' });

            const rawOptions = Array.isArray(req.body.options) ? req.body.options : [];
            const optionTexts = rawOptions.map(o => String(o).trim()).filter(Boolean);
            if (optionTexts.length < 2)
                return res.json({ api_status: 400, error_message: 'At least 2 options required' });
            if (optionTexts.length > 10)
                return res.json({ api_status: 400, error_message: 'Max 10 options allowed' });

            if (!await isMember(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            const poll = await ctx.wo_bot_polls.create({
                bot_id:                  `user_${userId}`,   // user_<id> encodes creator
                chat_id:                 `group_${groupId}`,
                question,
                poll_type:               req.body.poll_type === 'quiz' ? 'quiz' : 'regular',
                is_anonymous:            req.body.is_anonymous !== false && req.body.is_anonymous !== '0' ? 1 : 0,
                allows_multiple_answers: req.body.allows_multiple_answers ? 1 : 0,
                correct_option_id:       req.body.correct_option_id ? parseInt(req.body.correct_option_id) : null,
                explanation:             req.body.explanation || null,
                is_closed:               0,
            });

            for (let i = 0; i < optionTexts.length; i++) {
                await ctx.wo_bot_poll_options.create({
                    poll_id:      poll.id,
                    text:         optionTexts[i],
                    option_index: i,
                });
            }

            const pollData = await buildPollResponse(ctx, poll.id, userId);

            // Emit to group room
            io.to('group' + groupId).emit('group_poll_created', { group_id: groupId, poll: pollData });

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/group/poll/create]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET ──────────────────────────────────────────────────────────────────────

function getPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const pollId  = parseInt(req.body.poll_id);
            const groupId = parseInt(req.body.group_id);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });

            if (groupId && !await isMember(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            const pollData = await buildPollResponse(ctx, pollId, userId);
            if (!pollData)
                return res.json({ api_status: 404, error_message: 'Poll not found' });

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/group/poll/get]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── VOTE ─────────────────────────────────────────────────────────────────────

function votePoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const pollId   = parseInt(req.body.poll_id);
            const groupId  = parseInt(req.body.group_id);
            const optionIds = Array.isArray(req.body.option_ids)
                ? req.body.option_ids.map(id => parseInt(id)).filter(n => !isNaN(n))
                : (req.body.option_id ? [parseInt(req.body.option_id)] : []);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });
            if (optionIds.length === 0)
                return res.json({ api_status: 400, error_message: 'option_ids is required' });

            if (groupId && !await isMember(ctx, groupId, userId))
                return res.json({ api_status: 403, error_message: 'Not a member of this group' });

            const poll = await ctx.wo_bot_polls.findOne({ where: { id: pollId }, raw: true });
            if (!poll)
                return res.json({ api_status: 404, error_message: 'Poll not found' });
            if (poll.is_closed)
                return res.json({ api_status: 400, error_message: 'Poll is closed' });

            if (!poll.allows_multiple_answers && optionIds.length > 1)
                return res.json({ api_status: 400, error_message: 'This poll only allows one answer' });

            // Remove existing votes if not quiz
            if (poll.poll_type !== 'quiz') {
                await ctx.wo_bot_poll_votes.destroy({ where: { poll_id: pollId, user_id: userId } });
            } else {
                // Quiz: can only vote once
                const existing = await ctx.wo_bot_poll_votes.findOne({ where: { poll_id: pollId, user_id: userId }, raw: true });
                if (existing)
                    return res.json({ api_status: 400, error_message: 'Already voted in this quiz' });
            }

            for (const optId of optionIds) {
                await ctx.wo_bot_poll_votes.create({
                    poll_id:   pollId,
                    option_id: optId,
                    user_id:   userId,
                    // created_at auto-set by DB
                });
            }

            const pollData = await buildPollResponse(ctx, pollId, userId);

            if (groupId) {
                io.to('group' + groupId).emit('group_poll_updated', { group_id: groupId, poll: pollData });
            }

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/group/poll/vote]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── CLOSE ────────────────────────────────────────────────────────────────────

function closePoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const pollId  = parseInt(req.body.poll_id);
            const groupId = parseInt(req.body.group_id);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });

            const poll = await ctx.wo_bot_polls.findOne({ where: { id: pollId }, raw: true });
            if (!poll)
                return res.json({ api_status: 404, error_message: 'Poll not found' });

            if (!await isAdminOrCreator(ctx, groupId || 0, userId, poll))
                return res.json({ api_status: 403, error_message: 'Only the creator or admin can close this poll' });

            await ctx.wo_bot_polls.update({ is_closed: 1 }, { where: { id: pollId } });

            const pollData = await buildPollResponse(ctx, pollId, userId);

            if (groupId) {
                io.to('group' + groupId).emit('group_poll_closed', { group_id: groupId, poll: pollData });
            }

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/group/poll/close]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { createPoll, getPoll, votePoll, closePoll };
