-- ─────────────────────────────────────────────────────────────────────────────
-- Миграция 002: pHash + Текстовая модерация — WorldMates Messenger
-- Файл: 002_phash_and_text_moderation.sql
-- Применить: mysql -u social -p socialhub < 002_phash_and_text_moderation.sql
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. pHash в таблице хэш-блэклиста ─────────────────────────────────────────
-- Perceptual hash (pHash) позволяет находить визуально похожие изображения
-- даже если они были пережаты, обрезаны или немного изменены.
-- Хранится как BIGINT (64-битный хэш) для Hamming distance сравнения в Node.js.

ALTER TABLE `wm_content_hash_blacklist`
    ADD COLUMN IF NOT EXISTS `phash_int` BIGINT UNSIGNED NULL
        COMMENT 'Perceptual hash (pHash) как 64-bit BIGINT для Hamming distance сравнения',
    ADD KEY IF NOT EXISTS `idx_phash` (`phash_int`);


-- ── 2. phash_int в очереди модерации ─────────────────────────────────────────
-- При отклонении контента модератором — берём phash_int из очереди
-- и кладём в блэклист, чтобы похожие изображения тоже блокировались.

ALTER TABLE `wm_moderation_queue`
    ADD COLUMN IF NOT EXISTS `phash_int` BIGINT UNSIGNED NULL
        COMMENT 'pHash изображения (64-bit) для определения похожих при занесении в блэклист';


-- ── 3. Таблица событий текстовой токсичности ─────────────────────────────────
-- Логируем текстовые нарушения (WARN + BLOCK от Detoxify).
-- Не блокируем WARN сразу — накапливаем для статистики нарушителей.
-- Для анализа паттернов и дообучения собственной модели (Фаза 3).

CREATE TABLE IF NOT EXISTS `wm_text_violations` (
    `id`            INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    `sender_id`     INT UNSIGNED     NOT NULL DEFAULT 0    COMMENT 'user_id отправителя',
    `chat_type`     ENUM('private','group','channel') NOT NULL DEFAULT 'private',
    `entity_id`     INT UNSIGNED     NOT NULL DEFAULT 0    COMMENT 'group_id или channel_id (0 для private)',
    `action`        ENUM('warn','block') NOT NULL           COMMENT 'Решение Detoxify',
    `top_category`  VARCHAR(50)      NOT NULL DEFAULT ''    COMMENT 'Категория: toxicity/threat/insult/etc',
    `max_score`     FLOAT            NOT NULL DEFAULT 0     COMMENT 'Максимальный score (0.0-1.0)',
    `text_preview`  VARCHAR(200)     NOT NULL DEFAULT ''    COMMENT 'Первые 200 символов текста для ревью',
    `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sender`    (`sender_id`),
    KEY `idx_action`    (`action`),
    KEY `idx_created`   (`created_at`),
    KEY `idx_category`  (`top_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Лог токсичных текстовых сообщений для анализа нарушителей';
