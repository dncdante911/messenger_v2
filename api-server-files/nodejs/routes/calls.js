/**
 * Node.js REST API Routes for Call History
 * Replaces PHP call_history.php endpoint
 *
 * POST /api/v2/call_history.php
 *   body.type = get_history  — fetch call history
 *   body.type = delete_call  — soft-delete one call
 *   body.type = clear_history — soft-delete all calls
 */

const { QueryTypes } = require('sequelize');

async function authMiddleware(ctx, req, res, next) {
    const accessToken =
        req.headers['access-token'] ||
        req.query.access_token ||
        req.body.access_token;

    if (!accessToken) {
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
    }

    try {
        const session = await ctx.wo_appssessions.findOne({
            where: { session_id: accessToken }
        });

        if (!session) {
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });
        }

        req.userId = parseInt(session.user_id);
        next();
    } catch (err) {
        console.error('[Calls/Auth] Error:', err.message);
        return res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

async function getHistory(ctx, req, res) {
    try {
        const userId = req.userId;
        const limit  = Math.min(parseInt(req.body.limit)  || 50, 100);
        const offset = Math.max(parseInt(req.body.offset) || 0,  0);
        const filter = req.body.filter || 'all';

        const sequelize = ctx.wo_calls.sequelize;

        let filterSql;
        switch (filter) {
            case 'missed':
                filterSql = `c.to_id = :userId AND c.status IN ('missed', 'rejected')`;
                break;
            case 'incoming':
                filterSql = `c.to_id = :userId`;
                break;
            case 'outgoing':
                filterSql = `c.from_id = :userId`;
                break;
            default:
                filterSql = `(c.from_id = :userId OR c.to_id = :userId)`;
        }

        // 1-on-1 calls
        const calls = await sequelize.query(`
            SELECT
                c.id,
                'personal' AS call_category,
                c.call_type,
                c.status,
                c.created_at,
                c.accepted_at,
                c.ended_at,
                c.duration,
                CASE WHEN c.from_id = :userId THEN 'outgoing' ELSE 'incoming' END AS direction,
                CASE WHEN c.from_id = :userId THEN c.to_id ELSE c.from_id END AS other_user_id,
                u.username     AS other_username,
                u.first_name   AS other_first_name,
                u.last_name    AS other_last_name,
                u.avatar       AS other_avatar,
                u.verified     AS other_verified
            FROM wo_calls c
            LEFT JOIN Wo_Users u
                ON u.user_id = CASE WHEN c.from_id = :userId THEN c.to_id ELSE c.from_id END
            WHERE ${filterSql}
              AND NOT (c.from_id = :userId AND c.deleted_by_from = 1)
              AND NOT (c.to_id   = :userId AND c.deleted_by_to   = 1)
            ORDER BY c.created_at DESC
            LIMIT :limit OFFSET :offset
        `, {
            replacements: { userId, limit, offset },
            type: QueryTypes.SELECT
        });

        // Group calls
        const groupCalls = await sequelize.query(`
            SELECT
                gc.id,
                'group' AS call_category,
                gc.call_type,
                gc.status,
                gc.created_at,
                gc.started_at  AS accepted_at,
                gc.ended_at,
                0              AS duration,
                CASE WHEN gc.initiated_by = :userId THEN 'outgoing' ELSE 'incoming' END AS direction,
                gc.group_id,
                g.group_name,
                g.avatar       AS group_avatar,
                gc.max_participants
            FROM wo_group_calls gc
            INNER JOIN wo_group_call_participants gcp
                ON gcp.call_id = gc.id AND gcp.user_id = :userId
            LEFT JOIN Wo_GroupChat g ON g.group_id = gc.group_id
            ORDER BY gc.created_at DESC
            LIMIT :limit OFFSET :offset
        `, {
            replacements: { userId, limit, offset },
            type: QueryTypes.SELECT
        });

        // Merge, sort, cap
        const all = [...calls, ...groupCalls]
            .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
            .slice(0, limit);

        const formatted = all.map(call => {
            const item = {
                id:           parseInt(call.id),
                call_category: call.call_category,
                call_type:    call.call_type,
                status:       call.status,
                direction:    call.direction,
                created_at:   call.created_at,
                accepted_at:  call.accepted_at  || null,
                ended_at:     call.ended_at     || null,
                duration:     parseInt(call.duration) || 0,
                timestamp:    Math.floor(new Date(call.created_at).getTime() / 1000)
            };

            if (call.call_category === 'personal') {
                const firstName = call.other_first_name || '';
                const lastName  = call.other_last_name  || '';
                const name = (firstName || lastName)
                    ? (firstName + ' ' + lastName).trim()
                    : (call.other_username || '');

                item.other_user = {
                    user_id:  parseInt(call.other_user_id),
                    username: call.other_username || '',
                    name:     name,
                    avatar:   call.other_avatar   || '',
                    verified: parseInt(call.other_verified) || 0
                };
            } else {
                item.group_data = {
                    group_id:        parseInt(call.group_id),
                    group_name:      call.group_name       || '',
                    avatar:          call.group_avatar     || '',
                    max_participants: parseInt(call.max_participants) || 0
                };
            }

            return item;
        });

        // Total count for pagination
        const countRows = await sequelize.query(`
            SELECT COUNT(*) AS total
            FROM wo_calls c
            WHERE (c.from_id = :userId OR c.to_id = :userId)
              AND NOT (c.from_id = :userId AND c.deleted_by_from = 1)
              AND NOT (c.to_id   = :userId AND c.deleted_by_to   = 1)
        `, { replacements: { userId }, type: QueryTypes.SELECT });

        const total = parseInt(countRows[0]?.total) || 0;

        res.json({ api_status: 200, calls: formatted, total, offset, limit });

    } catch (err) {
        console.error('[Calls/History] Error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Failed to fetch call history' });
    }
}

