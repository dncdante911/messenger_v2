'use strict';

/**
 * Multi-avatar API — similar to Telegram's profile photo gallery
 *
 * Endpoints:
 *   GET  /api/node/user/avatars/:userId     – get avatar list for any user
 *   POST /api/node/user/avatars/upload      – upload a new avatar (multipart)
 *   POST /api/node/user/avatars/:id/set-main – set avatar as profile photo
 *   POST /api/node/user/avatars/reorder     – reorder avatars (body: {ids: [...]})
 *   DELETE /api/node/user/avatars/:id       – delete one avatar
 *
 * Limits:
 *   Regular users  → MAX_AVATARS_FREE  = 10
 *   Premium (is_pro=1) → MAX_AVATARS_PRO = 25
 *
 * Animated avatars (GIF / APNG / WebP / MP4-loop):
 *   Detected by mime_type starting with "image/gif", "image/apng",
 *   "image/webp" (animated), or "video/".
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');

const MAX_AVATARS_FREE = 10;
const MAX_AVATARS_PRO  = 25;

const ALLOWED_MIME = [
    'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/apng',
    'video/mp4', 'video/quicktime',
];
const ALLOWED_EXT = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.apng', '.mp4', '.mov'];
const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

// ─── helpers ──────────────────────────────────────────────────────────────────

const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');

function avatarUploadDir() {
    const now = new Date();
    const yy  = now.getFullYear();
    const mm  = String(now.getMonth() + 1).padStart(2, '0');
    return `upload/photos/${yy}/${mm}`;
}

function generateFilename(origName) {
    const ext    = path.extname(origName).toLowerCase() || '.jpg';
    const day    = String(new Date().getDate()).padStart(2, '0');
    const rand   = crypto.randomBytes(8).toString('hex');
    const hash   = crypto.createHash('md5').update(String(Date.now())).digest('hex');
    return `avatar_${day}_${rand}_${hash}${ext}`;
}

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true, mode: 0o777 });
}

function isAnimated(mimeType) {
    return mimeType === 'image/gif'
        || mimeType === 'image/apng'
        || mimeType === 'image/webp'
        || mimeType.startsWith('video/');
}

function fullUrl(ctx, filePath) {
    if (!filePath) return '';
    if (filePath.startsWith('http')) return filePath;
    const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
    return `${base}/${filePath}`;
}

// multer — memory storage, we write to disk ourselves
const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: MAX_FILE_SIZE },
    fileFilter: (_req, file, cb) => {
        const ext = path.extname(file.originalname).toLowerCase();
        if (ALLOWED_EXT.includes(ext) || ALLOWED_MIME.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error(`Unsupported file type: ${ext}`));
        }
    },
});

// ─── GET  /api/node/user/avatars/:userId ──────────────────────────────────────

async function getAvatars(ctx, req, res) {
    try {
        const targetId = parseInt(req.params.userId) || req.userId;

        const rows = await ctx.wo_user_avatars.findAll({
            where:  { user_id: targetId },
            order:  [['position', 'ASC'], ['id', 'ASC']],
            raw:    true,
        });

        const avatars = rows.map(r => ({
            id:          r.id,
            url:         fullUrl(ctx, r.file_path),
            file_path:   r.file_path,
            is_animated: !!r.is_animated,
            mime_type:   r.mime_type,
            position:    r.position,
            created_at:  r.created_at,
        }));

        res.json({ api_status: 200, avatars });
    } catch (err) {
        console.error('[Avatars/get]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/user/avatars/upload ───────────────────────────────────────

async function uploadAvatar(ctx, req, res) {
    try {
        const userId = req.userId;

        // Count existing avatars + check premium
        const user = await ctx.wo_users.findOne({
            attributes: ['user_id', 'is_pro', 'avatar'],
            where:      { user_id: userId },
            raw:        true,
        });
        if (!user) return res.status(404).json({ api_status: 404, error_message: 'User not found' });

        const maxAvatars = user.is_pro ? MAX_AVATARS_PRO : MAX_AVATARS_FREE;
        const count = await ctx.wo_user_avatars.count({ where: { user_id: userId } });
        if (count >= maxAvatars) {
            return res.status(403).json({
                api_status:    403,
                error_message: `Avatar limit reached (${maxAvatars}). ${user.is_pro ? '' : 'Upgrade to Premium for up to 25 avatars.'}`,
                limit:         maxAvatars,
            });
        }

        if (!req.file) {
            return res.status(400).json({ api_status: 400, error_message: 'No file uploaded' });
        }

        const mimeType    = req.file.mimetype;
        const relDir      = avatarUploadDir();
        const absDir      = path.join(SITE_ROOT, relDir);
        ensureDir(absDir);

        const filename    = generateFilename(req.file.originalname);
        const relPath     = `${relDir}/${filename}`;
        const absPath     = path.join(absDir, filename);

        await fs.promises.writeFile(absPath, req.file.buffer);

        const setAsMain = req.body.set_as_main !== 'false'; // default: true for first upload

        // All existing avatars — shift positions up by 1 if new one goes to front
        if (setAsMain || count === 0) {
            await ctx.wo_user_avatars.increment('position', {
                where: { user_id: userId },
            });
        }

        const newPos = (setAsMain || count === 0) ? 0 : count;

        const record = await ctx.wo_user_avatars.create({
            user_id:     userId,
            file_path:   relPath,
            is_animated: isAnimated(mimeType) ? 1 : 0,
            mime_type:   mimeType,
            position:    newPos,
            created_at:  Math.floor(Date.now() / 1000),
        });

        // If main avatar → update wo_users.avatar + last_avatar_mod
        if (setAsMain || count === 0) {
            await ctx.wo_users.update(
                { avatar: relPath, last_avatar_mod: Math.floor(Date.now() / 1000) },
                { where: { user_id: userId } }
            );
        }

        res.json({
            api_status:  200,
            avatar: {
                id:          record.id,
                url:         fullUrl(ctx, relPath),
                file_path:   relPath,
                is_animated: isAnimated(mimeType),
                mime_type:   mimeType,
                position:    newPos,
            },
            is_main: setAsMain || count === 0,
            count:   count + 1,
            limit:   maxAvatars,
        });
    } catch (err) {
        console.error('[Avatars/upload]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/user/avatars/:id/set-main ─────────────────────────────────

async function setMainAvatar(ctx, req, res) {
    try {
        const userId   = req.userId;
        const avatarId = parseInt(req.params.id);

        const avatar = await ctx.wo_user_avatars.findOne({
            where: { id: avatarId, user_id: userId },
            raw:   true,
        });
        if (!avatar) return res.status(404).json({ api_status: 404, error_message: 'Avatar not found' });

        // Reorder: set selected to position 0, shift others
        await ctx.wo_user_avatars.increment('position', { where: { user_id: userId } });
        await ctx.wo_user_avatars.update({ position: 0 }, { where: { id: avatarId } });

        // Update user profile photo
        await ctx.wo_users.update(
            { avatar: avatar.file_path, last_avatar_mod: Math.floor(Date.now() / 1000) },
            { where: { user_id: userId } }
        );

        res.json({ api_status: 200, url: fullUrl(ctx, avatar.file_path) });
    } catch (err) {
        console.error('[Avatars/set-main]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/user/avatars/reorder ──────────────────────────────────────
// body: { ids: [5, 3, 1, 7, ...] }  — ordered array of avatar IDs

async function reorderAvatars(ctx, req, res) {
    try {
        const userId = req.userId;
        const ids    = Array.isArray(req.body.ids) ? req.body.ids.map(Number) : [];
        if (!ids.length) return res.status(400).json({ api_status: 400, error_message: 'ids array required' });

        for (let i = 0; i < ids.length; i++) {
            await ctx.wo_user_avatars.update(
                { position: i },
                { where: { id: ids[i], user_id: userId } }
            );
        }

        // Update main avatar (position=0) in wo_users
        const mainAvatar = await ctx.wo_user_avatars.findOne({
            where: { id: ids[0], user_id: userId },
            raw:   true,
        });
        if (mainAvatar) {
            await ctx.wo_users.update(
                { avatar: mainAvatar.file_path, last_avatar_mod: Math.floor(Date.now() / 1000) },
                { where: { user_id: userId } }
            );
        }

        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Avatars/reorder]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── DELETE /api/node/user/avatars/:id ────────────────────────────────────────

async function deleteAvatar(ctx, req, res) {
    try {
        const userId   = req.userId;
        const avatarId = parseInt(req.params.id);

        const avatar = await ctx.wo_user_avatars.findOne({
            where: { id: avatarId, user_id: userId },
            raw:   true,
        });
        if (!avatar) return res.status(404).json({ api_status: 404, error_message: 'Avatar not found' });

        const wasMain = avatar.position === 0;

        // Delete from DB
        await ctx.wo_user_avatars.destroy({ where: { id: avatarId } });

        // Delete file from disk
        const absPath = path.join(SITE_ROOT, avatar.file_path);
        if (fs.existsSync(absPath)) fs.unlinkSync(absPath);

        // Normalize positions after deletion
        const remaining = await ctx.wo_user_avatars.findAll({
            where: { user_id: userId },
            order: [['position', 'ASC'], ['id', 'ASC']],
            raw:   true,
        });
        for (let i = 0; i < remaining.length; i++) {
            await ctx.wo_user_avatars.update({ position: i }, { where: { id: remaining[i].id } });
        }

        // If deleted was main, promote new position-0 (if any)
        if (wasMain && remaining.length > 0) {
            const newMain = remaining[0];
            await ctx.wo_users.update(
                { avatar: newMain.file_path, last_avatar_mod: Math.floor(Date.now() / 1000) },
                { where: { user_id: userId } }
            );
        } else if (wasMain && remaining.length === 0) {
            // No avatars left — reset to default
            await ctx.wo_users.update(
                { avatar: 'upload/photos/d-avatar.jpg', last_avatar_mod: Math.floor(Date.now() / 1000) },
                { where: { user_id: userId } }
            );
        }

        res.json({ api_status: 200, remaining_count: remaining.length });
    } catch (err) {
        console.error('[Avatars/delete]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerAvatarRoutes(app, ctx) {
    const auth = async (req, res, next) => {
        const token = req.headers['access-token']
                   || req.query.access_token
                   || req.body?.access_token;
        if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
        try {
            const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
            if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid token' });
            req.userId = session.user_id;
            next();
        } catch (e) {
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    };

    const uploadMiddleware = upload.single('avatar');

    app.get('/api/node/user/avatars/:userId', auth,
        (req, res) => getAvatars(ctx, req, res));

    app.post('/api/node/user/avatars/upload', auth, uploadMiddleware,
        (req, res) => uploadAvatar(ctx, req, res));

    app.post('/api/node/user/avatars/reorder', auth,
        (req, res) => reorderAvatars(ctx, req, res));

    app.post('/api/node/user/avatars/:id/set-main', auth,
        (req, res) => setMainAvatar(ctx, req, res));

    app.delete('/api/node/user/avatars/:id', auth,
        (req, res) => deleteAvatar(ctx, req, res));

    console.log('[Avatar API] Registered: GET|POST /api/node/user/avatars/*');
}

module.exports = { registerAvatarRoutes };
