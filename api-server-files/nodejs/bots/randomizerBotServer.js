'use strict';

/**
 * randomizerBotServer — встроенный демо-бот RandBot
 *
 * Работает как WallyBot: запускается внутри серверного процесса,
 * автоматически регистрируется в БД (is_public=1) и сразу появляется
 * в каталоге ботов (Bot Store) без отдельного процесса.
 *
 * Внешний шаблон для разработчиков: bots/randomizerBot.js
 */

const crypto = require('crypto');

// ─── Идентификаторы ────────────────────────────────────────────────────────────

const RANDBOT_ID      = 'bot_randomizerbot_001';
const RANDBOT_NAME    = 'RandBot';
const RANDBOT_USERNAME = 'randomizerbot';
const OWNER_USER_ID   = 1;

let RANDBOT_USER_ID = null; // заполняется при инициализации

// ─── Контент ──────────────────────────────────────────────────────────────────

const MAGIC8_ANSWERS = [
    '✅ Определённо да',
    '✅ Без сомнений',
    '✅ Да, конечно',
    '✅ Рассчитывай на это',
    '✅ Скорее всего',
    '✅ Хорошие перспективы',
    '✅ Знаки указывают на да',
    '🤔 Спроси снова позже',
    '🤔 Лучше сейчас не говорить',
    '🤔 Сложно сказать',
    '🤔 Сконцентрируйся и спроси снова',
    '🤔 Не предсказуемо',
    '❌ Не рассчитывай на это',
    '❌ Мой ответ — нет',
    '❌ Мои источники говорят нет',
    '❌ Перспективы невесёлые',
    '❌ Весьма сомнительно',
];

const COIN_SIDES = [
    { text: 'Орёл!',  emoji: '🦅' },
    { text: 'Решка!', emoji: '🌟' },
];

// ─── Вспомогательные функции ──────────────────────────────────────────────────

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function generateBotToken(botId) {
    const rand = crypto.randomBytes(32).toString('hex');
    const hash = crypto.createHmac('sha256', botId).update(rand).digest('hex');
    return `${botId}:${hash}`;
}

// ─── Inline-клавиатура ────────────────────────────────────────────────────────

function ik(rows) {
    return { inline_keyboard: rows };
}

function btn(text, callbackData) {
    return { text, callback_data: callbackData };
}

function mainKeyboard() {
    return ik([
        [btn('🎲 Кубик d6', 'rd_dice_6'),    btn('🪙 Монета',   'rd_flip')],
        [btn('🔢 Число 1–100', 'rd_rand100'), btn('🎱 Шар',      'rd_magic8')],
        [btn('❓ Помощь',   'rd_help'),        btn('🎰 Кубик d20','rd_dice_20')],
    ]);
}

function againMenu(againData) {
    return ik([
        [btn('🔄 Ещё раз', againData), btn('🏠 Меню', 'rd_menu')],
    ]);
}

// ─── Отправка сообщения ───────────────────────────────────────────────────────

async function send(ctx, io, userId, text, replyMarkup = null) {
    try {
        const now = Math.floor(Date.now() / 1000);

        const botMsg = await ctx.wo_bot_messages.create({
            bot_id:       RANDBOT_ID,
            chat_id:      String(userId),
            chat_type:    'private',
            direction:    'outgoing',
            text,
            reply_markup: replyMarkup ? JSON.stringify(replyMarkup) : null,
            processed:    1,
            processed_at: new Date()
        });

        if (RANDBOT_USER_ID) {
            try {
                const woMsg = await ctx.wo_messages.create({
                    from_id: RANDBOT_USER_ID,
                    to_id:   userId,
                    text,
                    seen:    0,
                    time:    now
                });

                if (io) {
                    io.to(String(userId)).emit('private_message', {
                        status:         200,
                        id:             woMsg.id,
                        from_id:        RANDBOT_USER_ID,
                        to_id:          userId,
                        text,
                        message:        text,
                        message_id:     woMsg.id,
                        time:           now,
                        time_api:       now,
                        messages_html:  '',
                        message_page_html: '',
                        username:       RANDBOT_NAME,
                        avatar:         'upload/photos/d-avatar.jpg',
                        receiver:       userId,
                        sender:         RANDBOT_USER_ID,
                        cipher_version: 1,
                        media:          '',
                        isMedia:        false,
                        isRecord:       false,
                        reply_markup:   replyMarkup || null,
                        bot_id:         RANDBOT_ID
                    });
                }
            } catch (e) {
                console.warn('[RandBot/Wo_Messages]', e.message);
            }
        }

        if (io) {
            io.to(String(userId)).emit('bot_message', {
                event:        'bot_message',
                bot_id:       RANDBOT_ID,
                message_id:   botMsg.id,
                text,
                reply_markup: replyMarkup,
                timestamp:    Date.now()
            });
        }

        await ctx.wo_bots.increment('messages_sent', { where: { bot_id: RANDBOT_ID } });
    } catch (err) {
        console.error('[RandBot/send]', err.message);
    }
}

