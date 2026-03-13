'use strict';

/**
 * Auth REST API — password reset + quick registration with email OTP.
 *
 * Endpoints (no access-token required — pre-login):
 *   POST /api/node/auth/request-password-reset  { email?, phone_number? }
 *   POST /api/node/auth/reset-password           { email?, phone_number?, code, new_password }
 *   POST /api/node/auth/quick-register           { email?, phone_number? }
 *   POST /api/node/auth/quick-verify             { email?, phone_number?, code }
 *
 * Email: SMTP support@worldmates.club, port 465 SSL.
 * mail.sthost.pro resolves to the server's own external IPs on this host,
 * so we attempt a cascade of SMTP endpoints (localhost first).
 * OTP:  6-digit, 10-minute TTL, stored in Redis (survives PM2 restarts).
 */

const nodemailer = require('nodemailer');
const md5        = require('md5');
const crypto     = require('crypto');
const redis      = require('redis');

// ─── Redis OTP client ──────────────────────────────────────────────────────────
// Окремий клієнт тільки для OTP — не заважає pub/sub клієнту в listeners.js.
// Підключається до того самого Redis що й основний сервер.
const _redisPass = process.env.REDIS_PASSWORD || '';
const otpRedis = redis.createClient({
    socket: { host: '127.0.0.1', port: 6379 },
    ...(_redisPass ? { password: _redisPass } : {}),
});
otpRedis.on('error', err => console.error('[Auth/Redis] Client error:', err.message));
otpRedis.connect().then(() => {
    console.log('[Auth/Redis] OTP Redis client connected');
}).catch(err => {
    console.error('[Auth/Redis] Failed to connect:', err.message);
});

const OTP_TTL_SECONDS = 10 * 60;   // 10 хвилин
const OTP_KEY_PREFIX  = 'wm:otp:'; // namespace key

// ─── SMTP — cascading hosts ────────────────────────────────────────────────────
// mail.sthost.pro DNS resolves to the server's own external IPs (195.22.131.11,
// 46.232.232.38). Hairpin NAT is blocked ⇒ we try localhost/127.0.0.1 first,
// then fall back to the external hostname if the mail daemon is reachable.
//
// Credentials are read from environment variables.
// Set in /etc/environment or pm2 ecosystem.config.js:
//   SMTP_USER=support@worldmates.club
//   SMTP_PASS=<password>

const SMTP_AUTH = {
    user: process.env.SMTP_USER || 'support@worldmates.club',
    pass: process.env.SMTP_PASS || '',
};

const SMTP_CANDIDATES = [
    // Same machine: connect directly to the local SMTP daemon
    { host: 'localhost',       port: 465, secure: true  },
    { host: '127.0.0.1',      port: 465, secure: true  },
    // Some servers expose STARTTLS on 587 from localhost
    { host: 'localhost',       port: 587, secure: false },
    { host: '127.0.0.1',      port: 587, secure: false },
    // External hostname last — works if hairpin NAT is allowed
    { host: 'mail.sthost.pro', port: 465, secure: true  },
];

async function sendEmail(to, subject, html) {
    let lastError;

    for (const candidate of SMTP_CANDIDATES) {
        try {
            const transporter = nodemailer.createTransport({
                ...candidate,
                auth: SMTP_AUTH,
                tls:  { rejectUnauthorized: false },
                connectionTimeout: 8000,
                greetingTimeout:   8000,
                socketTimeout:     10000
            });

            // Quick SMTP handshake verification before sending
            await transporter.verify();

            await transporter.sendMail({
                from:    '"WorldMates" <support@worldmates.club>',
                to,
                subject,
                html
            });

            console.log(`[SMTP] Sent to ${to} via ${candidate.host}:${candidate.port}`);
            return; // success — exit cascade
        } catch (err) {
            console.warn(`[SMTP] ${candidate.host}:${candidate.port} failed: ${err.message}`);
            lastError = err;
        }
    }

    // All candidates failed
    throw new Error(`SMTP delivery failed after ${SMTP_CANDIDATES.length} attempts. Last: ${lastError.message}`);
}

