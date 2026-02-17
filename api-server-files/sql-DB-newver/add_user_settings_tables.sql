-- ============================================================================
-- SQL Migration: Add User Settings Tables
-- ============================================================================
-- Создание таблиц для хранения настроек пользователей мессенджера
-- Дата: 2026-01-23
-- ============================================================================

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

-- ============================================================================
-- Таблица: Wo_UserCloudBackupSettings
-- Назначение: Расширенные настройки облачного бэкапа и автозагрузки медиа
-- ============================================================================
CREATE TABLE IF NOT EXISTS `Wo_UserCloudBackupSettings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,

  -- АВТОЗАГРУЗКА МЕДИА (Мобильный интернет)
  `mobile_photos` tinyint(1) NOT NULL DEFAULT 1,
  `mobile_videos` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_files` tinyint(1) NOT NULL DEFAULT 0,
  `mobile_videos_limit` int(11) NOT NULL DEFAULT 10485760 COMMENT 'Лимит в байтах (по умолчанию 10 MB)',
  `mobile_files_limit` int(11) NOT NULL DEFAULT 5242880 COMMENT 'Лимит в байтах (по умолчанию 5 MB)',

  -- АВТОЗАГРУЗКА МЕДИА (Wi-Fi)
  `wifi_photos` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_videos` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_files` tinyint(1) NOT NULL DEFAULT 1,
  `wifi_videos_limit` int(11) NOT NULL DEFAULT 52428800 COMMENT 'Лимит в байтах (по умолчанию 50 MB)',
  `wifi_files_limit` int(11) NOT NULL DEFAULT 20971520 COMMENT 'Лимит в байтах (по умолчанию 20 MB)',

  -- АВТОЗАГРУЗКА МЕДИА (Роуминг)
  `roaming_photos` tinyint(1) NOT NULL DEFAULT 0,

  -- СОХРАНЯТЬ В ГАЛЕРЕЕ
  `save_to_gallery_private_chats` tinyint(1) NOT NULL DEFAULT 1,
  `save_to_gallery_groups` tinyint(1) NOT NULL DEFAULT 1,
  `save_to_gallery_channels` tinyint(1) NOT NULL DEFAULT 1,

  -- СТРИМИНГ
  `streaming_enabled` tinyint(1) NOT NULL DEFAULT 1,

  -- УПРАВЛЕНИЕ КЭШЕМ (в байтах)
  `cache_size_limit` bigint(20) NOT NULL DEFAULT 5368709120 COMMENT 'По умолчанию 5 GB',

  -- ОБЛАЧНЫЙ БЭКАП
  `backup_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `backup_provider` varchar(50) NOT NULL DEFAULT 'LOCAL_SERVER' COMMENT 'LOCAL_SERVER, GOOGLE_DRIVE, MEGA, DROPBOX',
  `backup_frequency` varchar(50) NOT NULL DEFAULT 'NEVER' COMMENT 'NEVER, DAILY, WEEKLY, MONTHLY',
  `last_backup_time` bigint(20) DEFAULT NULL COMMENT 'Timestamp в миллисекундах',

  -- ПРОКСИ
  `proxy_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `proxy_host` varchar(255) DEFAULT NULL,
  `proxy_port` int(11) DEFAULT NULL,

  -- Служебные поля
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),

  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_backup` (`user_id`, `backup_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Таблица: Wo_UserMediaSettings
-- Назначение: Базовые настройки автозагрузки медиа (упрощенная версия)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `Wo_UserMediaSettings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,

  -- АВТОЗАГРУЗКА МЕДИА
  `auto_download_photos` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `auto_download_videos` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `auto_download_audio` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',
  `auto_download_documents` enum('wifi_only','always','never') NOT NULL DEFAULT 'wifi_only',

  -- СЖАТИЕ МЕДИА
  `compress_photos` tinyint(1) NOT NULL DEFAULT 1,
  `compress_videos` tinyint(1) NOT NULL DEFAULT 1,

  -- БЭКАП
  `backup_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `last_backup_time` bigint(20) DEFAULT NULL COMMENT 'Timestamp в миллисекундах',

  -- Служебные поля
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),

  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Индексы и внешние ключи
-- ============================================================================
ALTER TABLE `Wo_UserCloudBackupSettings`
  ADD CONSTRAINT `fk_cloud_backup_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE;

ALTER TABLE `Wo_UserMediaSettings`
  ADD CONSTRAINT `fk_media_settings_user` FOREIGN KEY (`user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE;

-- ============================================================================
-- Завершение миграции
-- ============================================================================
COMMIT;

-- ============================================================================
-- Информация о миграции
-- ============================================================================
-- Таблицы созданы:
--   1. Wo_UserCloudBackupSettings - расширенные настройки облачного бэкапа
--   2. Wo_UserMediaSettings - базовые настройки автозагрузки медиа
--
-- Связи:
--   - Обе таблицы связаны с Wo_Users через user_id
--   - При удалении пользователя его настройки также удаляются (CASCADE)
--
-- Значения по умолчанию:
--   - Фото загружаются автоматически по Wi-Fi и мобильной сети
--   - Видео и файлы загружаются только по Wi-Fi
--   - В роуминге ничего не загружается
--   - Кэш ограничен 5 GB
--   - Облачный бэкап отключен по умолчанию
-- ============================================================================
