'use strict';

/**
 * Business Directory API
 *
 * Endpoints:
 *   GET /api/node/business-directory            – List all businesses (pagination, filter, search, geo-sort)
 *   GET /api/node/business-directory/categories – List all unique business categories
 *   GET /api/node/business-directory/:userId    – Get single business profile details
 */

const { Op } = require('sequelize');

// ─── Auth middleware ──────────────────────────────────────────────────────────

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

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Calculate distance in km between two lat/lng points using Haversine formula.
 */
function haversineDistance(lat1, lng1, lat2, lng2) {
    const R = 6371; // Earth radius in km
    const toRad = deg => deg * Math.PI / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

/**
 * Determine if a user is verified based on wo_users `verified` or `is_pro` flags.
 */
function isVerified(user) {
    if (!user) return false;
    return user.verified === '1' || user.verified === 1 ||
           user.is_pro   === '1' || user.is_pro   === 1;
}

/**
 * Build a public-facing business directory item from joined profile + user rows.
 */
function buildDirectoryItem(profile, user, queryLat, queryLng) {
    let distance = null;
    if (queryLat != null && queryLng != null && profile.lat != null && profile.lng != null) {
        distance = Math.round(haversineDistance(
            parseFloat(queryLat), parseFloat(queryLng),
            parseFloat(profile.lat), parseFloat(profile.lng)
        ) * 10) / 10; // 1 decimal place
    }

    return {
        user_id:       profile.user_id,
        username:      user ? user.username : '',
        avatar:        user ? (user.avatar || null) : null,
        business_name: profile.business_name || '',
        category:      profile.category || '',
        description:   profile.description || null,
        address:       profile.address || null,
        phone:         profile.phone || null,
        email:         profile.email || null,
        website:       profile.website || null,
        is_verified:   isVerified(user),
        lat:           profile.lat ? parseFloat(profile.lat) : null,
        lng:           profile.lng ? parseFloat(profile.lng) : null,
        distance,
    };
}

// ─── Route registrar ──────────────────────────────────────────────────────────

function registerBusinessDirectoryRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // ── Categories (must be registered before /:userId to avoid conflict) ────

    app.get('/api/node/business-directory/categories', auth, async (req, res) => {
        try {
            const sequelize = ctx.wm_business_profile.sequelize;
            const rows = await sequelize.query(
                `SELECT DISTINCT category
                   FROM wm_business_profile
                  WHERE category IS NOT NULL
                    AND category != ''
                    AND (is_listed IS NULL OR is_listed = 1)
                  ORDER BY category ASC`,
                { type: sequelize.QueryTypes.SELECT }
            );
            const categories = rows.map(r => r.category).filter(Boolean);
            res.json({ api_status: 200, categories });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── List businesses ───────────────────────────────────────────────────────

    app.get('/api/node/business-directory', auth, async (req, res) => {
        try {
            const page     = Math.max(1, parseInt(req.query.page)  || 1);
            const limit    = Math.min(100, Math.max(1, parseInt(req.query.limit) || 20));
            const offset   = (page - 1) * limit;
            const category = req.query.category  || null;
            const search   = req.query.search    || null;
            const queryLat = req.query.lat != null ? parseFloat(req.query.lat) : null;
            const queryLng = req.query.lng != null ? parseFloat(req.query.lng) : null;

            // Build WHERE clause
            const where = {};
            if (category) {
                where.category = category;
            }
            if (search) {
                where[Op.or] = [
                    { business_name: { [Op.like]: `%${search}%` } },
                    { description:   { [Op.like]: `%${search}%` } },
                    { address:       { [Op.like]: `%${search}%` } },
                    { category:      { [Op.like]: `%${search}%` } },
                ];
            }

            // Only show profiles opted into the directory (is_listed = 1 or column absent)
            // We use a raw query to handle the optional column gracefully
            const sequelize = ctx.wm_business_profile.sequelize;

            // Count total
            const countResult = await sequelize.query(
                buildListQuery({ where, search, category, countOnly: true }),
                {
                    replacements: buildReplacements({ search, category }),
                    type: sequelize.QueryTypes.SELECT,
                }
            );
            const total = parseInt((countResult[0] || {}).total || 0);

            // Fetch rows
            const rows = await sequelize.query(
                buildListQuery({ where, search, category, countOnly: false, limit, offset }),
                {
                    replacements: { ...buildReplacements({ search, category }), limit, offset },
                    type: sequelize.QueryTypes.SELECT,
                }
            );

            let businesses = rows.map(row => {
                const profile = {
                    user_id:       row.user_id,
                    business_name: row.business_name,
                    category:      row.category,
                    description:   row.description,
                    address:       row.address,
                    phone:         row.phone,
                    email:         row.email,
                    website:       row.website,
                    lat:           row.lat,
                    lng:           row.lng,
                };
                const user = {
                    username: row.username,
                    avatar:   row.avatar,
                    verified: row.verified,
                    is_pro:   row.is_pro,
                };
                return buildDirectoryItem(profile, user, queryLat, queryLng);
            });

            // Geo-sort if coordinates provided
            if (queryLat != null && queryLng != null) {
                businesses = businesses.sort((a, b) => {
                    if (a.distance == null && b.distance == null) return 0;
                    if (a.distance == null) return 1;
                    if (b.distance == null) return -1;
                    return a.distance - b.distance;
                });
            }

            res.json({ api_status: 200, businesses, total, page });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    // ── Single business profile ───────────────────────────────────────────────

    app.get('/api/node/business-directory/:userId', auth, async (req, res) => {
        try {
            const targetId = parseInt(req.params.userId);
            if (isNaN(targetId)) {
                return res.status(400).json({ api_status: 400, error_message: 'Invalid userId' });
            }

            const profile = await ctx.wm_business_profile.findOne({
                where: { user_id: targetId },
                raw:   true,
            });
            if (!profile) {
                return res.status(404).json({ api_status: 404, error_message: 'Business profile not found' });
            }

            const user = await ctx.wo_users.findOne({
                where:      { user_id: targetId },
                attributes: ['user_id', 'username', 'avatar', 'verified', 'is_pro'],
                raw:        true,
            });

            const item = buildDirectoryItem(profile, user, null, null);
            res.json({ api_status: 200, ...item });
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });
}

// ─── SQL builder helpers ───────────────────────────────────────────────────────

function buildListQuery({ search, category, countOnly, limit, offset }) {
    const selectCols = countOnly
        ? 'COUNT(*) AS total'
        : `bp.user_id, bp.business_name, bp.category, bp.description,
           bp.address, bp.phone, bp.email, bp.website, bp.lat, bp.lng,
           u.username, u.avatar, u.verified, u.is_pro`;

    const whereClause = buildWhereClause({ search, category });
    const limitClause = (!countOnly && limit != null) ? 'LIMIT :limit OFFSET :offset' : '';

    return `
        SELECT ${selectCols}
          FROM wm_business_profile bp
          JOIN Wo_Users u ON u.user_id = bp.user_id
         WHERE (bp.is_listed IS NULL OR bp.is_listed = 1)
           AND bp.business_name IS NOT NULL
           AND bp.business_name != ''
           ${whereClause}
         ${!countOnly ? 'ORDER BY bp.business_name ASC' : ''}
         ${limitClause}
    `;
}

function buildWhereClause({ search, category }) {
    const parts = [];
    if (category) parts.push(`AND bp.category = :category`);
    if (search)   parts.push(`AND (bp.business_name LIKE :search OR bp.description LIKE :search OR bp.address LIKE :search OR bp.category LIKE :search OR u.username LIKE :search)`);
    return parts.join('\n');
}

function buildReplacements({ search, category }) {
    const r = {};
    if (category) r.category = category;
    if (search)   r.search   = `%${search}%`;
    return r;
}

module.exports = { registerBusinessDirectoryRoutes };
