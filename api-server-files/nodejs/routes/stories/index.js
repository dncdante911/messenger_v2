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
const ALLOWED_EXTS = [...ALLOWED_IMAGE_EXTS, ...ALLOWED_VIDEO_EXTS];
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
    for (const m of mediaRows) {
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
            fs.writeFileSync(absolutePath, file.buffer);
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
                fs.writeFileSync(path.join(SITE_ROOT, coverRelative), coverFile.buffer);
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
                const roomName = `stories_${userId}`;
                io.to(roomName).emit('story:created', {
                    userId,
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

// ─── register routes ────────────────────────────────────────────────────────

function registerStoryRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // Multer fields: 'file' (required) and 'cover' (optional, for video thumbnails)
    const uploadFields = upload.fields([
        { name: 'file', maxCount: 1 },
        { name: 'cover', maxCount: 1 },
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

    app.post('/api/node/stories/create',          auth, handleUpload, createStory(ctx, io));
    app.post('/api/node/stories/get',             auth, getStories(ctx));
    app.post('/api/node/stories/get-user-stories', auth, getUserStories(ctx));

    console.log('[Stories API] Endpoints registered under /api/node/stories/*');
    console.log('  Stories: create, get, get-user-stories');
}

module.exports = { registerStoryRoutes };
