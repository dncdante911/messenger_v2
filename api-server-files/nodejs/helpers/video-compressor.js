'use strict';

/**
 * Video compression helper using ffmpeg-static + child_process.
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
const { spawn } = require('child_process');

// ─── Server-load protection ───────────────────────────────────────────────────

// Maximum CPU threads per ffmpeg process. Keeps the server responsive when
// multiple compression jobs run concurrently. Override with FFMPEG_THREADS env.
const MAX_THREADS = Math.max(1, parseInt(process.env.FFMPEG_THREADS || '4', 10));

// Maximum number of ffmpeg processes allowed to run at the same time.
// Extra jobs queue up and start as soon as a slot opens.
const MAX_CONCURRENT = Math.max(1, parseInt(process.env.FFMPEG_MAX_CONCURRENT || '2', 10));

let _activeJobs = 0;
const _jobQueue  = [];

function acquireSlot() {
    return new Promise((resolve) => {
        const tryAcquire = () => {
            if (_activeJobs < MAX_CONCURRENT) {
                _activeJobs++;
                resolve();
            } else {
                _jobQueue.push(tryAcquire);
            }
        };
        tryAcquire();
    });
}

function releaseSlot() {
    _activeJobs--;
    if (_jobQueue.length > 0) {
        const next = _jobQueue.shift();
        next();
    }
}

// ─── Resolve ffmpeg binary ────────────────────────────────────────────────────

// Resolve ffmpeg binary: bundled ffmpeg-static → system PATH fallback
let ffmpegBin = 'ffmpeg';
try {
    const staticPath = require('ffmpeg-static');
    if (staticPath && fs.existsSync(staticPath)) {
        ffmpegBin = staticPath;
        console.log('[VideoCompressor] Using bundled ffmpeg:', ffmpegBin);
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

// ─── Duration probe ───────────────────────────────────────────────────────────

// Returns duration in seconds via ffprobe, or 0 if unavailable
function probeDuration(inputPath) {
    return new Promise((resolve) => {
        // Derive ffprobe path from ffmpegBin (same directory, sibling binary)
        let ffprobeBin = 'ffprobe';
        if (ffmpegBin !== 'ffmpeg') {
            const candidate = path.join(path.dirname(ffmpegBin), 'ffprobe');
            if (fs.existsSync(candidate)) ffprobeBin = candidate;
        }

        const args = [
            '-v', 'error',
            '-select_streams', 'v:0',
            '-show_entries', 'format=duration',
            '-of', 'csv=p=0',
            inputPath,
        ];

        let out = '';
        const proc = spawn(ffprobeBin, args);
        proc.stdout.on('data', (d) => { out += d.toString(); });
        proc.on('close', () => {
            const secs = parseFloat(out.trim());
            resolve(isNaN(secs) ? 0 : secs);
        });
        proc.on('error', () => resolve(0));
    });
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

    // Probe duration up-front so we can compute progress percentage
    const durationSecs = onProgress ? await probeDuration(inputPath) : 0;

    const args = [
        '-i', inputPath,
        // Limit CPU threads so the server stays responsive under concurrent jobs.
        // Default: 4. Override: FFMPEG_THREADS env var.
        '-threads', String(MAX_THREADS),
        '-c:v', 'libx264',
        '-crf', String(tier.crf),
        '-preset', tier.preset,
        // Scale: limit the longer dimension to maxHeight, keep AR, force even pixels
        '-vf', `scale=-2:'min(${tier.maxHeight},ih)'`,
        '-c:a', 'aac',
        '-b:a', tier.audioBitrate,
        '-ac', '2',
        // Web-optimised: moov atom at start for fast streaming
        '-movflags', '+faststart',
        // Progress output to stderr in a machine-readable format
        '-progress', 'pipe:2',
        '-nostats',
        '-y',
        outputPath,
    ];

    // Wait for a free slot before spawning ffmpeg (max MAX_CONCURRENT processes)
    await acquireSlot();
    console.log(
        `[VideoCompressor] Slot acquired (active=${_activeJobs}/${MAX_CONCURRENT}) ` +
        `threads=${MAX_THREADS}`
    );

    return new Promise((resolve) => {
        const proc = spawn(ffmpegBin, args);

        let stderrBuf = '';
        proc.stderr.on('data', (chunk) => {
            stderrBuf += chunk.toString();

            // ffmpeg -progress emits key=value pairs; parse "out_time_ms"
            if (onProgress && durationSecs > 0) {
                const match = stderrBuf.match(/out_time_ms=(\d+)/g);
                if (match && match.length > 0) {
                    const lastMs = parseInt(match[match.length - 1].split('=')[1], 10);
                    const pct = Math.min(99, Math.round((lastMs / 1e6 / durationSecs) * 100));
                    onProgress(pct);
                }
            }
        });

        proc.on('close', (code) => {
            releaseSlot();
            if (code === 0 && fs.existsSync(outputPath)) {
                const outSize = fs.statSync(outputPath).size;
                const saved = inputSize > 0 ? Math.round((1 - outSize / inputSize) * 100) : 0;
                console.log(
                    `[VideoCompressor] Done: ${(inputSize / 1024 / 1024).toFixed(1)}MB → ` +
                    `${(outSize / 1024 / 1024).toFixed(1)}MB (−${saved}%) ` +
                    `(active=${_activeJobs}/${MAX_CONCURRENT})`
                );
                resolve({ success: true, outputPath, originalSize: inputSize, compressedSize: outSize });
            } else {
                const errSnippet = stderrBuf.slice(-500);
                console.error('[VideoCompressor] ffmpeg exited with code', code, '\n', errSnippet);
                resolve({ success: false, error: `ffmpeg exited with code ${code}` });
            }
        });

        proc.on('error', (err) => {
            releaseSlot();
            console.error('[VideoCompressor] spawn error:', err.message);
            resolve({ success: false, error: err.message });
        });
    });
}

// ─── In-place compression ─────────────────────────────────────────────────────

/**
 * Compress a video file in-place:
 *   1. Compress to a temp path.
 *   2. Replace original with compressed only if smaller.
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
                if (fs.existsSync(tmpPath)) try { fs.unlinkSync(tmpPath); } catch (_) {}
                onDone && onDone({ success: false, error: result.error });
                return;
            }

            const tmpSize  = fs.existsSync(tmpPath)  ? fs.statSync(tmpPath).size  : 0;
            const origSize = fs.existsSync(filePath) ? fs.statSync(filePath).size : 0;

            if (tmpSize > 0 && tmpSize < origSize) {
                fs.renameSync(tmpPath, filePath);
                onDone && onDone({ success: true, originalSize: origSize, compressedSize: tmpSize });
            } else {
                if (fs.existsSync(tmpPath)) try { fs.unlinkSync(tmpPath); } catch (_) {}
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
