// ============================================================
// WorldMates Messenger — Signal Protocol Types
// TypeScript port of Android SignalModels.kt
// ============================================================

// ─────────────────────────────────────────────────────────────
// KEY PAIR
// ─────────────────────────────────────────────────────────────

/** X25519 key pair — 32-byte private + 32-byte public */
export interface X25519KeyPair {
  privateKey: Uint8Array; // 32 bytes
  publicKey: Uint8Array;  // 32 bytes
}

// ─────────────────────────────────────────────────────────────
// DOUBLE RATCHET SESSION STATE
// Mirrors Android SessionState (serialised to SecureStore as JSON)
// ─────────────────────────────────────────────────────────────

export interface SessionState {
  /** Current DH send key pair (private + public), base64-encoded */
  dhSendPriv: string;
  dhSendPub: string;
  /** Remote DH ratchet public key (base64), null before first message received */
  dhRecvPub: string | null;
  /** Root key (32 bytes, base64) */
  rootKey: string;
  /** Sending chain key (32 bytes, base64), null when not yet initialised */
  chainKeySend: string | null;
  /** Receiving chain key (32 bytes, base64), null when not yet initialised */
  chainKeyRecv: string | null;
  /** Message counter for current sending chain */
  sendN: number;
  /** Message counter for current receiving chain */
  recvN: number;
  /** Previous sending chain length (for header pn field) */
  prevChainLen: number;
  /**
   * Skipped message keys.
   * Key format: "<ratchetPubKeyHex>:<n>"
   * Value: base64-encoded 32-byte message key
   */
  skippedKeys: Record<string, string>;
  /**
   * Associated data (64 bytes, base64): IK_A ‖ IK_B
   * Prepended to every message's AAD.
   */
  associatedData: string;
  isInitiator: boolean;
  remoteUserId: string;
}

// ─────────────────────────────────────────────────────────────
// DOUBLE RATCHET HEADER
// ─────────────────────────────────────────────────────────────

/**
 * Per-message header transmitted in plaintext.
 * First message in a session also carries X3DH fields (ik / ek / opk_id).
 */
export interface DRHeader {
  /** Sender's current DH ratchet public key (base64) */
  ratchetKey: string;
  /** Message counter in current chain */
  n: number;
  /** Previous chain length */
  pn: number;
  // ── X3DH fields — only on the first message ──────────────
  /** Sender identity key (base64) — present on first message only */
  identityKey?: string;
  /** Sender ephemeral key (base64) — present on first message only */
  ephemeralKey?: string;
  /** OPK id consumed — present on first message when OPK was used */
  oneTimePreKeyId?: number;
}

// ─────────────────────────────────────────────────────────────
// ENCRYPTED DR MESSAGE
// ─────────────────────────────────────────────────────────────

export interface EncryptedDRMessage {
  header: DRHeader;
  /** AES-256-GCM ciphertext (base64) */
  ciphertext: string;
  /** 12-byte GCM nonce (base64) */
  iv: string;
  /** 16-byte GCM auth tag (base64) */
  tag: string;
}

// ─────────────────────────────────────────────────────────────
// PRE-KEY BUNDLE  (received from server when initiating session)
// Mirrors Android PreKeyBundle
// ─────────────────────────────────────────────────────────────

export interface PreKeyBundle {
  /** Remote identity public key (base64) */
  identityKey: string;
  signedPreKeyId: number;
  /** Remote signed pre-key public (base64) */
  signedPreKey: string;
  /** Ed25519 signature over signedPreKey by identityKey (base64) */
  signedPreKeySig: string;
  oneTimePreKeyId?: number;
  /** Remote one-time pre-key public (base64) */
  oneTimePreKey?: string;
}

// ─────────────────────────────────────────────────────────────
// SIGNAL REGISTRATION REQUEST  (sent to POST /api/node/signal/register)
// Mirrors Android SignalRegisterRequest
// ─────────────────────────────────────────────────────────────

export interface SignalRegisterRequest {
  /** Own identity public key (base64) */
  identity_key: string;
  signed_prekey_id: number;
  /** Own signed pre-key public (base64) */
  signed_prekey: string;
  /** Ed25519 signature (base64) */
  signed_prekey_sig: string;
  /** Array of one-time pre-key objects {id, public_key} */
  prekeys: Array<{ id: number; public_key: string }>;
}

// ─────────────────────────────────────────────────────────────
// PRE-KEY BUNDLE RESPONSE  (GET /api/node/signal/bundle/:userId)
// ─────────────────────────────────────────────────────────────

export interface PreKeyBundleResponse {
  api_status: string | number;
  user_id: string | number;
  identity_key: string;
  signed_prekey_id: number;
  signed_prekey: string;
  signed_prekey_sig: string;
  one_time_prekey_id?: number;
  one_time_prekey?: string;
  remaining_prekeys?: number;
}

// ─────────────────────────────────────────────────────────────
// IDENTITY RESPONSE  (GET /api/node/signal/identity/:userId)
// Used for stale-session detection without consuming an OPK
// ─────────────────────────────────────────────────────────────

export interface IdentityKeyResponse {
  api_status: string | number;
  user_id: string | number;
  identity_key: string;
}

// ─────────────────────────────────────────────────────────────
// OUTGOING SIGNAL PAYLOAD  (included in chat/send request)
// ─────────────────────────────────────────────────────────────

export interface SignalOutgoingPayload {
  /** AES-256-GCM ciphertext (base64) */
  ciphertext: string;
  /** 12-byte GCM nonce (base64) */
  iv: string;
  /** 16-byte GCM auth tag (base64) */
  tag: string;
  /** JSON string of DRHeader */
  signalHeader: string;
}

// ─────────────────────────────────────────────────────────────
// STORED OPK  (one-time pre-key kept in SecureStore)
// ─────────────────────────────────────────────────────────────

export interface StoredOPK {
  id: number;
  /** private key (base64) */
  priv: string;
  /** public key (base64) */
  pub: string;
}

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────

export const MAX_SKIP = 500;
export const OPK_BATCH_SIZE = 100;
export const OPK_REPLENISH_THRESHOLD = 20;
export const CIPHER_VERSION_SIGNAL = 3;

export const HKDF_INFO_X3DH = 'WorldMates_X3DH';
export const HKDF_INFO_DR_RK = 'WorldMates_DR_RK';
export const HKDF_INFO_DR_MSG = 'WorldMates_DR_MSG';
