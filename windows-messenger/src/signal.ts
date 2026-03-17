/**
 * Signal Protocol (Double Ratchet Algorithm) — TypeScript port of DoubleRatchetManager.kt
 *
 * Cryptographic primitives:
 *   ► X25519   — key agreement       (@noble/curves/x25519 — pure JS, works in Electron 33)
 *   ► HKDF-SHA256 — key derivation   (crypto.subtle, deriveBits)
 *   ► HMAC-SHA256 — chain-key ratchet (crypto.subtle, sign)
 *   ► AES-256-GCM — symmetric encryption (crypto.subtle, encrypt / decrypt)
 *
 * Wire format is 100 % identical to the Android/Kotlin client so sessions
 * initiated on Windows can be decrypted on Android and vice-versa.
 *
 * Key format:  raw 32-byte little-endian X25519 scalars (RFC 7748).
 * Storage:     base64url strings (no padding), JSON-serialisable SessionState.
 */

import { x25519 } from '@noble/curves/ed25519.js';

// ─── HKDF / HMAC info labels (must match Kotlin) ─────────────────────────────
const _enc = new TextEncoder();
const INFO_RK   = _enc.encode('WorldMates_DR_RK');
const INFO_X3DH = _enc.encode('WorldMates_X3DH');
const INFO_MSG  = _enc.encode('WorldMates_DR_MSG');
const ZERO_SALT = new Uint8Array(32);
const MAX_SKIP  = 500;

// ─── Public types ─────────────────────────────────────────────────────────────

export interface X25519KeyPair {
  privateKeyRaw: Uint8Array; // 32 bytes — raw scalar
  publicKeyRaw:  Uint8Array; // 32 bytes — Montgomery u-coordinate
}

/** Complete Double Ratchet session state — JSON-serialisable. */
export interface SessionState {
  dhSendPriv:     string;           // Base64 — current DH send private key
  dhSendPub:      string;           // Base64 — current DH send public  key
  dhRecvPub:      string | null;    // Base64 — current DH receive public key
  rootKey:        string;           // Base64 — 32-byte root key
  chainKeySend:   string | null;    // Base64 — current send chain key
  chainKeyRecv:   string | null;    // Base64 — current receive chain key
  sendN:          number;
  recvN:          number;
  prevChainLen:   number;
  skippedKeys:    Record<string, string>; // "<ratchet_pub_hex>:<n>" → Base64(MK)
  associatedData: string;           // Base64 — session AD (IK_A || IK_B, 64B)
  isInitiator:    boolean;
  remoteUserId:   number;
}

export interface DRHeader {
  ratchetKey: Uint8Array; // 32 bytes
  n:          number;
  pn:         number;
}

export interface EncryptedDRMessage {
  header:     DRHeader;
  ciphertext: Uint8Array; // AES-256-GCM ciphertext (without tag)
  iv:         Uint8Array; // 12-byte nonce
  tag:        Uint8Array; // 16-byte GCM authentication tag
}

/** Payload shape for POST /api/node/chat/send (cipher_version=3). */
export interface SignalOutgoingPayload {
  ciphertext:   string; // Base64
  iv:           string; // Base64
  tag:          string; // Base64
  signalHeader: string; // JSON string
}

// ─── X25519 key generation ────────────────────────────────────────────────────

export function generateKeyPair(): X25519KeyPair {
  const kp = x25519.keygen();
  return { privateKeyRaw: kp.secretKey, publicKeyRaw: kp.publicKey };
}

// ─── X25519 DH ────────────────────────────────────────────────────────────────

function dhRaw(privateKeyRaw: Uint8Array, publicKeyRaw: Uint8Array): Uint8Array {
  return x25519.getSharedSecret(privateKeyRaw, publicKeyRaw);
}

// ─── HKDF-SHA256 ─────────────────────────────────────────────────────────────

