-- Migration 002: Add status_emoji and status_text to Wo_Users
-- MariaDB 11.4+
-- Run once on production; safe to re-run (IF NOT EXISTS guard).

ALTER TABLE `Wo_Users`
    ADD COLUMN IF NOT EXISTS `status_emoji` VARCHAR(16)  NULL DEFAULT NULL COMMENT 'Custom emoji status (PRO feature)',
    ADD COLUMN IF NOT EXISTS `status_text`  VARCHAR(60)  NULL DEFAULT NULL COMMENT 'Custom status text (PRO feature)';
