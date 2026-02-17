/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Poll_Options = sequelize.define('Wo_Bot_Poll_Options', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    poll_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    option_text: {
      type: DataTypes.STRING(256),
      allowNull: false
    },
    option_index: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    voter_count: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Poll_Options'
  });

  return Wo_Bot_Poll_Options;
};
