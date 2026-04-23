# Промо-коды / Инвайт-коды — WorldMates Messenger

## Форматы кодов

```
# Обычные
ULTRA-XXXX-XXXX-XXXX     →  Lifetime premium (действует до 2099-12-31)
PRO-XXXX-XXXX-XXXX       →  +1 год от момента активации

# Блогерские (с префиксом 2–20 символов A-Z0-9)
IVANGAI-ULTRA-XXXX-XXXX-XXXX
IVANGAI-PRO-XXXX-XXXX-XXXX
```

`XXXX` — случайный hex (0–9, A–F). Регистр не важен — приводится к верхнему автоматически.

---

## Что получает пользователь при активации

| Тип кода | `pro_type` | `pro_time` |
|----------|-----------|-----------|
| ULTRA | `3` (lifetime) | до 2099-12-31 |
| PRO | `2` (annual) | now + 365 дней |
| *(без кода, user_id 101–850)* | `1` (early adopter) | now + 90 дней |

---

## Где и как активировать

Код вводится **при регистрации** в поле `invite_code` (POST `/api/node/auth/register`).  
Нельзя активировать после создания аккаунта — только в момент регистрации.

**Защита от перебора:** 5 неверных попыток с одного IP → блокировка на 1 час (Redis).

---

## Генерация кодов

Скрипт находится в `api-server-files/nodejs/scripts/`:

```bash
cd api-server-files/nodejs/scripts
```

### Стандартный батч (500 ULTRA + 2000 PRO)
```bash
node generate_invite_codes.js
```

### Только определённый тип
```bash
node generate_invite_codes.js --ultra 0 --pro 500
node generate_invite_codes.js --ultra 100 --pro 0
```

### Блогерские коды (нужно явно указать количество)
```bash
# 50 ULTRA + 100 PRO для блогера IVANGAI
node generate_invite_codes.js --blogger IVANGAI --ultra 50 --pro 100

# Только PRO для другого блогера
node generate_invite_codes.js --blogger WYLSACOM --pro 200

# Только ULTRA
node generate_invite_codes.js --blogger PIKABU --ultra 30
```

Скрипт выведет 5 примеров сгенерированных кодов в консоль. Остальные сразу попадают в БД.

**Требует:** `npm install mysql2` (если не установлен).  
**DB config:** читает из `../config/database.js` или из переменных окружения `DB_HOST`, `DB_USER`, `DB_PASS`, `DB_NAME`.

---

## SQL-запросы

### Статистика по кодам
```sql
SELECT
    type,
    blogger_tag,
    COUNT(*)                    AS total,
    SUM(is_used)                AS used,
    COUNT(*) - SUM(is_used)     AS available
FROM wm_invite_codes
GROUP BY type, blogger_tag
ORDER BY blogger_tag IS NULL DESC, type;
```

### Кто и когда активировал код
```sql
SELECT
    c.code,
    c.type,
    c.blogger_tag,
    u.username,
    u.email,
    FROM_UNIXTIME(c.used_at)    AS activated_at
FROM wm_invite_codes c
JOIN wo_users u ON u.user_id = c.used_by
WHERE c.is_used = 1
ORDER BY c.used_at DESC;
```

### Все коды конкретного блогера
```sql
SELECT
    code,
    type,
    is_used,
    used_by,
    FROM_UNIXTIME(used_at)  AS used_at,
    FROM_UNIXTIME(expires_at) AS expires_at
FROM wm_invite_codes
WHERE blogger_tag = 'IVANGAI'   -- замени на нужный тег
ORDER BY is_used ASC, id ASC;
```

### Свободные коды (для выдачи)
```sql
SELECT code, type, blogger_tag,
       FROM_UNIXTIME(expires_at) AS expires_at
FROM wm_invite_codes
WHERE is_used = 0
  AND (expires_at IS NULL OR expires_at > UNIX_TIMESTAMP())
ORDER BY type, blogger_tag IS NULL DESC
LIMIT 50;
```

### Истёкшие неиспользованные коды
```sql
SELECT code, type, blogger_tag, FROM_UNIXTIME(expires_at) AS expired_at
FROM wm_invite_codes
WHERE is_used = 0
  AND expires_at IS NOT NULL
  AND expires_at < UNIX_TIMESTAMP();
```

---

## REST API для модераторов

Все эндпоинты требуют `is_admin = 1` у пользователя.

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/node/moderation/queue` | Очередь ожидающих проверки |
| `POST` | `/api/node/moderation/approve` | Одобрить `{ queue_id }` |
| `POST` | `/api/node/moderation/reject` | Отклонить `{ queue_id, reason }` |
| `GET` | `/api/node/moderation/blacklist` | Список хэш-блэклиста |
| `POST` | `/api/node/moderation/blacklist/add` | Добавить хэш `{ sha256, reason }` |
| `POST` | `/api/node/moderation/content-policy` | Установить политику канала/группы |
| `GET` | `/api/node/moderation/stats` | Статистика очереди |

---

## Структура таблицы

```sql
-- wm_invite_codes
id          INT           -- auto increment
code        VARCHAR(32)   -- уникальный код (UNIQUE)
type        ENUM('ultra','pro')
blogger_tag VARCHAR(32)   -- NULL = обычный код
is_used     TINYINT(1)    -- 0 = свободен, 1 = использован
used_by     INT           -- user_id активировавшего
used_at     INT           -- unix timestamp активации
created_at  INT           -- unix timestamp создания
expires_at  INT           -- unix timestamp истечения (NULL = бессрочно)
```
