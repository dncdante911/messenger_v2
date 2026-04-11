-- ─── Add trial_used column to wo_users ───────────────────────────────────────
-- Required by routes/subscription.js start-trial endpoint.
-- Without this column, trial activation crashes with "Unknown column 'trial_used'"
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE `wo_users`
    ADD COLUMN IF NOT EXISTS `trial_used` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1 = free trial already used on this account'
        AFTER `pro_type`;
