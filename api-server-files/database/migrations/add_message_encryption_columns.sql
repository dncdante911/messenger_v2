-- ============================================================================
-- Migration: add_message_encryption_columns.sql
-- Добавляет колонки шифрования в таблицу Wo_Messages (если они отсутствуют)
-- Запускается один раз на сервере: mysql -u user -p db_name < this_file.sql
-- ============================================================================

-- AES-256-GCM поля (для WorldMates Android)
ALTER TABLE `Wo_Messages`
    ADD COLUMN IF NOT EXISTS `text_ecb`       TEXT          DEFAULT NULL
        COMMENT 'AES-128-ECB encrypted text (WoWonder website compatibility)',
    ADD COLUMN IF NOT EXISTS `text_preview`   VARCHAR(255)  DEFAULT NULL
        COMMENT 'Plaintext preview first 100 chars — используется для поиска',
    ADD COLUMN IF NOT EXISTS `iv`             VARCHAR(255)  DEFAULT NULL
        COMMENT 'Base64 GCM IV (12 bytes)',
    ADD COLUMN IF NOT EXISTS `tag`            VARCHAR(255)  DEFAULT NULL
        COMMENT 'Base64 GCM auth tag (16 bytes)',
    ADD COLUMN IF NOT EXISTS `cipher_version` INT(11)       DEFAULT 1
        COMMENT '1=AES-128-ECB (WoWonder), 2=AES-256-GCM (WorldMates)',
    ADD COLUMN IF NOT EXISTS `edited`         INT(11)       DEFAULT 0
        COMMENT '1 если сообщение было отредактировано';

-- Индекс для поиска по text_preview
ALTER TABLE `Wo_Messages`
    ADD INDEX IF NOT EXISTS `idx_text_preview` (`text_preview`(50));

-- Аналогично для нижнерегистровой таблицы wo_messages (оригинал WoWonder)
ALTER TABLE `wo_messages`
    ADD COLUMN IF NOT EXISTS `text_ecb`       TEXT          DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `text_preview`   VARCHAR(255)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `iv`             VARCHAR(255)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `tag`            VARCHAR(255)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS `cipher_version` INT(11)       DEFAULT 1,
    ADD COLUMN IF NOT EXISTS `edited`         INT(11)       DEFAULT 0;

SELECT 'Migration add_message_encryption_columns applied successfully' AS result;
