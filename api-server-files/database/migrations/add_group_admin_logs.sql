CREATE TABLE IF NOT EXISTS wm_group_admin_logs (
    id BIGINT(20) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id INT(11) NOT NULL,
    admin_id INT(11) NOT NULL,
    action VARCHAR(64) NOT NULL COMMENT 'e.g. add_member, remove_member, delete_message, change_title, pin_message, change_role, ban_user, unban_user, change_settings',
    target_user_id INT(11) DEFAULT NULL COMMENT 'Affected user if applicable',
    target_message_id BIGINT(20) DEFAULT NULL COMMENT 'Affected message if applicable',
    details TEXT DEFAULT NULL COMMENT 'JSON with extra info (old/new values)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_group_logs (group_id, created_at DESC),
    INDEX idx_admin_logs (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
