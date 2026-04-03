/**
 * Stories — REST API Routes
 *
 * Handles story creation with file upload and story retrieval.
 * Replaces the PHP create-story.php and get-stories.php endpoints.
 *
 * Endpoints:
 *   POST /api/node/stories/create   – create story with multipart file upload
 *   POST /api/node/stories/get      – get active stories list
 */

'use strict';

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const multer = require('multer');
const { Op } = require('sequelize');

// ─── constants ──────────────────────────────────────────────────────────────

const ALLOWED_IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.gif'];
const ALLOWED_VIDEO_EXTS = ['.mp4', '.mov', '.webm'];
const ALLOWED_AUDIO_EXTS = ['.mp3', '.m4a', '.aac', '.ogg', '.wav'];
const ALLOWED_EXTS = [...ALLOWED_IMAGE_EXTS, ...ALLOWED_VIDEO_EXTS, ...ALLOWED_AUDIO_EXTS];
const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
const STORY_TTL = 86400; // 24 hours in seconds

// ─── file upload setup ──────────────────────────────────────────────────────

// Document root is one level up from the nodejs directory
const SITE_ROOT = path.resolve(__dirname, '..', '..', '..');

function getUploadDir(fileType) {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const subdir = (fileType === 'video') ? 'videos' : 'photos';
    return `upload/${subdir}/${year}/${month}`;
}

function generateFilename(originalName, fileType) {
    const ext = path.extname(originalName).toLowerCase() || '.jpg';
    const day = String(new Date().getDate()).padStart(2, '0');
    const randomKey = crypto.randomBytes(10).toString('hex'); // 20 chars
    const hash = crypto.createHash('md5').update(String(Date.now())).digest('hex');
    return `${randomKey}_${day}_${hash}_${fileType}${ext}`;
}

function ensureDir(dirPath) {
    if (!fs.existsSync(dirPath)) {
        fs.mkdirSync(dirPath, { recursive: true, mode: 0o777 });
    }
}

// Configure multer for memory storage (we'll write files ourselves)
const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: MAX_FILE_SIZE },
    fileFilter: (req, file, cb) => {
        const ext = path.extname(file.originalname).toLowerCase();
        if (ALLOWED_EXTS.includes(ext)) {
            cb(null, true);
        } else {
            cb(new Error(`File type not allowed: ${ext}`));
        }
    }
});

// ─── auth middleware ────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
              || req.query.access_token
              || req.body?.access_token;

    if (!token)
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session)
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Stories/auth]', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── helpers ────────────────────────────────────────────────────────────────

async function getUserBasicData(ctx, userId) {
    try {
        const u = await ctx.wo_users.findOne({
            attributes: ['user_id', 'username', 'first_name', 'last_name', 'avatar',
                         'last_avatar_mod', 'is_pro', 'verified'],
            where: { user_id: userId },
            raw: true,
        });
        if (!u) return { user_id: userId };

        const siteUrl = ctx.globalconfig.site_url || '';
        let avatarUrl = u.avatar || '';
        if (avatarUrl && !avatarUrl.startsWith('http')) {
            avatarUrl = siteUrl + '/' + avatarUrl;
        }
        if (u.last_avatar_mod) {
            avatarUrl += '?cache=' + u.last_avatar_mod;
        }

        return {
            user_id:    u.user_id,
            username:   u.username || '',
            first_name: u.first_name || u.username || '',
            last_name:  u.last_name || '',
            avatar:     avatarUrl,
            avatar_org: u.avatar || '',
            is_pro:     u.is_pro || 0,
            verified:   u.verified || 0,
        };
    } catch (e) {
        console.error('[Stories/getUserBasicData]', e.message);
        return { user_id: userId };
    }
}

