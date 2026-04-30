# Windows Messenger — Progress Tracker

> Ветка разработки: `claude/audit-windows-android-comparison-OpZVF`

---

## Текущий статус

| Категория | Android | Windows | Прогресс |
|---|---|---|---|
| Личный чат (E2EE, media, reactions) | ✅ 100% | ✅ 70% | Хорошо |
| Группы — список | ✅ | ✅ | OK |
| Группы — чат | ✅ | ✅ iter-1 | Готово |
| Каналы — список | ✅ | ✅ | OK |
| Каналы — посты | ✅ | ✅ iter-5 | Готово |
| Каналы — комментарии | ✅ | ✅ iter-5 | Готово |
| Истории — список | ✅ | ✅ | OK |
| Истории — просмотрщик | ✅ | ✅ iter-1 | Готово |
| Поиск групп/каналов | ✅ | ✅ iter-2 | Готово |
| Поиск в чате | ✅ | ✅ iter-2 | Готово |
| Черновики | ✅ | ✅ iter-2 | Готово |
| Форматирование текста | ✅ | ✅ iter-2 | Готово |
| Загрузка медиа (сторисы/посты) | ✅ | ✅ iter-2 | Исправлено |
| Звонки — 1-на-1 | ✅ | ✅ iter-10 | Готово |
| Звонки — история | ✅ | ✅ iter-6 | Готово |
| Звонки — группа | ✅ | ✅ iter-10 | Готово |
| Голосовые сообщения | ✅ | ✅ iter-3 | Готово |
| Редактирование профиля | ✅ | ✅ iter-4 | Готово |
| Настройки приватности | ✅ | ✅ iter-6 | Готово |
| Блокировка пользователей | ✅ | ✅ iter-4 | Готово |
| Архив чатов (список) | ✅ | ✅ iter-4 | Готово |
| Стикеры / GIF | ✅ | ✅ iter-7 | Готово |
| Боты | ✅ | ✅ iter-7 | Готово |
| Безопасность (PIN/2FA) | ✅ | ✅ iter-10 | PIN готово, 2FA — серверная |
| Медиапросмотрщик (лайтбокс) | ✅ | ✅ iter-3 | Готово |
| Демонстрация экрана | ✅ | ❌ | Нет |

---

## Итерации

### Итерация 1 — Группы, Каналы, Просмотрщик историй
**Статус:** ✅ Завершено (сборка: `✓ built in 957ms`, TSC: 0 ошибок)

**Сделано:**
- **Group Chat** — клик на группу открывает чат в главной области; загрузка истории, отправка, редактирование, удаление, ответ, реакции, «загрузить ещё», socket-ивент `group_message`; имя отправителя в групповом пузыре
- **Channel Posts** — клик на канал показывает ленту постов; загрузка с пагинацией, публикация (текст + медиа), удаление, реакции (top-5 emoji), счётчики просмотров и комментариев, `post-view` при открытии
- **Story Viewer** — полноэкранный оверлей с прогресс-баром, авто-смена через 5 сек, навигация ‹/›, кнопка закрытия, кольцо «непросмотрено», `markStorySeen` API

**Файлы:**
- `src/api.ts` — `loadChannelPosts`, `loadMoreChannelPosts`, `createChannelPost`, `deleteChannelPost`, `reactToChannelPost`, `markChannelPostViewed`, `loadMoreGroupMessages`, `editGroupMessage`, `deleteGroupMessage`, `reactToGroupMessage`, `markGroupSeen`, `markStorySeen`, + нормализаторы групповых сообщений
- `src/types.ts` — `sender_name`, `group_id` в `MessageItem`; `ChannelPostsResponse`; `'archived'` в `ActiveSection`
- `src/App.tsx` — 10+ новых состояний, эффекты, обработчики, рендер групп/каналов/историй
- `src/styles.css` — `.group-sender-name`, `.channel-post*`, `.story-viewer*`, `.story-nav*`, `.story-unseen-ring`

