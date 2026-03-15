'use strict';

/**
 * Scheduled Messages API
 *
 * Endpoints:
 *   GET  /api/node/scheduled/list           – get scheduled messages for a chat
 *   POST /api/node/scheduled/create         – create a scheduled message
 *   POST /api/node/scheduled/update/:id     – edit a scheduled message
 *   DELETE /api/node/scheduled/:id          – cancel/delete a scheduled message
 *   POST /api/node/scheduled/:id/send-now   – send immediately
 *
 * Background scheduler:
 *   Started via startScheduler(ctx, io) — checks every 60s for due messages.
 *   On fire, posts the message to the group/channel/DM via existing routes logic.
 *   Handles repeat_type: daily | weekly | monthly by rescheduling.
 */

'use strict';

const { Op } = require('sequelize');

// ─── auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body?.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid token' });
        req.userId = session.user_id;
        next();
    } catch (e) {
        res.status(500).json({ api_status: 500, error_message: e.message });
    }
}

// ─── helpers ─────────────────────────────────────────────────────────────────

function nextRepeatTime(scheduledAt, repeatType) {
    const now = Math.floor(Date.now() / 1000);
    let next = scheduledAt;
    switch (repeatType) {
        case 'daily':   next += 86400; break;
        case 'weekly':  next += 86400 * 7; break;
        case 'monthly': next += 86400 * 30; break;
        default:        return null; // no repeat
    }
    // If already in the past (e.g. server was down), advance until future
    while (next <= now) {
        switch (repeatType) {
            case 'daily':   next += 86400; break;
            case 'weekly':  next += 86400 * 7; break;
            case 'monthly': next += 86400 * 30; break;
        }
    }
    return next;
}

function formatItem(row) {
    return {
        id:             row.id,
        chat_id:        row.chat_id,
        chat_type:      row.chat_type,
        text:           row.text,
        media_url:      row.media_url,
        media_type:     row.media_type,
        scheduled_at:   row.scheduled_at,
        repeat_type:    row.repeat_type,
        is_pinned:      !!row.is_pinned,
        notify_members: !!row.notify_members,
        status:         row.status,
        created_at:     row.created_at,
    };
}

// ─── GET /api/node/scheduled/list ────────────────────────────────────────────