async function hkdf(salt: Uint8Array, ikm: Uint8Array, info: Uint8Array, length: number): Promise<Uint8Array> {
  const material = await crypto.subtle.importKey('raw', toAB(ikm), 'HKDF', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'HKDF', salt: toAB(salt), info: toAB(info), hash: 'SHA-256' },
    material, length * 8
  );
  return new Uint8Array(bits);
}

// ─── HMAC-SHA256 ──────────────────────────────────────────────────────────────

async function hmacSHA256(key: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
  const cryptoKey = await crypto.subtle.importKey(
    'raw', toAB(key),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', cryptoKey, toAB(data));
  return new Uint8Array(sig);
}

// ─── Ratchet sub-operations ───────────────────────────────────────────────────

/** KDF_RK: HKDF(RK, DH_out, "WorldMates_DR_RK", 64) → (newRK, newCK) */
async function rkRatchet(rk: Uint8Array, dhOutput: Uint8Array): Promise<[Uint8Array, Uint8Array]> {
  const out = await hkdf(rk, dhOutput, INFO_RK, 64);
  return [out.slice(0, 32), out.slice(32, 64)];
}

/** KDF_CK: messageKey = HMAC(CK, 0x01), nextCK = HMAC(CK, 0x02) */
async function ckRatchet(ck: Uint8Array): Promise<[Uint8Array, Uint8Array]> {
  const [mk, nextCk] = await Promise.all([
    hmacSHA256(ck, new Uint8Array([0x01])),
    hmacSHA256(ck, new Uint8Array([0x02]))
  ]);
  return [nextCk, mk];
}

/** Derive 32-byte AES key from message key (key separation). */
async function deriveAesKey(mk: Uint8Array): Promise<Uint8Array> {
  return hkdf(new Uint8Array(32), mk, INFO_MSG, 32);
}

// ─── AES-256-GCM ─────────────────────────────────────────────────────────────

async function encryptWithMK(
  mk:        Uint8Array,
  plaintext: Uint8Array,
  ad:        Uint8Array
): Promise<{ ciphertext: Uint8Array; iv: Uint8Array; tag: Uint8Array }> {
  const aesKeyBytes = await deriveAesKey(mk);
  const cryptoKey   = await crypto.subtle.importKey('raw', toAB(aesKeyBytes), 'AES-GCM', false, ['encrypt']);
  const iv          = crypto.getRandomValues(new Uint8Array(12));

  const result = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: toAB(iv), additionalData: toAB(ad), tagLength: 128 },
    cryptoKey, toAB(plaintext)
  );
  const buf = new Uint8Array(result);
  return {
    ciphertext: buf.slice(0, buf.length - 16),
    iv,
    tag: buf.slice(buf.length - 16)
  };
}

async function decryptWithMK(
  mk:         Uint8Array,
  ciphertext: Uint8Array,
  iv:         Uint8Array,
  tag:        Uint8Array,
  ad:         Uint8Array
): Promise<Uint8Array> {
  const aesKeyBytes = await deriveAesKey(mk);
  const cryptoKey   = await crypto.subtle.importKey('raw', toAB(aesKeyBytes), 'AES-GCM', false, ['decrypt']);

  // Web Crypto expects ciphertext‖tag in a single buffer
  const input = new Uint8Array(ciphertext.length + tag.length);
  input.set(ciphertext);
  input.set(tag, ciphertext.length);

  const result = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: toAB(iv), additionalData: toAB(ad), tagLength: 128 },
    cryptoKey, toAB(input)
  );
  return new Uint8Array(result);
}

// ─── Associated-data helpers ──────────────────────────────────────────────────

/** buildAD: session_AD (64B) ‖ encode(header) (40B) */
function buildAD(associatedDataB64: string, header: DRHeader): Uint8Array {
  const sessionAD = associatedDataB64 ? b64Decode(associatedDataB64) : new Uint8Array(0);
  const headerBytes = encodeHeader(header);
  const ad = new Uint8Array(sessionAD.length + headerBytes.length);
  ad.set(sessionAD);
  ad.set(headerBytes, sessionAD.length);
  return ad;
}

