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

const msgs      = require('./messages');
const actions   = require('./actions');
const chatsList = require('./chats-list');
const favs      = require('./favorites');

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
    app.post('/api/node/chat/get',      auth, msgs.getMessages(ctx, io));
    app.post('/api/node/chat/send',     auth, msgs.sendMessage(ctx, io));
    app.post('/api/node/chat/loadmore', auth, msgs.loadMore(ctx, io));
    app.post('/api/node/chat/edit',     auth, msgs.editMessage(ctx, io));
    app.post('/api/node/chat/search',   auth, msgs.searchMessages(ctx, io));
    app.post('/api/node/chat/seen',     auth, msgs.seenMessages(ctx, io));
    app.post('/api/node/chat/typing',   auth, msgs.typing(ctx, io));

    // ── actions ─────────────────────────────────────────────────────────────
    app.post('/api/node/chat/delete',    auth, actions.deleteMessage(ctx, io));
    app.post('/api/node/chat/react',     auth, actions.reactMessage(ctx, io));
    app.post('/api/node/chat/pin',       auth, actions.pinMessage(ctx, io));
    app.post('/api/node/chat/pinned',    auth, actions.getPinnedMessages(ctx, io));
    app.post('/api/node/chat/forward',   auth, actions.forwardMessage(ctx, io));

    // ── chats list & settings ────────────────────────────────────────────────
    app.post('/api/node/chat/chats',                auth, chatsList.getChats(ctx, io));
    app.post('/api/node/chat/delete-conversation',  auth, chatsList.deleteConversation(ctx, io));
    app.post('/api/node/chat/archive',              auth, chatsList.archiveChat(ctx, io));
    app.post('/api/node/chat/mute',                 auth, chatsList.muteChat(ctx, io));
    app.post('/api/node/chat/pin-chat',             auth, chatsList.pinChat(ctx, io));
    app.post('/api/node/chat/color',                auth, chatsList.changeChatColor(ctx, io));
    app.post('/api/node/chat/read',                 auth, chatsList.readChats(ctx, io));

    // ── favorites ────────────────────────────────────────────────────────────
    app.post('/api/node/chat/fav',      auth, favs.favMessage(ctx, io));
    app.post('/api/node/chat/fav-list', auth, favs.getFavMessages(ctx, io));

    console.log('[Private Chat API] Endpoints registered under /api/node/chat/*');
    console.log('  Messages : get, send, loadmore, edit, search, seen, typing');
    console.log('  Actions  : delete, react, pin, pinned, forward');
    console.log('  Chats    : chats, delete-conversation, archive, mute, pin-chat, color, read');
    console.log('  Favorites: fav, fav-list');
}

module.exports = { registerPrivateChatRoutes };
