/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wm_Business_Quick_Replies', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    shortcut: {
      type: DataTypes.STRING(32),
      allowNull: false,
      comment: 'trigger keyword (without /)'
    },
    text: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    media_url: {
      type: DataTypes.STRING(255),
      allowNull: true,
      defaultValue: null
    },
    created_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    updated_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_business_quick_replies',
    indexes: [
      { unique: true, fields: ['user_id', 'shortcut'] },
      { fields: ['user_id'] }
    ]
  });
};
