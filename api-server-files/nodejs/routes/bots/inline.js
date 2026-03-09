'use strict';

/**
 * Inline Bots API
 *
 * Дозволяє користувачам викликати бота inline-запитом прямо з поля вводу:
 *   @botusername <query>
 *
 * ─── Клієнт → сервер ─────────────────────────────────────────────────────────
 *   POST /api/node/bot/answerInlineQuery
 *       Бот відповідає на inline-запит масивом результатів.
 *       Вимагає bot_token.
 *
 *   POST /api/node/bot/getInlineResults
 *       Клієнт (мобільний) надсилає запит від імені користувача, отримує результати.
 *       Вимагає access_token.
 *
 *   POST /api/node/bot/chooseInlineResult
 *       Клієнт сповіщає бота, що користувач вибрав конкретний результат.
 *       Вимагає access_token.
 *
 * ─── Socket (сервер → бот) ───────────────────────────────────────────────────
 *   inline_query  { id, from, query, offset }  — прийшов запит від користувача
 *
 * ─── Socket (бот → сервер) ───────────────────────────────────────────────────
 *   answer_inline_query  { inline_query_id, results, cache_time, is_personal,
 *                          next_offset, switch_pm_text, switch_pm_parameter }
 */

const crypto = require('crypto');
const { Op }  = require('sequelize');

// Тимчасовий in-memory кеш inline-запитів (queryId -> { botId, userId, results, ts })
const inlineCache = new Map();

// Очищення кешу кожні 5 хвилин
setInterval(() => {
    const now = Date.now();
    for (const [key, val] of inlineCache) {
        if (now - val.ts > 300_000) inlineCache.delete(key);
    }
}, 300_000);

// ─── helpers ──────────────────────────────────────────────────────────────────

function generateInlineQueryId() {
    return crypto.randomBytes(8).toString('hex');
}

async function getUserBasic(ctx, userId) {
    const u = await ctx.wo_users.findOne({
        attributes: ['user_id', 'username', 'first_name', 'last_name'],
        where: { user_id: userId },
        raw: true,
    }).catch(() => null);
    return u || { user_id: userId, username: '', first_name: '', last_name: '' };
}

async function getBotByUsername(ctx, username) {
    return ctx.wo_bots.findOne({
        where: { username: username, status: 'active', is_inline: 1 },
        raw: true,
    }).catch(() => null);
}

// ─── POST /api/node/bot/getInlineResults ─────────────────────────────────────
// Клієнт надсилає запит → сервер пересилає боту через socket, повертає результати

