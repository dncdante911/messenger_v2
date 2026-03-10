/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SCRIPT 2 — Серьёзная стрессовая нагрузка (~10 000 пользователей)       ║
 * ║  WorldMates Messenger v2 — k6 Stress Test Suite                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Сценарий: агрессивная нагрузка, имитирующая пиковый трафик.
 * Используется для проверки HAProxy (TCP-режим), nftables, Node.js кластера
 * и Redis Pub/Sub под давлением.
 *
 * Что тестируется сверх Script 1:
 *   • Массовые параллельные WebSocket-соединения
 *   • Одновременная отправка сообщений в группы
 *   • Загрузка истории чата с пагинацией (loadmore)
 *   • Проверка Signal Protocol (GET bundle, replenish keys)
 *   • Параллельные звонковые события (ICE servers)
 *   • Статусы «typing» от большого числа пользователей одновременно
 *
 * Профиль нагрузки (4 фазы):
 *   Ramp-up   2 мин  → 2 000 VU   (разогрев)
 *   Ramp-up   5 мин  → 5 000 VU   (средний пик)
 *   Ramp-up   5 мин  → 10 000 VU  (максимальный пик)
 *   Hold     15 мин  @ 10 000 VU  (удержание под максимальной нагрузкой)
 *   Ramp-down 3 мин  → 0 VU
 *   Итого: ~30 минут
 *
 * Пороги:
 *   • http_req_duration p(95) < 2 000 мс  (допустимо замедление под нагрузкой)
 *   • http_req_failed   rate  < 5 %
 *   • ws_connection_errors count < 500
 *
 * Запуск:
 *   k6 run --env USERS_FILE=./users.json k6-stress-test.js
 *
 * ⚠  Рекомендация: запускайте с нескольких k6-агентов (k6 cloud или
 *    k6 distributed runner), если один хост не тянет 10k VU.
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
      return Array.from({ length: 500 }, (_, i) => ({
        user_id:      String(2000 + i),
        access_token: `stress_token_${2000 + i}`,
        contacts:     [
          String(2001 + i), String(2002 + i),
          String(2003 + i), String(2004 + i),
        ],
        group_ids: [String(300 + (i % 20))], // 20 тестовых групп
      }));
    });
  }
})();

// ─── Профиль нагрузки ─────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    // Основной REST: сообщения, история, статусы
    rest_heavy: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: '2m',  target: 2000  },
        { duration: '5m',  target: 5000  },
        { duration: '5m',  target: 10000 },
        { duration: '15m', target: 10000 },
        { duration: '3m',  target: 0     },
      ],
      gracefulRampDown: '30s',
    },

    // WebSocket: параллельные соединения (60% пользователей онлайн)
    ws_heavy: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: '2m',  target: 1200 },
        { duration: '5m',  target: 3000 },
        { duration: '5m',  target: 6000 },
        { duration: '15m', target: 6000 },
        { duration: '3m',  target: 0    },
      ],
      gracefulRampDown: '30s',
      exec:             'wsHeavy',
    },

    // Signal Protocol: пополнение OPK и получение bundle
    signal_load: {
      executor:         'constant-vus',
      vus:              200,
      duration:         '25m',
      startTime:        '5m', // стартует после разогрева
      exec:             'signalScenario',
    },
  },

  thresholds: {
    'http_req_duration':     ['p(95)<2000', 'p(99)<5000'],
    'http_req_failed':       ['rate<0.05'],
    'msg_send_duration':     ['p(95)<2500'],
    'msg_get_duration':      ['p(95)<2000'],
    'ws_connection_errors':  ['count<500'],
    'checks':                ['rate>0.95'],
  },

  noConnectionReuse: false,
};

// ─── Метрики ─────────────────────────────────────────────────────────────────

const msgSendDuration   = new Trend('msg_send_duration');
const msgGetDuration    = new Trend('msg_get_duration');
const wsErrors          = new Counter('ws_connection_errors');
const apiErrors         = new Rate('api_error_rate');
const activeWsGauge     = new Gauge('active_ws_connections');

// ─── Helpers ─────────────────────────────────────────────────────────────────

function headers(token) {
  return {
    'Content-Type': 'application/json',
    'access-token':  token,
  };
}

function pickUser() {
  return USERS[randomIntBetween(0, USERS.length - 1)];
}

function fakeMsg() {
  const pool = [
    'ok',  'понял',  'ок',  'да',  'нет',  'скоро',
    'жди', 'момент', '👍',  '?',   'хорошо', 'принял',
  ];
  return pool[Math.floor(Math.random() * pool.length)];
}

function fakeEncrypted() {
  return btoa(fakeMsg() + ':' + Math.random().toString(36).slice(2));
}

