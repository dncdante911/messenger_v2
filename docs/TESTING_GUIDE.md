# WorldMates Messenger — Посібник з тестування

Охоплює всі 7 реалізованих завдань. Для кожного: що тестувати, як тестувати, на що звертати увагу.

---

## Задача №1 — OTP у Redis (замість in-memory Map)

### Що змінилось
`api-server-files/nodejs/routes/auth.js` — OTP більше не зберігається в пам'яті процесу Node.js.
Нові функції `storeOtp` / `checkOtp` з TTL 600 с у Redis (ключ `wm:otp:{type}:{contact}`).

### Як тестувати

**1. Базовий flow (правильний OTP)**
```bash
# Запити на відправку OTP (email або phone)
curl -X POST https://host/api/node/auth/send-otp \
  -d "type=email&contact=test@example.com"

# Перевірити ключ у Redis
redis-cli GET "wm:otp:email:test@example.com"
# Очікуємо: JSON {"code":"123456","attempts":0}

# Підтвердити OTP
curl -X POST https://host/api/node/auth/verify-otp \
  -d "type=email&contact=test@example.com&code=123456"
# Очікуємо: {"api_status":200}

# Після успішного verify ключ має зникнути
redis-cli GET "wm:otp:email:test@example.com"
# Очікуємо: (nil)
```

**2. Невірний OTP (increments attempts, KEEPTTL)**
```bash
# Послати неправильний код 3 рази
curl -X POST .../verify-otp -d "...&code=000000"
# {"api_status":400, "attemptsLeft":4}

# Перевірити лічильник
redis-cli GET "wm:otp:email:test@example.com"
# {"code":"123456","attempts":1}

# TTL має зберігатися (KEEPTTL)
redis-cli TTL "wm:otp:email:test@example.com"
# ~590 (не скидається)
```

**3. Блокування після 5 невдалих спроб**
```bash
# 5 неправильних кодів → ключ видаляється, відповідь: reason=locked
redis-cli GET "wm:otp:email:test@example.com"
# (nil) — заблоковано
```

**4. TTL expire (10 хвилин)**
```bash
redis-cli EXPIRE "wm:otp:email:test@example.com" 1
sleep 2
# Спроба verify → reason=expired
```

**5. Перезапуск Node.js — OTP зберігається**
```bash
# Відправити OTP
# Перезапустити Node.js: pm2 restart main
# Підтвердити OTP — має спрацювати (Redis, не in-memory)
```

### Логи для спостереження
```
[Auth/sendOtp] OTP stored in Redis for email:test@example.com (TTL 600s)
[Auth/verifyOtp] OTP verified for email:test@example.com
```

### На що звертати увагу
- Після перезапуску Node.js — OTP не губляться
- При масштабуванні (2 процеси Node.js) — OTP видимий обом процесам
- Не зберігається plaintext OTP в базі чи логах (`code` у Redis — тимчасово і шифрується через Redis AUTH)

---

## Задача №2 — WebRTC Адаптивний бітрейт

### Що змінилось
`app/.../network/WebRTCManager.kt` — додано корутину `startBitrateAdaptation()` що кожні 5 с опитує RTCStats (`remote-inbound-rtp`) і змінює якість відео:
- `fractionLost > 0.08` → знижує якість (DOWNGRADE)
- `fractionLost < 0.02` стабільно ≥ 15 с (3 ітерації) → підвищує (UPGRADE)

### Як тестувати

**1. Android ADB logcat**
```bash
adb logcat -s WebRTCManager:D BitrateAdapt:D
```
Фільтрувати теги `WebRTCManager`, `BitrateAdapt`. При дзвінку побачите:
```
D/WebRTCManager: [Adaptive] fractionLost=0.02 direction=STABLE
D/WebRTCManager: [Adaptive] fractionLost=0.15 direction=DOWNGRADE → LOW
D/WebRTCManager: [Adaptive] 3x GOOD → UPGRADE to MEDIUM
```

**2. Симуляція поганої мережі (Android Emulator)**
- Emulator → `...` → Network → Download speed: 1 Mbps, Packet loss: 15%
- Очікуємо: через 5 с якість впаде до LOW (240p)
- Прибрати втрати → через 15 с якість зросте

**3. UI індикатор якості**
Під час дзвінка відображається рядок:
- "Низька якість (240p)" / "Середня якість (480p)" / "Висока якість (720p)"
- "Адаптація…" при перемиканні

**4. Auto start/stop**
```
D/WebRTCManager: BitrateAdaptation started (state=CONNECTED)
D/WebRTCManager: BitrateAdaptation stopped (state=DISCONNECTED)
```
Адаптація запускається лише при активному дзвінку і зупиняється при роз'єднанні.

