// ─── Localisation (RU / UK / EN) ─────────────────────────────────────────────
// Default language: Russian.
// All user-visible strings must come from here — no hardcoded text in components.

export type Lang = 'ru' | 'uk' | 'en';

const STORAGE_KEY = 'wm_lang';

// ─── Translations ──────────────────────────────────────────────────────────────

const translations: Record<Lang, Record<string, string>> = {
  // ── Russian (default) ────────────────────────────────────────────────────────
  ru: {
    // Auth
    'auth.subtitle':             'Войдите в мессенджер',
    'auth.tab.login':            'Войти',
    'auth.tab.register':         'Регистрация',
    'auth.field.username':       'Имя пользователя',
    'auth.field.phone':          'Телефон',
    'auth.field.email':          'Email',
    'auth.field.password':       'Пароль',
    'auth.placeholder.username': 'ваш_никнейм',
    'auth.placeholder.phone':    '+7 000 000 0000',
    'auth.placeholder.email':    'email@example.com',
    'auth.placeholder.password': '••••••••',
    'auth.loading':              'Подождите…',
    'auth.createAccount':        'Создать аккаунт',
    'auth.signIn':               'Войти',
    'auth.error.regFailed':      'Ошибка регистрации.',
    'auth.error.authFailed':     'Ошибка входа.',
    'auth.error.unknown':        'Неизвестная ошибка',

    // Navigation
    'nav.chats':    'Чаты',
    'nav.groups':   'Группы',
    'nav.channels': 'Каналы',
    'nav.stories':  'Истории',
    'nav.calls':    'Звонки',
    'nav.settings': 'Настройки',
    'nav.logout':   'Выйти',

    // Sidebar
    'sidebar.messages':    'Сообщения',
    'sidebar.groups':      'Группы',
    'sidebar.channels':    'Каналы',
    'sidebar.stories':     'Истории',
    'sidebar.calls':       'Звонки',
    'sidebar.settings':    'Настройки',
    'sidebar.search':      'Поиск чатов…',
    'sidebar.noChats':     'Нет чатов',
    'sidebar.newGroupName':'Название группы…',
    'sidebar.create':      'Создать',
    'sidebar.noGroups':    'Нет групп',
    'sidebar.members':     'участников',
    'sidebar.channelName': 'Название канала…',
    'sidebar.description': 'Описание…',
    'sidebar.noChannels':  'Нет каналов',
    'sidebar.subscribers': 'подписчиков',
    'sidebar.chooseMedia': 'Выберите фото / видео',
    'sidebar.uploadStory': 'Опубликовать',

    // Calls
    'call.calling':          'Звоним…',
    'call.incoming':         'Входящий звонок',
    'call.connected':        'Соединено',
    'call.accept':           '✓ Принять',
    'call.decline':          '✕ Отклонить',
    'call.end':              '✕ Завершить',
    'call.callLabel':        'Звонок:',
    'call.voiceCall':        'Голосовой звонок',
    'call.videoCall':        'Видеозвонок',
    'call.selectChatFirst':  'Сначала выберите чат',

    // Chat area
    'chat.selectConversation':     'Выберите переписку',
    'chat.selectConversationHint': 'Выберите чат из списка, чтобы начать общение',
    'chat.typing':                 'печатает…',
    'chat.online':                 'в сети',
    'chat.offline':                'не в сети',
    'chat.mute':                   'Заглушить',
    'chat.archive':                'Архивировать',
    'chat.deleteConversation':     'Удалить переписку',
    'chat.deleteConversationConfirm': 'Удалить переписку?',
    'chat.loadEarlier':            'Загрузить ранние сообщения',
    'chat.loadError':              'Не удалось загрузить сообщения.\nСервер временно недоступен.',
    'chat.retry':                  'Повторить',
    'chat.editing':                '✎ Редактирование',
    'chat.replyTo':                '↩ Ответить ',
    'chat.yourself':               'себе',
    'chat.attachFile':             'Прикрепить файл',
    'chat.voiceMessage':           'Голосовое сообщение (удерживайте)',
    'chat.stopRecording':          'Остановить запись',
    'chat.editPlaceholder':        'Редактировать сообщение…',
    'chat.writePlaceholder':       'Написать сообщение…',

    // Settings
    'settings.userId':       'ID пользователя:',
    'settings.security':     'Безопасность',
    'settings.e2ee':         'Сквозное шифрование',
    'settings.signalBadge':  'Signal Protocol v3',
    'settings.keysReg':      'Ключи зарегистрированы',
    'settings.e2eeHint':     'Если сообщения отображаются как «Зашифровано», сбросьте E2EE ключи. Устройство будет перерегистрировано — контакты получат уведомление и шифрование восстановится автоматически.',
    'settings.resetting':    'Сброс…',
    'settings.keysReset':    '✓ Ключи сброшены — переподключите Android',
    'settings.errorRetry':   'Ошибка — попробуйте снова',
    'settings.resetKeys':    'Сбросить E2EE ключи',
    'settings.connection':   'Соединение',
    'settings.socketStatus': 'Статус соединения',
    'settings.signOut':      'Выйти',
    'settings.language':     'Язык интерфейса',

    // Profile editing
    'settings.editProfile':  'Редактировать профиль',
    'settings.firstName':    'Имя',
    'settings.lastName':     'Фамилия',
    'settings.username':     'Никнейм',
    'settings.about':        'О себе',
    'settings.saveProfile':  'Сохранить',
    'settings.saving':       'Сохраняю…',
    'settings.saved':        '✓ Сохранено',
    'settings.uploadAvatar': 'Сменить фото',

    // Archived chats
    'sidebar.archived':   'Архив',
    'sidebar.noArchived': 'Нет архивных чатов',
    'chat.unarchive':     'Из архива',

    // Block / unblock
    'settings.blockedUsers': 'Заблокированные',
    'settings.noBlocked':    'Нет заблокированных',
    'settings.unblock':      'Разблокировать',
    'chat.block':            'Заблокировать',
    'chat.unblock':          'Разблокировать',

    // Bubble actions
    'bubble.reply':   'Ответить',
    'bubble.react':   'Реакция',
    'bubble.edit':    'Изменить',
    'bubble.delete':  'Удалить',
    'bubble.you':     'Вы',
    'bubble.user':    'Пользователь',
    'bubble.media':   '[медиа]',
    'bubble.encrypted':       '🔒 Зашифровано',
    'bubble.edited':          ' (изм.)',
    'bubble.e2eTitle':        'Сквозное шифрование',

    // Misc / errors
    'misc.noMessages':  'Нет сообщений',
    'misc.sendError':   'Не удалось отправить сообщение. Сервер недоступен.',
    'misc.downloadFile':'Скачать файл',
    'channel.totalVotes':  'голосов',
    'channel.anonymous':   'Анонимный',
    'channel.pollClosed':  'Опрос закрыт',
    'channel.vote':        'Проголосовать',
    'channel.addComment':  'Написать комментарий…',
    'channel.comments':    'Комментарии',

    // Socket status translations
    'socket.connected':      'Подключено',
    'socket.offline':        'Не в сети',
    'socket.disconnected':   'Отключено',
    'socket.reconnected':    'Переподключено',
    'socket.error':          'Ошибка соединения',
    'socket.reconnectFailed':'Не удалось переподключиться',

    // Tray / notifications (mirrors MAIN_I18N in main.cjs)
    'tray.show':       'Показать',
    'tray.quit':       'Выйти',
    'tray.newMessage': 'Новое сообщение',
    'tray.unread':     'Непрочитанных: {count}',

    // Search / drafts
    'sidebar.searchGroups':   'Поиск групп…',
    'sidebar.searchChannels': 'Поиск каналов…',
    'chat.search':            'Поиск в чате…',
    'chat.searchPlaceholder': 'Поиск сообщений…',
    'chat.searchResults':     'Результаты поиска',
    'chat.searchEmpty':       'Ничего не найдено',
    'chat.draft':             'Черновик',

    // Language names
    'lang.ru': 'Русский',
    'lang.uk': 'Українська',
    'lang.en': 'English',

    // Call history
    'calls.history':     'История звонков',
    'calls.loadHistory': 'Загрузить историю',
    'calls.noHistory':   'Нет записей звонков',
    'calls.clearHistory':'Очистить всё',
    'calls.all':         'Все',
    'calls.missed':      'Пропущенные',
    'calls.incoming':    'Входящие',
    'calls.outgoing':    'Исходящие',

    // Privacy settings
    'settings.privacy':         'Приватность',
    'settings.loadPrivacy':     'Загрузить настройки',
    'settings.showLastSeen':    'Последнее посещение',
    'settings.messagePrivacy':  'Кто может писать мне',
    'settings.followPrivacy':   'Кто может подписаться',
    'settings.confirmFollowers':'Подтверждать подписчиков',
    'settings.friendPrivacy':   'Запросы дружбы',
    'settings.postPrivacy':     'Видимость постов',
    'settings.showActivities':  'Статус активности',
    'settings.birthPrivacy':    'Дата рождения',
    'settings.visitPrivacy':    'История посещений профиля',
    'settings.everyone':        'Все',
    'settings.following':       'Те, на кого я подписан',
    'settings.nobody':          'Никто',
    'settings.onlyMe':          'Только я',
    'settings.show':            'Показывать',
    'settings.hide':            'Скрывать',
    'settings.yes':             'Да',
    'settings.no':              'Нет',
    'settings.loadingDots':     'Загрузка…',

    // Sticker / GIF picker
    'chat.stickerPicker': 'Стикеры',
    'chat.gifPicker':     'GIF',
    'chat.stickers':      'Стикеры',
    'chat.gifTrending':   'Популярные GIF',
    'chat.searchGif':     'Поиск GIF…',
    'chat.noStickers':    'Нет стикеров',
    'chat.emoji':         'Эмодзи',

    // Bot search
    'sidebar.bots':       'Боты',
    'sidebar.searchBots': 'Поиск ботов…',
    'sidebar.chat':       'Написать',
  },

  // ── Ukrainian ────────────────────────────────────────────────────────────────
  uk: {
    'auth.subtitle':             'Увійдіть до месенджера',
    'auth.tab.login':            'Увійти',
    'auth.tab.register':         'Реєстрація',
    'auth.field.username':       "Ім'я користувача",
    'auth.field.phone':          'Телефон',
    'auth.field.email':          'Email',
    'auth.field.password':       'Пароль',
    'auth.placeholder.username': 'ваш_нікнейм',
    'auth.placeholder.phone':    '+380 00 000 0000',
    'auth.placeholder.email':    'email@example.com',
    'auth.placeholder.password': '••••••••',
    'auth.loading':              'Зачекайте…',
    'auth.createAccount':        'Створити акаунт',
    'auth.signIn':               'Увійти',
    'auth.error.regFailed':      'Помилка реєстрації.',
    'auth.error.authFailed':     'Помилка входу.',
    'auth.error.unknown':        'Невідома помилка',

    'nav.chats':    'Чати',
    'nav.groups':   'Групи',
    'nav.channels': 'Канали',
    'nav.stories':  'Історії',
    'nav.calls':    'Дзвінки',
    'nav.settings': 'Налаштування',
    'nav.logout':   'Вийти',

    'sidebar.messages':    'Повідомлення',
    'sidebar.groups':      'Групи',
    'sidebar.channels':    'Канали',
    'sidebar.stories':     'Історії',
    'sidebar.calls':       'Дзвінки',
    'sidebar.settings':    'Налаштування',
    'sidebar.search':      'Пошук чатів…',
    'sidebar.noChats':     'Немає чатів',
    'sidebar.newGroupName':'Назва групи…',
    'sidebar.create':      'Створити',
    'sidebar.noGroups':    'Немає груп',
    'sidebar.members':     'учасників',
    'sidebar.channelName': 'Назва каналу…',
    'sidebar.description': 'Опис…',
    'sidebar.noChannels':  'Немає каналів',
    'sidebar.subscribers': 'підписників',
    'sidebar.chooseMedia': 'Оберіть фото / відео',
    'sidebar.uploadStory': 'Опублікувати',

    'call.calling':         'Дзвонимо…',
    'call.incoming':        'Вхідний дзвінок',
    'call.connected':       "З'єднано",
    'call.accept':          '✓ Прийняти',
    'call.decline':         '✕ Відхилити',
    'call.end':             '✕ Завершити',
    'call.callLabel':       'Дзвінок:',
    'call.voiceCall':       'Голосовий дзвінок',
    'call.videoCall':       'Відеодзвінок',
    'call.selectChatFirst': 'Спочатку оберіть чат',

    'chat.selectConversation':     'Оберіть переписку',
    'chat.selectConversationHint': 'Оберіть чат зі списку, щоб почати спілкування',
    'chat.typing':                 'друкує…',
    'chat.online':                 'в мережі',
    'chat.offline':                'не в мережі',
    'chat.mute':                   'Вимкнути звук',
    'chat.archive':                'Архівувати',
    'chat.deleteConversation':     'Видалити переписку',
    'chat.deleteConversationConfirm': 'Видалити переписку?',
    'chat.loadEarlier':            'Завантажити ранні повідомлення',
    'chat.loadError':              'Не вдалося завантажити повідомлення.\nСервер тимчасово недоступний.',
    'chat.retry':                  'Повторити',
    'chat.editing':                '✎ Редагування',
    'chat.replyTo':                '↩ Відповісти ',
    'chat.yourself':               'собі',
    'chat.attachFile':             'Прикріпити файл',
    'chat.voiceMessage':           'Голосове повідомлення (утримуйте)',
    'chat.stopRecording':          'Зупинити запис',
    'chat.editPlaceholder':        'Редагувати повідомлення…',
    'chat.writePlaceholder':       'Написати повідомлення…',

    'settings.userId':       'ID користувача:',
    'settings.security':     'Безпека',
    'settings.e2ee':         'Наскрізне шифрування',
    'settings.signalBadge':  'Signal Protocol v3',
    'settings.keysReg':      'Ключі зареєстровано',
    'settings.e2eeHint':     "Якщо повідомлення відображаються як «Зашифровано», скиньте E2EE ключі. Пристрій буде перереєстровано — контакти отримають сповіщення і шифрування відновиться автоматично.",
    'settings.resetting':    'Скидання…',
    'settings.keysReset':    '✓ Ключі скинуто — перепідключіть Android',
    'settings.errorRetry':   'Помилка — спробуйте знову',
    'settings.resetKeys':    'Скинути E2EE ключі',
    'settings.connection':   "З'єднання",
    'settings.socketStatus': "Статус з'єднання",
    'settings.signOut':      'Вийти',
    'settings.language':     'Мова інтерфейсу',

    'settings.editProfile':  'Редагувати профіль',
    'settings.firstName':    "Ім'я",
    'settings.lastName':     'Прізвище',
    'settings.username':     'Нікнейм',
    'settings.about':        'Про себе',
    'settings.saveProfile':  'Зберегти',
    'settings.saving':       'Зберігаю…',
    'settings.saved':        '✓ Збережено',
    'settings.uploadAvatar': 'Змінити фото',

    'sidebar.archived':   'Архів',
    'sidebar.noArchived': 'Немає архівних чатів',
    'chat.unarchive':     'З архіву',

    'settings.blockedUsers': 'Заблоковані',
    'settings.noBlocked':    'Немає заблокованих',
    'settings.unblock':      'Розблокувати',
    'chat.block':            'Заблокувати',
    'chat.unblock':          'Розблокувати',

    'bubble.reply':    'Відповісти',
    'bubble.react':    'Реакція',
    'bubble.edit':     'Змінити',
    'bubble.delete':   'Видалити',
    'bubble.you':      'Ви',
    'bubble.user':     'Користувач',
    'bubble.media':    '[медіа]',
    'bubble.encrypted':  '🔒 Зашифровано',
    'bubble.edited':     ' (змін.)',
    'bubble.e2eTitle':   'Наскрізне шифрування',

    'misc.noMessages':   'Немає повідомлень',
    'misc.sendError':    'Не вдалося надіслати повідомлення. Сервер недоступний.',
    'misc.downloadFile': 'Завантажити файл',
    'channel.totalVotes':  'голосів',
    'channel.anonymous':   'Анонімне',
    'channel.pollClosed':  'Опитування закрито',
    'channel.vote':        'Проголосувати',
    'channel.addComment':  'Написати коментар…',
    'channel.comments':    'Коментарі',

    'socket.connected':      "З'єднано",
    'socket.offline':        'Не в мережі',
    'socket.disconnected':   'Відключено',
    'socket.reconnected':    'Перепідключено',
    'socket.error':          "Помилка з'єднання",
    'socket.reconnectFailed':"Не вдалося перепідключитися",

    'tray.show':       'Показати',
    'tray.quit':       'Вийти',
    'tray.newMessage': 'Нове повідомлення',
    'tray.unread':     'Непрочитаних: {count}',

    // Search / drafts
    'sidebar.searchGroups':   'Пошук груп…',
    'sidebar.searchChannels': 'Пошук каналів…',
    'chat.search':            'Пошук у чаті…',
    'chat.searchPlaceholder': 'Пошук повідомлень…',
    'chat.searchResults':     'Результати пошуку',
    'chat.searchEmpty':       'Нічого не знайдено',
    'chat.draft':             'Чернетка',

    'lang.ru': 'Русский',
    'lang.uk': 'Українська',
    'lang.en': 'English',

    // Call history
    'calls.history':     'Історія дзвінків',
    'calls.loadHistory': 'Завантажити історію',
    'calls.noHistory':   'Немає записів дзвінків',
    'calls.clearHistory':'Очистити все',
    'calls.all':         'Усі',
    'calls.missed':      'Пропущені',
    'calls.incoming':    'Вхідні',
    'calls.outgoing':    'Вихідні',

    // Privacy settings
    'settings.privacy':         'Приватність',
    'settings.loadPrivacy':     'Завантажити налаштування',
    'settings.showLastSeen':    'Останнє відвідування',
    'settings.messagePrivacy':  'Хто може писати мені',
    'settings.followPrivacy':   'Хто може підписатися',
    'settings.confirmFollowers':'Підтверджувати підписників',
    'settings.friendPrivacy':   'Запити дружби',
    'settings.postPrivacy':     'Видимість постів',
    'settings.showActivities':  'Статус активності',
    'settings.birthPrivacy':    'Дата народження',
    'settings.visitPrivacy':    'Історія відвідувань профілю',
    'settings.everyone':        'Всі',
    'settings.following':       'На кого я підписаний',
    'settings.nobody':          'Ніхто',
    'settings.onlyMe':          'Тільки я',
    'settings.show':            'Показувати',
    'settings.hide':            'Приховувати',
    'settings.yes':             'Так',
    'settings.no':              'Ні',
    'settings.loadingDots':     'Завантаження…',

    // Sticker / GIF picker
    'chat.stickerPicker': 'Стікери',
    'chat.gifPicker':     'GIF',
    'chat.stickers':      'Стікери',
    'chat.searchGif':     'Пошук GIF…',
    'chat.gifTrending':   'Популярні GIF',
    'chat.noStickers':    'Немає стікерів',
    'chat.emoji':         'Емодзі',

    // Bot search
    'sidebar.bots':       'Боти',
    'sidebar.searchBots': 'Пошук ботів…',
    'sidebar.chat':       'Написати',
  },

  // ── English ──────────────────────────────────────────────────────────────────
  en: {
    'auth.subtitle':             'Sign in to your messenger',
    'auth.tab.login':            'Sign In',
    'auth.tab.register':         'Register',
    'auth.field.username':       'Username',
    'auth.field.phone':          'Phone',
    'auth.field.email':          'Email',
    'auth.field.password':       'Password',
    'auth.placeholder.username': 'your_username',
    'auth.placeholder.phone':    '+1 234 567 8900',
    'auth.placeholder.email':    'email@example.com',
    'auth.placeholder.password': '••••••••',
    'auth.loading':              'Please wait…',
    'auth.createAccount':        'Create account',
    'auth.signIn':               'Sign in',
    'auth.error.regFailed':      'Registration failed.',
    'auth.error.authFailed':     'Auth failed.',
    'auth.error.unknown':        'Unknown error',

    'nav.chats':    'Chats',
    'nav.groups':   'Groups',
    'nav.channels': 'Channels',
    'nav.stories':  'Stories',
    'nav.calls':    'Calls',
    'nav.settings': 'Settings',
    'nav.logout':   'Logout',

    'sidebar.messages':    'Messages',
    'sidebar.groups':      'Groups',
    'sidebar.channels':    'Channels',
    'sidebar.stories':     'Stories',
    'sidebar.calls':       'Calls',
    'sidebar.settings':    'Settings',
    'sidebar.search':      'Search chats…',
    'sidebar.noChats':     'No chats yet',
    'sidebar.newGroupName':'New group name…',
    'sidebar.create':      'Create',
    'sidebar.noGroups':    'No groups',
    'sidebar.members':     'members',
    'sidebar.channelName': 'Channel name…',
    'sidebar.description': 'Description…',
    'sidebar.noChannels':  'No channels',
    'sidebar.subscribers': 'subscribers',
    'sidebar.chooseMedia': 'Choose image / video',
    'sidebar.uploadStory': 'Upload story',

    'call.calling':         'Calling…',
    'call.incoming':        'Incoming call',
    'call.connected':       'Connected',
    'call.accept':          '✓ Accept',
    'call.decline':         '✕ Decline',
    'call.end':             '✕ End',
    'call.callLabel':       'Call:',
    'call.voiceCall':       'Voice call',
    'call.videoCall':       'Video call',
    'call.selectChatFirst': 'Select a chat first',

    'chat.selectConversation':     'Select a conversation',
    'chat.selectConversationHint': 'Choose a chat from the list to start messaging',
    'chat.typing':                 'typing…',
    'chat.online':                 'online',
    'chat.offline':                'offline',
    'chat.mute':                   'Mute',
    'chat.archive':                'Archive',
    'chat.deleteConversation':     'Delete conversation',
    'chat.deleteConversationConfirm': 'Delete conversation?',
    'chat.loadEarlier':            'Load earlier messages',
    'chat.loadError':              "Couldn't load messages.\nServer may be temporarily unavailable.",
    'chat.retry':                  'Retry',
    'chat.editing':                '✎ Editing',
    'chat.replyTo':                '↩ Reply to ',
    'chat.yourself':               'yourself',
    'chat.attachFile':             'Attach file',
    'chat.voiceMessage':           'Voice message (hold)',
    'chat.stopRecording':          'Stop recording',
    'chat.editPlaceholder':        'Edit message…',
    'chat.writePlaceholder':       'Write a message…',

    'settings.userId':       'User ID:',
    'settings.security':     'Security',
    'settings.e2ee':         'End-to-end encryption',
    'settings.signalBadge':  'Signal Protocol v3',
    'settings.keysReg':      'Keys registered',
    'settings.e2eeHint':     'If messages show "Encrypted message", reset your E2EE keys. This re-registers your device — contacts will be notified and both sides will automatically re-establish encryption.',
    'settings.resetting':    'Resetting…',
    'settings.keysReset':    '✓ Keys reset — reconnect Android',
    'settings.errorRetry':   'Error — try again',
    'settings.resetKeys':    'Reset E2EE keys',
    'settings.connection':   'Connection',
    'settings.socketStatus': 'Socket status',
    'settings.signOut':      'Sign out',
    'settings.language':     'Interface language',

    'settings.editProfile':  'Edit profile',
    'settings.firstName':    'First name',
    'settings.lastName':     'Last name',
    'settings.username':     'Username',
    'settings.about':        'About',
    'settings.saveProfile':  'Save',
    'settings.saving':       'Saving…',
    'settings.saved':        '✓ Saved',
    'settings.uploadAvatar': 'Change photo',

    'sidebar.archived':   'Archive',
    'sidebar.noArchived': 'No archived chats',
    'chat.unarchive':     'Unarchive',

    'settings.blockedUsers': 'Blocked users',
    'settings.noBlocked':    'No blocked users',
    'settings.unblock':      'Unblock',
    'chat.block':            'Block user',
    'chat.unblock':          'Unblock user',

    'bubble.reply':    'Reply',
    'bubble.react':    'React',
    'bubble.edit':     'Edit',
    'bubble.delete':   'Delete',
    'bubble.you':      'You',
    'bubble.user':     'User',
    'bubble.media':    '[media]',
    'bubble.encrypted':  '🔒 Encrypted message',
    'bubble.edited':     ' (edited)',
    'bubble.e2eTitle':   'End-to-end encrypted',

    'misc.noMessages':   'No messages',
    'misc.sendError':    'Failed to send message. Server may be unavailable.',
    'misc.downloadFile': 'Download file',
    'channel.totalVotes':  'votes',
    'channel.anonymous':   'Anonymous',
    'channel.pollClosed':  'Poll closed',
    'channel.vote':        'Vote',
    'channel.addComment':  'Add a comment…',
    'channel.comments':    'Comments',

    'socket.connected':      'Connected',
    'socket.offline':        'Offline',
    'socket.disconnected':   'Disconnected',
    'socket.reconnected':    'Reconnected',
    'socket.error':          'Connection error',
    'socket.reconnectFailed':'Reconnect failed',

    'tray.show':       'Show',
    'tray.quit':       'Quit',
    'tray.newMessage': 'New message',
    'tray.unread':     'Unread: {count}',

    'sidebar.searchGroups':   'Search groups…',
    'sidebar.searchChannels': 'Search channels…',
    'chat.search':            'Search in chat…',
    'chat.searchPlaceholder': 'Search messages…',
    'chat.searchResults':     'Search results',
    'chat.searchEmpty':       'No messages found',
    'chat.draft':             'Draft',

    'lang.ru': 'Русский',
    'lang.uk': 'Українська',
    'lang.en': 'English',

    // Call history
    'calls.history':     'Call history',
    'calls.loadHistory': 'Load history',
    'calls.noHistory':   'No call records',
    'calls.clearHistory':'Clear all',
    'calls.all':         'All',
    'calls.missed':      'Missed',
    'calls.incoming':    'Incoming',
    'calls.outgoing':    'Outgoing',

    // Privacy settings
    'settings.privacy':         'Privacy',
    'settings.loadPrivacy':     'Load settings',
    'settings.showLastSeen':    'Last seen',
    'settings.messagePrivacy':  'Who can message me',
    'settings.followPrivacy':   'Who can follow me',
    'settings.confirmFollowers':'Approve followers',
    'settings.friendPrivacy':   'Friend requests',
    'settings.postPrivacy':     'Post visibility',
    'settings.showActivities':  'Activity status',
    'settings.birthPrivacy':    'Birthday',
    'settings.visitPrivacy':    'Profile visit history',
    'settings.everyone':        'Everyone',
    'settings.following':       'People I follow',
    'settings.nobody':          'Nobody',
    'settings.onlyMe':          'Only me',
    'settings.show':            'Show',
    'settings.hide':            'Hide',
    'settings.yes':             'Yes',
    'settings.no':              'No',
    'settings.loadingDots':     'Loading…',

    // Sticker / GIF picker
    'chat.stickerPicker': 'Stickers',
    'chat.gifPicker':     'GIF',
    'chat.stickers':      'Stickers',
    'chat.searchGif':     'Search GIF…',
    'chat.noStickers':    'No stickers',
    'chat.gifTrending':   'Trending GIFs',
    'chat.emoji':         'Emoji',

    // Bot search
    'sidebar.bots':       'Bots',
    'sidebar.searchBots': 'Search bots…',
    'sidebar.chat':       'Chat',
  },
};

