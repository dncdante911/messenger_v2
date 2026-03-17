'use strict';

/**
 * Signal Protocol — REST endpoints.
 *
 * Всі endpoints вимагають валідного access-token заголовку.
 *
 * ── Особисті чати (X3DH + Double Ratchet) ───────────────────────────────────
 *   POST /api/node/signal/register     — завантажити identity + pre-key bundle
 *   GET  /api/node/signal/bundle/:id   — отримати pre-key bundle іншого користувача
 *   POST /api/node/signal/replenish    — завантажити нові one-time pre-keys
 *   GET  /api/node/signal/prekey-count — скільки OPKs лишилось на сервері
 *
 * ── Групові чати (Sender Key Distribution Protocol) ─────────────────────────
 *   POST /api/node/signal/group/distribute
 *       — завантажити SenderKeyDistribution для учасників групи.
 *         Body: { group_id, distributions: [{recipient_id, distribution}] }
 *
 *   GET  /api/node/signal/group/pending-distributions
 *       — отримати всі невидані distributions для поточного користувача.
 *         Query: ?group_id=N (опціонально)
 *
 *   POST /api/node/signal/group/confirm-delivery
 *       — підтвердити отримання distributions.
 *         Body: { distribution_ids: [id, id, ...] }
 *
 *   POST /api/node/signal/group/invalidate-sender-key
 *       — інвалідувати SenderKey учасника (при виході з групи).
 *         Body: { group_id, sender_id }  (тільки admin або сам sender)
 *
 * Сервер зберігає лише ПУБЛІЧНІ ключі та ЗАШИФРОВАНІ distribution payload-и.
 * Приватні ключі НІКОЛИ не залишають пристрій.
 */

const signalStore      = require('../helpers/signal-store');
const signalGroupStore = require('../helpers/signal-group-store');

// ─── POST /api/node/signal/register ──────────────────────────────────────────

function register(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            const {
                identity_key,
                signed_prekey_id,
                signed_prekey,
                signed_prekey_sig,
                prekeys: prekeysRaw,
            } = req.body;

            if (!identity_key || !signed_prekey) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'identity_key and signed_prekey are required',
                });
            }

            // Parse prekeys — may arrive as JSON string or as parsed object
            let prekeys = [];
            if (prekeysRaw) {
                try {
                    prekeys = typeof prekeysRaw === 'string'
                        ? JSON.parse(prekeysRaw)
                        : prekeysRaw;
                } catch {
                    return res.status(400).json({
                        api_status:    400,
                        error_message: 'prekeys must be a valid JSON array',
                    });
                }
            }

            const result = await signalStore.saveKeyBundle(ctx, userId, {
                identityKey:      identity_key,
                signedPreKeyId:   parseInt(signed_prekey_id) || 0,
                signedPreKey:     signed_prekey,
                signedPreKeySig:  signed_prekey_sig || '',
                prekeys,
            });

            console.log(`[Signal] User ${userId} registered key bundle (OPKs: ${prekeys.length}, identityKeyChanged: ${result.identityKeyChanged})`);

            // Notify ALL contacts via Socket.IO when identity key changed (device change / reinstall).
            // Each contact that has a stale DR session with this user must clear it and
            // re-establish X3DH on the next message, otherwise decryption will fail.
            // We emit to the contact's own socket room (String(contactId)) so they receive it.
            if (result.identityKeyChanged && ctx.io) {
                try {
                    const contacts = await ctx.wo_userschat.findAll({
                        where:      { user_id: userId },
                        attributes: ['conversation_user_id'],
                        raw:        true,
                    });
                    for (const c of contacts) {
                        ctx.io.to(String(c.conversation_user_id)).emit('signal:identity_changed', { user_id: userId });
                    }
                    console.log(`[Signal] Emitted signal:identity_changed for user ${userId} to ${contacts.length} contacts`);
                } catch (notifyErr) {
                    console.warn('[Signal] Failed to notify contacts of identity change:', notifyErr.message);
                }
            }

            res.json({ api_status: 200, message: 'Key bundle registered', opk_count: result.opk_count });

        } catch (err) {
            console.error('[Signal/register]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to register key bundle' });
        }
    };
}

// ─── GET /api/node/signal/bundle/:userId ──────────────────────────────────────

