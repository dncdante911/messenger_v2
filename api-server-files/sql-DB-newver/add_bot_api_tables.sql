-- ============================================================
-- WorldMates Bot API - Database Schema Migration
-- Version: 1.0.0
-- Date: 2026-02-11
-- Description: Creates all tables needed for Bot API functionality
-- ============================================================

-- ==================== BOTS TABLE ====================
-- Main table for bot accounts
CREATE TABLE IF NOT EXISTS `Wo_Bots` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL COMMENT 'Unique bot identifier (e.g., bot_abc123)',
  `owner_id` int(11) NOT NULL COMMENT 'User ID of bot creator',
  `bot_token` varchar(128) NOT NULL COMMENT 'API token for bot authentication',
  `username` varchar(64) NOT NULL COMMENT 'Bot username (unique, e.g., @weather_bot)',
  `display_name` varchar(128) NOT NULL COMMENT 'Bot display name',
  `avatar` varchar(512) DEFAULT NULL COMMENT 'Bot avatar URL',
  `description` text DEFAULT NULL COMMENT 'Bot description',
  `about` text DEFAULT NULL COMMENT 'Short about text shown in profile',
  `bot_type` enum('standard','system','verified') NOT NULL DEFAULT 'standard',
  `status` enum('active','disabled','suspended','pending_review') NOT NULL DEFAULT 'active',
  `is_public` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Whether bot appears in bot store',
  `is_inline` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Supports inline queries',
  `can_join_groups` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Can be added to groups',
  `can_read_all_group_messages` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Privacy mode off = reads all messages',
  `supports_commands` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Has command menu',
  `category` varchar(64) DEFAULT NULL COMMENT 'Bot category (news, weather, tools, etc)',
  `tags` varchar(512) DEFAULT NULL COMMENT 'Comma-separated tags for search',
  `webhook_url` varchar(512) DEFAULT NULL COMMENT 'Webhook URL for receiving updates',
  `webhook_secret` varchar(128) DEFAULT NULL COMMENT 'Secret for webhook signature verification',
  `webhook_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `webhook_max_connections` int(11) NOT NULL DEFAULT 40,
  `webhook_allowed_updates` text DEFAULT NULL COMMENT 'JSON array of allowed update types',
  `rate_limit_per_second` int(11) NOT NULL DEFAULT 30 COMMENT 'Max API calls per second',
  `rate_limit_per_minute` int(11) NOT NULL DEFAULT 1500 COMMENT 'Max API calls per minute',
  `messages_sent` bigint(20) NOT NULL DEFAULT 0 COMMENT 'Total messages sent counter',
  `messages_received` bigint(20) NOT NULL DEFAULT 0 COMMENT 'Total messages received counter',
  `total_users` int(11) NOT NULL DEFAULT 0 COMMENT 'Total unique users interacted with',
  `active_users_24h` int(11) NOT NULL DEFAULT 0 COMMENT 'Active users in last 24h',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_active_at` datetime DEFAULT NULL COMMENT 'Last time bot processed a message',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_id` (`bot_id`),
  UNIQUE KEY `idx_bot_token` (`bot_token`),
  UNIQUE KEY `idx_bot_username` (`username`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT COMMANDS TABLE ====================
-- Registered bot commands (slash commands like /start, /help)
CREATE TABLE IF NOT EXISTS `Wo_Bot_Commands` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `command` varchar(64) NOT NULL COMMENT 'Command without slash (e.g., start, help)',
  `description` varchar(256) NOT NULL COMMENT 'Command description',
  `usage_hint` varchar(256) DEFAULT NULL COMMENT 'Usage example (e.g., /weather <city>)',
  `is_hidden` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Hidden from command menu',
  `scope` enum('all','private','group','admin') NOT NULL DEFAULT 'all',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_command` (`bot_id`, `command`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT MESSAGES TABLE ====================
-- Messages sent/received by bots (extends Wo_Messages)
CREATE TABLE IF NOT EXISTS `Wo_Bot_Messages` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL COMMENT 'User ID or Group ID',
  `chat_type` enum('private','group') NOT NULL DEFAULT 'private',
  `direction` enum('incoming','outgoing') NOT NULL,
  `message_id` bigint(20) DEFAULT NULL COMMENT 'Reference to Wo_Messages.id if applicable',
  `text` text DEFAULT NULL,
  `media_type` varchar(32) DEFAULT NULL COMMENT 'image, video, audio, file, sticker, location',
  `media_url` varchar(512) DEFAULT NULL,
  `reply_to_message_id` bigint(20) DEFAULT NULL,
  `reply_markup` text DEFAULT NULL COMMENT 'JSON inline keyboard or reply keyboard',
  `callback_data` varchar(256) DEFAULT NULL COMMENT 'Callback data from inline buttons',
  `entities` text DEFAULT NULL COMMENT 'JSON array of message entities (bold, links, etc)',
  `is_command` tinyint(1) NOT NULL DEFAULT 0,
  `command_name` varchar(64) DEFAULT NULL,
  `command_args` text DEFAULT NULL,
  `processed` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether bot has processed this message',
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_chat` (`bot_id`, `chat_id`),
  KEY `idx_direction` (`direction`),
  KEY `idx_processed` (`processed`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT WEBHOOKS TABLE ====================
-- Webhook delivery log
CREATE TABLE IF NOT EXISTS `Wo_Bot_Webhook_Log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL COMMENT 'message, callback_query, command, member_joined, etc',
  `payload` text NOT NULL COMMENT 'JSON payload sent to webhook',
  `webhook_url` varchar(512) NOT NULL,
  `response_code` int(11) DEFAULT NULL,
  `response_body` text DEFAULT NULL,
  `delivery_status` enum('pending','delivered','failed','retrying') NOT NULL DEFAULT 'pending',
  `attempts` int(11) NOT NULL DEFAULT 0,
  `max_attempts` int(11) NOT NULL DEFAULT 5,
  `next_retry_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_status` (`delivery_status`),
  KEY `idx_next_retry` (`next_retry_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT USERS TABLE ====================
-- Users who interact with bots (bot's user list)
CREATE TABLE IF NOT EXISTS `Wo_Bot_Users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `state` varchar(128) DEFAULT NULL COMMENT 'Current conversation state (FSM)',
  `state_data` text DEFAULT NULL COMMENT 'JSON state data for conversation flow',
  `is_blocked` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'User blocked the bot',
  `is_banned` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Bot banned the user',
  `first_interaction_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_interaction_at` datetime DEFAULT NULL,
  `messages_count` int(11) NOT NULL DEFAULT 0,
  `custom_data` text DEFAULT NULL COMMENT 'JSON - bot-specific user data',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_user` (`bot_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_last_interaction` (`last_interaction_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT KEYBOARDS TABLE ====================
-- Reusable keyboard templates
CREATE TABLE IF NOT EXISTS `Wo_Bot_Keyboards` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT 'Keyboard template name',
  `keyboard_type` enum('inline','reply','remove') NOT NULL DEFAULT 'inline',
  `keyboard_data` text NOT NULL COMMENT 'JSON keyboard layout',
  `is_persistent` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT CALLBACK QUERIES TABLE ====================
-- Inline button click tracking
CREATE TABLE IF NOT EXISTS `Wo_Bot_Callbacks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `message_id` bigint(20) DEFAULT NULL,
  `callback_data` varchar(256) NOT NULL,
  `answered` tinyint(1) NOT NULL DEFAULT 0,
  `answer_text` varchar(256) DEFAULT NULL,
  `answer_show_alert` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT POLLS TABLE ====================
-- Polls created by bots
CREATE TABLE IF NOT EXISTS `Wo_Bot_Polls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL,
  `question` varchar(512) NOT NULL,
  `poll_type` enum('regular','quiz') NOT NULL DEFAULT 'regular',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT 1,
  `allows_multiple_answers` tinyint(1) NOT NULL DEFAULT 0,
  `correct_option_id` int(11) DEFAULT NULL COMMENT 'For quiz type',
  `explanation` text DEFAULT NULL COMMENT 'For quiz type - explanation after answer',
  `is_closed` tinyint(1) NOT NULL DEFAULT 0,
  `close_date` datetime DEFAULT NULL COMMENT 'Auto-close datetime',
  `total_voters` int(11) NOT NULL DEFAULT 0,
  `message_id` bigint(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `closed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_is_closed` (`is_closed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT POLL OPTIONS TABLE ====================
CREATE TABLE IF NOT EXISTS `Wo_Bot_Poll_Options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_text` varchar(256) NOT NULL,
  `option_index` int(11) NOT NULL DEFAULT 0,
  `voter_count` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_poll_id` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT POLL VOTES TABLE ====================
CREATE TABLE IF NOT EXISTS `Wo_Bot_Poll_Votes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_unique_vote` (`poll_id`, `user_id`, `option_id`),
  KEY `idx_poll_id` (`poll_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT TASKS TABLE ====================
-- For task tracker bot
CREATE TABLE IF NOT EXISTS `Wo_Bot_Tasks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `chat_id` varchar(64) NOT NULL,
  `title` varchar(512) NOT NULL,
  `description` text DEFAULT NULL,
  `status` enum('todo','in_progress','done','cancelled') NOT NULL DEFAULT 'todo',
  `priority` enum('low','medium','high','urgent') NOT NULL DEFAULT 'medium',
  `due_date` datetime DEFAULT NULL,
  `assigned_to` int(11) DEFAULT NULL COMMENT 'User ID assigned to task',
  `reminder_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_user` (`bot_id`, `user_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_reminder` (`reminder_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT RSS FEEDS TABLE ====================
-- For RSS news bot
CREATE TABLE IF NOT EXISTS `Wo_Bot_RSS_Feeds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL COMMENT 'Chat/channel where to post',
  `feed_url` varchar(512) NOT NULL,
  `feed_name` varchar(256) DEFAULT NULL,
  `feed_language` varchar(10) DEFAULT 'en',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `check_interval_minutes` int(11) NOT NULL DEFAULT 30,
  `last_check_at` datetime DEFAULT NULL,
  `last_item_hash` varchar(64) DEFAULT NULL COMMENT 'Hash of last posted item to avoid duplicates',
  `items_posted` int(11) NOT NULL DEFAULT 0,
  `max_items_per_check` int(11) NOT NULL DEFAULT 5,
  `include_image` tinyint(1) NOT NULL DEFAULT 1,
  `include_description` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_chat` (`bot_id`, `chat_id`, `feed_url`(255)),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_last_check` (`last_check_at`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT RSS ITEMS TABLE ====================
-- Posted RSS items (deduplication)
CREATE TABLE IF NOT EXISTS `Wo_Bot_RSS_Items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `feed_id` int(11) NOT NULL,
  `item_hash` varchar(64) NOT NULL COMMENT 'MD5 hash of link+title',
  `title` varchar(512) DEFAULT NULL,
  `link` varchar(512) DEFAULT NULL,
  `posted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_hash` (`feed_id`, `item_hash`),
  KEY `idx_feed_id` (`feed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT RATE LIMITS TABLE ====================
CREATE TABLE IF NOT EXISTS `Wo_Bot_Rate_Limits` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `endpoint` varchar(128) NOT NULL,
  `requests_count` int(11) NOT NULL DEFAULT 0,
  `window_start` datetime NOT NULL,
  `window_type` enum('second','minute','hour') NOT NULL DEFAULT 'minute',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_endpoint_window` (`bot_id`, `endpoint`, `window_start`, `window_type`),
  KEY `idx_window_start` (`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== BOT API KEYS TABLE ====================
-- Public API keys for third-party developers
CREATE TABLE IF NOT EXISTS `Wo_Bot_Api_Keys` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT 'Developer user ID',
  `api_key` varchar(128) NOT NULL,
  `api_secret` varchar(128) NOT NULL,
  `app_name` varchar(256) NOT NULL COMMENT 'Developer app name',
  `description` text DEFAULT NULL,
  `permissions` text DEFAULT NULL COMMENT 'JSON array of allowed scopes',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `rate_limit_per_minute` int(11) NOT NULL DEFAULT 60,
  `total_requests` bigint(20) NOT NULL DEFAULT 0,
  `last_used_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_api_key` (`api_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== INDEXES FOR PERFORMANCE ====================
-- Additional composite indexes for common queries

-- Bot messages: get unprocessed messages for a bot
CREATE INDEX `idx_bot_unprocessed` ON `Wo_Bot_Messages` (`bot_id`, `processed`, `created_at`);

-- Bot users: active users query
CREATE INDEX `idx_bot_active_users` ON `Wo_Bot_Users` (`bot_id`, `is_blocked`, `last_interaction_at`);

-- Webhook log: cleanup old entries
CREATE INDEX `idx_webhook_cleanup` ON `Wo_Bot_Webhook_Log` (`created_at`, `delivery_status`);