---

### Итерация 2 — Черновики, Поиск, Форматирование, Фикс медиа
**Статус:** ✅ Завершено (сборка: `✓ built in 1.47s`, TSC: 0 ошибок)

**Сделано:**
- **Фикс медиа/сторисов** — `doRequest()` в `api.ts` теперь обходит IPC-прокси для `FormData` и напрямую использует `fetch`. Ранее FormData-тело молча отбрасывалось (IPC-прокси — только текст). Сторисы и посты с медиа теперь публикуются корректно
- **Поиск групп/каналов** — поля поиска в боковой панели: дебаунс 400 мс → серверный запрос (`searchGroups`, `searchChannels`), fallback — клиентская фильтрация при ошибке
- **Поиск в чате** — кнопка 🔍 в хедере чата → панель поиска с полем, крестиком закрытия, списком найденных сообщений; API `searchMessages`; дебаунс 400 мс
- **Черновики** — при смене чата текст в поле ввода автосохраняется в `localStorage` (`wm_draft_{userId}`); при открытии чата черновик восстанавливается
- **Форматирование текста** — парсер `renderText()` поддерживает `**bold**`, `_italic_`, `` `code` ``, `||spoiler||`; компонент `<Spoiler>` — клик раскрывает спойлер
- **i18n** — добавлены ключи `sidebar.searchGroups`, `sidebar.searchChannels`, `chat.search`, `chat.searchPlaceholder`, `chat.searchResults`, `chat.searchEmpty`, `chat.draft` для RU/UK/EN

**Файлы:**
- `src/api.ts` — `doRequest`: обход IPC для FormData; `searchGroups`, `searchChannels`
- `src/App.tsx` — `renderText`, `Spoiler`, `handleGroupSearch`, `handleChannelSearch`, `openChatSearch`, `closeChatSearch`, `handleChatSearchInput`, `handleComposerInputWithDraft`, draft-эффект; поиск в сайдбарах и панель поиска в чате
- `src/i18n.ts` — новые ключи + исправлена ошибка дублирующихся свойств в `ru` блоке
- `src/styles.css` — `.inline-code`, `.spoiler`, `.spoiler.revealed`, `.chat-search-panel`, `.chat-search-row`, `.chat-search-input`, `.chat-search-result`, `.chat-search-time`, `.chat-search-text`

---

### Итерация 3 — Медиа и голос
**Статус:** ✅ Завершено (сборка: `✓ built in 1.27s`, TSC: 0 ошибок)

**Сделано:**
- **Lightbox** — клик на картинку (в личном чате, группе, канале) открывает полноэкранный оверлей с кнопкой закрытия; клик по фону закрывает; ESC-эффект через onClick на overlay; картинка не масштабируется при клике по ней
- **Голосовые сообщения** — кнопка 🎤 в композере (видна только когда нет текста/файла); удержание начинает запись через `MediaRecorder`; отпускание завершает и отправляет файл (webm/ogg) через `sendVoiceMessage`; при записи кнопка пульсирует красным; voiceMessage рендерится через `<audio controls>`
- **Новый API** — `sendVoiceMessage(token, recipientId, File)` в `api.ts`: upload с `type:'voice'` + send-media с `media_type:'voice'`
- **i18n** — ключи `chat.voiceMessage` и `chat.stopRecording` в трёх локалях

**Файлы:**
- `src/api.ts` — `sendVoiceMessage`
- `src/App.tsx` — `lightboxSrc` state, lightbox overlay, `isRecordingVoice` / `mediaRecorderRef` / `voiceChunksRef`, `startVoiceRecording`, `stopVoiceRecording`, mic button UI, `onOpenMedia` prop для Bubble, замена `window.open` → lightbox во всех местах
- `src/i18n.ts` — 2 новых ключа × 3 локали
- `src/styles.css` — `.lightbox`, `.lightbox-close`, `.lightbox-img`, `.voice-btn`, `.voice-btn.recording`, `@keyframes pulse-rec`