/** Binary header encoding: ratchetKey[32] ‖ n_LE[4] ‖ pn_LE[4] = 40 bytes */
function encodeHeader(h: DRHeader): Uint8Array {
  const buf  = new Uint8Array(40);
  const view = new DataView(buf.buffer);
  buf.set(h.ratchetKey, 0);
  view.setUint32(32, h.n,  true); // LE
  view.setUint32(36, h.pn, true); // LE
  return buf;
}

/** Skipped-message map key: "<ratchetPubHex>:<n>" */
function skippedKeyId(ratchetPub: Uint8Array, n: number): string {
  const hex = Array.from(ratchetPub).map(b => b.toString(16).padStart(2, '0')).join('');
  return `${hex}:${n}`;
}

// ─── X3DH ─────────────────────────────────────────────────────────────────────

/**
 * Alice (initiator) computes X3DH shared secret.
 *
 * DH1 = DH(IK_A, SPK_B)
 * DH2 = DH(EK_A, IK_B)
 * DH3 = DH(EK_A, SPK_B)
 * DH4 = DH(EK_A, OPK_B)  [optional]
 * SK  = HKDF(0, DH1‖DH2‖DH3[‖DH4], "WorldMates_X3DH", 32)
 * AD  = IK_A.pub ‖ IK_B.pub
 */
export async function x3dhAlice(
  ikA:     X25519KeyPair,
  ikBPub:  Uint8Array,
  spkBPub: Uint8Array,
  opkBPub: Uint8Array | null,
  ekA:     X25519KeyPair
): Promise<[Uint8Array, Uint8Array]> {
  const dh1 = dhRaw(ikA.privateKeyRaw, spkBPub);
  const dh2 = dhRaw(ekA.privateKeyRaw, ikBPub);
  const dh3 = dhRaw(ekA.privateKeyRaw, spkBPub);

  let dhInput: Uint8Array;
  if (opkBPub) {
    const dh4 = dhRaw(ekA.privateKeyRaw, opkBPub);
    dhInput = concat(dh1, dh2, dh3, dh4);
  } else {
    dhInput = concat(dh1, dh2, dh3);
  }

  const sk = await hkdf(ZERO_SALT, dhInput, INFO_X3DH, 32);
  const ad = concat(ikA.publicKeyRaw, ikBPub);
  return [sk, ad];
}

/**
 * Bob (responder) recomputes X3DH shared secret.
 *
 * DH1 = DH(SPK_B, IK_A)
 * DH2 = DH(IK_B,  EK_A)
 * DH3 = DH(SPK_B, EK_A)
 * DH4 = DH(OPK_B, EK_A)  [optional]
 */
export async function x3dhBob(
  ikB:    X25519KeyPair,
  spkB:   X25519KeyPair,
  opkB:   X25519KeyPair | null,
  ikAPub: Uint8Array,
  ekAPub: Uint8Array
): Promise<[Uint8Array, Uint8Array]> {
  const dh1 = dhRaw(spkB.privateKeyRaw, ikAPub);
  const dh2 = dhRaw(ikB.privateKeyRaw,  ekAPub);
  const dh3 = dhRaw(spkB.privateKeyRaw, ekAPub);

  let dhInput: Uint8Array;
  if (opkB) {
    const dh4 = dhRaw(opkB.privateKeyRaw, ekAPub);
    dhInput = concat(dh1, dh2, dh3, dh4);
  } else {
    dhInput = concat(dh1, dh2, dh3);
  }

  const sk = await hkdf(ZERO_SALT, dhInput, INFO_X3DH, 32);
  const ad = concat(ikAPub, ikB.publicKeyRaw); // same order as Alice
  return [sk, ad];
}

// ─── Session initialisation ───────────────────────────────────────────────────

