/**
 * helpers.js — Shared utilities for all k6 stress test scripts
 * WorldMates Messenger v2 — Stress Test Suite
 *
 * Socket.IO Protocol 4 (Engine.IO v4) implementation for k6
 */

import http from 'k6/http';
import ws   from 'k6/ws';
import { check } from 'k6';

// ─── Constants ────────────────────────────────────────────────────────────────

export const BASE_URL    = 'https://worldmates.club';
export const WS_BASE_URL = 'wss://worldmates.club';

// Socket.IO EIO4 packet types
export const EIO = {
  OPEN:    '0',
  CLOSE:   '1',
  PING:    '2',
  PONG:    '3',
  MESSAGE: '4',
  UPGRADE: '5',
  NOOP:    '6',
};

// Socket.IO packet types (sit inside EIO MESSAGE = '4')
export const SIO = {
  CONNECT:    '0',
  DISCONNECT: '1',
  EVENT:      '2',
  ACK:        '3',
  ERROR:      '4',
  BINARY_EVENT: '5',
};

// HAProxy has 1 h tunnel timeout → long-lived WebSocket is fine
export const WS_TIMEOUT_MS = 55 * 60 * 1000; // 55 min safety margin

// Common HTTP headers
export function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'access-token': token,
  };
}

// ─── REST helpers ─────────────────────────────────────────────────────────────

/**
 * POST wrapper with auth + JSON body
 */
export function apiPost(path, payload, token, tags = {}) {
  const url = `${BASE_URL}${path}`;
  const res  = http.post(url, JSON.stringify(payload), {
    headers: authHeaders(token),
    tags,
    timeout: '15s',
  });
  return res;
}

/**
 * GET wrapper with auth
 */
export function apiGet(path, token, params = {}, tags = {}) {
  const url = `${BASE_URL}${path}`;
  const res  = http.get(url, {
    headers: authHeaders(token),
    params,
    tags,
    timeout: '10s',
  });
  return res;
}

/**
 * Health check — used as a quick liveness probe
 */
export function healthCheck() {
  return http.get(`${BASE_URL}/api/health`, { timeout: '5s' });
}

// ─── Socket.IO helpers ────────────────────────────────────────────────────────

/**
 * Perform the Engine.IO polling handshake to obtain a session ID (sid).
 * Returns sid string or null on failure.
 */
export function sioHandshake(token) {
  const res = http.get(
    `${BASE_URL}/socket.io/?EIO=4&transport=polling`,
    {
      headers: authHeaders(token),
      timeout: '10s',
      tags: { name: 'sio_handshake' },
    }
  );

  if (res.status !== 200) return null;

  try {
    // EIO4 response is: <length>\n<packetType><jsonPayload>  OR just <packetType><jsonPayload>
    // The body often looks like: 97{"sid":"...","upgrades":["websocket"],...}
    const body  = res.body;
    // Strip leading digits (length prefix)
    const jsonStart = body.indexOf('{');
    if (jsonStart === -1) return null;
    const parsed = JSON.parse(body.substring(jsonStart));
    return parsed.sid || null;
  } catch (_) {
    return null;
  }
}

/**
 * Build a Socket.IO WebSocket URL given the polling sid.
 */
export function sioWsUrl(sid) {
  return `${WS_BASE_URL}/socket.io/?EIO=4&transport=websocket&sid=${sid}`;
}

/**
 * Encode a Socket.IO event frame.
 * e.g. sioEncode('message:private', { to: '42', text: 'hi' })
 * → '42["message:private",{"to":"42","text":"hi"}]'
 */
export function sioEncode(event, data) {
  return `${EIO.MESSAGE}${SIO.EVENT}["${event}",${JSON.stringify(data)}]`;
}

/**
 * Full Socket.IO connect sequence + callback.
 * Handles ping/pong automatically.
 * userCallback(socket) is called once the socket is upgraded and registered.
 */
export function sioConnect(userId, token, userCallback) {
  const sid = sioHandshake(token);
  if (!sid) {
    console.warn(`[VU ${__VU}] Handshake failed for user ${userId}`);
    return false;
  }

  const url = sioWsUrl(sid);
  let upgraded = false;

  const res = ws.connect(url, { headers: authHeaders(token) }, function (socket) {
    socket.on('open', () => {
      // Step 1 – send WebSocket upgrade probe
      socket.send(`${EIO.PING}probe`);
    });

    socket.on('message', (data) => {
      // Pong heartbeat
      if (data === EIO.PING) {
        socket.send(EIO.PONG);
        return;
      }

      // Server confirms probe
      if (data === `${EIO.PONG}probe`) {
        socket.send(EIO.UPGRADE); // '5' — upgrade complete
        upgraded = true;

        // Register user in the socket room
        socket.send(sioEncode('user:register', {
          user_id:      userId,
          access_token: token,
        }));

        // Hand control to caller
        userCallback(socket);
        return;
      }

      // Socket.IO CONNECT ack: '40'
      if (data.startsWith(`${EIO.MESSAGE}${SIO.CONNECT}`)) {
        return; // normal, ignore
      }
    });

    socket.on('error', (e) => {
      console.warn(`[VU ${__VU}] WS error: ${e}`);
    });
  });

  check(res, { 'ws status 101': (r) => r && r.status === 101 });
  return upgraded;
}

// ─── Fake payload generators ──────────────────────────────────────────────────

const SAMPLE_MESSAGES = [
  'Привет! Как дела?',
  'Окей, понял тебя',
  'Скинь файл позже',
  'Созвонимся вечером?',
  'Хорошо, договорились',
  'Видел уведомление?',
  'Жди, сейчас напишу',
  'Всё норм, спасибо',
  'Можешь перезвонить?',
  'Принял, разберусь',
  'Отправляй, посмотрю',
  'Спасибо за инфо!',
  'Ладно, до завтра',
  'Не забудь напомнить',
  'Уже сделал, готово',
];

/**
 * Returns a random AES-256-GCM-like payload (base64 stub for load purposes).
 * Real clients encrypt before sending; here we send a realistic-length stub.
 */
export function fakeEncryptedText() {
  // Simulate 64-byte ciphertext + 12-byte IV → base64 ~102 chars
  const msg = SAMPLE_MESSAGES[Math.floor(Math.random() * SAMPLE_MESSAGES.length)];
  return btoa(msg + '|' + Math.random().toString(36).substring(2));
}

export function fakeIv() {
  // 12-byte IV in base64 = 16 chars
  return btoa(Math.random().toString(36).substring(2, 14));
}

export function fakeTag() {
  return btoa(Math.random().toString(36).substring(2, 18));
}

/**
 * Pick a random peer from the user's contact list.
 * contacts[] is an array of user_id strings.
 */
export function randomPeer(contacts, selfId) {
  const peers = contacts.filter(c => c !== selfId);
  return peers[Math.floor(Math.random() * peers.length)];
}

// ─── Think-time helpers ───────────────────────────────────────────────────────

import { sleep } from 'k6';

/** Short pause — simulates rapid interactions (typing, seen) */
export function thinkShort()  { sleep(Math.random() * 1 + 0.5);  }  // 0.5–1.5 s

/** Medium pause — simulates reading messages before replying */
export function thinkMedium() { sleep(Math.random() * 5 + 2);    }  // 2–7 s

/** Long pause — simulates user reading/going idle */
export function thinkLong()   { sleep(Math.random() * 20 + 10);  }  // 10–30 s
