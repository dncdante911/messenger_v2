'use strict';

/**
 * WorldMates Bot REST API — Node.js implementation
 *
 * Полностью заменяет PHP bot_api.php и bot_webhook_processor.php.
 * Все операции ботов выполняются на Node.js с прямым доступом к БД через Sequelize.
 *
 * ─── Управление ботами (требует access_token пользователя) ───────────────────
 *   POST   /api/node/bots            — create_bot
 *   GET    /api/node/bots            — get_my_bots
 *   GET    /api/node/bots/:bot_id    — get_bot_info
 *   PUT    /api/node/bots/:bot_id    — update_bot
 *   DELETE /api/node/bots/:bot_id    — delete_bot
 *   POST   /api/node/bots/:bot_id/regenerate-token
 *
 * ─── Операции бота (требует bot_token) ───────────────────────────────────────
 *   POST   /api/node/bot/sendMessage
 *   POST   /api/node/bot/getUpdates
 *   POST   /api/node/bot/editMessage
 *   POST   /api/node/bot/deleteMessage
 *   POST   /api/node/bot/answerCallbackQuery
 *   POST   /api/node/bot/sendPoll
 *   POST   /api/node/bot/stopPoll
 *   POST   /api/node/bot/setCommands
 *   GET    /api/node/bot/getCommands
 *   POST   /api/node/bot/setWebhook
 *   POST   /api/node/bot/deleteWebhook
 *   GET    /api/node/bot/getWebhookInfo
 *   POST   /api/node/bot/setUserState
 *   GET    /api/node/bot/getUserState
 *   GET    /api/node/bot/getMe
 *   GET    /api/node/bots/search     — публичный поиск ботов
 *
 * ─── Совместимость с PHP SDK ──────────────────────────────────────────────────
 *   POST   /api/node/bots/api        — type=xxx (legacy format)
 */

const crypto = require('crypto');
const https  = require('https');
const http   = require('http');
const { Op } = require('sequelize');

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

// ─── Middleware: auth пользователя ───────────────────────────────────────────

async function userAuth(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Bots/userAuth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Auth error' });
    }
}

// ─── Middleware: auth бота ────────────────────────────────────────────────────

async function botAuth(ctx, req, res, next) {
    const token = req.headers['bot-token']
               || req.query.bot_token
               || req.body.bot_token;

    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'bot_token required' });

    try {
        const bot = await ctx.wo_bots.findOne({ where: { bot_token: token, status: 'active' } });
        if (!bot)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid bot_token or bot is disabled' });

        req.bot = bot;
        req.botId = bot.bot_id;
        next();
    } catch (err) {
        console.error('[Bots/botAuth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Auth error' });
    }
}

// ─── Регистрация дефолтных команд ────────────────────────────────────────────

async function registerDefaultCommands(ctx, botId) {
    const defaults = [
        { command: 'start',   description: 'Начать работу с ботом',    sort_order: 0 },
        { command: 'help',    description: 'Список команд и помощь',    sort_order: 1 },
        { command: 'cancel',  description: 'Отменить текущее действие', sort_order: 2 }
    ];

    for (const cmd of defaults) {
        await ctx.wo_bot_commands.findOrCreate({
            where:    { bot_id: botId, command: cmd.command },
            defaults: { bot_id: botId, ...cmd, scope: 'all', is_hidden: 0 }
        });
    }
}

// ─── Доставка webhook ────────────────────────────────────────────────────────

function deliverWebhook(url, payload, signature, botId) {
    return new Promise((resolve) => {
        const json    = JSON.stringify(payload);
        const parsed  = new URL(url);
        const lib     = parsed.protocol === 'https:' ? https : http;
        const options = {
            method:   'POST',
            hostname: parsed.hostname,
            port:     parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
            path:     parsed.pathname + parsed.search,
            headers:  {
                'Content-Type':                   'application/json',
                'Content-Length':                 Buffer.byteLength(json),
                'X-WorldMates-Bot-Signature':     'sha256=' + signature,
                'X-WorldMates-Bot-Id':            botId,
                'User-Agent':                     'WorldMatesBot/2.0'
            },
            timeout: 10000
        };

        const req = lib.request(options, (res) => {
            let body = '';
            res.on('data', (d) => { body += d; });
            res.on('end',  () => resolve({
                success:   res.statusCode >= 200 && res.statusCode < 300,
                http_code: res.statusCode,
                response:  body.substring(0, 500)
            }));
        });

        req.on('timeout', () => { req.destroy(); resolve({ success: false, http_code: 0, response: 'timeout' }); });
        req.on('error',  (e) => resolve({ success: false, http_code: 0, response: e.message }));
        req.write(json);
        req.end();
    });
}

