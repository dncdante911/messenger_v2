'use strict';

/**
 * WallyBot — встроенный бот-менеджер WorldMates
 *
 * Аналог Telegram BotFather. Позволяет пользователям создавать и управлять
 * своими ботами прямо в чате, без использования API напрямую.
 *
 * Команды:
 *   /start        — приветствие и главное меню
 *   /help         — справка по командам
 *   /newbot       — создать нового бота (диалог)
 *   /mybots       — список своих ботов
 *   /editbot      — редактировать бота (описание, имя, категорию)
 *   /deletebot    — удалить бота
 *   /token        — показать/обновить токен бота
 *   /setcommands  — установить список команд бота
 *   /setdesc      — установить описание бота
 *   /learn        — научить WallyBot новому ответу (глобальная база знаний)
 *   /forget       — удалить ответ из базы знаний
 *   /ask          — задать вопрос WallyBot (поиск по обученным ответам)
 *   /messenger    — справка по функциям мессенджера (встроенная база RU/UK)
 *
 * Обучаемость:
 *   WallyBot может запоминать факты и ответы через команду /learn.
 *   База знаний хранится в Wo_Bot_Tasks (title=keyword, description=response).
 *   При любом текстовом сообщении бот ищет совпадения в базе знаний.
 */

const crypto = require('crypto');
const { Op }  = require('sequelize');

const WALLYBOT_ID    = 'wallybot';
const WALLYBOT_NAME  = 'WallyBot';
const OWNER_USER_ID  = 1; // системный владелец

const MESSENGER_KB_SEED = [
    // ── Боты ──────────────────────────────────────────────────────────────────
    {
        keyword: 'как найти бота в мессенджере',
        response: 'Открой вкладку Чаты и нажми на иконку робота (Bot Store), либо используй поиск по имени/@username бота.'
    },
    {
        keyword: 'как запустить бота',
        response: 'Открой чат бота и нажми START, после этого отправь /help для списка команд.'
    },
    {
        keyword: 'как создать своего бота',
        response: 'Напиши /newbot прямо сюда! Я проведу тебя за 3 шага: имя → username → описание. Готово за минуту.'
    },
    {
        keyword: 'что умеют боты',
        response: 'Боты могут отвечать на FAQ, рассылать новости, создавать опросы, принимать обращения и автоматизировать поддержку. Создай своего: /newbot'
    },
    {
        keyword: 'как добавить бота в группу',
        response: 'Открой профиль бота → "Добавить в группу" → выбери нужную группу. Бот должен поддерживать группы (настройка /setgroups).'
    },
    {
        keyword: 'как заблокировать бота',
        response: 'Открой чат с ботом → профиль → Block. Разблокировка: Настройки → Конфиденциальность → Заблокированные.'
    },
    {
        keyword: 'как использовать команды бота',
        response: 'Команды начинаются с /. Нажми иконку "/" у поля ввода или введи вручную: /start, /help, /settings и т.д.'
    },
    {
        keyword: 'что такое токен бота',
        response: 'Токен — секретный ключ для доступа к API бота. Получи его командой /token или при создании бота. Никому не передавай!'
    },
    {
        keyword: 'что такое вебхук бота',
        response: 'Webhook — адрес твоего сервера, куда WorldMates отправляет сообщения боту. Настраивается через API: POST /api/v2/bot/setWebhook с полем url.'
    },
    {
        keyword: 'как сделать рассылку',
        response: 'Используй команду /broadcast — выбери бота и введи текст. Сообщение получат все пользователи этого бота.'
    },
    // ── Чаты и сообщения ──────────────────────────────────────────────────────
    {
        keyword: 'как удалить сообщение',
        response: 'Зажми сообщение и выбери "Удалить". Можно удалить у себя или у обоих участников (если прошло менее 48 часов).'
    },
    {
        keyword: 'как редактировать сообщение',
        response: 'Зажми своё сообщение → "Редактировать". Время на редактирование — 48 часов. Собеседник увидит пометку "изменено".'
    },
    {
        keyword: 'как переслать сообщение',
        response: 'Зажми сообщение → "Переслать" → выбери чат или контакт. Можно переслать сразу нескольким.'
    },
    {
        keyword: 'как закрепить сообщение',
        response: 'В групповом чате зажми сообщение → "Закрепить". Только администраторы могут закреплять сообщения.'
    },
    {
        keyword: 'как ответить на сообщение',
        response: 'Свайпни сообщение вправо или зажми → "Ответить". Твой ответ будет привязан к оригинальному сообщению.'
    },
    {
        keyword: 'что такое сохранённые сообщения',
        response: '"Сохранённые сообщения" — твой личный блокнот в мессенджере. Пересылай туда всё что хочешь запомнить.'
    },
    {
        keyword: 'как очистить историю чата',
        response: 'Открой чат → нажми имя контакта вверху → "Очистить историю". Это удалит сообщения только у тебя.'
    },
    // ── Группы и каналы ───────────────────────────────────────────────────────
    {
        keyword: 'чем отличается группа от канала',
        response: 'Группа — чат для общения, все участники могут писать. Канал — только администраторы публикуют контент, остальные читают.'
    },
    {
        keyword: 'как создать группу',
        response: 'Нажми иконку карандаша → "Новая группа" → добавь участников → задай название и фото.'
    },
    {
        keyword: 'как создать канал',
        response: 'Нажми иконку карандаша → "Новый канал" → задай название, описание, выбери тип (публичный/приватный).'
    },
    {
        keyword: 'как стать администратором группы',
        response: 'Создатель группы может назначить администратора: открой список участников → нажми на пользователя → "Назначить администратором".'
    },
    {
        keyword: 'как удалить участника из группы',
        response: 'Только администратор: открой список участников → нажми на пользователя → "Удалить из группы".'
    },
    {
        keyword: 'как покинуть группу',
        response: 'Открой чат группы → нажми название вверху → "Покинуть группу".'
    },
    {
        keyword: 'как сделать группу публичной',
        response: 'Настройки группы → "Тип группы" → Публичная. Можно задать @username чтобы группу можно было найти через поиск.'
    },
    // ── Звонки ────────────────────────────────────────────────────────────────
    {
        keyword: 'как позвонить в мессенджере',
        response: 'Открой чат → нажми иконку телефона (аудио) или видеокамеры (видео) вверху справа. Или перейди на вкладку Звонки.'
    },
    {
        keyword: 'как сделать групповой звонок',
        response: 'В групповом чате нажми иконку телефона вверху. Все участники группы получат приглашение присоединиться.'
    },
    // ── Уведомления ───────────────────────────────────────────────────────────
    {
        keyword: 'как отключить уведомления',
        response: 'Открой чат → нажми имя контакта → "Уведомления" → выключи или задай время "Не беспокоить".'
    },
    {
        keyword: 'как настроить уведомления',
        response: 'Настройки → Уведомления. Там можно настроить звуки, вибрацию, уведомления для групп и личных чатов отдельно.'
    },
    // ── Аккаунт и профиль ─────────────────────────────────────────────────────
    {
        keyword: 'как изменить имя в профиле',
        response: 'Настройки → нажми на своё имя или аватар → "Редактировать профиль". Там можно изменить имя, фото, username и статус.'
    },
    {
        keyword: 'как изменить username',
        response: 'Настройки → профиль → "Изменить" → поле Username. Username должен быть уникальным и содержать от 5 символов.'
    },
    {
        keyword: 'как изменить фото профиля',
        response: 'Настройки → нажми на аватар → "Изменить фото". Можно загрузить из галереи или сделать новое фото.'
    },
    {
        keyword: 'как удалить аккаунт',
        response: 'Настройки → Аккаунт → "Удалить аккаунт". Удаление необратимо — все данные, чаты и боты будут удалены.'
    },
    {
        keyword: 'как восстановить пароль',
        response: 'На экране входа нажми "Забыли пароль?" → введи email или телефон → получи код подтверждения → задай новый пароль.'
    },
    // ── Конфиденциальность ────────────────────────────────────────────────────
    {
        keyword: 'как скрыть статус онлайн',
        response: 'Настройки → Конфиденциальность → "Статус онлайн" → выбери кто видит: Все / Мои контакты / Никто.'
    },
    {
        keyword: 'как заблокировать пользователя',
        response: 'Открой профиль пользователя → прокрути вниз → "Заблокировать". Заблокированный не сможет писать тебе и видеть твой статус.'
    },
    {
        keyword: 'как разблокировать пользователя',
        response: 'Настройки → Конфиденциальность → "Заблокированные" → выбери пользователя → "Разблокировать".'
    },
    {
        keyword: 'кто видит мой номер телефона',
        response: 'Настройки → Конфиденциальность → "Номер телефона" → выбери: Все / Мои контакты / Никто.'
    },
    // ── Медиа и файлы ─────────────────────────────────────────────────────────
    {
        keyword: 'как отправить файл',
        response: 'В поле ввода нажми иконку скрепки (📎) → выбери файл. Поддерживаются документы, фото, видео, аудио. Лимит — 2 ГБ.'
    },
    {
        keyword: 'как отправить голосовое сообщение',
        response: 'Зажми иконку микрофона справа от поля ввода и говори. Отпусти чтобы отправить, свайпни влево чтобы отменить.'
    },
    {
        keyword: 'как отправить стикер',
        response: 'Нажми иконку смайлика → вкладка Стикеры. Можно найти нужный стикер через поиск.'
    },
    // ── Поиск ─────────────────────────────────────────────────────────────────
    {
        keyword: 'как найти человека в мессенджере',
        response: 'Нажми иконку поиска → введи имя или @username. Если человек не появляется — уточни полный @username.'
    },
    {
        keyword: 'как найти сообщение в чате',
        response: 'Открой чат → нажми иконку поиска (🔍) вверху → введи текст. Можно искать по ключевым словам.'
    },
    // ── Сторис ────────────────────────────────────────────────────────────────
    {
        keyword: 'как добавить историю',
        response: 'Перейди на вкладку Истории → нажми кнопку "+" → загрузи фото или видео. История пропадёт через 24 часа.'
    },
    {
        keyword: 'как скрыть истории от пользователя',
        response: 'Настройки → Конфиденциальность → "Истории" → можно выбрать кто видит твои истории, и скрыть конкретных пользователей.'
    },
    // ── Premium ───────────────────────────────────────────────────────────────
    {
        keyword: 'что даёт premium worldmates',
        response: 'WorldMates PRO даёт: больший лимит файлов, эксклюзивные стикеры, приоритетную поддержку, расширенную статистику ботов и другие привилегии.'
    },
    {
        keyword: 'как купить premium',
        response: 'Настройки → WorldMates PRO → выбери план подписки → оплати. Доступны ежемесячная и годовая подписки.'
    },
    // ── Настройки — общий обзор ───────────────────────────────────────────────
    {
        keyword: 'где найти настройки',
        response: 'Настройки находятся на нижней панели навигации — иконка ⚙️ (шестерёнка). Там: профиль, конфиденциальность, уведомления, темы оформления, язык, безопасность и WorldMates PRO.'
    },
    {
        keyword: 'де знайти налаштування',
        response: 'Налаштування знаходяться на нижній панелі навігації — іконка ⚙️ (шестерня). Там: профіль, конфіденційність, сповіщення, теми оформлення, мова, безпека та WorldMates PRO.'
    },
    {
        keyword: 'что есть в настройках',
        response: 'В Настройках (⚙️ внизу экрана) есть:\n• Профиль — имя, фото, username, статус\n• Конфиденциальность — кто что видит\n• Уведомления — звуки, вибрация, превью\n• Темы — светлая / тёмная / системная\n• Язык — украинский / русский / английский\n• Безопасность — смена пароля, сессии\n• WorldMates PRO — подписка\n• Удаление аккаунта'
    },
    {
        keyword: 'що є в налаштуваннях',
        response: 'У Налаштуваннях (⚙️ внизу екрана) є:\n• Профіль — ім\'я, фото, username, статус\n• Конфіденційність — хто що бачить\n• Сповіщення — звуки, вібрація, превью\n• Теми — світла / темна / системна\n• Мова — українська / русский / англійська\n• Безпека — зміна пароля, сесії\n• WorldMates PRO — підписка\n• Видалення акаунту'
    },
    // ── Темы оформления ───────────────────────────────────────────────────────
    {
        keyword: 'как изменить тему оформления',
        response: 'Настройки → Темы (или "Внешний вид") → выбери: Светлая, Тёмная или По системе (следует за настройкой телефона). Тема применяется сразу без перезапуска.'
    },
    {
        keyword: 'як змінити тему оформлення',
        response: 'Налаштування → Теми (або "Зовнішній вигляд") → обери: Світла, Темна або За системою (слідує за налаштуванням телефону). Тема застосовується одразу без перезапуску.'
    },
    {
        keyword: 'как включить тёмную тему',
        response: 'Настройки → Темы → выбери "Тёмная". Или выбери "По системе" — тогда тема будет автоматически меняться вместе с системной темой телефона.'
    },
    {
        keyword: 'як увімкнути темну тему',
        response: 'Налаштування → Теми → обери "Темна". Або обери "За системою" — тоді тема буде автоматично змінюватись разом із системною темою телефону.'
    },
    {
        keyword: 'темная тема dark mode',
        response: 'Тёмная тема: Настройки → Темы → Тёмная. Вариант "По системе" автоматически переключает тему вместе с телефоном (день/ночь).'
    },
    {
        keyword: 'темна тема dark mode',
        response: 'Темна тема: Налаштування → Теми → Темна. Варіант "За системою" автоматично перемикає тему разом з телефоном (день/ніч).'
    },
    {
        keyword: 'как поменять язык приложения',
        response: 'Настройки → Язык (Language) → выбери Украинский / Русский / Английский. Интерфейс переключится сразу или после перезапуска.'
    },
    {
        keyword: 'як змінити мову додатку',
        response: 'Налаштування → Мова (Language) → обери Українська / Русский / Англійська. Інтерфейс переключиться одразу або після перезапуску.'
    },
];

// user_id WallyBot в таблице Wo_Users (устанавливается при инициализации)
// Нужен для отправки ответов через regular private_message канал
let WALLYBOT_USER_ID = null;

// ─── FSM состояния разговора ──────────────────────────────────────────────────

const STATES = {
    IDLE:               'idle',
    NEWBOT_NAME:        'newbot_name',
    NEWBOT_USERNAME:    'newbot_username',
    NEWBOT_DESC:        'newbot_desc',
    EDITBOT_SELECT:     'editbot_select',
    EDITBOT_FIELD:      'editbot_field',
    EDITBOT_VALUE:      'editbot_value',
    DELETEBOT_CONFIRM:  'deletebot_confirm',
    TOKEN_SELECT:       'token_select',
    SETCMD_SELECT:      'setcmd_select',
    SETCMD_INPUT:       'setcmd_input',
    SETDESC_SELECT:     'setdesc_select',
    SETDESC_INPUT:      'setdesc_input',
    LEARN_KEYWORD:      'learn_keyword',
    LEARN_RESPONSE:     'learn_response',
    FORGET_SELECT:      'forget_select',
    SETINLINE_SELECT:   'setinline_select',
    SETGROUPS_SELECT:   'setgroups_select',
    REVOKE_SELECT:      'revoke_select',
    BROADCAST_SELECT:   'broadcast_select',
    BROADCAST_INPUT:    'broadcast_input',
    TOUR_STEP:          'tour_step',
    TEMPLATE_SELECT:    'template_select',
    TEMPLATE_NAME:      'template_name',
    TEMPLATE_USERNAME:  'template_username',
    CHECKBOT_SELECT:    'checkbot_select',
    RSS_SELECT_BOT:     'rss_select_bot',
    RSS_ADD_URL:        'rss_add_url',
    RSS_ADD_NAME:       'rss_add_name',
};

// ─── Шаблоны ботов ────────────────────────────────────────────────────────────