async function deleteCall(ctx, req, res) {
    try {
        const userId = req.userId;
        const callId = parseInt(req.body.call_id) || 0;

        if (callId <= 0) {
            return res.status(400).json({ api_status: 400, error_message: 'call_id is required' });
        }

        const call = await ctx.wo_calls.findOne({ where: { id: callId }, raw: true });
        if (!call) {
            return res.status(404).json({ api_status: 404, error_message: 'Call not found' });
        }

        const sequelize = ctx.wo_calls.sequelize;

        if (parseInt(call.from_id) === userId) {
            await sequelize.query(
                'UPDATE wo_calls SET deleted_by_from = 1 WHERE id = :callId',
                { replacements: { callId }, type: QueryTypes.UPDATE }
            );
        } else if (parseInt(call.to_id) === userId) {
            await sequelize.query(
                'UPDATE wo_calls SET deleted_by_to = 1 WHERE id = :callId',
                { replacements: { callId }, type: QueryTypes.UPDATE }
            );
        } else {
            return res.status(403).json({ api_status: 403, error_message: 'Not authorized' });
        }

        res.json({ api_status: 200, message: 'Call deleted from history' });

    } catch (err) {
        console.error('[Calls/Delete] Error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Failed to delete call' });
    }
}

async function clearHistory(ctx, req, res) {
    try {
        const userId    = req.userId;
        const sequelize = ctx.wo_calls.sequelize;

        await sequelize.query(
            'UPDATE wo_calls SET deleted_by_from = 1 WHERE from_id = :userId',
            { replacements: { userId }, type: QueryTypes.UPDATE }
        );
        await sequelize.query(
            'UPDATE wo_calls SET deleted_by_to = 1 WHERE to_id = :userId',
            { replacements: { userId }, type: QueryTypes.UPDATE }
        );

        res.json({ api_status: 200, message: 'Call history cleared' });

    } catch (err) {
        console.error('[Calls/Clear] Error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Failed to clear history' });
    }
}

function registerCallRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // Same path as the PHP endpoint — drop-in replacement
    app.post('/api/v2/call_history.php', auth, (req, res) => {
        const type = req.body.type || req.query.type || '';
        switch (type) {
            case 'get_history':   return getHistory(ctx, req, res);
            case 'delete_call':   return deleteCall(ctx, req, res);
            case 'clear_history': return clearHistory(ctx, req, res);
            default:
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'Invalid type. Use: get_history, delete_call, clear_history'
                });
        }
    });

    console.log('[Calls API] POST /api/v2/call_history.php registered');
}

module.exports = { registerCallRoutes };
