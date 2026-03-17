/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wm_Business_Links', {
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
    title: {
      type: DataTypes.STRING(100),
      allowNull: false
    },
    prefilled_text: {
      type: DataTypes.TEXT,
      allowNull: true,
      defaultValue: null
    },
    slug: {
      type: DataTypes.STRING(64),
      allowNull: false,
      comment: 'unique URL slug'
    },
    views: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    created_at: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_business_links',
    indexes: [
      { unique: true, fields: ['slug'] },
      { fields: ['user_id'] }
    ]
  });
};
