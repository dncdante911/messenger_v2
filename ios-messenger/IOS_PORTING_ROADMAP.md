# WorldMates Messenger — iOS Porting Roadmap

> Полный аудит Android-приложения и пошаговый план переноса на iOS.
> Технология: **React Native** (Expo bare workflow) + TypeScript

---

## ⚠️ ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА РАЗРАБОТКИ (нарушать запрещено)

### 🌐 Правило 1: НИКАКИХ хардкоженных строк

**Каждая** видимая пользователю строка ОБЯЗАНА находиться в файлах локализации.

```
ios-messenger/src/i18n/uk.ts   ← украинский (ЯЗЫК ПО УМОЛЧАНИЮ)
ios-messenger/src/i18n/ru.ts   ← русский
ios-messenger/src/i18n/en.ts   ← английский
```

**Правила:**
- Добавляешь новый текст? → сначала добавь ключ в **все три файла** (`uk.ts`, `ru.ts`, `en.ts`)
- В коде используй ТОЛЬКО `t('ключ')` — никаких `'Текст'` прямо в JSX
- Имена ключей — snake_case, на английском: `send_message`, `delete_chat`, `error_network`
- Один ключ = один смысл. Не переиспользуй ключи с похожим, но другим значением

**Пример (ПРАВИЛЬНО):**
```typescript
import { useTranslation } from '../../i18n';
const { t } = useTranslation();

// В JSX:
<Text>{t('send_message')}</Text>
<TouchableOpacity onPress={...}><Text>{t('cancel')}</Text></TouchableOpacity>
Alert.alert(t('error'), t('error_network'));
```

**Пример (ЗАПРЕЩЕНО):**
```typescript
// ❌ Никогда так:
<Text>Send Message</Text>
<Text>Відправити</Text>
Alert.alert('Error', 'Network error');
```

**Для класс-компонентов** (например, ErrorBoundary) — использовать `getTranslation(key)`:
```typescript
import { getTranslation } from '../../i18n';
const text = getTranslation('something_went_wrong'); // без хука
```

---

### 🎨 Правило 2: НИКАКИХ хардкоженных цветов

**Каждый** цвет ОБЯЗАН приходить из темы через `useTheme()`.

```
ios-messenger/src/theme/colors.ts  ← BaseColors + ThemePalettes (7 вариантов)
ios-messenger/src/theme/index.ts   ← useTheme() hook, buildTheme(), defaultTheme
```

**Правила:**
- Добавляешь цвет в JSX? → только через `theme.propertyName`
- Статический `StyleSheet.create` НЕ может содержать цвета из темы — используй **inline styles**
- Для класс-компонентов — `defaultTheme.propertyName` (импорт из `'../../theme'`)

**Цветовые токены темы Classic Blue (дефолт, как в Android):**

| Токен | Значение | Применение |
|-------|----------|------------|
| `theme.primary` | `#1565C0` | Кнопки, ссылки, акцент |
| `theme.background` | `#0D1B3E` | Фон экранов |
| `theme.surface` | `#1A2B4A` | Карточки, айтемы |
| `theme.surfaceElevated` | `#1E3250` | Приподнятые элементы |
| `theme.inputBackground` | `#1C2333` | Поле ввода |
| `theme.tabBar` | `#0D1B3E` | Нижняя навигация |
| `theme.text` | `#F0F2F5` | Основной текст |
| `theme.textSecondary` | `#B0BEC5` | Вторичный текст |
| `theme.textTertiary` | `#8E8E93` | Плейсхолдеры, метки |
| `theme.messageBubbleOwn` | `#1976D2` | Пузырь своего сообщения |
| `theme.messageBubbleOther` | `#1A2B4A` | Пузырь чужого сообщения |
| `theme.accent` | `#4FC3F7` | Ссылки, двойная галочка |
| `theme.divider` | `#2F3336` | Разделители, рамки |
| `theme.online` | `#00C851` | Статус онлайн |
| `theme.error` | `#FF4444` | Ошибки |
| `theme.success` | `#4CAF50` | Успех |

**Пример (ПРАВИЛЬНО):**
```typescript
import { useTheme } from '../../theme';
const theme = useTheme();

// В JSX:
<View style={{ backgroundColor: theme.background }}>
  <Text style={{ color: theme.text }}>...</Text>
  <ActivityIndicator color={theme.primary} />
</View>
```

**Пример (ЗАПРЕЩЕНО):**
```typescript
// ❌ Никогда так:
<View style={{ backgroundColor: '#1A1B2E' }}>
<Text style={{ color: '#7C83FD' }}>
const styles = StyleSheet.create({ root: { backgroundColor: '#0D1B3E' } });
```

---

### 📋 Чеклист перед каждым коммитом

Перед коммитом обязательно проверить:
- [ ] `grep -r "'[А-Яа-яіїєІЇЄ]" src/` → должно быть 0 результатов (нет украинского/русского текста в коде)
- [ ] `grep -r '"[A-Z][a-z]' src/screens/` → проверить нет ли английских фраз вместо `t()`
- [ ] `grep -r "#[0-9A-Fa-f]\{6\}" src/` → цвета только в `colors.ts` и для исключений типа `#FFFFFF`/`rgba(...)`
- [ ] Все новые ключи добавлены в `uk.ts`, `ru.ts` И `en.ts`
- [ ] Приложение корректно работает при смене языка (uk → ru → en)

---

## Часть 1. Полный аудит Android-приложения

### 1.1 Общая архитектура

```
Android App (Kotlin + Jetpack Compose)
    ↕ HTTP REST (Retrofit/OkHttp)          → Node.js :449 /api/node/*
    ↕ Socket.IO (socket.io-client:2.1.1)   → Node.js :449 (Socket.IO v4)
    ↕ HTTP REST (legacy, WoWonder PHP)     → PHP backend /api/v2/
```

