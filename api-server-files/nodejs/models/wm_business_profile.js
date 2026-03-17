/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wm_Business_Profile', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    business_name: {
      type: DataTypes.STRING(100),
      allowNull: true,
      defaultValue: null
    },
    category: {
      type: DataTypes.STRING(100),
      allowNull: true,
      defaultValue: null
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    address: {
      type: DataTypes.STRING(255),
      allowNull: true,
      defaultValue: null
    },
    lat: {
      type: DataTypes.DECIMAL(10, 7),
      allowNull: true,
      defaultValue: null
    },
    lng: {
      type: DataTypes.DECIMAL(10, 7),
      allowNull: true,
      defaultValue: null
    },
    phone: {
      type: DataTypes.STRING(30),
      allowNull: true,
      defaultValue: null
    },
    email: {
      type: DataTypes.STRING(100),
      allowNull: true,
      defaultValue: null
    },
    website: {
      type: DataTypes.STRING(255),
      allowNull: true,
      defaultValue: null
    },
    auto_reply_enabled: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      defaultValue: 0
    },
    auto_reply_text: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    auto_reply_mode: {
      type: DataTypes.ENUM('always', 'outside_hours', 'away'),
      allowNull: false,
      defaultValue: 'always'
    },
    greeting_enabled: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      defaultValue: 0
    },
    greeting_text: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    away_enabled: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      defaultValue: 0
    },
    away_text: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    badge_enabled: {
      type: DataTypes.TINYINT(1),
      allowNull: false,
      defaultValue: 1
    },
    created_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    updated_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_business_profile',
    indexes: [
      { unique: true, fields: ['user_id'] }
    ]
  });
};
