'use strict';

module.exports = function(sequelize, DataTypes) {
    return sequelize.define('wm_content_policy', {
        id: {
            autoIncrement: true,
            type:          DataTypes.INTEGER.UNSIGNED,
            allowNull:     false,
            primaryKey:    true
        },
        entity_type: {
            type:      DataTypes.ENUM('channel', 'group'),
            allowNull: false
        },
        entity_id: {
            type:      DataTypes.INTEGER.UNSIGNED,
            allowNull: false
        },
        content_level: {
            type:         DataTypes.ENUM('all_ages', 'mature', 'adult_verified'),
            allowNull:    false,
            defaultValue: 'all_ages'
        },
        updated_by: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        updated_at: {
            type:         DataTypes.DATE,
            allowNull:    false,
            defaultValue: DataTypes.NOW
        }
    }, {
        sequelize,
        tableName:  'wm_content_policy',
        timestamps: false
    });
};
