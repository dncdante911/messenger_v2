/**
 * Secret Messages — Server-Side Cleanup & Timer Support
 *
 * Endpoints:
 *   POST /api/node/chat/secret/set-timer   – встановити таймер для чату (remove_at mode)
 *   POST /api/node/chat/secret/cleanup     – видалити прострочені повідомлення (викликається клієнтом)
 *   POST /api/node/chat/send               – підтримує поле remove_at при відправці
 *
 * Логіка:
 *   - remove_at зберігається в Wo_Messages як Unix timestamp (секунди)
 *   - При відправці клієнт може передати remove_at = now + timerSeconds
 *   - Cleanup видаляє повідомлення де remove_at > 0 і remove_at <= NOW()
 *   - Сервер також очищає при кожному запиті get/loadmore для даного чату
 */

'use strict';

const { Op } = require('sequelize');

/**
 * Очистити прострочені секретні повідомлення між двома користувачами.
 * Викликається автоматично під час завантаження чату.
 *
 * @param {Object} ctx     - Sequelize context (ctx.Wo_Messages)
 * @param {number} fromId  - ID першого учасника
 * @param {number} toId    - ID другого учасника
 */
async function cleanupExpiredMessages(ctx, fromId, toId) {
    const nowUnix = Math.floor(Date.now() / 1000);
    try {
        await ctx.Wo_Messages.destroy({
            where: {
                remove_at: { [Op.gt]: 0, [Op.lte]: nowUnix },
                [Op.or]: [
                    { from_id: fromId, to_id: toId },
                    { from_id: toId,   to_id: fromId }
                ]
            }
        });
    } catch (err) {
        console.error('[SecretCleanup] cleanup error:', err.message);
    }
}

/**
 * POST /api/node/chat/secret/cleanup
 * Ручний виклик очищення від клієнта.
 *
 * Body: { user_id: number }
 */
function cleanupHandler(ctx, io) {
    return async (req, res) => {
        const selfId  = req.userId;
        const otherId = parseInt(req.body.user_id, 10);

        if (!otherId) {
            return res.status(400).json({ api_status: 400, error_message: 'user_id required' });
        }

        try {
            const nowUnix = Math.floor(Date.now() / 1000);
            const deleted = await ctx.Wo_Messages.destroy({
                where: {
                    remove_at: { [Op.gt]: 0, [Op.lte]: nowUnix },
                    [Op.or]: [
                        { from_id: selfId,  to_id: otherId },
                        { from_id: otherId, to_id: selfId  }
                    ]
                }
            });

            res.json({ api_status: 200, deleted_count: deleted });
        } catch (err) {
            console.error('[SecretCleanup] cleanup handler error:', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Cleanup failed' });
        }
    };
}

/**
 * POST /api/node/chat/secret/set-timer
 * Зберігає таймер за чатом (оновлює всі існуючі повідомлення або тільки нові).
 * Клієнт сам управляє таймером локально; цей endpoint лише для синхронізації.
 *
 * Body: {
 *   user_id: number,      // з ким чат
 *   timer_seconds: number // 0 = вимкнено
 * }
 */
function setTimerHandler(ctx, io) {
    return async (req, res) => {
        const selfId       = req.userId;
        const otherId      = parseInt(req.body.user_id, 10);
        const timerSeconds = parseInt(req.body.timer_seconds, 10) || 0;

        if (!otherId) {
            return res.status(400).json({ api_status: 400, error_message: 'user_id required' });
        }

        try {
            // Якщо таймер вимкнено - скидаємо remove_at у 0 для повідомлень
            // що ще не видалились (мали б видалитись раніше, але не встигли)
            if (timerSeconds === 0) {
                await ctx.Wo_Messages.update(
                    { remove_at: 0 },
                    {
                        where: {
                            remove_at: { [Op.gt]: 0 },
                            [Op.or]: [
                                { from_id: selfId,  to_id: otherId },
                                { from_id: otherId, to_id: selfId  }
                            ]
                        }
                    }
                );
            }

            // Повідомляємо іншого учасника через Socket.io про зміну таймеру
            const socketRoom = `user_${otherId}`;
            io.to(socketRoom).emit('secret_timer_changed', {
                from_id:       selfId,
                timer_seconds: timerSeconds
            });

            res.json({ api_status: 200, timer_seconds: timerSeconds });
        } catch (err) {
            console.error('[SecretTimer] set-timer error:', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Timer update failed' });
        }
    };
}

module.exports = {
    cleanupHandler,
    setTimerHandler,
    cleanupExpiredMessages
};
