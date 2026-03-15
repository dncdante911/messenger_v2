'use strict';

/**
 * Backup & Cloud Settings REST API
 *
 * Replaces PHP:
 *   GET  /api/v2/endpoints/get_cloud_backup_settings.php
 *   POST /api/v2/endpoints/update_cloud_backup_settings.php
 *   GET  /api/v2/endpoints/get-backup-statistics.php
 *
 * Node.js endpoints:
 *   GET  /api/node/backup/settings    — get cloud backup settings
 *   POST /api/node/backup/settings    — update cloud backup settings
 *   POST /api/node/backup/statistics  — get backup statistics
 */

const { QueryTypes }    = require('sequelize');
const { requireAuth }   = require('../helpers/validate-token');

const TABLE = 'Wo_UserCloudBackupSettings';

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Serialize a DB row to the JSON shape expected by Android CloudBackupSettings. */
function serializeSettings(row) {
    return {
        mobile_photos:              !!row.mobile_photos,
        mobile_videos:              !!row.mobile_videos,
        mobile_files:               !!row.mobile_files,
        mobile_videos_limit:        row.mobile_videos_limit  ?? 100,
        mobile_files_limit:         row.mobile_files_limit   ?? 100,
        wifi_photos:                !!row.wifi_photos,
        wifi_videos:                !!row.wifi_videos,
        wifi_files:                 !!row.wifi_files,
        wifi_videos_limit:          row.wifi_videos_limit    ?? 100,
        wifi_files_limit:           row.wifi_files_limit     ?? 100,
        roaming_photos:             !!row.roaming_photos,
        save_to_gallery_private_chats: !!row.save_to_gallery_private_chats,
        save_to_gallery_groups:     !!row.save_to_gallery_groups,
        save_to_gallery_channels:   !!row.save_to_gallery_channels,
        streaming_enabled:          !!row.streaming_enabled,
        cache_size_limit:           row.cache_size_limit     ?? 0,
        backup_enabled:             !!row.backup_enabled,
        backup_provider:            row.backup_provider      ?? 'local',
        backup_frequency:           row.backup_frequency     ?? 'weekly',
        last_backup_time:           row.last_backup_time     ?? null,
        proxy_enabled:              !!row.proxy_enabled,
        proxy_host:                 row.proxy_host           ?? null,
        proxy_port:                 row.proxy_port           ?? null,
    };
}

/** Get or create default settings row for the user. Returns the row. */
async function getOrCreateSettings(ctx, userId) {
    const seq = ctx.sequelize;
    const rows = await seq.query(
        `SELECT * FROM \`${TABLE}\` WHERE user_id = :uid LIMIT 1`,
        { replacements: { uid: userId }, type: QueryTypes.SELECT }
    );
    if (rows.length > 0) return rows[0];

    await seq.query(
        `INSERT INTO \`${TABLE}\` (user_id) VALUES (:uid)`,
        { replacements: { uid: userId }, type: QueryTypes.INSERT }
    );
    const created = await seq.query(
        `SELECT * FROM \`${TABLE}\` WHERE user_id = :uid LIMIT 1`,
        { replacements: { uid: userId }, type: QueryTypes.SELECT }
    );
    return created[0];
}

// ─── Route Handlers ───────────────────────────────────────────────────────────

