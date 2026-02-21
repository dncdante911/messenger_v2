# Полный аудит Android + Node.js (личные чаты, API quality/workability)

Дата: 2026-02-21
Область: **Android приложение (`app/...`) + Node.js private chat API (`api-server-files/nodejs/...`)**.

---

## 0) Что именно проверялось

1. **Контракт Android ↔ Node.js**
   - `Constants.NODE_*` и `NodeApi.kt`
   - фактическая регистрация роутов в `routes/private-chats/index.js`
2. **Работоспособность Node.js сервера локально**
   - синтаксическая валидность JS
   - запуск `main.js`
   - целостность зависимостей
3. **Качество API эндпоинтов личных чатов**
   - валидация входных параметров
   - стабильность формата ответов
   - покрытие feature-set (get/send/edit/delete/react/pin/fav/archive/mute/read/search/loadmore)
4. **Крипто-совместимость Android ↔ Node.js**
   - формат полей `text + iv + tag + cipher_version`
   - серверное шифрование/дешифрование и клиентская расшифровка

---

## 1) Краткий итог

### Что работает (по коду и контрактам)

- Android имеет отдельный `NodeApi` для private chats и полный набор методов по личным чатам.
- Node.js регистрирует весь набор `/api/node/chat/*` роутов, который использует Android.
- Протокол auth единый: `access-token` в header/query/body, проверка через `wo_appssessions`.
- В Node.js реализованы базовые и расширенные операции личных чатов:
  - history / send / loadmore / edit / search / seen / typing
  - delete / react / pin / pinned / forward
  - chats list / archive / mute / pin-chat / color / read
  - fav / fav-list
- Крипто-поток между Node.js и Android реализован: сервер пишет GCM + iv/tag и Android умеет расшифровывать.

### Что не работает / критично

1. **Полная runtime-проверка внешних endpoint’ов недоступна из текущего окружения**:
   - доступ к `https://worldmates.club:449` блокировался (connect/proxy/DNS ограничения в среде).
2. **Node.js сервер в локальном запуске не поднимается из коробки**:
   - `node main.js` падает: `Cannot find module 'debug'` (битое дерево зависимостей).
3. **Android сборка в текущем репо невалидируема через wrapper**:
   - отсутствует `org.gradle.wrapper.GradleWrapperMain` (нет рабочего gradle wrapper jar), поэтому compile-check выполнить нельзя.

### Оценка зрелости Android + Node.js personal chats

- По архитектуре/функциям: **~75%**.
- По подтвержденной эксплуатационной готовности (в этом окружении): **~45-50%**, т.к. live endpoint smoke и app compile не прошли из-за инфраструктурных блокеров.

---

## 2) Endpoint coverage (Android vs Node.js)

Сверка показала: все `NODE_*` endpoint'ы Android совпадают с зарегистрированными роутами Node.js (`OK` по каждому path).

- `NODE_CHAT_GET` → `/api/node/chat/get` ✅
- `NODE_CHAT_SEND` → `/api/node/chat/send` ✅
- `NODE_CHAT_LOADMORE` → `/api/node/chat/loadmore` ✅
- `NODE_CHAT_EDIT` → `/api/node/chat/edit` ✅
- `NODE_CHAT_SEARCH` → `/api/node/chat/search` ✅
- `NODE_CHAT_SEEN` → `/api/node/chat/seen` ✅
- `NODE_CHAT_TYPING` → `/api/node/chat/typing` ✅
- `NODE_CHAT_DELETE` → `/api/node/chat/delete` ✅
- `NODE_CHAT_REACT` → `/api/node/chat/react` ✅
- `NODE_CHAT_PIN` → `/api/node/chat/pin` ✅
- `NODE_CHAT_PINNED` → `/api/node/chat/pinned` ✅
- `NODE_CHAT_FORWARD` → `/api/node/chat/forward` ✅
- `NODE_CHATS_LIST` → `/api/node/chat/chats` ✅
- `NODE_CHAT_DEL_CONV` → `/api/node/chat/delete-conversation` ✅
- `NODE_CHAT_ARCHIVE` → `/api/node/chat/archive` ✅
- `NODE_CHAT_MUTE` → `/api/node/chat/mute` ✅
- `NODE_CHAT_PIN_CHAT` → `/api/node/chat/pin-chat` ✅
- `NODE_CHAT_COLOR` → `/api/node/chat/color` ✅
- `NODE_CHAT_READ` → `/api/node/chat/read` ✅
- `NODE_CHAT_FAV` → `/api/node/chat/fav` ✅
- `NODE_CHAT_FAV_LIST` → `/api/node/chat/fav-list` ✅

Итог по coverage: **контракт путей полный, 21/21**.

---

## 3) Подробно: что работает и качество реализации

## 3.1 Auth / session

- Плюсы:
  - auth middleware унифицирован: header/query/body token.
  - возвращаются корректные HTTP-коды (`401`, `500`) и `api_status`.
