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

const path         = require('path');
const fs           = require('fs');
const multer       = require('multer');
const funcs        = require('../../functions/functions');
const management   = require('./management');
const subscriptions = require('./subscriptions');
const posts        = require('./posts');
const comments     = require('./comments');
const reactions    = require('./reactions');
const admin        = require('./admin');

// ─── Upload base path from config ───────────────────────────────────────────
// Use site_path from config.json (absolute filesystem path to web root).
// Falls back to __dirname-relative path if site_path is not set.
const configFile = require('../../config.json');
const SITE_ROOT = configFile.site_path || path.resolve(__dirname, '../../../..');
const CHANNELS_UPLOAD_DIR = path.join(SITE_ROOT, 'upload', 'photos', 'channels');
const CHANNELS_MEDIA_DIR  = path.join(SITE_ROOT, 'upload', 'photos', 'channels', 'media');

console.log('[Channels] Upload dirs:');
console.log('  avatar :', CHANNELS_UPLOAD_DIR);
console.log('  media  :', CHANNELS_MEDIA_DIR);

// ─── multer for avatar upload ───────────────────────────────────────────────
const upload = multer({
    storage: multer.diskStorage({
        destination: function (req, file, cb) {
            if (!fs.existsSync(CHANNELS_UPLOAD_DIR)) fs.mkdirSync(CHANNELS_UPLOAD_DIR, { recursive: true });
            cb(null, CHANNELS_UPLOAD_DIR);
        },
        filename: function (req, file, cb) {
            const ext = path.extname(file.originalname) || '.jpg';
            cb(null, 'ch_avatar_' + Date.now() + ext);
        }
    }),
    limits: { fileSize: 10 * 1024 * 1024 }, // 10MB
    fileFilter: (req, file, cb) => {
        if (file.mimetype.startsWith('image/')) cb(null, true);
        else cb(new Error('Only image files allowed'), false);
    }
});

// ─── multer for generic media upload (image/video/audio/file) ───────────────
const mediaUpload = multer({
    storage: multer.diskStorage({
        destination: function (req, file, cb) {
            if (!fs.existsSync(CHANNELS_MEDIA_DIR)) fs.mkdirSync(CHANNELS_MEDIA_DIR, { recursive: true });
            cb(null, CHANNELS_MEDIA_DIR);
        },
        filename: function (req, file, cb) {
            const ext = path.extname(file.originalname) || '';
            cb(null, 'ch_media_' + Date.now() + '_' + Math.floor(Math.random() * 100000) + ext);
        }
    }),
    limits: { fileSize: 100 * 1024 * 1024 } // 100MB
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

function uploadMedia(ctx) {
    return async (req, res) => {
        try {
            if (!req.file) {
                return res.json({
                    api_status: 400,
                    url: null,
                    error_code: 400,
                    error_message: 'File is required'
                });
            }

            const mediaType = (req.body.media_type || '').toLowerCase();
            const allowed = ['image', 'video', 'audio', 'file'];
            if (!allowed.includes(mediaType)) {
                return res.json({
                    api_status: 400,
                    url: null,
                    error_code: 400,
                    error_message: 'Invalid media_type'
                });
            }

            const relativePath = 'upload/photos/channels/media/' + req.file.filename;
            const fileUrl = await funcs.Wo_GetMedia(ctx, relativePath);

            return res.json({
                api_status: 200,
                url: fileUrl,
                error_code: null,
                error_message: null
            });
        } catch (err) {
            console.error('[Channels/uploadMedia]', err.message);
            return res.json({
                api_status: 500,
                url: null,
                error_code: 500,
                error_message: 'Server error'
            });
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

    // Node REST compatibility for Android client (/api/node/channel/*)
    // NOTE: list endpoint keeps dispatcher because client sends type=get_list/get_subscribed/search.
    app.post('/api/node/channel/list',            auth, channelsDispatcher(ctx, io));
    app.post('/api/node/channel/details',         auth, management.getDetails(ctx, io));
    app.post('/api/node/channel/create',          auth, management.createChannel(ctx, io));
    app.post('/api/node/channel/update',          auth, management.updateChannel(ctx, io));
    app.post('/api/node/channel/delete',          auth, management.deleteChannel(ctx, io));
    app.post('/api/node/channel/subscribe',       auth, subscriptions.subscribe(ctx, io));
    app.post('/api/node/channel/unsubscribe',     auth, subscriptions.unsubscribe(ctx, io));
    app.post('/api/node/channel/add-member',      auth, subscriptions.addMember(ctx, io));

    app.post('/api/node/channel/posts',           auth, posts.getPosts(ctx, io));
    app.post('/api/node/channel/create-post',     auth, posts.createPost(ctx, io));
    app.post('/api/node/channel/update-post',     auth, posts.updatePost(ctx, io));
    app.post('/api/node/channel/delete-post',     auth, posts.deletePost(ctx, io));
    app.post('/api/node/channel/pin-post',        auth, posts.pinPost(ctx, io));
    app.post('/api/node/channel/unpin-post',      auth, posts.unpinPost(ctx, io));

    app.post('/api/node/channel/comments',        auth, comments.getComments(ctx, io));
    app.post('/api/node/channel/add-comment',     auth, comments.addComment(ctx, io));
    app.post('/api/node/channel/delete-comment',  auth, comments.deleteComment(ctx, io));
    app.post('/api/node/channel/comment-reaction',auth, comments.addCommentReaction(ctx, io));

    app.post('/api/node/channel/post-reaction',   auth, reactions.addReaction(ctx, io));
    app.post('/api/node/channel/post-unreaction', auth, reactions.removeReaction(ctx, io));
    app.post('/api/node/channel/post-view',       auth, posts.registerView(ctx, io));

    app.post('/api/node/channel/add-admin',       auth, admin.addAdmin(ctx, io));
    app.post('/api/node/channel/remove-admin',    auth, admin.removeAdmin(ctx, io));
    app.post('/api/node/channel/settings',        auth, admin.updateSettings(ctx, io));
    app.post('/api/node/channel/statistics',      auth, admin.getStatistics(ctx, io));
    app.post('/api/node/channel/subscribers',     auth, subscriptions.getSubscribers(ctx, io));
    app.post('/api/node/channel/mute',            auth, admin.muteChannel(ctx, io));
    app.post('/api/node/channel/unmute',          auth, admin.unmuteChannel(ctx, io));
    app.post('/api/node/channel/qr-generate',     auth, admin.generateQr(ctx, io));
    app.post('/api/node/channel/qr-subscribe',    auth, admin.subscribeByQr(ctx, io));
    app.post('/api/node/channel/upload-avatar',   upload.single('avatar'), auth, admin.uploadAvatar(ctx, io));

    // Generic media upload (for channel post images/videos/files)
    app.post('/api/node/media/upload',             mediaUpload.single('file'), auth, uploadMedia(ctx));

    console.log('[Channel API] Endpoints registered:');
    console.log('  Main    : POST /api/v2/channels.php');
    console.log('  QR      : generate_channel_qr, subscribe_channel_by_qr');
    console.log('  Mute    : mute_channel, unmute_channel');
    console.log('  Upload  : upload_channel_avatar');
    console.log('  Media   : /api/node/media/upload (+aliases)');
}

module.exports = { registerChannelRoutes };
