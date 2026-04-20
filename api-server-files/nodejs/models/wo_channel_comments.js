'use strict';

/**
 * wo_channel_comments — Comments on channel posts (also used as thread messages)
 *
 * DB Migration (if table does not exist):
 *   CREATE TABLE `wo_channel_comments` (
 *     `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `post_id`     INT UNSIGNED NOT NULL,
 *     `user_id`     INT UNSIGNED NOT NULL,
 *     `text`        TEXT,
 *     `time`        INT UNSIGNED NOT NULL DEFAULT 0,
 *     `reply_to_id` INT UNSIGNED DEFAULT NULL,
 *     PRIMARY KEY (`id`),
 *     KEY `idx_post` (`post_id`),
 *     KEY `idx_user` (`user_id`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   ALTER TABLE `wo_channel_comments`
 *     ADD COLUMN IF NOT EXISTS `reply_to_id` INT UNSIGNED DEFAULT NULL;
 */

module.exports = (sequelize, DataTypes) => sequelize.define('wo_channel_comments', {
    id:          { type: DataTypes.INTEGER.UNSIGNED, autoIncrement: true, primaryKey: true },
    post_id:     { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    user_id:     { type: DataTypes.INTEGER.UNSIGNED, allowNull: false },
    text:        { type: DataTypes.TEXT, allowNull: true },
    time:        { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 },
    reply_to_id: { type: DataTypes.INTEGER.UNSIGNED, allowNull: true },
    sticker:     { type: DataTypes.STRING(512),       allowNull: true, defaultValue: null },
}, { tableName: 'wo_channel_comments', timestamps: false });
