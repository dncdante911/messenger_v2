-- ============================================
-- Migration: Group Admin Panel Tables & Columns
-- WorldMates Messenger
-- ============================================

-- 1. Add missing columns to Wo_GroupChat table
-- These columns are used by the admin panel in group_chat_v2.php

ALTER TABLE Wo_GroupChat
    ADD COLUMN IF NOT EXISTS `pinned_message_id` INT(11) DEFAULT NULL AFTER `type`,
    ADD COLUMN IF NOT EXISTS `qr_code` VARCHAR(255) DEFAULT NULL AFTER `pinned_message_id`,
    ADD COLUMN IF NOT EXISTS `is_private` TINYINT(1) DEFAULT 0 AFTER `qr_code`,
    ADD COLUMN IF NOT EXISTS `description` TEXT DEFAULT NULL AFTER `group_name`,
    ADD COLUMN IF NOT EXISTS `slow_mode_seconds` INT(11) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `history_visible_for_new_members` TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `anti_spam_enabled` TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `max_messages_per_minute` INT(11) DEFAULT 20,
    ADD COLUMN IF NOT EXISTS `allow_members_send_media` TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `allow_members_send_links` TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `allow_members_send_stickers` TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `allow_members_invite` TINYINT(1) DEFAULT 0;

-- 2. Add missing columns to Wo_GroupChatUsers table
ALTER TABLE Wo_GroupChatUsers
    ADD COLUMN IF NOT EXISTS `role` VARCHAR(20) DEFAULT 'member' AFTER `user_id`,
    ADD COLUMN IF NOT EXISTS `active` TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `last_seen` INT(11) DEFAULT 0;

-- 3. Add missing columns to Wo_Messages for encryption support
ALTER TABLE Wo_Messages
    ADD COLUMN IF NOT EXISTS `iv` VARCHAR(255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `tag` VARCHAR(255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `cipher_version` INT(11) DEFAULT NULL;

-- 4. Create Wo_GroupJoinRequests table (for private group join requests)
CREATE TABLE IF NOT EXISTS `Wo_GroupJoinRequests` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `group_id` INT(11) NOT NULL,
    `user_id` INT(11) NOT NULL,
    `message` TEXT DEFAULT NULL,
    `status` ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
    `created_time` INT(11) NOT NULL,
    `reviewed_by` INT(11) DEFAULT NULL,
    `reviewed_time` INT(11) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_group_status` (`group_id`, `status`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 5. Create Wo_ScheduledPosts table (for scheduled group posts)
CREATE TABLE IF NOT EXISTS `Wo_ScheduledPosts` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `group_id` INT(11) NOT NULL,
    `author_id` INT(11) NOT NULL,
    `text` TEXT NOT NULL,
    `media_url` VARCHAR(500) DEFAULT NULL,
    `scheduled_time` INT(11) NOT NULL,
    `created_time` INT(11) NOT NULL,
    `status` ENUM('scheduled', 'published', 'cancelled') DEFAULT 'scheduled',
    `repeat_type` VARCHAR(20) DEFAULT 'none',
    `is_pinned` TINYINT(1) DEFAULT 0,
    `notify_members` TINYINT(1) DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `idx_group_status` (`group_id`, `status`),
    KEY `idx_scheduled_time` (`scheduled_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 6. Create Wo_Subgroups table (topics/subgroups within a group)
CREATE TABLE IF NOT EXISTS `Wo_Subgroups` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `parent_group_id` INT(11) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `color` VARCHAR(20) DEFAULT '#2196F3',
    `is_private` TINYINT(1) DEFAULT 0,
    `is_closed` TINYINT(1) DEFAULT 0,
    `created_by` INT(11) NOT NULL,
    `created_time` INT(11) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_parent_group` (`parent_group_id`),
    KEY `idx_parent_open` (`parent_group_id`, `is_closed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 7. Create Wo_MessageComments table (for channel post comments)
CREATE TABLE IF NOT EXISTS `Wo_MessageComments` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `message_id` INT(11) NOT NULL,
    `user_id` INT(11) NOT NULL,
    `text` TEXT NOT NULL,
    `time` INT(11) NOT NULL,
    `reply_to_comment_id` INT(11) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_message` (`message_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