const BOT_TEMPLATES = {
    faq: {
        id:           'faq',
        icon:         '❓',
        name:         'FAQ-бот',
        description:  'Отвечает на часто задаваемые вопросы. Идеально для поддержки клиентов.',
        default_desc: 'Привет! Я помогу ответить на твои вопросы. Напиши /help чтобы начать.',
        commands: [
            { command: 'start',   description: 'Начать работу' },
            { command: 'help',    description: 'Список вопросов и ответов' },
            { command: 'contact', description: 'Связаться с поддержкой' },
        ],
        knowledge: [
            { keyword: 'помощь',    response: 'Напиши свой вопрос, и я постараюсь ответить!' },
            { keyword: 'контакт',   response: 'Свяжитесь с нами: напишите вопрос прямо здесь и мы ответим.' },
            { keyword: 'режим работы', response: 'Мы работаем ежедневно с 9:00 до 21:00.' },
        ]
    },
    news: {
        id:           'news',
        icon:         '📰',
        name:         'Бот новостей',
        description:  'Рассылает новости и анонсы подписчикам. Идеально для каналов и проектов.',
        default_desc: 'Подпишись чтобы получать актуальные новости и анонсы.',
        commands: [
            { command: 'start',     description: 'Подписаться на новости' },
            { command: 'help',      description: 'Информация о боте' },
            { command: 'unsubscribe', description: 'Отписаться от рассылки' },
        ],
        knowledge: [
            { keyword: 'подписка',   response: 'Вы подписаны на наши новости! Ждите обновлений.' },
            { keyword: 'отписаться', response: 'Вы отписались от рассылки. Если передумаете — напишите /start.' },
        ]
    },
    welcome: {
        id:           'welcome',
        icon:         '👋',
        name:         'Бот-приветствие',
        description:  'Встречает новых пользователей и знакомит их с продуктом или сервисом.',
        default_desc: 'Добро пожаловать! Я расскажу тебе всё о нашем проекте.',
        commands: [
            { command: 'start',  description: 'Начать знакомство' },
            { command: 'about',  description: 'О нашем проекте' },
            { command: 'help',   description: 'Что я умею' },
        ],
        knowledge: [
            { keyword: 'о проекте', response: 'Расскажи мне о себе — и я расскажу о нас!' },
            { keyword: 'начать',    response: 'Привет! Давай начнём знакомство. Что тебя интересует?' },
        ]
    },
    support: {
        id:           'support',
        icon:         '🆘',
        name:         'Бот поддержки',
        description:  'Принимает обращения пользователей и передаёт их команде. Для бизнеса.',
        default_desc: 'Служба поддержки. Опишите свою проблему и мы ответим в ближайшее время.',
        commands: [
            { command: 'start',   description: 'Написать в поддержку' },
            { command: 'status',  description: 'Статус моего обращения' },
            { command: 'help',    description: 'Частые вопросы' },
        ],
        knowledge: [
            { keyword: 'обращение',  response: 'Опишите проблему и мы ответим в течение 24 часов.' },
            { keyword: 'статус',     response: 'Ваше обращение рассматривается. Пожалуйста, ожидайте.' },
            { keyword: 'не работает', response: 'Опишите подробнее что именно не работает, и мы поможем.' },
        ]
    },
};

// ─── Хранение состояний в памяти (для быстрого доступа) ──────────────────────

const userStates = new Map(); // userId -> { state, data }

function getState(userId) {
    return userStates.get(userId) || { state: STATES.IDLE, data: {} };
}

function setState(userId, state, data = {}) {
    userStates.set(userId, { state, data });
}

function clearState(userId) {
    userStates.set(userId, { state: STATES.IDLE, data: {} });
}

// Возвращает true если пользователь ещё не получал сообщений от WallyBot
async function isNewUser(ctx, userId) {
    try {
        const count = await ctx.wo_bot_messages.count({
            where: { bot_id: WALLYBOT_ID, chat_id: String(userId), direction: 'outgoing' }
        });
        return count === 0;
    } catch { return false; }
}

// ─── Генерация токена бота ────────────────────────────────────────────────────

function generateBotToken(botId) {
    const rand = crypto.randomBytes(32).toString('hex');
    const hash = crypto.createHmac('sha256', botId).update(rand).digest('hex');
    return `${botId}:${hash}`;
}

function sanitize(str) {
    if (typeof str !== 'string') return str;
    return str.replace(/[<>"'&]/g, (c) => ({
        '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '&': '&amp;'
    }[c]));
}

// ─── Отправка сообщения пользователю ─────────────────────────────────────────
// Отправляет через regular private_message (Wo_Messages) — Android получает
// как обычное сообщение в чате. Дополнительно эмитит bot_message для совместимости.

async function sendToUser(ctx, io, userId, text, replyMarkup = null) {
    try {
        const now = Math.floor(Date.now() / 1000);

        // 1. Сохранить в Wo_Bot_Messages (для статистики и polling/webhook)
        const botMsg = await ctx.wo_bot_messages.create({
            bot_id:       WALLYBOT_ID,
            chat_id:      String(userId),
            chat_type:    'private',
            direction:    'outgoing',
            text,
            reply_markup: replyMarkup ? JSON.stringify(replyMarkup) : null,
            processed:    1,
            processed_at: new Date()
        });

        // 2. Если у WallyBot есть Wo_Users запись — сохранить в Wo_Messages
        //    (нужно для истории чата в Android)
        if (WALLYBOT_USER_ID) {
            try {
                const woMsg = await ctx.wo_messages.create({
                    from_id: WALLYBOT_USER_ID,
                    to_id:   userId,
                    text:    text,
                    seen:    0,
                    time:    now
                });

                // 3. Отправить как regular private_message — Android отобразит в обычном чате
                // Payload must match the standard buildMessage() shape so that:
                //   - MessagesViewModel reads from_id/to_id/text and shows message inline
                //   - MessageNotificationService reads from_id and suppresses the
                //     notification when the wallybot chat is already open
                if (io) {
                    io.to(String(userId)).emit('private_message', {
                        status:            200,
                        id:                woMsg.id,           // message ID (number), not user ID
                        from_id:           WALLYBOT_USER_ID,   // standard sender field
                        to_id:             userId,              // standard recipient field
                        text:              text,                // standard text field
                        message:           text,                // kept for legacy web clients
                        message_id:        woMsg.id,
                        time:              now,
                        time_api:          now,
                        messages_html:     '',
                        message_page_html: '',
                        username:          WALLYBOT_NAME,
                        avatar:            'upload/photos/d-avatar.jpg',
                        receiver:          userId,
                        sender:            WALLYBOT_USER_ID,   // kept for legacy web clients
                        cipher_version:    1,                   // plaintext — no decryption needed
                        media:             '',
                        isMedia:           false,
                        isRecord:          false,
                        reply_markup:      replyMarkup || null,
                        bot_id:            WALLYBOT_ID           // for Android to route callback_query
                    });
                }
            } catch (msgErr) {
                // Wo_Messages запись не критична — продолжаем
                console.warn('[WallyBot/Wo_Messages]', msgErr.message);
            }
        }

        // 4. Также эмитим bot_message для совместимости с клиентами, которые слушают его
        if (io) {
            const botPayload = {
                event:        'bot_message',
                bot_id:       WALLYBOT_ID,
                message_id:   botMsg.id,
                text,
                reply_markup: replyMarkup,
                timestamp:    Date.now()
            };
            io.to(String(userId)).emit('bot_message', botPayload);
            io.to(`user_bot_${userId}_${WALLYBOT_ID}`).emit('bot_message', botPayload);
        }

        await ctx.wo_bots.increment('messages_sent', { where: { bot_id: WALLYBOT_ID } });

    } catch (err) {
        console.error('[WallyBot/send]', err.message);
    }
}

function inlineKeyboard(buttons, columns = 2) {
    const keyboard = [];
    let row = [];
    for (const btn of buttons) {
        row.push(btn);
        if (row.length >= columns) { keyboard.push(row); row = []; }
    }
    if (row.length) keyboard.push(row);
    return { inline_keyboard: keyboard };
}

function btn(text, callbackData) {
    return { text, callback_data: callbackData };
}

// ─── Обучение: база знаний ────────────────────────────────────────────────────

async function learnFact(ctx, keyword, response, userId) {
    // Сохраняем в Wo_Bot_Tasks: title=keyword, description=response
    await ctx.wo_bot_tasks.findOrCreate({
        where:    { bot_id: WALLYBOT_ID, title: keyword.toLowerCase().trim() },
        defaults: {
            bot_id:      WALLYBOT_ID,
            user_id:     userId,
            chat_id:     String(userId),
            title:       keyword.toLowerCase().trim(),
            description: response,
            status:      'done',
            priority:    'low',
            created_at:  new Date()
        }
    });
    // Обновляем если уже есть
    await ctx.wo_bot_tasks.update(
        { description: response, user_id: userId },
        { where: { bot_id: WALLYBOT_ID, title: keyword.toLowerCase().trim() } }
    );
}

async function searchKnowledge(ctx, query) {
    const words = extractSearchTerms(query);
    if (!words.length) return null;

    // Ищем по ключам и тексту ответа, чтобы лучше покрыть RU/UK перефразировки.
    const conditions = words.flatMap(w => ([
        { title:       { [Op.like]: `%${w}%` } },
        { description: { [Op.like]: `%${w}%` } }
    ]));

    const results = await ctx.wo_bot_tasks.findAll({
        where: {
            bot_id: WALLYBOT_ID,
            status: 'done',
            [Op.or]: conditions
        },
        raw: true,
        limit: 60
    });

    if (!results.length) return null;

    let best = null;
    let bestScore = 0;

    for (const item of results) {
        const titleTokens = new Set(extractSearchTerms(item.title || ''));
        const descTokens  = new Set(extractSearchTerms(item.description || ''));

        let score = 0;
        for (const token of words) {
            if (titleTokens.has(token)) score += 4;
            if (descTokens.has(token))  score += 2;
        }

        // Бонус за совпадение фразы целиком (после нормализации)
        const normalizedQuery = normalizeText(query);
        const normalizedTitle = normalizeText(item.title || '');
        if (normalizedQuery && normalizedTitle && normalizedTitle.includes(normalizedQuery)) {
            score += 6;
        }

        if (score > bestScore) {
            bestScore = score;
            best = item;
        }
    }

    const confidence = Math.min(1, bestScore / Math.max(words.length * 4, 6));
    return best && confidence >= 0.35 ? best : null;
}

const SEARCH_STOPWORDS = new Set([
    // RU
    'и', 'или', 'в', 'во', 'на', 'с', 'со', 'к', 'по', 'за', 'из', 'под', 'над', 'о', 'об',
    'а', 'но', 'не', 'да', 'же', 'ли', 'это', 'этот', 'эта', 'эти', 'как', 'что', 'где', 'когда',
    'почему', 'зачем', 'мне', 'мой', 'моя', 'моё', 'мои', 'твой', 'твоя', 'их', 'его', 'ее',
    // UK
    'і', 'й', 'та', 'або', 'у', 'в', 'на', 'з', 'із', 'до', 'по', 'над', 'під', 'про',
    'але', 'не', 'це', 'цей', 'ця', 'ці', 'як', 'що', 'де', 'коли', 'чому', 'навіщо',
    'мені', 'мій', 'моя', 'моє', 'мої', 'твій', 'твоя', 'його', 'її', 'їх'
]);

const SEARCH_SYNONYMS = {
    // RU/UK support intents
    аккаунт: ['аккаунт', 'учетная', 'учётная', 'профиль', 'обліковий', 'акаунт', 'профіль'],
    пароль: ['пароль', 'код', 'pass', 'password'],
    сообщение: ['сообщение', 'сообщения', 'смс', 'меседж', 'повідомлення', 'повідомлень', 'message'],
    бот: ['бот', 'бота', 'боту', 'боти', 'ботів'],
    чат: ['чат', 'чаты', 'чатик', 'діалог', 'діалоги', 'розмова', 'переписка'],
    группа: ['группа', 'группы', 'группу', 'група', 'групи', 'спільнота'],
    звонок: ['звонок', 'звонки', 'вызов', 'дзвінок', 'дзвінки', 'виклик'],
    удалить: ['удалить', 'удаление', 'стереть', 'удалити', 'видалити', 'видалення'],
    создать: ['создать', 'сделать', 'добавить', 'створити', 'додати']
};

