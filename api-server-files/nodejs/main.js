// Завантажуємо змінні оточення з .env до будь-яких require (крім вбудованих)
require('dotenv').config();

const moment = require("moment");
var fs = require('fs');
var express = require('express');
var app = express();
const path = require('path');

let ctx = {};

// var http = require('http').createServer(app);
// var io = require('socket.io')(http);
const configFile = require("./config.json")
const { Sequelize, Op, DataTypes } = require("sequelize");

// const notificationTemplate = Handlebars.compile(notification.toString());

const listeners = require('./listeners/listeners')
const turnHelper = require('./helpers/turn-credentials');
const { initializeBotNamespace, getBotStats } = require('./listeners/bots-listener')
const { registerAuthRoutes } = require('./routes/auth')
const { registerMessagingRoutes } = require('./routes/messaging')
const { registerPrivateChatRoutes } = require('./routes/private-chats/index')
const { registerStoryRoutes } = require('./routes/stories/index')
const { registerChannelRoutes } = require('./routes/channels/index')
const { registerGroupRoutes }   = require('./routes/groups/index')
const { registerBotRoutes }     = require('./routes/bots/index')
const { registerCallRoutes }    = require('./routes/calls')
const { initializeWallyBot }    = require('./bots/wallybot')
const { registerSignalRoutes }  = require('./routes/signal')
const { createRateLimiter }     = require('./helpers/rateLimiter')

let serverPort
let server
let io

async function loadConfig(ctx) {
  let config = await ctx.wo_config.findAll({ raw: true })
  for (let c of config) {
    ctx.globalconfig[c.name] = c.value
  }
  ctx.globalconfig["site_url"] = configFile.site_url
  ctx.globalconfig['theme_url'] = ctx.globalconfig["site_url"] + '/themes/' + ctx.globalconfig['theme']

  ctx.globalconfig["s3_site_url"]         = "https://test.s3.amazonaws.com";
  if (ctx.globalconfig["bucket_name"] && ctx.globalconfig["bucket_name"] != '') {
      ctx.globalconfig["s3_site_url"] = "https://"+ctx.globalconfig["bucket_name"]+".s3.amazonaws.com";
  }
  ctx.globalconfig["s3_site_url_2"]          = "https://test.s3.amazonaws.com";
  if (ctx.globalconfig["bucket_name_2"] && ctx.globalconfig["bucket_name_2"] != '') {
      ctx.globalconfig["s3_site_url_2"] = "https://"+ctx.globalconfig["bucket_name_2"]+".s3.amazonaws.com";
  }
  var endpoint_url = ctx.globalconfig['ftp_endpoint']; 
  ctx.globalconfig['ftp_endpoint'] = endpoint_url.replace('https://', '');

   // NOTE: socket.io-redis adapter intentionally disabled.
   // With a single Node.js process it provides no benefit, but it causes a
   // critical failure: when Redis disconnects and reconnects, all socket room
   // memberships stored in Redis are lost.  Subsequent io.to(room).emit()
   // calls find no sockets and messages/notifications are silently dropped.
   // PHP→Node.js message delivery still works via the separate Redis pub/sub
   // subscriber in listeners.js (sub.subscribe('messages', ...)).
   //
   // if (ctx.globalconfig["redis"] === "Y") {
   //   const redisAdapter = require('socket.io-redis');
   //   io.adapter(redisAdapter({ host: '127.0.0.1', port: ctx.globalconfig["redis_port"], auth_pass: process.env.REDIS_PASSWORD }));
   // }



  if (ctx.globalconfig["nodejs_ssl"] == 1) {
    var https = require('https');
    var options = {
      key: fs.readFileSync(path.resolve(__dirname, ctx.globalconfig["nodejs_key_path"])),
      cert: fs.readFileSync(path.resolve(__dirname, ctx.globalconfig["nodejs_cert_path"]))
    };
    serverPort = ctx.globalconfig["nodejs_ssl_port"];
    server = https.createServer(options, app);
  } else {
    serverPort = ctx.globalconfig["nodejs_port"];
    server = require('http').createServer(app);
  }

}


async function loadLangs(ctx) {
  let langs = await ctx.wo_langs.findAll({ raw: true })
  for (let c of langs) {
    ctx.globallangs[c.lang_key] = c.english
  }
}


