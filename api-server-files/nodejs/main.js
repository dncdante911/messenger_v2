// Завантажуємо змінні оточення з .env до будь-яких require (крім вбудованих)
require('dotenv').config();

const moment = require("moment");
var fs = require('fs');
var express = require('express');
var app = express();
const path = require('path');
const compiledTemplates = require('./compiledTemplates/compiledTemplates');

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
const { startSecretSweeper }        = require('./routes/private-chats/secret')
const { registerStoryRoutes } = require('./routes/stories/index')
const { registerChannelRoutes } = require('./routes/channels/index')
const { registerGroupRoutes }   = require('./routes/groups/index')
const { registerBotRoutes }     = require('./routes/bots/index')
const { registerUserRoutes }    = require('./routes/users')
const { registerProfileRoutes } = require('./routes/users/profile')
const { registerRatingRoutes }  = require('./routes/users/rating')
const { registerCallRoutes }    = require('./routes/calls')
const { initializeWallyBot }    = require('./bots/wallybot')
const { registerSignalRoutes }       = require('./routes/signal')
const { registerSubscriptionRoutes } = require('./routes/subscription')
const registerLivestreamRoutes       = require('./routes/channels/livestream')
const registerRecordingRoutes        = require('./routes/recordings')
const registerChannelPremiumRoutes   = require('./routes/channels/channel-premium')
const { createRateLimiter }          = require('./helpers/rateLimiter')
const { instantView }                = require('./routes/instant_view')
const registerNotesRoutes            = require('./routes/notes')
const registerTranslatorRoutes       = require('./routes/translator')
const { registerAvatarRoutes }       = require('./routes/users/avatars')
const { registerScheduledRoutes }    = require('./routes/scheduled')
const { registerFolderRoutes }       = require('./routes/folders')
const { registerBackupRoutes }       = require('./routes/backup')
const { registerStickerRoutes }      = require('./routes/stickers')
const { registerBusinessRoutes, handleBusinessAutoReply } = require('./routes/business')
const { registerSearchRoutes }       = require('./routes/search/index')
const { startCronJobs }              = require('./jobs/cronJobs')
const { createGeoblockMiddleware }   = require('./middleware/geoblock')

