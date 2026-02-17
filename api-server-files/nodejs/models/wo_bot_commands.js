/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Commands = sequelize.define('Wo_Bot_Commands', {
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
    command: {
      type: DataTypes.STRING(64),
      allowNull: false
    },
    description: {
      type: DataTypes.STRING(256),
      allowNull: false
    },
    usage_hint: {
      type: DataTypes.STRING(256),
      allowNull: true
    },
    is_hidden: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    scope: {
      type: DataTypes.ENUM('all', 'private', 'group', 'admin'),
      allowNull: false,
      defaultValue: 'all'
    },
    sort_order: {
      type: DataTypes.INTEGER,
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
    tableName: 'Wo_Bot_Commands'
  });

  return Wo_Bot_Commands;
};
