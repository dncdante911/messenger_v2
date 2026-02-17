/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Rate_Limits = sequelize.define('Wo_Bot_Rate_Limits', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    bot_id: {
      type: DataTypes.STRING(64),
      allowNull: false
    },
    endpoint: {
      type: DataTypes.STRING(128),
      allowNull: false
    },
    requests_count: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    window_start: {
      type: DataTypes.DATE,
      allowNull: false
    },
    window_type: {
      type: DataTypes.ENUM('second', 'minute', 'hour'),
      allowNull: false,
      defaultValue: 'minute'
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Rate_Limits'
  });

  return Wo_Bot_Rate_Limits;
};