// ─── Module state ─────────────────────────────────────────────────────────────

let currentLang: Lang = 'ru';

/** Call once on app init to read the stored preference. */
export function initLang(): void {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'ru' || stored === 'uk' || stored === 'en') currentLang = stored;
  } catch { /* localStorage may not be available in tests */ }
}

export function getLang(): Lang { return currentLang; }

export function setLang(lang: Lang): void {
  currentLang = lang;
  try { localStorage.setItem(STORAGE_KEY, lang); } catch { /* ignore */ }
}

// ─── Translation function ──────────────────────────────────────────────────────

/**
 * Returns the translated string for `key` in the current language.
 * Falls back to English, then to the raw key if not found.
 * Supports simple variable substitution: t('tray.unread', { count: 3 }) → 'Unread: 3'
 */
export function t(key: string, vars?: Record<string, string | number>): string {
  let str = translations[currentLang]?.[key]
         ?? translations.en?.[key]
         ?? key;
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      str = str.replace(`{${k}}`, String(v));
    }
  }
  return str;
}

/** Translate a raw socket status string into the current language. */
export function translateSocketStatus(status: string): string {
  if (status === 'Connected')              return t('socket.connected');
  if (status === 'Offline')               return t('socket.offline');
  if (status.startsWith('Disconnected'))  return t('socket.disconnected');
  if (status.startsWith('Reconnected'))   return t('socket.reconnected');
  if (status.startsWith('Connection error')) return t('socket.error');
  if (status.startsWith('Reconnect failed')) return t('socket.reconnectFailed');
  return status;
}
