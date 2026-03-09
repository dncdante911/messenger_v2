module.exports = (sequelize, DataTypes) => {
    const WmChannelSubscriptions = sequelize.define('wm_channel_subscriptions', {
        id:         { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, autoIncrement: true },
        channel_id: { type: DataTypes.INTEGER, allowNull: false, unique: true },
        plan:       { type: DataTypes.ENUM('monthly', 'quarterly', 'annual'), defaultValue: 'monthly' },
        is_active:  { type: DataTypes.TINYINT(1), defaultValue: 0 },
        started_at: { type: DataTypes.DATE, defaultValue: null },
        expires_at: { type: DataTypes.DATE, defaultValue: null },
        updated_at: { type: DataTypes.DATE, defaultValue: DataTypes.NOW }
    }, {
        tableName: 'wm_channel_subscriptions',
        timestamps: false,
        indexes: [{ fields: ['channel_id'] }, { fields: ['expires_at'] }]
    });
    return WmChannelSubscriptions;
};