// Worker 0 is the "primary" worker: runs migrations, cron jobs, background
// sweepers, WallyBot, and the scheduled-messages scheduler so these run
// exactly once per cluster, not 18 times.
const isFirstWorker = !process.env.NODE_APP_INSTANCE || process.env.NODE_APP_INSTANCE === '0';

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
    logging: false,
    pool: {
        max: 20,
        min: 2,        // держим 2 соединения всегда открытыми — меньше задержка на первый запрос
        idle: 30000,   // закрывать idle-соединение через 30 с
        acquire: 15000 // timeout на получение соединения из пула (мс) — не висеть вечно
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
  ctx.wo_hashtags = require("./models/wo_hashtags")(sequelize, DataTypes)  // нужен в functions.js для разбора хэштегов в сообщениях
  ctx.wo_posts = require("./models/wo_posts")(sequelize, DataTypes)          // нужен! каналы хранят публикации в wo_posts
  ctx.wo_comments = require("./models/wo_comments")(sequelize, DataTypes)    // нужен! комментарии к постам каналов
  ctx.wo_comment_replies = require("./models/wo_comment_replies")(sequelize, DataTypes) // нужен! ответы на комментарии каналов
  ctx.wo_pages = require("./models/wo_pages")(sequelize, DataTypes)          // нужен! каналы — это wo_pages
  // [WoWonder social] wo_groups — группы соцсети (НЕ чат-группы; чат-группы это wo_groupchat)
  // ctx.wo_groups = require("./models/wo_groups")(sequelize, DataTypes)
  // [WoWonder social] wo_events — события/мероприятия соцсети, в мессенджере не используются
  // ctx.wo_events = require("./models/wo_events")(sequelize, DataTypes)
  ctx.wo_userstory = require("./models/wo_userstory")(sequelize, DataTypes)
  ctx.wo_userstorymedia = require("./models/wo_userstorymedia")(sequelize, DataTypes)
  ctx.wo_storyreactions = require("./models/wo_storyreactions")(sequelize, DataTypes)
  ctx.wo_storycomments = require("./models/wo_storycomments")(sequelize, DataTypes)
  ctx.wo_story_seen = require("./models/wo_story_seen")(sequelize, DataTypes)
  ctx.wo_mute_story = require("./models/wo_mute_story")(sequelize, DataTypes)
  ctx.wo_reactions_types = require("./models/wo_reactions_types")(sequelize, DataTypes)
  ctx.wo_reactions = require("./models/wo_reactions")(sequelize, DataTypes)
  // [WoWonder social] wo_blog_reaction — реакции к блог-постам соцсети
  // ctx.wo_blog_reaction = require("./models/wo_blog_reaction")(sequelize, DataTypes)
  ctx.wo_mute         = require("./models/wo_mute")(sequelize, DataTypes)
  ctx.wo_user_avatars       = require("./models/wo_user_avatars")(sequelize, DataTypes)
  ctx.wo_channel_comments   = require("./models/wo_channel_comments")(sequelize, DataTypes)

  // ==================== Scheduled Messages + Chat Folders Models ====================
  ctx.wm_scheduled_messages = require("./models/wm_scheduled_messages")(sequelize, DataTypes)
  ctx.wm_saved_messages     = require("./models/wm_saved_messages")(sequelize, DataTypes)
  const _folderModels = require("./models/wm_chat_folders")
  ctx.wm_chat_folders        = _folderModels.Folder(sequelize, DataTypes)
  ctx.wm_chat_folder_items   = _folderModels.FolderItem(sequelize, DataTypes)
  ctx.wm_chat_folder_members = _folderModels.FolderMember(sequelize, DataTypes)
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
  ctx.wo_stickers = require("./models/wo_stickers")(sequelize, DataTypes)

  // ==================== Channel Livestream + Premium Models ====================
  ctx.wm_chat_timers                     = require("./models/wm_chat_timers")(sequelize, DataTypes)
  ctx.wm_call_recordings                 = require("./models/wm_call_recordings")(sequelize, DataTypes)
  ctx.wm_channel_livestreams             = require("./models/wm_channel_livestreams")(sequelize, DataTypes)
  ctx.wm_channel_subscriptions           = require("./models/wm_channel_subscriptions")(sequelize, DataTypes)
  ctx.wm_channel_subscription_payments   = require("./models/wm_channel_subscription_payments")(sequelize, DataTypes)

  // ==================== Notes + User Storage Models ====================
  ctx.wm_notes        = require("./models/wm_notes")(sequelize, DataTypes)
  ctx.wm_user_storage = require("./models/wm_user_storage")(sequelize, DataTypes)

  // ==================== E2EE Key Backup ====================
  ctx.wm_key_backups = require("./models/wm_key_backups")(sequelize, DataTypes)

  // ==================== Business Mode Models ====================
  ctx.wm_business_profile       = require("./models/wm_business_profile")(sequelize, DataTypes)
  ctx.wm_business_hours         = require("./models/wm_business_hours")(sequelize, DataTypes)
  ctx.wm_business_quick_replies = require("./models/wm_business_quick_replies")(sequelize, DataTypes)
  ctx.wm_business_links         = require("./models/wm_business_links")(sequelize, DataTypes)

  // ==================== User Rating Models ====================
  ctx.wm_user_ratings = require("./models/wm_user_ratings")(sequelize, DataTypes)

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

  // Компилируем Handlebars-шаблоны один раз при старте, а не на каждый WebSocket-коннект.
  // Это убирает синхронное чтение файлов с диска при каждом подключении пользователя.
  await compiledTemplates.DefineTemplates(ctx)
  console.log('[Init] Handlebars templates compiled');

  // Migrations are deferred: runMigrations(ctx) is called from server.listen()
  // callback on worker 0 only — AFTER process.send('ready') so PM2 sees all
  // 18 workers as ready without waiting for potentially slow ALTER TABLE runs.

}

