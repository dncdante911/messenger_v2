'use strict';

/**
 * wm_scheduled_messages — Scheduled posts for groups, channels and DMs
 *
 * DB Migration (run on server):
 *   CREATE TABLE `wm_scheduled_messages` (
 *     `id`             INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `user_id`        INT UNSIGNED NOT NULL,
 *     `chat_id`        INT UNSIGNED NOT NULL,
 *     `chat_type`      ENUM('dm','group','channel') NOT NULL DEFAULT 'group',
 *     `text`           TEXT,
 *     `media_url`      VARCHAR(500) DEFAULT NULL,
 *     `media_type`     VARCHAR(50) DEFAULT NULL,
 *     `scheduled_at`   INT UNSIGNED NOT NULL,
 *     `repeat_type`    ENUM('none','daily','weekly','monthly') NOT NULL DEFAULT 'none',
 *     `is_pinned`      TINYINT(1) NOT NULL DEFAULT 0,
 *     `notify_members` TINYINT(1) NOT NULL DEFAULT 1,
 *     `status`         ENUM('pending','sent','failed','cancelled') NOT NULL DEFAULT 'pending',
 *     `created_at`     INT UNSIGNED NOT NULL DEFAULT 0,
 *     PRIMARY KEY (`id`),
 *     KEY `idx_chat` (`chat_type`, `chat_id`),
 *     KEY `idx_pending` (`status`, `scheduled_at`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */

module.exports = (sequelize, DataTypes) => sequelize.define('wm_scheduled_messages', {
    id: {
        type:          DataTypes.INTEGER.UNSIGNED,
        autoIncrement: true,
        primaryKey:    true,
    },
    user_id:        { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    chat_id:        { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    chat_type:      { type: DataTypes.ENUM('dm', 'group', 'channel'), defaultValue: 'group' },
    text:           { type: DataTypes.TEXT, allowNull: true },
    media_url:      { type: DataTypes.STRING(500), allowNull: true },
    media_type:     { type: DataTypes.STRING(50),  allowNull: true },
    scheduled_at:   { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    repeat_type:    { type: DataTypes.ENUM('none', 'daily', 'weekly', 'monthly'), defaultValue: 'none' },
    is_pinned:      { type: DataTypes.TINYINT,  defaultValue: 0 },
    notify_members: { type: DataTypes.TINYINT,  defaultValue: 1 },
    status:         { type: DataTypes.ENUM('pending', 'sent', 'failed', 'cancelled'), defaultValue: 'pending' },
    created_at:     { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 },
}, {
    tableName:  'wm_scheduled_messages',
    timestamps: false,
});
