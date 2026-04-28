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
 *   chat_type   – 'private' | 'group' | 'channel' (для политики контента)
 *   entity_id   – ID группы или канала (0 для приватного чата)
 *
 * Response (compatible with Android XhrUploadResponse):
 *   { status: 200, image: "url", image_src: "path", is_sensitive: false }
 *   { status: 200, video: "url", video_src: "path", compression_pending: false }
 *   { status: 200, audio: "url", audio_src: "path" }
 *   { status: 200, file:  "url", file_src:  "path", compression_pending: true }
 *
 * Video compression strategy (Telegram-like):
 *   type=video, quality=video_message → Android already compressed; server skips (< 50 MB expected)
 *   type=video, quality=compressed    → Android compressed to 720p; server skips
 *   type=video, quality=high          → Android compressed to 1080p; server skips
 *   type=video, quality=original      → No compression anywhere; stored as-is
 *   type=file   (large video/movie)   → Saved immediately; ffmpeg runs in background in-place.
 *                                       compression_pending=true in response.
 *                                       Socket.IO emits 'media_compressed' when done.
 *
 * File size limits:
 *   Images : 25 MB
 *   Videos : 1 GB   (Android pre-compresses, so this is the post-compression ceiling)
 *   Audio  : 100 MB
 *   Files  : 10 GB  (allows full movie uploads)
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');

const {
    DECISION,
    checkContent,
    addToModerationQueue
} = require('../../helpers/content-moderator');

const {
    compressInPlace,
    isVideoFile,
} = require('../../helpers/video-compressor');

// ─── Config ────────────────────────────────────────────────────────────────────

const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');

const LIMITS = {
    image: 25   * 1024 * 1024,          //  25 MB
    video: 1024 * 1024 * 1024,          //   1 GB (Android pre-compresses)
    audio: 100  * 1024 * 1024,          // 100 MB
    voice: 100  * 1024 * 1024,          // 100 MB
    file:  10   * 1024 * 1024 * 1024,   //  10 GB (full movies)
};

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

function checkMagicBytes(buffer) {
    if (!buffer || buffer.length < 4) {
        return { ok: false, reason: 'File too small to validate' };
    }
    for (const { sig, label } of BLOCKED_SIGNATURES) {
        if (buffer.length >= sig.length && buffer.slice(0, sig.length).equals(sig)) {
            return { ok: false, reason: `Rejected file type: ${label}` };
        }
    }
    return { ok: true };
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

// ─── multer — memory storage for validation, then write to disk ───────────────

const upload = multer({
    storage: multer.memoryStorage(),
    limits:  { fileSize: Math.max(...Object.values(LIMITS)) },
}).any();

// ─── Background compression for large video files (movies) ───────────────────

/**
 * Kick off background ffmpeg compression for a large file-type video.
 * The file URL stays the same; the on-disk file is replaced with a smaller one.
 * Notifies the uploader via Socket.IO when done.
 */
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

            // Notify the uploader so they can show a "file compressed" indicator
            if (io && userId) {
                io.to(String(userId)).emit('media_compressed', {
                    url,
                    original_size:    result.originalSize,
                    compressed_size:  result.compressedSize,
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

            try {
                const files = req.files || [];
                if (files.length === 0) {
                    return res.json({ status: 400, error: 'No file uploaded' });
                }

                const uploadedFile = files[0];

                let mediaType = (req.body.type || uploadedFile.fieldname || 'file').toLowerCase();
                if (!LIMITS[mediaType]) mediaType = 'file';

                if (uploadedFile.size > LIMITS[mediaType]) {
                    const limitMb = Math.round(LIMITS[mediaType] / 1024 / 1024);
                    return res.json({ status: 400, error: `File too large. Max ${limitMb} MB for type "${mediaType}"` });
                }

                // Magic bytes check
                const magicCheck = checkMagicBytes(uploadedFile.buffer);
                if (!magicCheck.ok) {
                    console.warn(`[ChatUpload] Blocked dangerous file from ${req.ip}: ${magicCheck.reason}`);
                    return res.json({ status: 400, error: `Upload rejected: ${magicCheck.reason}` });
                }

                // ── Content Moderation ──────────────────────────────────────
                const chatType = (req.body.chat_type  || 'private').toLowerCase();
                const entityId = parseInt(req.body.entity_id || '0', 10);
                const userId   = req.user?.user_id || 0;

                const moderationResult = await checkContent(
                    ctx,
                    uploadedFile.buffer,
                    mediaType,
                    { senderId: userId, chatType, entityId }
                );

                if (moderationResult.decision === DECISION.BLOCK) {
                    console.warn(
                        `[ChatUpload] BLOCK: user=${userId} reason=${moderationResult.reason} ` +
                        `sha256=${moderationResult.sha256.slice(0, 12)}...`
                    );
                    return res.json({
                        status: 451,
                        error:  'Загрузка запрещена: контент нарушает правила платформы',
                        reason: moderationResult.reason
                    });
                }
                // ── End Content Moderation ──────────────────────────────────

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

                await fs.promises.writeFile(absPath, uploadedFile.buffer);

                // ── Moderation queue ────────────────────────────────────────
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
                // ── End Moderation queue ────────────────────────────────────

                // ── Background video compression for large file uploads ─────
                // "file" type = user sent video as file (movie).
                // We compress in-place asynchronously so the URL stays valid.
                // "video" type = Android already compressed before upload — skip.
                const qualityParam = (req.body.quality || 'auto').toLowerCase();
                let compressionPending = false;

                if (
                    mediaType === 'file' &&
                    qualityParam !== 'original' &&
                    uploadedFile.size > MOVIE_COMPRESS_THRESHOLD &&
                    isVideoFile(filename)
                ) {
                    compressionPending = true;
                    // Non-blocking: respond immediately, compress in background
                    setImmediate(() => scheduleBackgroundCompression(absPath, url, userId, io));
                }
                // ── End compression ─────────────────────────────────────────

                // Build XhrUploadResponse-compatible JSON
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
                console.error('[ChatUpload]', e.message);
                return res.json({ status: 500, error: 'Failed to save uploaded file' });
            }
        });
    };
}

// ─── exports ──────────────────────────────────────────────────────────────────

module.exports = { uploadChatMedia };
