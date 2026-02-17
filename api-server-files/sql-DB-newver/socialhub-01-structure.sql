SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;

/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;

/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;

CREATE TABLE `bank_receipts` (
  `id` int(10) UNSIGNED NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `fund_id` int(11) NOT NULL DEFAULT 0,
  `description` text NOT NULL,
  `price` varchar(50) NOT NULL DEFAULT '0',
  `mode` varchar(50) NOT NULL DEFAULT '',
  `approved` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `receipt_file` varchar(250) NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `approved_at` int(10) UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `broadcast` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `image` varchar(150) DEFAULT 'upload/photos/d-group.jpg',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `broadcast_users` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `broadcast_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stickers` (
  `id` int(11) NOT NULL,
  `pack_id` int(11) DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `emoji` varchar(10) DEFAULT NULL,
  `keywords` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`keywords`)),
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `file_size` int(11) DEFAULT NULL,
  `format` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `sticker_packs` (
  `id` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `icon_url` varchar(500) DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `sticker_count` int(11) DEFAULT 0,
  `is_active` tinyint(1) DEFAULT 0,
  `is_animated` tinyint(1) DEFAULT 0,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `wondertage_settings` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Activities` (
  `id` int(11) NOT NULL,
  `user_id` int(255) NOT NULL DEFAULT 0,
  `post_id` int(255) NOT NULL DEFAULT 0,
  `reply_id` int(11) UNSIGNED DEFAULT 0,
  `comment_id` int(11) UNSIGNED DEFAULT 0,
  `follow_id` int(11) NOT NULL DEFAULT 0,
  `activity_type` varchar(32) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_activities` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(10) UNSIGNED DEFAULT 0,
  `comment_id` int(10) UNSIGNED DEFAULT 0,
  `follow_id` int(11) NOT NULL DEFAULT 0,
  `activity_type` varchar(32) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_AdminInvitations` (
  `id` int(11) NOT NULL,
  `code` varchar(300) NOT NULL DEFAULT '0',
  `posted` varchar(50) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_admininvitations` (
  `id` int(11) NOT NULL,
  `code` varchar(300) NOT NULL DEFAULT '0',
  `posted` varchar(50) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Ads` (
  `id` int(11) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT '',
  `code` mediumtext DEFAULT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_ads` (
  `id` int(11) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT '',
  `code` mediumtext DEFAULT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Affiliates_Requests` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_affiliates_requests` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_agoravideocall` (
  `id` int(11) NOT NULL,
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
  `access_token_2` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_AgoraVideoCall` (
  `id` int(11) NOT NULL,
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
  `access_token_2` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_albums_media` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `review_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(255) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Albums_Media` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `parent_id` int(11) NOT NULL DEFAULT 0,
  `review_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(255) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_announcement` (
  `id` int(11) NOT NULL,
  `text` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Announcement` (
  `id` int(11) NOT NULL,
  `text` mediumtext DEFAULT NULL,
  `time` int(32) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_announcement_views` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `announcement_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Announcement_Views` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `announcement_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Apps` (
  `id` int(11) NOT NULL,
  `app_user_id` int(11) NOT NULL DEFAULT 0,
  `app_name` varchar(32) NOT NULL,
  `app_website_url` varchar(55) NOT NULL,
  `app_description` mediumtext NOT NULL,
  `app_avatar` varchar(100) NOT NULL DEFAULT 'upload/photos/app-default-icon.png',
  `app_callback_url` varchar(255) NOT NULL,
  `app_id` varchar(32) NOT NULL,
  `app_secret` varchar(55) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_apps` (
  `id` int(11) NOT NULL,
  `app_user_id` int(11) NOT NULL DEFAULT 0,
  `app_name` varchar(32) NOT NULL,
  `app_website_url` varchar(55) NOT NULL,
  `app_description` mediumtext NOT NULL,
  `app_avatar` varchar(100) NOT NULL DEFAULT 'upload/photos/app-default-icon.png',
  `app_callback_url` varchar(255) NOT NULL,
  `app_id` varchar(32) NOT NULL,
  `app_secret` varchar(55) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_AppsSessions` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `session_id` varchar(120) NOT NULL DEFAULT '',
  `platform` varchar(32) NOT NULL DEFAULT '',
  `platform_details` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_appssessions` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `session_id` varchar(120) NOT NULL DEFAULT '',
  `platform` varchar(32) NOT NULL DEFAULT '',
  `platform_details` mediumtext DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_apps_hash` (
  `id` int(11) NOT NULL,
  `hash_id` varchar(200) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Apps_Hash` (
  `id` int(11) NOT NULL,
  `hash_id` varchar(200) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_apps_permission` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `app_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Apps_Permission` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `app_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_audiocalls` (
  `id` int(11) NOT NULL,
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
  `status` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_AudioCalls` (
  `id` int(11) NOT NULL,
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
  `status` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_backup_codes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `codes` varchar(500) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Backup_Codes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `codes` varchar(500) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_bad_login` (
  `id` int(11) NOT NULL,
  `ip` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Bad_Login` (
  `id` int(11) NOT NULL,
  `ip` varchar(100) NOT NULL DEFAULT '',
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_banned_ip` (
  `id` int(11) NOT NULL,
  `ip_address` varchar(100) NOT NULL DEFAULT '',
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Banned_Ip` (
  `id` int(11) NOT NULL,
  `ip_address` varchar(100) NOT NULL DEFAULT '',
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Blocks` (
  `id` int(11) NOT NULL,
  `blocker` int(11) NOT NULL DEFAULT 0,
  `blocked` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blocks` (
  `id` int(11) NOT NULL,
  `blocker` int(11) NOT NULL DEFAULT 0,
  `blocked` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Blog` (
  `id` int(11) NOT NULL,
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
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blog` (
  `id` int(11) NOT NULL,
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
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blogcommentreplies` (
  `id` int(11) NOT NULL,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_BlogCommentReplies` (
  `id` int(11) NOT NULL,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blogcomments` (
  `id` int(11) NOT NULL,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_BlogComments` (
  `id` int(11) NOT NULL,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_BlogMovieDisLikes` (
  `id` int(11) NOT NULL,
  `blog_comm_id` int(20) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(20) NOT NULL DEFAULT 0,
  `movie_comm_id` int(20) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(20) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(50) NOT NULL DEFAULT 0,
  `movie_id` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blogmoviedislikes` (
  `id` int(11) NOT NULL,
  `blog_comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(11) NOT NULL DEFAULT 0,
  `movie_comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blogmovielikes` (
  `id` int(11) NOT NULL,
  `blog_comm_id` int(11) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(11) NOT NULL DEFAULT 0,
  `movie_comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_BlogMovieLikes` (
  `id` int(11) NOT NULL,
  `blog_comm_id` int(20) NOT NULL DEFAULT 0,
  `blog_commreply_id` int(20) NOT NULL DEFAULT 0,
  `movie_comm_id` int(20) NOT NULL DEFAULT 0,
  `movie_commreply_id` int(20) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(50) NOT NULL DEFAULT 0,
  `movie_id` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Blogs_Categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blogs_categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_blog_reaction` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Blog_Reaction` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `blog_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_calls` (
  `id` int(11) NOT NULL,
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
  `end_reason` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_call_statistics` (
  `id` int(11) NOT NULL,
  `call_id` int(11) DEFAULT NULL COMMENT 'Посилання на wo_calls',
  `group_call_id` int(11) DEFAULT NULL COMMENT 'Посилання на wo_group_calls',
  `user_id` int(11) NOT NULL,
  `call_type` varchar(32) NOT NULL,
  `duration` int(11) NOT NULL COMMENT 'Тривалість в секундах',
  `bandwidth_used` float DEFAULT NULL COMMENT 'Пропускна спроможність в МБ',
  `packet_loss` float DEFAULT NULL COMMENT 'Втрата пакетів в %',
  `average_latency` int(11) DEFAULT NULL COMMENT 'Середня затримка в мс',
  `created_at` timestamp NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ChannelAdmins` (
  `id` bigint(20) NOT NULL,
  `channel_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `role` enum('admin','moderator') DEFAULT 'admin',
  `added_at` int(11) NOT NULL,
  `added_by` int(11) NOT NULL COMMENT 'User who added this admin'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ChannelComments` (
  `id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `text` text NOT NULL,
  `reply_to_id` bigint(20) DEFAULT NULL COMMENT 'Reply to another comment',
  `reactions_count` int(11) DEFAULT 0,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) DEFAULT NULL,
  `deleted_at` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ChannelPosts` (
  `id` bigint(20) NOT NULL,
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
  `deleted_at` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Channels` (
  `id` bigint(20) NOT NULL,
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
  `qr_code` varchar(50) DEFAULT NULL COMMENT 'QR code for quick subscribe'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ChannelSettings` (
  `id` bigint(20) NOT NULL,
  `channel_id` bigint(20) NOT NULL,
  `show_author_signature` tinyint(1) DEFAULT 1,
  `allow_comments` tinyint(1) DEFAULT 1,
  `allow_reactions` tinyint(1) DEFAULT 1,
  `allow_shares` tinyint(1) DEFAULT 1,
  `moderate_comments` tinyint(1) DEFAULT 0,
  `slow_mode` int(11) DEFAULT 0 COMMENT 'Seconds between posts (0=disabled)',
  `notify_subscribers` tinyint(1) DEFAULT 1,
  `show_statistics` tinyint(1) DEFAULT 1,
  `updated_at` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ChannelSubscribers` (
  `id` bigint(20) NOT NULL,
  `channel_id` bigint(20) NOT NULL,
  `user_id` int(11) NOT NULL,
  `subscribed_at` int(11) NOT NULL,
  `is_muted` tinyint(1) DEFAULT 0 COMMENT 'Mute notifications'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_codes` (
  `id` int(11) NOT NULL,
  `code` varchar(50) NOT NULL DEFAULT '',
  `app_id` varchar(50) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Codes` (
  `id` int(11) NOT NULL,
  `code` varchar(50) NOT NULL DEFAULT '',
  `app_id` varchar(50) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Colored_Posts` (
  `id` int(11) NOT NULL,
  `color_1` varchar(50) NOT NULL DEFAULT '',
  `color_2` varchar(50) NOT NULL DEFAULT '',
  `text_color` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(250) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_colored_posts` (
  `id` int(11) NOT NULL,
  `color_1` varchar(50) NOT NULL DEFAULT '',
  `color_2` varchar(50) NOT NULL DEFAULT '',
  `text_color` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(250) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_commentlikes` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_CommentLikes` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_comments` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `record` varchar(255) NOT NULL DEFAULT '',
  `c_file` varchar(255) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Comments` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `record` varchar(255) NOT NULL DEFAULT '',
  `c_file` varchar(255) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_CommentWonders` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_commentwonders` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_comment_replies` (
  `id` int(11) NOT NULL,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `c_file` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Comment_Replies` (
  `id` int(11) NOT NULL,
  `comment_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `c_file` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_comment_replies_likes` (
  `id` int(11) NOT NULL,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Comment_Replies_Likes` (
  `id` int(11) NOT NULL,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Comment_Replies_Wonders` (
  `id` int(11) NOT NULL,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_comment_replies_wonders` (
  `id` int(11) NOT NULL,
  `reply_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Config` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_config` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` mediumtext NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_custompages` (
  `id` int(11) NOT NULL,
  `page_name` varchar(50) NOT NULL DEFAULT '',
  `page_title` varchar(200) NOT NULL DEFAULT '',
  `page_content` mediumtext DEFAULT NULL,
  `page_type` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_CustomPages` (
  `id` int(11) NOT NULL,
  `page_name` varchar(50) NOT NULL DEFAULT '',
  `page_title` varchar(200) NOT NULL DEFAULT '',
  `page_content` mediumtext DEFAULT NULL,
  `page_type` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_custom_fields` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` varchar(50) DEFAULT '',
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(50) NOT NULL DEFAULT '',
  `required` varchar(11) NOT NULL DEFAULT 'on',
  `options` mediumtext DEFAULT NULL,
  `active` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Custom_Fields` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` varchar(50) DEFAULT '',
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(50) NOT NULL DEFAULT '',
  `required` varchar(11) NOT NULL DEFAULT 'on',
  `options` mediumtext DEFAULT NULL,
  `active` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Egoing` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_egoing` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Einterested` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_einterested` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Einvited` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `inviter_id` int(11) NOT NULL,
  `invited_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_einvited` (
  `id` int(11) NOT NULL,
  `event_id` int(11) NOT NULL,
  `inviter_id` int(11) NOT NULL,
  `invited_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_emails` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `email_to` varchar(50) NOT NULL DEFAULT '',
  `subject` varchar(32) NOT NULL DEFAULT '',
  `message` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Emails` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `email_to` varchar(50) NOT NULL DEFAULT '',
  `subject` varchar(32) NOT NULL DEFAULT '',
  `message` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Events` (
  `id` int(11) NOT NULL,
  `name` varchar(150) NOT NULL DEFAULT '',
  `location` varchar(300) NOT NULL DEFAULT '',
  `description` mediumtext NOT NULL,
  `start_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_date` date NOT NULL,
  `end_time` time NOT NULL,
  `poster_id` int(11) NOT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-cover.jpg'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_events` (
  `id` int(11) NOT NULL,
  `name` varchar(150) NOT NULL DEFAULT '',
  `location` varchar(300) NOT NULL DEFAULT '',
  `description` mediumtext NOT NULL,
  `start_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_date` date NOT NULL,
  `end_time` time NOT NULL,
  `poster_id` int(11) NOT NULL,
  `cover` varchar(500) NOT NULL DEFAULT 'upload/photos/d-cover.jpg'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Family` (
  `id` int(11) NOT NULL,
  `member_id` int(11) NOT NULL,
  `member` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `requesting` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_family` (
  `id` int(11) NOT NULL,
  `member_id` int(11) NOT NULL,
  `member` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `requesting` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_followers` (
  `id` int(11) NOT NULL,
  `following_id` int(11) NOT NULL DEFAULT 0,
  `follower_id` int(11) NOT NULL DEFAULT 0,
  `is_typing` int(11) NOT NULL DEFAULT 0,
  `active` int(11) NOT NULL DEFAULT 1,
  `notify` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Followers` (
  `id` int(11) NOT NULL,
  `following_id` int(11) NOT NULL DEFAULT 0,
  `follower_id` int(11) NOT NULL DEFAULT 0,
  `is_typing` int(11) NOT NULL DEFAULT 0,
  `active` int(255) NOT NULL DEFAULT 1,
  `notify` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Forums` (
  `id` int(11) NOT NULL,
  `name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `sections` int(11) NOT NULL DEFAULT 0,
  `posts` int(11) NOT NULL DEFAULT 0,
  `last_post` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_forums` (
  `id` int(11) NOT NULL,
  `name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `sections` int(11) NOT NULL DEFAULT 0,
  `posts` int(11) NOT NULL DEFAULT 0,
  `last_post` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_forumthreadreplies` (
  `id` int(11) NOT NULL,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `poster_id` int(11) NOT NULL DEFAULT 0,
  `post_subject` varchar(300) NOT NULL DEFAULT '',
  `post_text` mediumtext NOT NULL,
  `post_quoted` int(11) NOT NULL DEFAULT 0,
  `posted_time` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ForumThreadReplies` (
  `id` int(11) NOT NULL,
  `thread_id` int(11) NOT NULL DEFAULT 0,
  `forum_id` int(11) NOT NULL DEFAULT 0,
  `poster_id` int(11) NOT NULL DEFAULT 0,
  `post_subject` varchar(300) NOT NULL DEFAULT '',
  `post_text` mediumtext NOT NULL,
  `post_quoted` int(11) NOT NULL DEFAULT 0,
  `posted_time` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Forum_Sections` (
  `id` int(11) NOT NULL,
  `section_name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_forum_sections` (
  `id` int(11) NOT NULL,
  `section_name` varchar(200) NOT NULL DEFAULT '',
  `description` varchar(300) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_forum_threads` (
  `id` int(11) NOT NULL,
  `user` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `headline` varchar(300) NOT NULL DEFAULT '',
  `post` mediumtext NOT NULL,
  `posted` varchar(20) NOT NULL DEFAULT '0',
  `last_post` int(11) NOT NULL DEFAULT 0,
  `forum` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Forum_Threads` (
  `id` int(11) NOT NULL,
  `user` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `headline` varchar(300) NOT NULL DEFAULT '',
  `post` mediumtext NOT NULL,
  `posted` varchar(20) NOT NULL DEFAULT '0',
  `last_post` int(11) NOT NULL DEFAULT 0,
  `forum` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_funding` (
  `id` int(11) NOT NULL,
  `hashed_id` varchar(100) NOT NULL DEFAULT '',
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` longtext DEFAULT NULL,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(200) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Funding` (
  `id` int(11) NOT NULL,
  `hashed_id` varchar(100) NOT NULL DEFAULT '',
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` longtext DEFAULT NULL,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(200) NOT NULL DEFAULT '',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Funding_Raise` (
  `id` int(11) NOT NULL,
  `funding_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_funding_raise` (
  `id` int(11) NOT NULL,
  `funding_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` varchar(11) NOT NULL DEFAULT '0',
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Games` (
  `id` int(11) NOT NULL,
  `game_name` varchar(50) NOT NULL,
  `game_avatar` varchar(100) NOT NULL,
  `game_link` varchar(100) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_games` (
  `id` int(11) NOT NULL,
  `game_name` varchar(50) NOT NULL,
  `game_avatar` varchar(100) NOT NULL,
  `game_link` varchar(100) NOT NULL,
  `active` enum('0','1') NOT NULL DEFAULT '1',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Games_Players` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `game_id` int(11) NOT NULL DEFAULT 0,
  `last_play` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_games_players` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `game_id` int(11) NOT NULL DEFAULT 0,
  `last_play` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Gender` (
  `id` int(11) NOT NULL,
  `gender_id` varchar(50) NOT NULL DEFAULT '0',
  `image` varchar(300) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_gender` (
  `id` int(11) NOT NULL,
  `gender_id` varchar(50) NOT NULL DEFAULT '0',
  `image` varchar(300) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_gifts` (
  `id` int(10) UNSIGNED NOT NULL,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Gifts` (
  `id` int(11) UNSIGNED NOT NULL,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_GroupAdmins` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `privacy` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `members` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_group` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_groupadmins` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `privacy` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `members` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_group` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_GroupChat` (
  `group_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `group_name` varchar(255) NOT NULL DEFAULT '',
  `description` text DEFAULT NULL,
  `is_private` enum('0','1') NOT NULL DEFAULT '0',
  `settings` text DEFAULT NULL,
  `avatar` varchar(3000) NOT NULL DEFAULT 'upload/photos/d-group.jpg',
  `time` varchar(30) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'group',
  `destruct_at` int(11) UNSIGNED NOT NULL DEFAULT 0,
  `username` varchar(100) DEFAULT NULL COMMENT 'Унікальний @username для публічних каналів',
  `subscribers_count` int(10) UNSIGNED DEFAULT 0 COMMENT 'Кількість підписників',
  `posts_count` int(10) UNSIGNED DEFAULT 0 COMMENT 'Кількість постів',
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
  `pinned_message_id` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_groupchat` (
  `group_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `group_name` varchar(20) NOT NULL DEFAULT '',
  `avatar` varchar(3000) NOT NULL DEFAULT 'upload/photos/d-group.jpg',
  `time` varchar(30) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'group',
  `destruct_at` int(10) UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_groupchatusers` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `group_id` int(11) NOT NULL,
  `active` enum('1','0') NOT NULL DEFAULT '1',
  `last_seen` varchar(50) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_GroupChatUsers` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `group_id` int(11) NOT NULL,
  `role` enum('owner','admin','moderator','member') NOT NULL DEFAULT 'member',
  `active` enum('1','0') NOT NULL DEFAULT '1',
  `last_seen` varchar(50) NOT NULL DEFAULT '0',
  `muted` tinyint(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER `update_channel_subscribers_count_delete` AFTER DELETE ON `Wo_GroupChatUsers` FOR EACH ROW BEGIN
  DECLARE channel_type VARCHAR(50)$$
DELIMITER ;

CREATE TRIGGER `update_channel_subscribers_count_insert` AFTER INSERT ON `Wo_GroupChatUsers` FOR EACH ROW BEGIN
  DECLARE channel_type VARCHAR(50)$$
DELIMITER ;

CREATE TABLE `Wo_GroupJoinRequests` (
  `id` bigint(20) NOT NULL,
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `message` text DEFAULT NULL,
  `status` enum('pending','approved','rejected') DEFAULT 'pending',
  `created_time` bigint(20) DEFAULT NULL,
  `reviewed_by` bigint(20) DEFAULT NULL,
  `reviewed_time` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `Wo_Groups` (
  `id` int(11) NOT NULL,
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
  `qr_code` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_groups` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Groups_Categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_groups_categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_GroupTopics` (
  `topic_id` int(10) UNSIGNED NOT NULL,
  `group_id` int(10) UNSIGNED NOT NULL,
  `topic_name` varchar(255) NOT NULL,
  `topic_icon` varchar(50) DEFAULT '?',
  `topic_color` varchar(7) DEFAULT '#0084FF',
  `created_by` int(10) UNSIGNED NOT NULL,
  `created_time` int(10) UNSIGNED NOT NULL,
  `is_general` tinyint(1) DEFAULT 0,
  `message_count` int(10) UNSIGNED DEFAULT 0,
  `last_message_time` int(10) UNSIGNED DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_group_calls` (
  `id` int(11) NOT NULL,
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
  `max_participants` int(11) DEFAULT NULL COMMENT 'Максимум учасників'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_group_call_participants` (
  `id` int(11) NOT NULL,
  `group_call_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `sdp_answer` longtext DEFAULT NULL COMMENT 'SDP answer від конкретного користувача',
  `joined_at` timestamp NULL DEFAULT current_timestamp(),
  `left_at` timestamp NULL DEFAULT NULL,
  `duration` int(11) DEFAULT NULL COMMENT 'Тривалість участі в секундах'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Group_Members` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_group_members` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_hashtags` (
  `id` int(11) NOT NULL,
  `hash` varchar(255) NOT NULL DEFAULT '',
  `tag` varchar(255) NOT NULL DEFAULT '',
  `last_trend_time` int(11) NOT NULL DEFAULT 0,
  `trend_use_num` int(11) NOT NULL DEFAULT 0,
  `expire` date DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Hashtags` (
  `id` int(11) NOT NULL,
  `hash` varchar(255) NOT NULL DEFAULT '',
  `tag` varchar(255) NOT NULL DEFAULT '',
  `last_trend_time` int(11) NOT NULL DEFAULT 0,
  `trend_use_num` int(11) NOT NULL DEFAULT 0,
  `expire` date DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_HiddenPosts` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_hiddenposts` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_HTML_Emails` (
  `id` int(11) NOT NULL,
  `name` varchar(100) NOT NULL DEFAULT '',
  `value` text DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_ice_candidates` (
  `id` int(11) NOT NULL,
  `room_name` varchar(100) NOT NULL,
  `candidate` longtext NOT NULL,
  `sdp_m_line_index` int(11) NOT NULL,
  `sdp_mid` varchar(100) NOT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_invitation_links` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  `code` varchar(300) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Invitation_Links` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0,
  `code` varchar(300) NOT NULL DEFAULT '',
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_job` (
  `id` int(11) NOT NULL,
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
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Job` (
  `id` int(11) NOT NULL,
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
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Job_Apply` (
  `id` int(11) NOT NULL,
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
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_job_apply` (
  `id` int(11) NOT NULL,
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
  `time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Job_Categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_job_categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_LangIso` (
  `id` int(11) NOT NULL,
  `lang_name` varchar(100) NOT NULL DEFAULT '',
  `iso` varchar(50) NOT NULL DEFAULT '',
  `image` varchar(300) NOT NULL DEFAULT '',
  `direction` varchar(50) NOT NULL DEFAULT 'ltr'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Langs` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) DEFAULT NULL,
  `type` varchar(100) NOT NULL DEFAULT '',
  `english` longtext DEFAULT NULL,
  `russian` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_langs` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) DEFAULT NULL,
  `type` varchar(100) NOT NULL DEFAULT '',
  `english` longtext DEFAULT NULL,
  `russian` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_likes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Likes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Live_Sub_Users` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `is_watching` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_live_sub_users` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `is_watching` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Manage_Pro` (
  `id` int(11) NOT NULL,
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
  `features` varchar(800) DEFAULT '{"can_use_funding":1,"can_use_jobs":1,"can_use_games":1,"can_use_market":1,"can_use_events":1,"can_use_forum":1,"can_use_groups":1,"can_use_pages":1,"can_use_audio_call":1,"can_use_video_call":1,"can_use_offer":1,"can_use_blog":1,"can_use_movies":1,"can_use_story":1,"can_use_stickers":1,"can_use_gif":1,"can_use_gift":1,"can_use_nearby":1,"can_use_video_upload":1,"can_use_audio_upload":1,"can_use_shout_box":1,"can_use_colored_posts":1,"can_use_poll":1,"can_use_live":1,"can_use_background":1,"can_use_chat":1,"can_use_ai_image":1,"can_use_ai_post":1,"can_use_ai_user":1,"can_use_ai_blog":1}'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_manage_pro` (
  `id` int(11) NOT NULL,
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
  `features` varchar(800) DEFAULT '{"can_use_funding":1,"can_use_jobs":1,"can_use_games":1,"can_use_market":1,"can_use_events":1,"can_use_forum":1,"can_use_groups":1,"can_use_pages":1,"can_use_audio_call":1,"can_use_video_call":1,"can_use_offer":1,"can_use_blog":1,"can_use_movies":1,"can_use_story":1,"can_use_stickers":1,"can_use_gif":1,"can_use_gift":1,"can_use_nearby":1,"can_use_video_upload":1,"can_use_audio_upload":1,"can_use_shout_box":1,"can_use_colored_posts":1,"can_use_poll":1,"can_use_live":1,"can_use_background":1,"can_use_chat":1,"can_use_ai_image":1,"can_use_ai_post":1,"can_use_ai_user":1,"can_use_ai_blog":1}'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MessageCommentReactions` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `comment_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` int(11) NOT NULL,
  `reaction` varchar(10) NOT NULL COMMENT 'Emoji реакції',
  `time` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MessageComments` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `message_id` int(11) NOT NULL COMMENT 'ID повідомлення з Wo_Messages',
  `user_id` int(11) NOT NULL,
  `text` text NOT NULL,
  `time` int(11) NOT NULL,
  `edited_time` int(11) DEFAULT NULL,
  `reply_to_comment_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT 'Відповідь на коментар'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Messages` (
  `id` int(11) NOT NULL,
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
  `remove_at` int(11) UNSIGNED NOT NULL DEFAULT 0,
  `topic_id` int(11) DEFAULT NULL,
  `iv` varchar(255) DEFAULT NULL,
  `tag` varchar(255) DEFAULT NULL,
  `cipher_version` int(11) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER `update_channel_posts_count_delete` AFTER DELETE ON `Wo_Messages` FOR EACH ROW BEGIN
  DECLARE channel_type VARCHAR(50)$$
DELIMITER ;

CREATE TRIGGER `update_channel_posts_count_insert` AFTER INSERT ON `Wo_Messages` FOR EACH ROW BEGIN
  DECLARE channel_type VARCHAR(50)$$
DELIMITER ;

CREATE TABLE `wo_messages` (
  `id` int(11) NOT NULL,
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
  `remove_at` int(10) UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MessageViews` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `message_id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL COMMENT 'NULL для анонімних переглядів',
  `time` int(11) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MonetizationSubscription` (
  `id` int(11) UNSIGNED NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL DEFAULT 0,
  `monetization_id` int(11) NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `last_payment_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expire` int(10) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_monetizationsubscription` (
  `id` int(10) UNSIGNED NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `monetization_id` int(11) NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `last_payment_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expire` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MovieCommentReplies` (
  `id` int(11) NOT NULL,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_moviecommentreplies` (
  `id` int(11) NOT NULL,
  `comm_id` int(11) NOT NULL DEFAULT 0,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `likes` int(11) NOT NULL DEFAULT 0,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_MovieComments` (
  `id` int(11) NOT NULL,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_moviecomments` (
  `id` int(11) NOT NULL,
  `movie_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `posted` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_movies` (
  `id` int(11) NOT NULL,
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
  `rating` varchar(11) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Movies` (
  `id` int(11) NOT NULL,
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
  `rating` varchar(11) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Mute` (
  `id` int(11) NOT NULL,
  `chat_id` int(11) NOT NULL DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `notify` varchar(5) NOT NULL DEFAULT 'yes',
  `call_chat` varchar(5) NOT NULL DEFAULT 'yes',
  `archive` varchar(5) NOT NULL DEFAULT 'no',
  `pin` varchar(5) NOT NULL DEFAULT 'no',
  `fav` varchar(11) NOT NULL DEFAULT 'no',
  `type` varchar(10) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Mute_Story` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_user_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Notifications` (
  `id` int(255) NOT NULL,
  `notifier_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(11) UNSIGNED DEFAULT 0,
  `comment_id` int(11) UNSIGNED DEFAULT 0,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_notifications` (
  `id` int(11) NOT NULL,
  `notifier_id` int(11) NOT NULL DEFAULT 0,
  `recipient_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `reply_id` int(10) UNSIGNED DEFAULT 0,
  `comment_id` int(10) UNSIGNED DEFAULT 0,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Offers` (
  `id` int(11) NOT NULL,
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
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_offers` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pageadmins` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `info` int(11) NOT NULL DEFAULT 1,
  `social` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `design` int(11) NOT NULL DEFAULT 1,
  `admins` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_page` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_PageAdmins` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `general` int(11) NOT NULL DEFAULT 1,
  `info` int(11) NOT NULL DEFAULT 1,
  `social` int(11) NOT NULL DEFAULT 1,
  `avatar` int(11) NOT NULL DEFAULT 1,
  `design` int(11) NOT NULL DEFAULT 1,
  `admins` int(11) NOT NULL DEFAULT 0,
  `analytics` int(11) NOT NULL DEFAULT 1,
  `delete_page` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pagerating` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `valuation` int(11) DEFAULT 0,
  `review` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_PageRating` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `valuation` int(11) DEFAULT 0,
  `review` mediumtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Pages` (
  `page_id` int(11) NOT NULL,
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
  `time` int(20) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pages` (
  `page_id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Pages_Categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pages_categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pages_invites` (
  `id` int(11) NOT NULL,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `inviter_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Pages_Invites` (
  `id` int(11) NOT NULL,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `inviter_id` int(11) NOT NULL DEFAULT 0,
  `invited_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Pages_Likes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pages_likes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_PatreonSubscribers` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `subscriber_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Payments` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 0,
  `type` varchar(15) NOT NULL DEFAULT '',
  `date` varchar(30) NOT NULL DEFAULT '',
  `time` int(20) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_payments` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `amount` int(11) NOT NULL DEFAULT 0,
  `type` varchar(15) NOT NULL DEFAULT '',
  `date` varchar(30) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_payment_transactions` (
  `id` int(10) UNSIGNED NOT NULL,
  `userid` int(10) UNSIGNED NOT NULL,
  `kind` varchar(100) NOT NULL,
  `amount` decimal(11,0) UNSIGNED NOT NULL,
  `transaction_dt` timestamp NOT NULL DEFAULT current_timestamp(),
  `notes` mediumtext NOT NULL,
  `admin_commission` decimal(11,0) UNSIGNED DEFAULT 0,
  `extra` varchar(1000) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Payment_Transactions` (
  `id` int(11) UNSIGNED NOT NULL,
  `userid` int(11) UNSIGNED NOT NULL,
  `kind` varchar(100) NOT NULL,
  `amount` decimal(11,0) UNSIGNED NOT NULL,
  `transaction_dt` timestamp NOT NULL DEFAULT current_timestamp(),
  `notes` mediumtext NOT NULL,
  `admin_commission` decimal(11,0) UNSIGNED DEFAULT 0,
  `extra` varchar(1000) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_PendingPayments` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `payment_data` varchar(500) NOT NULL DEFAULT '',
  `method_name` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_PinnedPosts` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pinnedposts` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Pokes` (
  `id` int(11) NOT NULL,
  `received_user_id` int(11) NOT NULL DEFAULT 0,
  `send_user_id` int(11) NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_pokes` (
  `id` int(11) NOT NULL,
  `received_user_id` int(11) NOT NULL DEFAULT 0,
  `send_user_id` int(11) NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Polls` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `text` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_posts` (
  `id` int(11) NOT NULL,
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
  `ai_post` int(10) UNSIGNED DEFAULT 0,
  `videoTitle` varchar(200) DEFAULT NULL,
  `is_reel` tinyint(4) DEFAULT 0,
  `blur_url` varchar(300) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Posts` (
  `id` int(11) NOT NULL,
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
  `ai_post` int(2) UNSIGNED DEFAULT 0,
  `videoTitle` varchar(200) DEFAULT NULL,
  `is_reel` tinyint(4) DEFAULT 0,
  `blur_url` varchar(300) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_productreview` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  `star` int(11) NOT NULL DEFAULT 1,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ProductReview` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `review` mediumtext DEFAULT NULL,
  `star` int(11) NOT NULL DEFAULT 1,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_products` (
  `id` int(11) NOT NULL,
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
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Products` (
  `id` int(11) NOT NULL,
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
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_products_categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Products_Categories` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(160) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Products_Media` (
  `id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_products_media` (
  `id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `image` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ProfileFields` (
  `id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` mediumtext DEFAULT NULL,
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(32) NOT NULL DEFAULT 'profile',
  `registration_page` int(11) NOT NULL DEFAULT 0,
  `profile_page` int(11) NOT NULL DEFAULT 0,
  `select_type` varchar(32) NOT NULL DEFAULT 'none',
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_profilefields` (
  `id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `type` mediumtext DEFAULT NULL,
  `length` int(11) NOT NULL DEFAULT 0,
  `placement` varchar(32) NOT NULL DEFAULT 'profile',
  `registration_page` int(11) NOT NULL DEFAULT 0,
  `profile_page` int(11) NOT NULL DEFAULT 0,
  `select_type` varchar(32) NOT NULL DEFAULT 'none',
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Purchases` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `data` mediumtext DEFAULT NULL,
  `final_price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_purchases` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `data` mediumtext DEFAULT NULL,
  `final_price` float NOT NULL DEFAULT 0,
  `commission` float NOT NULL DEFAULT 0,
  `price` float NOT NULL DEFAULT 0,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Reactions` (
  `id` int(11) NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL DEFAULT 0,
  `post_id` int(11) UNSIGNED DEFAULT 0,
  `comment_id` int(11) UNSIGNED DEFAULT 0,
  `replay_id` int(11) UNSIGNED DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_reactions` (
  `id` int(11) NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `post_id` int(10) UNSIGNED DEFAULT 0,
  `comment_id` int(10) UNSIGNED DEFAULT 0,
  `replay_id` int(10) UNSIGNED DEFAULT 0,
  `message_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_reactions_types` (
  `id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL DEFAULT '',
  `wowonder_icon` varchar(300) NOT NULL DEFAULT '',
  `sunshine_icon` varchar(300) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Reactions_Types` (
  `id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL DEFAULT '',
  `wowonder_icon` varchar(300) NOT NULL DEFAULT '',
  `sunshine_icon` varchar(300) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_recentsearches` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `search_id` int(11) NOT NULL DEFAULT 0,
  `search_type` varchar(32) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_RecentSearches` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `search_id` int(11) NOT NULL DEFAULT 0,
  `search_type` varchar(32) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_refund` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `pro_type` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Refund` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `order_hash_id` varchar(100) NOT NULL DEFAULT '',
  `pro_type` varchar(50) NOT NULL DEFAULT '',
  `description` mediumtext DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `time` int(50) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Relationship` (
  `id` int(11) NOT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `relationship` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_relationship` (
  `id` int(11) NOT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `relationship` int(11) NOT NULL DEFAULT 0,
  `active` enum('0','1') NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Reports` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(15) UNSIGNED NOT NULL DEFAULT 0,
  `profile_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(15) NOT NULL DEFAULT 0,
  `group_id` int(15) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `reason` varchar(100) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_reports` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `comment_id` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `profile_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `group_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` mediumtext DEFAULT NULL,
  `reason` varchar(100) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_SavedPosts` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_savedposts` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_ScheduledPosts` (
  `id` bigint(20) NOT NULL,
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
  `notify_members` tinyint(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `Wo_Stickers` (
  `id` int(11) UNSIGNED NOT NULL,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_stickers` (
  `id` int(10) UNSIGNED NOT NULL,
  `name` varchar(250) DEFAULT NULL,
  `media_file` varchar(250) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_storycomments` (
  `id` int(11) NOT NULL,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_StoryComments` (
  `id` int(11) NOT NULL,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `text` text DEFAULT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `wo_storyreactions` (
  `id` int(11) NOT NULL,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(30) NOT NULL DEFAULT 'like' COMMENT 'Reaction type: like, love, haha, wow, sad, angry',
  `time` int(11) NOT NULL DEFAULT 0 COMMENT 'Unix timestamp when reaction was added'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Реакции на stories';

CREATE TABLE `Wo_StoryReactions` (
  `id` int(11) NOT NULL,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `reaction` varchar(30) NOT NULL DEFAULT 'like' COMMENT 'Reaction type: like, love, haha, wow, sad, angry',
  `time` int(11) NOT NULL DEFAULT 0 COMMENT 'Unix timestamp when reaction was added'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Реакции на stories';

CREATE TABLE `wo_story_seen` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `time` varchar(20) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Story_Seen` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `time` varchar(20) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Subgroups` (
  `id` bigint(20) NOT NULL,
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
  `pinned_message_id` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `wo_sub_categories` (
  `id` int(11) NOT NULL,
  `category_id` int(11) NOT NULL DEFAULT 0,
  `lang_key` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(200) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Sub_Categories` (
  `id` int(11) NOT NULL,
  `category_id` int(11) NOT NULL DEFAULT 0,
  `lang_key` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(200) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Terms` (
  `id` int(11) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT '',
  `text` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_terms` (
  `id` int(11) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT '',
  `text` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Tokens` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `app_id` int(11) NOT NULL DEFAULT 0,
  `token` varchar(200) NOT NULL DEFAULT '',
  `time` int(32) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_tokens` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `app_id` int(11) NOT NULL DEFAULT 0,
  `token` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UploadedMedia` (
  `id` int(11) NOT NULL,
  `filename` varchar(200) NOT NULL DEFAULT '',
  `storage` varchar(34) NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_useraddress` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `phone` varchar(50) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `city` varchar(100) NOT NULL DEFAULT '',
  `zip` varchar(20) NOT NULL DEFAULT '',
  `address` varchar(500) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserAddress` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `phone` varchar(50) NOT NULL DEFAULT '',
  `country` varchar(100) NOT NULL DEFAULT '',
  `city` varchar(100) NOT NULL DEFAULT '',
  `zip` varchar(20) NOT NULL DEFAULT '',
  `address` varchar(500) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserAds` (
  `id` int(11) NOT NULL,
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
  `budget` float UNSIGNED NOT NULL DEFAULT 0,
  `spent` float UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_userads_data` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `ad_id` int(11) NOT NULL DEFAULT 0,
  `clicks` int(11) NOT NULL DEFAULT 0,
  `views` int(11) NOT NULL DEFAULT 0,
  `spend` float UNSIGNED NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserAds_Data` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `ad_id` int(11) NOT NULL DEFAULT 0,
  `clicks` int(15) NOT NULL DEFAULT 0,
  `views` int(15) NOT NULL DEFAULT 0,
  `spend` float UNSIGNED NOT NULL DEFAULT 0,
  `dt` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserCard` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_usercard` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `product_id` int(11) NOT NULL DEFAULT 0,
  `units` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserCertification` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `issuing_organization` varchar(100) NOT NULL DEFAULT '',
  `credential_id` varchar(100) NOT NULL DEFAULT '',
  `credential_url` varchar(300) NOT NULL DEFAULT '',
  `certification_start` date NOT NULL,
  `certification_end` date NOT NULL,
  `pdf` varchar(300) NOT NULL DEFAULT '',
  `filename` varchar(200) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserCloudBackupSettings` (
  `id` int(11) NOT NULL,
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
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserExperience` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_userfields` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserFields` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserLanguages` (
  `id` int(11) NOT NULL,
  `lang_key` varchar(200) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserMediaSettings` (
  `id` int(11) NOT NULL,
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
  `updated_at` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_usermonetization` (
  `id` int(10) UNSIGNED NOT NULL,
  `user_id` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `price` varchar(11) NOT NULL DEFAULT '0',
  `currency` varchar(5) NOT NULL DEFAULT 'USD',
  `paid_every` int(11) NOT NULL DEFAULT 1,
  `period` varchar(10) NOT NULL DEFAULT 'Daily',
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserMonetization` (
  `id` int(11) UNSIGNED NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL DEFAULT 0,
  `price` varchar(11) NOT NULL DEFAULT '0',
  `currency` varchar(5) NOT NULL DEFAULT 'USD',
  `paid_every` int(3) NOT NULL DEFAULT 1,
  `period` varchar(10) NOT NULL DEFAULT 'Daily',
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserOpenTo` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `job_title` varchar(100) NOT NULL DEFAULT '',
  `job_location` varchar(100) NOT NULL DEFAULT '',
  `workplaces` varchar(600) NOT NULL DEFAULT '',
  `job_type` varchar(600) NOT NULL DEFAULT '',
  `services` varchar(1000) NOT NULL DEFAULT '',
  `description` varchar(1000) NOT NULL DEFAULT '',
  `type` varchar(100) NOT NULL DEFAULT '',
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_userorders` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserOrders` (
  `id` int(11) NOT NULL,
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
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserProjects` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `description` varchar(600) NOT NULL DEFAULT '',
  `associated_with` varchar(200) NOT NULL DEFAULT '',
  `project_url` varchar(300) NOT NULL DEFAULT '',
  `project_start` date NOT NULL,
  `project_end` date NOT NULL,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserRatings` (
  `id` int(11) NOT NULL,
  `rater_id` int(11) NOT NULL COMMENT 'ID користувача, який ставить оцінку',
  `rated_user_id` int(11) NOT NULL COMMENT 'ID користувача, якому ставлять оцінку',
  `rating_type` enum('like','dislike') NOT NULL COMMENT 'Тип оцінки: like (?) або dislike (?)',
  `comment` text DEFAULT NULL COMMENT 'Необов`язковий коментар до оцінки',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Система оцінки користувачів (карма/довіра)';

CREATE TRIGGER `update_user_rating_after_delete` AFTER DELETE ON `Wo_UserRatings` FOR EACH ROW BEGIN
    DECLARE total_likes INT DEFAULT 0$$
DELIMITER ;

CREATE TRIGGER `update_user_rating_after_insert` AFTER INSERT ON `Wo_UserRatings` FOR EACH ROW BEGIN
    DECLARE total_likes INT DEFAULT 0$$
DELIMITER ;

CREATE TRIGGER `update_user_rating_after_update` AFTER UPDATE ON `Wo_UserRatings` FOR EACH ROW BEGIN
    DECLARE total_likes INT DEFAULT 0$$
DELIMITER ;

CREATE TABLE `Wo_UserRatingSummary` (
`user_id` int(11)
,`username` varchar(32)
,`first_name` varchar(60)
,`last_name` varchar(32)
,`avatar` varchar(100)
,`rating_likes` int(11)
,`rating_dislikes` int(11)
,`rating_score` decimal(4,2)
,`trust_level` enum('untrusted','neutral','trusted','verified')
,`total_ratings` bigint(12)
,`trust_level_label` varchar(13)
);

CREATE TABLE `Wo_Users` (
  `user_id` int(11) NOT NULL,
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
  `points` float UNSIGNED NOT NULL DEFAULT 0,
  `daily_points` int(11) NOT NULL DEFAULT 0,
  `converted_points` float UNSIGNED NOT NULL DEFAULT 0,
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
  `trust_level` enum('untrusted','neutral','trusted','verified') NOT NULL DEFAULT 'neutral' COMMENT 'Рівень довіри'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_userschat` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `conversation_user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `color` varchar(100) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'chat',
  `disappearing_time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UsersChat` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `conversation_user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `color` varchar(100) NOT NULL DEFAULT '',
  `type` varchar(50) NOT NULL DEFAULT 'chat',
  `disappearing_time` varchar(50) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserSkills` (
  `id` int(11) NOT NULL,
  `name` varchar(300) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserStory` (
  `id` int(11) NOT NULL,
  `user_id` int(50) NOT NULL DEFAULT 0,
  `page_id` int(11) DEFAULT NULL,
  `title` varchar(100) NOT NULL DEFAULT '',
  `description` varchar(300) NOT NULL DEFAULT '',
  `posted` varchar(50) NOT NULL DEFAULT '',
  `expire` varchar(100) DEFAULT '',
  `thumbnail` varchar(100) NOT NULL DEFAULT '',
  `ad_id` int(11) DEFAULT NULL,
  `comment_count` int(11) NOT NULL DEFAULT 0 COMMENT 'Cached comment count',
  `reaction_count` int(11) NOT NULL DEFAULT 0 COMMENT 'Cached total reaction count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserStoryMedia` (
  `id` int(11) NOT NULL,
  `story_id` int(30) NOT NULL DEFAULT 0,
  `type` varchar(30) NOT NULL DEFAULT '',
  `filename` mediumtext DEFAULT NULL,
  `expire` varchar(100) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0 COMMENT 'Video duration in seconds'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_userstorymedia` (
  `id` int(11) NOT NULL,
  `story_id` int(11) NOT NULL DEFAULT 0,
  `type` varchar(30) NOT NULL DEFAULT '',
  `filename` mediumtext DEFAULT NULL,
  `expire` varchar(100) DEFAULT '',
  `duration` int(11) NOT NULL DEFAULT 0 COMMENT 'Video duration in seconds'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_UserTiers` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `title` varchar(200) NOT NULL DEFAULT '',
  `price` float NOT NULL DEFAULT 0,
  `image` varchar(400) NOT NULL DEFAULT '',
  `description` varchar(1000) NOT NULL DEFAULT '',
  `chat` varchar(100) NOT NULL DEFAULT '',
  `live_stream` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_user_gifts` (
  `id` int(11) NOT NULL,
  `from` int(11) NOT NULL DEFAULT 0,
  `to` int(11) NOT NULL DEFAULT 0,
  `gift_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_User_Gifts` (
  `id` int(11) NOT NULL,
  `from` int(11) NOT NULL DEFAULT 0,
  `to` int(11) NOT NULL DEFAULT 0,
  `gift_id` int(11) NOT NULL DEFAULT 0,
  `time` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `Wo_Verification_Requests` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `message` mediumtext DEFAULT NULL,
  `user_name` varchar(150) NOT NULL DEFAULT '',
  `passport` varchar(3000) NOT NULL DEFAULT '',
  `photo` varchar(3000) NOT NULL DEFAULT '',
  `type` varchar(32) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_verification_requests` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `page_id` int(11) NOT NULL DEFAULT 0,
  `message` mediumtext DEFAULT NULL,
  `user_name` varchar(150) NOT NULL DEFAULT '',
  `passport` varchar(3000) NOT NULL DEFAULT '',
  `photo` varchar(3000) NOT NULL DEFAULT '',
  `type` varchar(32) NOT NULL DEFAULT '',
  `seen` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wo_videocalles` (
  `id` int(11) NOT NULL,
  `access_token` text DEFAULT NULL,
  `access_token_2` text DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

CREATE TABLE `Wo_VideoCalles` (
  `id` int(11) NOT NULL,
  `access_token` text DEFAULT NULL,
  `access_token_2` text DEFAULT NULL,
  `from_id` int(11) NOT NULL DEFAULT 0,
  `to_id` int(11) NOT NULL DEFAULT 0,
  `room_name` varchar(50) NOT NULL DEFAULT '',
  `active` int(11) NOT NULL DEFAULT 0,
  `called` int(11) NOT NULL DEFAULT 0,
  `time` int(11) NOT NULL DEFAULT 0,
  `declined` int(11) NOT NULL DEFAULT 0,
  `status` varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

CREATE TABLE `wo_votes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `option_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

CREATE TABLE `Wo_Votes` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `option_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

CREATE TABLE `wo_wonders` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `type` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Wo_Wonders` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL DEFAULT 0,
  `post_id` int(11) NOT NULL DEFAULT 0,
  `type` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

DROP TABLE IF EXISTS `Wo_UserRatingSummary`;

CREATE ALGORITHM=UNDEFINED DEFINER=`social`@`localhost` SQL SECURITY DEFINER VIEW `Wo_UserRatingSummary`  AS SELECT `u`.`user_id` AS `user_id`, `u`.`username` AS `username`, `u`.`first_name` AS `first_name`, `u`.`last_name` AS `last_name`, `u`.`avatar` AS `avatar`, `u`.`rating_likes` AS `rating_likes`, `u`.`rating_dislikes` AS `rating_dislikes`, `u`.`rating_score` AS `rating_score`, `u`.`trust_level` AS `trust_level`, `u`.`rating_likes`+ `u`.`rating_dislikes` AS `total_ratings`, CASE WHEN `u`.`trust_level` = 'verified' THEN 'Перевірений ✅' WHEN `u`.`trust_level` = 'trusted' THEN 'Надійний ⭐' WHEN `u`.`trust_level` = 'neutral' THEN 'Нейтральний 🔵' WHEN `u`.`trust_level` = 'untrusted' THEN 'Ненадійний ⚠️' ELSE 'Невідомий' END AS `trust_level_label` FROM `Wo_Users` AS `u` WHERE `u`.`active` = '1' ;

ALTER TABLE `bank_receipts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `fund_id` (`fund_id`),
  ADD KEY `created_at` (`created_at`),
  ADD KEY `approved_at` (`approved_at`),
  ADD KEY `approved` (`approved`),
  ADD KEY `mode` (`mode`);

ALTER TABLE `broadcast`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id_2` (`user_id`),
  ADD KEY `time_2` (`time`);

ALTER TABLE `broadcast_users`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `broadcast_id` (`broadcast_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `stickers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `pack_id` (`pack_id`);

ALTER TABLE `sticker_packs`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wondertage_settings`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`);

ALTER TABLE `Wo_Activities`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `activity_type` (`activity_type`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`post_id`,`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `follow_id` (`follow_id`);

ALTER TABLE `wo_activities`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `activity_type` (`activity_type`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`post_id`,`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `follow_id` (`follow_id`);

ALTER TABLE `Wo_AdminInvitations`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`(255));

ALTER TABLE `wo_admininvitations`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`(255));

ALTER TABLE `Wo_Ads`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `type` (`type`);

ALTER TABLE `wo_ads`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `type` (`type`);

ALTER TABLE `Wo_Affiliates_Requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `status` (`status`),
  ADD KEY `type` (`type`);

ALTER TABLE `wo_affiliates_requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `status` (`status`),
  ADD KEY `type` (`type`);

ALTER TABLE `wo_agoravideocall`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `type` (`type`),
  ADD KEY `room_name` (`room_name`),
  ADD KEY `time` (`time`),
  ADD KEY `status` (`status`);

ALTER TABLE `Wo_AgoraVideoCall`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `type` (`type`),
  ADD KEY `room_name` (`room_name`),
  ADD KEY `time` (`time`),
  ADD KEY `status` (`status`);

ALTER TABLE `wo_albums_media`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `order1` (`post_id`,`id`),
  ADD KEY `parent_id` (`parent_id`),
  ADD KEY `review_id` (`review_id`);

ALTER TABLE `Wo_Albums_Media`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `order1` (`post_id`,`id`),
  ADD KEY `parent_id` (`parent_id`),
  ADD KEY `review_id` (`review_id`);

ALTER TABLE `wo_announcement`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_Announcement`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`);

ALTER TABLE `wo_announcement_views`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `announcement_id` (`announcement_id`);

ALTER TABLE `Wo_Announcement_Views`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `announcement_id` (`announcement_id`);

ALTER TABLE `Wo_Apps`
  ADD PRIMARY KEY (`id`),
  ADD KEY `app_user_id` (`app_user_id`),
  ADD KEY `app_id` (`app_id`),
  ADD KEY `active` (`active`);

ALTER TABLE `wo_apps`
  ADD PRIMARY KEY (`id`),
  ADD KEY `app_user_id` (`app_user_id`),
  ADD KEY `app_id` (`app_id`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_AppsSessions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `session_id` (`session_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `platform` (`platform`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_appssessions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `session_id` (`session_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `platform` (`platform`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_apps_hash`
  ADD PRIMARY KEY (`id`),
  ADD KEY `hash_id` (`hash_id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Apps_Hash`
  ADD PRIMARY KEY (`id`),
  ADD KEY `hash_id` (`hash_id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_apps_permission`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`app_id`);

ALTER TABLE `Wo_Apps_Permission`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`app_id`);

ALTER TABLE `wo_audiocalls`
  ADD PRIMARY KEY (`id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `call_id` (`call_id`),
  ADD KEY `called` (`called`),
  ADD KEY `declined` (`declined`);

ALTER TABLE `Wo_AudioCalls`
  ADD PRIMARY KEY (`id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `call_id` (`call_id`),
  ADD KEY `called` (`called`),
  ADD KEY `declined` (`declined`);

ALTER TABLE `wo_backup_codes`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Backup_Codes`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_bad_login`
  ADD PRIMARY KEY (`id`),
  ADD KEY `ip` (`ip`);

ALTER TABLE `Wo_Bad_Login`
  ADD PRIMARY KEY (`id`),
  ADD KEY `ip` (`ip`);

ALTER TABLE `wo_banned_ip`
  ADD PRIMARY KEY (`id`),
  ADD KEY `ip_address` (`ip_address`);

ALTER TABLE `Wo_Banned_Ip`
  ADD PRIMARY KEY (`id`),
  ADD KEY `ip_address` (`ip_address`);

ALTER TABLE `Wo_Blocks`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blocker` (`blocker`),
  ADD KEY `blocked` (`blocked`);

ALTER TABLE `wo_blocks`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blocker` (`blocker`),
  ADD KEY `blocked` (`blocked`);

ALTER TABLE `Wo_Blog`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user` (`user`),
  ADD KEY `title` (`title`),
  ADD KEY `category` (`category`),
  ADD KEY `active` (`active`);

ALTER TABLE `wo_blog`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user` (`user`),
  ADD KEY `title` (`title`),
  ADD KEY `category` (`category`),
  ADD KEY `active` (`active`);

ALTER TABLE `wo_blogcommentreplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comm_id` (`comm_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `order1` (`comm_id`,`id`),
  ADD KEY `order2` (`blog_id`,`id`),
  ADD KEY `order3` (`user_id`,`id`);

ALTER TABLE `Wo_BlogCommentReplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comm_id` (`comm_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `order1` (`comm_id`,`id`),
  ADD KEY `order2` (`blog_id`,`id`),
  ADD KEY `order3` (`user_id`,`id`);

ALTER TABLE `wo_blogcomments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_BlogComments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_BlogMovieDisLikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_comm_id` (`blog_comm_id`),
  ADD KEY `movie_comm_id` (`movie_comm_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_commreply_id` (`blog_commreply_id`),
  ADD KEY `movie_commreply_id` (`movie_commreply_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `movie_id` (`movie_id`);

ALTER TABLE `wo_blogmoviedislikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_comm_id` (`blog_comm_id`),
  ADD KEY `movie_comm_id` (`movie_comm_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_commreply_id` (`blog_commreply_id`),
  ADD KEY `movie_commreply_id` (`movie_commreply_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `movie_id` (`movie_id`);

ALTER TABLE `wo_blogmovielikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_id` (`blog_comm_id`),
  ADD KEY `movie_id` (`movie_comm_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_commreply_id` (`blog_commreply_id`),
  ADD KEY `movie_commreply_id` (`movie_commreply_id`),
  ADD KEY `blog_id_2` (`blog_id`),
  ADD KEY `movie_id_2` (`movie_id`);

ALTER TABLE `Wo_BlogMovieLikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `blog_id` (`blog_comm_id`),
  ADD KEY `movie_id` (`movie_comm_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_commreply_id` (`blog_commreply_id`),
  ADD KEY `movie_commreply_id` (`movie_commreply_id`),
  ADD KEY `blog_id_2` (`blog_id`),
  ADD KEY `movie_id_2` (`movie_id`);

ALTER TABLE `Wo_Blogs_Categories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `lang_key` (`lang_key`);

ALTER TABLE `wo_blogs_categories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `lang_key` (`lang_key`);

ALTER TABLE `wo_blog_reaction`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`);

ALTER TABLE `Wo_Blog_Reaction`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`);

ALTER TABLE `wo_calls`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_room_name` (`room_name`),
  ADD KEY `idx_from_id` (`from_id`),
  ADD KEY `idx_to_id` (`to_id`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_created_at` (`created_at`),
  ADD KEY `idx_calls_user` (`from_id`,`created_at` DESC),
  ADD KEY `idx_calls_to_user` (`to_id`,`created_at` DESC);

ALTER TABLE `wo_call_statistics`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_stats_user` (`user_id`),
  ADD KEY `idx_stats_call_type` (`call_type`),
  ADD KEY `idx_stats_created_at` (`created_at`),
  ADD KEY `idx_stats_call_id` (`call_id`),
  ADD KEY `idx_stats_group_call_id` (`group_call_id`);

ALTER TABLE `Wo_ChannelAdmins`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `channel_user` (`channel_id`,`user_id`),
  ADD KEY `channel_id` (`channel_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_ChannelComments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `created_at` (`created_at`),
  ADD KEY `reply_to_id` (`reply_to_id`);

ALTER TABLE `Wo_ChannelPosts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `channel_id` (`channel_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `created_at` (`created_at`),
  ADD KEY `is_pinned` (`is_pinned`),
  ADD KEY `channel_created` (`channel_id`,`created_at`);

ALTER TABLE `Wo_Channels`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `channel_username` (`channel_username`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `created_at` (`created_at`),
  ADD KEY `subscribers_count` (`subscribers_count`),
  ADD KEY `qr_code` (`qr_code`);

ALTER TABLE `Wo_Channels` ADD FULLTEXT KEY `search_channel` (`channel_name`,`channel_description`);

ALTER TABLE `Wo_ChannelSettings`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `channel_id` (`channel_id`);

ALTER TABLE `Wo_ChannelSubscribers`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `channel_user` (`channel_id`,`user_id`),
  ADD KEY `channel_id` (`channel_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `subscribed_at` (`subscribed_at`),
  ADD KEY `channel_muted` (`channel_id`,`user_id`,`is_muted`),
  ADD KEY `idx_channel_muted` (`channel_id`,`user_id`,`is_muted`);

ALTER TABLE `wo_codes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `app_id` (`app_id`);

ALTER TABLE `Wo_Codes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `app_id` (`app_id`);

ALTER TABLE `Wo_Colored_Posts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `color_1` (`color_1`),
  ADD KEY `color_2` (`color_2`);

ALTER TABLE `wo_colored_posts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `color_1` (`color_1`),
  ADD KEY `color_2` (`color_2`);

ALTER TABLE `wo_commentlikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `post_id` (`post_id`);

ALTER TABLE `Wo_CommentLikes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `post_id` (`post_id`);

ALTER TABLE `wo_comments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`page_id`,`id`),
  ADD KEY `order3` (`post_id`,`id`),
  ADD KEY `order4` (`user_id`,`id`),
  ADD KEY `order5` (`post_id`,`id`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_Comments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`page_id`,`id`),
  ADD KEY `order3` (`post_id`,`id`),
  ADD KEY `order4` (`user_id`,`id`),
  ADD KEY `order5` (`post_id`,`id`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_CommentWonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_commentwonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_comment_replies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `user_id` (`user_id`,`page_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_Comment_Replies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `user_id` (`user_id`,`page_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_comment_replies_likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Comment_Replies_Likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Comment_Replies_Wonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `reply_id` (`reply_id`,`user_id`);

ALTER TABLE `wo_comment_replies_wonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `reply_id` (`reply_id`,`user_id`);

ALTER TABLE `Wo_Config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_name` (`name`);

ALTER TABLE `wo_config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_name` (`name`);

ALTER TABLE `wo_custompages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_type` (`page_type`);

ALTER TABLE `Wo_CustomPages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_type` (`page_type`);

ALTER TABLE `wo_custom_fields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_Custom_Fields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_Egoing`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_egoing`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Einterested`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_einterested`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Einvited`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `inviter_id` (`invited_id`),
  ADD KEY `inviter_id_2` (`inviter_id`);

ALTER TABLE `wo_einvited`
  ADD PRIMARY KEY (`id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `inviter_id` (`invited_id`),
  ADD KEY `inviter_id_2` (`inviter_id`);

ALTER TABLE `wo_emails`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `email_to` (`email_to`);

ALTER TABLE `Wo_Emails`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `email_to` (`email_to`);

ALTER TABLE `Wo_Events`
  ADD PRIMARY KEY (`id`),
  ADD KEY `poster_id` (`poster_id`),
  ADD KEY `name` (`name`),
  ADD KEY `start_date` (`start_date`),
  ADD KEY `start_time` (`start_time`),
  ADD KEY `end_time` (`end_time`),
  ADD KEY `end_date` (`end_date`),
  ADD KEY `order1` (`poster_id`,`id`),
  ADD KEY `order2` (`id`);

ALTER TABLE `wo_events`
  ADD PRIMARY KEY (`id`),
  ADD KEY `poster_id` (`poster_id`),
  ADD KEY `name` (`name`),
  ADD KEY `start_date` (`start_date`),
  ADD KEY `start_time` (`start_time`),
  ADD KEY `end_time` (`end_time`),
  ADD KEY `end_date` (`end_date`),
  ADD KEY `order1` (`poster_id`,`id`),
  ADD KEY `order2` (`id`);

ALTER TABLE `Wo_Family`
  ADD PRIMARY KEY (`id`),
  ADD KEY `member_id` (`member_id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `requesting` (`requesting`);

ALTER TABLE `wo_family`
  ADD PRIMARY KEY (`id`),
  ADD KEY `member_id` (`member_id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `requesting` (`requesting`);

ALTER TABLE `wo_followers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `following_id` (`following_id`),
  ADD KEY `follower_id` (`follower_id`),
  ADD KEY `active` (`active`),
  ADD KEY `order1` (`following_id`,`id`),
  ADD KEY `order2` (`follower_id`,`id`),
  ADD KEY `is_typing` (`is_typing`),
  ADD KEY `notify` (`notify`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_Followers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `following_id` (`following_id`),
  ADD KEY `follower_id` (`follower_id`),
  ADD KEY `active` (`active`),
  ADD KEY `order1` (`following_id`,`id`),
  ADD KEY `order2` (`follower_id`,`id`),
  ADD KEY `is_typing` (`is_typing`),
  ADD KEY `notify` (`notify`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_Forums`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `description` (`description`(255)),
  ADD KEY `posts` (`posts`);

ALTER TABLE `wo_forums`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `description` (`description`(255)),
  ADD KEY `posts` (`posts`);

ALTER TABLE `wo_forumthreadreplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `thread_id` (`thread_id`),
  ADD KEY `forum_id` (`forum_id`),
  ADD KEY `poster_id` (`poster_id`),
  ADD KEY `post_subject` (`post_subject`(255)),
  ADD KEY `post_quoted` (`post_quoted`),
  ADD KEY `posted_time` (`posted_time`);

ALTER TABLE `Wo_ForumThreadReplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `thread_id` (`thread_id`),
  ADD KEY `forum_id` (`forum_id`),
  ADD KEY `poster_id` (`poster_id`),
  ADD KEY `post_subject` (`post_subject`(255)),
  ADD KEY `post_quoted` (`post_quoted`),
  ADD KEY `posted_time` (`posted_time`);

ALTER TABLE `Wo_Forum_Sections`
  ADD PRIMARY KEY (`id`),
  ADD KEY `section_name` (`section_name`),
  ADD KEY `description` (`description`(255));

ALTER TABLE `wo_forum_sections`
  ADD PRIMARY KEY (`id`),
  ADD KEY `section_name` (`section_name`),
  ADD KEY `description` (`description`(255));

ALTER TABLE `wo_forum_threads`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user` (`user`),
  ADD KEY `views` (`views`),
  ADD KEY `posted` (`posted`),
  ADD KEY `headline` (`headline`(255)),
  ADD KEY `forum` (`forum`);

ALTER TABLE `Wo_Forum_Threads`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user` (`user`),
  ADD KEY `views` (`views`),
  ADD KEY `posted` (`posted`),
  ADD KEY `headline` (`headline`(255)),
  ADD KEY `forum` (`forum`);

ALTER TABLE `wo_funding`
  ADD PRIMARY KEY (`id`),
  ADD KEY `hashed_id` (`hashed_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Funding`
  ADD PRIMARY KEY (`id`),
  ADD KEY `hashed_id` (`hashed_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Funding_Raise`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `funding_id` (`funding_id`);

ALTER TABLE `wo_funding_raise`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `funding_id` (`funding_id`);

ALTER TABLE `Wo_Games`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`);

ALTER TABLE `wo_games`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_Games_Players`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`game_id`,`active`);

ALTER TABLE `wo_games_players`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`game_id`,`active`);

ALTER TABLE `Wo_Gender`
  ADD PRIMARY KEY (`id`),
  ADD KEY `gender_id` (`gender_id`);

ALTER TABLE `wo_gender`
  ADD PRIMARY KEY (`id`),
  ADD KEY `gender_id` (`gender_id`);

ALTER TABLE `wo_gifts`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Gifts`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_GroupAdmins`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `members` (`members`);

ALTER TABLE `wo_groupadmins`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `members` (`members`);

ALTER TABLE `Wo_GroupChat`
  ADD PRIMARY KEY (`group_id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `type` (`type`),
  ADD KEY `destruct_at` (`destruct_at`),
  ADD KEY `is_private` (`is_private`),
  ADD KEY `idx_username` (`username`),
  ADD KEY `idx_type` (`type`);

ALTER TABLE `wo_groupchat`
  ADD PRIMARY KEY (`group_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `type` (`type`),
  ADD KEY `destruct_at` (`destruct_at`);

ALTER TABLE `wo_groupchatusers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `active` (`active`);

ALTER TABLE `Wo_GroupChatUsers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `active` (`active`),
  ADD KEY `role` (`role`);

ALTER TABLE `Wo_GroupJoinRequests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `status` (`status`);

ALTER TABLE `Wo_Groups`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `privacy` (`privacy`),
  ADD KEY `time` (`time`),
  ADD KEY `active` (`active`),
  ADD KEY `group_title` (`group_title`),
  ADD KEY `group_name` (`group_name`),
  ADD KEY `registered` (`registered`),
  ADD KEY `idx_qr_code` (`qr_code`);

ALTER TABLE `wo_groups`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `privacy` (`privacy`),
  ADD KEY `time` (`time`),
  ADD KEY `active` (`active`),
  ADD KEY `group_title` (`group_title`),
  ADD KEY `group_name` (`group_name`),
  ADD KEY `registered` (`registered`);

ALTER TABLE `Wo_Groups_Categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_groups_categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_GroupTopics`
  ADD PRIMARY KEY (`topic_id`),
  ADD KEY `idx_group_id` (`group_id`),
  ADD KEY `idx_last_message` (`last_message_time`),
  ADD KEY `idx_created_by` (`created_by`);

ALTER TABLE `wo_group_calls`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_group_room_name` (`room_name`),
  ADD KEY `idx_group_id` (`group_id`),
  ADD KEY `idx_initiated_by` (`initiated_by`),
  ADD KEY `idx_gc_status` (`status`),
  ADD KEY `idx_gc_created_at` (`created_at`),
  ADD KEY `idx_group_calls_group` (`group_id`,`created_at` DESC),
  ADD KEY `idx_group_calls_initiator` (`initiated_by`,`created_at` DESC);

ALTER TABLE `wo_group_call_participants`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_participant` (`group_call_id`,`user_id`),
  ADD KEY `idx_gcp_group_call` (`group_call_id`),
  ADD KEY `idx_gcp_user` (`user_id`);

ALTER TABLE `Wo_Group_Members`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id` (`user_id`,`group_id`,`active`);

ALTER TABLE `wo_group_members`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id` (`user_id`,`group_id`,`active`);

ALTER TABLE `wo_hashtags`
  ADD PRIMARY KEY (`id`),
  ADD KEY `last_trend_time` (`last_trend_time`),
  ADD KEY `trend_use_num` (`trend_use_num`),
  ADD KEY `tag` (`tag`),
  ADD KEY `expire` (`expire`),
  ADD KEY `hash` (`hash`);

ALTER TABLE `Wo_Hashtags`
  ADD PRIMARY KEY (`id`),
  ADD KEY `last_trend_time` (`last_trend_time`),
  ADD KEY `trend_use_num` (`trend_use_num`),
  ADD KEY `tag` (`tag`),
  ADD KEY `expire` (`expire`),
  ADD KEY `hash` (`hash`);

ALTER TABLE `Wo_HiddenPosts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_hiddenposts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_HTML_Emails`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_ice_candidates`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_ice_room_name` (`room_name`),
  ADD KEY `idx_ice_created_at` (`created_at`);

ALTER TABLE `wo_invitation_links`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`(255)),
  ADD KEY `invited_id` (`invited_id`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Invitation_Links`
  ADD PRIMARY KEY (`id`),
  ADD KEY `code` (`code`(255)),
  ADD KEY `invited_id` (`invited_id`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_job`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `title` (`title`),
  ADD KEY `category` (`category`),
  ADD KEY `lng` (`lng`),
  ADD KEY `lat` (`lat`),
  ADD KEY `status` (`status`),
  ADD KEY `job_type` (`job_type`),
  ADD KEY `minimum` (`minimum`),
  ADD KEY `maximum` (`maximum`);

ALTER TABLE `Wo_Job`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `title` (`title`),
  ADD KEY `category` (`category`),
  ADD KEY `lng` (`lng`),
  ADD KEY `lat` (`lat`),
  ADD KEY `status` (`status`),
  ADD KEY `job_type` (`job_type`),
  ADD KEY `minimum` (`minimum`),
  ADD KEY `maximum` (`maximum`);

ALTER TABLE `Wo_Job_Apply`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `job_id` (`job_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `user_name` (`user_name`);

ALTER TABLE `wo_job_apply`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `job_id` (`job_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `user_name` (`user_name`);

ALTER TABLE `Wo_Job_Categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_job_categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_LangIso`
  ADD PRIMARY KEY (`id`),
  ADD KEY `lang_name` (`lang_name`),
  ADD KEY `iso` (`iso`),
  ADD KEY `image` (`image`);

ALTER TABLE `Wo_Langs`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_name` (`lang_key`),
  ADD KEY `type` (`type`);

ALTER TABLE `wo_langs`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_name` (`lang_key`),
  ADD KEY `type` (`type`);

ALTER TABLE `wo_likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Live_Sub_Users`
  ADD PRIMARY KEY (`id`),
  ADD KEY `time` (`time`),
  ADD KEY `is_watching` (`is_watching`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_live_sub_users`
  ADD PRIMARY KEY (`id`),
  ADD KEY `time` (`time`),
  ADD KEY `is_watching` (`is_watching`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_Manage_Pro`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_manage_pro`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_MessageCommentReactions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_reaction` (`comment_id`,`user_id`,`reaction`),
  ADD KEY `idx_comment_id` (`comment_id`),
  ADD KEY `idx_user_id` (`user_id`);

ALTER TABLE `Wo_MessageComments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_message_id` (`message_id`),
  ADD KEY `idx_user_id` (`user_id`),
  ADD KEY `idx_time` (`time`),
  ADD KEY `idx_reply_to` (`reply_to_comment_id`);

ALTER TABLE `Wo_Messages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `time` (`time`),
  ADD KEY `deleted_two` (`deleted_two`),
  ADD KEY `deleted_one` (`deleted_one`),
  ADD KEY `sent_push` (`sent_push`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `order1` (`from_id`,`id`),
  ADD KEY `order2` (`group_id`,`id`),
  ADD KEY `order3` (`to_id`,`id`),
  ADD KEY `order7` (`seen`,`id`),
  ADD KEY `order8` (`time`,`id`),
  ADD KEY `order4` (`from_id`,`id`),
  ADD KEY `order5` (`group_id`,`id`),
  ADD KEY `order6` (`to_id`,`id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `broadcast_id` (`broadcast_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `notification_id` (`notification_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `page_id_2` (`page_id`),
  ADD KEY `notification_id_2` (`notification_id`),
  ADD KEY `product_id_2` (`product_id`),
  ADD KEY `story_id_2` (`story_id`),
  ADD KEY `reply_id_2` (`reply_id`),
  ADD KEY `broadcast_id_2` (`broadcast_id`),
  ADD KEY `forward` (`forward`),
  ADD KEY `listening` (`listening`),
  ADD KEY `remove_at` (`remove_at`);

ALTER TABLE `wo_messages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `time` (`time`),
  ADD KEY `deleted_two` (`deleted_two`),
  ADD KEY `deleted_one` (`deleted_one`),
  ADD KEY `sent_push` (`sent_push`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `order1` (`from_id`,`id`),
  ADD KEY `order2` (`group_id`,`id`),
  ADD KEY `order3` (`to_id`,`id`),
  ADD KEY `order7` (`seen`,`id`),
  ADD KEY `order8` (`time`,`id`),
  ADD KEY `order4` (`from_id`,`id`),
  ADD KEY `order5` (`group_id`,`id`),
  ADD KEY `order6` (`to_id`,`id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `broadcast_id` (`broadcast_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `notification_id` (`notification_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `page_id_2` (`page_id`),
  ADD KEY `notification_id_2` (`notification_id`),
  ADD KEY `product_id_2` (`product_id`),
  ADD KEY `story_id_2` (`story_id`),
  ADD KEY `reply_id_2` (`reply_id`),
  ADD KEY `broadcast_id_2` (`broadcast_id`),
  ADD KEY `forward` (`forward`),
  ADD KEY `listening` (`listening`),
  ADD KEY `remove_at` (`remove_at`);

ALTER TABLE `Wo_MessageViews`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_message_id` (`message_id`),
  ADD KEY `idx_user_id` (`user_id`),
  ADD KEY `idx_time` (`time`);

ALTER TABLE `Wo_MonetizationSubscription`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_monetizationsubscription`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_MovieCommentReplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comm_id` (`comm_id`),
  ADD KEY `movie_id` (`movie_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_moviecommentreplies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `comm_id` (`comm_id`),
  ADD KEY `movie_id` (`movie_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_MovieComments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `movie_id` (`movie_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_moviecomments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `movie_id` (`movie_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_movies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `genre` (`genre`),
  ADD KEY `country` (`country`),
  ADD KEY `release` (`release`);

ALTER TABLE `Wo_Movies`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`),
  ADD KEY `genre` (`genre`),
  ADD KEY `country` (`country`),
  ADD KEY `release` (`release`);

ALTER TABLE `Wo_Mute`
  ADD PRIMARY KEY (`id`),
  ADD KEY `chat_id` (`chat_id`),
  ADD KEY `message_id` (`message_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id_2` (`user_id`),
  ADD KEY `chat_id_2` (`chat_id`),
  ADD KEY `message_id_2` (`message_id`),
  ADD KEY `notify` (`notify`),
  ADD KEY `type` (`type`),
  ADD KEY `fav` (`fav`);

ALTER TABLE `Wo_Mute_Story`
  ADD PRIMARY KEY (`id`),
  ADD KEY `story_user_id` (`story_user_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `user_id_2` (`user_id`),
  ADD KEY `story_user_id_2` (`story_user_id`);

ALTER TABLE `Wo_Notifications`
  ADD PRIMARY KEY (`id`),
  ADD KEY `notifier_id` (`notifier_id`),
  ADD KEY `user_id` (`recipient_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `time` (`time`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`,`seen_pop`),
  ADD KEY `sent_push` (`sent_push`),
  ADD KEY `order1` (`seen`,`id`),
  ADD KEY `order2` (`notifier_id`,`id`),
  ADD KEY `order3` (`recipient_id`,`id`),
  ADD KEY `order4` (`post_id`,`id`),
  ADD KEY `order5` (`page_id`,`id`),
  ADD KEY `order6` (`group_id`,`id`),
  ADD KEY `order7` (`time`,`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `admin` (`admin`),
  ADD KEY `group_chat_id` (`group_chat_id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `thread_id` (`thread_id`);

ALTER TABLE `wo_notifications`
  ADD PRIMARY KEY (`id`),
  ADD KEY `notifier_id` (`notifier_id`),
  ADD KEY `user_id` (`recipient_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `time` (`time`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`,`seen_pop`),
  ADD KEY `sent_push` (`sent_push`),
  ADD KEY `order1` (`seen`,`id`),
  ADD KEY `order2` (`notifier_id`,`id`),
  ADD KEY `order3` (`recipient_id`,`id`),
  ADD KEY `order4` (`post_id`,`id`),
  ADD KEY `order5` (`page_id`,`id`),
  ADD KEY `order6` (`group_id`,`id`),
  ADD KEY `order7` (`time`,`id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `reply_id` (`reply_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `admin` (`admin`),
  ADD KEY `group_chat_id` (`group_chat_id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `thread_id` (`thread_id`);

ALTER TABLE `Wo_Offers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `spend` (`spend`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_offers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `spend` (`spend`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_pageadmins`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `Wo_PageAdmins`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `wo_pagerating`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `Wo_PageRating`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `Wo_Pages`
  ADD PRIMARY KEY (`page_id`),
  ADD KEY `registered` (`registered`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_category` (`page_category`),
  ADD KEY `active` (`active`),
  ADD KEY `verified` (`verified`),
  ADD KEY `boosted` (`boosted`),
  ADD KEY `time` (`time`),
  ADD KEY `page_name` (`page_name`),
  ADD KEY `page_title` (`page_title`),
  ADD KEY `sub_category` (`sub_category`);

ALTER TABLE `wo_pages`
  ADD PRIMARY KEY (`page_id`),
  ADD KEY `registered` (`registered`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_category` (`page_category`),
  ADD KEY `active` (`active`),
  ADD KEY `verified` (`verified`),
  ADD KEY `boosted` (`boosted`),
  ADD KEY `time` (`time`),
  ADD KEY `page_name` (`page_name`),
  ADD KEY `page_title` (`page_title`),
  ADD KEY `sub_category` (`sub_category`);

ALTER TABLE `Wo_Pages_Categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_pages_categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_pages_invites`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_id` (`page_id`,`inviter_id`,`invited_id`);

ALTER TABLE `Wo_Pages_Invites`
  ADD PRIMARY KEY (`id`),
  ADD KEY `page_id` (`page_id`,`inviter_id`,`invited_id`);

ALTER TABLE `Wo_Pages_Likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `wo_pages_likes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `active` (`active`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `Wo_PatreonSubscribers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `subscriber_id` (`subscriber_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_Payments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `date` (`date`);

ALTER TABLE `wo_payments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `date` (`date`);

ALTER TABLE `wo_payment_transactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `userid` (`userid`),
  ADD KEY `kind` (`kind`),
  ADD KEY `transaction_dt` (`transaction_dt`);

ALTER TABLE `Wo_Payment_Transactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `userid` (`userid`),
  ADD KEY `kind` (`kind`),
  ADD KEY `transaction_dt` (`transaction_dt`);

ALTER TABLE `Wo_PendingPayments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `payment_data` (`payment_data`),
  ADD KEY `method_name` (`method_name`),
  ADD KEY `time` (`time`);

ALTER TABLE `Wo_PinnedPosts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `active` (`active`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`);

ALTER TABLE `wo_pinnedposts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `active` (`active`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`);

ALTER TABLE `Wo_Pokes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `received_user_id` (`received_user_id`),
  ADD KEY `user_id` (`send_user_id`);

ALTER TABLE `wo_pokes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `received_user_id` (`received_user_id`),
  ADD KEY `user_id` (`send_user_id`);

ALTER TABLE `Wo_Polls`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_posts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `recipient_id` (`recipient_id`),
  ADD KEY `postFile` (`postFile`),
  ADD KEY `postShare` (`postShare`),
  ADD KEY `postType` (`postType`),
  ADD KEY `postYoutube` (`postYoutube`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `registered` (`registered`),
  ADD KEY `time` (`time`),
  ADD KEY `boosted` (`boosted`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `poll_id` (`poll_id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `videoViews` (`videoViews`),
  ADD KEY `shared_from` (`shared_from`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`page_id`,`id`),
  ADD KEY `order3` (`group_id`,`id`),
  ADD KEY `order4` (`recipient_id`,`id`),
  ADD KEY `order5` (`event_id`,`id`),
  ADD KEY `order6` (`parent_id`,`id`),
  ADD KEY `multi_image` (`multi_image`),
  ADD KEY `album_name` (`album_name`),
  ADD KEY `postFacebook` (`postFacebook`),
  ADD KEY `postVimeo` (`postVimeo`),
  ADD KEY `postDailymotion` (`postDailymotion`),
  ADD KEY `postSoundCloud` (`postSoundCloud`),
  ADD KEY `postYoutube_2` (`postYoutube`),
  ADD KEY `fund_raise_id` (`fund_raise_id`),
  ADD KEY `fund_id` (`fund_id`),
  ADD KEY `offer_id` (`offer_id`),
  ADD KEY `live_time` (`live_time`),
  ADD KEY `live_ended` (`live_ended`),
  ADD KEY `active` (`active`),
  ADD KEY `job_id` (`job_id`),
  ADD KEY `page_event_id` (`page_event_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `color_id` (`color_id`),
  ADD KEY `thread_id` (`thread_id`),
  ADD KEY `forum_id` (`forum_id`),
  ADD KEY `processing` (`processing`);

ALTER TABLE `Wo_Posts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `recipient_id` (`recipient_id`),
  ADD KEY `postFile` (`postFile`),
  ADD KEY `postShare` (`postShare`),
  ADD KEY `postType` (`postType`),
  ADD KEY `postYoutube` (`postYoutube`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `registered` (`registered`),
  ADD KEY `time` (`time`),
  ADD KEY `boosted` (`boosted`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `poll_id` (`poll_id`),
  ADD KEY `event_id` (`event_id`),
  ADD KEY `videoViews` (`videoViews`),
  ADD KEY `shared_from` (`shared_from`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`page_id`,`id`),
  ADD KEY `order3` (`group_id`,`id`),
  ADD KEY `order4` (`recipient_id`,`id`),
  ADD KEY `order5` (`event_id`,`id`),
  ADD KEY `order6` (`parent_id`,`id`),
  ADD KEY `multi_image` (`multi_image`),
  ADD KEY `album_name` (`album_name`),
  ADD KEY `postFacebook` (`postFacebook`),
  ADD KEY `postVimeo` (`postVimeo`),
  ADD KEY `postDailymotion` (`postDailymotion`),
  ADD KEY `postSoundCloud` (`postSoundCloud`),
  ADD KEY `postYoutube_2` (`postYoutube`),
  ADD KEY `fund_raise_id` (`fund_raise_id`),
  ADD KEY `fund_id` (`fund_id`),
  ADD KEY `offer_id` (`offer_id`),
  ADD KEY `live_time` (`live_time`),
  ADD KEY `live_ended` (`live_ended`),
  ADD KEY `active` (`active`),
  ADD KEY `job_id` (`job_id`),
  ADD KEY `page_event_id` (`page_event_id`),
  ADD KEY `blog_id` (`blog_id`),
  ADD KEY `color_id` (`color_id`),
  ADD KEY `thread_id` (`thread_id`),
  ADD KEY `forum_id` (`forum_id`),
  ADD KEY `processing` (`processing`);

ALTER TABLE `wo_productreview`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `star` (`star`);

ALTER TABLE `Wo_ProductReview`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `star` (`star`);

ALTER TABLE `wo_products`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `category` (`category`),
  ADD KEY `price` (`price`),
  ADD KEY `status` (`status`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `active` (`active`),
  ADD KEY `units` (`units`);

ALTER TABLE `Wo_Products`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `category` (`category`),
  ADD KEY `price` (`price`),
  ADD KEY `status` (`status`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `active` (`active`),
  ADD KEY `units` (`units`);

ALTER TABLE `wo_products_categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Products_Categories`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Products_Media`
  ADD PRIMARY KEY (`id`),
  ADD KEY `product_id` (`product_id`);

ALTER TABLE `wo_products_media`
  ADD PRIMARY KEY (`id`),
  ADD KEY `product_id` (`product_id`);

ALTER TABLE `Wo_ProfileFields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `registration_page` (`registration_page`),
  ADD KEY `active` (`active`),
  ADD KEY `profile_page` (`profile_page`);

ALTER TABLE `wo_profilefields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `registration_page` (`registration_page`),
  ADD KEY `active` (`active`),
  ADD KEY `profile_page` (`profile_page`);

ALTER TABLE `Wo_Purchases`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `timestamp` (`timestamp`),
  ADD KEY `time` (`time`),
  ADD KEY `owner_id` (`owner_id`),
  ADD KEY `final_price` (`final_price`),
  ADD KEY `order_hash_id` (`order_hash_id`),
  ADD KEY `data` (`data`(768));

ALTER TABLE `wo_purchases`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `timestamp` (`timestamp`),
  ADD KEY `time` (`time`),
  ADD KEY `owner_id` (`owner_id`),
  ADD KEY `final_price` (`final_price`),
  ADD KEY `order_hash_id` (`order_hash_id`),
  ADD KEY `data` (`data`(768));

ALTER TABLE `Wo_Reactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `idx_reaction` (`reaction`),
  ADD KEY `message_id` (`message_id`),
  ADD KEY `message_id_2` (`message_id`),
  ADD KEY `replay_id` (`replay_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `comment_id_2` (`comment_id`),
  ADD KEY `replay_id_2` (`replay_id`),
  ADD KEY `message_id_3` (`message_id`),
  ADD KEY `story_id_2` (`story_id`);

ALTER TABLE `wo_reactions`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `idx_reaction` (`reaction`),
  ADD KEY `message_id` (`message_id`),
  ADD KEY `message_id_2` (`message_id`),
  ADD KEY `replay_id` (`replay_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `comment_id` (`comment_id`),
  ADD KEY `comment_id_2` (`comment_id`),
  ADD KEY `replay_id_2` (`replay_id`),
  ADD KEY `message_id_3` (`message_id`),
  ADD KEY `story_id_2` (`story_id`);

ALTER TABLE `wo_reactions_types`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Reactions_Types`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_recentsearches`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`search_id`),
  ADD KEY `search_type` (`search_type`);

ALTER TABLE `Wo_RecentSearches`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`,`search_id`),
  ADD KEY `search_type` (`search_type`);

ALTER TABLE `wo_refund`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `pro_type` (`pro_type`),
  ADD KEY `status` (`status`);

ALTER TABLE `Wo_Refund`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `pro_type` (`pro_type`),
  ADD KEY `status` (`status`);

ALTER TABLE `Wo_Relationship`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `relationship` (`relationship`),
  ADD KEY `active` (`active`),
  ADD KEY `to_id` (`to_id`);

ALTER TABLE `wo_relationship`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `relationship` (`relationship`),
  ADD KEY `active` (`active`),
  ADD KEY `to_id` (`to_id`);

ALTER TABLE `Wo_Reports`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `profile_id` (`profile_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `comment_id` (`comment_id`);

ALTER TABLE `wo_reports`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `seen` (`seen`),
  ADD KEY `profile_id` (`profile_id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `comment_id` (`comment_id`);

ALTER TABLE `Wo_SavedPosts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `wo_savedposts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_ScheduledPosts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `group_id` (`group_id`),
  ADD KEY `channel_id` (`channel_id`),
  ADD KEY `status` (`status`),
  ADD KEY `scheduled_time` (`scheduled_time`);

ALTER TABLE `Wo_Stickers`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_stickers`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_storycomments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_StoryComments`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_storyreactions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_story_user` (`story_id`,`user_id`),
  ADD KEY `idx_story_id` (`story_id`),
  ADD KEY `idx_user_id` (`user_id`),
  ADD KEY `idx_reaction` (`reaction`);

ALTER TABLE `Wo_StoryReactions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_story_user` (`story_id`,`user_id`),
  ADD KEY `idx_story_id` (`story_id`),
  ADD KEY `idx_user_id` (`user_id`),
  ADD KEY `idx_reaction` (`reaction`);

ALTER TABLE `wo_story_seen`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `story_id` (`story_id`);

ALTER TABLE `Wo_Story_Seen`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `story_id` (`story_id`),
  ADD KEY `idx_story_user` (`story_id`,`user_id`);

ALTER TABLE `Wo_Subgroups`
  ADD PRIMARY KEY (`id`),
  ADD KEY `parent_group_id` (`parent_group_id`),
  ADD KEY `is_closed` (`is_closed`);

ALTER TABLE `wo_sub_categories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `category_id` (`category_id`),
  ADD KEY `lang_key` (`lang_key`),
  ADD KEY `type` (`type`);

ALTER TABLE `Wo_Sub_Categories`
  ADD PRIMARY KEY (`id`),
  ADD KEY `category_id` (`category_id`),
  ADD KEY `lang_key` (`lang_key`),
  ADD KEY `type` (`type`);

ALTER TABLE `Wo_Terms`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `wo_terms`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_Tokens`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `user_id_2` (`user_id`),
  ADD KEY `app_id` (`app_id`),
  ADD KEY `token` (`token`);

ALTER TABLE `wo_tokens`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `user_id_2` (`user_id`),
  ADD KEY `app_id` (`app_id`),
  ADD KEY `token` (`token`);

ALTER TABLE `Wo_UploadedMedia`
  ADD PRIMARY KEY (`id`),
  ADD KEY `filename` (`filename`),
  ADD KEY `time` (`time`),
  ADD KEY `filename_2` (`filename`,`storage`);

ALTER TABLE `wo_useraddress`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_UserAddress`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_UserAds`
  ADD PRIMARY KEY (`id`),
  ADD KEY `appears` (`appears`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `location` (`location`(255)),
  ADD KEY `gender` (`gender`),
  ADD KEY `status` (`status`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `wo_userads_data`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `ad_id` (`ad_id`);

ALTER TABLE `Wo_UserAds_Data`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `ad_id` (`ad_id`);

ALTER TABLE `Wo_UserCard`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `units` (`units`);

ALTER TABLE `wo_usercard`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `units` (`units`);

ALTER TABLE `Wo_UserCertification`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_UserCloudBackupSettings`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `user_id` (`user_id`),
  ADD KEY `idx_backup_provider` (`backup_provider`),
  ADD KEY `idx_backup_enabled` (`backup_enabled`);

ALTER TABLE `Wo_UserExperience`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `time` (`time`);

ALTER TABLE `wo_userfields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_UserFields`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `Wo_UserLanguages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `lang_key` (`lang_key`);

ALTER TABLE `Wo_UserMediaSettings`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `user_id` (`user_id`),
  ADD KEY `backup_enabled` (`backup_enabled`);

ALTER TABLE `wo_usermonetization`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_UserMonetization`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `Wo_UserOpenTo`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `job_title` (`job_title`),
  ADD KEY `job_location` (`job_location`),
  ADD KEY `workplaces` (`workplaces`),
  ADD KEY `job_type` (`job_type`),
  ADD KEY `type` (`type`),
  ADD KEY `time` (`time`),
  ADD KEY `services` (`services`(768)),
  ADD KEY `description` (`description`(768));

ALTER TABLE `wo_userorders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_owner_id` (`product_owner_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `final_price` (`final_price`),
  ADD KEY `status` (`status`),
  ADD KEY `time` (`time`),
  ADD KEY `hash_id` (`hash_id`),
  ADD KEY `units` (`units`),
  ADD KEY `address_id` (`address_id`);

ALTER TABLE `Wo_UserOrders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `product_owner_id` (`product_owner_id`),
  ADD KEY `product_id` (`product_id`),
  ADD KEY `final_price` (`final_price`),
  ADD KEY `status` (`status`),
  ADD KEY `time` (`time`),
  ADD KEY `hash_id` (`hash_id`),
  ADD KEY `units` (`units`),
  ADD KEY `address_id` (`address_id`);

ALTER TABLE `Wo_UserProjects`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `name` (`name`);

ALTER TABLE `Wo_UserRatings`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_rating` (`rater_id`,`rated_user_id`) COMMENT 'Один користувач може поставити лише одну оцінку',
  ADD KEY `idx_rated_user` (`rated_user_id`),
  ADD KEY `idx_rating_type` (`rating_type`),
  ADD KEY `idx_created_at` (`created_at`);

ALTER TABLE `Wo_Users`
  ADD PRIMARY KEY (`user_id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `active` (`active`),
  ADD KEY `admin` (`admin`),
  ADD KEY `src` (`src`),
  ADD KEY `gender` (`gender`),
  ADD KEY `avatar` (`avatar`),
  ADD KEY `first_name` (`first_name`),
  ADD KEY `last_name` (`last_name`),
  ADD KEY `registered` (`registered`),
  ADD KEY `joined` (`joined`),
  ADD KEY `phone_number` (`phone_number`) USING BTREE,
  ADD KEY `referrer` (`referrer`),
  ADD KEY `wallet` (`wallet`),
  ADD KEY `friend_privacy` (`friend_privacy`),
  ADD KEY `lat` (`lat`),
  ADD KEY `lng` (`lng`),
  ADD KEY `order1` (`username`,`user_id`),
  ADD KEY `order2` (`email`,`user_id`),
  ADD KEY `order3` (`lastseen`,`user_id`),
  ADD KEY `order4` (`active`,`user_id`),
  ADD KEY `last_data_update` (`last_data_update`),
  ADD KEY `points` (`points`),
  ADD KEY `paystack_ref` (`paystack_ref`),
  ADD KEY `relationship_id` (`relationship_id`),
  ADD KEY `post_privacy` (`post_privacy`),
  ADD KEY `email_code` (`email_code`),
  ADD KEY `password` (`password`),
  ADD KEY `status` (`status`),
  ADD KEY `type` (`type`),
  ADD KEY `is_pro` (`is_pro`),
  ADD KEY `ref_user_id` (`ref_user_id`),
  ADD KEY `currently_working` (`currently_working`),
  ADD KEY `banned` (`banned`),
  ADD KEY `two_factor_hash` (`two_factor_hash`),
  ADD KEY `pro_remainder` (`pro_remainder`),
  ADD KEY `converted_points` (`converted_points`),
  ADD KEY `idx_rating_score` (`rating_score`),
  ADD KEY `idx_trust_level` (`trust_level`);

ALTER TABLE `wo_userschat`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `conversation_user_id` (`conversation_user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`user_id`,`id`),
  ADD KEY `order3` (`conversation_user_id`,`id`),
  ADD KEY `order4` (`conversation_user_id`,`id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `color` (`color`);

ALTER TABLE `Wo_UsersChat`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `conversation_user_id` (`conversation_user_id`),
  ADD KEY `time` (`time`),
  ADD KEY `order1` (`user_id`,`id`),
  ADD KEY `order2` (`user_id`,`id`),
  ADD KEY `order3` (`conversation_user_id`,`id`),
  ADD KEY `order4` (`conversation_user_id`,`id`),
  ADD KEY `page_id` (`page_id`),
  ADD KEY `color` (`color`);

ALTER TABLE `Wo_UserSkills`
  ADD PRIMARY KEY (`id`),
  ADD KEY `name` (`name`);

ALTER TABLE `Wo_UserStory`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `expires` (`expire`),
  ADD KEY `Wo_UserStory_Wo_UserAds_id_fk` (`ad_id`),
  ADD KEY `idx_user_expire` (`user_id`,`expire`),
  ADD KEY `idx_posted` (`posted`),
  ADD KEY `idx_comment_count` (`comment_count`),
  ADD KEY `idx_reaction_count` (`reaction_count`),
  ADD KEY `idx_page_id` (`page_id`);

ALTER TABLE `Wo_UserStoryMedia`
  ADD PRIMARY KEY (`id`),
  ADD KEY `expire` (`expire`),
  ADD KEY `story_id` (`story_id`);

ALTER TABLE `wo_userstorymedia`
  ADD PRIMARY KEY (`id`),
  ADD KEY `expire` (`expire`),
  ADD KEY `story_id` (`story_id`);

ALTER TABLE `Wo_UserTiers`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `chat` (`chat`),
  ADD KEY `live_stream` (`live_stream`);

ALTER TABLE `wo_user_gifts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from` (`from`),
  ADD KEY `to` (`to`),
  ADD KEY `gift_id` (`gift_id`);

ALTER TABLE `Wo_User_Gifts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `from` (`from`),
  ADD KEY `to` (`to`),
  ADD KEY `gift_id` (`gift_id`);

ALTER TABLE `Wo_Verification_Requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `wo_verification_requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `page_id` (`page_id`);

ALTER TABLE `wo_videocalles`
  ADD PRIMARY KEY (`id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `called` (`called`),
  ADD KEY `declined` (`declined`);

ALTER TABLE `Wo_VideoCalles`
  ADD PRIMARY KEY (`id`),
  ADD KEY `to_id` (`to_id`),
  ADD KEY `from_id` (`from_id`),
  ADD KEY `called` (`called`),
  ADD KEY `declined` (`declined`);

ALTER TABLE `wo_votes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `option_id` (`option_id`);

ALTER TABLE `Wo_Votes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `option_id` (`option_id`);

ALTER TABLE `wo_wonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `type` (`type`);

ALTER TABLE `Wo_Wonders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `type` (`type`);

ALTER TABLE `bank_receipts`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `broadcast`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `broadcast_users`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `stickers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `sticker_packs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wondertage_settings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=37;

ALTER TABLE `Wo_Activities`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=49;

ALTER TABLE `wo_activities`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

ALTER TABLE `Wo_AdminInvitations`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_admininvitations`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Ads`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

ALTER TABLE `wo_ads`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

ALTER TABLE `Wo_Affiliates_Requests`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_affiliates_requests`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_agoravideocall`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_AgoraVideoCall`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

ALTER TABLE `wo_albums_media`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Albums_Media`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_announcement`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Announcement`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_announcement_views`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Announcement_Views`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Apps`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `wo_apps`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_AppsSessions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=330;

ALTER TABLE `wo_appssessions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=58;

ALTER TABLE `wo_apps_hash`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Apps_Hash`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_apps_permission`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Apps_Permission`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

ALTER TABLE `wo_audiocalls`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_AudioCalls`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_backup_codes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Backup_Codes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_bad_login`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

ALTER TABLE `Wo_Bad_Login`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=22;

ALTER TABLE `wo_banned_ip`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Banned_Ip`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Blocks`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_blocks`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Blog`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=14;

ALTER TABLE `wo_blog`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_blogcommentreplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_BlogCommentReplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_blogcomments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_BlogComments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_BlogMovieDisLikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_blogmoviedislikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_blogmovielikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_BlogMovieLikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Blogs_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `wo_blogs_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `wo_blog_reaction`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=43;

ALTER TABLE `Wo_Blog_Reaction`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=43;

ALTER TABLE `wo_calls`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=108;

ALTER TABLE `wo_call_statistics`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ChannelAdmins`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ChannelComments`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ChannelPosts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Channels`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ChannelSettings`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ChannelSubscribers`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_codes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Codes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=109;

ALTER TABLE `Wo_Colored_Posts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_colored_posts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_commentlikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_CommentLikes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_comments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Comments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `Wo_CommentWonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_commentwonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_comment_replies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Comment_Replies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_comment_replies_likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Comment_Replies_Likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Comment_Replies_Wonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_comment_replies_wonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Config`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=576;

ALTER TABLE `wo_config`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=577;

ALTER TABLE `wo_custompages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_CustomPages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

ALTER TABLE `wo_custom_fields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Custom_Fields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Egoing`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_egoing`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Einterested`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_einterested`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Einvited`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_einvited`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_emails`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Emails`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Events`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_events`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Family`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_family`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_followers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `Wo_Followers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=31;

ALTER TABLE `Wo_Forums`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_forums`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_forumthreadreplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ForumThreadReplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `Wo_Forum_Sections`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_forum_sections`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_forum_threads`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Forum_Threads`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_funding`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Funding`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Funding_Raise`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_funding_raise`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Games`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_games`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Games_Players`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_games_players`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Gender`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_gender`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_gifts`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Gifts`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `Wo_GroupAdmins`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_groupadmins`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_GroupChat`
  MODIFY `group_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=53;

ALTER TABLE `wo_groupchat`
  MODIFY `group_id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_groupchatusers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_GroupChatUsers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=59;

ALTER TABLE `Wo_GroupJoinRequests`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Groups`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_groups`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Groups_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `wo_groups_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `Wo_GroupTopics`
  MODIFY `topic_id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_group_calls`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_group_call_participants`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Group_Members`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_group_members`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_hashtags`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Hashtags`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=26;

ALTER TABLE `Wo_HiddenPosts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_hiddenposts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_HTML_Emails`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=10;

ALTER TABLE `wo_ice_candidates`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2155;

ALTER TABLE `wo_invitation_links`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Invitation_Links`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_job`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=16;

ALTER TABLE `Wo_Job`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=16;

ALTER TABLE `Wo_Job_Apply`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=14;

ALTER TABLE `wo_job_apply`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=14;

ALTER TABLE `Wo_Job_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=24;

ALTER TABLE `wo_job_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=24;

ALTER TABLE `Wo_LangIso`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=25;

ALTER TABLE `Wo_Langs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2568;

ALTER TABLE `wo_langs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2568;

ALTER TABLE `wo_likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=20;

ALTER TABLE `Wo_Live_Sub_Users`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_live_sub_users`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Manage_Pro`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `wo_manage_pro`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `Wo_MessageCommentReactions`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

ALTER TABLE `Wo_MessageComments`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

ALTER TABLE `Wo_Messages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=812;

ALTER TABLE `wo_messages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;

ALTER TABLE `Wo_MessageViews`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

ALTER TABLE `Wo_MonetizationSubscription`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_monetizationsubscription`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_MovieCommentReplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_moviecommentreplies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_MovieComments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_moviecomments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_movies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Movies`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Mute`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Mute_Story`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Notifications`
  MODIFY `id` int(255) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=82;

ALTER TABLE `wo_notifications`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

ALTER TABLE `Wo_Offers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_offers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_pageadmins`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_PageAdmins`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_pagerating`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_PageRating`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Pages`
  MODIFY `page_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `wo_pages`
  MODIFY `page_id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Pages_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `wo_pages_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `wo_pages_invites`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Pages_Invites`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Pages_Likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_pages_likes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_PatreonSubscribers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Payments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_payments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_payment_transactions`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

ALTER TABLE `Wo_Payment_Transactions`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

ALTER TABLE `Wo_PendingPayments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_PinnedPosts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `wo_pinnedposts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Pokes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_pokes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Polls`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_posts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

ALTER TABLE `Wo_Posts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=66;

ALTER TABLE `wo_productreview`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ProductReview`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_products`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Products`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_products_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

ALTER TABLE `Wo_Products_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

ALTER TABLE `Wo_Products_Media`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_products_media`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ProfileFields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_profilefields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Purchases`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_purchases`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Reactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=17;

ALTER TABLE `wo_reactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=21;

ALTER TABLE `wo_reactions_types`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

ALTER TABLE `Wo_Reactions_Types`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

ALTER TABLE `wo_recentsearches`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `Wo_RecentSearches`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `wo_refund`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Refund`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Relationship`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_relationship`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Reports`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_reports`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_SavedPosts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_savedposts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_ScheduledPosts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Stickers`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_stickers`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_storycomments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_StoryComments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `wo_storyreactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_StoryReactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `wo_story_seen`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `Wo_Story_Seen`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=10;

ALTER TABLE `Wo_Subgroups`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_sub_categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Sub_Categories`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Terms`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `wo_terms`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `Wo_Tokens`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=94;

ALTER TABLE `wo_tokens`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UploadedMedia`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_useraddress`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserAddress`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserAds`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_userads_data`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserAds_Data`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserCard`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_usercard`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserCertification`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserCloudBackupSettings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserExperience`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_userfields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=10;

ALTER TABLE `Wo_UserFields`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=653;

ALTER TABLE `Wo_UserLanguages`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=116;

ALTER TABLE `Wo_UserMediaSettings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_usermonetization`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserMonetization`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserOpenTo`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_userorders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserOrders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserProjects`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserRatings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=19;

ALTER TABLE `Wo_Users`
  MODIFY `user_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=659;

ALTER TABLE `wo_userschat`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `Wo_UsersChat`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=17;

ALTER TABLE `Wo_UserSkills`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_UserStory`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=22;

ALTER TABLE `Wo_UserStoryMedia`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=15;

ALTER TABLE `wo_userstorymedia`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `Wo_UserTiers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_user_gifts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_User_Gifts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

ALTER TABLE `Wo_Verification_Requests`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

ALTER TABLE `wo_verification_requests`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_videocalles`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_VideoCalles`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_votes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Votes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `wo_wonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `Wo_Wonders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `stickers`
  ADD CONSTRAINT `stickers_ibfk_1` FOREIGN KEY (`pack_id`) REFERENCES `sticker_packs` (`id`) ON DELETE CASCADE;

ALTER TABLE `Wo_MessageCommentReactions`
  ADD CONSTRAINT `Wo_MessageCommentReactions_ibfk_1` FOREIGN KEY (`comment_id`) REFERENCES `Wo_MessageComments` (`id`) ON DELETE CASCADE;

ALTER TABLE `Wo_UserCloudBackupSettings`
  ADD CONSTRAINT `fk_cloud_backup_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_cloud_backup_user_id` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE;

ALTER TABLE `Wo_UserMediaSettings`
  ADD CONSTRAINT `fk_media_settings_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE;

ALTER TABLE `Wo_UserRatings`
  ADD CONSTRAINT `fk_rated_user` FOREIGN KEY (`rated_user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_rater` FOREIGN KEY (`rater_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE;

ALTER TABLE `Wo_UserStory`
  ADD CONSTRAINT `Wo_UserStory_Wo_UserAds_id_fk` FOREIGN KEY (`ad_id`) REFERENCES `Wo_UserAds` (`id`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

