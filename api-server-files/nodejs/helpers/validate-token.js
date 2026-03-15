'use strict';

/**
 * Shared token validation helper.
 * Used by all routes that require authentication.
 *
 * Usage:
 *   const { requireAuth } = require('../helpers/validate-token');
 *   app.get('/some/route', requireAuth(ctx), handler);
 *
 * After middleware runs, req.userId contains the authenticated user's ID.
 */

/**
 * Validate an access token against Wo_AppsSessions.
 * Returns userId (integer) or null if invalid.
 */
async function validateToken(ctx, token) {
    if (!token || typeof token !== 'string' || token.length < 10) return null;
    try {
        const session = await ctx.wo_appssessions.findOne({
            where:      { session_id: token },
            attributes: ['user_id'],
            raw:        true,
        });
        return session ? session.user_id : null;
    } catch {
        return null;
    }
}

/**
 * Express middleware that enforces authentication.
 * Reads the token from (in order):
 *   1. Header:  access-token
 *   2. Body:    access_token
 *   3. Query:   access_token
 *
 * On success sets req.userId and req.accessToken, then calls next().
 * On failure responds with { api_status: 400, error_message: '...' }.
 */
function requireAuth(ctx) {
    return async (req, res, next) => {
        const token = (
            req.headers['access-token'] ||
            req.body?.access_token       ||
            req.query.access_token       ||
            ''
        ).trim();

        const userId = await validateToken(ctx, token);

        if (!userId) {
            return res.json({ api_status: 400, error_id: '0', error_message: 'Access token is not valid' });
        }

        req.userId      = userId;
        req.accessToken = token;
        next();
    };
}

module.exports = { validateToken, requireAuth };
