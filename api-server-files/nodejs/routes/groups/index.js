/**
 * Group Chats — Router Index
 *
 * Registers all /api/node/group/* endpoints.
 *
 * Sub-modules:
 *   management.js – CRUD (list, details, create, update, delete, leave, search)
 *   members.js    – get members, add, remove, set-role, join, join requests
 *   messages.js   – get, send, loadmore, edit, delete, pin, unpin, search, seen, typing
 *   admin.js      – avatar upload, settings, mute/unmute, QR, statistics, add/remove admin
 *
 * Auth: validates `access-token` header (same as channel routes).
 */

'use strict';

const path       = require('path');
const fs         = require('fs');
const multer     = require('multer');
const management = require('./management');
const members    = require('./members');
const messages   = require('./messages');
const admin      = require('./admin');

// ─── Upload directory for group avatars ─────────────────────────────────────
const configFile       = require('../../config.json');
const SITE_ROOT        = configFile.site_path || path.resolve(__dirname, '../../../..');
const GROUPS_UPLOAD_DIR = path.join(SITE_ROOT, 'upload', 'photos', 'groups');

// ─── multer for avatar upload ────────────────────────────────────────────────
const upload = multer({
    storage: multer.diskStorage({
        destination: function (req, file, cb) {
            if (!fs.existsSync(GROUPS_UPLOAD_DIR)) fs.mkdirSync(GROUPS_UPLOAD_DIR, { recursive: true });
            cb(null, GROUPS_UPLOAD_DIR);
        },
        filename: function (req, file, cb) {
            const ext = path.extname(file.originalname) || '.jpg';
            cb(null, 'grp_avatar_' + Date.now() + ext);
        },
    }),
    limits: { fileSize: 10 * 1024 * 1024 }, // 10 MB
    fileFilter: (req, file, cb) => {
        if (file.mimetype.startsWith('image/')) cb(null, true);
        else cb(new Error('Only image files allowed'), false);
    },
});

// ─── auth middleware ─────────────────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.json({ api_status: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Groups/auth]', err.message);
        res.json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── register routes ─────────────────────────────────────────────────────────
function registerGroupRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // ── Management ──────────────────────────────────────────────────────────
    app.post('/api/node/group/list',    auth, management.list(ctx, io));
    app.post('/api/node/group/details', auth, management.details(ctx, io));
    app.post('/api/node/group/create',  auth, management.create(ctx, io));
    app.post('/api/node/group/update',  auth, management.update(ctx, io));
    app.post('/api/node/group/delete',  auth, management.deleteGroup(ctx, io));
    app.post('/api/node/group/leave',   auth, management.leave(ctx, io));
    app.post('/api/node/group/search',  auth, management.search(ctx, io));

    // ── Members ─────────────────────────────────────────────────────────────
    app.post('/api/node/group/members',        auth, members.getMembers(ctx, io));
    app.post('/api/node/group/add-member',     auth, members.addMember(ctx, io));
    app.post('/api/node/group/remove-member',  auth, members.removeMember(ctx, io));
    app.post('/api/node/group/set-role',       auth, members.setRole(ctx, io));
    app.post('/api/node/group/join',           auth, members.join(ctx, io));
    app.post('/api/node/group/request-join',   auth, members.requestJoin(ctx, io));
    app.post('/api/node/group/join-requests',  auth, members.getJoinRequests(ctx, io));
    app.post('/api/node/group/approve-join',   auth, members.approveJoinRequest(ctx, io));
    app.post('/api/node/group/reject-join',    auth, members.rejectJoinRequest(ctx, io));
    app.post('/api/node/group/ban-member',     auth, members.banMember(ctx, io));
    app.post('/api/node/group/unban-member',   auth, members.unbanMember(ctx, io));
    app.post('/api/node/group/mute-member',    auth, members.muteMember(ctx, io));
    app.post('/api/node/group/unmute-member',  auth, members.unmuteMember(ctx, io));

    // ── Messages ────────────────────────────────────────────────────────────
    app.post('/api/node/group/messages/get',      auth, messages.getMessages(ctx, io));
    app.post('/api/node/group/messages/send',     auth, messages.sendMessage(ctx, io));
    app.post('/api/node/group/messages/loadmore', auth, messages.loadMore(ctx, io));
    app.post('/api/node/group/messages/edit',     auth, messages.editMessage(ctx, io));
    app.post('/api/node/group/messages/delete',   auth, messages.deleteMessage(ctx, io));
    app.post('/api/node/group/messages/pin',      auth, messages.pinMessage(ctx, io));
    app.post('/api/node/group/messages/unpin',    auth, messages.unpinMessage(ctx, io));
    app.post('/api/node/group/messages/search',   auth, messages.searchMessages(ctx, io));
    app.post('/api/node/group/messages/seen',          auth, messages.seenMessages(ctx, io));
    app.post('/api/node/group/messages/typing',        auth, messages.typing(ctx, io));
    app.post('/api/node/group/messages/user-action',   auth, messages.groupUserAction(ctx, io));
    app.post('/api/node/group/messages/clear-self',    auth, messages.clearHistorySelf(ctx, io));
    app.post('/api/node/group/messages/clear-all',     auth, messages.clearHistoryAdmin(ctx, io));

    // ── Admin ───────────────────────────────────────────────────────────────
    app.post('/api/node/group/upload-avatar', upload.single('avatar'), auth, admin.uploadAvatar(ctx, io));
    app.post('/api/node/group/settings',      auth, admin.updateSettings(ctx, io));
    app.post('/api/node/group/mute',          auth, admin.muteGroup(ctx, io));
    app.post('/api/node/group/unmute',        auth, admin.unmuteGroup(ctx, io));
    app.post('/api/node/group/qr-generate',   auth, admin.generateQr(ctx, io));
    app.post('/api/node/group/qr-join',       auth, admin.joinByQr(ctx, io));
    app.post('/api/node/group/statistics',    auth, admin.getStatistics(ctx, io));
    app.post('/api/node/group/add-admin',     auth, admin.addGroupAdmin(ctx, io));
    app.post('/api/node/group/remove-admin',  auth, admin.removeGroupAdmin(ctx, io));

    console.log('[Group API] Endpoints registered on /api/node/group/*');
}

module.exports = { registerGroupRoutes };