async function buildStoryResponse(ctx, story, loggedUserId) {
    const siteUrl = ctx.globalconfig.site_url || '';
    const storyId = story.id;
    const storyUserId = story.user_id;

    // Get user data
    const userData = await getUserBasicData(ctx, storyUserId);

    // Get media items
    const mediaRows = await ctx.wo_userstorymedia.findAll({
        where: { story_id: storyId },
        order: [['id', 'ASC']],
        raw: true,
    });

    const images = [];
    const videos = [];
    const mediaItems = [];
    let musicUrl = null;
    for (const m of mediaRows) {
        if (m.type === 'music') {
            const siteUrl = ctx.globalconfig?.site_url || '';
            musicUrl = m.filename.startsWith('http') ? m.filename : `${siteUrl.replace(/\/$/, '')}/${m.filename}`;
            continue;
        }
        const item = {
            id:       m.id,
            story_id: m.story_id,
            type:     m.type || 'image',
            filename: m.filename || '',
            expire:   m.expire ? parseInt(m.expire) : 0,
            duration: m.duration || 0,
        };
        mediaItems.push(item);
        if (item.type === 'video') {
            videos.push(item);
        } else {
            images.push(item);
        }
    }

    // Check if viewed
    const viewedCount = await ctx.wo_story_seen.count({
        where: { story_id: storyId, user_id: loggedUserId }
    });

    // Total views
    const totalViews = await ctx.wo_story_seen.count({
        where: { story_id: storyId }
    });

    // Reactions
    const reactionResult = {
        like: 0, love: 0, haha: 0,
        wow: 0, sad: 0, angry: 0,
        is_reacted: false, type: null,
    };
    try {
        const reactions = await ctx.wo_storyreactions.findAll({
            attributes: [
                'reaction',
                [ctx.wo_storyreactions.sequelize.fn('COUNT', '*'), 'cnt']
            ],
            where: { story_id: storyId },
            group: ['reaction'],
            raw: true,
        });
        for (const r of reactions) {
            const key = (r.reaction || '').toLowerCase();
            if (key in reactionResult) {
                reactionResult[key] = parseInt(r.cnt) || 0;
            }
        }
        const myReaction = await ctx.wo_storyreactions.findOne({
            where: { story_id: storyId, user_id: loggedUserId },
            raw: true,
        });
        if (myReaction) {
            reactionResult.is_reacted = true;
            reactionResult.type = myReaction.reaction;
        }
    } catch (e) {
        // StoryReactions table might not exist — ignore
    }

    const postedTs = story.posted ? parseInt(story.posted) : Math.floor(Date.now() / 1000);
    const expireTs = story.expire ? parseInt(story.expire) : (postedTs + STORY_TTL);

    return {
        id:            storyId,
        user_id:       storyUserId,
        page_id:       null,
        title:         story.title || '',
        description:   story.description || '',
        posted:        postedTs,
        expire:        expireTs,
        thumbnail:     story.thumbnail || '',
        user_data:     userData,
        images:        images,
        videos:        videos,
        mediaItems:    mediaItems,
        is_owner:      (storyUserId === loggedUserId),
        is_viewed:     viewedCount > 0 ? 1 : 0,
        view_count:    totalViews,
        comment_count: story.comment_count || 0,
        reaction:      reactionResult,
        music_url:     musicUrl,
    };
}

// ─── POST /api/node/stories/create ─────────────────────────────────────────

function createStory(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const fileType = (req.body.file_type || '').trim();
            const title    = (req.body.story_title || '').trim();
            const desc     = (req.body.story_description || '').trim();
            const duration = parseInt(req.body.video_duration) || 0;

            // Validate file
            if (!req.files || !req.files.file || req.files.file.length === 0) {
                return res.status(400).json({
                    api_status: 400, error_code: 3,
                    error_message: 'file is required'
                });
            }

            // Validate file_type
            if (!fileType || !['image', 'video'].includes(fileType)) {
                return res.status(400).json({
                    api_status: 400, error_code: 4,
                    error_message: 'file_type must be "image" or "video"'
                });
            }

            // Validate title length
            if (title && title.length > 100) {
                return res.status(400).json({
                    api_status: 400, error_code: 5,
                    error_message: 'story_title must not exceed 100 characters'
                });
            }

            // Validate description length
            if (desc && desc.length > 300) {
                return res.status(400).json({
                    api_status: 400, error_code: 6,
                    error_message: 'story_description must not exceed 300 characters'
                });
            }

            const now = Math.floor(Date.now() / 1000);
            const expireTime = now + STORY_TTL;

            // 1. Create story record in DB
            const storyRow = await ctx.wo_userstory.create({
                user_id:     userId,
                title:       title || '',
                description: desc || '',
                posted:      String(now),
                expire:      String(expireTime),
                thumbnail:   '',
            });

            if (!storyRow || !storyRow.id) {
                return res.status(500).json({
                    api_status: 400, error_code: 7,
                    error_message: 'Failed to create story record'
                });
            }

            const storyId = storyRow.id;

            // 2. Save uploaded file to disk
            const file = req.files.file[0];
            const uploadDir = getUploadDir(fileType);
            const filename = generateFilename(file.originalname, fileType);
            const relativePath = uploadDir + '/' + filename;
            const absoluteDir = path.join(SITE_ROOT, uploadDir);
            const absolutePath = path.join(SITE_ROOT, relativePath);

            ensureDir(absoluteDir);
            await fs.promises.writeFile(absolutePath, file.buffer);
            console.log(`[Stories/create] Saved file: ${relativePath} (${file.size} bytes)`);

            // 3. Insert media record
            await ctx.wo_userstorymedia.create({
                story_id: storyId,
                type:     fileType,
                filename: relativePath,
                expire:   String(expireTime),
                duration: (fileType === 'video') ? duration : 0,
            });

            // 4. Set thumbnail
            let thumbnail = '';
            const ext = path.extname(file.originalname).toLowerCase();
            if (ALLOWED_IMAGE_EXTS.includes(ext)) {
                thumbnail = relativePath;
            }

            // Handle cover file for video
            if (!thumbnail && req.files.cover && req.files.cover.length > 0) {
                const coverFile = req.files.cover[0];
                const coverDir = getUploadDir('image');
                const coverName = generateFilename(coverFile.originalname, 'image');
                const coverRelative = coverDir + '/' + coverName;
                const coverAbsDir = path.join(SITE_ROOT, coverDir);

                ensureDir(coverAbsDir);
                await fs.promises.writeFile(path.join(SITE_ROOT, coverRelative), coverFile.buffer);
                thumbnail = coverRelative;
                console.log(`[Stories/create] Saved cover: ${coverRelative}`);
            }

            if (thumbnail) {
                await ctx.wo_userstory.update(
                    { thumbnail: thumbnail },
                    { where: { id: storyId } }
                );
                storyRow.thumbnail = thumbnail;
            }

            // Save music file if provided
            const musicFile = req.files?.music?.[0];
            if (musicFile) {
                const musicDir = `upload/audios/${new Date().getFullYear()}/${String(new Date().getMonth() + 1).padStart(2, '0')}`;
                const musicAbsDir = path.join(SITE_ROOT, musicDir);
                ensureDir(musicAbsDir);
                const musicFilename = generateFilename(musicFile.originalname, 'music');
                const musicRelPath = `${musicDir}/${musicFilename}`;
                await fs.promises.writeFile(path.join(musicAbsDir, musicFilename), musicFile.buffer);
                await ctx.wo_userstorymedia.create({
                    story_id: storyId,
                    type: 'music',
                    filename: musicRelPath,
                    expire: '',
                    duration: 0,
                });
            }

            // 5. Build response
            const storyData = await buildStoryResponse(ctx, {
                id:            storyId,
                user_id:       userId,
                title:         title || '',
                description:   desc || '',
                posted:        String(now),
                expire:        String(expireTime),
                thumbnail:     thumbnail,
                comment_count: 0,
            }, userId);

            // 6. Broadcast via Socket.IO
            if (io) {
                // Notify subscribers (friends who follow this user's stories)
                const roomName = `stories_${userId}`;
                io.to(roomName).emit('story:created', {
                    userId,
                    story: storyData,
                });
                // Also notify the creator's own socket so their feed refreshes immediately
                io.to(`user_${userId}`).emit('story:self_created', {
                    story: storyData,
                });
            }

            console.log(`[Stories/create] User ${userId} created story ${storyId}`);

            res.json({
                api_status: 200,
                message:    'Story created successfully',
                story_id:   storyId,
                story:      storyData,
            });

        } catch (err) {
            console.error('[Stories/create] Error:', err.message, err.stack);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to create story: ' + err.message
            });
        }
    };
}

