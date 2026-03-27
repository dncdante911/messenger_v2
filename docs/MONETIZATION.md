# WorldMates — Монетизация: полное описание

> **Философия:** WorldMates — свободный мессенджер. PRO — набор бонусов и удобств.
> Базовые функции (чаты, звонки, группы, каналы) **бесплатны навсегда** и никогда не ограничиваются.

---

## Содержание

1. [Источники дохода](#1-источники-дохода)
2. [PRO подписка](#2-pro-подписка)
3. [WorldStars (внутренняя валюта)](#3-worldstars-внутренняя-валюта)
4. [Бесплатный пробный период](#4-бесплатный-пробный-период-7-дней)
5. [Подарить PRO подписку](#5-подарить-pro-подписку-за-worldstars)
6. [PRO-паки стикеров](#6-pro-паки-стикеров-strapi)
7. [Доход создателей](#7-доход-создателей-стикер-паков)
8. [Защита от дублей (idempotency)](#8-защита-от-дублей-webhooks)
9. [Где менять цены и настройки](#9-где-менять-цены-и-настройки)
10. [Таблицы БД](#10-таблицы-бд)
11. [API-эндпоинты](#11-api-эндпоинты)
12. [Android-интеграция](#12-android-интеграция)
13. [Что бесплатно, что PRO](#13-что-бесплатно-что-pro)

---

## 1. Источники дохода

| Источник | Механизм | Файл |
|----------|----------|------|
| PRO подписка | Реальные деньги (Way4Pay / LiqPay / Monobank) | `routes/subscription.js` |
| WorldStars | Покупка пакетов звёзд за реальные деньги | `routes/stars.js` |
| PRO-паки стикеров | Трата WorldStars | `routes/stickers.js` |
| Подарок PRO | Трата WorldStars | `routes/subscription.js` |

---

## 2. PRO подписка

### Как работает

1. Пользователь выбирает длительность (1–24 месяца) и платёжный провайдер
2. Сервер создаёт invoice через API провайдера, возвращает `payment_url`
3. Приложение открывает URL в браузере/WebView
4. После оплаты провайдер вызывает webhook
5. Webhook активирует PRO: `Wo_Users.is_pro = 1`, `pro_time = UNIX_timestamp_expiry`
6. При продлении — `pro_time` увеличивается от текущего значения (не сбрасывается)

### Провайдеры

| Провайдер | Webhook URL | Env-переменные |
|-----------|-------------|----------------|
| Way4Pay | `POST /api/node/subscription/wayforpay-webhook` | `WAYFORPAY_MERCHANT_ACCOUNT`, `WAYFORPAY_MERCHANT_SECRET`, `WAYFORPAY_MERCHANT_DOMAIN` |
| LiqPay | `POST /api/node/subscription/liqpay-webhook` | `LIQPAY_PUBLIC_KEY`, `LIQPAY_PRIVATE_KEY` |
| Monobank | `POST /api/node/subscription/monobank-webhook` | `MONOBANK_TOKEN`, `MONOBANK_WEBHOOK_SECRET` (опц.) |

### Цены

```
Базовая цена: 149 UAH/мес  (SUBSCRIPTION_PRICE_UAH в .env)

Скидки за длительность:
  1 мес     → 100%  = 149 UAH
  2–3 мес   → 95%   = ~142 UAH/мес
  4–6 мес   → 90%   = ~134 UAH/мес
  7–12 мес  → 85%   = ~127 UAH/мес
  13–24 мес → 80%   = ~119 UAH/мес
```

---

## 3. WorldStars (внутренняя валюта)

### Как пополнить

Покупка пакетов за реальные деньги (Way4Pay / LiqPay):

| Пакет | Звёзд | Цена |
|-------|:-----:|------|
| Starter | 100 ⭐ | ~29 UAH |
| Standard | 500 ⭐ | ~99 UAH |
| Popular | 1000 ⭐ | ~179 UAH |
| Pro | 2500 ⭐ | ~399 UAH |
| Ultimate | 5000 ⭐ | ~699 UAH |

*(Точные цены — в массиве `STAR_PACKS` в `routes/stars.js`, строка ~25)*

### Как тратить

- Покупка PRO-паков стикеров
- Подарить PRO подписку другому пользователю
- Отправить звёзды любому пользователю (`POST /api/node/stars/send`)

### Отправка звёзд

Любой пользователь может отправить звёзды другому (как подарок или «чаевые»). Звёзды **не теряются** — они перемещаются между балансами.

---

## 4. Бесплатный пробный период (7 дней)

### Условия
- **Один раз на аккаунт**, навсегда. Флаг `Wo_Users.trial_used = 1` после активации.
- Активировать можно в любой момент (не только при регистрации).
- Если уже активен PRO — trial добавляет 7 дней сверху.
- После истечения — обычный экран "продолжить PRO" с кнопкой оплаты.

### Пример UX

```
[При первом входе / в настройках PRO]

  ┌─────────────────────────────────────┐
  │  🎁 7 дней WorldMates PRO бесплатно │
  │                                     │
  │  Попробуй все PRO-функции без       │
  │  ограничений. Не нужна карта.       │
  │                                     │
  │  [Попробовать бесплатно]            │
  └─────────────────────────────────────┘

[После 7 дней — уведомление]

  ┌─────────────────────────────────────┐
  │  ⏰ Твой пробный период закончился  │
  │                                     │
  │  Понравилось? Продолжи за 149 UAH/м │
  │                                     │
  │  [Оформить подписку]  [Не сейчас]   │
  └─────────────────────────────────────┘
```

### API

```
POST /api/node/subscription/start-trial
Authorization: access-token

Response (первый раз):
{ "api_status": 200, "already_used": false, "trial_days": 7, "pro_time": 1753000000 }

Response (повторно):
{ "api_status": 200, "already_used": true, "error_message": "Free trial has already been used" }
```

---

## 5. Подарить PRO подписку за WorldStars

### Стоимость

```
500 WorldStars = 1 месяц PRO для получателя
```

**Где менять:** `routes/subscription.js`, константа `GIFT_PRICE_STARS_MONTH = 500`

| Длительность | Звёзд |
|:------------:|:-----:|
| 1 месяц | 500 ⭐ |
| 3 месяца | 1500 ⭐ |
| 6 месяцев | 3000 ⭐ |
| 12 месяцев | 6000 ⭐ |

### Поток

1. Отправитель выбирает получателя и длительность
2. Приложение запрашивает `GET /gift-price` — получает актуальные цены
3. Показывает подтверждение: «Подарить 3 мес PRO пользователю @username за 1500 ⭐?»
4. POST `/gift` — списывает звёзды, активирует PRO получателю
5. Получатель получает уведомление «@sender подарил тебе 3 месяца PRO!»

### API

```
POST /api/node/subscription/gift
Authorization: access-token
Body: { "to_user_id": 42, "months": 3 }

Response:
{
  "api_status": 200,
  "months": 3,
  "stars_spent": 1500,
  "new_balance": 2000,
  "recipient_pro_time": 1761000000,
  "stars_per_month": 500
}

Ошибка — мало звёзд:
{
  "api_status": 400,
  "error_message": "Not enough WorldStars. Need 1500, have 800",
  "balance": 800,
  "required": 1500,
  "stars_per_month": 500
}
```

---

## 6. PRO-паки стикеров (Strapi)

### Как работает

- Все стикер-паки хранятся в Strapi CMS на `cdn.worldmates.club`
- Обычные паки — бесплатны для всех
- PRO-паки — зарегистрированы в БД (`wm_sticker_pro_packs`) с ценой в WorldStars
- Покупка разовая — после покупки пак доступен навсегда

### Регистрация PRO-пака (администратор)

```
POST /api/node/stickers/admin/pro
Authorization: access-token  (только admin-аккаунт)
Body: {
  "slug": "cyberpunk-pack",
  "stars_price": 100,
  "creator_user_id": 1234   // опционально: ID создателя (получит 70% с продаж)
}

Response:
{ "api_status": 200, "slug": "cyberpunk-pack", "stars_price": 100,
  "creator_user_id": 1234, "creator_share_pct": 70 }
```

### Покупка (пользователь)

```
POST /api/node/stickers/strapi-buy
Authorization: access-token
Body: { "slug": "cyberpunk-pack" }

Response:
{ "api_status": 200, "new_balance": 450 }
```

---

## 7. Доход создателей стикер-паков

### Схема

```
Покупатель платит 100 ⭐
    ├── 70 ⭐ → Создатель пака  (зачисляются на баланс WorldStars)
    └── 30 ⭐ → Платформа       (остаются на счету, не начисляются никому)
```

**Где менять:** `routes/stickers.js`, константа `CREATOR_SHARE = 0.7` (0.7 = 70%)

### Условия

- Создатель должен быть указан при регистрации пака (`creator_user_id`)
- Если `creator_user_id` не указан — 100% остаётся на платформе
- Создатель не может купить собственный пак (логика в endpoint)
- Накопленные WorldStars создатель может тратить на подарки, другие паки или (в будущем) выводить

---

## 8. Защита от дублей (webhooks)

Платёжные шлюзы иногда отправляют один webhook **дважды**. Без защиты это означало бы двойное начисление звёзд или двойное продление PRO.

### Реализация

- **Звёзды (stars.js):** перед начислением — проверка `wm_stars_transactions.order_id UNIQUE`. Если запись с таким `order_id` уже есть → пропускаем, логируем.
- **Подписка (subscription.js):** перед активацией — проверка `wm_subscription_orders.order_id UNIQUE`. Аналогично.

```sql
-- При повторном вебхуке:
SELECT id FROM wm_stars_transactions WHERE order_id = 'WMS-123-1700000000' LIMIT 1;
-- Найдено → пропустить, ответить 200 OK (шлюз перестанет слать)
```

---

## 9. Где менять цены и настройки

| Что | Где | Пример |
|-----|-----|--------|
| Цена PRO подписки (UAH/мес) | `.env`: `SUBSCRIPTION_PRICE_UAH=149` | `SUBSCRIPTION_PRICE_UAH=199` |
| Длительность триала | `routes/subscription.js`: `TRIAL_DAYS = 7` | Строка ~20 |
| Цена подарка PRO (звёзды/мес) | `routes/subscription.js`: `GIFT_PRICE_STARS_MONTH = 500` | Строка ~21 |
| Доля создателя стикер-пака | `routes/stickers.js`: `CREATOR_SHARE = 0.7` | Строка ~11 |
| Пакеты звёзд (кол-во + цена) | `routes/stars.js`: массив `STAR_PACKS` | Строка ~25 |
| Скидки за длительность PRO | `routes/subscription.js`: функция `calcPrice()` | Строка ~40 |

---

## 10. Таблицы БД

### `wm_stars_balance`
| Колонка | Тип | Описание |
|---------|-----|----------|
| user_id | INT PK | Пользователь |
| balance | INT | Текущий баланс |
| total_purchased | INT | Сумма всех покупок |
| total_sent | INT | Сумма всех отправок |
| total_received | INT | Сумма всех поступлений |

### `wm_stars_transactions`
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | INT PK | |
| from_user_id | INT NULL | Отправитель (NULL = пополнение) |
| to_user_id | INT | Получатель |
| amount | INT | Кол-во звёзд |
| type | ENUM | `purchase`, `send`, `receive`, `refund` |
| ref_type | VARCHAR | `pack`, `sticker_pack`, `gift_pro` и т.д. |
| order_id | VARCHAR **UNIQUE** | ID заказа от шлюза (для идемпотентности) |

### `wm_sticker_pro_packs`
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | INT PK | |
| slug | VARCHAR **UNIQUE** | Slug пака из Strapi |
| stars_price | INT | Цена в WorldStars |
| creator_user_id | INT NULL | Создатель (получает 70%) |

### `wm_sticker_purchases`
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | INT PK | |
| user_id | INT | Покупатель |
| slug | VARCHAR | Slug пака |
| stars_paid | INT | Уплачено звёзд |
| UNIQUE | (user_id, slug) | Нельзя купить дважды |

### `wm_subscription_orders`
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | INT PK | |
| order_id | VARCHAR **UNIQUE** | ID заказа от шлюза |
| user_id | INT | Пользователь |
| provider | VARCHAR | `wayforpay`, `liqpay`, `monobank` |
| months | TINYINT | Длительность |
| amount_uah | INT | Сумма в UAH |
| processed_at | TIMESTAMP | Время обработки |

### Изменения в `Wo_Users`
| Колонка | Тип | Описание |
|---------|-----|----------|
| is_pro | TINYINT | 1 = PRO активен |
| pro_time | INT | UNIX-timestamp окончания PRO |
| trial_used | TINYINT | 1 = пробный период уже использован |

---

## 11. API-эндпоинты

### Подписка

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/node/subscription/status` | Статус подписки |
| POST | `/api/node/subscription/create-payment` | Создать платёж (Way4Pay/LiqPay/Monobank) |
| POST | `/api/node/subscription/wayforpay-webhook` | Вебхук Way4Pay (без auth) |
| POST | `/api/node/subscription/liqpay-webhook` | Вебхук LiqPay (без auth) |
| POST | `/api/node/subscription/monobank-webhook` | Вебхук Monobank (без auth) |
| POST | `/api/node/subscription/start-trial` | Активировать триал (1 раз) |
| POST | `/api/node/subscription/gift` | Подарить PRO за WorldStars |
| GET | `/api/node/subscription/gift-price` | Цены на подарок PRO |

### WorldStars

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/node/stars/balance` | Баланс + транзакции |
| GET | `/api/node/stars/transactions` | История транзакций |
| GET | `/api/node/stars/packs` | Пакеты для покупки |
| POST | `/api/node/stars/send` | Отправить звёзды |
| POST | `/api/node/stars/purchase` | Купить звёзды |
| POST | `/api/node/stars/wayforpay-webhook` | Вебхук Way4Pay (без auth) |
| POST | `/api/node/stars/liqpay-webhook` | Вебхук LiqPay (без auth) |

### Стикеры

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/node/stickers/strapi-meta` | PRO-статус паков для юзера |
| POST | `/api/node/stickers/strapi-buy` | Купить PRO-пак |
| POST | `/api/node/stickers/admin/pro` | (admin) Зарегистрировать PRO-пак |

---

## 12. Android-интеграция

### NodeSubscriptionApi

```kotlin
// Проверить статус
val status = NodeRetrofitClient.subscriptionApi.getStatus()
if (status.isPro == 1) { /* показать PRO UI */ }

// Начать триал
val trial = NodeRetrofitClient.subscriptionApi.startTrial()
if (trial.alreadyUsed) { /* показать кнопку оплаты */ }
else { /* активирован, обновить UserSession */ }

// Купить подарок
val gift = NodeRetrofitClient.subscriptionApi.giftSubscription(
    toUserId = 42,
    months   = 3
)
// gift.starsSpent, gift.newBalance

// Узнать цены на подарок
val prices = NodeRetrofitClient.subscriptionApi.getGiftPrice()
// prices.starsPerMonth = 500
// prices.plans = [{months:1, stars:500}, {months:3, stars:1500}, ...]
```

### Проверка PRO статуса везде в коде

```kotlin
// UserSession.kt — единый источник истины
UserSession.isProActive  // Boolean: is_pro=1 AND pro_time > now()

// Примеры:
if (UserSession.isProActive) showTranscriptButton()
if (UserSession.isProActive) allowLargeFileUpload()
```

### Где обновить UserSession после оплаты

После успешного `start-trial`, покупки или получения подарка — вызвать:

```kotlin
val status = NodeRetrofitClient.subscriptionApi.getStatus()
UserSession.updateProStatus(
    isPro       = status.isPro,
    proType     = status.proType,
    proExpiresAt = status.proTime * 1000L  // секунды → миллисекунды
)
```

---

## 13. Что бесплатно, что PRO

### Бесплатно навсегда ✅

- Личные чаты, группы, каналы
- Голосовые и видео звонки (до 25 участников)
- Отправка фото, видео, файлов, голосовых
- E2EE (Signal Protocol) — секретные чаты
- Stories, реакции, ответы
- Стандартные стикер-паки из Strapi
- Поиск, закладки, папки
- Боты

### PRO (бонусы, не ограничения) ⭐

| Функция | PRO |
|---------|:---:|
| Транскрипция голосовых → текст (AI Whisper) | ✅ |
| PRO-паки стикеров и анимированных эмодзи | ✅ |
| Закреп до 15 сообщений (Free: 5) | ✅ |
| Групповые звонки до 50+ участников | ✅ |
| Бизнес-режим (расписание, автоответ, quick replies) | ✅ |
| Повышенные лимиты загрузки | ✅ |
| Видеокружки высокого качества | ✅ |
| Значок PRO в профиле | ✅ |

---

*Документ актуален на Март 2026. Обновляется при изменении монетизации.*
