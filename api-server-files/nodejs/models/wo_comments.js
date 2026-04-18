/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_Comments', {
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
    page_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    post_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    text: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    record: {
      type: DataTypes.STRING(255),
      allowNull: false,
      defaultValue: ""
    },
    c_file: {
      type: DataTypes.STRING(255),
      allowNull: false,
      defaultValue: ""
    },
    time: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    written_as_channel: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    write_as_mode: {
      type: DataTypes.STRING(30),
      allowNull: true,
      defaultValue: null
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Comments'
  });
};