// ─── Обработка pending webhooks (Node.js замена PHP cron) ────────────────────

async function processWebhooks(ctx) {
    try {
        const bots = await ctx.wo_bots.findAll({
            where:      { webhook_enabled: 1, status: 'active' },
            attributes: ['bot_id', 'webhook_url', 'webhook_secret', 'webhook_allowed_updates'],
            raw:        true
        });

        for (const bot of bots) {
            if (!bot.webhook_url) continue;

            const messages = await ctx.wo_bot_messages.findAll({
                where:   { bot_id: bot.bot_id, direction: 'incoming', processed: 0 },
                limit:   50,
                order:   [['id', 'ASC']],
                raw:     true
            });

            for (const msg of messages) {
                // Загружаем данные пользователя
                const user = await ctx.wo_users.findOne({
                    where:      { user_id: parseInt(msg.chat_id) || 0 },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar'],
                    raw:        true
                });

                const updateType = msg.callback_data ? 'callback_query' : (msg.is_command ? 'command' : 'message');

                const allowed = bot.webhook_allowed_updates
                    ? JSON.parse(bot.webhook_allowed_updates)
                    : null;

                // Помечаем как обработанный независимо от фильтров
                await ctx.wo_bot_messages.update(
                    { processed: 1, processed_at: new Date() },
                    { where: { id: msg.id } }
                );

                if (allowed && !allowed.includes(updateType)) continue;

                const payload = buildWebhookPayload(msg, bot.bot_id, user);
                const sig     = crypto.createHmac('sha256', bot.webhook_secret || '')
                                      .update(JSON.stringify(payload)).digest('hex');

                const result  = await deliverWebhook(bot.webhook_url, payload, sig, bot.bot_id);

                await ctx.wo_bot_webhook_log.create({
                    bot_id:          bot.bot_id,
                    event_type:      updateType,
                    payload:         JSON.stringify(payload),
                    webhook_url:     bot.webhook_url,
                    response_code:   result.http_code,
                    response_body:   result.response,
                    delivery_status: result.success ? 'delivered' : 'failed',
                    attempts:        1,
                    delivered_at:    result.success ? new Date() : null,
                    created_at:      new Date()
                });
            }
        }
    } catch (err) {
        console.error('[Bots/processWebhooks]', err.message);
    }
}

function buildWebhookPayload(msg, botId, user) {
    const from = user ? {
        id:         parseInt(msg.chat_id),
        username:   user.username,
        first_name: user.first_name,
        last_name:  user.last_name,
        avatar:     user.avatar
    } : { id: parseInt(msg.chat_id) };

    const payload = {
        update_id:   msg.id,
        update_type: msg.callback_data ? 'callback_query' : (msg.is_command ? 'command' : 'message'),
        bot_id:      botId,
        message: {
            message_id: msg.id,
            from,
            chat:   { id: msg.chat_id, type: msg.chat_type },
            date:   Math.floor(new Date(msg.created_at).getTime() / 1000),
            text:   msg.text
        }
    };

    if (msg.is_command) {
        payload.command = { name: msg.command_name, args: msg.command_args };
    }
    if (msg.callback_data) {
        payload.callback_query = { id: String(msg.id), from, data: msg.callback_data };
    }
    if (msg.media_type) {
        payload.message.media = { type: msg.media_type, url: msg.media_url };
    }

    return payload;
}

// ─── HANDLERS ─────────────────────────────────────────────────────────────────

