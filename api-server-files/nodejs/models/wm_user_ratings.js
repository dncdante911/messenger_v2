/* jshint indent: 2 */

/**
 * wm_user_ratings — система карми/рейтингу користувачів.
 *
 * Унікальний constraint (rater_id, rated_user_id) — один юзер може поставити
 * лише одну оцінку іншому. При повторному vote запис оновлюється або видаляється.
 */
module.exports = function(sequelize, DataTypes) {
  return sequelize.define('wm_user_ratings', {
    id: {
      autoIncrement: true,
      type: DataTypes.INTEGER,
      allowNull: false,
      primaryKey: true
    },
    rater_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      comment: 'user_id того хто голосує'
    },
    rated_user_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      comment: 'user_id кого оцінюють'
    },
    rating_type: {
      type: DataTypes.ENUM('like', 'dislike'),
      allowNull: false
    },
    comment: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    created_at: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0,
      comment: 'Unix timestamp'
    },
    updated_at: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0,
      comment: 'Unix timestamp'
    }
  }, {
    sequelize,
    timestamps: false,
    tableName: 'wm_user_ratings',
    indexes: [
      {
        unique: true,
        fields: ['rater_id', 'rated_user_id'],
        name: 'unique_rating'
      },
      {
        fields: ['rated_user_id'],
        name: 'idx_rated_user'
      }
    ]
  });
};
