/**
 * Channels — Router Index
 *
 * Registers all /api/v2/channels.php endpoints (Node.js replacement for PHP).
 * Also registers separate endpoint paths:
 *   /api/v2/endpoints/generate_channel_qr.php
 *   /api/v2/endpoints/subscribe_channel_by_qr.php
 *   /api/v2/endpoints/mute_channel.php
 *   /api/v2/endpoints/unmute_channel.php
 *   /api/v2/endpoints/upload_channel_avatar.php
 *
 * Split into sub-modules:
 *   management.js    – CRUD, list, search, details
 *   subscriptions.js – subscribe, unsubscribe, members, subscribers
 *   posts.js         – posts CRUD, pin/unpin, views
 *   comments.js      – comments CRUD, comment reactions
 *   reactions.js     – post reactions add/remove
 *   admin.js         – admin management, settings, statistics, QR, mute
 *
 * Auth middleware: validates access_token query param or body field.
 */

'use strict';

const multer       = require('multer');
const management   = require('./management');
const subscriptions = require('./subscriptions');
const posts        = require('./posts');
const comments     = require('./comments');
const reactions    = require('./reactions');
const admin        = require('./admin');

// ─── multer for avatar upload ───────────────────────────────────────────────
const upload = multer({
    storage: multer.diskStorage({
        destination: function (req, file, cb) {
            const fs = require('fs');
            const dir = require('path').resolve(__dirname, '../../../../upload/photos/channels');
            if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
            cb(null, dir);
        },
        filename: function (req, file, cb) {
            const ext = require('path').extname(file.originalname) || '.jpg';
            cb(null, 'ch_avatar_' + Date.now() + ext);
        }
    }),
    limits: { fileSize: 10 * 1024 * 1024 }, // 10MB
    fileFilter: (req, file, cb) => {
        if (file.mimetype.startsWith('image/')) cb(null, true);
        else cb(new Error('Only image files allowed'), false);
    }
});

// ─── auth middleware ────────────────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.json({ api_status: 401, error_code: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.json({ api_status: 401, error_code: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Channels/auth]', err.message);
        res.json({ api_status: 500, error_code: 500, error_message: 'Authentication error' });
    }
}

// ─── unified channels.php dispatcher ────────────────────────────────────────
function channelsDispatcher(ctx, io) {
    return async (req, res) => {
        const type   = req.body.type   || req.query.type   || '';
        const action = req.body.action || req.query.action || '';

        try {
            // Dispatch by "action" field
            switch (action) {
                case 'create_channel':      return management.createChannel(ctx, io)(req, res);
                case 'update_channel':      return management.updateChannel(ctx, io)(req, res);
                case 'delete_channel':      return management.deleteChannel(ctx, io)(req, res);
                case 'subscribe_channel':   return subscriptions.subscribe(ctx, io)(req, res);
                case 'unsubscribe_channel': return subscriptions.unsubscribe(ctx, io)(req, res);
                case 'get_channel_posts':   return posts.getPosts(ctx, io)(req, res);
                case 'create_post':         return posts.createPost(ctx, io)(req, res);
                default: break;
            }

            // Dispatch by "type" field
            switch (type) {
                // Management
                case 'get_list':       return management.getList(ctx, io)(req, res);
                case 'get_subscribed': return management.getSubscribed(ctx, io)(req, res);
                case 'search':         return management.search(ctx, io)(req, res);
                case 'get_by_id':      return management.getDetails(ctx, io)(req, res);
                // Subscriptions
                case 'add_channel_member': return subscriptions.addMember(ctx, io)(req, res);
                case 'get_channel_subscribers': return subscriptions.getSubscribers(ctx, io)(req, res);
                // Posts
                case 'update_post':    return posts.updatePost(ctx, io)(req, res);
                case 'delete_post':    return posts.deletePost(ctx, io)(req, res);
                case 'pin_post':       return posts.pinPost(ctx, io)(req, res);
                case 'unpin_post':     return posts.unpinPost(ctx, io)(req, res);
                case 'register_post_view': return posts.registerView(ctx, io)(req, res);
                // Comments
                case 'get_comments':    return comments.getComments(ctx, io)(req, res);
                case 'add_comment':     return comments.addComment(ctx, io)(req, res);
                case 'delete_comment':  return comments.deleteComment(ctx, io)(req, res);
                case 'add_comment_reaction': return comments.addCommentReaction(ctx, io)(req, res);
                // Post reactions
                case 'add_post_reaction':    return reactions.addReaction(ctx, io)(req, res);
                case 'remove_post_reaction': return reactions.removeReaction(ctx, io)(req, res);
                // Admin
                case 'add_channel_admin':    return admin.addAdmin(ctx, io)(req, res);
                case 'remove_channel_admin': return admin.removeAdmin(ctx, io)(req, res);
                case 'update_settings':      return admin.updateSettings(ctx, io)(req, res);
                case 'get_channel_statistics': return admin.getStatistics(ctx, io)(req, res);
                default:
                    return res.json({
                        api_status: 404,
                        error_code: 1,
                        error_message: `Unknown channel action/type: action="${action}", type="${type}"`
                    });
            }
        } catch (err) {
            console.error('[Channels/dispatch]', err);
            res.json({ api_status: 500, error_code: 500, error_message: 'Internal server error' });
        }
    };
}

// ─── register routes ────────────────────────────────────────────────────────
function registerChannelRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // Main channels endpoint (replaces PHP channels.php)
    app.post('/api/v2/channels.php', auth, channelsDispatcher(ctx, io));

    // Separate endpoints (replaces PHP files in /api/v2/endpoints/)
    app.post('/api/v2/endpoints/generate_channel_qr.php',      auth, admin.generateQr(ctx, io));
    app.post('/api/v2/endpoints/subscribe_channel_by_qr.php',  auth, admin.subscribeByQr(ctx, io));
    app.post('/api/v2/endpoints/mute_channel.php',             auth, admin.muteChannel(ctx, io));
    app.post('/api/v2/endpoints/unmute_channel.php',           auth, admin.unmuteChannel(ctx, io));
    app.post('/api/v2/endpoints/upload_channel_avatar.php',    upload.single('avatar'), auth, admin.uploadAvatar(ctx, io));

    console.log('[Channel API] Endpoints registered:');
    console.log('  Main    : POST /api/v2/channels.php');
    console.log('  QR      : generate_channel_qr, subscribe_channel_by_qr');
    console.log('  Mute    : mute_channel, unmute_channel');
    console.log('  Upload  : upload_channel_avatar');
}

module.exports = { registerChannelRoutes };
