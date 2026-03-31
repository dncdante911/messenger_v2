'use strict';

/**
 * Call & Stream Recordings API
 *
 * The Android client records locally using MediaRecorder (WebRTC track capture)
 * and uploads the resulting blob here.
 *
 * Endpoints:
 *   POST   /api/node/recordings/upload       — upload recording blob (multipart/form-data)
 *   GET    /api/node/recordings/:room_name   — list recordings for a room
 *   GET    /api/node/recordings/file/:id     — download / stream recording file
 *   DELETE /api/node/recordings/:id          — delete recording (uploader or admin only)
 */

const path    = require('path');
const fs      = require('fs');
const crypto  = require('crypto');
const multer  = require('multer');

// ── Upload directory ─────────────────────────────────────────────────────────
const UPLOAD_DIR = path.join(__dirname, '..', 'uploads', 'recordings');
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// ── Multer storage ───────────────────────────────────────────────────────────
const storage = multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
    filename: (_req, file, cb) => {
        const ext = path.extname(file.originalname) || '.webm';
        const name = `rec_${Date.now()}_${crypto.randomBytes(6).toString('hex')}${ext}`;
        cb(null, name);
    },
});

const ALLOWED_MIME = new Set([
    'video/webm', 'video/mp4', 'video/ogg',
    'audio/webm', 'audio/ogg', 'audio/mp4',
]);

const upload = multer({
    storage,
    limits: { fileSize: 2 * 1024 * 1024 * 1024 }, // 2 GB max
    fileFilter: (_req, file, cb) => {
        if (ALLOWED_MIME.has(file.mimetype)) return cb(null, true);
        cb(new Error(`Unsupported mime type: ${file.mimetype}`));
    },
});

// ── Auth helper ──────────────────────────────────────────────────────────────
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

// ── Auto-cleanup: delete recordings older than 1 year ────────────────────────
async function purgeOldRecordings(ctx) {
    try {
        const ONE_YEAR_AGO = new Date(Date.now() - 365 * 24 * 60 * 60 * 1000);
        const old = await ctx.wm_call_recordings.findAll({
            where: { created_at: { [require('sequelize').Op.lt]: ONE_YEAR_AGO } },
            attributes: ['id', 'file_path'],
            raw: true,
        });
        if (!old.length) return;
        for (const rec of old) {
            try { if (fs.existsSync(rec.file_path)) fs.unlinkSync(rec.file_path); } catch (_) {}
        }
        const ids = old.map(r => r.id);
        await ctx.wm_call_recordings.destroy({ where: { id: ids } });
        console.log(`[Recordings] Purged ${ids.length} recordings older than 1 year`);
    } catch (e) {
        console.error('[Recordings] purge error:', e.message);
    }
}

