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
| `typing` / `typing_done` | Индикатор печати |
| `recording` | Индикатор записи голоса |
| `user_action` | Статус действия (listening, viewing...) |
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
- Секретные чаты — Signal Protocol (E2EE) — BouncyCastle
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
- Аудио-звонки (WebRTC — stream-webrtc-android)
- Видеозвонки (WebRTC)
- Групповые звонки
- Livestream (канальный)
- Запись звонков
- Передача звонка
- TURN-сервер (для NAT traversal)
- Виртуальные фоны (ML Kit Selfie Segmentation)
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

#### Бизнес-режим
- Бизнес-профиль, категория, рабочие часы
- Авто-ответы, быстрые ответы
- Бизнес-директория (поиск бизнесов по гео)
- Бизнес-входящие (inbox)

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
- Cloud backup (Google Drive, Dropbox, MEGA)
- Chat export (JSON)
- Темы оформления (Midnight Indigo и кастомные)
- Instant View (Article Reader)
- Несколько аккаунтов (AccountManager)

---

### 1.6 Хранение данных

| Слой | Android | iOS (план) |
|------|---------|-----------|
| Токен/сессия | EncryptedSharedPreferences | `expo-secure-store` |
| Черновики | Room DB (`DraftDao`) | SQLite через `expo-sqlite` |
| Кеш сообщений | Room DB (`MessageDao`) | SQLite через `expo-sqlite` |
| Очередь исходящих | Room DB (`OutgoingMessageDao`) | SQLite / Zustand persist |
| Настройки | DataStore Preferences | `@react-native-async-storage` |
| Медиа-кеш | OkHttp cache + custom | React Native cache |

---

### 1.7 Ключевые зависимости Android → iOS аналоги