**Два бэкенда:**
- **Node.js (основной)** — `https://worldmates.club:449/` — все мессенджерные функции
- **PHP (WoWonder)** — `https://worldmates.club/api/v2/` — пользователи, соцсеть, авторизация

---

### 1.2 Экраны (Activities / Screens)

| Экран | Файл Android | Приоритет |
|-------|-------------|-----------|
| Splash | `SplashActivity` | P0 |
| Выбор языка | `LanguageSelectionActivity` | P0 |
| Онбординг UI-стиля | `UIStyleOnboardingActivity` | P1 |
| Логин | `LoginActivity` | P0 |
| Быстрая регистрация | `QuickRegisterActivity` | P0 |
| Регистрация | `RegisterActivity` | P0 |
| Верификация email/SMS | `VerificationActivity` | P0 |
| Забыл пароль | `ForgotPasswordActivity` | P0 |
| Блокировка приложения (биометрика) | `AppLockActivity` | P1 |
| **Чаты (главный экран)** | `ChatsActivity` | P0 |
| **Сообщения (приватные)** | `MessagesActivity` | P0 |
| Запланированные сообщения | `ScheduledMessagesActivity` | P1 |
| Сохранённые сообщения | `SavedMessagesActivity` | P1 |
| Глобальный поиск | `GlobalSearchActivity` | P1 |
| Заметки | `NotesActivity` | P1 |
| Магазин ботов | `BotStoreActivity` | P2 |
| Mini App (WebView) | `MiniAppActivity` | P2 |
| Группы | `GroupsActivity` | P0 |
| Детали группы | `GroupDetailsActivity` | P0 |
| Панель админа группы | `GroupAdminPanelActivity` | P1 |
| QR-сканер | `QrScannerActivity` | P1 |
| Instant View | `InstantViewActivity` | P1 |
| Участники группы | `GroupMembersActivity` | P1 |
| Статистика группы | `GroupStatisticsActivity` | P2 |
| Логи админа группы | `GroupAdminLogsActivity` | P2 |
| Ответы на канале | `ChannelRepliesActivity` | P1 |
| Детали канала | `ChannelDetailsActivity` | P0 |
| Панель админа канала | `ChannelAdminPanelActivity` | P1 |
| Статистика канала | `ChannelStatisticsActivity` | P2 |
| Livestream канала | `ChannelLivestreamActivity` | P2 |
| Premium канала | `ChannelPremiumActivity` | P2 |
| Кастомизация канала | `ChannelAppearanceActivity` | P2 |
| Создание канала | `CreateChannelActivity` | P1 |
| Настройки | `SettingsActivity` | P1 |
| Настройки темы | `ThemeSettingsActivity` | P1 |
| Профиль пользователя | `UserProfileActivity` | P0 |
| История звонков | `CallHistoryActivity` | P1 |
| Активный звонок | `CallsActivity` | P1 |
| Входящий звонок | `IncomingCallActivity` | P1 |
| Входящий групповой звонок | `IncomingGroupCallActivity` | P2 |
| Черновики | `DraftsActivity` | P1 |
| Просмотр сторис | `StoryViewerActivity` | P1 |
| Подписка (Stars) | `StarsActivity` | P2 |
| Premium | `PremiumActivity` | P2 |
| Бизнес-режим | `BusinessActivity` | P2 |
| Бизнес-директория | `BusinessDirectoryActivity` | P2 |
| Профиль бизнеса | `BusinessProfileViewActivity` | P2 |
| Гео-открытие | `GeoDiscoveryActivity` | P2 |

---

### 1.3 Сетевой слой

#### HTTP REST — Node.js (основные эндпоинты)

| Группа | Примеры эндпоинтов |
|--------|-------------------|
| Авторизация | `api/node/auth/login`, `api/node/auth/register` (через PHP WoWonder) |
| Чаты | `api/node/chat/get`, `api/node/chat/send`, `api/node/chat/chats` |
| Медиа | `api/node/chat/send-media`, `api/node/media/upload` |
| Группы | `api/node/group/*` (create, messages, members, admin) |
| Каналы | `api/node/channel/*` (posts, comments, subscriptions) |
| Профиль | `api/node/users/me`, `api/node/users/{id}` |
| Поиск | `api/node/search/global` |
| Истории | через Socket.IO `stories-listener` |
| Scheduled | `api/node/scheduled/*` |
| Заметки | `api/node/notes` |
| Папки чатов | `api/node/folders` |

#### Socket.IO события (клиент → сервер)

| Событие | Назначение |
|---------|-----------|
| `join` | Авторизация сокета (передать токен) |
| `private_message` | Отправить/получить личное сообщение |
| `group_message` | Сообщение в группу |
| `channel_message` | Сообщение в канал |
| `typing` / `typing_done` | Индикатор печати (авто-сброс 6 сек) |
| `recording` | Индикатор записи голоса (авто-сброс 8 сек) |
| `user_action` | Статус действия (recording, viewing...) |
| `is_chat_on` / `close_chat` | Открыт/закрыт чат |
| `ping_for_lastseen` | Онлайн-статус |
| `lastseen` | Прочитано сообщение |
| `live_location_start/update/stop` | Живая геолокация |
| `on_user_loggedin/loggedoff` | Онлайн/оффлайн пользователь |
| `message_reaction` | Реакция на сообщение |
| `message_pinned` | Закреплено сообщение |

---

### 1.4 Типы сообщений

```
text | image | video | audio | voice | file | call | location | system
```

---

