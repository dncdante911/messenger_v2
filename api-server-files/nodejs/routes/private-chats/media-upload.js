'use strict';

/**
 * Chat Media Upload — replaces PHP /xhr/upload_*.php endpoints
 *
 * Single unified endpoint:
 *   POST /api/node/chat/upload
 *
 * Body (multipart/form-data):
 *   file   – the file to upload (any field name: image, video, audio, file)
 *   type   – media type: "image" | "video" | "audio" | "voice" | "file"
 *
 * Response (compatible with Android XhrUploadResponse):
 *   { status: 200, image: "url", image_src: "path" }   for images
 *   { status: 200, video: "url", video_src: "path" }   for videos
 *   { status: 200, audio: "url", audio_src: "path" }   for audio/voice
 *   { status: 200, file:  "url", file_src:  "path" }   for files
 *
 * File size limits (match Android Constants):
 *   Images : 25 MB
 *   Videos : 1 GB
 *   Audio  : 100 MB
 *   Files  : 250 MB
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');

// ─── Config ────────────────────────────────────────────────────────────────────

const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');

const LIMITS = {
    image: 25  * 1024 * 1024,   //  25 MB
    video: 1024 * 1024 * 1024,  //   1 GB
    audio: 100 * 1024 * 1024,   // 100 MB
    voice: 100 * 1024 * 1024,   // 100 MB
    file:  250 * 1024 * 1024,   // 250 MB
};

const UPLOAD_DIRS = {
    image: 'upload/photos',
    video: 'upload/videos',
    audio: 'upload/audios',
    voice: 'upload/audios',
    file:  'upload/files',
};

// ─── Magic Bytes Security ──────────────────────────────────────────────────────
// Block executable/script files disguised as media regardless of extension.

const BLOCKED_SIGNATURES = [
    { sig: Buffer.from([0x7F, 0x45, 0x4C, 0x46]),                   label: 'ELF executable'      }, // ELF
    { sig: Buffer.from([0x4D, 0x5A]),                                label: 'Windows PE/EXE/DLL'  }, // MZ
    { sig: Buffer.from([0x3C, 0x3F, 0x70, 0x68, 0x70]),             label: 'PHP script'           }, // <?php
    { sig: Buffer.from([0x23, 0x21, 0x2F, 0x62, 0x69, 0x6E]),       label: 'shell script (bin)'  }, // #!/bin
    { sig: Buffer.from([0x23, 0x21, 0x2F, 0x75, 0x73, 0x72]),       label: 'shell script (usr)'  }, // #!/usr
    { sig: Buffer.from([0xCA, 0xFE, 0xBA, 0xBE]),                   label: 'Java class / Mach-O' }, // .class
    { sig: Buffer.from([0xCF, 0xFA, 0xED, 0xFE]),                   label: 'Mach-O 64-bit'       }, // Mach-O 64
    { sig: Buffer.from([0x50, 0x4B, 0x03, 0x04]),                   label: 'ZIP / APK archive'   }, // PK
    { sig: Buffer.from([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07]),       label: 'RAR archive'         }, // Rar!
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

// ─── multer — memory storage, write to disk ourselves ─────────────────────────

// Accept any field name (image, video, audio, file) that Android might send
const upload = multer({
    storage: multer.memoryStorage(),
    limits:  { fileSize: Math.max(...Object.values(LIMITS)) },
}).any();

// ─── POST /api/node/chat/upload ────────────────────────────────────────────────

function uploadChatMedia(ctx) {
    return (req, res) => {
        upload(req, res, async (err) => {
            if (err instanceof multer.MulterError) {
                return res.json({ status: 400, error: `Upload error: ${err.message}` });
            }
            if (err) {
                return res.json({ status: 500, error: 'Server error during upload' });
            }

            try {
                // Determine media type from body param or from the field name used
                const files = req.files || [];
                if (files.length === 0) {
                    return res.json({ status: 400, error: 'No file uploaded' });
                }

                const uploadedFile = files[0];

                // type from body > field name > default to 'file'
                let mediaType = (req.body.type || uploadedFile.fieldname || 'file').toLowerCase();
                if (!LIMITS[mediaType]) mediaType = 'file';

                // Enforce size limit for the detected type
                if (uploadedFile.size > LIMITS[mediaType]) {
                    const limitMb = Math.round(LIMITS[mediaType] / 1024 / 1024);
                    return res.json({ status: 400, error: `File too large. Max ${limitMb} MB for type "${mediaType}"` });
                }

                // Magic bytes check — reject executables and scripts disguised as media
                const magicCheck = checkMagicBytes(uploadedFile.buffer);
                if (!magicCheck.ok) {
                    console.warn(`[ChatUpload] Blocked dangerous file from ${req.ip}: ${magicCheck.reason}`);
                    return res.json({ status: 400, error: `Upload rejected: ${magicCheck.reason}` });
                }

                // Build output path
                let subDir = UPLOAD_DIRS[mediaType];

                // For images, organise into YYYY/MM sub-directories like avatars do
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

                // Write buffer to disk (async — avoids blocking the Event Loop)
                await fs.promises.writeFile(absPath, uploadedFile.buffer);

                // Build XhrUploadResponse-compatible JSON
                const resp = {
                    status:     200,
                    image:      null, image_src: null,
                    video:      null, video_src: null,
                    audio:      null, audio_src: null,
                    file:       null, file_src:  null,
                    error:      null,
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

                console.log(`[ChatUpload] ${mediaType} uploaded: ${relPath} (${Math.round(uploadedFile.size / 1024)} KB)`);
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
