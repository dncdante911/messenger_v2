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
| Каналы — посты | ✅ | ✅ iter-1 | Готово |
| Истории — список | ✅ | ✅ | OK |
| Истории — просмотрщик | ✅ | ✅ iter-1 | Готово |
| Поиск групп/каналов | ✅ | ✅ iter-2 | Готово |
| Поиск в чате | ✅ | ✅ iter-2 | Готово |
| Черновики | ✅ | ✅ iter-2 | Готово |
| Форматирование текста | ✅ | ✅ iter-2 | Готово |
| Загрузка медиа (сторисы/посты) | ✅ | ✅ iter-2 | Исправлено |
| Звонки — 1-на-1 | ✅ | ✅ 30% | Базово |
| Звонки — история | ✅ | ❌ | Нет |
| Звонки — группа | ✅ | ❌ | Нет |
| Голосовые сообщения | ✅ | ✅ iter-3 | Готово |
| Редактирование профиля | ✅ | ✅ iter-4 | Готово |
| Настройки приватности | ✅ | ⚠️ iter-4 | Частично |
| Блокировка пользователей | ✅ | ✅ iter-4 | Готово |
| Архив чатов (список) | ✅ | ✅ iter-4 | Готово |
| Стикеры / GIF | ✅ | ❌ | Нет |
| Боты | ✅ | ❌ | Нет |
| Безопасность (PIN/2FA) | ✅ | ❌ | Нет |
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

### Итерация 5 — Звонки и демонстрация экрана
**Статус:** 🕐 Запланировано

- История звонков (лог входящих/исходящих)
- Полноэкранный incoming call — отдельный оверлей, рингтон
- Демонстрация экрана (`getDisplayMedia`)
- Групповые звонки (mesh WebRTC)
- Кнопка «Позвонить» непосредственно из хедера личного чата

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