### 1.5 Фичи приложения (полный список)

#### Мессенджер (Core)
- Личные чаты — текст, медиа, файлы, голос, гифки, стикеры, эмодзи
- Групповые чаты — до N участников, роли admin/moderator/member
- Каналы — посты, реакции, комментарии, темы-треды
- Секретные чаты — Signal Protocol (E2EE) — X3DH + Double Ratchet
- Избранные сообщения / Сохранённые сообщения / Заметки
- Черновики (Room DB), Запланированные сообщения
- Форвард, Редактирование, Удаление сообщений
- Закреплённые сообщения
- Цитирование (reply to message)
- Папки чатов (custom filters)
- Архив и скрытие чатов

#### Медиа
- Отправка фото/видео/аудио/файлов (до 10 файлов за раз)
- Видеосжатие (порог 50MB, threshold-based)
- Запись голосовых сообщений
- Запись видеосообщений (CameraX)
- GIF через GIPHY API
- Стикеры (кастомные + Telegram `.tgs` Lottie)
- Превью ссылок (Jsoup парсинг)
- Instant View (статьи без браузера)
- Музыкальный плеер (ExoPlayer + Media3)
- Автоудаление медиа (Media Auto Delete)

#### Звонки
- Аудио-звонки (WebRTC)
- Видеозвонки (WebRTC)
- Групповые звонки
- Livestream (канальный)
- Запись звонков
- Передача звонка
- TURN-сервер (для NAT traversal)
- PiP (Picture-in-Picture) в звонках

#### Уведомления
- Socket.IO ForegroundService (основной канал)
- FCM (Firebase) — резервный канал при убитом сервисе
- BootReceiver + AlarmManager (перезапуск сервиса)
- Звонок на заблокированном экране (full-screen intent)

#### Профиль
- Мультиаватар (несколько фото профиля)
- Кастомный эмодзи-статус (PRO)
- Приватность (кто видит фото, статус и т.д.)
- Рейтинг пользователя
- Подписчики / Подписки (follow system)
- Блокировка пользователей
- 2FA (TOTP / QR для Google Authenticator)
- Биометрика / App Lock

#### Боты
- Bot API (Telegram-совместимый)
- Inline боты
- Mini Apps (WebView с JS Bridge)
- RSS-фиды для ботов
- Каталог ботов

#### Монетизация
- Stars (внутренняя валюта)
- Premium подписка
- Премиум кастомизация каналов
- Промо-коды

#### Другое
- Глобальный поиск (пользователи, группы, каналы, сообщения)
- QR-код (генерация + сканер)
- Гео-открытие (nearby users/businesses)
- Истории (Stories) с реакциями, опросами
- Cloud backup
- Chat export (JSON)
- Темы оформления (7 вариантов)
- Instant View (Article Reader)
- Несколько аккаунтов

---

### 1.6 Хранение данных

| Слой | Android | iOS (план) |
|------|---------|-----------|
| Токен/сессия | EncryptedSharedPreferences | `expo-secure-store` |
| Черновики | Room DB (`DraftDao`) | SQLite через `expo-sqlite` |
| Кеш сообщений | Room DB (`MessageDao`) | SQLite через `expo-sqlite` |
| Очередь исходящих | Room DB (`OutgoingMessageDao`) | Zustand offlineQueue + flush on reconnect |
| Настройки | DataStore Preferences | `@react-native-async-storage` |
| Медиа-кеш | OkHttp cache + custom | React Native cache |

---

### 1.7 Ключевые зависимости Android → iOS аналоги

| Android (Kotlin) | iOS (React Native) | Заметка |
|-----------------|-------------------|---------|
| Retrofit + OkHttp | `axios` | Уже в Windows-версии |
| socket.io-client:2.1.1 | `socket.io-client:^4.x` | Уже в Windows-версии |
| Coil (images) | `expo-image` | |
| ExoPlayer / Media3 | `expo-av` | |
| Lottie | `lottie-react-native` | |
| Room DB | `expo-sqlite` | |
| DataStore | `@react-native-async-storage` | |
| EncryptedSharedPrefs | `expo-secure-store` | |
| Google Maps | `react-native-maps` | |
| WebRTC | `react-native-webrtc` | |
| CameraX | `expo-camera` | |
| BiometricPrompt | `expo-local-authentication` | |
| ZXing QR | `expo-barcode-scanner` | |
| FCM | `@react-native-firebase/messaging` | |
| BouncyCastle (Signal) | `@noble/curves` + `@noble/hashes` + `@noble/ciphers` | ✅ готово |
| ForegroundService | `expo-task-manager` + AppState | ✅ готово |

---

## Часть 2. Стек технологий iOS

### Почему React Native, а не SwiftUI?

1. **Windows-версия уже на React/TypeScript** — можно переиспользовать API-слой, типы, socket-сервис
2. **`socket.io-client` одинаковый** для Windows и iOS (тот же пакет npm)
3. **Нет нужды учить Swift** — TypeScript хватит
4. **Expo** даёт готовые нативные модули для камеры, push, биометрики
5. **Быстрее time-to-market** — дизайн-система Material → RN StyleSheet / NativeWind

### Стек

```
React Native 0.76+  (New Architecture включена по умолчанию)
Expo SDK 52         (bare workflow — полный контроль)
TypeScript 5.x
React Navigation 7  (Stack + Tab + Drawer)
Zustand             (стейт-менеджмент, проще Redux)
Axios               (HTTP, как в Windows-версии)
socket.io-client    (тот же что в Windows!)
expo-sqlite         (локальная БД для чатов/черновиков)
expo-secure-store   (токены — зашифровано в Keychain)
@react-native-async-storage  (настройки)
expo-image          (изображения с кешем)
expo-av             (аудио/видео плеер)
expo-camera         (камера)
expo-notifications  (push + local notifications)
react-native-maps   (Google Maps / Apple Maps)
react-native-webrtc (WebRTC звонки)
lottie-react-native (Telegram стикеры .tgs)
expo-local-authentication (биометрика Face ID)
expo-barcode-scanner (QR код)
```

