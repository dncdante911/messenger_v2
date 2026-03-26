/**
 * Secret Messages — Self-Destruct Timer & Server-Side Cleanup
 *
 * HTTP endpoints:
 *   POST /api/node/chat/secret/set-timer    — set/clear destroy timer for a chat
 *   POST /api/node/chat/secret/cleanup      — manual expired-message cleanup (called on chat open)
 *   GET  /api/node/chat/secret/timer/:userId — get current timer setting for a chat
 *
 * Logic:
 *   - remove_at is stored in Wo_Messages as a Unix timestamp (seconds)
 *   - On send, client passes remove_at = now + timerSeconds  (or 0 = no timer)
 *   - Server deletes messages where remove_at > 0 AND remove_at <= now
 *   - Server sends `secret:messages_expired` via Socket.IO to both participants when
 *     auto-deleting in the global sweeper, so the Android removes them from the UI
 *   - Timer setting is persisted in wm_chat_timers so it survives app restarts
 *   - Global sweeper runs every 60 s to catch messages from inactive chats
 *
 * SQL migration (run once):
 *   ALTER TABLE Wo_Messages ADD COLUMN IF NOT EXISTS remove_at INT NOT NULL DEFAULT 0;
 *   ALTER TABLE Wo_Messages ADD INDEX idx_remove_at (remove_at);
 *
 *   CREATE TABLE IF NOT EXISTS wm_chat_timers (
 *     id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 *     user_a         INT NOT NULL,
 *     user_b         INT NOT NULL,
 *     timer_seconds  INT NOT NULL DEFAULT 0,
 *     set_by         INT NOT NULL,
 *     updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *     UNIQUE KEY uq_pair (user_a, user_b)
 *   );
 */

'use strict';

const { Op } = require('sequelize');

// ── Canonical pair key (always smaller id first) ─────────────────────────────
function pairKey(a, b) {
    return a < b ? { user_a: a, user_b: b } : { user_a: b, user_b: a };
}

// ── Cleanup expired messages for a specific pair ──────────────────────────────
/**
 * Deletes expired secret messages between two users.
 * Returns array of deleted message IDs so callers can notify clients.
 */
async function cleanupExpiredMessages(ctx, fromId, toId) {
    const nowUnix = Math.floor(Date.now() / 1000);
    try {
        if (!ctx || !ctx.wo_messages) return [];
        // Fetch IDs first so we can emit them via Socket.IO
        const expired = await ctx.wo_messages.findAll({
            where: {
                remove_at: { [Op.gt]: 0, [Op.lte]: nowUnix },
                [Op.or]: [
                    { from_id: fromId, to_id: toId },
                    { from_id: toId,   to_id: fromId },
                ],
            },
            attributes: ['id'],
            raw: true,
        });
        if (!expired.length) return [];

        const ids = expired.map(m => m.id);
        await ctx.wo_messages.destroy({ where: { id: { [Op.in]: ids } } });
        return ids;
    } catch (err) {
        console.error('[SecretCleanup] cleanup error:', err.message);
        return [];
    }
}

// ── Global sweeper — called from startSecretSweeper() ────────────────────────
/**
 * Finds ALL expired messages globally, deletes them, then notifies both
 * participants via Socket.IO `secret:messages_expired` so the Android
 * removes the messages from the UI in real time.
 */
async function sweepAllExpired(ctx, io) {
    const nowUnix = Math.floor(Date.now() / 1000);
    try {
        if (!ctx?.wo_messages) return;

        const expired = await ctx.wo_messages.findAll({
            where: { remove_at: { [Op.gt]: 0, [Op.lte]: nowUnix } },
            attributes: ['id', 'from_id', 'to_id'],
            raw: true,
        });
        if (!expired.length) return;

        const ids = expired.map(m => m.id);
        await ctx.wo_messages.destroy({ where: { id: { [Op.in]: ids } } });

        // Notify affected participants
        if (io) {
            // Group by (from_id, to_id) pair so we can batch-emit per chat
            const pairs = new Map();
            for (const m of expired) {
                const key = `${Math.min(m.from_id, m.to_id)}_${Math.max(m.from_id, m.to_id)}`;
                if (!pairs.has(key)) pairs.set(key, { fromId: m.from_id, toId: m.to_id, ids: [] });
                pairs.get(key).ids.push(m.id);
            }
            for (const { fromId, toId, ids: msgIds } of pairs.values()) {
                const payload = { message_ids: msgIds, from_id: fromId, to_id: toId };
                io.to(`user_${fromId}`).emit('secret:messages_expired', payload);
                io.to(`user_${toId}`).emit('secret:messages_expired', payload);
            }
        }

        console.log(`[SecretSweeper] Deleted ${ids.length} expired messages`);
    } catch (err) {
        console.error('[SecretSweeper] sweep error:', err.message);
    }
}

