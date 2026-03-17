/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_AppsSessions', {
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
    session_id: {
      type: DataTypes.STRING(120),
      allowNull: false,
      defaultValue: "",
      unique: "session_id"
    },
    platform: {
      type: DataTypes.STRING(32),
      allowNull: false,
      defaultValue: ""
    },
    platform_details: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    time: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    expires_at: {
      type: DataTypes.INTEGER,
      allowNull: true,
      defaultValue: null
    },
    refresh_token: {
      type: DataTypes.STRING(120),
      allowNull: true,
      defaultValue: null
    },
    refresh_expires_at: {
      type: DataTypes.INTEGER,
      allowNull: true,
      defaultValue: null
    },
    ip_address: {
      type: DataTypes.STRING(64),
      allowNull: false,
      defaultValue: ''
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_AppsSessions'
  });
};
