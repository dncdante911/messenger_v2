'use strict';

/**
 * WorldMates Bot SDK — Node.js
 *
 * SDK для создания ботов на платформе WorldMates Messenger.
 * Поддерживает как Socket.IO подключение (рекомендуется), так и REST API.
 *
 * Быстрый старт:
 *
 *   const { WorldMatesBot } = require('./WorldMatesBotSDK');
 *
 *   const bot = new WorldMatesBot({
 *     botId:  'bot_abc123',
 *     token:  'bot_abc123:ваш_токен_здесь',
 *     // apiUrl: 'https://worldmates.club'  // опционально
 *   });
 *
 *   bot.onCommand('start', async ({ message, bot }) => {
 *     await bot.sendMessage(message.from.id, 'Привет! Я бот WorldMates!');
 *   });
 *
 *   bot.onMessage(async ({ message, bot }) => {
 *     await bot.sendMessage(message.from.id, `Ты написал: ${message.text}`);
 *   });
 *
 *   bot.start(); // WebSocket режим (рекомендуется)
 *   // или
 *   bot.startPolling(); // REST API polling режим
 *
 * Webhook режим:
 *
 *   const express = require('express');
 *   const app = express();
 *   app.use(express.json());
 *   app.post('/webhook', bot.webhookHandler());
 *   app.listen(3001);
 *
 *   await bot.setWebhook('https://my-server.com/webhook');
 */

const https   = require('https');
const http    = require('http');
const crypto  = require('crypto');
const EventEmitter = require('events');

// ─── Конфигурация ─────────────────────────────────────────────────────────────

const DEFAULT_API_URL   = 'https://worldmates.club';
const DEFAULT_NODE_HOST = '192.168.0.250';
const DEFAULT_NODE_PORT = 449;

// ─── HTTP helper ─────────────────────────────────────────────────────────────

