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
 * Returns userId (integer) or null if invalid / expired.
 * Tokens created before the expiry feature was added (expires_at = null) are
 * treated as perpetually valid to ensure backwards-compatibility during rollout.
 */
async function validateToken(ctx, token) {
    if (!token || typeof token !== 'string' || token.length < 10) return null;
    try {
        const session = await ctx.wo_appssessions.findOne({
            where:      { session_id: token },
            attributes: ['user_id', 'expires_at'],
            raw:        true,
        });
        if (!session) return null;
        // If expires_at is set and in the past, token has expired
        if (session.expires_at && session.expires_at < Math.floor(Date.now() / 1000)) {
            return null;
        }
        return session.user_id;
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

        if (!token || token.length < 10) {
            return res.json({ api_status: 400, error_id: '0', error_message: 'Access token is not valid' });
        }

        try {
            const session = await ctx.wo_appssessions.findOne({
                where:      { session_id: token },
                attributes: ['user_id', 'expires_at'],
                raw:        true,
            });

            if (!session) {
                return res.json({ api_status: 400, error_id: '0', error_message: 'Access token is not valid' });
            }

            if (session.expires_at && session.expires_at < Math.floor(Date.now() / 1000)) {
                // error_id '401' signals the client to attempt a token refresh
                return res.json({ api_status: 401, error_id: '401', error_message: 'Access token has expired' });
            }

            req.userId      = session.user_id;
            req.accessToken = token;
            next();
        } catch {
            return res.json({ api_status: 400, error_id: '0', error_message: 'Access token is not valid' });
        }
    };
}

module.exports = { validateToken, requireAuth };
