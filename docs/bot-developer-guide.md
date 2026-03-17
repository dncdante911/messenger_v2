# WorldMates Bot API — Developer Guide

> For bot developers and administrators building integrations on the WorldMates platform.

---

## Table of Contents

1. [Overview](#overview)
2. [Creating Your First Bot](#creating-your-first-bot)
3. [Authentication](#authentication)
4. [Bot API Endpoints](#bot-api-endpoints)
   - [Bot Info](#bot-info)
   - [Sending Messages](#sending-messages)
   - [Receiving Updates](#receiving-updates)
   - [Webhooks](#webhooks)
   - [Keyboards & Buttons](#keyboards--buttons)
   - [Polls](#polls)
   - [Media](#media)
   - [User State (FSM)](#user-state-fsm)
   - [Chat Info](#chat-info)
5. [Bot Management API](#bot-management-api)
6. [Inline Bots](#inline-bots)
7. [RSS Feeds](#rss-feeds)
8. [Rate Limits](#rate-limits)
9. [Telegram Compatibility](#telegram-compatibility)
10. [Code Examples](#code-examples)

---

## Overview

The WorldMates Bot API is a REST API for building interactive bots inside the WorldMates messenger. The API is intentionally similar to the Telegram Bot API, so developers familiar with Telegram bots can migrate quickly.

**Base URL:** `https://your-server.com`

All bot operation endpoints start with `/api/node/bot/`.
Bot management endpoints (create, list, delete) start with `/api/node/bots/`.

---

## Creating Your First Bot

### Option A: Via WallyBot (recommended, no code needed)

1. Open the WorldMates app
2. Find **WallyBot** in the Bot Store or search `@wallybot`
3. Send `/newbot`
4. Follow the 3-step dialog:
   - Enter **display name** (e.g. "My Assistant")
   - Enter **username** (e.g. `my_assistant_bot` — must end in `_bot`)
   - Enter **description** (or `/skip`)
5. WallyBot returns your `bot_token` — save it securely!

### Option B: Via REST API

```bash
POST /api/node/bots
Authorization: Bearer <user_access_token>
Content-Type: application/json

{
  "username": "my_assistant_bot",
  "display_name": "My Assistant",
  "description": "Answers FAQs automatically",
  "category": "business"
}
```

**Response:**
```json
{
  "api_status": 200,
  "bot_id": "bot_a1b2c3d4e5f6",
  "bot_token": "bot_a1b2c3d4e5f6:abc123...",
  "username": "my_assistant_bot"
}
```

---

## Authentication

### User Token (managing bots)

Used for **creating, editing, deleting** bots. Pass the user's session token:

```
access-token: <user_session_token>
```

### Bot Token (sending messages, webhooks)

Used for **all bot operations**. Pass the bot token in the header:

```
bot-token: <bot_id>:<secret_hash>
```

Or as a query parameter:
```
?bot_token=<bot_id>:<secret_hash>
```

---

## Bot API Endpoints

### Bot Info

#### `GET /api/node/bot/getMe`

Returns information about the authenticated bot.

```bash
curl -H "bot-token: bot_abc:xyz" \
  https://your-server.com/api/node/bot/getMe
```

```json
{
  "ok": true,
  "result": {
    "bot_id": "bot_abc",
    "username": "my_assistant_bot",
    "display_name": "My Assistant",
    "description": "Answers FAQs",
    "status": "active",
    "is_inline": false
  }
}
```

---

### Sending Messages

#### `POST /api/node/bot/sendMessage`

Send a text message to a user or group.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `chat_id` | int/string | ✅ | User ID or group ID |
| `text` | string | ✅ | Message text |
| `reply_markup` | object | ❌ | Inline keyboard |
| `parse_mode` | string | ❌ | `Markdown` or `HTML` |

```bash
curl -X POST -H "bot-token: bot_abc:xyz" \
  -H "Content-Type: application/json" \
  -d '{"chat_id": 42, "text": "Hello from my bot!"}' \
  https://your-server.com/api/node/bot/sendMessage
```

```json
{ "api_status": 200, "message_id": 1234, "ok": true }
```

#### `POST /api/node/bot/editMessage`

Edit a previously sent message.

| Parameter | Type | Required |
|-----------|------|----------|
| `message_id` | int | ✅ |
| `text` | string | ✅ |
| `reply_markup` | object | ❌ |

#### `POST /api/node/bot/deleteMessage`

| Parameter | Type | Required |
|-----------|------|----------|
| `message_id` | int | ✅ |

---

### Receiving Updates

There are two ways to receive incoming messages: **polling** and **webhooks**.

#### Polling — `POST /api/node/bot/getUpdates`

| Parameter | Type | Description |
|-----------|------|-------------|
| `offset` | int | Process only updates after this ID |
| `limit` | int | Max 100, default 20 |
| `timeout` | int | Long-poll seconds (max 30) |

```bash
curl -X POST -H "bot-token: bot_abc:xyz" \
  -d '{"offset": 0, "limit": 10}' \
  https://your-server.com/api/node/bot/getUpdates
```

```json
{
  "api_status": 200,
  "updates": [
    {
      "id": 55,
      "from": { "id": 42, "username": "john_doe" },
      "chat": { "id": 42, "type": "private" },
      "text": "/start",
      "date": 1710000000
    }
  ]
}
```

---

### Webhooks

Webhooks deliver updates to your server in real-time via HTTP POST.

#### `POST /api/node/bot/setWebhook`

| Parameter | Type | Required |
|-----------|------|----------|
| `url` | string | ✅ — must be HTTPS |
| `secret_token` | string | ❌ — included in `X-Bot-Signature` header |

```json
{ "url": "https://myservice.com/webhook", "secret_token": "mysecret123" }
```

The server sends HMAC-SHA256 signature in header `X-Bot-Signature` — verify it on your side.

#### `POST /api/node/bot/deleteWebhook`
#### `GET /api/node/bot/getWebhookInfo`

---

### Keyboards & Buttons

#### Inline Keyboard

```json
{
  "text": "Choose an option:",
  "reply_markup": {
    "inline_keyboard": [
      [
        { "text": "Option A", "callback_data": "option_a" },
        { "text": "Option B", "callback_data": "option_b" }
      ],
      [
        { "text": "Visit site", "url": "https://example.com" }
      ]
    ]
  }
}
```

#### Answering a Button Click — `POST /api/node/bot/answerCallbackQuery`

| Parameter | Type | Required |
|-----------|------|----------|
| `callback_query_id` | string | ✅ |
| `text` | string | ❌ — toast notification text |
| `show_alert` | bool | ❌ — show as alert dialog |

---

### Polls

#### `POST /api/node/bot/sendPoll`

| Parameter | Type | Required |
|-----------|------|----------|
| `chat_id` | int | ✅ |
| `question` | string | ✅ |
| `options` | string[] | ✅ — 2–10 options |
| `is_anonymous` | bool | ❌ |
| `allows_multiple_answers` | bool | ❌ |

```json
{
  "chat_id": 42,
  "question": "What is your favourite feature?",
  "options": ["Voice messages", "Secret chats", "Bot Store"]
}
```

#### `POST /api/node/bot/stopPoll`

| Parameter | Type | Required |
|-----------|------|----------|
| `poll_id` | int | ✅ |

---

### Media

All media endpoints follow the same pattern. Pass the URL of the media file.

| Endpoint | Required field | Description |
|----------|----------------|-------------|
| `POST /api/node/bot/sendPhoto` | `photo` | Image URL |
| `POST /api/node/bot/sendDocument` | `document` | File URL |
| `POST /api/node/bot/sendAudio` | `audio` | Audio URL |
| `POST /api/node/bot/sendVideo` | `video` | Video URL |

Optional: `caption`, `reply_markup`.

```json
{
  "chat_id": 42,
  "photo": "https://cdn.example.com/image.jpg",
  "caption": "Check this out!"
}
```

#### Send Location — `POST /api/node/bot/sendLocation`

```json
{ "chat_id": 42, "latitude": 50.4501, "longitude": 30.5234 }
```

#### Send Contact — `POST /api/node/bot/sendContact`

```json
{
  "chat_id": 42,
  "phone_number": "+380501234567",
  "first_name": "Support",
  "last_name": "Team"
}
```

#### Forward Message — `POST /api/node/bot/forwardMessage`

```json
{ "chat_id": 42, "from_chat_id": 42, "message_id": 1234 }
```

#### Typing Indicator — `POST /api/node/bot/sendChatAction`

```json
{ "chat_id": 42, "action": "typing" }
```

Actions: `typing`, `upload_photo`, `record_audio`, `upload_document`.

---

### User State (FSM)

Store per-user conversation state on the server (key-value store).

#### `POST /api/node/bot/setUserState`

```json
{ "user_id": 42, "state": "awaiting_email", "data": { "step": 2 } }
```

#### `GET /api/node/bot/getUserState?user_id=42`

```json
{ "state": "awaiting_email", "data": { "step": 2 } }
```

---

### Chat Info

#### `GET /api/node/bot/getChat?chat_id=42`

Returns user/group profile.

#### `GET /api/node/bot/getChatMember?chat_id=42&user_id=42`

Returns user's membership status in a group.

---

### Bot Commands

#### `POST /api/node/bot/setCommands`

```json
{
  "commands": [
    { "command": "start",   "description": "Start the bot" },
    { "command": "help",    "description": "Help & commands" },
    { "command": "weather", "description": "Get current weather" }
  ]
}
```

#### `GET /api/node/bot/getCommands`

---

## Bot Management API

These endpoints require a **user access token** (not bot token).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/node/bots` | Create a new bot |
| `GET` | `/api/node/bots` | List your bots |
| `GET` | `/api/node/bots/:bot_id` | Get bot info (public) |
| `PUT` | `/api/node/bots/:bot_id` | Update bot settings |
| `DELETE` | `/api/node/bots/:bot_id` | Delete a bot |
| `POST` | `/api/node/bots/:bot_id/regenerate-token` | Regenerate token |
| `GET` | `/api/node/bots/search?q=weather` | Search public bots |

#### Editable bot fields (PUT):

```json
{
  "display_name": "Weather Bot Pro",
  "description": "Real-time weather forecasts",
  "about": "Powered by open weather API",
  "category": "tools",
  "is_public": true,
  "can_join_groups": true,
  "is_inline": false,
  "status": "active"
}
```

---

## Inline Bots

Inline bots let users query your bot directly from any chat input field by typing `@your_bot_name query`.

#### Flow:

1. User types `@your_bot_name weather kyiv` in any chat
2. Server sends `inline_query` event to your bot via `getUpdates` or webhook
3. Your bot responds with `answerInlineQuery`
4. User picks a result — it's sent to the chat as a message

#### `POST /api/node/bot/answerInlineQuery`

```json
{
  "inline_query_id": "abc123",
  "results": [
    {
      "id": "1",
      "type": "article",
      "title": "Kyiv: +12°C, Partly cloudy",
      "description": "Wind: 5 m/s, Humidity: 72%",
      "input_message_content": {
        "message_text": "🌤 Kyiv: +12°C, Partly cloudy\nWind: 5 m/s | Humidity: 72%"
      }
    }
  ]
}
```

Enable inline mode when creating/updating your bot: `"is_inline": true`.

---

## RSS Feeds

Your bot can automatically post new RSS/Atom feed items to subscribed users.

#### `GET /api/node/bots/:bot_id/rss` — list feeds
#### `POST /api/node/bots/:bot_id/rss` — add feed

```json
{
  "url": "https://example.com/feed.xml",
  "name": "Tech News",
  "interval": 60,
  "target_chat_id": 42
}
```

#### `PUT /api/node/bots/:bot_id/rss/:feed_id` — update feed
#### `DELETE /api/node/bots/:bot_id/rss/:feed_id` — remove feed

---

## Rate Limits

| Scope | Limit |
|-------|-------|
| Global (all endpoints) | 300 req / min per IP |
| Auth endpoints | 15 req / 15 min per IP |
| Bot sendMessage | No per-bot hard limit currently |

If a rate limit is exceeded the server returns HTTP 429.

---

## Telegram Compatibility

The WorldMates Bot API is designed to be **structurally compatible** with Telegram Bot API. The following differences apply:

| Feature | Telegram | WorldMates |
|---------|----------|------------|
| Base URL | `api.telegram.org/bot<token>` | `your-server.com/api/node/bot` |
| Token header | Query param `?token=` | Header `bot-token:` |
| Response format | `{ ok: true, result: {} }` | `{ api_status: 200, ... }` |
| Chat IDs | Negative for groups | Positive int for all |
| File upload | multipart/form-data | URL string |
| `sendMessage` | ✅ | ✅ |
| `sendPhoto/Video/Audio` | ✅ | ✅ |
| `sendLocation/Contact` | ✅ | ✅ |
| `forwardMessage` | ✅ | ✅ |
| `sendChatAction` | ✅ | ✅ |
| `getChat/getChatMember` | ✅ | ✅ |
| Webhooks | ✅ | ✅ |
| Inline mode | ✅ | ✅ |
| Polls | ✅ | ✅ |
| `setCommands` | ✅ | ✅ |

---

## Code Examples

### JavaScript / Node.js

```js
const BASE = 'https://your-server.com/api/node/bot';
const TOKEN = 'bot_abc123:yoursecrettoken';

async function sendMessage(chatId, text) {
  const res = await fetch(`${BASE}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'bot-token': TOKEN },
    body: JSON.stringify({ chat_id: chatId, text })
  });
  return res.json();
}

// Long-polling loop
async function startPolling() {
  let offset = 0;
  while (true) {
    const { updates = [] } = await fetch(`${BASE}/getUpdates`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'bot-token': TOKEN },
      body: JSON.stringify({ offset, limit: 20, timeout: 10 })
    }).then(r => r.json());

    for (const upd of updates) {
      offset = upd.id + 1;
      if (upd.text === '/start') {
        await sendMessage(upd.from.id, 'Welcome! Send /help for commands.');
      }
    }
  }
}

startPolling().catch(console.error);
```

### Python

```python
import requests

BASE = 'https://your-server.com/api/node/bot'
TOKEN = 'bot_abc123:yoursecrettoken'
HEADERS = {'bot-token': TOKEN}

def send_message(chat_id, text, reply_markup=None):
    payload = {'chat_id': chat_id, 'text': text}
    if reply_markup:
        payload['reply_markup'] = reply_markup
    return requests.post(f'{BASE}/sendMessage', json=payload, headers=HEADERS).json()

def get_updates(offset=0, limit=20, timeout=10):
    return requests.post(f'{BASE}/getUpdates',
        json={'offset': offset, 'limit': limit, 'timeout': timeout},
        headers=HEADERS).json()

# Echo bot
offset = 0
while True:
    data = get_updates(offset)
    for upd in data.get('updates', []):
        offset = upd['id'] + 1
        if 'text' in upd:
            send_message(upd['from']['id'], f"You said: {upd['text']}")
```

### cURL (quick test)

```bash
# Send a message
curl -X POST https://your-server.com/api/node/bot/sendMessage \
  -H "bot-token: bot_abc123:yoursecrettoken" \
  -H "Content-Type: application/json" \
  -d '{"chat_id": 42, "text": "Hello from curl!"}'

# Get updates
curl -X POST https://your-server.com/api/node/bot/getUpdates \
  -H "bot-token: bot_abc123:yoursecrettoken" \
  -d '{"limit": 5}'
```

---

## Webhook Example (Express.js)

```js
const express = require('express');
const crypto  = require('crypto');
const app     = express();
app.use(express.json());

const SECRET = 'mysecret123';
const TOKEN  = 'bot_abc123:yoursecrettoken';

// Register webhook on startup
fetch('https://your-server.com/api/node/bot/setWebhook', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'bot-token': TOKEN },
  body: JSON.stringify({
    url: 'https://myservice.com/webhook',
    secret_token: SECRET
  })
});

app.post('/webhook', (req, res) => {
  // Verify signature
  const sig = req.headers['x-bot-signature'];
  const expected = crypto.createHmac('sha256', SECRET)
    .update(JSON.stringify(req.body)).digest('hex');
  if (sig !== expected) return res.sendStatus(403);

  const upd = req.body;
  if (upd.text === '/start') {
    // reply
    fetch('https://your-server.com/api/node/bot/sendMessage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'bot-token': TOKEN },
      body: JSON.stringify({ chat_id: upd.from.id, text: 'Welcome!' })
    });
  }

  res.sendStatus(200);
});

app.listen(3000);
```

---

*Last updated: 2026-03-17 | WorldMates Platform*
