/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SCRIPT 1 — Обычная дневная нагрузка (~2 000 пользователей)             ║
 * ║  WorldMates Messenger v2 — k6 Stress Test Suite                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Сценарий: эмуляция типичного дневного трафика мессенджера.
 * Каждый виртуальный пользователь (VU):
 *   1. Подключается к Socket.IO (WebSocket через HAProxy → Node.js :449)
 *   2. Получает историю чата с несколькими контактами
 *   3. Периодически отправляет сообщение (REST + WS-эвент)
 *   4. Отправляет индикатор «печатает»
 *   5. Помечает сообщения прочитанными
 *   6. Делает паузы (имитация чтения/ожидания ответа)
 *
 * Профиль нагрузки:
 *   Ramp-up  5 мин  → 2 000 VU
 *   Hold    20 мин  @ 2 000 VU
 *   Ramp-down 5 мин → 0 VU
 *   Итого: 30 минут
 *
 * Пороги (thresholds):
 *   • http_req_duration p(95) < 500 мс
 *   • http_req_failed   rate  < 1 %
 *   • ws_session_duration p(95) < 35 минут (длинный полинг через HAProxy)
 *
 * Запуск:
 *   k6 run --env USERS_FILE=./users.json k6-normal-load.js
 *
 * Файл users.json — массив объектов:
 *   [{"user_id":"101","access_token":"abc...","contacts":["102","103","104"]}]
 */

import http            from 'k6/http';
import ws              from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray }          from 'k6/data';
import { randomItem, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ─── Config ───────────────────────────────────────────────────────────────────

const BASE_URL    = 'https://worldmates.club';
const WS_BASE_URL = 'wss://worldmates.club';

// ─── Test users (параметризация) ─────────────────────────────────────────────

// Загружаем тестовых пользователей из JSON-файла.
// Если файл не задан, используем заглушку (тест пройдёт, но API вернёт 401).
const USERS = (() => {
  try {
    return new SharedArray('users', function () {
      return JSON.parse(open(__ENV.USERS_FILE || './users.json'));
    });
  } catch (_) {
    // Заглушка для запуска без файла данных
    return new SharedArray('users', function () {
      return Array.from({ length: 200 }, (_, i) => ({
        user_id:      String(1000 + i),
        access_token: `test_token_${1000 + i}`,
        contacts:     [String(1001 + i), String(1002 + i), String(1003 + i)],
      }));
    });
  }
})();

// ─── Профиль нагрузки ─────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    // REST-нагрузка: сообщения, статусы, история чатов
    rest_users: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '5m',  target: 2000 },   // плавный старт
        { duration: '20m', target: 2000 },   // стабильный plateau
        { duration: '5m',  target: 0    },   // завершение
      ],
      gracefulRampDown:  '30s',
    },
    // WebSocket-нагрузка: постоянные соединения (реальные пользователи онлайн)
    ws_users: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: '5m',  target: 800  },   // ~40% юзеров держат WS открытым
        { duration: '20m', target: 800  },
        { duration: '5m',  target: 0    },
      ],
      gracefulRampDown: '30s',
      exec:             'wsScenario',
    },
  },

  thresholds: {
    // Время ответа HTTP
    'http_req_duration':                     ['p(95)<500', 'p(99)<1500'],
    // Процент ошибок HTTP
    'http_req_failed':                       ['rate<0.01'],
    // Кастомные метрики
    'msg_send_duration':                     ['p(95)<600'],
    'msg_get_duration':                      ['p(95)<500'],
    'ws_connection_errors':                  ['count<50'],
    // Процент неудачных проверок
    'checks':                                ['rate>0.99'],
  },

  // Ограничение трафика: не давим слишком сильно на HAProxy сразу
  noConnectionReuse: false,
  discardResponseBodies: false,
};

// ─── Кастомные метрики ────────────────────────────────────────────────────────

const msgSendDuration = new Trend('msg_send_duration');
const msgGetDuration  = new Trend('msg_get_duration');
const wsErrors        = new Counter('ws_connection_errors');
const apiErrors       = new Rate('api_error_rate');

// ─── Вспомогательные функции ─────────────────────────────────────────────────

function headers(token) {
  return {
    'Content-Type': 'application/json',
    'access-token':  token,
  };
}

function fakeText() {
  const msgs = [
    'Привет, как дела?', 'Когда встретимся?', 'Окей, понял',
    'Скинь ссылку', 'Жди, скоро напишу', 'Уже сделал',
    'Созвонимся в 19:00?', 'Ладно, договорились', 'Спасибо!',
    'Не забудь напомнить', 'Принял, разберусь', 'Хорошо',
  ];
  return msgs[Math.floor(Math.random() * msgs.length)];
}

function fakeEncryptedPayload() {
  // Имитируем base64-зашифрованный AES-256-GCM текст реальной длины
  return btoa(fakeText() + ':' + Math.random().toString(36).slice(2));
}

// Получить данные случайного пользователя из пула
function pickUser() {
  return USERS[randomIntBetween(0, USERS.length - 1)];
}

// ─── Действия пользователя ───────────────────────────────────────────────────

/**
 * Получить историю чата с контактом (пагинация — первая страница)
 */
