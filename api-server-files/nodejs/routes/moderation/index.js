'use strict';

/**
 * Moderation API — WorldMates Messenger
 * ======================================
 * REST эндпоинты для системы модерации контента.
 * Доступны только пользователям с is_admin = 1.
 *
 * Эндпоинты:
 *   GET  /api/node/moderation/queue              — список ожидающих проверки
 *   POST /api/node/moderation/approve            — одобрить { queue_id }
 *   POST /api/node/moderation/reject             — отклонить { queue_id, reason }
 *   GET  /api/node/moderation/blacklist          — список хэш-блэклиста
 *   POST /api/node/moderation/blacklist/add      — добавить хэш { sha256, reason }
 *   POST /api/node/moderation/content-policy     — установить политику канала/группы
 *   GET  /api/node/moderation/stats              — статистика очереди
 */

const { Op }               = require('sequelize');
const { requireAuth }      = require('../../helpers/validate-token');
const { addToHashBlacklist } = require('../../helpers/content-moderator');
const path = require('path');
const fs   = require('fs');

// ─── Middleware: только модераторы ────────────────────────────────────────────

function requireModerator(ctx) {
    return [
        requireAuth(ctx),
        async (req, res, next) => {
            try {
                const user = await ctx.wo_users.findOne({
                    where:      { user_id: req.user.user_id },
                    attributes: ['is_admin'],
                    raw:        true
                });
                if (!user || String(user.is_admin) !== '1') {
                    return res.status(403).json({ api_status: 403, error_message: 'Доступ запрещён' });
                }
                next();
            } catch (e) {
                console.error('[Moderation] requireModerator error:', e.message);
                return res.status(500).json({ api_status: 500, error_message: 'Server error' });
            }
        }
    ];
}

// ─── Регистрация маршрутов ────────────────────────────────────────────────────

