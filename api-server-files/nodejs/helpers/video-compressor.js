'use strict';

/**
 * Video compression helper using fluent-ffmpeg.
 *
 * Telegram-like quality tiers:
 *   video_message – 480p, CRF 28  – short in-chat clips (~5-15 MB)
 *   compressed    – 720p, CRF 23  – user-chosen "compressed" send
 *   high          – 1080p, CRF 21 – user-chosen "good quality" send
 *   auto          – picks tier based on file size
 *
 * For large file uploads (movies):
 *   compressInPlace() saves original → compresses to temp → replaces original.
 *   The URL in the message stays the same; the file on disk shrinks.
 */

const path   = require('path');
const fs     = require('fs');
const ffmpeg = require('fluent-ffmpeg');

// Use bundled ffmpeg-static binary when available, otherwise fall back to PATH
try {
    const ffmpegPath = require('ffmpeg-static');
    if (ffmpegPath) {
        ffmpeg.setFfmpegPath(ffmpegPath);
        console.log('[VideoCompressor] Using bundled ffmpeg:', ffmpegPath);
    }
} catch (_) {
    console.log('[VideoCompressor] ffmpeg-static not found, using system ffmpeg from PATH');
}

// ─── Quality Tiers ────────────────────────────────────────────────────────────

const TIERS = {
    // Short video messages recorded inside the app (like TG video messages)
    video_message: { maxHeight: 480,  crf: 28, preset: 'fast',   audioBitrate: '96k'  },
    // "Send as video" – compressed, user chose to reduce file size
    compressed:    { maxHeight: 720,  crf: 23, preset: 'fast',   audioBitrate: '128k' },
    // "Good quality" – near-original, user chose quality over size
    high:          { maxHeight: 1080, crf: 21, preset: 'medium', audioBitrate: '192k' },
};

// Auto-select tier for large file uploads based on input size
function autoTierForFile(fileSizeBytes) {
    const mb = fileSizeBytes / (1024 * 1024);
    if (mb < 500)  return { maxHeight: 720,  crf: 23, preset: 'fast',   audioBitrate: '128k' };
    if (mb < 2048) return { maxHeight: 1080, crf: 26, preset: 'fast',   audioBitrate: '128k' };
    return              { maxHeight: 1080, crf: 28, preset: 'medium', audioBitrate: '128k' };
}

// ─── Core compression function ────────────────────────────────────────────────

/**
 * Compress a video file.
 *
 * @param {string}   inputPath   - Source file (absolute path)
 * @param {string}   outputPath  - Destination file (absolute path, must not exist)
 * @param {object}   [opts]
 * @param {string}   [opts.quality]    - 'video_message' | 'compressed' | 'high' | 'auto'
 * @param {number}   [opts.fileSize]   - Input size in bytes (used for 'auto' tier)
 * @param {Function} [opts.onProgress] - Called with (0-100) percent
 * @returns {Promise<{success: boolean, outputPath?: string, error?: string}>}
 */
async function compressVideo(inputPath, outputPath, opts = {}) {
    const { quality = 'auto', fileSize = 0, onProgress } = opts;

    let tier;
    if (quality === 'auto' || !TIERS[quality]) {
        tier = autoTierForFile(fileSize || (fs.existsSync(inputPath) ? fs.statSync(inputPath).size : 0));
    } else {
        tier = TIERS[quality];
    }

    const inputSize = fs.existsSync(inputPath) ? fs.statSync(inputPath).size : 0;
    console.log(
        `[VideoCompressor] Starting: quality=${quality} tier=${tier.maxHeight}p ` +
        `CRF=${tier.crf} preset=${tier.preset} input=${(inputSize / 1024 / 1024).toFixed(1)}MB`
    );

    return new Promise((resolve) => {
        let cmd = ffmpeg(inputPath)
            .videoCodec('libx264')
            .audioCodec('aac')
            .outputOptions([
                `-crf ${tier.crf}`,
                `-preset ${tier.preset}`,
                // Scale: limit height to maxHeight, keep aspect ratio, ensure even dimensions
                `-vf scale=-2:min(${tier.maxHeight}\\,ih)`,
                // Web-optimised: place moov atom at start of file for fast streaming
                '-movflags +faststart',
                `-b:a ${tier.audioBitrate}`,
                '-ac 2',
            ])
            .output(outputPath);

        if (onProgress) {
            cmd = cmd.on('progress', (p) => {
                onProgress(Math.min(99, Math.round(p.percent || 0)));
            });
        }

        cmd
            .on('end', () => {
                const outSize = fs.existsSync(outputPath) ? fs.statSync(outputPath).size : 0;
                const pct = inputSize > 0 ? Math.round((1 - outSize / inputSize) * 100) : 0;
                console.log(
                    `[VideoCompressor] Done: ${(inputSize / 1024 / 1024).toFixed(1)}MB → ` +
                    `${(outSize / 1024 / 1024).toFixed(1)}MB (−${pct}%)`
                );
                resolve({ success: true, outputPath, originalSize: inputSize, compressedSize: outSize });
            })
            .on('error', (err) => {
                console.error('[VideoCompressor] Error:', err.message);
                resolve({ success: false, error: err.message });
            })
            .run();
    });
}

// ─── In-place compression ─────────────────────────────────────────────────────

/**
 * Compress a video file in-place:
 *   1. Compress to a temp path.
 *   2. Replace original with compressed.
 *
 * The URL stored in the message stays valid — only the file on disk changes.
 *
 * @param {string}   filePath  - Absolute path to the file to compress (will be replaced)
 * @param {object}   [opts]    - Same as compressVideo opts
 * @param {Function} [onDone]  - Called with { success, originalSize, compressedSize } when finished
 */
function compressInPlace(filePath, opts = {}, onDone) {
    const tmpPath = filePath + '.compressing.tmp';

    // Clean up any leftover tmp from a previous crashed run
    if (fs.existsSync(tmpPath)) {
        try { fs.unlinkSync(tmpPath); } catch (_) {}
    }

    compressVideo(filePath, tmpPath, opts)
        .then((result) => {
            if (!result.success) {
                if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath);
                onDone && onDone({ success: false, error: result.error });
                return;
            }

            // Only replace if the compressed file is actually smaller
            const tmpSize = fs.existsSync(tmpPath) ? fs.statSync(tmpPath).size : 0;
            const origSize = fs.existsSync(filePath) ? fs.statSync(filePath).size : 0;

            if (tmpSize > 0 && tmpSize < origSize) {
                fs.renameSync(tmpPath, filePath);
                onDone && onDone({ success: true, originalSize: origSize, compressedSize: tmpSize });
            } else {
                // Compressed file is not smaller — keep original
                if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath);
                console.log('[VideoCompressor] Skipped replacement: compressed is not smaller');
                onDone && onDone({ success: true, originalSize: origSize, compressedSize: origSize, skipped: true });
            }
        })
        .catch((err) => {
            if (fs.existsSync(tmpPath)) try { fs.unlinkSync(tmpPath); } catch (_) {}
            console.error('[VideoCompressor] compressInPlace error:', err.message);
            onDone && onDone({ success: false, error: err.message });
        });
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const VIDEO_EXTENSIONS = new Set([
    '.mp4', '.mov', '.avi', '.mkv', '.webm',
    '.flv', '.m4v', '.wmv', '.ts', '.3gp', '.mpeg', '.mpg',
]);

function isVideoFile(filePath) {
    return VIDEO_EXTENSIONS.has(path.extname(filePath).toLowerCase());
}

module.exports = { compressVideo, compressInPlace, isVideoFile, TIERS, autoTierForFile };
