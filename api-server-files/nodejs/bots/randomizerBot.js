'use strict';

/**
 * randomizerBot — пример внешнего бота для WallyMates
 *
 * Демонстрирует полный цикл работы внешнего бота через WorldMatesBotSDK:
 *   1. Создай бота через WallyBot (/newbot в чате с @wallybot)
 *   2. Скопируй полученные bot_id и token в конфиг ниже
 *   3. Запусти: node randomizerBot.js
 *
 * Команды бота:
 *   /start   — приветствие
 *   /help    — список команд
 *   /random  — случайное число (диапазон через аргументы: /random 1 100)
 *   /flip    — орёл или решка
 *   /dice    — бросок кубика (1d6, /dice 20 для d20)
 *   /choose  — случайный выбор из вариантов: /choose вариант1 вариант2 вариант3
 *   /magic8  — Магический шар 8 (да/нет ответы)
 *
 * Режимы работы:
 *   - Polling (по умолчанию): бот сам спрашивает сервер о новых сообщениях
 *   - Webhook: сервер присылает сообщения на твой HTTPS-сервер (раскомментируй ниже)
 */

const path = require('path');
const { WorldMatesBot } = require(path.join(__dirname, '../bot-sdk/WorldMatesBotSDK'));

// ─── Конфигурация ─────────────────────────────────────────────────────────────
// Замени значения на свои после создания бота через /newbot в @wallybot

const CONFIG = {
    botId:  process.env.BOT_ID  || 'bot_ваш_id_здесь',
    token:  process.env.BOT_TOKEN || 'bot_ваш_id_здесь:ваш_токен_здесь',
    apiUrl: process.env.API_URL || 'http://localhost:449',  // или https://worldmates.club
    debug:  process.env.DEBUG === '1',
};

// ─── Инициализация ─────────────────────────────────────────────────────────────

const bot = new WorldMatesBot(CONFIG);

// ─── Контент ──────────────────────────────────────────────────────────────────

const MAGIC8_ANSWERS = [
    // Позитивные
    '✅ Определённо да',
    '✅ Без сомнений',
    '✅ Да, конечно',
    '✅ Рассчитывай на это',
    '✅ Скорее всего',
    '✅ Хорошие перспективы',
    '✅ Знаки указывают на да',
    // Нейтральные
    '🤔 Спроси снова позже',
    '🤔 Лучше сейчас не говорить',
    '🤔 Сложно сказать',
    '🤔 Сконцентрируйся и спроси снова',
    '🤔 Не предсказуемо',
    // Негативные
    '❌ Не рассчитывай на это',
    '❌ Мой ответ — нет',
    '❌ Мои источники говорят нет',
    '❌ Перспективы невесёлые',
    '❌ Весьма сомнительно',
];

const COIN_SIDES = [
    { text: '🪙 Орёл!',   emoji: '🦅' },
    { text: '🪙 Решка!',  emoji: '🌟' },
];

// ─── Вспомогательные функции ──────────────────────────────────────────────────

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function buildMainKeyboard() {
    return WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('🎲 Кубик',    'dice_6'),
        WorldMatesBot.callbackButton('🪙 Монета',   'flip'),
        WorldMatesBot.callbackButton('🔢 1–100',    'random_100'),
        WorldMatesBot.callbackButton('🎱 Шар',      'magic8'),
        WorldMatesBot.callbackButton('❓ Помощь',   'help'),
    ], 2);
}

// ─── Обработчики команд ───────────────────────────────────────────────────────

bot.onCommand('start', async ({ message, bot }) => {
    const userId = message.from.id;
    const text =
        `🎲 *Привет! Я RandBot — генератор случайностей!*\n\n` +
        `Умею:\n` +
        `• 🔢 Генерировать случайные числа\n` +
        `• 🪙 Подбрасывать монету\n` +
        `• 🎲 Бросать кубики (d6, d20...)\n` +
        `• 🎱 Отвечать как Магический шар\n` +
        `• 🃏 Выбирать случайно из вариантов\n\n` +
        `Выбери действие или напиши /help:`;

    await bot.sendMessageWithKeyboard(userId, text, buildMainKeyboard());
});

