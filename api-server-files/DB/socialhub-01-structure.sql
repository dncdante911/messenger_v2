/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;

/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;

/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;

/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;

DROP TABLE IF EXISTS `Wo_Activities`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Activities` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(255) NOT NULL DEFAULT 0,
  `post_id` int(255) NOT NULL DEFAULT 0,
  `reply_id` int(11) unsigned DEFAULT 0,
  `comment_id` int(11) unsigned DEFAULT 0,
  `follow_id` int(11) NOT NULL DEFAULT 0,
  `activity_type` varchar(32) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `activity_type` (`activity_type`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`post_id`,`id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`),
  KEY `follow_id` (`follow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_AdminInvitations`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_AdminInvitations` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(300) NOT NULL DEFAULT '0',
  `posted` varchar(50) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `code` (`code`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Ads`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Ads` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(32) NOT NULL DEFAULT '',
  `code` mediumtext DEFAULT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Affiliates_Requests`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Affiliates_Requests` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(100) NOT NULL DEFAULT '0',
  `full_amount` varchar(100) NOT NULL DEFAULT '',
  `iban` varchar(250) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `full_name` varchar(150) NOT NULL DEFAULT '',
  `swift_code` varchar(300) NOT NULL DEFAULT '',
  `address` varchar(600) NOT NULL DEFAULT '',
  `type` varchar(100) NOT NULL DEFAULT '',
  `transfer_info` varchar(600) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`),
  KEY `status` (`status`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_AgoraVideoCall`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_AgoraVideoCall` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `type` varchar(50) NOT NULL DEFAULT 'video',
  `room_name` varchar(50) NOT NULL DEFAULT '0',
  `time` int(11) NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `access_token` mediumtext DEFAULT NULL,
  `access_token_2` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `to_id` (`to_id`),
  KEY `type` (`type`),
  KEY `room_name` (`room_name`),
  KEY `time` (`time`),
  KEY `status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Albums_Media`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Albums_Media` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `review_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `order1` (`post_id`,`id`),
  KEY `parent_id` (`parent_id`),
  KEY `review_id` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Announcement`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Announcement` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `text` mediumtext DEFAULT NULL,
  `time` int(32) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Announcement_Views`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Announcement_Views` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `announcement_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `announcement_id` (`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Apps`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Apps` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_user_id` int(11) NOT NULL DEFAULT 0,
  `app_name` varchar(32) NOT NULL,
  `app_website_url` varchar(55) NOT NULL,
  `app_description` mediumtext NOT NULL,
  `app_avatar` varchar(100) NOT NULL DEFAULT 'upload/photos/app-default-icon.png',
  `app_callback_url` varchar(255) NOT NULL,
  `app_id` varchar(32) NOT NULL,
  `app_secret` varchar(55) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `app_user_id` (`app_user_id`),
  KEY `app_id` (`app_id`),
  KEY `active` (`active`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_AppsSessions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_AppsSessions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `session_id` varchar(120) NOT NULL DEFAULT '',
  `platform` varchar(32) NOT NULL DEFAULT '',
  `platform_details` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_id` (`session_id`),
  KEY `user_id` (`user_id`),
  KEY `platform` (`platform`),
  KEY `time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=366 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Apps_Hash`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Apps_Hash` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash_id` varchar(200) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `hash_id` (`hash_id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Apps_Permission`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Apps_Permission` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `app_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`app_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_AudioCalls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_AudioCalls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `call_id` varchar(30) NOT NULL DEFAULT '0',
  `access_token` mediumtext DEFAULT NULL,
  `call_id_2` varchar(30) NOT NULL DEFAULT '',
  `access_token_2` mediumtext DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `to_id` (`to_id`),
  KEY `from_id` (`from_id`),
  KEY `call_id` (`call_id`),
  KEY `called` (`called`),
  KEY `declined` (`declined`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Backup_Codes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Backup_Codes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `codes` varchar(500) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bad_Login`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bad_Login` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ip` varchar(100) NOT NULL DEFAULT '',
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ip` (`ip`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Banned_Ip`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Banned_Ip` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ip_address` varchar(100) NOT NULL DEFAULT '',
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ip_address` (`ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Blocks`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Blocks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blocker` int(11) NOT NULL DEFAULT 0,
  `blocked` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blocker` (`blocker`),
  KEY `blocked` (`blocked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Blog`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Blog` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user` int(11) NOT NULL DEFAULT 0,
  `title` varchar(120) NOT NULL DEFAULT '',
  `content` mediumtext DEFAULT NULL,
  `description` mediumtext DEFAULT NULL,
  `posted` varchar(300) DEFAULT '0',
  `category` int(255) DEFAULT 0,
  `thumbnail` varchar(100) DEFAULT 'upload/photos/d-blog.jpg',
  `view` int(11) DEFAULT 0,
  `shared` int(11) DEFAULT 0,
  `tags` varchar(300) DEFAULT '',
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `user` (`user`),
  KEY `title` (`title`),
  KEY `category` (`category`),
  KEY `active` (`active`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_BlogCommentReplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_BlogCommentReplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comm_id` (`comm_id`),
  KEY `blog_id` (`blog_id`),
  KEY `order1` (`comm_id`,`id`),
  KEY `order2` (`blog_id`,`id`),
  KEY `order3` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_BlogComments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_BlogComments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_id` (`blog_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_BlogMovieDisLikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_BlogMovieDisLikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_comm_id` int(20) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(20) NOT NULL DEFAULT 0,
  `movie_comm_id` int(20) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(20) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(50) NOT NULL DEFAULT 0,
  `movie_id` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_comm_id` (`blog_comm_id`),
  KEY `movie_comm_id` (`movie_comm_id`),
  KEY `user_id` (`user_id`),
  KEY `blog_commreply_id` (`blog_commreply_id`),
  KEY `movie_commreply_id` (`movie_commreply_id`),
  KEY `blog_id` (`blog_id`),
  KEY `movie_id` (`movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_BlogMovieLikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_BlogMovieLikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_comm_id` int(20) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(20) NOT NULL DEFAULT 0,
  `movie_comm_id` int(20) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(20) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(50) NOT NULL DEFAULT 0,
  `movie_id` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_id` (`blog_comm_id`),
  KEY `movie_id` (`movie_comm_id`),
  KEY `user_id` (`user_id`),
  KEY `blog_commreply_id` (`blog_commreply_id`),
  KEY `movie_commreply_id` (`movie_commreply_id`),
  KEY `blog_id_2` (`blog_id`),
  KEY `movie_id_2` (`movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Blog_Reaction`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Blog_Reaction` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `blog_id` (`blog_id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Blogs_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Blogs_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `lang_key` (`lang_key`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Api_Keys`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Api_Keys` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_api_key` (`api_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Callbacks`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Callbacks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `message_id` bigint(20) DEFAULT NULL,
  `callback_data` varchar(256) NOT NULL,
  `answered` tinyint(1) NOT NULL DEFAULT 0,
  `answer_text` varchar(256) DEFAULT NULL,
  `answer_show_alert` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Commands`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Commands` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `command` varchar(64) NOT NULL,
  `description` varchar(256) NOT NULL,
  `usage_hint` varchar(256) DEFAULT NULL,
  `is_hidden` tinyint(1) NOT NULL DEFAULT 0,
  `scope` enum('all','private','group','admin') NOT NULL DEFAULT 'all',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_command` (`bot_id`,`command`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Keyboards`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Keyboards` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `keyboard_type` enum('inline','reply','remove') NOT NULL DEFAULT 'inline',
  `keyboard_data` text NOT NULL,
  `is_persistent` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Messages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Messages` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bot_chat` (`bot_id`,`chat_id`),
  KEY `idx_direction` (`direction`),
  KEY `idx_processed` (`processed`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_bot_unprocessed` (`bot_id`,`processed`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Poll_Options`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Poll_Options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_text` varchar(256) NOT NULL,
  `option_index` int(11) NOT NULL DEFAULT 0,
  `voter_count` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_poll_id` (`poll_id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Poll_Votes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Poll_Votes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `poll_id` int(11) NOT NULL,
  `option_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_unique_vote` (`poll_id`,`user_id`,`option_id`),
  KEY `idx_poll_id` (`poll_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Polls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Polls` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `closed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_is_closed` (`is_closed`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_RSS_Feeds`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_RSS_Feeds` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_chat` (`bot_id`,`chat_id`,`feed_url`(255)),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_last_check` (`last_check_at`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_RSS_Items`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_RSS_Items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `feed_id` int(11) NOT NULL,
  `item_hash` varchar(64) NOT NULL,
  `title` varchar(512) DEFAULT NULL,
  `link` varchar(512) DEFAULT NULL,
  `posted_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_feed_hash` (`feed_id`,`item_hash`),
  KEY `idx_feed_id` (`feed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Rate_Limits`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Rate_Limits` (
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

DROP TABLE IF EXISTS `Wo_Bot_Tasks`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Tasks` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bot_user` (`bot_id`,`user_id`),
  KEY `idx_chat_id` (`chat_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_reminder` (`reminder_at`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Users`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bot_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `state` varchar(128) DEFAULT NULL,
  `state_data` text DEFAULT NULL,
  `is_blocked` tinyint(1) NOT NULL DEFAULT 0,
  `is_banned` tinyint(1) NOT NULL DEFAULT 0,
  `first_interaction_at` datetime NOT NULL DEFAULT current_timestamp(),
  `last_interaction_at` datetime DEFAULT NULL,
  `messages_count` int(11) NOT NULL DEFAULT 0,
  `custom_data` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_user` (`bot_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_last_interaction` (`last_interaction_at`),
  KEY `idx_bot_active_users` (`bot_id`,`is_blocked`,`last_interaction_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bot_Webhook_Log`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bot_Webhook_Log` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_bot_id` (`bot_id`),
  KEY `idx_status` (`delivery_status`),
  KEY `idx_next_retry` (`next_retry_at`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_webhook_cleanup` (`created_at`,`delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Bots`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Bots` (
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
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_active_at` datetime DEFAULT NULL,
  `linked_user_id` int(11) DEFAULT NULL,
  `inline_feedback` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1 = бот хоче отримувати chosen_inline_result події',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_bot_id` (`bot_id`),
  UNIQUE KEY `idx_bot_token` (`bot_token`),
  UNIQUE KEY `idx_bot_username` (`username`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ChannelAdmins`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ChannelAdmins` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `channel_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `role` enum('admin','moderator') DEFAULT 'admin',
  `added_at` int(11) NOT NULL,
  `added_by` int(11) NOT NULL COMMENT 'User who added this admin',
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_user` (`channel_id`,`user_id`),
  KEY `channel_id` (`channel_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ChannelComments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ChannelComments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `post_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `text` text NOT NULL,
  `reply_to_id` bigint(20) DEFAULT NULL COMMENT 'Reply to another comment',
  `reactions_count` int(11) DEFAULT 0,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) DEFAULT NULL,
  `deleted_at` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `created_at` (`created_at`),
  KEY `reply_to_id` (`reply_to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ChannelPosts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ChannelPosts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `channel_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL COMMENT 'Post author (admin)',
  `text` text DEFAULT NULL,
  `media` text DEFAULT NULL COMMENT 'JSON array of media URLs',
  `views_count` int(11) DEFAULT 0,
  `reactions_count` int(11) DEFAULT 0,
  `comments_count` int(11) DEFAULT 0,
  `shares_count` int(11) DEFAULT 0,
  `is_pinned` tinyint(1) DEFAULT 0,
  `disable_comments` tinyint(1) DEFAULT 0,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) DEFAULT NULL,
  `deleted_at` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `channel_id` (`channel_id`),
  KEY `user_id` (`user_id`),
  KEY `created_at` (`created_at`),
  KEY `is_pinned` (`is_pinned`),
  KEY `channel_created` (`channel_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ChannelSettings`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ChannelSettings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `channel_id` bigint(20) NOT NULL,
  `show_author_signature` tinyint(1) DEFAULT 1,
  `allow_comments` tinyint(1) DEFAULT 1,
  `allow_reactions` tinyint(1) DEFAULT 1,
  `allow_shares` tinyint(1) DEFAULT 1,
  `moderate_comments` tinyint(1) DEFAULT 0,
  `slow_mode` int(11) DEFAULT 0 COMMENT 'Seconds between posts (0=disabled)',
  `notify_subscribers` tinyint(1) DEFAULT 1,
  `show_statistics` tinyint(1) DEFAULT 1,
  `updated_at` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_id` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ChannelSubscribers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ChannelSubscribers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `channel_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `subscribed_at` int(11) NOT NULL,
  `is_muted` tinyint(1) DEFAULT 0 COMMENT 'Mute notifications',
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_user` (`channel_id`,`user_id`),
  KEY `channel_id` (`channel_id`),
  KEY `user_id` (`user_id`),
  KEY `subscribed_at` (`subscribed_at`),
  KEY `channel_muted` (`channel_id`,`user_id`,`is_muted`),
  KEY `idx_channel_muted` (`channel_id`,`user_id`,`is_muted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Channel_Bans`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Channel_Bans` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `channel_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `banned_by` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `ban_time` int(11) NOT NULL DEFAULT 0,
  `expire_time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_user_unique` (`channel_id`,`user_id`),
  KEY `idx_channel_id` (`channel_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Channels`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Channels` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT 'Channel owner/creator',
  `channel_name` varchar(255) NOT NULL,
  `channel_username` varchar(100) DEFAULT NULL COMMENT 'Unique @username',
  `channel_description` text DEFAULT NULL,
  `channel_category` varchar(100) DEFAULT NULL,
  `avatar` varchar(500) DEFAULT NULL,
  `cover` varchar(500) DEFAULT NULL,
  `is_private` tinyint(1) DEFAULT 0 COMMENT '0=public, 1=private',
  `is_verified` tinyint(1) DEFAULT 0,
  `subscribers_count` int(11) DEFAULT 0,
  `posts_count` int(11) DEFAULT 0,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) DEFAULT NULL,
  `qr_code` varchar(50) DEFAULT NULL COMMENT 'QR code for quick subscribe',
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_username` (`channel_username`),
  KEY `user_id` (`user_id`),
  KEY `created_at` (`created_at`),
  KEY `subscribers_count` (`subscribers_count`),
  KEY `qr_code` (`qr_code`),
  FULLTEXT KEY `search_channel` (`channel_name`,`channel_description`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Codes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Codes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL DEFAULT '',
  `app_id` varchar(50) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `code` (`code`),
  KEY `user_id` (`user_id`),
  KEY `app_id` (`app_id`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Colored_Posts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Colored_Posts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `color_1` varchar(50) NOT NULL DEFAULT '',
  `color_2` varchar(50) NOT NULL DEFAULT '',
  `text_color` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(250) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `color_1` (`color_1`),
  KEY `color_2` (`color_2`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_CommentLikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_CommentLikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `comment_id` (`comment_id`),
  KEY `post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_CommentWonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_CommentWonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `comment_id` (`comment_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Comment_Replies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Comment_Replies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `c_file` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comment_id` (`comment_id`),
  KEY `user_id` (`user_id`,`page_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Comment_Replies_Likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Comment_Replies_Likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `reply_id` (`reply_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Comment_Replies_Wonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Comment_Replies_Wonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `reply_id` (`reply_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Comments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Comments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `record` varchar(255) NOT NULL DEFAULT '',
  `c_file` varchar(255) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`page_id`,`id`),
  KEY `order3` (`post_id`,`id`),
  KEY `order4` (`user_id`,`id`),
  KEY `order5` (`post_id`,`id`),
  KEY `time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Config`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=576 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_CustomPages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_CustomPages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_name` varchar(50) NOT NULL DEFAULT '',
  `page_title` varchar(200) NOT NULL DEFAULT '',
  `page_content` mediumtext DEFAULT NULL,
  `page_type` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_type` (`page_type`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Custom_Fields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Custom_Fields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` varchar(50) DEFAULT '',
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(50) NOT NULL DEFAULT '',
  `required` varchar(11) NOT NULL DEFAULT 'on',
  `options` mediumtext DEFAULT NULL,
  `active` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Egoing`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Egoing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Einterested`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Einterested` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Einvited`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Einvited` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `inviter_id` int(11) NOT NULL,
  `invited_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `inviter_id` (`invited_id`),
  KEY `inviter_id_2` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Emails`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Emails` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `email_to` varchar(50) NOT NULL DEFAULT '',
  `subject` varchar(32) NOT NULL DEFAULT '',
  `message` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `email_to` (`email_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Events`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Events` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(150) NOT NULL DEFAULT '',
  `location` varchar(300) NOT NULL DEFAULT '',
  `description` mediumtext NOT NULL,
  `start_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_date` date NOT NULL,
  `end_time` time NOT NULL,
  `poster_id` int(11) NOT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-cover.jpg',
  PRIMARY KEY (`id`),
  KEY `poster_id` (`poster_id`),
  KEY `name` (`name`),
  KEY `start_date` (`start_date`),
  KEY `start_time` (`start_time`),
  KEY `end_time` (`end_time`),
  KEY `end_date` (`end_date`),
  KEY `order1` (`poster_id`,`id`),
  KEY `order2` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Family`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Family` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `member_id` int(11) NOT NULL,
  `member` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `requesting` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `member_id` (`member_id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`),
  KEY `requesting` (`requesting`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Followers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Followers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `following_id` int(11) NOT NULL DEFAULT 0,
  `follower_id` int(11) NOT NULL DEFAULT 0,
  `is_typing` int(11) NOT NULL DEFAULT 0,
  `active` int(255) NOT NULL DEFAULT 1,
  `notify` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `following_id` (`following_id`),
  KEY `follower_id` (`follower_id`),
  KEY `active` (`active`),
  KEY `order1` (`following_id`,`id`),
  KEY `order2` (`follower_id`,`id`),
  KEY `is_typing` (`is_typing`),
  KEY `notify` (`notify`),
  KEY `time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ForumThreadReplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ForumThreadReplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `poster_id` int(11) NOT NULL DEFAULT 0,
  `post_subject` varchar(300) NOT NULL DEFAULT '',
  `post_text` mediumtext NOT NULL,
  `post_quoted` int(11) NOT NULL DEFAULT 0,
  `posted_time` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `thread_id` (`thread_id`),
  KEY `forum_id` (`forum_id`),
  KEY `poster_id` (`poster_id`),
  KEY `post_subject` (`post_subject`(255)),
  KEY `post_quoted` (`post_quoted`),
  KEY `posted_time` (`posted_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Forum_Sections`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Forum_Sections` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `section_name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `section_name` (`section_name`),
  KEY `description` (`description`(255))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Forum_Threads`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Forum_Threads` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `headline` varchar(300) NOT NULL DEFAULT '',
  `post` mediumtext NOT NULL,
  `posted` varchar(20) NOT NULL DEFAULT '0',
  `last_post` int(11) NOT NULL DEFAULT 0,
  `forum` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user` (`user`),
  KEY `views` (`views`),
  KEY `posted` (`posted`),
  KEY `headline` (`headline`(255)),
  KEY `forum` (`forum`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Forums`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Forums` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `sections` int(11) NOT NULL DEFAULT 0,
  `posts` int(11) NOT NULL DEFAULT 0,
  `last_post` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `description` (`description`(255)),
  KEY `posts` (`posts`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Funding`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Funding` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hashed_id` varchar(100) NOT NULL DEFAULT '',
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` longtext DEFAULT NULL,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(200) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `hashed_id` (`hashed_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Funding_Raise`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Funding_Raise` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `funding_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `funding_id` (`funding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Games`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Games` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game_name` varchar(50) NOT NULL,
  `game_avatar` varchar(100) NOT NULL,
  `game_link` varchar(100) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Games_Players`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Games_Players` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `game_id` int(11) NOT NULL DEFAULT 0,
  `last_play` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`game_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Gender`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Gender` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `gender_id` varchar(50) NOT NULL DEFAULT '0',
  `image` varchar(300) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `gender_id` (`gender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Gifts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Gifts` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_GroupAdmins`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_GroupAdmins` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `privacy` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `members` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_group` int(11) NOT NULL DEFAULT 0,
  `is_anonymous_admin` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `group_id` (`group_id`),
  KEY `members` (`members`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_GroupChat`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_GroupChat` (
  `group_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `group_name` varchar(255) NOT NULL DEFAULT '',
  `description` text DEFAULT NULL,
  `is_private` enum('0','1') NOT NULL DEFAULT '0',
  `settings` text DEFAULT NULL,
  `avatar` varchar(3000) NOT NULL DEFAULT 'upload/photos/d-group.jpg',
  `time` varchar(30) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'group',
  `destruct_at` int(11) unsigned NOT NULL DEFAULT 0,
  `username` varchar(100) DEFAULT NULL COMMENT 'Унікальний @username для публічних каналів',
  `subscribers_count` int(10) unsigned DEFAULT 0 COMMENT 'Кількість підписників',
  `posts_count` int(10) unsigned DEFAULT 0 COMMENT 'Кількість постів',
  `is_verified` tinyint(1) DEFAULT 0 COMMENT 'Верифікований канал',
  `category` varchar(100) DEFAULT NULL COMMENT 'Категорія каналу',
  `slow_mode_seconds` int(11) DEFAULT 0,
  `history_visible_for_new_members` tinyint(1) DEFAULT 1,
  `history_messages_count` int(11) DEFAULT 100,
  `anti_spam_enabled` tinyint(1) DEFAULT 0,
  `max_messages_per_minute` int(11) DEFAULT 20,
  `auto_mute_spammers` tinyint(1) DEFAULT 1,
  `block_new_users_media` tinyint(1) DEFAULT 0,
  `new_user_restriction_hours` int(11) DEFAULT 24,
  `allow_members_send_media` tinyint(1) DEFAULT 1,
  `allow_members_send_links` tinyint(1) DEFAULT 1,
  `allow_members_send_stickers` tinyint(1) DEFAULT 1,
  `allow_members_send_gifs` tinyint(1) DEFAULT 1,
  `allow_members_send_polls` tinyint(1) DEFAULT 1,
  `allow_members_invite` tinyint(1) DEFAULT 0,
  `allow_members_pin` tinyint(1) DEFAULT 0,
  `allow_members_delete_messages` tinyint(1) DEFAULT 0,
  `allow_voice_calls` tinyint(1) DEFAULT 1,
  `allow_video_calls` tinyint(1) DEFAULT 1,
  `qr_code` varchar(64) DEFAULT NULL,
  `pinned_message_id` bigint(20) DEFAULT NULL,
  `theme_bubble_style` varchar(50) DEFAULT 'STANDARD',
  `theme_preset_background` varchar(50) DEFAULT 'ocean',
  `theme_accent_color` varchar(10) DEFAULT '#2196F3',
  `theme_enabled_by_admin` tinyint(1) DEFAULT 1,
  `theme_updated_at` int(11) DEFAULT 0,
  `theme_updated_by` int(11) DEFAULT 0,
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `username` (`username`),
  KEY `user_id` (`user_id`),
  KEY `type` (`type`),
  KEY `destruct_at` (`destruct_at`),
  KEY `is_private` (`is_private`),
  KEY `idx_username` (`username`),
  KEY `idx_type` (`type`),
  KEY `idx_group_theme_updated` (`theme_updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_GroupChatUsers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_GroupChatUsers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `group_id` int(11) NOT NULL,
  `role` enum('owner','admin','moderator','member') NOT NULL DEFAULT 'member',
  `active` enum('1','0') NOT NULL DEFAULT '1',
  `last_seen` varchar(50) NOT NULL DEFAULT '0',
  `muted` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `group_id` (`group_id`),
  KEY `active` (`active`),
  KEY `role` (`role`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_GroupJoinRequests`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_GroupJoinRequests` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `message` text DEFAULT NULL,
  `status` enum('pending','approved','rejected') DEFAULT 'pending',
  `created_time` bigint(20) DEFAULT NULL,
  `reviewed_by` bigint(20) DEFAULT NULL,
  `reviewed_time` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `group_id` (`group_id`),
  KEY `user_id` (`user_id`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `Wo_GroupTopics`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_GroupTopics` (
  `topic_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `group_id` int(10) unsigned NOT NULL,
  `topic_name` varchar(255) NOT NULL,
  `topic_icon` varchar(50) DEFAULT '?',
  `topic_color` varchar(7) DEFAULT '#0084FF',
  `created_by` int(10) unsigned NOT NULL,
  `created_time` int(10) unsigned NOT NULL,
  `is_general` tinyint(1) DEFAULT 0,
  `message_count` int(10) unsigned DEFAULT 0,
  `last_message_time` int(10) unsigned DEFAULT NULL,
  PRIMARY KEY (`topic_id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_last_message` (`last_message_time`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Group_Members`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Group_Members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `time` (`time`),
  KEY `user_id` (`user_id`,`group_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Groups`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Groups` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_name` varchar(32) NOT NULL DEFAULT '',
  `group_title` varchar(40) NOT NULL DEFAULT '',
  `avatar` varchar(120) NOT NULL DEFAULT 'upload/photos/d-group.jpg ',
  `cover` varchar(120) NOT NULL DEFAULT 'upload/photos/d-cover.jpg  ',
  `about` varchar(550) NOT NULL DEFAULT '',
  `category` int(11) NOT NULL DEFAULT 1,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `privacy` enum('1','2') NOT NULL DEFAULT '1',
  `join_privacy` enum('1','2') NOT NULL DEFAULT '1',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `time` int(20) NOT NULL DEFAULT 0,
  `qr_code` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `privacy` (`privacy`),
  KEY `time` (`time`),
  KEY `active` (`active`),
  KEY `group_title` (`group_title`),
  KEY `group_name` (`group_name`),
  KEY `registered` (`registered`),
  KEY `idx_qr_code` (`qr_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Groups_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Groups_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_HTML_Emails`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_HTML_Emails` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Hashtags`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Hashtags` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash` varchar(255) NOT NULL DEFAULT '',
  `tag` varchar(255) NOT NULL DEFAULT '',
  `last_trend_time` int(11) NOT NULL DEFAULT 0,
  `trend_use_num` int(11) NOT NULL DEFAULT 0,
  `expire` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `last_trend_time` (`last_trend_time`),
  KEY `trend_use_num` (`trend_use_num`),
  KEY `tag` (`tag`),
  KEY `expire` (`expire`),
  KEY `hash` (`hash`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_HiddenPosts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_HiddenPosts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Invitation_Links`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Invitation_Links` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  `code` varchar(300) NOT NULL DEFAULT '',
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `code` (`code`(255)),
  KEY `invited_id` (`invited_id`),
  KEY `time` (`time`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Job`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Job` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `title` varchar(200) NOT NULL DEFAULT '',
  `location` varchar(100) NOT NULL DEFAULT '',
  `lat` varchar(50) NOT NULL DEFAULT '',
  `lng` varchar(50) NOT NULL DEFAULT '',
  `minimum` varchar(50) NOT NULL DEFAULT '0',
  `maximum` varchar(50) NOT NULL DEFAULT '0',
  `salary_date` varchar(50) NOT NULL DEFAULT '',
  `job_type` varchar(50) NOT NULL DEFAULT '',
  `category` varchar(50) NOT NULL DEFAULT '',
  `question_one` varchar(200) NOT NULL DEFAULT '',
  `question_one_type` varchar(100) NOT NULL DEFAULT '',
  `question_one_answers` mediumtext DEFAULT NULL,
  `question_two` varchar(200) NOT NULL DEFAULT '',
  `question_two_type` varchar(100) NOT NULL DEFAULT '',
  `question_two_answers` mediumtext DEFAULT NULL,
  `question_three` varchar(200) NOT NULL DEFAULT '',
  `question_three_type` varchar(100) NOT NULL DEFAULT '',
  `question_three_answers` mediumtext DEFAULT NULL,
  `description` mediumtext DEFAULT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `image_type` varchar(11) NOT NULL DEFAULT '',
  `currency` varchar(11) NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT 1,
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`),
  KEY `title` (`title`),
  KEY `category` (`category`),
  KEY `lng` (`lng`),
  KEY `lat` (`lat`),
  KEY `status` (`status`),
  KEY `job_type` (`job_type`),
  KEY `minimum` (`minimum`),
  KEY `maximum` (`maximum`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Job_Apply`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Job_Apply` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `job_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `user_name` varchar(100) NOT NULL DEFAULT '',
  `phone_number` varchar(50) NOT NULL DEFAULT '',
  `location` varchar(50) NOT NULL DEFAULT '',
  `email` varchar(100) NOT NULL DEFAULT '',
  `question_one_answer` varchar(200) NOT NULL DEFAULT '',
  `question_two_answer` varchar(200) NOT NULL DEFAULT '',
  `question_three_answer` varchar(200) NOT NULL DEFAULT '',
  `position` varchar(100) NOT NULL DEFAULT '',
  `where_did_you_work` varchar(100) NOT NULL DEFAULT '',
  `experience_description` varchar(300) NOT NULL DEFAULT '',
  `experience_start_date` varchar(50) NOT NULL DEFAULT '',
  `experience_end_date` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `job_id` (`job_id`),
  KEY `page_id` (`page_id`),
  KEY `user_name` (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Job_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Job_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_LangIso`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_LangIso` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_name` varchar(100) NOT NULL DEFAULT '',
  `iso` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(300) NOT NULL DEFAULT '',
  `direction` varchar(50) NOT NULL DEFAULT 'ltr',
  PRIMARY KEY (`id`),
  KEY `lang_name` (`lang_name`),
  KEY `iso` (`iso`),
  KEY `image` (`image`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Langs`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Langs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) DEFAULT NULL,
  `type` varchar(100) NOT NULL DEFAULT '',
  `english` longtext DEFAULT NULL,
  `russian` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`lang_key`),
  KEY `type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=2568 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Live_Sub_Users`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Live_Sub_Users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `is_watching` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `time` (`time`),
  KEY `is_watching` (`is_watching`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Manage_Pro`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Manage_Pro` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(100) NOT NULL DEFAULT '',
  `price` varchar(11) NOT NULL DEFAULT '0',
  `featured_member` int(11) NOT NULL DEFAULT 0,
  `profile_visitors` int(11) NOT NULL DEFAULT 0,
  `last_seen` int(11) NOT NULL DEFAULT 0,
  `verified_badge` int(11) NOT NULL DEFAULT 0,
  `posts_promotion` int(11) NOT NULL DEFAULT 0,
  `pages_promotion` int(11) NOT NULL DEFAULT 0,
  `discount` mediumtext NOT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `night_image` varchar(300) NOT NULL DEFAULT '',
  `color` varchar(50) NOT NULL DEFAULT '#fafafa',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 1,
  `time` varchar(20) NOT NULL DEFAULT 'week',
  `time_count` int(11) NOT NULL DEFAULT 0,
  `max_upload` varchar(100) NOT NULL DEFAULT '96000000',
  `features` varchar(800) DEFAULT '{"can_use_funding":1,"can_use_jobs":1,"can_use_games":1,"can_use_market":1,"can_use_events":1,"can_use_forum":1,"can_use_groups":1,"can_use_pages":1,"can_use_audio_call":1,"can_use_video_call":1,"can_use_offer":1,"can_use_blog":1,"can_use_movies":1,"can_use_story":1,"can_use_stickers":1,"can_use_gif":1,"can_use_gift":1,"can_use_nearby":1,"can_use_video_upload":1,"can_use_audio_upload":1,"can_use_shout_box":1,"can_use_colored_posts":1,"can_use_poll":1,"can_use_live":1,"can_use_background":1,"can_use_chat":1,"can_use_ai_image":1,"can_use_ai_post":1,"can_use_ai_user":1,"can_use_ai_blog":1}',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MessageCommentReactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MessageCommentReactions` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `comment_id` bigint(20) unsigned NOT NULL,
  `user_id` int(11) NOT NULL,
  `reaction` varchar(10) NOT NULL COMMENT 'Emoji реакції',
  `time` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_reaction` (`comment_id`,`user_id`,`reaction`),
  KEY `idx_comment_id` (`comment_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `Wo_MessageCommentReactions_ibfk_1` FOREIGN KEY (`comment_id`) REFERENCES `Wo_MessageComments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MessageComments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MessageComments` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `message_id` int(11) NOT NULL COMMENT 'ID повідомлення з Wo_Messages',
  `user_id` int(11) NOT NULL,
  `text` text NOT NULL,
  `time` int(11) NOT NULL,
  `edited_time` int(11) DEFAULT NULL,
  `reply_to_comment_id` bigint(20) unsigned DEFAULT NULL COMMENT 'Відповідь на коментар',
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_time` (`time`),
  KEY `idx_reply_to` (`reply_to_comment_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MessageViews`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MessageViews` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `message_id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL COMMENT 'NULL для анонімних переглядів',
  `time` int(11) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Messages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Messages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `text_ecb` text DEFAULT NULL,
  `text_preview` varchar(255) DEFAULT NULL,
  `media` varchar(255) NOT NULL DEFAULT '',
  `mediaFileName` varchar(200) NOT NULL DEFAULT '',
  `mediaFileNames` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  `seen` int(11) NOT NULL DEFAULT 0,
  `deleted_one` enum('0','1') NOT NULL DEFAULT '0',
  `deleted_two` enum('0','1') NOT NULL DEFAULT '0',
  `sent_push` int(11) NOT NULL DEFAULT 0,
  `notification_id` varchar(50) NOT NULL DEFAULT '',
  `type_two` varchar(32) NOT NULL DEFAULT '',
  `stickers` mediumtext DEFAULT NULL,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `lat` varchar(200) NOT NULL DEFAULT '0',
  `lng` varchar(200) NOT NULL DEFAULT '0',
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `broadcast_id` int(11) NOT NULL DEFAULT 0,
  `forward` int(2) NOT NULL DEFAULT 0,
  `listening` int(11) NOT NULL DEFAULT 0,
  `remove_at` int(11) unsigned NOT NULL DEFAULT 0,
  `topic_id` int(11) DEFAULT NULL,
  `iv` varchar(255) DEFAULT NULL,
  `tag` varchar(255) DEFAULT NULL,
  `cipher_version` int(11) DEFAULT 1,
  `edited` int(11) DEFAULT 0 COMMENT '1 если сообщение было отредактировано',
  `signal_header` text DEFAULT NULL COMMENT 'JSON DR header for Signal-encrypted messages (cipher_version=3)',
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `to_id` (`to_id`),
  KEY `seen` (`seen`),
  KEY `time` (`time`),
  KEY `deleted_two` (`deleted_two`),
  KEY `deleted_one` (`deleted_one`),
  KEY `sent_push` (`sent_push`),
  KEY `group_id` (`group_id`),
  KEY `order1` (`from_id`,`id`),
  KEY `order2` (`group_id`,`id`),
  KEY `order3` (`to_id`,`id`),
  KEY `order7` (`seen`,`id`),
  KEY `order8` (`time`,`id`),
  KEY `order4` (`from_id`,`id`),
  KEY `order5` (`group_id`,`id`),
  KEY `order6` (`to_id`,`id`),
  KEY `reply_id` (`reply_id`),
  KEY `broadcast_id` (`broadcast_id`),
  KEY `story_id` (`story_id`),
  KEY `product_id` (`product_id`),
  KEY `notification_id` (`notification_id`),
  KEY `page_id` (`page_id`),
  KEY `page_id_2` (`page_id`),
  KEY `notification_id_2` (`notification_id`),
  KEY `product_id_2` (`product_id`),
  KEY `story_id_2` (`story_id`),
  KEY `reply_id_2` (`reply_id`),
  KEY `broadcast_id_2` (`broadcast_id`),
  KEY `forward` (`forward`),
  KEY `listening` (`listening`),
  KEY `remove_at` (`remove_at`),
  KEY `idx_text_preview` (`text_preview`(50)),
  KEY `idx_cipher_version` (`cipher_version`),
  KEY `idx_conv_time` (`from_id`,`to_id`,`time`),
  KEY `idx_toid_time` (`to_id`,`time`),
  KEY `idx_fromid_time` (`from_id`,`time`),
  KEY `idx_conv_seen` (`from_id`,`to_id`,`seen`)
) ENGINE=InnoDB AUTO_INCREMENT=1053 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MonetizationSubscription`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MonetizationSubscription` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(11) unsigned NOT NULL DEFAULT 0,
  `monetization_id` int(11) NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `last_payment_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expire` int(10) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MovieCommentReplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MovieCommentReplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comm_id` (`comm_id`),
  KEY `movie_id` (`movie_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_MovieComments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_MovieComments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `movie_id` (`movie_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Movies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Movies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `genre` varchar(50) NOT NULL DEFAULT '',
  `stars` varchar(300) NOT NULL DEFAULT '',
  `producer` varchar(100) NOT NULL DEFAULT '',
  `country` varchar(50) NOT NULL DEFAULT '',
  `release` year(4) DEFAULT NULL,
  `quality` varchar(10) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0,
  `description` mediumtext DEFAULT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-film.jpg',
  `source` varchar(1000) NOT NULL DEFAULT '',
  `iframe` varchar(1000) NOT NULL DEFAULT '',
  `video` varchar(3000) NOT NULL DEFAULT '',
  `views` int(11) NOT NULL DEFAULT 0,
  `rating` varchar(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `genre` (`genre`),
  KEY `country` (`country`),
  KEY `release` (`release`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Mute`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Mute` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `chat_id` int(11) NOT NULL DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `notify` varchar(5) NOT NULL DEFAULT 'yes',
  `call_chat` varchar(5) NOT NULL DEFAULT 'yes',
  `archive` varchar(5) NOT NULL DEFAULT 'no',
  `pin` varchar(5) NOT NULL DEFAULT 'no',
  `fav` varchar(11) NOT NULL DEFAULT 'no',
  `type` varchar(10) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `chat_id` (`chat_id`),
  KEY `message_id` (`message_id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`),
  KEY `user_id_2` (`user_id`),
  KEY `chat_id_2` (`chat_id`),
  KEY `message_id_2` (`message_id`),
  KEY `notify` (`notify`),
  KEY `type` (`type`),
  KEY `fav` (`fav`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Mute_Story`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Mute_Story` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `story_user_id` (`story_user_id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`),
  KEY `user_id_2` (`user_id`),
  KEY `story_user_id_2` (`story_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Notifications`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Notifications` (
  `id` int(255) NOT NULL AUTO_INCREMENT,
  `notifier_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) unsigned DEFAULT 0,
  `comment_id` int(11) unsigned DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `group_chat_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `seen_pop` int(11) NOT NULL DEFAULT 0,
  `type` varchar(255) NOT NULL DEFAULT '',
  `type2` varchar(32) NOT NULL DEFAULT '',
  `text` mediumtext DEFAULT NULL,
  `url` varchar(255) NOT NULL DEFAULT '',
  `full_link` varchar(1000) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `sent_push` int(11) NOT NULL DEFAULT 0,
  `admin` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `notifier_id` (`notifier_id`),
  KEY `user_id` (`recipient_id`),
  KEY `post_id` (`post_id`),
  KEY `seen` (`seen`),
  KEY `time` (`time`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`,`seen_pop`),
  KEY `sent_push` (`sent_push`),
  KEY `order1` (`seen`,`id`),
  KEY `order2` (`notifier_id`,`id`),
  KEY `order3` (`recipient_id`,`id`),
  KEY `order4` (`post_id`,`id`),
  KEY `order5` (`page_id`,`id`),
  KEY `order6` (`group_id`,`id`),
  KEY `order7` (`time`,`id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`),
  KEY `blog_id` (`blog_id`),
  KEY `story_id` (`story_id`),
  KEY `admin` (`admin`),
  KEY `group_chat_id` (`group_chat_id`),
  KEY `event_id` (`event_id`),
  KEY `thread_id` (`thread_id`)
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Offers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Offers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `discount_type` varchar(200) NOT NULL DEFAULT '',
  `discount_percent` int(11) NOT NULL DEFAULT 0,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `discounted_items` varchar(150) DEFAULT '',
  `buy` int(11) NOT NULL DEFAULT 0,
  `get_price` int(11) NOT NULL DEFAULT 0,
  `spend` int(11) NOT NULL DEFAULT 0,
  `amount_off` int(11) NOT NULL DEFAULT 0,
  `description` mediumtext DEFAULT NULL,
  `expire_date` date NOT NULL,
  `expire_time` time NOT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `currency` varchar(50) NOT NULL DEFAULT '',
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_id` (`page_id`),
  KEY `user_id` (`user_id`),
  KEY `spend` (`spend`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_PageAdmins`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_PageAdmins` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `info` int(11) NOT NULL DEFAULT 1,
  `social` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `design` int(11) NOT NULL DEFAULT 1,
  `admins` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_page` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_PageRating`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_PageRating` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `valuation` int(11) DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Pages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Pages` (
  `page_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_name` varchar(32) NOT NULL DEFAULT '',
  `page_title` varchar(32) NOT NULL DEFAULT '',
  `page_description` varchar(1000) NOT NULL DEFAULT '',
  `avatar` varchar(255) NOT NULL DEFAULT 'upload/photos/d-page.jpg',
  `cover` varchar(255) NOT NULL DEFAULT 'upload/photos/d-cover.jpg',
  `users_post` int(11) NOT NULL DEFAULT 0,
  `page_category` int(11) NOT NULL DEFAULT 1,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `website` varchar(255) NOT NULL DEFAULT '',
  `facebook` varchar(32) NOT NULL DEFAULT '',
  `google` varchar(32) NOT NULL DEFAULT '',
  `vk` varchar(32) NOT NULL DEFAULT '',
  `twitter` varchar(32) NOT NULL DEFAULT '',
  `linkedin` varchar(32) NOT NULL DEFAULT '',
  `company` varchar(32) NOT NULL DEFAULT '',
  `phone` varchar(32) NOT NULL DEFAULT '',
  `address` varchar(100) NOT NULL DEFAULT '',
  `call_action_type` int(11) NOT NULL DEFAULT 0,
  `call_action_type_url` varchar(255) NOT NULL DEFAULT '',
  `background_image` varchar(200) NOT NULL DEFAULT '',
  `background_image_status` int(11) NOT NULL DEFAULT 0,
  `instgram` varchar(32) NOT NULL DEFAULT '',
  `youtube` varchar(100) NOT NULL DEFAULT '',
  `verified` enum('0','1') NOT NULL DEFAULT '0',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `boosted` enum('0','1') NOT NULL DEFAULT '0',
  `time` int(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`page_id`),
  KEY `registered` (`registered`),
  KEY `user_id` (`user_id`),
  KEY `page_category` (`page_category`),
  KEY `active` (`active`),
  KEY `verified` (`verified`),
  KEY `boosted` (`boosted`),
  KEY `time` (`time`),
  KEY `page_name` (`page_name`),
  KEY `page_title` (`page_title`),
  KEY `sub_category` (`sub_category`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Pages_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Pages_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Pages_Invites`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Pages_Invites` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `inviter_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_id` (`page_id`,`inviter_id`,`invited_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Pages_Likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Pages_Likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_PatreonSubscribers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_PatreonSubscribers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `subscriber_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `subscriber_id` (`subscriber_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Payment_Transactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Payment_Transactions` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `userid` int(11) unsigned NOT NULL,
  `kind` varchar(100) NOT NULL,
  `amount` decimal(11,0) unsigned NOT NULL,
  `transaction_dt` timestamp NOT NULL DEFAULT current_timestamp(),
  `notes` mediumtext NOT NULL,
  `admin_commission` decimal(11,0) unsigned DEFAULT 0,
  `extra` varchar(1000) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `userid` (`userid`),
  KEY `kind` (`kind`),
  KEY `transaction_dt` (`transaction_dt`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Payments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Payments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 0,
  `type` varchar(15) NOT NULL DEFAULT '',
  `date` varchar(30) NOT NULL DEFAULT '',
  `time` int(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `date` (`date`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_PendingPayments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_PendingPayments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `payment_data` varchar(500) NOT NULL DEFAULT '',
  `method_name` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `payment_data` (`payment_data`),
  KEY `method_name` (`method_name`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_PinnedPosts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_PinnedPosts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `active` (`active`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Pokes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Pokes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `received_user_id` int(11) NOT NULL DEFAULT 0,
  `send_user_id` int(11) NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `received_user_id` (`received_user_id`),
  KEY `user_id` (`send_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Polls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Polls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Posts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Posts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `postText` text DEFAULT NULL,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `page_event_id` int(11) NOT NULL DEFAULT 0,
  `postLink` varchar(1000) NOT NULL DEFAULT '',
  `postLinkTitle` mediumtext DEFAULT NULL,
  `postLinkImage` varchar(100) NOT NULL DEFAULT '',
  `postLinkContent` mediumtext DEFAULT NULL,
  `postVimeo` varchar(100) NOT NULL DEFAULT '',
  `postDailymotion` varchar(100) NOT NULL DEFAULT '',
  `postFacebook` varchar(100) NOT NULL DEFAULT '',
  `postFile` varchar(255) NOT NULL DEFAULT '',
  `postFileName` varchar(200) NOT NULL DEFAULT '',
  `postFileThumb` varchar(3000) NOT NULL DEFAULT '',
  `postYoutube` varchar(255) NOT NULL DEFAULT '',
  `postVine` varchar(32) NOT NULL DEFAULT '',
  `postSoundCloud` varchar(255) NOT NULL DEFAULT '',
  `postPlaytube` varchar(500) NOT NULL DEFAULT '',
  `postDeepsound` varchar(500) NOT NULL DEFAULT '',
  `postMap` varchar(255) NOT NULL DEFAULT '',
  `postShare` int(11) NOT NULL DEFAULT 0,
  `postPrivacy` enum('0','1','2','3','4','5','6') NOT NULL DEFAULT '1',
  `postType` varchar(30) NOT NULL DEFAULT '',
  `postFeeling` varchar(255) NOT NULL DEFAULT '',
  `postListening` varchar(255) NOT NULL DEFAULT '',
  `postTraveling` varchar(255) NOT NULL DEFAULT '',
  `postWatching` varchar(255) NOT NULL DEFAULT '',
  `postPlaying` varchar(255) NOT NULL DEFAULT '',
  `postPhoto` varchar(3000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `album_name` varchar(52) NOT NULL DEFAULT '',
  `multi_image` enum('0','1') NOT NULL DEFAULT '0',
  `multi_image_post` int(11) NOT NULL DEFAULT 0,
  `boosted` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `poll_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `videoViews` int(11) NOT NULL DEFAULT 0,
  `postRecord` varchar(3000) NOT NULL DEFAULT '',
  `postSticker` mediumtext DEFAULT NULL,
  `shared_from` int(15) NOT NULL DEFAULT 0,
  `post_url` mediumtext DEFAULT NULL,
  `parent_id` int(15) NOT NULL DEFAULT 0,
  `cache` int(11) NOT NULL DEFAULT 0,
  `comments_status` int(11) NOT NULL DEFAULT 1,
  `blur` int(11) NOT NULL DEFAULT 0,
  `color_id` int(11) NOT NULL DEFAULT 0,
  `job_id` int(11) NOT NULL DEFAULT 0,
  `offer_id` int(11) NOT NULL DEFAULT 0,
  `fund_raise_id` int(11) NOT NULL DEFAULT 0,
  `fund_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 1,
  `stream_name` varchar(100) NOT NULL DEFAULT '',
  `agora_token` mediumtext DEFAULT NULL,
  `live_time` int(50) NOT NULL DEFAULT 0,
  `live_ended` int(11) NOT NULL DEFAULT 0,
  `agora_resource_id` mediumtext DEFAULT NULL,
  `agora_sid` varchar(500) NOT NULL DEFAULT '',
  `send_notify` varchar(11) NOT NULL DEFAULT '',
  `240p` int(2) NOT NULL DEFAULT 0,
  `360p` int(2) NOT NULL DEFAULT 0,
  `480p` int(2) NOT NULL DEFAULT 0,
  `720p` int(2) NOT NULL DEFAULT 0,
  `1080p` int(2) NOT NULL DEFAULT 0,
  `2048p` int(2) NOT NULL DEFAULT 0,
  `4096p` int(2) NOT NULL DEFAULT 0,
  `processing` int(2) NOT NULL DEFAULT 0,
  `ai_post` int(2) unsigned DEFAULT 0,
  `videoTitle` varchar(200) DEFAULT NULL,
  `is_reel` tinyint(4) DEFAULT 0,
  `blur_url` varchar(300) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `recipient_id` (`recipient_id`),
  KEY `postFile` (`postFile`),
  KEY `postShare` (`postShare`),
  KEY `postType` (`postType`),
  KEY `postYoutube` (`postYoutube`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`),
  KEY `registered` (`registered`),
  KEY `time` (`time`),
  KEY `boosted` (`boosted`),
  KEY `product_id` (`product_id`),
  KEY `poll_id` (`poll_id`),
  KEY `event_id` (`event_id`),
  KEY `videoViews` (`videoViews`),
  KEY `shared_from` (`shared_from`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`page_id`,`id`),
  KEY `order3` (`group_id`,`id`),
  KEY `order4` (`recipient_id`,`id`),
  KEY `order5` (`event_id`,`id`),
  KEY `order6` (`parent_id`,`id`),
  KEY `multi_image` (`multi_image`),
  KEY `album_name` (`album_name`),
  KEY `postFacebook` (`postFacebook`),
  KEY `postVimeo` (`postVimeo`),
  KEY `postDailymotion` (`postDailymotion`),
  KEY `postSoundCloud` (`postSoundCloud`),
  KEY `postYoutube_2` (`postYoutube`),
  KEY `fund_raise_id` (`fund_raise_id`),
  KEY `fund_id` (`fund_id`),
  KEY `offer_id` (`offer_id`),
  KEY `live_time` (`live_time`),
  KEY `live_ended` (`live_ended`),
  KEY `active` (`active`),
  KEY `job_id` (`job_id`),
  KEY `page_event_id` (`page_event_id`),
  KEY `blog_id` (`blog_id`),
  KEY `color_id` (`color_id`),
  KEY `thread_id` (`thread_id`),
  KEY `forum_id` (`forum_id`),
  KEY `processing` (`processing`)
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ProductReview`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ProductReview` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  `star` int(11) NOT NULL DEFAULT 1,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  KEY `star` (`star`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Products`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Products` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `category` int(11) NOT NULL DEFAULT 0,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `price` float NOT NULL DEFAULT 0,
  `location` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `type` enum('0','1') NOT NULL,
  `currency` varchar(40) NOT NULL DEFAULT 'USD',
  `lng` varchar(100) NOT NULL DEFAULT '0',
  `lat` varchar(100) NOT NULL DEFAULT '0',
  `units` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `category` (`category`),
  KEY `price` (`price`),
  KEY `status` (`status`),
  KEY `page_id` (`page_id`),
  KEY `active` (`active`),
  KEY `units` (`units`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Products_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Products_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Products_Media`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Products_Media` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ProfileFields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ProfileFields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` mediumtext DEFAULT NULL,
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(32) NOT NULL DEFAULT 'profile',
  `registration_page` int(11) NOT NULL DEFAULT 0,
  `profile_page` int(11) NOT NULL DEFAULT 0,
  `select_type` varchar(32) NOT NULL DEFAULT 'none',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `registration_page` (`registration_page`),
  KEY `active` (`active`),
  KEY `profile_page` (`profile_page`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Purchases`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Purchases` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `data` mediumtext DEFAULT NULL,
  `final_price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `timestamp` (`timestamp`),
  KEY `time` (`time`),
  KEY `owner_id` (`owner_id`),
  KEY `final_price` (`final_price`),
  KEY `order_hash_id` (`order_hash_id`),
  KEY `data` (`data`(768))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Reactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Reactions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) unsigned NOT NULL DEFAULT 0,
  `post_id` int(11) unsigned DEFAULT 0,
  `comment_id` int(11) unsigned DEFAULT 0,
  `replay_id` int(11) unsigned DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_reaction` (`reaction`),
  KEY `message_id` (`message_id`),
  KEY `message_id_2` (`message_id`),
  KEY `replay_id` (`replay_id`),
  KEY `story_id` (`story_id`),
  KEY `comment_id` (`comment_id`),
  KEY `comment_id_2` (`comment_id`),
  KEY `replay_id_2` (`replay_id`),
  KEY `message_id_3` (`message_id`),
  KEY `story_id_2` (`story_id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Reactions_Types`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Reactions_Types` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `wowonder_icon` varchar(300) NOT NULL DEFAULT '',
  `sunshine_icon` varchar(300) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_RecentSearches`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_RecentSearches` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `search_id` int(11) NOT NULL DEFAULT 0,
  `search_type` varchar(32) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`search_id`),
  KEY `search_type` (`search_type`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Refund`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Refund` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `pro_type` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `pro_type` (`pro_type`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Relationship`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Relationship` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `relationship` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `relationship` (`relationship`),
  KEY `active` (`active`),
  KEY `to_id` (`to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Reports`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Reports` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(15) unsigned NOT NULL DEFAULT 0,
  `profile_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(15) NOT NULL DEFAULT 0,
  `group_id` int(15) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `reason` varchar(100) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `seen` (`seen`),
  KEY `profile_id` (`profile_id`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`),
  KEY `comment_id` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_SavedPosts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_SavedPosts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_ScheduledPosts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_ScheduledPosts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) DEFAULT NULL,
  `channel_id` bigint(20) DEFAULT NULL,
  `author_id` bigint(20) NOT NULL,
  `text` text NOT NULL,
  `media_url` text DEFAULT NULL,
  `media_type` varchar(20) DEFAULT NULL,
  `scheduled_time` bigint(20) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `status` enum('scheduled','published','failed','cancelled') DEFAULT 'scheduled',
  `repeat_type` varchar(20) DEFAULT 'none',
  `is_pinned` tinyint(1) DEFAULT 0,
  `notify_members` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `group_id` (`group_id`),
  KEY `channel_id` (`channel_id`),
  KEY `status` (`status`),
  KEY `scheduled_time` (`scheduled_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `Wo_Stickers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Stickers` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_StoryComments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_StoryComments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `Wo_StoryReactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_StoryReactions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(30) NOT NULL DEFAULT 'like' COMMENT 'Reaction type: like, love, haha, wow, sad, angry',
  `time` int(11) NOT NULL DEFAULT 0 COMMENT 'Unix timestamp when reaction was added',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_story_user` (`story_id`,`user_id`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_reaction` (`reaction`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Реакции на stories';

DROP TABLE IF EXISTS `Wo_Story_Seen`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Story_Seen` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `time` varchar(20) NOT NULL DEFAULT '0',
  `anonymous_hash` varchar(64) DEFAULT NULL COMMENT 'SHA-256(IP+UA+story_id) для анонімних переглядів (user_id=0)',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `story_id` (`story_id`),
  KEY `idx_story_user` (`story_id`,`user_id`),
  KEY `idx_anon_hash` (`story_id`,`anonymous_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Sub_Categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Sub_Categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `category_id` int(11) NOT NULL DEFAULT 0,
  `lang_key` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(200) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `category_id` (`category_id`),
  KEY `lang_key` (`lang_key`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Subgroups`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Subgroups` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_group_id` bigint(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `icon_emoji` varchar(10) DEFAULT NULL,
  `color` varchar(10) DEFAULT '#2196F3',
  `is_private` tinyint(1) DEFAULT 0,
  `is_closed` tinyint(1) DEFAULT 0,
  `created_by` bigint(20) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `last_message_time` bigint(20) DEFAULT NULL,
  `pinned_message_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `parent_group_id` (`parent_group_id`),
  KEY `is_closed` (`is_closed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `Wo_Terms`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Terms` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(32) NOT NULL DEFAULT '',
  `text` longtext DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Tokens`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Tokens` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `app_id` int(11) NOT NULL DEFAULT 0,
  `token` varchar(200) NOT NULL DEFAULT '',
  `time` int(32) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `user_id_2` (`user_id`),
  KEY `app_id` (`app_id`),
  KEY `token` (`token`)
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UploadedMedia`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UploadedMedia` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `filename` varchar(200) NOT NULL DEFAULT '',
  `storage` varchar(34) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `filename` (`filename`),
  KEY `time` (`time`),
  KEY `filename_2` (`filename`,`storage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserAddress`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserAddress` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `phone` varchar(50) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `city` varchar(100) NOT NULL DEFAULT '',
  `zip` varchar(20) NOT NULL DEFAULT '',
  `address` varchar(500) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserAds`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserAds` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `url` varchar(3000) NOT NULL DEFAULT '',
  `headline` varchar(200) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `location` varchar(1000) NOT NULL DEFAULT 'us',
  `audience` longtext DEFAULT NULL,
  `ad_media` varchar(3000) NOT NULL DEFAULT '',
  `gender` varchar(15) NOT NULL DEFAULT 'all',
  `bidding` varchar(15) NOT NULL DEFAULT '',
  `clicks` int(15) NOT NULL DEFAULT 0,
  `views` int(15) NOT NULL DEFAULT 0,
  `posted` varchar(15) NOT NULL DEFAULT '',
  `status` int(1) NOT NULL DEFAULT 1,
  `appears` varchar(10) NOT NULL DEFAULT 'post',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` varchar(50) NOT NULL DEFAULT '',
  `start` varchar(50) NOT NULL DEFAULT '',
  `end` varchar(50) NOT NULL DEFAULT '',
  `budget` float unsigned NOT NULL DEFAULT 0,
  `spent` float unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `appears` (`appears`),
  KEY `user_id` (`user_id`),
  KEY `location` (`location`(255)),
  KEY `gender` (`gender`),
  KEY `status` (`status`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserAds_Data`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserAds_Data` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `ad_id` int(11) NOT NULL DEFAULT 0,
  `clicks` int(15) NOT NULL DEFAULT 0,
  `views` int(15) NOT NULL DEFAULT 0,
  `spend` float unsigned NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `ad_id` (`ad_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserCard`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserCard` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  KEY `units` (`units`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserCertification`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserCertification` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `issuing_organization` varchar(100) NOT NULL DEFAULT '',
  `credential_id` varchar(100) NOT NULL DEFAULT '',
  `credential_url` varchar(300) NOT NULL DEFAULT '',
  `certification_start` date NOT NULL,
  `certification_end` date NOT NULL,
  `pdf` varchar(300) NOT NULL DEFAULT '',
  `filename` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserCloudBackupSettings`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserCloudBackupSettings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `mobile_photos` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_videos` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_files` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_videos_limit` int(11) NOT NULL DEFAULT 10,
  `mobile_files_limit` int(11) NOT NULL DEFAULT 10,
  `wifi_photos` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_videos` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_files` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_videos_limit` int(11) NOT NULL DEFAULT 100,
  `wifi_files_limit` int(11) NOT NULL DEFAULT 100,
  `roaming_photos` tinyint(1) NOT NULL DEFAULT 0,
  `save_to_gallery_private_chats` tinyint(1) NOT NULL DEFAULT 0,
  `save_to_gallery_groups` tinyint(1) NOT NULL DEFAULT 0,
  `save_to_gallery_channels` tinyint(1) NOT NULL DEFAULT 0,
  `streaming_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `cache_size_limit` bigint(20) NOT NULL DEFAULT 3221225472,
  `backup_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `backup_provider` varchar(50) NOT NULL DEFAULT 'local_server',
  `backup_frequency` varchar(50) NOT NULL DEFAULT 'daily',
  `last_backup_time` bigint(20) DEFAULT NULL,
  `proxy_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `proxy_host` varchar(255) DEFAULT NULL,
  `proxy_port` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_backup_provider` (`backup_provider`),
  KEY `idx_backup_enabled` (`backup_enabled`),
  CONSTRAINT `fk_cloud_backup_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cloud_backup_user_id` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserExperience`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserExperience` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `title` varchar(150) NOT NULL DEFAULT '',
  `location` varchar(100) NOT NULL DEFAULT '',
  `experience_start` date NOT NULL,
  `experience_end` date NOT NULL,
  `industry` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `link` varchar(300) NOT NULL DEFAULT '',
  `headline` varchar(150) NOT NULL DEFAULT '',
  `company_name` varchar(100) NOT NULL DEFAULT '',
  `employment_type` varchar(11) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserFields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserFields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=680 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserLanguages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserLanguages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(200) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `lang_key` (`lang_key`)
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserMediaSettings`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserMediaSettings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `auto_download_photos` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `auto_download_videos` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `auto_download_audio` enum('wifi_only','always','never') NOT NULL DEFAULT 'always',
  `auto_download_documents` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `compress_photos` tinyint(1) NOT NULL DEFAULT 1,
  `compress_videos` tinyint(1) NOT NULL DEFAULT 1,
  `backup_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `last_backup_time` int(11) DEFAULT NULL,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `backup_enabled` (`backup_enabled`),
  CONSTRAINT `fk_media_settings_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserMonetization`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserMonetization` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(11) unsigned NOT NULL DEFAULT 0,
  `price` varchar(11) NOT NULL DEFAULT '0',
  `currency` varchar(5) NOT NULL DEFAULT 'USD',
  `paid_every` int(3) NOT NULL DEFAULT 1,
  `period` varchar(10) NOT NULL DEFAULT 'Daily',
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserOpenTo`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserOpenTo` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `job_title` varchar(100) NOT NULL DEFAULT '',
  `job_location` varchar(100) NOT NULL DEFAULT '',
  `workplaces` varchar(600) NOT NULL DEFAULT '',
  `job_type` varchar(600) NOT NULL DEFAULT '',
  `services` varchar(1000) NOT NULL DEFAULT '',
  `description` varchar(1000) NOT NULL DEFAULT '',
  `type` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `job_title` (`job_title`),
  KEY `job_location` (`job_location`),
  KEY `workplaces` (`workplaces`),
  KEY `job_type` (`job_type`),
  KEY `type` (`type`),
  KEY `time` (`time`),
  KEY `services` (`services`(768)),
  KEY `description` (`description`(768))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserOrders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserOrders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash_id` varchar(100) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_owner_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `address_id` int(11) NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `final_price` float NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0,
  `tracking_url` varchar(500) NOT NULL DEFAULT '',
  `tracking_id` varchar(50) NOT NULL DEFAULT '',
  `status` varchar(30) NOT NULL DEFAULT 'placed',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_owner_id` (`product_owner_id`),
  KEY `product_id` (`product_id`),
  KEY `final_price` (`final_price`),
  KEY `status` (`status`),
  KEY `time` (`time`),
  KEY `hash_id` (`hash_id`),
  KEY `units` (`units`),
  KEY `address_id` (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserProjects`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserProjects` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` varchar(600) NOT NULL DEFAULT '',
  `associated_with` varchar(200) NOT NULL DEFAULT '',
  `project_url` varchar(300) NOT NULL DEFAULT '',
  `project_start` date NOT NULL,
  `project_end` date NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserRatingSummary`;

SET @saved_cs_client     = @@character_set_client;

SET character_set_client = utf8mb4;

SET character_set_client = @saved_cs_client;

DROP TABLE IF EXISTS `Wo_UserRatings`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserRatings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rater_id` int(11) NOT NULL COMMENT 'ID користувача, який ставить оцінку',
  `rated_user_id` int(11) NOT NULL COMMENT 'ID користувача, якому ставлять оцінку',
  `rating_type` enum('like','dislike') NOT NULL COMMENT 'Тип оцінки: like (?) або dislike (?)',
  `comment` text DEFAULT NULL COMMENT 'Необов`язковий коментар до оцінки',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_rating` (`rater_id`,`rated_user_id`) COMMENT 'Один користувач може поставити лише одну оцінку',
  KEY `idx_rated_user` (`rated_user_id`),
  KEY `idx_rating_type` (`rating_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_rated_user` FOREIGN KEY (`rated_user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rater` FOREIGN KEY (`rater_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Система оцінки користувачів (карма/довіра)';

DROP TABLE IF EXISTS `Wo_UserSkills`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserSkills` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(300) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserStory`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserStory` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(50) NOT NULL DEFAULT 0,
  `page_id` int(11) DEFAULT NULL,
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `posted` varchar(50) NOT NULL DEFAULT '',
  `expire` varchar(100) DEFAULT '',
  `thumbnail` varchar(100) NOT NULL DEFAULT '',
  `ad_id` int(11) DEFAULT NULL,
  `comment_count` int(11) NOT NULL DEFAULT 0 COMMENT 'Cached comment count',
  `reaction_count` int(11) NOT NULL DEFAULT 0 COMMENT 'Cached total reaction count',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `expires` (`expire`),
  KEY `Wo_UserStory_Wo_UserAds_id_fk` (`ad_id`),
  KEY `idx_user_expire` (`user_id`,`expire`),
  KEY `idx_posted` (`posted`),
  KEY `idx_comment_count` (`comment_count`),
  KEY `idx_reaction_count` (`reaction_count`),
  KEY `idx_page_id` (`page_id`),
  CONSTRAINT `Wo_UserStory_Wo_UserAds_id_fk` FOREIGN KEY (`ad_id`) REFERENCES `Wo_UserAds` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserStoryMedia`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserStoryMedia` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(30) NOT NULL DEFAULT 0,
  `type` varchar(30) NOT NULL DEFAULT '',
  `filename` mediumtext DEFAULT NULL,
  `expire` varchar(100) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0 COMMENT 'Video duration in seconds',
  PRIMARY KEY (`id`),
  KEY `expire` (`expire`),
  KEY `story_id` (`story_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UserTiers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UserTiers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `title` varchar(200) NOT NULL DEFAULT '',
  `price` float NOT NULL DEFAULT 0,
  `image` varchar(400) NOT NULL DEFAULT '',
  `description` varchar(1000) NOT NULL DEFAULT '',
  `chat` varchar(100) NOT NULL DEFAULT '',
  `live_stream` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `chat` (`chat`),
  KEY `live_stream` (`live_stream`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_User_Gifts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_User_Gifts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from` int(11) NOT NULL DEFAULT 0,
  `to` int(11) NOT NULL DEFAULT 0,
  `gift_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `from` (`from`),
  KEY `to` (`to`),
  KEY `gift_id` (`gift_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Users`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Users` (
  `user_id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(32) NOT NULL DEFAULT '',
  `email` varchar(255) NOT NULL DEFAULT '',
  `password` varchar(70) NOT NULL DEFAULT '',
  `first_name` varchar(60) NOT NULL DEFAULT '',
  `last_name` varchar(32) NOT NULL DEFAULT '',
  `avatar` varchar(100) NOT NULL DEFAULT 'upload/photos/d-avatar.jpg',
  `cover` varchar(100) NOT NULL DEFAULT 'upload/photos/d-cover.jpg',
  `background_image` varchar(100) NOT NULL DEFAULT '',
  `background_image_status` enum('0','1') NOT NULL DEFAULT '0',
  `relationship_id` int(11) NOT NULL DEFAULT 0,
  `address` varchar(100) NOT NULL DEFAULT '',
  `working` varchar(32) NOT NULL DEFAULT '',
  `working_link` varchar(32) NOT NULL DEFAULT '',
  `about` mediumtext DEFAULT NULL,
  `school` varchar(32) NOT NULL DEFAULT '',
  `gender` varchar(32) NOT NULL DEFAULT 'male',
  `birthday` varchar(50) NOT NULL DEFAULT '0000-00-00',
  `country_id` int(11) NOT NULL DEFAULT 0,
  `website` varchar(50) NOT NULL DEFAULT '',
  `facebook` varchar(50) NOT NULL DEFAULT '',
  `google` varchar(50) NOT NULL DEFAULT '',
  `twitter` varchar(50) NOT NULL DEFAULT '',
  `linkedin` varchar(32) NOT NULL DEFAULT '',
  `youtube` varchar(100) NOT NULL DEFAULT '',
  `vk` varchar(32) NOT NULL DEFAULT '',
  `instagram` varchar(32) NOT NULL DEFAULT '',
  `qq` mediumtext DEFAULT NULL,
  `wechat` mediumtext DEFAULT NULL,
  `discord` mediumtext DEFAULT NULL,
  `mailru` mediumtext DEFAULT NULL,
  `okru` varchar(30) NOT NULL DEFAULT '',
  `language` varchar(31) NOT NULL DEFAULT 'english',
  `email_code` varchar(32) NOT NULL DEFAULT '',
  `src` varchar(32) NOT NULL DEFAULT 'Undefined',
  `ip_address` varchar(100) DEFAULT '',
  `follow_privacy` enum('1','0') NOT NULL DEFAULT '0',
  `friend_privacy` enum('0','1','2','3') NOT NULL DEFAULT '0',
  `post_privacy` varchar(255) NOT NULL DEFAULT 'ifollow',
  `message_privacy` enum('1','0','2') NOT NULL DEFAULT '0',
  `confirm_followers` enum('1','0') NOT NULL DEFAULT '0',
  `show_activities_privacy` enum('0','1') NOT NULL DEFAULT '1',
  `birth_privacy` enum('0','1','2') NOT NULL DEFAULT '0',
  `visit_privacy` enum('0','1') NOT NULL DEFAULT '0',
  `verified` enum('1','0') NOT NULL DEFAULT '0',
  `lastseen` int(32) NOT NULL DEFAULT 0,
  `showlastseen` enum('1','0') NOT NULL DEFAULT '1',
  `emailNotification` enum('1','0') NOT NULL DEFAULT '1',
  `e_liked` enum('0','1') NOT NULL DEFAULT '1',
  `e_wondered` enum('0','1') NOT NULL DEFAULT '1',
  `e_shared` enum('0','1') NOT NULL DEFAULT '1',
  `e_followed` enum('0','1') NOT NULL DEFAULT '1',
  `e_commented` enum('0','1') NOT NULL DEFAULT '1',
  `e_visited` enum('0','1') NOT NULL DEFAULT '1',
  `e_liked_page` enum('0','1') NOT NULL DEFAULT '1',
  `e_mentioned` enum('0','1') NOT NULL DEFAULT '1',
  `e_joined_group` enum('0','1') NOT NULL DEFAULT '1',
  `e_accepted` enum('0','1') NOT NULL DEFAULT '1',
  `e_profile_wall_post` enum('0','1') NOT NULL DEFAULT '1',
  `e_sentme_msg` enum('0','1') NOT NULL DEFAULT '0',
  `e_last_notif` varchar(50) NOT NULL DEFAULT '0',
  `notification_settings` varchar(400) NOT NULL DEFAULT '{"e_liked":1,"e_shared":1,"e_wondered":0,"e_commented":1,"e_followed":1,"e_accepted":1,"e_mentioned":1,"e_joined_group":1,"e_liked_page":1,"e_visited":1,"e_profile_wall_post":1,"e_memory":1}',
  `status` enum('1','0') NOT NULL DEFAULT '0',
  `active` enum('0','1','2') NOT NULL DEFAULT '0',
  `admin` enum('0','1','2') NOT NULL DEFAULT '0',
  `type` varchar(11) NOT NULL DEFAULT 'user',
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `start_up` enum('0','1') NOT NULL DEFAULT '0',
  `start_up_info` enum('0','1') NOT NULL DEFAULT '0',
  `startup_follow` enum('0','1') NOT NULL DEFAULT '0',
  `startup_image` enum('0','1') NOT NULL DEFAULT '0',
  `last_email_sent` int(32) NOT NULL DEFAULT 0,
  `phone_number` varchar(32) NOT NULL DEFAULT '',
  `sms_code` int(11) NOT NULL DEFAULT 0,
  `is_pro` enum('0','1') NOT NULL DEFAULT '0',
  `pro_time` int(11) NOT NULL DEFAULT 0,
  `pro_type` int(11) NOT NULL DEFAULT 0,
  `pro_remainder` varchar(20) NOT NULL DEFAULT '',
  `joined` int(11) NOT NULL DEFAULT 0,
  `css_file` varchar(100) NOT NULL DEFAULT '',
  `timezone` varchar(50) NOT NULL DEFAULT '',
  `referrer` int(11) NOT NULL DEFAULT 0,
  `ref_user_id` int(11) NOT NULL DEFAULT 0,
  `ref_level` mediumtext DEFAULT NULL,
  `balance` varchar(100) NOT NULL DEFAULT '0',
  `paypal_email` varchar(100) NOT NULL DEFAULT '',
  `notifications_sound` enum('0','1') NOT NULL DEFAULT '0',
  `order_posts_by` enum('0','1') NOT NULL DEFAULT '1',
  `social_login` enum('0','1') NOT NULL DEFAULT '0',
  `android_m_device_id` varchar(50) NOT NULL DEFAULT '',
  `ios_m_device_id` varchar(50) NOT NULL DEFAULT '',
  `android_n_device_id` varchar(50) NOT NULL DEFAULT '',
  `ios_n_device_id` varchar(50) NOT NULL DEFAULT '',
  `web_device_id` varchar(100) NOT NULL DEFAULT '',
  `wallet` varchar(20) NOT NULL DEFAULT '0.00',
  `lat` varchar(200) NOT NULL DEFAULT '0',
  `lng` varchar(200) NOT NULL DEFAULT '0',
  `last_location_update` varchar(30) NOT NULL DEFAULT '0',
  `share_my_location` int(11) NOT NULL DEFAULT 1,
  `last_data_update` int(11) NOT NULL DEFAULT 0,
  `details` varchar(300) NOT NULL DEFAULT '{"post_count":0,"album_count":0,"following_count":0,"followers_count":0,"groups_count":0,"likes_count":0}',
  `sidebar_data` mediumtext DEFAULT NULL,
  `last_avatar_mod` int(11) NOT NULL DEFAULT 0,
  `last_cover_mod` int(11) NOT NULL DEFAULT 0,
  `points` float unsigned NOT NULL DEFAULT 0,
  `daily_points` int(11) NOT NULL DEFAULT 0,
  `converted_points` float unsigned NOT NULL DEFAULT 0,
  `point_day_expire` varchar(50) NOT NULL DEFAULT '',
  `last_follow_id` int(11) NOT NULL DEFAULT 0,
  `share_my_data` int(11) NOT NULL DEFAULT 1,
  `last_login_data` mediumtext DEFAULT NULL,
  `two_factor` int(11) NOT NULL DEFAULT 0,
  `two_factor_hash` varchar(50) NOT NULL DEFAULT '',
  `new_email` varchar(255) NOT NULL DEFAULT '',
  `two_factor_verified` int(11) NOT NULL DEFAULT 0,
  `new_phone` varchar(32) NOT NULL DEFAULT '',
  `info_file` varchar(300) NOT NULL DEFAULT '',
  `city` varchar(50) NOT NULL DEFAULT '',
  `state` varchar(50) NOT NULL DEFAULT '',
  `zip` varchar(11) NOT NULL DEFAULT '',
  `school_completed` int(11) NOT NULL DEFAULT 0,
  `weather_unit` varchar(11) NOT NULL DEFAULT 'us',
  `paystack_ref` varchar(100) NOT NULL DEFAULT '',
  `code_sent` int(11) NOT NULL DEFAULT 0,
  `time_code_sent` int(11) NOT NULL DEFAULT 0,
  `permission` mediumtext DEFAULT NULL,
  `skills` mediumtext DEFAULT NULL,
  `languages` mediumtext DEFAULT NULL,
  `currently_working` varchar(50) NOT NULL DEFAULT '',
  `banned` int(5) NOT NULL DEFAULT 0,
  `banned_reason` varchar(500) NOT NULL DEFAULT '',
  `credits` float DEFAULT 0,
  `authy_id` varchar(100) NOT NULL DEFAULT '',
  `google_secret` varchar(100) NOT NULL DEFAULT '',
  `two_factor_method` varchar(50) NOT NULL DEFAULT 'two_factor',
  `phone_privacy` enum('0','1','2') NOT NULL DEFAULT '0',
  `have_monetization` int(10) NOT NULL DEFAULT 0,
  `rating_likes` int(11) NOT NULL DEFAULT 0 COMMENT 'Кількість позитивних оцінок (?)',
  `rating_dislikes` int(11) NOT NULL DEFAULT 0 COMMENT 'Кількість негативних оцінок (?)',
  `rating_score` decimal(4,2) NOT NULL DEFAULT 0.00 COMMENT 'Загальний рейтинг (від -100 до +100)',
  `trust_level` enum('untrusted','neutral','trusted','verified') NOT NULL DEFAULT 'neutral' COMMENT 'Рівень довіри',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `active` (`active`),
  KEY `admin` (`admin`),
  KEY `src` (`src`),
  KEY `gender` (`gender`),
  KEY `avatar` (`avatar`),
  KEY `first_name` (`first_name`),
  KEY `last_name` (`last_name`),
  KEY `registered` (`registered`),
  KEY `joined` (`joined`),
  KEY `phone_number` (`phone_number`) USING BTREE,
  KEY `referrer` (`referrer`),
  KEY `wallet` (`wallet`),
  KEY `friend_privacy` (`friend_privacy`),
  KEY `lat` (`lat`),
  KEY `lng` (`lng`),
  KEY `order1` (`username`,`user_id`),
  KEY `order2` (`email`,`user_id`),
  KEY `order3` (`lastseen`,`user_id`),
  KEY `order4` (`active`,`user_id`),
  KEY `last_data_update` (`last_data_update`),
  KEY `points` (`points`),
  KEY `paystack_ref` (`paystack_ref`),
  KEY `relationship_id` (`relationship_id`),
  KEY `post_privacy` (`post_privacy`),
  KEY `email_code` (`email_code`),
  KEY `password` (`password`),
  KEY `status` (`status`),
  KEY `type` (`type`),
  KEY `is_pro` (`is_pro`),
  KEY `ref_user_id` (`ref_user_id`),
  KEY `currently_working` (`currently_working`),
  KEY `banned` (`banned`),
  KEY `two_factor_hash` (`two_factor_hash`),
  KEY `pro_remainder` (`pro_remainder`),
  KEY `converted_points` (`converted_points`),
  KEY `idx_rating_score` (`rating_score`),
  KEY `idx_trust_level` (`trust_level`),
  KEY `idx_pro_time` (`pro_time`)
) ENGINE=InnoDB AUTO_INCREMENT=695 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_UsersChat`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_UsersChat` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `conversation_user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `color` varchar(100) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'chat',
  `disappearing_time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `conversation_user_id` (`conversation_user_id`),
  KEY `time` (`time`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`user_id`,`id`),
  KEY `order3` (`conversation_user_id`,`id`),
  KEY `order4` (`conversation_user_id`,`id`),
  KEY `page_id` (`page_id`),
  KEY `color` (`color`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_Verification_Requests`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Verification_Requests` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `message` mediumtext DEFAULT NULL,
  `user_name` varchar(150) NOT NULL DEFAULT '',
  `passport` varchar(3000) NOT NULL DEFAULT '',
  `photo` varchar(3000) NOT NULL DEFAULT '',
  `type` varchar(32) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `Wo_VideoCalles`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_VideoCalles` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `access_token` text DEFAULT NULL,
  `access_token_2` text DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `to_id` (`to_id`),
  KEY `from_id` (`from_id`),
  KEY `called` (`called`),
  KEY `declined` (`declined`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

DROP TABLE IF EXISTS `Wo_Votes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Votes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `option_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `option_id` (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

DROP TABLE IF EXISTS `Wo_Wonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `Wo_Wonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `type` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

DROP TABLE IF EXISTS `bank_receipts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `bank_receipts` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned NOT NULL DEFAULT 0,
  `fund_id` int(11) NOT NULL DEFAULT 0,
  `description` text NOT NULL,
  `price` varchar(50) NOT NULL DEFAULT '0',
  `mode` varchar(50) NOT NULL DEFAULT '',
  `approved` int(10) unsigned NOT NULL DEFAULT 0,
  `receipt_file` varchar(250) NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `approved_at` int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `fund_id` (`fund_id`),
  KEY `created_at` (`created_at`),
  KEY `approved_at` (`approved_at`),
  KEY `approved` (`approved`),
  KEY `mode` (`mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `broadcast`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `broadcast` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `image` varchar(150) DEFAULT 'upload/photos/d-group.jpg',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`),
  KEY `user_id_2` (`user_id`),
  KEY `time_2` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `broadcast_users`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `broadcast_users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `broadcast_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `broadcast_id` (`broadcast_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `signal_group_sender_keys`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `signal_group_sender_keys` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Первинний ключ',
  `group_id` int(10) unsigned NOT NULL COMMENT 'ID групового чату (wo_groupchat.group_id)',
  `sender_id` int(10) unsigned NOT NULL COMMENT 'ID учасника, що розповсюджує свій SenderKey',
  `recipient_id` int(10) unsigned NOT NULL COMMENT 'ID учасника, якому призначений цей payload',
  `distribution` mediumtext NOT NULL COMMENT 'Зашифрований SenderKeyDistributionMessage (Base64). Розшифровує лише recipient.',
  `delivered` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0 = очікує доставки, 1 = доставлено',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT 'Час створення запису',
  `delivered_at` timestamp NULL DEFAULT NULL COMMENT 'Час підтвердження доставки',
  PRIMARY KEY (`id`),
  KEY `idx_recipient_group` (`recipient_id`,`group_id`,`delivered`),
  KEY `idx_sender_group` (`sender_id`,`group_id`),
  KEY `idx_delivered_at` (`delivered_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Signal Protocol — SenderKey distributions для групових чатів (store-and-forward)';

DROP TABLE IF EXISTS `signal_keys`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `signal_keys` (
  `user_id` int(10) unsigned NOT NULL,
  `identity_key` varchar(64) NOT NULL DEFAULT '' COMMENT 'Base64 X25519 identity public key (32 bytes)',
  `signed_prekey_id` int(10) unsigned NOT NULL DEFAULT 0 COMMENT 'Integer ID of current signed pre-key',
  `signed_prekey` varchar(64) NOT NULL DEFAULT '' COMMENT 'Base64 X25519 signed pre-key public (32 bytes)',
  `signed_prekey_sig` varchar(128) NOT NULL DEFAULT '' COMMENT 'Base64 Ed25519 signature over signed_prekey (64 bytes)',
  `prekeys` mediumtext DEFAULT '[]' COMMENT 'JSON array of one-time pre-keys: [{id,key}]',
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_id`),
  KEY `idx_signal_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Signal Protocol — X3DH public key bundles per user';

DROP TABLE IF EXISTS `sticker_packs`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `sticker_packs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `icon_url` varchar(500) DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `sticker_count` int(11) DEFAULT 0,
  `is_active` tinyint(1) DEFAULT 0,
  `is_animated` tinyint(1) DEFAULT 0,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `stickers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `stickers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `pack_id` int(11) DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `emoji` varchar(10) DEFAULT NULL,
  `keywords` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`keywords`)),
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `file_size` int(11) DEFAULT NULL,
  `format` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `pack_id` (`pack_id`),
  CONSTRAINT `stickers_ibfk_1` FOREIGN KEY (`pack_id`) REFERENCES `sticker_packs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `wm_channel_livestream_viewers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wm_channel_livestream_viewers` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `stream_id` int(11) unsigned NOT NULL,
  `user_id` int(11) NOT NULL,
  `joined_at` datetime NOT NULL DEFAULT current_timestamp(),
  `left_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stream_id` (`stream_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wm_channel_livestreams`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wm_channel_livestreams` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `channel_id` int(11) NOT NULL COMMENT 'wo_pages.page_id',
  `host_user_id` int(11) NOT NULL COMMENT 'who started the stream',
  `room_name` varchar(120) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `quality` varchar(10) NOT NULL DEFAULT '720p' COMMENT '240p|360p|480p|720p|1080p|1080p60',
  `is_premium` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1 = channel has premium',
  `status` enum('live','ended') NOT NULL DEFAULT 'live',
  `viewer_count` int(11) NOT NULL DEFAULT 0,
  `peak_viewers` int(11) NOT NULL DEFAULT 0,
  `started_at` datetime NOT NULL DEFAULT current_timestamp(),
  `ended_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `room_name` (`room_name`),
  KEY `idx_channel_id` (`channel_id`),
  KEY `idx_host_user_id` (`host_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_started_at` (`started_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wm_channel_subscription_payments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wm_channel_subscription_payments` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `channel_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `order_id` varchar(64) NOT NULL,
  `provider` enum('wayforpay','liqpay') NOT NULL,
  `plan` enum('monthly','quarterly','annual') NOT NULL,
  `amount_uah` decimal(10,2) NOT NULL,
  `status` enum('pending','success','failed','refunded') NOT NULL DEFAULT 'pending',
  `raw_response` text DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_id` (`order_id`),
  KEY `idx_channel_id` (`channel_id`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wm_channel_subscriptions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wm_channel_subscriptions` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `channel_id` int(11) NOT NULL COMMENT 'wo_pages.page_id',
  `plan` enum('monthly','quarterly','annual') NOT NULL DEFAULT 'monthly',
  `is_active` tinyint(1) NOT NULL DEFAULT 0,
  `started_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `channel_id` (`channel_id`),
  KEY `idx_channel_id` (`channel_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wm_subscription_payments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wm_subscription_payments` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(11) unsigned NOT NULL,
  `order_id` varchar(64) NOT NULL,
  `provider` enum('wayforpay','liqpay') NOT NULL,
  `months` tinyint(3) unsigned NOT NULL DEFAULT 1,
  `amount_uah` decimal(10,2) NOT NULL,
  `status` enum('pending','success','failed') NOT NULL DEFAULT 'pending',
  `raw_response` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_activities`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_activities` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(10) unsigned DEFAULT 0,
  `comment_id` int(10) unsigned DEFAULT 0,
  `follow_id` int(11) NOT NULL DEFAULT 0,
  `activity_type` varchar(32) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `activity_type` (`activity_type`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`post_id`,`id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`),
  KEY `follow_id` (`follow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_admininvitations`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_admininvitations` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(300) NOT NULL DEFAULT '0',
  `posted` varchar(50) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `code` (`code`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_ads`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_ads` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(32) NOT NULL DEFAULT '',
  `code` mediumtext DEFAULT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_affiliates_requests`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_affiliates_requests` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(100) NOT NULL DEFAULT '0',
  `full_amount` varchar(100) NOT NULL DEFAULT '',
  `iban` varchar(250) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `full_name` varchar(150) NOT NULL DEFAULT '',
  `swift_code` varchar(300) NOT NULL DEFAULT '',
  `address` varchar(600) NOT NULL DEFAULT '',
  `type` varchar(100) NOT NULL DEFAULT '',
  `transfer_info` varchar(600) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `time` (`time`),
  KEY `status` (`status`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_agoravideocall`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_agoravideocall` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `type` varchar(50) NOT NULL DEFAULT 'video',
  `room_name` varchar(50) NOT NULL DEFAULT '0',
  `time` int(11) NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `access_token` mediumtext DEFAULT NULL,
  `access_token_2` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `to_id` (`to_id`),
  KEY `type` (`type`),
  KEY `room_name` (`room_name`),
  KEY `time` (`time`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_albums_media`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_albums_media` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `review_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `order1` (`post_id`,`id`),
  KEY `parent_id` (`parent_id`),
  KEY `review_id` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_announcement`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_announcement` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `text` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_announcement_views`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_announcement_views` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `announcement_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `announcement_id` (`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_apps`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_apps` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_user_id` int(11) NOT NULL DEFAULT 0,
  `app_name` varchar(32) NOT NULL,
  `app_website_url` varchar(55) NOT NULL,
  `app_description` mediumtext NOT NULL,
  `app_avatar` varchar(100) NOT NULL DEFAULT 'upload/photos/app-default-icon.png',
  `app_callback_url` varchar(255) NOT NULL,
  `app_id` varchar(32) NOT NULL,
  `app_secret` varchar(55) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `app_user_id` (`app_user_id`),
  KEY `app_id` (`app_id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_apps_hash`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_apps_hash` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash_id` varchar(200) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `hash_id` (`hash_id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_apps_permission`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_apps_permission` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `app_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_appssessions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_appssessions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `session_id` varchar(120) NOT NULL DEFAULT '',
  `platform` varchar(32) NOT NULL DEFAULT '',
  `platform_details` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_id` (`session_id`),
  KEY `user_id` (`user_id`),
  KEY `platform` (`platform`),
  KEY `time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=79 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_audiocalls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_audiocalls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `call_id` varchar(30) NOT NULL DEFAULT '0',
  `access_token` mediumtext DEFAULT NULL,
  `call_id_2` varchar(30) NOT NULL DEFAULT '',
  `access_token_2` mediumtext DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `to_id` (`to_id`),
  KEY `from_id` (`from_id`),
  KEY `call_id` (`call_id`),
  KEY `called` (`called`),
  KEY `declined` (`declined`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_backup_codes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_backup_codes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `codes` varchar(500) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_bad_login`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_bad_login` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ip` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ip` (`ip`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_banned_ip`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_banned_ip` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ip_address` varchar(100) NOT NULL DEFAULT '',
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ip_address` (`ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blocks`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blocks` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blocker` int(11) NOT NULL DEFAULT 0,
  `blocked` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blocker` (`blocker`),
  KEY `blocked` (`blocked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blog`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blog` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user` int(11) NOT NULL DEFAULT 0,
  `title` varchar(120) NOT NULL DEFAULT '',
  `content` mediumtext DEFAULT NULL,
  `description` mediumtext DEFAULT NULL,
  `posted` varchar(300) DEFAULT '0',
  `category` int(11) DEFAULT 0,
  `thumbnail` varchar(100) DEFAULT 'upload/photos/d-blog.jpg',
  `view` int(11) DEFAULT 0,
  `shared` int(11) DEFAULT 0,
  `tags` varchar(300) DEFAULT '',
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `user` (`user`),
  KEY `title` (`title`),
  KEY `category` (`category`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blog_reaction`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blog_reaction` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `blog_id` (`blog_id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blogcommentreplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blogcommentreplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comm_id` (`comm_id`),
  KEY `blog_id` (`blog_id`),
  KEY `order1` (`comm_id`,`id`),
  KEY `order2` (`blog_id`,`id`),
  KEY `order3` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blogcomments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blogcomments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_id` (`blog_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blogmoviedislikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blogmoviedislikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(11) NOT NULL DEFAULT 0,
  `movie_comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_comm_id` (`blog_comm_id`),
  KEY `movie_comm_id` (`movie_comm_id`),
  KEY `user_id` (`user_id`),
  KEY `blog_commreply_id` (`blog_commreply_id`),
  KEY `movie_commreply_id` (`movie_commreply_id`),
  KEY `blog_id` (`blog_id`),
  KEY `movie_id` (`movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blogmovielikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blogmovielikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `blog_comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(11) NOT NULL DEFAULT 0,
  `movie_comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `blog_id` (`blog_comm_id`),
  KEY `movie_id` (`movie_comm_id`),
  KEY `user_id` (`user_id`),
  KEY `blog_commreply_id` (`blog_commreply_id`),
  KEY `movie_commreply_id` (`movie_commreply_id`),
  KEY `blog_id_2` (`blog_id`),
  KEY `movie_id_2` (`movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_blogs_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_blogs_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `lang_key` (`lang_key`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_call_statistics`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_call_statistics` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `call_id` int(11) DEFAULT NULL COMMENT 'Посилання на wo_calls',
  `group_call_id` int(11) DEFAULT NULL COMMENT 'Посилання на wo_group_calls',
  `user_id` int(11) NOT NULL,
  `call_type` varchar(32) NOT NULL,
  `duration` int(11) NOT NULL COMMENT 'Тривалість в секундах',
  `bandwidth_used` float DEFAULT NULL COMMENT 'Пропускна спроможність в МБ',
  `packet_loss` float DEFAULT NULL COMMENT 'Втрата пакетів в %',
  `average_latency` int(11) DEFAULT NULL COMMENT 'Середня затримка в мс',
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_stats_user` (`user_id`),
  KEY `idx_stats_call_type` (`call_type`),
  KEY `idx_stats_created_at` (`created_at`),
  KEY `idx_stats_call_id` (`call_id`),
  KEY `idx_stats_group_call_id` (`group_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_calls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_calls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL COMMENT 'ID того, хто ініціював дзвінок',
  `to_id` int(11) NOT NULL COMMENT 'ID одержувача дзвінка',
  `call_type` varchar(32) NOT NULL DEFAULT 'audio' COMMENT 'audio або video',
  `status` varchar(32) NOT NULL DEFAULT 'ringing' COMMENT 'ringing, connected, ended, rejected, missed',
  `room_name` varchar(100) NOT NULL COMMENT 'Унікальне ім''я кімнати для WebRTC',
  `sdp_offer` longtext DEFAULT NULL COMMENT 'SDP offer від ініціатора',
  `sdp_answer` longtext DEFAULT NULL COMMENT 'SDP answer від одержувача',
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `accepted_at` timestamp NULL DEFAULT NULL,
  `ended_at` timestamp NULL DEFAULT NULL,
  `duration` int(11) DEFAULT NULL COMMENT 'Тривалість в секундах',
  `deleted_by_from` tinyint(1) DEFAULT 0,
  `deleted_by_to` tinyint(1) DEFAULT 0,
  `end_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_room_name` (`room_name`),
  KEY `idx_from_id` (`from_id`),
  KEY `idx_to_id` (`to_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_calls_user` (`from_id`,`created_at` DESC),
  KEY `idx_calls_to_user` (`to_id`,`created_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_codes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_codes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL DEFAULT '',
  `app_id` varchar(50) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `code` (`code`),
  KEY `user_id` (`user_id`),
  KEY `app_id` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_colored_posts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_colored_posts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `color_1` varchar(50) NOT NULL DEFAULT '',
  `color_2` varchar(50) NOT NULL DEFAULT '',
  `text_color` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(250) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `color_1` (`color_1`),
  KEY `color_2` (`color_2`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_comment_replies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_comment_replies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `c_file` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comment_id` (`comment_id`),
  KEY `user_id` (`user_id`,`page_id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_comment_replies_likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_comment_replies_likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `reply_id` (`reply_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_comment_replies_wonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_comment_replies_wonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `reply_id` (`reply_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_commentlikes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_commentlikes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `comment_id` (`comment_id`),
  KEY `post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_comments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_comments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `record` varchar(255) NOT NULL DEFAULT '',
  `c_file` varchar(255) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`page_id`,`id`),
  KEY `order3` (`post_id`,`id`),
  KEY `order4` (`user_id`,`id`),
  KEY `order5` (`post_id`,`id`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_commentwonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_commentwonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `comment_id` (`comment_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_config`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=577 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_custom_fields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_custom_fields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` varchar(50) DEFAULT '',
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(50) NOT NULL DEFAULT '',
  `required` varchar(11) NOT NULL DEFAULT 'on',
  `options` mediumtext DEFAULT NULL,
  `active` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_custompages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_custompages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_name` varchar(50) NOT NULL DEFAULT '',
  `page_title` varchar(200) NOT NULL DEFAULT '',
  `page_content` mediumtext DEFAULT NULL,
  `page_type` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_type` (`page_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_egoing`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_egoing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_einterested`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_einterested` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_einvited`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_einvited` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL,
  `inviter_id` int(11) NOT NULL,
  `invited_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `event_id` (`event_id`),
  KEY `inviter_id` (`invited_id`),
  KEY `inviter_id_2` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_emails`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_emails` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `email_to` varchar(50) NOT NULL DEFAULT '',
  `subject` varchar(32) NOT NULL DEFAULT '',
  `message` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `email_to` (`email_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_events`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_events` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(150) NOT NULL DEFAULT '',
  `location` varchar(300) NOT NULL DEFAULT '',
  `description` mediumtext NOT NULL,
  `start_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_date` date NOT NULL,
  `end_time` time NOT NULL,
  `poster_id` int(11) NOT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-cover.jpg',
  PRIMARY KEY (`id`),
  KEY `poster_id` (`poster_id`),
  KEY `name` (`name`),
  KEY `start_date` (`start_date`),
  KEY `start_time` (`start_time`),
  KEY `end_time` (`end_time`),
  KEY `end_date` (`end_date`),
  KEY `order1` (`poster_id`,`id`),
  KEY `order2` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_family`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_family` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `member_id` int(11) NOT NULL,
  `member` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `requesting` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `member_id` (`member_id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`),
  KEY `requesting` (`requesting`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_followers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_followers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `following_id` int(11) NOT NULL DEFAULT 0,
  `follower_id` int(11) NOT NULL DEFAULT 0,
  `is_typing` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 1,
  `notify` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `following_id` (`following_id`),
  KEY `follower_id` (`follower_id`),
  KEY `active` (`active`),
  KEY `order1` (`following_id`,`id`),
  KEY `order2` (`follower_id`,`id`),
  KEY `is_typing` (`is_typing`),
  KEY `notify` (`notify`),
  KEY `time` (`time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_forum_sections`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_forum_sections` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `section_name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `section_name` (`section_name`),
  KEY `description` (`description`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_forum_threads`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_forum_threads` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `headline` varchar(300) NOT NULL DEFAULT '',
  `post` mediumtext NOT NULL,
  `posted` varchar(20) NOT NULL DEFAULT '0',
  `last_post` int(11) NOT NULL DEFAULT 0,
  `forum` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user` (`user`),
  KEY `views` (`views`),
  KEY `posted` (`posted`),
  KEY `headline` (`headline`(255)),
  KEY `forum` (`forum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_forums`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_forums` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `sections` int(11) NOT NULL DEFAULT 0,
  `posts` int(11) NOT NULL DEFAULT 0,
  `last_post` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `description` (`description`(255)),
  KEY `posts` (`posts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_forumthreadreplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_forumthreadreplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `poster_id` int(11) NOT NULL DEFAULT 0,
  `post_subject` varchar(300) NOT NULL DEFAULT '',
  `post_text` mediumtext NOT NULL,
  `post_quoted` int(11) NOT NULL DEFAULT 0,
  `posted_time` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `thread_id` (`thread_id`),
  KEY `forum_id` (`forum_id`),
  KEY `poster_id` (`poster_id`),
  KEY `post_subject` (`post_subject`(255)),
  KEY `post_quoted` (`post_quoted`),
  KEY `posted_time` (`posted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_funding`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_funding` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hashed_id` varchar(100) NOT NULL DEFAULT '',
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` longtext DEFAULT NULL,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(200) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `hashed_id` (`hashed_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_funding_raise`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_funding_raise` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `funding_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `funding_id` (`funding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_games`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_games` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game_name` varchar(50) NOT NULL,
  `game_avatar` varchar(100) NOT NULL,
  `game_link` varchar(100) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_games_players`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_games_players` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `game_id` int(11) NOT NULL DEFAULT 0,
  `last_play` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`game_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_gender`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_gender` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `gender_id` varchar(50) NOT NULL DEFAULT '0',
  `image` varchar(300) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `gender_id` (`gender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_gifts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_gifts` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_group_call_participants`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_group_call_participants` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_call_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `sdp_answer` longtext DEFAULT NULL COMMENT 'SDP answer від конкретного користувача',
  `joined_at` timestamp NULL DEFAULT current_timestamp(),
  `left_at` timestamp NULL DEFAULT NULL,
  `duration` int(11) DEFAULT NULL COMMENT 'Тривалість участі в секундах',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_participant` (`group_call_id`,`user_id`),
  KEY `idx_gcp_group_call` (`group_call_id`),
  KEY `idx_gcp_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_group_calls`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_group_calls` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_id` int(11) NOT NULL,
  `initiated_by` int(11) NOT NULL COMMENT 'ID того, хто ініціював груповий дзвінок',
  `call_type` varchar(32) NOT NULL DEFAULT 'audio',
  `status` varchar(32) NOT NULL DEFAULT 'ringing' COMMENT 'ringing, active, ended',
  `room_name` varchar(100) NOT NULL COMMENT 'Унікальне ім''я кімнати',
  `sdp_offer` longtext DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `started_at` timestamp NULL DEFAULT NULL,
  `ended_at` timestamp NULL DEFAULT NULL,
  `duration` int(11) DEFAULT NULL COMMENT 'Тривалість в секундах',
  `max_participants` int(11) DEFAULT NULL COMMENT 'Максимум учасників',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_group_room_name` (`room_name`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_initiated_by` (`initiated_by`),
  KEY `idx_gc_status` (`status`),
  KEY `idx_gc_created_at` (`created_at`),
  KEY `idx_group_calls_group` (`group_id`,`created_at` DESC),
  KEY `idx_group_calls_initiator` (`initiated_by`,`created_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_group_members`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_group_members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `time` (`time`),
  KEY `user_id` (`user_id`,`group_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_groupadmins`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_groupadmins` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `privacy` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `members` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_group` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `group_id` (`group_id`),
  KEY `members` (`members`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_groupchat`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_groupchat` (
  `group_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `group_name` varchar(20) NOT NULL DEFAULT '',
  `avatar` varchar(3000) NOT NULL DEFAULT 'upload/photos/d-group.jpg',
  `time` varchar(30) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'group',
  `destruct_at` int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`group_id`),
  KEY `user_id` (`user_id`),
  KEY `type` (`type`),
  KEY `destruct_at` (`destruct_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_groupchatusers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_groupchatusers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `group_id` int(11) NOT NULL,
  `active` enum('1','0') NOT NULL DEFAULT '1',
  `last_seen` varchar(50) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `group_id` (`group_id`),
  KEY `active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_groups`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_groups` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_name` varchar(32) NOT NULL DEFAULT '',
  `group_title` varchar(40) NOT NULL DEFAULT '',
  `avatar` varchar(120) NOT NULL DEFAULT 'upload/photos/d-group.jpg ',
  `cover` varchar(120) NOT NULL DEFAULT 'upload/photos/d-cover.jpg  ',
  `about` varchar(550) NOT NULL DEFAULT '',
  `category` int(11) NOT NULL DEFAULT 1,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `privacy` enum('1','2') NOT NULL DEFAULT '1',
  `join_privacy` enum('1','2') NOT NULL DEFAULT '1',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `privacy` (`privacy`),
  KEY `time` (`time`),
  KEY `active` (`active`),
  KEY `group_title` (`group_title`),
  KEY `group_name` (`group_name`),
  KEY `registered` (`registered`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_groups_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_groups_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_hashtags`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_hashtags` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash` varchar(255) NOT NULL DEFAULT '',
  `tag` varchar(255) NOT NULL DEFAULT '',
  `last_trend_time` int(11) NOT NULL DEFAULT 0,
  `trend_use_num` int(11) NOT NULL DEFAULT 0,
  `expire` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `last_trend_time` (`last_trend_time`),
  KEY `trend_use_num` (`trend_use_num`),
  KEY `tag` (`tag`),
  KEY `expire` (`expire`),
  KEY `hash` (`hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_hiddenposts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_hiddenposts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_ice_candidates`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_ice_candidates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `room_name` varchar(100) NOT NULL,
  `candidate` longtext NOT NULL,
  `sdp_m_line_index` int(11) NOT NULL,
  `sdp_mid` varchar(100) NOT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_ice_room_name` (`room_name`),
  KEY `idx_ice_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2277 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_invitation_links`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_invitation_links` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  `code` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `code` (`code`(255)),
  KEY `invited_id` (`invited_id`),
  KEY `time` (`time`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_job`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_job` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `title` varchar(200) NOT NULL DEFAULT '',
  `location` varchar(100) NOT NULL DEFAULT '',
  `lat` varchar(50) NOT NULL DEFAULT '',
  `lng` varchar(50) NOT NULL DEFAULT '',
  `minimum` varchar(50) NOT NULL DEFAULT '0',
  `maximum` varchar(50) NOT NULL DEFAULT '0',
  `salary_date` varchar(50) NOT NULL DEFAULT '',
  `job_type` varchar(50) NOT NULL DEFAULT '',
  `category` varchar(50) NOT NULL DEFAULT '',
  `question_one` varchar(200) NOT NULL DEFAULT '',
  `question_one_type` varchar(100) NOT NULL DEFAULT '',
  `question_one_answers` mediumtext DEFAULT NULL,
  `question_two` varchar(200) NOT NULL DEFAULT '',
  `question_two_type` varchar(100) NOT NULL DEFAULT '',
  `question_two_answers` mediumtext DEFAULT NULL,
  `question_three` varchar(200) NOT NULL DEFAULT '',
  `question_three_type` varchar(100) NOT NULL DEFAULT '',
  `question_three_answers` mediumtext DEFAULT NULL,
  `description` mediumtext DEFAULT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `image_type` varchar(11) NOT NULL DEFAULT '',
  `currency` varchar(11) NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT 1,
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`),
  KEY `title` (`title`),
  KEY `category` (`category`),
  KEY `lng` (`lng`),
  KEY `lat` (`lat`),
  KEY `status` (`status`),
  KEY `job_type` (`job_type`),
  KEY `minimum` (`minimum`),
  KEY `maximum` (`maximum`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_job_apply`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_job_apply` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `job_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `user_name` varchar(100) NOT NULL DEFAULT '',
  `phone_number` varchar(50) NOT NULL DEFAULT '',
  `location` varchar(50) NOT NULL DEFAULT '',
  `email` varchar(100) NOT NULL DEFAULT '',
  `question_one_answer` varchar(200) NOT NULL DEFAULT '',
  `question_two_answer` varchar(200) NOT NULL DEFAULT '',
  `question_three_answer` varchar(200) NOT NULL DEFAULT '',
  `position` varchar(100) NOT NULL DEFAULT '',
  `where_did_you_work` varchar(100) NOT NULL DEFAULT '',
  `experience_description` varchar(300) NOT NULL DEFAULT '',
  `experience_start_date` varchar(50) NOT NULL DEFAULT '',
  `experience_end_date` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `job_id` (`job_id`),
  KEY `page_id` (`page_id`),
  KEY `user_name` (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_job_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_job_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_langs`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_langs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) DEFAULT NULL,
  `type` varchar(100) NOT NULL DEFAULT '',
  `english` longtext DEFAULT NULL,
  `russian` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`lang_key`),
  KEY `type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=2568 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_live_sub_users`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_live_sub_users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `is_watching` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `time` (`time`),
  KEY `is_watching` (`is_watching`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_manage_pro`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_manage_pro` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(100) NOT NULL DEFAULT '',
  `price` varchar(11) NOT NULL DEFAULT '0',
  `featured_member` int(11) NOT NULL DEFAULT 0,
  `profile_visitors` int(11) NOT NULL DEFAULT 0,
  `last_seen` int(11) NOT NULL DEFAULT 0,
  `verified_badge` int(11) NOT NULL DEFAULT 0,
  `posts_promotion` int(11) NOT NULL DEFAULT 0,
  `pages_promotion` int(11) NOT NULL DEFAULT 0,
  `discount` mediumtext NOT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `night_image` varchar(300) NOT NULL DEFAULT '',
  `color` varchar(50) NOT NULL DEFAULT '#fafafa',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 1,
  `time` varchar(20) NOT NULL DEFAULT 'week',
  `time_count` int(11) NOT NULL DEFAULT 0,
  `max_upload` varchar(100) NOT NULL DEFAULT '96000000',
  `features` varchar(800) DEFAULT '{"can_use_funding":1,"can_use_jobs":1,"can_use_games":1,"can_use_market":1,"can_use_events":1,"can_use_forum":1,"can_use_groups":1,"can_use_pages":1,"can_use_audio_call":1,"can_use_video_call":1,"can_use_offer":1,"can_use_blog":1,"can_use_movies":1,"can_use_story":1,"can_use_stickers":1,"can_use_gif":1,"can_use_gift":1,"can_use_nearby":1,"can_use_video_upload":1,"can_use_audio_upload":1,"can_use_shout_box":1,"can_use_colored_posts":1,"can_use_poll":1,"can_use_live":1,"can_use_background":1,"can_use_chat":1,"can_use_ai_image":1,"can_use_ai_post":1,"can_use_ai_user":1,"can_use_ai_blog":1}',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_messages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_messages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `media` varchar(255) NOT NULL DEFAULT '',
  `mediaFileName` varchar(200) NOT NULL DEFAULT '',
  `mediaFileNames` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  `seen` int(11) NOT NULL DEFAULT 0,
  `deleted_one` enum('0','1') NOT NULL DEFAULT '0',
  `deleted_two` enum('0','1') NOT NULL DEFAULT '0',
  `sent_push` int(11) NOT NULL DEFAULT 0,
  `notification_id` varchar(50) NOT NULL DEFAULT '',
  `type_two` varchar(32) NOT NULL DEFAULT '',
  `stickers` mediumtext DEFAULT NULL,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `lat` varchar(200) NOT NULL DEFAULT '0',
  `lng` varchar(200) NOT NULL DEFAULT '0',
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `broadcast_id` int(11) NOT NULL DEFAULT 0,
  `forward` int(11) NOT NULL DEFAULT 0,
  `listening` int(11) NOT NULL DEFAULT 0,
  `remove_at` int(10) unsigned NOT NULL DEFAULT 0,
  `text_ecb` text DEFAULT NULL,
  `text_preview` varchar(255) DEFAULT NULL,
  `iv` varchar(255) DEFAULT NULL,
  `tag` varchar(255) DEFAULT NULL,
  `cipher_version` int(11) DEFAULT 1,
  `edited` int(11) DEFAULT 0,
  `signal_header` text DEFAULT NULL COMMENT 'JSON DR header for Signal-encrypted messages (cipher_version=3)',
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `to_id` (`to_id`),
  KEY `seen` (`seen`),
  KEY `time` (`time`),
  KEY `deleted_two` (`deleted_two`),
  KEY `deleted_one` (`deleted_one`),
  KEY `sent_push` (`sent_push`),
  KEY `group_id` (`group_id`),
  KEY `order1` (`from_id`,`id`),
  KEY `order2` (`group_id`,`id`),
  KEY `order3` (`to_id`,`id`),
  KEY `order7` (`seen`,`id`),
  KEY `order8` (`time`,`id`),
  KEY `order4` (`from_id`,`id`),
  KEY `order5` (`group_id`,`id`),
  KEY `order6` (`to_id`,`id`),
  KEY `reply_id` (`reply_id`),
  KEY `broadcast_id` (`broadcast_id`),
  KEY `story_id` (`story_id`),
  KEY `product_id` (`product_id`),
  KEY `notification_id` (`notification_id`),
  KEY `page_id` (`page_id`),
  KEY `page_id_2` (`page_id`),
  KEY `notification_id_2` (`notification_id`),
  KEY `product_id_2` (`product_id`),
  KEY `story_id_2` (`story_id`),
  KEY `reply_id_2` (`reply_id`),
  KEY `broadcast_id_2` (`broadcast_id`),
  KEY `forward` (`forward`),
  KEY `listening` (`listening`),
  KEY `remove_at` (`remove_at`),
  KEY `idx_cipher_version` (`cipher_version`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_monetizationsubscription`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_monetizationsubscription` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned NOT NULL DEFAULT 0,
  `monetization_id` int(11) NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `last_payment_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expire` int(11) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_moviecommentreplies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_moviecommentreplies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `comm_id` (`comm_id`),
  KEY `movie_id` (`movie_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_moviecomments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_moviecomments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `posted` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `movie_id` (`movie_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_movies`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_movies` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `genre` varchar(50) NOT NULL DEFAULT '',
  `stars` varchar(300) NOT NULL DEFAULT '',
  `producer` varchar(100) NOT NULL DEFAULT '',
  `country` varchar(50) NOT NULL DEFAULT '',
  `release` year(4) DEFAULT NULL,
  `quality` varchar(10) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0,
  `description` mediumtext DEFAULT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-film.jpg',
  `source` varchar(1000) NOT NULL DEFAULT '',
  `iframe` varchar(1000) NOT NULL DEFAULT '',
  `video` varchar(3000) NOT NULL DEFAULT '',
  `views` int(11) NOT NULL DEFAULT 0,
  `rating` varchar(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `genre` (`genre`),
  KEY `country` (`country`),
  KEY `release` (`release`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_notifications`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_notifications` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `notifier_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(10) unsigned DEFAULT 0,
  `comment_id` int(10) unsigned DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `group_chat_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `seen_pop` int(11) NOT NULL DEFAULT 0,
  `type` varchar(255) NOT NULL DEFAULT '',
  `type2` varchar(32) NOT NULL DEFAULT '',
  `text` mediumtext DEFAULT NULL,
  `url` varchar(255) NOT NULL DEFAULT '',
  `full_link` varchar(1000) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `sent_push` int(11) NOT NULL DEFAULT 0,
  `admin` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `notifier_id` (`notifier_id`),
  KEY `user_id` (`recipient_id`),
  KEY `post_id` (`post_id`),
  KEY `seen` (`seen`),
  KEY `time` (`time`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`,`seen_pop`),
  KEY `sent_push` (`sent_push`),
  KEY `order1` (`seen`,`id`),
  KEY `order2` (`notifier_id`,`id`),
  KEY `order3` (`recipient_id`,`id`),
  KEY `order4` (`post_id`,`id`),
  KEY `order5` (`page_id`,`id`),
  KEY `order6` (`group_id`,`id`),
  KEY `order7` (`time`,`id`),
  KEY `comment_id` (`comment_id`),
  KEY `reply_id` (`reply_id`),
  KEY `blog_id` (`blog_id`),
  KEY `story_id` (`story_id`),
  KEY `admin` (`admin`),
  KEY `group_chat_id` (`group_chat_id`),
  KEY `event_id` (`event_id`),
  KEY `thread_id` (`thread_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_offers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_offers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `discount_type` varchar(200) NOT NULL DEFAULT '',
  `discount_percent` int(11) NOT NULL DEFAULT 0,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `discounted_items` varchar(150) DEFAULT '',
  `buy` int(11) NOT NULL DEFAULT 0,
  `get_price` int(11) NOT NULL DEFAULT 0,
  `spend` int(11) NOT NULL DEFAULT 0,
  `amount_off` int(11) NOT NULL DEFAULT 0,
  `description` mediumtext DEFAULT NULL,
  `expire_date` date NOT NULL,
  `expire_time` time NOT NULL,
  `image` varchar(300) NOT NULL DEFAULT '',
  `currency` varchar(50) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_id` (`page_id`),
  KEY `user_id` (`user_id`),
  KEY `spend` (`spend`),
  KEY `time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pageadmins`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pageadmins` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `info` int(11) NOT NULL DEFAULT 1,
  `social` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `design` int(11) NOT NULL DEFAULT 1,
  `admins` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_page` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pagerating`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pagerating` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `valuation` int(11) DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pages`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pages` (
  `page_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_name` varchar(32) NOT NULL DEFAULT '',
  `page_title` varchar(32) NOT NULL DEFAULT '',
  `page_description` varchar(1000) NOT NULL DEFAULT '',
  `avatar` varchar(255) NOT NULL DEFAULT 'upload/photos/d-page.jpg',
  `cover` varchar(255) NOT NULL DEFAULT 'upload/photos/d-cover.jpg',
  `users_post` int(11) NOT NULL DEFAULT 0,
  `page_category` int(11) NOT NULL DEFAULT 1,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `website` varchar(255) NOT NULL DEFAULT '',
  `facebook` varchar(32) NOT NULL DEFAULT '',
  `google` varchar(32) NOT NULL DEFAULT '',
  `vk` varchar(32) NOT NULL DEFAULT '',
  `twitter` varchar(32) NOT NULL DEFAULT '',
  `linkedin` varchar(32) NOT NULL DEFAULT '',
  `company` varchar(32) NOT NULL DEFAULT '',
  `phone` varchar(32) NOT NULL DEFAULT '',
  `address` varchar(100) NOT NULL DEFAULT '',
  `call_action_type` int(11) NOT NULL DEFAULT 0,
  `call_action_type_url` varchar(255) NOT NULL DEFAULT '',
  `background_image` varchar(200) NOT NULL DEFAULT '',
  `background_image_status` int(11) NOT NULL DEFAULT 0,
  `instgram` varchar(32) NOT NULL DEFAULT '',
  `youtube` varchar(100) NOT NULL DEFAULT '',
  `verified` enum('0','1') NOT NULL DEFAULT '0',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `boosted` enum('0','1') NOT NULL DEFAULT '0',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`page_id`),
  KEY `registered` (`registered`),
  KEY `user_id` (`user_id`),
  KEY `page_category` (`page_category`),
  KEY `active` (`active`),
  KEY `verified` (`verified`),
  KEY `boosted` (`boosted`),
  KEY `time` (`time`),
  KEY `page_name` (`page_name`),
  KEY `page_title` (`page_title`),
  KEY `sub_category` (`sub_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pages_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pages_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pages_invites`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pages_invites` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `inviter_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `page_id` (`page_id`,`inviter_id`,`invited_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pages_likes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pages_likes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `active` (`active`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_payment_transactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_payment_transactions` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `userid` int(10) unsigned NOT NULL,
  `kind` varchar(100) NOT NULL,
  `amount` decimal(11,0) unsigned NOT NULL,
  `transaction_dt` timestamp NOT NULL DEFAULT current_timestamp(),
  `notes` mediumtext NOT NULL,
  `admin_commission` decimal(11,0) unsigned DEFAULT 0,
  `extra` varchar(1000) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `userid` (`userid`),
  KEY `kind` (`kind`),
  KEY `transaction_dt` (`transaction_dt`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_payments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_payments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 0,
  `type` varchar(15) NOT NULL DEFAULT '',
  `date` varchar(30) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `date` (`date`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pinnedposts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pinnedposts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `active` (`active`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_pokes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_pokes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `received_user_id` int(11) NOT NULL DEFAULT 0,
  `send_user_id` int(11) NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `received_user_id` (`received_user_id`),
  KEY `user_id` (`send_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_posts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_posts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `postText` text DEFAULT NULL,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `page_event_id` int(11) NOT NULL DEFAULT 0,
  `postLink` varchar(1000) NOT NULL DEFAULT '',
  `postLinkTitle` mediumtext DEFAULT NULL,
  `postLinkImage` varchar(100) NOT NULL DEFAULT '',
  `postLinkContent` mediumtext DEFAULT NULL,
  `postVimeo` varchar(100) NOT NULL DEFAULT '',
  `postDailymotion` varchar(100) NOT NULL DEFAULT '',
  `postFacebook` varchar(100) NOT NULL DEFAULT '',
  `postFile` varchar(255) NOT NULL DEFAULT '',
  `postFileName` varchar(200) NOT NULL DEFAULT '',
  `postFileThumb` varchar(3000) NOT NULL DEFAULT '',
  `postYoutube` varchar(255) NOT NULL DEFAULT '',
  `postVine` varchar(32) NOT NULL DEFAULT '',
  `postSoundCloud` varchar(255) NOT NULL DEFAULT '',
  `postPlaytube` varchar(500) NOT NULL DEFAULT '',
  `postDeepsound` varchar(500) NOT NULL DEFAULT '',
  `postMap` varchar(255) NOT NULL DEFAULT '',
  `postShare` int(11) NOT NULL DEFAULT 0,
  `postPrivacy` enum('0','1','2','3','4','5','6') NOT NULL DEFAULT '1',
  `postType` varchar(30) NOT NULL DEFAULT '',
  `postFeeling` varchar(255) NOT NULL DEFAULT '',
  `postListening` varchar(255) NOT NULL DEFAULT '',
  `postTraveling` varchar(255) NOT NULL DEFAULT '',
  `postWatching` varchar(255) NOT NULL DEFAULT '',
  `postPlaying` varchar(255) NOT NULL DEFAULT '',
  `postPhoto` varchar(3000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  `registered` varchar(32) NOT NULL DEFAULT '0/0000',
  `album_name` varchar(52) NOT NULL DEFAULT '',
  `multi_image` enum('0','1') NOT NULL DEFAULT '0',
  `multi_image_post` int(11) NOT NULL DEFAULT 0,
  `boosted` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `poll_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `videoViews` int(11) NOT NULL DEFAULT 0,
  `postRecord` varchar(3000) NOT NULL DEFAULT '',
  `postSticker` mediumtext DEFAULT NULL,
  `shared_from` int(11) NOT NULL DEFAULT 0,
  `post_url` mediumtext DEFAULT NULL,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `cache` int(11) NOT NULL DEFAULT 0,
  `comments_status` int(11) NOT NULL DEFAULT 1,
  `blur` int(11) NOT NULL DEFAULT 0,
  `color_id` int(11) NOT NULL DEFAULT 0,
  `job_id` int(11) NOT NULL DEFAULT 0,
  `offer_id` int(11) NOT NULL DEFAULT 0,
  `fund_raise_id` int(11) NOT NULL DEFAULT 0,
  `fund_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 1,
  `stream_name` varchar(100) NOT NULL DEFAULT '',
  `agora_token` mediumtext DEFAULT NULL,
  `live_time` int(11) NOT NULL DEFAULT 0,
  `live_ended` int(11) NOT NULL DEFAULT 0,
  `agora_resource_id` mediumtext DEFAULT NULL,
  `agora_sid` varchar(500) NOT NULL DEFAULT '',
  `send_notify` varchar(11) NOT NULL DEFAULT '',
  `240p` int(11) NOT NULL DEFAULT 0,
  `360p` int(11) NOT NULL DEFAULT 0,
  `480p` int(11) NOT NULL DEFAULT 0,
  `720p` int(11) NOT NULL DEFAULT 0,
  `1080p` int(11) NOT NULL DEFAULT 0,
  `2048p` int(11) NOT NULL DEFAULT 0,
  `4096p` int(11) NOT NULL DEFAULT 0,
  `processing` int(11) NOT NULL DEFAULT 0,
  `ai_post` int(10) unsigned DEFAULT 0,
  `videoTitle` varchar(200) DEFAULT NULL,
  `is_reel` tinyint(4) DEFAULT 0,
  `blur_url` varchar(300) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `recipient_id` (`recipient_id`),
  KEY `postFile` (`postFile`),
  KEY `postShare` (`postShare`),
  KEY `postType` (`postType`),
  KEY `postYoutube` (`postYoutube`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`),
  KEY `registered` (`registered`),
  KEY `time` (`time`),
  KEY `boosted` (`boosted`),
  KEY `product_id` (`product_id`),
  KEY `poll_id` (`poll_id`),
  KEY `event_id` (`event_id`),
  KEY `videoViews` (`videoViews`),
  KEY `shared_from` (`shared_from`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`page_id`,`id`),
  KEY `order3` (`group_id`,`id`),
  KEY `order4` (`recipient_id`,`id`),
  KEY `order5` (`event_id`,`id`),
  KEY `order6` (`parent_id`,`id`),
  KEY `multi_image` (`multi_image`),
  KEY `album_name` (`album_name`),
  KEY `postFacebook` (`postFacebook`),
  KEY `postVimeo` (`postVimeo`),
  KEY `postDailymotion` (`postDailymotion`),
  KEY `postSoundCloud` (`postSoundCloud`),
  KEY `postYoutube_2` (`postYoutube`),
  KEY `fund_raise_id` (`fund_raise_id`),
  KEY `fund_id` (`fund_id`),
  KEY `offer_id` (`offer_id`),
  KEY `live_time` (`live_time`),
  KEY `live_ended` (`live_ended`),
  KEY `active` (`active`),
  KEY `job_id` (`job_id`),
  KEY `page_event_id` (`page_event_id`),
  KEY `blog_id` (`blog_id`),
  KEY `color_id` (`color_id`),
  KEY `thread_id` (`thread_id`),
  KEY `forum_id` (`forum_id`),
  KEY `processing` (`processing`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_productreview`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_productreview` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  `star` int(11) NOT NULL DEFAULT 1,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  KEY `star` (`star`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_products`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_products` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `category` int(11) NOT NULL DEFAULT 0,
  `sub_category` varchar(50) NOT NULL DEFAULT '',
  `price` float NOT NULL DEFAULT 0,
  `location` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `type` enum('0','1') NOT NULL,
  `currency` varchar(40) NOT NULL DEFAULT 'USD',
  `lng` varchar(100) NOT NULL DEFAULT '0',
  `lat` varchar(100) NOT NULL DEFAULT '0',
  `units` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `category` (`category`),
  KEY `price` (`price`),
  KEY `status` (`status`),
  KEY `page_id` (`page_id`),
  KEY `active` (`active`),
  KEY `units` (`units`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_products_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_products_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `lang_key` varchar(160) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_products_media`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_products_media` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_profilefields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_profilefields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` mediumtext DEFAULT NULL,
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(32) NOT NULL DEFAULT 'profile',
  `registration_page` int(11) NOT NULL DEFAULT 0,
  `profile_page` int(11) NOT NULL DEFAULT 0,
  `select_type` varchar(32) NOT NULL DEFAULT 'none',
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `registration_page` (`registration_page`),
  KEY `active` (`active`),
  KEY `profile_page` (`profile_page`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_purchases`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_purchases` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `data` mediumtext DEFAULT NULL,
  `final_price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `timestamp` (`timestamp`),
  KEY `time` (`time`),
  KEY `owner_id` (`owner_id`),
  KEY `final_price` (`final_price`),
  KEY `order_hash_id` (`order_hash_id`),
  KEY `data` (`data`(768))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_reactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_reactions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned NOT NULL DEFAULT 0,
  `post_id` int(10) unsigned DEFAULT 0,
  `comment_id` int(10) unsigned DEFAULT 0,
  `replay_id` int(10) unsigned DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_reaction` (`reaction`),
  KEY `message_id` (`message_id`),
  KEY `message_id_2` (`message_id`),
  KEY `replay_id` (`replay_id`),
  KEY `story_id` (`story_id`),
  KEY `comment_id` (`comment_id`),
  KEY `comment_id_2` (`comment_id`),
  KEY `replay_id_2` (`replay_id`),
  KEY `message_id_3` (`message_id`),
  KEY `story_id_2` (`story_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_reactions_types`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_reactions_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '',
  `wowonder_icon` varchar(300) NOT NULL DEFAULT '',
  `sunshine_icon` varchar(300) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_recentsearches`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_recentsearches` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `search_id` int(11) NOT NULL DEFAULT 0,
  `search_type` varchar(32) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`,`search_id`),
  KEY `search_type` (`search_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_refund`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_refund` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `pro_type` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `pro_type` (`pro_type`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_relationship`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_relationship` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `relationship` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `from_id` (`from_id`),
  KEY `relationship` (`relationship`),
  KEY `active` (`active`),
  KEY `to_id` (`to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_reports`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_reports` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(10) unsigned NOT NULL DEFAULT 0,
  `profile_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `reason` varchar(100) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `seen` (`seen`),
  KEY `profile_id` (`profile_id`),
  KEY `page_id` (`page_id`),
  KEY `group_id` (`group_id`),
  KEY `comment_id` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_savedposts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_savedposts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_stickers`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_stickers` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_story_seen`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_story_seen` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `time` varchar(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `story_id` (`story_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_storycomments`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_storycomments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `story_id` (`story_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_storyreactions`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_storyreactions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(30) NOT NULL DEFAULT 'like' COMMENT 'Reaction type: like, love, haha, wow, sad, angry',
  `time` int(11) NOT NULL DEFAULT 0 COMMENT 'Unix timestamp when reaction was added',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_story_user` (`story_id`,`user_id`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_reaction` (`reaction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Реакции на stories';

DROP TABLE IF EXISTS `wo_sub_categories`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_sub_categories` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `category_id` int(11) NOT NULL DEFAULT 0,
  `lang_key` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(200) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `category_id` (`category_id`),
  KEY `lang_key` (`lang_key`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_terms`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_terms` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(32) NOT NULL DEFAULT '',
  `text` longtext DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_tokens`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_tokens` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `app_id` int(11) NOT NULL DEFAULT 0,
  `token` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `user_id_2` (`user_id`),
  KEY `app_id` (`app_id`),
  KEY `token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_user_gifts`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_user_gifts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `from` int(11) NOT NULL DEFAULT 0,
  `to` int(11) NOT NULL DEFAULT 0,
  `gift_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `from` (`from`),
  KEY `to` (`to`),
  KEY `gift_id` (`gift_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_useraddress`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_useraddress` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `phone` varchar(50) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `city` varchar(100) NOT NULL DEFAULT '',
  `zip` varchar(20) NOT NULL DEFAULT '',
  `address` varchar(500) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_userads_data`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_userads_data` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `ad_id` int(11) NOT NULL DEFAULT 0,
  `clicks` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `spend` float unsigned NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `ad_id` (`ad_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_usercard`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_usercard` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  KEY `units` (`units`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_userfields`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_userfields` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_usermonetization`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_usermonetization` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned NOT NULL DEFAULT 0,
  `price` varchar(11) NOT NULL DEFAULT '0',
  `currency` varchar(5) NOT NULL DEFAULT 'USD',
  `paid_every` int(11) NOT NULL DEFAULT 1,
  `period` varchar(10) NOT NULL DEFAULT 'Daily',
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_userorders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_userorders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `hash_id` varchar(100) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_owner_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `address_id` int(11) NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `final_price` float NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0,
  `tracking_url` varchar(500) NOT NULL DEFAULT '',
  `tracking_id` varchar(50) NOT NULL DEFAULT '',
  `status` varchar(30) NOT NULL DEFAULT 'placed',
  `time` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_owner_id` (`product_owner_id`),
  KEY `product_id` (`product_id`),
  KEY `final_price` (`final_price`),
  KEY `status` (`status`),
  KEY `time` (`time`),
  KEY `hash_id` (`hash_id`),
  KEY `units` (`units`),
  KEY `address_id` (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_userschat`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_userschat` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `conversation_user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `color` varchar(100) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'chat',
  `disappearing_time` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `conversation_user_id` (`conversation_user_id`),
  KEY `time` (`time`),
  KEY `order1` (`user_id`,`id`),
  KEY `order2` (`user_id`,`id`),
  KEY `order3` (`conversation_user_id`,`id`),
  KEY `order4` (`conversation_user_id`,`id`),
  KEY `page_id` (`page_id`),
  KEY `color` (`color`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_userstorymedia`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_userstorymedia` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `type` varchar(30) NOT NULL DEFAULT '',
  `filename` mediumtext DEFAULT NULL,
  `expire` varchar(100) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0 COMMENT 'Video duration in seconds',
  PRIMARY KEY (`id`),
  KEY `expire` (`expire`),
  KEY `story_id` (`story_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_verification_requests`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_verification_requests` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `message` mediumtext DEFAULT NULL,
  `user_name` varchar(150) NOT NULL DEFAULT '',
  `passport` varchar(3000) NOT NULL DEFAULT '',
  `photo` varchar(3000) NOT NULL DEFAULT '',
  `type` varchar(32) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wo_videocalles`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_videocalles` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `access_token` text DEFAULT NULL,
  `access_token_2` text DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `to_id` (`to_id`),
  KEY `from_id` (`from_id`),
  KEY `called` (`called`),
  KEY `declined` (`declined`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

DROP TABLE IF EXISTS `wo_votes`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_votes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `option_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `post_id` (`post_id`),
  KEY `option_id` (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

DROP TABLE IF EXISTS `wo_wonders`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wo_wonders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `type` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `post_id` (`post_id`),
  KEY `user_id` (`user_id`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

DROP TABLE IF EXISTS `wondertage_settings`;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

CREATE TABLE `wondertage_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