- Риски:
  - отсутствует явная проверка срока жизни сессии/ротации токена на уровне middleware (только existence lookup).

## 3.2 Messages API

Реализовано хорошо:
- `get` с пагинацией (`before`, `after`, `message_id`), лимиты ограничены.
- `send` с валидацией контента (text/sticker/map/contact).
- `loadmore`, `edit`, `search`, `seen`, `typing` покрывают базовый UX.

Замечания по качеству:
- `search` использует `%LIKE%` по `text_preview` — функционально ок, но может стать bottleneck без индексов/FTS.
- event naming для seen/typing частично исторический (`lastseen`/`typing_done`) — надо проверить 1:1 соответствие всем Android слушателям.

## 3.3 Chat list/settings API

Реализовано:
- список диалогов с unread/last message/meta,
- archive/mute/pin/color/read/delete-conversation.

Замечания:
- `changeChatColor`: regex/формат допускают неоднозначность (обрезка до 7 hex-символов), лучше строгий `6` или `8` (ARGB) с явной схемой.
- `read` и `seen` функционально пересекаются; для API-клиента лучше оставить одну четкую семантику и вторую пометить legacy.

## 3.4 Favorites / reactions / forward

- Реализованы и в API, и в Android VM.
- Формат ответов в целом единый (`api_status`, payload, `error_message`).

Риски:
- forward делает N insert для списка получателей без batching/transaction; при частичном падении возможна частично успешная операция.

## 3.5 Encryption (Android ↔ Node.js)

Что хорошо:
- сервер хранит `text`(GCM), `iv`, `tag`, `cipher_version`.
- Android `MessagesViewModelNode` расшифровывает по `cipherVersion`, fallback для ECB учтен.

Ключевой риск:
- derivation от `timestamp` (не от устойчивого shared secret) снижает криптографическую стойкость модели и усложняет долгосрочную эволюцию E2E.
- это работает как «transport/storage obfuscation with integrity», но не современное E2E по стандартам мессенджеров.

---

## 4) Что конкретно НЕ работает в текущем состоянии (проверено)

1. **Remote endpoint smoke-test из среды не прошел**
   - попытки `curl -X POST https://worldmates.club:449/api/node/chat/...` возвращали сетевые ошибки (`000`, connect/proxy/DNS).
2. **Локальный старт Node.js не проходит**
   - `node main.js` → `Cannot find module 'debug'`.
   - `npm ls debug` показывает invalid dependency tree.
3. **Android compile-check недоступен**
   - `./gradlew :app:compileDebugKotlin` падает с `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`.

---

## 5) Что фиксить и где (приоритеты)

### P0 (блокеры)

1. **Исправить reproducible setup Node.js зависимостей**
   - Файлы: `api-server-files/nodejs/package.json`, lockfile strategy (`package-lock.json` vs `yarn.lock`), CI install script.
   - Цель: `npm ci`/`yarn install --frozen-lockfile` должны давать валидный dependency graph.

2. **Восстановить Gradle wrapper для Android**
   - Файлы: `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
   - Цель: возможность запускать compile/lint/test в CI.

3. **Сделать внешний smoke для `:449` из CI/monitoring**
   - Проверять `/api/health` и key private-chat endpoints на 401/200 сценариях.

### P1 (качество API)

4. **Унифицировать response contract**
   - Везде: одинаковые ключи ошибок/статусов и одинаковый тип `api_status`.

5. **Ужесточить валидации payload**
   - `color` строгий формат.
   - `forward` в транзакции + явный отчёт partial-success.

6. **Оптимизация поиска**
   - Индексация `text_preview`, ограничение длин запроса, optional FTS.

### P2 (безопасность/архитектура)

7. **Крипто-модель**
   - План миграции с timestamp-derived keys к нормальной схеме key management (если цель — реально защищенное E2E).

8. **Наблюдаемость и SLO**
   - Метрики latency/5xx по каждому `/api/node/chat/*`.
   - алерты по росту 401/500 и деградации p95.

---

## 6) Ответ на запрос "что работает / что нет / насколько полно"

### Работает
- Контракт Android↔Node endpoint paths закрыт полностью.
- Основная бизнес-логика личных чатов на Node.js реализована и в Android ViewModel интегрирована.
- Есть поддержка расширенных действий (edit/delete/react/pin/fav/forward/archive/mute/read/search/loadmore).

### Не работает (фактически подтверждено)
- Live-проверка endpoint’ов в этом окружении (сетевые ограничения до `:449`).
- Локальный старт Node.js из коробки (dependency issue с `debug`).
- Android compile validation через wrapper (сломанный/отсутствующий wrapper runtime).

### Насколько полно реализовано по Node.js personal chats
- **Функционально:** близко к full-featured private chats API (примерно **75%+**).
- **Операционно (доказуемо работоспособно прямо сейчас):** **~45-50%** из-за инфраструктурных блокеров проверки и запуска.