| Android (Kotlin) | iOS (React Native) | Заметка |
|-----------------|-------------------|---------|
| Retrofit + OkHttp | `axios` | Уже в Windows-версии |
| socket.io-client:2.1.1 | `socket.io-client:^4.x` | Уже в Windows-версии |
| Coil (images) | `expo-image` или `react-native-fast-image` | |
| ExoPlayer / Media3 | `expo-av` или `react-native-video` | |
| Lottie | `lottie-react-native` | |
| Room DB | `expo-sqlite` | |
| DataStore | `@react-native-async-storage` | |
| EncryptedSharedPrefs | `expo-secure-store` | |
| Google Maps | `react-native-maps` | |
| WebRTC | `react-native-webrtc` | |
| CameraX | `expo-camera` | |
| BiometricPrompt | `expo-local-authentication` | |
| ZXing QR | `expo-barcode-scanner` или `expo-camera` | |
| FCM | `@react-native-firebase/messaging` | |
| ML Kit (selfie) | `react-native-vision-camera` + plugin | |
| BouncyCastle (Signal) | `@noble/curves` + `@noble/hashes` + `@noble/ciphers` (pure-JS, Hermes-compatible) | ✅ |
| Dropbox SDK | Dropbox REST API (как в MEGA) | |
| Google Drive SDK | `expo-auth-session` + Drive REST API | |

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
react-native-vision-camera (видеозапись сообщений)
```

---

## Часть 3. Roadmap переноса (по фазам)

### Фаза 0 — Проект и инфраструктура ✅ ЗАВЕРШЕНО

| # | Задача | Статус |
|---|--------|--------|
| 0.1 | Инициализация React Native + Expo bare + TypeScript | ✅ |
| 0.2 | Константы API (`src/constants/api.ts`) | ✅ |
| 0.3 | HTTP клиент Axios (`src/api/apiClient.ts`) — `access-token` header, proactive+reactive refresh | ✅ |
| 0.4 | Socket.IO сервис (`src/services/socketService.ts`) | ✅ |
| 0.5 | Secure storage сервис — токены + `tokenExpiresAt` + `saveFullSession()` | ✅ |
| 0.6 | Zustand auth store — Node.js auth + VerificationRequired state | ✅ |
| 0.7 | Root Navigator + Auth Navigator | ✅ |
| 0.8 | Node.js API: `authApi.ts` — login/register/verify-code/send-code/reset-password/refresh | ✅ |
| 0.9 | Signal Protocol — `src/crypto/signal/` (X3DH + Double Ratchet + KeyStore + Service) | ✅ |

### Фаза 1 — Авторизация ✅ ЗАВЕРШЕНО (UI) / ✅ API ПОДКЛЮЧЁН

| # | Задача | Статус |
|---|--------|--------|
| 1.1 | SplashScreen (анимация) | ✅ |
| 1.2 | LanguageSelectionScreen | ✅ |
| 1.3 | LoginScreen | ✅ |
| 1.4 | RegisterScreen | ✅ |
| 1.5 | ForgotPasswordScreen | ✅ |
| 1.6 | VerificationScreen (email/SMS код) | ✅ |

### Фаза 2 — Главный экран и список чатов ✅ ЗАВЕРШЕНО

| # | Задача |
|---|--------|
| 2.1 | TabNavigator (Chats / Calls / Stories / Settings) |
| 2.2 | DrawerNavigator (боковое меню) |
| 2.3 | ChatsScreen + ChatListItem |
| 2.4 | Поиск по чатам |
| 2.5 | Папки чатов (Chat Folders) |
| 2.6 | Архив / скрытые чаты |
| 2.7 | Иконки историй над списком чатов |

### Фаза 3 — Дополнительные экраны ✅ ЗАВЕРШЕНО (Calls, Stories, Groups, ChannelDetails)

| # | Задача |
|---|--------|
| 3.1 | MessagesScreen — список сообщений (FlatList) |
| 3.2 | Bubble-компоненты (text, image, video, audio, voice, file, location) |
| 3.3 | Input bar (текст, медиа, стикеры, голос) |
| 3.4 | Отправка текста через Socket.IO |
| 3.5 | Получение сообщений в реальном времени |
| 3.6 | Реакции на сообщения |
| 3.7 | Reply (цитирование) |
| 3.8 | Edit / Delete сообщений |
| 3.9 | Forward сообщений |
| 3.10 | Пагинация (load more) |
| 3.11 | Прочитанность (seen / двойная галочка) |
| 3.12 | Индикатор печати / recording |

### Фаза 4 — Медиа

| # | Задача |
|---|--------|
| 4.1 | Отправка фото/видео (expo-image-picker) |
| 4.2 | Камера (expo-camera) |
| 4.3 | Запись голоса (expo-av) |
| 4.4 | Медиа-вьювер (полноэкранный просмотр) |
| 4.5 | Видео-плеер в чате |
| 4.6 | Аудио-плеер (голосовые) |
| 4.7 | GIF (Giphy API) |
| 4.8 | Стикеры + Lottie (.tgs) |
| 4.9 | Отправка файлов |
| 4.10 | Превью ссылок |

### Фаза 5 — Группы и каналы

| # | Задача |
|---|--------|
| 5.1 | GroupsScreen |
| 5.2 | GroupDetailsScreen |
| 5.3 | GroupMessagesScreen (переиспользовать MessagesScreen) |
| 5.4 | GroupAdminPanel |
| 5.5 | ChannelDetailsScreen |
| 5.6 | ChannelPostFeed |
| 5.7 | ChannelAdminPanel |

### Фаза 6 — Push уведомления

| # | Задача |
|---|--------|
| 6.1 | `expo-notifications` настройка |
| 6.2 | FCM интеграция (`@react-native-firebase/messaging`) |
| 6.3 | Фоновый Socket.IO (background task через `expo-task-manager`) |
| 6.4 | Уведомление входящего звонка (`expo-notifications` + CallKit) |
| 6.5 | Значок непрочитанных сообщений (badge) |

### Фаза 7 — Звонки (WebRTC)

| # | Задача |
|---|--------|
| 7.1 | `react-native-webrtc` настройка |
| 7.2 | CallsScreen (аудио/видео) |
| 7.3 | IncomingCallScreen |
| 7.4 | Socket.IO сигналинг (offer/answer/ice-candidates) |
| 7.5 | TURN-сервер интеграция |
| 7.6 | CallKit (iOS нативный UI для звонков) |
| 7.7 | Групповые звонки |

### Фаза 8 — Профиль и настройки

| # | Задача |
|---|--------|
| 8.1 | UserProfileScreen |
| 8.2 | SettingsScreen |
| 8.3 | EditProfileScreen |
| 8.4 | PrivacySettingsScreen |
| 8.5 | ThemeSettingsScreen |
| 8.6 | NotificationSettingsScreen |
| 8.7 | SecurityScreen (App Lock, биометрика) |
| 8.8 | MultiAvatarScreen |

### Фаза 9 — Дополнительные фичи P1

| # | Задача |
|---|--------|
| 9.1 | StoriesScreen + StoryViewerScreen |
| 9.2 | GlobalSearchScreen |
| 9.3 | SavedMessagesScreen |
| 9.4 | NotesScreen |
| 9.5 | DraftsScreen |
| 9.6 | ScheduledMessagesScreen |
| 9.7 | CallHistoryScreen |
| 9.8 | QR Scanner / Generator |
| 9.9 | Секретные чаты (Signal Protocol — X3DH + Double Ratchet) | ✅ |
| 9.10 | Instant View |

### Фаза 10 — P2 фичи

| # | Задача |
|---|--------|
| 10.1 | BotsScreen + BotStore |
| 10.2 | MiniAppWebView |
| 10.3 | BusinessMode |
| 10.4 | GeoDiscovery |
| 10.5 | StarsActivity / Premium |
| 10.6 | Cloud Backup |
| 10.7 | Livestream |

---

## Часть 4. Структура iOS проекта

```
ios-messenger/
├── app.json                    # Expo конфиг (bundleId, permissions...)
├── package.json
├── tsconfig.json
├── babel.config.js
├── App.tsx                     # Точка входа
│
└── src/
    ├── constants/
    │   ├── api.ts              # Все URL, события (из Constants.kt)
    │   └── config.ts           # APP_VERSION, PAGE_SIZE, etc.
    │
    ├── api/
    │   ├── apiClient.ts        # Axios instance + interceptors
    │   ├── authApi.ts          # Login, register, refresh
    │   ├── chatApi.ts          # Приватные чаты
    │   ├── groupApi.ts         # Группы
    │   ├── channelApi.ts       # Каналы
    │   ├── profileApi.ts       # Профиль
    │   ├── searchApi.ts        # Поиск
    │   └── types.ts            # Общие TypeScript типы
    │
    ├── services/
    │   ├── socketService.ts    # Socket.IO singleton (из SocketManager.kt)
    │   ├── storageService.ts   # SecureStore + AsyncStorage
    │   ├── notificationService.ts  # Push уведомления
    │   └── mediaService.ts     # Загрузка/скачивание медиа
    │
    ├── store/
    │   ├── authStore.ts        # Zustand: токен, юзер, loggedIn
    │   ├── chatStore.ts        # Zustand: чаты, сообщения
    │   ├── uiStore.ts          # Zustand: тема, язык
    │   └── callStore.ts        # Zustand: звонки
    │
    ├── navigation/
    │   ├── AppNavigator.tsx    # Root — Auth или Main
    │   ├── AuthNavigator.tsx   # Stack: Splash → Lang → Login...
    │   ├── MainNavigator.tsx   # Tab + Drawer
    │   └── types.ts            # NavigationProp types
    │
    ├── screens/
    │   ├── auth/
    │   │   ├── SplashScreen.tsx
    │   │   ├── LanguageSelectionScreen.tsx
    │   │   ├── LoginScreen.tsx
    │   │   ├── RegisterScreen.tsx
    │   │   ├── ForgotPasswordScreen.tsx
    │   │   └── VerificationScreen.tsx
    │   ├── chats/
    │   │   ├── ChatsScreen.tsx
    │   │   └── ChatItem.tsx
    │   ├── messages/
    │   │   ├── MessagesScreen.tsx
    │   │   ├── MessageBubble.tsx
    │   │   └── MessageInput.tsx
    │   ├── groups/
    │   ├── channels/
    │   ├── calls/
    │   ├── profile/
    │   ├── settings/
    │   ├── stories/
    │   ├── search/
    │   └── notes/
    │
    ├── components/
    │   ├── common/
    │   │   ├── Avatar.tsx
    │   │   ├── Badge.tsx
    │   │   ├── LoadingSpinner.tsx
    │   │   └── ErrorBoundary.tsx
    │   ├── chat/
    │   │   ├── TypingIndicator.tsx
    │   │   └── OnlineStatus.tsx
    │   └── calls/
    │       └── CallControls.tsx
    │
    ├── hooks/
    │   ├── useSocket.ts        # Подписка на Socket.IO события
    │   ├── useMessages.ts
    │   └── useChats.ts
    │
    └── utils/
        ├── dateUtils.ts
        ├── mediaUtils.ts
        └── cryptoUtils.ts
