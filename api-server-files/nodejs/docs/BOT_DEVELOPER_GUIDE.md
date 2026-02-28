# WorldMates Bot API — Руководство для разработчиков и администраторов

> **Версия API:** 2.0 (Node.js)
> **Base URL:** `https://worldmates.club`
> **Node.js endpoint:** `/api/node/bot/*` и `/api/node/bots/*`

---

## Содержание

1. [Обзор системы](#1-обзор-системы)
2. [Архитектура ботов](#2-архитектура-ботов)
3. [Создание первого бота](#3-создание-первого-бота)
4. [REST API — Управление ботами](#4-rest-api--управление-ботами)
5. [REST API — Операции бота](#5-rest-api--операции-бота)
6. [WebSocket API](#6-websocket-api)
7. [Node.js SDK](#7-nodejs-sdk)
8. [Webhook](#8-webhook)
9. [Состояния пользователей (FSM)](#9-состояния-пользователей-fsm)
10. [Опросы (Polls)](#10-опросы-polls)
11. [База данных](#11-база-данных)
12. [WallyBot — встроенный бот-менеджер](#12-wallybot--встроенный-бот-менеджер)
13. [Инфраструктура и деплой](#13-инфраструктура-и-деплой)
14. [Примеры ботов](#14-примеры-ботов)

---

## 1. Обзор системы

Система ботов WorldMates реализована полностью на **Node.js** и работает без PHP. Архитектура:

```
Пользователь (Android/Web)
        │
        ▼
Socket.IO (порт 449)        REST API (порт 449)
        │                           │
        ▼                           ▼
  bots-listener.js         routes/bots/index.js
        │                           │
        ▼                           ▼
  BotXxxController.js      Sequelize → MySQL
        │
        ▼
  botSockets Map (внутренние/WebSocket боты)
        │
        ├── WallyBot (встроенный)
        └── Внешние боты (via /bots namespace)
```

**Два способа создания ботов:**
1. **Через WallyBot** (в чате) — для пользователей и быстрого старта
2. **Через REST API** — для разработчиков, интеграций

**Два режима работы бота:**
1. **WebSocket** — бот подключается к `/bots` namespace Socket.IO, получает сообщения мгновенно
2. **REST Polling/Webhook** — бот опрашивает `/api/node/bot/getUpdates` или принимает webhook

---

## 2. Архитектура ботов

### Таблицы базы данных

| Таблица | Описание |
|---------|----------|
| `Wo_Bots` | Основная информация о ботах |
| `Wo_Bot_Commands` | Зарегистрированные команды бота |
| `Wo_Bot_Messages` | История сообщений (входящие и исходящие) |
| `Wo_Bot_Users` | Пользователи бота + состояния FSM |
| `Wo_Bot_Callbacks` | Обработанные callback queries |
| `Wo_Bot_Polls` | Опросы |
| `Wo_Bot_Poll_Options` | Варианты ответа |
| `Wo_Bot_Poll_Votes` | Голоса |
| `Wo_Bot_Webhook_Log` | Лог доставки webhook |
| `Wo_Bot_Tasks` | Задачи + база знаний WallyBot |
| `Wo_Bot_Rate_Limits` | Лимиты запросов |

### Аутентификация

**Управление ботами** (от имени пользователя):
```
Header: access-token: <user_session_id>
// или в теле запроса: access_token=<...>
```

**Операции бота** (от имени самого бота):
```
Header: bot-token: <bot_id>:<token_hash>
// или в теле запроса: bot_token=<...>
```

---

## 3. Создание первого бота

### Через WallyBot (рекомендуется)
1. Откройте чат с `@wallybot` в мессенджере
2. Напишите `/newbot`
3. Следуйте инструкциям диалога (3 шага)
4. Получите Bot ID и токен

### Через REST API

```bash
# Создание бота
curl -X POST https://worldmates.club/api/node/bots \
  -H "access-token: ВАШ_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "my_test_bot",
    "display_name": "Мой тестовый бот",
    "description": "Описание бота",
    "category": "utility"
  }'
```

**Ответ:**
```json
{
  "api_status": 200,
  "bot": {
    "bot_id": "bot_abc123def456",
    "bot_token": "bot_abc123def456:sha256hash...",
    "username": "my_test_bot",
    "display_name": "Мой тестовый бот",
    "status": "active"
  },
  "message": "Бот создан. Сохраните bot_token — он больше не будет показан."
}
```

> ⚠️ **ВАЖНО:** Сохраните `bot_token`! Он отображается только один раз при создании.

---

## 4. REST API — Управление ботами

> Все эндпоинты управления требуют `access-token` пользователя.

### POST /api/node/bots — Создать бота

| Поле | Тип | Обязательно | Описание |
|------|-----|-------------|----------|
| `username` | string | ✅ | Должен заканчиваться на `_bot`, 5-32 символа |
| `display_name` | string | ✅ | Отображаемое имя |
| `description` | string | — | Краткое описание (видно пользователям) |
| `about` | string | — | Подробное описание |
| `category` | string | — | Категория: general, utility, entertainment, news, support |
| `is_public` | int | — | 1 — публичный (виден в поиске), 0 — приватный |
| `can_join_groups` | int | — | 1 — может быть добавлен в группы |

### GET /api/node/bots — Список ботов пользователя

Параметры: `?limit=20&offset=0`

### GET /api/node/bots/:bot_id — Информация о боте

Публичный эндпоинт (токен скрыт). Владелец видит полную информацию.

### PUT /api/node/bots/:bot_id — Обновить бота

Изменяемые поля: `display_name`, `description`, `about`, `category`, `is_public`, `can_join_groups`, `status`

### DELETE /api/node/bots/:bot_id — Удалить бота

Каскадное удаление всех данных бота.

### POST /api/node/bots/:bot_id/regenerate-token — Обновить токен

Старый токен немедленно становится недействительным.

### GET /api/node/bots/search — Поиск ботов

Параметры: `?q=текст&limit=20&offset=0`

---

## 5. REST API — Операции бота

> Все эндпоинты операций требуют `bot-token` бота.

### GET /api/node/bot/getMe
Информация о боте (для самого бота).

---

### POST /api/node/bot/sendMessage — Отправить сообщение

```json
{
  "chat_id": 123,
  "text": "Привет!",
  "reply_markup": {
    "inline_keyboard": [
      [{"text": "Кнопка 1", "callback_data": "btn1"}],
      [{"text": "Ссылка", "url": "https://example.com"}]
    ]
  }
}
```

**Ответ:**
```json
{"api_status": 200, "message_id": 456, "ok": true}
```

---

### POST /api/node/bot/getUpdates — Получить обновления (polling)

```json
{
  "offset": 0,
  "limit": 20,
  "timeout": 5
}
```

`timeout` — секунды long polling (0 = мгновенный ответ, макс 30).

**Ответ:**
```json
{
  "api_status": 200,
  "updates": [
    {
      "update_id": 1,
      "update_type": "message",
      "message": {
        "message_id": 1,
        "from": {"id": 123},
        "chat": {"id": "123", "type": "private"},
        "date": 1700000000,
        "text": "Привет!"
      }
    }
  ],
  "count": 1
}
```

**Типы обновлений:** `message`, `command`, `callback_query`

---

### POST /api/node/bot/editMessage — Редактировать сообщение

```json
{
  "chat_id": 123,
  "message_id": 456,
  "text": "Новый текст"
}
```

---

### POST /api/node/bot/deleteMessage — Удалить сообщение

```json
{"chat_id": 123, "message_id": 456}
```

---

### POST /api/node/bot/answerCallbackQuery — Ответить на кнопку

```json
{
  "callback_query_id": "12345",
  "text": "Действие выполнено!",
  "show_alert": false
}
```

---

### POST /api/node/bot/setCommands — Установить команды

```json
{
  "commands": [
    {"command": "start",  "description": "Начать работу"},
    {"command": "help",   "description": "Помощь"},
    {"command": "status", "description": "Статус заказа", "usage_hint": "/status <номер>"}
  ]
}
```

### GET /api/node/bot/getCommands — Получить команды

---

## 6. WebSocket API

Для высоконагруженных ботов рекомендуется WebSocket подключение к namespace `/bots`.

### Подключение (Socket.IO)

```javascript
const { io } = require('socket.io-client');

const socket = io('https://worldmates.club/bots', {
  transports: ['websocket']
});

// Аутентификация
socket.emit('bot_auth', {
  bot_id:    'bot_abc123',
  bot_token: 'bot_abc123:ваш_токен'
});

socket.on('auth_success', (data) => {
  console.log('Бот подключён:', data.display_name);
});

// Получение сообщений от пользователей
socket.on('user_message', (data) => {
  const { user_id, text, is_command, command_name } = data;

  if (is_command && command_name === 'start') {
    socket.emit('bot_message', {
      bot_id:  'bot_abc123',
      chat_id: user_id,
      text:    'Привет! Я бот WorldMates!'
    });
  }
});

// Отправка сообщения пользователю
socket.emit('bot_message', {
  bot_id:       'bot_abc123',
  chat_id:      123,
  text:         'Привет!',
  reply_markup: {
    inline_keyboard: [[
      { text: 'Нажми меня', callback_data: 'click' }
    ]]
  }
});

// Индикатор печати
socket.emit('bot_typing', {
  bot_id:  'bot_abc123',
  chat_id: 123
});
```

### События бота (исходящие через `/bots` namespace)

| Событие | Данные | Описание |
|---------|--------|----------|
| `bot_auth` | `{bot_id, bot_token}` | Аутентификация |
| `bot_message` | `{bot_id, chat_id, text, reply_markup, media}` | Отправить сообщение |
| `bot_typing` | `{bot_id, chat_id}` | Показать индикатор печати |
| `callback_answer` | `{callback_query_id, text, show_alert}` | Ответить на кнопку |
| `update_markup` | `{bot_id, chat_id, message_id, reply_markup}` | Обновить клавиатуру |

### События от сервера (входящие)

| Событие | Описание |
|---------|----------|
| `auth_success` | Успешная аутентификация |
| `auth_error` | Ошибка аутентификации |
| `user_message` | Сообщение от пользователя |
| `bot_error` | Ошибка операции |

---

## 7. Node.js SDK

Файл: `api-server-files/nodejs/bot-sdk/WorldMatesBotSDK.js`

### Установка

```bash
# SDK входит в состав Node.js сервера
# Для внешнего бота скопируй WorldMatesBotSDK.js в свой проект
```

### Полный пример

```javascript
const { WorldMatesBot } = require('./WorldMatesBotSDK');

const bot = new WorldMatesBot({
  botId:  'bot_ваш_id',
  token:  'bot_ваш_id:ваш_токен',
  debug:  true   // включить логи
});

// Команда /start
bot.onCommand('start', async ({ message, bot }) => {
  const keyboard = WorldMatesBot.buildInlineKeyboard([
    WorldMatesBot.callbackButton('О боте',  'about'),
    WorldMatesBot.callbackButton('Помощь',  'help'),
    WorldMatesBot.urlButton('Сайт', 'https://worldmates.club')
  ]);

  await bot.sendMessageWithKeyboard(
    message.from.id,
    '**Привет!** Я тестовый бот WorldMates.\n\nЧем могу помочь?',
    keyboard
  );
});

// Команда /help
bot.onCommand('help', async ({ message, bot }) => {
  await bot.sendMessage(message.from.id,
    'Доступные команды:\n/start — начало\n/help — помощь'
  );
});

// Нажатие кнопки
bot.onCallbackQuery(async ({ callback, bot }) => {
  await bot.answerCallbackQuery(callback.id, 'Обработано!');

  if (callback.data === 'about') {
    await bot.sendMessage(callback.from.id, 'Это тестовый бот WorldMates v2');
  }
});

// FSM пример: многошаговый диалог
bot.onCommand('register', async ({ message, bot }) => {
  await bot.setUserState(message.from.id, 'awaiting_name');
  await bot.sendMessage(message.from.id, 'Как тебя зовут?');
});

bot.onState('awaiting_name', async ({ message, bot }) => {
  const name = message.text;
  await bot.setUserState(message.from.id, 'awaiting_email', { name });
  await bot.sendMessage(message.from.id, `Привет, ${name}! Введи email:`);
});

bot.onState('awaiting_email', async ({ message, stateData, bot }) => {
  await bot.clearUserState(message.from.id);
  await bot.sendMessage(message.from.id,
    `Регистрация завершена!\nИмя: ${stateData.name}\nEmail: ${message.text}`
  );
});

// Все остальные сообщения
bot.onMessage(async ({ message, bot }) => {
  await bot.sendMessage(message.from.id, `Ты написал: "${message.text}"`);
});

// Запуск (polling режим)
bot.start();

// Или webhook режим:
// const express = require('express');
// const app = express();
// app.use(express.json());
// app.post('/bot-webhook', bot.webhookHandler());
// app.listen(3001);
// await bot.setWebhook('https://my-server.com/bot-webhook');
```

---

## 8. Webhook

Webhook позволяет получать обновления в реальном времени без постоянного опроса.

### Установка webhook

```bash
curl -X POST https://worldmates.club/api/node/bot/setWebhook \
  -H "bot-token: ВАШ_ТОКЕН" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://my-server.com/webhook",
    "secret": "мой-секрет-для-верификации",
    "allowed_updates": ["message", "command", "callback_query"]
  }'
```

### Обработка webhook запросов

```javascript
const express = require('express');
const crypto  = require('crypto');
const app     = express();
app.use(express.json());

const WEBHOOK_SECRET = 'мой-секрет-для-верификации';

app.post('/webhook', (req, res) => {
  // Верификация подписи
  const signature = req.headers['x-worldmates-bot-signature'];
  const expected  = 'sha256=' + crypto
    .createHmac('sha256', WEBHOOK_SECRET)
    .update(JSON.stringify(req.body))
    .digest('hex');

  if (signature !== expected) {
    return res.status(403).send('Forbidden');
  }

  // Обработка обновления
  const update = req.body;
  console.log('Update type:', update.update_type);

  if (update.update_type === 'command') {
    // sendMessage(update.message.from.id, 'Привет!');
  }

  res.status(200).json({ ok: true }); // Важно ответить 200 сразу!
});

app.listen(3001);
```

### Формат webhook payload

```json
{
  "update_id": 123,
  "update_type": "command",
  "bot_id": "bot_abc123",
  "message": {
    "message_id": 456,
    "from": {
      "id": 789,
      "username": "username",
      "first_name": "Иван"
    },
    "chat": {"id": "789", "type": "private"},
    "date": 1700000000,
    "text": "/start"
  },
  "command": {
    "name": "start",
    "args": ""
  }
}
```

### Проверка webhook

```bash
curl https://worldmates.club/api/node/bot/getWebhookInfo \
  -H "bot-token: ВАШ_ТОКЕН"
```

---

## 9. Состояния пользователей (FSM)

Состояния позволяют реализовывать многошаговые диалоги.

### API

**Установить состояние:**
```bash
POST /api/node/bot/setUserState
{
  "user_id": 123,
  "state": "awaiting_phone",
  "state_data": {"step": 2, "name": "Иван"}
}
```

**Получить состояние:**
```bash
GET /api/node/bot/getUserState?user_id=123
```

**Ответ:**
```json
{
  "api_status": 200,
  "state": "awaiting_phone",
  "state_data": {"step": 2, "name": "Иван"}
}
```

**Сбросить:**
```json
{"user_id": 123, "state": null, "state_data": null}
```

---

## 10. Опросы (Polls)

### Создать опрос

```bash
POST /api/node/bot/sendPoll
{
  "chat_id": 123,
  "question": "Какой язык программирования вы предпочитаете?",
  "options": ["JavaScript", "Python", "Kotlin", "PHP"],
  "allows_multiple_answers": false,
  "is_anonymous": true
}
```

**Ответ:** `{"api_status": 200, "poll_id": 45}`

### Закрыть опрос

```bash
POST /api/node/bot/stopPoll
{"poll_id": 45}
```

---

## 11. База данных

### Wo_Bots — основная таблица

```sql
CREATE TABLE Wo_Bots (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  bot_id          VARCHAR(64) UNIQUE NOT NULL,   -- 'bot_abc123'
  owner_id        INT NOT NULL,                   -- user_id владельца
  bot_token       VARCHAR(128) UNIQUE NOT NULL,  -- токен авторизации
  username        VARCHAR(64) UNIQUE NOT NULL,   -- @username_bot
  display_name    VARCHAR(128) NOT NULL,
  description     TEXT,
  bot_type        ENUM('standard','system','verified') DEFAULT 'standard',
  status          ENUM('active','disabled','suspended') DEFAULT 'active',
  is_public       TINYINT DEFAULT 1,
  webhook_url     VARCHAR(512),
  webhook_enabled TINYINT DEFAULT 0,
  messages_sent   BIGINT DEFAULT 0,
  messages_received BIGINT DEFAULT 0,
  total_users     INT DEFAULT 0,
  created_at      DATETIME
);
```

---

## 12. WallyBot — встроенный бот-менеджер

**Bot ID:** `wallybot`
**Username:** `@wallybot`
**Тип:** `system` (не может быть удалён через API)

WallyBot — это встроенный бот, работающий внутри Node.js сервера. Он предоставляет интерфейс для управления ботами через обычный чат.

### Запуск

WallyBot инициализируется автоматически при старте сервера:
```javascript
// main.js
await initializeWallyBot(ctx, io);
```

При первом запуске создаётся запись в `Wo_Bots` и генерируется токен.

### Функции обучения

WallyBot обучаем через команду `/learn`. База знаний хранится в `Wo_Bot_Tasks`:
- `title` = ключевое слово/фраза
- `description` = ответ на это ключевое слово

Поиск по базе знаний использует нечёткое совпадение по словам.

### Архитектура внутреннего бота

WallyBot реализован как "виртуальный сокет":
```javascript
ctx.botSockets.set(WALLYBOT_ID, {
  isInternal: true,
  emit: (event, data) => {
    if (event === 'user_message') {
      handleMessage(ctx, io, data);
    }
  }
});
```

`BotUserMessageController` автоматически перенаправляет сообщения пользователей в соответствующий сокет бота. Это работает для WallyBot без внешнего подключения.

---

## 13. Инфраструктура и деплой

### Сервер
- **Node.js:** `192.168.0.250:449`
- **Redis:** `192.168.0.250:6379` (для Socket.IO adapter и bot messages)
- **MySQL:** localhost, БД `socialhub`

### Файлы системы ботов

```
api-server-files/nodejs/
├── routes/bots/
│   └── index.js          ← Полный Bot REST API (заменяет PHP bot_api.php)
├── bots/
│   └── wallybot.js       ← Встроенный бот-менеджер
├── bot-sdk/
│   └── WorldMatesBotSDK.js  ← Node.js SDK
├── listeners/
│   └── bots-listener.js  ← Socket.IO /bots namespace
├── controllers/
│   ├── BotAuthController.js
│   ├── BotSendMessageController.js
│   ├── BotUserMessageController.js
│   ├── BotCallbackQueryController.js
│   └── ...
└── models/
    ├── wo_bots.js
    ├── wo_bot_messages.js
    ├── wo_bot_users.js
    └── ...
```

### Webhook процессор

Webhook обработка встроена в Node.js (заменяет PHP cron):
```javascript
// routes/bots/index.js — автозапуск при регистрации роутов
setInterval(() => processWebhooks(ctx), 5000); // каждые 5 секунд
```

### Запуск сервера

```bash
cd api-server-files/nodejs
npm install
npm start  # или: node main.js
```

### Конфигурация

Файл `config.json`:
```json
{
  "sql_db_host": "localhost",
  "sql_db_user": "social",
  "sql_db_pass": "...",
  "sql_db_name": "socialhub",
  "site_url": "https://worldmates.club"
}
```

---

## 14. Примеры ботов

### Минимальный бот (polling)

```javascript
// mybot.js
const { WorldMatesBot } = require('./bot-sdk/WorldMatesBotSDK');

const bot = new WorldMatesBot({
  botId: 'bot_ВАШ_ID',
  token: 'bot_ВАШ_ID:ВАШ_ТОКЕН'
});

bot.onCommand('start', async ({ message, bot }) => {
  await bot.sendMessage(message.from.id, 'Привет! Я работаю!');
});

bot.onMessage(async ({ message, bot }) => {
  await bot.sendMessage(message.from.id, `Эхо: ${message.text}`);
});

bot.start(); // polling каждые 5 секунд
console.log('Бот запущен!');
```

### Бот с inline-кнопками

```javascript
bot.onCommand('menu', async ({ message, bot }) => {
  const keyboard = WorldMatesBot.buildInlineKeyboard([
    WorldMatesBot.callbackButton('Раздел 1', 'section_1'),
    WorldMatesBot.callbackButton('Раздел 2', 'section_2'),
    WorldMatesBot.callbackButton('Назад',    'main_menu')
  ], 2);

  await bot.sendMessageWithKeyboard(
    message.from.id,
    'Выберите раздел:',
    keyboard
  );
});

bot.onCallbackQuery(async ({ callback, bot }) => {
  await bot.answerCallbackQuery(callback.id);

  if (callback.data === 'section_1') {
    await bot.sendMessage(callback.from.id, 'Вы в разделе 1!');
  }
});
```

### Регистрация через бота (FSM)

```javascript
bot.onCommand('register', async ({ message, bot }) => {
  await bot.setUserState(message.from.id, 'reg_name');
  await bot.sendMessage(message.from.id, 'Как тебя зовут?');
});

bot.onState('reg_name', async ({ message, bot }) => {
  await bot.setUserState(message.from.id, 'reg_city', { name: message.text });
  await bot.sendMessage(message.from.id, `Привет, ${message.text}! Из какого ты города?`);
});

bot.onState('reg_city', async ({ message, stateData, bot }) => {
  await bot.clearUserState(message.from.id);
  await bot.sendMessage(message.from.id,
    `Зарегистрирован!\nИмя: ${stateData.name}\nГород: ${message.text}`
  );
});
```

---

## Legacy совместимость с PHP SDK

Если у вас есть существующий код, использующий PHP SDK (`WorldMatesBotSDK.php`) и эндпоинт `/api/v2/endpoints/bot_api.php`, он продолжит работать через совместимый эндпоинт:

```
POST /api/node/bots/api
Content-Type: application/x-www-form-urlencoded

type=send_message&bot_token=...&chat_id=123&text=Привет
```

Все типы запросов PHP API поддерживаются.

---

## Коды ответов

| `api_status` | Значение |
|-------------|---------|
| 200 | Успех |
| 400 | Ошибка запроса (неверные параметры) |
| 401 | Не авторизован |
| 403 | Нет доступа |
| 404 | Не найдено |
| 500 | Ошибка сервера |

---

*Документация обновлена: 2026 | WorldMates Messenger Bot API v2.0*
