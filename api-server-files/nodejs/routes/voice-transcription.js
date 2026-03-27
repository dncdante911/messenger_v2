'use strict';

/**
 * Voice Message Transcription API — PRO only
 *
 * POST /api/node/voice/transcribe
 *   Body: { url }          — URL of the voice message audio file
 *   Returns: { api_status: 200, transcript: "...", language: "uk" }
 *
 * Requires:
 *   PRO subscription: wo_users.is_pro = '1' AND pro_time > NOW()
 *   OPENAI_API_KEY env var
 *
 * Uses OpenAI Whisper-1 (https://api.openai.com/v1/audio/transcriptions)
 * Audio is downloaded server-side and proxied to OpenAI — no client key exposure.
 */

const https  = require('https');
const http   = require('http');
const url    = require('url');
const crypto = require('crypto');
const { requireAuth } = require('../helpers/validate-token');

const WHISPER_ENDPOINT = 'https://api.openai.com/v1/audio/transcriptions';
const OPENAI_API_KEY   = process.env.OPENAI_API_KEY || '';

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Download a remote URL to a Buffer.
 * Follows one level of redirect (HTTP → HTTPS or same protocol).
 */
function downloadToBuffer(fileUrl) {
    return new Promise((resolve, reject) => {
        const parsed   = url.parse(fileUrl);
        const protocol = parsed.protocol === 'https:' ? https : http;

        protocol.get(fileUrl, (res) => {
            // Follow redirect
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return downloadToBuffer(res.headers.location).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                return reject(new Error(`HTTP ${res.statusCode} downloading audio`));
            }
            const chunks = [];
            res.on('data', chunk => chunks.push(chunk));
            res.on('end',  ()    => resolve(Buffer.concat(chunks)));
            res.on('error', reject);
        }).on('error', reject);
    });
}

/**
 * Send multipart/form-data POST to OpenAI Whisper.
 * Returns parsed JSON response body.
 */
function whisperTranscribe(audioBuffer, filename) {
    return new Promise((resolve, reject) => {
        if (!OPENAI_API_KEY) return reject(new Error('OPENAI_API_KEY is not configured'));

        const boundary = '----WMBoundary' + crypto.randomBytes(12).toString('hex');

        // Build multipart body
        const partFile  = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: audio/mpeg\r\n\r\n`),
            audioBuffer,
            Buffer.from('\r\n'),
        ]);
        const partModel = Buffer.from(
            `--${boundary}\r\nContent-Disposition: form-data; name="model"\r\n\r\nwhisper-1\r\n`
        );
        const partLang  = Buffer.from(
            `--${boundary}\r\nContent-Disposition: form-data; name="language"\r\n\r\nuk\r\n`
        );
        const partEnd   = Buffer.from(`--${boundary}--\r\n`);
        const body      = Buffer.concat([partFile, partModel, partLang, partEnd]);

        const options = {
            hostname: 'api.openai.com',
            path:     '/v1/audio/transcriptions',
            method:   'POST',
            headers: {
                'Authorization':  `Bearer ${OPENAI_API_KEY}`,
                'Content-Type':   `multipart/form-data; boundary=${boundary}`,
                'Content-Length': body.length,
            },
        };

        const req = https.request(options, (res) => {
            const chunks = [];
            res.on('data',  chunk => chunks.push(chunk));
            res.on('end', () => {
                try {
                    const json = JSON.parse(Buffer.concat(chunks).toString());
                    resolve(json);
                } catch (e) {
                    reject(new Error('Invalid JSON from OpenAI'));
                }
            });
            res.on('error', reject);
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ─── POST /api/node/voice/transcribe ─────────────────────────────────────────

function transcribeVoice(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            // PRO check
            const user = await ctx.wo_users.findOne({
                where: { user_id: req.userId },
                attributes: ['is_pro', 'pro_time'],
                raw: true,
            });
            const nowSec  = Math.floor(Date.now() / 1000);
            const isPro   = user && user.is_pro === '1' && parseInt(user.pro_time, 10) > nowSec;
            if (!isPro) {
                return res.status(403).json({
                    api_status:    403,
                    error_message: 'Voice transcription is a PRO feature',
                });
            }

            const { url: audioUrl } = req.body;
            if (!audioUrl) {
                return res.status(400).json({ api_status: 400, error_message: 'url required' });
            }

            // Basic URL validation — must be absolute http(s)
            if (!/^https?:\/\/.+/.test(audioUrl)) {
                return res.status(400).json({ api_status: 400, error_message: 'url must be an absolute http(s) URL' });
            }

            // Download the audio
            let audioBuffer;
            try {
                audioBuffer = await downloadToBuffer(audioUrl);
            } catch (e) {
                return res.status(422).json({ api_status: 422, error_message: `Could not download audio: ${e.message}` });
            }

            if (audioBuffer.length > 25 * 1024 * 1024) {
                return res.status(413).json({ api_status: 413, error_message: 'Audio file exceeds 25 MB limit' });
            }

            // Derive filename from URL
            const filename = audioUrl.split('/').pop().split('?')[0] || 'voice.ogg';

            // Call OpenAI Whisper
            let whisperResult;
            try {
                whisperResult = await whisperTranscribe(audioBuffer, filename);
            } catch (e) {
                console.error('[Voice/transcribe] Whisper error:', e.message);
                return res.status(502).json({ api_status: 502, error_message: `Transcription failed: ${e.message}` });
            }

            if (whisperResult.error) {
                return res.status(502).json({ api_status: 502, error_message: whisperResult.error.message });
            }

            return res.json({
                api_status: 200,
                transcript: whisperResult.text || '',
                language:   whisperResult.language || 'uk',
            });
        } catch (err) {
            console.error('[Voice/transcribe] Error:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerVoiceTranscriptionRoutes(app, ctx) {
    app.post('/api/node/voice/transcribe', transcribeVoice(ctx));
    console.log('[VoiceTranscription] POST /api/node/voice/transcribe registered (PRO only)');
}

module.exports = { registerVoiceTranscriptionRoutes };
