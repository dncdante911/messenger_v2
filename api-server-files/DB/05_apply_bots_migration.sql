-- ============================================================
-- WorldMates Bot API — Миграция бот-системы
-- Версия: 2.0 (на основе свежего бекапа)
-- Совместимо: MariaDB 10.11+ / phpMyAdmin
--
-- КАК ЗАПУСТИТЬ ЧЕРЕЗ phpMyAdmin:
--   1. Войдите в phpMyAdmin
--   2. СЛЕВА выберите вашу базу данных (например, socialhub)
--   3. Нажмите вкладку «Импорт»
--   4. Выберите этот файл → «Вперёд»
--
-- Безопасно запускать повторно — IF NOT EXISTS / INSERT IGNORE
-- ============================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;

-- ============================================================
-- ТАБЛИЦЫ БОТ-СИСТЕМЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS `Wo_Bots` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `owner_id` int(11) NOT NULL,
  `bot_token` varchar(128) NOT NULL,
  `username` varchar(64) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `about` text DEFAULT NULL,
  `bot_type` enum('standard','system','verified') NOT NULL DEFAULT 'standard',
  `status` enum('active','disabled','suspended','pending_review') NOT NULL DEFAULT 'active',
  `is_public` tinyint(1) NOT NULL DEFAULT 1,
  `is_inline` tinyint(1) NOT NULL DEFAULT 0,
  `can_join_groups` tinyint(1) NOT NULL DEFAULT 1,
  `can_read_all_group_messages` tinyint(1) NOT NULL DEFAULT 0,
  `supports_commands` tinyint(1) NOT NULL DEFAULT 1,
  `category` varchar(64) DEFAULT NULL,
  `tags` varchar(512) DEFAULT NULL,
  `webhook_url` varchar(512) DEFAULT NULL,
  `webhook_secret` varchar(128) DEFAULT NULL,
  `webhook_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `webhook_max_connections` int(11) NOT NULL DEFAULT 40,
  `webhook_allowed_updates` text DEFAULT NULL,
  `rate_limit_per_second` int(11) NOT NULL DEFAULT 30,
  `rate_limit_per_minute` int(11) NOT NULL DEFAULT 1500,
  `messages_sent` bigint(20) NOT NULL DEFAULT 0,
  `messages_received` bigint(20) NOT NULL DEFAULT 0,
  `total_users` int(11) NOT NULL DEFAULT 0,
  `active_users_24h` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_active_at` datetime DEFAULT NULL,
  `linked_user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_id` (`bot_id`),
  UNIQUE KEY `idx_bot_token` (`bot_token`),
  UNIQUE KEY `idx_bot_username` (`username`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Commands` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `command` varchar(64) NOT NULL,
  `description` varchar(256) NOT NULL,
  `usage_hint` varchar(256) DEFAULT NULL,
  `is_hidden` tinyint(1) NOT NULL DEFAULT 0,
  `scope` enum('all','private','group','admin') NOT NULL DEFAULT 'all',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_command` (`bot_id`,`command`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Messages` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL,
  `chat_type` enum('private','group') NOT NULL DEFAULT 'private',
  `direction` enum('incoming','outgoing') NOT NULL,
  `message_id` bigint(20) DEFAULT NULL,
  `text` text DEFAULT NULL,
  `media_type` varchar(32) DEFAULT NULL,
  `media_url` varchar(512) DEFAULT NULL,
  `reply_to_message_id` bigint(20) DEFAULT NULL,
  `reply_markup` text DEFAULT NULL,
  `callback_data` varchar(256) DEFAULT NULL,
  `entities` text DEFAULT NULL,
  `is_command` tinyint(1) NOT NULL DEFAULT 0,
  `command_name` varchar(64) DEFAULT NULL,
  `command_args` text DEFAULT NULL,
  `processed` tinyint(1) NOT NULL DEFAULT 0,
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_chat` (`bot_id`,`chat_id`),
  KEY `idx_direction` (`direction`),
  KEY `idx_processed` (`processed`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_bot_unprocessed` (`bot_id`,`processed`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Webhook_Log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `payload` text NOT NULL,
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
  KEY `idx_webhook_cleanup` (`created_at`,`delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `state` varchar(128) DEFAULT NULL,
  `state_data` text DEFAULT NULL,
  `is_blocked` tinyint(1) NOT NULL DEFAULT 0,
  `is_banned` tinyint(1) NOT NULL DEFAULT 0,
  `first_interaction_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_interaction_at` datetime DEFAULT NULL,
  `messages_count` int(11) NOT NULL DEFAULT 0,
  `custom_data` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_user` (`bot_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_last_interaction` (`last_interaction_at`),
  KEY `idx_bot_active_users` (`bot_id`,`is_blocked`,`last_interaction_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Keyboards` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `keyboard_type` enum('inline','reply','remove') NOT NULL DEFAULT 'inline',
  `keyboard_data` text NOT NULL,
  `is_persistent` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS `Wo_Bot_Polls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL,
  `question` varchar(512) NOT NULL,
  `poll_type` enum('regular','quiz') NOT NULL DEFAULT 'regular',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT 1,
  `allows_multiple_answers` tinyint(1) NOT NULL DEFAULT 0,
  `correct_option_id` int(11) DEFAULT NULL,
  `explanation` text DEFAULT NULL,
  `is_closed` tinyint(1) NOT NULL DEFAULT 0,
  `close_date` datetime DEFAULT NULL,
  `total_voters` int(11) NOT NULL DEFAULT 0,
  `message_id` bigint(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `closed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_is_closed` (`is_closed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Poll_Options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_text` varchar(256) NOT NULL,
  `option_index` int(11) NOT NULL DEFAULT 0,
  `voter_count` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_poll_id` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Poll_Votes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_unique_vote` (`poll_id`,`user_id`,`option_id`),
  KEY `idx_poll_id` (`poll_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  `assigned_to` int(11) DEFAULT NULL,
  `reminder_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bot_user` (`bot_id`,`user_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_reminder` (`reminder_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_RSS_Feeds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `chat_id` varchar(64) NOT NULL,
  `feed_url` varchar(512) NOT NULL,
  `feed_name` varchar(256) DEFAULT NULL,
  `feed_language` varchar(10) DEFAULT 'en',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `check_interval_minutes` int(11) NOT NULL DEFAULT 30,
  `last_check_at` datetime DEFAULT NULL,
  `last_item_hash` varchar(64) DEFAULT NULL,
  `items_posted` int(11) NOT NULL DEFAULT 0,
  `max_items_per_check` int(11) NOT NULL DEFAULT 5,
  `include_image` tinyint(1) NOT NULL DEFAULT 1,
  `include_description` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_chat` (`bot_id`,`chat_id`,`feed_url`(255)),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_last_check` (`last_check_at`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_RSS_Items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `feed_id` int(11) NOT NULL,
  `item_hash` varchar(64) NOT NULL,
  `title` varchar(512) DEFAULT NULL,
  `link` varchar(512) DEFAULT NULL,
  `posted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_hash` (`feed_id`,`item_hash`),
  KEY `idx_feed_id` (`feed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Rate_Limits` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `endpoint` varchar(128) NOT NULL,
  `requests_count` int(11) NOT NULL DEFAULT 0,
  `window_start` datetime NOT NULL,
  `window_type` enum('second','minute','hour') NOT NULL DEFAULT 'minute',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_endpoint_window` (`bot_id`,`endpoint`,`window_start`,`window_type`),
  KEY `idx_window_start` (`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Wo_Bot_Api_Keys` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `api_key` varchar(128) NOT NULL,
  `api_secret` varchar(128) NOT NULL,
  `app_name` varchar(256) NOT NULL,
  `description` text DEFAULT NULL,
  `permissions` text DEFAULT NULL,
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
-- WallyBot: запись в Wo_Users
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
-- WallyBot: запись в Wo_Bots
-- Субзапрос вместо @переменной — надёжнее в phpMyAdmin
-- ============================================================

INSERT IGNORE INTO `Wo_Bots`
  (`bot_id`, `owner_id`, `bot_token`, `username`, `display_name`,
   `description`, `about`, `bot_type`, `status`, `is_public`,
   `is_inline`, `can_join_groups`, `can_read_all_group_messages`,
   `supports_commands`, `category`, `linked_user_id`,
   `created_at`, `updated_at`)
VALUES
  ('wallybot',
   1,
   CONCAT('wallybot:', SHA2(CONCAT('wallybot_system_', UUID()), 256)),
   'wallybot',
   'WallyBot',
   'Официальный бот-менеджер WorldMates. Создавайте своих ботов прямо в чате!',
   'Помогает создавать и управлять ботами WorldMates. Также обучаем — пишите /learn!',
   'system',
   'active',
   1,
   0,
   0,
   0,
   1,
   'system',
   (SELECT `user_id` FROM `Wo_Users` WHERE `username` = 'wallybot' LIMIT 1),
   NOW(),
   NOW());

-- ============================================================
-- WallyBot: команды
-- ============================================================

INSERT IGNORE INTO `Wo_Bot_Commands`
  (`bot_id`, `command`, `description`, `scope`, `is_hidden`, `sort_order`)
VALUES
  ('wallybot', 'start',       'Начать работу с ботом',          'all', 0,  0),
  ('wallybot', 'help',        'Помощь и список команд',         'all', 0,  1),
  ('wallybot', 'cancel',      'Отменить действие',              'all', 0,  2),
  ('wallybot', 'newbot',      'Создать нового бота',            'all', 0,  3),
  ('wallybot', 'mybots',      'Список моих ботов',              'all', 0,  4),
  ('wallybot', 'editbot',     'Редактировать бота',             'all', 0,  5),
  ('wallybot', 'deletebot',   'Удалить бота',                   'all', 0,  6),
  ('wallybot', 'token',       'Получить токен бота',            'all', 0,  7),
  ('wallybot', 'setcommands', 'Установить команды бота',        'all', 0,  8),
  ('wallybot', 'setdesc',     'Изменить описание бота',         'all', 0,  9),
  ('wallybot', 'learn',       'Научить WallyBot новому ответу', 'all', 0, 10),
  ('wallybot', 'forget',      'Удалить ответ из базы знаний',   'all', 0, 11),
  ('wallybot', 'ask',         'Задать вопрос WallyBot',         'all', 0, 12);

-- ============================================================
-- Для существующих ботов без записи в Wo_Users — создать
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

-- Привязать linked_user_id для всех ботов

UPDATE `Wo_Bots` b
JOIN `Wo_Users` u ON u.`username` = b.`username`
SET b.`linked_user_id` = u.`user_id`
WHERE b.`linked_user_id` IS NULL;

-- ============================================================
-- Отчёт
-- ============================================================

SELECT COUNT(*) AS bot_tables_created
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'Wo_Bot%';

SELECT COUNT(*) AS total_bots FROM `Wo_Bots`;

SELECT `user_id`, `username`, `type`, `active`
FROM `Wo_Users`
WHERE `type` = 'bot';

SET foreign_key_checks = 1;
