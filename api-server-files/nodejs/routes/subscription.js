'use strict';

/**
 * Subscription REST API — WorldMates PRO
 *
 * Endpoints:
 *   GET  /api/node/subscription/status              — check current subscription
 *   POST /api/node/subscription/create-payment      — initiate Way4Pay / LiqPay / Monobank payment
 *   POST /api/node/subscription/wayforpay-webhook   — Way4Pay server callback (no auth)
 *   POST /api/node/subscription/liqpay-webhook      — LiqPay server callback (no auth)
 *   POST /api/node/subscription/monobank-webhook    — Monobank server callback (no auth)
 *
 * Config (set in .env):
 *   WAYFORPAY_MERCHANT_ACCOUNT   — merchant login in Way4Pay cabinet
 *   WAYFORPAY_MERCHANT_SECRET    — secret key from Way4Pay cabinet
 *   WAYFORPAY_MERCHANT_DOMAIN    — your domain, e.g. worldmates.club
 *   LIQPAY_PUBLIC_KEY            — public key from LiqPay cabinet
 *   LIQPAY_PRIVATE_KEY           — private key from LiqPay cabinet
 *   MONOBANK_TOKEN               — Monobank Merchant API token (X-Token header)
 *   MONOBANK_WEBHOOK_SECRET      — optional HMAC-SHA256 secret for webhook verification
 *   SUBSCRIPTION_PRICE_UAH       — base price per month in UAH (default: 149)
 *   SITE_URL                     — your site URL, e.g. https://worldmates.club
 */

const crypto = require('crypto');
const md5    = require('md5');
const https  = require('https');

// ─── Pricing ──────────────────────────────────────────────────────────────────
const BASE_PRICE_UAH = parseFloat(process.env.SUBSCRIPTION_PRICE_UAH || '149');

/**
 * Returns total price in UAH for N months with volume discount.
 * 1m=100%, 2-3m=95%, 4-6m=90%, 7-12m=85%, 13-24m=80%
 */
function calcPrice(months) {
    let discount;
    if      (months >= 13) discount = 0.80;
    else if (months >= 7)  discount = 0.85;
    else if (months >= 4)  discount = 0.90;
    else if (months >= 2)  discount = 0.95;
    else                   discount = 1.00;
    return Math.round(BASE_PRICE_UAH * months * discount);
}

// ─── Auth middleware (same pattern as other routes) ──────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const accessToken = req.headers['access-token'] || req.query.access_token || req.body.access_token;
    if (!accessToken) {
        return res.status(401).json({ api_status: 401, error_message: 'access_token is required' });
    }
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: accessToken } });
        if (!session) {
            return res.status(401).json({ api_status: 401, error_message: 'Invalid or expired access_token' });
        }
        req.userId = session.user_id;
        next();
    } catch (err) {
        console.error('[Subscription] Auth error:', err.message);
        return res.status(500).json({ api_status: 500, error_message: 'Authentication error' });
    }
}

// ─── Way4Pay helpers ──────────────────────────────────────────────────────────
function wayforpaySignature(params, secretKey) {
    const str = params.join(';');
    return crypto.createHmac('md5', secretKey).update(str).digest('hex');
}

/**
 * Creates a Way4Pay INVOICE and returns the checkout URL.
 * Returns { invoiceUrl, orderReference }
 */
