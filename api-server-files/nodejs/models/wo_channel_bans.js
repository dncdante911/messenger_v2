/* jshint indent: 2 */

// Wo_Channel_Bans — stores channel ban records
// Fields: id, channel_id, user_id, banned_by, reason, ban_time, expire_time
// expire_time = 0 means permanent ban

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_Channel_Bans', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    channel_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    banned_by: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    reason: {
      type: DataTypes.STRING(500),
      allowNull: false,
      defaultValue: ''
    },
    ban_time: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    expire_time: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0  // 0 = permanent
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Channel_Bans'
  });
};
