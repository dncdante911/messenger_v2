'use strict';

/**
 * Channel Content Policy — WorldMates Messenger
 * ================================================
 * Allows channel owners to set the content level of their channel.
 *
 * Content levels:
 *   all_ages        — public, no adult content allowed (default)
 *   mature          — adult themes OK, but no explicit nudity
 *   adult_verified  — OnlyFans-style: explicit content allowed, subscribers must be PRO
 *
 * Rules:
 *   - Only the channel owner can change the policy
 *   - Setting "adult_verified" requires the owner to have is_pro = 1
 *   - Setting "adult_verified" automatically marks the channel as private/closed
 *   - Subscribers of an "adult_verified" channel must have is_pro = 1 to join
 *
 * Endpoints:
 *   GET  /api/node/channel/:channelId/content-policy  — get current policy
 *   POST /api/node/channel/:channelId/content-policy  — set policy
 */

const { requireAuth } = require('../../helpers/validate-token');

const VALID_LEVELS = ['all_ages', 'mature', 'adult_verified'];

// ── Helpers ───────────────────────────────────────────────────────────────────

async function getChannelOwner(ctx, channelId, userId) {
    const channel = await ctx.wo_pages.findOne({
        where:      { page_id: channelId },
        attributes: ['page_id', 'user_id', 'title', 'privacy'],
        raw:        true
    });
    if (!channel) return null;
    if (String(channel.user_id) !== String(userId)) return null;
    return channel;
}

async function getUserProStatus(ctx, userId) {
    const user = await ctx.wo_users.findOne({
        where:      { user_id: userId },
        attributes: ['is_pro', 'pro_time', 'pro_type'],
        raw:        true
    });
    if (!user) return false;
    const isPro  = String(user.is_pro) === '1';
    const proOk  = !user.pro_time || Number(user.pro_time) > Math.floor(Date.now() / 1000);
    return isPro && proOk;
}

async function getCurrentPolicy(ctx, channelId) {
    const policy = await ctx.wm_content_policy.findOne({
        where:      { entity_type: 'channel', entity_id: channelId },
        attributes: ['content_level'],
        raw:        true
    });
    return policy ? policy.content_level : 'all_ages';
}

// ── Route registration ────────────────────────────────────────────────────────

function registerContentPolicyRoutes(app, ctx) {
    const auth = requireAuth(ctx);

    // ── GET /api/node/channel/:channelId/content-policy ───────────────────────
    app.get('/api/node/channel/:channelId/content-policy', auth, async (req, res) => {
        try {
            const channelId = parseInt(req.params.channelId);
            if (!channelId) return res.json({ api_status: 400, error_message: 'Invalid channel ID' });

            const channel = await ctx.wo_pages.findOne({
                where:      { page_id: channelId },
                attributes: ['page_id', 'title', 'privacy'],
                raw:        true
            });
            if (!channel) return res.json({ api_status: 404, error_message: 'Channel not found' });

            const contentLevel = await getCurrentPolicy(ctx, channelId);

            return res.json({
                api_status:    200,
                channel_id:    channelId,
                content_level: contentLevel,
                is_private:    channel.privacy === '1' || channel.privacy === 1,
                adult_enabled: contentLevel === 'adult_verified',
                description: {
                    all_ages:       'Публичный канал, контент подходит всем возрастам',
                    mature:         'Канал 18+, откровенный контент без явной наготы',
                    adult_verified: 'Приватный канал только для PRO-подписчиков — разрешён откровенный контент'
                }[contentLevel] || ''
            });

        } catch (e) {
            console.error('[ContentPolicy] GET error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/channel/:channelId/content-policy ─────────────────────
    app.post('/api/node/channel/:channelId/content-policy', auth, async (req, res) => {
        try {
            const userId    = req.user.user_id;
            const channelId = parseInt(req.params.channelId);
            const newLevel  = (req.body.content_level || '').trim().toLowerCase();

            if (!channelId)
                return res.json({ api_status: 400, error_message: 'Invalid channel ID' });
            if (!VALID_LEVELS.includes(newLevel))
                return res.json({
                    api_status:    400,
                    error_message: `content_level must be one of: ${VALID_LEVELS.join(', ')}`
                });

            // Must be channel owner
            const channel = await getChannelOwner(ctx, channelId, userId);
            if (!channel)
                return res.json({ api_status: 403, error_message: 'Только владелец канала может изменить политику контента' });

            // adult_verified requires PRO subscription
            if (newLevel === 'adult_verified') {
                const isPro = await getUserProStatus(ctx, userId);
                if (!isPro)
                    return res.json({
                        api_status:    403,
                        error_message: 'Для включения режима adult_verified необходима PRO-подписка',
                        requires_pro:  true
                    });
            }

            // Upsert the content policy
            await ctx.wm_content_policy.upsert({
                entity_type:   'channel',
                entity_id:     channelId,
                content_level: newLevel,
                updated_at:    new Date()
            });

            // adult_verified → automatically make channel private
            if (newLevel === 'adult_verified') {
                await ctx.wo_pages.update(
                    { privacy: '1' },
                    { where: { page_id: channelId } }
                );
                console.log(`[ContentPolicy] Channel ${channelId} set to adult_verified (now private) by user ${userId}`);
            }

            // If downgrading from adult_verified to all_ages, make channel public again
            if (newLevel === 'all_ages') {
                await ctx.wo_pages.update(
                    { privacy: '0' },
                    { where: { page_id: channelId } }
                );
            }

            return res.json({
                api_status:    200,
                channel_id:    channelId,
                content_level: newLevel,
                is_private:    newLevel === 'adult_verified' ? true : (channel.privacy === '1'),
                message:       `Политика контента канала изменена на: ${newLevel}`
            });

        } catch (e) {
            console.error('[ContentPolicy] POST error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // ── POST /api/node/channel/:channelId/subscribe — gate for adult channels ─
    // Wraps existing subscription logic to add PRO gate
    app.post('/api/node/channel/:channelId/subscribe-gate', auth, async (req, res) => {
        try {
            const userId    = req.user.user_id;
            const channelId = parseInt(req.params.channelId);
            if (!channelId) return res.json({ api_status: 400, error_message: 'Invalid channel ID' });

            const contentLevel = await getCurrentPolicy(ctx, channelId);

            if (contentLevel === 'adult_verified') {
                const isPro = await getUserProStatus(ctx, userId);
                if (!isPro) {
                    return res.json({
                        api_status:    403,
                        error_message: 'Этот канал доступен только PRO-подписчикам',
                        requires_pro:  true,
                        content_level: 'adult_verified'
                    });
                }
            }

            // Gate passed — return success so client can proceed with real subscribe
            return res.json({
                api_status:    200,
                can_subscribe: true,
                content_level: contentLevel
            });

        } catch (e) {
            console.error('[ContentPolicy] subscribe-gate error:', e.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    });
}

module.exports = { registerContentPolicyRoutes };
