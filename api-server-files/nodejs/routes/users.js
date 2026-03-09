'use strict';

/**
 * User REST API
 *
 * Endpoints:
 *   GET /api/node/users/nearby  — пошук людей поруч (Haversine, радіус до 100 км)
 *
 * Query params:
 *   access_token  string  required  — токен сесії
 *   lat           number  required  — широта поточного користувача
 *   lon           number  required  — довгота поточного користувача
 *   radius_km     number  optional  — радіус пошуку в км (1–100, за замовч. 10)
 *   limit         number  optional  — максимальна кількість результатів (1–100, за замовч. 50)
 *
 * Умови фільтрації:
 *   - Користувач має бути активним (active = 1)
 *   - Користувач має дозволити ділитися локацією (share_my_location = 1)
 *   - Координати мають бути дійсними (lat != 0, lng != 0)
 *   - Сам запитувач виключається з результатів
 *   - Координати не старіші MAX_LOCATION_AGE_HOURS годин
 */

const { Op } = require('sequelize');

// Максимальний вік локації (год). Старіші координати ігноруються.
const MAX_LOCATION_AGE_HOURS = 24;

// ─── Haversine ────────────────────────────────────────────────────────────────

/**
 * Обчислює відстань між двома точками у кілометрах за формулою Haversine.
 * @param {number} lat1  Широта точки 1 (градуси)
 * @param {number} lon1  Довгота точки 1 (градуси)
 * @param {number} lat2  Широта точки 2 (градуси)
 * @param {number} lon2  Довгота точки 2 (градуси)
 * @returns {number} Відстань у кілометрах
 */
function haversineKm(lat1, lon1, lat2, lon2) {
    const R  = 6371;                   // Радіус Землі, км
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function toRad(deg) { return deg * (Math.PI / 180); }

// ─── Auth middleware ──────────────────────────────────────────────────────────

async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token']
               || req.query.access_token
               || req.body.access_token;

    if (!token)
        return res.json({ api_status: 401, error_message: 'access_token is required' });

    try {
        const session = await ctx.wo_appssessions.findOne({
            where: { session_id: token },
            raw:   true,
        });
        if (!session)
            return res.json({ api_status: 401, error_message: 'Invalid or expired access_token' });

        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Users/auth]', err.message);
        res.json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── Handler: GET /api/node/users/nearby ─────────────────────────────────────

function getNearbyUsers(ctx) {
    return async (req, res) => {
        try {
            const currentUserId = req.userId;

            // ── Вхідні параметри ──────────────────────────────────────────
            const lat      = parseFloat(req.query.lat      || req.body.lat);
            const lon      = parseFloat(req.query.lon      || req.body.lon);
            const radiusKm = Math.min(
                100,
                Math.max(1, parseInt(req.query.radius_km || req.body.radius_km) || 10)
            );
            const limit = Math.min(
                100,
                Math.max(1, parseInt(req.query.limit || req.body.limit) || 50)
            );

            if (isNaN(lat) || isNaN(lon))
                return res.json({ api_status: 400, error_message: 'lat and lon are required' });

            if (Math.abs(lat) > 90 || Math.abs(lon) > 180)
                return res.json({ api_status: 400, error_message: 'Invalid coordinates' });

            // ── Часова межа локації ───────────────────────────────────────
            const oldestAllowed = Math.floor(Date.now() / 1000) - MAX_LOCATION_AGE_HOURS * 3600;

            // ── Грубий географічний bbox (прямокутник, 1° ≈ 111 км) ───────
            // Це зменшує кількість рядків для точного Haversine-розрахунку.
            const latDelta = radiusKm / 111.0;
            const lonDelta = radiusKm / (111.0 * Math.cos(toRad(lat)));

            const users = await ctx.wo_users.findAll({
                attributes: [
                    'user_id', 'username', 'first_name', 'last_name',
                    'avatar', 'lastseen', 'lat', 'lng',
                ],
                where: {
                    user_id:           { [Op.ne]: currentUserId },
                    active:            '1',
                    share_my_location: 1,
                    lat: {
                        [Op.and]: [
                            { [Op.ne]: '0' },
                            { [Op.between]: [(lat - latDelta).toFixed(6), (lat + latDelta).toFixed(6)] },
                        ],
                    },
                    lng: {
                        [Op.and]: [
                            { [Op.ne]: '0' },
                            { [Op.between]: [(lon - lonDelta).toFixed(6), (lon + lonDelta).toFixed(6)] },
                        ],
                    },
                    last_location_update: { [Op.gte]: String(oldestAllowed) },
                },
                raw: true,
            });

            // ── Точний Haversine + фільтрація + сортування ────────────────
            const nearby = users
                .map(u => {
                    const uLat = parseFloat(u.lat);
                    const uLon = parseFloat(u.lng);
                    if (isNaN(uLat) || isNaN(uLon)) return null;

                    const distanceKm = haversineKm(lat, lon, uLat, uLon);
                    if (distanceKm > radiusKm) return null;

                    const displayName = [u.first_name, u.last_name].filter(Boolean).join(' ').trim()
                                     || u.username;

                    return {
                        user_id:      u.user_id,
                        username:     u.username,
                        display_name: displayName,
                        avatar:       u.avatar && u.avatar !== 'upload/photos/d-avatar.jpg'
                                          ? u.avatar
                                          : null,
                        distance_km:  Math.round(distanceKm * 10) / 10,  // 1 знак після коми
                        last_seen:    u.lastseen || null,
                    };
                })
                .filter(Boolean)
                .sort((a, b) => a.distance_km - b.distance_km)
                .slice(0, limit);

            console.log(
                `[Users/nearby] userId=${currentUserId} lat=${lat} lon=${lon} ` +
                `r=${radiusKm}km → ${nearby.length} users`
            );

            return res.json({ api_status: 200, users: nearby });

        } catch (err) {
            console.error('[Users/nearby]', err.message, err.stack);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── Update location ──────────────────────────────────────────────────────────

function updateLocation(ctx) {
    return async (req, res) => {
        const currentUserId = req.user && req.user.user_id;
        if (!currentUserId) return res.json({ api_status: 401, error_message: 'Unauthorized' });

        try {
            const lat   = parseFloat(req.body.lat);
            const lng   = parseFloat(req.body.lng);
            const share = parseInt(req.body.share_my_location, 10);

            if (isNaN(lat) || isNaN(lng))
                return res.json({ api_status: 400, error_message: 'lat and lng are required' });
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180)
                return res.json({ api_status: 400, error_message: 'Invalid coordinates' });

            const nowUnix = String(Math.floor(Date.now() / 1000));
            await ctx.wo_users.update(
                {
                    lat:                  lat.toFixed(6),
                    lng:                  lng.toFixed(6),
                    share_my_location:    isNaN(share) ? 1 : share,
                    last_location_update: nowUnix
                },
                { where: { user_id: currentUserId } }
            );

            return res.json({ api_status: 200 });
        } catch (err) {
            console.error('[Users/update-location]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── Register ─────────────────────────────────────────────────────────────────

function registerUserRoutes(app, ctx, io) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    // Підтримуємо обидва методи: GET (зручніше для мобільних) та POST (уніфіковано з іншими)
    app.get('/api/node/users/nearby',  auth, getNearbyUsers(ctx));
    app.post('/api/node/users/nearby', auth, getNearbyUsers(ctx));
    app.post('/api/node/users/update-location', auth, updateLocation(ctx));

    console.log('[User API] Endpoints registered: GET|POST /api/node/users/nearby, POST /api/node/users/update-location');
}

module.exports = { registerUserRoutes };
