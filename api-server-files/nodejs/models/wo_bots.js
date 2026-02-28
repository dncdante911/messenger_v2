/* jshint indent: 2 */

module.exports = function(sequelize, DataTypes) {
  const Wo_Bots = sequelize.define('Wo_Bots', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    bot_id: {
      type: DataTypes.STRING(64),
      allowNull: false,
      unique: true
    },
    owner_id: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    bot_token: {
      type: DataTypes.STRING(128),
      allowNull: false,
      unique: true
    },
    username: {
      type: DataTypes.STRING(64),
      allowNull: false,
      unique: true
    },
    display_name: {
      type: DataTypes.STRING(128),
      allowNull: false
    },
    avatar: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    about: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    bot_type: {
      type: DataTypes.ENUM('standard', 'system', 'verified'),
      allowNull: false,
      defaultValue: 'standard'
    },
    status: {
      type: DataTypes.ENUM('active', 'disabled', 'suspended', 'pending_review'),
      allowNull: false,
      defaultValue: 'active'
    },
    is_public: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    is_inline: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    can_join_groups: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    can_read_all_group_messages: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    supports_commands: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 1
    },
    category: {
      type: DataTypes.STRING(64),
      allowNull: true
    },
    tags: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    webhook_url: {
      type: DataTypes.STRING(512),
      allowNull: true
    },
    webhook_secret: {
      type: DataTypes.STRING(128),
      allowNull: true
    },
    webhook_enabled: {
      type: DataTypes.TINYINT,
      allowNull: false,
      defaultValue: 0
    },
    webhook_max_connections: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 40
    },
    webhook_allowed_updates: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    rate_limit_per_second: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 30
    },
    rate_limit_per_minute: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 1500
    },
    messages_sent: {
      type: DataTypes.BIGINT,
      allowNull: false,
      defaultValue: 0
    },
    messages_received: {
      type: DataTypes.BIGINT,
      allowNull: false,
      defaultValue: 0
    },
    total_users: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    active_users_24h: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0
    },
    created_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    updated_at: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    last_active_at: {
      type: DataTypes.DATE,
      allowNull: true
    },
    // Связанный user_id в Wo_Users — нужен для появления бота в поиске Android
    // и для маршрутизации DM-сообщений из PrivateMessageController
    linked_user_id: {
      type: DataTypes.INTEGER,
      allowNull: true
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'Wo_Bots'
  });

  return Wo_Bots;
};