```

---

## Часть 5. Инструкция по сборке (для тех, кто не знает iOS/Mac)

### 5.1 Что нужно купить/скачать

| Что | Зачем | Стоимость |
|-----|-------|-----------|
| Mac (macOS 13+) | Xcode работает только на Mac | Имеющийся или б/у |
| Xcode 16+ | Сборка iOS-приложений | Бесплатно (App Store) |
| Apple Developer Account | Публикация в App Store | $99/год |
| iPhone или iPad (для тестирования) | Тест на реальном устройстве | Имеющееся |

> **Важно:** Без Mac собрать нативное iOS-приложение нельзя. Xcode — только macOS.
> Альтернатива для тестирования в процессе разработки: **Expo Go** на iPhone (не требует Mac).

---

### 5.2 Установка окружения на Mac

```bash
# 1. Установи Homebrew (менеджер пакетов для Mac)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 2. Установи Node.js (версия 20 LTS)
brew install node@20
echo 'export PATH="/opt/homebrew/opt/node@20/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 3. Установи Watchman (нужен React Native)
brew install watchman

# 4. Установи CocoaPods (пакетный менеджер для iOS)
sudo gem install cocoapods
# Или через brew:
brew install cocoapods

# 5. Установи rbenv (если Ruby конфликтует)
brew install rbenv
rbenv install 3.2.0
rbenv global 3.2.0