function listScheduled(ctx) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const chatId   = parseInt(req.query.chat_id   || req.body.chat_id);
            const chatType = req.query.chat_type || req.body.chat_type || 'group';
            const status   = req.query.status   || req.body.status   || 'pending';

            if (!chatId) return res.status(400).json({ api_status: 400, error_message: 'chat_id required' });

            const rows = await ctx.wm_scheduled_messages.findAll({
                where:  { chat_id: chatId, chat_type: chatType, user_id: userId, status },
                order:  [['scheduled_at', 'ASC']],
                limit:  100,
                raw:    true,
            });

            res.json({ api_status: 200, scheduled: rows.map(formatItem) });
        } catch (err) {
            console.error('[Scheduled/list]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/scheduled/create ─────────────────────────────────────────

function createScheduled(ctx) {
    return async (req, res) => {
        try {
            const userId       = req.userId;
            const chatId       = parseInt(req.body.chat_id);
            const chatType     = req.body.chat_type || 'group';
            const text         = (req.body.text || '').trim();
            const mediaUrl     = req.body.media_url   || null;
            const mediaType    = req.body.media_type  || null;
            const scheduledAt  = parseInt(req.body.scheduled_at);
            const repeatType   = req.body.repeat_type    || 'none';
            const isPinned     = req.body.is_pinned     === 'true' || req.body.is_pinned === true;
            const notifyMem    = req.body.notify_members !== 'false' && req.body.notify_members !== false;

            if (!chatId || !scheduledAt || (!text && !mediaUrl))
                return res.status(400).json({ api_status: 400, error_message: 'chat_id, scheduled_at, and text or media are required' });

            if (scheduledAt <= Math.floor(Date.now() / 1000))
                return res.status(400).json({ api_status: 400, error_message: 'scheduled_at must be in the future' });

            const row = await ctx.wm_scheduled_messages.create({
                user_id:        userId,
                chat_id:        chatId,
                chat_type:      chatType,
                text:           text || null,
                media_url:      mediaUrl,
                media_type:     mediaType,
                scheduled_at:   scheduledAt,
                repeat_type:    repeatType,
                is_pinned:      isPinned ? 1 : 0,
                notify_members: notifyMem ? 1 : 0,
                status:         'pending',
                created_at:     Math.floor(Date.now() / 1000),
            });

            res.json({ api_status: 200, scheduled: formatItem(row.toJSON()) });
        } catch (err) {
            console.error('[Scheduled/create]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/scheduled/update/:id ─────────────────────────────────────

function updateScheduled(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const id     = parseInt(req.params.id);

            const row = await ctx.wm_scheduled_messages.findOne({ where: { id, user_id: userId } });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Not found' });
            if (row.status !== 'pending') return res.status(400).json({ api_status: 400, error_message: 'Cannot edit sent/cancelled post' });

            const fields = {};
            if (req.body.text         !== undefined) fields.text           = req.body.text.trim() || null;
            if (req.body.scheduled_at !== undefined) fields.scheduled_at   = parseInt(req.body.scheduled_at);
            if (req.body.repeat_type  !== undefined) fields.repeat_type    = req.body.repeat_type;
            if (req.body.is_pinned    !== undefined) fields.is_pinned      = req.body.is_pinned === 'true' ? 1 : 0;
            if (req.body.notify_members !== undefined) fields.notify_members = req.body.notify_members !== 'false' ? 1 : 0;

            await row.update(fields);
            res.json({ api_status: 200, scheduled: formatItem(row.toJSON()) });
        } catch (err) {
            console.error('[Scheduled/update]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── DELETE /api/node/scheduled/:id ──────────────────────────────────────────

function deleteScheduled(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const id     = parseInt(req.params.id);

            const row = await ctx.wm_scheduled_messages.findOne({ where: { id, user_id: userId } });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Not found' });

            await row.update({ status: 'cancelled' });
            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Scheduled/delete]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/scheduled/:id/send-now ───────────────────────────────────

function sendNow(ctx, io) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const id     = parseInt(req.params.id);

            const row = await ctx.wm_scheduled_messages.findOne({ where: { id, user_id: userId, status: 'pending' } });
            if (!row) return res.status(404).json({ api_status: 404, error_message: 'Not found or already sent' });

            await fireScheduledMessage(ctx, io, row);
            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Scheduled/send-now]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── Scheduler logic ─────────────────────────────────────────────────────────

async function fireScheduledMessage(ctx, io, row) {
    const now = Math.floor(Date.now() / 1000);

    try {
        if (row.chat_type === 'group') {
            // Insert as group message
            await ctx.wo_groupchat.create({
                group_id:  row.chat_id,
                from_id:   row.user_id,
                text:      row.text || '',
                media:     row.media_url || '',
                type:      row.media_type || 'text',
                time:      now,
                seen:      0,
            });

            // Emit via Socket.IO
            if (io) {
                io.to(`group_${row.chat_id}`).emit('group_message', {
                    group_id:  row.chat_id,
                    from_id:   row.user_id,
                    text:      row.text,
                    type:      row.media_type || 'text',
                    time:      now,
                    scheduled: true,
                });
            }
        } else if (row.chat_type === 'channel') {
            // Insert as channel post
            await ctx.wo_messages.create({
                from_id:  row.user_id,
                to_id:    0,
                page_id:  row.chat_id,
                text:     row.text || '',
                media:    row.media_url || '',
                type:     'post',
                type_two: row.media_type || 'text',
                time:     now,
                seen:     0,
            });
        } else if (row.chat_type === 'dm') {
            // Direct message — store as regular message
            await ctx.wo_messages.create({
                from_id: row.user_id,
                to_id:   row.chat_id,
                page_id: 0,
                text:    row.text || '',
                media:   row.media_url || '',
                type_two: row.media_type || 'text',
                time:    now,
                seen:    0,
            });

            if (io) {
                io.to(String(row.chat_id)).emit('chat_message_received', {
                    from_id: row.user_id,
                    text:    row.text,
                    time:    now,
                    scheduled: true,
                });
            }
        }

        // Handle repeat or mark as sent
        const nextTime = nextRepeatTime(row.scheduled_at, row.repeat_type);
        if (nextTime && row.repeat_type !== 'none') {
            await row.update({ scheduled_at: nextTime, status: 'pending' });
        } else {
            await row.update({ status: 'sent' });
        }

        console.log(`[Scheduler] Fired scheduled message id=${row.id} type=${row.chat_type} chat=${row.chat_id}`);
    } catch (err) {
        console.error(`[Scheduler] Failed to fire id=${row.id}:`, err.message);
        await row.update({ status: 'failed' });
    }
}

/**
 * Start the background scheduler — call once on server startup.
 * Checks every 60 seconds for messages whose scheduled_at <= now.
 */
function startScheduler(ctx, io) {
    const tick = async () => {
        try {
            const now = Math.floor(Date.now() / 1000);
            const due = await ctx.wm_scheduled_messages.findAll({
                where: { status: 'pending', scheduled_at: { [Op.lte]: now } },
                raw:   false,
            });
            for (const row of due) {
                await fireScheduledMessage(ctx, io, row);
            }
        } catch (err) {
            console.error('[Scheduler] tick error:', err.message);
        }
    };

    setInterval(tick, 60 * 1000); // every 60s
    tick(); // run immediately on startup
    console.log('[Scheduled] Background scheduler started (60s interval)');
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerScheduledRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.get('/api/node/scheduled/list',          auth, listScheduled(ctx));
    app.post('/api/node/scheduled/list',         auth, listScheduled(ctx));
    app.post('/api/node/scheduled/create',       auth, createScheduled(ctx));
    app.post('/api/node/scheduled/update/:id',   auth, updateScheduled(ctx));
    app.delete('/api/node/scheduled/:id',        auth, deleteScheduled(ctx));
    app.post('/api/node/scheduled/:id/send-now', auth, sendNow(ctx, io));

    startScheduler(ctx, io);
    console.log('[Scheduled API] Endpoints registered');
}

module.exports = { registerScheduledRoutes };