// ── Routes ───────────────────────────────────────────────────────────────────
module.exports = function registerRecordingRoutes(app, ctx) {
    // Run cleanup on startup and then once a day
    purgeOldRecordings(ctx);
    setInterval(() => purgeOldRecordings(ctx), 24 * 60 * 60 * 1000);

    /**
     * POST /api/node/recordings/upload
     * Multipart fields:
     *   - file        : recording blob (required)
     *   - room_name   : call/stream room (required)
     *   - type        : 'group_call' | 'channel_stream' | 'private_call' (required)
     *   - duration    : seconds (optional)
     *   - channel_id  : for streams (optional)
     *   - group_id    : for group calls (optional)
     */
    app.post('/api/node/recordings/upload',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        upload.single('file'),
        async (req, res) => {
            try {
                if (!req.file) return res.status(400).json({ api_status: 400, error_message: 'No file uploaded' });

                const { room_name, type, duration, channel_id, group_id } = req.body;
                if (!room_name) return res.status(400).json({ api_status: 400, error_message: 'room_name required' });

                const recType = ['group_call', 'channel_stream', 'private_call'].includes(type)
                    ? type : 'group_call';

                const rec = await ctx.wm_call_recordings.create({
                    room_name,
                    type:        recType,
                    uploader_id: req.userId,
                    channel_id:  channel_id ? parseInt(channel_id) : null,
                    group_id:    group_id   ? parseInt(group_id)   : null,
                    filename:    req.file.filename,
                    file_path:   req.file.path,
                    file_size:   req.file.size,
                    duration:    duration ? parseInt(duration) : 0,
                    mime_type:   req.file.mimetype,
                    status:      'ready',
                    created_at:  new Date(),
                });

                console.log(`[Recording] Saved: id=${rec.id} room=${room_name} type=${recType} size=${req.file.size}`);

                return res.json({
                    api_status:   200,
                    recording_id: rec.id,
                    room_name,
                    filename:     rec.filename,
                    duration:     rec.duration,
                    file_size:    rec.file_size,
                });
            } catch (e) {
                // Clean up partial upload
                if (req.file) fs.unlink(req.file.path, () => {});
                console.error('[Recording] upload error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    /**
     * GET /api/node/recordings/channel/:channel_id
     * List all recordings for a channel (for the archive tab).
     * Returns recordings ordered newest-first, up to 100 items.
     */
    app.get('/api/node/recordings/channel/:channel_id',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            try {
                const channelId = parseInt(req.params.channel_id);
                if (!channelId) return res.status(400).json({ api_status: 400, error_message: 'Invalid channel_id' });

                const recordings = await ctx.wm_call_recordings.findAll({
                    where: { channel_id: channelId, status: 'ready' },
                    attributes: ['id', 'room_name', 'type', 'uploader_id', 'filename',
                                 'file_size', 'duration', 'mime_type', 'created_at'],
                    order: [['created_at', 'DESC']],
                    limit: 100,
                    raw: true,
                });
                return res.json({ api_status: 200, recordings });
            } catch (e) {
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    /**
     * GET /api/node/recordings/:room_name
     * List all recordings for a given room.
     */
    app.get('/api/node/recordings/:room_name',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            try {
                const recordings = await ctx.wm_call_recordings.findAll({
                    where: { room_name: req.params.room_name, status: 'ready' },
                    attributes: ['id', 'room_name', 'type', 'uploader_id', 'filename',
                                 'file_size', 'duration', 'mime_type', 'created_at'],
                    order: [['created_at', 'DESC']],
                    raw: true,
                });
                return res.json({ api_status: 200, recordings });
            } catch (e) {
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    /**
     * GET /api/node/recordings/file/:id
     * Download or stream a recording file.
     */
    app.get('/api/node/recordings/file/:id',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            try {
                const rec = await ctx.wm_call_recordings.findOne({
                    where: { id: req.params.id, status: 'ready' },
                    raw: true,
                });
                if (!rec) return res.status(404).json({ api_status: 404, error_message: 'Recording not found' });
                if (!fs.existsSync(rec.file_path))
                    return res.status(404).json({ api_status: 404, error_message: 'File missing on disk' });

                const stat = fs.statSync(rec.file_path);
                const range = req.headers.range;

                res.setHeader('Content-Type', rec.mime_type || 'video/webm');
                res.setHeader('Content-Disposition', `inline; filename="${rec.filename}"`);
                res.setHeader('Accept-Ranges', 'bytes');

                if (range) {
                    // Partial content for video seek support
                    const [startStr, endStr] = range.replace(/bytes=/, '').split('-');
                    const start = parseInt(startStr, 10);
                    const end   = endStr ? parseInt(endStr, 10) : stat.size - 1;
                    const chunk = end - start + 1;
                    res.writeHead(206, {
                        'Content-Range':  `bytes ${start}-${end}/${stat.size}`,
                        'Content-Length': chunk,
                    });
                    fs.createReadStream(rec.file_path, { start, end }).pipe(res);
                } else {
                    res.setHeader('Content-Length', stat.size);
                    fs.createReadStream(rec.file_path).pipe(res);
                }
            } catch (e) {
                console.error('[Recording] download error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    /**
     * DELETE /api/node/recordings/:id
     * Delete a recording. Only the uploader can delete.
     */
    app.delete('/api/node/recordings/:id',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            try {
                const rec = await ctx.wm_call_recordings.findOne({
                    where: { id: req.params.id },
                    raw: true,
                });
                if (!rec) return res.status(404).json({ api_status: 404, error_message: 'Not found' });
                if (rec.uploader_id !== req.userId)
                    return res.status(403).json({ api_status: 403, error_message: 'Not allowed' });

                // Delete file from disk
                if (fs.existsSync(rec.file_path)) fs.unlinkSync(rec.file_path);

                await ctx.wm_call_recordings.destroy({ where: { id: rec.id } });

                return res.json({ api_status: 200 });
            } catch (e) {
                console.error('[Recording] delete error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    console.log('[Recordings API] Endpoints registered on /api/node/recordings/*');
};
