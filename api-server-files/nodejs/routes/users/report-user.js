'use strict';

/**
 * Report User — replaces PHP /api/v2/endpoints/report_user.php
 *
 * POST /api/node/user/report   { user_id, text }
 *   Creates a record in Wo_Reports for the reported user.
 *   Requires a valid access token (requireAuth).
 */

const { requireAuth } = require('../../helpers/validate-token');

function registerReportUserRoutes(app, ctx) {
    const { db } = ctx;

    app.post('/api/node/user/report', requireAuth(ctx), async (req, res) => {
        try {
            const reporterId = req.user.user_id;
            const userId     = parseInt(req.body.user_id, 10);
            const text       = (req.body.text || '').trim();

            if (!userId || userId <= 0) {
                return res.json({ api_status: 400, error_message: 'Invalid user_id' });
            }

            // Prevent self-reporting
            if (userId === reporterId) {
                return res.json({ api_status: 400, error_message: 'Cannot report yourself' });
            }

            // Check target user exists
            const target = await db.Wo_Users.findOne({ where: { user_id: userId } });
            if (!target) {
                return res.json({ api_status: 404, error_message: 'User not found' });
            }

            await db.Wo_Reports.create({
                user_id:    userId,
                profile_id: reporterId,
                text:       text || null,
                time:       Math.floor(Date.now() / 1000),
                seen:       0
            });

            return res.json({ api_status: 200, message: 'Report submitted' });
        } catch (err) {
            console.error('[report-user]', err);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerReportUserRoutes };
