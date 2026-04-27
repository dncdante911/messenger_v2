'use strict';
/**
 * Firebase Cloud Messaging — push notification helper.
 *
 * SETUP (one-time):
 *  1. Go to Firebase Console → Project Settings → Service Accounts
 *  2. Click "Generate new private key" → save as firebase-service-account.json
 *     in the same directory as this file (api-server-files/nodejs/).
 *  3. npm install firebase-admin  (already listed in package.json)
 *  4. That's it — the module self-initialises on first import.
 *
 * Usage:
 *   const { sendPush } = require('./firebase-push');
 *   await sendPush(fcmToken, { title: 'Alice', body: 'Hey!', data: { type: 'message', from_id: '5' } });
 */

const path = require('path');

let admin       = null;
let messaging   = null;
let initAttempted = false;

function init() {
    if (initAttempted) return;
    initAttempted = true;
    try {
        admin = require('firebase-admin');
        const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');
        const serviceAccount = require(serviceAccountPath);
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount),
        });
        messaging = admin.messaging();
        console.log('[FCM] Firebase Admin SDK initialised');
    } catch (e) {
        if (e.code === 'MODULE_NOT_FOUND' && e.message.includes('firebase-service-account')) {
            console.warn('[FCM] firebase-service-account.json not found — push notifications disabled.');
            console.warn('[FCM] See api-server-files/nodejs/firebase-push.js for setup instructions.');
        } else if (e.code === 'MODULE_NOT_FOUND' && e.message.includes('firebase-admin')) {
            console.warn('[FCM] firebase-admin package not installed. Run: npm install firebase-admin');
        } else {
            console.error('[FCM] Init error:', e.message);
        }
        admin     = null;
        messaging = null;
    }
}

/**
 * Send a push notification to a single FCM token.
 *
 * @param {string} token   - Device FCM registration token.
 * @param {object} opts
 * @param {string} opts.title  - Notification title (sender name).
 * @param {string} opts.body   - Notification body text.
 * @param {object} [opts.data] - Extra key→value data payload (strings only).
 * @returns {Promise<boolean>} true if sent, false on error / FCM disabled.
 */
async function sendPush(token, { title, body, data = {} }) {
    init();
    if (!messaging || !token) return false;

    // Stringify all data values — FCM requires string map
    const stringData = {};
    for (const [k, v] of Object.entries(data)) {
        stringData[k] = String(v);
    }

    const message = {
        token,
        notification: { title, body },
        data: stringData,
        android: {
            priority: 'high',
            notification: {
                sound:       'default',
                channelId:   'wm_messages',
                clickAction: 'OPEN_MESSAGES_ACTIVITY',
            },
        },
    };

    try {
        await messaging.send(message);
        return true;
    } catch (e) {
        // Token expired / unregistered — caller should delete it from DB
        if (e.code === 'messaging/registration-token-not-registered' ||
            e.code === 'messaging/invalid-registration-token') {
            console.warn('[FCM] Stale token removed for send attempt');
            return 'stale';  // Caller can use this to clean up the token
        }
        console.error('[FCM] sendPush error:', e.message);
        return false;
    }
}

/**
 * Send the same push notification to many FCM tokens in batches of 500.
 * Uses sendEachForMulticast (firebase-admin ≥ 11).
 *
 * @param {string[]} tokens   - Array of FCM registration tokens.
 * @param {object}  opts      - Same shape as sendPush opts: { title, body, data }.
 * @returns {Promise<{ sent: number, stale: string[] }>}
 */
async function sendPushToMany(tokens, { title, body, data = {} }) {
    init();
    if (!messaging || !tokens || tokens.length === 0) return { sent: 0, stale: [] };

    const stringData = {};
    for (const [k, v] of Object.entries(data)) stringData[k] = String(v);

    const BATCH_SIZE = 500;
    let sent  = 0;
    const stale = [];

    for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
        const batch = tokens.slice(i, i + BATCH_SIZE);
        const message = {
            tokens: batch,
            notification: { title, body },
            data: stringData,
            android: {
                priority: 'high',
                notification: {
                    sound:       'default',
                    channelId:   'wm_livestream',
                    clickAction: 'OPEN_CHANNEL_ACTIVITY',
                },
            },
        };
        try {
            const result = await messaging.sendEachForMulticast(message);
            sent += result.successCount;
            result.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    const code = resp.error?.code;
                    if (
                        code === 'messaging/registration-token-not-registered' ||
                        code === 'messaging/invalid-registration-token'
                    ) {
                        stale.push(batch[idx]);
                    }
                }
            });
        } catch (e) {
            console.error('[FCM] sendPushToMany batch error:', e.message);
        }
    }

    return { sent, stale };
}

module.exports = { sendPush, sendPushToMany };
