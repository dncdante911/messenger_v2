-- ─── wm_subscription_orders ─────────────────────────────────────────────────
-- Idempotency table for webhook deduplication.
-- Referenced in routes/subscription.js for Way4Pay, LiqPay, and Monobank webhooks.
-- Without this table, webhooks crash at runtime and subscriptions are never activated.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `wm_subscription_orders` (
    `id`           INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    `order_id`     VARCHAR(64)      NOT NULL                       COMMENT 'Unique payment reference from provider',
    `user_id`      INT              NOT NULL,
    `provider`     VARCHAR(20)      NOT NULL                       COMMENT 'wayforpay | liqpay | monobank',
    `months`       TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `amount_uah`   DECIMAL(10,2)    NOT NULL DEFAULT 0.00,
    `processed_at` TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_order_id` (`order_id`),
    KEY `idx_user_id`  (`user_id`),
    KEY `idx_provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
