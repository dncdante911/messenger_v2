module.exports = (sequelize, DataTypes) => {
    const WmCallRecordings = sequelize.define('wm_call_recordings', {
        id:          { type: DataTypes.INTEGER.UNSIGNED, primaryKey: true, autoIncrement: true },
        // For group calls: room_name from wo_group_calls; for streams: room_name from wm_channel_livestreams
        room_name:   { type: DataTypes.STRING(120), allowNull: false },
        // 'group_call' | 'channel_stream' | 'private_call'
        type:        { type: DataTypes.ENUM('group_call', 'channel_stream', 'private_call'), defaultValue: 'group_call' },
        // Who uploaded (recorder)
        uploader_id: { type: DataTypes.INTEGER, allowNull: false },
        // Associated channel or group
        channel_id:  { type: DataTypes.INTEGER, defaultValue: null },
        group_id:    { type: DataTypes.INTEGER, defaultValue: null },
        // Storage
        filename:    { type: DataTypes.STRING(255), allowNull: false },
        file_path:   { type: DataTypes.STRING(512), allowNull: false },
        file_size:   { type: DataTypes.BIGINT, defaultValue: 0 },   // bytes
        duration:    { type: DataTypes.INTEGER, defaultValue: 0 },  // seconds
        mime_type:   { type: DataTypes.STRING(64), defaultValue: 'video/webm' },
        // Status: processing → ready | failed
        status:      { type: DataTypes.ENUM('processing', 'ready', 'failed'), defaultValue: 'processing' },
        created_at:  { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
    }, {
        tableName:  'wm_call_recordings',
        timestamps: false,
        indexes: [
            { fields: ['room_name'] },
            { fields: ['uploader_id'] },
            { fields: ['channel_id'] },
            { fields: ['type', 'status'] },
        ],
    });
    return WmCallRecordings;
};
