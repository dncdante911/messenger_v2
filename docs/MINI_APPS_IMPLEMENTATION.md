# WorldMates Mini Apps — Реализация

> **Статус:** ✅ Реализовано
> **Дата начала:** 2026-03-25
> **Ветка:** `claude/messenger-app-audit-O8voW`

---

## Что такое Mini Apps

Mini Apps (Web Apps) — это веб-приложения, которые открываются прямо внутри чата с ботом.
Разработчик бота указывает URL своего веб-приложения. Когда пользователь нажимает кнопку `web_app` в inline-клавиатуре, приложение открывается в полноэкранном WebView внутри WorldMates Messenger.

**Аналог:** Telegram Mini Apps (TWA), WhatsApp Business Mini Apps.

```
Пользователь нажимает [🌐 Открыть магазин]
         ↓
Android открывает MiniAppActivity (WebView)
         ↓
Сервер генерирует init_data (HMAC-подписанные данные пользователя)
         ↓
WebView загружает https://shop.example.com?initData=...
         ↓
Веб-приложение верифицирует init_data и показывает персональный контент
         ↓
Пользователь заполняет форму → нажимает "Подтвердить"
         ↓
JS: WorldMatesWebApp.sendData('{"order_id": 42}')
         ↓
Сервер доставляет боту webhook с update_type: "web_app_data"
```

---

## Чеклист реализации

### Бэкенд (Node.js)
- [x] `wo_bots.js` — поле `web_app_url` добавлено
- [x] `POST /api/node/bot/setWebApp` — установить URL Mini App для бота
- [x] `DELETE /api/node/bot/deleteWebApp` — удалить Mini App
- [x] `POST /api/node/bot/createWebAppToken` — генерация `init_data` для пользователя (user token)
- [x] `POST /api/node/bot/answerWebAppQuery` — отправить данные из Mini App боту (user token)
- [x] Webhook поддерживает `update_type: "web_app_data"`
- [x] Legacy Bot API: `set_web_app`, `delete_web_app`, `answer_web_app_query`
- [x] SQL-миграция: `ADD COLUMN web_app_url`

### Android
- [x] `Bot.kt` — добавлены `WebAppInfo`, `BotWebAppDataResponse`, обновлён `BotInlineButton`
- [x] `MiniAppActivity.kt` — полноэкранный WebView с JS-мостом `WorldMatesWebApp`
- [x] `BotChatComponents.kt` — иконка 🌐 для кнопок `web_app`
- [x] `AndroidManifest.xml` — зарегистрирована `MiniAppActivity`
- [x] `NodeBotApi.kt` — эндпоинты `getWebAppToken`, `answerWebAppQuery`
- [x] `strings.xml` — строки для Mini Apps

---

## Архитектура

### Схема аутентификации init_data

```
Клиент (Android)         Сервер (Node.js)         Бот-разработчик
      |                        |                          |
      | POST /createWebAppToken|                          |
      | {bot_id, access_token} |                          |
      |----------------------->|                          |
      |                        | Загружает данные юзера   |
      |                        | Строит строку:           |
      |                        |   auth_date=...&         |
      |                        |   user={id,name,...}&    |
      |                        |   query_id=...           |
      |                        | Подписывает HMAC-SHA256: |
      |                        |   key = HMAC(bot_token,  |
      |                        |         "WebAppData")    |
      |                        |   hash = HMAC(key, data) |
      | <----------------------|                          |
      | {init_data: "..."}     |                          |
      |                        |                          |
      | Открывает WebView(url) |                          |
      | Передаёт init_data     |                          |
      | через URL + JS inject  |                          |
      |                        |                          |
      | (пользователь работает с Mini App)                |
      |                        |                          |
      | sendData({...})        |                          |
      |                        |                          |
      | POST /answerWebAppQuery|                          |
      | {bot_id, result_data}  |                          |
      |----------------------->|                          |
      |                        | Сохраняет в DB           |
      |                        | Доставляет webhook:      |
      |                        | update_type=web_app_data |
      |                        |------------------------->|
```

### JS API для разработчиков Mini Apps

