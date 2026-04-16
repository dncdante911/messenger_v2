'use strict';

/**
 * Crash Report Route
 * POST /api/node/crash-report
 *
 * Приймає звіти про краші Android-застосунку та зберігає їх у директорії
 * crash_logs/ поруч із main.js. Файли у форматі:
 *   crash_{userId}_{YYYY-MM-DD_HH-mm-ss}.log
 *
 * Захист: простий shared secret (передається у полі "secret").
 */

const path = require('path');
const fs   = require('fs');

const CRASH_SECRET = 'wm_crash_rpt_2025';
const CRASH_DIR    = path.join(__dirname, '..', 'crash_logs');

function registerCrashReportRoutes(app) {

    app.post('/api/node/crash-report', (req, res) => {
        const { report, filename, secret } = req.body || {};

        // ── Перевірка секрету ───────────────────────────────────────────────
        if (secret !== CRASH_SECRET) {
            console.warn('[CrashReport] Rejected: wrong secret');
            return res.status(403).json({ api_status: 403, error_message: 'Forbidden' });
        }

        // ── Валідація ───────────────────────────────────────────────────────
        if (!report || typeof report !== 'string') {
            return res.status(400).json({ api_status: 400, error_message: 'report is required' });
        }
        if (!filename || typeof filename !== 'string') {
            return res.status(400).json({ api_status: 400, error_message: 'filename is required' });
        }

        // ── Sanitize filename — забороняємо path traversal ──────────────────
        const safeFilename = path.basename(filename)
            .replace(/[^a-zA-Z0-9_\-\.]/g, '_')
            .substring(0, 120);   // обмеження довжини

        if (!safeFilename.endsWith('.log')) {
            return res.status(400).json({ api_status: 400, error_message: 'Invalid filename' });
        }

        try {
            // ── Створюємо директорію якщо не існує ─────────────────────────
            if (!fs.existsSync(CRASH_DIR)) {
                fs.mkdirSync(CRASH_DIR, { recursive: true });
            }

            const filepath = path.join(CRASH_DIR, safeFilename);

            // Якщо файл вже є — не перезаписуємо (дублікат)
            if (fs.existsSync(filepath)) {
                console.log(`[CrashReport] Duplicate ignored: ${safeFilename}`);
                return res.json({ api_status: 200, message: 'Already received' });
            }

            fs.writeFileSync(filepath, report, 'utf8');
            console.log(`[CrashReport] Saved: ${safeFilename} (${report.length} chars)`);

            return res.json({ api_status: 200, message: 'Report received' });

        } catch (err) {
            console.error('[CrashReport] Failed to save:', err.message);
            return res.status(500).json({ api_status: 500, error_message: 'Failed to save report' });
        }
    });

    // ── Опціональний GET для перегляду списку звітів ─────────────────────────
    // (тільки для локальної/адмін перевірки — без авторизації тут не вистачає)
    app.get('/api/node/crash-report/list', (req, res) => {
        const { secret } = req.query;
        if (secret !== CRASH_SECRET) {
            return res.status(403).json({ api_status: 403, error_message: 'Forbidden' });
        }
        try {
            if (!fs.existsSync(CRASH_DIR)) {
                return res.json({ api_status: 200, reports: [] });
            }
            const files = fs.readdirSync(CRASH_DIR)
                .filter(f => f.endsWith('.log'))
                .map(f => {
                    const stat = fs.statSync(path.join(CRASH_DIR, f));
                    return {
                        filename: f,
                        size:     stat.size,
                        created:  stat.birthtime,
                    };
                })
                .sort((a, b) => new Date(b.created) - new Date(a.created));

            return res.json({ api_status: 200, count: files.length, reports: files });
        } catch (err) {
            return res.status(500).json({ api_status: 500, error_message: err.message });
        }
    });

    // ── Перегляд вмісту конкретного звіту ────────────────────────────────────
    app.get('/api/node/crash-report/view/:filename', (req, res) => {
        const { secret } = req.query;
        if (secret !== CRASH_SECRET) {
            return res.status(403).json({ api_status: 403, error_message: 'Forbidden' });
        }
        const safeFilename = path.basename(req.params.filename);
        const filepath = path.join(CRASH_DIR, safeFilename);
        if (!fs.existsSync(filepath)) {
            return res.status(404).json({ api_status: 404, error_message: 'Not found' });
        }
        res.setHeader('Content-Type', 'text/plain; charset=utf-8');
        res.sendFile(filepath);
    });
}

module.exports = { registerCrashReportRoutes };
