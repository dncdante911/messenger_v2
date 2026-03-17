'use strict';

/**
 * Global Search — пошук по всіх чатах одразу.
 *
 * POST /api/node/search/global
 *   Body: { query: string, limit?: number, offset?: number }
 *   Returns: { results: [...], total: number }
 *
 * Шукає в:
 *   – особистих повідомленнях (Wo_Messages, from/to поточного користувача)
 *   – групових повідомленнях  (Wo_Messages де group_id > 0 і user є учасником)
 *
 * Використовує FULLTEXT index якщо доступний (MATCH ... AGAINST),
 * fallback на LIKE для сумісності.
 *
 * Повідомлення з cipher_version=3 (Signal E2EE) пропускаються —
 * text_preview для них порожній (сервер не знає plaintext).
 */

const { Op } = require('sequelize');

// ─── auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;
    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });
        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Search/auth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── POST /api/node/search/global ────────────────────────────────────────────

function globalSearch(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim();
            const limit  = Math.min(parseInt(req.body.limit)  || 50, 200);
            const offset = Math.max(parseInt(req.body.offset) || 0,  0);

            if (!query || query.length < 2) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'query must be at least 2 characters',
                });
            }

            // ── 1. Groups the user belongs to ────────────────────────────────
            const memberships = await ctx.wo_groupchatusers.findAll({
                where:      { user_id: userId, active: '1' },
                attributes: ['group_id'],
                raw:        true,
            });
            const groupIds = memberships.map(m => m.group_id);

            // ── 2. Search in private messages ────────────────────────────────
            const privateWhere = {
                [Op.and]: [
                    { text_preview: { [Op.like]: `%${query}%` } },
                    { cipher_version: { [Op.ne]: 3 } },        // skip E2EE
                    { group_id: 0 },
                    { page_id:  0 },
                    {
                        [Op.or]: [
                            { from_id: userId },
                            { to_id:   userId },
                        ],
                    },
                ],
            };

            // ── 3. Search in group messages ───────────────────────────────────
            const groupWhere = groupIds.length ? {
                [Op.and]: [
                    { text_preview:   { [Op.like]: `%${query}%` } },
                    { cipher_version: { [Op.ne]: 3 } },
                    { group_id:       { [Op.in]: groupIds } },
                ],
            } : null;

            const [privateMessages, groupMessages] = await Promise.all([
                ctx.wo_messages.findAll({
                    where:      privateWhere,
                    attributes: ['id', 'from_id', 'to_id', 'group_id', 'text_preview', 'time', 'cipher_version', 'stickers', 'media'],
                    order:      [['time', 'DESC']],
                    limit:      limit + offset,
                    raw:        true,
                }),
                groupWhere
                    ? ctx.wo_messages.findAll({
                        where:      groupWhere,
                        attributes: ['id', 'from_id', 'to_id', 'group_id', 'text_preview', 'time', 'cipher_version', 'stickers', 'media'],
                        order:      [['time', 'DESC']],
                        limit:      limit + offset,
                        raw:        true,
                    })
                    : Promise.resolve([]),
            ]);

            // ── 4. Merge, sort by time DESC, paginate ─────────────────────────
            const all = [...privateMessages, ...groupMessages]
                .sort((a, b) => b.time - a.time);

            const paginated = all.slice(offset, offset + limit);

            // ── 5. Attach chat_type label ─────────────────────────────────────
            const results = paginated.map(msg => ({
                id:           msg.id,
                chat_type:    msg.group_id > 0 ? 'group' : 'user',
                chat_id:      msg.group_id > 0 ? msg.group_id : (msg.from_id === userId ? msg.to_id : msg.from_id),
                from_id:      msg.from_id,
                group_id:     msg.group_id,
                text_preview: msg.text_preview,
                time:         msg.time,
                has_media:    !!(msg.media && msg.media !== ''),
                has_sticker:  !!(msg.stickers && msg.stickers !== ''),
            }));

            console.log(`[Search/global] user=${userId} query="${query}" found=${all.length} returned=${results.length}`);
            res.json({
                api_status: 200,
                query,
                total:      all.length,
                count:      results.length,
                offset,
                results,
            });

        } catch (err) {
            console.error('[Search/global]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Search failed' });
        }
    };
}

// ─── registration ─────────────────────────────────────────────────────────────

function registerSearchRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);
    app.post('/api/node/search/global', auth, globalSearch(ctx));
    console.log('[Search] Routes registered: POST /api/node/search/global');
}

module.exports = { registerSearchRoutes };
