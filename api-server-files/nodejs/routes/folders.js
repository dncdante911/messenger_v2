'use strict';

/**
 * Shared Chat Folders API
 *
 * Folders are synced server-side (not SharedPreferences) and can optionally
 * be shared with others via an invite link (like Telegram folder sharing).
 *
 * Endpoints:
 *   GET  /api/node/folders/list           – get all folders for current user
 *   POST /api/node/folders/create         – create a new folder
 *   POST /api/node/folders/update/:id     – rename/recolor/re-emoji folder
 *   DELETE /api/node/folders/:id          – delete folder
 *   POST /api/node/folders/:id/share      – generate/toggle share link
 *   POST /api/node/folders/join/:code     – join a shared folder via invite code
 *   POST /api/node/folders/:id/leave      – leave a shared folder
 *   POST /api/node/folders/:id/add-chat   – add a chat to folder
 *   POST /api/node/folders/:id/remove-chat – remove a chat from folder
 *   GET  /api/node/folders/:id/chats      – get chats in folder
 *   POST /api/node/folders/reorder        – reorder folders
 */

const crypto = require('crypto');
const { Op }  = require('sequelize');

const MAX_FOLDERS_FREE = 10;
const MAX_FOLDERS_PRO  = 50;

// ─── auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
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
}

// ─── helpers ─────────────────────────────────────────────────────────────────

async function isPremium(ctx, userId) {
    const u = await ctx.wo_users.findOne({ where: { user_id: userId }, attributes: ['is_pro'], raw: true }).catch(() => null);
    return u?.is_pro > 0;
}

async function canAccessFolder(ctx, userId, folderId) {
    // Owner OR member of shared folder
    const folder = await ctx.wm_chat_folders.findOne({ where: { id: folderId }, raw: true });
    if (!folder) return null;
    if (folder.owner_id === userId) return folder;
    if (folder.is_shared) {
        const mem = await ctx.wm_chat_folder_members.findOne({ where: { folder_id: folderId, user_id: userId }, raw: true });
        if (mem) return folder;
    }
    return null;
}

async function formatFolder(ctx, folder, userId) {
    const items = await ctx.wm_chat_folder_items.findAll({
        where: { folder_id: folder.id },
        raw: true,
    });
    const memberCount = folder.is_shared
        ? await ctx.wm_chat_folder_members.count({ where: { folder_id: folder.id } })
        : 0;

    const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
    const shareUrl = folder.share_code
        ? `${siteUrl}/folder/join/${folder.share_code}`
        : null;

    return {
        id:           folder.id,
        name:         folder.name,
        emoji:        folder.emoji,
        color:        folder.color,
        position:     folder.position,
        is_owner:     folder.owner_id === userId,
        is_shared:    !!folder.is_shared,
        share_code:   folder.share_code || null,
        share_url:    shareUrl,
        member_count: memberCount,
        chat_count:   items.length,
        created_at:   folder.created_at,
    };
}

// ─── GET /api/node/folders/list ──────────────────────────────────────────────