function actionGetChat(user, contactId) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/node/chat/get`,
    JSON.stringify({ user_id: contactId, limit: 30, offset: 0 }),
    { headers: headers(user.access_token), tags: { name: 'chat_get' }, timeout: '10s' }
  );
  msgGetDuration.add(Date.now() - start);
  apiErrors.add(res.status >= 400);

  check(res, {
    'chat/get 200':      (r) => r.status === 200,
    'chat/get has body': (r) => r.body && r.body.length > 0,
  });
}

/**
 * Отправить сообщение через REST API
 */
function actionSendMessage(user, contactId) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/node/chat/send`,
    JSON.stringify({
      to:           contactId,
      text:         fakeEncryptedPayload(),
      text_ecb:     fakeEncryptedPayload(),
      iv:           btoa(Math.random().toString(36).slice(2, 14)),
      tag:          btoa(Math.random().toString(36).slice(2, 18)),
      cipher_version: 2,
      type:         'text',
    }),
    { headers: headers(user.access_token), tags: { name: 'chat_send' }, timeout: '10s' }
  );
  msgSendDuration.add(Date.now() - start);
  apiErrors.add(res.status >= 400);

  check(res, {
    'chat/send 200': (r) => r.status === 200 || r.status === 201,
  });
}

/**
 * Пометить сообщения как прочитанные
 */
function actionMarkSeen(user, contactId) {
  const res = http.post(
    `${BASE_URL}/api/node/chat/seen`,
    JSON.stringify({ user_id: contactId }),
    { headers: headers(user.access_token), tags: { name: 'chat_seen' }, timeout: '5s' }
  );
  apiErrors.add(res.status >= 400);
  check(res, { 'chat/seen ok': (r) => r.status === 200 });
}

/**
 * Индикатор «печатает»
 */
function actionTyping(user, contactId) {
  http.post(
    `${BASE_URL}/api/node/chat/typing`,
    JSON.stringify({ user_id: contactId, status: 'typing' }),
    { headers: headers(user.access_token), tags: { name: 'chat_typing' }, timeout: '3s' }
  );
}

// ─── Health check в setup() ───────────────────────────────────────────────────

export function setup() {
  const res = http.get(`${BASE_URL}/api/health`, { timeout: '5s' });
  check(res, { 'Server is UP': (r) => r.status === 200 });
  console.log(`[setup] Health: ${res.status} | Body: ${res.body?.substring(0, 120)}`);
}

// ─── Основной сценарий (REST) ─────────────────────────────────────────────────

export default function () {
  const user = pickUser();
  if (!user || !user.contacts || user.contacts.length === 0) return;

  const contact = randomItem(user.contacts);

  // Сессия пользователя: реалистичная последовательность действий
  group('open_chat', () => {
    actionGetChat(user, contact);
    sleep(randomIntBetween(1, 3));
  });

  group('typing_and_send', () => {
    actionTyping(user, contact);
    sleep(randomIntBetween(2, 6));  // имитация набора текста
    actionSendMessage(user, contact);
    sleep(randomIntBetween(1, 2));
  });

  group('read_messages', () => {
    actionMarkSeen(user, contact);
    sleep(randomIntBetween(1, 3));
  });

  // Иногда читаем ещё один чат (многозадачность)
  if (Math.random() < 0.3 && user.contacts.length > 1) {
    const contact2 = randomItem(user.contacts.filter(c => c !== contact));
    group('check_second_chat', () => {
      actionGetChat(user, contact2);
      sleep(randomIntBetween(5, 15));
    });
  }

  // Пауза между итерациями — имитация «пользователь читает, не пишет»
  sleep(randomIntBetween(15, 45));
}

// ─── WebSocket сценарий ───────────────────────────────────────────────────────

export function wsScenario() {
  const user = pickUser();
  if (!user) return;

  // Шаг 1: Polling handshake — получить sid
  const handshake = http.get(
    `${BASE_URL}/socket.io/?EIO=4&transport=polling`,
    { headers: headers(user.access_token), tags: { name: 'sio_handshake' }, timeout: '8s' }
  );

  if (handshake.status !== 200) {
    wsErrors.add(1);
    sleep(5);
    return;
  }

  let sid = null;
  try {
    const body      = handshake.body;
    const jsonStart = body.indexOf('{');
    if (jsonStart !== -1) {
      sid = JSON.parse(body.substring(jsonStart)).sid;
    }
  } catch (_) {
    wsErrors.add(1);
    return;
  }

  if (!sid) {
    wsErrors.add(1);
    return;
  }

  // Шаг 2: WebSocket upgrade
  const wsUrl = `${WS_BASE_URL}/socket.io/?EIO=4&transport=websocket&sid=${sid}`;
  let registered = false;
  let msgCount   = 0;

  const res = ws.connect(wsUrl, { headers: headers(user.access_token) }, function (socket) {
    // Upgrade probe
    socket.on('open', () => socket.send('2probe'));

    socket.on('message', (data) => {
      // Сервер подтвердил probe → завершаем апгрейд
      if (data === '3probe') {
        socket.send('5'); // upgrade complete
        // Регистрация в комнате
        socket.send(`42["user:register",{"user_id":"${user.user_id}","access_token":"${user.access_token}"}]`);
        registered = true;
        return;
      }

      // Ping — отвечаем Pong
      if (data === '2') {
        socket.send('3');
        return;
      }

      // Входящее событие — счётчик
      if (data.startsWith('42')) msgCount++;
    });

    // Держим соединение открытым ~2-5 минут (активный онлайн)
    const holdMs = randomIntBetween(2 * 60 * 1000, 5 * 60 * 1000);
    socket.setTimeout(() => {
      // Периодически шлём typing-эвент пока сидим онлайн
      if (registered && user.contacts.length > 0) {
        const peer = randomItem(user.contacts);
        socket.send(`42["typing",{"user_id":"${peer}"}]`);
        sleep(randomIntBetween(3, 8));
        socket.send(`42["typing:done",{"user_id":"${peer}"}]`);
      }
      socket.close();
    }, holdMs);

    socket.on('error', (e) => wsErrors.add(1));
  });

  check(res, { 'ws upgraded (101)': (r) => r && r.status === 101 });
  check(registered, { 'ws registered': (v) => v === true });
}