export async function initAliceSession(
  sk:           Uint8Array,
  ad:           Uint8Array,
  spkBPub:      Uint8Array,
  remoteUserId: number
): Promise<SessionState> {
  const dhs = generateKeyPair();
  const [rk, cks] = await rkRatchet(sk, dhRaw(dhs.privateKeyRaw, spkBPub));

  return {
    dhSendPriv:     b64Encode(dhs.privateKeyRaw),
    dhSendPub:      b64Encode(dhs.publicKeyRaw),
    dhRecvPub:      b64Encode(spkBPub),
    rootKey:        b64Encode(rk),
    chainKeySend:   b64Encode(cks),
    chainKeyRecv:   null,
    sendN:          0,
    recvN:          0,
    prevChainLen:   0,
    skippedKeys:    {},
    associatedData: b64Encode(ad),
    isInitiator:    true,
    remoteUserId
  };
}

export function initBobSession(
  sk:           Uint8Array,
  ad:           Uint8Array,
  spkB:         X25519KeyPair,
  ekAPub:       Uint8Array,
  remoteUserId: number
): SessionState {
  return {
    dhSendPriv:     b64Encode(spkB.privateKeyRaw),
    dhSendPub:      b64Encode(spkB.publicKeyRaw),
    dhRecvPub:      b64Encode(ekAPub),
    rootKey:        b64Encode(sk),
    chainKeySend:   null,
    chainKeyRecv:   null,
    sendN:          0,
    recvN:          0,
    prevChainLen:   0,
    skippedKeys:    {},
    associatedData: b64Encode(ad),
    isInitiator:    false,
    remoteUserId
  };
}

// ─── Double Ratchet encrypt ───────────────────────────────────────────────────

export async function ratchetEncrypt(
  state:     SessionState,
  plaintext: Uint8Array
): Promise<[SessionState, EncryptedDRMessage]> {
  let s = { ...state };

  // DH ratchet step: needed on Bob's first send or after receiving a new ratchet key
  if (!s.chainKeySend) {
    const dhRPub = b64Decode(s.dhRecvPub!);
    const newDHS = generateKeyPair();
    const dhOut  = dhRaw(newDHS.privateKeyRaw, dhRPub);
    const [newRK, cks] = await rkRatchet(b64Decode(s.rootKey), dhOut);
    s = {
      ...s,
      dhSendPriv:   b64Encode(newDHS.privateKeyRaw),
      dhSendPub:    b64Encode(newDHS.publicKeyRaw),
      rootKey:      b64Encode(newRK),
      chainKeySend: b64Encode(cks),
      prevChainLen: s.sendN,
      sendN:        0
    };
  }

  const [nextCKs, mk] = await ckRatchet(b64Decode(s.chainKeySend!));
  const header: DRHeader = {
    ratchetKey: b64Decode(s.dhSendPub),
    n:  s.sendN,
    pn: s.prevChainLen
  };
  const ad = buildAD(s.associatedData, header);
  const { ciphertext, iv, tag } = await encryptWithMK(mk, plaintext, ad);

  const newState: SessionState = {
    ...s,
    chainKeySend: b64Encode(nextCKs),
    sendN:        s.sendN + 1
  };

  return [newState, { header, ciphertext, iv, tag }];
}

// ─── Double Ratchet decrypt ───────────────────────────────────────────────────

