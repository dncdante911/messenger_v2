-- ============================================================
-- WorldMates Bot API — Полная миграция (один файл, без SOURCE)
-- Совместимо с phpMyAdmin и любым SQL-редактором
-- Безопасно запускать повторно (IF NOT EXISTS / INSERT IGNORE)
-- ============================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;

-- ============================================================
-- ШАГ 1: Создание всех таблиц бот-системы
-- ============================================================

-- ==================== Wo_Bots ====================
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
  `linked_user_id` int(11) DEFAULT NULL COMMENT 'Соответствующий user_id в Wo_Users (для поиска и DM)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_id` (`bot_id`),
  UNIQUE KEY `idx_bot_token` (`bot_token`),
  UNIQUE KEY `idx_bot_username` (`username`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== Wo_Bot_Commands ====================
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

-- ==================== Wo_Bot_Messages ====================
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
  KEY `idx_message_id` (`message_id`),
  KEY `idx_bot_unprocessed` (`bot_id`, `processed`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== Wo_Bot_Webhook_Log ====================
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
  KEY `idx_created_at` (`created_at`),
  KEY `idx_webhook_cleanup` (`created_at`, `delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== Wo_Bot_Users ====================
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
  KEY `idx_last_interaction` (`last_interaction_at`),
  KEY `idx_bot_active_users` (`bot_id`, `is_blocked`, `last_interaction_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== Wo_Bot_Keyboards ====================
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

-- ==================== Wo_Bot_Callbacks ====================
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

-- ==================== Wo_Bot_Polls ====================
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

-- ==================== Wo_Bot_Poll_Options ====================
CREATE TABLE IF NOT EXISTS `Wo_Bot_Poll_Options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_text` varchar(256) NOT NULL,
  `option_index` int(11) NOT NULL DEFAULT 0,
  `voter_count` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_poll_id` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== Wo_Bot_Poll_Votes ====================
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

-- ==================== Wo_Bot_Tasks ====================
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

-- ==================== Wo_Bot_RSS_Feeds ====================
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

-- ==================== Wo_Bot_RSS_Items ====================
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

-- ==================== Wo_Bot_Rate_Limits ====================
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

-- ==================== Wo_Bot_Api_Keys ====================
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

-- ============================================================
-- ШАГ 2: Создать Wo_Users запись для WallyBot
-- type='bot' нужен для поиска Android
-- ============================================================

INSERT IGNORE INTO `Wo_Users`
  (`username`, `email`, `password`, `first_name`, `last_name`,
   `avatar`, `about`, `type`, `active`, `verified`,
   `message_privacy`, `lastseen`, `registered`, `joined`,
   `follow_privacy`, `friend_privacy`, `post_privacy`,
   `confirm_followers`, `show_activities_privacy`)
VALUES
  ('wallybot',
   'wallybot@bots.internal',
   MD5(UUID()),
   'WallyBot',
   '',
   'upload/photos/d-avatar.jpg',
   'Официальный бот-менеджер WorldMates. Помогает создавать и управлять ботами прямо в чате!',
   'bot',
   '1',
   '0',
   '0',
   UNIX_TIMESTAMP(),
   DATE_FORMAT(NOW(), '%m/%Y'),
   UNIX_TIMESTAMP(),
   '0',
   '0',
   'everyone',
   '0',
   '1');

-- ============================================================
-- ШАГ 3: Создать запись WallyBot в Wo_Bots
-- ============================================================

SET @wallybot_user_id = (SELECT `user_id` FROM `Wo_Users` WHERE `username` = 'wallybot' LIMIT 1);
SET @wallybot_token   = CONCAT('wallybot:', SHA2(CONCAT('wallybot', UUID()), 256));

INSERT IGNORE INTO `Wo_Bots`
  (`bot_id`, `owner_id`, `bot_token`, `username`, `display_name`,
   `description`, `about`, `bot_type`, `status`, `is_public`,
   `is_inline`, `can_join_groups`, `supports_commands`, `category`,
   `linked_user_id`, `created_at`, `updated_at`)
VALUES
  ('wallybot',
   1,
   @wallybot_token,
   'wallybot',
   'WallyBot',
   'Официальный бот-менеджер WorldMates. Создавай своих ботов прямо в чате!',
   'Как Telegram BotFather — помогает создавать и управлять ботами.',
   'system',
   'active',
   1,
   0,
   0,
   1,
   'system',
   @wallybot_user_id,
   NOW(),
   NOW());

SELECT CONCAT('WallyBot token = ', COALESCE(@wallybot_token, 'уже существует')) AS wallybot_info;

-- ============================================================
-- ШАГ 4: Команды WallyBot
-- ============================================================

INSERT IGNORE INTO `Wo_Bot_Commands`
  (`bot_id`, `command`, `description`, `scope`, `is_hidden`, `sort_order`)
VALUES
  ('wallybot', 'start',       'Главное меню',                      'all', 0, 0),
  ('wallybot', 'help',        'Список команд',                     'all', 0, 1),
  ('wallybot', 'cancel',      'Отменить текущее действие',         'all', 0, 2),
  ('wallybot', 'newbot',      'Создать нового бота',               'all', 0, 3),
  ('wallybot', 'mybots',      'Список моих ботов',                 'all', 0, 4),
  ('wallybot', 'editbot',     'Редактировать бота',                'all', 0, 5),
  ('wallybot', 'deletebot',   'Удалить бота',                      'all', 0, 6),
  ('wallybot', 'token',       'Получить токен бота',               'all', 0, 7),
  ('wallybot', 'setcommands', 'Установить команды бота',           'all', 0, 8),
  ('wallybot', 'setdesc',     'Изменить описание бота',            'all', 0, 9),
  ('wallybot', 'learn',       'Обучить WallyBot новому ответу',    'all', 0, 10),
  ('wallybot', 'forget',      'Удалить ответ из базы знаний',      'all', 0, 11),
  ('wallybot', 'ask',         'Задать вопрос WallyBot',            'all', 0, 12);

-- ============================================================
-- ШАГ 5: Создать Wo_Users для всех ранее существующих ботов
-- (без записи в Wo_Users)
-- ============================================================

INSERT IGNORE INTO `Wo_Users`
  (`username`, `email`, `password`, `first_name`, `last_name`,
   `about`, `type`, `active`, `verified`,
   `message_privacy`, `lastseen`, `registered`, `joined`,
   `follow_privacy`, `friend_privacy`, `post_privacy`,
   `confirm_followers`, `show_activities_privacy`)
SELECT
  b.`username`,
  CONCAT(b.`bot_id`, '@bots.internal'),
  MD5(UUID()),
  b.`display_name`,
  '',
  COALESCE(b.`description`, ''),
  'bot',
  '1',
  '0',
  '0',
  UNIX_TIMESTAMP(),
  DATE_FORMAT(NOW(), '%m/%Y'),
  UNIX_TIMESTAMP(),
  '0',
  '0',
  'everyone',
  '0',
  '1'
FROM `Wo_Bots` b
WHERE NOT EXISTS (
  SELECT 1 FROM `Wo_Users` u WHERE u.`username` = b.`username`
);

-- ============================================================
-- ШАГ 6: Привязать linked_user_id для всех ботов
-- ============================================================

UPDATE `Wo_Bots` b
JOIN `Wo_Users` u ON u.`username` = b.`username`
SET b.`linked_user_id` = u.`user_id`
WHERE b.`linked_user_id` IS NULL;

-- ============================================================
-- Финальный отчёт
-- ============================================================

SELECT 'Миграция завершена!' AS status;

SELECT COUNT(*) AS bot_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'Wo_Bot%';

SELECT COUNT(*) AS total_bots FROM `Wo_Bots`;

SELECT `user_id`, `username`, `type`, `active`
FROM `Wo_Users`
WHERE `type` = 'bot';

SET foreign_key_checks = 1;