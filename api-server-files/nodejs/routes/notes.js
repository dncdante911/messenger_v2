'use strict';

/**
 * Notes API — Telegram-style Saved Messages
 *
 * Endpoints:
 *   GET    /api/node/notes            — list notes (paginated, newest first)
 *   POST   /api/node/notes/create     — create text note
 *   POST   /api/node/notes/upload     — upload file/image/video/audio note (multipart)
 *   GET    /api/node/notes/file/:id   — stream/download file for a note
 *   DELETE /api/node/notes/:id        — delete note
 *   GET    /api/node/notes/storage    — get used / quota bytes
 *
 * Storage limits:
 *   - Per file: 512 MB
 *   - Free tier total: 5 GB
 *   - Premium (PRO) tier total: 25 GB
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');

// ── Upload directory ──────────────────────────────────────────────────────────
const UPLOAD_DIR = path.join(__dirname, '..', 'uploads', 'notes');
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// ── Limits ────────────────────────────────────────────────────────────────────
const MAX_FILE_SIZE    = 512  * 1024 * 1024;        // 512 MB per file
const FREE_QUOTA_BYTES = 5    * 1024 * 1024 * 1024; // 5 GB
const PRO_QUOTA_BYTES  = 25   * 1024 * 1024 * 1024; // 25 GB

// ── Multer storage ────────────────────────────────────────────────────────────
const storage = multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
    filename: (_req, file, cb) => {
        const ext  = path.extname(file.originalname) || '';
        const name = `note_${Date.now()}_${crypto.randomBytes(6).toString('hex')}${ext}`;
        cb(null, name);
    },
});

const upload = multer({
    storage,
    limits: { fileSize: MAX_FILE_SIZE },
});

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

// ── Storage helpers ───────────────────────────────────────────────────────────
async function getUsedBytes(ctx, userId) {
    const row = await ctx.wm_user_storage.findOne({ where: { user_id: userId }, raw: true });
    return row ? parseInt(row.used_bytes) || 0 : 0;
}

async function addUsedBytes(ctx, userId, delta) {
    await ctx.wm_user_storage.findOrCreate({
        where:    { user_id: userId },
        defaults: { user_id: userId, used_bytes: 0, updated_at: new Date() },
    });
    // Use SQL arithmetic to avoid race conditions
    await ctx.sequelize.query(
        'UPDATE wm_user_storage SET used_bytes = GREATEST(0, used_bytes + :delta), updated_at = NOW() WHERE user_id = :userId',
        { replacements: { delta, userId } }
    );
}

async function getUserQuota(ctx, userId) {
    const now  = Math.floor(Date.now() / 1000);
    const user = await ctx.wo_users.unscoped().findOne({
        attributes: ['user_id', 'is_pro', 'pro_time'],
        where: { user_id: userId },
        raw: true,
    });
    const isPro = user && parseInt(user.is_pro) === 1 && user.pro_time > now;
    return isPro ? PRO_QUOTA_BYTES : FREE_QUOTA_BYTES;
}

// ── Route registration ────────────────────────────────────────────────────────
module.exports = function registerNotesRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    /**
     * GET /api/node/notes
     * Query: page (default 1), limit (default 30, max 100)
     */
    app.get('/api/node/notes', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const limit  = Math.min(100, parseInt(req.query.limit) || 30);
            const page   = Math.max(1,   parseInt(req.query.page)  || 1);
            const offset = (page - 1) * limit;

            const { count, rows } = await ctx.wm_notes.findAndCountAll({
                where:  { user_id: userId },
                order:  [['created_at', 'DESC']],
                limit,
                offset,
                raw:    true,
            });

            return res.json({ api_status: 200, total: count, page, limit, notes: rows });
        } catch (e) {
            console.error('[Notes] list error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    /**
     * POST /api/node/notes/create
     * Body: { text }
     */
    app.post('/api/node/notes/create', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const text   = (req.body.text || '').toString().trim();
            if (!text) return res.status(400).json({ api_status: 400, error_message: 'text is required' });

            const note = await ctx.wm_notes.create({
                user_id:    userId,
                type:       'text',
                text,
                created_at: new Date(),
            });

            return res.json({ api_status: 200, note });
        } catch (e) {
            console.error('[Notes] create error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    /**
     * POST /api/node/notes/upload
     * Multipart fields: file (required), text (optional caption)
     */
    app.post('/api/node/notes/upload', auth, upload.single('file'), async (req, res) => {
        try {
            const userId = req.userId;
            if (!req.file) return res.status(400).json({ api_status: 400, error_message: 'No file uploaded' });

            // Check quota before saving metadata
            const [used, quota] = await Promise.all([
                getUsedBytes(ctx, userId),
                getUserQuota(ctx, userId),
            ]);

            if (used + req.file.size > quota) {
                fs.unlink(req.file.path, () => {});
                const quotaGB = (quota / (1024 ** 3)).toFixed(0);
                return res.status(413).json({
                    api_status:    413,
                    error_message: `Storage quota exceeded (${quotaGB} GB limit). Upgrade to PRO for 25 GB.`,
                    used_bytes:    used,
                    quota_bytes:   quota,
                });
            }

            // Determine note type from MIME
            const mime = req.file.mimetype || '';
            let type = 'file';
            if (mime.startsWith('image/')) type = 'image';
            else if (mime.startsWith('video/')) type = 'video';
            else if (mime.startsWith('audio/')) type = 'audio';

            const note = await ctx.wm_notes.create({
                user_id:    userId,
                type,
                text:       (req.body.text || '').toString().trim() || null,
                file_name:  req.file.originalname,
                file_path:  req.file.path,
                file_size:  req.file.size,
                mime_type:  req.file.mimetype,
                created_at: new Date(),
            });

            await addUsedBytes(ctx, userId, req.file.size);

            return res.json({
                api_status:  200,
                note,
                used_bytes:  used + req.file.size,
                quota_bytes: quota,
            });
        } catch (e) {
            if (req.file) fs.unlink(req.file.path, () => {});
            console.error('[Notes] upload error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    /**
     * GET /api/node/notes/file/:id
     * Range-request streaming for large files (video/audio seek support)
     */
    app.get('/api/node/notes/file/:id', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const note   = await ctx.wm_notes.findOne({
                where: { id: req.params.id, user_id: userId },
                raw:   true,
            });
            if (!note)           return res.status(404).json({ api_status: 404, error_message: 'Note not found' });
            if (!note.file_path) return res.status(400).json({ api_status: 400, error_message: 'Note has no file' });
            if (!fs.existsSync(note.file_path))
                return res.status(404).json({ api_status: 404, error_message: 'File missing on disk' });

            const stat  = fs.statSync(note.file_path);
            const range = req.headers.range;

            res.setHeader('Content-Type', note.mime_type || 'application/octet-stream');
            res.setHeader('Content-Disposition', `inline; filename="${note.file_name || 'file'}"`);
            res.setHeader('Accept-Ranges', 'bytes');

            if (range) {
                const [startStr, endStr] = range.replace(/bytes=/, '').split('-');
                const start = parseInt(startStr, 10);
                const end   = endStr ? parseInt(endStr, 10) : stat.size - 1;
                const chunk = end - start + 1;
                res.writeHead(206, {
                    'Content-Range':  `bytes ${start}-${end}/${stat.size}`,
                    'Content-Length': chunk,
                });
                fs.createReadStream(note.file_path, { start, end }).pipe(res);
            } else {
                res.setHeader('Content-Length', stat.size);
                fs.createReadStream(note.file_path).pipe(res);
            }
        } catch (e) {
            console.error('[Notes] file error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    /**
     * DELETE /api/node/notes/:id
     * Deletes note and its file (if any). Updates storage counter.
     */
    app.delete('/api/node/notes/:id', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const note   = await ctx.wm_notes.findOne({
                where: { id: req.params.id, user_id: userId },
                raw:   true,
            });
            if (!note) return res.status(404).json({ api_status: 404, error_message: 'Note not found' });

            if (note.file_path && fs.existsSync(note.file_path)) {
                fs.unlinkSync(note.file_path);
                await addUsedBytes(ctx, userId, -(parseInt(note.file_size) || 0));
            }

            await ctx.wm_notes.destroy({ where: { id: note.id } });

            return res.json({ api_status: 200 });
        } catch (e) {
            console.error('[Notes] delete error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    /**
     * GET /api/node/notes/storage
     * Returns used_bytes and quota_bytes for the authenticated user.
     */
    app.get('/api/node/notes/storage', auth, async (req, res) => {
        try {
            const userId = req.userId;
            const [used, quota] = await Promise.all([
                getUsedBytes(ctx, userId),
                getUserQuota(ctx, userId),
            ]);
            return res.json({
                api_status:  200,
                used_bytes:  used,
                quota_bytes: quota,
                used_gb:     +(used  / (1024 ** 3)).toFixed(3),
                quota_gb:    +(quota / (1024 ** 3)).toFixed(1),
            });
        } catch (e) {
            console.error('[Notes] storage error:', e.message);
            return res.status(500).json({ api_status: 500, error_message: e.message });
        }
    });

    console.log('[Notes API] Endpoints registered on /api/node/notes/*');
};