async function init() {
  // Credentials: пріоритет .env → config.json (fallback)
  const dbHost = process.env.DB_HOST || configFile.sql_db_host;
  const dbUser = process.env.DB_USER || configFile.sql_db_user;
  const dbPass = process.env.DB_PASS || configFile.sql_db_pass;
  const dbName = process.env.DB_NAME || configFile.sql_db_name;

  var sequelize = new Sequelize(dbName, dbUser, dbPass, {
    host: dbHost,
    dialect: "mysql",
    logging: function () {},
    pool: {
        max: 20,
        min: 0,
        idle: 10000
    }
  });



  ctx.sequelize   = sequelize
  ctx.wo_messages = require("./models/wo_messages")(sequelize, DataTypes)
  ctx.wo_userschat = require("./models/wo_userschat")(sequelize, DataTypes)
  ctx.wo_users = require("./models/wo_users")(sequelize, DataTypes)
  ctx.wo_notification = require("./models/wo_notifications")(sequelize, DataTypes)
  ctx.wo_groupchat = require("./models/wo_groupchat")(sequelize, DataTypes)
  ctx.wo_groupchatusers = require("./models/wo_groupchatusers")(sequelize, DataTypes)
  ctx.wo_videocalls = require("./models/wo_videocalles")(sequelize, DataTypes)
  ctx.wo_audiocalls = require("./models/wo_audiocalls")(sequelize, DataTypes)
  ctx.wo_appssessions = require("./models/wo_appssessions")(sequelize, DataTypes)
  ctx.wo_langs = require("./models/wo_langs")(sequelize, DataTypes)
  ctx.wo_config = require("./models/wo_config")(sequelize, DataTypes)
  ctx.wo_blocks = require("./models/wo_blocks")(sequelize, DataTypes)
  ctx.wo_followers = require("./models/wo_followers")(sequelize, DataTypes)
  ctx.wo_hashtags = require("./models/wo_hashtags")(sequelize, DataTypes)
  ctx.wo_posts = require("./models/wo_posts")(sequelize, DataTypes)
  ctx.wo_comments = require("./models/wo_comments")(sequelize, DataTypes)
  ctx.wo_comment_replies = require("./models/wo_comment_replies")(sequelize, DataTypes)
  ctx.wo_pages = require("./models/wo_pages")(sequelize, DataTypes)
  ctx.wo_groups = require("./models/wo_groups")(sequelize, DataTypes)
  ctx.wo_events = require("./models/wo_events")(sequelize, DataTypes)
  ctx.wo_userstory = require("./models/wo_userstory")(sequelize, DataTypes)
  ctx.wo_userstorymedia = require("./models/wo_userstorymedia")(sequelize, DataTypes)
  ctx.wo_storyreactions = require("./models/wo_storyreactions")(sequelize, DataTypes)
  ctx.wo_storycomments = require("./models/wo_storycomments")(sequelize, DataTypes)
  ctx.wo_story_seen = require("./models/wo_story_seen")(sequelize, DataTypes)
  ctx.wo_mute_story = require("./models/wo_mute_story")(sequelize, DataTypes)
  ctx.wo_reactions_types = require("./models/wo_reactions_types")(sequelize, DataTypes)
  ctx.wo_reactions = require("./models/wo_reactions")(sequelize, DataTypes)
  ctx.wo_blog_reaction = require("./models/wo_blog_reaction")(sequelize, DataTypes)
  ctx.wo_mute = require("./models/wo_mute")(sequelize, DataTypes)
  ctx.wo_calls = require("./models/wo_calls")(sequelize, DataTypes)
  ctx.wo_group_calls = require("./models/wo_group_calls")(sequelize, DataTypes)
  ctx.wo_group_call_participants = require("./models/wo_group_call_participants")(sequelize, DataTypes)
  ctx.wo_ice_candidates = require("./models/wo_ice_candidates")(sequelize, DataTypes)
  ctx.wo_call_statistics = require("./models/wo_call_statistics")(sequelize, DataTypes)

  // ==================== Group Chat Admin Models ====================
  ctx.wo_groupadmins = require("./models/wo_groupadmins")(sequelize, DataTypes)

  // ==================== Channel API Models ====================
  ctx.wo_pages_likes = require("./models/wo_pages_likes")(sequelize, DataTypes)
  ctx.wo_pageadmins = require("./models/wo_pageadmins")(sequelize, DataTypes)
  ctx.wo_pinnedposts = require("./models/wo_pinnedposts")(sequelize, DataTypes)
  ctx.wo_pages_invites = require("./models/wo_pages_invites")(sequelize, DataTypes)
  ctx.wo_channel_bans = require("./models/wo_channel_bans")(sequelize, DataTypes)

  // ==================== Bot API Models ====================
  ctx.wo_bots = require("./models/wo_bots")(sequelize, DataTypes)
  ctx.wo_bot_commands = require("./models/wo_bot_commands")(sequelize, DataTypes)
  ctx.wo_bot_messages = require("./models/wo_bot_messages")(sequelize, DataTypes)
  ctx.wo_bot_users = require("./models/wo_bot_users")(sequelize, DataTypes)
  ctx.wo_bot_callbacks = require("./models/wo_bot_callbacks")(sequelize, DataTypes)
  ctx.wo_bot_polls = require("./models/wo_bot_polls")(sequelize, DataTypes)
  ctx.wo_bot_poll_options = require("./models/wo_bot_poll_options")(sequelize, DataTypes)
  ctx.wo_bot_poll_votes = require("./models/wo_bot_poll_votes")(sequelize, DataTypes)
  ctx.wo_bot_webhook_log = require("./models/wo_bot_webhook_log")(sequelize, DataTypes)
  ctx.wo_bot_keyboards = require("./models/wo_bot_keyboards")(sequelize, DataTypes)
  ctx.wo_bot_tasks = require("./models/wo_bot_tasks")(sequelize, DataTypes)
  ctx.wo_bot_rss_feeds = require("./models/wo_bot_rss_feeds")(sequelize, DataTypes)
  ctx.wo_bot_rss_items = require("./models/wo_bot_rss_items")(sequelize, DataTypes)
  ctx.wo_bot_rate_limits = require("./models/wo_bot_rate_limits")(sequelize, DataTypes)
  ctx.wo_bot_api_keys = require("./models/wo_bot_api_keys")(sequelize, DataTypes)

  // ==================== Signal Protocol Models ====================
  ctx.signal_keys              = require("./models/signal_keys")(sequelize, DataTypes)
  // Signal Sender Key Distribution для групових E2EE чатів
  ctx.signal_group_sender_keys = require("./models/signal_group_sender_keys")(sequelize, DataTypes)

  ctx.globalconfig = {}
  ctx.globallangs = {}
  ctx.socketIdUserHash = {}
  ctx.userHashUserId = {}
  ctx.userIdCount = {}
  ctx.userIdChatOpen = {}
  ctx.userIdSocket = {}  // ✅ ВИПРАВЛЕНО: Має бути ОБ'ЄКТ, не масив!
  ctx.userIdExtra = {}
  ctx.userIdGroupChatOpen = {}
  ctx.botSockets = new Map()            // botId -> socket (connected bots)
  ctx.userBotSubscriptions = new Map()  // userId -> Set<botId> (active bot chats)

  await loadConfig(ctx)
  await loadLangs(ctx)

}