### На що звертати увагу
- `adaptiveJob?.cancel()` та `adaptiveScope.cancel()` при `close()` — витоку корутин не повинно бути
- Не повинно бути циклічних upgrade/downgrade ("flapping") при borderline packet loss ~8%
- Якість не піднімається вище FULL_HD і не падає нижче LOW

---

## Задача №3 — Пагінація списку чатів

### Що змінилось
`ChatsViewModel.kt` + `ChatsScreenModern.kt` — нескінченне прокручування з `PAGE_SIZE=50`.
При досягненні 3 елементів від кінця списку — автоматично завантажується наступна сторінка.

### Як тестувати

**1. ADB logcat**
```bash
adb logcat -s ChatsViewModel:D
```
```
D/ChatsViewModel: loadMoreChats() called (offset=0)
D/ChatsViewModel: Loaded 50 chats (offset now 50)
D/ChatsViewModel: loadMoreChats() called (offset=50)
D/ChatsViewModel: No more chats (server returned 30 < PAGE_SIZE)
```

**2. Перевірка дедуплікації**
Швидко прокрутіть до кінця і назад — не повинно бути дублікатів чатів.
```
D/ChatsViewModel: Deduped 5 chats (already in list)
```

**3. Spinner при завантаженні**
- Прокрутіть до кінця → з'являється "Завантаження чатів…" зі спіннером
- Після завантаження — "Усі чати завантажено" (коли сервер повернув < 50)

**4. Тест з реальними даними**
- Потрібно ≥ 50 чатів (можна через seed-скрипт або тест-акаунт)
- Прокрутіть до кінця → друга сторінка завантажується → прокрутіть ще → сторінка 3

**5. Перевірка граничних випадків**
- Менше 50 чатів загалом → `hasMoreChats=false` одразу, спіннер не з'являється
- Помилка мережі під час `loadMoreChats` → `isLoadingMore` скидається у `false` (блок `finally`)

---

## Задача №4 — Індикатор прогресу завантаження файлу

### Що змінилось
`MediaUploader.kt` — `ProgressRequestBody` тепер флашить sink після кожного 16 KB чанка.
`MessagesScreen.kt` — `AnimatedVisibility` з `LinearProgressIndicator` та текстом "Завантаження: N%".

### Як тестувати

**1. Завантаження великого файлу**
- Вибрати відеофайл > 5 MB
- Очікуємо плавний прогрес 0% → 99% → 100%
- Прогрес НЕ стрибає відразу до 100% при відправці (flush після кожного чанку)

**2. ADB logcat**
```bash
adb logcat -s MediaUploader:D
```
```
D/MediaUploader: Upload progress: 12% (1.2 MB / 10 MB)
D/MediaUploader: Upload progress: 45% (4.5 MB / 10 MB)
D/MediaUploader: Upload complete (100%)
```

**3. Плавність анімації**
- `AnimatedVisibility` — індикатор плавно з'являється на початку і зникає після завершення
- Перевірте що `visible = uploadProgress in 1..99` (не показується при 0 і 100)

**4. Поганий канал**
- Network Throttling у Chrome DevTools (якщо тест через PWA) або Android Emulator network throttling
- При slow 3G — прогрес має бути реальним (повільним, але точним)

### На що звертати увагу
- `lastReportedProgress` — progress reporting тільки при реальній зміні відсотка (не дублює події)
- `progress = { uploadProgress / 100f }` — lambda форма для Compose (уникає зайвих рекомпозицій)

---

## Задача №5 — E2EE Синхронізація ключів при зміні пристрою

### Що змінилось
- **Server** (`signal-store.js`): `saveKeyBundle` повертає `identityKeyChanged: true` коли identity key змінився
- **Server** (`signal.js`): якщо `identityKeyChanged` → `ctx.io.to('user_N').emit('signal:identity_changed', {user_id: N})`
- **Android** (`SignalKeyStore.kt`): `clearAllSessions()` та `clearForDeviceChange()`
- **Android** (`SocketManager.kt`): слухає `signal:identity_changed` → викликає `onSignalIdentityChanged(userId)`
- **Android** (`MessagesViewModel.kt`): `onSignalIdentityChanged(userId)` → `keyStore.deleteSession(userId)`

### Як тестувати

**1. Симуляція зміни пристрою (Android)**
```bash
# Стерти SignalKeyStore (EncryptedSharedPreferences)
adb shell pm clear com.worldmates.messenger

# АБО видалити тільки Signal prefs
adb shell run-as com.worldmates.messenger \
  rm /data/data/com.worldmates.messenger/shared_prefs/wm_signal_keys.xml
```
Потім увійти в той самий акаунт → `ensureRegistered()` генерує нові ключі → завантажує на сервер.

