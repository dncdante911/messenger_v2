# WorldMates Bot API - Developer Guide

Complete guide for creating, managing, and extending bots on WorldMates Messenger.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Creating Your First Bot](#creating-your-first-bot)
3. [Bot API Reference](#bot-api-reference)
4. [PHP SDK](#php-sdk)
5. [Socket.IO Integration](#socketio-integration)
6. [Webhooks](#webhooks)
7. [Commands & Menus](#commands--menus)
8. [Inline Keyboards](#inline-keyboards)
9. [Polls & Voting](#polls--voting)
10. [User State (FSM)](#user-state-fsm)
11. [Example Bots](#example-bots)
12. [Extending & Updating Bots](#extending--updating-bots)
13. [Best Practices](#best-practices)
14. [Database Schema](#database-schema)
15. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Prerequisites
- WorldMates Messenger account
- Access to Bot API endpoint: `POST /api/v2/endpoints/bot_api.php`
- PHP 7.4+ (for SDK) or any language (for REST API)

### 30-Second Setup

```bash
# 1. Create a bot via API
curl -X POST "https://your-domain.com/api/v2/endpoints/bot_api.php?type=create_bot" \
  -d "access_token=YOUR_ACCESS_TOKEN" \
  -d "username=my_first_bot" \
  -d "display_name=My First Bot" \
  -d "description=A test bot"

# Response:
# { "status": 200, "bot_id": "bot_abc123", "bot_token": "wmb_..." }

# 2. Set commands
curl -X POST "https://your-domain.com/api/v2/endpoints/bot_api.php?type=set_commands" \
  -d "bot_token=wmb_YOUR_BOT_TOKEN" \
  -d "commands=[{\"command\":\"start\",\"description\":\"Start the bot\"},{\"command\":\"help\",\"description\":\"Get help\"}]"

# 3. Send a message
curl -X POST "https://your-domain.com/api/v2/endpoints/bot_api.php?type=send_message" \
  -d "bot_token=wmb_YOUR_BOT_TOKEN" \
  -d "chat_id=12345" \
  -d "text=Hello from my bot!"
```

---

## Creating Your First Bot

### Method 1: Via API (recommended for developers)

```php
<?php
$response = file_get_contents('https://your-domain.com/api/v2/endpoints/bot_api.php?type=create_bot', false,
    stream_context_create([
        'http' => [
            'method' => 'POST',
            'header' => 'Content-Type: application/x-www-form-urlencoded',
            'content' => http_build_query([
                'access_token' => 'YOUR_USER_TOKEN',
                'username' => 'weather_bot',
                'display_name' => 'Weather Bot',
                'description' => 'Get weather forecasts for any city',
                'category' => 'tools',
                'is_public' => 1,
                'can_join_groups' => 1
            ])
        ]
    ])
);

$data = json_decode($response, true);
echo "Bot Token: " . $data['bot_token'];
// Save this token! You'll need it for all API calls.
```

### Method 2: Via Mobile App

1. Open WorldMates Messenger
2. Tap the **Bot icon** (FAB) on the Chats tab
3. Go to **My Bots** > **Create Bot**
4. Fill in username, name, description
5. Copy the generated **bot_token**

### Method 3: Via PHP SDK

```php
<?php
require_once 'bot-sdk/WorldMatesBotSDK.php';

// The SDK handles creation internally
$bot = new WorldMatesBot('wmb_YOUR_BOT_TOKEN', 'https://your-domain.com');
```

---

## Bot API Reference

**Base URL:** `POST /api/v2/endpoints/bot_api.php?type={action}`

**Authentication:** All requests require `bot_token` parameter (except `create_bot` which uses `access_token`).

### Bot Management

| Action | Description | Required Params |
|--------|-------------|----------------|
| `create_bot` | Create new bot | `access_token`, `username`, `display_name` |
| `get_my_bots` | List your bots | `access_token` |
| `update_bot` | Update bot info | `bot_token`, fields to update |
| `delete_bot` | Delete bot | `bot_token`, `confirm=DELETE` |
| `regenerate_token` | New token | `bot_token` |
| `get_bot_info` | Public bot info | `bot_token` or `bot_id` |

### Messaging

| Action | Description | Required Params |
|--------|-------------|----------------|
| `send_message` | Send text/media | `bot_token`, `chat_id`, `text` |
| `edit_message` | Edit sent message | `bot_token`, `message_id`, `text` |
| `delete_message` | Delete message | `bot_token`, `message_id` |
| `get_updates` | Long polling | `bot_token`, optional `offset`, `limit` |

### Commands

| Action | Description | Required Params |
|--------|-------------|----------------|
| `set_commands` | Register commands | `bot_token`, `commands` (JSON array) |
| `get_commands` | Get command list | `bot_token` |

### Interactive

| Action | Description | Required Params |
|--------|-------------|----------------|
| `answer_callback_query` | Answer button click | `bot_token`, `callback_query_id` |
| `send_poll` | Create poll | `bot_token`, `chat_id`, `question`, `options` |
| `stop_poll` | Close poll | `bot_token`, `poll_id` |

### Webhooks

| Action | Description | Required Params |
|--------|-------------|----------------|
| `set_webhook` | Set webhook URL | `bot_token`, `url` |
| `delete_webhook` | Remove webhook | `bot_token` |
| `get_webhook_info` | Get webhook status | `bot_token` |

### User State

| Action | Description | Required Params |
|--------|-------------|----------------|
| `set_user_state` | Set FSM state | `bot_token`, `user_id`, `state` |
| `get_user_state` | Get FSM state | `bot_token`, `user_id` |

### Search

| Action | Description | Required Params |
|--------|-------------|----------------|
| `search_bots` | Search public bots | `access_token`, `query` |
| `get_chat_member` | Get user info | `bot_token`, `chat_id`, `user_id` |

---

## PHP SDK

### Installation

Copy `bot-sdk/WorldMatesBotSDK.php` to your project.

### Basic Usage

```php
<?php
require_once 'WorldMatesBotSDK.php';

$bot = new WorldMatesBot('wmb_YOUR_BOT_TOKEN', 'https://your-domain.com');

// Register command handlers
$bot->onCommand('start', function($message) use ($bot) {
    $bot->sendMessage($message['user_id'],
        "Welcome! I'm your bot. Type /help for commands.");
});

$bot->onCommand('help', function($message) use ($bot) {
    $bot->sendMessage($message['user_id'],
        "/start - Start bot\n/help - Show help\n/weather <city> - Get weather");
});

// Handle any text message
$bot->onMessage(function($message) use ($bot) {
    $bot->sendMessage($message['user_id'],
        "You said: " . $message['text']);
});

// Start long polling
$bot->startPolling();
```

### Sending Messages with Keyboards

```php
// Inline keyboard (buttons under message)
$keyboard = [
    [
        ['text' => 'Option A', 'callback_data' => 'opt_a'],
        ['text' => 'Option B', 'callback_data' => 'opt_b']
    ],
    [
        ['text' => 'Visit Website', 'url' => 'https://example.com']
    ]
];

$bot->sendMessageWithKeyboard($chatId, "Choose an option:", [
    'inline_keyboard' => $keyboard
]);

// Reply keyboard (replaces user's keyboard)
$replyKeyboard = [
    [
        ['text' => 'Share Location', 'request_location' => true],
        ['text' => 'Share Contact', 'request_contact' => true]
    ],
    [
        ['text' => 'Cancel']
    ]
];

$bot->sendMessageWithKeyboard($chatId, "What would you like to share?", [
    'keyboard' => $replyKeyboard,
    'resize_keyboard' => true,
    'one_time_keyboard' => true
]);
```

### Handling Callback Queries

```php
$bot->onCallbackQuery(function($query) use ($bot) {
    $data = $query['data'];
    $userId = $query['user_id'];

    switch ($data) {
        case 'opt_a':
            $bot->answerCallbackQuery($query['id'], 'You chose Option A!');
            $bot->sendMessage($userId, 'Processing Option A...');
            break;
        case 'opt_b':
            $bot->answerCallbackQuery($query['id'], 'You chose Option B!');
            break;
    }
});
```

### State Machine (FSM)

```php
// Set user to "waiting for city" state
$bot->setUserState($userId, 'waiting_city');

// Handle based on state
$bot->onState('waiting_city', function($message) use ($bot) {
    $city = $message['text'];
    $weather = getWeather($city);
    $bot->sendMessage($message['user_id'], "Weather in $city: $weather");
    $bot->setUserState($message['user_id'], null); // Clear state
});
```

### Webhook Mode

```php
// Instead of long polling, set a webhook:
$bot->setWebhook('https://your-server.com/bot-webhook.php');

// In bot-webhook.php:
$bot = new WorldMatesBot('wmb_YOUR_TOKEN', 'https://your-domain.com');
$bot->onCommand('start', function($msg) use ($bot) { /* ... */ });
$bot->handleWebhook(); // Processes incoming webhook data
```

---

## Socket.IO Integration

Bots can connect via Socket.IO for real-time message delivery.

### Bot-Side Connection (namespace: `/bots`)

```javascript
const io = require('socket.io-client');

const socket = io('https://your-domain.com/bots', {
    transports: ['websocket']
});

// 1. Authenticate
socket.emit('bot_auth', {
    bot_id: 'bot_abc123',
    bot_token: 'wmb_your_token'
});

socket.on('auth_success', (data) => {
    console.log('Bot authenticated:', data.display_name);
});

// 2. Listen for user messages
socket.on('user_message', (data) => {
    console.log(`User ${data.user_id}: ${data.text}`);

    // Respond
    socket.emit('bot_message', {
        bot_id: 'bot_abc123',
        chat_id: data.user_id,
        text: 'Got your message!'
    });
});

// 3. Listen for callback queries (inline button clicks)
socket.on('callback_query', (data) => {
    console.log(`Button clicked: ${data.data}`);

    socket.emit('callback_answer', {
        bot_id: 'bot_abc123',
        callback_query_id: data.callback_query_id,
        user_id: data.user_id,
        text: 'Processing...'
    });
});

// 4. Typing indicator
socket.emit('bot_typing', {
    bot_id: 'bot_abc123',
    chat_id: '12345',
    is_typing: true
});
```

### User-Side Events (main namespace)

These events are handled automatically by the mobile app:

| Event | Direction | Description |
|-------|-----------|-------------|
| `subscribe_bot` | Client -> Server | User opens bot chat |
| `unsubscribe_bot` | Client -> Server | User leaves bot chat |
| `user_to_bot` | Client -> Server | User sends message to bot |
| `bot_callback_query` | Client -> Server | User clicks inline button |
| `bot_poll_vote` | Client -> Server | User votes in poll |
| `bot_message` | Server -> Client | Bot message received |
| `bot_typing` | Server -> Client | Bot is typing |
| `callback_answer` | Server -> Client | Bot answers button click |
| `update_markup` | Server -> Client | Bot updates keyboard |
| `bot_poll_update` | Server -> Client | Poll results updated |

### REST API for Pushing Messages

```bash
# Push bot message via Node.js REST API (used by PHP backend)
curl -X POST "https://your-domain.com:PORT/api/bots/push-message" \
  -H "Content-Type: application/json" \
  -d '{
    "bot_id": "bot_abc123",
    "chat_id": "12345",
    "text": "Hello!",
    "reply_markup": {"inline_keyboard": [[{"text": "OK", "callback_data": "ok"}]]}
  }'
```

---

## Webhooks

### Setting Up

```php
$bot->setWebhook('https://your-server.com/webhook.php', 'your_secret_key');
```

### Webhook Payload

Your server receives POST requests with JSON:

```json
{
    "update_id": 123456,
    "bot_id": "bot_abc123",
    "event_type": "message",
    "timestamp": 1707660000,
    "signature": "hmac_sha256_signature",
    "data": {
        "message_id": 789,
        "user_id": 12345,
        "text": "/start",
        "is_command": true,
        "command_name": "start"
    }
}
```

### Verifying Signature

```php
$signature = $_SERVER['HTTP_X_BOT_SIGNATURE'] ?? '';
$payload = file_get_contents('php://input');
$expected = hash_hmac('sha256', $payload, $webhookSecret);

if (!hash_equals($expected, $signature)) {
    http_response_code(403);
    die('Invalid signature');
}
```

### Retry Policy

Failed deliveries are retried with exponential backoff:
- Attempt 1: immediate
- Attempt 2: after 10 seconds
- Attempt 3: after 30 seconds
- Attempt 4: after 90 seconds
- Attempt 5: after 270 seconds

---

## Commands & Menus

### Registering Commands

```php
$bot->setCommands([
    ['command' => 'start', 'description' => 'Start the bot'],
    ['command' => 'help', 'description' => 'Show help'],
    ['command' => 'settings', 'description' => 'Bot settings'],
    ['command' => 'weather', 'description' => 'Get weather forecast'],
    ['command' => 'subscribe', 'description' => 'Subscribe to updates'],
    ['command' => 'unsubscribe', 'description' => 'Unsubscribe']
]);
```

### Command Scopes

```json
{
    "command": "admin",
    "description": "Admin panel",
    "scope": "admin"
}
```

Available scopes: `all`, `private`, `group`, `admin`

### How Commands Appear

In the mobile app, commands appear:
1. When user taps the **"/"** button next to the text input
2. As suggestion chips above the keyboard
3. In the bot profile/info screen

---

## Inline Keyboards

### Button Types

```php
// Callback button (triggers callback_query)
['text' => 'Like', 'callback_data' => 'like_post_123']

// URL button (opens browser)
['text' => 'Open Website', 'url' => 'https://example.com']
```

### Dynamic Keyboard Updates

```php
// Update keyboard after button click (e.g., toggle like)
$bot->editMessageMarkup($messageId, $chatId, [
    'inline_keyboard' => [
        [['text' => 'Liked! (5)', 'callback_data' => 'unlike_123']]
    ]
]);
```

### Pagination Example

```php
function getPaginationKeyboard($page, $totalPages) {
    $buttons = [];
    if ($page > 1) $buttons[] = ['text' => '< Prev', 'callback_data' => "page_" . ($page - 1)];
    $buttons[] = ['text' => "$page/$totalPages", 'callback_data' => 'noop'];
    if ($page < $totalPages) $buttons[] = ['text' => 'Next >', 'callback_data' => "page_" . ($page + 1)];
    return ['inline_keyboard' => [$buttons]];
}
```

---

## Polls & Voting

### Creating a Poll

```php
$bot->sendPoll($chatId, 'What is your favorite language?', [
    'options' => ['PHP', 'JavaScript', 'Python', 'Kotlin'],
    'is_anonymous' => true,
    'allows_multiple_answers' => false
]);
```

### Quiz Mode

```php
$bot->sendPoll($chatId, 'Capital of France?', [
    'options' => ['London', 'Berlin', 'Paris', 'Madrid'],
    'type' => 'quiz',
    'correct_option_id' => 2,
    'explanation' => 'Paris is the capital of France'
]);
```

---

## User State (FSM)

Finite State Machine for multi-step conversations:

```php
// Step 1: User starts registration
$bot->onCommand('register', function($msg) use ($bot) {
    $bot->sendMessage($msg['user_id'], 'What is your name?');
    $bot->setUserState($msg['user_id'], 'register_name');
});

// Step 2: User enters name
$bot->onState('register_name', function($msg) use ($bot) {
    $name = $msg['text'];
    $bot->setUserState($msg['user_id'], 'register_email', json_encode(['name' => $name]));
    $bot->sendMessage($msg['user_id'], "Nice, $name! Now enter your email:");
});

// Step 3: User enters email
$bot->onState('register_email', function($msg) use ($bot) {
    $stateData = json_decode($bot->getUserState($msg['user_id'])['state_data'], true);
    $name = $stateData['name'];
    $email = $msg['text'];

    // Save to database...
    $bot->sendMessage($msg['user_id'], "Registration complete!\nName: $name\nEmail: $email");
    $bot->setUserState($msg['user_id'], null); // Clear state
});
```

---

## Example Bots

### 1. RSS News Bot
**File:** `bot-examples/RSSNewsBot.php`

Features:
- Subscribe to RSS feeds (BBC, Reuters, TechCrunch, etc.)
- Auto-post new articles at configurable intervals
- Commands: `/start`, `/add <url>`, `/remove`, `/list`, `/latest`, `/sources`

### 2. Weather Bot
**File:** `bot-examples/WeatherBot.php`

Features:
- Current weather and forecasts via OpenWeatherMap
- City saving for quick access
- Commands: `/weather <city>`, `/forecast <city>`, `/setcity <city>`

### 3. Support Bot
**File:** `bot-examples/SupportBot.php`

Features:
- FAQ with category navigation (inline keyboards)
- Ticket creation with auto-numbering
- Feedback collection with star ratings

### 4. Task Tracker Bot
**File:** `bot-examples/TaskTrackerBot.php`

Features:
- Create/edit/delete tasks with priorities
- Due dates and reminders
- Progress tracking with visual statistics

### 5. Poll Bot
**File:** `bot-examples/PollBot.php`

Features:
- Create polls and quizzes
- Anonymous/public voting
- Auto-close by date
- Visual results with bar charts

---

## Extending & Updating Bots

### Adding a New Command

```php
// 1. Add handler in your bot code
$bot->onCommand('newcommand', function($message) use ($bot) {
    $bot->sendMessage($message['user_id'], 'New command response!');
});

// 2. Register with API so it shows in command menu
$currentCommands = $bot->getCommands();
$currentCommands[] = ['command' => 'newcommand', 'description' => 'My new command'];
$bot->setCommands($currentCommands);
```

### Adding Inline Keyboard Support

```php
// Before: simple text response
$bot->sendMessage($chatId, "Choose a color");

// After: with inline buttons
$bot->sendMessageWithKeyboard($chatId, "Choose a color:", [
    'inline_keyboard' => [
        [
            ['text' => 'Red', 'callback_data' => 'color_red'],
            ['text' => 'Blue', 'callback_data' => 'color_blue'],
            ['text' => 'Green', 'callback_data' => 'color_green']
        ]
    ]
]);

// Handle the callback
$bot->onCallbackQuery(function($query) use ($bot) {
    if (strpos($query['data'], 'color_') === 0) {
        $color = str_replace('color_', '', $query['data']);
        $bot->answerCallbackQuery($query['id'], "You chose $color!");
    }
});
```

### Adding Multi-Language Support

```php
$translations = [
    'en' => ['welcome' => 'Welcome!', 'help' => 'Available commands:'],
    'uk' => ['welcome' => 'Ласкаво просимо!', 'help' => 'Доступні команди:'],
    'ru' => ['welcome' => 'Добро пожаловать!', 'help' => 'Доступные команды:']
];

function t($key, $lang = 'en') {
    global $translations;
    return $translations[$lang][$key] ?? $translations['en'][$key] ?? $key;
}

$bot->onCommand('start', function($msg) use ($bot) {
    $lang = getUserLanguage($msg['user_id']); // your function
    $bot->sendMessage($msg['user_id'], t('welcome', $lang));
});
```

### Adding Media Support

```php
// Send image
$bot->sendMessage($chatId, "Check out this photo!", [
    'media_type' => 'image',
    'media_url' => 'https://example.com/photo.jpg'
]);

// Send file
$bot->sendMessage($chatId, "Here's your report:", [
    'media_type' => 'file',
    'media_url' => 'https://example.com/report.pdf'
]);
```

### Migrating from Long Polling to Webhooks

```php
// Before: Long polling (blocks script)
$bot->startPolling();

// After: Webhook (event-driven)
// 1. Set webhook URL
$bot->setWebhook('https://your-server.com/webhook.php');

// 2. Create webhook.php
$bot = new WorldMatesBot('wmb_TOKEN', 'https://domain.com');
$bot->onCommand('start', function($msg) use ($bot) { /* ... */ });
$bot->onCallbackQuery(function($q) use ($bot) { /* ... */ });
$bot->handleWebhook();
```

### Scaling Your Bot

1. **Add database persistence** for user data instead of using bot state
2. **Use webhooks** instead of polling for lower latency
3. **Add rate limiting awareness** - check `X-RateLimit-Remaining` headers
4. **Cache responses** - don't call APIs for static data on every message
5. **Use Redis** for session/state storage in high-traffic bots

---

## Best Practices

### Do's
- Always respond to `/start` - it's the first thing users see
- Keep responses concise - users expect quick bot replies
- Use inline keyboards for navigation (don't make users type everything)
- Handle errors gracefully - send user-friendly messages
- Set descriptive command descriptions
- Update `last_active_at` to show bot is alive

### Don'ts
- Don't spam users - only send messages they expect
- Don't store sensitive data in `callback_data` (it's visible)
- Don't ignore rate limits (30 req/sec, 1500 req/min by default)
- Don't forget to answer callback queries (users see a loading spinner)
- Don't make users wait - use typing indicators for long operations

### Security
- Never expose your `bot_token` in client-side code
- Verify webhook signatures
- Sanitize all user input
- Use HTTPS for webhook URLs
- Rotate bot tokens periodically

---

## Database Schema

15 tables support the Bot API:

| Table | Purpose |
|-------|---------|
| `Wo_Bots` | Bot accounts (tokens, settings, stats) |
| `Wo_Bot_Commands` | Registered slash commands |
| `Wo_Bot_Messages` | Message history (incoming/outgoing) |
| `Wo_Bot_Users` | Users who interact with bots |
| `Wo_Bot_Callbacks` | Inline button click tracking |
| `Wo_Bot_Polls` | Polls created by bots |
| `Wo_Bot_Poll_Options` | Poll answer options |
| `Wo_Bot_Poll_Votes` | User votes |
| `Wo_Bot_Webhook_Log` | Webhook delivery log |
| `Wo_Bot_Keyboards` | Reusable keyboard templates |
| `Wo_Bot_Tasks` | Task tracker bot data |
| `Wo_Bot_RSS_Feeds` | RSS bot subscriptions |
| `Wo_Bot_RSS_Items` | Posted RSS items (deduplication) |
| `Wo_Bot_Rate_Limits` | Rate limiting tracking |
| `Wo_Bot_Api_Keys` | Third-party developer API keys |

SQL migration file: `sql-DB-newver/add_bot_api_tables.sql`

---

## Troubleshooting

### Bot doesn't receive messages
1. Check bot status is `active` in Wo_Bots
2. Verify `bot_token` is correct
3. If using webhooks, check `Wo_Bot_Webhook_Log` for errors
4. If using polling, check `processed` field in `Wo_Bot_Messages`

### Inline keyboard not showing
1. Verify `reply_markup` JSON format
2. Check `callback_data` is under 256 characters
3. Ensure the message was sent successfully (check response)

### Rate limiting
- Default: 30 requests/second, 1500 requests/minute
- Check `Wo_Bot_Rate_Limits` table
- Contact admin to increase limits

### Webhook not delivering
1. Check URL is HTTPS and publicly accessible
2. Verify signature calculation
3. Check `Wo_Bot_Webhook_Log` for HTTP response codes
4. Webhook must respond within 10 seconds

---

## API Rate Limits

| Resource | Limit |
|----------|-------|
| API calls per second | 30 |
| API calls per minute | 1500 |
| Messages per chat per minute | 20 |
| Callback answers per second | 10 |
| Webhook max connections | 40 |

---

*WorldMates Bot API v1.0 - Built with love for developers*
