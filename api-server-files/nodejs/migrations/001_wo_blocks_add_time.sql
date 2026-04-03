-- Migration 001: Add `time` column to Wo_Blocks
-- MariaDB 11.4+
-- Run once on production; safe to re-run (IF NOT EXISTS guard).

ALTER TABLE `Wo_Blocks`
    ADD COLUMN IF NOT EXISTS `time` INT NOT NULL DEFAULT 0 COMMENT 'Unix timestamp when the block was created';

-- Back-fill existing rows with the current timestamp so sorting works correctly
UPDATE `Wo_Blocks` SET `time` = UNIX_TIMESTAMP() WHERE `time` = 0;