// ── DB migrations (idempotent, worker-0 only) ─────────────────────────────────
// Called in background from server.listen() callback — never blocks startup.
async function runMigrations(ctx) {
  console.log('[Migration] Worker 0 starting background migrations…');

  try {
    await ctx.sequelize.query(
      'ALTER TABLE Wo_GroupAdmins ADD COLUMN IF NOT EXISTS is_anonymous_admin TINYINT NOT NULL DEFAULT 0'
    );
    console.log('[Migration] Wo_GroupAdmins.is_anonymous_admin ensured');
  } catch (e) {
    console.warn('[Migration] is_anonymous_admin:', e.message);
  }

  // ── Refresh token + IP columns for Wo_AppsSessions ────────────────────────
  const sessionTokenColumns = [
    'ALTER TABLE Wo_AppsSessions ADD COLUMN IF NOT EXISTS expires_at         INT          NULL DEFAULT NULL',
    'ALTER TABLE Wo_AppsSessions ADD COLUMN IF NOT EXISTS refresh_token      VARCHAR(120) NULL DEFAULT NULL',
    'ALTER TABLE Wo_AppsSessions ADD COLUMN IF NOT EXISTS refresh_expires_at INT          NULL DEFAULT NULL',
    'ALTER TABLE Wo_AppsSessions ADD COLUMN IF NOT EXISTS ip_address         VARCHAR(64)  NOT NULL DEFAULT \'\'',
    'ALTER TABLE Wo_AppsSessions ADD INDEX idx_refresh_token (refresh_token)',
  ];
  for (const sql of sessionTokenColumns) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      if (!e.message.includes('Duplicate key name') && !e.message.includes('Duplicate column name')) {
        console.warn('[Migration] Wo_AppsSessions:', e.message);
      }
    }
  }
  console.log('[Migration] Wo_AppsSessions columns ensured');

  // wm_user_ratings — user karma / trust system
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_user_ratings (
        id             INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
        rater_id       INT          NOT NULL,
        rated_user_id  INT          NOT NULL,
        rating_type    ENUM('like','dislike') NOT NULL,
        comment        TEXT         NULL,
        created_at     INT          NOT NULL DEFAULT 0,
        updated_at     INT          NOT NULL DEFAULT 0,
        UNIQUE KEY unique_rating (rater_id, rated_user_id),
        KEY idx_rated_user (rated_user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    console.log('[Migration] wm_user_ratings table ensured');
  } catch (e) {
    console.warn('[Migration] wm_user_ratings:', e.message);
  }

  // Profile customization fields — accent color, badge emoji, header style
  const profileCustomizationColumns = [
    "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_accent    VARCHAR(7)  NOT NULL DEFAULT '#667EEA'",
    "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_badge     VARCHAR(8)  NOT NULL DEFAULT ''",
    "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_header_style ENUM('gradient','minimal','pattern') NOT NULL DEFAULT 'gradient'",
  ];
  for (const sql of profileCustomizationColumns) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      console.warn('[Migration] profile_customization column:', e.message);
    }
  }
  console.log('[Migration] Profile customization columns ensured');

  // ── Business Mode tables ──────────────────────────────────────────────────
  const businessTableSQLs = [
    `CREATE TABLE IF NOT EXISTS wm_business_profile (
      id                    INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
      user_id               INT          NOT NULL,
      business_name         VARCHAR(100) DEFAULT NULL,
      category              VARCHAR(100) DEFAULT NULL,
      description           TEXT         DEFAULT NULL,
      address               VARCHAR(255) DEFAULT NULL,
      lat                   DECIMAL(10,7) DEFAULT NULL,
      lng                   DECIMAL(10,7) DEFAULT NULL,
      phone                 VARCHAR(30)  DEFAULT NULL,
      email                 VARCHAR(100) DEFAULT NULL,
      website               VARCHAR(255) DEFAULT NULL,
      auto_reply_enabled    TINYINT(1)   NOT NULL DEFAULT 0,
      auto_reply_text       TEXT         DEFAULT NULL,
      auto_reply_mode       ENUM('always','outside_hours','away') NOT NULL DEFAULT 'always',
      greeting_enabled      TINYINT(1)   NOT NULL DEFAULT 0,
      greeting_text         TEXT         DEFAULT NULL,
      away_enabled          TINYINT(1)   NOT NULL DEFAULT 0,
      away_text             TEXT         DEFAULT NULL,
      badge_enabled         TINYINT(1)   NOT NULL DEFAULT 1,
      created_at            INT UNSIGNED NOT NULL DEFAULT 0,
      updated_at            INT UNSIGNED NOT NULL DEFAULT 0,
      UNIQUE KEY uq_user_id (user_id),
      KEY idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,

    `CREATE TABLE IF NOT EXISTS wm_business_hours (
      id         INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
      user_id    INT          NOT NULL,
      weekday    TINYINT(1)   NOT NULL,
      is_open    TINYINT(1)   NOT NULL DEFAULT 1,
      open_time  VARCHAR(5)   NOT NULL DEFAULT '09:00',
      close_time VARCHAR(5)   NOT NULL DEFAULT '18:00',
      UNIQUE KEY uq_user_weekday (user_id, weekday),
      KEY idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,

    `CREATE TABLE IF NOT EXISTS wm_business_quick_replies (
      id         INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
      user_id    INT          NOT NULL,
      shortcut   VARCHAR(32)  NOT NULL,
      text       TEXT         NOT NULL,
      media_url  VARCHAR(255) DEFAULT NULL,
      created_at INT UNSIGNED NOT NULL DEFAULT 0,
      updated_at INT UNSIGNED NOT NULL DEFAULT 0,
      UNIQUE KEY uq_user_shortcut (user_id, shortcut),
      KEY idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,

    `CREATE TABLE IF NOT EXISTS wm_business_links (
      id             INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
      user_id        INT          NOT NULL,
      title          VARCHAR(100) NOT NULL,
      prefilled_text TEXT         DEFAULT NULL,
      slug           VARCHAR(64)  NOT NULL,
      views          INT UNSIGNED NOT NULL DEFAULT 0,
      created_at     INT UNSIGNED NOT NULL DEFAULT 0,
      UNIQUE KEY uq_slug (slug),
      KEY idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
  ];
  for (const sql of businessTableSQLs) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      console.warn('[Migration] business table:', e.message);
    }
  }
  console.log('[Migration] Business Mode tables ensured');

  // ── Wo_Messages composite indexes ────────────────────────────────────────
  const messageIndexSQLs = [
    "ALTER TABLE Wo_Messages ADD INDEX idx_conv_time   (from_id, to_id, time)",
    "ALTER TABLE Wo_Messages ADD INDEX idx_toid_time   (to_id, time)",
    "ALTER TABLE Wo_Messages ADD INDEX idx_fromid_time (from_id, time)",
    "ALTER TABLE Wo_Messages ADD INDEX idx_conv_seen   (from_id, to_id, seen)",
  ];
  for (const sql of messageIndexSQLs) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      if (!e.message.includes('Duplicate key name')) {
        console.warn('[Migration] Wo_Messages index:', e.message);
      }
    }
  }
  console.log('[Migration] Wo_Messages composite indexes ensured');

  // ── FULLTEXT index on text_preview for global search ─────────────────────
  try {
    await ctx.sequelize.query(
      'ALTER TABLE Wo_Messages ADD FULLTEXT INDEX ft_text_preview (text_preview)'
    );
    console.log('[Migration] Wo_Messages FULLTEXT index ensured');
  } catch (e) {
    if (!e.message.includes('Duplicate key name')) {
      console.warn('[Migration] FULLTEXT index:', e.message);
    }
  }

  // ── wm_key_backups — E2EE encrypted key backup ───────────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_key_backups (
        id                INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_id           INT           NOT NULL,
        encrypted_payload LONGTEXT      NOT NULL,
        salt              VARCHAR(64)   NOT NULL,
        iv                VARCHAR(32)   NOT NULL,
        version           INT           NOT NULL DEFAULT 1,
        created_at        INT UNSIGNED  NOT NULL DEFAULT 0,
        updated_at        INT UNSIGNED  NOT NULL DEFAULT 0,
        UNIQUE KEY uq_user_id (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    console.log('[Migration] wm_key_backups table ensured');
  } catch (e) {
    console.warn('[Migration] wm_key_backups:', e.message);
  }

  console.log('[Migration] All background migrations complete');
}


