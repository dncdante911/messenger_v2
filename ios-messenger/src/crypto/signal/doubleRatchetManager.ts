// ============================================================
// WorldMates Messenger — Double Ratchet Manager
// TypeScript port of Android DoubleRatchetManager.kt
//
// Implements:
//   • X3DH key agreement (Alice + Bob sides)
//   • Double Ratchet encrypt / decrypt
//   • Skipped message key storage (MAX_SKIP = 500)
//
// Crypto primitives (all pure-JS, Hermes-compatible):
//   @noble/curves  — x25519 DH
//   @noble/hashes  — HKDF-SHA256, HMAC-SHA256
//   @noble/ciphers — AES-256-GCM
// ============================================================

import { x25519 } from '@noble/curves/ed25519';
import { hkdf } from '@noble/hashes/hkdf';
import { sha256 } from '@noble/hashes/sha256';
import { hmac } from '@noble/hashes/hmac';
import { gcm } from '@noble/ciphers/aes';
import { randomBytes } from '@noble/ciphers/webcrypto';

import type {
  X25519KeyPair,
  SessionState,
  DRHeader,
  EncryptedDRMessage,
} from './signalTypes';
import {
  MAX_SKIP,
  HKDF_INFO_X3DH,
  HKDF_INFO_DR_RK,
  HKDF_INFO_DR_MSG,
} from './signalTypes';

// ─────────────────────────────────────────────────────────────
// ENCODE / DECODE HELPERS
// ─────────────────────────────────────────────────────────────

function b64e(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString('base64');
}

function b64d(s: string): Uint8Array {
  return new Uint8Array(Buffer.from(s, 'base64'));
}

function hexEncode(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString('hex');
}

// ─────────────────────────────────────────────────────────────
// KEY PAIR GENERATION
// ─────────────────────────────────────────────────────────────

/** Generate a fresh X25519 key pair */
export function generateKeyPair(): X25519KeyPair {
  const privateKey = x25519.utils.randomPrivateKey();
  const publicKey = x25519.getPublicKey(privateKey);
  return { privateKey, publicKey };
}

// ─────────────────────────────────────────────────────────────
// X3DH — ALICE SIDE
// DH1 = DH(IK_A, SPK_B)
// DH2 = DH(EK_A, IK_B)
// DH3 = DH(EK_A, SPK_B)
// DH4 = DH(EK_A, OPK_B)  [optional]
// SK  = HKDF(0, DH1‖DH2‖DH3[‖DH4], "WorldMates_X3DH", 32)
// AD  = IK_A ‖ IK_B
// ─────────────────────────────────────────────────────────────

export interface X3DHResult {
  sharedSecret: Uint8Array; // 32 bytes
  associatedData: Uint8Array; // 64 bytes
}

export function x3dhAlice(
  ikA: X25519KeyPair,
  ikBPub: Uint8Array,
  spkBPub: Uint8Array,
  opkBPub: Uint8Array | null,
  ekA: X25519KeyPair,
): X3DHResult {
  const dh1 = x25519.getSharedSecret(ikA.privateKey, spkBPub);
  const dh2 = x25519.getSharedSecret(ekA.privateKey, ikBPub);
  const dh3 = x25519.getSharedSecret(ekA.privateKey, spkBPub);

  const dhConcat = opkBPub
    ? concat(dh1, dh2, dh3, x25519.getSharedSecret(ekA.privateKey, opkBPub))
    : concat(dh1, dh2, dh3);

  const salt = new Uint8Array(32); // zeros
  const sharedSecret = hkdf(sha256, dhConcat, salt, HKDF_INFO_X3DH, 32);
  const associatedData = concat(ikA.publicKey, ikBPub);

  return { sharedSecret, associatedData };
}

// ─────────────────────────────────────────────────────────────
// X3DH — BOB SIDE
// ─────────────────────────────────────────────────────────────

