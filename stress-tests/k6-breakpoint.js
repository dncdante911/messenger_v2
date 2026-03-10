/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SCRIPT 3 — Поиск предельной нагрузки (Breakpoint Test)                 ║
 * ║  WorldMates Messenger v2 — k6 Stress Test Suite                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Цель: найти точку отказа — уровень нагрузки, при котором сервер
 * перестаёт справляться (error rate > 30 % или p95 > 10 с).
 *
 * Стратегия: ramping-arrival-rate
 *   Нарастающий поток запросов в секунду, независимо от числа VU.
 *   Это точнее для нахождения предела пропускной способности (RPS),
 *   чем ramping-vus (который зависит от времени итерации).
 *
 * Профиль:
 *   0    → 100 RPS  за 2 мин   (разогрев)
 *   100  → 500 RPS  за 5 мин
 *   500  → 1 000 RPS за 5 мин
 *   1 000 → 3 000 RPS за 10 мин
 *   3 000 → 5 000 RPS за 10 мин
 *   Hold  @ 5 000 RPS 5 мин   (максимальное давление)
 *   Итого: ~37 минут
 *
 *   preAllocatedVUs: 500 (пул VU для быстрого старта)
 *   maxVUs: 5 000        (потолок, если VU станет узким местом)
 *
 * Дополнительный сценарий: WS connection storm
 *   Параллельно нарастает число WebSocket-соединений, чтобы
 *   проверить лимиты файловых дескрипторов HAProxy и Node.js.
 *
 * Пороги — НАМЕРЕННО МЯГКИЕ (тест должен дойти до предела):
 *   Скрипт НЕ завершится по threshold — мы смотрим на графики.
 *   abortOnFail: false везде.
 *
 * Запуск:
 *   k6 run \
 *     --env USERS_FILE=./users.json \
 *     --out json=results/breakpoint-$(date +%s).json \
 *     k6-breakpoint.js
 *
 * Анализ результатов после теста:
 *   k6 run ... --out influxdb=http://localhost:8086/k6  (Grafana-дашборд)
 *
 * ⚠  Запускайте ТОЛЬКО в обслуживаемое время.
 *    Скрипт намеренно не ограничивает ошибки — сервер МОЖЕТ упасть.
 */

import http            from 'k6/http';
import ws              from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import { SharedArray }                  from 'k6/data';
import { randomItem, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ─── Config ───────────────────────────────────────────────────────────────────

const BASE_URL    = 'https://worldmates.club';
const WS_BASE_URL = 'wss://worldmates.club';

// ─── Test users ───────────────────────────────────────────────────────────────

const USERS = (() => {
  try {
    return new SharedArray('users', function () {
      return JSON.parse(open(__ENV.USERS_FILE || './users.json'));
    });
  } catch (_) {
    return new SharedArray('users', function () {
      return Array.from({ length: 1000 }, (_, i) => ({
        user_id:      String(5000 + i),
        access_token: `bp_token_${5000 + i}`,
        contacts:     [String(5001 + i), String(5002 + i), String(5003 + i)],
        group_ids:    [String(500 + (i % 50))],
      }));
    });
  }
})();

// ─── Профиль нагрузки ─────────────────────────────────────────────────────────

export const options = {
  scenarios: {

    // ── Нарастающий RPS (основной сценарий поиска предела) ────────────────────
    rps_breakpoint: {
      executor: 'ramping-arrival-rate',

      // Начальный RPS
      startRate: 10,
      timeUnit:  '1s',

      stages: [
        // Разогрев
        { duration: '2m',  target: 100  },  // 0 → 100 rps
        // Нарастание
        { duration: '5m',  target: 500  },  // 100 → 500 rps
        { duration: '5m',  target: 1000 },  // 500 → 1000 rps
        { duration: '10m', target: 3000 },  // 1000 → 3000 rps
        { duration: '10m', target: 5000 },  // 3000 → 5000 rps
        // Максимальное давление
        { duration: '5m',  target: 5000 },  // hold @ 5000 rps
      ],

      // VU пул — k6 автоматически добавит VU если итерации накапливаются
      preAllocatedVUs: 500,
      maxVUs:          5000,
    },

    // ── WebSocket connection storm ─────────────────────────────────────────────
    ws_storm: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: '5m',  target: 500  },
        { duration: '10m', target: 2000 },
        { duration: '10m', target: 5000 },
        { duration: '5m',  target: 5000 },
        { duration: '2m',  target: 0    },
      ],
      gracefulRampDown: '10s',
      exec:             'wsStorm',
      startTime:        '5m', // дать REST-сценарию разогреться
    },

    // ── Мониторинг здоровья сервера (фоновый) ─────────────────────────────────
    health_probe: {
      executor:  'constant-arrival-rate',
      rate:      2,          // 2 запроса/с
      timeUnit:  '1s',
      duration:  '37m',
      preAllocatedVUs: 5,
      maxVUs:          10,
      exec:      'healthProbe',
    },
  },

  // Пороги НАБЛЮДЕНИЯ (abortOnFail: false → тест продолжается до конца)
  thresholds: {
    'http_req_duration': [
      { threshold: 'p(95)<10000', abortOnFail: false },
    ],
    'http_req_failed': [
      { threshold: 'rate<0.50', abortOnFail: false },
    ],
    'ws_connection_errors': [
      { threshold: 'count<5000', abortOnFail: false },
    ],
    // Критический порог — автостоп если ВСЁ сломалось (>80% ошибок)
    'http_req_failed{type:critical}': [
      { threshold: 'rate<0.80', abortOnFail: true, delayAbortEval: '30s' },
    ],
  },

  noConnectionReuse: false,
};

