const { DataTypes } = require('sequelize');
module.exports = (sequelize) => {
    return sequelize.define('wm_group_admin_logs', {
        id: { type: DataTypes.BIGINT, primaryKey: true, autoIncrement: true },
        group_id: { type: DataTypes.INTEGER, allowNull: false },
        admin_id: { type: DataTypes.INTEGER, allowNull: false },
        action: { type: DataTypes.STRING(64), allowNull: false },
        target_user_id: { type: DataTypes.INTEGER, defaultValue: null },
        target_message_id: { type: DataTypes.BIGINT, defaultValue: null },
        details: { type: DataTypes.TEXT, defaultValue: null },
        created_at: { type: DataTypes.DATE, defaultValue: DataTypes.NOW }
    }, {
        tableName: 'wm_group_admin_logs',
        timestamps: false
    });
};
