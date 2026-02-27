/* jshint indent: 2 */

// Real Wo_Reactions table schema (from DB dump):
//   id, user_id, post_id, comment_id, replay_id, message_id, story_id, reaction
// Note: NO 'time' column, NO 'reply_id' (it's 'replay_id'), HAS 'story_id'

module.exports = function(sequelize, DataTypes) {
  return sequelize.define('Wo_Reactions', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    user_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: false,
      defaultValue: 0
    },
    post_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: true,
      defaultValue: 0
    },
    comment_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: true,
      defaultValue: 0
    },
    replay_id: {
      type: DataTypes.INTEGER.UNSIGNED,
      allowNull: true,
      defaultValue: 0
    },
    message_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    story_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    reaction: {
      type: DataTypes.STRING(50),
      allowNull: true,
      defaultValue: null
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Reactions'
  });
};