function getBundle(ctx) {
    return async (req, res) => {
        try {
            const targetUserId = parseInt(req.params.userId);
            if (!targetUserId || isNaN(targetUserId)) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'userId path parameter is required',
                });
            }

            const bundle = await signalStore.getKeyBundle(ctx, targetUserId);
            if (!bundle) {
                return res.status(404).json({
                    api_status:    404,
                    error_message: 'No Signal key bundle registered for this user',
                });
            }

            console.log(`[Signal] Bundle fetched for user ${targetUserId} ` +
                        `(remaining OPKs: ${bundle.remaining_prekeys})`);
            res.json({ api_status: 200, ...bundle });

        } catch (err) {
            console.error('[Signal/bundle]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to fetch key bundle' });
        }
    };
}

// ─── POST /api/node/signal/replenish ─────────────────────────────────────────

function replenish(ctx) {
    return async (req, res) => {
        try {
            const userId    = req.userId;
            let prekeysRaw  = req.body.prekeys;

            if (!prekeysRaw) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'prekeys is required',
                });
            }

            let prekeys;
            try {
                prekeys = typeof prekeysRaw === 'string'
                    ? JSON.parse(prekeysRaw)
                    : prekeysRaw;
            } catch {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'prekeys must be a valid JSON array',
                });
            }

            const result = await signalStore.replenishPrekeys(ctx, userId, prekeys);
            if (!result) {
                return res.status(404).json({
                    api_status:    404,
                    error_message: 'User has no Signal key bundle (register first)',
                });
            }

            console.log(`[Signal] User ${userId} replenished OPKs: +${result.added} (total: ${result.total})`);
            res.json({
                api_status: 200,
                message:    `Added ${result.added} one-time pre-keys`,
                count:      result.total,
            });

        } catch (err) {
            console.error('[Signal/replenish]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to replenish pre-keys' });
        }
    };
}

// ─── GET /api/node/signal/prekey-count ───────────────────────────────────────

function preKeyCount(ctx) {
    return async (req, res) => {
        try {
            const count = await signalStore.getRemainingPreKeyCount(ctx, req.userId);
            res.json({ api_status: 200, count });
        } catch (err) {
            console.error('[Signal/prekey-count]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Failed to get pre-key count' });
        }
    };
}

// ─── POST /api/node/signal/group/distribute ───────────────────────────────────

/**
 * Завантажує SenderKeyDistribution від поточного користувача до списку учасників.
 *
 * Body:
 *   group_id       {number}  — ID групи
 *   distributions  {Array}   — [{recipient_id: number, distribution: string}]
 *
 * distributions[].distribution — зашифрований SenderKeyDistributionMessage,
 *   зашифрований індивідуальним Double Ratchet сеансом між sender і recipient.
 *   Сервер не може розшифрувати цей payload.
 */
function groupDistribute(ctx, io) {
    return async (req, res) => {
        try {
            const senderId = req.userId;
            const groupId  = parseInt(req.body.group_id);

            if (!groupId || isNaN(groupId)) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'group_id обов\'язковий',
                });
            }

            // Перевірити членство в групі
            const member = await ctx.wo_groupchatusers.findOne({
                where: { group_id: groupId, user_id: senderId, active: '1' },
                raw:   true,
            });
            if (!member) {
                return res.status(403).json({
                    api_status:    403,
                    error_message: 'Ви не є учасником цієї групи',
                });
            }

            let distributionsRaw = req.body.distributions;
            if (!distributionsRaw) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'distributions обов\'язковий',
                });
            }
            if (typeof distributionsRaw === 'string') {
                try { distributionsRaw = JSON.parse(distributionsRaw); }
                catch {
                    return res.status(400).json({
                        api_status:    400,
                        error_message: 'distributions має бути JSON масивом',
                    });
                }
            }
            if (!Array.isArray(distributionsRaw)) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'distributions має бути масивом',
                });
            }

            const result = await signalGroupStore.saveDistributions(
                ctx, groupId, senderId, distributionsRaw
            );

            // ── Real-time notification to online recipients ───────────────────
            // For each recipient that has a pending distribution, emit a
            // signal:group_distributions_pending event so they fetch immediately
            // instead of waiting for a polling interval.
            if (io && result.saved > 0) {
                const recipientIds = distributionsRaw
                    .map(d => parseInt(d.recipient_id))
                    .filter(id => id && !isNaN(id));

                for (const recipientId of recipientIds) {
                    io.to(String(recipientId)).emit('signal:group_distributions_pending', {
                        group_id:  groupId,
                        sender_id: senderId,
                    });
                }
            }

            console.log(`[Signal/group] User ${senderId} розподілив SenderKey у групі ${groupId} (${result.saved} recipients)`);
            res.json({
                api_status: 200,
                message:    'SenderKey distribution збережено',
                saved:      result.saved,
            });

        } catch (err) {
            console.error('[Signal/group/distribute]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Помилка збереження distribution' });
        }
    };
}