**2. Перевірка Socket.IO emit**
```bash
# У Redis Sub-client (listeners.js)
# Або через Socket.IO monitor (socket.io-admin-ui)
# Очікуємо подію: signal:identity_changed { user_id: 123 }
```

**3. ADB logcat на NEW device**
```bash
adb logcat -s SignalEncSvc:I SignalKeyStore:I
```
```
I/SignalKeyStore: Generated new identity key
I/SignalKeyStore: Signal state cleared for device change
I/SignalEncSvc: Signal keys registered (OPKs: 100)
```

**4. ADB logcat на OTHER device (контакт)**
```bash
adb logcat -s SocketManager:D MessagesViewModel:I
```
```
D/SocketManager: signal:identity_changed for user 123 — clearing DR session
I/MessagesViewModel: 🔑 [Signal] DR session cleared for user=123 (identity key changed on new device)
```

**5. Відправка повідомлення після зміни пристрою**
- OLD device: відправити повідомлення контакту, що змінив пристрій → нова X3DH автоматично
- NEW device: отримати перше повідомлення → успішно розшифровано (X3DH у заголовку)

**6. Перевірка Node.js логу**
```
[Signal] User 123 registered key bundle (OPKs: 100, identityKeyChanged: true)
[Signal] Emitted signal:identity_changed for user 123
```

### На що звертати увагу
- `ctx.io` встановлюється до першого HTTP-запиту (main.js рядок 471) — Signal routes реєструються раніше, але ctx.io доступний під час обробки запитів
- DR session видаляється негайно при отриманні Socket.IO події, ще до наступного повідомлення
- `clearForDeviceChange()` не викликається автоматично — тільки вручну або при logout

---

## Задача №6 — Offline черга повідомлень

### Що змінилось
- **Android** (`MessagesViewModel.kt`):
  - `sendMessage()` при `java.io.IOException` → `queueMessageLocally(text)` → Room DB з `isSynced=false`
  - `drainOfflineQueue()` — відправляє всі pending при `onSocketConnected()`
  - `onSocketDisconnected()` → показує `connection_lost` з strings.xml
- **Android** (`MessageBubbleComponents.kt`): `PendingMessageIcon` — годинник замість ✓✓ для `isLocalPending=true`
- **Android** strings: `message_queued_offline`, `message_pending_indicator`, `connection_lost`

### Як тестувати

**1. Симуляція offline**
```bash
# Android Emulator: Settings → Network → Flight mode ON
# АБО ADB:
adb shell svc wifi disable
adb shell svc data disable
```

**2. Відправка повідомлення offline**
- Написати повідомлення → відправити
- Очікуємо:
  - Повідомлення з'являється в чаті з іконкою 🕐 (годинник) замість ✓✓
  - Тост/снекбар: "Немає з'єднання — повідомлення буде надіслано при відновленні"
- Перевірити Room DB:
```bash
adb shell run-as com.worldmates.messenger \
  sqlite3 databases/worldmates_messenger.db \
  "SELECT id, decryptedText, isSynced FROM cached_messages WHERE isSynced=0;"
# Очікуємо: row з isSynced=0 та id < 0 (негативний локальний ID)
```

**3. Відновлення з'єднання**
```bash
adb shell svc wifi enable
```
- `drainOfflineQueue()` спрацьовує автоматично при `onSocketConnected()`
- ADB logcat:
```bash
adb logcat -s MessagesViewModel:I
```
```
I/MessagesViewModel: Draining 2 offline-queued messages
I/MessagesViewModel: Offline-queued message delivered (local=-1748235600000, server=98765)
```
- Іконка 🕐 змінюється на ✓✓
- Локальний запис видаляється з Room, з'являється серверна версія

**4. Перезапуск додатку offline**
- Відправити повідомлення offline → закрити додаток → відкрити знову
- `getUnsyncedMessages()` завантажує pending з Room → показує з 🕐
- При підключенні мережі → `drainOfflineQueue()` → доставка

**5. Помилка сервера (не network)**
```bash
# Зупинити Node.js (симуляція 500 error)
pm2 stop main

# Відправити повідомлення — НЕ повинно потрапити в queue (HTTP 5xx != IOException)
# Замість цього: "Помилка: ..."
```
> **Важливо**: offline queue спрацьовує ТІЛЬКИ при IOException/SocketTimeoutException, не при HTTP помилках.

### На що звертати увагу
- Негативні ID (локальні) не конфліктують з серверними (сервер завжди дає позитивні auto-increment)
- `drainOfflineQueue()` перебирає повідомлення по черзі (ORDER BY timestamp ASC) — зберігається порядок
- При повторній спробі відправки — pending запис у Room видаляється лише після успіху (apiStatus==200)

---

## Задача №7 — Груповий E2EE (Signal Sender Key Protocol)

