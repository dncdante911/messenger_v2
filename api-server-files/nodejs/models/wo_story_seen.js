/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_Story_Seen', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    story_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    time: {
      type: DataTypes.STRING(20),
      allowNull: false,
      defaultValue: "0"
    },
    // SHA-256 хеш IP+UA для анонімних переглядів (user_id = 0)
    anonymous_hash: {
      type: DataTypes.STRING(64),
      allowNull: true,
      defaultValue: null
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Story_Seen'
  });
};
