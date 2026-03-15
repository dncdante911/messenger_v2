'use strict';

/**
 * User Rating (karma / trust system)
 *
 * GET  /api/node/users/:id/rating             Get rating for any user
 * POST /api/node/users/:id/rate               Like / dislike / remove vote
 *                                             body: { rating_type: "like"|"dislike" , comment? }
 *                                             Send same rating_type again → removes vote (toggle)
 */

const { requireAuth } = require('../../helpers/validate-token');
const { t }           = require('../../helpers/i18n');
const { Op }          = require('sequelize');

// ─── Trust level calculator ───────────────────────────────────────────────────

function calcTrustLevel(likes, dislikes, L) {
    const total = likes + dislikes;
    const pct   = total > 0 ? (likes / total) * 100 : 0;

    if (total >= 20 && pct >= 80) {
        return { level: 'verified',  label: L.trust_verified,  emoji: '✅', color: '#4CAF50' };
    }
    if (total >= 5 && pct >= 65) {
        return { level: 'trusted',   label: L.trust_trusted,   emoji: '🟢', color: '#8BC34A' };
    }
    if (total >= 5 && pct < 40) {
        return { level: 'untrusted', label: L.trust_untrusted, emoji: '🔴', color: '#F44336' };
    }
    return       { level: 'neutral',   label: L.trust_neutral,   emoji: '🔵', color: '#9E9E9E' };
}

function buildRatingObject(ctx, ratedUserId, rows, myRow, user, L) {
    const likes    = rows.filter(r => r.rating_type === 'like').length;
    const dislikes = rows.filter(r => r.rating_type === 'dislike').length;
    const total    = likes + dislikes;
    const score    = total > 0 ? parseFloat(((likes / total) * 5).toFixed(2)) : 0;
    const trust    = calcTrustLevel(likes, dislikes, L);

    const mediaUrl = (path) => {
        if (!path || /^https?:\/\//.test(path)) return path || '';
        const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
        return `${base}/${path.replace(/^\//, '')}`;
    };

    return {
        user_id:             ratedUserId,
        username:            user?.username || '',
        name:                `${user?.first_name || ''} ${user?.last_name || ''}`.trim(),
        avatar:              mediaUrl(user?.avatar),
        likes,
        dislikes,
        score,
        trust_level:         trust.level,
        trust_level_label:   trust.label,
        trust_level_emoji:   trust.emoji,
        trust_level_color:   trust.color,
        total_ratings:       total,
        like_percentage:     total > 0 ? parseFloat(((likes    / total) * 100).toFixed(1)) : 0,
        dislike_percentage:  total > 0 ? parseFloat(((dislikes / total) * 100).toFixed(1)) : 0,
        my_rating:           myRow ? {
            type:       myRow.rating_type,
            comment:    myRow.comment || null,
            created_at: new Date(myRow.created_at * 1000).toISOString(),
        } : null,
    };
}

// ─── GET /api/node/users/:id/rating ──────────────────────────────────────────

function getUserRating(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const L             = t(req);
        const targetId      = parseInt(req.params.id);
        const includeList   = req.query.include_details === '1' || req.query.include_details === 'true';
        const limit         = Math.min(parseInt(req.query.limit) || 50, 200);

        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        try {
            const [user, rows] = await Promise.all([
                ctx.wo_users.findOne({ where: { user_id: targetId }, attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'], raw: true }),
                ctx.wm_user_ratings.findAll({ where: { rated_user_id: targetId }, raw: true }),
            ]);

            if (!user) return res.json({ api_status: 404, error_message: 'User not found' });

            const myRow = rows.find(r => r.rater_id === req.userId) || null;
            const rating = buildRatingObject(ctx, targetId, rows, myRow, user, L);

            const resp = { api_status: 200, rating, ratings_count: rows.length };

            if (includeList) {
                // Collect rater user_ids and fetch their info in bulk
                const raterIds = rows.map(r => r.rater_id);
                const raters   = raterIds.length
                    ? await ctx.wo_users.findAll({
                        where: { user_id: { [Op.in]: raterIds } },
                        attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                        limit,
                        raw: true,
                    })
                    : [];
                const raterMap = Object.fromEntries(raters.map(u => [u.user_id, u]));
                const mediaUrl = (path) => {
                    if (!path || /^https?:\/\//.test(path)) return path || '';
                    const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
                    return `${base}/${path.replace(/^\//, '')}`;
                };

                resp.ratings_list = rows.slice(0, limit).map(r => {
                    const rater = raterMap[r.rater_id] || {};
                    return {
                        id:           r.id,
                        rater_id:     r.rater_id,
                        rater_username: rater.username || '',
                        rater_name:   `${rater.first_name || ''} ${rater.last_name || ''}`.trim(),
                        rater_avatar: mediaUrl(rater.avatar),
                        rating_type:  r.rating_type,
                        comment:      r.comment || null,
                        created_at:   new Date(r.created_at * 1000).toISOString(),
                    };
                });
            }

            return res.json(resp);

        } catch (err) {
            console.error('[Rating/get]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/users/:id/rate ───────────────────────────────────────────

function rateUser(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const L          = t(req);
        const targetId   = parseInt(req.params.id);
        const ratingType = (req.body.rating_type || '').trim();
        const comment    = (req.body.comment || '').trim().substring(0, 500) || null;

        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });
        if (targetId === req.userId) return res.json({ api_status: 400, error_message: 'Cannot rate yourself' });
        if (!['like', 'dislike'].includes(ratingType)) {
            return res.json({ api_status: 400, error_message: 'rating_type must be "like" or "dislike"' });
        }

        try {
            const user = await ctx.wo_users.findOne({
                where: { user_id: targetId, active: '1' },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                raw: true,
            });
            if (!user) return res.json({ api_status: 404, error_message: 'User not found' });

            const now      = Math.floor(Date.now() / 1000);
            const existing = await ctx.wm_user_ratings.findOne({
                where: { rater_id: req.userId, rated_user_id: targetId }
            });

            let action;

            if (existing) {
                if (existing.rating_type === ratingType) {
                    // Same vote → toggle off (remove)
                    await existing.destroy();
                    action = 'removed';
                } else {
                    // Different vote → update
                    await existing.update({ rating_type: ratingType, comment, updated_at: now });
                    action = 'updated';
                }
            } else {
                await ctx.wm_user_ratings.create({
                    rater_id:      req.userId,
                    rated_user_id: targetId,
                    rating_type:   ratingType,
                    comment,
                    created_at:    now,
                    updated_at:    now,
                });
                action = 'added';
            }

            // Rebuild rating object
            const rows  = await ctx.wm_user_ratings.findAll({ where: { rated_user_id: targetId }, raw: true });
            const myRow = rows.find(r => r.rater_id === req.userId) || null;
            const userRating = buildRatingObject(ctx, targetId, rows, myRow, user, L);

            const actionMsg = action === 'removed' ? L.rating_removed
                            : action === 'updated' ? L.rating_updated
                            : L.rating_added;

            return res.json({
                api_status:  200,
                message:     actionMsg,
                action,
                rating_type: action === 'removed' ? null : ratingType,
                user_rating: userRating,
            });

        } catch (err) {
            console.error('[Rating/rate]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerRatingRoutes(app, ctx) {
    app.get ('/api/node/users/:id/rating', getUserRating(ctx));
    app.post('/api/node/users/:id/rate',   rateUser(ctx));

    console.log('[Rating] Routes registered:');
    console.log('  GET  /api/node/users/:id/rating');
    console.log('  POST /api/node/users/:id/rate');
}

module.exports = { registerRatingRoutes };
