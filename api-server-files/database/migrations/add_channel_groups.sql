-- ============================================================
-- Migration: Channel-linked private groups (sub-groups)
--
-- Allows a private channel (with active premium subscription)
-- to attach up to 5 private groups. Subscribers of the channel
-- automatically gain/lose membership in those groups.
-- ============================================================

CREATE TABLE IF NOT EXISTS `wm_channel_groups` (
    `id`           INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    `channel_id`   INT             NOT NULL  COMMENT 'FK → Wo_Pages.page_id',
    `group_id`     INT             NOT NULL  COMMENT 'FK → Wo_GroupChat.group_id',
    `sort_order`   TINYINT         NOT NULL  DEFAULT 0 COMMENT 'Display order (0-4)',
    `created_at`   TIMESTAMP       NOT NULL  DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE  KEY `uq_channel_group`  (`channel_id`, `group_id`),
    KEY          `idx_channel_id`   (`channel_id`),
    KEY          `idx_group_id`     (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Links private premium channels to their attached sub-groups (max 5 per channel)';