---

### Итерация 4 — Профиль, Блокировка, Архив
**Статус:** ✅ Завершено (сборка: `✓ built in 1.01s`, TSC: 0 ошибок)

**Сделано:**
- **Редактирование профиля** — секция «Edit Profile» в Settings: поля имени, фамилии, username, bio; кнопка Save с состояниями saving/done/error; API `getMyProfile`, `updateMyProfile`; профиль загружается при первом открытии Settings
- **Аватар** — клик на аватар в Settings открывает file picker; загрузка через `uploadAvatar` → обновление превью; badge-кнопка 📷 поверх аватара
- **Блокировка** — кнопка 🚫 в хедере личного чата вызывает `blockUser`; в Settings раздел «Blocked users» со списком заблокированных и кнопкой Unblock у каждого; API `blockUser`, `unblockUser`, `loadBlockedUsers`
- **Архив чатов** — в сайдбаре чатов кнопка-разворот «📦 Archived» с счётчиком; при раскрытии грузится список через `loadArchivedChats`; у каждого архивного чата кнопка 📤 Unarchive (API `archiveChat`)
- **Новые API** — `getMyProfile`, `updateMyProfile`, `uploadAvatar`, `loadArchivedChats`, `blockUser`, `unblockUser`, `loadBlockedUsers`, `nodePut<T>`, `nodeDelete<T>`
- **i18n** — ключи `settings.editProfile`, `settings.firstName`, `settings.lastName`, `settings.username`, `settings.about`, `settings.saveProfile`, `settings.saving`, `settings.saved`, `settings.uploadAvatar`, `sidebar.archived`, `sidebar.noArchived`, `chat.unarchive`, `settings.blockedUsers`, `settings.noBlocked`, `settings.unblock`, `chat.block`, `chat.unblock` × 3 локали
- **CSS** — `.settings-input`, `.settings-textarea`, `.avatar-upload-label`, `.avatar-upload-badge`, `.settings-blocked-info`, `.archived-toggle`, `.archived-toggle-arrow`, `.archived-item`, `.btn-success`, `.btn-secondary`, `.btn-outline`

**Файлы:**
- `src/api.ts` — новые функции профиля/блокировки/архива
- `src/App.tsx` — `myProfile`, `profileFirst/Last/About/User`, `profileSaving`, `archivedChats`, `showArchived`, `archivedLoaded`, `blockedUsers`, `blockedLoaded`; обработчики `handleSaveProfile`, `handleAvatarUpload`, `handleToggleArchived`, `handleUnarchive`, `handleBlockUser`, `handleLoadBlocked`, `handleUnblock`; UI в Settings и в сайдбаре чатов
- `src/i18n.ts` — 16 новых ключей × 3 локали
- `src/styles.css` — 12 новых правил/классов

---

### Итерация 5 — Каналы: компактный UI и комментарии
**Статус:** ✅ Завершено (сборка: `✓ built in 924ms`, TSC: 0 ошибок)

**Сделано:**
- **Компактный UI каналов** — Telegram-подобный формат `.cp` карточек: аватар канала, имя, время, текст, галерея медиа (max-height 280px), футер со счётчиком просмотров + кнопкой комментариев + реакциями
- **Комментарии к постам** — клик на 💬 N открывает панель комментариев: список с аватарами пользователей, временем, текстом, кнопками удаления и реакций; форма отправки; ответ на комментарий (reply banner); кнопка «Назад»
- **Голосовые сообщения — исправление** — улучшен `isVoice` детектор: проверяет `media_type`, `media_filename`, URL-паттерн `VOICE_*`; имя файла при записи теперь начинается с `VOICE_` (uppercase) для совместимости с Android
- **Новые API** — `loadChannelComments`, `addChannelComment`, `deleteChannelComment`, `reactToChannelComment`
- **i18n** — ключи `channel.addComment`, `channel.comments` × 3 локали
- **CSS** — `.channel-feed`, `.cp`, `.cp-header`, `.cp-author`, `.cp-time`, `.cp-text`, `.cp-footer`, `.cp-stat`, `.cp-comment-btn`; `.comments-panel`, `.comment-item`, `.comment-meta`, `.comment-author`, `.comment-text`, `.comment-actions`

