/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_RSS_Feeds = sequelize.define('Wo_Bot_RSS_Feeds', {
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
    chat_id: {
      type: DataTypes.STRING(64),
      allowNull: false
    },
    feed_url: {
      type: DataTypes.STRING(512),
      allowNull: false
    },
    feed_name: {
      type: DataTypes.STRING(256),
      allowNull: true
    },
    feed_language: {
      type: DataTypes.STRING(10),
      allowNull: true,
      defaultValue: 'en'
    },
    is_active: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    check_interval_minutes: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 30
    },
    last_check_at: {
      type: DataTypes.DATE,
      allowNull: true
    },
    last_item_hash: {
      type: DataTypes.STRING(64),
      allowNull: true
    },
    items_posted: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    max_items_per_check: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 5
    },
    include_image: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    include_description: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_RSS_Feeds'
  });

  return Wo_Bot_RSS_Feeds;
};