# 6. Установи Xcode из App Store (это займёт ~8 ГБ, долго)
# После установки открой Xcode и прими License Agreement

# 7. Установи Xcode Command Line Tools
xcode-select --install

# 8. Установи EAS CLI (Expo Application Services)
npm install -g eas-cli

# 9. Установи Expo CLI
npm install -g expo-cli

# Проверка:
node --version     # v20.x.x
npm --version      # 10.x.x
pod --version      # 1.x.x
xcodebuild -version  # Xcode 16.x
```

---

### 5.3 Первый запуск проекта

```bash
# Перейди в папку iOS-проекта
cd ios-messenger

# Установи JavaScript-зависимости
npm install

# Установи iOS-зависимости (CocoaPods)
cd ios && pod install && cd ..

# Запусти на симуляторе (iPhone 16)
npx expo run:ios

# Запусти на реальном iPhone (должен быть подключён по USB)
npx expo run:ios --device
```

---

### 5.4 Тестирование без Mac (Expo Go)

На этапе разработки (без нативных модулей) можно тестировать на iPhone через **Expo Go**:

```bash
# Установи Expo Go на iPhone из App Store

# Запусти dev-сервер
npx expo start

# На iPhone открой Expo Go → сканируй QR-код в терминале
```

> **Ограничение:** Expo Go не поддерживает `react-native-webrtc`, FCM push, CallKit.
> Эти фичи требуют сборки через Xcode или EAS Build.

---

### 5.5 Сборка через EAS Build (облако, без Mac)

EAS Build — сервис Expo, который собирает iOS-приложение в облаке (на серверах Apple Silicon):

```bash
# Войди в Expo аккаунт
eas login

# Настрой проект
eas build:configure

