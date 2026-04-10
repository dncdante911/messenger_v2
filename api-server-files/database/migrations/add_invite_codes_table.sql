-- ─── Invite codes table ──────────────────────────────────────────────────────
-- ULTRA codes: 500 lifetime codes (valid until 2099-12-31)
-- PRO codes:   2000 annual codes (+1 year from activation, code valid 1 year)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `wm_invite_codes` (
    `id`         INT              NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(32)      NOT NULL,
    `type`       ENUM('ultra','pro') NOT NULL,
    `is_used`    TINYINT(1)       NOT NULL DEFAULT 0,
    `used_by`    INT              NULL     DEFAULT NULL,
    `used_at`    INT              NULL     DEFAULT NULL,
    `created_at` INT              NOT NULL,
    `expires_at` INT              NULL     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_code` (`code`),
    KEY `idx_is_used` (`is_used`),
    KEY `idx_type` (`type`),
    KEY `idx_used_by` (`used_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