### Що змінилось
- **Android** (`SocketManager.kt`): слухає `group:member_joined`, `group:member_left`
- **Android** (`MessagesViewModel.kt`):
  - `onGroupMemberJoined(groupId, userId)` → `signalGroupService.invalidateMyKey(groupId)` (force re-distribution на наступному send)
  - `onGroupMemberLeft(groupId, userId)` → видалити sender key члена + API `/api/node/signal/group/invalidate-sender-key` + invalidateMyKey
- Існуючий код `fetchAndApplyPendingDistributions` вже викликається в `fetchGroupMessages()`

### Як тестувати

**1. Перший вхід у групу (fetch distributions)**
```bash
adb logcat -s SignalGroupEncSvc:I
```
При відкритті групового чату:
```
I/SignalGroupEncSvc: [Group 42] Застосовано 3/3 distributions
```
Якщо відправити повідомлення і отримати — воно повинно розшифруватись.

**2. SenderKey distribution при першому повідомленні**
```
I/SignalGroupEncSvc: [Group 42] SenderKey розподілено між 4 учасниками
```
Після першого send — наступні не розподіляють (прапор `isDistributed=true`).

**3. Новий учасник приєднується**
```bash
# Backend emits: group:member_joined {group_id: 42, user_id: 99}
# АБО: через REST API (PHP) додати учасника → сервер emit
```
Logcat:
```
D/SocketManager: group:member_joined group=42 user=99
I/MessagesViewModel: 🔑 [Signal/Group] SenderKey invalidated for group=42 (new member userId=99)
```
При наступному send → повторний distribution (включаючи нового учасника).

**4. Учасник виходить з групи**
```bash
# Backend emits: group:member_left {group_id: 42, user_id: 77}
```
Logcat:
```
D/SocketManager: group:member_left group=42 user=77
I/MessagesViewModel: 🔑 [Signal/Group] SenderKey of user=77 removed for group=42 (member left)
```
API виклик: `POST /api/node/signal/group/invalidate-sender-key {group_id:42, sender_id:77}`

**5. Розшифровка повідомлення без SenderKey**
- Симуляція: вручну видалити локальний SenderKey відправника:
```bash
adb shell run-as com.worldmates.messenger \
  sqlite3 ...  # або через EncryptedSharedPreferences dump
```
- При отриманні наступного повідомлення від того ж відправника:
```
D/MessagesViewModel: 🔄 [Signal/Group] SenderKey від user=5 не знайдено, завантажуємо distributions...
I/SignalGroupEncSvc: [Group 42] Застосовано 1/1 distributions
```
Повідомлення розшифровується після автоматичного fetch.

**6. Перевірка сервера (pending distributions API)**
```bash
curl -H "access-token: TOKEN" \
  https://host/api/node/signal/group/pending-distributions?group_id=42
# {api_status:200, distributions:[...], count: N}
```

### На що звертати увагу
- `fetchAndApplyPendingDistributions` — async, обробляється в coroutine (не блокує UI)
- Після confirm delivery (API call) — distributions видаляються з черги (delivered=1)
- При виході учасника — MY sender key також інвалідується (наступний send = новий chain key без нього)

---

## Загальні інструменти налагодження

### Signal Protocol стан
```bash
# Показати Signal KeyStore (через adb + EncryptedSharedPreferences — неможливо читати напряму)
# Замість цього: дивитись лог при відкритті чату
adb logcat -s MessagesViewModel:I
```
```
I/MessagesViewModel: 🔑 [Signal] user=5 session=true registered=true opk_pool=87 ik=abc12345...
```

### Перевірка Redis OTP
```bash
redis-cli --scan --pattern "wm:otp:*" | xargs redis-cli TTL
```

### Socket.IO monitor
Додати тимчасово в `main.js`:
```js
io.on('connection', (socket) => {
  socket.onAny((event, ...args) => {
    console.log('[SocketMonitor]', event, JSON.stringify(args).slice(0, 200));
  });
});
```

### Room DB inspection
```bash
adb shell run-as com.worldmates.messenger \
  sqlite3 databases/worldmates_messenger.db ".tables"
# cached_messages, drafts, accounts

adb shell run-as com.worldmates.messenger \
  sqlite3 databases/worldmates_messenger.db \
  "SELECT id, chatId, isSynced, decryptedText FROM cached_messages LIMIT 10;"
```

### Network simulation (Android)
```bash
# Відключити мережу
adb shell svc wifi disable && adb shell svc data disable

# Включити
adb shell svc wifi enable

# Throttle (через tc, якщо root)
adb shell tc qdisc add dev wlan0 root netem loss 15% delay 200ms
adb shell tc qdisc del dev wlan0 root
```

---

*Документ складено 2026-03-13. Відповідає коміту після реалізації всіх 7 завдань.*
