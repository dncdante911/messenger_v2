-- ============================================================
-- Migration 06: Anonymous Story Views + Group Online Status
-- ============================================================

-- 1. Додаємо поле anonymous_hash до Wo_Story_Seen
--    Дозволяє фіксувати анонімні перегляди без user_id

ALTER TABLE `Wo_Story_Seen`
    ADD COLUMN IF NOT EXISTS `anonymous_hash` VARCHAR(64) NULL DEFAULT NULL
    COMMENT 'SHA-256(IP+UA+story_id) для анонімних переглядів (user_id=0)';

-- Індекс для швидкого пошуку дублікатів анонімних переглядів
ALTER TABLE `Wo_Story_Seen`
    ADD INDEX IF NOT EXISTS `idx_anon_hash` (`story_id`, `anonymous_hash`);

-- 2. Поле is_inline для ботів (якщо ще немає)
ALTER TABLE `Wo_Bots`
    ADD COLUMN IF NOT EXISTS `is_inline` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '1 = бот підтримує inline-режим (@botname запит)';

-- 3. Додаємо supports_inline_feedback для ботів
ALTER TABLE `Wo_Bots`
    ADD COLUMN IF NOT EXISTS `inline_feedback` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '1 = бот хоче отримувати chosen_inline_result події';

-- ============================================================
-- Rollback (якщо потрібно відкотити):
-- ALTER TABLE `Wo_Story_Seen` DROP COLUMN `anonymous_hash`;
-- ALTER TABLE `Wo_Bots` DROP COLUMN `is_inline`;
-- ALTER TABLE `Wo_Bots` DROP COLUMN `inline_feedback`;
-- ============================================================