// ─── POST /api/node/stories/get ────────────────────────────────────────────

function getStories(ctx) {
    return async (req, res) => {
        try {
            const userId = req.userId;
            let limit = parseInt(req.body.limit) || 35;
            if (limit < 1) limit = 1;
            if (limit > 50) limit = 50;

            const now = Math.floor(Date.now() / 1000);
            const expireThreshold = now - STORY_TTL;

            // Get muted user IDs
            let mutedIds = [];
            try {
                const muted = await ctx.wo_mute_story.findAll({
                    attributes: ['story_user_id'],
                    where: { user_id: userId },
                    raw: true,
                });
                mutedIds = muted.map(m => m.story_user_id);
            } catch (e) {
                // Mute table might not exist
            }

            // Build where clause
            const whereClause = {
                [Op.and]: [
                    {
                        [Op.or]: [
                            { expire: null },
                            { expire: '' },
                            ctx.wo_userstory.sequelize.literal(`CAST(\`expire\` AS UNSIGNED) > ${now}`)
                        ]
                    },
                    ctx.wo_userstory.sequelize.literal(`CAST(\`posted\` AS UNSIGNED) > ${expireThreshold}`)
                ]
            };

            if (mutedIds.length > 0) {
                whereClause.user_id = { [Op.notIn]: mutedIds };
            }

            const stories = await ctx.wo_userstory.findAll({
                where: whereClause,
                order: [['id', 'DESC']],
                limit: limit,
                raw: true,
            });

            // Build response for each story
            // Cache user data per user_id
            const userDataCache = {};
            const result = [];

            for (const story of stories) {
                const storyData = await buildStoryResponse(ctx, story, userId);
                result.push(storyData);
            }

            console.log(`[Stories/get] User ${userId} fetched ${result.length} stories`);

            res.json({
                api_status: 200,
                stories:    result,
            });

        } catch (err) {
            console.error('[Stories/get] Error:', err.message, err.stack);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to fetch stories: ' + err.message
            });
        }
    };
}

// ─── POST /api/node/stories/get-user-stories ────────────────────────────────

