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

// ─── Media Settings (Wo_UserMediaSettings) ────────────────────────────────────

const MEDIA_TABLE = 'Wo_UserMediaSettings';

const ALLOWED_DOWNLOAD_MODES = ['wifi_only', 'always', 'never'];

async function getOrCreateMediaSettings(ctx, userId) {
    const seq = ctx.sequelize;
    const rows = await seq.query(
        `SELECT * FROM \`${MEDIA_TABLE}\` WHERE user_id = :uid LIMIT 1`,
        { replacements: { uid: userId }, type: QueryTypes.SELECT }
    );
    if (rows.length > 0) return rows[0];

    await seq.query(
        `INSERT INTO \`${MEDIA_TABLE}\`
         (user_id, auto_download_photos, auto_download_videos, auto_download_audio,
          auto_download_documents, compress_photos, compress_videos, backup_enabled,
          last_backup_time, created_at, updated_at)
         VALUES (:uid, 'wifi_only', 'wifi_only', 'always', 'wifi_only', 1, 1, 1, NULL, :now, :now)`,
        { replacements: { uid: userId, now: Math.floor(Date.now() / 1000) }, type: QueryTypes.INSERT }
    );
    const created = await seq.query(
        `SELECT * FROM \`${MEDIA_TABLE}\` WHERE user_id = :uid LIMIT 1`,
        { replacements: { uid: userId }, type: QueryTypes.SELECT }
    );
    return created[0];
}

function serializeMediaSettings(row) {
    return {
        auto_download_photos:    row.auto_download_photos    || 'wifi_only',
        auto_download_videos:    row.auto_download_videos    || 'wifi_only',
        auto_download_audio:     row.auto_download_audio     || 'always',
        auto_download_documents: row.auto_download_documents || 'wifi_only',
        compress_photos:         !!row.compress_photos,
        compress_videos:         !!row.compress_videos,
        backup_enabled:          !!row.backup_enabled,
        last_backup_time:        row.last_backup_time || null,
    };
}