// ─── Метрики ─────────────────────────────────────────────────────────────────

const sendDuration      = new Trend('msg_send_duration');
const getDuration       = new Trend('msg_get_duration');
const healthDuration    = new Trend('health_check_duration');
const wsErrors          = new Counter('ws_connection_errors');
const apiErrors         = new Rate('api_error_rate');
const criticalErrors    = new Rate('http_req_failed{type:critical}');
const activeWs          = new Gauge('active_ws_connections');
const serverErrorsCount = new Counter('server_5xx_count');

// ─── Helpers ─────────────────────────────────────────────────────────────────

function hdr(token) {
  return {
    'Content-Type': 'application/json',
    'access-token':  token,
  };
}

function pickUser() {
  return USERS[randomIntBetween(0, USERS.length - 1)];
}

function shortMsg() {
  const pool = ['ok', 'да', '?', 'понял', '+', 'ок', 'жди', '👍', 'нет', 'да!'];
  return pool[Math.floor(Math.random() * pool.length)];
}

function enc() {
  return btoa(shortMsg() + ':' + Math.random().toString(36).slice(2));
}

// ─── Setup ────────────────────────────────────────────────────────────────────

export function setup() {
  const res = http.get(`${BASE_URL}/api/health`, { timeout: '10s' });
  check(res, { 'server reachable': (r) => r.status === 200 });
  console.log(`[BREAKPOINT] Starting. Health status: ${res.status}`);
  console.log(`[BREAKPOINT] Loaded ${USERS.length} test users.`);
  return { startTime: Date.now() };
}

// ─── Основной сценарий (rps_breakpoint) ──────────────────────────────────────

