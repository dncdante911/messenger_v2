'use strict';

/**
 * wm_key_backups — E2EE key backup storage
 *
 * Stores AES-256-GCM encrypted key material per user.
 * The server NEVER sees plaintext keys — all encryption/decryption
 * happens on the client device using a password-derived key (PBKDF2).
 *
 * Schema:
 *   user_id           — one backup per user (UNIQUE)
 *   encrypted_payload — AES-256-GCM ciphertext (base64), includes GCM auth tag
 *   salt              — PBKDF2 salt (base64, 32 bytes)
 *   iv                — AES-GCM IV (base64, 12 bytes)
 *   version           — backup format version (currently 1)
 *   created_at / updated_at — unix timestamps
 */
module.exports = (sequelize, DataTypes) =>
    sequelize.define('wm_key_backups', {
        id: {
            type:          DataTypes.INTEGER.UNSIGNED,
            primaryKey:    true,
            autoIncrement: true,
        },
        user_id: {
            type:      DataTypes.INTEGER,
            allowNull: false,
        },
        encrypted_payload: {
            type:      DataTypes.TEXT('long'),
            allowNull: false,
        },
        salt: {
            type:      DataTypes.STRING(64),
            allowNull: false,
        },
        iv: {
            type:      DataTypes.STRING(32),
            allowNull: false,
        },
        version: {
            type:         DataTypes.INTEGER,
            allowNull:    false,
            defaultValue: 1,
        },
        created_at: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0,
        },
        updated_at: {
            type:         DataTypes.INTEGER.UNSIGNED,
            allowNull:    false,
            defaultValue: 0,
        },
    }, {
        tableName:  'wm_key_backups',
        timestamps: false,
        indexes: [
            { unique: true, fields: ['user_id'] },
        ],
    });