---

## Часть 3. Roadmap переноса (по фазам)

### Фаза 0 — Проект и инфраструктура ✅ ЗАВЕРШЕНО

| # | Задача | Статус | Файл |
|---|--------|--------|------|
| 0.1 | Инициализация React Native + Expo bare + TypeScript | ✅ | `package.json`, `app.json` |
| 0.2 | Константы API + все Socket.IO события | ✅ | `src/constants/api.ts` |
| 0.3 | HTTP клиент Axios — `access-token` header, proactive+reactive refresh | ✅ | `src/api/apiClient.ts` |
| 0.4 | Socket.IO сервис + reconnect backoff + onConnect() | ✅ | `src/services/socketService.ts` |
| 0.5 | Secure storage — токены + `tokenExpiresAt` + `saveFullSession()` | ✅ | `src/services/storageService.ts` |
| 0.6 | Zustand auth store — Node.js auth + VerificationRequired state | ✅ | `src/store/authStore.ts` |
| 0.7 | Root Navigator + Auth Navigator | ✅ | `src/navigation/` |
| 0.8 | Node.js API: login/register/verify/reset-password/refresh | ✅ | `src/api/authApi.ts` |
| 0.9 | Signal Protocol — X3DH + Double Ratchet + KeyStore + Service | ✅ | `src/crypto/signal/` |
| 0.10 | Background Socket Service (AppState + expo-background-fetch) | ✅ | `src/services/backgroundSocketService.ts` |
| 0.11 | Presence Service — Zustand store онлайн-статусов + lastSeen | ✅ | `src/services/presenceService.ts` |
| 0.12 | Placeholder assets (icon, splash, adaptive-icon, sounds) | ✅ | `assets/` |

### Фаза 1 — Авторизация ✅ ЗАВЕРШЕНО

| # | Задача | Статус |
|---|--------|--------|
| 1.1 | SplashScreen (анимация) | ✅ |
| 1.2 | LanguageSelectionScreen | ✅ |
| 1.3 | LoginScreen | ✅ |
| 1.4 | RegisterScreen | ✅ |
| 1.5 | ForgotPasswordScreen | ✅ |
| 1.6 | VerificationScreen (email/SMS код) | ✅ |

### Фаза 2 — Главный экран и список чатов ✅ ЗАВЕРШЕНО

| # | Задача | Статус |
|---|--------|--------|
| 2.1 | TabNavigator (Chats / Calls / Stories / Settings) | ✅ |
| 2.2 | DrawerNavigator (боковое меню) | ✅ |
| 2.3 | ChatsScreen + ChatListItem | ✅ |
| 2.4 | Поиск по чатам | ✅ |
| 2.5 | Папки чатов (Chat Folders) | ✅ |
| 2.6 | Архив / скрытые чаты | ✅ |
| 2.7 | Иконки историй над списком чатов | ✅ |

### Фаза 3 — Экран сообщений и Chat Store ✅ ЗАВЕРШЕНО

| # | Задача | Статус |
|---|--------|--------|
| 3.1 | MessagesScreen — список сообщений (FlatList) | ✅ |
| 3.2 | MessageBubble (text, image, video, audio, voice, file, location) | ✅ |
| 3.3 | MessageInput (текст, медиа, голос) | ✅ |
| 3.4 | TypingIndicator компонент | ✅ |
| 3.5 | chatStore — оптимистичная отправка + offline queue + flush при reconnect | ✅ |
| 3.6 | Typing auto-reset 6 сек (как в Android) | ✅ |
| 3.7 | Recording indicator + auto-clear 8 сек (USER_ACTION_RECORDING) | ✅ |
| 3.8 | Реакции на сообщения (MESSAGE_REACTION socket event) | ✅ |
| 3.9 | Закреплённые сообщения (MESSAGE_PINNED socket event) | ✅ |
| 3.10 | Групповые сообщения (GROUP_MESSAGE, GROUP_TYPING, GROUP_TYPING_DONE) | ✅ |
| 3.11 | Редактирование/удаление в группах (GROUP_MESSAGE_EDITED/DELETED) | ✅ |
| 3.12 | История очищена (GROUP_HISTORY_CLEARED, PRIVATE_HISTORY_CLEARED) | ✅ |
| 3.13 | Signal E2EE async decrypt при получении и загрузке истории | ✅ |
| 3.14 | Bulk seen receipts после loadMessages | ✅ |
| 3.15 | lastSeen через presenceService.setLastSeen | ✅ |
| 3.16 | presenceService wired: online/offline во всём chatStore | ✅ |
| 3.17 | sendGroupMessage в chatApi | ✅ |
| 3.18 | Пагинация (load more по скроллу вверх) | ✅ |

### Фаза 3B — Исправления и Android-parity для личных чатов ✅ ЗАВЕРШЕНО

