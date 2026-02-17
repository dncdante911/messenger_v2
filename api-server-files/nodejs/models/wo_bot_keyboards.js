/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Keyboards = sequelize.define('Wo_Bot_Keyboards', {
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
    name: {
      type: DataTypes.STRING(128),
      allowNull: false
    },
    keyboard_type: {
      type: DataTypes.ENUM('inline', 'reply', 'remove'),
      allowNull: false,
      defaultValue: 'inline'
    },
    keyboard_data: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    is_persistent: {
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
    tableName: 'Wo_Bot_Keyboards'
  });

  return Wo_Bot_Keyboards;
};
