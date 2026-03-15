'use strict';

module.exports = function(sequelize, DataTypes) {
    return sequelize.define('wm_saved_messages', {
        id: {
            autoIncrement: true,
            type:          DataTypes.INTEGER,
            allowNull:     false,
            primaryKey:    true,
        },
        user_id: {
            type:      DataTypes.INTEGER,
            allowNull: false,
        },
        message_id: {
            type:      DataTypes.INTEGER,
            allowNull: false,
        },
        chat_type: {
            type:         DataTypes.ENUM('chat', 'group', 'channel'),
            allowNull:    false,
            defaultValue: 'chat',
        },
        chat_id: {
            type:         DataTypes.INTEGER,
            allowNull:    false,
            defaultValue: 0,
        },
        chat_name: {
            type:         DataTypes.STRING(255),
            allowNull:    false,
            defaultValue: '',
        },
        sender_name: {
            type:         DataTypes.STRING(255),
            allowNull:    false,
            defaultValue: '',
        },
        text: {
            type:      DataTypes.TEXT,
            allowNull: true,
        },
        media_url: {
            type:      DataTypes.STRING(500),
            allowNull: true,
        },
        media_type: {
            type:      DataTypes.STRING(50),
            allowNull: true,
        },
        saved_at: {
            type:         DataTypes.INTEGER,
            allowNull:    false,
            defaultValue: 0,
        },
        original_time: {
            type:         DataTypes.INTEGER,
            allowNull:    false,
            defaultValue: 0,
        },
    }, {
        sequelize,
        timestamps: false,
        tableName:  'wm_saved_messages',
    });
};
