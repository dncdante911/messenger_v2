'use strict';

/**
 * wm_chat_folders — User-defined chat folders (synced across devices, shareable)
 *
 * DB Migration (run on server):
 *   CREATE TABLE `wm_chat_folders` (
 *     `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `owner_id`    INT UNSIGNED NOT NULL,
 *     `name`        VARCHAR(100) NOT NULL,
 *     `emoji`       VARCHAR(10) NOT NULL DEFAULT '📁',
 *     `color`       VARCHAR(7)  NOT NULL DEFAULT '#2196F3',
 *     `position`    SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 *     `is_shared`   TINYINT(1) NOT NULL DEFAULT 0,
 *     `share_code`  VARCHAR(32) DEFAULT NULL,
 *     `created_at`  INT UNSIGNED NOT NULL DEFAULT 0,
 *     PRIMARY KEY (`id`),
 *     UNIQUE KEY `uk_share_code` (`share_code`),
 *     KEY `idx_owner` (`owner_id`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   CREATE TABLE `wm_chat_folder_items` (
 *     `id`         INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `folder_id`  INT UNSIGNED NOT NULL,
 *     `chat_type`  ENUM('dm','group','channel') NOT NULL,
 *     `chat_id`    INT UNSIGNED NOT NULL,
 *     `added_by`   INT UNSIGNED NOT NULL,
 *     `added_at`   INT UNSIGNED NOT NULL DEFAULT 0,
 *     PRIMARY KEY (`id`),
 *     UNIQUE KEY `uk_folder_chat` (`folder_id`, `chat_type`, `chat_id`),
 *     KEY `idx_folder` (`folder_id`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   CREATE TABLE `wm_chat_folder_members` (
 *     `id`        INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `folder_id` INT UNSIGNED NOT NULL,
 *     `user_id`   INT UNSIGNED NOT NULL,
 *     `joined_at` INT UNSIGNED NOT NULL DEFAULT 0,
 *     PRIMARY KEY (`id`),
 *     UNIQUE KEY `uk_folder_user` (`folder_id`, `user_id`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */

module.exports.Folder = (sequelize, DataTypes) => sequelize.define('wm_chat_folders', {
    id:         { type: DataTypes.INTEGER.UNSIGNED, autoIncrement: true, primaryKey: true },
    owner_id:   { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    name:       { type: DataTypes.STRING(100), allowNull: false },
    emoji:      { type: DataTypes.STRING(10),  defaultValue: '📁' },
    color:      { type: DataTypes.STRING(7),   defaultValue: '#2196F3' },
    position:   { type: DataTypes.SMALLINT.UNSIGNED, defaultValue: 0 },
    is_shared:  { type: DataTypes.TINYINT, defaultValue: 0 },
    share_code: { type: DataTypes.STRING(32), allowNull: true },
    created_at: { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 },
}, { tableName: 'wm_chat_folders', timestamps: false });

module.exports.FolderItem = (sequelize, DataTypes) => sequelize.define('wm_chat_folder_items', {
    id:        { type: DataTypes.INTEGER.UNSIGNED, autoIncrement: true, primaryKey: true },
    folder_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    chat_type: { type: DataTypes.ENUM('dm', 'group', 'channel'), allowNull: false },
    chat_id:   { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    added_by:  { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    added_at:  { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 },
}, { tableName: 'wm_chat_folder_items', timestamps: false });

module.exports.FolderMember = (sequelize, DataTypes) => sequelize.define('wm_chat_folder_members', {
    id:        { type: DataTypes.INTEGER.UNSIGNED, autoIncrement: true, primaryKey: true },
    folder_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    user_id:   { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    joined_at: { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 },
}, { tableName: 'wm_chat_folder_members', timestamps: false });
