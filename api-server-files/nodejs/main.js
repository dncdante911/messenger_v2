// Завантажуємо змінні оточення з .env до будь-яких require (крім вбудованих)
// Use __dirname (not process.cwd()) so the path is always correct regardless
// of which directory PM2 launches the process from.
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '.env') });

// ── Professional logging via Winston ─────────────────────────────────────────
// Must be required BEFORE any other module so every subsequent console.*
// call (in controllers, routes, listeners, etc.) routes through Winston.
// This gives us: timestamps, log levels, colorised stdout, and a rotating
// error.log file — without touching the 900+ console.* calls individually.
const logger = require('./helpers/logger');
console.log   = (...args) => logger.info(args.map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
console.info  = (...args) => logger.info(args.map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
console.warn  = (...args) => logger.warn(args.map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
console.error = (...args) => logger.error(args.map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
console.debug = (...args) => logger.debug(args.map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
// ─────────────────────────────────────────────────────────────────────────────

const moment = require("moment");
var fs = require('fs');
var express = require('express');
var app = express();
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
const { initializeWallyBot }         = require('./bots/wallybot')
const { initializeRandomizerBot }    = require('./bots/randomizerBotServer')
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
const { registerSessionRoutes }      = require('./routes/users/sessions')
const { registerTwoFactorRoutes }    = require('./routes/users/two-factor')
const { registerDeleteAccountRoutes } = require('./routes/users/delete-account')
const { registerReportUserRoutes }    = require('./routes/users/report-user')
const { registerModerationRoutes }   = require('./routes/moderation/index')
const { registerScheduledRoutes }    = require('./routes/scheduled')
const { registerFolderRoutes }       = require('./routes/folders')
const { registerBackupRoutes }       = require('./routes/backup')
const { registerStickerRoutes }      = require('./routes/stickers')
const { registerBusinessRoutes, handleBusinessAutoReply } = require('./routes/business')
const { registerBusinessDirectoryRoutes } = require('./routes/business-directory')
const { registerSearchRoutes }       = require('./routes/search/index')
const { registerLinkPreviewRoutes }  = require('./routes/link-preview')
const { registerStarsRoutes }                = require('./routes/stars')
const { registerChannelScheduledPostRoutes } = require('./routes/channels/scheduled-posts')
const { registerVoiceTranscriptionRoutes }   = require('./routes/voice-transcription')
const { registerCrashReportRoutes }          = require('./routes/crash-report')
const { registerShareRoutes }                = require('./routes/channels/share')
const { registerContentPolicyRoutes }        = require('./routes/channels/content-policy')
const { startCronJobs }              = require('./jobs/cronJobs')
const setupMediaAutoDeleteJob        = require('./jobs/media-auto-delete')
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
        // ── Connection pool sizing for PM2 cluster mode ──────────────────────
        // With 30–34 workers you MUST cap max per worker or MariaDB will reject
        // connections (default max_connections = 151).
        //
        // Rule of thumb:  DB_POOL_MAX ≤ floor( DB_MAX_CONNECTIONS × 0.8 / workers )
        //   MariaDB default (151):  floor(151 × 0.8 / 34) ≈ 3  → DB_POOL_MAX=3
        //   MariaDB tuned  (300):   floor(300 × 0.8 / 34) ≈ 7  → DB_POOL_MAX=7
        //   MariaDB tuned  (500):   floor(500 × 0.8 / 34) ≈ 11 → DB_POOL_MAX=11
        //
        // Set in ecosystem.config.js env section:
        //   DB_POOL_MAX=5    ← connections per worker
        //   DB_POOL_MIN=1    ← always-open connections per worker
        max:     parseInt(process.env.DB_POOL_MAX) || 5,
        min:     parseInt(process.env.DB_POOL_MIN) || 1,
        idle:    30000,   // close idle connection after 30 s
        acquire: 15000,   // timeout waiting for a connection (ms) — fail fast, don't queue forever
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
  ctx.wm_channel_premium_customization   = require("./models/wm_channel_premium_customization")(sequelize, DataTypes)

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
  ctx.wm_business_chats         = require("./models/wm_business_chats")(sequelize, DataTypes)

  // ==================== User Rating Models ====================
  ctx.wm_user_ratings = require("./models/wm_user_ratings")(sequelize, DataTypes)

  // ==================== Signal Protocol Models ====================
  ctx.signal_keys              = require("./models/signal_keys")(sequelize, DataTypes)
  // Signal Sender Key Distribution для групових E2EE чатів
  ctx.signal_group_sender_keys = require("./models/signal_group_sender_keys")(sequelize, DataTypes)

  // ==================== Search History Models ====================
  // Stores recent + saved search queries per user
  ctx.wo_search_queries = require("./models/wo_searchqueries")(sequelize, DataTypes)

  // ==================== Content Moderation Models ====================
  ctx.wm_content_hash_blacklist = require("./models/wm_content_hash_blacklist")(sequelize, DataTypes)
  ctx.wm_moderation_queue       = require("./models/wm_moderation_queue")(sequelize, DataTypes)
  ctx.wm_content_policy         = require("./models/wm_content_policy")(sequelize, DataTypes)
  ctx.wm_text_violations        = require("./models/wm_text_violations")(sequelize, DataTypes)

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

  // ── Critical schema migrations (synchronous — must complete before first request) ──
  // is_business_chat must exist in Wo_Messages before any chat queries run.
  // The background runMigrations() covers the full migration set, but can race
  // with incoming requests on a fresh deploy. Run the critical ALTER here.
  try {
    await ctx.sequelize.query(
      `ALTER TABLE Wo_Messages ADD COLUMN is_business_chat TINYINT(1) NOT NULL DEFAULT 0 AFTER page_id`
    );
    console.log('[Init] Wo_Messages.is_business_chat column added');
  } catch (e) {
    if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
      console.warn('[Init] is_business_chat migration warning:', e.message);
    }
  }
  // reply_to_text / reply_to_name — cache for Signal E2EE replies (server can't decrypt)
  for (const [col, def] of [
    ['reply_to_text', 'VARCHAR(512) NULL DEFAULT NULL'],
    ['reply_to_name', 'VARCHAR(128) NULL DEFAULT NULL'],
  ]) {
    try {
      await ctx.sequelize.query(`ALTER TABLE Wo_Messages ADD COLUMN ${col} ${def}`);
      console.log(`[Init] Wo_Messages.${col} column added`);
    } catch (e) {
      if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
        console.warn(`[Init] ${col} migration warning:`, e.message);
      }
    }
  }
  try {
    await ctx.sequelize.query(
      `CREATE TABLE IF NOT EXISTS wm_business_chats (
        id               INT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id          INT UNSIGNED NOT NULL,
        business_user_id INT UNSIGNED NOT NULL,
        last_message_id  INT UNSIGNED NOT NULL DEFAULT 0,
        last_time        INT UNSIGNED NOT NULL DEFAULT 0,
        unread_count     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (id),
        UNIQUE KEY uq_biz_conv (user_id, business_user_id),
        KEY idx_user_time (user_id, last_time),
        KEY idx_biz_user_time (business_user_id, last_time)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
    );
  } catch (e) {
    if (!e.message.includes('already exists')) console.warn('[Init] wm_business_chats:', e.message);
  }
  // fcm_token column in Wo_AppsSessions — stores per-device FCM registration token
  try {
    await ctx.sequelize.query(
      `ALTER TABLE Wo_AppsSessions ADD COLUMN fcm_token VARCHAR(255) NULL DEFAULT NULL`
    );
    console.log('[Init] Wo_AppsSessions.fcm_token column added');
  } catch (e) {
    if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
      console.warn('[Init] fcm_token migration warning:', e.message);
    }
  }
  // profile customization + emoji status + verification_level — user profile features
  for (const sql of [
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_accent       VARCHAR(7)   NOT NULL DEFAULT '#667EEA'`,
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_badge        VARCHAR(8)   NOT NULL DEFAULT ''`,
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS profile_header_style VARCHAR(20)  NOT NULL DEFAULT 'gradient'`,
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS status_emoji         VARCHAR(8)   NULL     DEFAULT NULL`,
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS status_text          VARCHAR(100) NULL     DEFAULT NULL`,
    `ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS verification_level   TINYINT      NOT NULL DEFAULT 0`,
  ]) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
        console.warn('[Init] Wo_Users column migration warning:', e.message);
      }
    }
  }
  // poll + comment_count — story polls and cached comment counter (migration 004)
  for (const sql of [
    `ALTER TABLE Wo_UserStory ADD COLUMN IF NOT EXISTS poll TEXT NULL DEFAULT NULL`,
    `ALTER TABLE Wo_UserStory ADD COLUMN IF NOT EXISTS comment_count INT NOT NULL DEFAULT 0`,
  ]) {
    try {
      await ctx.sequelize.query(sql);
      console.log('[Init] Wo_UserStory column ensured:', sql.match(/COLUMN \w+ (\w+)/)?.[1]);
    } catch (e) {
      if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
        console.warn('[Init] Wo_UserStory migration warning:', e.message);
      }
    }
  }
  // settings_json — channel settings storage (migration 005)
  try {
    await ctx.sequelize.query(
      `ALTER TABLE Wo_Pages ADD COLUMN IF NOT EXISTS settings_json TEXT NULL DEFAULT NULL`
    );
  } catch (e) {
    if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
      console.warn('[Init] Wo_Pages settings_json migration warning:', e.message);
    }
  }
  // written_as_channel + write_as_mode — comment identity (migration 006)
  for (const sql of [
    `ALTER TABLE Wo_Comments ADD COLUMN IF NOT EXISTS written_as_channel TINYINT NOT NULL DEFAULT 0`,
    `ALTER TABLE Wo_Comments ADD COLUMN IF NOT EXISTS write_as_mode VARCHAR(30) NULL DEFAULT NULL`,
  ]) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      if (!e.message.includes('Duplicate column') && !e.message.includes('already exists')) {
        console.warn('[Init] Wo_Comments write_as migration warning:', e.message);
      }
    }
  }
  // ─────────────────────────────────────────────────────────────────────────────

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

  // 2FA fields — google_secret and two_factor_method
  const twoFactorColumns = [
    "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS google_secret     VARCHAR(64)  NOT NULL DEFAULT ''",
    "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(32)  NOT NULL DEFAULT ''",
  ];
  for (const sql of twoFactorColumns) {
    try {
      await ctx.sequelize.query(sql);
    } catch (e) {
      console.warn('[Migration] 2FA column:', e.message);
    }
  }
  console.log('[Migration] 2FA columns ensured');

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

  // ── Wo_SearchQueries — history + saved searches ──────────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS Wo_SearchQueries (
        id         INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_id    INT UNSIGNED  NOT NULL,
        query      VARCHAR(255)  NOT NULL DEFAULT '',
        is_saved   TINYINT       NOT NULL DEFAULT 0,
        created_at INT UNSIGNED  NOT NULL DEFAULT 0,
        INDEX idx_sq_user_saved   (user_id, is_saved),
        INDEX idx_sq_user_created (user_id, created_at)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    console.log('[Migration] Wo_SearchQueries table ensured');
  } catch (e) {
    if (!e.message.includes('already exists')) {
      console.warn('[Migration] Wo_SearchQueries:', e.message);
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

  // ── Cloud Backup Settings table (used by /api/node/backup/* routes) ──────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS Wo_UserCloudBackupSettings (
        id                INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_id           INT           NOT NULL,
        auto_backup       TINYINT(1)    NOT NULL DEFAULT 1,
        backup_frequency  VARCHAR(20)   NOT NULL DEFAULT 'weekly',
        backup_wifi_only  TINYINT(1)    NOT NULL DEFAULT 1,
        backup_provider   VARCHAR(32)   NOT NULL DEFAULT 'local',
        include_media     TINYINT(1)    NOT NULL DEFAULT 1,
        include_messages  TINYINT(1)    NOT NULL DEFAULT 1,
        include_settings  TINYINT(1)    NOT NULL DEFAULT 1,
        last_backup_time  BIGINT        DEFAULT NULL,
        backup_size_bytes BIGINT        DEFAULT NULL,
        created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uq_user_id (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    console.log('[Migration] Wo_UserCloudBackupSettings table ensured');
  } catch (e) {
    console.warn('[Migration] Wo_UserCloudBackupSettings:', e.message);
  }

  // ── wm_pinned_messages — per-conversation pins (up to 5) ─────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_pinned_messages (
        id         INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_id    INT          NOT NULL,
        chat_id    INT          NOT NULL,
        chat_type  ENUM('user','group') NOT NULL DEFAULT 'user',
        message_id INT          NOT NULL,
        pinned_at  INT UNSIGNED NOT NULL DEFAULT 0,
        UNIQUE KEY uq_pin   (user_id, chat_id, chat_type, message_id),
        KEY        idx_chat  (user_id, chat_id, chat_type)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    console.log('[Migration] wm_pinned_messages table ensured');
  } catch (e) {
    console.warn('[Migration] wm_pinned_messages:', e.message);
  }

  // ── wm_saved_messages — "Saved Messages" personal notes/bookmarks ────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_saved_messages (
        id         INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_id    INT          NOT NULL,
        text       TEXT         NULL,
        media      VARCHAR(255) NOT NULL DEFAULT '',
        media_type VARCHAR(32)  NOT NULL DEFAULT '',
        file_size  INT          NOT NULL DEFAULT 0,
        time       INT UNSIGNED NOT NULL DEFAULT 0,
        KEY idx_user_time (user_id, time)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    console.log('[Migration] wm_saved_messages table ensured');
  } catch (e) {
    console.warn('[Migration] wm_saved_messages:', e.message);
  }

  // ── WorldStars — internal currency ───────────────────────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_stars_balance (
        user_id          INT UNSIGNED NOT NULL,
        balance          INT UNSIGNED NOT NULL DEFAULT 0,
        total_purchased  INT UNSIGNED NOT NULL DEFAULT 0,
        total_sent       INT UNSIGNED NOT NULL DEFAULT 0,
        total_received   INT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_stars_transactions (
        id           INT UNSIGNED NOT NULL AUTO_INCREMENT,
        from_user_id INT UNSIGNED DEFAULT NULL,
        to_user_id   INT UNSIGNED NOT NULL,
        amount       INT UNSIGNED NOT NULL,
        type         ENUM('purchase','send','receive','refund') NOT NULL,
        ref_type     VARCHAR(32)  DEFAULT NULL,
        ref_id       INT UNSIGNED DEFAULT NULL,
        note         VARCHAR(255) DEFAULT NULL,
        order_id     VARCHAR(128) DEFAULT NULL,
        created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_to_user   (to_user_id),
        KEY idx_from_user (from_user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    console.log('[Migration] wm_stars tables ensured');
  } catch (e) {
    console.warn('[Migration] wm_stars:', e.message);
  }

  // ── Business/Creator — stats, api keys, verification ─────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_business_stats (
        user_id           INT UNSIGNED NOT NULL,
        date              DATE NOT NULL,
        profile_views     INT UNSIGNED NOT NULL DEFAULT 0,
        messages_received INT UNSIGNED NOT NULL DEFAULT 0,
        link_clicks       INT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (user_id, date)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_business_api_keys (
        id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id INT UNSIGNED NOT NULL,
        api_key VARCHAR(64)  NOT NULL,
        label   VARCHAR(128) NOT NULL DEFAULT 'My API Key',
        PRIMARY KEY (id),
        UNIQUE KEY uk_user (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    const bizAlters = [
      "ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS bio_link VARCHAR(512) DEFAULT NULL",
      "ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS verification_status ENUM('none','pending','approved','rejected') NOT NULL DEFAULT 'none'",
      "ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS verification_note VARCHAR(512) DEFAULT NULL",
      "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS is_creator_verified TINYINT(1) NOT NULL DEFAULT 0",
    ];
    for (const sql of bizAlters) {
      try { await ctx.sequelize.query(sql); } catch (e) {
        if (!e.message.includes('Duplicate column')) console.warn('[Migration] biz alter:', e.message);
      }
    }
    console.log('[Migration] wm_business_stats, wm_business_api_keys, biz columns ensured');
  } catch (e) {
    console.warn('[Migration] business/creator:', e.message);
  }

  // ── Business Chat Separation ──────────────────────────────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_business_chats (
        id               INT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id          INT UNSIGNED NOT NULL COMMENT 'Customer user_id',
        business_user_id INT UNSIGNED NOT NULL COMMENT 'Business owner user_id',
        last_message_id  INT UNSIGNED NOT NULL DEFAULT 0,
        last_time        INT UNSIGNED NOT NULL DEFAULT 0,
        unread_count     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (id),
        UNIQUE KEY uq_biz_conv (user_id, business_user_id),
        KEY idx_user_time (user_id, last_time),
        KEY idx_biz_user_time (business_user_id, last_time)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `);
    // Add is_business_chat column to Wo_Messages if not exists
    try {
      await ctx.sequelize.query(
        `ALTER TABLE Wo_Messages ADD COLUMN is_business_chat TINYINT(1) NOT NULL DEFAULT 0 AFTER page_id`
      );
    } catch (e) {
      if (!e.message.includes('Duplicate column')) console.warn('[Migration] is_business_chat col:', e.message);
    }
    try {
      await ctx.sequelize.query(
        `ALTER TABLE Wo_Messages ADD INDEX idx_biz_chat (from_id, to_id, is_business_chat)`
      );
    } catch (e) {
      if (!e.message.includes('Duplicate key name')) console.warn('[Migration] idx_biz_chat:', e.message);
    }
    console.log('[Migration] wm_business_chats, is_business_chat ensured');
  } catch (e) {
    console.warn('[Migration] business-chat-separation:', e.message);
  }

  // ── Channel Scheduled Posts ───────────────────────────────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_channel_scheduled_posts (
        id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
        channel_id        INT UNSIGNED NOT NULL,
        author_id         INT UNSIGNED NOT NULL,
        text              TEXT         DEFAULT NULL,
        media_url         VARCHAR(255) DEFAULT NULL,
        media_type        VARCHAR(32)  DEFAULT NULL,
        is_pinned         TINYINT(1)   NOT NULL DEFAULT 0,
        scheduled_at      DATETIME     NOT NULL,
        status            ENUM('pending','published','cancelled') NOT NULL DEFAULT 'pending',
        published_post_id INT UNSIGNED DEFAULT NULL,
        PRIMARY KEY (id),
        KEY idx_channel_status (channel_id, status),
        KEY idx_scheduled_at   (scheduled_at, status)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    // Add columns to existing tables (idempotent)
    const spAlters = [
      "ALTER TABLE wm_channel_scheduled_posts ADD COLUMN IF NOT EXISTS media_url VARCHAR(255) DEFAULT NULL",
      "ALTER TABLE wm_channel_scheduled_posts ADD COLUMN IF NOT EXISTS media_type VARCHAR(32) DEFAULT NULL",
      "ALTER TABLE wm_channel_scheduled_posts ADD COLUMN IF NOT EXISTS is_pinned TINYINT(1) NOT NULL DEFAULT 0",
      "ALTER TABLE wm_channel_scheduled_posts ADD COLUMN IF NOT EXISTS published_post_id INT UNSIGNED DEFAULT NULL",
    ];
    for (const sql of spAlters) {
      try { await ctx.sequelize.query(sql); } catch (e) {
        if (!e.message.includes('Duplicate column')) console.warn('[Migration] sp alter:', e.message);
      }
    }
    console.log('[Migration] wm_channel_scheduled_posts ensured');
  } catch (e) {
    console.warn('[Migration] channel_scheduled_posts:', e.message);
  }

  // ── Sticker PRO packs (Strapi slugs + WorldStars price) ──────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_sticker_pro_packs (
        id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
        slug        VARCHAR(128) NOT NULL,
        stars_price INT UNSIGNED NOT NULL DEFAULT 50,
        created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uk_slug (slug)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_sticker_purchases (
        id         INT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id    INT UNSIGNED NOT NULL,
        slug       VARCHAR(128) NOT NULL,
        stars_paid INT UNSIGNED NOT NULL DEFAULT 0,
        purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uk_user_slug (user_id, slug),
        KEY idx_user (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    console.log('[Migration] wm_sticker_pro_packs, wm_sticker_purchases ensured');
  } catch (e) {
    console.warn('[Migration] sticker_pro_packs:', e.message);
  }

  // ── Monetization: creator column, idempotency, trial, subscription orders ──
  try {
    // Creator gets a cut when their PRO sticker pack is bought
    const monAlters = [
      "ALTER TABLE wm_sticker_pro_packs ADD COLUMN IF NOT EXISTS creator_user_id INT UNSIGNED DEFAULT NULL COMMENT 'NULL = platform pack, otherwise the creator who earns 70%'",
      // Unique index on order_id for deduplication (NULLs do not collide in MySQL UNIQUE)
      "ALTER TABLE wm_stars_transactions ADD UNIQUE KEY IF NOT EXISTS uk_order_id (order_id)",
      // 7-day trial flag per user
      "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS trial_used   TINYINT(1) NOT NULL DEFAULT 0",
      // Founding-member badge: first 250 users registered after platform launch
      "ALTER TABLE Wo_Users ADD COLUMN IF NOT EXISTS is_founder   TINYINT(1) NOT NULL DEFAULT 0",
    ];
    for (const sql of monAlters) {
      try { await ctx.sequelize.query(sql); } catch (e) {
        if (!e.message.includes('Duplicate') && !e.message.includes('already exists'))
          console.warn('[Migration] monetization alter:', e.message);
      }
    }

    // Orders table — idempotent webhook deduplication for subscription payments
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_subscription_orders (
        id           INT UNSIGNED NOT NULL AUTO_INCREMENT,
        order_id     VARCHAR(128) NOT NULL,
        user_id      INT UNSIGNED NOT NULL,
        provider     VARCHAR(32)  NOT NULL,
        months       TINYINT UNSIGNED NOT NULL DEFAULT 1,
        amount_uah   INT UNSIGNED NOT NULL DEFAULT 0,
        processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uk_order (order_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    console.log('[Migration] monetization columns + wm_subscription_orders ensured');
  } catch (e) {
    console.warn('[Migration] monetization:', e.message);
  }

  console.log('[Migration] All background migrations complete');

  // ── Sticker support for channel comments, thread messages, story comments ──
  try {
    await ctx.sequelize.query("ALTER TABLE wo_channel_comments ADD COLUMN IF NOT EXISTS sticker VARCHAR(512) DEFAULT NULL");
    await ctx.sequelize.query("ALTER TABLE Wo_StoryComments ADD COLUMN IF NOT EXISTS sticker VARCHAR(512) DEFAULT NULL");
    await ctx.sequelize.query("ALTER TABLE Wo_Comments ADD COLUMN IF NOT EXISTS sticker VARCHAR(512) DEFAULT NULL");
    console.log('[Migration] sticker column added to comment tables');
  } catch (e) {
    console.warn('[Migration] sticker columns:', e.message);
  }

  // ── wm_call_recordings — stream & call recording archive ─────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_call_recordings (
        id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
        room_name   VARCHAR(120) NOT NULL,
        type        ENUM('group_call','channel_stream','private_call') NOT NULL DEFAULT 'group_call',
        uploader_id INT          NOT NULL,
        channel_id  INT          NULL DEFAULT NULL,
        group_id    INT          NULL DEFAULT NULL,
        filename    VARCHAR(255) NOT NULL,
        file_path   VARCHAR(512) NOT NULL,
        file_size   BIGINT       NOT NULL DEFAULT 0,
        duration    INT          NOT NULL DEFAULT 0,
        mime_type   VARCHAR(64)  NOT NULL DEFAULT 'video/webm',
        status      ENUM('processing','ready','failed') NOT NULL DEFAULT 'processing',
        created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_room   (room_name),
        KEY idx_uid    (uploader_id),
        KEY idx_ch     (channel_id),
        KEY idx_type   (type, status)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    console.log('[Migration] wm_call_recordings table ensured');
  } catch (e) {
    console.warn('[Migration] wm_call_recordings:', e.message);
  }

  // ── wm_scheduled_messages — background-scheduler table ──────────────────────
  try {
    await ctx.sequelize.query(`
      CREATE TABLE IF NOT EXISTS wm_scheduled_messages (
        id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
        user_id        INT UNSIGNED NOT NULL,
        chat_id        INT UNSIGNED NOT NULL,
        chat_type      ENUM('dm','group','channel') NOT NULL DEFAULT 'group',
        text           TEXT,
        media_url      VARCHAR(500) DEFAULT NULL,
        media_type     VARCHAR(50)  DEFAULT NULL,
        scheduled_at   INT UNSIGNED NOT NULL,
        repeat_type    ENUM('none','daily','weekly','monthly') NOT NULL DEFAULT 'none',
        is_pinned      TINYINT(1) NOT NULL DEFAULT 0,
        notify_members TINYINT(1) NOT NULL DEFAULT 1,
        status         ENUM('pending','sent','failed','cancelled') NOT NULL DEFAULT 'pending',
        created_at     INT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (id),
        KEY idx_chat    (chat_type, chat_id),
        KEY idx_pending (status, scheduled_at)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);
    console.log('[Migration] wm_scheduled_messages table ensured');
  } catch (e) {
    console.warn('[Migration] wm_scheduled_messages:', e.message);
  }
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
      windowMs: 60000,
      max:      300,
      message:  'Rate limit exceeded. Please slow down.',
  });
  // Strict limit for unauthenticated / sensitive endpoints: 15 req / 15 min per IP.
  // Prevents brute-force on login, registration, and password-reset flows.
  const authLimiter = createRateLimiter({
      windowMs: 15 * 60000,
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

    // Database connectivity check (5 s timeout to prevent health endpoint from hanging)
    let db = { status: 'ok', latencyMs: null };
    try {
      const t0 = Date.now();
      await Promise.race([
        ctx.sequelize.authenticate(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('DB ping timeout')), 5000)),
      ]);
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
  // Обязателен при PM2 cluster mode.
  // @socket.io/redis-adapter v8: комнаты хранятся В ПАМЯТИ каждого воркера,
  // Redis используется ТОЛЬКО для роутинга emit()-ов между воркерами.
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
    const redisHost = process.env.REDIS_HOST || '127.0.0.1';
    const redisPort = parseInt(process.env.REDIS_PORT) || 6379;
    const redisPass = process.env.REDIS_PASSWORD || '';
    // Diagnostic: show connection target (never log the password)
    console.log(`[Redis Adapter] Connecting to ${redisHost}:${redisPort} auth=${redisPass ? 'yes' : 'NO — REDIS_PASSWORD not set'}`);
    const redisOpts = {
      socket: {
        host:               redisHost,
        port:               redisPort,
        reconnectStrategy: (retries) => Math.min(retries * 100, 3000),
      },
      // Only include password key when a non-empty value is configured.
      // Passing password:'' causes the client to send AUTH with an empty string,
      // which Redis rejects with WRONGPASS if requirepass is set.
      ...(redisPass ? { password: redisPass } : {}),
    };
    const pubClient = createClient(redisOpts);
    const subClient = pubClient.duplicate();
    pubClient.on('error', err => console.error('[Redis Adapter] pub error:', err.message));
    subClient.on('error', err => console.error('[Redis Adapter] sub error:', err.message));
    // Race with an explicit timeout so a slow Redis cannot block server.listen()
    // and prevent process.send('ready') from reaching PM2.
    await Promise.race([
      Promise.all([pubClient.connect(), subClient.connect()]),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error('Redis connect timeout (8 s)')), 8000)
      ),
    ]);
    io.adapter(createAdapter(pubClient, subClient));
    console.log('[Redis Adapter] Socket.IO Redis adapter active — cluster/multi-server ready');
  } catch (redisErr) {
    // Non-fatal: server works fine without the adapter (single-process mode).
    // Most common causes: wrong REDIS_PASSWORD in .env, Redis not running,
    // or .env file not found (check dotenv path = __dirname/.env).
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

  // ── FCM token registration ────────────────────────────────────────────────
  // POST /api/node/user/register-fcm-token
  // Stores the Android FCM registration token in Wo_AppsSessions so the server
  // can send push notifications when the Socket.IO service is killed.
  app.post('/api/node/user/register-fcm-token', async (req, res) => {
    try {
      // Inline auth: validate access-token from header/query/body
      const token = req.headers['access-token'] || req.query.access_token || req.body?.access_token;
      if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
      const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
      if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });
      req.userId = session.user_id;
      const fcmToken = (req.body.fcm_token || '').trim();
      if (!fcmToken) return res.json({ api_status: 400, error_message: 'fcm_token required' });
      const sessionId = req.headers['session-id'] || req.headers['session_id'] || req.body.session_id;
      if (sessionId) {
        await ctx.wo_appssessions.update(
          { fcm_token: fcmToken },
          { where: { user_id: req.userId, session_id: sessionId } }
        );
      } else {
        // Fallback: update all sessions for this user
        await ctx.wo_appssessions.update(
          { fcm_token: fcmToken },
          { where: { user_id: req.userId } }
        );
      }
      res.json({ api_status: 200 });
    } catch (err) {
      console.error('[FCM/register]', err.message);
      res.status(500).json({ api_status: 500, error_message: 'Server error' });
    }
  });
  // ─────────────────────────────────────────────────────────────────────────

  // Register User REST API (nearby people, multi-avatars, etc.)
  registerUserRoutes(app, ctx, io);
  registerAvatarRoutes(app, ctx);
  // Register Sessions REST API (replaces PHP sessions.php)
  registerSessionRoutes(app, ctx);
  // Register 2FA REST API (replaces PHP update_two_factor.php)
  registerTwoFactorRoutes(app, ctx);
  // Register Delete Account REST API (replaces PHP delete-user.php)
  registerDeleteAccountRoutes(app, ctx);
  // Register Report User REST API (replaces PHP report_user.php)
  registerReportUserRoutes(app, ctx);
  // Register Content Moderation REST API (очередь, блэклист, політики контенту)
  registerModerationRoutes(app, ctx);
  // Register Channel Content Policy routes (all_ages / mature / adult_verified + subscribe gate)
  registerContentPolicyRoutes(app, ctx);
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

  // Register WorldStars internal currency routes
  registerStarsRoutes(app, ctx);

  // Register Channel Scheduled Posts routes
  registerChannelScheduledPostRoutes(app, ctx);

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

  // Register Voice Transcription route (PRO — OpenAI Whisper)
  registerVoiceTranscriptionRoutes(app, ctx);

  // Register Business Mode routes (profile, hours, quick replies, links, auto-reply)
  registerBusinessRoutes(app, ctx);

  // Register Business Directory routes (public browsable business catalogue)
  registerBusinessDirectoryRoutes(app, ctx);

  // Register Global Search route
  registerSearchRoutes(app, ctx);
  registerLinkPreviewRoutes(app);
  registerCrashReportRoutes(app);
  registerShareRoutes(app, ctx);
  ctx.handleBusinessAutoReply = handleBusinessAutoReply;

  // ── Background cron jobs (premium expiry, story cleanup, notification purge)
  // Run on worker 0 only to avoid 18× redundant DB load in cluster mode.
  if (isFirstWorker) startCronJobs(ctx);

  // ── Media auto-delete job (deletes media files when timer expires)
  // Runs every hour on worker 0 only.
  if (isFirstWorker) setupMediaAutoDeleteJob(ctx.sequelize, io);

  // ── App update check ──────────────────────────────────────────────────────
  // GET /api/node/update/check — serves mobile_update_config.json (no auth required)
  // To publish a new version: edit api-server-files/api/v2/endpoints/mobile_update_config.json
  //   1. Bump "latest_version" and "version_code"
  //   2. Add entries to "changelog" array (newest first)
  //   3. Set "is_mandatory": true to force update
  //   4. Update "apk_url" if the APK location changed
  //   5. Restart the Node.js server (or it auto-reloads if you use pm2 --watch)
  // UPDATE_CONFIG_PATH: use env var for production flexibility.
  // Default resolves relative to this file: nodejs/ → api-server-files/ → api/v2/endpoints/
  const UPDATE_CONFIG_PATH = process.env.UPDATE_CONFIG_PATH ||
    path.resolve(__dirname, '../api/v2/endpoints/mobile_update_config.json');
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
    try {
      await listeners.registerListeners(socket, io, ctx)
    } catch (err) {
      console.error('[Socket.IO] registerListeners error for socket', socket.id, ':', err.message)
      try { socket.disconnect(true) } catch (_) {}
    }
  })

  // ── Server error handler ─────────────────────────────────────────────────
  // Must be attached BEFORE server.listen() so EADDRINUSE (and other bind
  // errors) are caught here rather than leaking to uncaughtException.
  // In PM2 cluster mode the master process holds the TCP socket; a worker
  // receiving EADDRINUSE means the master itself could not bind the port
  // (e.g. a previous PM2 instance is still holding it).  We log the error
  // clearly and exit with code 1 so PM2 retries after restart_delay.
  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.error(
        `[Server] FATAL: port ${serverPort} is already in use (EADDRINUSE). ` +
        `Another process may still hold the port. ` +
        `PM2 will retry after restart_delay. ` +
        `Run: lsof -i :${serverPort}  to identify the owner.`
      );
    } else {
      console.error('[Server] Fatal listen error:', err.message);
    }
    process.exit(1);
  });

  server.listen(serverPort, function() {
    console.log(`server up and running at port ${serverPort}`);

    // ── Signal PM2 FIRST so the next cluster worker can start immediately.
    // Required when ecosystem.config.js sets wait_ready: true.
    // We do NOT block on migrations or WallyBot here — they run in background.
    if (process.send) process.send('ready');

    // ── Worker-0-only background tasks (run after ready to avoid delaying PM2) ─
    if (isFirstWorker) {
      // DB migrations (idempotent ALTER TABLE / CREATE TABLE / ADD INDEX)
      runMigrations(ctx).catch(e => console.error('[Migration] Fatal error:', e));
    }

    // WallyBot must register its handler on EVERY worker because ctx.botSockets
    // is a plain in-memory Map — not shared across PM2 cluster processes.
    // Without this, only worker-0 has the handler; requests routed to workers
    // 1…N see ctx.botSockets.has('wallybot') === false → complete silence.
    // All DB operations inside initializeWallyBot are idempotent (findOrCreate /
    // update-if-changed), so running them on every worker is safe.
    initializeWallyBot(ctx, io).catch(e => console.error('[WallyBot] Init error:', e));
    initializeRandomizerBot(ctx, io).catch(e => console.error('[RandBot] Init error:', e));
  });
}