async function createWayforpayPayment({ orderId, amountUAH, months, userId }) {
    const merchantAccount = process.env.WAYFORPAY_MERCHANT_ACCOUNT;
    const secretKey       = process.env.WAYFORPAY_MERCHANT_SECRET;
    const merchantDomain  = process.env.WAYFORPAY_MERCHANT_DOMAIN || 'worldmates.club';
    const siteUrl         = process.env.SITE_URL || 'https://worldmates.club';

    if (!merchantAccount || !secretKey) {
        throw new Error('Way4Pay credentials not configured (WAYFORPAY_MERCHANT_ACCOUNT / WAYFORPAY_MERCHANT_SECRET)');
    }

    const orderDate   = Math.floor(Date.now() / 1000);
    const productName = `WorldMates PRO ${months} міс.`;
    const amount      = (amountUAH).toFixed(2);
    const currency    = 'UAH';

    // Signature string (order matters — see Way4Pay docs):
    // merchantAccount;merchantDomainName;orderReference;orderDate;amount;currency;productName;productCount;productPrice
    const sigParts = [
        merchantAccount,
        merchantDomain,
        orderId,
        orderDate,
        amount,
        currency,
        productName,
        '1',
        amount
    ];
    const signature = wayforpaySignature(sigParts, secretKey);

    const payload = {
        transactionType:    'CREATE_INVOICE',
        merchantAccount,
        merchantAuthType:   'SimpleSignature',
        merchantDomainName: merchantDomain,
        orderReference:     orderId,
        orderDate,
        amount,
        currency,
        productName:  [productName],
        productCount: [1],
        productPrice: [amount],
        merchantSignature: signature,
        defaultPaymentSystem: 'card',
        orderLifetime: 600,  // 10 minutes
        returnUrl: `${siteUrl}/premium/success?order=${orderId}&provider=wayforpay`,
        serviceUrl: `${siteUrl}/api/node/subscription/wayforpay-webhook`
    };

    return new Promise((resolve, reject) => {
        const body = JSON.stringify(payload);
        const req = https.request({
            hostname: 'api.wayforpay.com',
            path:     '/api',
            method:   'POST',
            headers:  { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (json.invoiceUrl) {
                        resolve({ invoiceUrl: json.invoiceUrl, orderReference: orderId });
                    } else {
                        reject(new Error(json.reasonCode + ': ' + json.reason));
                    }
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ─── LiqPay helpers ───────────────────────────────────────────────────────────
function liqpaySignature(privateKey, data) {
    return crypto.createHash('sha1')
        .update(privateKey + data + privateKey)
        .digest('base64');
}

function createLiqpayPayment({ orderId, amountUAH, months }) {
    const publicKey  = process.env.LIQPAY_PUBLIC_KEY;
    const privateKey = process.env.LIQPAY_PRIVATE_KEY;
    const siteUrl    = process.env.SITE_URL || 'https://worldmates.club';

    if (!publicKey || !privateKey) {
        throw new Error('LiqPay credentials not configured (LIQPAY_PUBLIC_KEY / LIQPAY_PRIVATE_KEY)');
    }

    const params = {
        version:      3,
        public_key:   publicKey,
        action:       'pay',
        amount:       amountUAH,
        currency:     'UAH',
        description:  `WorldMates PRO ${months} міс.`,
        order_id:     orderId,
        result_url:   `${siteUrl}/premium/success?order=${orderId}&provider=liqpay`,
        server_url:   `${siteUrl}/api/node/subscription/liqpay-webhook`
    };

    const data      = Buffer.from(JSON.stringify(params)).toString('base64');
    const signature = liqpaySignature(privateKey, data);

    // Mobile-friendly checkout URL
    const checkoutUrl = `https://www.liqpay.ua/api/3/checkout?data=${encodeURIComponent(data)}&signature=${encodeURIComponent(signature)}`;
    return { checkoutUrl, data, signature };
}

// ─── Monobank helpers ─────────────────────────────────────────────────────────
/**
 * Creates a Monobank invoice and returns the checkout URL.
 * Monobank Merchant API: https://api.monobank.ua/docs/acquiring.html
 * Returns { invoiceUrl, invoiceId }
 */
async function createMonobankPayment({ orderId, amountUAH, months }) {
    const token   = process.env.MONOBANK_TOKEN;
    const siteUrl = process.env.SITE_URL || 'https://worldmates.club';

    if (!token) {
        throw new Error('MONOBANK_TOKEN not configured');
    }

    // Monobank uses kopecks (UAH * 100)
    const amountKopecks = Math.round(amountUAH * 100);

    const payload = JSON.stringify({
        amount:      amountKopecks,
        ccy:         980, // UAH currency code
        merchantPaymInfo: {
            reference:   orderId,
            destination: `WorldMates PRO ${months} міс.`,
            basketOrder: [{
                name:   `WorldMates PRO ${months} міс.`,
                qty:    1,
                sum:    amountKopecks,
                icon:   `${siteUrl}/favicon.ico`,
                unit:   'шт',
            }],
        },
        redirectUrl: `${siteUrl}/premium/success?order=${orderId}&provider=monobank`,
        webHookUrl:  `${siteUrl}/api/node/subscription/monobank-webhook`,
        validity:    600, // 10 minutes
        paymentType: 'debit',
    });

    return new Promise((resolve, reject) => {
        const req = https.request({
            hostname: 'api.monobank.ua',
            path:     '/api/merchant/invoice/create',
            method:   'POST',
            headers:  {
                'X-Token':       token,
                'Content-Type':  'application/json',
                'Content-Length': Buffer.byteLength(payload),
            },
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (json.invoiceId && json.pageUrl) {
                        resolve({ invoiceUrl: json.pageUrl, invoiceId: json.invoiceId });
                    } else {
                        reject(new Error(json.errText || JSON.stringify(json)));
                    }
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(payload);
        req.end();
    });
}

// ─── DB helper: activate subscription ────────────────────────────────────────
async function activateSubscription(ctx, userId, months) {
    const now = Math.floor(Date.now() / 1000);
    const newExpiry = now + months * 30 * 24 * 3600;

    // If already PRO and not expired, extend from current expiry
    const user = await ctx.wo_users.unscoped().findOne({
        attributes: ['user_id', 'is_pro', 'pro_time'],
        where: { user_id: userId }
    });
    let baseTime = now;
    if (user && parseInt(user.is_pro) === 1 && user.pro_time > now) {
        baseTime = user.pro_time; // extend
    }
    const expiry = baseTime + months * 30 * 24 * 3600;

    await ctx.wo_users.unscoped().update(
        { is_pro: '1', pro_type: months, pro_time: expiry },
        { where: { user_id: userId } }
    );
    return expiry;
}

// ─── Route registration ───────────────────────────────────────────────────────
function registerSubscriptionRoutes(app, ctx) {

    // GET /api/node/subscription/status
    app.get('/api/node/subscription/status', (req, res, next) => authMiddleware(ctx, req, res, next), async (req, res) => {
        try {
            const user = await ctx.wo_users.unscoped().findOne({
                attributes: ['user_id', 'is_pro', 'pro_type', 'pro_time'],
                where: { user_id: req.userId }
            });
            if (!user) return res.status(404).json({ api_status: 404, error_message: 'User not found' });

            const now    = Math.floor(Date.now() / 1000);
            const isPro  = parseInt(user.is_pro) === 1 && user.pro_time > now ? 1 : 0;
            const daysLeft = isPro ? Math.ceil((user.pro_time - now) / 86400) : 0;

            // Auto-expire in DB if needed
            if (parseInt(user.is_pro) === 1 && user.pro_time <= now) {
                await ctx.wo_users.unscoped().update(
                    { is_pro: '0' },
                    { where: { user_id: req.userId } }
                );
            }

            return res.json({
                api_status: 200,
                is_pro:     isPro,
                pro_type:   user.pro_type,
                pro_time:   user.pro_time,
                days_left:  daysLeft
            });
        } catch (err) {
            console.error('[Subscription] status error:', err);
            return res.status(500).json({ api_status: 500, error_message: 'Server error' });
        }
    });

    // POST /api/node/subscription/create-payment
    // Body: { months: 1..24, provider: 'wayforpay'|'liqpay' }
    app.post('/api/node/subscription/create-payment', (req, res, next) => authMiddleware(ctx, req, res, next), async (req, res) => {
        try {
            const months   = Math.max(1, Math.min(24, parseInt(req.body.months) || 1));
            const provider = (req.body.provider || 'wayforpay').toLowerCase();
            const amount   = calcPrice(months);
            const orderId  = `WM-${req.userId}-${Date.now()}`;

            if (provider === 'wayforpay') {
                const { invoiceUrl, orderReference } = await createWayforpayPayment({
                    orderId, amountUAH: amount, months, userId: req.userId
                });
                return res.json({
                    api_status:   200,
                    provider:     'wayforpay',
                    payment_url:  invoiceUrl,
                    order_id:     orderReference,
                    amount_uah:   amount,
                    months
                });

            } else if (provider === 'liqpay') {
                const { checkoutUrl } = createLiqpayPayment({
                    orderId, amountUAH: amount, months
                });
                return res.json({
                    api_status:   200,
                    provider:     'liqpay',
                    payment_url:  checkoutUrl,
                    order_id:     orderId,
                    amount_uah:   amount,
                    months
                });

            } else if (provider === 'monobank') {
                const { invoiceUrl, invoiceId } = await createMonobankPayment({
                    orderId, amountUAH: amount, months
                });
                return res.json({
                    api_status:   200,
                    provider:     'monobank',
                    payment_url:  invoiceUrl,
                    invoice_id:   invoiceId,
                    order_id:     orderId,
                    amount_uah:   amount,
                    months
                });

            } else {
                return res.status(400).json({ api_status: 400, error_message: 'Unknown provider. Use wayforpay, liqpay or monobank' });
            }

        } catch (err) {
            console.error('[Subscription] create-payment error:', err);
            return res.status(500).json({ api_status: 500, error_message: err.message || 'Server error' });
        }
    });

    // POST /api/node/subscription/wayforpay-webhook
    // Called by Way4Pay server after successful payment
    app.post('/api/node/subscription/wayforpay-webhook', async (req, res) => {
        try {
            const secretKey = process.env.WAYFORPAY_MERCHANT_SECRET;
            const {
                merchantAccount, orderReference, amount, currency,
                authCode, cardPan, transactionStatus, reasonCode, merchantSignature
            } = req.body;

            // Verify signature
            const sigParts = [merchantAccount, orderReference, amount, currency, authCode, cardPan, transactionStatus, reasonCode];
            const expected = wayforpaySignature(sigParts, secretKey);
            if (merchantSignature !== expected) {
                console.warn('[Subscription/Way4Pay] Invalid signature for order', orderReference);
                return res.json({ status: 'fail', code: 'INVALID_SIGNATURE' });
            }

            if (transactionStatus === 'Approved') {
                // Parse orderId: WM-{userId}-{timestamp}
                const parts  = (orderReference || '').split('-');
                const userId = parseInt(parts[1]);
                // Extract months from product name is fragile; use order amount instead:
                // Brute-force: find months where calcPrice(m) === amount
                const amountNum = parseFloat(amount);
                let months = 1;
                for (let m = 1; m <= 24; m++) {
                    if (calcPrice(m) === amountNum) { months = m; break; }
                }

                if (userId > 0) {
                    await activateSubscription(ctx, userId, months);
                    console.log(`[Subscription/Way4Pay] PRO activated user=${userId} months=${months}`);
                }
            }

            // Way4Pay requires this response
            const now = Math.floor(Date.now() / 1000);
            const time = now + 30;
            const resSigParts = ['accept', orderReference, time];
            const resSig = wayforpaySignature(resSigParts, secretKey);
            return res.json({
                orderReference,
                status: 'accept',
                time,
                signature: resSig
            });

        } catch (err) {
            console.error('[Subscription/Way4Pay] webhook error:', err);
            return res.status(500).json({ status: 'fail' });
        }
    });

    // POST /api/node/subscription/liqpay-webhook
    // Called by LiqPay server after payment
    app.post('/api/node/subscription/liqpay-webhook', async (req, res) => {
        try {
            const privateKey = process.env.LIQPAY_PRIVATE_KEY;
            const { data, signature } = req.body;

            // Verify signature
            const expected = liqpaySignature(privateKey, data);
            if (signature !== expected) {
                console.warn('[Subscription/LiqPay] Invalid signature');
                return res.status(400).json({ status: 'fail' });
            }

            const payload = JSON.parse(Buffer.from(data, 'base64').toString('utf8'));

            if (payload.status === 'success' || payload.status === 'sandbox') {
                // order_id format: WM-{userId}-{timestamp}
                const parts  = (payload.order_id || '').split('-');
                const userId = parseInt(parts[1]);
                const amountNum = parseFloat(payload.amount);
                let months = 1;
                for (let m = 1; m <= 24; m++) {
                    if (calcPrice(m) === amountNum) { months = m; break; }
                }
                if (userId > 0) {
                    await activateSubscription(ctx, userId, months);
                    console.log(`[Subscription/LiqPay] PRO activated user=${userId} months=${months}`);
                }
            }

            return res.status(200).send('OK');
        } catch (err) {
            console.error('[Subscription/LiqPay] webhook error:', err);
            return res.status(500).send('Error');
        }
    });

    // POST /api/node/subscription/monobank-webhook
    // Called by Monobank server after invoice status change
    app.post('/api/node/subscription/monobank-webhook', async (req, res) => {
        try {
            // Optional: verify webhook signature if MONOBANK_WEBHOOK_SECRET is set
            const secret = process.env.MONOBANK_WEBHOOK_SECRET;
            if (secret) {
                const xSign    = req.headers['x-sign'];
                const rawBody  = req.body ? JSON.stringify(req.body) : '';
                const expected = crypto.createHmac('sha256', secret).update(rawBody).digest('base64');
                if (xSign !== expected) {
                    console.warn('[Subscription/Monobank] Invalid webhook signature');
                    return res.status(400).json({ status: 'fail', code: 'INVALID_SIGNATURE' });
                }
            }

            const { status, reference, amount, invoiceId } = req.body;

            // status 'success' means payment completed
            if (status === 'success') {
                // reference = orderId = WM-{userId}-{timestamp}
                const parts     = (reference || '').split('-');
                const userId    = parseInt(parts[1]);
                // Monobank amount is in kopecks (1/100 UAH)
                const amountUAH = Math.round((amount || 0) / 100);

                let months = 1;
                for (let m = 1; m <= 24; m++) {
                    if (calcPrice(m) === amountUAH) { months = m; break; }
                }

                if (userId > 0) {
                    await activateSubscription(ctx, userId, months);
                    console.log(`[Subscription/Monobank] PRO activated user=${userId} months=${months} invoiceId=${invoiceId}`);
                }
            }

            // Monobank expects 200 OK
            return res.status(200).json({ status: 'ok' });

        } catch (err) {
            console.error('[Subscription/Monobank] webhook error:', err);
            return res.status(500).json({ status: 'fail' });
        }
    });

    console.log('[Subscription] routes registered');
}

module.exports = { registerSubscriptionRoutes, calcPrice };
