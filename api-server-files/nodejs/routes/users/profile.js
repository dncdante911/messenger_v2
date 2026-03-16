'use strict';

/**
 * User Profile REST API
 *
 * All endpoints require access-token header (or body/query param).
 *
 * ─── Own profile ─────────────────────────────────────────────────────────────
 *   GET  /api/node/users/me                    Full own profile
 *   PUT  /api/node/users/me                    Update name, about, gender, birthday, socials, etc.
 *   PUT  /api/node/users/me/privacy            Update privacy settings
 *   PUT  /api/node/users/me/password           Change password
 *   PUT  /api/node/users/me/notifications      Update notification preferences
 *
 * ─── Other users ─────────────────────────────────────────────────────────────
 *   GET  /api/node/users/:id                   View another user's profile
 *   GET  /api/node/users/search?q=&limit=&offset=  Search users
 *
 * ─── Social graph ─────────────────────────────────────────────────────────────
 *   GET  /api/node/users/:id/followers?limit=&offset=
 *   GET  /api/node/users/:id/following?limit=&offset=
 *   POST /api/node/users/:id/follow            Follow a user
 *   DELETE /api/node/users/:id/follow          Unfollow a user
 *
 * ─── Blocking ────────────────────────────────────────────────────────────────
 *   GET  /api/node/users/me/blocked            List of blocked users
 *   POST /api/node/users/:id/block             Block a user
 *   DELETE /api/node/users/:id/block           Unblock a user
 */

const bcrypt    = require('bcryptjs');
const md5       = require('md5');
const crypto    = require('crypto');
const { Op }    = require('sequelize');
const { requireAuth } = require('../../helpers/validate-token');

const BCRYPT_ROUNDS  = 10;
const ONLINE_TIMEOUT = 5 * 60; // 5 minutes — user is "online" if seen within this window

// ─── Internal helpers ─────────────────────────────────────────────────────────

