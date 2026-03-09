-- Channel Livestreams
-- Tracks live broadcast sessions started from channels.
-- Quality limits:  regular channels → 240p–720p
--                  premium channels  → 360p–1080p @ 60 fps

CREATE TABLE IF NOT EXISTS `wm_channel_livestreams` (
    `id`              INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    `channel_id`      INT(11) NOT NULL COMMENT 'wo_pages.page_id',
    `host_user_id`    INT(11) NOT NULL COMMENT 'who started the stream',
    `room_name`       VARCHAR(120) NOT NULL UNIQUE,
    `title`           VARCHAR(255) DEFAULT NULL,
    `quality`         VARCHAR(10) NOT NULL DEFAULT '720p' COMMENT '240p|360p|480p|720p|1080p|1080p60',
    `is_premium`      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 = channel has premium',
    `status`          ENUM('live','ended') NOT NULL DEFAULT 'live',
    `viewer_count`    INT(11) NOT NULL DEFAULT 0,
    `peak_viewers`    INT(11) NOT NULL DEFAULT 0,
    `started_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ended_at`        DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_channel_id`   (`channel_id`),
    KEY `idx_host_user_id` (`host_user_id`),
    KEY `idx_status`       (`status`),
    KEY `idx_started_at`   (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wm_channel_livestream_viewers` (
    `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    `stream_id`    INT(11) UNSIGNED NOT NULL,
    `user_id`      INT(11) NOT NULL,
    `joined_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `left_at`      DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_stream_id` (`stream_id`),
    KEY `idx_user_id`   (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Channel Premium Subscriptions
-- Separate from user (wo_users.is_pro) premium — this is for channel pages.

CREATE TABLE IF NOT EXISTS `wm_channel_subscriptions` (
    `id`           INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    `channel_id`   INT(11) NOT NULL UNIQUE COMMENT 'wo_pages.page_id',
    `plan`         ENUM('monthly','quarterly','annual') NOT NULL DEFAULT 'monthly',
    `is_active`    TINYINT(1) NOT NULL DEFAULT 0,
    `started_at`   DATETIME DEFAULT NULL,
    `expires_at`   DATETIME DEFAULT NULL,
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_channel_id` (`channel_id`),
    KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wm_channel_subscription_payments` (
    `id`            INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    `channel_id`    INT(11) NOT NULL,
    `owner_user_id` INT(11) NOT NULL,
    `order_id`      VARCHAR(64) NOT NULL UNIQUE,
    `provider`      ENUM('wayforpay','liqpay') NOT NULL,
    `plan`          ENUM('monthly','quarterly','annual') NOT NULL,
    `amount_uah`    DECIMAL(10,2) NOT NULL,
    `status`        ENUM('pending','success','failed','refunded') NOT NULL DEFAULT 'pending',
    `raw_response`  TEXT DEFAULT NULL,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_channel_id`    (`channel_id`),
    KEY `idx_owner_user_id` (`owner_user_id`),
    KEY `idx_status`        (`status`),
    KEY `idx_created_at`    (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