**Файлы:**
- `src/api.ts` — 4 новых функции комментариев
- `src/types.ts` — `ChannelComment` тип
- `src/App.tsx` — состояния `commentPost/comments/newComment/commentReplyTo`; хендлеры; компактный рендер постов и панель комментариев
- `src/i18n.ts` — 2 новых ключа × 3 локали
- `src/styles.css` — ~25 новых правил

---

### Итерация 6 — История звонков и Настройки приватности
**Статус:** ✅ Завершено (сборка: `✓ built in 1.66s`, TSC: 0 ошибок)

**Сделано:**
- **История звонков** — в разделе «Звонки» полный лог входящих/исходящих/пропущенных; фильтры по типу (All/Missed/Incoming/Outgoing); иконки направления; длительность; кнопка удаления записи; кнопка «Очистить всё»; API: `loadCallHistory`, `deleteCallRecord`, `clearCallHistory` (PHP `/api/v2/call_history.php`)
- **Настройки приватности** — в разделе Settings новый блок «Приватность»: кто видит «Последнее посещение», кто может писать, кто может подписаться, подтверждение подписчиков; сохранение через `/api/v2/index.php?type=update-privacy-settings`; состояния saving/done/error
- **Новые типы** — `CallHistoryItem`, `PrivacySettings` в `types.ts`
- **phpPost helper** — вспомогательная функция для PHP v2 API (form-encoded, токен в query-параметре)
- **i18n** — 8 ключей для истории звонков + 14 ключей настроек приватности × 3 локали
- **CSS** — `.call-history-header/item/avatar/info/name/meta/delete`; `.privacy-row/label/select`

**Файлы:**
- `src/api.ts` — `phpPost`, `loadCallHistory`, `deleteCallRecord`, `clearCallHistory`, `loadPrivacySettings`, `updatePrivacySettings`
- `src/types.ts` — `CallHistoryItem`, `PrivacySettings`
- `src/App.tsx` — стейты, эффект, хендлеры, UI в Calls и Settings
- `src/i18n.ts` — 22 новых ключа × 3 локали
- `src/styles.css` — 16 новых правил

---

### Итерация 7 — Стикеры, GIF, Поиск ботов
**Статус:** ✅ Завершено (сборка: `✓ built in 1.43s`, TSC: 0 ошибок)

**Сделано:**
- **Стикер-пикер** — кнопка 🎭 в композере (видна когда нет текста); открывает панель `.sticker-gif-picker` над композером; вкладки пакетов (иконки); сетка стикеров 5×N; клик отправляет стикер через `POST /api/node/chat/send` (`media_type: 'sticker'`); рендер в пузыре: `.bubble-sticker` (140×140px)
- **GIF-пикер** — кнопка GIF рядом со стикер-кнопкой; вкладка GIF в той же панели; поиск с дебаунсом 400 мс; тренды при открытии; 2-колоночная сетка; отправка через `media_type: 'gif'`; рендер: `.bubble-gif` (280×200px) с lightbox; «Powered by GIPHY» footer
- **Поиск ботов** — кнопка-разворот «🤖 Боты» в сайдбаре чатов; поисковое поле с дебаунсом 400 мс; список ботов с аватаром, именем, описанием; кнопки «Написать» и «🌐 App» (если есть `web_app_url`); клик «Написать» открывает бота как чат
- **Новые API** — `loadStickerPacks`, `sendStickerMessage`, `sendGifMessage`, `loadTrendingGifs`, `searchGifs`, `searchBots`
- **Новые типы** — `Sticker`, `StickerPack`, `GifItem`, `BotItem` в `types.ts`
- **i18n** — 9 новых ключей × 3 локали
- **CSS** — `.sticker-gif-picker`, `.picker-tabs`, `.picker-close`, `.sticker-pack-tabs`, `.sticker-pack-tab`, `.sticker-grid`, `.sticker-item`, `.gif-grid`, `.gif-item`, `.giphy-footer`, `.bubble-sticker`, `.bubble-gif`