export default function (data) {
  const user = pickUser();
  if (!user || !user.contacts || user.contacts.length === 0) return;

  const contact = randomItem(user.contacts);
  const groupId = user.group_ids ? randomItem(user.group_ids) : null;

  // Распределение типов запросов — максимально реалистичный микс
  const r = Math.random();

  if (r < 0.30) {
    // 30% — отправка сообщения (самая тяжёлая операция: DB write + Redis pub)
    const t = Date.now();
    const res = http.post(
      `${BASE_URL}/api/node/chat/send`,
      JSON.stringify({
        to:             contact,
        text:           enc(),
        text_ecb:       enc(),
        iv:             btoa(Math.random().toString(36).slice(2, 14)),
        tag:            btoa(Math.random().toString(36).slice(2, 18)),
        cipher_version: 2,
        type:           'text',
      }),
      { headers: hdr(user.access_token), tags: { name: 'chat_send' }, timeout: '15s' }
    );
    sendDuration.add(Date.now() - t);
    apiErrors.add(res.status >= 400);
    if (res.status >= 500) serverErrorsCount.add(1);
    check(res, { 'send ok': (r) => r.status === 200 || r.status === 201 });

  } else if (r < 0.55) {
    // 25% — получение истории чата (DB read + decrypt)
    const t = Date.now();
    const res = http.post(
      `${BASE_URL}/api/node/chat/get`,
      JSON.stringify({ user_id: contact, limit: 30, offset: 0 }),
      { headers: hdr(user.access_token), tags: { name: 'chat_get' }, timeout: '12s' }
    );
    getDuration.add(Date.now() - t);
    apiErrors.add(res.status >= 400);
    if (res.status >= 500) serverErrorsCount.add(1);
    check(res, { 'get ok': (r) => r.status === 200 });

  } else if (r < 0.65) {
    // 10% — typing (лёгкий, частый)
    http.post(
      `${BASE_URL}/api/node/chat/typing`,
      JSON.stringify({ user_id: contact, status: 'typing' }),
      { headers: hdr(user.access_token), tags: { name: 'typing' }, timeout: '3s' }
    );

  } else if (r < 0.75) {
    // 10% — seen (лёгкий DB write)
    http.post(
      `${BASE_URL}/api/node/chat/seen`,
      JSON.stringify({ user_id: contact }),
      { headers: hdr(user.access_token), tags: { name: 'seen' }, timeout: '5s' }
    );

  } else if (r < 0.82) {
    // 7% — группа: отправка
    if (groupId) {
      const res = http.post(
        `${BASE_URL}/api/node/groups/${groupId}/messages`,
        JSON.stringify({ text: enc(), cipher_version: 2, type: 'text' }),
        { headers: hdr(user.access_token), tags: { name: 'group_send' }, timeout: '12s' }
      );
      if (res.status >= 500) serverErrorsCount.add(1);
    }

  } else if (r < 0.89) {
    // 7% — loadmore (пагинация, тяжелее чем get)
    const res = http.post(
      `${BASE_URL}/api/node/chat/loadmore`,
      JSON.stringify({ user_id: contact, limit: 50, offset: randomIntBetween(30, 200) }),
      { headers: hdr(user.access_token), tags: { name: 'loadmore' }, timeout: '12s' }
    );
    if (res.status >= 500) serverErrorsCount.add(1);

  } else if (r < 0.93) {
    // 4% — Signal bundle fetch (чтение ключей E2EE)
    const peer = randomItem(user.contacts);
    http.get(
      `${BASE_URL}/api/node/signal/bundle/${peer}`,
      { headers: hdr(user.access_token), tags: { name: 'signal_bundle' }, timeout: '8s' }
    );

  } else if (r < 0.96) {
    // 3% — ICE servers (WebRTC setup)
    http.get(
      `${BASE_URL}/api/ice-servers/${user.user_id}`,
      { headers: hdr(user.access_token), tags: { name: 'ice_servers' }, timeout: '5s' }
    );

  } else {
    // 4% — поиск в чате (CPU heavy: plaintext search в БД)
    http.post(
      `${BASE_URL}/api/node/chat/search`,
      JSON.stringify({ user_id: contact, query: shortMsg() }),
      { headers: hdr(user.access_token), tags: { name: 'chat_search' }, timeout: '15s' }
    );
  }

  // При breakpoint-тесте пауз минимум — чтобы реально давить на RPS
  sleep(Math.random() * 0.1); // 0–100 мс
}

// ─── WebSocket storm ──────────────────────────────────────────────────────────

