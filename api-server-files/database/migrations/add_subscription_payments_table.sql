-- Migration: WorldMates PRO subscription payments table
-- Run once on the production DB

-- 1. Payments log table
CREATE TABLE IF NOT EXISTS `wm_subscription_payments` (
  `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      INT(11) UNSIGNED NOT NULL,
  `order_id`     VARCHAR(64)      NOT NULL,
  `provider`     ENUM('wayforpay','liqpay') NOT NULL,
  `months`       TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `amount_uah`   DECIMAL(10,2)   NOT NULL,
  `status`       ENUM('pending','success','failed') NOT NULL DEFAULT 'pending',
  `raw_response` TEXT            DEFAULT NULL,
  `created_at`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id`  (`user_id`),
  KEY `idx_status`   (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Ensure wo_users has the columns we use
--    (pro_time already exists as INT - Unix timestamp of expiry)
--    is_pro  ENUM('0','1') — already exists
--    pro_type INT          — already exists (repurposed: stores months of plan)
--    Nothing new needed for wo_users.

-- 3. Index for fast expiry checks (optional but recommended)
ALTER TABLE `wo_users`
  ADD INDEX IF NOT EXISTS `idx_pro_time` (`pro_time`);
