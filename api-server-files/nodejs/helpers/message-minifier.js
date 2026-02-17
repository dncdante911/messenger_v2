/**
 * 📦 Message Minifier - Скорочення JSON payload для економії трафіку
 *
 * Замість повних назв полів використовуємо короткі скорочення.
 * Економія: ~40-50% розміру JSON
 */

/**
 * Мінімізувати об'єкт повідомлення (long → short keys)
 * @param {Object} msg - Повне повідомлення
 * @returns {Object} - Мінімізоване повідомлення
 */
function minifyMessage(msg) {
    return {
        f: msg.from_id,           // from_id
        t: msg.to_id,             // to_id
        m: msg.message_text,      // message_text
        ts: msg.timestamp,        // timestamp
        sn: msg.sender_name,      // sender_name
        sa: msg.sender_avatar,    // sender_avatar
        mt: msg.media_type,       // media_type (image/video/audio)
        mu: msg.media_url,        // media_url
        tu: msg.thumbnail_url,    // thumbnail_url (превью)
        r: msg.reply_to,          // reply_to (ID повідомлення на яке відповідаємо)
        id: msg.id,               // message id
        iv: msg.iv,               // encryption iv
        tag: msg.tag,             // encryption tag
        cv: msg.cipher_version    // cipher_version (v1/v2)
    };
}

/**
 * Розширити мінімізоване повідомлення назад до повного (short → long keys)
 * @param {Object} minMsg - Мінімізоване повідомлення
 * @returns {Object} - Повне повідомлення
 */
function expandMessage(minMsg) {
    return {
        from_id: minMsg.f,
        to_id: minMsg.t,
        message_text: minMsg.m,
        timestamp: minMsg.ts,
        sender_name: minMsg.sn,
        sender_avatar: minMsg.sa,
        media_type: minMsg.mt,
        media_url: minMsg.mu,
        thumbnail_url: minMsg.tu,
        reply_to: minMsg.r,
        id: minMsg.id,
        iv: minMsg.iv,
        tag: minMsg.tag,
        cipher_version: minMsg.cv
    };
}

/**
 * Мінімізувати групове повідомлення
 * @param {Object} msg - Повне групове повідомлення
 * @returns {Object} - Мінімізоване повідомлення
 */
function minifyGroupMessage(msg) {
    return {
        f: msg.from_id,
        g: msg.group_id,          // group_id
        m: msg.message_text,
        ts: msg.timestamp,
        sn: msg.sender_name,
        sa: msg.sender_avatar,
        mt: msg.media_type,
        mu: msg.media_url,
        tu: msg.thumbnail_url,
        r: msg.reply_to,
        id: msg.id,
        iv: msg.iv,
        tag: msg.tag,
        cv: msg.cipher_version
    };
}

/**
 * Розширити мінімізоване групове повідомлення
 * @param {Object} minMsg - Мінімізоване повідомлення
 * @returns {Object} - Повне повідомлення
 */
function expandGroupMessage(minMsg) {
    return {
        from_id: minMsg.f,
        group_id: minMsg.g,
        message_text: minMsg.m,
        timestamp: minMsg.ts,
        sender_name: minMsg.sn,
        sender_avatar: minMsg.sa,
        media_type: minMsg.mt,
        media_url: minMsg.mu,
        thumbnail_url: minMsg.tu,
        reply_to: minMsg.r,
        id: minMsg.id,
        iv: minMsg.iv,
        tag: minMsg.tag,
        cipher_version: minMsg.cv
    };
}

/**
 * Мінімізувати typing indicator
 * @param {Object} data - Повний typing об'єкт
 * @returns {Object} - Мінімізований
 */
function minifyTyping(data) {
    return {
        f: data.from_id,
        t: data.to_id,
        ty: data.typing  // typing (boolean)
    };
}

/**
 * Мінімізувати online status
 * @param {Object} data - Повний status об'єкт
 * @returns {Object} - Мінімізований
 */
function minifyOnlineStatus(data) {
    return {
        u: data.user_id,
        o: data.is_online,        // online (boolean)
        l: data.last_seen         // last_seen (timestamp)
    };
}

module.exports = {
    minifyMessage,
    expandMessage,
    minifyGroupMessage,
    expandGroupMessage,
    minifyTyping,
    minifyOnlineStatus
};
