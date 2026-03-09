module.exports = (sequelize, DataTypes) => {
    const WmChannelLivestreams = sequelize.define('wm_channel_livestreams', {
        id:           { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, autoIncrement: true },
        channel_id:   { type: DataTypes.INTEGER, allowNull: false },
        host_user_id: { type: DataTypes.INTEGER, allowNull: false },
        room_name:    { type: DataTypes.STRING(120), allowNull: false, unique: true },
        title:        { type: DataTypes.STRING(255), defaultValue: null },
        quality:      { type: DataTypes.STRING(10), defaultValue: '720p' },
        is_premium:   { type: DataTypes.TINYINT(1), defaultValue: 0 },
        status:       { type: DataTypes.ENUM('live', 'ended'), defaultValue: 'live' },
        viewer_count: { type: DataTypes.INTEGER, defaultValue: 0 },
        peak_viewers: { type: DataTypes.INTEGER, defaultValue: 0 },
        started_at:   { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
        ended_at:     { type: DataTypes.DATE, defaultValue: null }
    }, {
        tableName: 'wm_channel_livestreams',
        timestamps: false,
        indexes: [
            { fields: ['channel_id'] },
            { fields: ['host_user_id'] },
            { fields: ['status'] },
            { fields: ['room_name'], unique: true }
        ]
    });
    return WmChannelLivestreams;
};