export function wsStorm() {
  const user = pickUser();
  if (!user) { sleep(1); return; }

  // Polling handshake
  const hRes = http.get(
    `${BASE_URL}/socket.io/?EIO=4&transport=polling`,
    { headers: hdr(user.access_token), tags: { name: 'sio_handshake' }, timeout: '5s' }
  );

  if (hRes.status !== 200) {
    wsErrors.add(1);
    sleep(randomIntBetween(1, 3));
    return;
  }

  let sid = null;
  try {
    const j = hRes.body.indexOf('{');
    if (j !== -1) sid = JSON.parse(hRes.body.substring(j)).sid;
  } catch (_) {
    wsErrors.add(1);
    return;
  }

  if (!sid) { wsErrors.add(1); return; }

  const wsUrl = `${WS_BASE_URL}/socket.io/?EIO=4&transport=websocket&sid=${sid}`;
  let upgraded = false;
  let pings    = 0;

  const res = ws.connect(wsUrl, { headers: hdr(user.access_token) }, function (socket) {
    activeWs.add(1);

    socket.on('open', () => socket.send('2probe'));

    socket.on('message', (data) => {
      if (data === '3probe') {
        socket.send('5');
        socket.send(
          `42["user:register",{"user_id":"${user.user_id}","access_token":"${user.access_token}"}]`
        );
        upgraded = true;

        // Тест reconnect storm: посылаем несколько эвентов и закрываем
        socket.setTimeout(() => {
          if (user.contacts && user.contacts.length > 0) {
            const peer = randomItem(user.contacts);
            // Пачка typing для максимального давления на Redis pub/sub
            for (let i = 0; i < randomIntBetween(5, 15); i++) {
              socket.send(`42["typing",{"user_id":"${peer}"}]`);
            }
            socket.send(`42["typing:done",{"user_id":"${peer}"}]`);
          }
        }, randomIntBetween(500, 2000));

        return;
      }

      if (data === '2') {
        socket.send('3');
        pings++;
        return;
      }
    });

    // Короткое время жизни → максимум reconnect-ов → нагрузка на accept/close
    const holdMs = randomIntBetween(10 * 1000, 60 * 1000);
    socket.setTimeout(() => {
      socket.close();
      activeWs.add(-1);
    }, holdMs);

    socket.on('error', () => {
      wsErrors.add(1);
      activeWs.add(-1);
    });
  });

  check(res,      { 'ws 101':        (r) => r && r.status === 101 });
  check(upgraded, { 'ws registered': (v) => v === true });

  // Минимальная пауза до следующего соединения
  sleep(randomIntBetween(1, 3));
}

// ─── Health probe (фоновый мониторинг) ────────────────────────────────────────

export function healthProbe() {
  const t   = Date.now();
  const res = http.get(`${BASE_URL}/api/health`, {
    tags:    { name: 'health_probe' },
    timeout: '8s',
  });
  healthDuration.add(Date.now() - t);

  const ok = res.status === 200;
  check(res, { 'server alive': () => ok });

  if (!ok) {
    console.error(`[HEALTH PROBE] ❌ Status: ${res.status} | Time: ${new Date().toISOString()} | Body: ${res.body?.substring(0, 300)}`);
  } else {
    // Логируем каждые ~30 проверок (не засорять вывод)
    if (Math.random() < 0.03) {
      try {
        const body = JSON.parse(res.body);
        console.log(
          `[HEALTH] ✅ uptime=${body.uptime}s | mem=${JSON.stringify(body.memory)} | sockets=${body.socketCount} | db=${body.database}`
        );
      } catch (_) {
        console.log(`[HEALTH] ✅ ${res.body?.substring(0, 150)}`);
      }
    }
  }

  // Health probe не спит — rate контролируется constant-arrival-rate
}

// ─── Teardown ─────────────────────────────────────────────────────────────────

export function teardown(data) {
  const elapsed = data ? Math.round((Date.now() - data.startTime) / 1000) : 0;
  console.log(`\n[BREAKPOINT] Test completed in ${elapsed}s.`);
  console.log('[BREAKPOINT] Check your metrics for the inflection point:');
  console.log('  → Where did p95 latency start climbing exponentially?');
  console.log('  → At what RPS did error rate exceed 5%? 10%? 30%?');
  console.log('  → How many active WS connections caused issues?');
  console.log('  → Did HAProxy or Node.js hit its fd limit?');
  console.log('  → Did Redis Pub/Sub queue grow unbounded?');
}