| # | Задача | Статус | Файл |
|---|--------|--------|------|
| 3B.1 | Точка входа — `index.js` + `registerRootComponent` (вместо expo-router) | ✅ | `index.js`, `package.json` |
| 3B.2 | Авторизация — плоский ответ сервера (`body.access_token`, не `body.data.access_token`) | ✅ | `src/api/authApi.ts` |
| 3B.3 | Язык — единый ключ `wm_language`, немедленное обновление Zustand store | ✅ | `src/i18n/index.ts`, `LanguageSelectionScreen.tsx` |
| 3B.4 | Гидратация языка/темы до навигации в SplashScreen | ✅ | `SplashScreen.tsx` |
| 3B.5 | chatApi полный рерайт — POST вместо GET, snake_case имена полей (`recipient_id`, `before_message_id`) | ✅ | `src/api/chatApi.ts` |
| 3B.6 | `normaliseMessage()` — маппинг snake_case → camelCase (как Windows `normaliseMessage`) | ✅ | `src/api/chatApi.ts` |
| 3B.7 | `normaliseChat()` — маппинг списка чатов | ✅ | `src/api/chatApi.ts` |
| 3B.8 | Socket handler `PRIVATE_MESSAGE` — применяет `normaliseMessage()` к raw event data | ✅ | `MessagesScreen.tsx` |
| 3B.9 | Socket dedup — игнорировать повторное сообщение с тем же id | ✅ | `MessagesScreen.tsx` |
| 3B.10 | Корректный timestamp (ms vs seconds) — `formatTime()` + оптимистичный `Date.now()` | ✅ | `MessageBubble.tsx`, `MessagesScreen.tsx` |
| 3B.11 | Редактирование сообщений — `Alert.prompt` + `chatApi.editMessage()` + откат при ошибке | ✅ | `MessagesScreen.tsx` |
| 3B.12 | Удаление сообщений — подтверждение + `chatApi.deleteMessage()` + откат при ошибке | ✅ | `MessagesScreen.tsx` |
| 3B.13 | Копирование текста в буфер (`@react-native-clipboard/clipboard`) | ✅ | `MessageBubble.tsx` |
| 3B.14 | Константы `CIPHER_VERSION_AES=2`, `CIPHER_VERSION_SIGNAL=3` | ✅ | `src/constants/api.ts` |
| 3B.15 | Локализация ru/uk/en для новых ключей (delete_message_confirm, error_delete_message, error_edit_message) | ✅ | `i18n/ru.ts`, `uk.ts`, `en.ts` |

### Фаза 4 — Медиа

| # | Задача | Статус |
|---|--------|--------|
| 4.1 | Отправка фото/видео (expo-image-picker) | ⏳ |
| 4.2 | Камера (expo-camera) | ⏳ |
| 4.3 | Запись голоса (expo-av) | ⏳ |
| 4.4 | Медиа-вьювер (полноэкранный просмотр) | ⏳ |
| 4.5 | Видео-плеер в чате | ⏳ |
| 4.6 | Аудио-плеер (голосовые сообщения) | ⏳ |
| 4.7 | GIF (Giphy API) | ⏳ |
| 4.8 | Стикеры + Lottie (.tgs) | ⏳ |
| 4.9 | Отправка файлов | ⏳ |
| 4.10 | Превью ссылок | ⏳ |

### Фаза 5 — Группы и каналы

| # | Задача | Статус |
|---|--------|--------|
| 5.1 | GroupsScreen | ⏳ |
| 5.2 | GroupDetailsScreen | ⏳ |
| 5.3 | GroupMessagesScreen | ⏳ |
| 5.4 | GroupAdminPanel | ⏳ |
| 5.5 | ChannelDetailsScreen | ⏳ |
| 5.6 | ChannelPostFeed | ⏳ |
| 5.7 | ChannelAdminPanel | ⏳ |

### Фаза 6 — Push уведомления

| # | Задача | Статус |
|---|--------|--------|
| 6.1 | `expo-notifications` настройка | ⏳ |
| 6.2 | FCM интеграция (`@react-native-firebase/messaging`) | ⏳ |
| 6.3 | Фоновый Socket.IO (background task) | ✅ `backgroundSocketService.ts` |
| 6.4 | Уведомление входящего звонка (CallKit) | ⏳ |
| 6.5 | Значок непрочитанных сообщений (badge) | ⏳ |

### Фаза 7 — Звонки (WebRTC)

| # | Задача | Статус |
|---|--------|--------|
| 7.1 | `react-native-webrtc` настройка | ⏳ |
| 7.2 | CallsScreen (аудио/видео) | ⏳ |
| 7.3 | IncomingCallScreen | ⏳ |
| 7.4 | Socket.IO сигналинг (offer/answer/ice-candidates) | ⏳ |
| 7.5 | TURN-сервер интеграция | ⏳ |
| 7.6 | CallKit (iOS нативный UI для звонков) | ⏳ |
| 7.7 | Групповые звонки | ⏳ |

### Фаза 8 — Профиль и настройки

| # | Задача | Статус |
|---|--------|--------|
| 8.1 | UserProfileScreen | ⏳ |
| 8.2 | SettingsScreen | ⏳ |
| 8.3 | EditProfileScreen | ⏳ |
| 8.4 | PrivacySettingsScreen | ⏳ |
| 8.5 | ThemeSettingsScreen | ⏳ |
| 8.6 | NotificationSettingsScreen | ⏳ |
| 8.7 | SecurityScreen (App Lock, Face ID) | ⏳ |
| 8.8 | MultiAvatarScreen | ⏳ |

### Фаза 9 — Дополнительные фичи P1

| # | Задача | Статус |
|---|--------|--------|
| 9.1 | StoriesScreen + StoryViewerScreen | ⏳ |
| 9.2 | GlobalSearchScreen | ⏳ |
| 9.3 | SavedMessagesScreen | ⏳ |
| 9.4 | NotesScreen | ⏳ |
| 9.5 | DraftsScreen | ⏳ |
| 9.6 | ScheduledMessagesScreen | ⏳ |
| 9.7 | CallHistoryScreen | ⏳ |
| 9.8 | QR Scanner / Generator | ⏳ |
| 9.9 | Секретные чаты (Signal Protocol UI) | ⏳ |
| 9.10 | Instant View | ⏳ |