// ─── Обработчики команд ───────────────────────────────────────────────────────

async function cmdStart(ctx, io, userId) {
    const text =
        `🎲 Привет! Я RandBot — генератор случайностей!\n\n` +
        `Умею:\n` +
        `• 🔢 Генерировать случайные числа\n` +
        `• 🪙 Подбрасывать монету\n` +
        `• 🎲 Бросать кубики (d6, d20 и любые другие)\n` +
        `• 🎱 Отвечать как Магический шар\n` +
        `• 🃏 Выбирать случайно из твоих вариантов\n\n` +
        `Выбери действие 👇`;
    await send(ctx, io, userId, text, mainKeyboard());
}

async function cmdHelp(ctx, io, userId) {
    const text =
        `❓ Команды RandBot:\n\n` +
        `🔢 /random — случайное число 1–100\n` +
        `   /random 1 6 — число в своём диапазоне\n` +
        `🪙 /flip — подбросить монету\n` +
        `🎲 /dice — бросок d6\n` +
        `   /dice 20 — бросок d20\n` +
        `🃏 /choose вариант1 вариант2 ... — выбрать один\n` +
        `🎱 /magic8 вопрос? — Магический шар\n\n` +
        `Или используй кнопки 👇`;
    await send(ctx, io, userId, text, mainKeyboard());
}

async function cmdRandom(ctx, io, userId, args) {
    const parts = (args || '').trim().split(/\s+/).map(Number).filter(n => !isNaN(n) && isFinite(n));
    let min = 1, max = 100;
    if (parts.length >= 2) {
        [min, max] = [Math.min(parts[0], parts[1]), Math.max(parts[0], parts[1])];
    } else if (parts.length === 1) {
        max = Math.abs(parts[0]) || 100;
    }
    if (max - min > 1_000_000) {
        return send(ctx, io, userId, '❌ Слишком большой диапазон. Максимум 1 000 000.');
    }
    const result = randomInt(min, max);
    await send(ctx, io, userId,
        `🔢 Случайное число от ${min} до ${max}:\n\n${result}`,
        againMenu(`rd_rand_${min}_${max}`)
    );
}

async function cmdFlip(ctx, io, userId) {
    const side = pick(COIN_SIDES);
    await send(ctx, io, userId,
        `🪙 Подбрасываю монету...\n\n${side.emoji} ${side.text}`,
        againMenu('rd_flip')
    );
}

async function cmdDice(ctx, io, userId, args) {
    const sides  = Math.min(Math.max(parseInt(args) || 6, 2), 1000);
    const result = randomInt(1, sides);
    const emoji  = sides <= 6 ? '🎲' : sides <= 20 ? '🎰' : '🎱';
    await send(ctx, io, userId,
        `${emoji} Бросок d${sides}:\n\n${result} из ${sides}`,
        ik([
            [btn(`🔄 d${sides} снова`, `rd_dice_${sides}`), btn('🎲 d6', 'rd_dice_6')],
            [btn('🎰 d20', 'rd_dice_20'),                   btn('🏠 Меню', 'rd_menu')],
        ])
    );
}

