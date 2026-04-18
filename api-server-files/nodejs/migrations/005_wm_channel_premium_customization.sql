-- Migration 005: Create wm_channel_premium_customization table
-- Stores the per-channel appearance preset chosen by an owner whose
-- channel has an active premium subscription. All values are short
-- string ids (or small ints) validated against a fixed preset list on
-- the server; raw hex colors are never accepted.

CREATE TABLE IF NOT EXISTS `wm_channel_premium_customization` (
    `channel_id`            INT UNSIGNED NOT NULL,
    `accent_color_id`       VARCHAR(40)  DEFAULT NULL,
    `banner_pattern_id`     VARCHAR(40)  DEFAULT NULL,
    `emoji_pack_id`         VARCHAR(40)  DEFAULT NULL,
    `font_weight`           VARCHAR(40)  DEFAULT NULL,
    `post_corner_radius`    TINYINT UNSIGNED DEFAULT NULL,
    `avatar_frame`          VARCHAR(40)  DEFAULT NULL,
    `posts_backdrop_enabled` TINYINT(1)  NOT NULL DEFAULT 0,
    `updated_at`            INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
