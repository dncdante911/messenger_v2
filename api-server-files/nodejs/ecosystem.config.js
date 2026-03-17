module.exports = {
  apps: [{
    name: 'WallyMates',
    script: 'main.js',
    // Absolute cwd is required when pm2-runtime is launched from a different
    // working directory (e.g. via aaPanel Node manager).
    // Without it pm2-runtime does path.join(undefined, 'main.js') → crash.
    cwd: '/www/wwwroot/worldmates.club/nodejs',

    // ── Cluster mode ────────────────────────────────────────────────────────
    instances: 18,
    exec_mode: 'cluster',

    // ── Готовность воркера ───────────────────────────────────────────────────
    // wait_ready: true — PM2 запускает следующий воркер ТОЛЬКО после того,
    // как предыдущий прислал process.send('ready') из server.listen().
    // Это устраняет гонку при одновременном запуске 18 воркеров на порт 449.
    wait_ready: true,
    listen_timeout: 60000,   // 60 s — enough for worker 0 migrations on first deploy
    kill_timeout: 5000,       // даём 5 с на graceful shutdown (SIGTERM → SIGKILL)

    max_memory_restart: '1G',
    restart_delay: 2000,

    env: {
      NODE_ENV: 'production',
    },

    error_file: '/www/wwwroot/worldmates.club/nodejs/logs/pm2-error.log',
    out_file:   '/www/wwwroot/worldmates.club/nodejs/logs/pm2-out.log',
    merge_logs: true,
  }]
};