// POST /api/node/bots — Создать бота
function createBot(ctx) {
    return async (req, res) => {
        const { username, display_name, description = '', about = '',
                category = 'general', can_join_groups = 1, is_public = 1 } = req.body;

        if (!username || !display_name) {
            return res.json({ api_status: 400, error_message: 'username and display_name are required' });
        }

        // Валидация формата username
        if (!/^[a-zA-Z][a-zA-Z0-9_]{2,30}_bot$/.test(username)) {
            return res.json({
                api_status:    400,
                error_message: 'Username: 3–31 символов, буквы/цифры/подчёркивание, должен заканчиваться на _bot'
            });
        }

        try {
            const existing = await ctx.wo_bots.findOne({ where: { username } });
            if (existing) {
                return res.json({ api_status: 400, error_message: 'Имя пользователя уже занято' });
            }

            const botCount = await ctx.wo_bots.count({ where: { owner_id: req.userId } });
            if (botCount >= 20) {
                return res.json({ api_status: 400, error_message: 'Максимум 20 ботов на одного пользователя' });
            }

            const botId    = 'bot_' + crypto.randomBytes(12).toString('hex');
            const botToken = generateBotToken(botId);
            const now      = Math.floor(Date.now() / 1000);

            // Создаём Wo_Users запись для бота — необходимо для поиска в Android
            // (Android ищет только в Wo_Users, type='bot' идентифицирует аккаунт как бот)
            let linkedUserId = null;
            try {
                const [botUserEntry] = await ctx.wo_users.findOrCreate({
                    where: { username: sanitize(username) },
                    defaults: {
                        email:           `${botId}@bots.internal`,
                        password:        crypto.randomBytes(20).toString('hex'),
                        first_name:      sanitize(display_name),
                        last_name:       '',
                        about:           sanitize(description),
                        type:            'bot',
                        active:          '1',
                        verified:        '0',
                        lastseen:        now,
                        registered:      new Date().toLocaleDateString('en-US'),
                        joined:          now,
                        message_privacy: '0'
                    }
                });
                linkedUserId = botUserEntry.user_id;
            } catch (userErr) {
                console.warn('[Bots/createBot] Wo_Users entry skipped:', userErr.message);
            }

            await ctx.wo_bots.create({
                bot_id:         botId,
                owner_id:       req.userId,
                bot_token:      botToken,
                username:       sanitize(username),
                display_name:   sanitize(display_name),
                description:    sanitize(description),
                about:          sanitize(about),
                category:       sanitize(category),
                can_join_groups: parseInt(can_join_groups) ? 1 : 0,
                is_public:      parseInt(is_public) ? 1 : 0,
                status:         'active',
                linked_user_id: linkedUserId,
                created_at:     new Date(),
                updated_at:     new Date()
            });

            await registerDefaultCommands(ctx, botId);

            console.log(`[Bots] Created: ${username} (${botId}) user_id=${linkedUserId} owner=${req.userId}`);
            return res.json({
                api_status: 200,
                bot: { bot_id: botId, bot_token: botToken, username, display_name, status: 'active' },
                message:    'Бот создан. Сохраните bot_token — он больше не будет показан.'
            });

        } catch (err) {
            console.error('[Bots/createBot]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bots — Список ботов пользователя
function getMyBots(ctx) {
    return async (req, res) => {
        const limit  = Math.min(parseInt(req.query.limit)  || 20, 50);
        const offset = parseInt(req.query.offset) || 0;

        try {
            const bots = await ctx.wo_bots.findAll({
                where:      { owner_id: req.userId },
                attributes: ['bot_id', 'username', 'display_name', 'avatar', 'description', 'about',
                             'bot_type', 'status', 'is_public', 'category', 'messages_sent',
                             'messages_received', 'total_users', 'active_users_24h',
                             'created_at', 'last_active_at', 'webhook_url', 'webhook_enabled'],
                order:      [['created_at', 'DESC']],
                limit,
                offset,
                raw:        true
            });

            for (const bot of bots) {
                bot.commands_count = await ctx.wo_bot_commands.count({ where: { bot_id: bot.bot_id } });
            }

            return res.json({ api_status: 200, bots, count: bots.length });
        } catch (err) {
            console.error('[Bots/getMyBots]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bots/:bot_id — Информация о боте
function getBotInfo(ctx) {
    return async (req, res) => {
        const botId = req.params.bot_id;
        try {
            const bot = await ctx.wo_bots.findOne({
                where:      { bot_id: botId },
                attributes: { exclude: ['bot_token'] },
                raw:        true
            });

            if (!bot) return res.json({ api_status: 404, error_message: 'Бот не найден' });

            // Показываем токен только владельцу
            if (req.userId && bot.owner_id === req.userId) {
                const full = await ctx.wo_bots.findOne({ where: { bot_id: botId }, raw: true });
                bot.bot_token = full.bot_token;
            }

            const commands = await ctx.wo_bot_commands.findAll({
                where: { bot_id: botId, is_hidden: 0 },
                order: [['sort_order', 'ASC']],
                raw:   true
            });

            return res.json({ api_status: 200, bot, commands });
        } catch (err) {
            console.error('[Bots/getBotInfo]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// PUT /api/node/bots/:bot_id — Обновить бота
function updateBot(ctx) {
    return async (req, res) => {
        const botId = req.params.bot_id;
        try {
            const bot = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: req.userId } });
            if (!bot) return res.json({ api_status: 403, error_message: 'Бот не найден или нет доступа' });

            const allowed = ['display_name', 'description', 'about', 'category',
                             'is_public', 'can_join_groups', 'can_read_all_group_messages',
                             'is_inline', 'supports_commands', 'status'];
            const updates = {};
            for (const field of allowed) {
                if (req.body[field] !== undefined) {
                    updates[field] = sanitize(String(req.body[field]));
                }
            }
            updates.updated_at = new Date();

            await ctx.wo_bots.update(updates, { where: { bot_id: botId } });
            return res.json({ api_status: 200, message: 'Бот обновлен' });
        } catch (err) {
            console.error('[Bots/updateBot]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// DELETE /api/node/bots/:bot_id — Удалить бота
function deleteBot(ctx) {
    return async (req, res) => {
        const botId = req.params.bot_id;
        try {
            const bot = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: req.userId } });
            if (!bot) return res.json({ api_status: 403, error_message: 'Бот не найден или нет доступа' });

            // Каскадное удаление
            await ctx.wo_bot_commands.destroy(   { where: { bot_id: botId } });
            await ctx.wo_bot_messages.destroy(   { where: { bot_id: botId } });
            await ctx.wo_bot_users.destroy(      { where: { bot_id: botId } });
            await ctx.wo_bot_keyboards.destroy(  { where: { bot_id: botId } });
            await ctx.wo_bot_callbacks.destroy(  { where: { bot_id: botId } });
            await ctx.wo_bot_webhook_log.destroy({ where: { bot_id: botId } });
            await ctx.wo_bot_rate_limits.destroy({ where: { bot_id: botId } });
            await ctx.wo_bots.destroy(           { where: { bot_id: botId } });

            console.log(`[Bots] Deleted: ${botId} by user ${req.userId}`);
            return res.json({ api_status: 200, message: 'Бот и все его данные удалены' });
        } catch (err) {
            console.error('[Bots/deleteBot]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bots/:bot_id/regenerate-token
function regenerateToken(ctx) {
    return async (req, res) => {
        const botId = req.params.bot_id;
        try {
            const bot = await ctx.wo_bots.findOne({ where: { bot_id: botId, owner_id: req.userId } });
            if (!bot) return res.json({ api_status: 403, error_message: 'Бот не найден или нет доступа' });

            const newToken = generateBotToken(botId);
            await ctx.wo_bots.update({ bot_token: newToken, updated_at: new Date() }, { where: { bot_id: botId } });

            return res.json({
                api_status: 200,
                bot_token:  newToken,
                message:    'Токен обновлён. Старый токен больше не действителен.'
            });
        } catch (err) {
            console.error('[Bots/regenerateToken]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bots/search — публичный поиск ботов
function searchBots(ctx) {
    return async (req, res) => {
        const q      = sanitize((req.query.q || '').trim());
        const limit  = Math.min(parseInt(req.query.limit) || 20, 50);
        const offset = parseInt(req.query.offset) || 0;

        if (!q) return res.json({ api_status: 400, error_message: 'Укажите параметр q' });

        try {
            const bots = await ctx.wo_bots.findAll({
                where: {
                    is_public: 1,
                    status:    'active',
                    [Op.or]: [
                        { username:     { [Op.like]: `%${q}%` } },
                        { display_name: { [Op.like]: `%${q}%` } },
                        { description:  { [Op.like]: `%${q}%` } }
                    ]
                },
                attributes: ['bot_id', 'username', 'display_name', 'avatar', 'description',
                             'category', 'total_users', 'bot_type'],
                limit,
                offset,
                raw: true
            });

            return res.json({ api_status: 200, bots, count: bots.length });
        } catch (err) {
            console.error('[Bots/search]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// ─── BOT OPERATION HANDLERS ───────────────────────────────────────────────────

// GET /api/node/bot/getMe
function getMe(ctx) {
    return async (req, res) => {
        const bot = await ctx.wo_bots.findOne({
            where:      { bot_id: req.botId },
            attributes: { exclude: ['bot_token', 'owner_id'] },
            raw:        true
        });
        return res.json({ api_status: 200, bot });
    };
}

// POST /api/node/bot/sendMessage
function sendMessage(ctx, io) {
    return async (req, res) => {
        const { chat_id, text, reply_markup, parse_mode, media } = req.body;

        if (!chat_id) return res.json({ api_status: 400, error_message: 'chat_id required' });
        if (!text && !media) return res.json({ api_status: 400, error_message: 'text or media required' });

        try {
            const msg = await ctx.wo_bot_messages.create({
                bot_id:     req.botId,
                chat_id:    String(chat_id),
                chat_type:  'private',
                direction:  'outgoing',
                text:       text || null,
                media_type: media ? media.type : null,
                media_url:  media ? media.url  : null,
                reply_markup: reply_markup ? JSON.stringify(reply_markup) : null,
                processed:  1,
                processed_at: new Date()
            });

            await ctx.wo_bots.increment('messages_sent', { where: { bot_id: req.botId } });
            await ctx.wo_bot_users.upsert({
                bot_id:             req.botId,
                user_id:            parseInt(chat_id),
                last_interaction_at: new Date()
            });

            const payload = {
                event:        'bot_message',
                bot_id:       req.botId,
                message_id:   msg.id,
                text,
                media:        media || null,
                reply_markup: reply_markup || null,
                timestamp:    Date.now()
            };

            // Real-time доставка
            if (io) {
                io.to(String(chat_id)).emit('bot_message', payload);
                io.to(`user_bot_${chat_id}_${req.botId}`).emit('bot_message', payload);
            }

            return res.json({ api_status: 200, message_id: msg.id, ok: true });
        } catch (err) {
            console.error('[Bots/sendMessage]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/getUpdates — Long polling
function getUpdates(ctx) {
    return async (req, res) => {
        const offset  = parseInt(req.body.offset)  || 0;
        const limit   = Math.min(parseInt(req.body.limit) || 20, 100);
        const timeout = Math.min(parseInt(req.body.timeout) || 0, 30); // seconds

        const fetchUpdates = async () => {
            const where = { bot_id: req.botId, direction: 'incoming', processed: 0 };
            if (offset > 0) where.id = { [Op.gt]: offset - 1 };

            const messages = await ctx.wo_bot_messages.findAll({
                where,
                limit,
                order: [['id', 'ASC']],
                raw:   true
            });

            return messages.map(msg => ({
                update_id:   msg.id,
                update_type: msg.callback_data ? 'callback_query' : (msg.is_command ? 'command' : 'message'),
                message: {
                    message_id: msg.id,
                    from:       { id: parseInt(msg.chat_id) },
                    chat:       { id: msg.chat_id, type: msg.chat_type },
                    date:       Math.floor(new Date(msg.created_at).getTime() / 1000),
                    text:       msg.text
                },
                command:        msg.is_command ? { name: msg.command_name, args: msg.command_args } : undefined,
                callback_query: msg.callback_data ? { id: String(msg.id), data: msg.callback_data } : undefined
            }));
        };

        try {
            let updates = await fetchUpdates();

            // Long polling: ждём новые сообщения если timeout > 0
            if (updates.length === 0 && timeout > 0) {
                updates = await new Promise((resolve) => {
                    const deadline = Date.now() + timeout * 1000;
                    const poll = async () => {
                        const found = await fetchUpdates().catch(() => []);
                        if (found.length > 0) return resolve(found);
                        if (Date.now() >= deadline) return resolve([]);
                        setTimeout(poll, 1000);
                    };
                    poll();
                });
            }

            // Помечаем как обработанные
            if (updates.length > 0) {
                const ids = updates.map(u => u.update_id);
                await ctx.wo_bot_messages.update(
                    { processed: 1, processed_at: new Date() },
                    { where: { id: { [Op.in]: ids } } }
                );
            }

            return res.json({ api_status: 200, updates, count: updates.length });
        } catch (err) {
            console.error('[Bots/getUpdates]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/editMessage
function editMessage(ctx, io) {
    return async (req, res) => {
        const { chat_id, message_id, text, reply_markup } = req.body;
        if (!chat_id || !message_id) return res.json({ api_status: 400, error_message: 'chat_id and message_id required' });

        try {
            const updates = {};
            if (text         !== undefined) updates.text         = text;
            if (reply_markup !== undefined) updates.reply_markup = JSON.stringify(reply_markup);

            await ctx.wo_bot_messages.update(updates, {
                where: { id: message_id, bot_id: req.botId, direction: 'outgoing' }
            });

            if (io) {
                io.to(String(chat_id)).emit('bot_message_edit', {
                    bot_id: req.botId, message_id, text, reply_markup, timestamp: Date.now()
                });
            }

            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[Bots/editMessage]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/deleteMessage
function deleteMessage(ctx, io) {
    return async (req, res) => {
        const { chat_id, message_id } = req.body;
        if (!chat_id || !message_id) return res.json({ api_status: 400, error_message: 'chat_id and message_id required' });

        try {
            await ctx.wo_bot_messages.destroy({
                where: { id: message_id, bot_id: req.botId }
            });

            if (io) {
                io.to(String(chat_id)).emit('bot_message_delete', {
                    bot_id: req.botId, message_id, timestamp: Date.now()
                });
            }

            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[Bots/deleteMessage]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/answerCallbackQuery
function answerCallbackQuery(ctx, io) {
    return async (req, res) => {
        const { callback_query_id, text = '', show_alert = false, chat_id } = req.body;
        if (!callback_query_id) return res.json({ api_status: 400, error_message: 'callback_query_id required' });

        try {
            // Находим chat_id если не передан
            let targetChatId = chat_id;
            if (!targetChatId) {
                const cbMsg = await ctx.wo_bot_messages.findOne({
                    where: { id: callback_query_id, bot_id: req.botId },
                    raw:   true
                });
                targetChatId = cbMsg ? cbMsg.chat_id : null;
            }

            if (io && targetChatId) {
                io.to(String(targetChatId)).emit('bot_callback_answer', {
                    bot_id:            req.botId,
                    callback_query_id,
                    text,
                    show_alert,
                    timestamp:         Date.now()
                });
            }

            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[Bots/answerCB]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/sendPoll
function sendPoll(ctx, io) {
    return async (req, res) => {
        const { chat_id, question, options = [], allows_multiple_answers = false, is_anonymous = true } = req.body;
        if (!chat_id || !question || !options.length) {
            return res.json({ api_status: 400, error_message: 'chat_id, question and options required' });
        }

        try {
            const poll = await ctx.wo_bot_polls.create({
                bot_id:                  req.botId,
                chat_id:                 String(chat_id),
                question:                sanitize(question),
                allows_multiple_answers: allows_multiple_answers ? 1 : 0,
                is_anonymous:            is_anonymous ? 1 : 0,
                is_closed:               0,
                created_at:              new Date()
            });

            const pollOptions = [];
            for (let i = 0; i < options.length; i++) {
                const opt = await ctx.wo_bot_poll_options.create({
                    poll_id:    poll.id,
                    bot_id:     req.botId,
                    text:       sanitize(String(options[i])),
                    sort_order: i,
                    voter_count: 0
                });
                pollOptions.push({ id: opt.id, text: opt.text, voter_count: 0 });
            }

            const payload = {
                event:    'bot_poll',
                bot_id:   req.botId,
                poll_id:  poll.id,
                question,
                options:  pollOptions,
                is_anonymous,
                allows_multiple_answers,
                timestamp: Date.now()
            };

            if (io) {
                io.to(String(chat_id)).emit('bot_message', payload);
            }

            return res.json({ api_status: 200, poll_id: poll.id, ok: true });
        } catch (err) {
            console.error('[Bots/sendPoll]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/stopPoll
function stopPoll(ctx, io) {
    return async (req, res) => {
        const { poll_id } = req.body;
        if (!poll_id) return res.json({ api_status: 400, error_message: 'poll_id required' });

        try {
            const poll = await ctx.wo_bot_polls.findOne({
                where: { id: poll_id, bot_id: req.botId },
                raw:   true
            });
            if (!poll) return res.json({ api_status: 404, error_message: 'Опрос не найден' });

            await ctx.wo_bot_polls.update({ is_closed: 1 }, { where: { id: poll_id } });

            if (io) {
                io.to(String(poll.chat_id)).emit('bot_poll_closed', {
                    bot_id: req.botId, poll_id, timestamp: Date.now()
                });
            }

            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[Bots/stopPoll]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/setCommands
function setCommands(ctx) {
    return async (req, res) => {
        let commands = req.body.commands;
        if (typeof commands === 'string') {
            try { commands = JSON.parse(commands); } catch { commands = []; }
        }
        if (!Array.isArray(commands)) {
            return res.json({ api_status: 400, error_message: 'commands must be a JSON array' });
        }
        if (commands.length > 100) {
            return res.json({ api_status: 400, error_message: 'Максимум 100 команд' });
        }

        try {
            await ctx.wo_bot_commands.destroy({ where: { bot_id: req.botId } });

            let order = 0;
            for (const cmd of commands) {
                if (!cmd.command || !cmd.description) continue;
                await ctx.wo_bot_commands.create({
                    bot_id:      req.botId,
                    command:     sanitize(cmd.command.replace(/^\//, '').toLowerCase()),
                    description: sanitize(cmd.description),
                    usage_hint:  sanitize(cmd.usage_hint || ''),
                    is_hidden:   cmd.is_hidden ? 1 : 0,
                    scope:       sanitize(cmd.scope || 'all'),
                    sort_order:  order++
                });
            }

            return res.json({ api_status: 200, message: 'Команды обновлены', count: order });
        } catch (err) {
            console.error('[Bots/setCommands]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bot/getCommands
function getCommands(ctx) {
    return async (req, res) => {
        try {
            const commands = await ctx.wo_bot_commands.findAll({
                where: { bot_id: req.botId },
                order: [['sort_order', 'ASC']],
                raw:   true
            });
            return res.json({ api_status: 200, commands });
        } catch (err) {
            console.error('[Bots/getCommands]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/setWebhook
function setWebhook(ctx) {
    return async (req, res) => {
        const { url, secret, max_connections = 40, allowed_updates } = req.body;

        if (!url) {
            // Удаление webhook (пустой URL)
            await ctx.wo_bots.update(
                { webhook_url: null, webhook_enabled: 0, webhook_secret: null, updated_at: new Date() },
                { where: { bot_id: req.botId } }
            );
            return res.json({ api_status: 200, message: 'Webhook удалён' });
        }

        // Валидация URL
        try { new URL(url); } catch {
            return res.json({ api_status: 400, error_message: 'Неверный URL' });
        }

        try {
            await ctx.wo_bots.update({
                webhook_url:             sanitize(url),
                webhook_enabled:         1,
                webhook_secret:          secret ? sanitize(secret) : crypto.randomBytes(16).toString('hex'),
                webhook_max_connections: Math.min(parseInt(max_connections) || 40, 100),
                webhook_allowed_updates: allowed_updates ? JSON.stringify(allowed_updates) : null,
                updated_at:              new Date()
            }, { where: { bot_id: req.botId } });

            return res.json({ api_status: 200, ok: true, message: 'Webhook установлен' });
        } catch (err) {
            console.error('[Bots/setWebhook]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/deleteWebhook
function deleteWebhook(ctx) {
    return async (req, res) => {
        try {
            await ctx.wo_bots.update(
                { webhook_url: null, webhook_enabled: 0, updated_at: new Date() },
                { where: { bot_id: req.botId } }
            );
            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bot/getWebhookInfo
function getWebhookInfo(ctx) {
    return async (req, res) => {
        try {
            const bot = await ctx.wo_bots.findOne({
                where:      { bot_id: req.botId },
                attributes: ['webhook_url', 'webhook_enabled', 'webhook_max_connections',
                             'webhook_allowed_updates', 'webhook_secret'],
                raw:        true
            });

            const pendingCount = await ctx.wo_bot_messages.count({
                where: { bot_id: req.botId, direction: 'incoming', processed: 0 }
            });

            return res.json({
                api_status:              200,
                url:                     bot.webhook_url || '',
                has_custom_certificate:  false,
                pending_update_count:    pendingCount,
                max_connections:         bot.webhook_max_connections,
                allowed_updates:         bot.webhook_allowed_updates
                                             ? JSON.parse(bot.webhook_allowed_updates)
                                             : [],
                is_enabled:              bot.webhook_enabled === 1
            });
        } catch (err) {
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// POST /api/node/bot/setUserState
function setUserState(ctx) {
    return async (req, res) => {
        const { user_id, state, state_data } = req.body;
        if (!user_id) return res.json({ api_status: 400, error_message: 'user_id required' });

        try {
            await ctx.wo_bot_users.upsert({
                bot_id:     req.botId,
                user_id:    parseInt(user_id),
                state:      state || null,
                state_data: state_data ? JSON.stringify(state_data) : null
            });
            return res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[Bots/setUserState]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// GET /api/node/bot/getUserState
function getUserState(ctx) {
    return async (req, res) => {
        const userId = req.query.user_id || req.body.user_id;
        if (!userId) return res.json({ api_status: 400, error_message: 'user_id required' });

        try {
            const botUser = await ctx.wo_bot_users.findOne({
                where: { bot_id: req.botId, user_id: parseInt(userId) },
                raw:   true
            });

            return res.json({
                api_status: 200,
                state:      botUser ? botUser.state      : null,
                state_data: botUser ? (botUser.state_data ? JSON.parse(botUser.state_data) : null) : null
            });
        } catch (err) {
            console.error('[Bots/getUserState]', err.message);
            return res.json({ api_status: 500, error_message: 'Ошибка сервера' });
        }
    };
}

// ─── Legacy совместимость с PHP SDK ──────────────────────────────────────────

function legacyBotApi(ctx, io) {
    return async (req, res) => {
        const type = req.body.type || req.query.type || '';

        // Авторизация
        let bot = null;
        let userId = null;

        const botToken = req.body.bot_token || req.headers['bot-token'];
        const userToken = req.body.access_token || req.headers['access-token'];

        if (botToken) {
            bot = await ctx.wo_bots.findOne({ where: { bot_token: botToken, status: 'active' }, raw: true });
            if (!bot) return res.json({ api_status: 401, error_message: 'Invalid bot_token' });
            req.botId = bot.bot_id;
            req.bot   = bot;
        } else if (userToken) {
            const session = await ctx.wo_appssessions.findOne({ where: { session_id: userToken }, raw: true });
            if (!session) return res.json({ api_status: 401, error_message: 'Invalid access_token' });
            req.userId = userId = session.user_id;
        }

        // Маппинг type → handler
        const handlers = {
            create_bot:          () => createBot(ctx)(req, res),
            get_my_bots:         () => getMyBots(ctx)(req, res),
            update_bot:          () => { req.params = { bot_id: req.body.bot_id }; return updateBot(ctx)(req, res); },
            delete_bot:          () => { req.params = { bot_id: req.body.bot_id }; return deleteBot(ctx)(req, res); },
            regenerate_token:    () => { req.params = { bot_id: req.body.bot_id }; return regenerateToken(ctx)(req, res); },
            send_message:        () => sendMessage(ctx, io)(req, res),
            get_updates:         () => getUpdates(ctx)(req, res),
            edit_message:        () => editMessage(ctx, io)(req, res),
            delete_message:      () => deleteMessage(ctx, io)(req, res),
            answer_callback_query: () => answerCallbackQuery(ctx, io)(req, res),
            send_poll:           () => sendPoll(ctx, io)(req, res),
            stop_poll:           () => stopPoll(ctx, io)(req, res),
            set_commands:        () => setCommands(ctx)(req, res),
            get_commands:        () => getCommands(ctx)(req, res),
            set_webhook:         () => setWebhook(ctx)(req, res),
            delete_webhook:      () => deleteWebhook(ctx)(req, res),
            get_webhook_info:    () => getWebhookInfo(ctx)(req, res),
            set_user_state:      () => setUserState(ctx)(req, res),
            get_user_state:      () => getUserState(ctx)(req, res),
            get_me:              () => getMe(ctx)(req, res)
        };

        const handler = handlers[type];
        if (handler) return handler();

        return res.json({ api_status: 400, error_message: `Unknown type: ${type}` });
    };
}

// ─── Запуск обработчика webhook-ов каждые 5 секунд ───────────────────────────

let webhookProcessorStarted = false;

function startWebhookProcessor(ctx) {
    if (webhookProcessorStarted) return;
    webhookProcessorStarted = true;

    setInterval(() => processWebhooks(ctx), 5000);
    console.log('[Bots] Webhook processor started (every 5s)');
}

// ─── Регистрация маршрутов ────────────────────────────────────────────────────

function registerBotRoutes(app, ctx, io) {
    const uAuth = (req, res, next) => userAuth(ctx, req, res, next);
    const bAuth = (req, res, next) => botAuth(ctx, req, res, next);

    // ── Управление ботами (user token) ──────────────────────────────────────
    app.post(   '/api/node/bots',                          uAuth, createBot(ctx));
    app.get(    '/api/node/bots',                          uAuth, getMyBots(ctx));
    app.get(    '/api/node/bots/search',                   searchBots(ctx));          // публичный
    app.get(    '/api/node/bots/:bot_id',                  getBotInfo(ctx));           // публичный (скрывает токен)
    app.put(    '/api/node/bots/:bot_id',                  uAuth, updateBot(ctx));
    app.delete( '/api/node/bots/:bot_id',                  uAuth, deleteBot(ctx));
    app.post(   '/api/node/bots/:bot_id/regenerate-token', uAuth, regenerateToken(ctx));

    // ── Операции бота (bot token) ────────────────────────────────────────────
    app.get(    '/api/node/bot/getMe',               bAuth, getMe(ctx));
    app.post(   '/api/node/bot/sendMessage',          bAuth, sendMessage(ctx, io));
    app.post(   '/api/node/bot/getUpdates',           bAuth, getUpdates(ctx));
    app.post(   '/api/node/bot/editMessage',          bAuth, editMessage(ctx, io));
    app.post(   '/api/node/bot/deleteMessage',        bAuth, deleteMessage(ctx, io));
    app.post(   '/api/node/bot/answerCallbackQuery',  bAuth, answerCallbackQuery(ctx, io));
    app.post(   '/api/node/bot/sendPoll',             bAuth, sendPoll(ctx, io));
    app.post(   '/api/node/bot/stopPoll',             bAuth, stopPoll(ctx, io));
    app.post(   '/api/node/bot/setCommands',          bAuth, setCommands(ctx));
    app.get(    '/api/node/bot/getCommands',          bAuth, getCommands(ctx));
    app.post(   '/api/node/bot/setWebhook',           bAuth, setWebhook(ctx));
    app.post(   '/api/node/bot/deleteWebhook',        bAuth, deleteWebhook(ctx));
    app.get(    '/api/node/bot/getWebhookInfo',       bAuth, getWebhookInfo(ctx));
    app.post(   '/api/node/bot/setUserState',         bAuth, setUserState(ctx));
    app.get(    '/api/node/bot/getUserState',         bAuth, getUserState(ctx));
    app.post(   '/api/node/bot/getUserState',         bAuth, getUserState(ctx));

    // ── Legacy PHP SDK совместимость ─────────────────────────────────────────
    app.post(   '/api/node/bots/api', legacyBotApi(ctx, io));

    // Запускаем обработчик webhook-ов
    startWebhookProcessor(ctx);

    console.log('[Bots API] Registered:');
    console.log('  POST/GET /api/node/bots           — управление ботами');
    console.log('  POST     /api/node/bot/*           — операции бота');
    console.log('  POST     /api/node/bots/api        — legacy PHP SDK совместимость');
}

module.exports = { registerBotRoutes };
