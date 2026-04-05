'use strict';

/**
 * Search routes
 *
 * POST /api/node/search/global          — поиск по всіх чатах (+ фільтри)
 * POST /api/node/search/users           — пошук користувачів
 * POST /api/node/search/suggestions     — autocomplete (recent + saved + users)
 * GET  /api/node/search/saved           — список збережених пошуків
 * POST /api/node/search/saved           — зберегти пошук
 * DELETE /api/node/search/saved/:id     — видалити збережений пошук
 * POST /api/node/search/recent          — зберегти в history (automatic)
 * DELETE /api/node/search/recent        — очистити всю history
 *
 * Full-text search:
 *   Якщо на Wo_Messages.text_preview є FULLTEXT index (ft_text_preview),
 *   використовується MATCH ... AGAINST IN BOOLEAN MODE.
 *   При відсутності або помилці — fallback на LIKE.
 */

const { Op } = require('sequelize');

// Max recent searches stored per user before auto-pruning
const MAX_RECENT = 20;

// ─── inline auth ─────────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body?.access_token;
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

// ─── helpers ─────────────────────────────────────────────────────────────────

/**
 * Шукає повідомлення по тексту з FULLTEXT-first стратегією.
 * @param {object} ctx         — app context
 * @param {string} query       — sanitized query (special chars stripped)
 * @param {string} extraWhere  — additional SQL WHERE conditions (AND ...)
 * @param {Array}  extraParams — replacements for extraWhere placeholders
 * @param {Array}  filterParams — replacements for filterClauses placeholders (date, from_id)
 * @param {string} filterClauses — additional filter SQL (AND ...)
 * @param {number} limitPlusOffset
 * @returns {Promise<Array>}
 */
async function searchMessages(ctx, query, extraWhere, extraParams, filterClauses, filterParams, limitPlusOffset) {
    const base = `SELECT id, from_id, to_id, group_id, text_preview, time, stickers, media
                  FROM Wo_Messages
                  WHERE`;
    const tail = `${extraWhere} ${filterClauses}
                  ORDER BY time DESC
                  LIMIT ${limitPlusOffset}`;
    try {
        // ── FULLTEXT (MATCH ... AGAINST IN BOOLEAN MODE) ──────────────────────
        // Boolean mode с * для prefix matching; спецсимволи вже прибрані з query
        return await ctx.sequelize.query(
            `${base} MATCH(text_preview) AGAINST(? IN BOOLEAN MODE) ${tail}`,
            {
                replacements: [`${query}*`, ...extraParams, ...filterParams],
                type:         ctx.sequelize.QueryTypes.SELECT,
            }
        );
    } catch (_e) {
        // ── LIKE fallback ─────────────────────────────────────────────────────
        return await ctx.sequelize.query(
            `${base} text_preview LIKE ? ${tail}`,
            {
                replacements: [`%${query}%`, ...extraParams, ...filterParams],
                type:         ctx.sequelize.QueryTypes.SELECT,
            }
        );
    }
}

