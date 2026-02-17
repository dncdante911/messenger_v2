/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Users = sequelize.define('Wo_Bot_Users', {
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
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    state: {
      type: DataTypes.STRING(128),
      allowNull: true
    },
    state_data: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    is_blocked: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    is_banned: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    first_interaction_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    last_interaction_at: {
      type: DataTypes.DATE,
      allowNull: true
    },
    messages_count: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    custom_data: {
      type: DataTypes.TEXT,
      allowNull: true
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Users'
  });

  return Wo_Bot_Users;
};
