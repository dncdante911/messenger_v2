/**
 * Private Chats — Router Index
 *
 * Registers all /api/node/chat/* endpoints.
 * Split into sub-modules to keep each file under ~300 lines:
 *
 *   messages.js   – get, send, loadmore, edit, search, seen, typing
 *   actions.js    – delete, react, pin (message), forward
 *   chats-list.js – chats list, delete-conversation, archive, mute, pin-chat, color, read
 *   favorites.js  – fav, fav-list
 *
 * Auth middleware: validates access-token header and attaches req.userId.
 */

'use strict';

const msgs       = require('./messages');
const actions    = require('./actions');
const chatsList  = require('./chats-list');
const favs       = require('./favorites');
const saved      = require('./saved');
const secret     = require('./secret');
const exportChat = require('./export');
const mediaUp    = require('./media-upload');

// ─── auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Node/auth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── register routes ─────────────────────────────────────────────────────────

function registerPrivateChatRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // ── messages ────────────────────────────────────────────────────────────
    app.post('/api/node/chat/get',           auth, msgs.getMessages(ctx, io));
    app.post('/api/node/chat/send',          auth, msgs.sendMessage(ctx, io));
    app.post('/api/node/chat/loadmore',      auth, msgs.loadMore(ctx, io));
    app.post('/api/node/chat/edit',          auth, msgs.editMessage(ctx, io));
    app.post('/api/node/chat/search',        auth, msgs.searchMessages(ctx, io));
    app.post('/api/node/chat/seen',          auth, msgs.seenMessages(ctx, io));
    app.post('/api/node/chat/typing',        auth, msgs.typing(ctx, io));
    app.post('/api/node/chat/user-action',   auth, msgs.userAction(ctx, io));
    app.post('/api/node/chat/notify-media',  auth, msgs.notifyMediaMessage(ctx, io));
    app.post('/api/node/chat/send-media',    auth, msgs.sendMediaMessage(ctx, io));
    app.post('/api/node/chat/count',         auth, msgs.countMessages(ctx));

    // ── actions ─────────────────────────────────────────────────────────────
    app.post('/api/node/chat/delete',    auth, actions.deleteMessage(ctx, io));
    app.post('/api/node/chat/react',     auth, actions.reactMessage(ctx, io));
    app.post('/api/node/chat/pin',       auth, actions.pinMessage(ctx, io));
    app.post('/api/node/chat/pinned',    auth, actions.getPinnedMessages(ctx, io));
    app.post('/api/node/chat/forward',   auth, actions.forwardMessage(ctx, io));

    // ── chats list & settings ────────────────────────────────────────────────
    app.post('/api/node/chat/chats',                auth, chatsList.getChats(ctx, io));
    app.post('/api/node/chat/business-chats',  auth, chatsList.getBusinessChats(ctx, io));
    app.post('/api/node/chat/business-inbox',  auth, chatsList.getBusinessChats(ctx, io)); // legacy alias
    app.post('/api/node/chat/delete-conversation',  auth, chatsList.deleteConversation(ctx, io));
    app.post('/api/node/chat/clear-history',        auth, chatsList.clearHistory(ctx, io));
    app.post('/api/node/chat/mute-status',          auth, chatsList.getMuteStatus(ctx, io));
    app.post('/api/node/chat/archive',              auth, chatsList.archiveChat(ctx, io));
    app.post('/api/node/chat/mute',                 auth, chatsList.muteChat(ctx, io));
    app.post('/api/node/chat/pin-chat',             auth, chatsList.pinChat(ctx, io));
    app.post('/api/node/chat/color',                auth, chatsList.changeChatColor(ctx, io));
    app.post('/api/node/chat/read',                 auth, chatsList.readChats(ctx, io));

    // ── archive count ─────────────────────────────────────────────────────────
    app.get('/api/node/chat/archive/count', auth, chatsList.archivedCount(ctx));

    // ── hide / unhide chat ────────────────────────────────────────────────────
    app.post('/api/node/chat/hide',          auth, chatsList.hideChat(ctx, io));
    app.get( '/api/node/chat/hidden/count',  auth, chatsList.hiddenCount(ctx));

    // ── favorites ────────────────────────────────────────────────────────────
    app.post('/api/node/chat/fav',      auth, favs.favMessage(ctx, io));
    app.post('/api/node/chat/fav-list', auth, favs.getFavMessages(ctx, io));

    // ── saved messages (server-side bookmarks) ────────────────────────────────
    app.post('/api/node/chat/saved/save',   auth, saved.saveMessage(ctx));
    app.post('/api/node/chat/saved/unsave', auth, saved.unsaveMessage(ctx));
    app.post('/api/node/chat/saved/list',   auth, saved.listSaved(ctx));
    app.post('/api/node/chat/saved/clear',  auth, saved.clearSaved(ctx));

    // ── export ────────────────────────────────────────────────────────────────
    app.post('/api/node/chat/export',  auth, exportChat.exportChat(ctx, io));

    // ── user presence ─────────────────────────────────────────────────────────
    // Returns current online status + last_seen for any user.
    // Called by Android MessagesViewModel on chat open to initialise header bar.
    app.post('/api/node/user/status',   auth, msgs.userStatus(ctx, io));

    // ── secret messages / self-destruct ────────────────────────────────────────
    app.post('/api/node/chat/secret/cleanup',     auth, secret.cleanupHandler(ctx, io));
    app.post('/api/node/chat/secret/set-timer',   auth, secret.setTimerHandler(ctx, io));
    app.get( '/api/node/chat/secret/timer/:userId', auth, secret.getTimerHandler(ctx));

    // ── media auto-delete setting ─────────────────────────────────────────────
    // GET  /api/node/chat/media-auto-delete-setting?chat_id=X  – fetch current setting
    // POST /api/node/chat/media-auto-delete-setting            – update setting
    app.get( '/api/node/chat/media-auto-delete-setting', auth, actions.getMediaAutoDeleteSetting(ctx));
    app.post('/api/node/chat/media-auto-delete-setting', auth, actions.setMediaAutoDeleteSetting(ctx));

    // ── media upload (replaces PHP /xhr/upload_*.php) ─────────────────────────
    // POST /api/node/chat/upload  body: { file (multipart), type: image|video|audio|voice|file }
    app.post('/api/node/chat/upload', auth, mediaUp.uploadChatMedia(ctx));

    console.log('[Private Chat API] Endpoints registered under /api/node/chat/* and /api/node/user/*');
    console.log('  Messages : get, send, send-media, loadmore, edit, search, seen, typing, notify-media');
    console.log('  Actions  : delete, react, pin, pinned, forward');
    console.log('  Chats    : chats, business-chats, delete-conversation, clear-history, mute-status, archive, archive/count, mute, pin-chat, color, read');
    console.log('  Favorites: fav, fav-list');
    console.log('  Saved    : saved/save, saved/unsave, saved/list, saved/clear');
    console.log('  User     : /api/node/user/status');
    console.log('  Secret   : secret/cleanup, secret/set-timer, secret/timer/:userId');
    console.log('  Media    : media-auto-delete-setting (GET/POST)');
}

module.exports = { registerPrivateChatRoutes };