/** Build absolute URL for a stored file path. */
function mediaUrl(ctx, path) {
    if (!path || path === '' || path === '0') return '';
    if (/^https?:\/\//.test(path)) return path;
    const base = (ctx.globalconfig?.site_url || '').replace(/\/$/, '');
    return `${base}/${path.replace(/^\//, '')}`;
}

/** Parse the details JSON string safely. */
function parseDetails(raw) {
    try {
        return typeof raw === 'string' ? JSON.parse(raw) : (raw || {});
    } catch {
        return {};
    }
}

/**
 * Serialize a user row into the public profile shape.
 * viewerUserId — the user making the request (for privacy checks).
 * isSelf       — true when viewer is the same as the profile owner.
 * extra        — additional fields like relationship, already computed.
 */
function serializeUser(ctx, u, { isSelf = false, extra = {} } = {}) {
    const now     = Math.floor(Date.now() / 1000);
    const details = parseDetails(u.details);
    const isOnline = u.lastseen && (now - Number(u.lastseen)) < ONLINE_TIMEOUT;

    // Counts also exposed as top-level strings — Android User model reads them directly
    const followersCount = String(parseInt(details.followers_count) || 0);
    const followingCount = String(parseInt(details.following_count) || 0);

    const base = {
        user_id:    u.user_id,
        username:   u.username,
        first_name: u.first_name || '',
        last_name:  u.last_name  || '',
        avatar:     mediaUrl(ctx, u.avatar),
        cover:      mediaUrl(ctx, u.cover),
        about:      u.about || '',
        gender:     u.gender || 'male',
        birthday:   u.birthday !== '0000-00-00' ? (u.birthday || '') : '',
        address:    u.address || '',
        city:       u.city    || '',
        state:      u.state   || '',
        zip:        u.zip     || '',
        website:    u.website || '',
        working:    u.working || '',
        working_link: u.working_link || '',
        school:     u.school  || '',
        country_id: String(u.country_id || 0),
        timezone:   u.timezone || '',
        language:   u.language || 'english',
        // Social links (also kept flat for Android compat)
        facebook:   u.facebook  || '',
        twitter:    u.twitter   || '',
        instagram:  u.instagram || '',
        linkedin:   u.linkedin  || '',
        youtube:    u.youtube   || '',
        // Grouped social (for web/future clients)
        social: {
            facebook:  u.facebook  || '',
            twitter:   u.twitter   || '',
            instagram: u.instagram || '',
            linkedin:  u.linkedin  || '',
            youtube:   u.youtube   || '',
        },
        // Counts — top-level strings (Android reads followers_count, following_count directly)
        followers_count: followersCount,
        following_count: followingCount,
        likes_count:     String(parseInt(details.likes_count)  || 0),
        groups_count:    String(parseInt(details.groups_count) || 0),
        // Nested details object (Android UserDetails)
        details: {
            post_count:      parseInt(details.post_count)      || 0,
            album_count:     parseInt(details.album_count)     || 0,
            following_count: parseInt(details.following_count) || 0,
            followers_count: parseInt(details.followers_count) || 0,
            groups_count:    parseInt(details.groups_count)    || 0,
            likes_count:     parseInt(details.likes_count)     || 0,
        },
        // Nested stats (for web/future clients)
        stats: {
            followers: parseInt(details.followers_count) || 0,
            following: parseInt(details.following_count) || 0,
            posts:     parseInt(details.post_count)      || 0,
        },
        // Online status
        lastseen:        u.lastseen ? Number(u.lastseen) : 0,
        lastseen_status: isOnline ? 'online' : 'offline',  // Android reads this string
        online: {
            is_online: isOnline,
            last_seen: u.lastseen ? Number(u.lastseen) : 0,
        },
        verified:  u.verified  || '0',
        is_pro:    u.is_pro    || '0',
        pro_type:  parseInt(u.pro_type || '0'),
        active:    u.active    || '1',
        joined:    u.joined    || 0,
        relationship_id: u.relationship_id || 0,
        status:    u.status    || '0',
        balance:   u.balance   || '0',
        wallet:    u.wallet    || '0.00',
        // Profile customization
        profile_accent:       u.profile_accent       || '#667EEA',
        profile_badge:        u.profile_badge        || '',
        profile_header_style: u.profile_header_style || 'gradient',
    };

    if (isSelf) {
        // Own profile — include sensitive fields and full privacy/notification config
        base.email        = u.email        || '';
        base.phone_number = u.phone_number || '';
        base.two_factor   = u.two_factor   || 0;
        base.privacy = {
            follow_privacy:          u.follow_privacy          || '0',
            message_privacy:         u.message_privacy         || '0',
            birth_privacy:           u.birth_privacy           || '0',
            friend_privacy:          u.friend_privacy          || '0',
            visit_privacy:           u.visit_privacy           || '0',
            showlastseen:            u.showlastseen            || '1',
            share_my_location:       u.share_my_location       ?? 1,
            confirm_followers:       u.confirm_followers       || '0',
            show_activities_privacy: u.show_activities_privacy || '1',
        };
        base.notifications = {
            e_liked:            u.e_liked            || '1',
            e_shared:           u.e_shared           || '1',
            e_wondered:         u.e_wondered         || '1',
            e_commented:        u.e_commented         || '1',
            e_followed:         u.e_followed         || '1',
            e_accepted:         u.e_accepted         || '1',
            e_visited:          u.e_visited          || '1',
            e_mentioned:        u.e_mentioned        || '1',
            e_sentme_msg:       u.e_sentme_msg       || '0',
            e_joined_group:     u.e_joined_group     || '1',
            e_liked_page:       u.e_liked_page       || '1',
            e_profile_wall_post: u.e_profile_wall_post || '1',
        };
    } else {
        // Other user's profile — include relationship block
        base.relationship = extra.relationship || {
            is_following:    false,
            is_following_me: false,
            follow_pending:  false,
            is_blocked:      false,
            can_follow:      true,
            can_message:     true,
        };
        // Respect last-seen privacy
        if (u.showlastseen === '0') {
            base.lastseen        = 0;
            base.lastseen_status = 'offline';
            base.online          = { is_online: false, last_seen: 0 };
        }
    }

    return base;
}

/**
 * Minimal user card used in lists (followers, search results, etc.)
 */
function serializeUserCard(ctx, u, relationship = {}) {
    const now      = Math.floor(Date.now() / 1000);
    const isOnline = u.lastseen && (now - Number(u.lastseen)) < ONLINE_TIMEOUT;
    const showSeen = u.showlastseen !== '0';
    return {
        user_id:         u.user_id,
        username:        u.username,
        first_name:      u.first_name || '',
        last_name:       u.last_name  || '',
        // Combined full name (Android SearchUser + BlockedUser models expect this)
        name:            `${u.first_name || ''} ${u.last_name || ''}`.trim() || u.username || '',
        avatar:          mediaUrl(ctx, u.avatar),
        about:           u.about || '',
        verified:        u.verified  || '0',
        is_pro:          u.is_pro    || '0',
        // Flat lastseen fields (BlockedUser, SearchUser models use these)
        lastseen:        showSeen ? (u.lastseen ? Number(u.lastseen) : 0) : 0,
        lastseen_status: showSeen ? (isOnline ? 'online' : 'offline') : 'offline',
        // Nested online object (older clients use this)
        online: {
            is_online: showSeen ? isOnline : false,
            last_seen: showSeen ? (u.lastseen ? Number(u.lastseen) : 0) : 0,
        },
        ...relationship,
    };
}

/** Compute relationship info between viewerId and targetId. */
async function getRelationship(ctx, viewerId, targetId) {
    const [followRow, reverseRow, blockRow] = await Promise.all([
        ctx.wo_followers.findOne({ where: { follower_id: viewerId, following_id: targetId }, raw: true }),
        ctx.wo_followers.findOne({ where: { follower_id: targetId, following_id: viewerId }, raw: true }),
        ctx.wo_blocks.findOne({
            where: {
                [Op.or]: [
                    { blocker: viewerId, blocked: targetId },
                    { blocker: targetId, blocked: viewerId },
                ]
            },
            raw: true,
        }),
    ]);

    const isBlocked      = !!blockRow;
    const isFollowing    = !!followRow && followRow.active === 1;
    const followPending  = !!followRow && followRow.active === 0;
    const isFollowingMe  = !!reverseRow && reverseRow.active === 1;

    return {
        is_following:    isFollowing,
        is_following_me: isFollowingMe,
        follow_pending:  followPending,
        is_blocked:      isBlocked,
        can_follow:      !isBlocked,
        can_message:     !isBlocked,
    };
}

/**
 * Atomically update the followers_count / following_count in Wo_Users.details.
 * delta: +1 or -1
 */
async function updateFollowStats(ctx, followingId, followerId, delta) {
    // following_id's followers_count changes
    // follower_id's following_count changes
    const applyDelta = async (userId, field) => {
        try {
            const user = await ctx.wo_users.findOne({ where: { user_id: userId }, attributes: ['details'], raw: true });
            if (!user) return;
            const d = parseDetails(user.details);
            d[field] = Math.max(0, (parseInt(d[field]) || 0) + delta);
            await ctx.wo_users.update({ details: JSON.stringify(d) }, { where: { user_id: userId } });
        } catch (e) {
            console.warn(`[Profile] updateFollowStats(${userId}, ${field}): ${e.message}`);
        }
    };
    await Promise.all([
        applyDelta(followingId, 'followers_count'),
        applyDelta(followerId,  'following_count'),
    ]);
}

// ─── GET /api/node/users/me ───────────────────────────────────────────────────

function getMe(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const user = await ctx.wo_users.findOne({ where: { user_id: req.userId }, raw: true });
            if (!user) return res.json({ api_status: 400, error_message: 'User not found' });

            const data = serializeUser(ctx, user, { isSelf: true });
            return res.json({ api_status: 200, user_data: data, user: data });
        } catch (err) {
            console.error('[Profile/getMe]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── PUT /api/node/users/me ───────────────────────────────────────────────────

const ALLOWED_UPDATE_FIELDS = [
    'first_name', 'last_name', 'about', 'gender', 'birthday',
    'address', 'city', 'state',
    'website', 'facebook', 'twitter', 'instagram', 'linkedin', 'youtube',
    'language',
];

const VALID_ACCENT_COLORS = [
    '#667EEA', '#764BA2', '#FF6B35', '#4CAF50',
    '#F44336', '#00BCD4', '#E91E63', '#FF9800',
    '#795548', '#607D8B', '#009688', '#3F51B5',
];

const VALID_HEADER_STYLES = ['gradient', 'minimal', 'pattern'];

function updateMe(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const updates = {};

            for (const field of ALLOWED_UPDATE_FIELDS) {
                if (req.body[field] !== undefined) {
                    updates[field] = String(req.body[field]).trim().substring(0, 300);
                }
            }

            // Validate gender
            if (updates.gender && !['male', 'female', 'other'].includes(updates.gender)) {
                return res.json({ api_status: 400, error_message: 'Invalid gender value' });
            }

            // Validate birthday format YYYY-MM-DD
            if (updates.birthday && updates.birthday !== '' && !/^\d{4}-\d{2}-\d{2}$/.test(updates.birthday)) {
                return res.json({ api_status: 400, error_message: 'Birthday must be in YYYY-MM-DD format' });
            }

            // Username change — extra validation
            if (req.body.username !== undefined) {
                const newUsername = String(req.body.username).trim().toLowerCase();
                if (!/^[a-z0-9_]{5,32}$/.test(newUsername)) {
                    return res.json({ api_status: 400, error_message: 'Username must be 5-32 characters, letters, digits and underscores only' });
                }
                const exists = await ctx.wo_users.findOne({ where: { username: newUsername, user_id: { [Op.ne]: req.userId } } });
                if (exists) return res.json({ api_status: 400, error_message: 'This username is already taken' });
                updates.username = newUsername;
            }

            if (Object.keys(updates).length === 0) {
                return res.json({ api_status: 400, error_message: 'No valid fields to update' });
            }

            updates.last_data_update = Math.floor(Date.now() / 1000);

            await ctx.wo_users.update(updates, { where: { user_id: req.userId } });

            const user = await ctx.wo_users.findOne({ where: { user_id: req.userId }, raw: true });
            const data = serializeUser(ctx, user, { isSelf: true });
            return res.json({ api_status: 200, message: 'Profile updated', user_data: data, user: data });

        } catch (err) {
            console.error('[Profile/updateMe]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── PUT /api/node/users/me/privacy ──────────────────────────────────────────

const PRIVACY_FIELDS = {
    follow_privacy:          ['0', '1'],
    message_privacy:         ['0', '1', '2'],
    birth_privacy:           ['0', '1', '2'],
    friend_privacy:          ['0', '1', '2', '3'],
    visit_privacy:           ['0', '1'],
    showlastseen:            ['0', '1'],
    confirm_followers:       ['0', '1'],
    show_activities_privacy: ['0', '1'],
    share_my_location:       [0, 1, '0', '1'],
};

function updatePrivacy(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const updates = {};

            for (const [field, allowed] of Object.entries(PRIVACY_FIELDS)) {
                if (req.body[field] !== undefined) {
                    const val = req.body[field];
                    // Coerce to string for comparison but keep numeric for share_my_location
                    const strVal = String(val);
                    const allowedStr = allowed.map(String);
                    if (!allowedStr.includes(strVal)) {
                        return res.json({ api_status: 400, error_message: `Invalid value for ${field}` });
                    }
                    updates[field] = field === 'share_my_location' ? parseInt(strVal) : strVal;
                }
            }

            if (Object.keys(updates).length === 0) {
                return res.json({ api_status: 400, error_message: 'No valid privacy fields provided' });
            }

            await ctx.wo_users.update(updates, { where: { user_id: req.userId } });

            return res.json({ api_status: 200, message: 'Privacy settings updated', privacy: updates });

        } catch (err) {
            console.error('[Profile/updatePrivacy]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── PUT /api/node/users/me/notifications ────────────────────────────────────

const NOTIFICATION_FIELDS = [
    'e_liked', 'e_shared', 'e_wondered', 'e_commented', 'e_followed',
    'e_accepted', 'e_visited', 'e_mentioned', 'e_sentme_msg',
    'e_joined_group', 'e_liked_page', 'e_profile_wall_post',
];

function updateNotifications(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const updates = {};

            for (const field of NOTIFICATION_FIELDS) {
                if (req.body[field] !== undefined) {
                    const val = String(req.body[field]);
                    if (val !== '0' && val !== '1') {
                        return res.json({ api_status: 400, error_message: `${field} must be 0 or 1` });
                    }
                    updates[field] = val;
                }
            }

            if (Object.keys(updates).length === 0) {
                return res.json({ api_status: 400, error_message: 'No notification fields provided' });
            }

            await ctx.wo_users.update(updates, { where: { user_id: req.userId } });

            return res.json({ api_status: 200, message: 'Notification settings updated' });

        } catch (err) {
            console.error('[Profile/updateNotifications]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── PUT /api/node/users/me/password ─────────────────────────────────────────

async function verifyPassword(plaintext, storedHash) {
    if (!plaintext || !storedHash) return false;
    if (/^\$2[aby]\$/.test(storedHash))           return bcrypt.compare(plaintext, storedHash);
    if (/^[0-9a-f]{40}$/i.test(storedHash))       return crypto.createHash('sha1').update(plaintext).digest('hex') === storedHash.toLowerCase();
    if (/^[0-9a-f]{32}$/i.test(storedHash))       return md5(plaintext) === storedHash.toLowerCase();
    return false;
}

function changePassword(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const currentPass = (req.body.current_password || '').trim();
        const newPass     = (req.body.new_password     || '').trim();

        if (!currentPass || !newPass) {
            return res.json({ api_status: 400, error_message: 'current_password and new_password are required' });
        }
        if (newPass.length < 6) {
            return res.json({ api_status: 400, error_message: 'New password must be at least 6 characters' });
        }

        try {
            const user = await ctx.wo_users.unscoped().findOne({ where: { user_id: req.userId }, raw: true });
            if (!user) return res.json({ api_status: 400, error_message: 'User not found' });

            const ok = await verifyPassword(currentPass, user.password);
            if (!ok) return res.json({ api_status: 400, error_message: 'Current password is incorrect' });

            const newHash = await bcrypt.hash(newPass, BCRYPT_ROUNDS);
            await ctx.wo_users.unscoped().update({ password: newHash }, { where: { user_id: req.userId } });

            // Invalidate all OTHER sessions (keep current one)
            await ctx.wo_appssessions.destroy({
                where: {
                    user_id:    req.userId,
                    session_id: { [Op.ne]: req.accessToken },
                }
            }).catch(() => {});

            return res.json({ api_status: 200, message: 'Password changed successfully' });

        } catch (err) {
            console.error('[Profile/changePassword]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── GET /api/node/users/:id ──────────────────────────────────────────────────

function getUserById(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        // Own profile redirect
        if (targetId === req.userId) {
            const self = await ctx.wo_users.findOne({ where: { user_id: req.userId }, raw: true });
            if (!self) return res.json({ api_status: 400, error_message: 'User not found' });
            const selfData = serializeUser(ctx, self, { isSelf: true });
            return res.json({ api_status: 200, user_data: selfData, user: selfData });
        }

        try {
            const [user, relationship] = await Promise.all([
                ctx.wo_users.findOne({ where: { user_id: targetId, active: { [Op.ne]: '0' } }, raw: true }),
                getRelationship(ctx, req.userId, targetId),
            ]);

            if (!user) return res.json({ api_status: 404, error_message: 'User not found' });

            const data = serializeUser(ctx, user, { isSelf: false, extra: { relationship } });
            return res.json({ api_status: 200, user_data: data, user: data });

        } catch (err) {
            console.error('[Profile/getUserById]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── GET /api/node/users/search ───────────────────────────────────────────────

function searchUsers(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const q      = (req.query.q || req.query.query || req.body.q || req.body.query || '').trim();
        const limit  = Math.min(parseInt(req.query.limit  || req.body.limit)  || 20, 50);
        const offset = Math.max(parseInt(req.query.offset || req.body.offset) || 0,  0);

        if (!q || q.length < 2) {
            return res.json({ api_status: 400, error_message: 'Search query must be at least 2 characters' });
        }

        try {
            const users = await ctx.wo_users.findAll({
                where: {
                    active: '1',
                    user_id: { [Op.ne]: req.userId },
                    [Op.or]: [
                        { username:   { [Op.like]: `%${q}%` } },
                        { first_name: { [Op.like]: `%${q}%` } },
                        { last_name:  { [Op.like]: `%${q}%` } },
                    ],
                },
                limit,
                offset,
                raw: true,
            });

            // Get blocks and follows in bulk for all results
            const ids = users.map(u => u.user_id);

            const [follows, blocks] = await Promise.all([
                ids.length ? ctx.wo_followers.findAll({
                    where: { follower_id: req.userId, following_id: { [Op.in]: ids } },
                    raw: true,
                }) : [],
                ids.length ? ctx.wo_blocks.findAll({
                    where: {
                        [Op.or]: [
                            { blocker: req.userId, blocked: { [Op.in]: ids } },
                            { blocker: { [Op.in]: ids }, blocked: req.userId },
                        ]
                    },
                    raw: true,
                }) : [],
            ]);

            const followMap = Object.fromEntries(follows.map(f => [f.following_id, f]));
            const blockSet  = new Set([
                ...blocks.filter(b => b.blocker === req.userId).map(b => b.blocked),
                ...blocks.filter(b => b.blocked === req.userId).map(b => b.blocker),
            ]);

            const result = users
                .filter(u => !blockSet.has(u.user_id))  // hide blocked users
                .map(u => {
                    const fw = followMap[u.user_id];
                    return serializeUserCard(ctx, u, {
                        is_following:   !!fw && fw.active === 1,
                        follow_pending: !!fw && fw.active === 0,
                    });
                });

            return res.json({ api_status: 200, users: result, count: result.length, offset: offset + result.length });

        } catch (err) {
            console.error('[Profile/searchUsers]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── GET /api/node/users/:id/followers ───────────────────────────────────────
// ─── GET /api/node/users/:id/following ───────────────────────────────────────

function getFollowers(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        const limit  = Math.min(parseInt(req.query.limit)  || 30, 100);
        const offset = Math.max(parseInt(req.query.offset) || 0,  0);

        try {
            const rows = await ctx.wo_followers.findAll({
                where:  { following_id: targetId, active: 1 },
                limit, offset, raw: true,
            });
            const ids = rows.map(r => r.follower_id);

            const [users, myFollows] = await Promise.all([
                ids.length ? ctx.wo_users.findAll({
                    where: { user_id: { [Op.in]: ids }, active: '1' }, raw: true
                }) : [],
                ids.length ? ctx.wo_followers.findAll({
                    where: { follower_id: req.userId, following_id: { [Op.in]: ids }, active: 1 }, raw: true
                }) : [],
            ]);

            const userMap   = Object.fromEntries(users.map(u => [u.user_id, u]));
            const followSet = new Set(myFollows.map(f => f.following_id));

            const result = ids
                .map(id => userMap[id])
                .filter(Boolean)
                .map(u => serializeUserCard(ctx, u, { is_following: followSet.has(u.user_id) }));

            return res.json({ api_status: 200, followers: result, count: result.length });

        } catch (err) {
            console.error('[Profile/getFollowers]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

function getFollowing(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        const limit  = Math.min(parseInt(req.query.limit)  || 30, 100);
        const offset = Math.max(parseInt(req.query.offset) || 0,  0);

        try {
            const rows = await ctx.wo_followers.findAll({
                where:  { follower_id: targetId, active: 1 },
                limit, offset, raw: true,
            });
            const ids = rows.map(r => r.following_id);

            const [users, myFollows] = await Promise.all([
                ids.length ? ctx.wo_users.findAll({
                    where: { user_id: { [Op.in]: ids }, active: '1' }, raw: true
                }) : [],
                ids.length ? ctx.wo_followers.findAll({
                    where: { follower_id: req.userId, following_id: { [Op.in]: ids }, active: 1 }, raw: true
                }) : [],
            ]);

            const userMap   = Object.fromEntries(users.map(u => [u.user_id, u]));
            const followSet = new Set(myFollows.map(f => f.following_id));

            const result = ids
                .map(id => userMap[id])
                .filter(Boolean)
                .map(u => serializeUserCard(ctx, u, { is_following: followSet.has(u.user_id) }));

            return res.json({ api_status: 200, following: result, count: result.length });

        } catch (err) {
            console.error('[Profile/getFollowing]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/users/:id/follow ─────────────────────────────────────────

function followUser(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId || targetId === req.userId) {
            return res.json({ api_status: 400, error_message: 'Invalid target user' });
        }

        try {
            const target = await ctx.wo_users.findOne({
                where: { user_id: targetId, active: '1' }, raw: true
            });
            if (!target) return res.json({ api_status: 404, error_message: 'User not found' });

            // Block check
            const block = await ctx.wo_blocks.findOne({
                where: {
                    [Op.or]: [
                        { blocker: req.userId, blocked: targetId },
                        { blocker: targetId,   blocked: req.userId },
                    ]
                }
            });
            if (block) return res.json({ api_status: 400, error_message: 'Cannot follow this user' });

            // Already following?
            const existing = await ctx.wo_followers.findOne({
                where: { follower_id: req.userId, following_id: targetId }
            });

            if (existing) {
                if (existing.active === 1) {
                    return res.json({ api_status: 200, message: 'Already following', status: 'following' });
                }
                // Re-activate pending
                await existing.update({ active: 1, time: Math.floor(Date.now() / 1000) });
                await updateFollowStats(ctx, targetId, req.userId, +1);
                return res.json({ api_status: 200, message: 'Now following', status: 'following' });
            }

            // Determine if auto-approve or requires confirmation
            const needsApproval = target.confirm_followers === '1';
            const activeVal     = needsApproval ? 0 : 1;

            await ctx.wo_followers.create({
                follower_id:  req.userId,
                following_id: targetId,
                active:       activeVal,
                time:         Math.floor(Date.now() / 1000),
            });

            if (!needsApproval) {
                await updateFollowStats(ctx, targetId, req.userId, +1);
            }

            return res.json({
                api_status: 200,
                message:    needsApproval ? 'Follow request sent' : 'Now following',
                status:     needsApproval ? 'pending' : 'following',
            });

        } catch (err) {
            console.error('[Profile/followUser]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── DELETE /api/node/users/:id/follow ───────────────────────────────────────

function unfollowUser(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        try {
            const row = await ctx.wo_followers.findOne({
                where: { follower_id: req.userId, following_id: targetId }
            });

            if (!row) return res.json({ api_status: 200, message: 'Not following', status: 'not_following' });

            const wasActive = row.active === 1;
            await row.destroy();

            if (wasActive) {
                await updateFollowStats(ctx, targetId, req.userId, -1);
            }

            return res.json({ api_status: 200, message: 'Unfollowed successfully', status: 'not_following' });

        } catch (err) {
            console.error('[Profile/unfollowUser]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── GET /api/node/users/me/blocked ──────────────────────────────────────────

function getBlocked(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const blocks = await ctx.wo_blocks.findAll({
                where: { blocker: req.userId }, raw: true
            });
            const ids = blocks.map(b => b.blocked);

            const users = ids.length
                ? await ctx.wo_users.findAll({ where: { user_id: { [Op.in]: ids } }, raw: true })
                : [];

            const userMap = Object.fromEntries(users.map(u => [u.user_id, u]));
            const result  = ids.map(id => userMap[id]).filter(Boolean).map(u => serializeUserCard(ctx, u));

            return res.json({ api_status: 200, blocked: result, count: result.length });

        } catch (err) {
            console.error('[Profile/getBlocked]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── POST /api/node/users/:id/block ──────────────────────────────────────────

function blockUser(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId || targetId === req.userId) {
            return res.json({ api_status: 400, error_message: 'Invalid target user' });
        }

        try {
            const [block, created] = await ctx.wo_blocks.findOrCreate({
                where: { blocker: req.userId, blocked: targetId },
                defaults: { blocker: req.userId, blocked: targetId },
            });

            if (!created) {
                return res.json({ api_status: 200, message: 'Already blocked' });
            }

            // Auto-unfollow in both directions when blocking
            const [fw1, fw2] = await Promise.all([
                ctx.wo_followers.findOne({ where: { follower_id: req.userId, following_id: targetId } }),
                ctx.wo_followers.findOne({ where: { follower_id: targetId, following_id: req.userId } }),
            ]);

            const tasks = [];
            if (fw1) { const wasActive = fw1.active === 1; tasks.push(fw1.destroy().then(() => wasActive && updateFollowStats(ctx, targetId, req.userId, -1))); }
            if (fw2) { const wasActive = fw2.active === 1; tasks.push(fw2.destroy().then(() => wasActive && updateFollowStats(ctx, req.userId, targetId, -1))); }
            await Promise.allSettled(tasks);

            return res.json({ api_status: 200, message: 'User blocked' });

        } catch (err) {
            console.error('[Profile/blockUser]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── DELETE /api/node/users/:id/block ────────────────────────────────────────

function unblockUser(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        const targetId = parseInt(req.params.id);
        if (!targetId) return res.json({ api_status: 400, error_message: 'Invalid user ID' });

        try {
            const deleted = await ctx.wo_blocks.destroy({
                where: { blocker: req.userId, blocked: targetId }
            });
            if (deleted === 0) return res.json({ api_status: 200, message: 'User was not blocked' });
            return res.json({ api_status: 200, message: 'User unblocked' });

        } catch (err) {
            console.error('[Profile/unblockUser]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── PUT /api/node/users/me/appearance ────────────────────────────────────────

function updateAppearance(ctx) {
    return [requireAuth(ctx), async (req, res) => {
        try {
            const updates = {};

            if (req.body.profile_accent !== undefined) {
                const accent = String(req.body.profile_accent).trim().toUpperCase();
                const normalized = accent.startsWith('#') ? accent : `#${accent}`;
                if (!VALID_ACCENT_COLORS.map(c => c.toUpperCase()).includes(normalized)) {
                    return res.json({ api_status: 400, error_message: 'Invalid accent color' });
                }
                updates.profile_accent = normalized.toLowerCase().replace(/^#/, c => '#') ;
                // keep as provided lowercase
                updates.profile_accent = String(req.body.profile_accent).trim();
            }

            if (req.body.profile_badge !== undefined) {
                const badge = String(req.body.profile_badge).trim();
                // Allow up to 8 bytes (2 emoji), or empty to clear
                if (badge.length > 8) {
                    return res.json({ api_status: 400, error_message: 'Badge too long (max 2 emoji)' });
                }
                updates.profile_badge = badge;
            }

            if (req.body.profile_header_style !== undefined) {
                const style = String(req.body.profile_header_style).trim();
                if (!VALID_HEADER_STYLES.includes(style)) {
                    return res.json({ api_status: 400, error_message: `profile_header_style must be one of: ${VALID_HEADER_STYLES.join(', ')}` });
                }
                updates.profile_header_style = style;
            }

            if (Object.keys(updates).length === 0) {
                return res.json({ api_status: 400, error_message: 'No valid appearance fields provided' });
            }

            await ctx.wo_users.update(updates, { where: { user_id: req.userId } });

            const user = await ctx.wo_users.findOne({ where: { user_id: req.userId }, raw: true });
            const data = serializeUser(ctx, user, { isSelf: true });
            return res.json({ api_status: 200, message: 'Appearance updated', user_data: data, user: data });

        } catch (err) {
            console.error('[Profile/updateAppearance]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    }];
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerProfileRoutes(app, ctx) {
    // Own profile — MUST be before /:id to avoid "me" being matched as an ID
    app.get   ('/api/node/users/me',                  getMe(ctx));
    app.put   ('/api/node/users/me',                  updateMe(ctx));
    app.put   ('/api/node/users/me/privacy',           updatePrivacy(ctx));
    app.put   ('/api/node/users/me/password',          changePassword(ctx));
    app.put   ('/api/node/users/me/notifications',     updateNotifications(ctx));
    app.put   ('/api/node/users/me/appearance',        updateAppearance(ctx));
    app.get   ('/api/node/users/me/blocked',           getBlocked(ctx));

    // Search
    app.get   ('/api/node/users/search',              searchUsers(ctx));
    app.post  ('/api/node/users/search',              searchUsers(ctx));

    // Other users
    app.get   ('/api/node/users/:id',                 getUserById(ctx));
    app.get   ('/api/node/users/:id/followers',       getFollowers(ctx));
    app.get   ('/api/node/users/:id/following',       getFollowing(ctx));
    app.post  ('/api/node/users/:id/follow',          followUser(ctx));
    app.delete('/api/node/users/:id/follow',          unfollowUser(ctx));
    app.post  ('/api/node/users/:id/block',           blockUser(ctx));
    app.delete('/api/node/users/:id/block',           unblockUser(ctx));

    console.log('[Profile] Routes registered:');
    console.log('  GET    /api/node/users/me');
    console.log('  PUT    /api/node/users/me');
    console.log('  PUT    /api/node/users/me/privacy');
    console.log('  PUT    /api/node/users/me/password');
    console.log('  PUT    /api/node/users/me/notifications');
    console.log('  PUT    /api/node/users/me/appearance');
    console.log('  GET    /api/node/users/me/blocked');
    console.log('  GET    /api/node/users/search');
    console.log('  GET    /api/node/users/:id');
    console.log('  GET    /api/node/users/:id/followers');
    console.log('  GET    /api/node/users/:id/following');
    console.log('  POST   /api/node/users/:id/follow');
    console.log('  DELETE /api/node/users/:id/follow');
    console.log('  POST   /api/node/users/:id/block');
    console.log('  DELETE /api/node/users/:id/block');
}

module.exports = { registerProfileRoutes };
