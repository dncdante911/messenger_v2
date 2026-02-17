/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Api_Keys = sequelize.define('Wo_Bot_Api_Keys', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    api_key: {
      type: DataTypes.STRING(128),
      allowNull: false,
      unique: true
    },
    api_secret: {
      type: DataTypes.STRING(128),
      allowNull: false
    },
    app_name: {
      type: DataTypes.STRING(256),
      allowNull: false
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    permissions: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    is_active: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    rate_limit_per_minute: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 60
    },
    total_requests: {
      type: DataTypes.BIGINT,
      allowNull: false,
      defaultValue: 0
    },
    last_used_at: {
      type: DataTypes.DATE,
      allowNull: true
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    expires_at: {
      type: DataTypes.DATE,
      allowNull: true
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Api_Keys'
  });

  return Wo_Bot_Api_Keys;
};