function getInlineResults(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const botUsername = (req.body.bot_username || '').trim().replace(/^@/, '');
            const query       = (req.body.query || '').trim();
            const offset      = req.body.offset || '';

            if (!botUsername) {
                return res.json({ api_status: 400, error_message: 'bot_username required' });
            }

            const bot = await getBotByUsername(ctx, botUsername);
            if (!bot) {
                return res.json({ api_status: 404, error_message: 'Bot not found or not inline-enabled' });
            }

            const user     = await getUserBasic(ctx, userId);
            const queryId  = generateInlineQueryId();
            const queryTs  = Date.now();

            // Зберігаємо очікування в кеші
            inlineCache.set(queryId, {
                botId:  bot.bot_id,
                userId,
                ts:     queryTs,
                results: null,
                resolve: null,
            });

            // Надсилаємо inline_query боту через Socket.IO namespace /bots
            const botNs = io.of('/bots');
            const botSockets = [...botNs.sockets.values()].filter(s => s.botId === bot.bot_id);

            if (botSockets.length === 0) {
                inlineCache.delete(queryId);
                return res.json({ api_status: 503, error_message: 'Bot is offline' });
            }

            // Надсилаємо першому підключеному сокету бота
            botSockets[0].emit('inline_query', {
                id:     queryId,
                from:   user,
                query:  query,
                offset: offset,
            });

            // Чекаємо відповіді (максимум 5 секунд)
            const results = await new Promise((resolve) => {
                const entry = inlineCache.get(queryId);
                if (entry) entry.resolve = resolve;
                setTimeout(() => resolve(null), 5000);
            });

            inlineCache.delete(queryId);

            if (!results) {
                return res.json({ api_status: 504, error_message: 'Bot did not respond in time' });
            }

            res.json({ api_status: 200, ...results });

        } catch (err) {
            console.error('[InlineBot/getInlineResults]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/bot/answerInlineQuery ─────────────────────────────────────
// Бот відповідає на inline-запит (викликається з bot SDK)

function answerInlineQuery(ctx) {
    return async (req, res) => {
        try {
            const bot            = req.bot;
            const inlineQueryId  = req.body.inline_query_id;
            const results        = req.body.results;          // Array of InlineQueryResult
            const cacheTime      = parseInt(req.body.cache_time) || 300;
            const isPersonal     = !!req.body.is_personal;
            const nextOffset     = req.body.next_offset || '';
            const switchPmText   = req.body.switch_pm_text || null;
            const switchPmParam  = req.body.switch_pm_parameter || null;

            if (!inlineQueryId) {
                return res.json({ api_status: 400, error_message: 'inline_query_id required' });
            }
            if (!Array.isArray(results)) {
                return res.json({ api_status: 400, error_message: 'results must be an array' });
            }
            if (results.length > 50) {
                return res.json({ api_status: 400, error_message: 'Too many results (max 50)' });
            }

            const entry = inlineCache.get(inlineQueryId);
            if (!entry || entry.botId !== bot.bot_id) {
                return res.json({ api_status: 404, error_message: 'inline_query_id not found or expired' });
            }

            const payload = { results, cache_time: cacheTime, is_personal: isPersonal,
                              next_offset: nextOffset, switch_pm_text: switchPmText,
                              switch_pm_parameter: switchPmParam };

            if (entry.resolve) {
                entry.resolve(payload);
            } else {
                entry.results = payload;
            }

            res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[InlineBot/answerInlineQuery]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/bot/chooseInlineResult ───────────────────────────────────
// Клієнт сповіщає бота про вибір результату

function chooseInlineResult(ctx, io) {
    return async (req, res) => {
        try {
            const userId      = req.userId;
            const resultId    = req.body.result_id;
            const botUsername = (req.body.bot_username || '').replace(/^@/, '');
            const query       = req.body.query || '';

            const bot = await getBotByUsername(ctx, botUsername);
            if (!bot) return res.json({ api_status: 404, error_message: 'Bot not found' });

            const user = await getUserBasic(ctx, userId);
            const botNs = io.of('/bots');
            const botSockets = [...botNs.sockets.values()].filter(s => s.botId === bot.bot_id);
            if (botSockets.length > 0) {
                botSockets[0].emit('chosen_inline_result', {
                    result_id: resultId,
                    from:      user,
                    query:     query,
                });
            }

            res.json({ api_status: 200, ok: true });
        } catch (err) {
            console.error('[InlineBot/chooseInlineResult]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── Socket handler: бот відповідає через socket (альтернатива REST) ─────────

function attachInlineBotSocketHandlers(socket, io) {
    socket.on('answer_inline_query', (data) => {
        const { inline_query_id, results, cache_time, is_personal, next_offset } = data || {};
        const entry = inlineCache.get(inline_query_id);
        if (!entry || entry.botId !== socket.botId) return;
        const payload = { results, cache_time, is_personal, next_offset };
        if (entry.resolve) entry.resolve(payload);
        else entry.results = payload;
    });
}

// ─── register routes ──────────────────────────────────────────────────────────

function registerInlineBotRoutes(app, ctx, io, userAuthMiddleware, botAuthMiddleware) {
    app.post('/api/node/bot/getInlineResults',   userAuthMiddleware, getInlineResults(ctx, io));
    app.post('/api/node/bot/answerInlineQuery',  botAuthMiddleware,  answerInlineQuery(ctx));
    app.post('/api/node/bot/chooseInlineResult', userAuthMiddleware, chooseInlineResult(ctx, io));

    console.log('[InlineBot API] Registered: getInlineResults, answerInlineQuery, chooseInlineResult');
}

module.exports = { registerInlineBotRoutes, attachInlineBotSocketHandlers };
