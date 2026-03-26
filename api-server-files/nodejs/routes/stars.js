'use strict';

/**
 * WorldStars REST API — внутрішня валюта WorldMates
 *
 * Endpoints:
 *   GET  /api/node/stars/balance              — баланс + останні транзакції
 *   GET  /api/node/stars/transactions         — повна історія (pagination)
 *   GET  /api/node/stars/packs                — пакети для покупки (статичні)
 *   POST /api/node/stars/send                 — надіслати зірки користувачу
 *   POST /api/node/stars/purchase             — ініціювати оплату пакету
 *   POST /api/node/stars/wayforpay-webhook    — Way4Pay callback (no auth)
 *   POST /api/node/stars/liqpay-webhook       — LiqPay callback (no auth)
 *
 * Config (.env):
 *   WAYFORPAY_MERCHANT_ACCOUNT, WAYFORPAY_MERCHANT_SECRET, WAYFORPAY_MERCHANT_DOMAIN
 *   LIQPAY_PUBLIC_KEY, LIQPAY_PRIVATE_KEY
 *   SITE_URL
 */

const crypto = require('crypto');
const md5    = require('md5');

// ─── Star packs (static catalogue) ───────────────────────────────────────────
const STAR_PACKS = [
    { id: 1, stars: 50,   price_uah: 19,   is_popular: false, label: 'Starter' },
    { id: 2, stars: 100,  price_uah: 35,   is_popular: false, label: 'Basic'   },
    { id: 3, stars: 500,  price_uah: 149,  is_popular: true,  label: 'Popular' },
    { id: 4, stars: 1000, price_uah: 279,  is_popular: false, label: 'Value'   },
    { id: 5, stars: 5000, price_uah: 1199, is_popular: false, label: 'Pro'     },
];

// ─── Auth middleware ──────────────────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token'] || req.query.access_token || req.body.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired token' });
        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Stars] Auth error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── DB helpers ───────────────────────────────────────────────────────────────

/** Повертає поточний баланс або 0 якщо рядка ще немає. */
async function getBalance(ctx, userId) {
    const [rows] = await ctx.sequelize.query(
        'SELECT balance, total_purchased, total_sent, total_received FROM wm_stars_balance WHERE user_id = ?',
        { replacements: [userId] }
    );
    return rows[0] || { balance: 0, total_purchased: 0, total_sent: 0, total_received: 0 };
}

/**
 * Атомарно змінює баланс на delta (може бути від'ємним).
 * Повертає { ok: bool, newBalance: int }.
 */
async function adjustBalance(ctx, userId, delta, statsField) {
    // INSERT ... ON DUPLICATE KEY UPDATE — один запит, без race condition
    const setClause = delta > 0
        ? `balance = balance + ?, ${statsField} = ${statsField} + ?`
        : `balance = GREATEST(0, balance + ?)`;  // не допускаємо від'ємний баланс

    const vals = delta > 0 ? [delta, delta, userId] : [delta, userId];

    await ctx.sequelize.query(
        `INSERT INTO wm_stars_balance (user_id, balance, ${statsField})
         VALUES (?, GREATEST(0, ?), ?)
         ON DUPLICATE KEY UPDATE ${setClause}`,
        { replacements: delta > 0 ? [userId, delta, delta, ...vals] : [userId, delta, 0, ...vals] }
    );

    const [rows] = await ctx.sequelize.query(
        'SELECT balance FROM wm_stars_balance WHERE user_id = ?',
        { replacements: [userId] }
    );
    return rows[0]?.balance ?? 0;
}

