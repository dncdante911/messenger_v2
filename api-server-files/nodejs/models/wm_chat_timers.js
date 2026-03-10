module.exports = (sequelize, DataTypes) => {
    const WmChatTimers = sequelize.define('wm_chat_timers', {
        id:             { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, autoIncrement: true },
        // Canonical key: always min(user_a, user_b) / max(user_a, user_b) so one row per pair
        user_a:         { type: DataTypes.INTEGER, allowNull: false },
        user_b:         { type: DataTypes.INTEGER, allowNull: false },
        // 0 = disabled; positive = seconds before message expires after being read/sent
        timer_seconds:  { type: DataTypes.INTEGER, defaultValue: 0 },
        set_by:         { type: DataTypes.INTEGER, allowNull: false },
        updated_at:     { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
    }, {
        tableName:  'wm_chat_timers',
        timestamps: false,
        indexes: [
            { unique: true, fields: ['user_a', 'user_b'] },
        ],
    });
    return WmChatTimers;
};
