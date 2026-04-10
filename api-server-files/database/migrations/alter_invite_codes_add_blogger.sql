-- Add blogger_tag column to wm_invite_codes
-- Stores which blogger's campaign a code belongs to (NULL = standard code)

ALTER TABLE `wm_invite_codes`
    ADD COLUMN `blogger_tag` VARCHAR(32) NULL DEFAULT NULL AFTER `type`,
    ADD KEY `idx_blogger_tag` (`blogger_tag`);
