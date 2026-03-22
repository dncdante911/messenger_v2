'use strict';

module.exports = function(sequelize, DataTypes) {
    return sequelize.define('wm_moderation_queue', {
        id: {
            autoIncrement: true,
            type:          DataTypes.INTEGER.UNSIGNED,
            allowNull:     false,
            primaryKey:    true
        },
        file_path: {
            type:      DataTypes.STRING(500),
            allowNull: false
        },
        file_url: {
            type:      DataTypes.STRING(500),
            allowNull: false
        },
        media_type: {
            type:         DataTypes.ENUM('image', 'video', 'audio', 'file'),
            allowNull:    false,
            defaultValue: 'image'
        },
        sender_id: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        channel_id: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        group_id: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        chat_type: {
            type:         DataTypes.ENUM('private', 'group', 'channel'),
            allowNull:    false,
            defaultValue: 'private'
        },
        content_level: {
            type:         DataTypes.ENUM('all_ages', 'mature', 'adult_verified'),
            allowNull:    false,
            defaultValue: 'all_ages'
        },
        sha256_hash: {
            type:         DataTypes.CHAR(64),
            allowNull:    false,
            defaultValue: ''
        },
        nudenet_labels: {
            type:      DataTypes.TEXT,
            allowNull: true
        },
        nudenet_score: {
            type:         DataTypes.FLOAT,
            allowNull:    false,
            defaultValue: 0
        },
        trigger_reason: {
            type:         DataTypes.STRING(200),
            allowNull:    false,
            defaultValue: ''
        },
        status: {
            type:         DataTypes.ENUM('pending', 'approved', 'rejected'),
            allowNull:    false,
            defaultValue: 'pending'
        },
        reviewed_by: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        reviewed_at: {
            type:      DataTypes.DATE,
            allowNull: true
        },
        created_at: {
            type:         DataTypes.DATE,
            allowNull:    false,
            defaultValue: DataTypes.NOW
        }
    }, {
        sequelize,
        tableName:  'wm_moderation_queue',
        timestamps: false
    });
};
