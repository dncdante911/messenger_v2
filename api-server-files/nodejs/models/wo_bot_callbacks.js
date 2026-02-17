/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Callbacks = sequelize.define('Wo_Bot_Callbacks', {
    id: {
      autoIncrement: true,
      type: DataTypes.BIGINT,
      allowNull: false,
      primaryKey: true
    },
    bot_id: {
      type: DataTypes.STRING(64),
      allowNull: false
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    message_id: {
      type: DataTypes.BIGINT,
      allowNull: true
    },
    callback_data: {
      type: DataTypes.STRING(256),
      allowNull: false
    },
    answered: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    answer_text: {
      type: DataTypes.STRING(256),
      allowNull: true
    },
    answer_show_alert: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Callbacks'
  });

  return Wo_Bot_Callbacks;
};
