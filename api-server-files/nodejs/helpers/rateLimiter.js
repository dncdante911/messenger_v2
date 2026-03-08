'use strict';

/**
 * In-memory sliding-window rate limiter.
 *
 * No external dependencies required.
 * Works correctly in single-process (fork-mode PM2) deployments.
 *
 * For multi-process cluster deployments, replace with a Redis-backed
 * solution such as `rate-limit-redis` + `express-rate-limit`.
 *
 * Usage:
 *   const { createRateLimiter } = require('./helpers/rateLimiter');
 *   const limiter = createRateLimiter({ windowMs: 60_000, max: 100 });
 *   app.use(limiter);
 */

/**
 * @param {object} opts
 * @param {number}   opts.windowMs  - Window length in ms (default: 60 000)
 * @param {number}   opts.max       - Max requests per window per key (default: 100)
 * @param {string}   opts.message   - Error message sent on 429 (default: generic)
 * @param {Function} opts.keyFn     - (req) => string key (default: client IP)
 * @returns {Function} Express middleware
 */
function createRateLimiter({
    windowMs = 60_000,
    max      = 100,
    message  = 'Too many requests, please try again later',
    keyFn    = defaultKeyFn,
} = {}) {
    // key -> [timestamp, ...]
    const store = new Map();

    // Periodic cleanup to prevent unbounded memory growth.
    // setInterval(...).unref() lets the process exit even if the interval is alive.
    const cleanupInterval = setInterval(() => {
        const cutoff = Date.now() - windowMs;
        for (const [key, times] of store) {
            const fresh = times.filter(t => t > cutoff);
            if (fresh.length === 0) store.delete(key);
            else store.set(key, fresh);
        }
    }, windowMs).unref();

    function middleware(req, res, next) {
        const key    = keyFn(req);
        const now    = Date.now();
        const cutoff = now - windowMs;
        const times  = (store.get(key) || []).filter(t => t > cutoff);

        if (times.length >= max) {
            const retryAfter = Math.ceil(windowMs / 1000);
            res.set('Retry-After', String(retryAfter));
            return res.status(429).json({
                api_status:    429,
                error_message: message,
                retry_after:   retryAfter,
            });
        }

        times.push(now);
        store.set(key, times);
        next();
    }

    /** Call this if you need to tear down (e.g. in tests). */
    middleware.destroy = () => clearInterval(cleanupInterval);

    return middleware;
}

function defaultKeyFn(req) {
    // Respect X-Forwarded-For if behind a reverse proxy (nginx/apache).
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) return forwarded.split(',')[0].trim();
    return req.socket.remoteAddress || 'unknown';
}

module.exports = { createRateLimiter };
