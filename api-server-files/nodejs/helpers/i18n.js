'use strict';

/**
 * Minimal i18n helper for server-side user-facing messages.
 *
 * Usage:
 *   const { t } = require('../helpers/i18n');
 *
 *   // In a route handler:
 *   const L = t(req);
 *   return res.json({ error_message: L.server_error });
 *
 *   // String-producing functions:
 *   const L = t(req);
 *   await sendSms(phone, L.sms_reset(code));
 *
 * Language detection:
 *   Reads Accept-Language header sent by the Android app.
 *   Falls back to Ukrainian ('uk') when header is absent or unrecognised.
 *
 * Supported locales: 'uk' (Ukrainian, default), 'ru' (Russian).
 */

const LOCALES = {
    uk: require('../locales/uk'),
    ru: require('../locales/ru'),
};

const DEFAULT_LOCALE = 'uk';

/**
 * Returns locale strings for the given request or language tag.
 * @param {import('express').Request|string} reqOrLang  Express request or a BCP-47 language tag.
 * @returns {Object} locale strings object
 */
function t(reqOrLang) {
    let lang = DEFAULT_LOCALE;

    if (typeof reqOrLang === 'string') {
        lang = reqOrLang.toLowerCase().slice(0, 2);
    } else if (reqOrLang && reqOrLang.headers) {
        // Accept-Language: ru, uk;q=0.9, en;q=0.8
        const al = (reqOrLang.headers['accept-language'] || '').toLowerCase();
        if (al.startsWith('ru')) lang = 'ru';
        else if (al.startsWith('uk') || al.startsWith('ua')) lang = 'uk';
    }

    return LOCALES[lang] || LOCALES[DEFAULT_LOCALE];
}

module.exports = { t };