// ─── GET /api/node/signal/group/pending-distributions ────────────────────────

/**
 * Повертає всі невидані SenderKey distributions для поточного користувача.
 * Опціональний query-параметр group_id для фільтрації по конкретній групі.
 *
 * Query: ?group_id=123
 */
function groupPendingDistributions(ctx) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const groupId = parseInt(req.query.group_id || req.body.group_id) || 0;

            const distributions = await signalGroupStore.getPendingDistributions(
                ctx, userId, groupId
            );

            res.json({
                api_status:    200,
                distributions,
                count:         distributions.length,
            });

        } catch (err) {
            console.error('[Signal/group/pending-distributions]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Помилка отримання distributions' });
        }
    };
}

// ─── POST /api/node/signal/group/confirm-delivery ────────────────────────────

/**
 * Підтверджує отримання SenderKey distributions.
 * Після цього записи позначаються як delivered=1 (soft-delete).
 *
 * Body: { distribution_ids: [id, id, ...] }
 */
function groupConfirmDelivery(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            let   ids    = req.body.distribution_ids;

            if (!ids) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'distribution_ids обов\'язковий',
                });
            }
            if (typeof ids === 'string') {
                try { ids = JSON.parse(ids); } catch { ids = []; }
            }
            if (!Array.isArray(ids)) {
                return res.status(400).json({
                    api_status:    400,
                    error_message: 'distribution_ids має бути масивом',
                });
            }

            const result = await signalGroupStore.confirmDelivery(ctx, userId, ids);

            res.json({
                api_status: 200,
                message:    `Підтверджено ${result.confirmed} distributions`,
                confirmed:  result.confirmed,
            });

        } catch (err) {
            console.error('[Signal/group/confirm-delivery]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Помилка підтвердження' });
        }
    };
}

// ─── POST /api/node/signal/group/invalidate-sender-key ───────────────────────

/**
 * Інвалідує SenderKey учасника групи.
 * Викликається при виході/видаленні учасника — решта учасників
 * повинні будуть перерозподілити ключі (клієнт сам ініціює re-key).
 *
 * Body: { group_id, sender_id }
 * Дозволено: сам sender_id або admin/owner групи.
 */
function groupInvalidateSenderKey(ctx) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const groupId  = parseInt(req.body.group_id);
            const senderId = parseInt(req.body.sender_id);

            if (!groupId || isNaN(groupId)) {
                return res.status(400).json({ api_status: 400, error_message: 'group_id обов\'язковий' });
            }
            if (!senderId || isNaN(senderId)) {
                return res.status(400).json({ api_status: 400, error_message: 'sender_id обов\'язковий' });
            }

            // Перевірка прав: тільки сам sender або адміністратор групи
            const isSelf = (userId === senderId);
            const isAdmin = isSelf ? true : await (async () => {
                const m = await ctx.wo_groupchatusers.findOne({
                    attributes: ['role'],
                    where: {
                        group_id: groupId,
                        user_id:  userId,
                        active:   '1',
                    },
                    raw: true,
                });
                return m && ['owner', 'admin', 'moderator'].includes(m.role);
            })();

            if (!isAdmin) {
                return res.status(403).json({
                    api_status:    403,
                    error_message: 'Недостатньо прав для інвалідації ключа',
                });
            }

            const result = await signalGroupStore.invalidateSenderKey(ctx, groupId, senderId);

            console.log(`[Signal/group] SenderKey user=${senderId} інвалідовано у групі ${groupId} (${result.invalidated} записів)`);
            res.json({
                api_status:  200,
                message:     'SenderKey інвалідовано',
                invalidated: result.invalidated,
            });

        } catch (err) {
            console.error('[Signal/group/invalidate-sender-key]', err.message);
            res.status(500).json({ api_status: 500, error_message: 'Помилка інвалідації ключа' });
        }
    };
}