/** Strip MySQL FULLTEXT boolean mode special chars from user input. */
function sanitizeQuery(q) {
    return q.replace(/[+\-><()~*"@]/g, ' ').replace(/\s+/g, ' ').trim();
}

// ─── POST /api/node/search/global ────────────────────────────────────────────
// Фільтри: date_from, date_to, from_id, msg_type (all|text|media|sticker)

function globalSearch(ctx) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const rawQuery = (req.body.query || '').trim();
            const limit   = Math.min(parseInt(req.body.limit)  || 50, 200);
            const offset  = Math.max(parseInt(req.body.offset) || 0,  0);

            // ── Filters ───────────────────────────────────────────────────────
            const dateFrom = req.body.date_from ? parseInt(req.body.date_from) : null;
            const dateTo   = req.body.date_to   ? parseInt(req.body.date_to)   : null;
            const fromId   = req.body.from_id   ? parseInt(req.body.from_id)   : null;
            const msgType  = (req.body.msg_type || 'all').toLowerCase(); // all|text|media|sticker

            if (!rawQuery || rawQuery.length < 2) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'query must be at least 2 characters',
                });
            }

            const query = sanitizeQuery(rawQuery);

            // ── Build shared filter SQL ───────────────────────────────────────
            let filterClauses = `AND cipher_version != 3`;
            const filterParams = [];

            if (dateFrom) { filterClauses += ` AND time >= ?`; filterParams.push(dateFrom); }
            if (dateTo)   { filterClauses += ` AND time <= ?`; filterParams.push(dateTo);   }
            if (fromId)   { filterClauses += ` AND from_id = ?`; filterParams.push(fromId); }
            if (msgType === 'media')
                filterClauses += ` AND media IS NOT NULL AND media != ''`;
            else if (msgType === 'sticker')
                filterClauses += ` AND stickers IS NOT NULL AND stickers != ''`;
            else if (msgType === 'text')
                filterClauses += ` AND (media IS NULL OR media = '') AND (stickers IS NULL OR stickers = '')`;

            // ── Groups the user belongs to ────────────────────────────────────
            const memberships = await ctx.wo_groupchatusers.findAll({
                where:      { user_id: userId, active: '1' },
                attributes: ['group_id'],
                raw:        true,
            });
            const groupIds = memberships.map(m => m.group_id);

            // ── Concurrent search ─────────────────────────────────────────────
            const [privateRows, groupRows] = await Promise.all([
                searchMessages(
                    ctx, query,
                    `AND group_id = 0 AND page_id = 0 AND (from_id = ? OR to_id = ?)`,
                    [userId, userId],
                    filterClauses, filterParams,
                    limit + offset
                ),
                groupIds.length
                    ? searchMessages(
                        ctx, query,
                        `AND group_id IN (${groupIds.map(() => '?').join(',')})`,
                        groupIds,
                        filterClauses, filterParams,
                        limit + offset
                    )
                    : Promise.resolve([]),
            ]);

            // ── Merge, sort DESC, paginate ────────────────────────────────────
            const all = [...privateRows, ...groupRows].sort((a, b) => b.time - a.time);
            const paginated = all.slice(offset, offset + limit);

            const results = paginated.map(msg => ({
                id:           msg.id,
                chat_type:    msg.group_id > 0 ? 'group' : 'user',
                chat_id:      msg.group_id > 0
                    ? msg.group_id
                    : (msg.from_id === userId ? msg.to_id : msg.from_id),
                from_id:      msg.from_id,
                group_id:     msg.group_id,
                text_preview: msg.text_preview,
                time:         msg.time,
                has_media:    !!(msg.media    && msg.media    !== ''),
                has_sticker:  !!(msg.stickers && msg.stickers !== ''),
            }));

            res.json({
                api_status: 200,
                query:      rawQuery,
                total:      all.length,
                count:      results.length,
                offset,
                results,
            });

        } catch (err) {
            console.error('[Search/global]', err.stack || err.message);
            res.status(500).json({ api_status: 500, error_message: 'Search failed' });
        }
    };
}

// ─── POST /api/node/search/users ─────────────────────────────────────────────

function searchUsers(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim();
            const limit  = Math.min(parseInt(req.body.limit) || 30, 100);

            if (!query || query.length < 2)
                return res.status(400).json({ api_status: 400, error_message: 'query must be at least 2 characters' });

            const rows = await ctx.wo_users.findAll({
                where: {
                    [Op.and]: [
                        { user_id: { [Op.ne]: userId } },
                        {
                            [Op.or]: [
                                { username:   { [Op.like]: `%${query}%` } },
                                { first_name: { [Op.like]: `%${query}%` } },
                                { last_name:  { [Op.like]: `%${query}%` } },
                            ],
                        },
                    ],
                },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'last_seen', 'user_verified'],
                limit,
                raw: true,
            });

            const users = rows.map(u => ({
                user_id:     u.user_id,
                username:    u.username   || '',
                first_name:  u.first_name || '',
                last_name:   u.last_name  || '',
                avatar:      u.avatar     || '',
                last_seen:   u.last_seen  || 0,
                is_verified: !!u.user_verified,
            }));

            res.json({ api_status: 200, query, users, total: users.length });
        } catch (err) {
            console.error('[Search/users]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'User search failed' });
        }
    };
}

// ─── POST /api/node/search/suggestions ───────────────────────────────────────
// Autocomplete: saved searches + recent searches + user name hints

function getSuggestions(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim().toLowerCase();

            const suggestions = [];

            // ── Saved searches (up to 5, filtered by query prefix) ────────────
            const saved = await ctx.wo_search_queries.findAll({
                where:      { user_id: userId, is_saved: 1 },
                order:      [['created_at', 'DESC']],
                limit:      20,
                raw:        true,
            });
            for (const s of saved) {
                if (!query || s.query.toLowerCase().includes(query)) {
                    suggestions.push({ type: 'saved', id: s.id, query: s.query });
                    if (suggestions.filter(x => x.type === 'saved').length >= 5) break;
                }
            }

            // ── Recent searches (up to 5, filtered by query prefix) ───────────
            const recent = await ctx.wo_search_queries.findAll({
                where:      { user_id: userId, is_saved: 0 },
                order:      [['created_at', 'DESC']],
                limit:      MAX_RECENT,
                raw:        true,
            });
            for (const r of recent) {
                if (!query || r.query.toLowerCase().includes(query)) {
                    // Skip duplicates already listed as saved
                    const alreadyListed = suggestions.some(s => s.query === r.query);
                    if (!alreadyListed) {
                        suggestions.push({ type: 'recent', id: r.id, query: r.query });
                        if (suggestions.filter(x => x.type === 'recent').length >= 5) break;
                    }
                }
            }

            // ── User name hints (only when query ≥ 2) ────────────────────────
            if (query.length >= 2) {
                const users = await ctx.wo_users.findAll({
                    where: {
                        user_id: { [Op.ne]: userId },
                        [Op.or]: [
                            { username:   { [Op.like]: `%${query}%` } },
                            { first_name: { [Op.like]: `%${query}%` } },
                            { last_name:  { [Op.like]: `%${query}%` } },
                        ],
                    },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'user_verified'],
                    limit: 3,
                    raw:   true,
                });
                for (const u of users) {
                    suggestions.push({
                        type: 'user',
                        user: {
                            user_id:     u.user_id,
                            username:    u.username   || '',
                            first_name:  u.first_name || '',
                            last_name:   u.last_name  || '',
                            avatar:      u.avatar     || '',
                            is_verified: !!u.user_verified,
                        },
                    });
                }
            }

            res.json({ api_status: 200, suggestions });
        } catch (err) {
            console.error('[Search/suggestions]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get suggestions' });
        }
    };
}

