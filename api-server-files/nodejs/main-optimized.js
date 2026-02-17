const moment = require("moment");
var fs = require('fs');
var express = require('express');
var app = express();
const path = require('path');

let ctx = {};

const configFile = require("./config.json")
const { Sequelize, Op, DataTypes } = require("sequelize");

const listeners = require('./listeners/listeners')

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

   if (ctx.globalconfig["redis"] === "Y") {
     const redisAdapter = require('socket.io-redis');
     io.adapter(redisAdapter({ host: 'localhost', port: ctx.globalconfig["redis_port"] }));
   }


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
  var sequelize = new Sequelize(configFile.sql_db_name, configFile.sql_db_user, configFile.sql_db_pass, {
    host: configFile.sql_db_host,
    dialect: "mysql",
    logging: function () {},
    pool: {
        max: 20,
        min: 0,
        idle: 10000
    }
  });



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
  ctx.wo_reactions_types = require("./models/wo_reactions_types")(sequelize, DataTypes)
  ctx.wo_reactions = require("./models/wo_reactions")(sequelize, DataTypes)
  ctx.wo_blog_reaction = require("./models/wo_blog_reaction")(sequelize, DataTypes)
  ctx.wo_mute = require("./models/wo_mute")(sequelize, DataTypes)
  ctx.wo_calls = require("./models/wo_calls")(sequelize, DataTypes)
  ctx.wo_group_calls = require("./models/wo_group_calls")(sequelize, DataTypes)
  ctx.wo_group_call_participants = require("./models/wo_group_call_participants")(sequelize, DataTypes)
  ctx.wo_ice_candidates = require("./models/wo_ice_candidates")(sequelize, DataTypes)
  ctx.wo_call_statistics = require("./models/wo_call_statistics")(sequelize, DataTypes)

  ctx.globalconfig = {}
  ctx.globallangs = {}
  ctx.socketIdUserHash = {}
  ctx.userHashUserId = {}
  ctx.userIdCount = {}
  ctx.userIdChatOpen = {}
  ctx.userIdSocket = []
  ctx.userIdExtra = {}
  ctx.userIdGroupChatOpen = {}

  await loadConfig(ctx)
  await loadLangs(ctx)

}


async function main() {
  await init()

  app.get('/', (req, res) => {
    res.sendFile(__dirname + '/index.html');
  });

  // 🚀 ОПТИМІЗОВАНИЙ Socket.IO з compression та adaptive transport
  io = require('socket.io')(server, {
    allowEIO3: true,
    cors: {
        origin: true,
        credentials: true
    },

    // 🔥 Транспорти: WebSocket з fallback на polling
    transports: ['websocket', 'polling'],

    // 🗜️ Compression для WebSocket (економія 60-70% трафіку)
    perMessageDeflate: {
        threshold: 1024, // Стискати тільки пакети > 1KB
        zlibDeflateOptions: {
            chunkSize: 8 * 1024,
            memLevel: 7,
            level: 3 // Баланс між швидкістю та стисненням (1-9, де 3 = оптимально)
        },
        zlibInflateOptions: {
            chunkSize: 10 * 1024
        },
        clientNoContextTakeover: true,
        serverNoContextTakeover: true,
        serverMaxWindowBits: 10,
        concurrencyLimit: 10
    },

    // ⏱️ Налаштування ping/pong для підтримки з'єднання
    pingInterval: 25000, // 25 секунд
    pingTimeout: 60000,  // 60 секунд (клієнт має відповісти за цей час)

    // 📦 Максимальний розмір payload
    maxHttpBufferSize: 1e6, // 1 MB

    // ⚡ Upgrade timeout для переходу з polling на WebSocket
    upgradeTimeout: 10000 // 10 секунд
  });

  // 📊 Моніторинг з'єднань
  const connectionStats = {
    total: 0,
    active: 0,
    peakActive: 0,
    avgLatency: 0,
    messagesSent: 0,
    messagesReceived: 0
  };

  // 📈 Оновлюємо статистику кожні 60 секунд
  setInterval(() => {
    console.log('📊 Connection Stats:', {
      active: connectionStats.active,
      peak: connectionStats.peakActive,
      total: connectionStats.total,
      sent: connectionStats.messagesSent,
      received: connectionStats.messagesReceived
    });
  }, 60000);

  // 🔌 Connection handler з адаптивними налаштуваннями
  io.on('connection', async (socket, query) => {
    connectionStats.total++;
    connectionStats.active++;
    if (connectionStats.active > connectionStats.peakActive) {
      connectionStats.peakActive = connectionStats.active;
    }

    // 🏷️ Логування нового з'єднання
    console.log(`✅ New connection: ${socket.id} (Transport: ${socket.conn.transport.name})`);

    // 📡 Відстежуємо зміну транспорту (polling → websocket)
    socket.conn.on('upgrade', () => {
      console.log(`⬆️ Transport upgraded to WebSocket: ${socket.id}`);
    });

    // 📨 Відстежуємо всі повідомлення для статистики
    socket.onAny((event, ...args) => {
      connectionStats.messagesReceived++;

      // 🐛 Debug: логуємо події typing тільки якщо потрібно
      if (event === 'typing' && Math.random() < 0.1) { // Логуємо тільки 10% typing
        console.log(`📝 Typing indicator from ${socket.id}`);
      }
    });

    // 🎯 Реєструємо стандартні listeners
    await listeners.registerListeners(socket, io, ctx)

    // 🔌 Disconnect handler
    socket.on('disconnect', (reason) => {
      connectionStats.active--;
      console.log(`❌ Disconnected: ${socket.id} (Reason: ${reason})`);
    });

    // 📊 Ping handler для моніторингу латентності
    socket.on('ping_latency', (timestamp) => {
      const latency = Date.now() - timestamp;
      socket.emit('pong_latency', { latency, timestamp: Date.now() });

      // Оновлюємо середню латентність (простий експоненціальний фільтр)
      connectionStats.avgLatency = connectionStats.avgLatency * 0.9 + latency * 0.1;
    });
  })

  // 🚀 Запуск сервера
  server.listen(serverPort, function() {
    console.log('🚀 WorldMates Socket.IO Server (OPTIMIZED)');
    console.log(`📡 Running at port ${serverPort}`);
    console.log(`🗜️ Compression: ENABLED (perMessageDeflate)`);
    console.log(`⚡ Transports: WebSocket (primary), Polling (fallback)`);
    console.log(`📊 Monitoring: ENABLED`);
    console.log('========================================');
  });
}

main()