async function main() {
  await init()

  // ==================== REST API для TURN/ICE ====================
  // turnHelper вже імпортовано на початку файлу

  // Middleware для парсинга JSON
  app.use(express.json());
  // Middleware для парсинга application/x-www-form-urlencoded (Retrofit @FormUrlEncoded)
  app.use(express.urlencoded({ extended: true }));

  // ── Rate Limiting ────────────────────────────────────────────────────────────
  // Global limit: 300 req / min per IP. Covers all /api/* endpoints.
  const globalLimiter = createRateLimiter({
      windowMs: 60_000,
      max:      300,
      message:  'Rate limit exceeded. Please slow down.',
  });
  // Strict limit for unauthenticated / sensitive endpoints: 15 req / 15 min per IP.
  // Prevents brute-force on login, registration, and password-reset flows.
  const authLimiter = createRateLimiter({
      windowMs: 15 * 60_000,
      max:      15,
      message:  'Too many attempts. Please try again in 15 minutes.',
  });
  app.use(globalLimiter);
  app.use('/api/node/auth', authLimiter);
  app.use('/api/node/signal/register', authLimiter);
  // ─────────────────────────────────────────────────────────────────────────────

  // GET /api/ice-servers/:userId - получить ICE серверы с TURN credentials
  app.get('/api/ice-servers/:userId', (req, res) => {
    try {
      const userId = req.params.userId;

      if (!userId) {
        return res.status(400).json({
          success: false,
          error: 'userId is required'
        });
      }

      const iceConfig = turnHelper.getIceConfigForAndroid(userId);
      res.json(iceConfig);

      console.log(`[ICE] Generated ICE servers for user ${userId}`);
    } catch (error) {
      console.error('[ICE] Error generating ICE servers:', error);
      res.status(500).json({
        success: false,
        error: 'Failed to generate ICE servers'
      });
    }
  });

  // POST /api/turn-credentials - альтернативный метод
  app.post('/api/turn-credentials', (req, res) => {
    try {
      const { userId, ttl } = req.body;

      if (!userId) {
        return res.status(400).json({
          success: false,
          error: 'userId is required'
        });
      }

      const credentials = turnHelper.generateTurnCredentials(userId, ttl || 86400);
      const iceServers = turnHelper.getIceServers(userId, ttl || 86400);

      res.json({
        success: true,
        credentials: credentials,
        iceServers: iceServers
      });

      console.log(`[TURN] Generated credentials for user ${userId}`);
    } catch (error) {
      console.error('[TURN] Error generating credentials:', error);
      res.status(500).json({
        success: false,
        error: 'Failed to generate credentials'
      });
    }
  });

  // Health check endpoint — includes DB ping, memory, socket count
  app.get('/api/health', async (req, res) => {
    const start = Date.now();

    // Database connectivity check
    let db = { status: 'ok', latencyMs: null };
    try {
      const t0 = Date.now();
      await ctx.sequelize.authenticate();
      db.latencyMs = Date.now() - t0;
    } catch (e) {
      db = { status: 'error', error: e.message };
    }

    const mem  = process.memoryUsage();
    const overall = db.status === 'ok' ? 'ok' : 'degraded';

    res.status(overall === 'ok' ? 200 : 503).json({
      status:         overall,
      timestamp:      new Date().toISOString(),
      uptime:         Math.floor(process.uptime()),
      responseTimeMs: Date.now() - start,
      database:       db,
      memory: {
        rssMB:       Math.round(mem.rss       / 1024 / 1024),
        heapUsedMB:  Math.round(mem.heapUsed  / 1024 / 1024),
        heapTotalMB: Math.round(mem.heapTotal / 1024 / 1024),
      },
      sockets: {
        connected: io?.engine?.clientsCount ?? 0,
      },
      bots: getBotStats(ctx),
    });
  });

  // GET /api/bots/stats - Bot connection statistics
  app.get('/api/bots/stats', (req, res) => {
    res.json({
      success: true,
      ...getBotStats(ctx)
    });
  });

  // POST /api/bots/push-message - Push bot message from PHP backend
  app.post('/api/bots/push-message', (req, res) => {
    try {
      const { bot_id, chat_id, text, media, reply_markup, message_id } = req.body;
      if (!bot_id || !chat_id) {
        return res.status(400).json({ success: false, error: 'bot_id and chat_id required' });
      }

      const { pushBotMessage } = require('./listeners/bots-listener');
      pushBotMessage(io, bot_id, chat_id, { text, media, reply_markup, message_id });

      res.json({ success: true, delivered: true });
      console.log(`[Bot API] Push message: ${bot_id} -> ${chat_id}`);
    } catch (error) {
      console.error('[Bot API] Push message error:', error);
      res.status(500).json({ success: false, error: 'Failed to push message' });
    }
  });

  app.get('/', (req, res) => {
    res.sendFile(__dirname + '/index.html');
  });
  io = require('socket.io')(server, {
    allowEIO3: true,
    cors: {
        origin: true,
        credentials: true
    },
  });

  // Initialize Bot API /bots namespace (bot-side connections)
  initializeBotNamespace(io, ctx);

  // Register REST API endpoints for messaging (replaces PHP polling)
  registerAuthRoutes(app, ctx);
  registerMessagingRoutes(app, ctx, io);
  registerPrivateChatRoutes(app, ctx, io);
  registerStoryRoutes(app, ctx, io);
  registerChannelRoutes(app, ctx, io);
  registerGroupRoutes(app, ctx, io);

  // Register Bot REST API (полная замена PHP bot_api.php)
  registerBotRoutes(app, ctx, io);

  // Register Call History REST API (замена PHP call_history.php)
  registerCallRoutes(app, ctx);

  // Register Signal Protocol key server (X3DH pre-key distribution)
  registerSignalRoutes(app, ctx);

  // Инициализация WallyBot (встроенный бот-менеджер)
  await initializeWallyBot(ctx, io);

  io.on('connection', async (socket, query) => {
    await listeners.registerListeners(socket, io, ctx)
  })

  server.listen(serverPort, function() {
    console.log('server up and running at %s port', serverPort);
  });
}

main()