function normalizeText(value = '') {
    return String(value)
        .toLowerCase()
        .replace(/[ё]/g, 'е')
        .replace(/['']/g, '')
        .replace(/[^\p{L}\p{N}\s]/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function normalizeToken(token = '') {
    let t = normalizeText(token);
    // Простейшее стеммирование для RU/UK, чтобы снизить зависимость от формы слова.
    t = t
        .replace(/(ами|ями|ого|ему|ому|ах|ях|ий|ый|ой|ая|яя|ое|ее|ые|ие|ов|ев|ів|ів|ий|ій|ою|ею|ом|ем|ам|ям|у|ю|а|я|ы|і|ї|е)$/u, '');
    return t;
}

function expandSynonyms(token) {
    for (const list of Object.values(SEARCH_SYNONYMS)) {
        if (list.includes(token)) {
            return list.map(normalizeToken).filter(Boolean);
        }
    }
    return [token];
}

function extractSearchTerms(text) {
    const baseTokens = normalizeText(text)
        .split(/\s+/)
        .map(normalizeToken)
        .filter(t => t.length >= 2 && !SEARCH_STOPWORDS.has(t));

    const expanded = new Set();
    for (const token of baseTokens) {
        expanded.add(token);
        for (const s of expandSynonyms(token)) expanded.add(s);
    }

    return [...expanded];
}

async function getKnowledgeList(ctx, limit = 20) {
    return ctx.wo_bot_tasks.findAll({
        where:  { bot_id: WALLYBOT_ID, status: 'done' },
        order:  [['created_at', 'DESC']],
        limit,
        raw:    true
    });
}

async function forgetFact(ctx, keyword) {
    await ctx.wo_bot_tasks.destroy({
        where: { bot_id: WALLYBOT_ID, title: keyword.toLowerCase().trim() }
    });
}

async function ensureMessengerKnowledgeBase(ctx) {
    for (const item of MESSENGER_KB_SEED) {
        await ctx.wo_bot_tasks.findOrCreate({
            where: {
                bot_id: WALLYBOT_ID,
                title: item.keyword.toLowerCase().trim()
            },
            defaults: {
                bot_id:      WALLYBOT_ID,
                user_id:     OWNER_USER_ID,
                chat_id:     String(OWNER_USER_ID),
                title:       item.keyword.toLowerCase().trim(),
                description: item.response,
                status:      'done',
                priority:    'low',
                created_at:  new Date()
            }
        });
    }
}

// ─── Создание Wo_Users записи для бота ───────────────────────────────────────
// Нужна чтобы бот появлялся в поиске и мог отправлять private_message (с кнопками)

async function createBotUser(ctx, botId, username, displayName) {
    try {
        const now = Math.floor(Date.now() / 1000);
        const [botUser] = await ctx.wo_users.findOrCreate({
            where: { username: username.toLowerCase() },
            defaults: {
                email:           `${username.toLowerCase()}@bots.internal`,
                password:        crypto.randomBytes(20).toString('hex'),
                first_name:      displayName,
                last_name:       '',
                about:           '',
                type:            'bot',
                active:          '1',
                verified:        '0',
                lastseen:        now,
                registered:      new Date().toLocaleDateString('en-US'),
                joined:          now,
                message_privacy: '0'
            }
        });
        // Sync linked_user_id so PrivateMessageController can find the bot
        await ctx.wo_bots.update(
            { linked_user_id: botUser.user_id },
            { where: { bot_id: botId } }
        );
        console.log(`[WallyBot] Bot @${username} linked to Wo_Users.user_id=${botUser.user_id}`);
        return botUser.user_id;
    } catch (e) {
        console.warn('[WallyBot] createBotUser failed:', e.message);
        return null;
    }
}

// ─── Отправка сообщения от произвольного бота пользователю ───────────────────
// Используется дефолтным шеллом. Отправляет через private_message (так же как WallyBot)
// чтобы reply_markup отображался в Android.

async function sendFromBot(ctx, io, botLinkedUserId, botId, botName, targetUserId, text, replyMarkup) {
    try {
        const now = Math.floor(Date.now() / 1000);

        // Сохранить в Wo_Bot_Messages
        const botMsg = await ctx.wo_bot_messages.create({
            bot_id:       botId,
            chat_id:      String(targetUserId),
            chat_type:    'private',
            direction:    'outgoing',
            text,
            reply_markup: replyMarkup ? JSON.stringify(replyMarkup) : null,
            processed:    1,
            processed_at: new Date()
        });

        // Если есть Wo_Users запись — отправляем через private_message (Android отобразит кнопки)
        if (botLinkedUserId && io) {
            try {
                const woMsg = await ctx.wo_messages.create({
                    from_id: botLinkedUserId,
                    to_id:   targetUserId,
                    text:    text,
                    seen:    0,
                    time:    now
                });
                io.to(String(targetUserId)).emit('private_message', {
                    status:            200,
                    id:                woMsg.id,
                    from_id:           botLinkedUserId,
                    to_id:             targetUserId,
                    text,
                    message:           text,
                    message_id:        woMsg.id,
                    time:              now,
                    time_api:          now,
                    messages_html:     '',
                    message_page_html: '',
                    username:          botName,
                    avatar:            'upload/photos/d-avatar.jpg',
                    receiver:          targetUserId,
                    sender:            botLinkedUserId,
                    cipher_version:    1,
                    media:             '',
                    isMedia:           false,
                    isRecord:          false,
                    reply_markup:      replyMarkup || null,
                    bot_id:            botId
                });
            } catch (msgErr) {
                console.warn('[BotShell/Wo_Messages]', msgErr.message);
            }
        }

        // Также emit bot_message для совместимости
        if (io) {
            io.to(String(targetUserId)).emit('bot_message', {
                event:        'bot_message',
                bot_id:       botId,
                message_id:   botMsg.id,
                text,
                reply_markup: replyMarkup || null,
                timestamp:    Date.now()
            });
        }

        await ctx.wo_bots.increment('messages_sent', { where: { bot_id: botId } });
    } catch (e) {
        console.error('[BotShell/send]', e.message);
    }
}

// ─── Вспомогательные функции управления ботами ───────────────────────────────

async function getUserBots(ctx, userId) {
    return ctx.wo_bots.findAll({
        where:      { owner_id: userId },
        attributes: ['bot_id', 'username', 'display_name', 'status', 'total_users', 'created_at'],
        order:      [['created_at', 'DESC']],
        limit:      20,
        raw:        true
    });
}

async function registerDefaultCommands(ctx, botId) {
    const defaults = [
        { command: 'start',     description: 'Начать работу с ботом',               sort_order: 0 },
        { command: 'help',      description: 'Помощь и список команд',               sort_order: 1 },
        { command: 'guide',     description: 'Гид по WallyMates — что где найти',    sort_order: 2 },
        { command: 'messenger', description: 'Справка по функциям мессенджера',      sort_order: 3 },
        { command: 'cancel',    description: 'Отменить действие',                    sort_order: 4 }
    ];
    for (const cmd of defaults) {
        await ctx.wo_bot_commands.findOrCreate({
            where:    { bot_id: botId, command: cmd.command },
            defaults: { bot_id: botId, ...cmd, scope: 'all', is_hidden: 0 }
        });
    }
}

// ─── ОБРАБОТЧИКИ КОМАНД ───────────────────────────────────────────────────────

// ─── Personality helpers ──────────────────────────────────────────────────────

const GREETINGS = [
    (name) => `Привет, ${name}! 👋 Я WallyBot — твой личный помощник в WorldMates.\n\nЧем могу помочь сегодня?`,
    (name) => `Приветствую, ${name}! ✨ Рад тебя видеть!\n\nГотов помочь с ботами и ответить на вопросы.`,
    (name) => `Привет, ${name}! 🤖 Я WallyBot — создаю ботов, отвечаю на вопросы и помогаю разобраться в WorldMates.\n\nЧто сделаем?`,
    (name) => `Привет, ${name}! 🌟 WallyBot на связи!\n\nЕсли нужна помощь — я здесь.`,
];

function randomGreeting(name) {
    return GREETINGS[Math.floor(Math.random() * GREETINGS.length)](name);
}

// Simple language detection: returns 'uk' if more Ukrainian-specific characters, else 'ru'
function detectLang(text = '') {
    const ukChars = (text.match(/[іїєґІЇЄҐ]/g) || []).length;
    return ukChars > 0 ? 'uk' : 'ru';
}

async function handleStart(ctx, io, userId, userName) {
    clearState(userId);

    // Первый раз — показываем онбординг
    const firstTime = await isNewUser(ctx, userId);
    if (firstTime) {
        const onboarding =
            `👋 Привет, ${userName}! Я *WallyBot* — помощник WorldMates.\n\n` +
            `Я помогу тебе:\n` +
            `🤖 Создавать ботов без кода\n` +
            `🧠 Настраивать автоответы\n` +
            `📢 Делать рассылки подписчикам\n` +
            `❓ Разобраться в функциях мессенджера\n\n` +
            `С чего начнём?`;
        return sendToUser(ctx, io, userId, onboarding, inlineKeyboard([
            btn('📖 Гид по приложению',    'cmd_guide'),
            btn('📖 Краткий тур (3 шага)', 'tour_step_1'),
            btn('🛠 Создать бота сразу',   'cmd_newbot'),
            btn('📋 Шаблоны ботов',        'cmd_templates'),
            btn('❓ Задать вопрос',         'cmd_ask')
        ], 1));
    }

    const text = randomGreeting(userName);
    const kb = inlineKeyboard([
        btn('📖 Гид по приложению',  'cmd_guide'),
        btn('🛠 Создать бота',        'cmd_newbot'),
        btn('📋 Шаблоны',            'cmd_templates'),
        btn('📦 Мои боты',           'cmd_mybots'),
        btn('🔍 Диагностика',        'cmd_checkbot'),
        btn('📰 RSS-ленты',          'cmd_rss'),
        btn('🧠 Обучить меня',       'cmd_learn'),
        btn('❓ Спросить WallyBot',  'cmd_ask'),
        btn('📊 Статистика',         'cmd_stats'),
        btn('🔍 Функции мессенджера','cmd_messenger_guide'),
        btn('💡 Помощь',             'cmd_help')
    ], 2);
    await sendToUser(ctx, io, userId, text, kb);
}

async function handleHelp(ctx, io, userId) {
    clearState(userId);
    const text =
        `*Команды WallyBot:*\n\n` +
        `🤖 *Управление ботами:*\n` +
        `/newbot — создать нового бота\n` +
        `/mybots — список твоих ботов\n` +
        `/editbot — изменить настройки бота\n` +
        `/deletebot — удалить бота\n` +
        `/token — токен бота\n` +
        `/setcommands — команды бота\n` +
        `/setdesc — описание бота\n\n` +
        `🧠 *База знаний:*\n` +
        `/learn — научить меня новому\n` +
        `/forget — забыть ответ\n` +
        `/ask — задать вопрос\n` +
        `/messenger — справка по мессенджеру\n\n` +
        `📖 */guide — гид по WallyMates* (что где найти)\n` +
        `📱 */about — описание приложения* и навигация\n\n` +
        `📊 /stats — статистика ботов\n` +
        `/checkbot — диагностика бота (@username или выбор из списка)\n` +
        `/rss — управление RSS-лентами\n\n` +
        `💬 Просто напиши вопрос — постараюсь ответить!`;
    await sendToUser(ctx, io, userId, text, inlineKeyboard([
        btn('📖 Гид по приложению', 'cmd_guide'),
        btn('🛠 Создать бота',      'cmd_newbot'),
        btn('📦 Мои боты',          'cmd_mybots'),
        btn('🔍 Диагностика',       'cmd_checkbot'),
        btn('📰 RSS',               'cmd_rss'),
        btn('📖 Главное меню',      'cmd_start')
    ], 2));
}

async function handleNewBot(ctx, io, userId) {
    clearState(userId);
    // Предлагаем шаблоны как более простой путь
    const templates = Object.values(BOT_TEMPLATES);
    const text =
        `🛠 *Создание нового бота*\n\n` +
        `Можно начать с готового шаблона (быстрее и проще) или создать бота с нуля.\n\n` +
        `*Доступные шаблоны:*\n` +
        templates.map(t => `${t.icon} *${t.name}* — ${t.description}`).join('\n');
    await sendToUser(ctx, io, userId, text, inlineKeyboard([
        ...templates.map(t => btn(`${t.icon} ${t.name}`, `template_select_${t.id}`)),
        btn('✏️ С нуля (без шаблона)', 'newbot_blank')
    ], 1));
}

async function handleNewBotBlank(ctx, io, userId) {
    setState(userId, STATES.NEWBOT_NAME, {});
    await sendToUser(ctx, io, userId,
        `Создаём нового бота!\n\nШаг 1/3: Введи отображаемое имя бота (например: "Мой Помощник", "WeatherBot"):`
    );
}

async function handleMyBots(ctx, io, userId) {
    clearState(userId);
    const bots = await getUserBots(ctx, userId);

    if (!bots.length) {
        const kb = inlineKeyboard([btn('Создать первого бота', 'cmd_newbot')]);
        return sendToUser(ctx, io, userId, 'У тебя пока нет ботов.\nСоздай своего первого бота!', kb);
    }

    let text = `*Твои боты (${bots.length}):*\n\n`;
    const buttons = [];
    for (const bot of bots) {
        const statusEmoji = bot.status === 'active' ? '🟢' : '🔴';
        text += `${statusEmoji} @${bot.username} — ${bot.display_name}\n`;
        text += `   Пользователей: ${bot.total_users}\n\n`;
        buttons.push(btn(`@${bot.username}`, `bot_info_${bot.bot_id}`));
    }

    buttons.push(btn('Создать ещё', 'cmd_newbot'));
    const kb = inlineKeyboard(buttons, 2);
    await sendToUser(ctx, io, userId, text, kb);
}

async function handleEditBot(ctx, io, userId) {
    const bots = await getUserBots(ctx, userId);
    if (!bots.length) {
        return sendToUser(ctx, io, userId, 'У тебя нет ботов для редактирования.');
    }

    setState(userId, STATES.EDITBOT_SELECT, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `editselect_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери бота для редактирования:', inlineKeyboard(buttons));
}

async function handleDeleteBot(ctx, io, userId) {
    const bots = await getUserBots(ctx, userId);
    if (!bots.length) return sendToUser(ctx, io, userId, 'У тебя нет ботов для удаления.');

    setState(userId, STATES.DELETEBOT_CONFIRM, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `deleteselect_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери бота для удаления:', inlineKeyboard(buttons));
}

async function handleToken(ctx, io, userId) {
    const bots = await getUserBots(ctx, userId);
    if (!bots.length) return sendToUser(ctx, io, userId, 'У тебя нет ботов.');

    setState(userId, STATES.TOKEN_SELECT, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `tokenshow_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери бота чтобы получить токен:', inlineKeyboard(buttons));
}

async function handleSetCommands(ctx, io, userId) {
    const bots = await getUserBots(ctx, userId);
    if (!bots.length) return sendToUser(ctx, io, userId, 'У тебя нет ботов.');

    setState(userId, STATES.SETCMD_SELECT, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `setcmd_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери бота для установки команд:', inlineKeyboard(buttons));
}

async function handleSetDesc(ctx, io, userId) {
    const bots = await getUserBots(ctx, userId);
    if (!bots.length) return sendToUser(ctx, io, userId, 'У тебя нет ботов.');

    setState(userId, STATES.SETDESC_SELECT, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `setdesc_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери бота для изменения описания:', inlineKeyboard(buttons));
}

async function handleLearn(ctx, io, userId) {
    setState(userId, STATES.LEARN_KEYWORD, {});
    await sendToUser(ctx, io, userId,
        `*Обучение WallyBot*\n\nШаг 1/2: Введи ключевое слово или фразу, по которой меня будут искать:\n_(например: "погода", "как зарегистрироваться", "контакт поддержки")_`
    );
}

async function handleForget(ctx, io, userId) {
    const list = await getKnowledgeList(ctx, 15);
    if (!list.length) {
        return sendToUser(ctx, io, userId, 'База знаний пуста. Сначала научи меня чему-нибудь через /learn');
    }

    setState(userId, STATES.FORGET_SELECT, {});
    const buttons = list.map(item => btn(item.title.substring(0, 30), `forget_${item.title}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    await sendToUser(ctx, io, userId, 'Выбери запись для удаления:', inlineKeyboard(buttons, 1));
}

async function handleAsk(ctx, io, userId) {
    setState(userId, STATES.IDLE, {});
    await sendToUser(ctx, io, userId, 'Задай мне любой вопрос — и я поищу ответ в базе знаний!');
}

async function handleMessengerGuide(ctx, io, userId) {
    clearState(userId);
    const text = `*Справка по мессенджеру*\n\n` +
        `Я могу отвечать по функциям WorldMates. Примеры вопросов:\n` +
        `• Как найти бота в мессенджере?\n` +
        `• Как создать своего бота?\n` +
        `• Как добавить бота в группу?\n` +
        `• Как заблокировать бота?\n` +
        `• Как использовать команды бота?\n\n` +
        `Просто напиши вопрос обычным текстом — я постараюсь подобрать ответ.`;

    return sendToUser(ctx, io, userId, text, inlineKeyboard([
        btn('Задать вопрос', 'cmd_ask'),
        btn('Помощь', 'cmd_help')
    ]));
}

// ─── /guide — Интерактивный гид по приложению WallyMates ─────────────────────
// Двуязычный (RU/UK): меню по разделам + подробное описание каждого раздела.

const GUIDE_SECTIONS = {
    basics: {
        icon: '📱',
        title_ru: 'Основы мессенджера',
        title_uk: 'Основи месенджера',
        text_ru:
            `📱 *Основы WallyMates*\n\n` +
            `WallyMates — мессенджер с чатами, группами, звонками, ботами и сторис.\n\n` +
            `*Навигация (вкладки внизу экрана):*\n` +
            `💬 *Чаты* — все личные переписки и боты\n` +
            `👥 *Группы* — групповые чаты и каналы\n` +
            `📞 *Звонки* — история и новые звонки\n` +
            `📖 *Истории* — сторис контактов\n` +
            `⚙️ *Настройки* — профиль, безопасность, язык\n\n` +
            `*Как начать:*\n` +
            `1. Зарегистрируйся или войди в аккаунт\n` +
            `2. Найди контакт через поиск 🔍\n` +
            `3. Напиши первое сообщение!\n\n` +
            `📌 Значок карандаша ✏️ (вверху справа) — создать новый чат/группу/канал.`,
        text_uk:
            `📱 *Основи WallyMates*\n\n` +
            `WallyMates — месенджер з чатами, групами, дзвінками, ботами та сторіс.\n\n` +
            `*Навігація (вкладки внизу екрану):*\n` +
            `💬 *Чати* — усі особисті листування та боти\n` +
            `👥 *Групи* — групові чати та канали\n` +
            `📞 *Дзвінки* — історія та нові дзвінки\n` +
            `📖 *Сторіс* — сторіс контактів\n` +
            `⚙️ *Налаштування* — профіль, безпека, мова\n\n` +
            `*Як розпочати:*\n` +
            `1. Зареєструйся або увійди в акаунт\n` +
            `2. Знайди контакт через пошук 🔍\n` +
            `3. Напиши перше повідомлення!\n\n` +
            `📌 Значок олівця ✏️ (вгорі праворуч) — створити новий чат/групу/канал.`,
    },
    chats: {
        icon: '💬',
        title_ru: 'Чаты и сообщения',
        title_uk: 'Чати та повідомлення',
        text_ru:
            `💬 *Чаты и сообщения*\n\n` +
            `*Отправка сообщений:*\n` +
            `• Текст — напиши в поле ввода и нажми ➤\n` +
            `• Голосовое — удерживай 🎤 и говори\n` +
            `• Видеосообщение — нажми кнопку 📹 рядом с микрофоном\n` +
            `• Медиафайл — нажми 📎 для фото/видео/файлов\n` +
            `• Стикеры и GIF — кнопка 😊 → вкладки\n\n` +
            `*Работа с сообщениями (зажми сообщение):*\n` +
            `↩️ Ответить на сообщение\n` +
            `✏️ Редактировать (свои, до 48ч)\n` +
            `🗑️ Удалить (у себя / у обоих)\n` +
            `↗️ Переслать в другой чат\n` +
            `😊 Добавить реакцию\n` +
            `📌 Закрепить (в группах — только админ)\n\n` +
            `*Поиск в чате:* нажми 🔍 вверху → введи текст.`,
        text_uk:
            `💬 *Чати та повідомлення*\n\n` +
            `*Надсилання повідомлень:*\n` +
            `• Текст — напиши у поле вводу і натисни ➤\n` +
            `• Голосове — утримуй 🎤 і говори\n` +
            `• Відеоповідомлення — кнопка 📹 поряд з мікрофоном\n` +
            `• Медіафайл — натисни 📎 для фото/відео/файлів\n` +
            `• Стикери та GIF — кнопка 😊 → вкладки\n\n` +
            `*Робота з повідомленнями (затисни повідомлення):*\n` +
            `↩️ Відповісти на повідомлення\n` +
            `✏️ Редагувати (свої, до 48г)\n` +
            `🗑️ Видалити (у себе / у обох)\n` +
            `↗️ Переслати в інший чат\n` +
            `😊 Додати реакцію\n` +
            `📌 Закріпити (в групах — лише адмін)\n\n` +
            `*Пошук у чаті:* натисни 🔍 вгорі → введи текст.`,
    },
    groups: {
        icon: '👥',
        title_ru: 'Группы и каналы',
        title_uk: 'Групи та канали',
        text_ru:
            `👥 *Группы и каналы*\n\n` +
            `*Группа* — все участники могут переписываться.\n` +
            `*Канал* — только администраторы публикуют контент.\n\n` +
            `*Создать:* нажми ✏️ → "Новая группа" / "Новый канал"\n\n` +
            `*Публичные vs Приватные:*\n` +
            `🌐 Публичные — найти через поиск по @username\n` +
            `🔒 Приватные — только по ссылке-приглашению\n\n` +
            `*Роли в группе:*\n` +
            `👑 Создатель — полный контроль\n` +
            `🛡️ Администратор — управление участниками\n` +
            `👤 Участник — обычные права\n\n` +
            `*Темы (Topics):* в больших группах можно создавать подтемы — как форум внутри чата.\n\n` +
            `*Как добавить участника:* настройки группы → "Добавить участника" → найди через поиск.`,
        text_uk:
            `👥 *Групи та канали*\n\n` +
            `*Група* — всі учасники можуть переписуватись.\n` +
            `*Канал* — лише адміністратори публікують контент.\n\n` +
            `*Створити:* натисни ✏️ → "Нова група" / "Новий канал"\n\n` +
            `*Публічні vs Приватні:*\n` +
            `🌐 Публічні — знайти через пошук по @username\n` +
            `🔒 Приватні — лише за посиланням-запрошенням\n\n` +
            `*Ролі в групі:*\n` +
            `👑 Творець — повний контроль\n` +
            `🛡️ Адміністратор — керування учасниками\n` +
            `👤 Учасник — звичайні права\n\n` +
            `*Теми (Topics):* у великих групах можна створювати підтеми — як форум всередині чату.\n\n` +
            `*Як додати учасника:* налаштування групи → "Додати учасника" → знайди через пошук.`,
    },
    bots: {
        icon: '🤖',
        title_ru: 'Боты и WallyBot',
        title_uk: 'Боти та WallyBot',
        text_ru:
            `🤖 *Боты в WallyMates*\n\n` +
            `Боты — автоматические помощники в чате.\n\n` +
            `*Где найти:* вкладка Чаты → иконка робота 🤖 (Bot Store)\n\n` +
            `*Как запустить бота:*\n` +
            `1. Открой чат с ботом из Bot Store\n` +
            `2. Нажми кнопку *НАЧАТЬ* внизу экрана\n` +
            `3. Бот отправит /start — можешь писать!\n\n` +
            `*Что умеют боты:*\n` +
            `• Отвечают на FAQ и вопросы\n` +
            `• Создают опросы и голосования\n` +
            `• Делают рассылки подписчикам\n` +
            `• Принимают заявки и обращения\n` +
            `• Показывают RSS-новости\n\n` +
            `*WallyBot* (этот бот) — управляй своими ботами:\n` +
            `/newbot — создать бота\n` +
            `/mybots — мои боты\n` +
            `/learn — обучить WallyBot\n` +
            `/messenger — справка по мессенджеру`,
        text_uk:
            `🤖 *Боти у WallyMates*\n\n` +
            `Боти — автоматичні помічники у чаті.\n\n` +
            `*Де знайти:* вкладка Чати → іконка робота 🤖 (Bot Store)\n\n` +
            `*Як запустити бота:*\n` +
            `1. Відкрий чат з ботом з Bot Store\n` +
            `2. Натисни кнопку *ПОЧАТИ* внизу екрана\n` +
            `3. Бот надішле /start — можеш писати!\n\n` +
            `*Що вміють боти:*\n` +
            `• Відповідають на FAQ та питання\n` +
            `• Створюють опитування та голосування\n` +
            `• Роблять розсилки передплатникам\n` +
            `• Приймають заявки та звернення\n` +
            `• Показують RSS-новини\n\n` +
            `*WallyBot* (цей бот) — керуй своїми ботами:\n` +
            `/newbot — створити бота\n` +
            `/mybots — мої боти\n` +
            `/learn — навчити WallyBot\n` +
            `/messenger — довідка по месенджеру`,
    },
    calls: {
        icon: '📞',
        title_ru: 'Звонки',
        title_uk: 'Дзвінки',
        text_ru:
            `📞 *Звонки в WallyMates*\n\n` +
            `*Как позвонить:*\n` +
            `• Открой чат → нажми ☎️ (аудио) или 📹 (видео) вверху\n` +
            `• Или перейди на вкладку *Звонки* → нажми ✏️\n\n` +
            `*Типы звонков:*\n` +
            `🎙️ Аудиозвонок — голос без видео\n` +
            `📹 Видеозвонок — с камерой\n` +
            `👥 Групповой звонок — в групповом чате нажми ☎️\n\n` +
            `*Во время звонка:*\n` +
            `🔇 Выключить микрофон\n` +
            `📷 Включить/выключить камеру\n` +
            `🔊 Переключить на динамик\n` +
            `📲 Перевести в фоновый режим\n\n` +
            `*История звонков:* вкладка Звонки → видишь все входящие/исходящие.`,
        text_uk:
            `📞 *Дзвінки у WallyMates*\n\n` +
            `*Як подзвонити:*\n` +
            `• Відкрий чат → натисни ☎️ (аудіо) або 📹 (відео) вгорі\n` +
            `• Або перейди на вкладку *Дзвінки* → натисни ✏️\n\n` +
            `*Типи дзвінків:*\n` +
            `🎙️ Аудіодзвінок — голос без відео\n` +
            `📹 Відеодзвінок — з камерою\n` +
            `👥 Груповий дзвінок — у груповому чаті натисни ☎️\n\n` +
            `*Під час дзвінка:*\n` +
            `🔇 Вимкнути мікрофон\n` +
            `📷 Увімкнути/вимкнути камеру\n` +
            `🔊 Переключити на динамік\n` +
            `📲 Перевести у фоновий режим\n\n` +
            `*Історія дзвінків:* вкладка Дзвінки → бачиш усі вхідні/вихідні.`,
    },
    settings: {
        icon: '⚙️',
        title_ru: 'Настройки и профиль',
        title_uk: 'Налаштування та профіль',
        text_ru:
            `⚙️ *Настройки WallyMates*\n\n` +
            `*Где найти:* вкладка ⚙️ Настройки (внизу)\n\n` +
            `*Профиль:*\n` +
            `• Нажми на аватар → Редактировать профиль\n` +
            `• Изменить имя, фото, username, статус\n\n` +
            `*Конфиденциальность:*\n` +
            `🔒 Кто видит статус онлайн / фото профиля\n` +
            `📞 Кто может звонить тебе\n` +
            `🚫 Заблокированные пользователи\n` +
            `📱 Номер телефона: скрыть от всех\n\n` +
            `*Уведомления:*\n` +
            `🔔 Настроить звук, вибрацию, превью\n` +
            `💤 "Не беспокоить" для конкретных чатов\n\n` +
            `*Язык интерфейса:*\n` +
            `Настройки → Язык → выбери Украинский / Русский / Английский\n\n` +
            `*Безопасность:*\n` +
            `🔑 Смена пароля\n` +
            `📱 Активные сессии\n` +
            `🗑️ Удаление аккаунта`,
        text_uk:
            `⚙️ *Налаштування WallyMates*\n\n` +
            `*Де знайти:* вкладка ⚙️ Налаштування (внизу)\n\n` +
            `*Профіль:*\n` +
            `• Натисни на аватар → Редагувати профіль\n` +
            `• Змінити ім'я, фото, username, статус\n\n` +
            `*Конфіденційність:*\n` +
            `🔒 Хто бачить статус онлайн / фото профілю\n` +
            `📞 Хто може телефонувати тобі\n` +
            `🚫 Заблоковані користувачі\n` +
            `📱 Номер телефону: приховати від усіх\n\n` +
            `*Сповіщення:*\n` +
            `🔔 Налаштувати звук, вібрацію, превью\n` +
            `💤 "Не турбувати" для конкретних чатів\n\n` +
            `*Мова інтерфейсу:*\n` +
            `Налаштування → Мова → обери Українська / Русский / Англійська\n\n` +
            `*Безпека:*\n` +
            `🔑 Зміна пароля\n` +
            `📱 Активні сесії\n` +
            `🗑️ Видалення акаунту`,
    },
    search: {
        icon: '🔍',
        title_ru: 'Поиск',
        title_uk: 'Пошук',
        text_ru:
            `🔍 *Поиск в WallyMates*\n\n` +
            `*Глобальный поиск:*\n` +
            `• Нажми 🔍 вверху вкладки Чаты\n` +
            `• Ищи по имени, @username или ключевому слову\n` +
            `• Результаты: контакты, группы, каналы, боты\n\n` +
            `*Поиск в чате:*\n` +
            `• Открой чат → нажми 🔍 вверху\n` +
            `• Введи слово — найдёт все совпадения в истории\n\n` +
            `*Поиск бота:*\n` +
            `• Вкладка Чаты → иконка 🤖 Bot Store\n` +
            `• Или в поиске введи @username бота\n\n` +
            `*Поиск сообщения:*\n` +
            `• Зажми сообщение → "Найти похожие"\n` +
            `• Или используй встроенный поиск в чате\n\n` +
            `💡 *Совет:* если не можешь найти контакт — убедись что знаешь точный @username.`,
        text_uk:
            `🔍 *Пошук у WallyMates*\n\n` +
            `*Глобальний пошук:*\n` +
            `• Натисни 🔍 вгорі вкладки Чати\n` +
            `• Шукай за іменем, @username або ключовим словом\n` +
            `• Результати: контакти, групи, канали, боти\n\n` +
            `*Пошук у чаті:*\n` +
            `• Відкрий чат → натисни 🔍 вгорі\n` +
            `• Введи слово — знайде всі збіги в історії\n\n` +
            `*Пошук бота:*\n` +
            `• Вкладка Чати → іконка 🤖 Bot Store\n` +
            `• Або у пошуку введи @username бота\n\n` +
            `*Пошук повідомлення:*\n` +
            `• Затисни повідомлення → "Знайти схожі"\n` +
            `• Або використовуй вбудований пошук у чаті\n\n` +
            `💡 *Порада:* якщо не можеш знайти контакт — переконайся що знаєш точний @username.`,
    },
};

async function handleGuide(ctx, io, userId) {
    clearState(userId);
    const lang = detectLang('');  // default RU for menu; sections auto-detect via user input lang

    const menuText =
        `📖 *Гид по WallyMates*\n\n` +
        `Выбери раздел — я расскажу как всё работает и где найти нужную функцию:\n\n` +
        Object.values(GUIDE_SECTIONS)
            .map(s => `${s.icon} ${s.title_ru}`)
            .join('\n');

    const kb = inlineKeyboard([
        ...Object.entries(GUIDE_SECTIONS).map(([key, s]) =>
            btn(`${s.icon} ${s.title_ru}`, `guide_section_${key}`)
        ),
        btn('🔍 Задать вопрос', 'cmd_ask'),
        btn('📖 Главное меню',  'cmd_start')
    ], 2);

    await sendToUser(ctx, io, userId, menuText, kb);
}

async function handleGuideSection(ctx, io, userId, sectionKey) {
    const section = GUIDE_SECTIONS[sectionKey];
    if (!section) {
        return sendToUser(ctx, io, userId, 'Раздел не найден. /guide — выбрать раздел.',
            inlineKeyboard([btn('📖 Назад к гиду', 'cmd_guide')])
        );
    }

    // Detect language from recent interaction (use RU as default for guide sections)
    const text = section.text_ru;
    const kb = inlineKeyboard([
        btn(`🇺🇦 Українська`, `guide_uk_${sectionKey}`),
        btn('📖 Все разделы', 'cmd_guide'),
        btn('❓ Задать вопрос', 'cmd_ask'),
        btn('🏠 Главное меню', 'cmd_start')
    ], 2);

    await sendToUser(ctx, io, userId, text, kb);
}

async function handleGuideSectionUk(ctx, io, userId, sectionKey) {
    const section = GUIDE_SECTIONS[sectionKey];
    if (!section) {
        return sendToUser(ctx, io, userId, 'Розділ не знайдено. /guide — обрати розділ.',
            inlineKeyboard([btn('📖 Назад до гіду', 'cmd_guide')])
        );
    }

    const text = section.text_uk;
    const kb = inlineKeyboard([
        btn(`🇷🇺 Русский`, `guide_section_${sectionKey}`),
        btn('📖 Всі розділи', 'cmd_guide'),
        btn('❓ Запитати', 'cmd_ask'),
        btn('🏠 Головне меню', 'cmd_start')
    ], 2);

    await sendToUser(ctx, io, userId, text, kb);
}

// ─── /about — описание приложения WallyMates и навигация ─────────────────────

async function handleAbout(ctx, io, userId) {
    clearState(userId);
    const text =
        `📱 *WallyMates — мессенджер нового поколения*\n\n` +
        `WallyMates — это защищённый мессенджер с богатым функционалом:\n` +
        `сквозное шифрование, боты, группы, каналы, звонки, сторис и мини-приложения.\n\n` +

        `*📌 Где что находится:*\n\n` +

        `💬 *Чаты* (вкладка 1)\n` +
        `   Личные переписки, боты, сохранённые сообщения.\n` +
        `   Иконка 🤖 — Bot Store (найти/запустить бота)\n\n` +

        `👥 *Группы* (вкладка 2)\n` +
        `   Групповые чаты и публичные каналы.\n` +
        `   Кнопка ✏️ — создать группу или канал\n\n` +

        `📞 *Звонки* (вкладка 3)\n` +
        `   Аудио и видео звонки, история вызовов.\n` +
        `   Поддержка групповых звонков\n\n` +

        `📖 *Истории* (вкладка 4)\n` +
        `   Сторис контактов, создай свою через кнопку +\n\n` +

        `⚙️ *Настройки* (вкладка 5)\n` +
        `   Профиль, темы, язык, конфиденциальность,\n` +
        `   уведомления, безопасность, WorldMates PRO\n\n` +

        `*🔧 Быстрые действия:*\n` +
        `🔍 Поиск — иконка лупы в Чатах\n` +
        `✏️ Новый чат/группа — иконка карандаша\n` +
        `📎 Отправить файл/фото — в поле ввода\n` +
        `🎤 Голосовое — удержи иконку микрофона\n\n` +

        `Отправь /guide для подробного интерактивного гида по разделам.`;

    await sendToUser(ctx, io, userId, text, inlineKeyboard([
        btn('📖 Интерактивный гид', 'cmd_guide'),
        btn('💬 Чаты и сообщения',  'guide_section_chats'),
        btn('🤖 Боты',              'guide_section_bots'),
        btn('⚙️ Настройки',        'guide_section_settings'),
        btn('🏠 Главное меню',      'cmd_start')
    ], 2));
}

async function handleCancel(ctx, io, userId) {
    clearState(userId);
    await sendToUser(ctx, io, userId, 'Хорошо, отменено! ✌️ Чем ещё могу помочь?',
        inlineKeyboard([
            btn('Создать бота', 'cmd_newbot'),
            btn('Мои боты',     'cmd_mybots'),
            btn('Помощь',       'cmd_help')
        ])
    );
}

async function handleStats(ctx, io, userId) {
    clearState(userId);
    try {
        const [totalBots, activeBots, totalMessages, kbCount] = await Promise.all([
            ctx.wo_bots.count({ where: {} }),
            ctx.wo_bots.count({ where: { status: 'active' } }),
            ctx.wo_bots.sum('messages_sent') || 0,
            ctx.wo_bot_tasks.count({ where: { bot_id: WALLYBOT_ID, status: 'done' } }),
        ]);
        const text =
            `📊 *Статистика WorldMates Bots*\n\n` +
            `🤖 Всего ботов: *${totalBots}*\n` +
            `🟢 Активных: *${activeBots}*\n` +
            `💬 Отправлено сообщений: *${totalMessages || 0}*\n` +
            `🧠 Фактов в базе знаний WallyBot: *${kbCount}*\n\n` +
            `Хочешь стать частью экосистемы? Создай своего бота! 🚀`;
        await sendToUser(ctx, io, userId, text,
            inlineKeyboard([btn('Создать бота', 'cmd_newbot'), btn('Мои боты', 'cmd_mybots')])
        );
    } catch (e) {
        await sendToUser(ctx, io, userId, 'Не удалось получить статистику 😔 Попробуй позже.');
    }
}

// Easter egg: /who or free-text "хто ти / кто ты / who are you"
async function handleWho(ctx, io, userId) {
    const lines = [
        '🤖 Я WallyBot — умный бот-менеджер WorldMates!\n\nСоздан, чтобы помогать тебе строить собственных ботов и отвечать на вопросы. Немного знаю о мессенджере, немного — о вселенной. 😄',
        '👾 Я WallyBot! Часть команды WorldMates.\n\nМоя миссия: сделать ботов доступными каждому. Спрашивай — отвечу!',
        '🌟 WallyBot к твоим услугам!\n\nРаботаю 24/7, не ем, не сплю — только помогаю. Иногда шучу. 😄',
    ];
    await sendToUser(ctx, io, userId, lines[Math.floor(Math.random() * lines.length)]);
}

// "Thank you" handler
async function handleThanks(ctx, io, userId) {
    const replies = [
        'Пожалуйста! 😊 Всегда рад помочь.',
        'Не за что! Если снова что-то понадобится — пиши. 🤝',
        'На здоровье! 🌟 Удачи с ботами!',
        'Пожалуйста! Если что — я рядом. 👋',
    ];
    await sendToUser(ctx, io, userId, replies[Math.floor(Math.random() * replies.length)]);
}

// ─── Онбординг / тур ─────────────────────────────────────────────────────────

const TOUR_STEPS = [
    {
        text:
            `🤖 *Добро пожаловать в WallyBot!* (1/3)\n\n` +
            `Я твой помощник в WorldMates. Вот что я умею:\n\n` +
            `✅ Создавать ботов за 3 шага — без кода\n` +
            `✅ Рассылать сообщения подписчикам\n` +
            `✅ Отвечать на вопросы о мессенджере\n` +
            `✅ Обучаться новым знаниям через /learn`,
        kb: () => inlineKeyboard([
            btn('Дальше →',      'tour_step_2'),
            btn('Пропустить тур', 'tour_skip')
        ])
    },
    {
        text:
            `🛠 *Создание ботов* (2/3)\n\n` +
            `Бот — это автоматический помощник в чате. Ты можешь:\n\n` +
            `• Подключить его к сайту или приложению\n` +
            `• Настроить автоответы на вопросы\n` +
            `• Рассылать новости подписчикам\n` +
            `• Принимать обращения в поддержку\n\n` +
            `Есть готовые *шаблоны* — запуск за 2 минуты!`,
        kb: () => inlineKeyboard([
            btn('← Назад',            'tour_step_1'),
            btn('Дальше →',           'tour_step_3'),
            btn('🛠 Создать бота',    'cmd_newbot')
        ])
    },
    {
        text:
            `🧠 *Умный помощник* (3/3)\n\n` +
            `Меня можно обучить отвечать на любые вопросы.\n\n` +
            `Используй /learn — добавь вопрос и ответ, и я буду помогать твоим пользователям.\n\n` +
            `Я уже знаю много о WorldMates — просто спроси!\n\n` +
            `Готов начать? 🚀`,
        kb: () => inlineKeyboard([
            btn('← Назад',           'tour_step_2'),
            btn('🛠 Создать бота',   'cmd_newbot'),
            btn('📋 Шаблоны',        'cmd_templates'),
            btn('📖 Главное меню',   'cmd_start')
        ])
    }
];

async function handleTour(ctx, io, userId, step = 1) {
    const idx  = Math.max(1, Math.min(step, TOUR_STEPS.length)) - 1;
    const tour = TOUR_STEPS[idx];
    await sendToUser(ctx, io, userId, tour.text, tour.kb());
    // Сохраняем шаг в FSM — позволяет навигацию текстом если кнопки не отображаются
    setState(userId, STATES.TOUR_STEP, { step: idx + 1 });
}

// ─── Шаблоны ботов ───────────────────────────────────────────────────────────

async function handleTemplates(ctx, io, userId) {
    clearState(userId);
    const templates = Object.values(BOT_TEMPLATES);
    const text =
        `📋 *Шаблоны ботов*\n\n` +
        `Выбери готовый шаблон — я создам бота с нужными командами и базой знаний. Тебе останется только задать имя и username.\n\n` +
        templates.map(t => `${t.icon} *${t.name}* — ${t.description}`).join('\n\n');

    const buttons = templates.map(t => btn(`${t.icon} ${t.name}`, `template_select_${t.id}`));
    buttons.push(btn('✏️ Создать с нуля', 'cmd_newbot'));
    await sendToUser(ctx, io, userId, text, inlineKeyboard(buttons, 1));
}

async function applyTemplate(ctx, io, userId, templateId, displayName, username) {
    const tpl = BOT_TEMPLATES[templateId];
    if (!tpl) return;

    const botCount = await ctx.wo_bots.count({ where: { owner_id: userId } });
    if (botCount >= 20) {
        clearState(userId);
        return sendToUser(ctx, io, userId, 'Достигнут лимит: максимум 20 ботов на аккаунт.');
    }

    const botId    = 'bot_' + crypto.randomBytes(12).toString('hex');
    const botToken = generateBotToken(botId);

    await ctx.wo_bots.create({
        bot_id:       botId,
        owner_id:     userId,
        bot_token:    botToken,
        username:     sanitize(username),
        display_name: sanitize(displayName),
        description:  tpl.default_desc,
        category:     'general',
        status:       'active',
        created_at:   new Date(),
        updated_at:   new Date()
    });

    // Регистрируем команды шаблона + дефолтные
    await registerDefaultCommands(ctx, botId);
    for (let i = 0; i < tpl.commands.length; i++) {
        const cmd = tpl.commands[i];
        await ctx.wo_bot_commands.findOrCreate({
            where:    { bot_id: botId, command: cmd.command },
            defaults: { bot_id: botId, ...cmd, scope: 'all', is_hidden: 0, sort_order: 10 + i }
        });
    }

    // Заполняем базу знаний из шаблона
    for (const item of tpl.knowledge) {
        await ctx.wo_bot_tasks.findOrCreate({
            where: { bot_id: botId, title: item.keyword },
            defaults: {
                bot_id:      botId,
                user_id:     userId,
                chat_id:     String(userId),
                title:       item.keyword,
                description: item.response,
                status:      'done',
                priority:    'low',
                created_at:  new Date()
            }
        });
    }

    // Создаём Wo_Users запись для бота
    await createBotUser(ctx, botId, username, displayName);

    clearState(userId);
    console.log(`[WallyBot] Created bot @${username} from template "${tpl.id}" for user ${userId}`);

    const responseText =
        `${tpl.icon} *Бот создан по шаблону "${tpl.name}"!*\n\n` +
        `Имя: ${displayName}\n` +
        `Username: @${username}\n` +
        `Bot ID: \`${botId}\`\n\n` +
        `*Токен (СЕКРЕТНЫЙ, сохрани!):*\n\`${botToken}\`\n\n` +
        `✅ Команды и база знаний уже настроены по шаблону.\n` +
        `Используй /learn чтобы добавить свои ответы.`;

    return sendToUser(ctx, io, userId, responseText, inlineKeyboard([
        btn('📋 Мои боты',           'cmd_mybots'),
        btn('🔍 Диагностика',        `checkbot_${botId}`),
        btn('📰 RSS-лента',          `rss_${botId}`),
        btn('🧠 Добавить знания',    'cmd_learn'),
        btn('➕ Создать ещё',        'cmd_newbot')
    ], 2));
}

// ─── /setinline — перемикання inline-режиму бота ──────────────────────────────

async function handleSetInline(ctx, io, userId) {
    clearState(userId);
    const bots = await ctx.wo_bots.findAll({ where: { owner_id: userId, status: 'active' }, raw: true });
    if (!bots.length) {
        return sendToUser(ctx, io, userId,
            'У тебя ещё нет активных ботов.\nСоздай бота: /newbot',
            inlineKeyboard([btn('Создать бота', 'cmd_newbot')])
        );
    }
    setState(userId, STATES.SETINLINE_SELECT);
    const buttons = bots.map(b => btn(
        `@${b.username} [${b.supports_inline ? '✅ inline ON' : '❌ inline OFF'}]`,
        `setinline_${b.bot_id}`
    ));
    return sendToUser(ctx, io, userId,
        '🔀 *Inline-режим*\n\nВыбери бота, чтобы переключить inline:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── /setgroups — дозвіл/заборона входу в групи ────────────────────────────

async function handleSetGroups(ctx, io, userId) {
    clearState(userId);
    const bots = await ctx.wo_bots.findAll({ where: { owner_id: userId, status: 'active' }, raw: true });
    if (!bots.length) {
        return sendToUser(ctx, io, userId,
            'У тебя ещё нет активных ботов.\nСоздай бота: /newbot',
            inlineKeyboard([btn('Создать бота', 'cmd_newbot')])
        );
    }
    setState(userId, STATES.SETGROUPS_SELECT);
    const buttons = bots.map(b => btn(
        `@${b.username} [${b.can_join_groups ? '✅ группы ON' : '❌ группы OFF'}]`,
        `setgroups_${b.bot_id}`
    ));
    return sendToUser(ctx, io, userId,
        '👥 *Доступ к группам*\n\nВыбери бота, чтобы переключить режим:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── /revoke — відкликати токен бота ──────────────────────────────────────

async function handleRevoke(ctx, io, userId) {
    clearState(userId);
    const bots = await ctx.wo_bots.findAll({ where: { owner_id: userId }, raw: true });
    if (!bots.length) {
        return sendToUser(ctx, io, userId, 'У тебя нет ботов.');
    }
    setState(userId, STATES.REVOKE_SELECT);
    const buttons = bots.map(b => btn(`@${b.username}`, `revokeconfirm_${b.bot_id}`));
    return sendToUser(ctx, io, userId,
        '🔑 *Отозвать токен*\n\n⚠️ Старый токен станет недействительным сразу!\nВыбери бота:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── /broadcast — розсилка всім користувачам бота ─────────────────────────

async function handleBroadcast(ctx, io, userId) {
    clearState(userId);
    const bots = await ctx.wo_bots.findAll({ where: { owner_id: userId, status: 'active' }, raw: true });
    if (!bots.length) {
        return sendToUser(ctx, io, userId, 'У тебя нет активных ботов.');
    }
    setState(userId, STATES.BROADCAST_SELECT);
    const buttons = bots.map(b => {
        const userCount = b.total_users || 0;
        return btn(`@${b.username} (${userCount} польз.)`, `broadcast_${b.bot_id}`);
    });
    return sendToUser(ctx, io, userId,
        '📢 *Рассылка*\n\nВыбери бота для рассылки:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── /stats із підтримкою @botname ────────────────────────────────────────

async function handleBotStats(ctx, io, userId, botUsername) {
    // Per-bot stats
    const bot = await ctx.wo_bots.findOne({ where: { username: botUsername, owner_id: userId }, raw: true });
    if (!bot) {
        return sendToUser(ctx, io, userId,
            `Бот @${botUsername} не знайдений або не є твоїм.`,
            inlineKeyboard([btn('Мої боти', 'cmd_mybots')])
        );
    }

    const now = new Date();
    const oneDayAgo   = new Date(now - 86400 * 1000);
    const sevenDaysAgo = new Date(now - 7 * 86400 * 1000);

    const [totalUsers, activeDay, activeWeek, totalIn, totalOut] = await Promise.all([
        ctx.wo_bot_users.count({ where: { bot_id: bot.bot_id } }),
        ctx.wo_bot_users.count({ where: { bot_id: bot.bot_id, last_interaction_at: { [Op.gte]: oneDayAgo } } }),
        ctx.wo_bot_users.count({ where: { bot_id: bot.bot_id, last_interaction_at: { [Op.gte]: sevenDaysAgo } } }),
        ctx.wo_bot_messages.count({ where: { bot_id: bot.bot_id, direction: 'incoming' } }),
        ctx.wo_bot_messages.count({ where: { bot_id: bot.bot_id, direction: 'outgoing' } }),
    ]);

    const activePercent = totalUsers > 0 ? Math.round((activeDay / totalUsers) * 100) : 0;
    const bar = (pct) => {
        const filled = Math.round(pct / 10);
        return '█'.repeat(filled) + '░'.repeat(10 - filled) + ` ${pct}%`;
    };

    const text =
        `📊 *Статистика @${bot.username}*\n\n` +
        `👥 Пользователей: *${totalUsers}*\n` +
        `🟢 Активных (24h): *${activeDay}* ${bar(activePercent)}\n` +
        `📅 Активных (7d): *${activeWeek}*\n\n` +
        `📥 Получено сообщений: *${totalIn}*\n` +
        `📤 Отправлено сообщений: *${totalOut}*\n\n` +
        `🔗 Webhook: ${bot.webhook_enabled ? '✅ активен' : '❌ отключён'}\n` +
        `📌 Статус: *${bot.status}*`;

    return sendToUser(ctx, io, userId, text,
        inlineKeyboard([
            btn('Мои боты',    'cmd_mybots'),
            btn('Главное меню', 'cmd_start')
        ])
    );
}

// ─── ОБРАБОТКА СОСТОЯНИЙ ─────────────────────────────────────────────────────

async function processState(ctx, io, userId, text, currentState) {
    const { state, data } = currentState;

    // NEWBOT: шаг 1 — имя
    if (state === STATES.NEWBOT_NAME) {
        if (!text.trim()) return sendToUser(ctx, io, userId, 'Имя не может быть пустым. Попробуй снова:');
        setState(userId, STATES.NEWBOT_USERNAME, { display_name: text.trim() });
        return sendToUser(ctx, io, userId,
            `Отлично! Имя: *${text.trim()}*\n\n` +
            `Шаг 2/3: Введи username бота (только буквы, цифры, подчёркивание, должен заканчиваться на _bot):\n` +
            `Например: my_helper_bot, weather_check_bot`
        );
    }

    // NEWBOT: шаг 2 — username
    if (state === STATES.NEWBOT_USERNAME) {
        const username = text.trim().toLowerCase();
        if (!/^[a-zA-Z][a-zA-Z0-9_]{2,30}_bot$/.test(username)) {
            return sendToUser(ctx, io, userId,
                'Неверный формат! Username должен:\n' +
                '• Начинаться с буквы\n• Содержать только буквы, цифры, _\n' +
                '• Заканчиваться на _bot\n• Длина: 5-32 символа\n\nПопробуй снова:'
            );
        }

        const existing = await ctx.wo_bots.findOne({ where: { username } });
        if (existing) {
            return sendToUser(ctx, io, userId, `Username @${username} уже занят! Придумай другой:`);
        }

        setState(userId, STATES.NEWBOT_DESC, { ...data, username });
        return sendToUser(ctx, io, userId,
            `Username: *@${username}*\n\nШаг 3/3: Введи краткое описание бота (или /skip чтобы пропустить):`
        );
    }

    // NEWBOT: шаг 3 — описание
    if (state === STATES.NEWBOT_DESC) {
        const description = text === '/skip' ? '' : text.trim();
        const botCount = await ctx.wo_bots.count({ where: { owner_id: userId } });
        if (botCount >= 20) {
            clearState(userId);
            return sendToUser(ctx, io, userId, 'Достигнут лимит: максимум 20 ботов на аккаунт.');
        }

        const botId    = 'bot_' + crypto.randomBytes(12).toString('hex');
        const botToken = generateBotToken(botId);

        await ctx.wo_bots.create({
            bot_id:      botId,
            owner_id:    userId,
            bot_token:   botToken,
            username:    sanitize(data.username),
            display_name: sanitize(data.display_name),
            description: sanitize(description),
            category:    'general',
            status:      'active',
            created_at:  new Date(),
            updated_at:  new Date()
        });

        await registerDefaultCommands(ctx, botId);

        // Создаём Wo_Users запись для бота — нужна чтобы бот появлялся в поиске
        // и мог отправлять сообщения с inline-кнопками через private_message.
        await createBotUser(ctx, botId, data.username, data.display_name);

        clearState(userId);

        const responseText =
            `*Бот создан!*\n\n` +
            `Имя: ${data.display_name}\n` +
            `Username: @${data.username}\n` +
            `Bot ID: \`${botId}\`\n\n` +
            `*Токен (СЕКРЕТНЫЙ, сохрани!)* :\n` +
            `\`${botToken}\`\n\n` +
            `Используй этот токен в своём боте для аутентификации.\n` +
            `Документация: /help`;

        const kb = inlineKeyboard([
            btn('📋 Мои боты',           'cmd_mybots'),
            btn('🔍 Диагностика',        `checkbot_${botId}`),
            btn('⚙️ Установить команды', `setcmd_${botId}`),
            btn('📝 Описание',           `setdesc_${botId}`),
            btn('📰 RSS-лента',          `rss_${botId}`),
            btn('➕ Создать ещё',        'cmd_newbot')
        ], 2);

        await ctx.wo_bots.increment('total_users', { where: { bot_id: WALLYBOT_ID } });
        console.log(`[WallyBot] Created bot @${data.username} (${botId}) for user ${userId}`);
        return sendToUser(ctx, io, userId, responseText, kb);
    }

    // SETCMD: ввод команд
    if (state === STATES.SETCMD_INPUT) {
        const { bot_id: targetBotId } = data;

        // Парсим команды формата: /start - Описание
        const lines = text.split('\n').filter(l => l.trim());
        const commands = [];
        for (const line of lines) {
            const match = line.match(/^\/?([\w]+)\s*[-—]\s*(.+)$/);
            if (match) {
                commands.push({ command: match[1].toLowerCase(), description: match[2].trim() });
            }
        }

        if (!commands.length) {
            return sendToUser(ctx, io, userId,
                'Не удалось разобрать команды. Используй формат:\n`/команда - Описание`\n\nПо одной на строку. Попробуй снова:'
            );
        }

        await ctx.wo_bot_commands.destroy({ where: { bot_id: targetBotId } });
        for (let i = 0; i < commands.length; i++) {
            await ctx.wo_bot_commands.create({
                bot_id:      targetBotId,
                command:     sanitize(commands[i].command),
                description: sanitize(commands[i].description),
                scope:       'all',
                is_hidden:   0,
                sort_order:  i
            });
        }

        clearState(userId);
        return sendToUser(ctx, io, userId,
            `✅ *Команды установлены!*\n\n` +
            commands.map(c => `/${c.command} — ${c.description}`).join('\n'),
            inlineKeyboard([
                btn('🔍 Диагностика', `checkbot_${targetBotId}`),
                btn('📝 Описание',    `setdesc_${targetBotId}`),
                btn('📰 RSS-лента',   `rss_${targetBotId}`),
                btn('📦 Мои боты',    'cmd_mybots')
            ], 2)
        );
    }

    // SETDESC: ввод описания
    if (state === STATES.SETDESC_INPUT) {
        const { bot_id: targetBotId } = data;
        const desc = text.trim();

        await ctx.wo_bots.update(
            { description: sanitize(desc), updated_at: new Date() },
            { where: { bot_id: targetBotId, owner_id: userId } }
        );

        clearState(userId);
        return sendToUser(ctx, io, userId,
            `✅ Описание обновлено!`,
            inlineKeyboard([
                btn('🔍 Диагностика', `checkbot_${targetBotId}`),
                btn('⚙️ Команды',    `setcmd_${targetBotId}`),
                btn('📦 Мои боты',   'cmd_mybots')
            ], 2)
        );
    }

    // EDITBOT: ввод нового значения поля
    if (state === STATES.EDITBOT_VALUE) {
        const { bot_id: targetBotId, field } = data;
        const value = text.trim();

        const allowedFields = {
            display_name: 'display_name',
            description:  'description',
            about:        'about',
            category:     'category'
        };

        if (!allowedFields[field]) {
            clearState(userId);
            return sendToUser(ctx, io, userId, 'Неизвестное поле. Действие отменено.');
        }

        await ctx.wo_bots.update(
            { [allowedFields[field]]: sanitize(value), updated_at: new Date() },
            { where: { bot_id: targetBotId, owner_id: userId } }
        );

        clearState(userId);
        return sendToUser(ctx, io, userId, `Поле *${field}* обновлено!`);
    }

    // LEARN: ввод ключевого слова
    if (state === STATES.LEARN_KEYWORD) {
        if (!text.trim()) return sendToUser(ctx, io, userId, 'Ключевое слово не может быть пустым. Попробуй снова:');
        setState(userId, STATES.LEARN_RESPONSE, { keyword: text.trim() });
        return sendToUser(ctx, io, userId,
            `Ключевое слово: *"${text.trim()}"*\n\nШаг 2/2: Введи ответ который я должен давать на этот запрос:`
        );
    }

    // LEARN: ввод ответа
    if (state === STATES.LEARN_RESPONSE) {
        if (!text.trim()) return sendToUser(ctx, io, userId, 'Ответ не может быть пустым. Попробуй снова:');

        await learnFact(ctx, data.keyword, text.trim(), userId);
        clearState(userId);
        return sendToUser(ctx, io, userId,
            `Запомнил!\nТеперь на запросы о *"${data.keyword}"* я буду отвечать:\n\n_${text.trim()}_`,
            inlineKeyboard([
                btn('Научить ещё', 'cmd_learn'),
                btn('Посмотреть всё', 'cmd_knowledge')
            ])
        );
    }

    // BROADCAST: підтвердження тексту розсилки
    if (state === STATES.BROADCAST_INPUT) {
        const { bot_id: targetBotId, username: botUsername, user_count } = data;
        const msgText = text.trim();
        if (!msgText) {
            return sendToUser(ctx, io, userId, 'Сообщение не может быть пустым. Введи текст:');
        }

        // Encode message as base64 to embed in callback_data safely
        const encoded = Buffer.from(msgText).toString('base64');
        const cbData  = `broadcastdo_${targetBotId}:${encoded}`;

        // callback_data limit ~64 bytes — if too large, truncate with warning
        if (cbData.length > 200) {
            return sendToUser(ctx, io, userId,
                '⚠️ Сообщение слишком длинное для подтверждения. Сократи текст до ~100 символов и введи снова:'
            );
        }

        return sendToUser(ctx, io, userId,
            `📋 Предпросмотр рассылки:\n\n${msgText}\n\n—\n📢 Будет отправлено *${user_count}* пользователям @${botUsername}. Подтвердить?`,
            inlineKeyboard([
                btn('✅ Отправить',  cbData),
                btn('❌ Отменить', 'cmd_cancel')
            ], 1)
        );
    }

    // TOUR: текстовая навигация (запасной вариант когда кнопки не отображаются)
    if (state === STATES.TOUR_STEP) {
        const { step } = data;
        const lText = text.toLowerCase().trim();
        if (/^(дальше|далее|вперёд|вперед|следующий|>|next|\+|2)$/.test(lText)) {
            if (step >= TOUR_STEPS.length) {
                // Последний шаг — идём в главное меню
                clearState(userId);
                return handleStart(ctx, io, userId, 'ты');
            }
            return handleTour(ctx, io, userId, step + 1);
        }
        if (/^(назад|<|back|prev|\-|1)$/.test(lText)) {
            return handleTour(ctx, io, userId, Math.max(1, step - 1));
        }
        if (/^(пропустить|стоп|stop|выход|меню|menu|главное)$/.test(lText)) {
            clearState(userId);
            return handleStart(ctx, io, userId, 'ты');
        }
        // Любой другой текст — подсказка по навигации
        const tour = TOUR_STEPS[step - 1];
        return sendToUser(ctx, io, userId,
            `Ты сейчас в туре (шаг ${step}/${TOUR_STEPS.length}).\n` +
            `Напиши *"дальше"* или *"назад"* для навигации, *"пропустить"* для выхода.`,
            tour.kb()
        );
    }

    // TEMPLATE: шаг 1 — имя бота
    if (state === STATES.TEMPLATE_NAME) {
        if (!text.trim()) return sendToUser(ctx, io, userId, 'Имя не может быть пустым. Попробуй снова:');
        setState(userId, STATES.TEMPLATE_USERNAME, { ...data, display_name: text.trim() });
        return sendToUser(ctx, io, userId,
            `Имя: *${text.trim()}*\n\n` +
            `*Шаг 2/2:* Введи username бота\n_(только буквы, цифры, _, должен заканчиваться на _bot)_\n` +
            `Например: my_support_bot, news_myproject_bot`
        );
    }

    // TEMPLATE: шаг 2 — username → создать бота
    if (state === STATES.TEMPLATE_USERNAME) {
        const username = text.trim().toLowerCase();
        if (!/^[a-zA-Z][a-zA-Z0-9_]{2,30}_bot$/.test(username)) {
            return sendToUser(ctx, io, userId,
                'Неверный формат! Username должен:\n' +
                '• Начинаться с буквы\n• Содержать только буквы, цифры, _\n' +
                '• Заканчиваться на _bot\n• Длина: 5-32 символа\n\nПопробуй снова:'
            );
        }
        const existing = await ctx.wo_bots.findOne({ where: { username } });
        if (existing) return sendToUser(ctx, io, userId, `Username @${username} уже занят! Придумай другой:`);
        return applyTemplate(ctx, io, userId, data.tplId, data.display_name, username);
    }

    // RSS: ввод URL ленты
    if (state === STATES.RSS_ADD_URL) {
        const { bot_id: targetBotId } = data;
        const feedUrl = text.trim();

        if (!feedUrl.startsWith('http')) {
            return sendToUser(ctx, io, userId,
                '⚠️ Введи корректный URL RSS-ленты (должен начинаться с http:// или https://).\n\nПопробуй снова:'
            );
        }

        setState(userId, STATES.RSS_ADD_NAME, { ...data, feed_url: feedUrl });
        return sendToUser(ctx, io, userId,
            `URL принят!\n\nТеперь введи название ленты (например: "Хабр", "BBC News"),\nили /skip чтобы использовать URL как название:`
        );
    }

    // RSS: ввод имени ленты
    if (state === STATES.RSS_ADD_NAME) {
        const { bot_id: targetBotId, feed_url } = data;
        const feedName = (text === '/skip' || !text.trim())
            ? null
            : text.trim().substring(0, 60);

        const rssModel = ctx.wo_bot_rss_feeds;
        if (!rssModel) {
            clearState(userId);
            return sendToUser(ctx, io, userId, '⚠️ RSS не поддерживается на сервере.');
        }

        try {
            await rssModel.create({
                bot_id:                 targetBotId,
                chat_id:                String(userId),
                feed_url:               feed_url,
                feed_name:              feedName,
                is_active:              1,
                feed_language:          'ru',
                check_interval_minutes: 30,
                max_items_per_check:    5,
                include_image:          1,
                include_description:    1,
                items_posted:           0,
                created_at:             new Date()
            });
        } catch (e) {
            clearState(userId);
            console.error('[WallyBot/RSS add]', e.message);
            return sendToUser(ctx, io, userId,
                '❌ Не удалось добавить ленту. Проверь URL и попробуй снова.'
            );
        }

        clearState(userId);
        const displayName = feedName || feed_url.replace(/https?:\/\//, '').substring(0, 40);
        return sendToUser(ctx, io, userId,
            `✅ *RSS-лента добавлена!*\n\n📰 ${displayName}\n🔗 ${feed_url}\n\nПроверка будет происходить каждые 30 минут.`,
            inlineKeyboard([
                btn('📋 Все ленты',    `rss_${targetBotId}`),
                btn('📦 Мои боты',     'cmd_mybots')
            ])
        );
    }

    return null; // состояние не обработано
}

// ─── Default Bot Shell ───────────────────────────────────────────────────────
// Отвечает пользователю от имени бота без webhook/socket (auto-reply).
// Даёт базовую навигацию с inline-кнопками команд до тех пор, пока владелец
// не подключит webhook или свой сокет.

async function handleDefaultBot(ctx, io, bot, userId, text, isCommand, cmdParts) {
    const botName     = bot.display_name || bot.username;
    const botUserId   = bot.linked_user_id || null;

    // Загружаем команды бота
    const commands = await ctx.wo_bot_commands.findAll({
        where:   { bot_id: bot.bot_id },
        order:   [['sort_order', 'ASC']],
        limit:   20,
        raw:     true
    });

    // Строим inline-клавиатуру из команд бота
    // callback_data: shell_cmd_<command> — обрабатывается BotCallbackQueryController
    const cmdButtons = commands
        .filter(c => c.command !== 'cancel')
        .map(c => btn(`/${c.command}`, `shell_cmd_${c.command}`));

    const mainKb = cmdButtons.length ? inlineKeyboard(cmdButtons, 2) : null;

    async function reply(msg, markup) {
        return sendFromBot(ctx, io, botUserId, bot.bot_id, botName, userId, msg, markup);
    }

    if (isCommand) {
        const cmd = (cmdParts[0] || '').toLowerCase();

        if (cmd === 'start') {
            const desc     = bot.description ? `\n\n${bot.description}` : '';
            const cmdList  = commands.length
                ? '\n\n*Команды:*\n' + commands.map(c => `/${c.command} — ${c.description}`).join('\n')
                : '';
            return reply(`👋 Привет! Я *${botName}*.${desc}${cmdList}`, mainKb);
        }

        if (cmd === 'help') {
            const desc    = bot.description ? `${bot.description}\n\n` : '';
            const cmdList = commands.length
                ? '*Команды:*\n' + commands.map(c => `/${c.command} — ${c.description}`).join('\n')
                : 'Команды ещё не настроены.';
            return reply(`ℹ️ *${botName}*\n\n${desc}${cmdList}`, mainKb);
        }

        // Известная команда — откликаемся её описанием
        const knownCmd = commands.find(c => c.command === cmd);
        if (knownCmd) {
            return reply(`*/${knownCmd.command}*\n${knownCmd.description}`, mainKb);
        }

        return reply(`Команда /${cmd} не поддерживается.\n\nВведи /help для списка команд.`, mainKb);
    }

    // Текстовое сообщение без команды
    return reply(
        `💬 Бот *${botName}* работает в автономном режиме.\n\nДля навигации используй /start или /help.`,
        mainKb
    );
}

// ─── /checkbot — диагностика бота ────────────────────────────────────────────

async function sendBotDiagnosis(ctx, io, userId, bot) {
    const rssModel = ctx.wo_bot_rss_feeds;
    const [cmdsCount, usersCount, messagesIn, messagesOut, rssCount] = await Promise.all([
        ctx.wo_bot_commands.count({ where: { bot_id: bot.bot_id } }),
        ctx.wo_bot_users.count({ where: { bot_id: bot.bot_id } }),
        ctx.wo_bot_messages.count({ where: { bot_id: bot.bot_id, direction: 'incoming' } }),
        ctx.wo_bot_messages.count({ where: { bot_id: bot.bot_id, direction: 'outgoing' } }),
        rssModel
            ? rssModel.count({ where: { bot_id: bot.bot_id, is_active: 1 } })
            : Promise.resolve(0),
    ]);

    const statusOk  = bot.status === 'active';
    const hasDesc   = !!bot.description;
    const hasCmds   = cmdsCount >= 2;
    const hasUsers  = usersCount > 0;
    const healthScore = [statusOk, hasDesc, hasCmds, hasUsers].filter(Boolean).length;
    const healthEmoji = healthScore >= 4 ? '🟢' : healthScore >= 2 ? '🟡' : '🔴';

    const statusIcon = statusOk ? '🟢' : '🔴';
    const descIcon   = hasDesc  ? '✅' : '⚠️';
    const cmdsIcon   = hasCmds  ? '✅' : '⚠️';
    const hookIcon   = bot.webhook_enabled ? '✅' : '⚪';

    const issues = [];
    if (!hasDesc)              issues.push('• Нет описания — добавь через /setdesc или кнопку ниже');
    if (!hasCmds)              issues.push('• Мало команд — добавь через /setcommands или кнопку ниже');
    if (!bot.webhook_enabled)  issues.push('• Webhook не настроен — бот не будет получать сообщения без webhook или Socket.IO интеграции');
    if (!hasUsers)             issues.push('• Пока нет пользователей — поделись ссылкой @' + bot.username);

    const shortDesc = bot.description
        ? (bot.description.length > 60 ? bot.description.substring(0, 60) + '…' : bot.description)
        : '_не задано_';

    const text =
        `🔍 *Диагностика @${bot.username}*\n\n` +
        `${healthEmoji} Здоровье: ${healthScore}/4 — ${['критично', 'плохо', 'неплохо', 'хорошо', 'отлично'][healthScore]}\n\n` +
        `${statusIcon} Статус: *${bot.status}*\n` +
        `${descIcon} Описание: ${shortDesc}\n` +
        `${cmdsIcon} Команд: *${cmdsCount}*\n` +
        `${hookIcon} Webhook: ${bot.webhook_enabled ? '✅ активен' : '⚪ не настроен'}\n` +
        `👥 Пользователей: *${usersCount}*\n` +
        `📥 Получено: *${messagesIn}* / 📤 Отправлено: *${messagesOut}*\n` +
        `📰 RSS-лент активных: *${rssCount}*\n\n` +
        (issues.length
            ? `*⚠️ Рекомендации:*\n${issues.join('\n')}`
            : `✅ Всё настроено хорошо!`);

    const buttons = [btn('📦 Мои боты', 'cmd_mybots')];
    if (!hasDesc)  buttons.push(btn('📝 Описание',  `setdesc_${bot.bot_id}`));
    if (!hasCmds)  buttons.push(btn('⚙️ Команды',  `setcmd_${bot.bot_id}`));
    buttons.push(btn('📰 RSS-ленты', `rss_${bot.bot_id}`));
    buttons.push(btn('🔄 Обновить',  `checkbot_${bot.bot_id}`));

    return sendToUser(ctx, io, userId, text, inlineKeyboard(buttons, 2));
}

async function handleCheckBot(ctx, io, userId, botUsername) {
    clearState(userId);
    if (botUsername) {
        const bot = await ctx.wo_bots.findOne({
            where: { username: botUsername.replace(/^@/, ''), owner_id: userId },
            raw: true
        });
        if (!bot) {
            return sendToUser(ctx, io, userId,
                `Бот @${botUsername} не найден или не является твоим.`,
                inlineKeyboard([btn('Мои боты', 'cmd_mybots')])
            );
        }
        return sendBotDiagnosis(ctx, io, userId, bot);
    }

    const bots = await getUserBots(ctx, userId);
    if (!bots.length) {
        return sendToUser(ctx, io, userId,
            'У тебя нет ботов для диагностики.',
            inlineKeyboard([btn('Создать бота', 'cmd_newbot')])
        );
    }
    if (bots.length === 1) {
        const bot = await ctx.wo_bots.findOne({ where: { bot_id: bots[0].bot_id }, raw: true });
        return sendBotDiagnosis(ctx, io, userId, bot);
    }

    const buttons = bots.map(b => btn(`@${b.username}`, `checkbot_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    return sendToUser(ctx, io, userId,
        '🔍 *Диагностика бота*\n\nВыбери бота для проверки:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── /rss — управление RSS-лентами ───────────────────────────────────────────

async function showRssForBot(ctx, io, userId, botId) {
    clearState(userId);
    const bot = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
    if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

    const rssModel = ctx.wo_bot_rss_feeds;
    if (!rssModel) {
        return sendToUser(ctx, io, userId,
            '⚠️ RSS-ленты не поддерживаются на этом сервере.',
            inlineKeyboard([btn('Мои боты', 'cmd_mybots')])
        );
    }

    const feeds = await rssModel.findAll({ where: { bot_id: botId }, raw: true, limit: 10 });

    let text = `📰 *RSS-ленты @${bot.username}*\n\n`;
    const buttons = [];

    if (!feeds.length) {
        text += `Лент пока нет.\n\nДобавь RSS-ленту — бот будет автоматически публиковать новые материалы!`;
    } else {
        for (const feed of feeds) {
            const icon = feed.is_active ? '🟢' : '⏸';
            const name = (feed.feed_name || feed.feed_url.replace(/https?:\/\//, '')).substring(0, 35);
            text += `${icon} *${name}*\n   Опубликовано: ${feed.items_posted} шт.\n\n`;
            buttons.push(btn(`${icon} ${name.substring(0, 22)}`, `rss_toggle_${feed.id}`));
            buttons.push(btn(`🗑 Удалить`, `rss_delete_${feed.id}`));
        }
    }

    buttons.push(btn('➕ Добавить ленту', `rss_add_${botId}`));
    buttons.push(btn('🔍 Диагностика',   `checkbot_${botId}`));
    buttons.push(btn('◀ Назад',          'cmd_mybots'));

    return sendToUser(ctx, io, userId, text, inlineKeyboard(buttons, 2));
}

async function handleRss(ctx, io, userId) {
    clearState(userId);

    if (!ctx.wo_bot_rss_feeds) {
        return sendToUser(ctx, io, userId,
            '⚠️ RSS-ленты не поддерживаются на этом сервере.',
            inlineKeyboard([btn('Мои боты', 'cmd_mybots')])
        );
    }

    const bots = await getUserBots(ctx, userId);
    if (!bots.length) {
        return sendToUser(ctx, io, userId,
            '📰 *RSS-рассылка*\n\nСначала создай бота, которому можно подключить RSS-ленту.',
            inlineKeyboard([btn('Создать бота', 'cmd_newbot')])
        );
    }
    if (bots.length === 1) {
        return showRssForBot(ctx, io, userId, bots[0].bot_id);
    }

    setState(userId, STATES.RSS_SELECT_BOT, {});
    const buttons = bots.map(b => btn(`@${b.username}`, `rss_${b.bot_id}`));
    buttons.push(btn('Отмена', 'cmd_cancel'));
    return sendToUser(ctx, io, userId,
        '📰 *RSS-рассылка*\n\nВыбери бота для управления RSS-лентами:',
        inlineKeyboard(buttons, 1)
    );
}

// ─── ОБРАБОТКА CALLBACK QUERY ────────────────────────────────────────────────

async function handleCallback(ctx, io, userId, callbackData, callbackId) {
    // Отвечаем на callback чтобы убрать индикатор загрузки
    if (io) {
        io.to(String(userId)).emit('bot_callback_answer', {
            bot_id:            WALLYBOT_ID,
            callback_query_id: callbackId,
            text:              '',
            show_alert:        false
        });
    }

    // ── Главные команды через кнопки ────────────────────────────────────────
    if (callbackData === 'cmd_newbot')    return handleNewBot(ctx, io, userId);
    if (callbackData === 'newbot_blank')  return handleNewBotBlank(ctx, io, userId);
    if (callbackData === 'cmd_templates') return handleTemplates(ctx, io, userId);
    if (callbackData === 'cmd_mybots')    return handleMyBots(ctx, io, userId);
    if (callbackData === 'cmd_help')      return handleHelp(ctx, io, userId);
    if (callbackData === 'cmd_cancel')    return handleCancel(ctx, io, userId);
    if (callbackData === 'cmd_learn')     return handleLearn(ctx, io, userId);
    if (callbackData === 'cmd_ask')       return handleAsk(ctx, io, userId);
    if (callbackData === 'cmd_messenger_guide') return handleMessengerGuide(ctx, io, userId);
    if (callbackData === 'cmd_guide')     return handleGuide(ctx, io, userId);
    if (callbackData === 'cmd_about')     return handleAbout(ctx, io, userId);
    if (callbackData.startsWith('guide_section_')) {
        const sectionKey = callbackData.replace('guide_section_', '');
        return handleGuideSection(ctx, io, userId, sectionKey);
    }
    if (callbackData.startsWith('guide_uk_')) {
        const sectionKey = callbackData.replace('guide_uk_', '');
        return handleGuideSectionUk(ctx, io, userId, sectionKey);
    }
    if (callbackData === 'cmd_start')     return handleStart(ctx, io, userId, 'ты');
    if (callbackData === 'cmd_stats')     return handleStats(ctx, io, userId);
    if (callbackData === 'cmd_setinline') return handleSetInline(ctx, io, userId);
    if (callbackData === 'cmd_setgroups') return handleSetGroups(ctx, io, userId);
    if (callbackData === 'cmd_revoke')    return handleRevoke(ctx, io, userId);
    if (callbackData === 'cmd_broadcast') return handleBroadcast(ctx, io, userId);

    // ── Тур/онбординг ───────────────────────────────────────────────────────
    if (callbackData === 'tour_skip') return handleStart(ctx, io, userId, 'ты');
    if (callbackData.startsWith('tour_step_')) {
        const step = parseInt(callbackData.replace('tour_step_', ''), 10);
        return handleTour(ctx, io, userId, step);
    }

    // ── Шаблоны ботов ───────────────────────────────────────────────────────
    if (callbackData.startsWith('template_select_')) {
        const tplId = callbackData.replace('template_select_', '');
        const tpl   = BOT_TEMPLATES[tplId];
        if (!tpl) return sendToUser(ctx, io, userId, 'Шаблон не найден.');
        setState(userId, STATES.TEMPLATE_NAME, { tplId });
        return sendToUser(ctx, io, userId,
            `${tpl.icon} *Шаблон "${tpl.name}"*\n\n${tpl.description}\n\n` +
            `*Шаг 1/2:* Введи отображаемое имя бота\n_(например: "Моя Поддержка", "Новости Проекта")_`
        );
    }

    // ── Просмотр базы знаний ─────────────────────────────────────────────────
    if (callbackData === 'cmd_knowledge') {
        const list = await getKnowledgeList(ctx, 10);
        if (!list.length) return sendToUser(ctx, io, userId, 'База знаний пуста. Обучи меня через /learn!');
        const text = `*База знаний WallyBot (${list.length} записей):*\n\n` +
            list.map(item => `• *${item.title}*\n  ${item.description.substring(0, 80)}...`).join('\n\n');
        return sendToUser(ctx, io, userId, text, inlineKeyboard([
            btn('Удалить запись', 'cmd_forget'),
            btn('Добавить',       'cmd_learn')
        ]));
    }

    if (callbackData === 'cmd_forget') return handleForget(ctx, io, userId);

    // ── Информация о конкретном боте ─────────────────────────────────────────
    if (callbackData.startsWith('bot_info_')) {
        const botId = callbackData.replace('bot_info_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        const cmdsCount = await ctx.wo_bot_commands.count({ where: { bot_id: botId } });
        const text = `*@${bot.username}*\n\n` +
            `Имя: ${bot.display_name}\n` +
            `Статус: ${bot.status}\n` +
            `Описание: ${bot.description || '_нет_'}\n` +
            `Категория: ${bot.category}\n` +
            `Команд: ${cmdsCount}\n` +
            `Пользователей: ${bot.total_users}\n` +
            `Создан: ${new Date(bot.created_at).toLocaleDateString('ru-RU')}`;

        const kb = inlineKeyboard([
            btn('✏️ Изменить',    `editselect_${botId}`),
            btn('🔑 Токен',       `tokenshow_${botId}`),
            btn('⚙️ Команды',    `setcmd_${botId}`),
            btn('🔍 Диагностика', `checkbot_${botId}`),
            btn('📰 RSS-ленты',   `rss_${botId}`),
            btn('🗑 Удалить',     `deleteconfirm_${botId}`),
            btn('◀ Назад',        'cmd_mybots')
        ], 2);

        return sendToUser(ctx, io, userId, text, kb);
    }

    // ── Выбор бота для редактирования ───────────────────────────────────────
    if (callbackData.startsWith('editselect_')) {
        const botId = callbackData.replace('editselect_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        setState(userId, STATES.EDITBOT_FIELD, { bot_id: botId });
        const kb = inlineKeyboard([
            btn('Изменить имя',       `editfield_${botId}_display_name`),
            btn('Изменить описание',  `editfield_${botId}_description`),
            btn('Изменить about',     `editfield_${botId}_about`),
            btn('Изменить категорию', `editfield_${botId}_category`),
            btn('Отмена',             'cmd_cancel')
        ], 1);

        return sendToUser(ctx, io, userId, `Что изменить в @${bot.username}?`, kb);
    }

    // ── Выбор поля для редактирования ───────────────────────────────────────
    if (callbackData.startsWith('editfield_')) {
        const parts = callbackData.replace('editfield_', '').split('_');
        // bot_id может содержать _, поэтому берём последний элемент как field
        const field = parts.pop();
        const botId = parts.join('_');

        setState(userId, STATES.EDITBOT_VALUE, { bot_id: botId, field });
        const fieldNames = {
            display_name: 'отображаемое имя',
            description:  'описание',
            about:        'about',
            category:     'категорию'
        };
        return sendToUser(ctx, io, userId, `Введи новое ${fieldNames[field] || field}:`);
    }

    // ── Токен бота ───────────────────────────────────────────────────────────
    if (callbackData.startsWith('tokenshow_')) {
        const botId = callbackData.replace('tokenshow_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        const text = `*Токен бота @${bot.username}:*\n\n\`${bot.bot_token}\`\n\n⚠️ Никому не передавай токен!`;
        const kb   = inlineKeyboard([
            btn('Обновить токен', `tokenregen_${botId}`),
            btn('Назад',          `bot_info_${botId}`)
        ]);
        return sendToUser(ctx, io, userId, text, kb);
    }

    // ── Обновить токен бота ──────────────────────────────────────────────────
    if (callbackData.startsWith('tokenregen_')) {
        const botId    = callbackData.replace('tokenregen_', '');
        const bot      = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId } });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        const newToken = generateBotToken(botId);
        await ctx.wo_bots.update({ bot_token: newToken, updated_at: new Date() }, { where: { bot_id: botId } });

        return sendToUser(ctx, io, userId,
            `Токен обновлён!\n\nНовый токен @${bot.username}:\n\`${newToken}\`\n\nСтарый токен больше не действителен.`
        );
    }

    // ── Установка команд бота ────────────────────────────────────────────────
    if (callbackData.startsWith('setcmd_')) {
        const botId = callbackData.replace('setcmd_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        setState(userId, STATES.SETCMD_INPUT, { bot_id: botId });
        return sendToUser(ctx, io, userId,
            `Устанавливаем команды для @${bot.username}\n\nВведи команды в формате (по одной на строку):\n\`/start - Начало работы\`\n\`/help - Справка\`\n\`/info - Информация\``
        );
    }

    // ── Установка описания бота ──────────────────────────────────────────────
    if (callbackData.startsWith('setdesc_')) {
        const botId = callbackData.replace('setdesc_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        setState(userId, STATES.SETDESC_INPUT, { bot_id: botId });
        return sendToUser(ctx, io, userId, `Введи новое описание для @${bot.username}:`);
    }

    // ── Удаление бота — выбор ────────────────────────────────────────────────
    if (callbackData.startsWith('deleteselect_') || callbackData.startsWith('deleteconfirm_')) {
        const botId = callbackData.replace('deleteselect_', '').replace('deleteconfirm_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        setState(userId, STATES.DELETEBOT_CONFIRM, { bot_id: botId });
        const kb = inlineKeyboard([
            btn(`Да, удалить @${bot.username}`, `deletedo_${botId}`),
            btn('Нет, отмена',                   'cmd_cancel')
        ], 1);

        return sendToUser(ctx, io, userId,
            `Ты уверен что хочешь удалить бота *@${bot.username}*?\n\n` +
            `Это действие необратимо! Все данные (сообщения, пользователи, команды) будут удалены.`,
            kb
        );
    }

    // ── Удаление бота — подтверждение ────────────────────────────────────────
    if (callbackData.startsWith('deletedo_')) {
        const botId = callbackData.replace('deletedo_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId } });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');

        const username = bot.username;

        // Каскадное удаление
        await ctx.wo_bot_commands.destroy(   { where: { bot_id: botId } });
        await ctx.wo_bot_messages.destroy(   { where: { bot_id: botId } });
        await ctx.wo_bot_users.destroy(      { where: { bot_id: botId } });
        await ctx.wo_bot_keyboards.destroy(  { where: { bot_id: botId } });
        await ctx.wo_bot_callbacks.destroy(  { where: { bot_id: botId } });
        await ctx.wo_bot_webhook_log.destroy({ where: { bot_id: botId } });
        await ctx.wo_bot_rate_limits.destroy({ where: { bot_id: botId } });
        await ctx.wo_bots.destroy(           { where: { bot_id: botId } });

        clearState(userId);
        console.log(`[WallyBot] Deleted bot ${botId} (@${username}) by user ${userId}`);

        return sendToUser(ctx, io, userId,
            `Бот *@${username}* удалён.`,
            inlineKeyboard([btn('Мои боты', 'cmd_mybots'), btn('Создать нового', 'cmd_newbot')])
        );
    }

    // ── Удаление из базы знаний ──────────────────────────────────────────────
    if (callbackData.startsWith('forget_')) {
        const keyword = decodeURIComponent(callbackData.replace('forget_', ''));
        await forgetFact(ctx, keyword);
        clearState(userId);
        return sendToUser(ctx, io, userId, `Забыл всё о *"${keyword}"*.`);
    }

    // ── Перемикання inline-режиму ────────────────────────────────────────────
    if (callbackData.startsWith('setinline_')) {
        const botId = callbackData.replace('setinline_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId } });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не знайдений.');

        const newVal = bot.supports_inline ? 0 : 1;
        await ctx.wo_bots.update({ supports_inline: newVal, updated_at: new Date() }, { where: { bot_id: botId } });
        clearState(userId);

        return sendToUser(ctx, io, userId,
            newVal
                ? `✅ Inline-режим *включён* для @${bot.username}.\n\nТеперь пользователи могут использовать бота через \`@${bot.username} запрос\` в любом чате.`
                : `❌ Inline-режим *отключён* для @${bot.username}.`,
            inlineKeyboard([btn('Мои боты', 'cmd_mybots'), btn('Настроить ещё', 'cmd_setinline')])
        );
    }

    // ── Перемикання доступу до груп ──────────────────────────────────────────
    if (callbackData.startsWith('setgroups_')) {
        const botId = callbackData.replace('setgroups_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId } });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не знайдений.');

        const newVal = bot.can_join_groups ? 0 : 1;
        await ctx.wo_bots.update({ can_join_groups: newVal, updated_at: new Date() }, { where: { bot_id: botId } });
        clearState(userId);

        return sendToUser(ctx, io, userId,
            newVal
                ? `✅ Доступ к группам *включён* для @${bot.username}.`
                : `❌ Доступ к группам *отключён* для @${bot.username}.`,
            inlineKeyboard([btn('Мои боты', 'cmd_mybots'), btn('Настроить ещё', 'cmd_setgroups')])
        );
    }

    // ── Підтвердження відкликання токена ────────────────────────────────────
    if (callbackData.startsWith('revokeconfirm_')) {
        const botId = callbackData.replace('revokeconfirm_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не знайдений.');

        return sendToUser(ctx, io, userId,
            `⚠️ Ты уверен, что хочешь отозвать токен *@${bot.username}*?\n\nСтарый токен станет *недействительным немедленно*. Все интеграции остановятся.`,
            inlineKeyboard([
                btn(`✅ Да, отозвать`, `revokedo_${botId}`),
                btn('❌ Отменить',     'cmd_cancel')
            ], 1)
        );
    }

    // ── Виконання відкликання токена ─────────────────────────────────────────
    if (callbackData.startsWith('revokedo_')) {
        const botId = callbackData.replace('revokedo_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId } });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не знайдений.');

        const newToken = generateBotToken(botId);
        await ctx.wo_bots.update({ bot_token: newToken, updated_at: new Date() }, { where: { bot_id: botId } });
        clearState(userId);

        return sendToUser(ctx, io, userId,
            `🔑 Токен *@${bot.username}* отозван и заменён!\n\nНовый токен:\n\`${newToken}\`\n\n⚠️ Сохрани его — показываю только один раз!`,
            inlineKeyboard([btn('Мои боты', 'cmd_mybots')])
        );
    }

    // ── Вибір бота для розсилки ───────────────────────────────────────────────
    if (callbackData.startsWith('broadcast_') && !callbackData.startsWith('broadcastdo_')) {
        const botId = callbackData.replace('broadcast_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не знайдений.');

        const userCount = await ctx.wo_bot_users.count({ where: { bot_id: botId } });
        if (userCount === 0) {
            return sendToUser(ctx, io, userId,
                `У бота @${bot.username} ещё нет пользователей. Рассылать некому.`,
                inlineKeyboard([btn('Мои боты', 'cmd_mybots')])
            );
        }

        setState(userId, STATES.BROADCAST_INPUT, { bot_id: botId, username: bot.username, user_count: userCount });
        return sendToUser(ctx, io, userId,
            `📢 Рассылка для *@${bot.username}*\nПолучателей: *${userCount}*\n\nВведи текст сообщения для рассылки:`,
            inlineKeyboard([btn('Отменить', 'cmd_cancel')])
        );
    }

    // ── Підтвердження розсилки ────────────────────────────────────────────────
    if (callbackData.startsWith('broadcastdo_')) {
        const [,botId, ...msgParts] = callbackData.split(':');
        // broadcastdo_ encodes bot_id:base64msg
        const encodedMsg = msgParts.join(':');
        let broadcastText;
        try { broadcastText = Buffer.from(encodedMsg, 'base64').toString('utf8'); } catch { broadcastText = ''; }

        const bot = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot || !broadcastText) return sendToUser(ctx, io, userId, 'Ошибка. Попробуй ещё раз.');

        const recipients = await ctx.wo_bot_users.findAll({ where: { bot_id: botId }, attributes: ['user_id'], raw: true });

        let sent = 0;
        for (const r of recipients) {
            try {
                await ctx.wo_bot_messages.create({
                    bot_id: botId, chat_id: String(r.user_id), chat_type: 'private',
                    direction: 'outgoing', text: broadcastText, processed: 1, processed_at: new Date()
                });
                if (io) {
                    io.to(String(r.user_id)).emit('bot_message', {
                        event: 'bot_message', bot_id: botId, text: broadcastText, timestamp: Date.now()
                    });
                }
                sent++;
            } catch {}
        }

        await ctx.wo_bots.increment('messages_sent', { by: sent, where: { bot_id: botId } });
        clearState(userId);

        return sendToUser(ctx, io, userId,
            `✅ Рассылка завершена!\n\nОтправлено: *${sent}* / ${recipients.length} сообщений`,
            inlineKeyboard([btn('Мои боты', 'cmd_mybots'), btn('Ещё рассылка', 'cmd_broadcast')])
        );
    }

    // ── Диагностика бота (/checkbot) ─────────────────────────────────────────
    if (callbackData === 'cmd_checkbot') return handleCheckBot(ctx, io, userId, '');

    if (callbackData.startsWith('checkbot_')) {
        const botId = callbackData.replace('checkbot_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.', inlineKeyboard([btn('Мои боты', 'cmd_mybots')]));
        return sendBotDiagnosis(ctx, io, userId, bot);
    }

    // ── RSS-ленты (/rss) ─────────────────────────────────────────────────────
    if (callbackData === 'cmd_rss') return handleRss(ctx, io, userId);

    // rss_<botId> — показать ленты для бота (НЕ пересекается с rss_toggle/delete/add)
    if (callbackData.startsWith('rss_') &&
        !callbackData.startsWith('rss_toggle_') &&
        !callbackData.startsWith('rss_delete_') &&
        !callbackData.startsWith('rss_add_')) {
        const botId = callbackData.replace('rss_', '');
        return showRssForBot(ctx, io, userId, botId);
    }

    if (callbackData.startsWith('rss_add_')) {
        const botId = callbackData.replace('rss_add_', '');
        const bot   = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: userId }, raw: true });
        if (!bot) return sendToUser(ctx, io, userId, 'Бот не найден.');
        setState(userId, STATES.RSS_ADD_URL, { bot_id: botId });
        return sendToUser(ctx, io, userId,
            `📰 *Добавление RSS-ленты для @${bot.username}*\n\nВведи URL RSS-ленты:\n_(например: https://habr.com/ru/rss/all/all/ или https://feeds.bbci.co.uk/news/rss.xml)_`
        );
    }

    if (callbackData.startsWith('rss_toggle_')) {
        const feedId = parseInt(callbackData.replace('rss_toggle_', ''), 10);
        const rssModel = ctx.wo_bot_rss_feeds;
        if (!rssModel) return sendToUser(ctx, io, userId, '⚠️ RSS не поддерживается.');
        const feed = await rssModel.findOne({ where: { id: feedId }, raw: true });
        if (!feed) return sendToUser(ctx, io, userId, 'Лента не найдена.');
        await rssModel.update({ is_active: feed.is_active ? 0 : 1 }, { where: { id: feedId } });
        return showRssForBot(ctx, io, userId, feed.bot_id);
    }

    if (callbackData.startsWith('rss_delete_')) {
        const feedId = parseInt(callbackData.replace('rss_delete_', ''), 10);
        const rssModel = ctx.wo_bot_rss_feeds;
        if (!rssModel) return sendToUser(ctx, io, userId, '⚠️ RSS не поддерживается.');
        const feed = await rssModel.findOne({ where: { id: feedId }, raw: true });
        if (!feed) return sendToUser(ctx, io, userId, 'Лента не найдена.');
        const botId = feed.bot_id;
        await rssModel.destroy({ where: { id: feedId } });
        return showRssForBot(ctx, io, userId, botId);
    }
}

// ─── ГЛАВНЫЙ ДИСПЕТЧЕР СООБЩЕНИЙ ─────────────────────────────────────────────

async function handleMessage(ctx, io, data) {
    const { user_id: userId, text = '', is_command, command_name, command_args,
            callback_data, callback_query_id } = data;

    if (!userId) return;

    // Получаем имя пользователя
    let userName = 'друг';
    try {
        const user = await ctx.wo_users.findOne({
            where:      { user_id: userId },
            attributes: ['first_name', 'username'],
            raw:        true
        });
        if (user) userName = user.first_name || user.username || userName;
    } catch {}

    // Обработка callback query (нажатие кнопки)
    if (callback_data) {
        return handleCallback(ctx, io, userId, callback_data, callback_query_id);
    }

    // Обработка команд
    if (is_command || text.startsWith('/')) {
        const cmd = (command_name || text.replace('/', '').split(' ')[0]).toLowerCase();
        const cmdHandlers = {
            start:       () => handleStart(ctx, io, userId, userName),
            help:        () => handleHelp(ctx, io, userId),
            newbot:      () => handleNewBot(ctx, io, userId),
            mybots:      () => handleMyBots(ctx, io, userId),
            editbot:     () => handleEditBot(ctx, io, userId),
            deletebot:   () => handleDeleteBot(ctx, io, userId),
            token:       () => handleToken(ctx, io, userId),
            setcommands: () => handleSetCommands(ctx, io, userId),
            setcmd:      () => handleSetCommands(ctx, io, userId),
            setdesc:     () => handleSetDesc(ctx, io, userId),
            setinline:   () => handleSetInline(ctx, io, userId),
            setgroups:   () => handleSetGroups(ctx, io, userId),
            revoke:      () => handleRevoke(ctx, io, userId),
            broadcast:   () => handleBroadcast(ctx, io, userId),
            learn:       () => handleLearn(ctx, io, userId),
            forget:      () => handleForget(ctx, io, userId),
            ask:         () => handleAsk(ctx, io, userId),
            messenger:   () => handleMessengerGuide(ctx, io, userId),
            guide:       () => handleGuide(ctx, io, userId),
            about:       () => handleAbout(ctx, io, userId),
            cancel:      () => handleCancel(ctx, io, userId),
            stats:       () => {
                // /stats @botname — per-bot stats; /stats — global
                const rawArg = (command_args || text.split(' ').slice(1).join(' ')).trim();
                const botUsername = rawArg.replace(/^@/, '');
                if (botUsername) return handleBotStats(ctx, io, userId, botUsername);
                return handleStats(ctx, io, userId);
            },
            who:         () => handleWho(ctx, io, userId),
            tour:        () => handleTour(ctx, io, userId, 1),
            templates:   () => handleTemplates(ctx, io, userId),
            checkbot:    () => {
                const rawArg = (command_args || text.split(' ').slice(1).join(' ')).trim();
                return handleCheckBot(ctx, io, userId, rawArg);
            },
            rss:         () => handleRss(ctx, io, userId),
        };

        const handler = cmdHandlers[cmd];
        if (handler) return handler();

        return sendToUser(ctx, io, userId,
            `Неизвестная команда: /${cmd} 🤔\n\nВведи /help для списка команд.`,
            inlineKeyboard([btn('Помощь', 'cmd_help'), btn('Главное меню', 'cmd_start')])
        );
    }

    // Обработка текстовых сообщений
    if (text.trim()) {
        const lText = text.toLowerCase().trim();

        // Easter eggs / social responses (before FSM/KB checks)
        const isGreeting = /^(привіт|привет|хай|hi|hello|йо|hey|вітаю|здравствуй|добрый|добридень)\b/.test(lText);
        const isThanks   = /\b(дякую|спасибо|дяки|thanks|thank you|дякую|спс|thx)\b/.test(lText);
        const isWho      = /\b(хто ти|кто ты|who are you|що ти|что ты)\b/.test(lText);

        if (isGreeting) {
            const currentState = getState(userId);
            if (currentState.state === STATES.IDLE) {
                const greets = [
                    `${userName}! 👋 Привет! Чем могу помочь?`,
                    `Привет, ${userName}! 😊 Рад тебя видеть! Пиши /help или просто спрашивай.`,
                    `Привет, ${userName}! 🌟 Что будем делать?`,
                ];
                return sendToUser(ctx, io, userId,
                    greets[Math.floor(Math.random() * greets.length)],
                    inlineKeyboard([btn('Главное меню', 'cmd_start'), btn('Помощь', 'cmd_help')])
                );
            }
        }

        if (isThanks && getState(userId).state === STATES.IDLE) {
            return handleThanks(ctx, io, userId);
        }
        if (isWho && getState(userId).state === STATES.IDLE) {
            return handleWho(ctx, io, userId);
        }

        // Сначала пробуем FSM состояние
        const currentState = getState(userId);
        if (currentState.state !== STATES.IDLE) {
            const handled = await processState(ctx, io, userId, text, currentState);
            if (handled !== null) return;
        }

        // Затем ищем в базе знаний
        const fact = await searchKnowledge(ctx, text);
        if (fact) {
            return sendToUser(ctx, io, userId,
                `*${fact.title}*\n\n${fact.description}`,
                inlineKeyboard([
                    btn('Помогло! 👍',       'kb_helpful_yes'),
                    btn('Не то, что нужно', 'kb_helpful_no')
                ])
            );
        }

        // Fallback — Russian
        const fallback = `Хм, я не знаю ответа на это 🤔\n\nПопробуй:\n• /help — список команд\n• /ask — поиск по базе знаний\n• /learn — научи меня этому!\n• /messenger — справка по мессенджеру`;
        await sendToUser(ctx, io, userId, fallback,
            inlineKeyboard([
                btn('Обучить WallyBot', 'cmd_learn'),
                btn('Главное меню',     'cmd_start')
            ])
        );
    }
}

// ─── Инициализация WallyBot ───────────────────────────────────────────────────

async function initializeWallyBot(ctx, io) {
    try {
        const now = Math.floor(Date.now() / 1000);

        // ── 1. Создать/найти Wo_Users запись для WallyBot ──────────────────────
        // Нужно для появления в поиске Android (Android ищет только Wo_Users)
        const [wallyUser] = await ctx.wo_users.findOrCreate({
            where: { username: 'wallybot' },
            defaults: {
                email:           'wallybot@bots.internal',
                password:        crypto.randomBytes(20).toString('hex'),
                first_name:      'WallyBot',
                last_name:       '',
                about:           'Офіційний помічник WallyMates. /guide — гід, /about — опис додатку, /newbot — створити бота.',
                type:            'bot',
                active:          '1',
                verified:        '0',
                lastseen:        now,
                registered:      new Date().toLocaleDateString('en-US'),
                joined:          now,
                message_privacy: '0'  // любой может написать
            }
        });

        WALLYBOT_USER_ID = wallyUser.user_id;
        console.log(`[WallyBot] Wo_Users entry: user_id=${WALLYBOT_USER_ID}`);

        // ── 2. Создать/найти Wo_Bots запись ────────────────────────────────────
        const existing = await ctx.wo_bots.findOne({ where: { bot_id: WALLYBOT_ID } });

        if (!existing) {
            const token = generateBotToken(WALLYBOT_ID);
            await ctx.wo_bots.create({
                bot_id:          WALLYBOT_ID,
                owner_id:        OWNER_USER_ID,
                bot_token:       token,
                username:        'wallybot',
                display_name:    'WallyBot',
                description:     'Официальный помощник WallyMates. Создавай ботов, получай гид по приложению (/guide), задавай вопросы (/ask).',
                about:           'WallyBot — встроенный помощник мессенджера WallyMates.\n\n/guide — интерактивный гид по приложению\n/about — описание WallyMates и навигация\n/newbot — создать своего бота\n/ask — задать вопрос\n/messenger — справка по функциям',
                category:        'system',
                bot_type:        'system',
                status:          'active',
                is_public:       1,
                can_join_groups: 0,
                supports_commands: 1,
                linked_user_id:  WALLYBOT_USER_ID,
                created_at:      new Date(),
                updated_at:      new Date()
            });
            console.log(`[WallyBot] Wo_Bots entry created, token: ${token}`);
        } else if (existing.linked_user_id !== WALLYBOT_USER_ID) {
            // Always sync linked_user_id — it diverges if the Wo_Users record was
            // deleted and findOrCreate above recreated it with a new auto-increment ID.
            // The old check (!existing.linked_user_id) missed this case and left a
            // dangling foreign-key that breaks PrivateMessageController's bot lookup.
            await ctx.wo_bots.update(
                { linked_user_id: WALLYBOT_USER_ID },
                { where: { bot_id: WALLYBOT_ID } }
            );
            console.log(`[WallyBot] Wo_Bots.linked_user_id synced → ${WALLYBOT_USER_ID}`);
        }

        // ── 3. Команды WallyBot ──────────────────────────────────────────────
        await registerDefaultCommands(ctx, WALLYBOT_ID);
        await ensureMessengerKnowledgeBase(ctx);

        const extraCommands = [
            { command: 'tour',        description: 'Краткий тур по возможностям WallyBot',        sort_order: 3  },
            { command: 'templates',   description: 'Готовые шаблоны для быстрого старта',          sort_order: 4  },
            { command: 'newbot',      description: 'Создать нового бота',                          sort_order: 5  },
            { command: 'mybots',      description: 'Список моих ботов',                            sort_order: 6  },
            { command: 'editbot',     description: 'Редактировать бота',                           sort_order: 7  },
            { command: 'deletebot',   description: 'Удалить бота',                                 sort_order: 8  },
            { command: 'token',       description: 'Получить токен бота',                          sort_order: 9  },
            { command: 'setcommands', description: 'Установить команды бота',                      sort_order: 10 },
            { command: 'setdesc',     description: 'Изменить описание бота',                       sort_order: 11 },
            { command: 'setinline',   description: 'Вкл/выкл inline-режим бота',                   sort_order: 12 },
            { command: 'setgroups',   description: 'Разрешить/запретить бота в группах',           sort_order: 13 },
            { command: 'revoke',      description: 'Отозвать и заменить токен бота',               sort_order: 14 },
            { command: 'broadcast',   description: 'Отправить сообщение всем пользователям',       sort_order: 15 },
            { command: 'stats',       description: 'Статистика (@botname для конкретного бота)',    sort_order: 16 },
            { command: 'learn',       description: 'Обучить WallyBot новому ответу',               sort_order: 17 },
            { command: 'forget',      description: 'Удалить ответ из базы знаний',                 sort_order: 18 },
            { command: 'ask',         description: 'Задать вопрос WallyBot',                       sort_order: 19 },
            { command: 'messenger',   description: 'Справка по функциям мессенджера',              sort_order: 20 },
            { command: 'about',       description: 'Описание WallyMates — что где найти',           sort_order: 21 },
            { command: 'checkbot',    description: 'Диагностика бота (@username)',                  sort_order: 22 },
            { command: 'rss',         description: 'Управление RSS-лентами бота',                   sort_order: 23 }
        ];

        for (const cmd of extraCommands) {
            await ctx.wo_bot_commands.findOrCreate({
                where:    { bot_id: WALLYBOT_ID, command: cmd.command },
                defaults: { bot_id: WALLYBOT_ID, ...cmd, scope: 'all', is_hidden: 0 }
            });
        }

        // ── 4. Регистрируем внутренний сокет WallyBot ───────────────────────
        if (ctx.botSockets) {
            ctx.botSockets.set(WALLYBOT_ID, {
                isInternal: true,
                botId:      WALLYBOT_ID,
                emit: (event, data) => {
                    if (event === 'user_message') {
                        handleMessage(ctx, io, data).catch(err =>
                            console.error('[WallyBot/handleMessage]', err.message)
                        );
                    }
                    // Нажатие inline-кнопки — BotCallbackQueryController отправляет event='callback_query'
                    // с полем data.data (строка callback_data) и data.user_id
                    if (event === 'callback_query') {
                        handleMessage(ctx, io, {
                            user_id:           data.user_id,
                            text:              '',
                            callback_data:     data.data,
                            callback_query_id: data.callback_query_id
                        }).catch(err =>
                            console.error('[WallyBot/callback_query]', err.message)
                        );
                    }
                }
            });
        }

        // ── 5. Регистрируем дефолтный шелл для ботов без вебхука ─────────────
        // PrivateMessageController вызывает ctx.defaultBotShell когда бот не имеет
        // webhook и не зарегистрирован в ctx.botSockets.
        ctx.defaultBotShell = (bot, userId, text, isCommand, cmdParts) =>
            handleDefaultBot(ctx, io, bot, userId, text, isCommand, cmdParts)
                .catch(e => console.error('[BotShell]', e.message));

        console.log(`[WallyBot] Ready! Bot ID: ${WALLYBOT_ID}, Wo_Users ID: ${WALLYBOT_USER_ID}`);
        console.log(`[WallyBot] Users can find WallyBot by searching "@wallybot" in the app`);

    } catch (err) {
        console.error('[WallyBot/init]', err.message);
    }
}

module.exports = { initializeWallyBot, WALLYBOT_ID };
