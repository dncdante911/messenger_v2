'use strict';

module.exports = function(sequelize, DataTypes) {
    return sequelize.define('wm_content_hash_blacklist', {
        id: {
            autoIncrement: true,
            type:          DataTypes.INTEGER.UNSIGNED,
            allowNull:     false,
            primaryKey:    true
        },
        sha256_hash: {
            type:      DataTypes.CHAR(64),
            allowNull: false,
            unique:    true
        },
        reason: {
            type:         DataTypes.STRING(100),
            allowNull:    false,
            defaultValue: 'explicit'
        },
        added_by: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0
        },
        created_at: {
            type:         DataTypes.DATE,
            allowNull:    false,
            defaultValue: DataTypes.NOW
        }
    }, {
        sequelize,
        tableName:  'wm_content_hash_blacklist',
        timestamps: false
    });
};