// ── Graceful Shutdown ─────────────────────────────────────────────────────────
// Timeline (must fit inside ecosystem.config.js kill_timeout: 10000 ms):
//   0 ms  — SIGTERM received → server.close() (stop accepting new connections)
//   5 s   — force-disconnect remaining Socket.IO clients so server.close()
//            callback fires and the DB pool is closed cleanly
//   7 s   — hard process.exit(0) fallback in case server.close() never fires
//  10 s   — PM2 would send SIGKILL (we are already gone by now)
//
// Why drain must be < kill_timeout:
//   If the process is still alive when PM2 sends SIGKILL, the OS may keep the
//   TCP port in TIME_WAIT / kernel socket state for a short window.  The next
//   PM2 restart then fails with EADDRINUSE on that port.
function gracefulShutdown(signal) {
  console.log(`[Shutdown] ${signal} received — draining sockets (5 s max)…`);
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

  // Step 2: after 5 s force-disconnect all remaining Socket.IO clients.
  // This closes their underlying HTTP keep-alive connections and causes
  // server.close() callback (above) to fire.
  const drainTimer = setTimeout(() => {
    console.warn('[Shutdown] 5 s drain timeout — force-disconnecting sockets…');
    if (io) io.disconnectSockets(true);
  }, 5000);
  drainTimer.unref();

  // Step 3: hard exit at 7 s — before PM2's SIGKILL at 10 s so we release the
  // port cleanly before the next worker starts.  Use exit code 0 so PM2 does
  // not log this as a crash (it was a controlled shutdown that just timed out).
  setTimeout(() => {
    console.error('[Shutdown] Forced exit after 7 s (server.close() timed out)');
    process.exit(0);
  }, 7000).unref();
}
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT',  () => gracefulShutdown('SIGINT'));