bot.onCommand('help', async ({ message, bot }) => {
    const userId = message.from.id;
    const text =
        `❓ *Команды RandBot:*\n\n` +
        `🔢 */random* — случайное число 1–100\n` +
        `   */random 1 6* — случайное число в диапазоне\n` +
        `🪙 */flip* — подбросить монету\n` +
        `🎲 */dice* — бросок d6 (кубика)\n` +
        `   */dice 20* — бросок d20\n` +
        `🃏 */choose* вариант1 вариант2 ... — выбор из вариантов\n` +
        `🎱 */magic8* вопрос — Магический шар\n\n` +
        `Или используй кнопки внизу 👇`;

    await bot.sendMessageWithKeyboard(userId, text, buildMainKeyboard());
});

bot.onCommand('random', async ({ message, args, bot }) => {
    const userId = message.from.id;
    const parts  = (args || '').trim().split(/\s+/).map(Number).filter(n => !isNaN(n) && isFinite(n));

    let min = 1, max = 100;
    if (parts.length >= 2) {
        [min, max] = [Math.min(parts[0], parts[1]), Math.max(parts[0], parts[1])];
    } else if (parts.length === 1) {
        max = Math.abs(parts[0]) || 100;
    }

    if (max - min > 1_000_000) {
        return bot.sendMessage(userId, '❌ Слишком большой диапазон. Максимум 1 000 000.');
    }

    const result = randomInt(min, max);
    const text   = `🎲 Случайное число от *${min}* до *${max}*:\n\n🔢 *${result}*`;
    await bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('🔄 Ещё раз', `random_${min}_${max}`),
        WorldMatesBot.callbackButton('🏠 Меню',     'menu'),
    ]));
});

bot.onCommand('flip', async ({ message, bot }) => {
    const userId = message.from.id;
    const side   = pick(COIN_SIDES);
    const text   = `🪙 Подбрасываю монету...\n\n${side.emoji} *${side.text}*`;
    await bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('🔄 Ещё раз', 'flip'),
        WorldMatesBot.callbackButton('🏠 Меню',    'menu'),
    ]));
});

bot.onCommand('dice', async ({ message, args, bot }) => {
    const userId = message.from.id;
    const sides  = Math.min(Math.max(parseInt(args) || 6, 2), 1000);
    const result = randomInt(1, sides);

    const diceEmoji = sides <= 6 ? '🎲' : sides <= 20 ? '🎰' : '🎱';
    const text = `${diceEmoji} Бросок d${sides}:\n\n🔢 *${result}* из ${sides}`;

    await bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton(`🔄 d${sides} снова`,  `dice_${sides}`),
        WorldMatesBot.callbackButton('🎲 d6',               'dice_6'),
        WorldMatesBot.callbackButton('🎰 d20',              'dice_20'),
        WorldMatesBot.callbackButton('🏠 Меню',             'menu'),
    ]));
});

bot.onCommand('choose', async ({ message, args, bot }) => {
    const userId  = message.from.id;
    const options = (args || '').split(/[,;\s]+/).map(s => s.trim()).filter(Boolean);

    if (options.length < 2) {
        return bot.sendMessage(userId,
            '❌ Укажи минимум 2 варианта через пробел, запятую или точку с запятой.\n' +
            'Пример: /choose пицца суши бургер'
        );
    }

    const chosen = pick(options);
    const optionsList = options.map((o, i) => (o === chosen ? `👉 *${o}*` : `○ ${o}`)).join('\n');
    const text = `🃏 Выбираю из ${options.length} вариантов...\n\n${optionsList}`;

    await bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('🔄 Выбрать снова', `choose_${options.join('|')}`),
        WorldMatesBot.callbackButton('🏠 Меню',          'menu'),
    ]));
});

