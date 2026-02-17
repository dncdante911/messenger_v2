/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Polls = sequelize.define('Wo_Bot_Polls', {
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
    question: {
      type: DataTypes.STRING(512),
      allowNull: false
    },
    poll_type: {
      type: DataTypes.ENUM('regular', 'quiz'),
      allowNull: false,
      defaultValue: 'regular'
    },
    is_anonymous: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    allows_multiple_answers: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    correct_option_id: {
      type: DataTypes.INTEGER,
      allowNull: true
    },
    explanation: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    is_closed: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    close_date: {
      type: DataTypes.DATE,
      allowNull: true
    },
    total_voters: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    message_id: {
      type: DataTypes.BIGINT,
      allowNull: true
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    closed_at: {
      type: DataTypes.DATE,
      allowNull: true
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Polls'
  });

  return Wo_Bot_Polls;
};
