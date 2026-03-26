-- Migration: Business / Creator Account — extended features
-- Run once on the production DB

-- 1. Add bio_link + verification status to existing business profile table
ALTER TABLE `wm_business_profile`
  ADD COLUMN IF NOT EXISTS `bio_link`            VARCHAR(512)  DEFAULT NULL         AFTER `website`,
  ADD COLUMN IF NOT EXISTS `verification_status` ENUM('none','pending','approved','rejected')
                                                  NOT NULL DEFAULT 'none'            AFTER `badge_enabled`,
  ADD COLUMN IF NOT EXISTS `verification_note`   VARCHAR(512)  DEFAULT NULL         AFTER `verification_status`;

-- 2. Add is_verified flag to wo_users for fast display
ALTER TABLE `wo_users`
  ADD COLUMN IF NOT EXISTS `is_creator_verified` TINYINT(1) NOT NULL DEFAULT 0    AFTER `verified`;

-- 3. Business statistics (daily buckets, auto-upsert on each event)
CREATE TABLE IF NOT EXISTS `wm_business_stats` (
  `user_id`           INT(11) UNSIGNED NOT NULL,
  `date`              DATE             NOT NULL,
  `profile_views`     INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `messages_received` INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `link_clicks`       INT(11) UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `date`),
  KEY `idx_user_date` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Business API keys (for personal data API access)
CREATE TABLE IF NOT EXISTS `wm_business_api_keys` (
  `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      INT(11) UNSIGNED NOT NULL,
  `api_key`      VARCHAR(64)      NOT NULL,
  `label`        VARCHAR(128)     DEFAULT 'My API Key',
  `last_used_at` TIMESTAMP        DEFAULT NULL,
  `created_at`   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key`  (`api_key`),
  UNIQUE KEY `uk_user_key` (`user_id`),   -- one key per user
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Channel scheduled posts
CREATE TABLE IF NOT EXISTS `wm_channel_scheduled_posts` (
  `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_id`   INT(11) UNSIGNED NOT NULL,
  `author_id`    INT(11) UNSIGNED NOT NULL,
  `text`         TEXT             DEFAULT NULL,
  `media_url`    VARCHAR(512)     DEFAULT NULL,
  `media_type`   VARCHAR(32)      DEFAULT NULL,
  `scheduled_at` DATETIME         NOT NULL,
  `is_pinned`    TINYINT(1)       NOT NULL DEFAULT 0,
  `status`       ENUM('pending','published','cancelled') NOT NULL DEFAULT 'pending',
  `published_post_id` INT(11) UNSIGNED DEFAULT NULL,
  `created_at`   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_channel_status` (`channel_id`, `status`, `scheduled_at`),
  KEY `idx_pending_time`   (`status`, `scheduled_at`)  -- for cron job
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