bot.onCommand('magic8', async ({ message, args, bot }) => {
    const userId   = message.from.id;
    const question = (args || '').trim();
    const answer   = pick(MAGIC8_ANSWERS);

    const text = question
        ? `🎱 *Вопрос:* ${question}\n\n${answer}`
        : `🎱 *Магический шар говорит:*\n\n${answer}`;

    await bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('🔄 Спросить снова', 'magic8'),
        WorldMatesBot.callbackButton('🏠 Меню',           'menu'),
    ]));
});

// ─── Обработчик кнопок (callback_query) ───────────────────────────────────────

bot.onCallbackQuery(async ({ callback, bot }) => {
    const userId = callback.from?.id;
    const data   = callback.data || '';

    if (!userId) return;

    if (data === 'menu') {
        const text = `🎲 Главное меню RandBot — выбери действие:`;
        return bot.sendMessageWithKeyboard(userId, text, buildMainKeyboard());
    }

    if (data === 'help') {
        const text =
            `❓ *Команды RandBot:*\n\n` +
            `🔢 /random — случайное число\n` +
            `🪙 /flip — монета\n` +
            `🎲 /dice — кубик\n` +
            `🃏 /choose вариант1 вариант2 — выбор\n` +
            `🎱 /magic8 вопрос — Магический шар`;
        return bot.sendMessageWithKeyboard(userId, text, buildMainKeyboard());
    }

    if (data === 'flip') {
        const side = pick(COIN_SIDES);
        const text = `🪙 ${side.emoji} *${side.text}*`;
        return bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton('🔄 Ещё', 'flip'),
            WorldMatesBot.callbackButton('🏠 Меню', 'menu'),
        ]));
    }

    if (data === 'magic8') {
        const answer = pick(MAGIC8_ANSWERS);
        const text   = `🎱 *Магический шар:*\n\n${answer}`;
        return bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton('🔄 Ещё', 'magic8'),
            WorldMatesBot.callbackButton('🏠 Меню', 'menu'),
        ]));
    }

    if (data === 'random_100') {
        const result = randomInt(1, 100);
        const text   = `🔢 Случайное число 1–100:\n\n*${result}*`;
        return bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton('🔄 Ещё',  'random_100'),
            WorldMatesBot.callbackButton('🏠 Меню', 'menu'),
        ]));
    }

    // random_MIN_MAX
    if (data.startsWith('random_')) {
        const parts  = data.replace('random_', '').split('_').map(Number);
        const [min, max] = parts.length >= 2 ? parts : [1, parts[0] || 100];
        const result = randomInt(min, max);
        const text   = `🔢 Число от *${min}* до *${max}*:\n\n*${result}*`;
        return bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton('🔄 Ещё',  data),
            WorldMatesBot.callbackButton('🏠 Меню', 'menu'),
        ]));
    }

    // dice_SIDES
    if (data.startsWith('dice_')) {
        const sides  = Math.min(Math.max(parseInt(data.replace('dice_', '')) || 6, 2), 1000);
        const result = randomInt(1, sides);
        const emoji  = sides <= 6 ? '🎲' : sides <= 20 ? '🎰' : '🎱';
        const text   = `${emoji} d${sides}: *${result}* из ${sides}`;
        return bot.sendMessageWithKeyboard(userId, text, WorldMatesBot.buildInlineKeyboard([
            WorldMatesBot.callbackButton(`🔄 d${sides}`, data),
            WorldMatesBot.callbackButton('🎲 d6',        'dice_6'),
            WorldMatesBot.callbackButton('🎰 d20',       'dice_20'),
            WorldMatesBot.callbackButton('🏠 Меню',      'menu'),
        ]));
    }

    // choose_option1|option2|...
    if (data.startsWith('choose_')) {
        const options = data.replace('choose_', '').split('|').filter(Boolean);
        if (options.length < 2) return;
        const chosen = pick(options);
        const list   = options.map(o => (o === chosen ? `👉 *${o}*` : `○ ${o}`)).join('\n');
        return bot.sendMessageWithKeyboard(userId,
            `🃏 Выбор:\n\n${list}`,
            WorldMatesBot.buildInlineKeyboard([
                WorldMatesBot.callbackButton('🔄 Снова', data),
                WorldMatesBot.callbackButton('🏠 Меню',  'menu'),
            ])
        );
    }
});

