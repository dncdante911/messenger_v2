-- ============================================================================
-- Міграція: add_signal_group_sender_keys.sql
-- Signal Protocol — Sender Key Distribution для групових чатів
--
-- Протокол: Signal Sender Key (аналог WhatsApp групового E2EE)
--   1. Кожен учасник групи генерує власний SenderKey для групи.
--   2. Розподіляє його всім іншим учасникам (кожен payload зашифрований
--      індивідуальним Signal-сеансом між відправником та одержувачем).
--   3. Сервер зберігає зашифровані payload-и до доставки (store-and-forward).
--   4. Після отримання клієнт підтверджує доставку → запис видаляється.
--
-- Запуск: mysql -u user -p db_name < add_signal_group_sender_keys.sql
-- ============================================================================

-- ── Таблиця розподілу групових ключів відправника ────────────────────────────
-- Сервер зберігає ЛИШЕ зашифровані payload-и. Відкритих ключів групи немає.
-- Розшифрувати може тільки одержувач за допомогою свого приватного ключа.

CREATE TABLE IF NOT EXISTS `signal_group_sender_keys` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT
                    COMMENT 'Первинний ключ',
    `group_id`      INT UNSIGNED     NOT NULL
                    COMMENT 'ID групового чату (wo_groupchat.group_id)',
    `sender_id`     INT UNSIGNED     NOT NULL
                    COMMENT 'ID учасника, що розповсюджує свій SenderKey',
    `recipient_id`  INT UNSIGNED     NOT NULL
                    COMMENT 'ID учасника, якому призначений цей payload',
    `distribution`  MEDIUMTEXT       NOT NULL
                    COMMENT 'Зашифрований SenderKeyDistributionMessage (Base64). Розшифровує лише recipient.',
    `delivered`     TINYINT(1)       NOT NULL DEFAULT 0
                    COMMENT '0 = очікує доставки, 1 = доставлено',
    `created_at`    TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
                    COMMENT 'Час створення запису',
    `delivered_at`  TIMESTAMP        NULL     DEFAULT NULL
                    COMMENT 'Час підтвердження доставки',
    PRIMARY KEY (`id`),
    -- Індекс для запиту "всі невидані distributions для цього recipient у групі"
    INDEX `idx_recipient_group`  (`recipient_id`, `group_id`, `delivered`),
    -- Індекс для запиту "distributions від конкретного sender у групі"
    INDEX `idx_sender_group`     (`sender_id`, `group_id`),
    -- Для очищення старих доставлених записів
    INDEX `idx_delivered_at`     (`delivered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Signal Protocol — SenderKey distributions для групових чатів (store-and-forward)';

SELECT 'Міграція add_signal_group_sender_keys застосована успішно' AS result;
