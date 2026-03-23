'use strict';

/**
 * Report User/Message/Media — WorldMates Messenger
 * =================================================
 * POST /api/node/user/report
 *   Принимает жалобу на пользователя, сообщение или медиафайл.
 *   При достижении REPORT_AUTO_QUEUE_COUNT жалоб на одного пользователя
 *   за последние REPORT_AUTO_QUEUE_MINUTES минут — его последние медиа
 *   автоматически добавляются в очередь модерации (авто-блюр).
 *
 * Body:
 *   user_id      {number}  — ID пользователя на которого жалоба
 *   text         {string}  — причина жалобы (опционально)
 *   report_type  {string}  — 'user' | 'message' | 'media' (по умолчанию 'user')
 *   message_id   {number}  — ID сообщения (для report_type=message)
 *   media_path   {string}  — путь к медиафайлу (для report_type=media)
 */

const { Op }           = require('sequelize');
const { requireAuth }  = require('../../helpers/validate-token');

// ─── Конфиг ───────────────────────────────────────────────────────────────────

const AUTO_QUEUE_COUNT   = parseInt(process.env.REPORT_AUTO_QUEUE_COUNT   || '3',  10);
const AUTO_QUEUE_MINUTES = parseInt(process.env.REPORT_AUTO_QUEUE_MINUTES || '60', 10);

// ─── Авто-очередь при накоплении жалоб ───────────────────────────────────────

/**
 * Считает жалобы на targetUserId за последние N минут.
 * Если >= AUTO_QUEUE_COUNT — добавляет последние незапрошенные медиа в очередь.
 */
async function checkAndAutoQueue(ctx, targetUserId) {
    try {
        const since = new Date(Date.now() - AUTO_QUEUE_MINUTES * 60 * 1000);

        const recentReportCount = await ctx.db.Wo_Reports.count({
            where: {
                user_id: targetUserId,
                // только уникальные репортеры, чтобы один не накрутил
                time: { [Op.gte]: Math.floor(since.getTime() / 1000) }
            }
        });

        if (recentReportCount < AUTO_QUEUE_COUNT) return;

        // Порог достигнут — проверяем, не ставили ли уже в очередь
        const alreadyQueued = await ctx.wm_moderation_queue.count({
            where: {
                sender_id:     targetUserId,
                trigger_reason: 'report_threshold',
                status:        'pending',
                created_at:    { [Op.gte]: since }
            }
        });

        if (alreadyQueued > 0) return; // уже в очереди, не дублируем

        // Ищем последние медиа-сообщения пользователя (до 5 штук за последние 24 часа)
        const oneDayAgo = Math.floor((Date.now() - 86400 * 1000) / 1000);
        const mediaMessages = await ctx.wo_messages.findAll({
            where: {
                from_id:  targetUserId,
                media:    { [Op.ne]: '' },
                time:     { [Op.gte]: oneDayAgo }
            },
            order:      [['time', 'DESC']],
            limit:      5,
            attributes: ['id', 'media', 'mediaFileName', 'to_id', 'group_id', 'time'],
            raw:        true
        });

        for (const msg of mediaMessages) {
            // Не добавляем одно и то же медиа дважды
            const exists = await ctx.wm_moderation_queue.findOne({
                where: { file_url: msg.media, status: 'pending' },
                raw:   true
            });
            if (exists) continue;

            await ctx.wm_moderation_queue.create({
                file_path:      msg.media,
                file_url:       msg.media,
                media_type:     'image', // консервативно — media-upload знает точный тип
                sender_id:      targetUserId,
                channel_id:     0,
                group_id:       msg.group_id || 0,
                chat_type:      msg.group_id ? 'group' : 'private',
                content_level:  'all_ages',
                sha256_hash:    '',
                nudenet_labels: null,
                nudenet_score:  0,
                trigger_reason: 'report_threshold',
                status:         'pending',
                created_at:     new Date()
            });
        }

        // Если жалоба на конкретное сообщение без медиа — просто логируем в очереди
        if (mediaMessages.length === 0) {
            await ctx.wm_moderation_queue.create({
                file_path:      '',
                file_url:       '',
                media_type:     'file',
                sender_id:      targetUserId,
                channel_id:     0,
                group_id:       0,
                chat_type:      'private',
                content_level:  'all_ages',
                sha256_hash:    '',
                nudenet_labels: null,
                nudenet_score:  0,
                trigger_reason: `report_threshold:user_${targetUserId}:no_media`,
                status:         'pending',
                created_at:     new Date()
            });
        }

        console.warn(
            `[report-user] Авто-очередь: user=${targetUserId} ` +
            `reports=${recentReportCount} media_queued=${mediaMessages.length}`
        );
    } catch (e) {
        console.error('[report-user] checkAndAutoQueue error:', e.message);
    }
}

// ─── Регистрация маршрутов ────────────────────────────────────────────────────

function registerReportUserRoutes(app, ctx) {
    const { db } = ctx;

    app.post('/api/node/user/report', requireAuth(ctx), async (req, res) => {
        try {
            const reporterId  = req.user.user_id;
            const userId      = parseInt(req.body.user_id, 10);
            const text        = (req.body.text || '').trim();
            const reportType  = ['user', 'message', 'media'].includes(req.body.report_type)
                ? req.body.report_type : 'user';
            const messageId   = parseInt(req.body.message_id, 10) || 0;
            const mediaPath   = (req.body.media_path || '').trim();

            if (!userId || userId <= 0) {
                return res.json({ api_status: 400, error_message: 'Invalid user_id' });
            }

            if (userId === reporterId) {
                return res.json({ api_status: 400, error_message: 'Cannot report yourself' });
            }

            // Проверяем что пользователь существует
            const target = await db.Wo_Users.findOne({ where: { user_id: userId } });
            if (!target) {
                return res.json({ api_status: 404, error_message: 'User not found' });
            }

            // Дедупликация: один человек не может подать две жалобы на одного за 10 минут
            const tenMinAgo = Math.floor((Date.now() - 10 * 60 * 1000) / 1000);
            const duplicate = await db.Wo_Reports.findOne({
                where: {
                    profile_id: reporterId,
                    user_id:    userId,
                    time:       { [Op.gte]: tenMinAgo }
                }
            });
            if (duplicate) {
                return res.json({ api_status: 429, error_message: 'You already reported this user recently' });
            }

            await db.Wo_Reports.create({
                user_id:     userId,
                profile_id:  reporterId,
                text:        text || null,
                time:        Math.floor(Date.now() / 1000),
                seen:        0,
                report_type: reportType,
                message_id:  messageId,
                media_path:  mediaPath || null
            });

            // Асинхронно проверяем порог — не блокируем ответ клиенту
            setImmediate(() => checkAndAutoQueue(ctx, userId));

            return res.json({ api_status: 200, message: 'Report submitted' });
        } catch (err) {
            console.error('[report-user]', err);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerReportUserRoutes };
