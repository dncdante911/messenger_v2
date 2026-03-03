/* jshint indent: 2 */

/**
 * Sequelize model for signal_keys table.
 *
 * Stores public key material for Signal Protocol (X3DH pre-key server).
 * Private keys NEVER reach the server — they are generated and stored on devices.
 *
 * Columns:
 *   user_id          — FK to wo_users
 *   identity_key     — Base64 X25519 identity public key (32 bytes → 44 Base64 chars)
 *   signed_prekey_id — integer ID of current signed pre-key
 *   signed_prekey    — Base64 X25519 signed pre-key public (32 bytes)
 *   signed_prekey_sig— Base64 Ed25519 signature over signed_prekey (64 bytes)
 *   prekeys          — JSON array: [{id:int, key:string}, ...] one-time pre-keys
 *   updated_at       — last update timestamp
 */
module.exports = function(sequelize, DataTypes) {
  return sequelize.define('signal_keys', {
    user_id: {
      type:       DataTypes.INTEGER.UNSIGNED,
      allowNull:  false,
      primaryKey: true,
    },
    identity_key: {
      type:       DataTypes.STRING(64),
      allowNull:  false,
      defaultValue: '',
      comment:    'Base64 X25519 identity public key (32 bytes)',
    },
    signed_prekey_id: {
      type:       DataTypes.INTEGER.UNSIGNED,
      allowNull:  false,
      defaultValue: 0,
      comment:    'Signed pre-key ID',
    },
    signed_prekey: {
      type:       DataTypes.STRING(64),
      allowNull:  false,
      defaultValue: '',
      comment:    'Base64 X25519 signed pre-key public (32 bytes)',
    },
    signed_prekey_sig: {
      type:       DataTypes.STRING(128),
      allowNull:  false,
      defaultValue: '',
      comment:    'Base64 Ed25519 signature (64 bytes)',
    },
    prekeys: {
      type:       DataTypes.TEXT,
      allowNull:  true,
      defaultValue: '[]',
      comment:    'JSON array of one-time pre-keys: [{id, key}]',
      get() {
        const raw = this.getDataValue('prekeys');
        if (!raw) return [];
        try { return JSON.parse(raw); } catch { return []; }
      },
      set(value) {
        this.setDataValue('prekeys',
          typeof value === 'string' ? value : JSON.stringify(value));
      },
    },
    updated_at: {
      type:       DataTypes.DATE,
      allowNull:  false,
      defaultValue: DataTypes.NOW,
    },
  }, {
    sequelize,
    tableName:  'signal_keys',
    timestamps: false,
  });
};