async function cmdChoose(ctx, io, userId, args) {
    const options = (args || '').split(/[,;\s]+/).map(s => s.trim()).filter(Boolean);
    if (options.length < 2) {
        return send(ctx, io, userId,
            '❌ Нужно минимум 2 варианта.\nПример: /choose пицца суши бургер'
        );
    }
    const chosen = pick(options);
    const list   = options.map(o => (o === chosen ? `👉 ${o}` : `○ ${o}`)).join('\n');
    await send(ctx, io, userId,
        `🃏 Выбираю из ${options.length} вариантов...\n\n${list}`,
        againMenu(`rd_choose_${options.join('|')}`)
    );
}

async function cmdMagic8(ctx, io, userId, args) {
    const question = (args || '').trim();
    const answer   = pick(MAGIC8_ANSWERS);
    const text     = question
        ? `🎱 Вопрос: ${question}\n\n${answer}`
        : `🎱 Магический шар говорит:\n\n${answer}`;
    await send(ctx, io, userId, text, againMenu('rd_magic8'));
}

// ─── Обработчик callback_query ────────────────────────────────────────────────

async function handleCallback(ctx, io, userId, data) {
    if (data === 'rd_menu') return cmdStart(ctx, io, userId);
    if (data === 'rd_help') return cmdHelp(ctx, io, userId);
    if (data === 'rd_flip') return cmdFlip(ctx, io, userId);
    if (data === 'rd_magic8') return cmdMagic8(ctx, io, userId, '');
    if (data === 'rd_rand100') return cmdRandom(ctx, io, userId, '');

    if (data.startsWith('rd_rand_')) {
        const parts = data.replace('rd_rand_', '').split('_').map(Number);
        const [min, max] = parts.length >= 2 ? parts : [1, parts[0] || 100];
        return cmdRandom(ctx, io, userId, `${min} ${max}`);
    }

    if (data.startsWith('rd_dice_')) {
        const sides = data.replace('rd_dice_', '');
        return cmdDice(ctx, io, userId, sides);
    }

    if (data.startsWith('rd_choose_')) {
        const options = data.replace('rd_choose_', '').split('|').filter(Boolean);
        return cmdChoose(ctx, io, userId, options.join(' '));
    }
}

// ─── Основной диспетчер сообщений ─────────────────────────────────────────────

async function handleMessage(ctx, io, payload) {
    const userId       = payload.user_id;
    const text         = (payload.text || '').trim();
    const callbackData = payload.callback_data || null;

    if (!userId) return;

    // Callback-кнопка
    if (callbackData) {
        return handleCallback(ctx, io, userId, callbackData);
    }

    // Команды
    if (text.startsWith('/')) {
        const [cmd, ...rest] = text.slice(1).split(/\s+/);
        const cmdClean = cmd.toLowerCase().replace(/@\S+$/, '');
        const args     = rest.join(' ');

        if (cmdClean === 'start')   return cmdStart(ctx, io, userId);
        if (cmdClean === 'help')    return cmdHelp(ctx, io, userId);
        if (cmdClean === 'random')  return cmdRandom(ctx, io, userId, args);
        if (cmdClean === 'flip')    return cmdFlip(ctx, io, userId);
        if (cmdClean === 'dice')    return cmdDice(ctx, io, userId, args);
        if (cmdClean === 'choose')  return cmdChoose(ctx, io, userId, args);
        if (cmdClean === 'magic8')  return cmdMagic8(ctx, io, userId, args);

        return send(ctx, io, userId,
            `Неизвестная команда. Попробуй /help или кнопки ниже 👇`,
            mainKeyboard()
        );
    }

    // Текст, заканчивающийся на ? — Магический шар
    if (text.endsWith('?') && text.length > 4) {
        return cmdMagic8(ctx, io, userId, text);
    }

    // Fallback
    await send(ctx, io, userId,
        `Выбери действие или введи команду 👇`,
        mainKeyboard()
    );
}

// ─── Инициализация ────────────────────────────────────────────────────────────

