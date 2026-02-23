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
 * Email: SMTP via mail.sthost.pro:465 SSL (support@worldmates.club)
 * OTP:   6-digit, 10-minute TTL, stored in-memory Map
 */

const nodemailer = require('nodemailer');
const md5        = require('md5');
const crypto     = require('crypto');

// ─── SMTP ─────────────────────────────────────────────────────────────────────

const SMTP = {
    host: 'mail.sthost.pro',
    port: 465,
    secure: true,
    auth: {
        user: 'support@worldmates.club',
        pass: '3344Frz@q0607'
    },
    tls: { rejectUnauthorized: false }
};

async function sendEmail(to, subject, html) {
    const transporter = nodemailer.createTransport(SMTP);
    await transporter.sendMail({
        from: '"WorldMates" <support@worldmates.club>',
        to,
        subject,
        html
    });
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

// ─── OTP store ────────────────────────────────────────────────────────────────

// key: `${type}:${normalised_contact}` → { code, userId, expiresAt, ... }
const otpStore = new Map();

// Auto-clean expired entries every 5 minutes
setInterval(() => {
    const now = Date.now();
    for (const [k, v] of otpStore) {
        if (v.expiresAt < now) otpStore.delete(k);
    }
}, 5 * 60 * 1000);

function otpKey(type, contact) {
    return `${type}:${contact.toLowerCase().trim()}`;
}

function storeOtp(type, contact, code, extra = {}) {
    otpStore.set(otpKey(type, contact), {
        code: String(code),
        expiresAt: Date.now() + 10 * 60 * 1000,
        ...extra
    });
}

function checkOtp(type, contact, code) {
    const key   = otpKey(type, contact);
    const entry = otpStore.get(key);
    if (!entry) return { ok: false, reason: 'expired' };
    if (Date.now() > entry.expiresAt) {
        otpStore.delete(key);
        return { ok: false, reason: 'expired' };
    }
    if (entry.code !== String(code).trim()) return { ok: false, reason: 'invalid' };
    otpStore.delete(key);
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

            storeOtp('reset', contact, code, { userId: user.user_id });

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
        const result  = checkOtp('reset', contact, code);

        if (!result.ok) {
            const msg = result.reason === 'invalid'
                ? 'Невірний код підтвердження'
                : 'Код прострочений. Запросіть новий.';
            return res.json({ api_status: 400, error_message: msg });
        }

        try {
            await ctx.wo_users.update(
                { password: hashPassword(newPass) },
                { where: { user_id: result.entry.userId } }
            );
            console.log(`[Auth] Password changed for user ${result.entry.userId}`);
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

            storeOtp('quick', contact, code, { userId: user.user_id, isNew });

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
        const result  = checkOtp('quick', contact, code);

        if (!result.ok) {
            const msg = result.reason === 'invalid'
                ? 'Невірний код підтвердження'
                : 'Код прострочений. Запросіть новий.';
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
