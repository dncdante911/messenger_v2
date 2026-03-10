/**
 * wm_notes — Telegram-style Saved Messages / Notes
 *
 * SQL migration (run once):
 *
 *   CREATE TABLE IF NOT EXISTS wm_notes (
 *     id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 *     user_id     INT             NOT NULL,
 *     type        ENUM('text','file','image','video','audio') NOT NULL DEFAULT 'text',
 *     text        LONGTEXT        DEFAULT NULL,
 *     file_name   VARCHAR(255)    DEFAULT NULL,
 *     file_path   VARCHAR(512)    DEFAULT NULL,
 *     file_size   BIGINT          DEFAULT 0,
 *     mime_type   VARCHAR(64)     DEFAULT NULL,
 *     created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     PRIMARY KEY (id),
 *     INDEX idx_user_created (user_id, created_at)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */

module.exports = (sequelize, DataTypes) => {
    const WmNotes = sequelize.define('wm_notes', {
        id:         { type: DataTypes.BIGINT.UNSIGNED, primaryKey: true, autoIncrement: true },
        user_id:    { type: DataTypes.INTEGER, allowNull: false },
        type:       { type: DataTypes.ENUM('text', 'file', 'image', 'video', 'audio'), defaultValue: 'text' },
        text:       { type: DataTypes.TEXT('long'), defaultValue: null },
        file_name:  { type: DataTypes.STRING(255), defaultValue: null },
        file_path:  { type: DataTypes.STRING(512), defaultValue: null },
        file_size:  { type: DataTypes.BIGINT, defaultValue: 0 },
        mime_type:  { type: DataTypes.STRING(64), defaultValue: null },
        created_at: { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
    }, {
        tableName:  'wm_notes',
        timestamps: false,
        indexes:    [{ fields: ['user_id', 'created_at'] }],
    });
    return WmNotes;
};
