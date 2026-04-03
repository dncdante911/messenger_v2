'use strict';

/**
 * Voice Message Transcription API — PRO only
 *
 * POST /api/node/voice/transcribe
 *   Body: { url }  — абсолютный URL голосового файла
 *   Returns: { api_status: 200, transcript: "...", language: "uk", engine: "gemini"|"whisper" }
 *
 * ── Движки (приоритет) ──────────────────────────────────────────────────────
 *
 *   1. Gemini 2.0 Flash  (GEMINI_API_KEY в .env)
 *      — Лучше для украинского/русского, бесплатный тир 1500 req/day,
 *        быстрее (~3–8 s). Лимит inline-аудио: 15 MB raw.
 *        Если файл > 15 MB — автоматически передаётся на Whisper.
 *
 *   2. OpenAI Whisper-1  (OPENAI_API_KEY в .env) — fallback
 *      — Используется если: GEMINI_API_KEY не задан, Gemini вернул ошибку,
 *        или файл > 15 MB. Лимит: 25 MB.
 *
 * ── .env ключи ──────────────────────────────────────────────────────────────
 *   GEMINI_API_KEY=ваш_ключ       # https://aistudio.google.com/apikey
 *   OPENAI_API_KEY=ваш_ключ       # https://platform.openai.com/api-keys (fallback)
 *
 * ── Формат аудио ────────────────────────────────────────────────────────────
 *   Поддерживаются: .ogg, .opus, .mp3, .m4a, .aac, .wav, .flac, .aiff
 */

const https  = require('https');
const http   = require('http');
const urlMod = require('url');
const crypto = require('crypto');
const { requireAuth } = require('../helpers/validate-token');

const GEMINI_API_KEY  = process.env.GEMINI_API_KEY  || '';
const OPENAI_API_KEY  = process.env.OPENAI_API_KEY  || '';

// Лимиты
const GEMINI_MAX_BYTES  = 15 * 1024 * 1024;  // 15 MB raw → ~20 MB base64 в JSON
const WHISPER_MAX_BYTES = 25 * 1024 * 1024;  // 25 MB (лимит OpenAI)
const GEMINI_TIMEOUT_MS = 75_000;
const WHISPER_TIMEOUT_MS = 75_000;

// Движок, который реально будет использован — логируется для отладки
let _activeEngine = 'none';

// ─── Общий хелпер: скачать URL в Buffer ──────────────────────────────────────

function downloadToBuffer(fileUrl) {
    return new Promise((resolve, reject) => {
        const parsed   = urlMod.parse(fileUrl);
        const protocol = parsed.protocol === 'https:' ? https : http;

        protocol.get(fileUrl, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return downloadToBuffer(res.headers.location).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                return reject(new Error(`HTTP ${res.statusCode} downloading audio`));
            }
            const chunks = [];
            res.on('data',  chunk => chunks.push(chunk));
            res.on('end',   ()    => resolve(Buffer.concat(chunks)));
            res.on('error', reject);
        }).on('error', reject);
    });
}

// ─── Хелпер: определить MIME-тип по имени файла ──────────────────────────────

function mimeFromFilename(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const map = {
        ogg:  'audio/ogg',
        opus: 'audio/ogg',   // .opus часто сохраняется с контейнером ogg
        mp3:  'audio/mpeg',
        mpeg: 'audio/mpeg',
        m4a:  'audio/mp4',
        mp4:  'audio/mp4',
        aac:  'audio/aac',
        wav:  'audio/wav',
        flac: 'audio/flac',
        aiff: 'audio/aiff',
        aif:  'audio/aiff',
    };
    return map[ext] || 'audio/ogg';  // голосовые в мессенджерах почти всегда .ogg
}

// ─── Движок 1: Google Gemini 2.0 Flash ───────────────────────────────────────
//
// Документация: https://ai.google.dev/gemini-api/docs/audio
// Модель: gemini-2.0-flash — поддерживает аудио inline_data до ~20 MB
//
// Чтобы переключиться на другую модель — измените GEMINI_MODEL ниже.
// Доступные модели с поддержкой аудио:
//   gemini-2.0-flash          — быстрый, бесплатный тир, рекомендуется
//   gemini-1.5-pro            — умнее, но дороже и медленнее
//   gemini-1.5-flash          — предыдущее поколение flash