// ─── GET /api/node/search/saved ───────────────────────────────────────────────

function listSavedSearches(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const rows   = await ctx.wo_search_queries.findAll({
                where:  { user_id: userId, is_saved: 1 },
                order:  [['created_at', 'DESC']],
                limit:  50,
                raw:    true,
            });
            const saved = rows.map(r => ({ id: r.id, query: r.query, created_at: r.created_at }));
            res.json({ api_status: 200, saved });
        } catch (err) {
            console.error('[Search/saved list]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to list saved searches' });
        }
    };
}

// ─── POST /api/node/search/saved ─────────────────────────────────────────────

function saveSearch(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim();
            if (!query || query.length < 2)
                return res.status(400).json({ api_status: 400, error_message: 'query is too short' });

            // Upsert: if same query already saved — return existing
            const existing = await ctx.wo_search_queries.findOne({
                where: { user_id: userId, query, is_saved: 1 },
                raw:   true,
            });
            if (existing) {
                return res.json({ api_status: 200, id: existing.id, query: existing.query });
            }

            const row = await ctx.wo_search_queries.create({
                user_id:    userId,
                query,
                is_saved:   1,
                created_at: Math.floor(Date.now() / 1000),
            });
            res.json({ api_status: 200, id: row.id, query: row.query });
        } catch (err) {
            console.error('[Search/save]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to save search' });
        }
    };
}

// ─── DELETE /api/node/search/saved/:id ───────────────────────────────────────

function deleteSavedSearch(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const id     = parseInt(req.params.id);
            if (!id) return res.status(400).json({ api_status: 400, error_message: 'id is required' });

            await ctx.wo_search_queries.destroy({
                where: { id, user_id: userId },
            });
            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Search/delete saved]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to delete saved search' });
        }
    };
}

// ─── POST /api/node/search/recent ────────────────────────────────────────────
// Save a query to recent history. Called after user submits a search.

function saveRecentSearch(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const query  = (req.body.query || '').trim();
            if (!query || query.length < 2)
                return res.json({ api_status: 200 }); // silently ignore too-short queries

            // Remove duplicate if exists (keep only latest)
            await ctx.wo_search_queries.destroy({
                where: { user_id: userId, query, is_saved: 0 },
            });

            await ctx.wo_search_queries.create({
                user_id:    userId,
                query,
                is_saved:   0,
                created_at: Math.floor(Date.now() / 1000),
            });

            // Prune oldest entries if over limit
            const allRecent = await ctx.wo_search_queries.findAll({
                where:      { user_id: userId, is_saved: 0 },
                order:      [['created_at', 'DESC']],
                attributes: ['id'],
                raw:        true,
            });
            if (allRecent.length > MAX_RECENT) {
                const toDelete = allRecent.slice(MAX_RECENT).map(r => r.id);
                await ctx.wo_search_queries.destroy({ where: { id: toDelete } });
            }

            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Search/recent save]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to save recent search' });
        }
    };
}

// ─── DELETE /api/node/search/recent ──────────────────────────────────────────

function clearRecentSearches(ctx) {
    return async (req, res) => {
        try {
            await ctx.wo_search_queries.destroy({
                where: { user_id: req.userId, is_saved: 0 },
            });
            res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Search/recent clear]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to clear recent searches' });
        }
    };
}

// ─── registration ─────────────────────────────────────────────────────────────

function registerSearchRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.post  ('/api/node/search/global',       auth, globalSearch(ctx));
    app.post  ('/api/node/search/users',         auth, searchUsers(ctx));
    app.post  ('/api/node/search/suggestions',   auth, getSuggestions(ctx));
    app.get   ('/api/node/search/saved',         auth, listSavedSearches(ctx));
    app.post  ('/api/node/search/saved',         auth, saveSearch(ctx));
    app.delete('/api/node/search/saved/:id',     auth, deleteSavedSearch(ctx));
    app.post  ('/api/node/search/recent',        auth, saveRecentSearch(ctx));
    app.delete('/api/node/search/recent',        auth, clearRecentSearches(ctx));

    console.log('[Search] Routes registered: global, users, suggestions, saved CRUD, recent');
}

module.exports = { registerSearchRoutes };
