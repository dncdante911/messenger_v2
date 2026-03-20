'use strict';
/**
 * Background cron jobs for WorldMates Node.js server.
 *
 * Handles tasks that the PHP cron-job.php also covers, so the app keeps
 * working even if the external crontab is not configured:
 *
 *   ┌─ premiumExpiry      — every 15 min
 *   ├─ storyCleanup       — every 60 min
 *   └─ notificationPurge  — every 24 h
 *
 * Start via: startCronJobs(ctx)  — call once in main.js after DB init.
 */

const { Op } = require('sequelize');

// ─── helpers ────────────────────────────────────────────────────────────────

function nowSec() {
    return Math.floor(Date.now() / 1000);
}

function log(tag, msg) {
    const ts = new Date().toISOString().replace('T', ' ').slice(0, 19);
    console.log(`[${ts}] [CRON:${tag}] ${msg}`);
}

// ─── 1. PREMIUM EXPIRY ───────────────────────────────────────────────────────
// Runs every 15 minutes.
// Finds users whose pro_time (= expiry unix timestamp) is in the past,
// sets is_pro = 0, verified = 0, and un-boosts their posts/pages.

async function runPremiumExpiry(ctx) {
    try {
        const now = nowSec();

        // Find expired pro users
        const expired = await ctx.wo_users.unscoped().findAll({
            attributes: ['user_id'],
            where: {
                is_pro: '1',
                admin: { [Op.ne]: '1' },
                pro_time: { [Op.gt]: 0, [Op.lt]: now }
            },
            raw: true
        });

        if (!expired.length) return;

        const ids = expired.map(u => u.user_id);
        log('PREMIUM', `Expiring ${ids.length} user(s): [${ids.join(', ')}]`);

        // Revoke pro + verified badge
        await ctx.wo_users.unscoped().update(
            { is_pro: '0', verified: '0', pro_type: '0' },
            { where: { user_id: { [Op.in]: ids } } }
        );

        // Un-boost their posts
        await ctx.wo_posts.update(
            { boosted: '0' },
            { where: { user_id: { [Op.in]: ids } } }
        );

        // Un-boost their pages
        await ctx.wo_pages.update(
            { boosted: '0' },
            { where: { user_id: { [Op.in]: ids } } }
        );

        // Send an in-app notification to each user
        const notifications = ids.map(uid => ({
            recipient_id: uid,
            type:         'pro_expired',
            text:         'Ваша Premium-підписка закінчилась. Продовжіть, щоб зберегти всі переваги.',
            url:          'index.php?link1=home',
            seen:         '0',
            time:         now
        }));
        await ctx.wo_notification.bulkCreate(notifications, { ignoreDuplicates: true });

        log('PREMIUM', `Done. ${ids.length} subscription(s) expired.`);
    } catch (err) {
        log('PREMIUM', `ERROR: ${err.message}`);
    }
}

// ─── 2. STORY CLEANUP ────────────────────────────────────────────────────────
// Runs every 60 minutes.
// Deletes all expired stories and their related rows
// (media, comments, reactions, views, mutes).

async function runStoryCleanup(ctx) {
    try {
        const now = nowSec();

        const expired = await ctx.wo_userstory.findAll({
            attributes: ['id', 'user_id'],
            where: {
                expire: { [Op.gt]: '0', [Op.lt]: String(now) }
            },
            raw: true
        });

        if (!expired.length) return;

        const storyIds  = expired.map(s => s.id);
        const userIds   = [...new Set(expired.map(s => s.user_id))];

        log('STORIES', `Cleaning up ${storyIds.length} expired story(ies)…`);

        // Delete related tables in dependency order
        await ctx.wo_userstorymedia.destroy({ where: { story_id: { [Op.in]: storyIds } } });
        await ctx.wo_storycomments.destroy({ where: { story_id: { [Op.in]: storyIds } } });
        await ctx.wo_storyreactions.destroy({ where: { story_id: { [Op.in]: storyIds } } });
        await ctx.wo_story_seen.destroy({ where: { story_id: { [Op.in]: storyIds } } });

        // Mutes are per-user, not per-story — only remove mutes for users who
        // have NO remaining active stories, to avoid muting real ongoing stories.
        for (const userId of userIds) {
            const remaining = await ctx.wo_userstory.count({
                where: {
                    user_id: userId,
                    expire: { [Op.gte]: String(now) }
                }
            });
            if (remaining === 0) {
                await ctx.wo_mute_story.destroy({ where: { story_user_id: userId } });
            }
        }

        // Finally delete the stories themselves
        await ctx.wo_userstory.destroy({ where: { id: { [Op.in]: storyIds } } });

        log('STORIES', `Done. ${storyIds.length} story(ies) deleted.`);
    } catch (err) {
        log('STORIES', `ERROR: ${err.message}`);
    }
}