const GEMINI_MODEL = 'gemini-2.0-flash';

function geminiTranscribe(audioBuffer, filename) {
    return new Promise((resolve, reject) => {
        if (!GEMINI_API_KEY) return reject(new Error('GEMINI_API_KEY is not configured'));

        const mimeType   = mimeFromFilename(filename);
        const base64data = audioBuffer.toString('base64');

        const body = JSON.stringify({
            contents: [{
                parts: [
                    {
                        inline_data: {
                            mime_type: mimeType,
                            data:      base64data,
                        },
                    },
                    {
                        // Промпт: только транскрипция, без пояснений
                        text: 'Transcribe this voice message. Output only the transcription text, no labels, no explanations, no punctuation fixes — just the spoken words as-is.',
                    },
                ],
            }],
            generationConfig: {
                temperature: 0,   // детерминированный вывод для транскрипции
            },
        });

        const options = {
            hostname: 'generativelanguage.googleapis.com',
            path:     `/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
            method:   'POST',
            headers: {
                'Content-Type':   'application/json',
                'Content-Length': Buffer.byteLength(body),
            },
        };

        const req = https.request(options, (res) => {
            const chunks = [];
            res.on('data',  chunk => chunks.push(chunk));
            res.on('end', () => {
                try {
                    const json = JSON.parse(Buffer.concat(chunks).toString());
                    if (json.error) {
                        return reject(new Error(`Gemini API error: ${json.error.message}`));
                    }
                    const text = json?.candidates?.[0]?.content?.parts?.[0]?.text;
                    if (!text) return reject(new Error('Gemini returned empty transcript'));
                    resolve(text.trim());
                } catch (e) {
                    reject(new Error('Invalid JSON from Gemini'));
                }
            });
            res.on('error', reject);
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ─── Движок 2: OpenAI Whisper-1 (fallback) ───────────────────────────────────
//
// Документация: https://platform.openai.com/docs/guides/speech-to-text
// Используется когда: GEMINI_API_KEY не задан, Gemini упал, или файл > 15 MB.
//
// Чтобы полностью отключить Whisper — закомментируй блок ниже
// и убери OPENAI_API_KEY из .env.

function whisperTranscribe(audioBuffer, filename) {
    return new Promise((resolve, reject) => {
        if (!OPENAI_API_KEY) return reject(new Error('OPENAI_API_KEY is not configured'));

        const boundary = '----WMBoundary' + crypto.randomBytes(12).toString('hex');
        const mimeType = mimeFromFilename(filename);

        const partFile  = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: ${mimeType}\r\n\r\n`),
            audioBuffer,
            Buffer.from('\r\n'),
        ]);
        const partModel = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="model"\r\n\r\nwhisper-1\r\n`);
        // language: не задаём явно — Whisper определит сам (лучше для смешанного контента)
        const partEnd   = Buffer.from(`--${boundary}--\r\n`);
        const body      = Buffer.concat([partFile, partModel, partEnd]);

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
                    if (json.error) return reject(new Error(json.error.message));
                    resolve(json.text || '');
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

// ─── Обёртка с таймаутом ─────────────────────────────────────────────────────

function withTimeout(promise, ms, label) {
    const timeout = new Promise((_, reject) =>
        setTimeout(() => reject(new Error(`${label} timeout after ${ms / 1000}s`)), ms)
    );
    return Promise.race([promise, timeout]);
}

// ─── POST /api/node/voice/transcribe ─────────────────────────────────────────

function transcribeVoice(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            // PRO check
            const user = await ctx.wo_users.findOne({
                where:      { user_id: req.userId },
                attributes: ['is_pro', 'pro_time'],
                raw:        true,
            });
            const nowSec = Math.floor(Date.now() / 1000);
            const isPro  = user && user.is_pro === '1' && parseInt(user.pro_time, 10) > nowSec;
            if (!isPro) {
                return res.status(403).json({
                    api_status:    403,
                    error_message: 'Voice transcription is a PRO feature',
                });
            }

            const { url: audioUrl } = req.body;
            if (!audioUrl || !/^https?:\/\/.+/.test(audioUrl)) {
                return res.status(400).json({ api_status: 400, error_message: 'url must be an absolute http(s) URL' });
            }

            // Скачать аудио
            let audioBuffer;
            try {
                audioBuffer = await downloadToBuffer(audioUrl);
            } catch (e) {
                return res.status(422).json({ api_status: 422, error_message: `Could not download audio: ${e.message}` });
            }

            if (audioBuffer.length > WHISPER_MAX_BYTES) {
                return res.status(413).json({ api_status: 413, error_message: 'Audio file exceeds 25 MB limit' });
            }

            const filename = audioUrl.split('/').pop().split('?')[0] || 'voice.ogg';

            // ── Логика выбора движка ──────────────────────────────────────────
            //
            //   GEMINI_API_KEY задан  И  файл ≤ 15 MB → пробуем Gemini
            //     ↓ успех                              → возвращаем transcript, engine: "gemini"
            //     ↓ ошибка                             → пробуем Whisper (если OPENAI_API_KEY есть)
            //
            //   GEMINI_API_KEY не задан  ИЛИ  файл > 15 MB  → сразу Whisper
            //     ↓ OPENAI_API_KEY задан                      → возвращаем transcript, engine: "whisper"
            //     ↓ OPENAI_API_KEY тоже не задан              → 503 с инструкцией

            const useGemini = !!GEMINI_API_KEY && audioBuffer.length <= GEMINI_MAX_BYTES;

            let transcript = null;
            let engine     = null;
            let lastError  = null;

            if (useGemini) {
                try {
                    transcript = await withTimeout(
                        geminiTranscribe(audioBuffer, filename),
                        GEMINI_TIMEOUT_MS,
                        'Gemini'
                    );
                    engine = 'gemini';
                    console.log(`[Voice/transcribe] Gemini OK — ${transcript.length} chars`);
                } catch (e) {
                    lastError = e.message;
                    console.warn(`[Voice/transcribe] Gemini failed (${e.message}), falling back to Whisper`);
                }
            }

            // Fallback: Whisper
            if (transcript === null) {
                if (!OPENAI_API_KEY) {
                    const hint = GEMINI_API_KEY
                        ? `Gemini failed: ${lastError}. No OPENAI_API_KEY fallback configured.`
                        : 'Neither GEMINI_API_KEY nor OPENAI_API_KEY is configured in .env';
                    return res.status(503).json({ api_status: 503, error_message: hint });
                }
                try {
                    const result = await withTimeout(
                        whisperTranscribe(audioBuffer, filename),
                        WHISPER_TIMEOUT_MS,
                        'Whisper'
                    );
                    transcript = result;
                    engine     = 'whisper';
                    console.log(`[Voice/transcribe] Whisper OK — ${transcript.length} chars`);
                } catch (e) {
                    console.error(`[Voice/transcribe] Whisper also failed: ${e.message}`);
                    return res.status(502).json({ api_status: 502, error_message: `Transcription failed: ${e.message}` });
                }
            }

            return res.json({
                api_status: 200,
                transcript,
                engine,        // "gemini" или "whisper" — удобно для отладки на клиенте
            });

        } catch (err) {
            console.error('[Voice/transcribe] Unexpected error:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerVoiceTranscriptionRoutes(app, ctx) {
    app.post('/api/node/voice/transcribe', transcribeVoice(ctx));

    // Логируем какие движки активны при старте
    const engines = [];
    if (GEMINI_API_KEY)  engines.push('Gemini 2.0 Flash (primary)');
    if (OPENAI_API_KEY)  engines.push('Whisper-1 (fallback)');
    const status = engines.length ? engines.join(' → ') : '⚠ NO ENGINE CONFIGURED';
    console.log(`[VoiceTranscription] POST /api/node/voice/transcribe — ${status}`);
}

module.exports = { registerVoiceTranscriptionRoutes };