/** Додає рядок у wm_stars_transactions. */
async function logTx(ctx, { fromUserId, toUserId, amount, type, refType, refId, note, orderId }) {
    await ctx.sequelize.query(
        `INSERT INTO wm_stars_transactions
         (from_user_id, to_user_id, amount, type, ref_type, ref_id, note, order_id)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        { replacements: [fromUserId || null, toUserId, amount, type, refType || null, refId || null, note || null, orderId || null] }
    );
}

/** Форматує список транзакцій для відповіді API. */
function formatTx(rows) {
    return rows.map(r => ({
        id:           r.id,
        from_user_id: r.from_user_id,
        to_user_id:   r.to_user_id,
        amount:       r.amount,
        type:         r.type,
        ref_type:     r.ref_type,
        ref_id:       r.ref_id,
        note:         r.note,
        created_at:   r.created_at,
        // Розгорнуті дані про другу сторону (JOIN)
        other_user_name:   r.other_user_name || null,
        other_user_avatar: r.other_user_avatar || null,
    }));
}

// ─── Way4Pay helpers ──────────────────────────────────────────────────────────
function wayforpaySign(params, secret) {
    return crypto.createHmac('md5', secret).update(params.join(';')).digest('hex');
}

async function createWayforpayPayment({ orderId, amountUAH, stars, userId }) {
    const merchant = process.env.WAYFORPAY_MERCHANT_ACCOUNT;
    const secret   = process.env.WAYFORPAY_MERCHANT_SECRET;
    const domain   = process.env.WAYFORPAY_MERCHANT_DOMAIN || 'worldmates.club';
    const siteUrl  = process.env.SITE_URL || 'https://worldmates.club';

    const orderDate = Math.floor(Date.now() / 1000);
    const productName = [`WorldStars ${stars}⭐`];
    const productCount = [1];
    const productPrice = [amountUAH];

    const sigParts = [
        merchant, orderId, String(amountUAH), 'UAH', String(orderDate),
        domain, ...productName, ...productCount.map(String), ...productPrice.map(String),
    ];
    const signature = wayforpaySign(sigParts, secret);

    const body = JSON.stringify({
        transactionType:  'CREATE_INVOICE',
        merchantAccount:  merchant,
        merchantDomainName: domain,
        merchantSignature: signature,
        apiVersion: 1,
        language: 'UA',
        serviceUrl:  `${siteUrl}/api/node/stars/wayforpay-webhook`,
        returnUrl:   `${siteUrl}/payment-return`,
        orderReference: orderId,
        orderDate,
        amount: amountUAH,
        currency: 'UAH',
        productName,
        productCount,
        productPrice,
        clientAccountId: String(userId),
    });

    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'api.wayforpay.com',
            path: '/api',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
        };
        const req = require('https').request(options, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    if (parsed.invoiceUrl) resolve({ invoiceUrl: parsed.invoiceUrl, orderReference: parsed.orderReference || orderId });
                    else reject(new Error(parsed.reasonCode || 'No invoiceUrl'));
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ─── LiqPay helpers ───────────────────────────────────────────────────────────
function liqpaySign(data, privateKey) {
    return crypto.createHash('sha1').update(privateKey + data + privateKey).digest('base64');
}

function createLiqpayPayment({ orderId, amountUAH, stars, userId }) {
    const pub  = process.env.LIQPAY_PUBLIC_KEY;
    const priv = process.env.LIQPAY_PRIVATE_KEY;
    const siteUrl = process.env.SITE_URL || 'https://worldmates.club';

    const params = {
        public_key:  pub,
        version:     '3',
        action:      'pay',
        amount:      amountUAH,
        currency:    'UAH',
        description: `WorldStars ${stars}⭐`,
        order_id:    orderId,
        result_url:  `${siteUrl}/payment-return`,
        server_url:  `${siteUrl}/api/node/stars/liqpay-webhook`,
    };
    const data = Buffer.from(JSON.stringify(params)).toString('base64');
    const signature = liqpaySign(data, priv);
    const checkoutUrl = `https://www.liqpay.ua/api/3/checkout?data=${data}&signature=${signature}`;
    return { checkoutUrl, orderId };
}

// ─── Route handlers ───────────────────────────────────────────────────────────

/** GET /api/node/stars/balance */
async function getBalanceRoute(ctx, req, res) {
    try {
        const bal = await getBalance(ctx, req.userId);
        const [txRows] = await ctx.sequelize.query(
            `SELECT t.*, u.first_name AS other_user_name, u.avatar AS other_user_avatar
             FROM wm_stars_transactions t
             LEFT JOIN Wo_Users u ON u.user_id = IF(t.from_user_id = ?, t.to_user_id, t.from_user_id)
             WHERE t.to_user_id = ? OR t.from_user_id = ?
             ORDER BY t.created_at DESC LIMIT 10`,
            { replacements: [req.userId, req.userId, req.userId] }
        );
        res.json({
            api_status:        200,
            balance:           bal.balance,
            total_purchased:   bal.total_purchased,
            total_sent:        bal.total_sent,
            total_received:    bal.total_received,
            recent_transactions: formatTx(txRows),
        });
    } catch (err) {
        console.error('[Stars] getBalance error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Server error' });
    }
}

/** GET /api/node/stars/transactions?limit=20&offset=0 */
async function getTransactionsRoute(ctx, req, res) {
    try {
        const limit  = Math.min(parseInt(req.query.limit  || '20', 10), 100);
        const offset = parseInt(req.query.offset || '0', 10);
        const [rows] = await ctx.sequelize.query(
            `SELECT t.*, u.first_name AS other_user_name, u.avatar AS other_user_avatar
             FROM wm_stars_transactions t
             LEFT JOIN Wo_Users u ON u.user_id = IF(t.from_user_id = ?, t.to_user_id, t.from_user_id)
             WHERE t.to_user_id = ? OR t.from_user_id = ?
             ORDER BY t.created_at DESC LIMIT ? OFFSET ?`,
            { replacements: [req.userId, req.userId, req.userId, limit, offset] }
        );
        res.json({ api_status: 200, transactions: formatTx(rows), limit, offset });
    } catch (err) {
        console.error('[Stars] getTransactions error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Server error' });
    }
}

