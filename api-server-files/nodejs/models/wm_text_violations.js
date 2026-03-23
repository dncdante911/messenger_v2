'use strict';

module.exports = function(sequelize, DataTypes) {
    return sequelize.define('wm_text_violations', {
        id: {
            autoIncrement: true,
            type:          DataTypes.INTEGER.UNSIGNED,
            allowNull:     false,
            primaryKey:    true
        },
        sender_id: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        chat_type: {
            type:         DataTypes.ENUM('private', 'group', 'channel'),
            allowNull:    false,
            defaultValue: 'private'
        },
        entity_id: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        action: {
            type:      DataTypes.ENUM('warn', 'block'),
            allowNull: false
        },
        top_category: {
            type:         DataTypes.STRING(50),
            allowNull:    false,
            defaultValue: ''
        },
        max_score: {
            type:         DataTypes.FLOAT,
            allowNull:    false,
            defaultValue: 0
        },
        text_preview: {
            type:         DataTypes.STRING(200),
            allowNull:    false,
            defaultValue: ''
        },
        created_at: {
            type:         DataTypes.DATE,
            allowNull:    false,
            defaultValue: DataTypes.NOW
        }
    }, {
        sequelize,
        tableName:  'wm_text_violations',
        timestamps: false
    });
};