### Фаза 10 — P2 фичи

| # | Задача | Статус |
|---|--------|--------|
| 10.1 | BotsScreen + BotStore | ⏳ |
| 10.2 | MiniAppWebView | ⏳ |
| 10.3 | BusinessMode | ⏳ |
| 10.4 | GeoDiscovery | ⏳ |
| 10.5 | StarsActivity / Premium | ⏳ |
| 10.6 | Cloud Backup | ⏳ |
| 10.7 | Livestream | ⏳ |

---

## Часть 4. Структура iOS проекта

```
ios-messenger/
├── app.json                    # Expo конфиг (bundleId, permissions...)
├── package.json
├── tsconfig.json
├── babel.config.js
├── App.tsx                     # Точка входа
├── assets/                     # Иконки, сплэш, звуки
│   ├── icon.png                # 1024×1024 (teal #4ECDC4)
│   ├── splash.png              # 2048×2048 (navy #0D1B3E)
│   ├── adaptive-icon.png       # 1024×1024 (Android adaptive)
│   ├── notification-icon.png   # 96×96 (white mono)
│   ├── favicon.png             # 64×64
│   └── sounds/
│       ├── message.wav
│       └── call.wav
│
└── src/
    ├── constants/
    │   ├── api.ts              # Все URL, события (из Constants.kt)
    │   └── config.ts           # APP_VERSION, PAGE_SIZE, etc.
    │
    ├── api/
    │   ├── apiClient.ts        # Axios instance + interceptors
    │   ├── authApi.ts          # Login, register, refresh
    │   ├── chatApi.ts          # Приватные чаты + sendGroupMessage
    │   ├── groupApi.ts         # Группы
    │   ├── channelApi.ts       # Каналы
    │   ├── profileApi.ts       # Профиль
    │   ├── searchApi.ts        # Поиск
    │   └── types.ts            # Общие TypeScript типы
    │
    ├── services/
    │   ├── socketService.ts        # Socket.IO singleton + reconnect + onConnect()
    │   ├── storageService.ts       # SecureStore + AsyncStorage
    │   ├── backgroundSocketService.ts  # AppState listener + expo-background-fetch
    │   └── presenceService.ts      # Zustand store онлайн/оффлайн + lastSeen
    │
    ├── store/
    │   ├── authStore.ts        # Zustand: токен, юзер, loggedIn
    │   ├── chatStore.ts        # Zustand: полный стейт чатов (все фичи Android-parity)
    │   ├── uiStore.ts          # Zustand: тема, язык
    │   └── callStore.ts        # Zustand: звонки
    │
    ├── crypto/
    │   └── signal/
    │       ├── index.ts
    │       ├── signalEncryptionService.ts  # ensureRegistered, encryptForSend, decryptIncoming
    │       ├── doubleRatchetManager.ts     # X3DH + DR encrypt/decrypt
    │       ├── signalKeyStore.ts           # OPK pool, sessions, keys
    │       └── signalTypes.ts
    │
    ├── navigation/
    │   ├── AppNavigator.tsx    # Root — Auth или Main
    │   ├── AuthNavigator.tsx   # Stack: Splash → Lang → Login...
    │   ├── MainNavigator.tsx   # Tab + Drawer
    │   └── types.ts
    │
    ├── screens/
    │   ├── auth/               # SplashScreen, LanguageSelection, Login, Register...
    │   ├── chats/              # ChatsScreen, ChatListItem
    │   ├── messages/           # MessagesScreen, MessageBubble, MessageInput, TypingIndicator
    │   ├── groups/
    │   ├── channels/
    │   ├── calls/
    │   ├── profile/
    │   ├── settings/
    │   ├── stories/
    │   ├── search/
    │   └── notes/
    │
    ├── i18n/
    │   ├── uk.ts               # Украинский (по умолчанию)
    │   ├── ru.ts               # Русский
    │   ├── en.ts               # Английский
    │   └── index.ts            # useTranslation() + getTranslation()
    │
    ├── theme/
    │   ├── colors.ts           # BaseColors + ThemePalettes (7 тем)
    │   └── index.ts            # useTheme() + buildTheme() + defaultTheme
    │
    ├── components/
    │   ├── common/             # Avatar, Badge, LoadingSpinner, ErrorBoundary
    │   ├── chat/               # TypingIndicator, OnlineStatus
    │   └── calls/              # CallControls
    │
    ├── hooks/
    │   ├── useSocket.ts
    │   ├── useMessages.ts
    │   └── useChats.ts
    │
    └── utils/
        ├── dateUtils.ts
        ├── mediaUtils.ts
        └── cryptoUtils.ts
```

---

## Часть 5. Сборка приложения на iPhone 12 Pro

### 5.0 Ответ на главный вопрос

**Да, iPhone 12 Pro поддерживается полностью.**

- iOS 14.1+ (на iPhone 12 Pro из коробки), наш проект требует iOS 13+
- React Native 0.76 + Expo 52 поддерживают iOS 13 и выше
- Face ID на iPhone 12 Pro работает с `expo-local-authentication`

---

### 5.1 Вариант A — macOS 14 Sonoma (полная нативная сборка)

#### Шаг 1. Установи Xcode

```bash
# Откроем App Store → найди "Xcode" → установи (≈9 ГБ, займёт 20-40 мин)
# После установки ОБЯЗАТЕЛЬНО открой Xcode, прими License Agreement, дождись установки компонентов

# Установи Command Line Tools
xcode-select --install

# Проверь версию (нужен Xcode 15 или 16)
xcodebuild -version
# Xcode 16.x
# Build version 16xxx
```

