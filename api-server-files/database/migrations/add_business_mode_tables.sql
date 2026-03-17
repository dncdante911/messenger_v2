-- ─────────────────────────────────────────────────────────────────────────────
-- Migration: Business Mode tables
-- Tables:
--   wm_business_profile    — main business profile per user
--   wm_business_hours      — working hours per weekday
--   wm_business_quick_replies — saved quick-reply templates
--   wm_business_links      — business deep-links with pre-filled text
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Business Profile ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `wm_business_profile` (
  `id`                    INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`               INT(11) NOT NULL,

  -- General info
  `business_name`         VARCHAR(100)  DEFAULT NULL,
  `category`              VARCHAR(100)  DEFAULT NULL,
  `description`           TEXT          DEFAULT NULL,
  `address`               VARCHAR(255)  DEFAULT NULL,
  `lat`                   DECIMAL(10,7) DEFAULT NULL,
  `lng`                   DECIMAL(10,7) DEFAULT NULL,
  `phone`                 VARCHAR(30)   DEFAULT NULL,
  `email`                 VARCHAR(100)  DEFAULT NULL,
  `website`               VARCHAR(255)  DEFAULT NULL,

  -- Auto-reply (away message)
  `auto_reply_enabled`    TINYINT(1)    NOT NULL DEFAULT 0,
  `auto_reply_text`       TEXT          DEFAULT NULL,
  `auto_reply_mode`       ENUM('always','outside_hours','away') NOT NULL DEFAULT 'always',

  -- Greeting message (for new contacts opening chat for the first time)
  `greeting_enabled`      TINYINT(1)    NOT NULL DEFAULT 0,
  `greeting_text`         TEXT          DEFAULT NULL,

  -- Away message (sent when marking yourself as away)
  `away_enabled`          TINYINT(1)    NOT NULL DEFAULT 0,
  `away_text`             TEXT          DEFAULT NULL,

  -- Business badge visible on profile
  `badge_enabled`         TINYINT(1)    NOT NULL DEFAULT 1,

  `created_at`            INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `updated_at`            INT(11) UNSIGNED NOT NULL DEFAULT 0,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ── Business Hours (per weekday) ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `wm_business_hours` (
  `id`          INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     INT(11) NOT NULL,
  `weekday`     TINYINT(1) NOT NULL COMMENT '0=Sun, 1=Mon … 6=Sat',
  `is_open`     TINYINT(1) NOT NULL DEFAULT 1,
  `open_time`   VARCHAR(5) NOT NULL DEFAULT '09:00' COMMENT 'HH:MM',
  `close_time`  VARCHAR(5) NOT NULL DEFAULT '18:00' COMMENT 'HH:MM',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_weekday` (`user_id`, `weekday`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ── Quick Replies ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `wm_business_quick_replies` (
  `id`          INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     INT(11) NOT NULL,
  `shortcut`    VARCHAR(32)  NOT NULL COMMENT 'trigger keyword (without /)',
  `text`        TEXT         NOT NULL,
  `media_url`   VARCHAR(255) DEFAULT NULL,
  `created_at`  INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `updated_at`  INT(11) UNSIGNED NOT NULL DEFAULT 0,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_shortcut` (`user_id`, `shortcut`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ── Business Links (deep-links with pre-filled message) ──────────────────────
CREATE TABLE IF NOT EXISTS `wm_business_links` (
  `id`              INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         INT(11) NOT NULL,
  `title`           VARCHAR(100) NOT NULL,
  `prefilled_text`  TEXT         DEFAULT NULL,
  `slug`            VARCHAR(64)  NOT NULL COMMENT 'unique URL slug',
  `views`           INT(11) UNSIGNED NOT NULL DEFAULT 0,
  `created_at`      INT(11) UNSIGNED NOT NULL DEFAULT 0,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_slug` (`slug`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