/** POST /api/node/media-settings/get */
function getMediaSettings(ctx) {
    return async (req, res) => {
        try {
            const row = await getOrCreateMediaSettings(ctx, req.userId);
            return res.json({ api_status: 200, settings: serializeMediaSettings(row) });
        } catch (err) {
            console.error('[MediaSettings/get]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

/** POST /api/node/media-settings/update */
function updateMediaSettings(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const b = req.body;
            const now = Math.floor(Date.now() / 1000);

            await getOrCreateMediaSettings(ctx, userId);

            const setClauses = [];
            const replacements = { userId, now };

            if (b.auto_download_photos !== undefined && ALLOWED_DOWNLOAD_MODES.includes(b.auto_download_photos)) {
                setClauses.push('auto_download_photos = :adp');
                replacements.adp = b.auto_download_photos;
            }
            if (b.auto_download_videos !== undefined && ALLOWED_DOWNLOAD_MODES.includes(b.auto_download_videos)) {
                setClauses.push('auto_download_videos = :adv');
                replacements.adv = b.auto_download_videos;
            }
            if (b.auto_download_audio !== undefined && ALLOWED_DOWNLOAD_MODES.includes(b.auto_download_audio)) {
                setClauses.push('auto_download_audio = :ada');
                replacements.ada = b.auto_download_audio;
            }
            if (b.auto_download_documents !== undefined && ALLOWED_DOWNLOAD_MODES.includes(b.auto_download_documents)) {
                setClauses.push('auto_download_documents = :add');
                replacements.add = b.auto_download_documents;
            }
            if (b.compress_photos !== undefined) {
                setClauses.push('compress_photos = :cp');
                replacements.cp = (b.compress_photos === 'true' || b.compress_photos === true || b.compress_photos === 1) ? 1 : 0;
            }
            if (b.compress_videos !== undefined) {
                setClauses.push('compress_videos = :cv');
                replacements.cv = (b.compress_videos === 'true' || b.compress_videos === true || b.compress_videos === 1) ? 1 : 0;
            }
            if (b.backup_enabled !== undefined) {
                setClauses.push('backup_enabled = :be');
                replacements.be = (b.backup_enabled === 'true' || b.backup_enabled === true || b.backup_enabled === 1) ? 1 : 0;
            }
            if (b.mark_backup_complete === 'true' || b.mark_backup_complete === true) {
                setClauses.push('last_backup_time = :now');
            }

            setClauses.push('updated_at = :now');

            await ctx.sequelize.query(
                `UPDATE \`${MEDIA_TABLE}\` SET ${setClauses.join(', ')} WHERE user_id = :userId`,
                { replacements, type: QueryTypes.UPDATE }
            );

            return res.json({ api_status: 200, message: 'settings updated successfully' });
        } catch (err) {
            console.error('[MediaSettings/update]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET /api/node/backup/export ─────────────────────────────────────────────
// Collects user messages, groups, and settings into a JSON export.

function exportUserData(ctx) {
    return async (req, res) => {
        const userId = req.userId;
        try {
            const { Op } = require('sequelize');

            // Messages involving this user
            const messages = await ctx.wo_userschat.findAll({
                where: {
                    [Op.or]: [
                        { user_id: userId },
                        { to_id: userId },
                    ],
                },
                limit: 5000,
                order: [['id', 'DESC']],
                raw: true,
            });

            // Groups the user belongs to
            const groupMemberships = await ctx.wo_groupchatusers.findAll({
                where: { user_id: userId },
                raw: true,
            });
            const groupIds = groupMemberships.map(m => m.group_id);
            const groups   = groupIds.length
                ? await ctx.wo_groupchat.findAll({ where: { id: { [Op.in]: groupIds } }, raw: true })
                : [];

            // User profile
            const user = await ctx.wo_users.findOne({
                where: { user_id: userId },
                attributes: ['user_id', 'username', 'first_name', 'last_name', 'email', 'avatar', 'about'],
                raw: true,
            });

            const now = Math.floor(Date.now() / 1000);
            const exportData = {
                manifest: {
                    version:        '1.0',
                    created_at:     now,
                    user_id:        userId,
                    app_version:    '1.0',
                    encryption:     'none',
                    total_size:     0,
                    total_messages: messages.length,
                    total_groups:   groups.length,
                },
                user:     user || {},
                messages: messages,
                contacts: [],
                groups:   groups,
                channels: [],
                settings: {},
                blocked_users: [],
            };

            const backupJson = JSON.stringify(exportData);
            const sizeBytes  = Buffer.byteLength(backupJson, 'utf8');

            console.log(`[Backup/export] user=${userId}: ${messages.length} msgs, ${groups.length} groups`);

            return res.json({
                api_status:  200,
                message:     'Export successful',
                backup_file: `backup_${userId}_${now}.json`,
                backup_url:  '',
                backup_size: sizeBytes,
                export_data: exportData,
            });

        } catch (err) {
            console.error('[Backup/export]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── GET /api/node/backup/list ───────────────────────────────────────────────
// Returns server-side backup files for the user. Currently returns an empty
// list — backups live on the client's chosen cloud provider (Drive, Dropbox…).

function listBackups(ctx) {
    return async (req, res) => {
        return res.json({
            api_status:    200,
            backups:       [],
            total_backups: 0,
        });
    };
}

// ─── POST /api/node/backup/import ────────────────────────────────────────────
// Accepts a JSON backup and restores chat messages for the user.
// Only imports messages where the current user is sender or recipient.

function importUserData(ctx) {
    return async (req, res) => {
        const userId = req.userId;
        try {
            const backupJson = req.body.backup_data;
            if (!backupJson) {
                return res.json({ api_status: 400, error_message: 'backup_data is required' });
            }

            let backup;
            try { backup = JSON.parse(backupJson); }
            catch { return res.json({ api_status: 400, error_message: 'Invalid backup JSON' }); }

            const messages = Array.isArray(backup.messages) ? backup.messages : [];
            let importedMessages = 0;

            for (const msg of messages) {
                // Only import messages that belong to this user
                if (String(msg.user_id) !== String(userId) && String(msg.to_id) !== String(userId)) continue;
                try {
                    await ctx.wo_userschat.findOrCreate({
                        where: { id: msg.id },
                        defaults: msg,
                    });
                    importedMessages++;
                } catch (e) {
                    // Skip duplicates / invalid rows
                }
            }

            console.log(`[Backup/import] user=${userId}: imported ${importedMessages} messages`);

            return res.json({
                api_status: 200,
                message:    'Import successful',
                imported: {
                    messages: importedMessages,
                    groups:   0,
                    channels: 0,
                    settings: false,
                },
            });

        } catch (err) {
            console.error('[Backup/import]', err.message);
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
    app.get ('/api/node/backup/export',     auth, exportUserData(ctx));
    app.get ('/api/node/backup/list',       auth, listBackups(ctx));
    app.post('/api/node/backup/import',     auth, importUserData(ctx));
    // Media download / auto-download settings
    app.post('/api/node/media-settings/get',    auth, getMediaSettings(ctx));
    app.post('/api/node/media-settings/update', auth, updateMediaSettings(ctx));
}

module.exports = { registerBackupRoutes };
