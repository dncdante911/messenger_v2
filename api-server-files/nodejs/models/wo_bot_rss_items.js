/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_RSS_Items = sequelize.define('Wo_Bot_RSS_Items', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    feed_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    item_hash: {
      type: DataTypes.STRING(64),
      allowNull: false
    },
    title: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    link: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    posted_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_RSS_Items'
  });

  return Wo_Bot_RSS_Items;
};
