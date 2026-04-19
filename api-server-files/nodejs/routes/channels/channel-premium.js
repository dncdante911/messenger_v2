'use strict';

/**
 * Channel Premium Subscription API
 *
 * Endpoints:
 *   GET  /api/node/channels/:channel_id/premium/status       — check channel premium status
 *   POST /api/node/channels/:channel_id/premium/create-payment — start payment (owner only)
 *   POST /api/node/channel-premium/wayforpay-webhook          — Way4Pay callback (no auth)
 *   POST /api/node/channel-premium/liqpay-webhook             — LiqPay callback (no auth)
 *   POST /api/node/channel-premium/monobank-webhook           — Monobank callback (no auth)
 *
 * Plans and pricing:
 *   monthly   = 1 month  × BASE_CHANNEL_PRICE_UAH × 1.00
 *   quarterly = 3 months × BASE_CHANNEL_PRICE_UAH × 0.90  (10% off)
 *   annual    = 12 months × BASE_CHANNEL_PRICE_UAH × 0.75 (25% off)
 *
 * Env vars:
 *   CHANNEL_SUBSCRIPTION_PRICE_UAH  — base price per month (default: 299)
 *   (reuses WAYFORPAY_*, LIQPAY_*, MONOBANK_TOKEN from subscription.js)
 */

const crypto  = require('crypto');
const md5     = require('md5');
const https   = require('https');

const BASE_PRICE_UAH = parseFloat(process.env.CHANNEL_SUBSCRIPTION_PRICE_UAH || '299');
const TRIAL_DAYS     = parseInt(process.env.CHANNEL_PREMIUM_TRIAL_DAYS || '7', 10);
const TRIAL_PLAN     = 'trial';

const PLANS = {
    monthly:   { months: 1,  discount: 1.00 },
    quarterly: { months: 3,  discount: 0.90 },
    annual:    { months: 12, discount: 0.75 }
};

function calcChannelPrice(plan) {
    const p = PLANS[plan];
    if (!p) return null;
    return Math.round(BASE_PRICE_UAH * p.months * p.discount);
}

function planToMonths(plan) { return PLANS[plan]?.months || 1; }

// ─── Auth helpers ─────────────────────────────────────────────────────────────
async function authMiddleware(ctx, req, res, next) {
    const token = req.headers['access-token'] || req.query.access_token || req.body.access_token;
    if (!token) return res.status(401).json({ api_status: 401, error_message: 'access_token required' });
    try {
        const session = await ctx.wo_appssessions.findOne({ where: { session_id: token } });
        if (!session) return res.status(401).json({ api_status: 401, error_message: 'Invalid access_token' });
        req.userId = session.user_id;
        next();
    } catch (e) {
        return res.status(500).json({ api_status: 500, error_message: 'Auth error' });
    }
}

async function isChannelOwner(ctx, channelId, userId) {
    const page = await ctx.wo_pages.findOne({ where: { page_id: channelId }, attributes: ['user_id'], raw: true });
    return page && page.user_id == userId;
}

// ─── Way4Pay helpers (same as subscription.js) ───────────────────────────────
function wayforpaySignature(params, secretKey) {
    return crypto.createHmac('md5', secretKey).update(params.join(';')).digest('hex');
}