```javascript
// Объект доступен после загрузки страницы
const wm = window.WorldMatesWebApp;

// ── Данные пользователя ──────────────────────────────────────────────────────
wm.initData       // строка init_data (для верификации на вашем сервере)
wm.initDataUnsafe // объект {user, auth_date, query_id, hash}
wm.version        // "1.0"

// ── Управление окном ─────────────────────────────────────────────────────────
wm.close()                        // закрыть Mini App
wm.expand()                       // развернуть на весь экран (уже полный экран)
wm.ready()                        // сигнализировать о готовности страницы

// ── Отправка данных боту ─────────────────────────────────────────────────────
wm.sendData(data: string)         // отправить данные боту (webhook web_app_data)
                                  // data — строка до 4096 байт

// ── UI-вспомогательные методы ────────────────────────────────────────────────
wm.showAlert(message: string)     // показать нативный диалог Android
wm.showConfirm(message: string, callback: (ok: boolean) => void)
wm.hapticFeedback(type: string)   // "light" | "medium" | "heavy" | "selection"

// ── Тема ────────────────────────────────────────────────────────────────────
wm.themeParams   // {bg_color, text_color, hint_color, link_color, button_color, ...}
wm.colorScheme   // "light" | "dark"

// ── Кнопка "Главная" (MainButton) ────────────────────────────────────────────
wm.MainButton.text       = "Подтвердить заказ"
wm.MainButton.color      = "#2196F3"
wm.MainButton.textColor  = "#FFFFFF"
wm.MainButton.isVisible  = true
wm.MainButton.isActive   = true
wm.MainButton.onClick(callback)
wm.MainButton.show()
wm.MainButton.hide()
```

### Верификация init_data на стороне бота (Node.js пример)

```javascript
const crypto = require('crypto');

function verifyInitData(initDataString, botToken) {
    const params = new URLSearchParams(initDataString);
    const hash = params.get('hash');
    params.delete('hash');

    // Сортируем параметры по алфавиту
    const dataCheckString = [...params.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => `${k}=${v}`)
        .join('\n');

    // Ключ = HMAC-SHA256("WebAppData", botToken)
    const secretKey = crypto.createHmac('sha256', 'WebAppData')
                            .update(botToken)
                            .digest();

    const expectedHash = crypto.createHmac('sha256', secretKey)
                               .update(dataCheckString)
                               .digest('hex');

    return expectedHash === hash;
}

// Использование в вашем боте:
app.post('/mini-app-data', (req, res) => {
    const { init_data, payload } = req.body;
    if (!verifyInitData(init_data, process.env.BOT_TOKEN)) {
        return res.status(403).json({ error: 'Invalid init_data' });
    }
    // init_data верифицирован, обрабатываем заказ...
});
```

### Пример кнопки в боте (Bot SDK)

```javascript
bot.sendMessage(chatId, "Добро пожаловать в наш магазин!", {
    reply_markup: {
        inline_keyboard: [[
            {
                text: "🛍️ Открыть магазин",
                web_app: { url: "https://shop.example.com/mini-app" }
            }
        ], [
            { text: "ℹ️ Помощь", callback_data: "help" }
        ]]
    }
});

// Обработка данных из Mini App
bot.on('web_app_data', (update) => {
    const data = JSON.parse(update.web_app_data.data);
    console.log('Получены данные из Mini App:', data);
    // {order_id: 42, items: [...], total: 1500}

    bot.sendMessage(update.from.id, `✅ Заказ #${data.order_id} принят!`);
});
```

---

## Что НЕ было изменено

> Следующие файлы **не затронуты** данной реализацией:

- `utils/signal/` — Signal Protocol, Double Ratchet, X3DH (защищены claude.md)
- `MessagesViewModel.kt` — методы encryptForSend, decryptSignalMessage (защищены)
- Любая логика шифрования E2EE, AES-256-GCM, AES-128
- WebRTC / звонки / Coturn
- Существующие bot API эндпоинты (только новые добавлены)
- Существующие Socket.IO события (только новые добавлены)

---

## Файлы изменений

### Новые файлы
```
app/src/main/java/com/worldmates/messenger/ui/bots/MiniAppActivity.kt  ← НОВЫЙ
api-server-files/nodejs/database/migrations/add_web_app_url.sql        ← НОВЫЙ
```

### Изменённые файлы
```
api-server-files/nodejs/models/wo_bots.js               ← +web_app_url поле
api-server-files/nodejs/routes/bots/index.js            ← +4 эндпоинта Mini Apps
app/src/main/java/com/worldmates/messenger/data/model/Bot.kt             ← +WebAppInfo, обновлён BotInlineButton
app/src/main/java/com/worldmates/messenger/ui/bots/BotChatComponents.kt  ← web_app кнопки
app/src/main/AndroidManifest.xml                                         ← +MiniAppActivity
app/src/main/java/com/worldmates/messenger/network/NodeBotApi.kt         ← +2 эндпоинта
app/src/main/res/values/strings.xml                                      ← +строки Mini Apps
```

---

*Дата последнего обновления: 2026-03-25*
