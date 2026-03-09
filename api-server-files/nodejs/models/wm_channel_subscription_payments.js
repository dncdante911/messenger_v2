module.exports = (sequelize, DataTypes) => {
    const WmChannelSubscriptionPayments = sequelize.define('wm_channel_subscription_payments', {
        id:            { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, autoIncrement: true },
        channel_id:    { type: DataTypes.INTEGER, allowNull: false },
        owner_user_id: { type: DataTypes.INTEGER, allowNull: false },
        order_id:      { type: DataTypes.STRING(64), allowNull: false, unique: true },
        provider:      { type: DataTypes.ENUM('wayforpay', 'liqpay'), allowNull: false },
        plan:          { type: DataTypes.ENUM('monthly', 'quarterly', 'annual'), allowNull: false },
        amount_uah:    { type: DataTypes.DECIMAL(10, 2), allowNull: false },
        status:        { type: DataTypes.ENUM('pending', 'success', 'failed', 'refunded'), defaultValue: 'pending' },
        raw_response:  { type: DataTypes.TEXT, defaultValue: null },
        created_at:    { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
        updated_at:    { type: DataTypes.DATE, defaultValue: DataTypes.NOW }
    }, {
        tableName: 'wm_channel_subscription_payments',
        timestamps: false,
        indexes: [
            { fields: ['channel_id'] },
            { fields: ['owner_user_id'] },
            { fields: ['status'] }
        ]
    });
    return WmChannelSubscriptionPayments;
};
