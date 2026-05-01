'use strict';

/**
 * Chat Media Upload — replaces PHP /xhr/upload_*.php endpoints
 *
 * Single unified endpoint:
 *   POST /api/node/chat/upload
 *
 * Body (multipart/form-data):
 *   file        – the file to upload (any field name: image, video, audio, file)
 *   type        – media type: "image" | "video" | "audio" | "voice" | "file"
 *   quality     – video quality: "video_message" | "compressed" | "high" | "original" | "auto"
 *                 Defaults to "auto". "original" skips server-side compression entirely.
 *   chat_type   – 'private' | 'group' | 'channel'
 *   entity_id   – ID группы или канала (0 для приватного чата)
 *
 * Storage strategy:
 *   multer.diskStorage → write directly to upload/tmp/<random_name>
 *   Magic bytes check  → read 16 bytes from disk (no buffer in RAM)
 *   Content moderation → stream SHA-256 for large files; buffer only for images (≤25 MB)
 *   Final placement    → fs.rename (same volume) or copyFile+unlink (cross-volume)
 *   Temp cleanup       → always removed on error
 *
 * Video compression strategy (Telegram-like):
 *   type=video, quality=video_message → Android already compressed; server skips
 *   type=video, quality=compressed    → Android compressed to 720p; server skips
 *   type=video, quality=high          → Android compressed to 1080p; server skips
 *   type=video, quality=original      → No compression anywhere; stored as-is
 *   type=file   (large video/movie)   → Saved immediately; ffmpeg runs in background in-place.
 *                                       compression_pending=true in response.
 *                                       Socket.IO emits 'media_compressed' when done.
 *
 * File size limits (subscription-aware):
 *   Free  – Images: 25 MB | Videos: 1 GB | Audio: 100 MB | Files: 1 GB
 *   Pro   – Images: 25 MB | Videos: 4 GB | Audio: 100 MB | Files: 4 GB
 *             + 10 large files (>1 GB) per calendar month
 *             Pro-uploaded large files persist on disk even after subscription expires.
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');
const { QueryTypes } = require('sequelize');

const {
    DECISION,
    checkContentFromFile,
    addToModerationQueue
} = require('../../helpers/content-moderator');

const {
    compressInPlace,
    isVideoFile,
} = require('../../helpers/video-compressor');

// ─── Config ────────────────────────────────────────────────────────────────────

const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');

// File size limits per subscription tier
const FREE_LIMITS = {
    image: 25  * 1024 * 1024,          //  25 MB
    video: 1   * 1024 * 1024 * 1024,   //   1 GB
    audio: 100 * 1024 * 1024,          // 100 MB
    voice: 100 * 1024 * 1024,          // 100 MB
    file:  1   * 1024 * 1024 * 1024,   //   1 GB
};

const PRO_LIMITS = {
    image: 25  * 1024 * 1024,          //  25 MB  (same)
    video: 4   * 1024 * 1024 * 1024,   //   4 GB
    audio: 100 * 1024 * 1024,          // 100 MB  (same)
    voice: 100 * 1024 * 1024,          // 100 MB  (same)
    file:  4   * 1024 * 1024 * 1024,   //   4 GB
};

// Alias used for multer's hard cap (the highest possible limit across all tiers)
const LIMITS = PRO_LIMITS;

// Files above this threshold count against the pro monthly quota (10 per month)
const PRO_LARGE_FILE_THRESHOLD = 1 * 1024 * 1024 * 1024; // 1 GB
const PRO_MONTHLY_LARGE_FILE_LIMIT = 10;

const UPLOAD_DIRS = {
    image: 'upload/photos',
    video: 'upload/videos',
    audio: 'upload/audios',
    voice: 'upload/audios',
    file:  'upload/files',
};

// Minimum file size (bytes) to trigger background compression for "file" type video uploads
const MOVIE_COMPRESS_THRESHOLD = 100 * 1024 * 1024; // 100 MB

// ─── Magic Bytes Security ──────────────────────────────────────────────────────

const BLOCKED_SIGNATURES = [
    { sig: Buffer.from([0x7F, 0x45, 0x4C, 0x46]),                   label: 'ELF executable'      },
    { sig: Buffer.from([0x4D, 0x5A]),                                label: 'Windows PE/EXE/DLL'  },
    { sig: Buffer.from([0x3C, 0x3F, 0x70, 0x68, 0x70]),             label: 'PHP script'           },
    { sig: Buffer.from([0x23, 0x21, 0x2F, 0x62, 0x69, 0x6E]),       label: 'shell script (bin)'  },
    { sig: Buffer.from([0x23, 0x21, 0x2F, 0x75, 0x73, 0x72]),       label: 'shell script (usr)'  },
    { sig: Buffer.from([0xCA, 0xFE, 0xBA, 0xBE]),                   label: 'Java class / Mach-O' },
    { sig: Buffer.from([0xCF, 0xFA, 0xED, 0xFE]),                   label: 'Mach-O 64-bit'       },
    { sig: Buffer.from([0x50, 0x4B, 0x03, 0x04]),                   label: 'ZIP / APK archive'   },
    { sig: Buffer.from([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07]),       label: 'RAR archive'         },
];

// Reads the first 16 bytes from disk — no full buffer in memory
function checkMagicBytesFromDisk(filePath) {
    let fd;
    try {
        fd = fs.openSync(filePath, 'r');
        const header = Buffer.alloc(16);
        const bytesRead = fs.readSync(fd, header, 0, 16, 0);
        if (bytesRead < 4) return { ok: false, reason: 'File too small to validate' };

        for (const { sig, label } of BLOCKED_SIGNATURES) {
            if (bytesRead >= sig.length && header.slice(0, sig.length).equals(sig)) {
                return { ok: false, reason: `Rejected file type: ${label}` };
            }
        }
        return { ok: true };
    } catch (e) {
        return { ok: false, reason: `Cannot read file header: ${e.message}` };
    } finally {
        if (fd !== undefined) try { fs.closeSync(fd); } catch {}
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

function ensureDir(dir) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true, mode: 0o777 });
    }
}

function generateFilename(originalName) {
    const ext  = path.extname(originalName).toLowerCase() || '.bin';
    const rand = crypto.randomBytes(10).toString('hex');
    return `${Date.now()}_${rand}${ext}`;
}

function fullUrl(ctx, relPath) {
    const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
    return `${base}/${relPath}`;
}

// Move temp file to final destination.
// Uses rename (atomic, same volume). Falls back to copyFile+unlink if cross-device.
async function moveTempFile(tmpPath, destPath) {
    try {
        await fs.promises.rename(tmpPath, destPath);
    } catch (e) {
        if (e.code === 'EXDEV') {
            // Different filesystems — copy then delete
            await fs.promises.copyFile(tmpPath, destPath);
            await fs.promises.unlink(tmpPath);
        } else {
            throw e;
        }
    }
}

// Always try to delete temp file, swallow errors
function cleanupTemp(tmpPath) {
    if (!tmpPath) return;
    fs.unlink(tmpPath, () => {});
}

// ─── multer — disk storage (no RAM buffer) ────────────────────────────────────

const TMP_DIR = path.join(SITE_ROOT, 'upload/tmp');
ensureDir(TMP_DIR);

const storage = multer.diskStorage({
    destination: (_req, _file, cb) => {
        ensureDir(TMP_DIR);
        cb(null, TMP_DIR);
    },
    filename: (_req, file, cb) => {
        cb(null, generateFilename(file.originalname));
    }
});

const upload = multer({
    storage,
    limits: { fileSize: Math.max(...Object.values(LIMITS)) },
}).any();

// ─── Background compression for large video files (movies) ───────────────────

function scheduleBackgroundCompression(absPath, url, userId, io) {
    const fileSizeBytes = fs.existsSync(absPath) ? fs.statSync(absPath).size : 0;

    console.log(
        `[ChatUpload] Scheduling background compression: ` +
        `${(fileSizeBytes / 1024 / 1024).toFixed(1)} MB → ${absPath}`
    );

    compressInPlace(absPath, { quality: 'auto', fileSize: fileSizeBytes }, (result) => {
        if (result.success && !result.skipped) {
            const origMb = (result.originalSize / 1024 / 1024).toFixed(1);
            const compMb = (result.compressedSize / 1024 / 1024).toFixed(1);
            const pct    = Math.round((1 - result.compressedSize / result.originalSize) * 100);
            console.log(`[ChatUpload] Background compression done: ${origMb}MB → ${compMb}MB (−${pct}%)`);

            if (io && userId) {
                io.to(String(userId)).emit('media_compressed', {
                    url,
                    original_size:     result.originalSize,
                    compressed_size:   result.compressedSize,
                    reduction_percent: pct,
                });
            }
        } else if (result.skipped) {
            console.log(`[ChatUpload] Background compression skipped (already small): ${absPath}`);
        } else {
            console.warn(`[ChatUpload] Background compression failed: ${result.error}`);
        }
    });
}

// ─── Subscription helpers ─────────────────────────────────────────────────────

async function getUserSubscription(ctx, userId) {
    if (!userId) return { isPro: false };
    try {
        const rows = await ctx.sequelize.query(
            'SELECT is_pro, pro_time FROM wo_users WHERE user_id = ? LIMIT 1',
            { replacements: [userId], type: QueryTypes.SELECT }
        );
        const user = rows[0];
        if (!user) return { isPro: false };
        const now = Math.floor(Date.now() / 1000);
        const isPro = parseInt(user.is_pro) === 1 && user.pro_time > now;
        return { isPro };
    } catch (e) {
        console.warn('[ChatUpload] subscription check failed:', e.message);
        return { isPro: false };
    }
}

// Returns current large-file count for user in YYYY-MM. Returns -1 on DB error.
async function getLargeFileCount(ctx, userId, yearMonth) {
    try {
        const rows = await ctx.sequelize.query(
            'SELECT large_file_count FROM wo_upload_quota WHERE user_id = ? AND year_month = ?',
            { replacements: [userId, yearMonth], type: QueryTypes.SELECT }
        );
        return rows[0] ? (rows[0].large_file_count || 0) : 0;
    } catch (e) {
        console.warn('[ChatUpload] quota read failed:', e.message);
        return -1;
    }
}

async function incrementLargeFileCount(ctx, userId, yearMonth) {
    try {
        await ctx.sequelize.query(
            `INSERT INTO wo_upload_quota (user_id, year_month, large_file_count)
             VALUES (?, ?, 1)
             ON DUPLICATE KEY UPDATE large_file_count = large_file_count + 1`,
            { replacements: [userId, yearMonth], type: QueryTypes.INSERT }
        );
    } catch (e) {
        console.warn('[ChatUpload] quota increment failed:', e.message);
    }
}

// ─── POST /api/node/chat/upload ────────────────────────────────────────────────

function uploadChatMedia(ctx, io) {
    return (req, res) => {
        upload(req, res, async (err) => {
            if (err instanceof multer.MulterError) {
                return res.json({ status: 400, error: `Upload error: ${err.message}` });
            }
            if (err) {
                return res.json({ status: 500, error: 'Server error during upload' });
            }

            const files = req.files || [];
            if (files.length === 0) {
                return res.json({ status: 400, error: 'No file uploaded' });
            }

            const uploadedFile = files[0];
            const tmpPath      = uploadedFile.path; // disk path from multer

            try {
                const userId   = req.user?.user_id || 0;
                const chatType = (req.body.chat_type  || 'private').toLowerCase();
                const entityId = parseInt(req.body.entity_id || '0', 10);

                let mediaType = (req.body.type || uploadedFile.fieldname || 'file').toLowerCase();
                if (!FREE_LIMITS[mediaType]) mediaType = 'file';

                // ── Subscription-aware size limits ──────────────────────────────
                const { isPro } = await getUserSubscription(ctx, userId);
                const limits    = isPro ? PRO_LIMITS : FREE_LIMITS;

                if (uploadedFile.size > limits[mediaType]) {
                    cleanupTemp(tmpPath);
                    const limitGb = (limits[mediaType] / 1024 / 1024 / 1024).toFixed(0);
                    const limitMb = Math.round(limits[mediaType] / 1024 / 1024);
                    const limitStr = limits[mediaType] >= 1024 * 1024 * 1024 ? `${limitGb} GB` : `${limitMb} MB`;
                    const hint = !isPro && uploadedFile.size <= PRO_LIMITS[mediaType]
                        ? ' Upgrade to Pro to upload larger files.'
                        : '';
                    return res.json({ status: 400, error: `File too large. Max ${limitStr} for type "${mediaType}".${hint}` });
                }

                // ── Pro monthly large-file quota ─────────────────────────────────
                const isLargeFile = uploadedFile.size > PRO_LARGE_FILE_THRESHOLD;
                let yearMonth = null;
                if (isPro && isLargeFile) {
                    const now = new Date();
                    yearMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
                    const usedCount = await getLargeFileCount(ctx, userId, yearMonth);
                    if (usedCount >= PRO_MONTHLY_LARGE_FILE_LIMIT) {
                        cleanupTemp(tmpPath);
                        return res.json({
                            status: 429,
                            error:  `Monthly large-file limit reached (${PRO_MONTHLY_LARGE_FILE_LIMIT} files >1 GB per month).`
                        });
                    }
                }
                // ── End size / quota checks ─────────────────────────────────────

                // ── Magic bytes check (reads 16 bytes from disk, no RAM buffer) ──
                const magicCheck = checkMagicBytesFromDisk(tmpPath);
                if (!magicCheck.ok) {
                    console.warn(`[ChatUpload] Blocked dangerous file from ${req.ip}: ${magicCheck.reason}`);
                    cleanupTemp(tmpPath);
                    return res.json({ status: 400, error: `Upload rejected: ${magicCheck.reason}` });
                }

                // ── Content Moderation ──────────────────────────────────────────
                // Stream-based SHA-256 for videos/files; full buffer only for images (≤25 MB)

                const moderationResult = await checkContentFromFile(
                    ctx, tmpPath, mediaType,
                    { senderId: userId, chatType, entityId }
                );

                if (moderationResult.decision === DECISION.BLOCK) {
                    console.warn(
                        `[ChatUpload] BLOCK: user=${userId} reason=${moderationResult.reason} ` +
                        `sha256=${moderationResult.sha256.slice(0, 12)}...`
                    );
                    cleanupTemp(tmpPath);
                    return res.json({
                        status: 451,
                        error:  'Загрузка запрещена: контент нарушает правила платформы',
                        reason: moderationResult.reason
                    });
                }
                // ── End Content Moderation ──────────────────────────────────────

                // Build output path
                let subDir = UPLOAD_DIRS[mediaType];
                if (mediaType === 'image') {
                    const now = new Date();
                    subDir = `${subDir}/${now.getFullYear()}/${String(now.getMonth() + 1).padStart(2, '0')}`;
                }

                const absDir  = path.join(SITE_ROOT, subDir);
                ensureDir(absDir);

                const filename = generateFilename(uploadedFile.originalname);
                const absPath  = path.join(absDir, filename);
                const relPath  = `${subDir}/${filename}`;
                const url      = fullUrl(ctx, relPath);

                // ── Move temp file to final destination ─────────────────────────
                // rename() = atomic, zero-copy on same volume.
                // Falls back to copyFile+unlink if tmp and dest are on different mounts.
                await moveTempFile(tmpPath, absPath);

                // Record large-file quota usage now that the file is safely on disk
                if (isPro && isLargeFile && yearMonth) {
                    await incrementLargeFileCount(ctx, userId, yearMonth);
                }

                // ── Moderation queue ────────────────────────────────────────────
                if (moderationResult.decision === DECISION.HOLD) {
                    console.log(`[ChatUpload] HOLD: файл сохранён, добавлен в очередь модерации: ${relPath}`);
                    await addToModerationQueue(ctx, {
                        filePath:      relPath,
                        fileUrl:       url,
                        mediaType,
                        senderId:      userId,
                        channelId:     chatType === 'channel' ? entityId : 0,
                        groupId:       chatType === 'group'   ? entityId : 0,
                        chatType,
                        contentLevel:  moderationResult.reason,
                        sha256:        moderationResult.sha256,
                        nudeNetResult: moderationResult.nudeNet,
                        reason:        moderationResult.reason
                    });
                }
                // ── End Moderation queue ────────────────────────────────────────

                // ── Background video compression for large file uploads ─────────
                const qualityParam = (req.body.quality || 'auto').toLowerCase();
                let compressionPending = false;

                if (
                    mediaType === 'file' &&
                    qualityParam !== 'original' &&
                    uploadedFile.size > MOVIE_COMPRESS_THRESHOLD &&
                    isVideoFile(filename)
                ) {
                    compressionPending = true;
                    setImmediate(() => scheduleBackgroundCompression(absPath, url, userId, io));
                }
                // ── End compression ─────────────────────────────────────────────

                const resp = {
                    status:              200,
                    image:               null, image_src: null,
                    video:               null, video_src: null,
                    audio:               null, audio_src: null,
                    file:                null, file_src:  null,
                    error:               null,
                    is_sensitive:        moderationResult.isSensitive,
                    moderation:          moderationResult.decision,
                    compression_pending: compressionPending,
                    is_pro_upload:       isPro && isLargeFile,
                };

                switch (mediaType) {
                    case 'image':
                        resp.image     = url;
                        resp.image_src = relPath;
                        break;
                    case 'video':
                        resp.video     = url;
                        resp.video_src = relPath;
                        break;
                    case 'audio':
                    case 'voice':
                        resp.audio     = url;
                        resp.audio_src = relPath;
                        break;
                    default:
                        resp.file      = url;
                        resp.file_src  = relPath;
                }

                const sizeKb = Math.round(uploadedFile.size / 1024);
                console.log(
                    `[ChatUpload] ${mediaType} uploaded: ${relPath} (${sizeKb} KB) ` +
                    `quality=${qualityParam} compression_pending=${compressionPending} ` +
                    `decision=${moderationResult.decision} sensitive=${moderationResult.isSensitive}`
                );
                return res.json(resp);

            } catch (e) {
                cleanupTemp(tmpPath);
                console.error('[ChatUpload]', e.message);
                return res.json({ status: 500, error: 'Failed to save uploaded file' });
            }
        });
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { uploadChatMedia };
