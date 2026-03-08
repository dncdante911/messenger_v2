# Підключення платіжних систем: Way4Pay та LiqPay

## Зміст
1. [Way4Pay — покрокове підключення](#1-way4pay)
2. [LiqPay — покрокове підключення](#2-liqpay)
3. [Налаштування .env на сервері](#3-env)
4. [Запуск міграції БД](#4-db-migration)
5. [Тестування](#5-testing)
6. [Усунення проблем](#6-troubleshooting)

---

## 1. Way4Pay

### 1.1. Реєстрація та кабінет

1. Перейди на [https://way4pay.com](https://way4pay.com) → «Підключитись»
2. Заповни форму реєстрації магазину (назва: "WorldMates PRO", сайт: `worldmates.club`)
3. Після підтвердження email увійди до **кабінету мерчанта**

### 1.2. Отримання ключів

1. У кабінеті: **Налаштування → Мерчанти**
2. Вибери або створи мерчанта
3. Запиши два значення:
   - **Merchant Login** (він же `merchantAccount`) — наприклад: `worldmates_club`
   - **Secret Key** — натисни «Показати ключ» і скопіюй рядок (48 символів)

### 1.3. Налаштування домену і Webhook URL

1. У розділі **Мерчанти → Налаштування мерчанта**:
   - **Домен сайту**: `worldmates.club`
   - **URL для сповіщень (Webhook/Service URL)**:
     ```
     https://worldmates.club:449/api/node/subscription/wayforpay-webhook
     ```
2. Збережи налаштування

### 1.4. Увімкнення тестового режиму (опційно)

- Перемкни **Тестовий режим → Увімкнений**
- У тестовому режимі картка: `4111111111111111`, CVV: `123`, термін: будь-який майбутній
- Деактивуй тестовий режим перед запуском у продакшн

---

## 2. LiqPay

### 2.1. Реєстрація

1. Перейди на [https://www.liqpay.ua](https://www.liqpay.ua) → «Зареєструватись»
2. Підтвердь телефон та ідентифікуй себе (або компанію)
3. Після активації переходь до **Кабінету розробника**

### 2.2. Отримання ключів

1. **Кабінет → Магазин → Налаштування**
2. Вибери свій магазин (або створи: назва "WorldMates PRO", сайт `worldmates.club`)
3. Запиши:
   - **Public key** — починається з `i_` (наприклад: `i_sRkDmfkKnVLuCwHZoHMlrQ`)
   - **Private key** — починається з `i_` або `p_` (наприклад: `p_Zs...`)

   > ⚠️ **Ніколи не публікуй Private key у відкритому репозиторії!**

### 2.3. Налаштування Callback URL

1. **Кабінет → Магазин → Налаштування**:
   - **Server Callback URL**:
     ```
     https://worldmates.club:449/api/node/subscription/liqpay-webhook
     ```
2. Збережи

### 2.4. Тестовий режим

- LiqPay має sandbox: у `create-payment` передай `action: "sandbox"` замість `"pay"` (або додай параметр `sandbox: 1` у payload)
- Тестові картки: [https://www.liqpay.ua/documentation/api/sandbox](https://www.liqpay.ua/documentation/api/sandbox)

---

## 3. Налаштування .env на сервері

Відкрий файл `/var/www/www-root/data/www/worldmates.club/api-server-files/nodejs/.env`
(якщо файл не існує — створи його в тій же папці, де `main.js`).

Додай такі рядки:

```dotenv
# ── Way4Pay ───────────────────────────────────────────────────────────────────
WAYFORPAY_MERCHANT_ACCOUNT=ТУТ_ВСТАВЬ_MERCHANT_LOGIN
WAYFORPAY_MERCHANT_SECRET=ТУТ_ВСТАВЬ_SECRET_KEY
WAYFORPAY_MERCHANT_DOMAIN=worldmates.club

# ── LiqPay ────────────────────────────────────────────────────────────────────
LIQPAY_PUBLIC_KEY=ТУТ_ВСТАВЬ_PUBLIC_KEY
LIQPAY_PRIVATE_KEY=ТУТ_ВСТАВЬ_PRIVATE_KEY

# ── Загальне ──────────────────────────────────────────────────────────────────
SITE_URL=https://worldmates.club
SUBSCRIPTION_PRICE_UAH=149
```

### Де що взяти:

| Змінна | Де взяти |
|--------|----------|
| `WAYFORPAY_MERCHANT_ACCOUNT` | Way4Pay кабінет → Мерчанти → Merchant Login |
| `WAYFORPAY_MERCHANT_SECRET` | Way4Pay кабінет → Мерчанти → Secret Key |
| `WAYFORPAY_MERCHANT_DOMAIN` | Твій домен без https:// |
| `LIQPAY_PUBLIC_KEY` | LiqPay кабінет → Магазин → Public key |
| `LIQPAY_PRIVATE_KEY` | LiqPay кабінет → Магазин → Private key |
| `SUBSCRIPTION_PRICE_UAH` | Базова ціна 1 місяця в гривнях (за замовчуванням 149) |

### Перезапусти Node.js після змін:

```bash
pm2 restart worldmates-node
# або
systemctl restart worldmates-node
```

---

## 4. Міграція БД

Запусти SQL-міграцію один раз:

```bash
mysql -u social -p socialhub < /var/www/.../api-server-files/database/migrations/add_subscription_payments_table.sql
```

Або через phpMyAdmin / DBeaver — відкрий файл:
```
api-server-files/database/migrations/add_subscription_payments_table.sql
```
і виконай.

---

## 5. Тестування

### 5.1. Перевірка статусу (curl)

```bash
curl -X GET https://worldmates.club:449/api/node/subscription/status \
  -H "access-token: ВАШ_ACCESS_TOKEN"
```

Очікувана відповідь:
```json
{
  "api_status": 200,
  "is_pro": 0,
  "pro_type": 0,
  "pro_time": 0,
  "days_left": 0
}
```

### 5.2. Створення платежу (curl)

```bash
curl -X POST https://worldmates.club:449/api/node/subscription/create-payment \
  -H "access-token: ВАШ_ACCESS_TOKEN" \
  -d "months=3&provider=liqpay"
```

Очікувана відповідь:
```json
{
  "api_status": 200,
  "provider": "liqpay",
  "payment_url": "https://www.liqpay.ua/api/3/checkout?data=...",
  "order_id": "WM-12345-1717000000000",
  "amount_uah": 425,
  "months": 3
}
```

### 5.3. Симуляція webhook Way4Pay

```bash
curl -X POST https://worldmates.club:449/api/node/subscription/wayforpay-webhook \
  -H "Content-Type: application/json" \
  -d '{
    "merchantAccount": "ВАШ_MERCHANT_LOGIN",
    "orderReference": "WM-12345-1717000000000",
    "amount": "425.00",
    "currency": "UAH",
    "authCode": "123456",
    "cardPan": "4111111111111111",
    "transactionStatus": "Approved",
    "reasonCode": "1100",
    "merchantSignature": "ПІДПИС"
  }'
```

---

## 6. Усунення проблем

### Way4Pay повертає помилку підпису
- Перевір `WAYFORPAY_MERCHANT_ACCOUNT` і `WAYFORPAY_MERCHANT_SECRET` — без пробілів
- Перевір `WAYFORPAY_MERCHANT_DOMAIN` — тільки домен, без `https://` і без слешу в кінці

### LiqPay повертає `WRONG_CREDENTIALS`
- Переконайся, що `LIQPAY_PUBLIC_KEY` починається з `i_`
- Магазин у LiqPay повинен бути активований (пройти верифікацію)

### Webhook не приходить
- Перевір, що порт 449 відкритий для зовнішніх запитів у firewall:
  ```bash
  ufw allow 449/tcp
  iptables -I INPUT -p tcp --dport 449 -j ACCEPT
  ```
- Way4Pay і LiqPay роблять POST-запит на твій SITE_URL. Переконайся, що `https://worldmates.club:449` доступний ззовні.

### Android не відкриває браузер
- Перевір, що `internet` permission є в `AndroidManifest.xml` (вже є)
- Переконайся, що `payment_url` повертається не порожнім (перевір логи Node.js)

---

## Цінова сітка (для довідки)

| Місяців | Знижка | Сума (₴) | За місяць (₴) |
|---------|--------|----------|--------------|
| 1       | 0%     | 149      | 149          |
| 2-3     | 5%     | 283–425  | 141–142      |
| 4-6     | 10%    | 537–805  | 134          |
| 7-12    | 15%    | 884–1519 | 126–127      |
| 13-24   | 20%    | 1545–2851| 119          |

Базова ціна визначається змінною `SUBSCRIPTION_PRICE_UAH` в `.env` (за замовчуванням: 149 ₴/міс).
