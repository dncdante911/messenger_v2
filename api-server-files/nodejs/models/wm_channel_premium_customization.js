module.exports = (sequelize, DataTypes) => {
    const WmChannelPremiumCustomization = sequelize.define('wm_channel_premium_customization', {
        channel_id:            { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, allowNull: false },
        accent_color_id:       { type: DataTypes.STRING(40), defaultValue: null },
        banner_pattern_id:     { type: DataTypes.STRING(40), defaultValue: null },
        emoji_pack_id:         { type: DataTypes.STRING(40), defaultValue: null },
        font_weight:           { type: DataTypes.STRING(40), defaultValue: null },
        post_corner_radius:    { type: DataTypes.TINYINT.UNSIGNED, defaultValue: null },
        avatar_frame:          { type: DataTypes.STRING(40), defaultValue: null },
        posts_backdrop_enabled:{ type: DataTypes.TINYINT(1), defaultValue: 0 },
        updated_at:            { type: DataTypes.INTEGER.UNSIGNED, defaultValue: 0 }
    }, {
        tableName: 'wm_channel_premium_customization',
        timestamps: false,
        indexes: [{ fields: ['channel_id'] }]
    });
    return WmChannelPremiumCustomization;
};
