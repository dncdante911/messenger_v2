-- ─────────────────────────────────────────────────────────────────────────────
-- Миграция: Система модерации контента — WorldMates Messenger
-- Файл: 001_content_moderation.sql
-- Применить: mysql -u social -p socialhub < 001_content_moderation.sql
-- ─────────────────────────────────────────────────────────────────────────────

-- Хэш-блэклист запрещённого контента
-- Каждый удалённый модератором файл хэшируется и сюда добавляется.
-- При повторной загрузке такого файла — автоблок без ML.
CREATE TABLE IF NOT EXISTS `wm_content_hash_blacklist` (
    `id`          INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    `sha256_hash` CHAR(64)         NOT NULL COMMENT 'Точный SHA-256 хэш файла',
    `reason`      VARCHAR(100)     NOT NULL DEFAULT 'explicit' COMMENT 'nudity/violence/csam/spam',
    `added_by`    INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'user_id модератора; 0 = авто',
    `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sha256` (`sha256_hash`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Хэши запрещённого контента для автоблокировки повторных загрузок';


-- Очередь модерации
-- Контент, который NudeNet пометил как подозрительный, уходит сюда.
-- Модераторы просматривают через API и выносят решение.
CREATE TABLE IF NOT EXISTS `wm_moderation_queue` (
    `id`             INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    `file_path`      VARCHAR(500)     NOT NULL COMMENT 'Относительный путь к файлу на диске',
    `file_url`       VARCHAR(500)     NOT NULL COMMENT 'Полный URL файла',
    `media_type`     ENUM('image','video','audio','file') NOT NULL DEFAULT 'image',
    `sender_id`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'user_id отправителя',
    `channel_id`     INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '0 если не из канала',
    `group_id`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '0 если не из группы',
    `chat_type`      ENUM('private','group','channel') NOT NULL DEFAULT 'private',
    `content_level`  ENUM('all_ages','mature','adult_verified') NOT NULL DEFAULT 'all_ages',
    `sha256_hash`    CHAR(64)         NOT NULL DEFAULT '' COMMENT 'Хэш файла',
    `nudenet_labels` TEXT             COMMENT 'JSON: детекции NudeNet [{"label":...,"score":...}]',
    `nudenet_score`  FLOAT            NOT NULL DEFAULT 0 COMMENT 'Максимальный score угрозы',
    `trigger_reason` VARCHAR(200)     NOT NULL DEFAULT '' COMMENT 'Причина попадания в очередь',
    `status`         ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    `reviewed_by`    INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'user_id модератора',
    `reviewed_at`    DATETIME         COMMENT 'Когда была проверена',
    `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status`     (`status`),
    KEY `idx_sender`     (`sender_id`),
    KEY `idx_created`    (`created_at`),
    KEY `idx_score`      (`nudenet_score`),
    KEY `idx_sha256`     (`sha256_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Очередь контента, ожидающего ручной модерации';


-- Политики контента для каналов и групп
-- Устанавливается вручную администратором через API.
-- content_level определяет что разрешено загружать в этот канал/группу.
CREATE TABLE IF NOT EXISTS `wm_content_policy` (
    `id`            INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    `entity_type`   ENUM('channel','group') NOT NULL,
    `entity_id`     INT UNSIGNED     NOT NULL,
    `content_level` ENUM('all_ages','mature','adult_verified') NOT NULL DEFAULT 'all_ages',
    `updated_by`    INT UNSIGNED     NOT NULL DEFAULT 0,
    `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_entity` (`entity_type`, `entity_id`),
    KEY `idx_level` (`content_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Политика контента: какой уровень разрешён в канале/группе';


-- Расширяем таблицу жалоб: добавляем поля для жалоб на медиафайлы
-- (безопасно: только добавляем новые колонки, существующие не трогаем)
ALTER TABLE `Wo_Reports`
    ADD COLUMN IF NOT EXISTS `media_path`   VARCHAR(500) NULL COMMENT 'Путь к файлу на который жалоба',
    ADD COLUMN IF NOT EXISTS `report_type`  VARCHAR(50)  NOT NULL DEFAULT 'user' COMMENT 'user/media/message/channel',
    ADD COLUMN IF NOT EXISTS `message_id`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'ID сообщения если жалоба на сообщение';