**Файлы:**
- `src/api.ts` — 7 новых функций (стикеры, GIF, боты)
- `src/types.ts` — 4 новых типа (уже были добавлены ранее)
- `src/App.tsx` — стейты, хендлеры, UI пикера, бот-поиск в сайдбаре, пузырьки sticker/gif
- `src/i18n.ts` — 9 новых ключей × 3 локали
- `src/styles.css` — 12 новых правил

---

## Зафиксированные проблемы

| # | Проблема | Приоритет | Статус |
|---|---|---|---|
| 1 | Группы — нет UI чата, только список | High | ✅ iter-1 |
| 2 | Каналы — нет UI постов, только список | High | ✅ iter-1 |
| 3 | Истории — нет просмотрщика | High | ✅ iter-1 |
| 4 | Сторисы/медиа не публикуются (IPC+FormData) | High | ✅ iter-2 |
| 5 | Поиск групп — отсутствует | Medium | ✅ iter-2 |
| 6 | Поиск каналов — отсутствует | Medium | ✅ iter-2 |
| 7 | Черновики теряются при смене чата | Medium | ✅ iter-2 |
| 8 | Поиск сообщений — API есть, UI нет | Medium | ✅ iter-2 |
| 9 | Форматирование текста отсутствует | Medium | ✅ iter-2 |
| 10 | Архив чатов — кнопка есть, список нет | Medium | ✅ iter-4 |
| 11 | Редактирование профиля недоступно | Medium | ✅ iter-4 |
| 12 | Голосовые сообщения не записываются | Medium | ✅ iter-3 |
| 13 | Картинки открываются в браузере, нет lightbox | Low | ✅ iter-3 |
| 14 | Настройки приватности отсутствуют | Medium | ⚠️ iter-4 частично |
| 15 | Блокировка пользователей — нет UI | Medium | ✅ iter-4 |
| 16 | Голосовые отображаются как видео на Android | High | ⚠️ частично: имя VOICE_, fallback video_src, normaliseMessage type-field fix; root cause — сервер может игнорировать media_type для WebM |
| 17 | Голосовые «Скачать файл» в Windows чате | High | ✅ bugfix: normaliseMessage теперь читает m.type как fallback |
| 18 | Каналы: только 1 медиа на пост | Medium | ✅ bugfix: gallery из media_items[] |
| 19 | Каналы: опросы не отображаются | Medium | ✅ bugfix: PollWidget с голосованием |
| 20 | Группы: call-JSON видно как текст | Medium | ✅ bugfix: CallBubble рендер |
| 21 | Настройки не листаются | High | ✅ bugfix: min-height:0 на .list-scroll |
| 22 | Сторисы — битые изображения (relative path) | High | ✅ bugfix: storyAbsUrl() в api.ts |
| 23 | GIF — горизонтальные полосы вместо сетки | High | ✅ bugfix: auto-fill minmax + aspect-ratio:1 |
| 24 | Эмодзи полностью отсутствуют | High | ✅ bugfix: emoji picker 😊 в композере, 7 категорий × ~80 эмодзи |
| 25 | Бот не открывает чат (NaN user_id) | High | ✅ bugfix: BotItem.bot_id_str + getBotLinkedUser() |

---

### Итерация 8 — Исправление багов (сторис, GIF, эмодзи, боты, прокрутка)
**Статус:** ✅ Завершено (сборка: `✓ built in 1.43s`, TSC: 0 ошибок)

**Исправлено:**
- **Скролл настроек** — `min-height: 0` добавлен к `.list-scroll`; теперь flex-потомок с `overflow-y: auto` правильно создаёт контекст прокрутки
- **Сторисы (битые пути)** — добавлена `storyAbsUrl()` в `api.ts`: если путь не начинается с `http`, к нему добавляется `https://worldmates.club`; применяется к `file` и `thumbnail` в `loadStories`
- **GIF-сетка** — исправлено с 2 широких колонок на `repeat(auto-fill, minmax(130px, 1fr))` + `aspect-ratio: 1` на `.gif-item`; GIF теперь отображаются как квадратные миниатюры
- **Эмодзи-пикер** — кнопка 😊 в composer-row; 7 категорий (😀 Смайлики, 👋 Жесты, 🐶 Животные, 🍕 Еда, ✈️ Путешествия, 💡 Объекты, ❤️ Символы) по ~80–100 эмодзи каждая; вставка по позиции курсора
- **Боты** — исправлен тип `BotItem`: `bot_id_str: string` + `user_id: number`; добавлена `getBotLinkedUser(token, botIdStr)` в api.ts; `openBotChat` теперь делает fallback-запрос к `/api/node/bots/:id` если `user_id === 0`

**Файлы:**
- `src/api.ts` — `storyAbsUrl()` + `getBotLinkedUser()` + исправлен `searchBots`
- `src/types.ts` — `BotItem.bot_id → bot_id_str: string, user_id: number`
- `src/App.tsx` — импорт `getBotLinkedUser`, состояние `showEmojiComposer/emojiCatIdx`, эмодзи-пикер UI, `handleInsertEmoji()`, исправлен `openBotChat`, исправлен `key={bot.bot_id_str}`
- `src/i18n.ts` — ключ `chat.emoji` × 3 локали
- `src/styles.css` — `min-height:0` для `.list-scroll`, GIF auto-fill, emoji picker CSS

---

### Итерация 9 — Редизайн настроек, плавающий GIF-пикер, починка ботов
**Статус:** ✅ Завершено (сборка: `✓ built in 1.44s`, TSC: 0 ошибок)

**Исправлено:**
- **Настройки (полный редизайн)** — перенесены из 300px сайдбара в главную область; двухколоночный layout: левая навигация (280px) + правая область контента (1fr); 5 разделов: Профиль, Приватность, Заблокированные, Язык, Безопасность; приватность загружается автоматически при открытии вкладки; показываются все 9 полей приватности (showlastseen, message_privacy, follow_privacy, confirm_followers, friend_privacy, post_privacy, show_activities_privacy, birth_privacy, visit_privacy)
- **GIF-пикер** — переведён с inline-элемента в `position: absolute; bottom: 100%; right: 0; width: 380px; height: 300px;` над кнопками composer; 3 колонки квадратных миниатюр; `display: block` на `.gif-item` для работы `aspect-ratio: 1`; отдельный `.gif-search-input`; стикер-пикер тоже использует тот же popup
- **Боты** — исправлен `getBotLinkedUser`: данные вложены в `resp.bot.linked_user_id`, а не `resp.linked_user_id`; кнопка «Написать» теперь работает

**Файлы:**
- `src/api.ts` — фикс `getBotLinkedUser` (читает `resp.bot.linked_user_id`)
- `src/App.tsx` — `settingsTab` state, useEffect авто-загрузки приватности/блока, настройки в `<main>`, стикер/GIF пикер внутри `.composer`
- `src/i18n.ts` — 7 новых ключей × 3 локали (friendPrivacy, postPrivacy, showActivities, birthPrivacy, visitPrivacy, loadingDots, gifTrending)
- `src/styles.css` — `.settings-main-view` + `.settings-nav-col` + `.settings-content-col` + доп. правила; `.sticker-gif-picker` как абсолютный popup; `.gif-item { display: block }`

---

### Итерация 10 — Звонки (WebRTC), Групповые звонки, PIN-блокировка
**Статус:** ✅ Завершено (сборка: `✓ built in 1.43s`, TSC: 0 ошибок)

**Сделано:**
- **1-на-1 звонки (полный WebRTC)** — исправлен протокол сигнализации: `call:initiate` → `call:incoming` → `call:accept` → `call:answer` + `ice:candidate` ↔ `ice:candidate` → `call:end/reject`. Полная реализация: создание `RTCPeerConnection`, захват локального потока (`createLocalAudioStream`/`createLocalVideoStream`), обработчики `ontrack` для удалённого потока, `onicecandidate` для обмена ICE, функции `startCall`, `acceptCall`, `rejectIncomingCall`, `endActiveCall`, таймер длительности
- **Видео-оверлей** — удалённое видео занимает весь экран (full-size), локальное видео — PiP-окошко 120×90px в правом нижнем углу; для аудиозвонков — аватар + таймер по центру
- **Управление звонком** — кнопки Mute (🎙/🔇), камера (📹/📷), завершить (📵); треки включаются/отключаются через `track.enabled`; состояние кнопок синхронизировано с `callMuted`/`callCamOff`
- **Групповые звонки (mesh WebRTC)** — mesh-топология: отдельный `RTCPeerConnection` на каждого участника; обработчики `onGroupCallOffer`, `onGroupCallAnswer`, `onGroupCallIceCandidate`, `onGroupCallParticipantLeft`, `onGroupCallEnded`; грид-сетка тайлов видео с `group-call-grid`; принятие/отклонение через `acceptGroupCall`/`endActiveCall`
- **Сигналы сокета** — обновлён `socket.ts`: добавлены типы `IncomingCallData`, `CallAnswerData`, `IceCandidateData`, `GroupCallIncomingData/OfferData/AnswerData`; 15+ новых `socket.on` обработчиков; emitters: `emitCallInitiate`, `emitCallAccept`, `emitCallEnd`, `emitCallReject`, `emitIceCandidate`, `emitGroupCallJoin`, `emitGroupCallEnd`; типы расширены в `types.ts` (`CallState` с `roomName`, фазы `group_incoming`/`group_connected`, `GroupCallPeer`)
- **PIN-блокировка** — полноэкранный оверлей с 4 точками индикации и паролем при запуске если PIN включён; SHA-256 хэш пароля в `localStorage['wm_pin_hash']`; функции `hashPin`, `checkPin`, `enablePin`, `disablePin`; раздел в настройках Безопасности: статус, кнопки Установить/Изменить/Отключить, форма с подтверждением; ошибки: «Неверный PIN», «PIN слишком короткий», «PIN-коды не совпадают»
- **Фикс z-index GIF/стикер** — `.composer { position: relative; z-index: 10 }` → пикер больше не перекрывается сообщениями
- **i18n** — 28 новых строк × 3 локали: звонковые кнопки (mute, cam, speaker, groupCall, groupIncoming) + PIN строки (lock, enter, wrong, tooShort, mismatch, unlock, newPin, confirmPin, oldPin) + настройки PIN (pinLock, pinHint, pinEnabled, pinDisabled, setPinCode, changePinCode, disablePinCode)

**Файлы:**
- `src/socket.ts` — 6 новых типов, 15+ новых `socket.on` событий, 10+ новых `emit` хелперов
- `src/types.ts` — `CallState` расширен (`roomName`, `group_incoming`, `group_connected`), добавлен `GroupCallPeer`
- `src/App.tsx` — `callStateRef`, `stopCallEverything`, `startCallTimer/stopCallTimer/stopLocalStream/formatCallDuration`, полный `startCall`/`acceptCall`/`rejectIncomingCall`/`endActiveCall`, `handleMute`/`handleCamToggle`, `acceptGroupCall`, PIN-функции (`hashPin/checkPin/enablePin/disablePin/handleUnlockPin/handleSetPin`), переработан оверлей вызова (video PiP, audio card, incoming, group grid), PIN overlay, PIN в настройках Безопасности
- `src/i18n.ts` — 28 новых ключей × 3 локали
- `src/styles.css` — `.call-overlay` (video/audio/incoming/group layouts), `.call-ctrl-btn`, `.group-call-grid/peer`, `.pin-overlay`, `.settings-pin-card/row/form`, `.call-btn-round`