export function x3dhBob(
  ikB: X25519KeyPair,
  spkB: X25519KeyPair,
  opkB: X25519KeyPair | null,
  ikAPub: Uint8Array,
  ekAPub: Uint8Array,
): X3DHResult {
  const dh1 = x25519.getSharedSecret(spkB.privateKey, ikAPub);
  const dh2 = x25519.getSharedSecret(ikB.privateKey, ekAPub);
  const dh3 = x25519.getSharedSecret(spkB.privateKey, ekAPub);

  const dhConcat = opkB
    ? concat(dh1, dh2, dh3, x25519.getSharedSecret(opkB.privateKey, ekAPub))
    : concat(dh1, dh2, dh3);

  const salt = new Uint8Array(32);
  const sharedSecret = hkdf(sha256, dhConcat, salt, HKDF_INFO_X3DH, 32);
  const associatedData = concat(ikAPub, ikB.publicKey);

  return { sharedSecret, associatedData };
}

// ─────────────────────────────────────────────────────────────
// DOUBLE RATCHET — SESSION INIT
// ─────────────────────────────────────────────────────────────

/**
 * Alice (initiator) initialises a session.
 * @param sk             32-byte shared secret from X3DH
 * @param ad             64-byte associated data from X3DH
 * @param spkBPub        Bob's signed pre-key public (becomes initial dhRecvPub)
 * @param remoteUserId   For session identification
 */
export function initAliceSession(
  sk: Uint8Array,
  ad: Uint8Array,
  spkBPub: Uint8Array,
  remoteUserId: string,
): SessionState {
  const sendKP = generateKeyPair();
  const [rootKey, chainKeySend] = rkRatchet(sk, x25519.getSharedSecret(sendKP.privateKey, spkBPub));

  return {
    dhSendPriv: b64e(sendKP.privateKey),
    dhSendPub: b64e(sendKP.publicKey),
    dhRecvPub: b64e(spkBPub),
    rootKey: b64e(rootKey),
    chainKeySend: b64e(chainKeySend),
    chainKeyRecv: null,
    sendN: 0,
    recvN: 0,
    prevChainLen: 0,
    skippedKeys: {},
    associatedData: b64e(ad),
    isInitiator: true,
    remoteUserId,
  };
}

/**
 * Bob (responder) initialises a session from the X3DH result + Alice's first message header.
 */
export function initBobSession(
  sk: Uint8Array,
  ad: Uint8Array,
  spkB: X25519KeyPair,
  ekAPub: Uint8Array,
  remoteUserId: string,
): SessionState {
  return {
    dhSendPriv: b64e(spkB.privateKey),
    dhSendPub: b64e(spkB.publicKey),
    dhRecvPub: b64e(ekAPub),
    rootKey: b64e(sk),
    chainKeySend: null,
    chainKeyRecv: null,
    sendN: 0,
    recvN: 0,
    prevChainLen: 0,
    skippedKeys: {},
    associatedData: b64e(ad),
    isInitiator: false,
    remoteUserId,
  };
}

// ─────────────────────────────────────────────────────────────
// DOUBLE RATCHET — ENCRYPT
// ─────────────────────────────────────────────────────────────

export interface RatchetEncryptResult {
  newState: SessionState;
  message: EncryptedDRMessage;
}

export function ratchetEncrypt(state: SessionState, plaintext: Uint8Array): RatchetEncryptResult {
  let s = { ...state, skippedKeys: { ...state.skippedKeys } };

  // CK ratchet
  if (!s.chainKeySend) throw new Error('chainKeySend not initialised');
  const [nextCK, mk] = ckRatchet(b64d(s.chainKeySend));

  const header: DRHeader = {
    ratchetKey: s.dhSendPub,
    n: s.sendN,
    pn: s.prevChainLen,
  };

  const iv = randomBytes(12);
  const encKey = deriveAesKey(mk);
  const aad = buildAD(s.associatedData, header);
  const { ciphertext, tag } = aesGcmEncrypt(encKey, iv, plaintext, aad);

  s.chainKeySend = b64e(nextCK);
  s.sendN += 1;

  return {
    newState: s,
    message: {
      header,
      ciphertext: b64e(ciphertext),
      iv: b64e(iv),
      tag: b64e(tag),
    },
  };
}