/** GET /api/node/stars/packs */
function getPacksRoute(req, res) {
    res.json({ api_status: 200, packs: STAR_PACKS });
}

/** POST /api/node/stars/send  body: { to_user_id, amount, note? } */
async function sendStarsRoute(ctx, req, res) {
    try {
        const toUserId = parseInt(req.body.to_user_id, 10);
        const amount   = parseInt(req.body.amount, 10);
        const note     = String(req.body.note || '').slice(0, 255).trim() || null;

        if (!toUserId || toUserId === req.userId) {
            return res.json({ api_status: 400, error_message: 'Invalid recipient' });
        }
        if (!amount || amount < 1 || amount > 10000) {
            return res.json({ api_status: 400, error_message: 'Amount must be 1..10000' });
        }

        // Перевіряємо що одержувач існує
        const recipient = await ctx.wo_users.findOne({ where: { user_id: toUserId } });
        if (!recipient) return res.json({ api_status: 404, error_message: 'User not found' });

        // Перевіряємо баланс відправника
        const senderBal = await getBalance(ctx, req.userId);
        if (senderBal.balance < amount) {
            return res.json({ api_status: 402, error_message: 'Insufficient stars balance' });
        }

        // Списуємо у відправника
        await ctx.sequelize.query(
            'UPDATE wm_stars_balance SET balance = balance - ?, total_sent = total_sent + ?, updated_at = NOW() WHERE user_id = ? AND balance >= ?',
            { replacements: [amount, amount, req.userId, amount] }
        );

        // Нараховуємо одержувачу (upsert)
        await ctx.sequelize.query(
            `INSERT INTO wm_stars_balance (user_id, balance, total_received)
             VALUES (?, ?, ?)
             ON DUPLICATE KEY UPDATE balance = balance + ?, total_received = total_received + ?, updated_at = NOW()`,
            { replacements: [toUserId, amount, amount, amount, amount] }
        );

        // Лог — два записи: send (від відправника) + receive (у одержувача)
        await logTx(ctx, { fromUserId: req.userId, toUserId, amount, type: 'send', refType: 'user', refId: toUserId, note });
        await logTx(ctx, { fromUserId: req.userId, toUserId, amount, type: 'receive', refType: 'user', refId: req.userId, note });

        const newBal = await getBalance(ctx, req.userId);
        res.json({ api_status: 200, new_balance: newBal.balance });
    } catch (err) {
        console.error('[Stars] send error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Server error' });
    }
}

/** POST /api/node/stars/purchase  body: { pack_id, provider } */
async function purchaseRoute(ctx, req, res) {
    try {
        const packId   = parseInt(req.body.pack_id, 10);
        const provider = req.body.provider || 'wayforpay';

        const pack = STAR_PACKS.find(p => p.id === packId);
        if (!pack) return res.json({ api_status: 400, error_message: 'Invalid pack_id' });

        const orderId = `WMS-${req.userId}-${Date.now()}`;

        if (provider === 'liqpay') {
            const { checkoutUrl } = createLiqpayPayment({
                orderId, amountUAH: pack.price_uah, stars: pack.stars, userId: req.userId,
            });
            return res.json({
                api_status:  200,
                provider:    'liqpay',
                payment_url: checkoutUrl,
                order_id:    orderId,
                pack,
            });
        }

        // Default: wayforpay
        const { invoiceUrl, orderReference } = await createWayforpayPayment({
            orderId, amountUAH: pack.price_uah, stars: pack.stars, userId: req.userId,
        });
        res.json({
            api_status:  200,
            provider:    'wayforpay',
            payment_url: invoiceUrl,
            order_id:    orderReference,
            pack,
        });
    } catch (err) {
        console.error('[Stars] purchase error:', err.message);
        res.status(500).json({ api_status: 500, error_message: 'Payment initiation failed' });
    }
}