function emailTemplate(title, content) {
    return `<!DOCTYPE html>
<html><head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#f0f0f0;font-family:Arial,sans-serif">
<table width="100%" cellpadding="0" cellspacing="0">
  <tr><td align="center" style="padding:40px 20px">
    <table width="480" cellpadding="0" cellspacing="0"
           style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,.12)">
      <tr><td style="background:linear-gradient(135deg,#6200EA,#7C4DFF);padding:24px 32px">
        <h1 style="margin:0;color:#fff;font-size:22px;font-weight:700">WorldMates</h1>
      </td></tr>
      <tr><td style="padding:32px">
        <h2 style="color:#212121;margin:0 0 16px;font-size:20px">${title}</h2>
        ${content}
        <p style="margin:32px 0 0;color:#bbb;font-size:12px">© WorldMates · worldmates.club</p>
      </td></tr>
    </table>
  </td></tr>
</table>
</body></html>`;
}

function codeBlock(code) {
    return `<div style="text-align:center;margin:28px 0">
      <span style="display:inline-block;font-size:42px;font-weight:bold;letter-spacing:12px;
                   color:#6200EA;background:#f3e5ff;padding:16px 28px;border-radius:14px">
        ${code}
      </span>
    </div>
    <p style="color:#777;font-size:14px;margin:0">
      Код дійсний <strong>10 хвилин</strong>.<br>
      Якщо ви не робили цього запиту — просто проігноруйте лист.
    </p>`;
}

// ─── OTP store (Redis) ────────────────────────────────────────────────────────
// Формат ключа: wm:otp:{type}:{contact}
// Значення: JSON { code, userId, isNew, attempts }
// TTL: OTP_TTL_SECONDS секунд (встановлюється при SET, оновлюється при кожній
//       невдалій спробі щоб не дати часу на brute-force).

const OTP_MAX_ATTEMPTS = 5;

function otpRedisKey(type, contact) {
    return `${OTP_KEY_PREFIX}${type}:${contact.toLowerCase().trim()}`;
}

async function storeOtp(type, contact, code, extra = {}) {
    const key  = otpRedisKey(type, contact);
    const data = JSON.stringify({ code: String(code), attempts: 0, ...extra });
    // SET key value EX ttl — атомарна операція, TTL скидається при кожному новому запиті
    await otpRedis.set(key, data, { EX: OTP_TTL_SECONDS });
}

async function checkOtp(type, contact, code) {
    const key = otpRedisKey(type, contact);
    const raw = await otpRedis.get(key);

    if (!raw) return { ok: false, reason: 'expired' };

    let entry;
    try { entry = JSON.parse(raw); } catch { return { ok: false, reason: 'expired' }; }

    if (entry.attempts >= OTP_MAX_ATTEMPTS) {
        await otpRedis.del(key);
        return { ok: false, reason: 'locked' };
    }

    if (entry.code !== String(code).trim()) {
        entry.attempts += 1;
        // Зберігаємо оновлену кількість спроб; TTL залишаємо без змін (KEEPTTL)
        await otpRedis.set(key, JSON.stringify(entry), { KEEPTTL: true });
        return { ok: false, reason: 'invalid', attemptsLeft: OTP_MAX_ATTEMPTS - entry.attempts };
    }

    // Код правильний — видаляємо ключ
    await otpRedis.del(key);
    return { ok: true, entry };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function generateCode(len = 6) {
    const min = Math.pow(10, len - 1);
    const max = Math.pow(10, len) - 1;
    return String(min + crypto.randomInt(max - min + 1));
}

function generateToken() {
    return crypto.randomBytes(32).toString('hex');
}

function hashPassword(pw) {
    return md5(pw);
}

// Bypass Sequelize defaultScope (which excludes 'password', 'email_code')
function findUserByEmail(ctx, email) {
    return ctx.wo_users.unscoped().findOne({ where: { email } });
}

function findUserByPhone(ctx, phone) {
    return ctx.wo_users.unscoped().findOne({ where: { phone_number: phone } });
}

function findUserById(ctx, id) {
    return ctx.wo_users.findOne({ where: { user_id: id } });
}

// ─── POST /api/node/auth/request-password-reset ───────────────────────────────

function requestPasswordReset(ctx) {
    return async (req, res) => {
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();

        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: 'Вкажіть email або номер телефону' });
        }

        try {
            const user = email
                ? await findUserByEmail(ctx, email)
                : await findUserByPhone(ctx, phone);

            if (!user) {
                return res.json({ api_status: 400, error_message: 'Акаунт з таким email/телефоном не знайдено' });
            }

            const code       = generateCode(6);
            const contact    = email || phone;
            const targetEmail = email || user.email;

            await storeOtp('reset', contact, code, { userId: user.user_id });

            await sendEmail(
                targetEmail,
                'Відновлення доступу — WorldMates',
                emailTemplate('Відновлення доступу',
                    `<p style="color:#555">Ваш код підтвердження для відновлення доступу:</p>
                     ${codeBlock(code)}`
                )
            );

            console.log(`[Auth] Reset code sent → ${targetEmail} (user ${user.user_id})`);
            return res.json({ api_status: 200, message: `Код надіслано на ${targetEmail}` });

        } catch (err) {
            console.error('[Auth/reset-request]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера. Спробуйте пізніше.' });
        }
    };
}

