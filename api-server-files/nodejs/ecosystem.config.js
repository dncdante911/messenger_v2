module.exports = {
  apps: [{
    name: 'WallyMates',
    script: 'main.js',

    // ── Cluster mode ────────────────────────────────────────────────────────
    // ВАЖНО: instances должен быть числом, а НЕ строкой.
    // Строка '14' в некоторых версиях PM2 воспринимается некорректно и
    // вызывает гонку при запуске воркеров → EADDRINUSE null:449.
    instances: 14,
    exec_mode: 'cluster',

    // ── Готовность воркера ───────────────────────────────────────────────────
    // wait_ready: true — PM2 запускает следующий воркер ТОЛЬКО после того,
    // как предыдущий прислал process.send('ready') из server.listen().
    // Это устраняет гонку при одновременном запуске 14 воркеров на порт 449.
    wait_ready: true,
    listen_timeout: 10000,   // ждём ready-сигнала не более 10 с
    kill_timeout: 5000,       // даём 5 с на graceful shutdown (SIGTERM → SIGKILL)

    max_memory_restart: '1G',
    restart_delay: 2000,

    env: {
      NODE_ENV: 'production',
    },

    error_file: './logs/pm2-error.log',
    out_file:   './logs/pm2-out.log',
    merge_logs: true,
  }]
};