function registerModerationRoutes(app, ctx) {

    // ── GET /api/node/moderation/queue ────────────────────────────────────────
    app.get('/api/node/moderation/queue', requireModerator(ctx), async (req, res) => {
        try {
            const limit    = Math.min(parseInt(req.query.limit    || '20', 10), 100);
            const offset   = parseInt(req.query.offset   || '0',  10);
            const minScore = parseFloat(req.query.min_score || '0');

            const { count, rows } = await ctx.wm_moderation_queue.findAndCountAll({
                where:  { status: 'pending', nudenet_score: { [Op.gte]: minScore } },
                order:  [['nudenet_score', 'DESC'], ['created_at', 'ASC']],
                limit,
                offset,
                raw:    true
            });

            return res.json({
                api_status: 200,
                total:      count,
                items:      rows.map(row => ({
                    ...row,
                    nudenet_labels: row.nudenet_labels ? JSON.parse(row.nudenet_labels) : []
                }))
            });
        } catch (e) {
            console.error('[Moderation] queue error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/moderation/approve ─────────────────────────────────────
    app.post('/api/node/moderation/approve', requireModerator(ctx), async (req, res) => {
        try {
            const queueId = parseInt(req.body.queue_id, 10);
            if (!queueId) return res.json({ api_status: 400, error_message: 'queue_id обязателен' });

            const item = await ctx.wm_moderation_queue.findOne({ where: { id: queueId } });
            if (!item) return res.json({ api_status: 404, error_message: 'Элемент не найден' });
            if (item.status !== 'pending') {
                return res.json({ api_status: 400, error_message: `Уже обработан: ${item.status}` });
            }

            await item.update({ status: 'approved', reviewed_by: req.user.user_id, reviewed_at: new Date() });
            return res.json({ api_status: 200, message: 'Одобрено' });
        } catch (e) {
            console.error('[Moderation] approve error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/moderation/reject ──────────────────────────────────────
    app.post('/api/node/moderation/reject', requireModerator(ctx), async (req, res) => {
        try {
            const queueId = parseInt(req.body.queue_id, 10);
            const reason  = (req.body.reason || 'explicit').trim();

            if (!queueId) return res.json({ api_status: 400, error_message: 'queue_id обязателен' });

            const item = await ctx.wm_moderation_queue.findOne({ where: { id: queueId } });
            if (!item) return res.json({ api_status: 404, error_message: 'Элемент не найден' });
            if (item.status !== 'pending') {
                return res.json({ api_status: 400, error_message: `Уже обработан: ${item.status}` });
            }

            // Хэш в блэклист — повторная загрузка будет заблокирована (SHA-256 + pHash)
            if (item.sha256_hash) {
                const phashInt = item.phash_int ? BigInt(item.phash_int) : null;
                await addToHashBlacklist(ctx, item.sha256_hash, reason, req.user.user_id, phashInt);
            }

            // Физически удалить файл с диска
            if (item.file_path) {
                const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');
                const absPath   = path.join(SITE_ROOT, item.file_path);
                try {
                    if (fs.existsSync(absPath)) {
                        await fs.promises.unlink(absPath);
                        console.log(`[Moderation] Файл удалён: ${absPath}`);
                    }
                } catch (unlinkErr) {
                    console.warn(`[Moderation] Не удалось удалить файл ${absPath}:`, unlinkErr.message);
                }
            }

            await item.update({ status: 'rejected', reviewed_by: req.user.user_id, reviewed_at: new Date() });
            return res.json({ api_status: 200, message: 'Отклонено, файл удалён, хэш в блэклисте' });
        } catch (e) {
            console.error('[Moderation] reject error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── GET /api/node/moderation/blacklist ────────────────────────────────────
    app.get('/api/node/moderation/blacklist', requireModerator(ctx), async (req, res) => {
        try {
            const limit  = Math.min(parseInt(req.query.limit  || '50', 10), 200);
            const offset = parseInt(req.query.offset || '0', 10);

            const { count, rows } = await ctx.wm_content_hash_blacklist.findAndCountAll({
                order: [['created_at', 'DESC']],
                limit,
                offset,
                raw:   true
            });

            return res.json({ api_status: 200, total: count, items: rows });
        } catch (e) {
            console.error('[Moderation] blacklist error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/moderation/blacklist/add ───────────────────────────────
    app.post('/api/node/moderation/blacklist/add', requireModerator(ctx), async (req, res) => {
        try {
            const sha256 = (req.body.sha256 || '').trim().toLowerCase();
            const reason = (req.body.reason || 'manual').trim();

            if (!sha256 || sha256.length !== 64) {
                return res.json({ api_status: 400, error_message: 'sha256 должен быть 64-символьным hex' });
            }

            await addToHashBlacklist(ctx, sha256, reason, req.user.user_id);
            return res.json({ api_status: 200, message: 'Добавлено в блэклист' });
        } catch (e) {
            console.error('[Moderation] blacklist/add error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/moderation/content-policy ──────────────────────────────
    app.post('/api/node/moderation/content-policy', requireModerator(ctx), async (req, res) => {
        try {
            const entityType   = req.body.entity_type;
            const entityId     = parseInt(req.body.entity_id, 10);
            const contentLevel = req.body.content_level;

            if (!['channel', 'group'].includes(entityType)) {
                return res.json({ api_status: 400, error_message: 'entity_type: channel | group' });
            }
            if (!entityId || entityId <= 0) {
                return res.json({ api_status: 400, error_message: 'Неверный entity_id' });
            }
            if (!['all_ages', 'mature', 'adult_verified'].includes(contentLevel)) {
                return res.json({ api_status: 400, error_message: 'content_level: all_ages | mature | adult_verified' });
            }

            await ctx.wm_content_policy.upsert({
                entity_type:   entityType,
                entity_id:     entityId,
                content_level: contentLevel,
                updated_by:    req.user.user_id,
                updated_at:    new Date()
            });

            return res.json({ api_status: 200, message: 'Политика контента обновлена' });
        } catch (e) {
            console.error('[Moderation] content-policy error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── GET /api/node/moderation/stats ────────────────────────────────────────
    app.get('/api/node/moderation/stats', requireModerator(ctx), async (req, res) => {
        try {
            const [pending, approved, rejected, blacklistTotal] = await Promise.all([
                ctx.wm_moderation_queue.count({ where: { status: 'pending' } }),
                ctx.wm_moderation_queue.count({ where: { status: 'approved' } }),
                ctx.wm_moderation_queue.count({ where: { status: 'rejected' } }),
                ctx.wm_content_hash_blacklist.count()
            ]);

            return res.json({
                api_status: 200,
                stats: {
                    queue:     { pending, approved, rejected, total: pending + approved + rejected },
                    blacklist: { total: blacklistTotal }
                }
            });
        } catch (e) {
            console.error('[Moderation] stats error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── GET /api/node/moderation/violators ────────────────────────────────────
    // Систематические нарушители: пользователи с наибольшим кол-вом отклонённого контента
    // и/или жалоб. Объединяем данные из wm_moderation_queue + Wo_Reports.
    //
    // Query params:
    //   limit     {number}  — результатов на страницу (макс. 100, по умолчанию 20)
    //   offset    {number}  — смещение
    //   days      {number}  — за последние N дней (по умолчанию 30)
    //   min_score {number}  — минимальный суммарный "счёт нарушений"
    app.get('/api/node/moderation/violators', requireModerator(ctx), async (req, res) => {
        try {
            const limit  = Math.min(parseInt(req.query.limit  || '20', 10), 100);
            const offset = parseInt(req.query.offset || '0', 10);
            const days   = Math.min(parseInt(req.query.days   || '30', 10), 365);

            const since = new Date(Date.now() - days * 86400 * 1000);
            const sinceTs = Math.floor(since.getTime() / 1000);

            const { sequelize } = ctx;

            // 1. Отклонённый контент (rejected в очереди модерации)
            const rejectedRows = await sequelize.query(
                `SELECT sender_id AS user_id,
                        COUNT(*) AS rejected_count,
                        MAX(nudenet_score) AS max_nudenet_score
                 FROM wm_moderation_queue
                 WHERE status = 'rejected'
                   AND created_at >= :since
                   AND sender_id > 0
                 GROUP BY sender_id`,
                { replacements: { since }, type: sequelize.QueryTypes.SELECT }
            );

            // 2. Жалобы (Wo_Reports)
            const reportRows = await sequelize.query(
                `SELECT user_id,
                        COUNT(*)                    AS report_count,
                        COUNT(DISTINCT profile_id)  AS unique_reporters
                 FROM Wo_Reports
                 WHERE time >= :sinceTs
                   AND user_id > 0
                 GROUP BY user_id`,
                { replacements: { sinceTs }, type: sequelize.QueryTypes.SELECT }
            );

            // 3. Контент в очереди на проверке (pending — ещё не рассмотрен)
            const pendingRows = await sequelize.query(
                `SELECT sender_id AS user_id,
                        COUNT(*) AS pending_count
                 FROM wm_moderation_queue
                 WHERE status = 'pending'
                   AND created_at >= :since
                   AND sender_id > 0
                 GROUP BY sender_id`,
                { replacements: { since }, type: sequelize.QueryTypes.SELECT }
            );

            // Сводим всё в карту по user_id
            const map = new Map();

            for (const r of rejectedRows) {
                const uid = r.user_id;
                if (!map.has(uid)) map.set(uid, { user_id: uid, rejected_count: 0, report_count: 0, unique_reporters: 0, pending_count: 0, max_nudenet_score: 0 });
                const e = map.get(uid);
                e.rejected_count   = parseInt(r.rejected_count, 10);
                e.max_nudenet_score = parseFloat(r.max_nudenet_score) || 0;
            }
            for (const r of reportRows) {
                const uid = r.user_id;
                if (!map.has(uid)) map.set(uid, { user_id: uid, rejected_count: 0, report_count: 0, unique_reporters: 0, pending_count: 0, max_nudenet_score: 0 });
                const e = map.get(uid);
                e.report_count    = parseInt(r.report_count, 10);
                e.unique_reporters = parseInt(r.unique_reporters, 10);
            }
            for (const r of pendingRows) {
                const uid = r.user_id;
                if (!map.has(uid)) map.set(uid, { user_id: uid, rejected_count: 0, report_count: 0, unique_reporters: 0, pending_count: 0, max_nudenet_score: 0 });
                map.get(uid).pending_count = parseInt(r.pending_count, 10);
            }

            // Считаем суммарный "violation_score" для сортировки
            // Логика: rejected > unique_reporters > pending
            let violators = Array.from(map.values()).map(v => ({
                ...v,
                violation_score: v.rejected_count * 3 + v.unique_reporters * 2 + v.pending_count
            }));

            // Сортируем по violation_score DESC
            violators.sort((a, b) => b.violation_score - a.violation_score);

            const total  = violators.length;
            const paged  = violators.slice(offset, offset + limit);

            // Добавляем базовые данные о пользователях
            const userIds = paged.map(v => v.user_id);
            let userMap   = {};
            if (userIds.length > 0) {
                const users = await ctx.wo_users.findAll({
                    where:      { user_id: userIds },
                    attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar', 'email'],
                    raw:        true
                });
                for (const u of users) userMap[u.user_id] = u;
            }

            const items = paged.map(v => ({
                ...v,
                user: userMap[v.user_id] || null
            }));

            return res.json({
                api_status: 200,
                period_days: days,
                total,
                items
            });
        } catch (e) {
            console.error('[Moderation] violators error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    console.log('[Moderation] Маршруты модерации зарегистрированы');
}

module.exports = { registerModerationRoutes };
