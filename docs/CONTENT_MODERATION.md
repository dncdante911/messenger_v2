# Система модерации контента — WorldMates Messenger

> **Статус:** В разработке
> **Язык реализации:** Node.js (бэкенд) + Python (NudeNet сервис)
> **Ветка:** `claude/fix-backend-stability-VuOeH`

---

## 1. Что будет реализовано

### Уровень 1 — NudeNet ML-сервис (Python)
Отдельный Python-процесс, который запускается рядом с Node.js сервером.
Принимает изображение → возвращает категории контента + уровень угрозы.
Без него — только хэш-проверка. С ним — 80%+ автоматики по изображениям.

### Уровень 2 — Хэш-блэклист (Node.js + MySQL)
Каждый файл, удалённый модератором, хэшируется (SHA-256 точно + pHash визуально).
Повторная загрузка того же или похожего файла → автоблок без ML.
База растёт сама по мере работы модераторов.

### Уровень 3 — Политики контента по типу канала/группы
| Тип | Правило |
|-----|---------|
| `public` — открытый канал/группа | Любой NSFW → немедленный блок |
| `news` — новостной канал | Частичная обнажёнка → блюр + доставка |
| `private_adult` — закрытый 18+ | Эротика/обнажёнка → доставка; явное порно → очередь |

### Уровень 4 — Очередь модерации
Контент, который не попадает под автоблок, но требует проверки, уходит в очередь.
Модератор через API: смотрит → одобряет или блокирует + добавляет хэш в блэклист.

### Уровень 5 — Система жалоб (улучшенная)
- Существующая таблица `Wo_Reports` расширяется полями контента
- N жалоб за M минут на медиафайл → автоматически уходит в очередь модерации
- Жалобы на текст (токсичность) — Detoxify (Уровень 2 развития, не в этом спринте)

---

## 2. Что НЕ будет сделано сейчас (позже)

| Компонент | Почему не сейчас |
|-----------|-----------------|
| Анализ видео (покадровый) | Дорого по CPU, требует очереди задач (Bull/Redis) |
| PhotoDNA | Нужна верификация от Microsoft CAID |
| Detoxify (анализ текста) | Требует GPU или отдельного сервера |
| Панель веб-интерфейса модератора | Фронтенд, отдельная задача |
| Верификация 18+ пользователей | Интеграция с платёжным сервисом/документами |

---

## 3. Архитектура системы

```
Загрузка файла (POST /api/node/chat/upload)
          |
          v
   [1] Magic Bytes Check (уже есть)
          |
       блок если EXE/PHP/etc
          |
          v
   [2] SHA-256 Hash → проверка wm_content_hash_blacklist
          |
       блок если совпадение с удалённым контентом
          |
          v
   [3] Только для изображений:
       HTTP запрос к NudeNet сервису (localhost:5001)
          |
          v
   [4] Decision Engine
       channel_type + nudenet_result → action
          |
     ┌────┴─────┬──────────┬────────────┐
     v          v          v            v
   ALLOW       BLUR      HOLD         BLOCK
 (сохранить) (сохр +   (очередь)  (отклонить,
             is_sens.)             не сохр.)
          |
          v
   [5] Если HOLD → запись в wm_moderation_queue
          |
          v
   [6] Ответ клиенту
```

---

## 4. Новые таблицы БД

### `wm_content_hash_blacklist`
Хранит хэши запрещённого контента.

```sql
id              INT AUTO_INCREMENT PRIMARY KEY
sha256_hash     CHAR(64) UNIQUE  -- точный хэш файла
phash           CHAR(64)         -- перцептивный хэш (для похожих изображений)
reason          VARCHAR(100)     -- 'nudity', 'violence', 'csam' и т.д.
added_by        INT              -- user_id модератора (0 = автоматически)
created_at      DATETIME
```

### `wm_moderation_queue`
Очередь контента, ожидающего ручной проверки.

```sql
id              INT AUTO_INCREMENT PRIMARY KEY
file_path       VARCHAR(500)     -- относительный путь к файлу
file_url        VARCHAR(500)     -- полный URL
media_type      VARCHAR(20)      -- image/video/audio
sender_id       INT              -- кто отправил (user_id)
channel_id      INT DEFAULT 0    -- канал (если из канала)
group_id        INT DEFAULT 0    -- группа (если из группы)
nudenet_labels  TEXT             -- JSON с результатами NudeNet
nudenet_score   FLOAT            -- максимальный score угрозы
status          ENUM('pending','approved','rejected') DEFAULT 'pending'
reviewed_by     INT DEFAULT 0    -- user_id модератора
reviewed_at     DATETIME
created_at      DATETIME
```

### `wm_content_policy`
Политика контента для каналов и групп.

```sql
id              INT AUTO_INCREMENT PRIMARY KEY
entity_type     ENUM('channel','group')
entity_id       INT
content_level   ENUM('all_ages','mature','adult_verified') DEFAULT 'all_ages'
updated_by      INT
updated_at      DATETIME
```

---

## 5. NudeNet сервис — настройка

### Расположение файлов
```
api-server-files/
└── moderation/
    ├── nudenet_service.py     # Flask HTTP сервис
    ├── requirements.txt       # Python зависимости
    ├── start.sh               # Скрипт запуска
    └── nudenet.service        # systemd unit файл
```

### Требования к серверу
- Python 3.8+
- ~500 MB RAM для модели NudeNet (загружается один раз при старте)
- CPU достаточно, GPU не нужен для изображений

### Установка (один раз на сервере)
```bash
cd /путь/к/api-server-files/moderation
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Запуск как systemd сервис
```bash
sudo cp nudenet.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable nudenet
sudo systemctl start nudenet
```

### API ключи / внешние зависимости
**NudeNet не требует API ключей** — это open-source библиотека, работает локально.
Модель (~80 MB) скачивается автоматически при первом запуске.

---

## 6. Переменные окружения (.env)

Добавить в `api-server-files/nodejs/.env`:

```env
# Модерация контента
NUDENET_SERVICE_URL=http://localhost:5001
NUDENET_ENABLED=true
NUDENET_TIMEOUT_MS=8000

# Порог срабатывания (0.0 - 1.0)
NUDENET_BLOCK_THRESHOLD=0.85
NUDENET_BLUR_THRESHOLD=0.60
NUDENET_QUEUE_THRESHOLD=0.45

# Жалобы — автоматическая очередь
REPORT_AUTO_QUEUE_COUNT=3
REPORT_AUTO_QUEUE_MINUTES=60
```

---

## 7. Что означают категории NudeNet

NudeNet возвращает список обнаруженных объектов с уверенностью 0–1:

| Метка NudeNet | Что это | Решение для PUBLIC |
|--------------|---------|-------------------|
| `EXPOSED_GENITALIA_*` | Обнажённые гениталии | BLOCK |
| `EXPOSED_BREAST_*` | Обнажённая грудь | BLOCK |
| `EXPOSED_BUTTOCKS` | Обнажённые ягодицы | BLOCK |
| `EXPOSED_BELLY` | Живот | ALLOW |
| `COVERED_*` | Всё в одежде | ALLOW |
| `FACE_*` | Лицо | ALLOW |

Для `news` канала: `EXPOSED_BREAST/BUTTOCKS` → BLUR (не блок).
Для `adult_verified` канала: всё разрешено кроме CSAM.

---

## 8. Маршруты API модерации

```
GET  /api/node/moderation/queue          — список очереди (только модераторы)
POST /api/node/moderation/approve        — одобрить контент { queue_id }
POST /api/node/moderation/reject         — отклонить контент { queue_id, reason }
GET  /api/node/moderation/blacklist      — список хэш-блэклиста
POST /api/node/moderation/blacklist/add  — добавить хэш вручную { sha256, reason }
```

**Авторизация:** только пользователи с `is_admin = 1` или специальной ролью.

---

## 9. Что реально даст система на старте

| Угроза | Покрытие |
|--------|---------|
| Порно/обнажёнка в PUBLIC (изображения) | ~85% автоблок |
| Повторная загрузка удалённых файлов | ~98% через SHA-256 + pHash |
| Явная обнажёнка в PRIVATE → очередь | 100% попадает к модератору |
| Видео | 0% авто (только жалобы → очередь) |
| Текст с токсичным контентом | 0% авто (позже) |
| Новые ранее невиданные изображения | ~80% NudeNet |

---

## 10. Ограничения первой версии

1. **Видео не анализируется** — только хэш-проверка (если модератор уже удалял этот файл). Видео попадает в очередь только по жалобам.
2. **Текст не анализируется** — только система жалоб.
3. **pHash только для изображений** — реализован через `sharp` (Node.js), требует установки `sharp`.
4. **NudeNet не определяет насилие/расчленёнку** — его специализация: обнажёнка/эротика. Насилие — только жалобы.
5. **Нет веб-панели** — только REST API. Фронтенд для модераторов — отдельная задача.
6. **content_level устанавливается только вручную** через API — автоопределения типа канала нет.