async function initializeRandomizerBot(ctx, io) {
    try {
        const now = Math.floor(Date.now() / 1000);

        // 1. Создать/найти Wo_Users
        const [botUser] = await ctx.wo_users.findOrCreate({
            where: { username: RANDBOT_USERNAME },
            defaults: {
                email:           `${RANDBOT_USERNAME}@bots.internal`,
                password:        crypto.randomBytes(20).toString('hex'),
                first_name:      RANDBOT_NAME,
                last_name:       '',
                about:           'Генератор случайностей: числа, монета, кубики, Магический шар.',
                type:            'bot',
                active:          '1',
                verified:        '0',
                lastseen:        now,
                registered:      new Date().toLocaleDateString('en-US'),
                joined:          now,
                message_privacy: '0'
            }
        });

        RANDBOT_USER_ID = botUser.user_id;
        console.log(`[RandBot] Wo_Users entry: user_id=${RANDBOT_USER_ID}`);

        // 2. Создать/найти Wo_Bots
        const existing = await ctx.wo_bots.findOne({ where: { bot_id: RANDBOT_ID } });
        if (!existing) {
            const token = generateBotToken(RANDBOT_ID);
            await ctx.wo_bots.create({
                bot_id:            RANDBOT_ID,
                owner_id:          OWNER_USER_ID,
                bot_token:         token,
                username:          RANDBOT_USERNAME,
                display_name:      RANDBOT_NAME,
                description:       'Генератор случайностей: числа, монета, кубики, Магический шар 🎱',
                about:             'RandBot умеет:\n• 🔢 Случайные числа (/random)\n• 🪙 Монета (/flip)\n• 🎲 Кубики (/dice)\n• 🃏 Выбор из вариантов (/choose)\n• 🎱 Магический шар (/magic8)\n\nПример внешнего бота на WorldMatesBotSDK.',
                category:          'entertainment',
                bot_type:          'external',
                status:            'active',
                is_public:         1,
                can_join_groups:   0,
                supports_commands: 1,
                linked_user_id:    RANDBOT_USER_ID,
                created_at:        new Date(),
                updated_at:        new Date()
            });
            console.log(`[RandBot] Wo_Bots entry created`);
        } else if (existing.linked_user_id !== RANDBOT_USER_ID) {
            await ctx.wo_bots.update(
                { linked_user_id: RANDBOT_USER_ID },
                { where: { bot_id: RANDBOT_ID } }
            );
            console.log(`[RandBot] linked_user_id synced → ${RANDBOT_USER_ID}`);
        }

        // 3. Команды
        const commands = [
            { command: 'start',  description: 'Начать / главное меню',                   sort_order: 1 },
            { command: 'help',   description: 'Список команд',                            sort_order: 2 },
            { command: 'random', description: 'Случайное число (пример: /random 1 100)',  sort_order: 3 },
            { command: 'flip',   description: 'Подбросить монету 🪙',                      sort_order: 4 },
            { command: 'dice',   description: 'Бросок кубика (пример: /dice 20)',         sort_order: 5 },
            { command: 'choose', description: 'Выбрать вариант: /choose а б в',           sort_order: 6 },
            { command: 'magic8', description: 'Магический шар 🎱',                         sort_order: 7 },
        ];
        for (const cmd of commands) {
            await ctx.wo_bot_commands.findOrCreate({
                where:    { bot_id: RANDBOT_ID, command: cmd.command },
                defaults: { bot_id: RANDBOT_ID, ...cmd, scope: 'all', is_hidden: 0 }
            });
        }

        // 4. Внутренний сокет
        if (ctx.botSockets) {
            ctx.botSockets.set(RANDBOT_ID, {
                isInternal: true,
                botId:      RANDBOT_ID,
                emit: (event, data) => {
                    if (event === 'user_message') {
                        handleMessage(ctx, io, data).catch(err =>
                            console.error('[RandBot/handleMessage]', err.message)
                        );
                    }
                    if (event === 'callback_query') {
                        handleMessage(ctx, io, {
                            user_id:           data.user_id,
                            text:              '',
                            callback_data:     data.data,
                            callback_query_id: data.callback_query_id
                        }).catch(err =>
                            console.error('[RandBot/callback_query]', err.message)
                        );
                    }
                }
            });
        }

        console.log(`[RandBot] Ready! Bot ID: ${RANDBOT_ID}, Wo_Users ID: ${RANDBOT_USER_ID}`);
        console.log(`[RandBot] Find in app by searching "@${RANDBOT_USERNAME}"`);

    } catch (err) {
        console.error('[RandBot/init]', err.message);
    }
}

module.exports = { initializeRandomizerBot, RANDBOT_ID };
