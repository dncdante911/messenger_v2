# WorldStars ⭐ — Внутрішня валюта WorldMates

> **Версія документу:** 1.0 (початкова реалізація)
> **Статус:** В розробці — реалізовано повний стек

---

## Концепція

WorldStars — це внутрішня валюта мессенджера WorldMates. Принцип простий:
**ти купуєш зірки — і дариш їх людям, яких цінуєш.**

Це не "плати або страждай" — це спосіб висловити подяку, підтримати автора
або отримати щось особливе. Зручно, приємно, без нав'язливості.

---

## Що можна робити зі зірками

| Дія | Опис |
|-----|------|
| 🎁 **Подарувати користувачу** | Відправити зірки будь-якому користувачу в особистих повідомленнях або з його профілю |
| 🤖 **Підтримати бота** | Надіслати зірки боту/Mini App — розробник отримує винагороду |
| 📢 **Буст каналу** | Підняти публікацію в каналі, збільшити охоплення |
| 🖼️ **Ексклюзивний контент** | Розблокувати платний контент в каналах (майбутнє) |
| 🎨 **Купити стікерпак** | Придбати авторський пак стікерів у дизайнера (майбутнє) |

---

## Пакети зірок (ціни)

| Пакет | Зірки | Ціна | Примітка |
|-------|-------|------|----------|
| Starter | ⭐ 50 | 19 UAH | Спробувати |
| Basic | ⭐ 100 | 35 UAH | — |
| Popular | ⭐ 500 | 149 UAH | 🔥 Найпопулярніше |
| Value | ⭐ 1 000 | 279 UAH | -7% |
| Pro | ⭐ 5 000 | 1 199 UAH | -20% |

---

## Архітектура

```
Android App
  └── StarsActivity / StarsScreen (Jetpack Compose)
        └── StarsViewModel (StateFlow, coroutines)
              └── NodeStarsApi (Retrofit)
                    │
                    ▼
Node.js API (port 449)
  └── routes/stars.js
        ├── GET  /api/node/stars/balance        — баланс + останні транзакції
        ├── GET  /api/node/stars/transactions   — повна історія (pagination)
        ├── GET  /api/node/stars/packs          — список пакетів для покупки
        ├── POST /api/node/stars/send           — надіслати зірки користувачу
        ├── POST /api/node/stars/purchase       — ініціювати оплату
        ├── POST /api/node/stars/wayforpay-webhook
        └── POST /api/node/stars/liqpay-webhook
              │
              ▼
MySQL Database
  ├── wm_stars_balance      — поточний баланс кожного юзера
  └── wm_stars_transactions — вся історія операцій
```

---

## База даних

### `wm_stars_balance`
| Поле | Тип | Опис |
|------|-----|------|
| `user_id` | INT UNSIGNED PK | ID користувача |
| `balance` | INT UNSIGNED | Поточний баланс зірок |
| `total_purchased` | INT UNSIGNED | Всього куплено за весь час |
| `total_sent` | INT UNSIGNED | Всього відправлено |
| `total_received` | INT UNSIGNED | Всього отримано |
| `updated_at` | TIMESTAMP | Час останньої зміни |

### `wm_stars_transactions`
| Поле | Тип | Опис |
|------|-----|------|
| `id` | INT UNSIGNED PK | ID транзакції |
| `from_user_id` | INT | Хто відправив (NULL = система/покупка) |
| `to_user_id` | INT | Хто отримав |
| `amount` | INT UNSIGNED | Кількість зірок |
| `type` | ENUM | `purchase` / `send` / `receive` / `refund` |
| `ref_type` | VARCHAR | `user` / `bot` / `channel` / `pack` |
| `ref_id` | INT | ID ref-об'єкта |
| `note` | VARCHAR | Коментар відправника |
| `order_id` | VARCHAR | ID платежу (для purchase) |
| `created_at` | TIMESTAMP | Час транзакції |

---

## API Endpoints

### `GET /api/node/stars/balance`
Повертає баланс та останні 10 транзакцій.

```json
{
  "api_status": 200,
  "balance": 350,
  "total_purchased": 600,
  "total_sent": 200,
  "total_received": 50,
  "recent_transactions": [...]
}
```

### `GET /api/node/stars/transactions?limit=20&offset=0`
Повна пагінована історія транзакцій.

### `GET /api/node/stars/packs`
Список пакетів зірок з актуальними цінами.

### `POST /api/node/stars/send`
```json
{ "to_user_id": 123, "amount": 50, "note": "Дякую!" }
```

### `POST /api/node/stars/purchase`
```json
{ "pack_id": 2, "provider": "wayforpay" }
```
Повертає `payment_url` для редиректу в браузер.

### Webhooks (без авторизації)
- `POST /api/node/stars/wayforpay-webhook`
- `POST /api/node/stars/liqpay-webhook`

---

## Android — структура файлів

```
ui/stars/
  ├── StarsActivity.kt     — Activity-обгортка (як PremiumActivity)
  ├── StarsScreen.kt       — Compose UI (баланс, транзакції, покупка, відправка)
  └── StarsViewModel.kt    — ViewModel зі StateFlow

network/
  └── NodeStarsApi.kt      — Retrofit інтерфейс + DTO моделі
```

### Екрани (вкладки всередині StarsScreen)
1. **Баланс** — велика зірка, поточний баланс, остання активність
2. **Поповнити** — сітка пакетів, вибір платіжки, кнопка оплати
3. **Надіслати** — пошук користувача, сума, коментар, кнопка відправки

---

## Локалізація

Всі рядки у `values/strings.xml` (Ukrainian) та `values-ru/strings.xml` (Russian).
Префікс: `stars_*`

---

## Прогрес реалізації

- [x] Документ (WORLDSTARS.md)
- [x] Database migration (`add_worldstars_tables.sql`)
- [x] Node.js backend (`routes/stars.js`)
- [x] Реєстрація роуту в `main.js`
- [x] Android Retrofit API (`NodeStarsApi.kt`)
- [x] Реєстрація в `NodeRetrofitClient.kt`
- [x] ViewModel (`StarsViewModel.kt`)
- [x] UI (StarsActivity.kt + StarsScreen.kt)
- [x] Навігація (ChatsActivity drawer)
- [x] Локалізація UK + RU

---

## Майбутні можливості (roadmap)

- [ ] Відправка зірок прямо з чату (кнопка в тулбарі)
- [ ] Ексклюзивний контент в каналах (платні пости)
- [ ] Авторські стікерпаки за зірки
- [ ] Статистика для розробників ботів (скільки зірок отримав бот)
- [ ] Буст публікацій в каналах
- [ ] Рейтинг щедрості (топ відправників)
- [ ] Реферальні бонуси зірками
