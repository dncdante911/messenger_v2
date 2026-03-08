'use strict';

/**
 * Channel Polls (user-initiated, independent from group polls)
 *
 * Endpoints:
 *   POST /api/node/channel/poll/create  – create a poll in a channel post
 *   POST /api/node/channel/poll/get     – get poll with vote counts
 *   POST /api/node/channel/poll/vote    – vote on an option
 *   POST /api/node/channel/poll/close   – close a poll (admin only)
 *
 * Channel polls are attached to a page (channel) and stored separately from
 * group polls. Uses same wo_bot_polls / wo_bot_poll_options / wo_bot_poll_votes
 * tables, with bot_id='channel' and chat_id='channel_<page_id>'.
 */

const { Op } = require('sequelize');

async function isChannelAdmin(ctx, pageId, userId) {
    const page = await ctx.wo_page.findOne({
        attributes: ['user_id'],
        where: { id: pageId },
        raw: true,
    }).catch(() => null);
    if (page && Number(page.user_id) === Number(userId)) return true;

    // Check page managers
    const mgr = await ctx.wo_page_managers?.findOne?.({
        where: { page_id: pageId, user_id: userId, active: 1 },
        raw: true,
    }).catch(() => null);
    return !!mgr;
}

async function isSubscriber(ctx, pageId, userId) {
    if (await isChannelAdmin(ctx, pageId, userId)) return true;
    const sub = await ctx.wo_page_likes?.findOne?.({
        where: { page_id: pageId, user_id: userId },
        raw: true,
    }).catch(() => null);
    return !!sub;
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
            id:         opt.id,
            text:       opt.text,
            vote_count: voteCount,
            percent:    totalVotes > 0 ? Math.round((voteCount / totalVotes) * 100) : 0,
            is_voted:   myOptionIds.includes(opt.id),
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
        options:                 optionsWithCounts,
    };
}

// ─── CREATE ───────────────────────────────────────────────────────────────────

function createChannelPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const pageId = parseInt(req.body.page_id);

            if (!pageId || isNaN(pageId))
                return res.json({ api_status: 400, error_message: 'page_id is required' });

            if (!await isChannelAdmin(ctx, pageId, userId))
                return res.json({ api_status: 403, error_message: 'Only channel admins can create polls' });

            const question = (req.body.question || '').trim();
            if (!question)
                return res.json({ api_status: 400, error_message: 'question is required' });

            const rawOptions  = Array.isArray(req.body.options) ? req.body.options : [];
            const optionTexts = rawOptions.map(o => String(o).trim()).filter(Boolean);
            if (optionTexts.length < 2)
                return res.json({ api_status: 400, error_message: 'At least 2 options required' });
            if (optionTexts.length > 10)
                return res.json({ api_status: 400, error_message: 'Max 10 options allowed' });

            const poll = await ctx.wo_bot_polls.create({
                bot_id:                  `channel_${userId}`,   // channel_<id> encodes creator
                chat_id:                 `channel_${pageId}`,
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

            // Emit to channel subscribers via page room
            io.to('page' + pageId).emit('channel_poll_created', { page_id: pageId, poll: pollData });

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/channel/poll/create]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET ──────────────────────────────────────────────────────────────────────

function getChannelPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const pollId = parseInt(req.body.poll_id);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });

            const pollData = await buildPollResponse(ctx, pollId, userId);
            if (!pollData)
                return res.json({ api_status: 404, error_message: 'Poll not found' });

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/channel/poll/get]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── VOTE ─────────────────────────────────────────────────────────────────────

function voteChannelPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const pollId   = parseInt(req.body.poll_id);
            const pageId   = parseInt(req.body.page_id);
            const optionIds = Array.isArray(req.body.option_ids)
                ? req.body.option_ids.map(id => parseInt(id)).filter(n => !isNaN(n))
                : (req.body.option_id ? [parseInt(req.body.option_id)] : []);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });
            if (optionIds.length === 0)
                return res.json({ api_status: 400, error_message: 'option_ids is required' });

            const poll = await ctx.wo_bot_polls.findOne({ where: { id: pollId }, raw: true });
            if (!poll)
                return res.json({ api_status: 404, error_message: 'Poll not found' });
            if (poll.is_closed)
                return res.json({ api_status: 400, error_message: 'Poll is closed' });
            if (!poll.allows_multiple_answers && optionIds.length > 1)
                return res.json({ api_status: 400, error_message: 'This poll only allows one answer' });

            if (poll.poll_type !== 'quiz') {
                await ctx.wo_bot_poll_votes.destroy({ where: { poll_id: pollId, user_id: userId } });
            } else {
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

            if (pageId) {
                io.to('page' + pageId).emit('channel_poll_updated', { page_id: pageId, poll: pollData });
            }

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/channel/poll/vote]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── CLOSE ────────────────────────────────────────────────────────────────────

function closeChannelPoll(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const pollId = parseInt(req.body.poll_id);
            const pageId = parseInt(req.body.page_id);

            if (!pollId || isNaN(pollId))
                return res.json({ api_status: 400, error_message: 'poll_id is required' });
            if (!pageId || isNaN(pageId))
                return res.json({ api_status: 400, error_message: 'page_id is required' });

            if (!await isChannelAdmin(ctx, pageId, userId))
                return res.json({ api_status: 403, error_message: 'Only channel admins can close polls' });

            await ctx.wo_bot_polls.update({ is_closed: 1 }, { where: { id: pollId } });

            const pollData = await buildPollResponse(ctx, pollId, userId);
            io.to('page' + pageId).emit('channel_poll_closed', { page_id: pageId, poll: pollData });

            return res.json({ api_status: 200, poll: pollData });
        } catch (err) {
            console.error('[Node/channel/poll/close]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { createChannelPoll, getChannelPoll, voteChannelPoll, closeChannelPoll };
