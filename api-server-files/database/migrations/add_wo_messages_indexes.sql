-- ─────────────────────────────────────────────────────────────────────────────
-- Migration: add composite indexes to Wo_Messages
-- Purpose:
--   1. idx_conv_time   (from_id, to_id, time)  — fetching a conversation
--      between two users ordered by time (covers the most common chat query)
--   2. idx_toid_time   (to_id, time)            — inbox: messages received by
--      a user sorted by time
--   3. idx_fromid_time (from_id, time)          — outbox: messages sent by a
--      user sorted by time
--   4. idx_conv_seen   (from_id, to_id, seen)   — unread count per conversation
-- ─────────────────────────────────────────────────────────────────────────────

-- Conversation timeline  (SELECT … WHERE from_id=? AND to_id=? ORDER BY time DESC)
ALTER TABLE `Wo_Messages`
  ADD INDEX `idx_conv_time` (`from_id`, `to_id`, `time`);

-- Inbox sorted by time  (SELECT … WHERE to_id=? ORDER BY time DESC)
ALTER TABLE `Wo_Messages`
  ADD INDEX `idx_toid_time` (`to_id`, `time`);

-- Outbox sorted by time  (SELECT … WHERE from_id=? ORDER BY time DESC)
ALTER TABLE `Wo_Messages`
  ADD INDEX `idx_fromid_time` (`from_id`, `time`);

-- Unread count per conversation  (SELECT COUNT(*) WHERE from_id=? AND to_id=? AND seen=0)
ALTER TABLE `Wo_Messages`
  ADD INDEX `idx_conv_seen` (`from_id`, `to_id`, `seen`);