// ─── POST /api/node/auth/reset-password ──────────────────────────────────────

function resetPassword(ctx) {
    return async (req, res) => {
        const email   = (req.body.email || '').trim().toLowerCase();
        const phone   = (req.body.phone_number || '').trim();
        const code    = (req.body.code || '').trim();
        const newPass = (req.body.new_password || '').trim();

        if (!code || !newPass) {
            return res.json({ api_status: 400, error_message: 'Вкажіть код та новий пароль' });
        }
        if (newPass.length < 6) {
            return res.json({ api_status: 400, error_message: 'Пароль надто короткий (мін. 6 символів)' });
        }

        const contact = email || phone;
        const result  = await checkOtp('reset', contact, code);

        if (!result.ok) {
            let msg;
            if (result.reason === 'invalid') {
                msg = result.attemptsLeft > 0
                    ? `Невірний код. Залишилось спроб: ${result.attemptsLeft}`
                    : 'Забагато невірних спроб. Запросіть новий код.';
            } else if (result.reason === 'locked') {
                msg = 'Код заблоковано через забагато невірних спроб. Запросіть новий.';
            } else {
                msg = 'Код прострочений. Запросіть новий.';
            }
            return res.json({ api_status: 400, error_message: msg });
        }

        try {
            // Use .unscoped() so the defaultScope (which may exclude 'password')
            // does not silently skip the column in the UPDATE statement.
            const [affectedRows] = await ctx.wo_users.unscoped().update(
                { password: hashPassword(newPass) },
                { where: { user_id: result.entry.userId } }
            );

            if (affectedRows === 0) {
                console.error(`[Auth] Password update affected 0 rows for user ${result.entry.userId}`);
                return res.json({ api_status: 500, error_message: 'Не вдалося оновити пароль. Спробуйте ще раз.' });
            }

            console.log(`[Auth] Password changed for user ${result.entry.userId} (${affectedRows} row updated)`);

            // Invalidate all existing sessions so old tokens stop working immediately.
            // This is standard security practice after a password change.
            try {
                const deletedSessions = await ctx.wo_appssessions.destroy({
                    where: { user_id: result.entry.userId }
                });
                console.log(`[Auth] Invalidated ${deletedSessions} session(s) for user ${result.entry.userId}`);
            } catch (sessionErr) {
                // Non-fatal: old sessions will expire naturally; log but don't fail the reset.
                console.warn(`[Auth] Could not invalidate sessions: ${sessionErr.message}`);
            }

            return res.json({ api_status: 200, message: 'Пароль успішно змінено' });

        } catch (err) {
            console.error('[Auth/reset-password]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера.' });
        }
    };
}

// ─── POST /api/node/auth/quick-register ──────────────────────────────────────