// ─── Действия ────────────────────────────────────────────────────────────────

function doGetChat(user, contactId) {
  const t   = Date.now();
  const res = http.post(
    `${BASE_URL}/api/node/chat/get`,
    JSON.stringify({ user_id: contactId, limit: 30, offset: 0 }),
    { headers: headers(user.access_token), tags: { name: 'chat_get' }, timeout: '10s' }
  );
  msgGetDuration.add(Date.now() - t);
  apiErrors.add(res.status >= 400);
  check(res, { 'chat/get ok': (r) => r.status === 200 });
}

function doLoadMore(user, contactId) {
  const res = http.post(
    `${BASE_URL}/api/node/chat/loadmore`,
    JSON.stringify({ user_id: contactId, limit: 30, offset: 30 }),
    { headers: headers(user.access_token), tags: { name: 'chat_loadmore' }, timeout: '10s' }
  );
  apiErrors.add(res.status >= 400);
  check(res, { 'chat/loadmore ok': (r) => r.status === 200 });
}

function doSendMessage(user, contactId) {
  const t   = Date.now();
  const res = http.post(
    `${BASE_URL}/api/node/chat/send`,
    JSON.stringify({
      to:             contactId,
      text:           fakeEncrypted(),
      text_ecb:       fakeEncrypted(),
      iv:             btoa(Math.random().toString(36).slice(2, 14)),
      tag:            btoa(Math.random().toString(36).slice(2, 18)),
      cipher_version: 2,
      type:           'text',
    }),
    { headers: headers(user.access_token), tags: { name: 'chat_send' }, timeout: '10s' }
  );
  msgSendDuration.add(Date.now() - t);
  apiErrors.add(res.status >= 400);
  check(res, { 'chat/send ok': (r) => r.status === 200 || r.status === 201 });
}

function doSendGroupMessage(user, groupId) {
  if (!groupId) return;
  const res = http.post(
    `${BASE_URL}/api/node/groups/${groupId}/messages`,
    JSON.stringify({
      text:           fakeEncrypted(),
      cipher_version: 2,
      type:           'text',
    }),
    { headers: headers(user.access_token), tags: { name: 'group_send' }, timeout: '10s' }
  );
  apiErrors.add(res.status >= 400);
  check(res, { 'group/send ok': (r) => r.status === 200 || r.status === 201 });
}

function doMarkSeen(user, contactId) {
  http.post(
    `${BASE_URL}/api/node/chat/seen`,
    JSON.stringify({ user_id: contactId }),
    { headers: headers(user.access_token), tags: { name: 'chat_seen' }, timeout: '5s' }
  );
}

function doTyping(user, contactId) {
  http.post(
    `${BASE_URL}/api/node/chat/typing`,
    JSON.stringify({ user_id: contactId, status: 'typing' }),
    { headers: headers(user.access_token), tags: { name: 'chat_typing' }, timeout: '3s' }
  );
}

function doGetIceServers(user) {
  const res = http.get(
    `${BASE_URL}/api/ice-servers/${user.user_id}`,
    { headers: headers(user.access_token), tags: { name: 'ice_servers' }, timeout: '5s' }
  );
  check(res, { 'ice-servers ok': (r) => r.status === 200 });
}

// ─── Setup ────────────────────────────────────────────────────────────────────

export function setup() {
  const res = http.get(`${BASE_URL}/api/health`, { timeout: '5s' });
  check(res, { 'health ok': (r) => r.status === 200 });
  console.log(`[setup] Status: ${res.status} | ${res.body?.substring(0, 200)}`);
  return {};
}

// ─── Основной REST сценарий ───────────────────────────────────────────────────

export default function () {
  const user = pickUser();
  if (!user || !user.contacts || user.contacts.length === 0) return;

  const contact = randomItem(user.contacts);
  const groupId = user.group_ids ? randomItem(user.group_ids) : null;

  // Случайное поведение — реалистичнее, чем линейный скрипт
  const action = Math.random();

  if (action < 0.25) {
    // 25% — открыть чат + прокрутить историю
    group('browse_chat', () => {
      doGetChat(user, contact);
      sleep(randomIntBetween(1, 3));
      if (Math.random() < 0.4) {
        doLoadMore(user, contact);
        sleep(1);
      }
      doMarkSeen(user, contact);
    });

  } else if (action < 0.55) {
    // 30% — напечатать и отправить сообщение
    group('send_message', () => {
      doTyping(user, contact);
      sleep(randomIntBetween(1, 4));
      doSendMessage(user, contact);
      sleep(1);
      doMarkSeen(user, contact);
    });

  } else if (action < 0.70) {
    // 15% — сообщение в группу
    group('send_group_msg', () => {
      doSendGroupMessage(user, groupId);
      sleep(randomIntBetween(1, 3));
    });

  } else if (action < 0.80) {
    // 10% — только typing (spam-like pressure)
    group('typing_burst', () => {
      for (let i = 0; i < randomIntBetween(2, 5); i++) {
        doTyping(user, contact);
        sleep(0.5);
      }
    });

  } else if (action < 0.90) {
    // 10% — получить ICE-серверы (WebRTC prep)
    group('call_prep', () => {
      doGetIceServers(user);
      sleep(randomIntBetween(2, 5));
    });

  } else {
    // 10% — просто прочитать чат (lurker)
    group('read_only', () => {
      doGetChat(user, contact);
      sleep(randomIntBetween(10, 30));
    });
  }

  // Короткая пауза между итерациями (агрессивный режим → меньше сна)
  sleep(randomIntBetween(3, 12));
}