/** POST /api/node/stars/wayforpay-webhook  (no auth — called by Way4Pay server) */
async function wayforpayWebhook(ctx, req, res) {
    try {
        const {
            merchantAccount, orderReference, amount, currency,
            authCode, cardPan, transactionStatus, reasonCode, merchantSignature,
        } = req.body;

        const secret = process.env.WAYFORPAY_MERCHANT_SECRET;
        const expected = wayforpaySign(
            [merchantAccount, orderReference, amount, currency, authCode, cardPan, transactionStatus, reasonCode],
            secret
        );
        if (merchantSignature !== expected) {
            console.warn('[Stars] Way4Pay: invalid signature');
            return res.status(400).json({ status: 'INVALID_SIGNATURE' });
        }

        if (transactionStatus === 'Approved') {
            // Витягуємо userId з orderId: WMS-{userId}-{ts}
            const parts  = String(orderReference).split('-');
            const userId = parseInt(parts[1], 10);
            const pack   = STAR_PACKS.find(p => p.price_uah === Math.round(parseFloat(amount)));

            if (userId && pack) {
                // Нараховуємо зірки
                await ctx.sequelize.query(
                    `INSERT INTO wm_stars_balance (user_id, balance, total_purchased)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE balance = balance + ?, total_purchased = total_purchased + ?, updated_at = NOW()`,
                    { replacements: [userId, pack.stars, pack.stars, pack.stars, pack.stars] }
                );
                await logTx(ctx, {
                    fromUserId: null, toUserId: userId,
                    amount: pack.stars, type: 'purchase',
                    refType: 'pack', refId: pack.id,
                    note: `Way4Pay — ${pack.price_uah} UAH`,
                    orderId: orderReference,
                });
                console.info(`[Stars] Way4Pay: +${pack.stars}⭐ to user ${userId} (order ${orderReference})`);
            }
        }

        const now = Math.floor(Date.now() / 1000) + 30;
        const secret2 = process.env.WAYFORPAY_MERCHANT_SECRET;
        res.json({
            orderReference,
            status:    'accept',
            time:      now,
            signature: wayforpaySign(['accept', orderReference, String(now)], secret2),
        });
    } catch (err) {
        console.error('[Stars] Way4Pay webhook error:', err.message);
        res.status(500).json({ status: 'error' });
    }
}

/** POST /api/node/stars/liqpay-webhook  (no auth — called by LiqPay server) */
async function liqpayWebhook(ctx, req, res) {
    try {
        const { data, signature } = req.body;
        const priv    = process.env.LIQPAY_PRIVATE_KEY;
        const expected = liqpaySign(data, priv);
        if (signature !== expected) {
            console.warn('[Stars] LiqPay: invalid signature');
            return res.status(400).send('INVALID_SIGNATURE');
        }

        const params = JSON.parse(Buffer.from(data, 'base64').toString());
        if (params.status === 'success' || params.status === 'sandbox') {
            const parts  = String(params.order_id).split('-');
            const userId = parseInt(parts[1], 10);
            const amountUAH = Math.round(parseFloat(params.amount));
            const pack   = STAR_PACKS.find(p => p.price_uah === amountUAH);

            if (userId && pack) {
                await ctx.sequelize.query(
                    `INSERT INTO wm_stars_balance (user_id, balance, total_purchased)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE balance = balance + ?, total_purchased = total_purchased + ?, updated_at = NOW()`,
                    { replacements: [userId, pack.stars, pack.stars, pack.stars, pack.stars] }
                );
                await logTx(ctx, {
                    fromUserId: null, toUserId: userId,
                    amount: pack.stars, type: 'purchase',
                    refType: 'pack', refId: pack.id,
                    note: `LiqPay — ${amountUAH} UAH`,
                    orderId: params.order_id,
                });
                console.info(`[Stars] LiqPay: +${pack.stars}⭐ to user ${userId}`);
            }
        }
        res.send('OK');
    } catch (err) {
        console.error('[Stars] LiqPay webhook error:', err.message);
        res.status(500).send('error');
    }
}

// ─── Route registration ───────────────────────────────────────────────────────
function registerStarsRoutes(app, ctx) {
    const auth = (req, res, next) => authMiddleware(ctx, req, res, next);

    app.get ('/api/node/stars/balance',           auth, (req, res) => getBalanceRoute(ctx, req, res));
    app.get ('/api/node/stars/transactions',      auth, (req, res) => getTransactionsRoute(ctx, req, res));
    app.get ('/api/node/stars/packs',             auth, (req, res) => getPacksRoute(req, res));
    app.post('/api/node/stars/send',              auth, (req, res) => sendStarsRoute(ctx, req, res));
    app.post('/api/node/stars/purchase',          auth, (req, res) => purchaseRoute(ctx, req, res));
    app.post('/api/node/stars/wayforpay-webhook',       (req, res) => wayforpayWebhook(ctx, req, res));
    app.post('/api/node/stars/liqpay-webhook',          (req, res) => liqpayWebhook(ctx, req, res));
}

module.exports = { registerStarsRoutes };
