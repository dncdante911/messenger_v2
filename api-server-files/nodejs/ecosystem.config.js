/**
 * PM2 Ecosystem — WorldMates Node.js cluster
 *
 * Hardware profile: 56 CPU threads · 64 GB RAM
 *
 * Worker math
 * ───────────
 *   instances       : 4  (sweet spot for I/O-bound Socket.IO + Sequelize workload)
 *   RAM per worker  : ~300–500 MB typical; restart at 1.5 GB as safety net
 *   Total Node RAM  : 4 × 1.5 GB = 6 GB  (leaves 58 GB for OS, Redis, MariaDB, Nginx)
 *   Worker 0        : "primary" — runs migrations, cron, WallyBot, sweepers
 *
 *   Why 4 and not 18?
 *   Node.js is single-threaded event-loop, not CPU-bound. For Socket.IO + DB queries,
 *   4 workers saturate the DB connection pool and Redis pub/sub throughput.
 *   More workers = more memory, more Redis traffic, more DB connections, zero benefit.
 *   Scale UP only if CPU per worker hits >80% sustained (check `pm2 monit`).
 *
 * DB pool math (MariaDB default max_connections = 151)
 * ───────────────────────────────────────────────────
 *   DB_POOL_MAX = floor(151 × 0.8 / 4) = 30  per worker
 *   Total open connections ≤ 30 × 4 = 120  (< 151 × 0.8 = 120.8 ✓)
 *   DB_POOL_MIN = 2  (keep a couple warm for fast queries)
 *
 * Socket.IO cluster mode
 * ──────────────────────
 *   @socket.io/redis-adapter routes emit() calls between workers via Redis pub/sub.
 *   Every user joins a Socket.IO room = String(user_id) on connect (JoinController).
 *   All emit() calls in listeners use io.to(String(userId)) instead of
 *   ctx.userIdSocket[userId], making them cluster-safe automatically.
 */

module.exports = {
  apps: [{
    name:   'WallyMates',
    script: 'main.js',

    // Absolute cwd is required when pm2-runtime is launched from a different
    // working directory (e.g. via aaPanel Node manager).
    cwd: '/www/wwwroot/worldmates.club/nodejs',

    // ── Cluster ──────────────────────────────────────────────────────────────
    // Redis 8.2 confirmed working. Adapter retries every 3s if Redis is slow at startup.
    instances:  4,
    exec_mode: 'cluster',

    // ── Worker readiness ─────────────────────────────────────────────────────
    wait_ready:     true,
    listen_timeout: 30000,
    kill_timeout:   10000,

    // ── Memory guard ─────────────────────────────────────────────────────────
    max_memory_restart: '1500M',
    restart_delay:       3000,

    // ── Node.js tuning ───────────────────────────────────────────────────────
    node_args: '--max-old-space-size=1300',

    // ── Environment ──────────────────────────────────────────────────────────
    env: {
      NODE_ENV: 'production',

      // DB connection pool — per worker.
      // Formula: floor(max_connections × 0.8 / instances) = floor(151×0.8/4) = 30
      DB_POOL_MAX: '30',
      DB_POOL_MIN: '2',

      // Winston log level
      LOG_LEVEL: 'info',
    },

    // ── Log files ────────────────────────────────────────────────────────────
    error_file: '/www/wwwroot/worldmates.club/nodejs/logs/pm2-error.log',
    out_file:   '/www/wwwroot/worldmates.club/nodejs/logs/pm2-out.log',
    merge_logs:  true,

    // ── Log rotation ─────────────────────────────────────────────────────────
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
  }]
};
