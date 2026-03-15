-- Migration: add_saved_messages_table
-- Saved (bookmarked) messages per user — server-side sync.
-- Allows messages to be preserved across device changes.

CREATE TABLE IF NOT EXISTS wm_saved_messages (
    id            INT(11)      NOT NULL AUTO_INCREMENT,
    user_id       INT(11)      NOT NULL,
    message_id    INT(11)      NOT NULL,
    chat_type     ENUM('chat','group','channel') NOT NULL DEFAULT 'chat',
    chat_id       INT(11)      NOT NULL DEFAULT 0,
    chat_name     VARCHAR(255) NOT NULL DEFAULT '',
    sender_name   VARCHAR(255) NOT NULL DEFAULT '',
    text          TEXT,
    media_url     VARCHAR(500) DEFAULT NULL,
    media_type    VARCHAR(50)  DEFAULT NULL,
    saved_at      INT(11)      NOT NULL DEFAULT 0,
    original_time INT(11)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY unique_saved (user_id, message_id, chat_type),
    KEY idx_user_id  (user_id),
    KEY idx_saved_at (user_id, saved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