/** GET /api/node/backup/settings */
function getBackupSettings(ctx) {
    return async (req, res) => {
        try {
            const row = await getOrCreateSettings(ctx, req.userId);
            return res.json({ api_status: 200, settings: serializeSettings(row) });
        } catch (err) {
            console.error('[Backup/getSettings]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

/** POST /api/node/backup/settings */
function updateBackupSettings(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const b      = req.body;

            // Build only the fields that were provided
            const updates = {};
            const boolField = (k) => { if (b[k] !== undefined) updates[k] = b[k] === 'true' || b[k] === true || b[k] === 1 ? 1 : 0; };
            const intField  = (k) => { if (b[k] !== undefined && b[k] !== null && b[k] !== '') updates[k] = parseInt(b[k], 10); };
            const strField  = (k) => { if (b[k] !== undefined && b[k] !== null) updates[k] = String(b[k]); };

            boolField('mobile_photos');
            boolField('mobile_videos');
            boolField('mobile_files');
            intField('mobile_videos_limit');
            intField('mobile_files_limit');
            boolField('wifi_photos');
            boolField('wifi_videos');
            boolField('wifi_files');
            intField('wifi_videos_limit');
            intField('wifi_files_limit');
            boolField('roaming_photos');
            boolField('save_to_gallery_private_chats');
            boolField('save_to_gallery_groups');
            boolField('save_to_gallery_channels');
            boolField('streaming_enabled');
            intField('cache_size_limit');
            boolField('backup_enabled');
            strField('backup_provider');
            strField('backup_frequency');
            boolField('proxy_enabled');
            strField('proxy_host');
            intField('proxy_port');

            if (b.mark_backup_complete === 'true' || b.mark_backup_complete === true) {
                updates.last_backup_time = Math.floor(Date.now() / 1000);
            }

            if (Object.keys(updates).length === 0) {
                // Nothing to update — just return current settings
                const row = await getOrCreateSettings(ctx, userId);
                return res.json({ api_status: 200, message: 'No changes', settings: serializeSettings(row) });
            }

            // Ensure row exists
            await getOrCreateSettings(ctx, userId);

            const setClauses = Object.keys(updates).map(k => `\`${k}\` = :${k}`).join(', ');
            await ctx.sequelize.query(
                `UPDATE \`${TABLE}\` SET ${setClauses} WHERE user_id = :userId`,
                { replacements: { ...updates, userId }, type: QueryTypes.UPDATE }
            );

            const row = await getOrCreateSettings(ctx, userId);
            return res.json({ api_status: 200, message: 'Settings updated', settings: serializeSettings(row) });

        } catch (err) {
            console.error('[Backup/updateSettings]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

/** POST /api/node/backup/statistics */
function getBackupStatistics(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const seq    = ctx.sequelize;

            // Message counts
            const [msgStats] = await seq.query(
                `SELECT
                    COUNT(*)                                      AS total_messages,
                    SUM(CASE WHEN from_id = :uid THEN 1 ELSE 0 END) AS messages_sent,
                    SUM(CASE WHEN to_id   = :uid THEN 1 ELSE 0 END) AS messages_received
                 FROM Wo_Messages WHERE from_id = :uid OR to_id = :uid`,
                { replacements: { uid: userId }, type: QueryTypes.SELECT }
            );

            // Media count
            const [mediaStats] = await seq.query(
                `SELECT COUNT(*) AS media_files_count
                 FROM Wo_Messages
                 WHERE (from_id = :uid OR to_id = :uid)
                   AND media IS NOT NULL AND media != ''`,
                { replacements: { uid: userId }, type: QueryTypes.SELECT }
            );

            // Groups count
            const [groupsRow] = await seq.query(
                `SELECT COUNT(*) AS groups_count FROM Wo_GroupChat
                 WHERE id IN (SELECT group_id FROM Wo_GroupChatUsers WHERE user_id = :uid)
                    OR user_id = :uid`,
                { replacements: { uid: userId }, type: QueryTypes.SELECT }
            );

            // Last backup time from settings
            const settings = await getOrCreateSettings(ctx, userId);

            // Estimate storage: avg 500 bytes per message + 500 KB per media file
            const totalMsg      = parseInt(msgStats?.total_messages      ?? 0, 10);
            const mediaCount    = parseInt(mediaStats?.media_files_count ?? 0, 10);
            const mediaSizeBytes = mediaCount * 512 * 1024;
            const totalBytes    = totalMsg * 500 + mediaSizeBytes;
            const serverName    = (ctx.globalconfig?.site_name || ctx.globalconfig?.site_url || 'WorldMates');

            return res.json({
                api_status: 200,
                statistics: {
                    total_messages:         totalMsg,
                    messages_sent:          parseInt(msgStats?.messages_sent     ?? 0, 10),
                    messages_received:      parseInt(msgStats?.messages_received ?? 0, 10),
                    media_files_count:      mediaCount,
                    media_size_bytes:       mediaSizeBytes,
                    media_size_mb:          parseFloat((mediaSizeBytes / 1024 / 1024).toFixed(2)),
                    groups_count:           parseInt(groupsRow?.groups_count ?? 0, 10),
                    channels_count:         0,
                    total_storage_bytes:    totalBytes,
                    total_storage_mb:       parseFloat((totalBytes / 1024 / 1024).toFixed(2)),
                    total_storage_gb:       parseFloat((totalBytes / 1024 / 1024 / 1024).toFixed(3)),
                    last_backup_time:       settings.last_backup_time ?? null,
                    backup_provider:        settings.backup_provider  ?? 'local',
                    backup_frequency:       settings.backup_frequency ?? 'weekly',
                    server_name:            serverName,
                },
            });

        } catch (err) {
            console.error('[Backup/statistics]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── Route Registration ───────────────────────────────────────────────────────

function registerBackupRoutes(app, ctx) {
    const auth = requireAuth(ctx);
    app.get ('/api/node/backup/settings',   auth, getBackupSettings(ctx));
    app.post('/api/node/backup/settings',   auth, updateBackupSettings(ctx));
    app.post('/api/node/backup/statistics', auth, getBackupStatistics(ctx));
}

module.exports = { registerBackupRoutes };
