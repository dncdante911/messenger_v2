/**
 * wm_user_storage — Tracks uploaded file storage per user (used by Notes)
 *
 * SQL migration (run once):
 *
 *   CREATE TABLE IF NOT EXISTS wm_user_storage (
 *     user_id     INT     NOT NULL,
 *     used_bytes  BIGINT  NOT NULL DEFAULT 0,
 *     updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *     PRIMARY KEY (user_id)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */

module.exports = (sequelize, DataTypes) => {
    const WmUserStorage = sequelize.define('wm_user_storage', {
        user_id:    { type: DataTypes.INTEGER, primaryKey: true },
        used_bytes: { type: DataTypes.BIGINT, defaultValue: 0 },
        updated_at: { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
    }, {
        tableName:  'wm_user_storage',
        timestamps: false,
    });
    return WmUserStorage;
};