/**
 * Start the global 60-second sweeper. Call once from main.js after io is ready.
 * Returns the interval handle so it can be cleared in tests.
 */
function startSecretSweeper(ctx, io) {
    const handle = setInterval(() => sweepAllExpired(ctx, io), 60000);
    handle.unref(); // don't prevent process exit
    console.log('[SecretSweeper] Global 60 s sweeper started');
    return handle;
}

// ── HTTP handlers ─────────────────────────────────────────────────────────────

/**
 * POST /api/node/chat/secret/cleanup
 * Manual cleanup on chat open.
 * Body: { user_id: number }
 */
function cleanupHandler(ctx, io) {
    return async (req, res) => {
        const selfId  = req.userId;
        const otherId = parseInt(req.body.user_id, 10);
        if (!otherId) return res.status(400).json({ api_status: 400, error_message: 'user_id required' });

        try {
            const deletedIds = await cleanupExpiredMessages(ctx, selfId, otherId);

            // Notify both sides if anything was deleted
            if (io && deletedIds.length) {
                const payload = { message_ids: deletedIds, from_id: selfId, to_id: otherId };
                io.to(`user_${selfId}`).emit('secret:messages_expired', payload);
                io.to(`user_${otherId}`).emit('secret:messages_expired', payload);
            }

            return res.json({ api_status: 200, deleted_count: deletedIds.length });
        } catch (err) {
            console.error('[SecretCleanup] cleanup handler error:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Cleanup failed' });
        }
    };
}

/**
 * POST /api/node/chat/secret/set-timer
 * Set or clear self-destruct timer for a chat. Persists to wm_chat_timers.
 * Body: { user_id: number, timer_seconds: number }
 */
function setTimerHandler(ctx, io) {
    return async (req, res) => {
        const selfId       = req.userId;
        const otherId      = parseInt(req.body.user_id, 10);
        const timerSeconds = Math.max(0, parseInt(req.body.timer_seconds, 10) || 0);

        if (!otherId) return res.status(400).json({ api_status: 400, error_message: 'user_id required' });

        // Validate allowed timer values: 0 (off), 5s, 10s, 30s, 1m, 5m, 1h, 1d, 1w
        const ALLOWED = new Set([0, 5, 10, 30, 60, 300, 3600, 86400, 604800]);
        if (!ALLOWED.has(timerSeconds)) {
            return res.status(400).json({ api_status: 400, error_message: 'Invalid timer_seconds value' });
        }

        try {
            // Persist the timer setting
            if (ctx.wm_chat_timers) {
                const pair = pairKey(selfId, otherId);
                await ctx.wm_chat_timers.upsert({
                    ...pair,
                    timer_seconds: timerSeconds,
                    set_by:        selfId,
                    updated_at:    new Date(),
                });
            }

            // If timer is disabled — clear pending remove_at on existing messages
            if (timerSeconds === 0) {
                await ctx.wo_messages.update(
                    { remove_at: 0 },
                    {
                        where: {
                            remove_at: { [Op.gt]: 0 },
                            [Op.or]: [
                                { from_id: selfId,  to_id: otherId },
                                { from_id: otherId, to_id: selfId  },
                            ],
                        },
                    }
                );
            }

            // Notify the other participant about the new timer
            io.to(`user_${otherId}`).emit('secret_timer_changed', {
                from_id:       selfId,
                timer_seconds: timerSeconds,
            });

            return res.json({ api_status: 200, timer_seconds: timerSeconds });
        } catch (err) {
            console.error('[SecretTimer] set-timer error:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Timer update failed' });
        }
    };
}

/**
 * GET /api/node/chat/secret/timer/:userId
 * Get the current self-destruct timer for the chat with :userId.
 */
function getTimerHandler(ctx) {
    return async (req, res) => {
        const selfId  = req.userId;
        const otherId = parseInt(req.params.userId, 10);
        if (!otherId) return res.status(400).json({ api_status: 400, error_message: 'userId required' });

        try {
            let timerSeconds = 0;
            if (ctx.wm_chat_timers) {
                const pair   = pairKey(selfId, otherId);
                const record = await ctx.wm_chat_timers.findOne({ where: pair, raw: true });
                if (record) timerSeconds = record.timer_seconds;
            }
            return res.json({ api_status: 200, timer_seconds: timerSeconds });
        } catch (err) {
            console.error('[SecretTimer] get-timer error:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Failed to get timer' });
        }
    };
}

module.exports = {
    cleanupHandler,
    setTimerHandler,
    getTimerHandler,
    cleanupExpiredMessages,
    startSecretSweeper,
};