function getUserStories(ctx) {
    return async (req, res) => {
        try {
            const loggedUserId = req.userId;
            const targetUserId = parseInt(req.body.user_id);
            let limit = parseInt(req.body.limit) || 35;
            if (limit < 1) limit = 1;
            if (limit > 50) limit = 50;

            if (!targetUserId || isNaN(targetUserId)) {
                return res.status(400).json({
                    api_status: 400, error_code: 3,
                    error_message: 'user_id is required'
                });
            }

            const now = Math.floor(Date.now() / 1000);
            const expireThreshold = now - STORY_TTL;

            const whereClause = {
                user_id: targetUserId,
                [Op.and]: [
                    {
                        [Op.or]: [
                            { expire: null },
                            { expire: '' },
                            ctx.wo_userstory.sequelize.literal(`CAST(\`expire\` AS UNSIGNED) > ${now}`)
                        ]
                    },
                    ctx.wo_userstory.sequelize.literal(`CAST(\`posted\` AS UNSIGNED) > ${expireThreshold}`)
                ]
            };

            const stories = await ctx.wo_userstory.findAll({
                where: whereClause,
                order: [['id', 'DESC']],
                limit: limit,
                raw: true,
            });

            const result = [];
            for (const story of stories) {
                const storyData = await buildStoryResponse(ctx, story, loggedUserId);
                result.push(storyData);
            }

            console.log(`[Stories/get-user] User ${loggedUserId} fetched ${result.length} stories for user ${targetUserId}`);

            res.json({
                api_status: 200,
                stories:    result,
            });

        } catch (err) {
            console.error('[Stories/get-user] Error:', err.message, err.stack);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to fetch user stories: ' + err.message
            });
        }
    };
}

// ─── POST /api/node/stories/get-archive ─────────────────────────────────────
// Own stories archive: all stories posted within the last 365 days (no active TTL filter).
// Accessible only to the story owner (logged-in user's own stories).

function getMyStoriesArchive(ctx) {
    return async (req, res) => {
        try {
            const loggedUserId = req.userId;
            let limit = parseInt(req.body.limit) || 50;
            let offset = parseInt(req.body.offset) || 0;
            if (limit < 1) limit = 1;
            if (limit > 100) limit = 100;

            const ARCHIVE_TTL = 365 * 86400; // 365 days in seconds
            const now = Math.floor(Date.now() / 1000);
            const archiveThreshold = now - ARCHIVE_TTL;

            const stories = await ctx.wo_userstory.findAll({
                where: {
                    user_id: loggedUserId,
                    [Op.and]: [
                        ctx.wo_userstory.sequelize.literal(`CAST(\`posted\` AS UNSIGNED) > ${archiveThreshold}`)
                    ]
                },
                order: [['id', 'DESC']],
                limit,
                offset,
                raw: true,
            });

            const result = [];
            for (const story of stories) {
                const storyData = await buildStoryResponse(ctx, story, loggedUserId);
                result.push(storyData);
            }

            console.log(`[Stories/get-archive] User ${loggedUserId} fetched ${result.length} archived stories`);

            res.json({ api_status: 200, stories: result });

        } catch (err) {
            console.error('[Stories/get-archive] Error:', err.message, err.stack);
            res.status(500).json({
                api_status: 500,
                error_message: 'Failed to fetch story archive: ' + err.message
            });
        }
    };
}

// ─── POST /api/node/stories/mark-viewed ─────────────────────────────────────

