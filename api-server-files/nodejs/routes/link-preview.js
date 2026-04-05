'use strict';

/**
 * Link Preview — fetches OpenGraph / Twitter Card / <title> metadata for a URL.
 *
 * POST /api/node/link-preview
 *   Body: { url: "https://..." }
 *   Returns: { api_status, url, title, description, image, hostname }
 *
 * Uses only Node.js built-in https/http — no external dependencies.
 * Follows one redirect, caps response at 300 KB, times out after 6 s.
 */

const https = require('https');
const http  = require('http');
const { URL } = require('url');

// ─── constants ────────────────────────────────────────────────────────────────

const MAX_BYTES    = 300 * 1024;   // 300 KB — enough for <head>
const TIMEOUT_MS   = 6_000;
const MAX_REDIRECTS = 2;

// Friendly UA so most sites don't block the request
const USER_AGENT = 'Mozilla/5.0 (compatible; WorldMatesBot/1.0; +https://worldmates.club)';

// ─── helpers ─────────────────────────────────────────────────────────────────

/**
 * Fetch raw HTML from url, following up to maxRedirects redirects.
 * Resolves with the first ≤MAX_BYTES chunk of the response body.
 */
function fetchHtml(rawUrl, redirectsLeft = MAX_REDIRECTS) {
    return new Promise((resolve, reject) => {
        let parsed;
        try { parsed = new URL(rawUrl); } catch (e) {
            return reject(new Error('Invalid URL'));
        }

        const client  = parsed.protocol === 'https:' ? https : http;
        const options = {
            hostname: parsed.hostname,
            port:     parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
            path:     parsed.pathname + parsed.search,
            method:   'GET',
            timeout:  TIMEOUT_MS,
            headers: {
                'User-Agent': USER_AGENT,
                'Accept':     'text/html,application/xhtml+xml',
                'Accept-Language': 'en,uk;q=0.9,ru;q=0.8',
            },
        };

        const req = client.request(options, (resp) => {
            // Handle redirect
            if (resp.statusCode >= 300 && resp.statusCode < 400 && resp.headers.location && redirectsLeft > 0) {
                resp.destroy();
                // Resolve relative redirects
                let nextUrl = resp.headers.location;
                if (!nextUrl.startsWith('http')) {
                    nextUrl = `${parsed.protocol}//${parsed.host}${nextUrl}`;
                }
                return resolve(fetchHtml(nextUrl, redirectsLeft - 1));
            }

            const contentType = resp.headers['content-type'] || '';
            if (!contentType.includes('text/html') && !contentType.includes('application/xhtml')) {
                resp.destroy();
                return resolve(''); // Not HTML — no metadata to extract
            }

            let body = '';
            resp.setEncoding('utf8');

            resp.on('data', chunk => {
                body += chunk;
                // Stop after we have enough to parse <head>
                if (body.length > MAX_BYTES) resp.destroy();
            });
            resp.on('end',   () => resolve(body));
            resp.on('close', () => resolve(body)); // fired when we destroy early
        });

        req.on('error',   reject);
        req.on('timeout', () => { req.destroy(); reject(new Error('Request timeout')); });
        req.end();
    });
}

/**
 * Extract OpenGraph / Twitter Card / fallback metadata from raw HTML.
 */
function extractMeta(html, pageUrl) {
    if (!html) return buildEmpty(pageUrl);

    const getOg = (property) => {
        // <meta property="og:..." content="..."> or reversed attribute order
        const re1 = new RegExp(`<meta[^>]+property=["']${property}["'][^>]+content=["']([^"'<>]{0,600})["']`, 'i');
        const re2 = new RegExp(`<meta[^>]+content=["']([^"'<>]{0,600})["'][^>]+property=["']${property}["']`, 'i');
        return (html.match(re1) || html.match(re2))?.[1]?.trim() || null;
    };

    const getName = (name) => {
        const re1 = new RegExp(`<meta[^>]+name=["']${name}["'][^>]+content=["']([^"'<>]{0,600})["']`, 'i');
        const re2 = new RegExp(`<meta[^>]+content=["']([^"'<>]{0,600})["'][^>]+name=["']${name}["']`, 'i');
        return (html.match(re1) || html.match(re2))?.[1]?.trim() || null;
    };

    const title = getOg('og:title')
               || getName('twitter:title')
               || html.match(/<title[^>]*>\s*([^<]{1,200}?)\s*<\/title>/i)?.[1]?.trim()
               || '';

    const description = getOg('og:description')
                     || getName('description')
                     || getName('twitter:description')
                     || '';

    let image = getOg('og:image')
             || getName('twitter:image')
             || getName('twitter:image:src')
             || '';

    // Resolve relative image URLs
    if (image && !image.startsWith('http')) {
        try {
            const base = new URL(pageUrl);
            image = new URL(image, base).href;
        } catch (_) { image = ''; }
    }

    let hostname = '';
    try { hostname = new URL(pageUrl).hostname.replace(/^www\./, ''); } catch (_) {}

    return {
        url:         pageUrl,
        title:       decodeHtmlEntities(title).slice(0, 200),
        description: decodeHtmlEntities(description).slice(0, 500),
        image:       image.slice(0, 500),
        hostname,
    };
}

function buildEmpty(url) {
    let hostname = '';
    try { hostname = new URL(url).hostname.replace(/^www\./, ''); } catch (_) {}
    return { url, title: '', description: '', image: '', hostname };
}

/** Decode the most common HTML entities that appear in meta content attributes. */
function decodeHtmlEntities(str) {
    if (!str) return '';
    return str
        .replace(/&amp;/g,  '&')
        .replace(/&lt;/g,   '<')
        .replace(/&gt;/g,   '>')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g,  "'")
        .replace(/&nbsp;/g, ' ');
}

// ─── route handler ────────────────────────────────────────────────────────────

function registerLinkPreviewRoutes(app) {
    /**
     * POST /api/node/link-preview
     * No auth required — rate-limited by the global middleware in main.js.
     * Body: { url }
     */
    app.post('/api/node/link-preview', async (req, res) => {
        const rawUrl = (req.body.url || '').trim();

        if (!rawUrl) {
            return res.json({ api_status: 400, error_message: 'url is required' });
        }

        // Only http(s) URLs
        if (!/^https?:\/\//i.test(rawUrl)) {
            return res.json({ api_status: 400, error_message: 'URL must start with http:// or https://' });
        }

        // Block private / LAN addresses to prevent SSRF
        try {
            const { hostname } = new URL(rawUrl);
            if (/^(localhost|127\.|10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)/.test(hostname)) {
                return res.json({ api_status: 400, error_message: 'Private URLs not allowed' });
            }
        } catch (_) {
            return res.json({ api_status: 400, error_message: 'Invalid URL' });
        }

        try {
            const html    = await fetchHtml(rawUrl);
            const preview = extractMeta(html, rawUrl);
            return res.json({ api_status: 200, ...preview });
        } catch (err) {
            console.warn('[LinkPreview]', err.message);
            // Return empty preview rather than error so client can degrade gracefully
            return res.json({ api_status: 200, ...buildEmpty(rawUrl) });
        }
    });

    console.log('[LinkPreview] POST /api/node/link-preview registered');
}

module.exports = { registerLinkPreviewRoutes };
