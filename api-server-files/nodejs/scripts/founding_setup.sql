-- ============================================================
--  founding_setup.sql
--  Run ONCE by the DBA/admin to:
--   1. Wipe all users with user_id >= 101 and their data
--   2. Grant lifetime PRO (pro_time = 0 → never expires) to users 1-100
--   3. Add the is_founder column (tracks first-250 badge holders)
--
--  IMPORTANT: Make a full DB backup before running this script.
--             pro_time = 0 is safe — the cron job condition is
--             "pro_time > 0 AND pro_time < now", so 0 means forever.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── Step 1: add is_founder column (idempotent) ──────────────────
ALTER TABLE Wo_Users
    ADD COLUMN IF NOT EXISTS is_founder TINYINT NOT NULL DEFAULT 0;

-- ── Step 2: clean up data belonging to users >= 101 ─────────────
-- Messages sent or received
DELETE FROM Wo_Messages
WHERE from_id >= 101 OR to_id >= 101;

-- Sessions
DELETE FROM Wo_AppsSessions
WHERE user_id >= 101;

-- Notifications
DELETE FROM wo_notification
WHERE recipient_id >= 101;

-- Saved messages
DELETE FROM wm_saved_messages
WHERE user_id >= 101;

-- Pinned messages
DELETE FROM wm_pinned_messages
WHERE user_id >= 101;

-- Scheduled messages
DELETE FROM wm_scheduled_messages
WHERE user_id >= 101;

-- User chat metadata
DELETE FROM wo_userschat
WHERE user_id >= 101;

-- ── Step 3: delete users >= 101 ─────────────────────────────────
DELETE FROM Wo_Users WHERE user_id >= 101;

-- ── Step 4: lifetime PRO for users 1-100 ────────────────────────
--   is_pro = '1'  → active
--   pro_time = 0  → never expires (cron skips pro_time = 0)
--   pro_type = '2' → full PRO
--   verified = '1' → verified badge
UPDATE Wo_Users
SET
    is_pro    = '1',
    pro_time  = 0,
    pro_type  = '2',
    verified  = '1'
WHERE user_id BETWEEN 1 AND 100;

SET FOREIGN_KEY_CHECKS = 1;

SELECT
    user_id,
    username,
    is_pro,
    pro_type,
    pro_time,
    verified
FROM Wo_Users
WHERE user_id BETWEEN 1 AND 100
ORDER BY user_id;

SELECT CONCAT('Wiped users >= 101. Remaining: ', COUNT(*)) AS result
FROM Wo_Users;