// ─── Обработчик всех текстовых сообщений (fallback) ──────────────────────────

bot.onMessage(async ({ message, bot }) => {
    const userId = message.from.id;
    const text   = (message.text || '').trim();

    // Если пользователь написал что-то похожее на вопрос для Магического шара
    if (text.endsWith('?') && text.length > 5) {
        const answer = pick(MAGIC8_ANSWERS);
        return bot.sendMessageWithKeyboard(userId,
            `🎱 *Магический шар отвечает:*\n\n${answer}`,
            WorldMatesBot.buildInlineKeyboard([
                WorldMatesBot.callbackButton('🔄 Ещё', 'magic8'),
                WorldMatesBot.callbackButton('🏠 Меню', 'menu'),
            ])
        );
    }

    // Fallback — подсказка
    await bot.sendMessageWithKeyboard(userId,
        `Попробуй команду или выбери действие:`,
        buildMainKeyboard()
    );
});

// ─── Обработчик ошибок ────────────────────────────────────────────────────────

bot.on('error', (err) => {
    console.error(`[RandBot] Ошибка:`, err.message);
});

// ─── Регистрация команд на сервере ────────────────────────────────────────────

async function registerCommands() {
    const result = await bot.setCommands([
        { command: 'start',  description: 'Начать / главное меню' },
        { command: 'help',   description: 'Список команд' },
        { command: 'random', description: 'Случайное число (пример: /random 1 100)' },
        { command: 'flip',   description: 'Подбросить монету 🪙' },
        { command: 'dice',   description: 'Бросок кубика (пример: /dice 20)' },
        { command: 'choose', description: 'Выбрать вариант: /choose а б в' },
        { command: 'magic8', description: 'Магический шар 🎱: /magic8 Всё будет хорошо?' },
    ]).catch(() => null);

    if (result?.api_status === 200) {
        console.log('[RandBot] Команды зарегистрированы');
    }
}

// ─── Запуск ────────────────────────────────────────────────────────────────────

async function main() {
    console.log(`[RandBot] Запуск...`);
    console.log(`[RandBot] Bot ID : ${CONFIG.botId}`);
    console.log(`[RandBot] API URL: ${CONFIG.apiUrl}`);

    // Проверяем подключение
    const me = await bot.getMe().catch(() => null);
    if (!me || me.api_status !== 200) {
        console.error('[RandBot] Не удалось получить информацию о боте.');
        console.error('[RandBot] Проверь BOT_ID и BOT_TOKEN в конфигурации.');
        console.error('[RandBot] Создай бота: напиши /newbot в чат @wallybot');
        process.exit(1);
    }
    console.log(`[RandBot] Подключён как @${me.username || CONFIG.botId}`);

    // Регистрируем команды
    await registerCommands();

    // ── Режим polling (рекомендуется для локального запуска) ────────────────
    // Бот будет спрашивать сервер о новых сообщениях каждые 2 секунды.
    console.log('[RandBot] Запущен в режиме polling. Ctrl+C для остановки.');
    bot.start(5);   // timeout=5s (long polling)

    // ── Режим webhook (для продакшена с HTTPS) ───────────────────────────────
    // Раскомментируй следующие строки и закомментируй bot.start() выше:
    //
    // const express = require('express');
    // const app     = express();
    // app.use(express.json());
    // app.post('/webhook', bot.webhookHandler());
    // app.listen(3001, () => console.log('[RandBot] Webhook сервер на порту 3001'));
    // await bot.setWebhook('https://my-server.com/webhook');
}

main().catch(err => {
    console.error('[RandBot] Критическая ошибка:', err.message);
    process.exit(1);
});
