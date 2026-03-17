/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wm_Business_Hours', {
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
    weekday: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      comment: '0=Sun, 1=Mon … 6=Sat'
    },
    is_open: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      defaultValue: 1
    },
    open_time: {
      type: DataTypes.STRING(5),
      allowNull: false,
      defaultValue: '09:00'
    },
    close_time: {
      type: DataTypes.STRING(5),
      allowNull: false,
      defaultValue: '18:00'
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_business_hours',
    indexes: [
      { unique: true, fields: ['user_id', 'weekday'] },
      { fields: ['user_id'] }
    ]
  });
};
