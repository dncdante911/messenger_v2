/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wm_Business_Chats', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      comment: 'Customer user_id'
    },
    business_user_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      comment: 'Business owner user_id'
    },
    last_message_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    last_time: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    unread_count: {
      type: DataTypes.SMALLINT.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_business_chats',
    indexes: [
      { unique: true, fields: ['user_id', 'business_user_id'] },
      { name: 'idx_user_time',     fields: ['user_id', 'last_time'] },
      { name: 'idx_biz_user_time', fields: ['business_user_id', 'last_time'] }
    ]
  });
};