#### Шаг 2. Установи Homebrew и Node.js

```bash
# Homebrew (если ещё нет)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# После установки Homebrew добавь в PATH (покажет сама установка, но обычно это):
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"

# Node.js 20 LTS
brew install node@20
echo 'export PATH="/opt/homebrew/opt/node@20/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Проверь
node --version   # v20.x.x
npm --version    # 10.x.x
```

#### Шаг 3. Установи Ruby + CocoaPods

> macOS 14 Sonoma имеет встроенный Ruby 2.6, но CocoaPods требует 3.x.
> Не используй системный Ruby — только через rbenv.

```bash
# Установи rbenv
brew install rbenv ruby-build

# Добавь rbenv в PATH
echo 'eval "$(rbenv init - zsh)"' >> ~/.zshrc
source ~/.zshrc

# Установи Ruby 3.2.x
rbenv install 3.2.4
rbenv global 3.2.4

# Проверь (должен показать /Users/тебя/.rbenv/...)
which ruby
ruby --version   # ruby 3.2.4

# Установи CocoaPods
gem install cocoapods

# Проверь
pod --version    # 1.15.x
```

#### Шаг 4. Установи Watchman

```bash
brew install watchman
```

#### Шаг 5. Клонируй репозиторий

```bash
git clone https://github.com/dncdante911/messenger_v2.git
cd messenger_v2
git checkout claude/port-messenger-ios-HGsXM
cd ios-messenger
```

#### Шаг 6. Установи JavaScript-зависимости

```bash
npm install
```

#### Шаг 7. Сгенерируй нативный iOS-проект

```bash
# Эта команда создаёт папку ios/ со всеми Xcode-файлами из app.json
npx expo prebuild --platform ios --clean
```

> После этого появится папка `ios/` с файлом `WorldmatesMessenger.xcworkspace`

#### Шаг 8. Установи CocoaPods зависимости

```bash
cd ios
pod install
cd ..
```

> Если ошибка `M1/M2 архитектура` — попробуй:
> ```bash
> arch -x86_64 pod install
> # или
> sudo arch -x86_64 gem install ffi && pod install
> ```

#### Шаг 9. Подключи iPhone 12 Pro к Mac по USB

```bash
# Запусти на реальном устройстве
npx expo run:ios --device
```

При первом запуске:
1. На iPhone появится запрос "Доверять этому компьютеру?" → **Доверять**
2. В Xcode может появиться ошибка подписи → смотри шаг 9б

#### Шаг 9б. Настройка подписи в Xcode (если ошибка provisioning)

```
1. Открой: ios/WorldmatesMessenger.xcworkspace (именно .xcworkspace!)
2. Слева выбери проект "WorldmatesMessenger" → вкладка "Signing & Capabilities"
3. Поставь галочку "Automatically manage signing"
4. Team → нажми "Add Account" → войди в Apple ID (бесплатный, не нужен Dev аккаунт)
5. Bundle Identifier → смени на уникальный: com.ТВОЕФАМИЛИЕ.worldmates
6. Нажми кнопку ▶ Play → выбери свой iPhone
```

> С **бесплатным Apple ID** можно устанавливать на свой iPhone, но приложение
> перестанет работать через 7 дней (нужно пересобирать). Для постоянной установки
> нужен **Apple Developer аккаунт ($99/год)**.

#### Шаг 10. Проверь, что работает

```bash
# Альтернативный запуск через Metro + Xcode
npx expo start
# В Xcode нажми ▶
```

---

### 5.2 Вариант B — Windows (через EAS Build в облаке)

**На Windows нельзя запустить Xcode.** Но можно собрать iOS-приложение через
**EAS Build** — облачный сервис Expo, который компилирует на серверах Apple.

#### Шаг 1. Установи Node.js на Windows

