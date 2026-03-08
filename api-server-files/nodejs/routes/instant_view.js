'use strict';

/**
 * Instant View
 *
 * Fetches a web article URL, extracts the main content (title, text, images),
 * and returns it in a clean structured format for in-app reading.
 *
 * Endpoint:
 *   POST /api/node/instant-view   { url: 'https://...' }
 *
 * Returns:
 *   { api_status, url, title, description, site_name, image, content_html, reading_time_min }
 *
 * Security: only fetches HTTP/HTTPS URLs with a 5-second timeout.
 * Does NOT proxy images — client loads them directly.
 */

const https = require('https');
const http  = require('http');
const { URL } = require('url');

const MAX_CONTENT_BYTES = 512 * 1024; // 512 KB
const FETCH_TIMEOUT_MS  = 7_000;

const ALLOWED_SCHEMES = ['http:', 'https:'];

function fetchUrl(rawUrl) {
    return new Promise((resolve, reject) => {
        let parsedUrl;
        try {
            parsedUrl = new URL(rawUrl);
        } catch {
            return reject(new Error('Invalid URL'));
        }

        if (!ALLOWED_SCHEMES.includes(parsedUrl.protocol))
            return reject(new Error('Only http/https URLs are supported'));

        const lib     = parsedUrl.protocol === 'https:' ? https : http;
        const options = {
            hostname: parsedUrl.hostname,
            path:     parsedUrl.pathname + parsedUrl.search,
            port:     parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
            method:   'GET',
            headers: {
                'User-Agent':      'WorldMatesBot/2.0 (+https://worldmates.com/bot)',
                'Accept':          'text/html,application/xhtml+xml',
                'Accept-Language': 'en-US,en;q=0.9',
            },
            timeout: FETCH_TIMEOUT_MS,
        };

        const req = lib.request(options, resp => {
            // Follow single redirect
            if ((resp.statusCode === 301 || resp.statusCode === 302) && resp.headers.location) {
                return fetchUrl(resp.headers.location).then(resolve).catch(reject);
            }
            if (resp.statusCode !== 200) {
                return reject(new Error(`HTTP ${resp.statusCode}`));
            }

            const ct = resp.headers['content-type'] || '';
            if (!ct.includes('html') && !ct.includes('xml')) {
                return reject(new Error('URL does not return HTML content'));
            }

            let body = '';
            let bytes = 0;
            resp.setEncoding('utf8');
            resp.on('data', chunk => {
                bytes += Buffer.byteLength(chunk, 'utf8');
                if (bytes > MAX_CONTENT_BYTES) { resp.destroy(); return; }
                body += chunk;
            });
            resp.on('end', () => resolve(body));
        });

        req.on('timeout', () => { req.destroy(); reject(new Error('Request timed out')); });
        req.on('error', reject);
        req.end();
    });
}

// Very lightweight HTML parser — no external deps
function extractMeta(html) {
    function getMeta(name) {
        // og: and twitter: meta tags
        const patterns = [
            new RegExp(`<meta[^>]+property=["']og:${name}["'][^>]+content=["']([^"']+)["']`, 'i'),
            new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:${name}["']`, 'i'),
            new RegExp(`<meta[^>]+name=["']twitter:${name}["'][^>]+content=["']([^"']+)["']`, 'i'),
            new RegExp(`<meta[^>]+name=["']${name}["'][^>]+content=["']([^"']+)["']`, 'i'),
        ];
        for (const re of patterns) {
            const m = html.match(re);
            if (m) return decodeHtmlEntities(m[1]);
        }
        return '';
    }

    function getTitle() {
        const og = getMeta('title');
        if (og) return og;
        const m = html.match(/<title[^>]*>([^<]+)<\/title>/i);
        return m ? decodeHtmlEntities(m[1].trim()) : '';
    }

    return {
        title:       getTitle(),
        description: getMeta('description'),
        image:       getMeta('image'),
        site_name:   getMeta('site_name'),
    };
}

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

function stripTags(html) {
    return html.replace(/<[^>]+>/g, ' ').replace(/\s{2,}/g, ' ').trim();
}

function extractBody(html) {
    // Try <article> first, then <main>, then <body>
    const selectors = [
        /<article[^>]*>([\s\S]*?)<\/article>/i,
        /<main[^>]*>([\s\S]*?)<\/main>/i,
        /<div[^>]+class=["'][^"']*(?:article|post|content|entry|text|story)[^"']*["'][^>]*>([\s\S]*?)<\/div>/i,
        /<body[^>]*>([\s\S]*?)<\/body>/i,
    ];

    for (const re of selectors) {
        const m = html.match(re);
        if (m && m[1] && stripTags(m[1]).length > 200) {
            return m[1];
        }
    }
    return html;
}

function cleanHtml(raw) {
    return raw
        // Remove script/style/noscript/nav/header/footer/aside/form/iframe
        .replace(/<(script|style|noscript|nav|header|footer|aside|form|iframe)[^>]*>[\s\S]*?<\/\1>/gi, '')
        // Keep only safe tags
        .replace(/<(?!\/?(?:p|br|h[1-6]|ul|ol|li|a|img|blockquote|em|strong|i|b|figure|figcaption|picture|source)\b)[^>]+>/gi, ' ')
        // Clean attributes (keep only src/alt/href)
        .replace(/<(img|a|source)\s+[^>]*?(src|href|alt)=["']([^"']+)["'][^>]*>/gi, (m, tag, attr, val) => {
            if (tag.toLowerCase() === 'img') return `<img src="${val}" alt="">`;
            if (tag.toLowerCase() === 'a')   return `<a href="${val}">`;
            return m;
        })
        .replace(/\s{3,}/g, ' ')
        .trim();
}

function estimateReadingTime(html) {
    const words = stripTags(html).split(/\s+/).length;
    return Math.max(1, Math.round(words / 200));
}

// ─── Route handler ────────────────────────────────────────────────────────────

function instantView(ctx, io) {
    return async (req, res) => {
        try {
            const rawUrl = (req.body.url || '').trim();
            if (!rawUrl)
                return res.json({ api_status: 400, error_message: 'url is required' });

            // Validate URL
            let parsedUrl;
            try {
                parsedUrl = new URL(rawUrl);
            } catch {
                return res.json({ api_status: 400, error_message: 'Invalid URL' });
            }
            if (!ALLOWED_SCHEMES.includes(parsedUrl.protocol))
                return res.json({ api_status: 400, error_message: 'Only http/https URLs are supported' });

            console.log(`[InstantView] Fetching: ${rawUrl}`);

            const html  = await fetchUrl(rawUrl);
            const meta  = extractMeta(html);
            const body  = extractBody(html);
            const clean = cleanHtml(body);
            const rtMin = estimateReadingTime(clean);

            console.log(`[InstantView] OK: "${meta.title}" reading_time=${rtMin}min`);

            return res.json({
                api_status:       200,
                url:              rawUrl,
                title:            meta.title,
                description:      meta.description,
                site_name:        meta.site_name || parsedUrl.hostname,
                image:            meta.image,
                content_html:     clean,
                reading_time_min: rtMin,
            });
        } catch (err) {
            console.error('[InstantView]', err.message);
            return res.json({ api_status: 500, error_message: err.message || 'Failed to fetch article' });
        }
    };
}

module.exports = { instantView };
