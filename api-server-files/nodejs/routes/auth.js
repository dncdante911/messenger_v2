'use strict';

/**
 * Auth REST API — login, logout, password reset, quick registration.
 *
 * Endpoints (no access-token required — pre-login):
 *   POST /api/node/auth/login                  { username, password }
 *   POST /api/node/auth/logout                 { } + header access-token
 *   POST /api/node/auth/request-password-reset { email?, phone_number? }
 *   POST /api/node/auth/reset-password         { email?, phone_number?, code, new_password }
 *   POST /api/node/auth/quick-register         { email?, phone_number? }
 *   POST /api/node/auth/quick-verify           { email?, phone_number?, code }
 *
 * Password hashing: bcrypt (PHP PASSWORD_DEFAULT) with MD5/SHA1 legacy fallback.
 * On login with a legacy hash the password is automatically re-hashed to bcrypt.
 *
 * Email: настроюється через .env (SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS)
 * SMS:   Twilio (TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM)
 * OTP:   6-digit, 10-minute TTL, stored in Redis.
 */

const nodemailer = require('nodemailer');
const bcrypt     = require('bcryptjs');
const md5        = require('md5');
const crypto     = require('crypto');
const redis      = require('redis');
const { t }      = require('../helpers/i18n');

const BCRYPT_ROUNDS = 10;

// ─── Redis OTP client ──────────────────────────────────────────────────────────
// reconnectStrategy: exponential backoff up to 5 s so the client auto-heals
// after Redis restarts (e.g. PM2 pm2 restart redis / system reboot).
const _redisPass = process.env.REDIS_PASSWORD || '';
const otpRedis = redis.createClient({
    socket: {
        host: process.env.REDIS_HOST || '127.0.0.1',
        port: parseInt(process.env.REDIS_PORT) || 6379,
        reconnectStrategy: (retries) => Math.min(retries * 150, 5000),
    },
    ...(_redisPass ? { password: _redisPass } : {}),
});
otpRedis.on('error',        err => console.error('[Auth/Redis] OTP client error:', err.message));
otpRedis.on('reconnecting', ()  => console.warn('[Auth/Redis] OTP client reconnecting…'));
otpRedis.on('ready',        ()  => console.log('[Auth/Redis] OTP client ready'));
otpRedis.connect()
    .then(() => console.log('[Auth/Redis] OTP Redis client connected'))
    .catch(err => console.error('[Auth/Redis] Failed to connect:', err.message));

const OTP_TTL_SECONDS  = 10 * 60;
const OTP_KEY_PREFIX   = 'wm:otp:';
const OTP_MAX_ATTEMPTS = 5;

// ─── SMTP ──────────────────────────────────────────────────────────────────────
//
// Налаштовується через .env:
//   SMTP_HOST=mail.yourserver.com     (обов'язково)
//   SMTP_PORT=465                     (за замовчуванням 465)
//   SMTP_SECURE=true                  (true = SSL/TLS, false = STARTTLS)
//   SMTP_USER=support@yourserver.com  (обов'язково)
//   SMTP_PASS=yourpassword            (обов'язково)
//   SMTP_FROM="WorldMates" <support@yourserver.com>  (необов'язково)
//
const SMTP_CONFIG = {
    host:   process.env.SMTP_HOST   || 'localhost',
    port:   parseInt(process.env.SMTP_PORT)  || 465,
    secure: (process.env.SMTP_SECURE || 'true') !== 'false',  // default true (SSL)
    auth: {
        user: process.env.SMTP_USER || '',
        pass: process.env.SMTP_PASS || '',
    },
    from: process.env.SMTP_FROM || `"WorldMates" <${process.env.SMTP_USER || 'noreply@worldmates.club'}>`,
    tls:  { rejectUnauthorized: false },
    connectionTimeout: 10000,
    greetingTimeout:   8000,
    socketTimeout:     12000,
};

let _smtpTransporter = null;

function getSmtpTransporter() {
    if (!_smtpTransporter) {
        _smtpTransporter = nodemailer.createTransport(SMTP_CONFIG);
    }
    return _smtpTransporter;
}

async function sendEmail(to, subject, html) {
    const transporter = getSmtpTransporter();

    try {
        await transporter.verify();
    } catch (err) {
        console.error(`[SMTP] verify failed (${SMTP_CONFIG.host}:${SMTP_CONFIG.port}): ${err.message}`);
        // Try to re-create the transporter in case the connection went stale
        _smtpTransporter = null;
        const fresh = getSmtpTransporter();
        try {
            await fresh.verify();
        } catch (e2) {
            console.error(`[SMTP] second verify also failed: ${e2.message}`);
            throw new Error(`SMTP: cannot connect to ${SMTP_CONFIG.host}:${SMTP_CONFIG.port} — ${e2.message}`);
        }
        _smtpTransporter = fresh;
    }

    try {
        await _smtpTransporter.sendMail({ from: SMTP_CONFIG.from, to, subject, html });
        console.log(`[SMTP] Sent "${subject}" → ${to} via ${SMTP_CONFIG.host}:${SMTP_CONFIG.port}`);
    } catch (err) {
        console.error(`[SMTP] sendMail failed → ${to}: ${err.message}`);
        throw new Error(`SMTP send error: ${err.message}`);
    }
}

// ─── Twilio SMS ────────────────────────────────────────────────────────────────
//
// Налаштовується через .env:
//   TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
//   TWILIO_AUTH_TOKEN=your_auth_token
//   TWILIO_FROM=+1234567890   (або Messaging Service SID: MGxxxxx)
//
// Якщо змінні не вказані — SMS надсилатись не буде (кинеться помилка).
//
function getTwilioClient() {
    const sid   = process.env.TWILIO_ACCOUNT_SID;
    const token = process.env.TWILIO_AUTH_TOKEN;
    if (!sid || !token) {
        throw new Error('Twilio is not configured. Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in .env');
    }
    // Lazy import — Twilio loaded only when needed
    const twilio = require('twilio');
    return twilio(sid, token);
}

async function sendSms(to, body) {
    const client = getTwilioClient();
    const from   = process.env.TWILIO_FROM;
    if (!from) throw new Error('TWILIO_FROM is not set in .env');

    const msg = await client.messages.create({ to, from, body });
    console.log(`[Twilio] SMS sent → ${to}, SID: ${msg.sid}`);
}

// ─── Email templates ──────────────────────────────────────────────────────────

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

function codeBlock(code, L) {
    return `<div style="text-align:center;margin:28px 0">
      <span style="display:inline-block;font-size:42px;font-weight:bold;letter-spacing:12px;
                   color:#6200EA;background:#f3e5ff;padding:16px 28px;border-radius:14px">
        ${code}
      </span>
    </div>
    <p style="color:#777;font-size:14px;margin:0">
      Код дійсний <strong>${L.code_validity}</strong>.<br>
      ${L.code_disclaimer}
    </p>`;
}

// ─── OTP helpers ──────────────────────────────────────────────────────────────

function generateCode(len = 6) {
    const min = Math.pow(10, len - 1);
    const max = Math.pow(10, len) - 1;
    return String(min + crypto.randomInt(max - min + 1));
}

function otpRedisKey(type, contact) {
    return `${OTP_KEY_PREFIX}${type}:${contact.toLowerCase().trim()}`;
}

async function storeOtp(type, contact, code, extra = {}) {
    const key  = otpRedisKey(type, contact);
    const data = JSON.stringify({ code: String(code), attempts: 0, ...extra });
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
        await otpRedis.set(key, JSON.stringify(entry), { KEEPTTL: true });
        return { ok: false, reason: 'invalid', attemptsLeft: OTP_MAX_ATTEMPTS - entry.attempts };
    }

    await otpRedis.del(key);
    return { ok: true, entry };
}

function otpErrorMessage(result, L) {
    if (result.reason === 'invalid') {
        return result.attemptsLeft > 0
            ? L.otp_invalid(result.attemptsLeft)
            : L.otp_too_many;
    }
    if (result.reason === 'locked') return L.otp_locked;
    return L.otp_expired;
}

// ─── Token & password helpers ─────────────────────────────────────────────────

function generateToken() {
    return crypto.randomBytes(32).toString('hex');
}

async function hashPassword(pw) {
    return bcrypt.hash(pw, BCRYPT_ROUNDS);
}

/**
 * Перевіряє пароль проти збереженого хешу.
 * Підтримує: bcrypt ($2y$/$2b$), sha1 (40 hex), md5 (32 hex).
 * needsUpgrade=true коли хеш застарілий — треба перехешувати в bcrypt.
 */
async function verifyPassword(plaintext, storedHash) {
    if (!plaintext || !storedHash) return { ok: false, needsUpgrade: false };

    if (/^\$2[aby]\$/.test(storedHash)) {
        const ok = await bcrypt.compare(plaintext, storedHash);
        return { ok, needsUpgrade: false };
    }
    if (/^[0-9a-f]{40}$/i.test(storedHash)) {
        const ok = crypto.createHash('sha1').update(plaintext).digest('hex') === storedHash.toLowerCase();
        return { ok, needsUpgrade: ok };
    }
    if (/^[0-9a-f]{32}$/i.test(storedHash)) {
        const ok = md5(plaintext) === storedHash.toLowerCase();
        return { ok, needsUpgrade: ok };
    }
    return { ok: false, needsUpgrade: false };
}

// ─── DB helpers ───────────────────────────────────────────────────────────────

function findUserByEmail(ctx, email) {
    return ctx.wo_users.unscoped().findOne({ where: { email } });
}

function findUserByPhone(ctx, phone) {
    return ctx.wo_users.unscoped().findOne({ where: { phone_number: phone } });
}

function findUserById(ctx, id) {
    return ctx.wo_users.findOne({ where: { user_id: id } });
}

function findUserByLogin(ctx, login) {
    const { Op } = require('sequelize');
    return ctx.wo_users.unscoped().findOne({
        where: {
            [Op.or]: [
                { username:     login },
                { email:        login },
                { phone_number: login },
            ]
        }
    });
}

// Access token TTL: 30 days; refresh token TTL: 90 days
const ACCESS_TOKEN_TTL  = 30 * 24 * 3600;
const REFRESH_TOKEN_TTL = 90 * 24 * 3600;

function getClientIp(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) return forwarded.split(',')[0].trim();
    return req.socket?.remoteAddress || req.ip || '';
}

