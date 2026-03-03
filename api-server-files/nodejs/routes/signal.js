'use strict';

/**
 * Signal Protocol — REST endpoints.
 *
 * All endpoints require a valid access-token header (same auth as private-chats).
 *
 *   POST /api/node/signal/register     — upload identity + pre-key bundle
 *   GET  /api/node/signal/bundle/:id   — fetch pre-key bundle for another user
 *   POST /api/node/signal/replenish    — upload new one-time pre-keys
 *   GET  /api/node/signal/prekey-count — how many OPKs remain on server for me
 *
 * The server is a "pre-key server" only — it stores and distributes PUBLIC keys.
 * Private keys never leave devices.
 * Messages encrypted with cipher_version=3 are stored opaque; the server cannot
 * decrypt them (true end-to-end encryption).
 */

const signalStore = require('../helpers/signal-store');

// ─── POST /api/node/signal/register ──────────────────────────────────────────

function register(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const {
                identity_key,
                signed_prekey_id,
                signed_prekey,
                signed_prekey_sig,
                prekeys: prekeysRaw,
            } = req.body;

            if (!identity_key || !signed_prekey) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'identity_key and signed_prekey are required',
                });
            }

            // Parse prekeys — may arrive as JSON string or as parsed object
            let prekeys = [];
            if (prekeysRaw) {
                try {
                    prekeys = typeof prekeysRaw === 'string'
                        ? JSON.parse(prekeysRaw)
                        : prekeysRaw;
                } catch {
                    return res.status(400).json({
                        api_status:    400,
                        error_message: 'prekeys must be a valid JSON array',
                    });
                }
            }

            const result = await signalStore.saveKeyBundle(ctx, userId, {
                identityKey:      identity_key,
                signedPreKeyId:   parseInt(signed_prekey_id) || 0,
                signedPreKey:     signed_prekey,
                signedPreKeySig:  signed_prekey_sig || '',
                prekeys,
            });

            console.log(`[Signal] User ${userId} registered key bundle (OPKs: ${prekeys.length})`);
            res.json({ api_status: 200, message: 'Key bundle registered', opk_count: result.opk_count });

        } catch (err) {
            console.error('[Signal/register]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to register key bundle' });
        }
    };
}

// ─── GET /api/node/signal/bundle/:userId ──────────────────────────────────────

function getBundle(ctx) {
    return async (req, res) => {
        try {
            const targetUserId = parseInt(req.params.userId);
            if (!targetUserId || isNaN(targetUserId)) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'userId path parameter is required',
                });
            }

            const bundle = await signalStore.getKeyBundle(ctx, targetUserId);
            if (!bundle) {
                return res.status(404).json({
                    api_status:    404,
                    error_message: 'No Signal key bundle registered for this user',
                });
            }

            console.log(`[Signal] Bundle fetched for user ${targetUserId} ` +
                        `(remaining OPKs: ${bundle.remaining_prekeys})`);
            res.json({ api_status: 200, ...bundle });

        } catch (err) {
            console.error('[Signal/bundle]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch key bundle' });
        }
    };
}

// ─── POST /api/node/signal/replenish ─────────────────────────────────────────

function replenish(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            let prekeysRaw  = req.body.prekeys;

            if (!prekeysRaw) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'prekeys is required',
                });
            }

            let prekeys;
            try {
                prekeys = typeof prekeysRaw === 'string'
                    ? JSON.parse(prekeysRaw)
                    : prekeysRaw;
            } catch {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'prekeys must be a valid JSON array',
                });
            }

            const result = await signalStore.replenishPrekeys(ctx, userId, prekeys);
            if (!result) {
                return res.status(404).json({
                    api_status:    404,
                    error_message: 'User has no Signal key bundle (register first)',
                });
            }

            console.log(`[Signal] User ${userId} replenished OPKs: +${result.added} (total: ${result.total})`);
            res.json({
                api_status: 200,
                message:    `Added ${result.added} one-time pre-keys`,
                count:      result.total,
            });

        } catch (err) {
            console.error('[Signal/replenish]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to replenish pre-keys' });
        }
    };
}

// ─── GET /api/node/signal/prekey-count ───────────────────────────────────────

function preKeyCount(ctx) {
    return async (req, res) => {
        try {
            const count = await signalStore.getRemainingPreKeyCount(ctx, req.userId);
            res.json({ api_status: 200, count });
        } catch (err) {
            console.error('[Signal/prekey-count]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get pre-key count' });
        }
    };
}

// ─── auth middleware (same logic as private-chats/index.js) ──────────────────

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
        console.error('[Signal/auth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── route registration ───────────────────────────────────────────────────────

/**
 * Register Signal Protocol routes on [app].
 * Requires ctx.signal_keys (Sequelize model) and ctx.wo_appssessions (for auth).
 */
function registerSignalRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.post('/api/node/signal/register',       auth, register(ctx));
    app.get ('/api/node/signal/bundle/:userId',  auth, getBundle(ctx));
    app.post('/api/node/signal/replenish',       auth, replenish(ctx));
    app.get ('/api/node/signal/prekey-count',    auth, preKeyCount(ctx));

    console.log('[Signal] Routes registered: register, bundle, replenish, prekey-count');
}

module.exports = { registerSignalRoutes };