// ─────────────────────────────────────────────────────────────
// DOUBLE RATCHET — DECRYPT
// ─────────────────────────────────────────────────────────────

export interface RatchetDecryptResult {
  newState: SessionState;
  plaintext: Uint8Array;
}

export function ratchetDecrypt(state: SessionState, msg: EncryptedDRMessage): RatchetDecryptResult {
  let s = { ...state, skippedKeys: { ...state.skippedKeys } };

  // Try skipped keys first
  const skippedResult = trySkippedKey(s, msg);
  if (skippedResult) return skippedResult;

  const ratchetPub = b64d(msg.header.ratchetKey);

  // DH ratchet step if header ratchet key differs from dhRecvPub
  if (!s.dhRecvPub || msg.header.ratchetKey !== s.dhRecvPub) {
    s = skipMessageKeys(s, msg.header.pn);
    s = dhRatchetStep(s, ratchetPub);
  }

  s = skipMessageKeys(s, msg.header.n);

  // Decrypt with current receiving chain
  if (!s.chainKeyRecv) throw new Error('chainKeyRecv not initialised');
  const [nextCK, mk] = ckRatchet(b64d(s.chainKeyRecv));

  const aad = buildAD(s.associatedData, msg.header);
  const plaintext = aesGcmDecrypt(
    deriveAesKey(mk),
    b64d(msg.iv),
    b64d(msg.ciphertext),
    b64d(msg.tag),
    aad,
  );

  s.chainKeyRecv = b64e(nextCK);
  s.recvN += 1;

  return { newState: s, plaintext };
}

// ─────────────────────────────────────────────────────────────
// INTERNAL RATCHET OPERATIONS
// ─────────────────────────────────────────────────────────────

/**
 * Root key ratchet.
 * HKDF(salt=RK, IKM=dhOutput, info="WorldMates_DR_RK", length=64)
 * → split into [newRK(32), newCK(32)]
 */
function rkRatchet(rk: Uint8Array, dhOutput: Uint8Array): [Uint8Array, Uint8Array] {
  const out = hkdf(sha256, dhOutput, rk, HKDF_INFO_DR_RK, 64);
  return [out.slice(0, 32), out.slice(32, 64)];
}

/**
 * Chain key ratchet.
 * MK      = HMAC-SHA256(CK, 0x01)
 * nextCK  = HMAC-SHA256(CK, 0x02)
 * Returns [nextCK, MK]
 */
function ckRatchet(ck: Uint8Array): [Uint8Array, Uint8Array] {
  const mk = hmac(sha256, ck, new Uint8Array([0x01]));
  const nextCK = hmac(sha256, ck, new Uint8Array([0x02]));
  return [nextCK, mk];
}

/** Derive AES-256 encryption key from message key via HKDF */
function deriveAesKey(mk: Uint8Array): Uint8Array {
  const salt = new Uint8Array(32);
  return hkdf(sha256, mk, salt, HKDF_INFO_DR_MSG, 32);
}

/**
 * Build Additional Authenticated Data:
 * AD = sessionAD ‖ encodedHeader
 * encodedHeader = ratchetKey[32] ‖ n[4 LE] ‖ pn[4 LE]
 */
function buildAD(adBase64: string, header: DRHeader): Uint8Array {
  const sessionAD = b64d(adBase64);
  const ratchetKeyBytes = b64d(header.ratchetKey);
  const encoded = new Uint8Array(40);
  encoded.set(ratchetKeyBytes.slice(0, 32), 0);
  const view = new DataView(encoded.buffer);
  view.setUint32(32, header.n, true);   // little-endian
  view.setUint32(36, header.pn, true);
  return concat(sessionAD, encoded);
}

/** Perform a DH ratchet step: advance RK, generate new send chain key */
function dhRatchetStep(state: SessionState, remotePub: Uint8Array): SessionState {
  const s = { ...state };
  const dhOutput = x25519.getSharedSecret(b64d(s.dhSendPriv), remotePub);
  const [newRK, newChainKeyRecv] = rkRatchet(b64d(s.rootKey), dhOutput);

  s.dhRecvPub = b64e(remotePub);
  s.rootKey = b64e(newRK);
  s.chainKeyRecv = b64e(newChainKeyRecv);
  s.recvN = 0;

  // Generate new send ratchet key pair
  const newSendKP = generateKeyPair();
  const dhOutput2 = x25519.getSharedSecret(newSendKP.privateKey, remotePub);
  const [newRK2, newChainKeySend] = rkRatchet(newRK, dhOutput2);

  s.prevChainLen = s.sendN;
  s.sendN = 0;
  s.dhSendPriv = b64e(newSendKP.privateKey);
  s.dhSendPub = b64e(newSendKP.publicKey);
  s.rootKey = b64e(newRK2);
  s.chainKeySend = b64e(newChainKeySend);

  return s;
}

/** Store skipped message keys up to `until` counter, respecting MAX_SKIP */
function skipMessageKeys(state: SessionState, until: number): SessionState {
  if (until <= state.recvN) return state;
  if (until - state.recvN > MAX_SKIP) {
    throw new Error(`Too many skipped messages: ${until - state.recvN}`);
  }

  const s = { ...state, skippedKeys: { ...state.skippedKeys } };
  let ck = s.chainKeyRecv ? b64d(s.chainKeyRecv) : null;

  while (s.recvN < until) {
    if (!ck) break;
    const [nextCK, mk] = ckRatchet(ck);
    const keyId = `${s.dhRecvPub ?? ''}:${s.recvN}`;
    s.skippedKeys[keyId] = b64e(mk);
    ck = nextCK;
    s.recvN += 1;
  }

  if (ck) s.chainKeyRecv = b64e(ck);
  return s;
}

/** Try decryption with a previously skipped message key */
function trySkippedKey(
  state: SessionState,
  msg: EncryptedDRMessage,
): RatchetDecryptResult | null {
  const keyId = `${msg.header.ratchetKey}:${msg.header.n}`;
  const mkB64 = state.skippedKeys[keyId];
  if (!mkB64) return null;

  const mk = b64d(mkB64);
  const aad = buildAD(state.associatedData, msg.header);
  const plaintext = aesGcmDecrypt(
    deriveAesKey(mk),
    b64d(msg.iv),
    b64d(msg.ciphertext),
    b64d(msg.tag),
    aad,
  );

  const newState = { ...state, skippedKeys: { ...state.skippedKeys } };
  delete newState.skippedKeys[keyId];

  return { newState, plaintext };
}

// ─────────────────────────────────────────────────────────────
// AES-256-GCM  (@noble/ciphers)
// ─────────────────────────────────────────────────────────────

function aesGcmEncrypt(
  key: Uint8Array,
  iv: Uint8Array,
  plaintext: Uint8Array,
  aad: Uint8Array,
): { ciphertext: Uint8Array; tag: Uint8Array } {
  const cipher = gcm(key, iv, aad);
  const encrypted = cipher.encrypt(plaintext);
  // @noble/ciphers appends the 16-byte tag at the end
  const ciphertext = encrypted.slice(0, encrypted.length - 16);
  const tag = encrypted.slice(encrypted.length - 16);
  return { ciphertext, tag };
}

function aesGcmDecrypt(
  key: Uint8Array,
  iv: Uint8Array,
  ciphertext: Uint8Array,
  tag: Uint8Array,
  aad: Uint8Array,
): Uint8Array {
  const cipher = gcm(key, iv, aad);
  // @noble/ciphers expects ciphertext+tag concatenated
  const combined = concat(ciphertext, tag);
  return cipher.decrypt(combined);
}

// ─────────────────────────────────────────────────────────────
// UTILITY
// ─────────────────────────────────────────────────────────────

function concat(...arrays: Uint8Array[]): Uint8Array {
  const total = arrays.reduce((n, a) => n + a.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const a of arrays) {
    out.set(a, offset);
    offset += a.length;
  }
  return out;
}

export { b64e, b64d, hexEncode };
