-- Migration 003: Create wm_scheduled_messages table
-- Stores scheduled messages for groups, channels, and DMs.
-- The background scheduler in routes/scheduled.js fires these automatically.

CREATE TABLE IF NOT EXISTS `wm_scheduled_messages` (
    `id`             INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        INT UNSIGNED NOT NULL,
    `chat_id`        INT UNSIGNED NOT NULL,
    `chat_type`      ENUM('dm','group','channel') NOT NULL DEFAULT 'group',
    `text`           TEXT,
    `media_url`      VARCHAR(500) DEFAULT NULL,
    `media_type`     VARCHAR(50)  DEFAULT NULL,
    `scheduled_at`   INT UNSIGNED NOT NULL,
    `repeat_type`    ENUM('none','daily','weekly','monthly') NOT NULL DEFAULT 'none',
    `is_pinned`      TINYINT(1) NOT NULL DEFAULT 0,
    `notify_members` TINYINT(1) NOT NULL DEFAULT 1,
    `status`         ENUM('pending','sent','failed','cancelled') NOT NULL DEFAULT 'pending',
    `created_at`     INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_chat`    (`chat_type`, `chat_id`),
    KEY `idx_pending` (`status`, `scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
