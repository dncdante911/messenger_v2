'use strict';

/**
 * Anti-Spam — WorldMates Messenger
 * ==================================
 * In-memory protection for group/channel message sending.
 *
 * Checks (in order):
 *   1. Rate limit   — max RATE_MAX messages per user per RATE_WINDOW_MS
 *   2. Duplicate    — same text sent twice within DUPE_WINDOW_MS
 *   3. Link spam    — >LINK_MAX URLs in one msg from accounts < ACCOUNT_MIN_DAYS old
 *
 * All thresholds are configurable via env vars.
 * fail-safe: never blocks on internal error.
 */

const RATE_MAX         = parseInt(process.env.SPAM_RATE_MAX        || '30',    10); // msgs/window
const RATE_WINDOW_MS   = parseInt(process.env.SPAM_RATE_WINDOW_MS  || '60000', 10); // 1 min
const DUPE_WINDOW_MS   = parseInt(process.env.SPAM_DUPE_WINDOW_MS  || '30000', 10); // 30 s
const LINK_MAX         = parseInt(process.env.SPAM_LINK_MAX        || '3',     10); // URLs/msg
const ACCOUNT_MIN_DAYS = parseInt(process.env.SPAM_ACCOUNT_MIN_DAYS || '7',    10); // days

// URL detection — simple but effective
const URL_RE = /https?:\/\/[^\s]{4,}/gi;

// ── In-memory stores ───────────────────────────────────────────────────────────
// key: String(userId)

const rateStore = new Map(); // userId → [timestamp, ...]
const dupeStore = new Map(); // userId → [{ hash, ts }]

// Cleanup old entries every 2 minutes to prevent unbounded growth
setInterval(() => {
    const now = Date.now();
    for (const [k, times] of rateStore) {
        const fresh = times.filter(t => t > now - RATE_WINDOW_MS);
        if (fresh.length === 0) rateStore.delete(k); else rateStore.set(k, fresh);
    }
    for (const [k, dupes] of dupeStore) {
        const fresh = dupes.filter(d => d.ts > now - DUPE_WINDOW_MS);
        if (fresh.length === 0) dupeStore.delete(k); else dupeStore.set(k, fresh);
    }
}, 120_000).unref();

// ── Fast non-crypto hash (djb2) ───────────────────────────────────────────────

function fastHash(str) {
    let h = 5381;
    for (let i = 0; i < str.length; i++) {
        h = (h * 33 ^ str.charCodeAt(i)) >>> 0;
    }
    return String(h);
}

// ── Main check ────────────────────────────────────────────────────────────────

/**
 * Check a message for spam patterns.
 *
 * @param {number} userId           — sender's user_id
 * @param {string} text             — plaintext of the message (may be empty for media)
 * @param {number} [accountJoinedTs=0] — unix timestamp when user registered (for link spam gate)
 * @returns {{ blocked: boolean, reason: string|null }}
 */
function checkSpam(userId, text = '', accountJoinedTs = 0) {
    try {
        const now = Date.now();
        const key = String(userId);

        // 1. Per-user rate limit
        const times = (rateStore.get(key) || []).filter(t => t > now - RATE_WINDOW_MS);
        if (times.length >= RATE_MAX) {
            return { blocked: true, reason: 'rate_limit' };
        }
        times.push(now);
        rateStore.set(key, times);

        // 2. Duplicate message detection (only for non-trivial text)
        const trimmed = text.trim();
        if (trimmed.length > 2) {
            const hash  = fastHash(trimmed.toLowerCase());
            const dupes = (dupeStore.get(key) || []).filter(d => d.ts > now - DUPE_WINDOW_MS);
            if (dupes.some(d => d.hash === hash)) {
                return { blocked: true, reason: 'duplicate_message' };
            }
            dupes.push({ hash, ts: now });
            dupeStore.set(key, dupes);
        }

        // 3. Link spam for new accounts
        if (trimmed.length > 0 && accountJoinedTs > 0) {
            const accountAgeDays = (now / 1000 - accountJoinedTs) / 86400;
            if (accountAgeDays < ACCOUNT_MIN_DAYS) {
                const linkCount = (trimmed.match(URL_RE) || []).length;
                if (linkCount > LINK_MAX) {
                    return { blocked: true, reason: 'link_spam_new_account' };
                }
            }
        }

        return { blocked: false, reason: null };

    } catch (e) {
        // fail-open: never block a message due to internal error
        console.error('[AntiSpam] checkSpam error:', e.message);
        return { blocked: false, reason: null };
    }
}

/**
 * Express middleware factory — wraps checkSpam for route-level use.
 * Expects req.userId to be set (after requireAuth).
 *
 * Usage:
 *   router.post('/send', requireAuth(ctx), antiSpamMiddleware(ctx), handler)
 */
function antiSpamMiddleware(ctx) {
    return async (req, res, next) => {
        try {
            const userId = req.userId;
            if (!userId) return next(); // unauthenticated — let auth handle it

            const text = (req.body.text || '').trim();

            // Fetch account age (joined timestamp)
            let joinedTs = 0;
            try {
                const user = await ctx.wo_users.findOne({
                    where:      { user_id: userId },
                    attributes: ['joined'],
                    raw:        true
                });
                joinedTs = user?.joined || 0;
            } catch {
                // non-critical — skip age check
            }

            const result = checkSpam(userId, text, joinedTs);
            if (result.blocked) {
                console.warn(`[AntiSpam] BLOCKED user=${userId} reason=${result.reason}`);
                return res.status(429).json({
                    api_status:    429,
                    error_message: spamMessage(result.reason),
                    spam_reason:   result.reason
                });
            }

            next();
        } catch (e) {
            console.error('[AntiSpam] middleware error:', e.message);
            next(); // fail-open
        }
    };
}

function spamMessage(reason) {
    switch (reason) {
        case 'rate_limit':             return 'Вы отправляете сообщения слишком быстро. Подождите немного.';
        case 'duplicate_message':      return 'Это сообщение уже было отправлено. Подождите перед повтором.';
        case 'link_spam_new_account':  return 'Новые аккаунты не могут отправлять много ссылок в одном сообщении.';
        default:                       return 'Сообщение заблокировано системой защиты от спама.';
    }
}

module.exports = { checkSpam, antiSpamMiddleware };