// ─── 3. NOTIFICATION PURGE ───────────────────────────────────────────────────
// Runs every 24 hours.
// Deletes seen notifications older than 5 days (same logic as PHP cron).

async function runNotificationPurge(ctx) {
    try {
        const cutoff = nowSec() - 60 * 60 * 24 * 5; // 5 days ago

        const deleted = await ctx.wo_notification.destroy({
            where: {
                time:  { [Op.lt]: cutoff },
                seen:  { [Op.ne]: '0' }
            }
        });

        if (deleted > 0) {
            log('NOTIF', `Purged ${deleted} old seen notification(s).`);

            // Update last_notification_delete_run in config
            await ctx.wo_config.update(
                { value: String(nowSec()) },
                { where: { name: 'last_notification_delete_run' } }
            ).catch(() => {}); // Config row may not exist — ignore
        }
    } catch (err) {
        log('NOTIF', `ERROR: ${err.message}`);
    }
}

// ─── 4. SESSION CLEANUP ──────────────────────────────────────────────────────
// Runs every 24 hours.
// Deletes Wo_AppsSessions rows whose access token has expired.
// Without this, the table grows forever — a zombie session row per login,
// plus one extra row per token refresh. With 30k DAU this is ~1M rows/year.
//
// Only removes rows where expires_at IS set and in the past.
// Legacy rows (expires_at = NULL) are kept — they are permanent-session devices.

async function runSessionCleanup(ctx) {
    try {
        const now = nowSec();
        const deleted = await ctx.wo_appssessions.destroy({
            where: {
                expires_at: { [Op.gt]: 0, [Op.lt]: now }
            }
        });
        if (deleted > 0) {
            log('SESSIONS', `Purged ${deleted} expired session row(s).`);
        }
    } catch (err) {
        log('SESSIONS', `ERROR: ${err.message}`);
    }
}

// ─── ENTRY POINT ─────────────────────────────────────────────────────────────

function startCronJobs(ctx) {
    const PREMIUM_INTERVAL  = 15 * 60 * 1000;       // 15 min
    const STORY_INTERVAL    = 60 * 60 * 1000;        // 1 hour
    const NOTIF_INTERVAL    = 24 * 60 * 60 * 1000;  // 24 hours
    const SESSION_INTERVAL  = 24 * 60 * 60 * 1000;  // 24 hours

    // Run once immediately on startup, then on schedule
    runPremiumExpiry(ctx);
    runStoryCleanup(ctx);
    runNotificationPurge(ctx);
    // Session cleanup runs with a 5-minute delay so it doesn't race startup DB queries
    setTimeout(() => {
        runSessionCleanup(ctx);
        setInterval(() => runSessionCleanup(ctx), SESSION_INTERVAL);
    }, 5 * 60 * 1000);

    setInterval(() => runPremiumExpiry(ctx),    PREMIUM_INTERVAL);
    setInterval(() => runStoryCleanup(ctx),      STORY_INTERVAL);
    setInterval(() => runNotificationPurge(ctx), NOTIF_INTERVAL);

    log('INIT', `Cron jobs started — premium every 15 min, stories every 1 h, notifs/sessions every 24 h`);
}

module.exports = { startCronJobs };