export async function ratchetDecrypt(
  state: SessionState,
  msg:   EncryptedDRMessage
): Promise<[SessionState, Uint8Array]> {
  const { header } = msg;
  let s = { ...state };

  // 1. Check skipped message keys
  const skipId    = skippedKeyId(header.ratchetKey, header.n);
  const skippedMK = s.skippedKeys[skipId];
  if (skippedMK) {
    const mk    = b64Decode(skippedMK);
    const ad    = buildAD(s.associatedData, header);
    const plain = await decryptWithMK(mk, msg.ciphertext, msg.iv, msg.tag, ad);
    const newSkipped = { ...s.skippedKeys };
    delete newSkipped[skipId];
    return [{ ...s, skippedKeys: newSkipped }, plain];
  }

  // 2. DH ratchet step if new ratchet key
  const curDHr    = s.dhRecvPub ? b64Decode(s.dhRecvPub) : null;
  const newRatchet = !curDHr || !arrayEqual(header.ratchetKey, curDHr);
  if (newRatchet) {
    if (s.chainKeyRecv) {
      s = await skipMessageKeys(s, header.pn);
    }
    s = await performDHRatchetStep(s, header.ratchetKey);
  }

  // 3. Skip ahead to message n
  s = await skipMessageKeys(s, header.n);

  // 4. Derive message key and decrypt
  const [nextCKr, mk] = await ckRatchet(b64Decode(s.chainKeyRecv!));
  s = { ...s, chainKeyRecv: b64Encode(nextCKr), recvN: s.recvN + 1 };

  const ad    = buildAD(s.associatedData, header);
  const plain = await decryptWithMK(mk, msg.ciphertext, msg.iv, msg.tag, ad);
  return [s, plain];
}

async function performDHRatchetStep(state: SessionState, newDHRPub: Uint8Array): Promise<SessionState> {
  const prevSendN = state.sendN;

  const dhOut1 = dhRaw(b64Decode(state.dhSendPriv), newDHRPub);
  const [rk1, newCKr] = await rkRatchet(b64Decode(state.rootKey), dhOut1);

  const newDHS = generateKeyPair();
  const dhOut2 = dhRaw(newDHS.privateKeyRaw, newDHRPub);
  const [rk2, newCKs] = await rkRatchet(rk1, dhOut2);

  return {
    ...state,
    dhSendPriv:   b64Encode(newDHS.privateKeyRaw),
    dhSendPub:    b64Encode(newDHS.publicKeyRaw),
    dhRecvPub:    b64Encode(newDHRPub),
    rootKey:      b64Encode(rk2),
    chainKeySend: b64Encode(newCKs),
    chainKeyRecv: b64Encode(newCKr),
    sendN:        0,
    recvN:        0,
    prevChainLen: prevSendN
  };
}

async function skipMessageKeys(state: SessionState, until: number): Promise<SessionState> {
  const toSkip = until - state.recvN;
  if (toSkip > MAX_SKIP) throw new Error(`Too many skipped messages: ${toSkip}`);
  let s = { ...state };
  while (s.recvN < until) {
    const [nextCKr, mk] = await ckRatchet(b64Decode(s.chainKeyRecv!));
    const id = skippedKeyId(b64Decode(s.dhRecvPub!), s.recvN);
    s = {
      ...s,
      chainKeyRecv: b64Encode(nextCKr),
      recvN:        s.recvN + 1,
      skippedKeys:  { ...s.skippedKeys, [id]: b64Encode(mk) }
    };
  }
  return s;
}

// ─── ArrayBuffer cast helper ─────────────────────────────────────────────────
// Converts Uint8Array (with potentially ArrayBufferLike .buffer) to a clean
// ArrayBuffer for Web Crypto API calls that require a strict ArrayBuffer type.
function toAB(u: Uint8Array): ArrayBuffer {
  return u.buffer.slice(u.byteOffset, u.byteOffset + u.byteLength) as ArrayBuffer;
}

// ─── Base64 helpers ───────────────────────────────────────────────────────────

export function b64Encode(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

export function b64Decode(s: string): Uint8Array {
  return Uint8Array.from(atob(s), c => c.charCodeAt(0));
}

// ─── Utility ──────────────────────────────────────────────────────────────────

function concat(...arrays: Uint8Array[]): Uint8Array {
  const total = arrays.reduce((acc, a) => acc + a.length, 0);
  const out   = new Uint8Array(total);
  let offset  = 0;
  for (const a of arrays) { out.set(a, offset); offset += a.length; }
  return out;
}

function arrayEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}
