-- ─── Business chat separation ────────────────────────────────────────────────
-- Separates business chat threads from personal chat threads.
-- Business messages are stored in Wo_Messages with is_business_chat=1,
-- and conversation metadata is tracked in wm_business_chats (separate from wo_userschat).

-- 1. Add is_business_chat column to Wo_Messages
ALTER TABLE `Wo_Messages`
    ADD COLUMN `is_business_chat` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1 = business chat thread, 0 = personal chat thread'
    AFTER `page_id`;

-- Index for filtering business vs personal threads
ALTER TABLE `Wo_Messages`
    ADD INDEX `idx_biz_chat` (`from_id`, `to_id`, `is_business_chat`);

-- 2. Create wm_business_chats table (mirrors wo_userschat for business threads)
CREATE TABLE IF NOT EXISTS `wm_business_chats` (
    `id`                INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`           INT UNSIGNED NOT NULL COMMENT 'Customer user_id',
    `business_user_id`  INT UNSIGNED NOT NULL COMMENT 'Business owner user_id',
    `last_message_id`   INT UNSIGNED NOT NULL DEFAULT 0,
    `last_time`         INT UNSIGNED NOT NULL DEFAULT 0,
    `unread_count`      SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_biz_conv` (`user_id`, `business_user_id`),
    KEY `idx_user_time` (`user_id`, `last_time`),
    KEY `idx_biz_user_time` (`business_user_id`, `last_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Business chat conversation metadata (separated from personal chats)';
