/**
 * PM2 Ecosystem — WorldMates Node.js cluster
 *
 * Hardware profile: 56 CPU threads · 64 GB RAM
 *
 * Worker math
 * ───────────
 *   instances       : 18  (leaves ~38 threads for OS, Redis, MariaDB, Nginx)
 *   RAM per worker  : ~1.7 GB average; restart at 1.5 GB to keep headroom
 *   Total Node RAM  : 18 × 1.5 GB = 27 GB  (leaves ~37 GB for OS + DB)
 *   Worker 0        : "primary" — runs migrations, cron, WallyBot, sweepers
 *                     (ensures heavy background tasks fire exactly once)
 *
 * DB pool math (MariaDB default max_connections = 151)
 * ───────────────────────────────────────────────────
 *   DB_POOL_MAX = floor(151 × 0.8 / 18) = 6  per worker
 *   Total open connections ≤ 6 × 18 = 108  (< 151 × 0.8 = 120.8 ✓)
 *   DB_POOL_MIN = 1  (lazy — don't pre-open 30 idle connections per worker)
 */

module.exports = {
  apps: [{
    name:   'WallyMates',
    script: 'main.js',

    // Absolute cwd is required when pm2-runtime is launched from a different
    // working directory (e.g. via aaPanel Node manager).
    cwd: '/www/wwwroot/worldmates.club/nodejs',

    // ── Cluster ──────────────────────────────────────────────────────────────
    instances:  18,
    exec_mode: 'cluster',

    // ── Worker readiness ─────────────────────────────────────────────────────
    // Each worker calls process.send('ready') inside server.listen() callback.
    // PM2 will NOT start the next worker until the previous one is ready,
    // preventing port-binding races under high instance counts.
    wait_ready:     true,
    listen_timeout: 30000,  // 30 s — migrations run in background AFTER process.send('ready')
    kill_timeout:   8000,   // 8 s graceful SIGTERM window before SIGKILL

    // ── Memory guard ─────────────────────────────────────────────────────────
    // Restart a worker that exceeds 1.5 GB to prevent gradual heap bloat
    // from taking down the whole box (30 × 1.5 GB = 45 GB total budget).
    max_memory_restart: '1500M',
    restart_delay:       3000,   // 3 s cooldown between automatic restarts

    // ── Node.js tuning ───────────────────────────────────────────────────────
    // --max-old-space-size must be < max_memory_restart so the OOM killer
    // triggers a clean PM2 restart instead of a hard OS kill.
    node_args: '--max-old-space-size=1300',

    // ── Environment ──────────────────────────────────────────────────────────
    env: {
      NODE_ENV: 'production',

      // DB connection pool — keep total connections within MariaDB limits.
      // Formula: floor(max_connections × 0.8 / instances) = floor(151×0.8/18) = 6
      DB_POOL_MAX: '6',
      DB_POOL_MIN: '1',

      // Winston log level (override per environment if needed)
      LOG_LEVEL: 'info',
    },

    // ── Log files ────────────────────────────────────────────────────────────
    // PM2 captures stdout/stderr; Winston also writes errors to error.log.
    // merge_logs=true avoids 30 separate per-worker log files.
    error_file: '/www/wwwroot/worldmates.club/nodejs/logs/pm2-error.log',
    out_file:   '/www/wwwroot/worldmates.club/nodejs/logs/pm2-out.log',
    merge_logs:  true,

    // ── Log rotation ─────────────────────────────────────────────────────────
    // Requires: pm2 install pm2-logrotate
    // Rotate daily, keep 14 days, max 100 MB per file.
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
  }]
};
