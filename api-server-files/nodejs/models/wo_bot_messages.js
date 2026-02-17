/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bot_Messages = sequelize.define('Wo_Bot_Messages', {
    id: {
      autoIncrement: true,
      type: DataTypes.BIGINT,
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
    chat_type: {
      type: DataTypes.ENUM('private', 'group'),
      allowNull: false,
      defaultValue: 'private'
    },
    direction: {
      type: DataTypes.ENUM('incoming', 'outgoing'),
      allowNull: false
    },
    message_id: {
      type: DataTypes.BIGINT,
      allowNull: true
    },
    text: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    media_type: {
      type: DataTypes.STRING(32),
      allowNull: true
    },
    media_url: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    reply_to_message_id: {
      type: DataTypes.BIGINT,
      allowNull: true
    },
    reply_markup: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    callback_data: {
      type: DataTypes.STRING(256),
      allowNull: true
    },
    entities: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    is_command: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    command_name: {
      type: DataTypes.STRING(64),
      allowNull: true
    },
    command_args: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    processed: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    processed_at: {
      type: DataTypes.DATE,
      allowNull: true
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bot_Messages'
  });

  return Wo_Bot_Messages;
};