function quickRegister(ctx) {
    return async (req, res) => {
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();

        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: 'Вкажіть email або номер телефону' });
        }

        try {
            let user;
            let isNew = false;

            if (email) {
                user = await findUserByEmail(ctx, email);

                if (!user) {
                    // ── Create new account ───────────────────────────────────
                    let username;
                    for (let i = 0; i < 10; i++) {
                        const candidate = 'user_' + crypto.randomInt(100000, 999999).toString();
                        const exists = await ctx.wo_users.findOne({ where: { username: candidate } });
                        if (!exists) { username = candidate; break; }
                    }
                    if (!username) username = 'wm_' + Date.now().toString().slice(-8);

                    const displayName = email.split('@')[0]
                        .replace(/[^a-zA-Z0-9]/g, '')
                        .substring(0, 20) || 'User';
                    const now = Math.floor(Date.now() / 1000);

                    user = await ctx.wo_users.create({
                        username,
                        email,
                        password:  hashPassword(generateToken().slice(0, 16)),
                        first_name: displayName,
                        last_name:  '',
                        lastseen:   now,
                        active:     '1',
                        registered: new Date().toLocaleDateString('en-US'),
                        joined:     now
                    });
                    isNew = true;
                    console.log(`[Auth] New user created: ${username} (${email})`);
                }

            } else {
                // Phone — only for existing users (no SMS gateway yet)
                user = await findUserByPhone(ctx, phone);
                if (!user) {
                    return res.json({
                        api_status: 400,
                        error_message: 'Акаунт з таким номером не знайдено. Пройдіть стандартну реєстрацію.'
                    });
                }
            }

            const code        = generateCode(6);
            const contact     = email || phone;
            const targetEmail = email || user.email;

            await storeOtp('quick', contact, code, { userId: user.user_id, isNew });

            await sendEmail(
                targetEmail,
                isNew ? 'Ласкаво просимо до WorldMates!' : 'Код для входу — WorldMates',
                emailTemplate(
                    isNew ? 'Ласкаво просимо!' : 'Код для входу',
                    `<p style="color:#555">${isNew
                        ? 'Ваш акаунт WorldMates створено! Підтвердіть реєстрацію кодом:'
                        : 'Ваш одноразовий код для входу до WorldMates:'
                    }</p>
                    ${codeBlock(code)}`
                )
            );

            return res.json({
                api_status: 200,
                message:    `Код підтвердження надіслано на ${targetEmail}`,
                user_id:    user.user_id,
                username:   user.username
            });

        } catch (err) {
            console.error('[Auth/quick-register]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера. Спробуйте пізніше.' });
        }
    };
}

// ─── POST /api/node/auth/quick-verify ────────────────────────────────────────

function quickVerify(ctx) {
    return async (req, res) => {
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();
        const code  = (req.body.code || '').trim();

        if (!code) {
            return res.json({ api_status: 400, error_message: 'Вкажіть код підтвердження' });
        }

        const contact = email || phone;
        const result  = await checkOtp('quick', contact, code);

        if (!result.ok) {
            let msg;
            if (result.reason === 'invalid') {
                msg = result.attemptsLeft > 0
                    ? `Невірний код. Залишилось спроб: ${result.attemptsLeft}`
                    : 'Забагато невірних спроб. Запросіть новий код.';
            } else if (result.reason === 'locked') {
                msg = 'Код заблоковано через забагато невірних спроб. Запросіть новий.';
            } else {
                msg = 'Код прострочений. Запросіть новий.';
            }
            return res.json({ api_status: 400, error_message: msg });
        }

        try {
            const user = await findUserById(ctx, result.entry.userId);
            if (!user) {
                return res.json({ api_status: 400, error_message: 'Акаунт не знайдено' });
            }

            const token = generateToken();
            const now   = Math.floor(Date.now() / 1000);

            await ctx.wo_appssessions.create({
                user_id:          user.user_id,
                session_id:       token,
                platform:         'phone',
                platform_details: 'quick-auth',
                time:             now
            });

            console.log(`[Auth] Session created for user ${user.user_id}`);
            return res.json({
                api_status:   200,
                access_token: token,
                user_id:      user.user_id,
                username:     user.username,
                avatar:       user.avatar,
                message:      'Авторизація успішна'
            });

        } catch (err) {
            console.error('[Auth/quick-verify]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера.' });
        }
    };
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerAuthRoutes(app, ctx) {
    // No auth middleware — these endpoints are for unauthenticated users
    app.post('/api/node/auth/request-password-reset', requestPasswordReset(ctx));
    app.post('/api/node/auth/reset-password',         resetPassword(ctx));
    app.post('/api/node/auth/quick-register',         quickRegister(ctx));
    app.post('/api/node/auth/quick-verify',           quickVerify(ctx));

    console.log('[Auth API] Registered:');
    console.log('  POST /api/node/auth/request-password-reset');
    console.log('  POST /api/node/auth/reset-password');
    console.log('  POST /api/node/auth/quick-register');
    console.log('  POST /api/node/auth/quick-verify');
}

module.exports = { registerAuthRoutes };
