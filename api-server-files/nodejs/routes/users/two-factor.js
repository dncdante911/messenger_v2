'use strict';

/**
 * Two-Factor Authentication — replaces PHP /api/v2/endpoints/update_two_factor.php
 *
 * POST /api/node/user/2fa
 *   type=verify  → enable 2FA: store google_secret, set two_factor=1, two_factor_verified=1
 *   type=disable → disable 2FA: clear google_secret, set two_factor=0, two_factor_verified=0
 */

const crypto = require('crypto');
const { requireAuth } = require('../../helpers/validate-token');

// ─── TOTP helpers (no external library required) ──────────────────────────────

function base32Decode(input) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = 0, value = 0;
    const output = [];
    for (const ch of input.replace(/=+$/, '').toUpperCase()) {
        const idx = chars.indexOf(ch);
        if (idx < 0) continue;
        value = (value << 5) | idx;
        bits += 5;
        if (bits >= 8) {
            output.push((value >>> (bits - 8)) & 0xff);
            bits -= 8;
        }
    }
    return Buffer.from(output);
}

function hotp(key, counter) {
    const buf = Buffer.alloc(8);
    let tmp = counter;
    for (let i = 7; i >= 0; i--) { buf[i] = tmp & 0xff; tmp = Math.floor(tmp / 256); }
    const hmac = crypto.createHmac('sha1', key).update(buf).digest();
    const offset = hmac[19] & 0xf;
    const code = ((hmac[offset] & 0x7f) << 24) | ((hmac[offset + 1] & 0xff) << 16) |
                 ((hmac[offset + 2] & 0xff) << 8)  |  (hmac[offset + 3] & 0xff);
    return (code % 1000000).toString().padStart(6, '0');
}

function verifyTOTP(secret, token) {
    try {
        const key = base32Decode(secret);
        const counter = Math.floor(Date.now() / 1000 / 30);
        for (let delta = -1; delta <= 1; delta++) {
            if (hotp(key, counter + delta) === token) return true;
        }
    } catch (_) {}
    return false;
}

// ─── Route registration ────────────────────────────────────────────────────────

function registerTwoFactorRoutes(app, ctx) {

    // POST /api/node/user/2fa
    app.post('/api/node/user/2fa', requireAuth(ctx), async (req, res) => {
        try {
            const userId = req.userId;
            const type   = (req.body.type || '').trim().toLowerCase();

            if (type === 'verify') {
                // Enable 2FA
                const secret       = (req.body.secret        || '').trim();
                const code         = (req.body.code          || '').trim();
                const factorMethod = (req.body.factor_method || 'google').trim();

                if (!secret || !code) {
                    return res.json({ api_status: 400, error_message: 'secret and code are required' });
                }
                if (!/^\d{6}$/.test(code)) {
                    return res.json({ api_status: 400, error_message: 'Invalid code format' });
                }
                if (!verifyTOTP(secret, code)) {
                    return res.json({ api_status: 400, error_message: 'Invalid TOTP code' });
                }

                await ctx.wo_users.update(
                    {
                        google_secret:       secret,
                        two_factor:          1,
                        two_factor_verified: 1,
                        two_factor_method:   factorMethod,
                    },
                    { where: { user_id: userId } }
                );

                return res.json({ api_status: 200, message: '2FA enabled' });

            } else {
                // Disable 2FA (type=disable or no type)
                await ctx.wo_users.update(
                    {
                        google_secret:       '',
                        two_factor:          0,
                        two_factor_verified: 0,
                        two_factor_method:   '',
                    },
                    { where: { user_id: userId } }
                );

                return res.json({ api_status: 200, message: '2FA disabled' });
            }
        } catch (err) {
            console.error('[2FA] POST error:', err.message);
            res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerTwoFactorRoutes };
