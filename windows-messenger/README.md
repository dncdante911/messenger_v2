# WorldMates Windows Messenger

Desktop клиент под **Windows 10/11** (Electron + React + TypeScript), совместимый с backend API Android приложения.

## Реализовано в текущем этапе

- Экран входа + регистрация
  - логин по username/password
  - логин по phone/password
  - регистрация пользователя
- Чаты
  - список чатов
  - история сообщений
  - отправка сообщений
  - AES-256-GCM шифрование/дешифрование для текстовых сообщений (desktop-side)
- Медиа
  - отправка медиа в сообщении (image/video/docs)
  - отображение ссылки на download медиа в ленте сообщения
- Группы
  - загрузка списка групп
  - создание группы
- Каналы
  - загрузка списка каналов
  - создание канала
- Stories
  - загрузка списка stories
  - upload story (image/video)
- Звонки (база)
  - WebRTC desktop integration (offer flow)
  - ICE servers из backend + fallback TURN/STUN
- Packaging для Windows
  - NSIS `.exe`
  - `.msi`
  - portable
  - executable name: `WorldMatesMessenger.exe`

## Стек

- Electron
- React 18 + TypeScript
- Vite
- socket.io-client
- Web Crypto API (AES-256-GCM)
- WebRTC
- electron-builder
- Electron main-process request bridge (IPC) for auth/API in desktop mode

## Запуск

```bash
cd windows-messenger
npm install
npm run dev
```


## Важно про `http://127.0.0.1:5173`

Это **только локальный dev UI сервер Vite**.

- Интерфейс открывается с `127.0.0.1:5173` в dev режиме.
- API/Socket запросы идут на ваш внешний домен из конфигурации:
  - `https://worldmates.club/api/v2/`
  - `https://worldmates.club:449/`

То есть локальный адрес не означает, что backend локальный.

Дополнительно: в desktop-режиме авторизационные form-запросы отправляются через Electron main process (IPC bridge), чтобы обойти возможные CORS/Fetch ограничения renderer в dev-режиме.


### Fallback на `api/windows_app`

Если `api/v2` логин/чаты не отвечают корректно для desktop-клиента, приложение теперь автоматически пробует fallback на:
- `api/windows_app/login.php?type=user_login`
- `api/windows_app/get_users_list.php?type=get_users_list`
- `api/windows_app/get_user_messages.php?type=get_user_messages`
- `api/windows_app/insert_new_message.php?type=insert_new_message`

Это сделано для совместимости с вашим WoWonder windows API слоем.


### Windows API decryptor (новый)

Добавлен новый серверный дешифратор для windows API:
- `api-server-files/api/windows_app/core/windows_message_decryptor.php`

И он подключен в `get_user_messages.php`: при получении сообщений сервер пытается расшифровать
`text` (AES-128-ECB legacy и AES-256-GCM), после чего отдает `decrypted_text` + обновленный `text`.

## Сборка installer

```bash
npm run dist:win
```

Артефакты в `windows-messenger/release/`.

## Ограничения текущего этапа

Это уже сильно расширенный desktop MVP, но не абсолютный 1:1 parity по всем экранам Android. Полный 1:1 требует дальнейшего переноса всех флоу (детальные звонки с answer/ice handling UI, весь media pipeline, полные настройки и security screens, backup UI, moderation scenarios и т.д.).


## Merge conflicts

Смотрите `windows-messenger/docs/MERGE_RESOLVE_GUIDE.md` для правил выбора Current/Incoming/Both без поломки auth/network слоя.
