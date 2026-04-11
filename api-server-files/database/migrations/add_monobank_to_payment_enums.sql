-- ─── Add monobank to wm_channel_subscription_payments provider enum ──────────
-- Also fix wm_subscription_payments table to include monobank.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE `wm_channel_subscription_payments`
    MODIFY COLUMN `provider`
        ENUM('wayforpay', 'liqpay', 'monobank') NOT NULL;

-- Also add monobank to the user PRO payments table if it exists
ALTER TABLE `wm_subscription_payments`
    MODIFY COLUMN `provider`
        ENUM('wayforpay', 'liqpay', 'monobank') NOT NULL;
