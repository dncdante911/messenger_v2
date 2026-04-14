'use strict';

/**
 * Delete Account — replaces PHP /api/v2/endpoints/delete-user.php
 *
 * DELETE /api/node/user/account   { password }
 *   Verifies the user's password then permanently deletes the account row.
 *   Supports bcrypt ($2y$/$2b$), sha1 (40 hex), and md5 (32 hex) hashes.
 */

const crypto   = require('crypto');
const bcrypt   = require('bcryptjs');
const md5      = require('md5');
const { requireAuth } = require('../../helpers/validate-token');

// Same multi-hash verifier used in auth.js
async function verifyPassword(plaintext, storedHash) {
    if (!plaintext || !storedHash) return false;
    if (/^\$2[aby]\$/.test(storedHash)) {
        return bcrypt.compare(plaintext, storedHash);
    }
    if (/^[0-9a-f]{40}$/i.test(storedHash)) {
        return crypto.createHash('sha1').update(plaintext).digest('hex') === storedHash.toLowerCase();
    }
    if (/^[0-9a-f]{32}$/i.test(storedHash)) {
        return md5(plaintext) === storedHash.toLowerCase();
    }
    return false;
}

function registerDeleteAccountRoutes(app, ctx) {

    // DELETE /api/node/user/account
    app.delete('/api/node/user/account', requireAuth(ctx), async (req, res) => {
        try {
            const userId  = req.userId;
            const password = (req.body.password || '').trim();

            if (!password) {
                return res.json({ api_status: 400, error_message: 'Password is required' });
            }

            // Fetch the user (unscoped to access the password and type fields)
            const user = await ctx.wo_users.unscoped().findOne({
                where:      { user_id: userId },
                attributes: ['user_id', 'password', 'type'],
                raw:        true,
            });

            if (!user) {
                return res.json({ api_status: 404, error_message: 'User not found' });
            }

            // Guard: never allow deletion of system bot accounts.
            // Bot users (type='bot') are created by the server itself — deleting them
            // breaks bot routing in PrivateMessageController and causes silent failures.
            if (user.type === 'bot') {
                return res.json({ api_status: 403, error_message: 'System accounts cannot be deleted' });
            }

            const ok = await verifyPassword(password, user.password);
            if (!ok) {
                return res.json({ api_status: 400, error_message: 'Incorrect password' });
            }

            // Permanently delete the user row
            await ctx.wo_users.destroy({ where: { user_id: userId } });

            // Also invalidate all sessions for this user
            try {
                await ctx.wo_appssessions.destroy({ where: { user_id: userId } });
            } catch (_) {}

            res.json({ api_status: 200, message: 'Account deleted' });
        } catch (err) {
            console.error('[DeleteAccount] error:', err.message);
            res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerDeleteAccountRoutes };
