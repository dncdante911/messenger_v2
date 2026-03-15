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

const BCRYPT_ROUNDS = 10;

// ─── Redis OTP client ──────────────────────────────────────────────────────────
const _redisPass = process.env.REDIS_PASSWORD || '';
const otpRedis = redis.createClient({
    socket: {
        host: process.env.REDIS_HOST || '127.0.0.1',
        port: parseInt(process.env.REDIS_PORT) || 6379,
    },
    ...(_redisPass ? { password: _redisPass } : {}),
});
otpRedis.on('error', err => console.error('[Auth/Redis] OTP client error:', err.message));
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
        // Пробуємо перестворити transporter якщо з'єднання протухло
        _smtpTransporter = null;
        const fresh = getSmtpTransporter();
        await fresh.verify();
        _smtpTransporter = fresh;
    }

    await _smtpTransporter.sendMail({ from: SMTP_CONFIG.from, to, subject, html });
    console.log(`[SMTP] Sent "${subject}" → ${to} via ${SMTP_CONFIG.host}:${SMTP_CONFIG.port}`);
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
        throw new Error('Twilio не налаштовано. Додайте TWILIO_ACCOUNT_SID і TWILIO_AUTH_TOKEN у .env');
    }
    // Ледачий імпорт — Twilio вантажиться тільки коли потрібно
    const twilio = require('twilio');
    return twilio(sid, token);
}

async function sendSms(to, body) {
    const client = getTwilioClient();
    const from   = process.env.TWILIO_FROM;
    if (!from) throw new Error('TWILIO_FROM не вказано у .env');

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

function codeBlock(code) {
    return `<div style="text-align:center;margin:28px 0">
      <span style="display:inline-block;font-size:42px;font-weight:bold;letter-spacing:12px;
                   color:#6200EA;background:#f3e5ff;padding:16px 28px;border-radius:14px">
        ${code}
      </span>
    </div>
    <p style="color:#777;font-size:14px;margin:0">
      Код дійсний <strong>10 хвилин</strong>.<br>
      Якщо ви не робили цього запиту — просто проігноруйте це повідомлення.
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

function otpErrorMessage(result) {
    if (result.reason === 'invalid') {
        return result.attemptsLeft > 0
            ? `Невірний код. Залишилось спроб: ${result.attemptsLeft}`
            : 'Забагато невірних спроб. Запросіть новий код.';
    }
    if (result.reason === 'locked') return 'Код заблоковано. Запросіть новий.';
    return 'Код прострочений. Запросіть новий.';
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

async function createSession(ctx, userId, platform = 'phone', details = '') {
    const token = generateToken();
    await ctx.wo_appssessions.create({
        user_id:          userId,
        session_id:       token,
        platform,
        platform_details: details,
        time:             Math.floor(Date.now() / 1000),
    });
    return token;
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

            const token = await createSession(ctx, user.user_id, platform, 'login');

            ctx.wo_users.update(
                { lastseen: Math.floor(Date.now() / 1000) },
                { where: { user_id: user.user_id } }
            ).catch(() => {});

            console.log(`[Auth] Login OK: user ${user.user_id} (${user.username})`);

            return res.json({
                api_status:    200,
                access_token:  token,
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

            const code    = generateCode(6);
            const contact = email || phone;

            await storeOtp('reset', contact, code, { userId: user.user_id });

            if (email) {
                await sendEmail(
                    email,
                    'Відновлення доступу — WorldMates',
                    emailTemplate('Відновлення доступу',
                        `<p style="color:#555">Ваш код підтвердження для відновлення доступу:</p>${codeBlock(code)}`
                    )
                );
            } else {
                await sendSms(phone, `WorldMates: ваш код відновлення пароля: ${code}. Дійсний 10 хвилин.`);
            }

            const maskedContact = email
                ? email.replace(/(?<=.{2}).(?=[^@]*@)/, '*').replace(/(?<=@.{1}).+(?=\..{2,})/, '***')
                : phone.slice(0, -4).replace(/\d/g, '*') + phone.slice(-4);

            console.log(`[Auth] Reset code sent → ${maskedContact} (user ${user.user_id})`);
            return res.json({ api_status: 200, message: `Код надіслано` });

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
        if (!contact) {
            return res.json({ api_status: 400, error_message: 'Вкажіть email або номер телефону' });
        }

        const result = await checkOtp('reset', contact, code);
        if (!result.ok) {
            return res.json({ api_status: 400, error_message: otpErrorMessage(result) });
        }

        try {
            const newHash = await hashPassword(newPass);

            const [affectedRows] = await ctx.wo_users.unscoped().update(
                { password: newHash },
                { where: { user_id: result.entry.userId } }
            );

            if (affectedRows === 0) {
                return res.json({ api_status: 500, error_message: 'Не вдалося оновити пароль.' });
            }

            // Анулювання всіх сесій після зміни пароля
            await ctx.wo_appssessions.destroy({ where: { user_id: result.entry.userId } }).catch(() => {});

            console.log(`[Auth] Password changed for user ${result.entry.userId}`);
            return res.json({ api_status: 200, message: 'Пароль успішно змінено' });

        } catch (err) {
            console.error('[Auth/reset-password]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера.' });
        }
    };
}

// ─── POST /api/node/auth/quick-register ──────────────────────────────────────
// Крок 1: реєстрація/вхід по email або телефону — надсилає OTP.
// Якщо акаунт не існує — створює новий автоматично.

function quickRegister(ctx) {
    return async (req, res) => {
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();

        if (!email && !phone) {
            return res.json({ api_status: 400, error_message: 'Вкажіть email або номер телефону' });
        }

        // Валідація номера телефону — має починатись з + і містити тільки цифри
        if (phone && !/^\+\d{7,15}$/.test(phone)) {
            return res.json({
                api_status: 400,
                error_message: 'Невірний формат телефону. Використовуйте міжнародний формат: +380XXXXXXXXX'
            });
        }

        try {
            let user  = email ? await findUserByEmail(ctx, email) : await findUserByPhone(ctx, phone);
            let isNew = false;

            if (!user) {
                // Новий акаунт — реєструємо автоматично
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
            }

            const code    = generateCode(6);
            const contact = email || phone;

            await storeOtp('quick', contact, code, { userId: user.user_id, isNew });

            if (email) {
                await sendEmail(
                    email,
                    isNew ? 'Ласкаво просимо до WorldMates!' : 'Код для входу — WorldMates',
                    emailTemplate(
                        isNew ? 'Ласкаво просимо!' : 'Код для входу',
                        `<p style="color:#555">${isNew
                            ? 'Ваш акаунт WorldMates створено! Підтвердіть кодом нижче:'
                            : 'Ваш одноразовий код для входу до WorldMates:'
                        }</p>${codeBlock(code)}`
                    )
                );
            } else {
                const smsText = isNew
                    ? `WorldMates: ваш код реєстрації: ${code}. Дійсний 10 хвилин.`
                    : `WorldMates: ваш код для входу: ${code}. Дійсний 10 хвилин.`;
                await sendSms(phone, smsText);
            }

            return res.json({
                api_status: 200,
                message:    'Код підтвердження надіслано',
                user_id:    user.user_id,
                username:   user.username,
                is_new:     isNew,
            });

        } catch (err) {
            console.error('[Auth/quick-register]', err.message);

            // Більш інформативна помилка якщо Twilio не налаштовано
            if (err.message.includes('Twilio')) {
                return res.json({ api_status: 503, error_message: err.message });
            }

            return res.json({ api_status: 500, error_message: 'Помилка сервера. Спробуйте пізніше.' });
        }
    };
}

// ─── POST /api/node/auth/quick-verify ────────────────────────────────────────
// Крок 2: перевірка OTP — повертає access_token.

function quickVerify(ctx) {
    return async (req, res) => {
        const email = (req.body.email || '').trim().toLowerCase();
        const phone = (req.body.phone_number || '').trim();
        const code  = (req.body.code || '').trim();

        if (!code) {
            return res.json({ api_status: 400, error_message: 'Вкажіть код підтвердження' });
        }

        const contact = email || phone;
        if (!contact) {
            return res.json({ api_status: 400, error_message: 'Вкажіть email або номер телефону' });
        }

        const result = await checkOtp('quick', contact, code);
        if (!result.ok) {
            return res.json({ api_status: 400, error_message: otpErrorMessage(result) });
        }

        try {
            const user = await findUserById(ctx, result.entry.userId);
            if (!user) {
                return res.json({ api_status: 400, error_message: 'Акаунт не знайдено' });
            }

            const token = await createSession(ctx, user.user_id, 'phone', 'quick-auth');

            ctx.wo_users.update(
                { lastseen: Math.floor(Date.now() / 1000) },
                { where: { user_id: user.user_id } }
            ).catch(() => {});

            console.log(`[Auth] Quick-auth OK for user ${user.user_id}`);
            return res.json({
                api_status:   200,
                access_token: token,
                user_id:      user.user_id,
                username:     user.username,
                first_name:   user.first_name,
                last_name:    user.last_name,
                avatar:       user.avatar,
                is_new:       result.entry.isNew || false,
                message:      'Авторизація успішна',
            });

        } catch (err) {
            console.error('[Auth/quick-verify]', err.message);
            return res.json({ api_status: 500, error_message: 'Помилка сервера.' });
        }
    };
}

// ─── Register routes ──────────────────────────────────────────────────────────

function registerAuthRoutes(app, ctx) {
    app.post('/api/node/auth/login',                  login(ctx));
    app.post('/api/node/auth/logout',                 logout(ctx));
    app.post('/api/node/auth/request-password-reset', requestPasswordReset(ctx));
    app.post('/api/node/auth/reset-password',         resetPassword(ctx));
    app.post('/api/node/auth/quick-register',         quickRegister(ctx));
    app.post('/api/node/auth/quick-verify',           quickVerify(ctx));

    console.log('[Auth] Routes registered:');
    console.log('  POST /api/node/auth/login');
    console.log('  POST /api/node/auth/logout');
    console.log('  POST /api/node/auth/request-password-reset');
    console.log('  POST /api/node/auth/reset-password');
    console.log('  POST /api/node/auth/quick-register');
    console.log('  POST /api/node/auth/quick-verify');
}

module.exports = { registerAuthRoutes };