async function createSession(ctx, userId, platform = 'phone', details = '', ip = '') {
    const now           = Math.floor(Date.now() / 1000);
    const token         = generateToken();
    const refreshToken  = generateToken();
    await ctx.wo_appssessions.create({
        user_id:            userId,
        session_id:         token,
        platform,
        platform_details:   details,
        ip_address:         ip,
        time:               now,
        expires_at:         now + ACCESS_TOKEN_TTL,
        refresh_token:      refreshToken,
        refresh_expires_at: now + REFRESH_TOKEN_TTL,
    });
    return { token, refreshToken, expiresAt: now + ACCESS_TOKEN_TTL };
}

// ─── Auth middleware (for authenticated session endpoints) ─────────────────────

async function requireAuth(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;
    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
    try {
        const session = await ctx.wo_appssessions.findOne({
            where:      { session_id: token },
            attributes: ['id', 'user_id', 'expires_at'],
            raw:        true,
        });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        // Check token expiry — return error_id '401' so the client knows to refresh
        const now = Math.floor(Date.now() / 1000);
        if (session.expires_at && session.expires_at < now) {
            return res.status(401).json({ api_status: 401, error_id: '401', error_message: 'Access token has expired' });
        }

        req.userId    = session.user_id;
        req.sessionId = session.id;
        next();
    } catch (err) {
        console.error('[Auth/requireAuth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

/**
 * Генерує унікальний username для нового акаунту.
 */
async function generateUsername(ctx) {
    for (let i = 0; i < 15; i++) {
        const candidate = 'user_' + crypto.randomInt(100000, 999999).toString();
        const exists = await ctx.wo_users.findOne({ where: { username: candidate } });
        if (!exists) return candidate;
    }
    return 'wm_' + Date.now().toString().slice(-10);
}

// ─── POST /api/node/auth/login ────────────────────────────────────────────────

function login(ctx) {
    return async (req, res) => {
        const loginStr = (req.body.username || req.body.email || '').trim();
        const password = (req.body.password || '').trim();
        const platform = (req.body.device_type || req.body.platform || 'phone').trim();

        if (!loginStr || !password) {
            return res.json({ api_status: 400, error_id: '3', error_message: 'Missing username or password' });
        }

        try {
            const user = await findUserByLogin(ctx, loginStr);
            if (!user) {
                return res.json({ api_status: 400, error_id: '4', error_message: 'Username/email not found' });
            }

            if (user.active === '0') {
                return res.json({ api_status: 400, error_id: '7', error_message: 'Account is not active or has been banned' });
            }

            const { ok, needsUpgrade } = await verifyPassword(password, user.password);
            if (!ok) {
                return res.json({ api_status: 400, error_id: '5', error_message: 'Incorrect password' });
            }

            // Авто-апгрейд md5/sha1 → bcrypt
            if (needsUpgrade) {
                try {
                    const newHash = await hashPassword(password);
                    await ctx.wo_users.unscoped().update(
                        { password: newHash },
                        { where: { user_id: user.user_id } }
                    );
                    console.log(`[Auth] Hash upgraded to bcrypt for user ${user.user_id}`);
                } catch (e) {
                    console.warn(`[Auth] Hash upgrade failed: ${e.message}`);
                }
            }

            const { token, refreshToken, expiresAt } = await createSession(ctx, user.user_id, platform, 'login', getClientIp(req));

            ctx.wo_users.update(
                { lastseen: Math.floor(Date.now() / 1000) },
                { where: { user_id: user.user_id } }
            ).catch(() => {});

            console.log(`[Auth] Login OK: user ${user.user_id} (${user.username})`);

            return res.json({
                api_status:    200,
                access_token:  token,
                refresh_token: refreshToken,
                expires_at:    expiresAt,
                user_id:       user.user_id,
                username:      user.username,
                first_name:    user.first_name,
                last_name:     user.last_name,
                avatar:        user.avatar,
                cover:         user.cover,
                email:         user.email,
                phone_number:  user.phone_number,
                verified:      user.verified,
                is_pro:        user.is_pro,
                user_platform: platform,
            });

        } catch (err) {
            console.error('[Auth/login]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── POST /api/node/auth/logout ───────────────────────────────────────────────

function logout(ctx) {
    return async (req, res) => {
        const token = (
            req.headers['access-token'] ||
            req.body.access_token        ||
            req.query.access_token       ||
            ''
        ).trim();

        if (!token) {
            return res.json({ api_status: 400, error_message: 'No access token provided' });
        }

        try {
            const deleted = await ctx.wo_appssessions.destroy({ where: { session_id: token } });
            if (deleted === 0) {
                return res.json({ api_status: 400, error_message: 'Session not found or already expired' });
            }
            return res.json({ api_status: 200, message: 'Logged out successfully' });
        } catch (err) {
            console.error('[Auth/logout]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── POST /api/node/auth/request-password-reset ───────────────────────────────

function requestPasswordReset(ctx) {
    return async (req, res) => {
        const L     = t(req);
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();

        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }

        try {
            const user = email
                ? await findUserByEmail(ctx, email)
                : await findUserByPhone(ctx, phone);

            if (!user) {
                return res.json({ api_status: 400, error_message: L.account_not_found });
            }

            const code    = generateCode(6);
            const contact = email || phone;

            await storeOtp('reset', contact, code, { userId: user.user_id });

            if (email) {
                await sendEmail(
                    email,
                    L.email_subject_reset,
                    emailTemplate(L.email_title_reset,
                        `<p style="color:#555">${L.email_body_reset}</p>${codeBlock(code, L)}`
                    )
                );
            } else {
                await sendSms(phone, L.sms_reset(code));
            }

            const maskedContact = email
                ? email.replace(/(?<=.{2}).(?=[^@]*@)/, '*').replace(/(?<=@.{1}).+(?=\..{2,})/, '***')
                : phone.slice(0, -4).replace(/\d/g, '*') + phone.slice(-4);

            console.log(`[Auth] Reset code sent → ${maskedContact} (user ${user.user_id})`);
            return res.json({ api_status: 200, message: L.code_sent });

        } catch (err) {
            console.error('[Auth/reset-request]', err.message);
            // Surface SMTP errors so the admin can diagnose them without looking at logs
            const userMsg = err.message.startsWith('SMTP') ? err.message : L.server_error;
            return res.json({ api_status: 500, error_message: userMsg });
        }
    };
}

// ─── POST /api/node/auth/reset-password ──────────────────────────────────────

function resetPassword(ctx) {
    return async (req, res) => {
        const L       = t(req);
        const email   = (req.body.email || '').trim().toLowerCase();
        const phone   = (req.body.phone_number || '').trim();
        const code    = (req.body.code || '').trim();
        const newPass = (req.body.new_password || '').trim();

        if (!code || !newPass) {
            return res.json({ api_status: 400, error_message: L.provide_code_and_password });
        }
        if (newPass.length < 6) {
            return res.json({ api_status: 400, error_message: L.password_too_short });
        }

        const contact = email || phone;
        if (!contact) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }

        const result = await checkOtp('reset', contact, code);
        if (!result.ok) {
            return res.json({ api_status: 400, error_message: otpErrorMessage(result, L) });
        }

        try {
            const newHash = await hashPassword(newPass);

            const [affectedRows] = await ctx.wo_users.unscoped().update(
                { password: newHash },
                { where: { user_id: result.entry.userId } }
            );

            if (affectedRows === 0) {
                return res.json({ api_status: 500, error_message: L.update_password_error });
            }

            // Invalidate all sessions after password change
            await ctx.wo_appssessions.destroy({ where: { user_id: result.entry.userId } }).catch(() => {});

            console.log(`[Auth] Password changed for user ${result.entry.userId}`);
            return res.json({ api_status: 200, message: L.password_updated });

        } catch (err) {
            console.error('[Auth/reset-password]', err.message);
            return res.json({ api_status: 500, error_message: L.server_error });
        }
    };
}

// ─── POST /api/node/auth/quick-register ──────────────────────────────────────
// Крок 1: реєстрація/вхід по email або телефону — надсилає OTP.
// Якщо акаунт не існує — створює новий автоматично.

function quickRegister(ctx) {
    return async (req, res) => {
        const L     = t(req);
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();

        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }

        // Phone number validation — must start with + and contain only digits
        if (phone && !/^\+\d{7,15}$/.test(phone)) {
            return res.json({ api_status: 400, error_message: L.phone_format_error });
        }

        try {
            let user  = email ? await findUserByEmail(ctx, email) : await findUserByPhone(ctx, phone);
            let isNew = false;

            if (!user) {
                // New account — auto-register
                const username    = await generateUsername(ctx);
                const now         = Math.floor(Date.now() / 1000);
                const tempPassword = await hashPassword(generateToken().slice(0, 16));

                let displayName = 'User';
                if (email) {
                    displayName = email.split('@')[0].replace(/[^a-zA-Z0-9]/g, '').substring(0, 20) || 'User';
                }

                user = await ctx.wo_users.create({
                    username,
                    email:        email || '',
                    phone_number: phone || '',
                    password:     tempPassword,
                    first_name:   displayName,
                    last_name:    '',
                    lastseen:     now,
                    active:       '1',
                    registered:   new Date().toLocaleDateString('en-US'),
                    joined:       now,
                });
                isNew = true;
                console.log(`[Auth] New user registered: ${username} via ${email ? 'email' : 'phone'}`);

                // ── Founding-member gift (first 250 users after launch) ──────
                try {
                    const founderCount = await ctx.wo_users.unscoped().count({ where: { is_founder: 1 } });
                    if (founderCount < 250) {
                        await ctx.wo_users.unscoped().update(
                            { is_pro: '1', pro_time: 0, pro_type: '2', is_founder: 1 },
                            { where: { user_id: user.user_id } }
                        );
                        console.log(`[Auth] 🏅 Founding member #${founderCount + 1}: ${username} (id=${user.user_id})`);
                    }
                } catch (e) {
                    console.error('[Auth] Founder check failed:', e.message);
                }
                // ────────────────────────────────────────────────────────────
            }

            const code    = generateCode(6);
            const contact = email || phone;

            await storeOtp('quick', contact, code, { userId: user.user_id, isNew });

            if (email) {
                await sendEmail(
                    email,
                    isNew ? L.email_subject_welcome : L.email_subject_login,
                    emailTemplate(
                        isNew ? L.email_title_welcome : L.email_title_login,
                        `<p style="color:#555">${isNew ? L.email_body_welcome : L.email_body_login}</p>${codeBlock(code, L)}`
                    )
                );
            } else {
                await sendSms(phone, isNew ? L.sms_register(code) : L.sms_login(code));
            }

            return res.json({
                api_status: 200,
                message:    L.code_sent,
                user_id:    user.user_id,
                username:   user.username,
                is_new:     isNew,
            });

        } catch (err) {
            console.error('[Auth/quick-register]', err.message);

            if (err.message.includes('Twilio')) {
                return res.json({ api_status: 503, error_message: err.message });
            }
            // Surface SMTP errors so the admin can diagnose without looking at logs
            const userMsg = err.message.startsWith('SMTP') ? err.message : L.server_error;
            return res.json({ api_status: 500, error_message: userMsg });
        }
    };
}

// ─── POST /api/node/auth/quick-verify ────────────────────────────────────────
// Крок 2: перевірка OTP — повертає access_token.

function quickVerify(ctx) {
    return async (req, res) => {
        const L     = t(req);
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();
        const code  = (req.body.code || '').trim();

        if (!code) {
            return res.json({ api_status: 400, error_message: L.provide_code });
        }

        const contact = email || phone;
        if (!contact) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }

        const result = await checkOtp('quick', contact, code);
        if (!result.ok) {
            return res.json({ api_status: 400, error_message: otpErrorMessage(result, L) });
        }

        try {
            const user = await findUserById(ctx, result.entry.userId);
            if (!user) {
                return res.json({ api_status: 400, error_message: L.account_not_found_short });
            }

            const { token, refreshToken, expiresAt } = await createSession(ctx, user.user_id, 'phone', 'quick-auth', getClientIp(req));

            ctx.wo_users.update(
                { lastseen: Math.floor(Date.now() / 1000) },
                { where: { user_id: user.user_id } }
            ).catch(() => {});

            console.log(`[Auth] Quick-auth OK for user ${user.user_id}`);
            return res.json({
                api_status:    200,
                access_token:  token,
                refresh_token: refreshToken,
                expires_at:    expiresAt,
                user_id:       user.user_id,
                username:      user.username,
                first_name:    user.first_name,
                last_name:     user.last_name,
                avatar:        user.avatar,
                is_new:        result.entry.isNew || false,
                message:       L.auth_success,
            });

        } catch (err) {
            console.error('[Auth/quick-verify]', err.message);
            return res.json({ api_status: 500, error_message: L.server_error });
        }
    };
}

// ─── POST /api/node/auth/register ────────────────────────────────────────────
// Classic registration: username + email/phone + password.
// Returns success_type="verification" when email/phone validation is enabled,
// otherwise creates a session and returns access_token immediately.

function register(ctx) {
    return async (req, res) => {
        const L          = t(req);
        const username   = (req.body.username || '').trim();
        const email      = (req.body.email    || '').trim().toLowerCase();
        const phone      = (req.body.phone_number || '').trim();
        const password   = (req.body.password         || '').trim();
        const confirm    = (req.body.confirm_password || '').trim();
        const gender     = (req.body.gender   || 'male').trim();
        const platform   = (req.body.device_type || 'phone').trim();
        const inviteCode = (req.body.invite_code || '').trim().toUpperCase();

        // ── Invite code format check (fast, no DB hit) ────────────────────────
        // Valid formats:
        //   ULTRA-XXXX-XXXX-XXXX
        //   PRO-XXXX-XXXX-XXXX
        //   BLOGGER-ULTRA-XXXX-XXXX-XXXX   (blogger prefix: 2–20 A-Z0-9)
        //   BLOGGER-PRO-XXXX-XXXX-XXXX
        const INVITE_REGEX = /^([A-Z][A-Z0-9]{1,19}-)?(ULTRA|PRO)-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/;
        if (inviteCode && !INVITE_REGEX.test(inviteCode)) {
            return res.json({ api_status: 400, error_message: L.invite_code_invalid || 'Invalid invite code format' });
        }
        // ─────────────────────────────────────────────────────────────────────

        if (!username) {
            return res.json({ api_status: 400, error_message: L.username_required || 'Username is required' });
        }
        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }
        if (!password) {
            return res.json({ api_status: 400, error_message: L.password_required || 'Password is required' });
        }
        if (password !== confirm) {
            return res.json({ api_status: 400, error_message: L.passwords_do_not_match || 'Passwords do not match' });
        }
        if (password.length < 6) {
            return res.json({ api_status: 400, error_message: L.password_too_short });
        }
        if (phone && !/^\+\d{7,15}$/.test(phone)) {
            return res.json({ api_status: 400, error_message: L.phone_format_error });
        }

        try {
            const { Op } = require('sequelize');

            // Check username uniqueness
            const byUsername = await ctx.wo_users.findOne({ where: { username } });
            if (byUsername) {
                return res.json({ api_status: 400, error_message: L.username_taken || 'Username is already taken' });
            }

            // Check email/phone uniqueness
            if (email) {
                const byEmail = await findUserByEmail(ctx, email);
                if (byEmail) {
                    return res.json({ api_status: 400, error_message: L.email_taken || 'Email is already registered' });
                }
            }
            if (phone) {
                const byPhone = await findUserByPhone(ctx, phone);
                if (byPhone) {
                    return res.json({ api_status: 400, error_message: L.phone_taken || 'Phone number is already registered' });
                }
            }

            // Determine if verification is required.
            // .env REQUIRE_EMAIL_VERIFICATION / REQUIRE_PHONE_VERIFICATION takes
            // full priority over the WoWonder admin-panel DB settings.
            //   'true'  → always require OTP (email or phone)
            //   'false' → skip verification, register immediately
            //   unset   → fall back to WoWonder DB (email_validation / phone_number_validation)
            const _envEmail = process.env.REQUIRE_EMAIL_VERIFICATION;
            const _envPhone = process.env.REQUIRE_PHONE_VERIFICATION;
            const emailValidation = _envEmail !== undefined
                ? _envEmail === 'true'
                : (ctx.globalconfig['email_validation'] === '1' || ctx.globalconfig['email_validation'] === 1);
            const phoneValidation = _envPhone !== undefined
                ? _envPhone === 'true'
                : (ctx.globalconfig['phone_number_validation'] === '1' || ctx.globalconfig['phone_number_validation'] === 1);
            const needsVerification = (email && emailValidation) || (phone && phoneValidation);

            const hashedPassword = await hashPassword(password);
            const now            = Math.floor(Date.now() / 1000);

            // ── Brute-force protection + invite code validation ───────────────
            // Per-IP counter stored in Redis. After 5 wrong codes → locked 1 hour.
            const clientIp   = getClientIp(req);
            const failKey    = `invite_fail:${clientIp}`;
            const MAX_FAILS  = 5;
            const LOCK_TTL   = 3600; // seconds

            let inviteRow = null;
            if (inviteCode) {
                // Check if this IP is already locked out
                const failCount = parseInt(await otpRedis.get(failKey) || '0');
                if (failCount >= MAX_FAILS) {
                    const ttl = await otpRedis.ttl(failKey);
                    return res.json({
                        api_status:    429,
                        error_message: L.invite_too_many_attempts || 'Too many invalid invite code attempts. Try again in 1 hour.',
                        retry_after:   ttl,
                    });
                }

                inviteRow = await ctx.sequelize.query(
                    `SELECT id, type, expires_at FROM wm_invite_codes
                      WHERE code = ? AND is_used = 0 LIMIT 1`,
                    { replacements: [inviteCode], type: ctx.sequelize.QueryTypes.SELECT }
                ).then(rows => rows[0] || null);

                if (!inviteRow) {
                    // Record failed attempt
                    await otpRedis.multi()
                        .incr(failKey)
                        .expire(failKey, LOCK_TTL)
                        .exec();
                    const remaining = MAX_FAILS - (failCount + 1);
                    const msg = remaining > 0
                        ? `${L.invite_code_invalid || 'Invalid or already used invite code'} (${remaining} attempts left)`
                        : (L.invite_too_many_attempts || 'Too many invalid invite code attempts. Try again in 1 hour.');
                    return res.json({ api_status: 400, error_message: msg });
                }
                if (inviteRow.expires_at && inviteRow.expires_at < now) {
                    await otpRedis.multi().incr(failKey).expire(failKey, LOCK_TTL).exec();
                    return res.json({ api_status: 400, error_message: L.invite_code_expired || 'Invite code has expired' });
                }

                // Valid code — reset fail counter
                await otpRedis.del(failKey);
            }
            // ─────────────────────────────────────────────────────────────────

            const user = await ctx.wo_users.create({
                username,
                email:        email || '',
                phone_number: phone || '',
                password:     hashedPassword,
                first_name:   '',
                last_name:    '',
                gender,
                lastseen:     now,
                active:       needsVerification ? '0' : '1',
                registered:   new Date().toLocaleDateString('en-US'),
                joined:       now,
            });

            console.log(`[Auth] User registered: ${username} (id=${user.user_id}, needsVerification=${needsVerification})`);

            // ── Early adopter gift + invite code redemption (single transaction) ─
            try {
                await ctx.sequelize.transaction(async (t) => {
                    if (inviteCode && inviteRow) {
                        // ── Redeem invite code ────────────────────────────────
                        // Lock the row so a parallel request can't double-redeem
                        const [locked] = await ctx.sequelize.query(
                            `SELECT id FROM wm_invite_codes
                              WHERE id = ? AND is_used = 0
                              FOR UPDATE`,
                            { replacements: [inviteRow.id], type: ctx.sequelize.QueryTypes.SELECT, transaction: t }
                        );
                        if (!locked) {
                            throw new Error('INVITE_ALREADY_USED');
                        }

                        let proTime, proType;
                        if (inviteRow.type === 'ultra') {
                            // Lifetime premium: expires 2099-12-31
                            proTime = Math.floor(new Date('2099-12-31T23:59:59Z').getTime() / 1000);
                            proType = '3';   // lifetime / ULTRA tier
                            console.log(`[Auth] 🌟 ULTRA invite redeemed by ${username} (id=${user.user_id})`);
                        } else {
                            // PRO: +1 year from activation, regardless of code remaining validity
                            proTime = now + 365 * 24 * 60 * 60;
                            proType = '2';
                            console.log(`[Auth] ⭐ PRO invite redeemed by ${username} (id=${user.user_id})`);
                        }

                        await ctx.sequelize.query(
                            `UPDATE wm_invite_codes
                                SET is_used = 1, used_by = ?, used_at = ?
                              WHERE id = ?`,
                            { replacements: [user.user_id, now, inviteRow.id], transaction: t }
                        );

                        await ctx.wo_users.unscoped().update(
                            { is_pro: '1', pro_time: proTime, pro_type: proType },
                            { where: { user_id: user.user_id }, transaction: t }
                        );

                    } else {
                        // ── Early adopter: user_id 101–850 → 3 months premium ─
                        // Lock the user row to prevent concurrent registration race
                        const [earlyUser] = await ctx.sequelize.query(
                            `SELECT user_id FROM wo_users
                              WHERE user_id = ? AND user_id BETWEEN 101 AND 850
                              FOR UPDATE`,
                            { replacements: [user.user_id], type: ctx.sequelize.QueryTypes.SELECT, transaction: t }
                        );

                        if (earlyUser) {
                            const threeMonths = now + 90 * 24 * 60 * 60;
                            await ctx.wo_users.unscoped().update(
                                { is_pro: '1', pro_time: threeMonths, pro_type: '1' },
                                { where: { user_id: user.user_id }, transaction: t }
                            );
                            console.log(`[Auth] 🎁 Early adopter #${user.user_id}: ${username} — 3 months premium`);
                        }
                    }
                });
            } catch (e) {
                if (e.message === 'INVITE_ALREADY_USED') {
                    // Race condition: someone redeemed the same code a millisecond earlier
                    // User is created but gets no premium — inform them
                    console.warn(`[Auth] Invite code race for ${inviteCode} by ${username}`);
                } else {
                    console.error('[Auth] Premium grant failed:', e.message);
                }
            }
            // ─────────────────────────────────────────────────────────────────

            if (needsVerification) {
                return res.json({
                    api_status:   200,
                    success_type: 'verification',
                    user_id:      user.user_id,
                    username:     user.username,
                });
            }

            const { token, refreshToken, expiresAt } = await createSession(ctx, user.user_id, platform, 'register', getClientIp(req));
            return res.json({
                api_status:    200,
                access_token:  token,
                refresh_token: refreshToken,
                expires_at:    expiresAt,
                user_id:       user.user_id,
                username:      user.username,
                first_name:    user.first_name,
                last_name:     user.last_name,
                avatar:        user.avatar || '',
                success_type:  'registered',
            });

        } catch (err) {
            console.error('[Auth/register]', err.message);
            return res.json({ api_status: 500, error_message: L.server_error });
        }
    };
}

// ─── POST /api/node/auth/send-code ────────────────────────────────────────────
// Send a 6-digit OTP to an existing user for email/phone verification.

function sendCode(ctx) {
    return async (req, res) => {
        const L               = t(req);
        const verificationType = (req.body.verification_type || '').trim();
        const contactInfo      = (req.body.contact_info || '').trim().toLowerCase();

        if (!contactInfo) {
            return res.json({ api_status: 400, error_message: L.provide_email_or_phone });
        }

        try {
            const user = verificationType === 'phone'
                ? await findUserByPhone(ctx, contactInfo)
                : await findUserByEmail(ctx, contactInfo);

            if (!user) {
                return res.json({ api_status: 400, error_message: L.account_not_found });
            }

            const code = generateCode(6);
            await storeOtp('verify', contactInfo, code, { userId: user.user_id });

            if (verificationType === 'phone') {
                await sendSms(contactInfo, `Your WorldMates verification code: ${code}`);
            } else {
                await sendEmail(
                    contactInfo,
                    'WorldMates — Email Verification',
                    emailTemplate('Email Verification',
                        `<p style="color:#555">Enter this code to verify your account:</p>${codeBlock(code, L)}`
                    )
                );
            }

            console.log(`[Auth] Verification code sent to ${contactInfo} (user ${user.user_id})`);
            return res.json({
                api_status:  200,
                message:     L.code_sent,
                code_length: 6,
                expires_in:  OTP_TTL_SECONDS,
            });

        } catch (err) {
            console.error('[Auth/send-code]', err.message);
            if (err.message.includes('Twilio')) {
                return res.json({ api_status: 503, error_message: err.message });
            }
            const userMsg = err.message.startsWith('SMTP') ? err.message : L.server_error;
            return res.json({ api_status: 500, error_message: userMsg });
        }
    };
}

// ─── POST /api/node/auth/verify-code ─────────────────────────────────────────
// Verify the OTP, activate the user account, and return an access_token.

function verifyCode(ctx) {
    return async (req, res) => {
        const L               = t(req);
        const verificationType = (req.body.verification_type || '').trim();
        const contactInfo      = (req.body.contact_info || '').trim().toLowerCase();
        const code             = (req.body.code || '').trim();

        if (!code || !contactInfo) {
            return res.json({ api_status: 400, error_message: L.provide_code });
        }

        const result = await checkOtp('verify', contactInfo, code);
        if (!result.ok) {
            return res.json({
                api_status:  400,
                errors:      { error_text: otpErrorMessage(result, L) },
                message:     otpErrorMessage(result, L),
            });
        }

        try {
            const user = await findUserById(ctx, result.entry.userId);
            if (!user) {
                return res.json({ api_status: 400, error_message: L.account_not_found_short });
            }

            // Activate account if it was pending verification
            if (user.active !== '1') {
                await ctx.wo_users.update(
                    { active: '1' },
                    { where: { user_id: user.user_id } }
                );
            }

            const { token, refreshToken, expiresAt } = await createSession(ctx, user.user_id, 'phone', 'verify', getClientIp(req));

            ctx.wo_users.update(
                { lastseen: Math.floor(Date.now() / 1000) },
                { where: { user_id: user.user_id } }
            ).catch(() => {});

            console.log(`[Auth] Verification OK for user ${user.user_id}`);
            return res.json({
                api_status:    200,
                access_token:  token,
                refresh_token: refreshToken,
                expires_at:    expiresAt,
                user_id:       user.user_id,
                username:      user.username,
                message:       L.auth_success,
            });

        } catch (err) {
            console.error('[Auth/verify-code]', err.message);
            return res.json({ api_status: 500, error_message: L.server_error });
        }
    };
}

// ─── POST /api/node/auth/refresh ─────────────────────────────────────────────
// Rotates the access token using a valid refresh token.
// Old session is deleted; a brand-new session (with new refresh token) is created.
// Request body: { refresh_token: "..." }
// Response: { api_status: 200, access_token, refresh_token, expires_at, user_id }

function refreshToken(ctx) {
    return async (req, res) => {
        const incomingRefresh = (
            req.body.refresh_token ||
            req.headers['refresh-token'] ||
            ''
        ).trim();

        if (!incomingRefresh || incomingRefresh.length < 10) {
            return res.json({ api_status: 400, error_message: 'refresh_token is required' });
        }

        try {
            const now     = Math.floor(Date.now() / 1000);
            const session = await ctx.wo_appssessions.findOne({
                where: { refresh_token: incomingRefresh },
                raw:   true,
            });

            if (!session) {
                return res.json({ api_status: 401, error_message: 'Invalid or expired refresh token' });
            }

            // Refresh token itself has an expiry
            if (session.refresh_expires_at && session.refresh_expires_at < now) {
                await ctx.wo_appssessions.destroy({ where: { id: session.id } });
                return res.json({ api_status: 401, error_message: 'Refresh token has expired, please log in again' });
            }

            // Rotation: delete old session, issue new access + refresh tokens
            await ctx.wo_appssessions.destroy({ where: { id: session.id } });

            const { token, refreshToken: newRefresh, expiresAt } = await createSession(
                ctx,
                session.user_id,
                session.platform || 'phone',
                'token-refresh'
            );

            console.log(`[Auth] Token refreshed for user ${session.user_id}`);
            return res.json({
                api_status:    200,
                access_token:  token,
                refresh_token: newRefresh,
                expires_at:    expiresAt,
                user_id:       session.user_id,
            });

        } catch (err) {
            console.error('[Auth/refresh]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET /api/node/auth/sessions ─────────────────────────────────────────────

function listSessions(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const sessions = await ctx.wo_appssessions.findAll({
                where:      { user_id: userId },
                attributes: ['id', 'session_id', 'platform', 'platform_details', 'ip_address', 'time', 'expires_at'],
                order:      [['time', 'DESC']],
                raw:        true,
            });
            const now = Math.floor(Date.now() / 1000);
            const result = sessions.map(s => ({
                id:               s.id,
                session_id:       s.session_id,
                platform:         s.platform,
                platform_details: s.platform_details,
                ip_address:       s.ip_address,
                time:             s.time,
                expires_at:       s.expires_at,
                is_current:       s.id === req.sessionId,
                is_active:        !s.expires_at || s.expires_at > now,
            }));
            res.json({ api_status: 200, sessions: result });
        } catch (err) {
            console.error('[Auth/listSessions]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── DELETE /api/node/auth/sessions/:id ──────────────────────────────────────

function terminateSession(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            const sessionId = parseInt(req.params.id);
            if (!sessionId || isNaN(sessionId))
                return res.status(400).json({ api_status: 400, error_message: 'Invalid session id' });

            const session = await ctx.wo_appssessions.findOne({ where: { id: sessionId, user_id: userId } });
            if (!session)
                return res.status(404).json({ api_status: 404, error_message: 'Session not found' });

            await ctx.wo_appssessions.destroy({ where: { id: sessionId, user_id: userId } });
            console.log(`[Auth/terminateSession] user=${userId} terminated session ${sessionId}`);
            res.json({ api_status: 200, message: 'Session terminated' });
        } catch (err) {
            console.error('[Auth/terminateSession]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── Register routes ──────────────────────────────────────────────────────────

function registerAuthRoutes(app, ctx) {
    const auth = (req, res, next) => requireAuth(ctx, req, res, next);

    app.post('/api/node/auth/login',                  login(ctx));
    app.post('/api/node/auth/logout',                 logout(ctx));
    app.post('/api/node/auth/refresh',                refreshToken(ctx));
    app.post('/api/node/auth/request-password-reset', requestPasswordReset(ctx));
    app.post('/api/node/auth/reset-password',         resetPassword(ctx));
    app.post('/api/node/auth/quick-register',         quickRegister(ctx));
    app.post('/api/node/auth/quick-verify',           quickVerify(ctx));
    app.post('/api/node/auth/register',               register(ctx));
    app.post('/api/node/auth/send-code',              sendCode(ctx));
    app.post('/api/node/auth/verify-code',            verifyCode(ctx));
    app.get   ('/api/node/auth/sessions',             auth, listSessions(ctx));
    app.delete('/api/node/auth/sessions/:id',         auth, terminateSession(ctx));

    console.log('[Auth] Routes registered:');
    console.log('  POST   /api/node/auth/login');
    console.log('  POST   /api/node/auth/logout');
    console.log('  POST   /api/node/auth/refresh');
    console.log('  POST   /api/node/auth/request-password-reset');
    console.log('  POST   /api/node/auth/reset-password');
    console.log('  POST   /api/node/auth/quick-register');
    console.log('  POST   /api/node/auth/quick-verify');
    console.log('  POST   /api/node/auth/register');
    console.log('  POST   /api/node/auth/send-code');
    console.log('  POST   /api/node/auth/verify-code');
    console.log('  GET    /api/node/auth/sessions');
    console.log('  DELETE /api/node/auth/sessions/:id');
}

module.exports = { registerAuthRoutes };