async function main() {
  await init()

  // ==================== REST API для TURN/ICE ====================
  // turnHelper вже імпортовано на початку файлу

  // Middleware для парсинга JSON
  app.use(express.json());
  // Middleware для парсинга application/x-www-form-urlencoded (Retrofit @FormUrlEncoded)
  app.use(express.urlencoded({ extended: true }));

  // ── Geo-blocking (санкційні країни / hostile countries) ──────────────────────
  // Блокує RU, BY, IR, IQ, KP, CN на рівні IP через geoip-lite (офлайн DB).
  // GEOBLOCK_ENABLED=false — вимкнути (для dev-середовища)
  // GEOBLOCK_EXTRA_COUNTRIES=SY,VE — додати країни
  // GEOBLOCK_WHITELIST_IPS=1.2.3.4 — пропустити конкретні IP
  app.use(createGeoblockMiddleware());

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

  // ── Redis Adapter (Socket.IO cluster / multi-server) ──────────────────────
  // Обязателен при PM2 cluster mode (instances: 'max' = все ядра) и при
  // горизонтальном масштабировании на 2+ серверах.
  //
  // Почему НЕ socket.io-redis (старый):
  //   Он хранил socket rooms в Redis. При реконнекте Redis комнаты терялись
  //   → io.to(room).emit() попадал в пустоту, сообщения пропадали молча.
  //
  // Почему @socket.io/redis-adapter (новый, v8):
  //   Комнаты хранятся В ПАМЯТИ каждого воркера.
  //   Redis используется ТОЛЬКО для роутинга emit()-ов между воркерами/серверами.
  //   Реконнект Redis ≠ потеря комнат. Проблема старого адаптера устранена.
  try {
    const { createAdapter } = require('@socket.io/redis-adapter');
    const { createClient }  = require('redis');
    const redisPass = process.env.REDIS_PASSWORD || '';
    const redisOpts = {
      socket: {
        host: process.env.REDIS_HOST || '127.0.0.1',
        port: parseInt(process.env.REDIS_PORT) || 6379,
        reconnectStrategy: (retries) => Math.min(retries * 100, 3000),
      },
      // Only include password key when a non-empty value is configured.
      // Passing password:undefined causes the client to send AUTH with an
      // empty string, which Redis rejects with NOAUTH.
      ...(redisPass ? { password: redisPass } : {}),
    };
    const pubClient = createClient(redisOpts);
    const subClient = pubClient.duplicate();
    pubClient.on('error', err => console.error('[Redis Adapter] pub error:', err.message));
    subClient.on('error', err => console.error('[Redis Adapter] sub error:', err.message));
    await Promise.all([pubClient.connect(), subClient.connect()]);
    io.adapter(createAdapter(pubClient, subClient));
    console.log('[Redis Adapter] Socket.IO Redis adapter active — cluster/multi-server ready');
  } catch (redisErr) {
    // Non-fatal: server works fine without the adapter (single-process mode).
    // Fix: set REDIS_PASSWORD in environment if Redis requires authentication.
    console.error('[Redis Adapter] DISABLED — could not connect:', redisErr.message);
  }
  // ─────────────────────────────────────────────────────────────────────────────

  // Initialize Bot API /bots namespace (bot-side connections)
  initializeBotNamespace(io, ctx);

  // Register REST API endpoints for messaging (replaces PHP polling)
  registerAuthRoutes(app, ctx);
  registerMessagingRoutes(app, ctx, io);
  registerPrivateChatRoutes(app, ctx, io);
  if (isFirstWorker) startSecretSweeper(ctx, io); // global 60 s sweeper for self-destructing messages (worker 0 only)
  registerStoryRoutes(app, ctx, io);
  registerChannelRoutes(app, ctx, io);
  registerGroupRoutes(app, ctx, io);

  // Register User REST API (nearby people, multi-avatars, etc.)
  registerUserRoutes(app, ctx, io);
  registerAvatarRoutes(app, ctx);
  // Register Profile REST API (own profile, other users, follow, block, search)
  registerProfileRoutes(app, ctx);
  // Register User Rating / karma system
  registerRatingRoutes(app, ctx);

  // Register Bot REST API (полная замена PHP bot_api.php)
  registerBotRoutes(app, ctx, io);

  // Register Call History REST API (замена PHP call_history.php)
  registerCallRoutes(app, ctx);

  // Register Signal Protocol key server (X3DH pre-key distribution)
  // io passed so group/distribute can emit real-time notifications to recipients
  registerSignalRoutes(app, ctx, io);

  // Register Subscription/PRO purchase routes (Way4Pay + LiqPay)
  registerSubscriptionRoutes(app, ctx);

  // Register Channel Livestream routes
  ctx.io = io;   // needed by livestream routes and anywhere ctx.io is used
  registerLivestreamRoutes(app, ctx, io);

  // Register Call & Stream Recording routes
  registerRecordingRoutes(app, ctx);

  // Register Channel Premium Subscription routes
  registerChannelPremiumRoutes(app, ctx);
  // Register Notes (Saved Messages) routes
  registerNotesRoutes(app, ctx);

  // Register Message Translator routes
  registerTranslatorRoutes(app, ctx);

  // Register Scheduled Messages routes + background scheduler
  registerScheduledRoutes(app, ctx, io);

  // Register Shared Chat Folders routes
  registerFolderRoutes(app, ctx);

  // Register Backup / Cloud Settings routes (replaces PHP get/update_cloud_backup_settings.php)
  registerBackupRoutes(app, ctx);

  // Register Sticker & Emoji Packs routes (replaces PHP sticker_pack/emoji_pack endpoints)
  registerStickerRoutes(app, ctx);

  // Register Business Mode routes (profile, hours, quick replies, links, auto-reply)
  registerBusinessRoutes(app, ctx);

  // Register Global Search route
  registerSearchRoutes(app, ctx);
  ctx.handleBusinessAutoReply = handleBusinessAutoReply;

  // ── Background cron jobs (premium expiry, story cleanup, notification purge)
  // Run on worker 0 only to avoid 18× redundant DB load in cluster mode.
  if (isFirstWorker) startCronJobs(ctx);

  // ── App update check ──────────────────────────────────────────────────────
  // GET /api/node/update/check — serves mobile_update_config.json (no auth required)
  // To publish a new version: edit api-server-files/api/v2/endpoints/mobile_update_config.json
  //   1. Bump "latest_version" and "version_code"
  //   2. Add entries to "changelog" array (newest first)
  //   3. Set "is_mandatory": true to force update
  //   4. Update "apk_url" if the APK location changed
  //   5. Restart the Node.js server (or it auto-reloads if you use pm2 --watch)
  const UPDATE_CONFIG_PATH = path.resolve(__dirname, '../../api/v2/endpoints/mobile_update_config.json');
  app.get('/api/node/update/check', (req, res) => {
    try {
      // Always read fresh from disk so edits take effect without restart
      const raw = fs.readFileSync(UPDATE_CONFIG_PATH, 'utf8');
      const cfg = JSON.parse(raw);
      res.json({ success: true, data: cfg });
    } catch (err) {
      console.error('[Update/check]', err.message);
      res.json({ success: false, message: 'Update config unavailable' });
    }
  });

  // Instant View — article reader (no auth required, rate-limited at global level)
  app.post('/api/node/instant-view', instantView(ctx, io));

  io.on('connection', async (socket, query) => {
    await listeners.registerListeners(socket, io, ctx)
  })

  server.listen(serverPort, function() {
    console.log('server up and running at %s port', serverPort);

    // ── Signal PM2 FIRST so the next cluster worker can start immediately.
    // Required when ecosystem.config.js sets wait_ready: true.
    // We do NOT block on migrations or WallyBot here — they run in background.
    if (process.send) process.send('ready');

    // ── Worker-0-only background tasks (run after ready to avoid delaying PM2) ─
    if (isFirstWorker) {
      // DB migrations (idempotent ALTER TABLE / CREATE TABLE / ADD INDEX)
      runMigrations(ctx).catch(e => console.error('[Migration] Fatal error:', e));

      // WallyBot (встроенный бот-менеджер) — only one instance per cluster
      initializeWallyBot(ctx, io).catch(e => console.error('[WallyBot] Init error:', e));
    }
  });
}

