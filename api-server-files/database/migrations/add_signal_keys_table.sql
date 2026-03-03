-- ============================================================================
-- Migration: add_signal_keys_table.sql
-- Signal Protocol — pre-key server storage
--
-- Creates:  signal_keys table (public key material for X3DH)
-- Alters:   Wo_Messages / wo_messages — adds signal_header column
--
-- Run once on the server:
--   mysql -u user -p db_name < add_signal_keys_table.sql
-- ============================================================================

-- ── signal_keys table ────────────────────────────────────────────────────────
-- Stores PUBLIC key material only.  Private keys never leave devices.

CREATE TABLE IF NOT EXISTS `signal_keys` (
    `user_id`           INT UNSIGNED     NOT NULL,
    `identity_key`      VARCHAR(64)      NOT NULL DEFAULT ''
                        COMMENT 'Base64 X25519 identity public key (32 bytes)',
    `signed_prekey_id`  INT UNSIGNED     NOT NULL DEFAULT 0
                        COMMENT 'Integer ID of current signed pre-key',
    `signed_prekey`     VARCHAR(64)      NOT NULL DEFAULT ''
                        COMMENT 'Base64 X25519 signed pre-key public (32 bytes)',
    `signed_prekey_sig` VARCHAR(128)     NOT NULL DEFAULT ''
                        COMMENT 'Base64 Ed25519 signature over signed_prekey (64 bytes)',
    `prekeys`           MEDIUMTEXT       DEFAULT '[]'
                        COMMENT 'JSON array of one-time pre-keys: [{id,key}]',
    `updated_at`        TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    INDEX `idx_signal_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Signal Protocol — X3DH public key bundles per user';

-- ── signal_header column in Wo_Messages ──────────────────────────────────────
-- Stores the Double Ratchet + X3DH message header for cipher_version=3.
-- NULL for messages encrypted with version 1 (ECB) or 2 (GCM).

ALTER TABLE `Wo_Messages`
    ADD COLUMN IF NOT EXISTS `signal_header` TEXT DEFAULT NULL
        COMMENT 'JSON DR header for Signal-encrypted messages (cipher_version=3)';

ALTER TABLE `wo_messages`
    ADD COLUMN IF NOT EXISTS `signal_header` TEXT DEFAULT NULL
        COMMENT 'JSON DR header for Signal-encrypted messages (cipher_version=3)';

-- ── index for signal header look-up ──────────────────────────────────────────
-- (optional, useful for admin tooling; messages are fetched by id/conversation)
ALTER TABLE `Wo_Messages`
    ADD INDEX IF NOT EXISTS `idx_cipher_version` (`cipher_version`);

ALTER TABLE `wo_messages`
    ADD INDEX IF NOT EXISTS `idx_cipher_version` (`cipher_version`);

SELECT 'Migration add_signal_keys_table applied successfully' AS result;
