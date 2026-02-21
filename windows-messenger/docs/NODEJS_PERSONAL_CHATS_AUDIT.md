# Аудит реализации Node.js desktop messenger (личные чаты)

Дата аудита: 2026-02-21

## 1) Что сейчас работает

На уровне кода и сборки:

- Проект устанавливает зависимости без ошибок (`npm install`).
- Production web-сборка проходит (`npm run build` / Vite build).
- Есть рабочий UI-флоу auth (login/register), секции chats/groups/channels/stories/calls.
- Для личных чатов реализованы:
  - загрузка списка чатов (`loadChats`)
  - загрузка истории сообщений (`loadMessages`)
  - отправка текстового сообщения (`sendMessage`)
  - отправка сообщения с медиа (`sendMessageWithMedia`)
  - подписка на socket-события `private_message`/`new_message`.
- Есть fallback на `api/windows_app/*` для login/chats/messages/send.
- Есть попытка шифрования/дешифрования AES-256-GCM на desktop стороне.

## 2) Что НЕ работает / проблемные зоны

Критично:

1. **TypeScript проект не проходит typecheck.**
   - Ошибка cleanup-функции в `useEffect` (возвращается `Socket`, а не `void`).
   - Ошибка типов `BufferSource` при дешифровании в `crypto.ts`.

2. **AES-256-GCM ключ несовместим между собеседниками.**
   - Ключ формируется как `${aesSeed}:${session.userId}:${selectedChatId}`.
   - У второго участника пары порядок userId/chatId будет обратный, следовательно шифр не расшифруется симметрично между 2 пользователями.
   - Фактически end-to-end для personal chat сейчас не гарантированно работает.

3. **WebRTC calls реализованы только на уровне offer.**
   - Нет полноценной обработки answer/ICE кандидатов и UI call state machine.
   - Для реальных звонков реализация неполная.

Средний приоритет:

4. **Нет валидации результата отправки сообщения перед optimistic update.**
   - Сообщение добавляется в UI сразу после `await sendMessage*`, но без проверки статуса ответа API payload.

5. **Socket-сообщения обрабатываются только для текущего выбранного чата.**
   - Сообщения для других диалогов не отражаются в preview/unread-механике.

6. **Нет поддержки editing/deleting/read receipts/typing в личных чатах.**
   - Это ограничивает parity с типичным production messenger UX.

## 3) Что нужно пофиксить и где

### Блок A (обязательно для стабильного релиза)

1. Исправить typecheck ошибки:
   - `src/App.tsx` — cleanup в `useEffect` с socket disconnect должен возвращать `void`.
   - `src/crypto.ts` — привести `fromBase64(...)` к совместимому `BufferSource` для `crypto.subtle.decrypt`.

2. Исправить схему derivation key для personal chats:
   - `src/App.tsx` + `src/crypto.ts`.
   - Нужен детерминированный общий ключ пары пользователей (например, сортированная пара id: `minId:maxId`).
   - Иначе зашифрованные сообщения не читаются вторым участником.

3. Добавить подтверждение API-статуса перед обновлением UI:
   - `src/api.ts` (возврат структурированного ответа `sendMessage*`).
   - `src/App.tsx` (`handleSendMessage`) — optimistic update только при `api_status === '200'`.

### Блок B (для полноты функционала личных чатов)

4. Реализовать unread/preview обновления для неактивных чатов:
   - `src/App.tsx` + `src/types.ts` (добавить поля unread/last message timestamp в локальную модель).

5. Добавить paging/history loading:
   - `src/api.ts` (`loadMessages` с `before_message_id`),
   - `src/App.tsx` (кнопка/scroll load more).

6. Реализовать call answer/ICE обработчики:
   - `src/socket.ts` (`onCallSignal` полноценный routing),
   - `src/webrtc.ts` + `src/App.tsx` (RTCPeerConnection lifecycle).

## 4) Оценка полноты именно по Node.js личным чатам

Оценка: **~60%** от ожидаемого production-ready функционала personal chats.

### Работает (ядро)
- Базовый login.
- Список чатов.
- Открытие диалога и загрузка истории.
- Отправка текста и медиа.
- Базовый realtime канал.

### Не хватает / работает неполно
- Надежное межпользовательское шифрование (ключ сейчас неустойчив для пары пользователей).
- Полноценная realtime модель (unread, preview, события по неактивным чатам).
- QoL/прод-функции: retry, delivery/read status, typing, edit/delete.
- Полноценные звонки (answer/ICE/UI states).
- Type-safe готовность к релизу (typecheck red).

## 5) Риск-резюме

- В текущем состоянии продукт можно считать **MVP/demo**, но не production-ready для безопасного и предсказуемого personal messaging.
- Главные blockers перед релизом: **typecheck + фикс ключа шифрования + подтвержденный send flow**.