// ── Graceful Shutdown ─────────────────────────────────────────────────────────
// Послідовність:
//   1. server.close() — перестаємо приймати нові HTTP-з'єднання.
//   2. 30 секунд: даємо відкритим Socket.IO-з'єднанням завершити роботу.
//   3. Через 30 с примусово відключаємо залишкові сокети → server.close() callback спрацює.
//   4. Закриваємо пул БД та виходимо з кодом 0.
//   5. Hard kill через 35 с якщо callback не спрацював.
function gracefulShutdown(signal) {
  console.log(`[Shutdown] ${signal} received — draining sockets (30 s max)…`);
  if (!server) { process.exit(0); return; }

  // Step 1: stop accepting new connections
  server.close(async () => {
    console.log('[Shutdown] HTTP server closed');
    try {
      await ctx.sequelize.close();
      console.log('[Shutdown] DB pool closed');
    } catch (e) {
      console.error('[Shutdown] DB close error:', e.message);
    }
    process.exit(0);
  });

  // Step 2: after 30 s force-disconnect all remaining Socket.IO clients
  // This causes their underlying HTTP keep-alive connections to close,
  // which triggers the server.close() callback above.
  const drainTimer = setTimeout(() => {
    console.warn('[Shutdown] 30 s drain timeout — force-disconnecting sockets…');
    if (io) io.disconnectSockets(true);
  }, 30_000);
  drainTimer.unref();

  // Step 3: hard kill fallback at 35 s (in case server.close never resolves)
  setTimeout(() => {
    console.error('[Shutdown] Forced exit after 35 s');
    process.exit(1);
  }, 35_000).unref();
}
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT',  () => gracefulShutdown('SIGINT'));
// ─────────────────────────────────────────────────────────────────────────────

main()