async function createWayforpayPayment({ orderId, amountUAH, plan, channelId }) {
    const merchantAccount = process.env.WAYFORPAY_MERCHANT_ACCOUNT;
    const secretKey       = process.env.WAYFORPAY_MERCHANT_SECRET;
    const merchantDomain  = process.env.WAYFORPAY_MERCHANT_DOMAIN || 'worldmates.club';
    const siteUrl         = process.env.SITE_URL || 'https://worldmates.club';
    const webhookBase     = process.env.NODE_WEBHOOK_BASE_URL || siteUrl;

    if (!merchantAccount || !secretKey) throw new Error('Way4Pay credentials not configured');

    const productName = `Channel Premium (${plan}) – channel #${channelId}`;
    const orderDate   = Math.floor(Date.now() / 1000);
    const signParams  = [merchantAccount, merchantDomain, orderId, orderDate, amountUAH, 'UAH', productName, 1, amountUAH];
    const signature   = wayforpaySignature(signParams, secretKey);

    const payload = JSON.stringify({
        transactionType:   'CREATE_INVOICE',
        merchantAccount,
        merchantDomainName: merchantDomain,
        orderReference:    orderId,
        orderDate,
        amount:            amountUAH,
        currency:          'UAH',
        productName:       [productName],
        productCount:      [1],
        productPrice:      [amountUAH],
        merchantSignature: signature,
        returnUrl: `${siteUrl}/channel-premium-success`,
        serviceUrl: `${webhookBase}/api/node/channel-premium/wayforpay-webhook`
    });

    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'api.wayforpay.com', port: 443,
            path: '/api', method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) }
        };
        const req = https.request(options, (r) => {
            let data = '';
            r.on('data', d => data += d);
            r.on('end', () => {
                try {
                    const body = JSON.parse(data);
                    if (body.invoiceUrl) resolve({ invoiceUrl: body.invoiceUrl, orderReference: orderId });
                    else reject(new Error(body.reason || 'Way4Pay returned no invoiceUrl'));
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(payload);
        req.end();
    });
}

// ─── LiqPay helper ────────────────────────────────────────────────────────────
function createLiqpayPayment({ orderId, amountUAH, plan, channelId }) {
    const publicKey  = process.env.LIQPAY_PUBLIC_KEY;
    const privateKey = process.env.LIQPAY_PRIVATE_KEY;
    if (!publicKey || !privateKey) throw new Error('LiqPay credentials not configured');

    const siteUrl     = process.env.SITE_URL || 'https://worldmates.club';
    const webhookBase = process.env.NODE_WEBHOOK_BASE_URL || siteUrl;
    const params   = {
        public_key: publicKey, version: '3', action: 'pay',
        amount: amountUAH, currency: 'UAH',
        description: `Channel Premium (${plan}) – channel #${channelId}`,
        order_id: orderId,
        result_url: `${siteUrl}/channel-premium-success`,
        server_url: `${webhookBase}/api/node/channel-premium/liqpay-webhook`
    };
    const data      = Buffer.from(JSON.stringify(params)).toString('base64');
    const signature = Buffer.from(crypto.createHash('sha1').update(privateKey + data + privateKey).digest()).toString('base64');
    return { data, signature, checkoutUrl: `https://www.liqpay.ua/api/3/checkout` };
}

// ─── Monobank helper ─────────────────────────────────────────────────────────
async function createMonobankPayment({ orderId, amountUAH, plan, channelId }) {
    const token       = process.env.MONOBANK_TOKEN;
    const siteUrl     = process.env.SITE_URL || 'https://worldmates.club';
    const webhookBase = process.env.NODE_WEBHOOK_BASE_URL || siteUrl;
    if (!token) throw new Error('MONOBANK_TOKEN not configured');

    const amountKopecks = Math.round(amountUAH * 100);
    const description   = `Channel Premium (${plan}) – channel #${channelId}`;

    const payload = JSON.stringify({
        amount:      amountKopecks,
        ccy:         980,
        merchantPaymInfo: {
            reference:   orderId,
            destination: description,
            basketOrder: [{
                name:   description,
                qty:    1,
                sum:    amountKopecks,
                icon:   `${siteUrl}/favicon.ico`,
                unit:   'шт',
            }],
        },
        redirectUrl: `${siteUrl}/channel-premium-success?order=${orderId}&provider=monobank`,
        webHookUrl:  `${webhookBase}/api/node/channel-premium/monobank-webhook`,
        validity:    600,
        paymentType: 'debit',
    });

    return new Promise((resolve, reject) => {
        const req = https.request({
            hostname: 'api.monobank.ua',
            path:     '/api/merchant/invoice/create',
            method:   'POST',
            headers:  {
                'X-Token':        token,
                'Content-Type':   'application/json',
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

// ─── Activate helper ──────────────────────────────────────────────────────────
async function activateChannelPremium(ctx, channelId, plan) {
    if (!ctx.wm_channel_subscriptions) return;
    const months   = planToMonths(plan);
    const now      = new Date();
    const existing = await ctx.wm_channel_subscriptions.findOne({ where: { channel_id: channelId } });
    let baseTime   = now;
    if (existing && existing.is_active && existing.expires_at && new Date(existing.expires_at) > now) {
        baseTime = new Date(existing.expires_at); // extend
    }
    const expires  = new Date(baseTime.getTime() + months * 30 * 24 * 60 * 60 * 1000);

    if (existing) {
        await ctx.wm_channel_subscriptions.update(
            { is_active: 1, plan, started_at: now, expires_at: expires },
            { where: { channel_id: channelId } }
        );
    } else {
        await ctx.wm_channel_subscriptions.create({
            channel_id: channelId, is_active: 1, plan, started_at: now, expires_at: expires
        });
    }
    console.log(`[ChannelPremium] Channel ${channelId} activated: plan=${plan} expires=${expires}`);
}

module.exports = function registerChannelPremiumRoutes(app, ctx) {

    // ── GET status ───────────────────────────────────────────────────────────
    app.get('/api/node/channels/:channel_id/premium/status',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            try {
                let status = { is_active: 0, plan: null, expires_at: null, days_left: 0 };
                let trialAvailable = 0;

                if (ctx.wm_channel_subscriptions) {
                    const sub = await ctx.wm_channel_subscriptions.findOne({
                        where: { channel_id: channelId }, raw: true
                    });
                    if (sub) {
                        const now  = new Date();
                        const active = sub.is_active && (!sub.expires_at || new Date(sub.expires_at) > now);
                        const daysLeft = active && sub.expires_at
                            ? Math.ceil((new Date(sub.expires_at) - now) / 86400000)
                            : 0;

                        // Auto-expire
                        if (sub.is_active && sub.expires_at && new Date(sub.expires_at) <= now) {
                            await ctx.wm_channel_subscriptions.update(
                                { is_active: 0 }, { where: { channel_id: channelId } }
                            );
                        }

                        status = {
                            is_active:  active ? 1 : 0,
                            plan:       sub.plan,
                            expires_at: sub.expires_at,
                            days_left:  daysLeft,
                            started_at: sub.started_at
                        };
                        // Trial is only available if the channel never had any
                        // subscription before — existing record blocks it.
                        trialAvailable = 0;
                    } else {
                        trialAvailable = 1;
                    }
                }

                return res.json({ api_status: 200, ...status, base_price_uah: BASE_PRICE_UAH,
                    trial_available: trialAvailable,
                    trial_days: TRIAL_DAYS,
                    plans: Object.fromEntries(
                        Object.entries(PLANS).map(([k, v]) => [k, { months: v.months, price_uah: calcChannelPrice(k) }])
                    )
                });
            } catch (e) {
                console.error('[ChannelPremium] status error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── POST start-trial ─────────────────────────────────────────────────────
    //
    // Grants a free 7-day (configurable) premium subscription to a channel
    // whose owner has never subscribed before. Idempotent inside its
    // eligibility window — callers that hit it twice get a 409.
    app.post('/api/node/channels/:channel_id/premium/start-trial',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const userId    = req.userId;
            try {
                if (!await isChannelOwner(ctx, channelId, userId)) {
                    return res.status(403).json({ api_status: 403, error_message: 'Only channel owner can start trial' });
                }
                if (!ctx.wm_channel_subscriptions) {
                    return res.status(500).json({ api_status: 500, error_message: 'Subscriptions table unavailable' });
                }

                const existing = await ctx.wm_channel_subscriptions.findOne({ where: { channel_id: channelId } });
                if (existing) {
                    return res.status(409).json({ api_status: 409, error_message: 'Trial not available — channel already has a subscription history' });
                }

                const now = new Date();
                const expires = new Date(now.getTime() + TRIAL_DAYS * 86400000);
                await ctx.wm_channel_subscriptions.create({
                    channel_id: channelId,
                    is_active:  1,
                    plan:       TRIAL_PLAN,
                    started_at: now,
                    expires_at: expires
                });

                return res.json({
                    api_status: 200,
                    expires_at: expires.toISOString(),
                    trial_days: TRIAL_DAYS
                });
            } catch (e) {
                console.error('[ChannelPremium] start-trial error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── POST create-payment ───────────────────────────────────────────────────
    app.post('/api/node/channels/:channel_id/premium/create-payment',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const { plan = 'monthly', provider = 'wayforpay' } = req.body;
            const userId = req.userId;

            try {
                if (!await isChannelOwner(ctx, channelId, userId)) {
                    return res.status(403).json({ api_status: 403, error_message: 'Only channel owner can purchase premium' });
                }

                const amountUAH = calcChannelPrice(plan);
                if (!amountUAH) return res.status(400).json({ api_status: 400, error_message: 'Invalid plan' });

                const orderId = `ch${channelId}_${plan}_${Date.now()}_u${userId}`;

                // Save pending payment
                if (ctx.wm_channel_subscription_payments) {
                    await ctx.wm_channel_subscription_payments.create({
                        channel_id: channelId, owner_user_id: userId,
                        order_id: orderId, provider, plan,
                        amount_uah: amountUAH, status: 'pending'
                    });
                }

                if (provider === 'liqpay') {
                    const liqpay = createLiqpayPayment({ orderId, amountUAH, plan, channelId });
                    return res.json({ api_status: 200, provider: 'liqpay', ...liqpay, order_id: orderId, amount_uah: amountUAH });
                } else if (provider === 'monobank') {
                    const mono = await createMonobankPayment({ orderId, amountUAH, plan, channelId });
                    return res.json({ api_status: 200, provider: 'monobank', invoice_url: mono.invoiceUrl, invoice_id: mono.invoiceId, order_id: orderId, amount_uah: amountUAH });
                } else {
                    const w4p = await createWayforpayPayment({ orderId, amountUAH, plan, channelId });
                    return res.json({ api_status: 200, provider: 'wayforpay', invoice_url: w4p.invoiceUrl, order_id: orderId, amount_uah: amountUAH });
                }
            } catch (e) {
                console.error('[ChannelPremium] create-payment error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // ── Way4Pay webhook (no auth) ────────────────────────────────────────────
    app.post('/api/node/channel-premium/wayforpay-webhook', async (req, res) => {
        try {
            const body       = req.body;
            const orderId    = body.orderReference;
            const reasonCode = body.reasonCode;

            console.log(`[ChannelPremium] W4P webhook: orderId=${orderId} reasonCode=${reasonCode}`);

            if (ctx.wm_channel_subscription_payments) {
                const payment = await ctx.wm_channel_subscription_payments.findOne({
                    where: { order_id: orderId }, raw: true
                });

                if (payment && reasonCode === 1100) {
                    await ctx.wm_channel_subscription_payments.update(
                        { status: 'success', raw_response: JSON.stringify(body) },
                        { where: { order_id: orderId } }
                    );
                    await activateChannelPremium(ctx, payment.channel_id, payment.plan);
                } else if (payment) {
                    await ctx.wm_channel_subscription_payments.update(
                        { status: 'failed', raw_response: JSON.stringify(body) },
                        { where: { order_id: orderId } }
                    );
                }
            }

            const merchantAccount = process.env.WAYFORPAY_MERCHANT_ACCOUNT;
            const secretKey       = process.env.WAYFORPAY_MERCHANT_SECRET;
            if (!secretKey) {
                console.error('[ChannelPremium] WAYFORPAY_MERCHANT_SECRET not set');
                return res.status(500).end();
            }
            const time   = Math.floor(Date.now() / 1000);
            const sigStr = [merchantAccount, orderId, time].join(';');
            const sig    = crypto.createHmac('md5', secretKey).update(sigStr).digest('hex');

            return res.json({ orderReference: orderId, status: 'accept', time, signature: sig });
        } catch (e) {
            console.error('[ChannelPremium] W4P webhook error:', e);
            return res.status(500).end();
        }
    });

    // ── LiqPay webhook (no auth) ─────────────────────────────────────────────
    app.post('/api/node/channel-premium/liqpay-webhook', async (req, res) => {
        try {
            const { data, signature } = req.body;
            const privateKey  = process.env.LIQPAY_PRIVATE_KEY || '';
            const expectedSig = Buffer.from(
                crypto.createHash('sha1').update(privateKey + data + privateKey).digest()
            ).toString('base64');

            if (signature !== expectedSig) {
                console.warn('[ChannelPremium] LiqPay signature mismatch');
                return res.status(400).end();
            }

            const params  = JSON.parse(Buffer.from(data, 'base64').toString());
            const orderId = params.order_id;
            const status  = params.status;

            console.log(`[ChannelPremium] LiqPay webhook: orderId=${orderId} status=${status}`);

            if (ctx.wm_channel_subscription_payments) {
                const payment = await ctx.wm_channel_subscription_payments.findOne({
                    where: { order_id: orderId }, raw: true
                });

                if (payment && (status === 'success' || status === 'sandbox')) {
                    await ctx.wm_channel_subscription_payments.update(
                        { status: 'success', raw_response: JSON.stringify(params) },
                        { where: { order_id: orderId } }
                    );
                    await activateChannelPremium(ctx, payment.channel_id, payment.plan);
                } else if (payment) {
                    await ctx.wm_channel_subscription_payments.update(
                        { status: 'failed', raw_response: JSON.stringify(params) },
                        { where: { order_id: orderId } }
                    );
                }
            }

            return res.status(200).end();
        } catch (e) {
            console.error('[ChannelPremium] LiqPay webhook error:', e);
            return res.status(500).end();
        }
    });

    // ── Monobank webhook (no auth) ───────────────────────────────────────────
    app.post('/api/node/channel-premium/monobank-webhook', async (req, res) => {
        try {
            // Optional HMAC-SHA256 verification
            const secret = process.env.MONOBANK_WEBHOOK_SECRET;
            if (secret) {
                const xSign    = req.headers['x-sign'];
                const rawBody  = req.body ? JSON.stringify(req.body) : '';
                const expected = crypto.createHmac('sha256', secret).update(rawBody).digest('base64');
                if (xSign !== expected) {
                    console.warn('[ChannelPremium] Monobank invalid webhook signature');
                    return res.status(400).json({ status: 'fail', code: 'INVALID_SIGNATURE' });
                }
            }

            const { status, reference } = req.body;
            console.log(`[ChannelPremium] Monobank webhook: reference=${reference} status=${status}`);

            if (status === 'success' && reference) {
                if (ctx.wm_channel_subscription_payments) {
                    const payment = await ctx.wm_channel_subscription_payments.findOne({
                        where: { order_id: reference }, raw: true
                    });
                    if (payment && payment.status !== 'success') {
                        await ctx.wm_channel_subscription_payments.update(
                            { status: 'success', raw_response: JSON.stringify(req.body) },
                            { where: { order_id: reference } }
                        );
                        await activateChannelPremium(ctx, payment.channel_id, payment.plan);
                    } else if (!payment) {
                        console.warn(`[ChannelPremium] Monobank webhook: order ${reference} not found`);
                    }
                }
            }

            return res.status(200).json({ status: 'ok' });
        } catch (e) {
            console.error('[ChannelPremium] Monobank webhook error:', e);
            return res.status(500).end();
        }
    });

    // ─── Customization (premium-only appearance presets) ─────────────────────
    //
    // The client sends short preset ids ("gold", "rose_gold", "aurora_veil",
    // etc.) — never raw hex colors. The server validates every field
    // against a fixed whitelist so a crafted request can't inject
    // arbitrary strings into the UI layer. Unknown or missing ids are
    // simply persisted as NULL and the client falls back to its default.

    const PRESETS = {
        accent_color_id:    ['gold', 'rose_gold', 'emerald', 'sapphire', 'amethyst', 'crimson'],
        banner_pattern_id:  ['none', 'dots', 'diagonal', 'diamond', 'hex', 'aurora_veil'],
        emoji_pack_id:      ['classic', 'celebration', 'nature', 'finance', 'cosmos'],
        font_weight:        ['editorial', 'classic', 'display'],
        avatar_frame:       ['none', 'gold_ring', 'gold_double', 'rose_gold_halo', 'aurora_gradient', 'engraved_notch', 'crystal_edge'],
    };
    const CORNER_RADIUS_MIN = 6;
    const CORNER_RADIUS_MAX = 32;

    function sanitizePreset(field, value) {
        if (value == null) return null;
        const list = PRESETS[field];
        if (!list) return null;
        const v = String(value).toLowerCase();
        return list.includes(v) ? v : null;
    }

    async function isPremiumActive(channelId) {
        if (!ctx.wm_channel_subscriptions) return false;
        const sub = await ctx.wm_channel_subscriptions.findOne({
            where: { channel_id: channelId }, raw: true
        });
        if (!sub || !sub.is_active) return false;
        if (sub.expires_at && new Date(sub.expires_at) <= new Date()) return false;
        return true;
    }

    // GET — public; any authed user can read a channel's appearance so the
    //       feed can render it without a second permission check.
    app.get('/api/node/channels/:channel_id/premium/customization',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            try {
                if (!ctx.wm_channel_premium_customization) {
                    return res.json({ api_status: 200, customization: null });
                }
                const row = await ctx.wm_channel_premium_customization.findOne({
                    where: { channel_id: channelId }, raw: true
                });
                if (!row) return res.json({ api_status: 200, customization: null });
                return res.json({
                    api_status: 200,
                    customization: {
                        accent_color_id:        row.accent_color_id,
                        banner_pattern_id:      row.banner_pattern_id,
                        emoji_pack_id:          row.emoji_pack_id,
                        font_weight:            row.font_weight,
                        post_corner_radius:     row.post_corner_radius,
                        avatar_frame:           row.avatar_frame,
                        posts_backdrop_enabled: !!row.posts_backdrop_enabled
                    }
                });
            } catch (e) {
                console.error('[ChannelPremium] customization GET error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );

    // PUT — owner-only; requires an active premium subscription on the
    //       channel so the API can't be abused to leak gated customization
    //       to non-premium channels.
    app.put('/api/node/channels/:channel_id/premium/customization',
        (req, res, next) => authMiddleware(ctx, req, res, next),
        async (req, res) => {
            const channelId = parseInt(req.params.channel_id);
            const userId = req.userId;
            try {
                if (!await isChannelOwner(ctx, channelId, userId)) {
                    return res.status(403).json({ api_status: 403, error_message: 'Only channel owner can edit appearance' });
                }
                if (!await isPremiumActive(channelId)) {
                    return res.status(402).json({ api_status: 402, error_message: 'Channel premium subscription required' });
                }
                if (!ctx.wm_channel_premium_customization) {
                    return res.status(500).json({ api_status: 500, error_message: 'Customization model not available' });
                }

                const body = req.body || {};
                const cornerRaw = body.post_corner_radius;
                let cornerRadius = null;
                if (cornerRaw != null) {
                    const n = parseInt(cornerRaw, 10);
                    if (Number.isFinite(n) && n >= CORNER_RADIUS_MIN && n <= CORNER_RADIUS_MAX) {
                        cornerRadius = n;
                    }
                }

                const payload = {
                    channel_id:             channelId,
                    accent_color_id:        sanitizePreset('accent_color_id',   body.accent_color_id),
                    banner_pattern_id:      sanitizePreset('banner_pattern_id', body.banner_pattern_id),
                    emoji_pack_id:          sanitizePreset('emoji_pack_id',     body.emoji_pack_id),
                    font_weight:            sanitizePreset('font_weight',       body.font_weight),
                    post_corner_radius:     cornerRadius,
                    avatar_frame:           sanitizePreset('avatar_frame',      body.avatar_frame),
                    posts_backdrop_enabled: body.posts_backdrop_enabled ? 1 : 0,
                    updated_at:             Math.floor(Date.now() / 1000)
                };

                const existing = await ctx.wm_channel_premium_customization.findOne({
                    where: { channel_id: channelId }
                });
                if (existing) {
                    await ctx.wm_channel_premium_customization.update(payload, { where: { channel_id: channelId } });
                } else {
                    await ctx.wm_channel_premium_customization.create(payload);
                }

                return res.json({
                    api_status: 200,
                    customization: {
                        accent_color_id:        payload.accent_color_id,
                        banner_pattern_id:      payload.banner_pattern_id,
                        emoji_pack_id:          payload.emoji_pack_id,
                        font_weight:            payload.font_weight,
                        post_corner_radius:     payload.post_corner_radius,
                        avatar_frame:           payload.avatar_frame,
                        posts_backdrop_enabled: !!payload.posts_backdrop_enabled
                    }
                });
            } catch (e) {
                console.error('[ChannelPremium] customization PUT error:', e);
                return res.status(500).json({ api_status: 500, error_message: e.message });
            }
        }
    );
};