function markStoryViewed(ctx) {
    return async (req, res) => {
        try {
            const loggedUserId = req.userId;
            const storyId = parseInt(req.body.story_id);

            if (!storyId) {
                return res.status(400).json({ api_status: 400, error_message: 'story_id is required' });
            }

            // Don't record own views
            const story = await ctx.wo_userstory.findOne({
                where: { id: storyId }, attributes: ['user_id'], raw: true,
            });
            if (!story) return res.json({ api_status: 200, message: 'not_found' });
            if (story.user_id === loggedUserId) return res.json({ api_status: 200, message: 'own_story' });

            // Prevent duplicate views
            const existing = await ctx.wo_story_seen.findOne({
                where: { story_id: storyId, user_id: loggedUserId },
            });

            if (!existing) {
                await ctx.wo_story_seen.create({
                    story_id: storyId,
                    user_id: loggedUserId,
                    time: String(Math.floor(Date.now() / 1000)),
                });
            }

            res.json({ api_status: 200, message: 'viewed' });
        } catch (err) {
            console.error('[Stories/mark-viewed]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/react ───────────────────────────────────────────

function reactToStory(ctx) {
    return async (req, res) => {
        try {
            const loggedUserId = req.userId;
            const storyId = parseInt(req.body.story_id);
            const reaction = (req.body.reaction || '').toLowerCase().trim();

            const validReactions = ['like', 'love', 'haha', 'wow', 'sad', 'angry'];
            if (!storyId || !validReactions.includes(reaction)) {
                return res.status(400).json({
                    api_status: 400,
                    error_message: 'story_id and valid reaction required',
                });
            }

            const existing = await ctx.wo_storyreactions.findOne({
                where: { story_id: storyId, user_id: loggedUserId },
            });

            let action;
            if (existing) {
                if (existing.reaction === reaction) {
                    await existing.destroy();
                    action = 'removed';
                } else {
                    existing.reaction = reaction;
                    existing.time = Math.floor(Date.now() / 1000);
                    await existing.save();
                    action = 'updated';
                }
            } else {
                await ctx.wo_storyreactions.create({
                    story_id: storyId,
                    user_id: loggedUserId,
                    reaction: reaction,
                    time: Math.floor(Date.now() / 1000),
                });
                action = 'added';
            }

            console.log(`[Stories/react] User ${loggedUserId} ${action} ${reaction} on story ${storyId}`);
            res.json({ api_status: 200, action, reaction: action === 'removed' ? null : reaction });
        } catch (err) {
            console.error('[Stories/react]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/get-comments ────────────────────────────────────

function getStoryComments(ctx) {
    return async (req, res) => {
        try {
            const storyId = parseInt(req.body.story_id);
            let limit = parseInt(req.body.limit) || 20;
            const offset = parseInt(req.body.offset) || 0;

            if (!storyId) {
                return res.status(400).json({ api_status: 400, error_message: 'story_id is required' });
            }
            if (limit > 50) limit = 50;

            const whereClause = { story_id: storyId };
            if (offset > 0) {
                whereClause.id = { [Op.lt]: offset };
            }

            const comments = await ctx.wo_storycomments.findAll({
                where: whereClause,
                order: [['time', 'DESC']],
                limit,
                raw: true,
            });

            const result = [];
            for (const c of comments) {
                const userData = await getUserBasicData(ctx, c.user_id);
                result.push({
                    id: c.id,
                    story_id: c.story_id,
                    user_id: c.user_id,
                    text: c.text || '',
                    time: c.time ? parseInt(c.time) : 0,
                    user_data: userData,
                    offset_id: c.id,
                });
            }

            const total = await ctx.wo_storycomments.count({ where: { story_id: storyId } });

            res.json({ api_status: 200, comments: result, total });
        } catch (err) {
            console.error('[Stories/get-comments]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/create-comment ──────────────────────────────────

function createStoryComment(ctx) {
    return async (req, res) => {
        try {
            const loggedUserId = req.userId;
            const storyId = parseInt(req.body.story_id);
            const text = (req.body.text || '').trim();

            if (!storyId || !text) {
                return res.status(400).json({ api_status: 400, error_message: 'story_id and text are required' });
            }

            const story = await ctx.wo_userstory.findOne({ where: { id: storyId }, raw: true });
            if (!story) {
                return res.status(404).json({ api_status: 404, error_message: 'Story not found' });
            }

            const now = Math.floor(Date.now() / 1000);
            const comment = await ctx.wo_storycomments.create({
                story_id: storyId,
                user_id: loggedUserId,
                text: text,
                time: now,
            });

            // Increment comment_count
            await ctx.wo_userstory.increment('comment_count', { where: { id: storyId } });

            const userData = await getUserBasicData(ctx, loggedUserId);

            console.log(`[Stories/comment] User ${loggedUserId} commented on story ${storyId}`);
            res.json({
                api_status: 200,
                comment: {
                    id: comment.id,
                    story_id: storyId,
                    user_id: loggedUserId,
                    text: text,
                    time: now,
                    user_data: userData,
                    offset_id: comment.id,
                },
            });
        } catch (err) {
            console.error('[Stories/create-comment]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/mark-viewed-anonymous ────────────────────────────
// Анонімний перегляд: записує перегляд без прив'язки до user_id (зберігає IP-хеш)

function markStoryViewedAnonymous(ctx) {
    return async (req, res) => {
        try {
            const storyId = parseInt(req.body.story_id);
            if (!storyId) {
                return res.status(400).json({ api_status: 400, error_message: 'story_id is required' });
            }

            const story = await ctx.wo_userstory.findOne({
                where: { id: storyId }, attributes: ['id', 'user_id'], raw: true,
            });
            if (!story) return res.json({ api_status: 200, message: 'not_found' });

            // Замість user_id використовуємо анонімний ідентифікатор на основі IP+UA
            const ip  = req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
            const ua  = req.headers['user-agent'] || '';
            const anonId = crypto.createHash('sha256').update(ip + ua + storyId).digest('hex');

            // Зберігаємо в wo_story_seen з псевдо user_id = 0 (анонімний)
            // і поміщаємо хеш в поле 'anonymous_hash' якщо він є, або просто рахуємо
            const existing = await ctx.wo_story_seen.findOne({
                where: { story_id: storyId, user_id: 0, anonymous_hash: anonId },
            }).catch(() => null); // поле може не існувати в старій схемі

            if (!existing) {
                await ctx.wo_story_seen.create({
                    story_id: storyId,
                    user_id: 0,
                    anonymous_hash: anonId,
                    time: String(Math.floor(Date.now() / 1000)),
                }).catch(async () => {
                    // Fallback: якщо anonymous_hash не існує в схемі — просто create без нього
                    await ctx.wo_story_seen.create({
                        story_id: storyId,
                        user_id: 0,
                        time: String(Math.floor(Date.now() / 1000)),
                    }).catch(() => {});
                });
            }

            res.json({ api_status: 200, message: 'anonymous_view_recorded' });
        } catch (err) {
            console.error('[Stories/mark-viewed-anonymous]', err.message);
            res.status(500).json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/get-by-id ───────────────────────────────────────

function getStoryById(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const storyId = parseInt(req.body.id || req.body.story_id);
        if (!storyId) {
            return res.json({ api_status: 400, error_message: 'id is required' });
        }
        try {
            const story = await ctx.wo_userstory.findOne({ where: { id: storyId }, raw: true });
            if (!story) {
                return res.json({ api_status: 404, error_message: 'Story not found' });
            }
            const storyData = await buildStoryResponse(ctx, story, loggedUserId);
            res.json({ api_status: 200, story: storyData });
        } catch (err) {
            console.error('[Stories/get-by-id]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/delete ──────────────────────────────────────────

function deleteStory(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const storyId = parseInt(req.body.story_id);
        if (!storyId) {
            return res.json({ api_status: 400, error_message: 'story_id is required' });
        }
        try {
            const story = await ctx.wo_userstory.findOne({ where: { id: storyId }, raw: true });
            if (!story) {
                return res.json({ api_status: 404, error_message: 'Story not found' });
            }
            if (story.user_id !== loggedUserId) {
                return res.json({ api_status: 403, error_message: 'Not authorized to delete this story' });
            }
            await ctx.wo_userstorymedia.destroy({ where: { story_id: storyId } });
            await ctx.wo_story_seen.destroy({ where: { story_id: storyId } });
            try { await ctx.wo_storyreactions.destroy({ where: { story_id: storyId } }); } catch (e) {}
            try { await ctx.wo_storycomments.destroy({ where: { story_id: storyId } }); } catch (e) {}
            await ctx.wo_userstory.destroy({ where: { id: storyId } });
            console.log(`[Stories/delete] User ${loggedUserId} deleted story ${storyId}`);
            res.json({ api_status: 200, message: 'Story deleted successfully' });
        } catch (err) {
            console.error('[Stories/delete]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/get-views ───────────────────────────────────────

function getStoryViews(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const storyId = parseInt(req.body.story_id);
        let limit = parseInt(req.body.limit) || 20;
        const offset = parseInt(req.body.offset) || 0;
        if (!storyId) {
            return res.json({ api_status: 400, error_message: 'story_id is required' });
        }
        if (limit > 50) limit = 50;
        try {
            const story = await ctx.wo_userstory.findOne({
                where: { id: storyId }, attributes: ['user_id'], raw: true,
            });
            if (!story) return res.json({ api_status: 404, error_message: 'Story not found' });
            if (story.user_id !== loggedUserId) {
                return res.json({ api_status: 403, error_message: 'Only story owner can view viewers' });
            }

            const whereClause = { story_id: storyId, user_id: { [Op.gt]: 0 } };
            if (offset > 0) whereClause.id = { [Op.lt]: offset };

            const views = await ctx.wo_story_seen.findAll({
                where: whereClause,
                order: [['id', 'DESC']],
                limit,
                raw: true,
            });

            const users = [];
            for (const v of views) {
                const userData = await getUserBasicData(ctx, v.user_id);
                users.push({ ...userData, view_time: v.time ? parseInt(v.time) : 0, offset_id: v.id });
            }

            const total = await ctx.wo_story_seen.count({
                where: { story_id: storyId, user_id: { [Op.gt]: 0 } },
            });

            res.json({ api_status: 200, users, total });
        } catch (err) {
            console.error('[Stories/get-views]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/mute ────────────────────────────────────────────

function muteStory(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const targetUserId = parseInt(req.body.user_id);
        if (!targetUserId) {
            return res.json({ api_status: 400, error_message: 'user_id is required' });
        }
        try {
            const existing = await ctx.wo_mute_story.findOne({
                where: { user_id: loggedUserId, story_user_id: targetUserId },
            });
            let action;
            if (existing) {
                await existing.destroy();
                action = 'unmuted';
            } else {
                await ctx.wo_mute_story.create({ user_id: loggedUserId, story_user_id: targetUserId });
                action = 'muted';
            }
            console.log(`[Stories/mute] User ${loggedUserId} ${action} stories of user ${targetUserId}`);
            res.json({ api_status: 200, action });
        } catch (err) {
            console.error('[Stories/mute]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/delete-comment ──────────────────────────────────

function deleteStoryComment(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const commentId = parseInt(req.body.comment_id);
        if (!commentId) {
            return res.json({ api_status: 400, error_message: 'comment_id is required' });
        }
        try {
            const comment = await ctx.wo_storycomments.findOne({ where: { id: commentId }, raw: true });
            if (!comment) {
                return res.json({ api_status: 404, error_message: 'Comment not found' });
            }
            const story = await ctx.wo_userstory.findOne({
                where: { id: comment.story_id }, attributes: ['user_id'], raw: true,
            });
            const isCommentAuthor = comment.user_id === loggedUserId;
            const isStoryOwner = story && story.user_id === loggedUserId;
            if (!isCommentAuthor && !isStoryOwner) {
                return res.json({ api_status: 403, error_message: 'Not authorized to delete this comment' });
            }
            await ctx.wo_storycomments.destroy({ where: { id: commentId } });
            try {
                await ctx.wo_userstory.decrement('comment_count', { where: { id: comment.story_id } });
            } catch (e) {}
            console.log(`[Stories/delete-comment] Comment ${commentId} deleted by user ${loggedUserId}`);
            res.json({ api_status: 200, message: 'Comment deleted' });
        } catch (err) {
            console.error('[Stories/delete-comment]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/create-channel ──────────────────────────────────

function createChannelStory(ctx, io) {
    return async (req, res) => {
        try {
            const userId   = req.userId;
            const channelId = parseInt(req.body.channel_id);
            const fileType = (req.body.file_type || '').trim();
            const title    = (req.body.story_title || '').trim();
            const desc     = (req.body.story_description || '').trim();

            if (!channelId || channelId < 1) {
                return res.json({ api_status: 400, error_message: 'channel_id is required' });
            }
            if (!req.files || !req.files.file || req.files.file.length === 0) {
                return res.json({ api_status: 400, error_code: 3, error_message: 'file is required' });
            }
            if (!fileType || !['image', 'video'].includes(fileType)) {
                return res.json({ api_status: 400, error_code: 4, error_message: 'file_type must be "image" or "video"' });
            }

            // Check admin permission for channel
            const { QueryTypes } = require('sequelize');
            const [channelAdmin] = await ctx.sequelize.query(
                `SELECT page_id FROM Wo_Pages WHERE page_id = ? AND user_id = ?
                 UNION
                 SELECT group_id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ? AND role IN ('owner','admin')`,
                { replacements: [channelId, userId, channelId, userId], type: QueryTypes.SELECT }
            ).catch(() => [null]);
            if (!channelAdmin) {
                return res.json({ api_status: 403, error_message: 'Only channel admins can create channel stories' });
            }

            const now = Math.floor(Date.now() / 1000);
            const expireTime = now + STORY_TTL;

            const storyRow = await ctx.wo_userstory.create({
                user_id:     userId,
                page_id:     channelId,
                title:       title || '',
                description: desc || '',
                posted:      String(now),
                expire:      String(expireTime),
                thumbnail:   '',
            });

            const storyId = storyRow.id;
            const file = req.files.file[0];
            const uploadDir = getUploadDir(fileType);
            const filename = generateFilename(file.originalname, fileType);
            const relativePath = uploadDir + '/' + filename;
            const absoluteDir = path.join(SITE_ROOT, uploadDir);

            ensureDir(absoluteDir);
            await fs.promises.writeFile(path.join(SITE_ROOT, relativePath), file.buffer);

            await ctx.wo_userstorymedia.create({
                story_id: storyId,
                type:     fileType,
                filename: relativePath,
                expire:   String(expireTime),
                duration: 0,
            });

            let thumbnail = '';
            const ext = path.extname(file.originalname).toLowerCase();
            if (ALLOWED_IMAGE_EXTS.includes(ext)) thumbnail = relativePath;
            if (thumbnail) {
                await ctx.wo_userstory.update({ thumbnail }, { where: { id: storyId } });
            }

            const storyData = await buildStoryResponse(ctx, {
                id: storyId, user_id: userId, page_id: channelId,
                title, description: desc, posted: String(now), expire: String(expireTime),
                thumbnail, comment_count: 0,
            }, userId);

            console.log(`[Stories/create-channel] User ${userId} created channel story ${storyId} for channel ${channelId}`);
            res.json({ api_status: 200, message: 'Channel story created successfully', story_id: storyId, story: storyData });
        } catch (err) {
            console.error('[Stories/create-channel]', err.message, err.stack);
            res.json({ api_status: 500, error_message: 'Failed to create channel story: ' + err.message });
        }
    };
}

// ─── POST /api/node/stories/get-channel-subscribed ──────────────────────────

function getSubscribedChannelStories(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        let limit = parseInt(req.body.limit) || 30;
        if (limit > 50) limit = 50;
        try {
            const { QueryTypes } = require('sequelize');
            const now = Math.floor(Date.now() / 1000);
            const expireThreshold = now - STORY_TTL;

            // Get channel IDs the user is a fan/follower of
            const fanRows = await ctx.sequelize.query(
                'SELECT page_id FROM Wo_Page_Fans WHERE user_id = ?',
                { replacements: [loggedUserId], type: QueryTypes.SELECT }
            ).catch(() => []);

            const channelIds = fanRows.map(r => r.page_id);
            if (channelIds.length === 0) {
                return res.json({ api_status: 200, stories: [] });
            }

            const channelIdList = channelIds.join(',');
            const storyRows = await ctx.sequelize.query(
                `SELECT * FROM Wo_UserStory
                 WHERE page_id IN (${channelIdList})
                   AND (expire IS NULL OR expire = '' OR CAST(expire AS UNSIGNED) > ?)
                   AND CAST(posted AS UNSIGNED) > ?
                 ORDER BY id DESC LIMIT ?`,
                { replacements: [now, expireThreshold, limit], type: QueryTypes.SELECT }
            ).catch(() => []);

            const result = [];
            for (const story of storyRows) {
                const storyData = await buildStoryResponse(ctx, story, loggedUserId);
                result.push(storyData);
            }

            console.log(`[Stories/get-channel-subscribed] User ${loggedUserId} fetched ${result.length} channel stories`);
            res.json({ api_status: 200, stories: result });
        } catch (err) {
            console.error('[Stories/get-channel-subscribed]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── POST /api/node/stories/delete-channel ──────────────────────────────────

function deleteChannelStory(ctx) {
    return async (req, res) => {
        const loggedUserId = req.userId;
        const storyId = parseInt(req.body.story_id);
        if (!storyId) {
            return res.json({ api_status: 400, error_message: 'story_id is required' });
        }
        try {
            const story = await ctx.wo_userstory.findOne({ where: { id: storyId }, raw: true });
            if (!story) return res.json({ api_status: 404, error_message: 'Story not found' });
            if (!story.page_id) return res.json({ api_status: 400, error_message: 'Not a channel story' });

            const channelId = story.page_id;
            const { QueryTypes } = require('sequelize');
            const [isAdmin] = await ctx.sequelize.query(
                `SELECT page_id FROM Wo_Pages WHERE page_id = ? AND user_id = ?`,
                { replacements: [channelId, loggedUserId], type: QueryTypes.SELECT }
            ).catch(() => [null]);

            if (!isAdmin && story.user_id !== loggedUserId) {
                return res.json({ api_status: 403, error_message: 'Not authorized to delete this channel story' });
            }

            await ctx.wo_userstorymedia.destroy({ where: { story_id: storyId } });
            await ctx.wo_story_seen.destroy({ where: { story_id: storyId } });
            try { await ctx.wo_storyreactions.destroy({ where: { story_id: storyId } }); } catch (e) {}
            try { await ctx.wo_storycomments.destroy({ where: { story_id: storyId } }); } catch (e) {}
            await ctx.wo_userstory.destroy({ where: { id: storyId } });

            console.log(`[Stories/delete-channel] Channel story ${storyId} deleted by user ${loggedUserId}`);
            res.json({ api_status: 200, message: 'Channel story deleted successfully' });
        } catch (err) {
            console.error('[Stories/delete-channel]', err.message);
            res.json({ api_status: 500, error_message: err.message });
        }
    };
}

// ─── register routes ────────────────────────────────────────────────────────

function registerStoryRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // Multer fields: 'file' (required) and 'cover' (optional, for video thumbnails)
    const uploadFields = upload.fields([
        { name: 'file',   maxCount: 1 },
        { name: 'cover',  maxCount: 1 },
        { name: 'music',  maxCount: 1 },
    ]);

    // Wrap multer to catch errors gracefully
    const handleUpload = (req, res, next) => {
        uploadFields(req, res, (err) => {
            if (err) {
                if (err instanceof multer.MulterError) {
                    return res.status(400).json({
                        api_status: 400,
                        error_message: 'Upload error: ' + err.message
                    });
                }
                return res.status(400).json({
                    api_status: 400,
                    error_message: err.message
                });
            }
            next();
        });
    };

    app.post('/api/node/stories/create',           auth, handleUpload, createStory(ctx, io));
    app.post('/api/node/stories/get',              auth, getStories(ctx));
    app.post('/api/node/stories/get-user-stories', auth, getUserStories(ctx));
    app.post('/api/node/stories/get-archive',      auth, getMyStoriesArchive(ctx));
    app.post('/api/node/stories/mark-viewed',      auth, markStoryViewed(ctx));
    app.post('/api/node/stories/react',            auth, reactToStory(ctx));
    app.post('/api/node/stories/get-comments',     auth, getStoryComments(ctx));
    app.post('/api/node/stories/create-comment',   auth, createStoryComment(ctx));
    // Анонімний перегляд — не потребує авторизації
    app.post('/api/node/stories/mark-viewed-anonymous', markStoryViewedAnonymous(ctx));
    // New endpoints replacing PHP
    app.post('/api/node/stories/get-by-id',              auth, getStoryById(ctx));
    app.post('/api/node/stories/delete',                  auth, deleteStory(ctx));
    app.post('/api/node/stories/get-views',               auth, getStoryViews(ctx));
    app.post('/api/node/stories/mute',                    auth, muteStory(ctx));
    app.post('/api/node/stories/delete-comment',          auth, deleteStoryComment(ctx));
    app.post('/api/node/stories/create-channel',          auth, handleUpload, createChannelStory(ctx, io));
    app.post('/api/node/stories/get-channel-subscribed',  auth, getSubscribedChannelStories(ctx));
    app.post('/api/node/stories/delete-channel',          auth, deleteChannelStory(ctx));

    console.log('[Stories API] Endpoints registered under /api/node/stories/*');
    console.log('  Stories: create, get, get-user-stories, mark-viewed, react, get-comments, create-comment,');
    console.log('           get-by-id, delete, get-views, mute, delete-comment,');
    console.log('           create-channel, get-channel-subscribed, delete-channel');
}

module.exports = { registerStoryRoutes };