async function listFolders(ctx, req, res) {
    try {
        const userId = req.userId;

        // Own folders
        const ownFolders = await ctx.wm_chat_folders.findAll({
            where: { owner_id: userId },
            order: [['position', 'ASC']],
            raw:   true,
        });

        // Joined shared folders
        const memberships = await ctx.wm_chat_folder_members.findAll({
            where: { user_id: userId },
            raw:   true,
        });
        const sharedIds = memberships.map(m => m.folder_id);
        const sharedFolders = sharedIds.length
            ? await ctx.wm_chat_folders.findAll({ where: { id: { [Op.in]: sharedIds } }, raw: true })
            : [];

        const allFolders = [...ownFolders, ...sharedFolders];
        const formatted = await Promise.all(allFolders.map(f => formatFolder(ctx, f, userId)));

        res.json({ api_status: 200, folders: formatted });
    } catch (err) {
        console.error('[Folders/list]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/create ───────────────────────────────────────────

async function createFolder(ctx, req, res) {
    try {
        const userId = req.userId;
        const name   = (req.body.name  || '').trim().slice(0, 100);
        const emoji  = (req.body.emoji || '📁').slice(0, 10);
        const color  = (req.body.color || '#2196F3').replace(/[^#a-fA-F0-9]/g, '').slice(0, 7);

        if (!name) return res.status(400).json({ api_status: 400, error_message: 'name required' });

        const pro   = await isPremium(ctx, userId);
        const max   = pro ? MAX_FOLDERS_PRO : MAX_FOLDERS_FREE;
        const count = await ctx.wm_chat_folders.count({ where: { owner_id: userId } });

        if (count >= max)
            return res.status(403).json({
                api_status: 403,
                error_message: `Folder limit reached (${max}). ${pro ? '' : 'Upgrade to Premium for up to 50 folders.'}`,
                limit: max,
            });

        const maxPos = await ctx.wm_chat_folders.max('position', { where: { owner_id: userId } }) || 0;
        const folder = await ctx.wm_chat_folders.create({
            owner_id:   userId,
            name,
            emoji,
            color,
            position:   maxPos + 1,
            is_shared:  0,
            share_code: null,
            created_at: Math.floor(Date.now() / 1000),
        });

        res.json({ api_status: 200, folder: await formatFolder(ctx, folder.toJSON(), userId) });
    } catch (err) {
        console.error('[Folders/create]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/update/:id ───────────────────────────────────────

async function updateFolder(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);

        const folder = await ctx.wm_chat_folders.findOne({ where: { id: folderId, owner_id: userId } });
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found or not owner' });

        const fields = {};
        if (req.body.name  !== undefined) fields.name  = req.body.name.trim().slice(0, 100);
        if (req.body.emoji !== undefined) fields.emoji  = req.body.emoji.slice(0, 10);
        if (req.body.color !== undefined) fields.color  = req.body.color.replace(/[^#a-fA-F0-9]/g, '').slice(0, 7);

        await folder.update(fields);
        res.json({ api_status: 200, folder: await formatFolder(ctx, folder.toJSON(), userId) });
    } catch (err) {
        console.error('[Folders/update]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── DELETE /api/node/folders/:id ────────────────────────────────────────────

async function deleteFolder(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);

        const folder = await ctx.wm_chat_folders.findOne({ where: { id: folderId, owner_id: userId } });
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found or not owner' });

        // Remove all items and members
        await ctx.wm_chat_folder_items.destroy({ where: { folder_id: folderId } });
        await ctx.wm_chat_folder_members.destroy({ where: { folder_id: folderId } });
        await folder.destroy();

        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Folders/delete]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/:id/share ────────────────────────────────────────

async function shareFolder(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);
        const enable   = req.body.enable !== false && req.body.enable !== 'false';

        const folder = await ctx.wm_chat_folders.findOne({ where: { id: folderId, owner_id: userId } });
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found' });

        if (enable) {
            const shareCode = folder.share_code || crypto.randomBytes(12).toString('hex');
            await folder.update({ is_shared: 1, share_code: shareCode });

            const siteUrl = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
            res.json({
                api_status:  200,
                share_code:  shareCode,
                share_url:   `${siteUrl}/folder/join/${shareCode}`,
            });
        } else {
            await folder.update({ is_shared: 0, share_code: null });
            // Remove all joined members (leave only owner)
            await ctx.wm_chat_folder_members.destroy({ where: { folder_id: folderId } });
            res.json({ api_status: 200, is_shared: false });
        }
    } catch (err) {
        console.error('[Folders/share]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/join/:code ───────────────────────────────────────

async function joinFolder(ctx, req, res) {
    try {
        const userId    = req.userId;
        const shareCode = req.params.code;

        const folder = await ctx.wm_chat_folders.findOne({ where: { share_code: shareCode, is_shared: 1 }, raw: true });
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Invite link is invalid or expired' });

        if (folder.owner_id === userId) return res.json({ api_status: 200, message: 'You are the owner', folder: await formatFolder(ctx, folder, userId) });

        // Upsert membership
        const [, created] = await ctx.wm_chat_folder_members.findOrCreate({
            where:    { folder_id: folder.id, user_id: userId },
            defaults: { joined_at: Math.floor(Date.now() / 1000) },
        });

        res.json({
            api_status: 200,
            already_member: !created,
            folder: await formatFolder(ctx, folder, userId),
        });
    } catch (err) {
        console.error('[Folders/join]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/:id/leave ────────────────────────────────────────

async function leaveFolder(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);

        await ctx.wm_chat_folder_members.destroy({ where: { folder_id: folderId, user_id: userId } });
        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Folders/leave]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/:id/add-chat ─────────────────────────────────────

async function addChat(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);
        const chatType = req.body.chat_type || 'dm'; // dm | group | channel
        const chatId   = parseInt(req.body.chat_id);

        if (!chatId) return res.status(400).json({ api_status: 400, error_message: 'chat_id required' });

        const folder = await canAccessFolder(ctx, userId, folderId);
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found' });

        await ctx.wm_chat_folder_items.findOrCreate({
            where:    { folder_id: folderId, chat_type: chatType, chat_id: chatId },
            defaults: { added_by: userId, added_at: Math.floor(Date.now() / 1000) },
        });

        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Folders/add-chat]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/:id/remove-chat ──────────────────────────────────

async function removeChat(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);
        const chatType = req.body.chat_type || 'dm';
        const chatId   = parseInt(req.body.chat_id);

        const folder = await canAccessFolder(ctx, userId, folderId);
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found' });

        await ctx.wm_chat_folder_items.destroy({ where: { folder_id: folderId, chat_type: chatType, chat_id: chatId } });
        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Folders/remove-chat]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── GET /api/node/folders/:id/chats ─────────────────────────────────────────

async function getFolderChats(ctx, req, res) {
    try {
        const userId   = req.userId;
        const folderId = parseInt(req.params.id);

        const folder = await canAccessFolder(ctx, userId, folderId);
        if (!folder) return res.status(404).json({ api_status: 404, error_message: 'Folder not found' });

        const items = await ctx.wm_chat_folder_items.findAll({
            where: { folder_id: folderId },
            raw:   true,
        });

        res.json({ api_status: 200, chats: items.map(i => ({ chat_type: i.chat_type, chat_id: i.chat_id })) });
    } catch (err) {
        console.error('[Folders/chats]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── POST /api/node/folders/reorder ──────────────────────────────────────────

async function reorderFolders(ctx, req, res) {
    try {
        const userId  = req.userId;
        const ids     = Array.isArray(req.body.ids) ? req.body.ids.map(Number) : [];
        if (!ids.length) return res.status(400).json({ api_status: 400, error_message: 'ids array required' });

        for (let i = 0; i < ids.length; i++) {
            await ctx.wm_chat_folders.update(
                { position: i },
                { where: { id: ids[i], owner_id: userId } }
            );
        }
        res.json({ api_status: 200 });
    } catch (err) {
        console.error('[Folders/reorder]', err.message);
        res.status(500).json({ api_status: 500, error_message: err.message });
    }
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerFolderRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.get('/api/node/folders/list',               auth, (req, res) => listFolders(ctx, req, res));
    app.post('/api/node/folders/create',            auth, (req, res) => createFolder(ctx, req, res));
    app.post('/api/node/folders/reorder',           auth, (req, res) => reorderFolders(ctx, req, res));
    app.post('/api/node/folders/update/:id',        auth, (req, res) => updateFolder(ctx, req, res));
    app.delete('/api/node/folders/:id',             auth, (req, res) => deleteFolder(ctx, req, res));
    app.post('/api/node/folders/:id/share',         auth, (req, res) => shareFolder(ctx, req, res));
    app.post('/api/node/folders/join/:code',        auth, (req, res) => joinFolder(ctx, req, res));
    app.post('/api/node/folders/:id/leave',         auth, (req, res) => leaveFolder(ctx, req, res));
    app.post('/api/node/folders/:id/add-chat',      auth, (req, res) => addChat(ctx, req, res));
    app.post('/api/node/folders/:id/remove-chat',   auth, (req, res) => removeChat(ctx, req, res));
    app.get('/api/node/folders/:id/chats',          auth, (req, res) => getFolderChats(ctx, req, res));

    console.log('[Folders API] Endpoints registered');
}

module.exports = { registerFolderRoutes };
