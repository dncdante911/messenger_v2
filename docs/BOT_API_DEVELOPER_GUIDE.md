# WallyMates Bot API — Руководство разработчика

Полное руководство по созданию, подключению и расширению ботов.

> **Стек:** Node.js + WorldMatesBotSDK. Никакого PHP — бэкенд 100% чистый Node.js.

---

## Содержание

1. [Типы ботов](#типы-ботов)
2. [Быстрый старт — внешний бот](#быстрый-старт--внешний-бот)
3. [WorldMatesBotSDK](#worldmatesbotsdk)
4. [REST API Reference](#rest-api-reference)
5. [Команды и меню](#команды-и-меню)
6. [Inline-клавиатуры](#inline-клавиатуры)
7. [Callback-запросы](#callback-запросы)
8. [State Machine (FSM)](#state-machine-fsm)
9. [Webhook-режим](#webhook-режим)
10. [Встроенные серверные боты](#встроенные-серверные-боты)
11. [Пример бота — randomizerBot](#пример-бота--randomizerbot)
12. [Лучшие практики](#лучшие-практики)
13. [Схема базы данных](#схема-базы-данных)
14. [Устранение неполадок](#устранение-неполадок)

---

## Типы ботов

В WallyMates есть два типа ботов:

| Тип | Где работает | Кто создаёт |
|-----|-------------|------------|
| **Встроенный (серверный)** | Внутри процесса Node.js-сервера | Команда WallyMates |
| **Внешний** | Отдельный процесс / VPS / облако | Сторонние разработчики |

Внешние боты работают по той же схеме что в Telegram BotFather:
1. Регистрируешь бота через `@wallybot` → получаешь `bot_id` и `bot_token`
2. Копируешь SDK-файл к себе в проект
3. Запускаешь свой скрипт — он опрашивает сервер или принимает webhook-запросы
4. Бот появляется в Bot Store для других пользователей

---

## Быстрый старт — внешний бот

### Шаг 1 — Зарегистрировать бота

Напиши в чат `@wallybot` в приложении:

```
/newbot
```

WallyBot проведёт пошагово: попросит username, название, описание и вернёт:

```
✅ Бот создан!
Bot ID:    bot_abc123def456
Bot Token: bot_abc123def456:a1b2c3...длинная_строка
API URL:   https://worldmates.club
```

Сохрани `bot_id` и `bot_token` — они нужны для всех запросов.

### Шаг 2 — Скопировать SDK

```bash
# В директорию своего проекта
cp /path/to/api-server-files/nodejs/bot-sdk/WorldMatesBotSDK.js ./WorldMatesBotSDK.js
```

Или скачай с сервера по пути `api-server-files/nodejs/bot-sdk/WorldMatesBotSDK.js`.

### Шаг 3 — Минимальный бот (50 строк)

```javascript
'use strict';

const { WorldMatesBot } = require('./WorldMatesBotSDK');

const bot = new WorldMatesBot({
    botId:  'bot_abc123def456',          // из /newbot
    token:  'bot_abc123def456:...',      // из /newbot
    apiUrl: 'https://worldmates.club',   // или http://localhost:449 для локальной разработки
});

bot.onCommand('start', async ({ message, bot }) => {
    const userId = message.from.id;
    await bot.sendMessageWithKeyboard(userId,
        'Привет! Я твой бот 👋\n\nНажми /help для команд.',
        WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton('❓ Помощь', 'help'),
        ])
    );
});

bot.onCommand('help', async ({ message, bot }) => {
    await bot.sendMessage(message.from.id,
        '/start — начать\n/help — помощь'
    );
});

bot.onCallbackQuery(async ({ callback, bot }) => {
    if (callback.data === 'help') {
        await bot.sendMessage(callback.from.id, 'Это помощь!');
    }
});

bot.on('error', (err) => console.error('[Bot]', err.message));

// Запуск: long-polling (опрашивает сервер каждые 5 секунд)
bot.start(5);
console.log('Бот запущен. Ctrl+C для остановки.');
```

### Шаг 4 — Запустить

```bash
# Обычный запуск
node my_bot.js

# Или с переменными окружения (рекомендуется)
BOT_ID=bot_abc123 BOT_TOKEN=bot_abc123:... node my_bot.js

# Или через .env файл (нужен dotenv)
node -r dotenv/config my_bot.js
```

### Шаг 5 — Найти бота в приложении

После запуска бот появится в Bot Store (`is_public=1` устанавливается при создании через `/newbot`). Найди его через поиск `@username`.

---

## WorldMatesBotSDK

Файл: `api-server-files/nodejs/bot-sdk/WorldMatesBotSDK.js`

### Конструктор

```javascript
const bot = new WorldMatesBot({
    botId:    'bot_abc123',              // обязательно
    token:    'bot_abc123:secret_hash', // обязательно
    apiUrl:   'https://worldmates.club', // по умолчанию
    nodePort: 449,                       // порт Node.js-сервера
    debug:    false,                     // логировать все запросы
});
```

### Методы отправки

```javascript
// Простое текстовое сообщение
await bot.sendMessage(userId, 'Привет!');

// Сообщение с inline-клавиатурой
await bot.sendMessageWithKeyboard(userId, 'Выбери:', keyboard);

// Установить webhook
await bot.setWebhook('https://my-server.com/webhook');

// Снять webhook
await bot.deleteWebhook();

// Получить информацию о боте
const me = await bot.getMe();
// → { api_status: 200, username: 'my_bot', display_name: '...' }
```

### Обработчики событий

```javascript
// Конкретная команда
bot.onCommand('start', async ({ message, args, bot }) => {
    // message.from.id — user_id отправителя
    // args — строка после команды: "/weather Kyiv" → args="Kyiv"
});

// Все текстовые сообщения (не команды)
bot.onMessage(async ({ message, bot }) => {
    console.log(message.text);
});

// Нажатие inline-кнопки
bot.onCallbackQuery(async ({ callback, bot }) => {
    // callback.data — строка callback_data кнопки
    // callback.from.id — user_id нажавшего
});

// Состояние (FSM) — см. раздел State Machine
bot.onState('waiting_input', async ({ message, bot }) => { ... });

// Ошибки SDK
bot.on('error', (err) => console.error(err));
```

### Построение клавиатур

```javascript
// Статические хелперы — не нужен экземпляр bot
WorldMatesBot.buildInlineKeyboard(buttons, columns = 2)
WorldMatesBot.callbackButton(text, callbackData)

// Пример:
const keyboard = WorldMatesBot.buildInlineKeyboard([
    WorldMatesBot.callbackButton('🎲 Кубик',  'dice'),
    WorldMatesBot.callbackButton('🪙 Монета', 'flip'),
    WorldMatesBot.callbackButton('❓ Помощь', 'help'),
], 2);  // 2 кнопки в ряд
```

### Режим запуска

```javascript
// Polling — бот сам спрашивает сервер (для локальной разработки и VPS)
bot.start(timeout_seconds);  // timeout=5 рекомендуется

// Webhook — сервер шлёт запросы к тебе (для продакшена с HTTPS)
const express = require('express');
const app = express();
app.use(express.json());
app.post('/webhook', bot.webhookHandler());
app.listen(3001);
await bot.setWebhook('https://my-server.com/webhook');
```

---

## REST API Reference

**Базовый URL:** `https://worldmates.club:449/api/node/`

**Авторизация:** заголовок `Authorization: Bearer <bot_token>` или параметр `bot_token`.

### Управление ботом

| Метод | Путь | Описание |
|-------|------|---------|
| `GET` | `/bots/me` | Информация о боте |
| `POST` | `/bots/set-commands` | Зарегистрировать команды |
| `GET` | `/bots/get-commands` | Список команд |
| `POST` | `/bots/set-webhook` | Установить webhook |
| `POST` | `/bots/delete-webhook` | Убрать webhook |

### Сообщения

| Метод | Путь | Описание |
|-------|------|---------|
| `POST` | `/bots/send-message` | Отправить сообщение |
| `GET` | `/bots/get-updates` | Long polling (новые сообщения) |

### Поиск

| Метод | Путь | Описание |
|-------|------|---------|
| `GET` | `/bots/search?q=query` | Поиск публичных ботов |

### Пример — отправить сообщение напрямую через curl

```bash
curl -X POST "https://worldmates.club:449/api/node/bots/send-message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bot_abc123:secret..." \
  -d '{
    "chat_id": 12345,
    "text": "Привет из curl!",
    "reply_markup": {
      "inline_keyboard": [[
        {"text": "OK", "callback_data": "ok"}
      ]]
    }
  }'
```

### Пример — получить обновления (polling вручную)

```bash
curl "https://worldmates.club:449/api/node/bots/get-updates?bot_token=bot_abc123:secret&offset=0&limit=10"
```

---

## Команды и меню

### Зарегистрировать команды

```javascript
const result = await bot.setCommands([
    { command: 'start',   description: 'Начать / главное меню' },
    { command: 'help',    description: 'Список команд' },
    { command: 'weather', description: 'Погода: /weather Киев' },
    { command: 'settings',description: 'Настройки' },
]);
```

Команды появятся в приложении когда пользователь нажмёт `/` рядом с полем ввода.

### Области применения (scope)

```javascript
{ command: 'admin', description: 'Панель администратора', scope: 'admin' }
```

Доступные значения: `all`, `private`, `group`, `admin`.

---

## Inline-клавиатуры

### Структура

```javascript
const keyboard = {
    inline_keyboard: [
        [
            { text: 'Вариант А', callback_data: 'opt_a' },
            { text: 'Вариант Б', callback_data: 'opt_b' },
        ],
        [
            { text: 'Открыть сайт', url: 'https://example.com' },
        ],
    ]
};

await bot.sendMessageWithKeyboard(userId, 'Выбери:', keyboard);
```

### Через хелперы SDK

```javascript
const keyboard = WorldMatesBot.buildInlineKeyboard([
    WorldMatesBot.callbackButton('🔢 Число', 'random'),
    WorldMatesBot.callbackButton('🪙 Монета', 'flip'),
    WorldMatesBot.callbackButton('❓ Помощь', 'help'),
], 2);  // 2 кнопки в ряд → [[random, flip], [help]]
```

### Ограничения

- `callback_data` — максимум 64 байта (лучше держать до 32)
- Максимум 8 кнопок в ряду, 100 кнопок всего
- Не храни чувствительные данные в `callback_data` — они видны клиенту

---

## Callback-запросы

```javascript
bot.onCallbackQuery(async ({ callback, bot }) => {
    const userId = callback.from.id;
    const data   = callback.data;

    if (data === 'confirm') {
        await bot.sendMessage(userId, '✅ Подтверждено!');
        return;
    }

    if (data.startsWith('page_')) {
        const page = parseInt(data.replace('page_', ''));
        await showPage(bot, userId, page);
        return;
    }

    // Пагинация
    if (data === 'prev' || data === 'next') {
        // ...
    }
});
```

---

## State Machine (FSM)

Для многошаговых диалогов (анкеты, регистрация, настройки):

```javascript
// Шаг 1: начать сбор данных
bot.onCommand('register', async ({ message, bot }) => {
    await bot.sendMessage(message.from.id, 'Как тебя зовут?');
    bot.setState(message.from.id, 'register_name');
});

// Шаг 2: получить имя, спросить email
bot.onState('register_name', async ({ message, bot }) => {
    const name = message.text;
    bot.setStateData(message.from.id, { name });
    await bot.sendMessage(message.from.id, `Отлично, ${name}! Введи email:`);
    bot.setState(message.from.id, 'register_email');
});

// Шаг 3: завершить регистрацию
bot.onState('register_email', async ({ message, bot }) => {
    const { name } = bot.getStateData(message.from.id);
    const email = message.text;
    // ... сохранить в БД ...
    await bot.sendMessage(message.from.id,
        `✅ Готово!\nИмя: ${name}\nEmail: ${email}`
    );
    bot.clearState(message.from.id);
});
```

---

## Webhook-режим

Webhook подходит для продакшена: сервер сам шлёт обновления к тебе — не нужно постоянно опрашивать.

**Требования:** HTTPS, публично доступный URL, ответ за 10 секунд.

```javascript
const express = require('express');
const app     = express();

app.use(express.json());

// Подключить обработчики бота
bot.onCommand('start', async ({ message, bot }) => { ... });
bot.onCallbackQuery(async ({ callback, bot }) => { ... });

// Зарегистрировать endpoint
app.post('/webhook', bot.webhookHandler());

app.listen(3001, async () => {
    console.log('Webhook сервер запущен на порту 3001');
    await bot.setWebhook('https://my-server.com/webhook');
    console.log('Webhook установлен');
});
```

### Проверка подписи вручную

```javascript
const crypto = require('crypto');

app.post('/webhook', express.raw({ type: 'application/json' }), (req, res) => {
    const signature = req.headers['x-bot-signature'] || '';
    const expected  = crypto
        .createHmac('sha256', BOT_TOKEN)
        .update(req.body)
        .digest('hex');

    if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) {
        return res.status(403).send('Bad signature');
    }

    const update = JSON.parse(req.body);
    // Обрабатываем update...
    res.json({ ok: true });
});
```

### Payload входящего webhook

```json
{
    "update_id": 123456,
    "bot_id": "bot_abc123",
    "event_type": "message",
    "timestamp": 1707660000,
    "signature": "hmac_sha256_...",
    "data": {
        "message_id": 789,
        "user_id": 12345,
        "text": "/start",
        "is_command": true,
        "command_name": "start",
        "command_args": ""
    }
}
```

---

## Встроенные серверные боты

Это боты которые живут **внутри серверного процесса** — без отдельного сервера, без polling, без отдельного деплоя. Они автоматически появляются в Bot Store при старте сервера.

Такой подход используется для `@wallybot` и `@randomizerbot`.

### Архитектура

```
PrivateMessageController
       │
       ├─ ctx.botSockets.has(bot_id)?  ──YES──► internalHandler.emit('user_message', payload)
       │                                                │
       │                                         handleMessage(ctx, io, payload)
       │                                                │
       │                                         send(ctx, io, userId, text, markup)
       │                                                │
       │                                   ┌──────────────────────┐
       │                                   │ wo_bot_messages       │
       │                                   │ wo_messages           │
       │                                   │ io.emit('private_msg')│
       │                                   └──────────────────────┘
       └─ NO webhook? ──► defaultBotShell (WallyBot обрабатывает)
```

### Создать встроенный бот

**Файл:** `api-server-files/nodejs/bots/myBot.js`

```javascript
'use strict';
const crypto = require('crypto');

const BOT_ID   = 'bot_mybot_001';
const BOT_NAME = 'MyBot';
const USERNAME = 'mybot';
let   BOT_USER_ID = null;

function generateToken(botId) {
    const rand = crypto.randomBytes(32).toString('hex');
    return `${botId}:${crypto.createHmac('sha256', botId).update(rand).digest('hex')}`;
}

function ik(rows) { return { inline_keyboard: rows }; }
function btn(text, cb) { return { text, callback_data: cb }; }

async function send(ctx, io, userId, text, markup = null) {
    const now = Math.floor(Date.now() / 1000);
    const botMsg = await ctx.wo_bot_messages.create({
        bot_id: BOT_ID, chat_id: String(userId), chat_type: 'private',
        direction: 'outgoing', text,
        reply_markup: markup ? JSON.stringify(markup) : null,
        processed: 1, processed_at: new Date()
    });
    if (BOT_USER_ID) {
        try {
            const woMsg = await ctx.wo_messages.create({
                from_id: BOT_USER_ID, to_id: userId, text, seen: 0, time: now
            });
            if (io) io.to(String(userId)).emit('private_message', {
                status: 200, id: woMsg.id,
                from_id: BOT_USER_ID, to_id: userId,
                text, message: text, message_id: woMsg.id,
                time: now, time_api: now,
                username: BOT_NAME, avatar: 'upload/photos/d-avatar.jpg',
                receiver: userId, sender: BOT_USER_ID,
                cipher_version: 1, media: '', isMedia: false, isRecord: false,
                reply_markup: markup || null, bot_id: BOT_ID
            });
        } catch (e) { console.warn('[MyBot/msg]', e.message); }
    }
}

async function handleMessage(ctx, io, payload) {
    const userId = payload.user_id;
    if (!userId) return;

    if (payload.callback_data) {
        if (payload.callback_data === 'menu') {
            return send(ctx, io, userId, 'Главное меню', ik([[btn('ОК', 'ok')]]));
        }
        return;
    }

    const cmd = payload.command_name || '';
    const args = payload.command_args || '';

    if (cmd === 'start') {
        return send(ctx, io, userId, `Привет! Я ${BOT_NAME}.`, ik([[btn('Меню', 'menu')]]));
    }
    await send(ctx, io, userId, 'Попробуй /start');
}

async function initializeMyBot(ctx, io) {
    const now = Math.floor(Date.now() / 1000);

    const [user] = await ctx.wo_users.findOrCreate({
        where: { username: USERNAME },
        defaults: {
            email: `${USERNAME}@bots.internal`,
            password: crypto.randomBytes(20).toString('hex'),
            first_name: BOT_NAME, last_name: '',
            about: 'Мой первый встроенный бот.',
            type: 'bot', active: '1', verified: '0',
            lastseen: now, registered: new Date().toLocaleDateString('en-US'),
            joined: now, message_privacy: '0'
        }
    });
    BOT_USER_ID = user.user_id;

    const existing = await ctx.wo_bots.findOne({ where: { bot_id: BOT_ID } });
    if (!existing) {
        await ctx.wo_bots.create({
            bot_id: BOT_ID, owner_id: 1, bot_token: generateToken(BOT_ID),
            username: USERNAME, display_name: BOT_NAME,
            description: 'Мой бот', about: 'Подробное описание',
            category: 'tools', bot_type: 'standard',   // ← ENUM: standard / system / verified
            status: 'active', is_public: 1,
            can_join_groups: 0, supports_commands: 1,
            linked_user_id: BOT_USER_ID,
            created_at: new Date(), updated_at: new Date()
        });
    }

    // Команды
    const commands = [
        { command: 'start', description: 'Начать', sort_order: 1 },
        { command: 'help',  description: 'Помощь',  sort_order: 2 },
    ];
    for (const cmd of commands) {
        await ctx.wo_bot_commands.findOrCreate({
            where: { bot_id: BOT_ID, command: cmd.command },
            defaults: { bot_id: BOT_ID, ...cmd, scope: 'all', is_hidden: 0 }
        });
    }

    // Регистрируем внутренний обработчик
    if (ctx.botSockets) {
        ctx.botSockets.set(BOT_ID, {
            isInternal: true, botId: BOT_ID,
            emit: (event, data) => {
                if (event === 'user_message') {
                    handleMessage(ctx, io, data).catch(e =>
                        console.error('[MyBot]', e.message));
                }
                if (event === 'callback_query') {
                    handleMessage(ctx, io, {
                        user_id: data.user_id, text: '',
                        callback_data: data.data
                    }).catch(e => console.error('[MyBot/cb]', e.message));
                }
            }
        });
    }

    console.log(`[MyBot] Ready @${USERNAME}`);
}

module.exports = { initializeMyBot };
```

**Подключить в `main.js`:**

```javascript
const { initializeMyBot } = require('./bots/myBot');

// В обработчике 'connection' рядом с WallyBot:
initializeMyBot(ctx, io).catch(e => console.error('[MyBot] Init error:', e));
```

### Важные ограничения

- `bot_type` в модели — ENUM: `'standard'`, `'system'`, `'verified'`. Значение `'external'` не существует.
- `linked_user_id` должен совпадать с `user_id` в `Wo_Users` — иначе Android не покажет чат.
- `ctx.botSockets` — in-memory Map. В кластере PM2 каждый воркер инициализирует бота отдельно (это нормально — DB-операции идемпотентны через `findOrCreate`).

---

## Пример бота — randomizerBot

Два варианта одного бота. Изучи исходники:

### Внешний (через SDK)
**Файл:** `api-server-files/nodejs/bots/randomizerBot.js`

Запускается отдельно. Требует `bot_id` и `bot_token` после создания через `/newbot`.

```bash
BOT_ID=bot_xxx BOT_TOKEN=bot_xxx:secret node randomizerBot.js
```

### Встроенный (серверный)
**Файл:** `api-server-files/nodejs/bots/randomizerBotServer.js`

Запускается автоматически с сервером. Бот `@randomizerbot` — живой пример встроенного бота.

---

## Лучшие практики

### Делай

- Всегда обрабатывай `/start` — это первое что видит пользователь
- Используй inline-кнопки для навигации (не заставляй печатать)
- Используй `command_name` / `command_args` из payload (они уже распарсены сервером)
- Для продакшена используй webhook вместо polling
- Храни токены в переменных окружения, не в коде

### Не делай

- Не пиши `bot_type: 'external'` — нет такого значения в ENUM
- Не храни чувствительные данные в `callback_data`
- Не игнорируй rate limits (30 запросов/сек)
- Не блокируй event loop в обработчиках (используй async/await)
- Не забывай обрабатывать ошибки (`try/catch` или `.catch()`)

### Безопасность

- Никогда не публикуй `bot_token` в репозитории
- Проверяй подпись webhook-запросов
- Санитизируй пользовательский ввод перед сохранением в БД
- Webhook URL должен быть HTTPS

---

## Схема базы данных

| Таблица | Назначение |
|---------|-----------|
| `Wo_Bots` | Аккаунты ботов (токены, настройки, статистика) |
| `Wo_Bot_Commands` | Зарегистрированные slash-команды |
| `Wo_Bot_Messages` | История сообщений (входящие/исходящие) |
| `Wo_Bot_Users` | Пользователи, взаимодействующие с ботом |
| `Wo_Bot_Callbacks` | Клики по inline-кнопкам |
| `Wo_Bot_Tasks` | База знаний WallyBot (KV-хранилище) |
| `Wo_Bot_Webhook_Log` | Лог доставки webhook |

### Ключевые поля `Wo_Bots`

| Поле | Тип | Описание |
|------|-----|---------|
| `bot_id` | VARCHAR(64) | Уникальный ID бота |
| `bot_token` | VARCHAR(128) | Секретный токен |
| `username` | VARCHAR(64) | @username для поиска |
| `linked_user_id` | INTEGER | ID в Wo_Users — нужен для DM |
| `is_public` | TINYINT | 1 = виден в Bot Store |
| `status` | ENUM | `active` / `disabled` / `suspended` |
| `bot_type` | ENUM | `standard` / `system` / `verified` |
| `webhook_url` | VARCHAR(512) | URL для webhook-режима |
| `webhook_enabled` | TINYINT | 0 = polling, 1 = webhook |

---

## Устранение неполадок

### Бот не получает сообщения

1. Проверь что `status = 'active'` в `Wo_Bots`
2. Убедись что `bot_token` правильный
3. Для polling — проверь поле `processed` в `Wo_Bot_Messages` (должно становиться 1)
4. Для webhook — смотри `Wo_Bot_Webhook_Log`
5. Для встроенного бота — убедись что `ctx.botSockets.has(bot_id)` возвращает `true`

### Ошибка `Data truncated for column 'bot_type'`

Используй только допустимые значения ENUM: `'standard'`, `'system'`, `'verified'`. Значения `'external'`, `'custom'` и другие не существуют.

### Inline-кнопки не отображаются

1. Проверь формат `reply_markup` — должен быть объект `{ inline_keyboard: [[...]] }`
2. `callback_data` — максимум 64 символа
3. `linked_user_id` в `Wo_Bots` должен указывать на реальный `user_id` в `Wo_Users`

### Бот появился в сокете но не в Bot Store

Проверь что `is_public = 1` и `status = 'active'` в `Wo_Bots`.

### Webhook не доставляется

1. URL должен быть HTTPS с валидным сертификатом
2. Сервер должен отвечать за 10 секунд
3. Проверь `Wo_Bot_Webhook_Log` для статус-кодов ответа

---

*WallyMates Bot API v2.0 — Node.js, чистый и быстрый*
