'use strict';

/**
 * Geo-blocking middleware for WorldMates
 *
 * Blocks requests from countries that are under Ukrainian sanctions or are
 * considered hostile based on current geopolitical context (2024-2026).
 *
 * Uses geoip-lite (MaxMind GeoLite2 bundled DB — no external API calls,
 * no latency, no rate limits).  Database updates ship with package updates.
 * Run `node node_modules/geoip-lite/scripts/updatedb.js` periodically to
 * refresh the bundled DB.
 *
 * Blocked by default:
 *   RU — Russia
 *   BY — Belarus
 *   IR — Iran
 *   IQ — Iraq
 *   KP — North Korea
 *   CN — China (full country; regional sub-blocking is not supported by
 *             GeoIP country-level resolution — if you need partial block
 *             you would need a city-level DB and ASN filtering)
 *
 * Always allowed (bypass geo-check):
 *   - Private / loopback addresses  (server-side calls, health checks)
 *   - Requests matching GEOBLOCK_WHITELIST_IPS env var (comma-separated)
 *   - Endpoints listed in BYPASS_PATHS (health check, admin probes)
 *
 * Environment overrides (in .env):
 *   GEOBLOCK_ENABLED=false         — disable entirely (e.g. for local dev)
 *   GEOBLOCK_EXTRA_COUNTRIES=SY,VE — add extra country codes to block list
 *   GEOBLOCK_WHITELIST_IPS=1.2.3.4,5.6.7.8 — always-allow specific IPs
 */

let geoip;
try {
    geoip = require('geoip-lite');
} catch (_) {
    console.warn('[GeoBlock] geoip-lite not installed — run: npm install geoip-lite');
    geoip = null;
}

// ── Blocked country codes ─────────────────────────────────────────────────────

const DEFAULT_BLOCKED = new Set([
    'RU',  // Russia   — aggressor state, EU/UA sanctions
    'BY',  // Belarus  — co-aggressor, EU/UA sanctions
    'IR',  // Iran     — supplies drones to Russia, under UN/EU sanctions
    'IQ',  // Iraq     — under various international sanctions regimes
    'KP',  // North Korea — UN sanctions; supplies weapons to Russia
    'CN',  // China    — full country block (partial block not feasible at
           //            IP-geolocation level without ASN/city filtering)
]);

// Append any extra codes from env (comma-separated)
const extraCodes = (process.env.GEOBLOCK_EXTRA_COUNTRIES || '')
    .split(',').map(c => c.trim().toUpperCase()).filter(Boolean);
for (const code of extraCodes) DEFAULT_BLOCKED.add(code);

// ── Whitelisted IPs (always bypass) ──────────────────────────────────────────

const whitelistEnv = (process.env.GEOBLOCK_WHITELIST_IPS || '')
    .split(',').map(s => s.trim()).filter(Boolean);
const WHITELIST_IPS = new Set(whitelistEnv);

// ── Paths that always bypass geo-check ───────────────────────────────────────

const BYPASS_PATHS = new Set([
    '/api/health',       // load-balancer probes
    '/api/node/update/check',  // mobile app update check (public CDN endpoint)
    '/favicon.ico',
]);

// ── IP helpers ────────────────────────────────────────────────────────────────

const PRIVATE_IP_RE = /^(127\.|::1$|::ffff:127\.|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|fc00:|fe80:)/;

function getClientIp(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) return forwarded.split(',')[0].trim();
    return req.socket?.remoteAddress || req.ip || '';
}

function isPrivateIp(ip) {
    return PRIVATE_IP_RE.test(ip);
}

// ── Middleware factory ────────────────────────────────────────────────────────

/**
 * Returns an Express middleware that blocks requests from sanctioned countries.
 * Call once during server startup and attach with app.use(createGeoblockMiddleware()).
 */
function createGeoblockMiddleware() {
    const enabled = process.env.GEOBLOCK_ENABLED !== 'false';

    if (!enabled) {
        console.log('[GeoBlock] Disabled via GEOBLOCK_ENABLED=false');
        return (_req, _res, next) => next();
    }

    if (!geoip) {
        console.warn('[GeoBlock] geoip-lite unavailable — blocking skipped');
        return (_req, _res, next) => next();
    }

    console.log(`[GeoBlock] Active. Blocked countries: ${[...DEFAULT_BLOCKED].join(', ')}`);

    return function geoblockMiddleware(req, res, next) {
        // Always let through bypass paths (health checks etc.)
        if (BYPASS_PATHS.has(req.path)) return next();

        const ip = getClientIp(req);

        // Always allow private/loopback addresses
        if (!ip || isPrivateIp(ip)) return next();

        // Always allow explicitly whitelisted IPs (e.g. your own CDN, admin)
        if (WHITELIST_IPS.has(ip)) return next();

        const geo = geoip.lookup(ip);
        if (!geo) return next(); // unknown IP → allow (geo DB doesn't cover all allocations)

        if (DEFAULT_BLOCKED.has(geo.country)) {
            console.warn(`[GeoBlock] Blocked ${ip} (${geo.country}) → ${req.method} ${req.path}`);
            return res.status(403).json({
                api_status:    403,
                error_message: 'Access from your region is not available.',
            });
        }

        next();
    };
}

module.exports = { createGeoblockMiddleware };