// ─── auth middleware (same logic as private-chats/index.js) ──────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });
        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Signal/auth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── route registration ───────────────────────────────────────────────────────

/**
 * Register Signal Protocol routes on [app].
 * Requires ctx.signal_keys (Sequelize model) and ctx.wo_appssessions (for auth).
 */
function registerSignalRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // ── Особисті чати (X3DH pre-key server) ─────────────────────────────────
    app.post('/api/node/signal/register',       auth, register(ctx));
    app.get ('/api/node/signal/bundle/:userId',  auth, getBundle(ctx));
    app.post('/api/node/signal/replenish',       auth, replenish(ctx));
    app.get ('/api/node/signal/prekey-count',    auth, preKeyCount(ctx));

    // ── Групові чати (Sender Key Distribution Protocol) ──────────────────
    // io passed to groupDistribute so it can notify online recipients in real-time
    app.post('/api/node/signal/group/distribute',           auth, groupDistribute(ctx, io));
    app.get ('/api/node/signal/group/pending-distributions', auth, groupPendingDistributions(ctx));
    app.post('/api/node/signal/group/pending-distributions', auth, groupPendingDistributions(ctx));
    app.post('/api/node/signal/group/confirm-delivery',      auth, groupConfirmDelivery(ctx));
    app.post('/api/node/signal/group/invalidate-sender-key', auth, groupInvalidateSenderKey(ctx));

    // ── E2EE Key Backup (client-side encrypted, server is blind) ─────────────
    app.post  ('/api/node/signal/key-backup', auth, uploadKeyBackup(ctx));
    app.get   ('/api/node/signal/key-backup', auth, downloadKeyBackup(ctx));
    app.delete('/api/node/signal/key-backup', auth, deleteKeyBackup(ctx));

    console.log('[Signal] Routes registered: register, bundle, replenish, prekey-count, group/*, key-backup');
}

// ─── E2EE Key Backup handlers ─────────────────────────────────────────────────
// The server stores an opaque encrypted blob.  It cannot decrypt it — only the
// client who knows the backup password can.  The client uses PBKDF2+AES-256-GCM.

function uploadKeyBackup(ctx) {
    return async (req, res) => {
        try {
            const userId  = req.userId;
            const { encrypted_payload, salt, iv, version = 1 } = req.body;
            if (!encrypted_payload || !salt || !iv) {
                return res.status(400).json({ api_status: 400, error_message: 'encrypted_payload, salt, iv required' });
            }
            const now = Math.floor(Date.now() / 1000);
            const [record, created] = await ctx.wm_key_backups.findOrCreate({
                where:    { user_id: userId },
                defaults: { user_id: userId, encrypted_payload, salt, iv, version, created_at: now, updated_at: now },
            });
            if (!created) {
                await record.update({ encrypted_payload, salt, iv, version, updated_at: now });
            }
            res.json({ api_status: 200, created, updated_at: now });
        } catch (e) {
            console.error('[Signal/key-backup/upload]', e.message);
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    };
}

function downloadKeyBackup(ctx) {
    return async (req, res) => {
        try {
            const record = await ctx.wm_key_backups.findOne({ where: { user_id: req.userId }, raw: true });
            if (!record) return res.json({ api_status: 200, backup: null });
            res.json({
                api_status: 200,
                backup: {
                    encrypted_payload: record.encrypted_payload,
                    salt:              record.salt,
                    iv:                record.iv,
                    version:           record.version,
                    created_at:        record.created_at,
                    updated_at:        record.updated_at,
                },
            });
        } catch (e) {
            console.error('[Signal/key-backup/download]', e.message);
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    };
}

function deleteKeyBackup(ctx) {
    return async (req, res) => {
        try {
            await ctx.wm_key_backups.destroy({ where: { user_id: req.userId } });
            res.json({ api_status: 200, deleted: true });
        } catch (e) {
            console.error('[Signal/key-backup/delete]', e.message);
            res.status(500).json({ api_status: 500, error_message: e.message });
        }
    };
}

module.exports = { registerSignalRoutes };
