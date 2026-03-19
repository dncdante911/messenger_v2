'use strict';

/**
 * Session management — replaces PHP /api/v2/endpoints/sessions.php
 *
 * GET    /api/node/sessions         → list all active sessions for the current user
 * DELETE /api/node/sessions/:id     → terminate a specific session by ID
 * DELETE /api/node/sessions         → terminate ALL other sessions (keep current)
 */

const { requireAuth } = require('../../helpers/validate-token');

function registerSessionRoutes(app, ctx) {
    // ── GET /api/node/sessions ────────────────────────────────────────────────
    app.get('/api/node/sessions', requireAuth(ctx), async (req, res) => {
        try {
            const userId = req.userId;
            const now    = Math.floor(Date.now() / 1000);

            const rows = await ctx.wo_appssessions.findAll({
                where: {
                    user_id:    userId,
                    // Exclude expired sessions (or include ones with no expiry)
                    ...(ctx.sequelize.literal ? {} : {}),
                },
                attributes: ['id', 'platform', 'platform_details', 'time', 'ip_address', 'expires_at'],
                order:      [['time', 'DESC']],
                raw:        true,
            });

            // Filter out expired sessions in JS (avoids raw SQL complexity)
            const active = rows.filter(s => !s.expires_at || s.expires_at > now);

            const sessions = active.map(s => {
                let deviceName = null;
                if (s.platform_details) {
                    try {
                        const pd = JSON.parse(s.platform_details);
                        deviceName = pd.device_name || pd.deviceName || pd.model || null;
                    } catch (_) {}
                }
                return {
                    id:          s.id,
                    platform:    s.platform || '',
                    device_name: deviceName,
                    ip:          s.ip_address || null,
                    time:        s.time || 0,
                    is_current:  req.accessToken
                        ? (async () => false)()   // resolved below
                        : false,
                };
            });

            // Mark the session that corresponds to the current token
            const currentSession = await ctx.wo_appssessions.findOne({
                where: { session_id: req.accessToken },
                attributes: ['id'],
                raw: true,
            });
            const currentId = currentSession?.id ?? null;

            const result = sessions.map(s => ({
                ...s,
                is_current: s.id === currentId,
            }));

            res.json({ api_status: 200, sessions: result });
        } catch (err) {
            console.error('[Sessions] GET error:', err.message);
            res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── DELETE /api/node/sessions/:id ─────────────────────────────────────────
    app.delete('/api/node/sessions/:id', requireAuth(ctx), async (req, res) => {
        try {
            const userId    = req.userId;
            const sessionId = parseInt(req.params.id, 10);

            if (!sessionId || isNaN(sessionId)) {
                return res.json({ api_status: 400, error_message: 'Invalid session id' });
            }

            // Prevent user from deleting someone else's session
            const session = await ctx.wo_appssessions.findOne({
                where: { id: sessionId, user_id: userId },
                attributes: ['id', 'session_id'],
                raw: true,
            });

            if (!session) {
                return res.json({ api_status: 404, error_message: 'Session not found' });
            }

            // Prevent deleting the currently-active session
            if (session.session_id === req.accessToken) {
                return res.json({ api_status: 400, error_message: 'Cannot terminate the current session. Use logout instead.' });
            }

            await ctx.wo_appssessions.destroy({ where: { id: sessionId, user_id: userId } });

            res.json({ api_status: 200, message: 'Session terminated' });
        } catch (err) {
            console.error('[Sessions] DELETE/:id error:', err.message);
            res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── DELETE /api/node/sessions (terminate all OTHER sessions) ─────────────
    app.delete('/api/node/sessions', requireAuth(ctx), async (req, res) => {
        try {
            const userId = req.userId;
            const { Op }  = require('sequelize');

            const deleted = await ctx.wo_appssessions.destroy({
                where: {
                    user_id:    userId,
                    session_id: { [Op.ne]: req.accessToken },
                },
            });

            res.json({ api_status: 200, message: `Terminated ${deleted} session(s)` });
        } catch (err) {
            console.error('[Sessions] DELETE all error:', err.message);
            res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerSessionRoutes };
