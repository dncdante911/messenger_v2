'use strict';

/**
 * Message Translator API
 *
 * Endpoint:
 *   POST /api/node/chat/translate
 *
 * Body:
 *   text        {string}  — text to translate (required)
 *   target_lang {string}  — BCP-47 / ISO 639-1 language code, e.g. "uk", "en", "de" (required)
 *   source_lang {string}  — source language; omit or "auto" for auto-detect (optional)
 *   provider    {string}  — "google" | "deepl"; auto-selected if omitted (optional)
 *
 * Config (.env):
 *   GOOGLE_TRANSLATE_KEY  — Google Cloud Translate v2 API key
 *   DEEPL_API_KEY         — DeepL API authentication key (free or pro)
 *   DEEPL_API_URL         — DeepL base URL (optional)
 *                           Free:  https://api-free.deepl.com  (default)
 *                           Pro:   https://api.deepl.com
 *
 * Provider selection priority (when provider not specified in request):
 *   1. DeepL  — if DEEPL_API_KEY is set
 *   2. Google — if GOOGLE_TRANSLATE_KEY is set
 *   Returns 503 if neither is configured.
 */

const https = require('https');
const http  = require('http');
const { URL } = require('url');

// ── Auth middleware ───────────────────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token'] || req.query.access_token || req.body?.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid access_token' });
        req.userId = session.user_id;
        next();
    } catch (e) {
        return res.status(500).json({ api_status: 500, error_message: 'Auth error' });
    }
}

// ── Google Translate v2 ───────────────────────────────────────────────────────
async function googleTranslate(text, targetLang, sourceLang) {
    const apiKey = process.env.GOOGLE_TRANSLATE_KEY;
    if (!apiKey) throw new Error('GOOGLE_TRANSLATE_KEY not configured');

    const params = new URLSearchParams({ q: text, target: targetLang, key: apiKey, format: 'text' });
    if (sourceLang && sourceLang !== 'auto') params.set('source', sourceLang);

    const urlStr = `https://translation.googleapis.com/language/translate/v2?${params.toString()}`;

    return new Promise((resolve, reject) => {
        https.get(urlStr, (resp) => {
            let data = '';
            resp.on('data', c => data += c);
            resp.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (json.error) return reject(new Error(json.error.message));
                    const t = json.data.translations[0];
                    resolve({
                        translated_text:      t.translatedText,
                        detected_source_lang: t.detectedSourceLanguage || null,
                    });
                } catch (e) { reject(e); }
            });
        }).on('error', reject);
    });
}

// ── DeepL v2 ─────────────────────────────────────────────────────────────────
async function deeplTranslate(text, targetLang, sourceLang) {
    const apiKey  = process.env.DEEPL_API_KEY;
    const baseUrl = (process.env.DEEPL_API_URL || 'https://api-free.deepl.com').replace(/\/$/, '');
    if (!apiKey) throw new Error('DEEPL_API_KEY not configured');

    const body = JSON.stringify({
        text:        [text],
        target_lang: targetLang.toUpperCase(),
        ...(sourceLang && sourceLang !== 'auto' ? { source_lang: sourceLang.toUpperCase() } : {}),
    });

    const parsed = new URL(`${baseUrl}/v2/translate`);
    const lib    = parsed.protocol === 'https:' ? https : http;

    return new Promise((resolve, reject) => {
        const req = lib.request({
            hostname: parsed.hostname,
            path:     parsed.pathname,
            method:   'POST',
            headers:  {
                'Authorization':  `DeepL-Auth-Key ${apiKey}`,
                'Content-Type':   'application/json',
                'Content-Length': Buffer.byteLength(body),
            },
        }, (resp) => {
            let data = '';
            resp.on('data', c => data += c);
            resp.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (!json.translations) return reject(new Error(json.message || 'DeepL API error'));
                    const t = json.translations[0];
                    resolve({
                        translated_text:      t.text,
                        detected_source_lang: t.detected_source_language || null,
                    });
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ── Route registration ────────────────────────────────────────────────────────
module.exports = function registerTranslatorRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    /**
     * POST /api/node/chat/translate
     */
    app.post('/api/node/chat/translate', auth, async (req, res) => {
        try {
            const text       = (req.body.text        || '').toString().trim();
            const targetLang = (req.body.target_lang  || '').toString().trim();
            const sourceLang = (req.body.source_lang  || 'auto').toString().trim();
            let   provider   = (req.body.provider     || '').toLowerCase().trim();

            if (!text)       return res.status(400).json({ api_status: 400, error_message: 'text is required' });
            if (!targetLang) return res.status(400).json({ api_status: 400, error_message: 'target_lang is required' });

            // Auto-select provider when not specified
            if (!provider || !['google', 'deepl'].includes(provider)) {
                if (process.env.DEEPL_API_KEY)          provider = 'deepl';
                else if (process.env.GOOGLE_TRANSLATE_KEY) provider = 'google';
                else return res.status(503).json({
                    api_status:    503,
                    error_message: 'No translation provider configured. Set DEEPL_API_KEY or GOOGLE_TRANSLATE_KEY in .env',
                });
            }

            let result;
            if (provider === 'deepl') {
                result = await deeplTranslate(text, targetLang, sourceLang);
            } else {
                result = await googleTranslate(text, targetLang, sourceLang);
            }

            return res.json({
                api_status:           200,
                provider,
                original_text:        text,
                translated_text:      result.translated_text,
                target_lang:          targetLang,
                detected_source_lang: result.detected_source_lang,
            });

        } catch (e) {
            console.error('[Translator] error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    console.log('[Translator API] Endpoint registered: POST /api/node/chat/translate');
};
