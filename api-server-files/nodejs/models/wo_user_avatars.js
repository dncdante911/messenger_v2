'use strict';

/**
 * wo_user_avatars — multiple avatars per user
 *
 * Schema (MySQL):
 *   CREATE TABLE `wo_user_avatars` (
 *     `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     `user_id`     INT UNSIGNED NOT NULL,
 *     `file_path`   VARCHAR(500) NOT NULL,
 *     `is_animated` TINYINT(1) NOT NULL DEFAULT 0,
 *     `mime_type`   VARCHAR(80) NOT NULL DEFAULT 'image/jpeg',
 *     `position`    SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 *     `created_at`  INT UNSIGNED NOT NULL DEFAULT 0,
 *     PRIMARY KEY (`id`),
 *     KEY `idx_user_pos` (`user_id`, `position`)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */

module.exports = (sequelize, DataTypes) => sequelize.define('wo_user_avatars', {
    id: {
        type:          DataTypes.INTEGER.UNSIGNED,
        autoIncrement: true,
        primaryKey:    true,
    },
    user_id: {
        type:      DataTypes.INTEGER.UNSIGNED,
        allowNull: false,
    },
    file_path: {
        type:      DataTypes.STRING(500),
        allowNull: false,
    },
    is_animated: {
        type:         DataTypes.TINYINT,
        defaultValue: 0,
    },
    mime_type: {
        type:         DataTypes.STRING(80),
        defaultValue: 'image/jpeg',
    },
    position: {
        type:         DataTypes.SMALLINT.UNSIGNED,
        defaultValue: 0,
    },
    created_at: {
        type:         DataTypes.INTEGER.UNSIGNED,
        defaultValue: 0,
    },
}, {
    tableName:  'wo_user_avatars',
    timestamps: false,
});
