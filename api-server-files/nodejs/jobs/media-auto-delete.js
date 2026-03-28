'use strict';
/**
 * Media Auto-Delete Job
 *
 * Runs every hour. Finds all messages where media_delete_at <= NOW()
 * and media_deleted = 0, then:
 *   1. Deletes the physical media file from the server.
 *   2. Marks the message as media_deleted = 1, sets media = 'deleted'.
 *   3. Emits 'media_deleted' socket event to both participants so the UI
 *      shows a placeholder instead of the file.
 *
 * Started from main.js (worker 0 only):
 *   const setupMediaAutoDeleteJob = require('./jobs/media-auto-delete');
 *   setupMediaAutoDeleteJob(sequelize, io);
 */

const path = require('path');
const fs   = require('fs');

// Same SITE_ROOT as media-upload.js — resolves to api-server-files/
const SITE_ROOT = path.resolve(__dirname, '..', '..');

// Media sub-directories by type_two value
const TYPE_DIR_MAP = {
    image: 'upload/photos',
    video: 'upload/videos',
    audio: 'upload/audios',
    voice: 'upload/audios',
    file:  'upload/files',
};

function log(msg) {
    const ts = new Date().toISOString().replace('T', ' ').slice(0, 19);
    console.log(`[${ts}] [CRON:MEDIA-AUTO-DELETE] ${msg}`);
}

/**
 * Resolve the full filesystem path for a media file stored in a message.
 *
 * `media` column stores relative web paths like:
 *   upload/photos/2024/03/abc123.jpg
 *   upload/videos/xyz.mp4
 *
 * We support both: a plain filename (falls back to type_two dir lookup)
 * and a full relative path that already contains the sub-dir.
 */
function resolveFilePath(mediaValue, typeTwo) {
    if (!mediaValue || mediaValue === 'deleted') return null;

    let fullPath;
    if (mediaValue.startsWith('upload/')) {
        fullPath = path.join(SITE_ROOT, mediaValue);
    } else {
        const dir = TYPE_DIR_MAP[typeTwo] || TYPE_DIR_MAP['file'];
        fullPath = path.join(SITE_ROOT, dir, mediaValue);
    }

    // Security: reject path traversal attempts (e.g. media = '../../etc/passwd')
    const resolved = path.resolve(fullPath);
    const siteRoot = path.resolve(SITE_ROOT);
    if (!resolved.startsWith(siteRoot + path.sep) && resolved !== siteRoot) {
        log(`SECURITY: Blocked path traversal for media value: ${mediaValue}`);
        return null;
    }

    return resolved;
}

async function runCleanup(sequelize, io) {
    try {
        const now = new Date();

        // Find all messages that need media deletion
        const [messages] = await sequelize.query(
            `SELECT id, from_id, to_id, media, type_two
             FROM Wo_Messages
             WHERE media_delete_at IS NOT NULL
               AND media_delete_at <= :now
               AND media_deleted = 0
               AND media IS NOT NULL
               AND media != ''
               AND media != 'deleted'
             LIMIT 500`,
            { replacements: { now } }
        );

        if (!messages || !messages.length) return;

        log(`Processing ${messages.length} message(s) for media deletion…`);

        let deleted = 0;
        let failed  = 0;

        for (const msg of messages) {
            try {
                // 1. Delete physical file
                const filePath = resolveFilePath(msg.media, msg.type_two);
                if (filePath) {
                    try {
                        if (fs.existsSync(filePath)) {
                            fs.unlinkSync(filePath);
                        }
                    } catch (fileErr) {
                        // File not found or permission error — still mark as deleted
                        log(`WARN: Could not delete file ${filePath}: ${fileErr.message}`);
                    }
                }

                // 2. Mark message as media deleted
                await sequelize.query(
                    `UPDATE Wo_Messages
                     SET media = 'deleted', media_deleted = 1
                     WHERE id = :id`,
                    { replacements: { id: msg.id } }
                );

                // 3. Notify both participants via Socket.IO
                const payload = {
                    message_id:    msg.id,
                    media_deleted: true,
                };
                if (io) {
                    if (msg.from_id) io.to(String(msg.from_id)).emit('media_deleted', payload);
                    if (msg.to_id)   io.to(String(msg.to_id)).emit('media_deleted',   payload);
                }

                deleted++;
            } catch (msgErr) {
                log(`ERROR processing message ${msg.id}: ${msgErr.message}`);
                failed++;
            }
        }

        log(`Done. ${deleted} media file(s) deleted, ${failed} error(s).`);
    } catch (err) {
        log(`ERROR: ${err.message}`);
    }
}

/**
 * Set up the media auto-delete job.
 *
 * @param {object} sequelize  Sequelize instance (from main.js ctx.sequelize)
 * @param {object} io         Socket.IO server instance
 */
function setupMediaAutoDeleteJob(sequelize, io) {
    const INTERVAL = 60 * 60 * 1000; // 1 hour

    // Run once at startup (small delay so server is fully up)
    setTimeout(() => runCleanup(sequelize, io), 10 * 1000);

    // Then every hour
    setInterval(() => runCleanup(sequelize, io), INTERVAL);

    log('Media auto-delete job scheduled — runs every 1 hour');
}

module.exports = setupMediaAutoDeleteJob;