# Сборка для симулятора (бесплатно, для тестирования)
eas build --platform ios --profile preview

# Сборка для App Store (нужен Apple Developer Account)
eas build --platform ios --profile production

# Отправка в App Store
eas submit --platform ios
```

> **Плюс EAS Build:** Не нужен Mac для сборки — всё происходит в облаке.
> **Минус:** Небольшой Free tier, платно для частых сборок (~$15/мес).

---

### 5.6 Регистрация в App Store

1. Зарегистрируйся на [developer.apple.com](https://developer.apple.com) ($99/год)
2. Создай App ID в **Certificates, Identifiers & Profiles**
   - Bundle ID: `com.worldmates.messenger`
3. Создай приложение в **App Store Connect** ([appstoreconnect.apple.com](https://appstoreconnect.apple.com))
4. Заполни метаданные: название, описание, скриншоты, иконка
5. Загрузи сборку через EAS Submit или Xcode Organizer
6. Отправь на ревью Apple (обычно 1-3 дня)

---

### 5.7 Что нужно для подписи приложения

```
App Bundle ID:     com.worldmates.messenger
Team ID:           (из Apple Developer Account → Membership)
Provisioning Profile: Distribution (для App Store)
Certificate:       Apple Distribution Certificate
```

В `app.json` (уже настроено):
```json
{
  "ios": {
    "bundleIdentifier": "com.worldmates.messenger",
    "buildNumber": "1"
  }
}
```

---

### 5.8 Xcode — базовые действия

```
Открыть проект:  ios-messenger/ios/WorldMatesMessenger.xcworkspace
                              ↑ Именно .xcworkspace, НЕ .xcodeproj!

Запуск:          Кнопка ▶ (Play) в Xcode → выбери симулятор или устройство

Логи:            View → Debug Area → Activate Console (Cmd+Shift+Y)

Очистить:        Product → Clean Build Folder (Cmd+Shift+K)

Архив (Release): Product → Archive → затем Distribute App
```

---

### 5.9 Частые проблемы и решения

| Проблема | Решение |
|---------|---------|
| `pod install` завис | `sudo gem install cocoapods`, потом `pod repo update` |
| `Xcode: No matching provisioning profiles found` | Xcode → Preferences → Accounts → добавь Apple ID |
| Metro bundler не запускается | `npx expo start --clear` |
| `Build failed: duplicate symbols` | `cd ios && pod deintegrate && pod install` |
| Симулятор не видит устройство | Xcode → Window → Devices and Simulators |
| `FLIPPER_DISABLE=1` ошибки | Добавь `FLIPPER_DISABLE=1` в `.env` |
| Ошибка SSL сертификата | Убедись, что Node.js не старый: `node --version` |

---

## Часть 6. Конфигурация бэкенда для iOS

iOS строже Android в плане безопасности. Нужно убедиться, что:

### 6.1 ATS (App Transport Security)

iOS запрещает HTTP-запросы по умолчанию. Наш сервер уже HTTPS (`https://worldmates.club`), поэтому проблем нет. В `app.json` НЕ нужно добавлять `NSAllowsArbitraryLoads`.

### 6.2 Push-уведомления

Для FCM на iOS нужен `.p8` ключ от Apple:
1. developer.apple.com → Keys → создай ключ с APNs
2. Загрузи в Firebase Console → Project Settings → Cloud Messaging

### 6.3 Разрешения в `app.json`

```json
"infoPlist": {
  "NSCameraUsageDescription": "Для отправки фото и видео",
  "NSMicrophoneUsageDescription": "Для голосовых сообщений и звонков",
  "NSPhotoLibraryUsageDescription": "Для отправки медиафайлов",
  "NSLocationWhenInUseUsageDescription": "Для отправки геолокации",
  "NSLocationAlwaysUsageDescription": "Для живой геолокации",
  "NSFaceIDUsageDescription": "Для разблокировки приложения",
  "NSContactsUsageDescription": "Для поиска друзей по контактам"
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

*Создан: 2026-05-02 | Версия: 1.0*
