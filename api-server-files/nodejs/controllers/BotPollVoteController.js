/**
 * BotPollVoteController - User votes in a bot poll
 *
 * Event: bot_poll_vote
 * Data: { user_id (session), bot_id, poll_id, option_index }
 *
 * Handles user voting on a bot poll. Updates vote counts and
 * broadcasts updated results to all subscribed users.
 */

const { Op } = require("sequelize");

const BotPollVoteController = async (ctx, data, io, socket) => {
    const { bot_id, poll_id, option_index } = data;

    // Resolve user_id
    let user_id;
    if (data.user_id && ctx.userHashUserId[data.user_id]) {
        user_id = ctx.userHashUserId[data.user_id];
    } else if (socket.userId) {
        user_id = socket.userId;
    } else {
        console.log('[Bot] ❌ bot_poll_vote: cannot resolve user_id');
        return;
    }

    if (!bot_id || !poll_id || option_index === undefined) {
        console.log('[Bot] ❌ bot_poll_vote: bot_id, poll_id, and option_index required');
        return;
    }

    try {
        // Get poll
        const poll = await ctx.wo_bot_polls.findOne({
            where: { id: poll_id, bot_id: bot_id, is_closed: 0 }
        });

        if (!poll) {
            socket.emit('bot_error', { error: 'Poll not found or already closed' });
            return;
        }

        // Get the option
        const option = await ctx.wo_bot_poll_options.findOne({
            where: { poll_id: poll_id, option_index: option_index }
        });

        if (!option) {
            socket.emit('bot_error', { error: 'Invalid option index' });
            return;
        }

        // Check if user already voted (for single-answer polls)
        if (!poll.allows_multiple_answers) {
            const existingVote = await ctx.wo_bot_poll_votes.findOne({
                where: { poll_id: poll_id, user_id: user_id }
            });
            if (existingVote) {
                socket.emit('bot_error', { error: 'Already voted' });
                return;
            }
        }

        // Check duplicate vote on same option
        const duplicateVote = await ctx.wo_bot_poll_votes.findOne({
            where: { poll_id: poll_id, user_id: user_id, option_id: option.id }
        });
        if (duplicateVote) {
            socket.emit('bot_error', { error: 'Already voted for this option' });
            return;
        }

        // Record vote
        await ctx.wo_bot_poll_votes.create({
            poll_id: poll_id,
            option_id: option.id,
            user_id: user_id
        });

        // Update option voter count
        await ctx.wo_bot_poll_options.increment('voter_count', {
            where: { id: option.id }
        });

        // Update poll total voters
        await ctx.wo_bot_polls.increment('total_voters', {
            where: { id: poll_id }
        });

        // Get updated results
        const allOptions = await ctx.wo_bot_poll_options.findAll({
            where: { poll_id: poll_id },
            order: [['option_index', 'ASC']],
            raw: true
        });

        const updatedPoll = await ctx.wo_bot_polls.findOne({
            where: { id: poll_id },
            raw: true
        });

        const results = allOptions.map(opt => ({
            option_index: opt.option_index,
            option_text: opt.option_text,
            voter_count: opt.voter_count,
            percentage: updatedPoll.total_voters > 0
                ? opt.voter_count / updatedPoll.total_voters
                : 0
        }));

        const payload = {
            event: 'poll_update',
            bot_id: bot_id,
            poll_id: poll_id,
            total_voters: updatedPoll.total_voters,
            results: results,
            voter_user_id: poll.is_anonymous ? null : user_id,
            timestamp: Date.now()
        };

        // Broadcast poll update to all users in the chat
        const chatId = poll.chat_id;
        io.to(String(chatId)).emit('bot_poll_update', payload);
        io.to(`user_bot_${chatId}_${bot_id}`).emit('bot_poll_update', payload);

        // Notify the bot
        if (ctx.botSockets && ctx.botSockets.has(bot_id)) {
            ctx.botSockets.get(bot_id).emit('poll_answer', {
                poll_id: poll_id,
                user_id: user_id,
                option_index: option_index,
                total_voters: updatedPoll.total_voters
            });
        }

        // Send confirmation to voter
        socket.emit('bot_poll_voted', {
            poll_id: poll_id,
            option_index: option_index,
            results: results,
            total_voters: updatedPoll.total_voters
        });

        console.log(`[Bot] 🗳️ User ${user_id} voted in poll ${poll_id} (option ${option_index})`);

    } catch (err) {
        console.error(`[Bot] ❌ Poll vote error:`, err.message);
        socket.emit('bot_error', { error: 'Failed to register vote' });
    }
};

module.exports = { BotPollVoteController };
