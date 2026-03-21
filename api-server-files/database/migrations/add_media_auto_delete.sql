-- ─── Auto-delete Media by Timer ─────────────────────────────────────────────
-- Per-message: when the media file should be physically deleted from storage.
-- Per-chat:    the user's chosen auto-delete interval for this conversation.

-- Add columns to messages table
ALTER TABLE Wo_Messages ADD COLUMN IF NOT EXISTS media_delete_at TIMESTAMP DEFAULT NULL COMMENT 'When to auto-delete the media file (message text remains)';
ALTER TABLE Wo_Messages ADD COLUMN IF NOT EXISTS media_deleted TINYINT(1) DEFAULT 0 COMMENT '1 = media file has been deleted, placeholder shown to both sides';

-- Chat-level media auto-delete setting (per-user, per-chat)
CREATE TABLE IF NOT EXISTS wm_chat_media_settings (
    id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT(11) NOT NULL,
    chat_id INT(11) NOT NULL,
    media_auto_delete_seconds INT(11) DEFAULT 0 COMMENT '0=never, 86400=1day, 259200=3days, 604800=1week, 1209600=2weeks, 2592000=1month',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_chat (user_id, chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
