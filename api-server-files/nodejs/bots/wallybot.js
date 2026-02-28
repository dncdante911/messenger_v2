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
    FORGET_SELECT:      'forget_select'
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

async function sendToUser(ctx, io, userId, text, replyMarkup = null) {
    try {
        const msg = await ctx.wo_bot_messages.create({
            bot_id:       WALLYBOT_ID,
            chat_id:      String(userId),
            chat_type:    'private',
            direction:    'outgoing',
            text,
            reply_markup: replyMarkup ? JSON.stringify(replyMarkup) : null,
            processed:    1,
            processed_at: new Date()
        });

        const payload = {
            event:        'bot_message',
            bot_id:       WALLYBOT_ID,
            message_id:   msg.id,
            text,
            reply_markup: replyMarkup,
            timestamp:    Date.now()
        };

        if (io) {
            io.to(String(userId)).emit('bot_message', payload);
            io.to(`user_bot_${userId}_${WALLYBOT_ID}`).emit('bot_message', payload);
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
    const q = query.toLowerCase().trim();
    const words = q.split(/\s+/).filter(w => w.length > 2);

    if (!words.length) return null;

    // Ищем по ключевым словам
    const conditions = words.map(w => ({ title: { [Op.like]: `%${w}%` } }));
    const results = await ctx.wo_bot_tasks.findAll({
        where: {
            bot_id: WALLYBOT_ID,
            status: 'done',
            [Op.or]: conditions
        },
        raw: true
    });

    if (!results.length) return null;

    // Ранжируем по количеству совпадений
    let best = null;
    let bestScore = 0;
    for (const r of results) {
        let score = 0;
        for (const w of words) {
            if (r.title.includes(w)) score++;
        }
        if (score > bestScore) { bestScore = score; best = r; }
    }

    return bestScore > 0 ? best : null;
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
        { command: 'start',  description: 'Начать работу с ботом', sort_order: 0 },
        { command: 'help',   description: 'Помощь и список команд', sort_order: 1 },
        { command: 'cancel', description: 'Отменить действие',      sort_order: 2 }
    ];
    for (const cmd of defaults) {
        await ctx.wo_bot_commands.findOrCreate({
            where:    { bot_id: botId, command: cmd.command },
            defaults: { bot_id: botId, ...cmd, scope: 'all', is_hidden: 0 }
        });
    }
}

// ─── ОБРАБОТЧИКИ КОМАНД ───────────────────────────────────────────────────────

async function handleStart(ctx, io, userId, userName) {
    clearState(userId);
    const text = `Привет, ${userName}! Я WallyBot — твой персональный помощник по управлению ботами WorldMates.\n\nЧто ты хочешь сделать?`;
    const kb = inlineKeyboard([
        btn('Создать бота',     'cmd_newbot'),
        btn('Мои боты',         'cmd_mybots'),
        btn('Обучить меня',     'cmd_learn'),
        btn('Спросить WallyBot','cmd_ask'),
        btn('Помощь',           'cmd_help')
    ], 2);
    await sendToUser(ctx, io, userId, text, kb);
}

async function handleHelp(ctx, io, userId) {
    clearState(userId);
    const text = `*Команды WallyBot:*\n\n` +
        `/newbot — создать нового бота\n` +
        `/mybots — список твоих ботов\n` +
        `/editbot — изменить настройки бота\n` +
        `/deletebot — удалить бота\n` +
        `/token — получить/обновить токен бота\n` +
        `/setcommands — установить команды бота\n` +
        `/setdesc — установить описание бота\n\n` +
        `*База знаний WallyBot:*\n` +
        `/learn — научить меня чему-то новому\n` +
        `/forget — забыть что-то\n` +
        `/ask — задать вопрос\n\n` +
        `Просто напиши вопрос — я попробую ответить из базы знаний!`;
    await sendToUser(ctx, io, userId, text);
}

async function handleNewBot(ctx, io, userId) {
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

async function handleCancel(ctx, io, userId) {
    clearState(userId);
    await sendToUser(ctx, io, userId, 'Действие отменено. Чем могу помочь?',
        inlineKeyboard([
            btn('Создать бота', 'cmd_newbot'),
            btn('Мои боты',     'cmd_mybots'),
            btn('Помощь',       'cmd_help')
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
            btn('Все мои боты', 'cmd_mybots'),
            btn('Создать ещё',  'cmd_newbot')
        ]);

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
            `*Команды установлены!*\n\n` +
            commands.map(c => `/${c.command} — ${c.description}`).join('\n')
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
        return sendToUser(ctx, io, userId, `Описание обновлено!`);
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

    return null; // состояние не обработано
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
    if (callbackData === 'cmd_mybots')    return handleMyBots(ctx, io, userId);
    if (callbackData === 'cmd_help')      return handleHelp(ctx, io, userId);
    if (callbackData === 'cmd_cancel')    return handleCancel(ctx, io, userId);
    if (callbackData === 'cmd_learn')     return handleLearn(ctx, io, userId);
    if (callbackData === 'cmd_ask')       return handleAsk(ctx, io, userId);

    // ── Просмотр базы знаний ─────────────────────────────────────────────────
    if (callbackData === 'cmd_knowledge') {
        const list = await getKnowledgeList(ctx, 10);
        if (!list.length) return sendToUser(ctx, io, userId, 'База знаний пуста. Обучи меня через /learn!');
        const text = `*База знаний WallyBot (${list.length} записей):*\n\n` +
            list.map(item => `• *${item.title}*\n  ${item.description.substring(0, 80)}...`).join('\n\n');
        return sendToUser(ctx, io, userId, text, inlineKeyboard([
            btn('Забыть запись', 'cmd_forget'),
            btn('Добавить',      'cmd_learn')
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
            btn('Изменить', `editselect_${botId}`),
            btn('Токен',    `tokenshow_${botId}`),
            btn('Команды',  `setcmd_${botId}`),
            btn('Удалить',  `deleteconfirm_${botId}`),
            btn('Назад',    'cmd_mybots')
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
}

// ─── ГЛАВНЫЙ ДИСПЕТЧЕР СООБЩЕНИЙ ─────────────────────────────────────────────

async function handleMessage(ctx, io, data) {
    const { user_id: userId, text = '', is_command, command_name, command_args,
            callback_data, callback_query_id } = data;

    if (!userId) return;

    // Получаем имя пользователя
    let userName = 'пользователь';
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
            learn:       () => handleLearn(ctx, io, userId),
            forget:      () => handleForget(ctx, io, userId),
            ask:         () => handleAsk(ctx, io, userId),
            cancel:      () => handleCancel(ctx, io, userId)
        };

        const handler = cmdHandlers[cmd];
        if (handler) return handler();

        return sendToUser(ctx, io, userId,
            `Неизвестная команда: /${cmd}\n\nВведи /help для списка команд.`
        );
    }

    // Обработка текстовых сообщений
    if (text.trim()) {
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
                    btn('Это помогло!',    'kb_helpful_yes'),
                    btn('Не то, что нужно', 'kb_helpful_no')
                ])
            );
        }

        // Дефолтный ответ
        await sendToUser(ctx, io, userId,
            `Не знаю ответа на это.\n\nПопробуй:\n• /help — список команд\n• /newbot — создать бота\n• /learn — научи меня этому`,
            inlineKeyboard([
                btn('Главное меню', 'cmd_start'),
                btn('Помощь',       'cmd_help')
            ])
        );
    }
}

// ─── Инициализация WallyBot ───────────────────────────────────────────────────

async function initializeWallyBot(ctx, io) {
    try {
        // Проверяем существование WallyBot в базе
        const existing = await ctx.wo_bots.findOne({ where: { bot_id: WALLYBOT_ID } });

        if (!existing) {
            // Создаём WallyBot
            const token = generateBotToken(WALLYBOT_ID);
            await ctx.wo_bots.create({
                bot_id:       WALLYBOT_ID,
                owner_id:     OWNER_USER_ID,
                bot_token:    token,
                username:     'wallybot',
                display_name: 'WallyBot',
                description:  'Официальный бот-менеджер WorldMates. Создавайте своих ботов прямо в чате!',
                about:        'Помогает создавать и управлять ботами WorldMates. Также обучаем — пишите /learn!',
                category:     'system',
                bot_type:     'system',
                status:       'active',
                is_public:    1,
                can_join_groups: 0,
                supports_commands: 1,
                created_at:   new Date(),
                updated_at:   new Date()
            });

            await registerDefaultCommands(ctx, WALLYBOT_ID);

            // Дополнительные команды WallyBot
            const commands = [
                { command: 'newbot',      description: 'Создать нового бота',              sort_order: 3 },
                { command: 'mybots',      description: 'Список моих ботов',                sort_order: 4 },
                { command: 'editbot',     description: 'Редактировать бота',               sort_order: 5 },
                { command: 'deletebot',   description: 'Удалить бота',                     sort_order: 6 },
                { command: 'token',       description: 'Получить токен бота',              sort_order: 7 },
                { command: 'setcommands', description: 'Установить команды бота',          sort_order: 8 },
                { command: 'setdesc',     description: 'Изменить описание бота',           sort_order: 9 },
                { command: 'learn',       description: 'Научить WallyBot новому ответу',   sort_order: 10 },
                { command: 'forget',      description: 'Удалить ответ из базы знаний',     sort_order: 11 },
                { command: 'ask',         description: 'Задать вопрос WallyBot',           sort_order: 12 }
            ];

            for (const cmd of commands) {
                await ctx.wo_bot_commands.findOrCreate({
                    where:    { bot_id: WALLYBOT_ID, command: cmd.command },
                    defaults: { bot_id: WALLYBOT_ID, ...cmd, scope: 'all', is_hidden: 0 }
                });
            }

            console.log(`[WallyBot] Initialized with token: ${token}`);
        }

        // Регистрируем WallyBot в botSockets как "внутренний" бот
        if (ctx.botSockets) {
            ctx.botSockets.set(WALLYBOT_ID, {
                isInternal: true,
                botId:      WALLYBOT_ID,
                emit:       (event, data) => {
                    // Внутренний обработчик сообщений
                    if (event === 'user_message') {
                        handleMessage(ctx, io, data).catch(err =>
                            console.error('[WallyBot/handleMessage]', err.message)
                        );
                    }
                }
            });
        }

        console.log('[WallyBot] Ready. Bot ID:', WALLYBOT_ID);

    } catch (err) {
        console.error('[WallyBot/init]', err.message);
    }
}

module.exports = { initializeWallyBot, WALLYBOT_ID };
