/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_GroupChat', {
    group_id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    group_name: {
      type: DataTypes.STRING(255),
      allowNull: false,
      defaultValue: ""
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    is_private: {
      type: DataTypes.ENUM('0','1'),
      allowNull: false,
      defaultValue: "0"
    },
    settings: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    avatar: {
      type: DataTypes.STRING(3000),
      allowNull: false,
      defaultValue: "upload/photos/d-group.jpg"
    },
    time: {
      type: DataTypes.STRING(30),
      allowNull: false,
      defaultValue: ""
    },
    type: {
      type: DataTypes.STRING(50),
      allowNull: false,
      defaultValue: "group"
    },
    destruct_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_GroupChat'
  });
};