Скачай с [nodejs.org](https://nodejs.org) → версия 20 LTS → установщик .msi

```powershell
node --version   # v20.x.x
npm --version    # 10.x.x
```

#### Шаг 2. Установи Git и клонируй проект

```powershell
# Git скачать с git-scm.com
git clone https://github.com/dncdante911/messenger_v2.git
cd messenger_v2
git checkout claude/port-messenger-ios-HGsXM
cd ios-messenger
npm install
```

#### Шаг 3. Зарегистрируйся в Expo

```powershell
npm install -g eas-cli
eas login
# Введи email + пароль от expo.dev аккаунта
# Если нет аккаунта — зарегистрируйся на expo.dev (бесплатно)
```

#### Шаг 4. Привяжи проект к Expo

```powershell
eas init
# Выбери "Create a new project" или привяжи существующий
# Запишет projectId в app.json
```

#### Шаг 5. Создай конфиг сборки

Создай файл `eas.json` в папке `ios-messenger/`:

```json
{
  "cli": {
    "version": ">= 7.0.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "ios": {
        "simulator": false
      }
    },
    "preview": {
      "distribution": "internal",
      "ios": {
        "buildConfiguration": "Release"
      }
    },
    "production": {
      "ios": {
        "buildConfiguration": "Release"
      }
    }
  }
}
```

#### Шаг 6. Сборка для установки на iPhone (без App Store)

```powershell
# Сборка .ipa файла для прямой установки на устройство
eas build --platform ios --profile preview
```

Процесс:
1. EAS спросит про Apple ID → введи свой
2. Спросит создать Provisioning Profile → **Yes**
3. Загрузит код в облако (~5 мин)
4. Скомпилирует на серверах Apple (~15-25 мин)
5. Даст ссылку на скачивание `.ipa`

#### Шаг 7. Установи .ipa на iPhone

**Вариант 7A — через AltStore (бесплатно, без Developer аккаунта):**
1. Установи [AltServer](https://altstore.io) на Windows
2. Подключи iPhone по USB
3. В AltServer → Install AltStore → выбери свой iPhone
4. На iPhone открой AltStore → My Apps → + → выбери скачанный .ipa

> AltStore переподписывает приложение каждые 7 дней автоматически,
> пока iPhone подключён к ПК с AltServer.

**Вариант 7B — через Apple Developer аккаунт ($99/год):**
```powershell
# Прямая отправка через EAS
eas submit --platform ios
```
После этого приложение появится в TestFlight или App Store.

---

### 5.3 Сравнение способов сборки

| Способ | ОС | Стоимость | Срок действия .ipa | Сложность |
|--------|----|-----------|--------------------|-----------|
| Xcode + Mac, бесплатный Apple ID | macOS | $0 | 7 дней | Средняя |
| Xcode + Mac, Developer аккаунт | macOS | $99/год | 1 год | Низкая |
| EAS Build + AltStore | Windows / любая | $0 | 7 дней | Средняя |
| EAS Build + Developer аккаунт | Windows / любая | $99/год | 1 год | Низкая |
| EAS Build + App Store | Windows / любая | $99/год | Бессрочно | Высокая (ревью Apple) |

---

### 5.4 Частые проблемы и решения

| Проблема | Причина | Решение |
|---------|---------|---------|
| `pod install` падает с ошибкой ffi | M1/M2 архитектура + старый ffi | `sudo arch -x86_64 gem install ffi && pod install` |
| `No matching provisioning profiles` | Не настроена подпись | Xcode → Signing → Automatically manage signing |
| `Untrusted Developer` на iPhone | Первый запуск без Dev аккаунта | iPhone → Настройки → Основные → VPN и управление устройством → Доверять |
| Metro bundler не стартует | Зависший процесс | `npx expo start --clear` |
| `Build failed: duplicate symbols` | Конфликт CocoaPods | `cd ios && pod deintegrate && pod install` |
| Симулятор не видит iPhone | Xcode не видит устройство | Xcode → Window → Devices and Simulators → добавь устройство |
| EAS build fails: missing GoogleService-Info.plist | Firebase не настроен | Закомментируй firebase plugin в app.json до настройки Firebase |
| `react-native-webrtc` build error | Нужен Xcode 15+ | Обнови Xcode через App Store |

---

### 5.5 Что нужно перед первой сборкой (чеклист)

- [ ] Создан Apple ID (бесплатно на appleid.apple.com)
- [ ] Xcode 15 или 16 установлен и открыт хотя бы раз (принят License Agreement)
- [ ] `npm install` прошёл без ошибок
- [ ] `npx expo prebuild --platform ios` создал папку `ios/`
- [ ] `pod install` прошёл без ошибок
- [ ] В Xcode настроен Team (Apple ID) и Bundle ID уникальный
- [ ] iPhone доверяет компьютеру (диалог при подключении USB)
- [ ] Сервер бэкенда (`worldmates.club`) доступен

> **GoogleService-Info.plist** — нужен для Firebase/FCM (push-уведомления).
> Без него приложение запустится, но push работать не будут.
> Получить в Firebase Console → Project Settings → iOS app.

---

## Часть 6. Конфигурация бэкенда для iOS

iOS строже Android в плане безопасности.

### 6.1 ATS (App Transport Security)

iOS запрещает HTTP-запросы по умолчанию. Наш сервер уже HTTPS (`https://worldmates.club`),
поэтому проблем нет. В `app.json` НЕ нужно добавлять `NSAllowsArbitraryLoads`.

### 6.2 Push-уведомления

Для FCM на iOS нужен `.p8` ключ от Apple:
1. developer.apple.com → Keys → создай ключ с APNs
2. Загрузи в Firebase Console → Project Settings → Cloud Messaging

### 6.3 Разрешения (уже настроено в app.json)

```json
"infoPlist": {
  "NSCameraUsageDescription": "Для отправки фото и видео",
  "NSMicrophoneUsageDescription": "Для голосовых сообщений и звонков",
  "NSPhotoLibraryUsageDescription": "Для отправки медиафайлов",
  "NSLocationWhenInUseUsageDescription": "Для отправки геолокации",
  "NSFaceIDUsageDescription": "Для разблокировки приложения",
  "NSContactsUsageDescription": "Для поиска друзей по контактам",
  "UIBackgroundModes": ["fetch", "remote-notification", "voip", "audio"]
}
```

---

## Часть 7. Сравнение с Windows-версией

| Фича | Windows (Electron) | iOS (React Native) | Общий код |
|------|-------------------|-------------------|-----------|
| API константы | `src/config.ts` | `src/constants/api.ts` | ~80% |
| Socket.IO | `src/socket.ts` | `src/services/socketService.ts` | ~70% |
| HTTP клиент | `src/api.ts` | `src/api/apiClient.ts` | ~60% |
| Типы данных | `src/types.ts` | `src/api/types.ts` | ~90% |
| UI компоненты | React DOM | React Native | 0% (разный рендеринг) |
| Навигация | React Router | React Navigation | 0% |
| Уведомления | Electron IPC | expo-notifications | 0% |

> TypeScript типы и API-логику можно выделить в `shared/` пакет и использовать и в Windows, и в iOS.

---

*Создан: 2026-05-02 | Обновлён: 2026-05-05 | Версия: 2.1*
*Выполнено фаз: 0 (полностью), 1 (полностью), 2 (полностью), 3 (полностью), 3B (полностью), 6.3 (частично)*
