'use strict';

/**
 * Signal Protocol — Sender Key сховище для групових чатів (серверна сторона).
 *
 * Протокол Signal Sender Key (store-and-forward):
 *   1. Учасник A генерує SenderKey для групи та шифрує його
 *      індивідуальним Signal DR-сеансом для кожного учасника B, C, D...
 *   2. A надсилає зашифровані distribution payload-и на сервер.
 *   3. Сервер зберігає їх до моменту, поки B/C/D не забрали свій payload.
 *   4. B/C/D забирають payload, розшифровують локально і підтверджують отримання.
 *   5. Після підтвердження записи позначаються як delivered=1.
 *
 * Сервер НІКОЛИ не бачить вміст SenderKey — він зберігає лише
 * зашифрований blob, котрий може розшифрувати тільки конкретний одержувач.
 *
 * Таблиця: signal_group_sender_keys
 */

// ─── Зберегти SenderKey distributions (від одного відправника до багатьох) ───

/**
 * Зберігає масив distributions від sender до списку recipients.
 *
 * @param {object} ctx           — Sequelize model context (ctx.signal_group_sender_keys)
 * @param {number} groupId       — ID групи
 * @param {number} senderId      — ID відправника SenderKey
 * @param {Array}  distributions — [{recipient_id: int, distribution: string (Base64)}]
 * @returns {object}             — { saved: number }
 */
async function saveDistributions(ctx, groupId, senderId, distributions) {
    if (!Array.isArray(distributions) || distributions.length === 0) {
        return { saved: 0 };
    }

    const now    = new Date();
    const rows   = [];
    const seen   = new Set();

    for (const d of distributions) {
        const recipientId = parseInt(d.recipient_id);
        const payload     = String(d.distribution || '').trim();

        if (!recipientId || !payload) continue;
        // Пропускаємо відправника самому собі та дублікати в межах одного запиту
        if (recipientId === senderId) continue;
        const key = `${recipientId}`;
        if (seen.has(key)) continue;
        seen.add(key);

        rows.push({
            group_id:     groupId,
            sender_id:    senderId,
            recipient_id: recipientId,
            distribution: payload,
            delivered:    0,
            created_at:   now,
            delivered_at: null,
        });
    }

    if (rows.length === 0) return { saved: 0 };

    await ctx.signal_group_sender_keys.bulkCreate(rows);
    return { saved: rows.length };
}

// ─── Отримати pending distributions для конкретного recipient ────────────────

/**
 * Повертає всі невидані distributions для recipient у вказаній групі
 * (або всіх групах, якщо groupId = 0).
 * НЕ позначає їх як delivered — клієнт має підтвердити отримання окремим запитом.
 *
 * @param {object} ctx        — Sequelize model context
 * @param {number} userId     — ID поточного користувача (recipient)
 * @param {number} [groupId]  — Фільтр по групі (0 = всі групи)
 * @returns {Array}           — [{id, group_id, sender_id, distribution, created_at}]
 */
async function getPendingDistributions(ctx, userId, groupId = 0) {
    const { Op } = require('sequelize');

    const where = {
        recipient_id: userId,
        delivered:    0,
    };
    if (groupId > 0) where.group_id = groupId;

    const rows = await ctx.signal_group_sender_keys.findAll({
        where,
        order: [['id', 'ASC']],
        raw:   true,
    });

    return rows.map(r => ({
        id:           r.id,
        group_id:     r.group_id,
        sender_id:    r.sender_id,
        distribution: r.distribution,
        created_at:   r.created_at,
    }));
}

// ─── Підтвердити отримання distributions ─────────────────────────────────────

/**
 * Позначає вказані distributions як delivered.
 * Перевіряє, що всі IDs належать саме цьому recipient (захист від перебору).
 *
 * @param {object} ctx          — Sequelize model context
 * @param {number} userId       — ID поточного користувача
 * @param {Array}  distributionIds — [id, id, ...]
 * @returns {object}            — { confirmed: number }
 */
async function confirmDelivery(ctx, userId, distributionIds) {
    if (!Array.isArray(distributionIds) || distributionIds.length === 0) {
        return { confirmed: 0 };
    }

    const { Op } = require('sequelize');

    const ids = distributionIds.map(id => parseInt(id)).filter(id => id > 0);
    if (ids.length === 0) return { confirmed: 0 };

    const now = new Date();
    const [updated] = await ctx.signal_group_sender_keys.update(
        { delivered: 1, delivered_at: now },
        {
            where: {
                id:           { [Op.in]: ids },
                recipient_id: userId,      // захист: лише власні distributions
                delivered:    0,           // ідемпотентність
            },
        }
    );

    return { confirmed: updated };
}

// ─── Інвалідувати SenderKey для учасника (вихід / видалення з групи) ─────────

/**
 * Видаляє всі nevydani distributions від вказаного sender у групі.
 * Викликається, коли sender покидає групу або його видаляють —
 * інші учасники більше не повинні використовувати його SenderKey.
 *
 * @param {object} ctx      — Sequelize model context
 * @param {number} groupId  — ID групи
 * @param {number} senderId — ID учасника, що покинув групу
 * @returns {object}        — { invalidated: number }
 */
async function invalidateSenderKey(ctx, groupId, senderId) {
    const deleted = await ctx.signal_group_sender_keys.destroy({
        where: {
            group_id:  groupId,
            sender_id: senderId,
            delivered: 0,
        },
    });
    return { invalidated: deleted };
}

// ─── Очистити старі доставлені записи (garbage collection) ───────────────────

/**
 * Видаляє delivered=1 записи старше вказаної кількості днів.
 * Рекомендується запускати за розкладом (cron/setInterval).
 *
 * @param {object} ctx     — Sequelize model context
 * @param {number} days    — записи старше N днів видаляються (default: 30)
 * @returns {object}       — { cleaned: number }
 */
async function cleanDeliveredOlderThan(ctx, days = 30) {
    const { Op } = require('sequelize');
    const cutoff = new Date(Date.now() - days * 86400 * 1000);

    const deleted = await ctx.signal_group_sender_keys.destroy({
        where: {
            delivered:    1,
            delivered_at: { [Op.lt]: cutoff },
        },
    });
    return { cleaned: deleted };
}

module.exports = {
    saveDistributions,
    getPendingDistributions,
    confirmDelivery,
    invalidateSenderKey,
    cleanDeliveredOlderThan,
};