// ── Unhandled rejection / exception guards ────────────────────────────────────
// Prevent a single async bug from crashing the entire worker process.
// Log the error so it can be diagnosed; for truly unrecoverable errors,
// PM2 will restart the worker automatically.
process.on('unhandledRejection', (reason, promise) => {
  // JSON.stringify(Error) returns '{}' — always extract message/stack explicitly
  const msg = reason instanceof Error
    ? (reason.stack || reason.message)
    : (typeof reason === 'object' ? JSON.stringify(reason) : String(reason));
  console.error('[Process] Unhandled Promise Rejection:', msg);
  // Do NOT exit — let PM2 handle restarts only for truly fatal states.
  // Most promise rejections in Socket.IO event handlers are recoverable.
});

process.on('uncaughtException', (err, origin) => {
  console.error('[Process] Uncaught Exception:', err.message, '| Origin:', origin, err.stack);
  // Exit after logging so PM2 can restart the worker.
  // Staying alive after an uncaught exception risks corrupted state.
  process.exit(1);
});
// ─────────────────────────────────────────────────────────────────────────────

main().catch(err => {
  // main() is async — catch top-level failures so they don't silently swallow
  // the real error as '{}' (JSON.stringify of Error is always empty object).
  console.error('[Fatal] main() crashed before server.listen():', err instanceof Error ? err.stack : err);
  process.exit(1);
});
