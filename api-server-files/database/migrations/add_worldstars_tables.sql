-- Migration: WorldStars internal currency tables
-- Run once on the production DB

-- 1. Stars balance per user (upsert-friendly)
CREATE TABLE IF NOT EXISTS `wm_stars_balance` (
  `user_id`         INT(11) UNSIGNED NOT NULL,
  `balance`         INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `total_purchased` INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `total_sent`      INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `total_received`  INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `updated_at`      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Full transaction log
CREATE TABLE IF NOT EXISTS `wm_stars_transactions` (
  `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `from_user_id` INT(11) UNSIGNED          DEFAULT NULL,   -- NULL = system (purchase/refund)
  `to_user_id`   INT(11) UNSIGNED NOT NULL,
  `amount`       INT(11) UNSIGNED NOT NULL,
  `type`         ENUM('purchase','send','receive','refund') NOT NULL,
  `ref_type`     VARCHAR(32)               DEFAULT NULL,   -- 'user','bot','channel','pack'
  `ref_id`       INT(11) UNSIGNED          DEFAULT NULL,
  `note`         VARCHAR(255)              DEFAULT NULL,   -- optional message from sender
  `order_id`     VARCHAR(128)              DEFAULT NULL,   -- for purchase: payment order ID
  `created_at`   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_to_user`   (`to_user_id`, `created_at`),
  KEY `idx_from_user` (`from_user_id`, `created_at`),
  KEY `idx_order_id`  (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