function httpPost(url, data, headers = {}) {
    return new Promise((resolve, reject) => {
        const body    = JSON.stringify(data);
        const parsed  = new URL(url);
        const lib     = parsed.protocol === 'https:' ? https : http;
        const options = {
            method:   'POST',
            hostname: parsed.hostname,
            port:     parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
            path:     parsed.pathname + parsed.search,
            headers: {
                'Content-Type':   'application/json',
                'Content-Length': Buffer.byteLength(body),
                ...headers
            },
            timeout: 30000
        };

        const req = lib.request(options, (res) => {
            let buf = '';
            res.on('data', (d) => { buf += d; });
            res.on('end', () => {
                try { resolve(JSON.parse(buf)); }
                catch { resolve({ api_status: 0, error_message: 'Invalid JSON', raw: buf }); }
            });
        });

        req.on('timeout', () => {
            req.destroy();
            reject(new Error('Request timeout'));
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

function httpGet(url, headers = {}) {
    return new Promise((resolve, reject) => {
        const parsed = new URL(url);
        const lib    = parsed.protocol === 'https:' ? https : http;
        const options = {
            method:   'GET',
            hostname: parsed.hostname,
            port:     parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
            path:     parsed.pathname + parsed.search,
            headers,
            timeout: 30000
        };

        const req = lib.request(options, (res) => {
            let buf = '';
            res.on('data', (d) => { buf += d; });
            res.on('end', () => {
                try { resolve(JSON.parse(buf)); }
                catch { resolve({ api_status: 0, error_message: 'Invalid JSON' }); }
            });
        });

        req.on('timeout', () => { req.destroy(); reject(new Error('Timeout')); });
        req.on('error', reject);
        req.end();
    });
}

// ─── Основной класс SDK ───────────────────────────────────────────────────────

class WorldMatesBot extends EventEmitter {

    /**
     * @param {object} options
     * @param {string} options.botId    — ID бота (bot_xxx...)
     * @param {string} options.token    — Bot API Token
     * @param {string} [options.apiUrl] — URL сервера (по умолчанию worldmates.club)
     * @param {boolean} [options.debug] — включить дебаг логи
     */
    constructor(options) {
        super();

        if (typeof options === 'string') {
            // Legacy: WorldMatesBot('token')
            this.token  = options;
            this.botId  = options.split(':')[0] || '';
            this.apiUrl = DEFAULT_API_URL;
        } else {
            this.botId  = options.botId  || '';
            this.token  = options.token  || '';
            this.apiUrl = (options.apiUrl || DEFAULT_API_URL).replace(/\/$/, '');
        }

        this.debug     = options?.debug || false;
        this.botApiUrl = `${this.apiUrl}/api/node/bot`;
        this.mgmtUrl   = `${this.apiUrl}/api/node/bots`;

        this._commandHandlers  = new Map();
        this._messageHandler   = null;
        this._callbackHandler  = null;
        this._stateHandlers    = new Map();
        this._polling          = false;
        this._lastUpdateId     = 0;
        this._pollTimeout      = 5;
        this._io               = null; // для WebSocket
    }

    // ─── Логирование ─────────────────────────────────────────────────────────

    _log(msg, ...args) {
        if (this.debug) console.log(`[Bot:${this.botId}]`, msg, ...args);
    }

    _headers() {
        return { 'bot-token': this.token };
    }

    // ─── Базовые API вызовы ───────────────────────────────────────────────────

    async _get(method, params = {}) {
        const qs  = new URLSearchParams(params).toString();
        const url = `${this.botApiUrl}/${method}${qs ? '?' + qs : ''}`;
        return httpGet(url, this._headers());
    }

    async _post(method, data = {}) {
        return httpPost(`${this.botApiUrl}/${method}`, data, this._headers());
    }

    // ─── Отправка сообщений ───────────────────────────────────────────────────

    /**
     * Отправить текстовое сообщение
     * @param {number|string} chatId
     * @param {string} text
     * @param {object} [options] — { reply_markup, parse_mode }
     */
    async sendMessage(chatId, text, options = {}) {
        const result = await this._post('sendMessage', {
            chat_id:    chatId,
            text,
            ...options
        });
        this._log('sendMessage', chatId, text.substring(0, 50));
        return result;
    }

    /**
     * Отправить сообщение с inline-клавиатурой
     * @param {number|string} chatId
     * @param {string} text
     * @param {Array} keyboard — массив кнопок (формат Telegram)
     * @param {object} [options]
     */
    async sendMessageWithKeyboard(chatId, text, keyboard, options = {}) {
        return this.sendMessage(chatId, text, {
            reply_markup: { inline_keyboard: keyboard },
            ...options
        });
    }

    /**
     * Отправить сообщение с reply-клавиатурой
     */
    async sendMessageWithReplyKeyboard(chatId, text, keyboard, options = {}) {
        return this.sendMessage(chatId, text, {
            reply_markup: {
                keyboard:           keyboard,
                resize_keyboard:    true,
                one_time_keyboard:  options.one_time || false
            },
            ...options
        });
    }

    /**
     * Редактировать сообщение
     */
    async editMessage(chatId, messageId, text, options = {}) {
        return this._post('editMessage', {
            chat_id:    chatId,
            message_id: messageId,
            text,
            ...options
        });
    }

    /**
     * Удалить сообщение
     */
    async deleteMessage(chatId, messageId) {
        return this._post('deleteMessage', { chat_id: chatId, message_id: messageId });
    }

    /**
     * Ответить на callback query (нажатие кнопки)
     * @param {string} callbackQueryId
     * @param {string} [text] — текст уведомления
     * @param {boolean} [showAlert] — показать как alert
     */
    async answerCallbackQuery(callbackQueryId, text = '', showAlert = false) {
        return this._post('answerCallbackQuery', {
            callback_query_id: callbackQueryId,
            text,
            show_alert: showAlert
        });
    }

    /**
     * Отправить опрос
     * @param {number|string} chatId
     * @param {string} question
     * @param {string[]} options — варианты ответа
     * @param {object} [pollOptions]
     */
    async sendPoll(chatId, question, options, pollOptions = {}) {
        return this._post('sendPoll', {
            chat_id:   chatId,
            question,
            options,
            ...pollOptions
        });
    }

    /**
     * Закрыть опрос
     */
    async stopPoll(pollId) {
        return this._post('stopPoll', { poll_id: pollId });
    }

    // ─── Управление командами ─────────────────────────────────────────────────

    /**
     * Установить список команд бота
     * @param {Array} commands — [{ command: 'start', description: 'Начало' }, ...]
     */
    async setCommands(commands) {
        return this._post('setCommands', { commands });
    }

    /**
     * Получить список команд бота
     */
    async getCommands() {
        return this._get('getCommands');
    }

    // ─── Webhook ──────────────────────────────────────────────────────────────

    /**
     * Установить webhook
     * @param {string} url — HTTPS URL
     * @param {object} [options] — { secret, max_connections, allowed_updates }
     */
    async setWebhook(url, options = {}) {
        return this._post('setWebhook', { url, ...options });
    }

    /**
     * Удалить webhook (переключиться на polling)
     */
    async deleteWebhook() {
        return this._post('deleteWebhook');
    }

    /**
     * Получить информацию о webhook
     */
    async getWebhookInfo() {
        return this._get('getWebhookInfo');
    }

    /**
     * Обработчик для Express.js (webhook mode)
     * @returns {Function} middleware
     */
    webhookHandler() {
        return async (req, res) => {
            res.status(200).json({ ok: true }); // Отвечаем сразу

            const update = req.body;
            if (!update) return;

            // Проверяем подпись если есть секрет
            const signature = req.headers['x-worldmates-bot-signature'];
            if (signature) {
                // Опциональная верификация
                this._log('Webhook received, signature:', signature.substring(0, 20));
            }

            await this._processUpdate(update);
        };
    }

    // ─── Состояния пользователей ──────────────────────────────────────────────

    /**
     * Установить состояние диалога пользователя (FSM)
     * @param {number} userId
     * @param {string|null} state
     * @param {object|null} [stateData]
     */
    async setUserState(userId, state, stateData = null) {
        return this._post('setUserState', {
            user_id:    userId,
            state,
            state_data: stateData
        });
    }

    /**
     * Получить текущее состояние пользователя
     * @param {number} userId
     */
    async getUserState(userId) {
        return this._get('getUserState', { user_id: userId });
    }

    /**
     * Сбросить состояние пользователя
     */
    async clearUserState(userId) {
        return this.setUserState(userId, null, null);
    }

    // ─── Информация о боте ────────────────────────────────────────────────────

    /**
     * Получить информацию о боте
     */
    async getMe() {
        return this._get('getMe');
    }

    // ─── Регистрация обработчиков ─────────────────────────────────────────────

    /**
     * Зарегистрировать обработчик команды
     * @param {string} command — без слеша (например 'start', 'help')
     * @param {Function} handler — async ({ message, args, bot }) => {}
     */
    onCommand(command, handler) {
        this._commandHandlers.set(command.toLowerCase().replace(/^\//, ''), handler);
        return this;
    }

    /**
     * Зарегистрировать обработчик текстовых сообщений
     * @param {Function} handler — async ({ message, bot }) => {}
     */
    onMessage(handler) {
        this._messageHandler = handler;
        return this;
    }

    /**
     * Зарегистрировать обработчик нажатий на кнопки
     * @param {Function} handler — async ({ callback, bot }) => {}
     */
    onCallbackQuery(handler) {
        this._callbackHandler = handler;
        return this;
    }

    /**
     * Зарегистрировать обработчик состояния (FSM)
     * @param {string} state — название состояния
     * @param {Function} handler — async ({ message, stateData, bot }) => {}
     */
    onState(state, handler) {
        this._stateHandlers.set(state, handler);
        return this;
    }

    // ─── Обработка обновлений ─────────────────────────────────────────────────

    async _processUpdate(update) {
        try {
            // Callback query (нажатие кнопки)
            if (update.callback_query) {
                if (this._callbackHandler) {
                    await this._callbackHandler({ callback: update.callback_query, bot: this });
                }
                return;
            }

            // Сообщение
            if (update.message) {
                const message = update.message;
                const userId  = message.from?.id;

                // Команда
                if (update.command) {
                    const cmdName = update.command.name?.toLowerCase();
                    const handler = this._commandHandlers.get(cmdName);
                    if (handler) {
                        await handler({ message, args: update.command.args, bot: this });
                        return;
                    }
                }

                // FSM состояние
                if (userId && this._stateHandlers.size > 0) {
                    const stateResult = await this.getUserState(userId).catch(() => null);
                    if (stateResult?.api_status === 200 && stateResult.state) {
                        const stateHandler = this._stateHandlers.get(stateResult.state);
                        if (stateHandler) {
                            await stateHandler({ message, stateData: stateResult.state_data, bot: this });
                            return;
                        }
                    }
                }

                // Общий обработчик
                if (this._messageHandler) {
                    await this._messageHandler({ message, bot: this });
                }
            }
        } catch (err) {
            this.emit('error', err);
            if (this.debug) console.error(`[Bot:${this.botId}] processUpdate error:`, err.message);
        }
    }

    // ─── Long Polling ─────────────────────────────────────────────────────────

    /**
     * Запустить получение обновлений через long polling
     * @param {number} [timeout] — таймаут опроса в секундах (0-30)
     */
    async startPolling(timeout = 5) {
        this._polling      = true;
        this._pollTimeout  = timeout;

        console.log(`[Bot:${this.botId}] Starting polling...`);

        while (this._polling) {
            try {
                const result = await this._post('getUpdates', {
                    offset:  this._lastUpdateId + 1,
                    limit:   20,
                    timeout: this._pollTimeout
                });

                if (result.api_status === 200 && result.updates?.length) {
                    for (const update of result.updates) {
                        this._lastUpdateId = Math.max(this._lastUpdateId, update.update_id || 0);
                        await this._processUpdate(update);
                    }
                }
            } catch (err) {
                this.emit('error', err);
                // Пауза при ошибке перед следующей попыткой
                await new Promise(r => setTimeout(r, 2000));
            }
        }
    }

    /**
     * Остановить polling
     */
    stopPolling() {
        this._polling = false;
    }

    /**
     * Алиас для startPolling — стартует polling в фоне
     */
    start(timeout = 5) {
        this.startPolling(timeout).catch(err => this.emit('error', err));
        return this;
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    /**
     * Построить inline-клавиатуру
     * @param {Array} buttons — [{ text, callback_data }, ...]
     * @param {number} columns — кнопок в ряд
     */
    static buildInlineKeyboard(buttons, columns = 2) {
        const keyboard = [];
        let row = [];
        for (const btn of buttons) {
            row.push(btn);
            if (row.length >= columns) { keyboard.push(row); row = []; }
        }
        if (row.length) keyboard.push(row);
        return keyboard;
    }

    /**
     * Создать callback-кнопку
     */
    static callbackButton(text, callbackData) {
        return { text, callback_data: callbackData };
    }

    /**
     * Создать URL-кнопку
     */
    static urlButton(text, url) {
        return { text, url };
    }
}

// ─── Экспорт ────────────────────────────────────────────────────────────────

module.exports = { WorldMatesBot };

// ─── Пример использования (закомментировано) ─────────────────────────────────
/*
const { WorldMatesBot } = require('./WorldMatesBotSDK');

const bot = new WorldMatesBot({
    botId: 'bot_ваш_id',
    token: 'bot_ваш_id:ваш_токен',
    debug: true
});

// Обработчик команды /start
bot.onCommand('start', async ({ message, bot }) => {
    const keyboard = WorldMatesBot.buildInlineKeyboard([
        WorldMatesBot.callbackButton('Помощь',  'help'),
        WorldMatesBot.callbackButton('О боте',  'about')
    ]);

    await bot.sendMessageWithKeyboard(
        message.from.id,
        'Привет! Я тестовый бот.',
        keyboard
    );
});

// Обработчик нажатия кнопки
bot.onCallbackQuery(async ({ callback, bot }) => {
    if (callback.data === 'help') {
        await bot.answerCallbackQuery(callback.id, 'Помощь!');
        await bot.sendMessage(callback.from.id, 'Справочная информация...');
    }
});

// Обработчик всех сообщений
bot.onMessage(async ({ message, bot }) => {
    await bot.sendMessage(message.from.id, `Ты написал: ${message.text}`);
});

// Запуск в режиме polling
bot.start();
*/
