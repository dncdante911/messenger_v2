-- Migration: Add Wo_Channel_Bans table
-- Run this SQL on the socialhub database before deploying the ban feature.

CREATE TABLE IF NOT EXISTS `Wo_Channel_Bans` (
    `id`         int(11)      NOT NULL AUTO_INCREMENT,
    `channel_id` int(11)      NOT NULL DEFAULT 0,
    `user_id`    int(11)      NOT NULL DEFAULT 0,
    `banned_by`  int(11)      NOT NULL DEFAULT 0,
    `reason`     varchar(500) NOT NULL DEFAULT '',
    `ban_time`   int(11)      NOT NULL DEFAULT 0,
    `expire_time` int(11)     NOT NULL DEFAULT 0,  -- 0 = permanent
    PRIMARY KEY (`id`),
    UNIQUE KEY `channel_user_unique` (`channel_id`, `user_id`),
    KEY `idx_channel_id` (`channel_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