// ─── WebSocket сценарий (тяжёлый) ────────────────────────────────────────────

export function wsHeavy() {
  const user = pickUser();
  if (!user) return;

  // Handshake
  const hRes = http.get(
    `${BASE_URL}/socket.io/?EIO=4&transport=polling`,
    { headers: headers(user.access_token), tags: { name: 'sio_handshake' }, timeout: '8s' }
  );

  if (hRes.status !== 200) {
    wsErrors.add(1);
    sleep(3);
    return;
  }

  let sid = null;
  try {
    const body = hRes.body;
    const j    = body.indexOf('{');
    if (j !== -1) sid = JSON.parse(body.substring(j)).sid;
  } catch (_) {
    wsErrors.add(1);
    return;
  }

  if (!sid) { wsErrors.add(1); return; }

  const wsUrl = `${WS_BASE_URL}/socket.io/?EIO=4&transport=websocket&sid=${sid}`;
  let registered  = false;
  let msgReceived = 0;
  let pingSent    = 0;

  const res = ws.connect(wsUrl, { headers: headers(user.access_token) }, function (socket) {
    activeWsGauge.add(1);

    socket.on('open', () => socket.send('2probe'));

    socket.on('message', (data) => {
      if (data === '3probe') {
        socket.send('5');
        socket.send(`42["user:register",{"user_id":"${user.user_id}","access_token":"${user.access_token}"}]`);
        registered = true;

        // Через 1-3 с после регистрации — typing burst (высокая нагрузка)
        socket.setTimeout(() => {
          if (!user.contacts || user.contacts.length === 0) return;
          const peer = randomItem(user.contacts);
          for (let i = 0; i < randomIntBetween(3, 8); i++) {
            socket.send(`42["typing",{"user_id":"${peer}"}]`);
            sleep(0.3);
          }
          socket.send(`42["typing:done",{"user_id":"${peer}"}]`);
        }, randomIntBetween(1000, 3000));

        return;
      }

      if (data === '2') {
        socket.send('3');
        pingSent++;
        return;
      }

      if (data.startsWith('42')) msgReceived++;
    });

    // Держим соединение 1-3 минуты (более короткий hold для стресса → больше reconnects)
    socket.setTimeout(() => {
      socket.close();
      activeWsGauge.add(-1);
    }, randomIntBetween(60 * 1000, 3 * 60 * 1000));

    socket.on('error', () => { wsErrors.add(1); activeWsGauge.add(-1); });
  });

  check(res,        { 'ws 101':       (r) => r && r.status === 101 });
  check(registered, { 'ws registered': (v) => v === true });

  // Немедленное переподключение (стресс reconnect-storm)
  sleep(randomIntBetween(1, 5));
}

// ─── Signal Protocol сценарий ─────────────────────────────────────────────────

export function signalScenario() {
  const user = pickUser();
  if (!user || !user.contacts || user.contacts.length === 0) {
    sleep(5);
    return;
  }

  const peer = randomItem(user.contacts);

  group('signal_fetch_bundle', () => {
    const res = http.get(
      `${BASE_URL}/api/node/signal/bundle/${peer}`,
      { headers: headers(user.access_token), tags: { name: 'signal_bundle' }, timeout: '8s' }
    );
    check(res, { 'signal/bundle ok': (r) => r.status === 200 || r.status === 404 });
    apiErrors.add(res.status >= 500);
  });

  sleep(randomIntBetween(1, 3));

  group('signal_prekey_count', () => {
    const res = http.get(
      `${BASE_URL}/api/node/signal/prekey-count`,
      { headers: headers(user.access_token), tags: { name: 'signal_opk_count' }, timeout: '5s' }
    );
    check(res, { 'prekey-count ok': (r) => r.status === 200 });
  });

  sleep(randomIntBetween(10, 30));
}