---

### Итерация 11 — Фикс звонков (SDP/TURN), поиск людей, вступление в группы, подписка на каналы
**Статус:** ✅ Завершено (сборка: `✓ built in 1.69s`, TSC: 0 ошибок)

**Исправлено:**
- **SDP формат (критический баг)** — Android шлёт сырые SDP-строки (`"v=0\r\n…"`), а не JSON. Windows пытался `JSON.parse()` → `SyntaxError`. Добавлен `parseSdp(raw, type)`: если строка начинается с `{` — пробует JSON.parse, иначе — создаёт `{ type, sdp: raw }`. Затронуто 4 места: `acceptCall`, `onCallAnswer`, `onGroupCallOffer`, `onGroupCallAnswer`. При отправке тоже исправлено: `sdpOffer: offer.sdp ?? ''` (вместо `JSON.stringify(offer)`), `sdpAnswer: answer.sdp ?? ''`
- **TURN credentials (критический баг)** — `TURN_FALLBACK` содержал TURN-серверы без `username`/`credential` → `InvalidAccessError: Both username and credential are required`. Убраны TURN-записи из fallback — теперь только 2 STUN (Google). Настоящие TURN с HMAC-кредами приходят с сервера в `call:incoming` → `iceServers`
- **getIceServers с токеном** — добавлен параметр `token`; теперь запрос к `/api/ice-servers/` отправляется с заголовком `access-token`, и сервер возвращает правильно сформированные TURN-кред
- **Кнопки видеозвонка** — убран `setSection('calls')` из обработчиков кнопок 🎙/📹 в шапке чата; теперь звонок запускается прямо из чата без смены раздела

**Добавлено:**
- **Поиск пользователей** — второй search-box в сайдбаре чатов (👤 «Найти людей»); дебаунс 400 мс; минимум 2 символа; вызывает `GET /api/node/users/search?q=`; список результатов с аватаром, именем, @username и кнопкой «Написать»; клик открывает чат
- **Вступление в группы** — при поиске групп (`groupSearchResults !== null`) рядом с каждой незнакомой группой показывается кнопка «Вступить»; вызывает `POST /api/node/group/join`; после успеха перезагружает список групп
- **Подписка на каналы** — при поиске каналов (`channelSearchResults !== null`) рядом с каждым неподписанным каналом показывается кнопка «Подписаться»; вызывает `POST /api/node/channel/subscribe`; после успеха перезагружает список каналов

**Новые API (api.ts):**
- `searchUsers(token, query)` — `GET /api/node/users/search?q=`
- `joinGroup(token, groupId)` — `POST /api/node/group/join`
- `subscribeChannel(token, channelId)` — `POST /api/node/channel/subscribe`
- `type UserSearchResult` — `{ id, username, first_name?, last_name?, avatar? }`

**i18n — 5 новых ключей × 3 локали:**
- `sidebar.searchPeople`, `sidebar.noPeopleFound`, `sidebar.joinGroup`, `sidebar.requestJoin`, `sidebar.subscribeChannel`

**Файлы:**
- `src/api.ts` — TURN_FALLBACK (только STUN), `getIceServers` с токеном, `searchUsers`, `joinGroup`, `subscribeChannel`, `UserSearchResult`
- `src/App.tsx` — `parseSdp()`, все JSON.parse на SDP заменены, sdpOffer/sdpAnswer отправляются как raw, состояния `userSearchQuery/userSearchResults/userSearchTimer`, `handleUserSearch`, `handleJoinGroup`, `handleSubscribeChannel`, `openUserChat`, UI поиска людей, кнопки Join/Subscribe в группах/каналах
- `src/i18n.ts` — 5 новых ключей × 3 локали
