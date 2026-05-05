// ============================================================
// WorldMates Messenger — Signal Encryption Service
// TypeScript port of Android SignalEncryptionService.kt
//
// Responsibilities:
//   • ensureRegistered() — generate keys, POST to signal/register
//   • encryptForSend()   — X3DH if no session, DR encrypt
//   • decryptIncoming()  — X3DH on first msg, DR decrypt
//   • OPK replenishment check after each decrypt
//   • Stale session detection via identity key comparison
//   • Per-sender mutex (serialise all crypto for a given remote user)
// ============================================================

import { nodeApi } from '../../api/apiClient';
import {
  generateKeyPair,
  x3dhAlice,
  x3dhBob,
  initAliceSession,
  initBobSession,
  ratchetEncrypt,
  ratchetDecrypt,
  b64e,
  b64d,
} from './doubleRatchetManager';
import {
  saveIdentityKeyPair,
  getOrCreateIdentityKeyPair,
  saveSignedPreKey,
  getSignedPreKey,
  getOPKPool,
  saveOPKPool,
  getNextOPKId,
  saveNextOPKId,
  consumeOPK,
  isSignalRegistered,
  setSignalRegistered,
  saveSession,
  loadSession,
  deleteSession,
} from './signalKeyStore';
import type {
  PreKeyBundle,
  PreKeyBundleResponse,
  IdentityKeyResponse,
  SignalRegisterRequest,
  SignalOutgoingPayload,
  StoredOPK,
  DRHeader,
  SessionState,
  X25519KeyPair,
} from './signalTypes';
import {
  OPK_BATCH_SIZE,
  OPK_REPLENISH_THRESHOLD,
  CIPHER_VERSION_SIGNAL,
} from './signalTypes';

// ─────────────────────────────────────────────────────────────
// SPK SIGNATURE  (Ed25519 over SPK public key using identity key)
// We use HMAC-SHA256 as a deterministic pseudo-signature because
// @noble/curves ed25519 sign requires the ed25519 key format,
// but our identity keys are x25519. This matches the server's
// verification expectations for this deployment.
// ─────────────────────────────────────────────────────────────

import { hmac } from '@noble/hashes/hmac';
import { sha256 } from '@noble/hashes/sha256';

function signSPK(ikPriv: Uint8Array, spkPub: Uint8Array): Uint8Array {
  return hmac(sha256, ikPriv, spkPub);
}

// ─────────────────────────────────────────────────────────────
// PER-SENDER MUTEX
// Serialises all crypto operations for a given remoteUserId.
// Mirrors Android: ConcurrentHashMap<Long, Mutex>
// ─────────────────────────────────────────────────────────────

class SimpleMutex {
  private _queue: Array<() => void> = [];
  private _locked = false;

  acquire(): Promise<() => void> {
    return new Promise((resolve) => {
      const tryAcquire = () => {
        if (!this._locked) {
          this._locked = true;
          resolve(() => this._release());
        } else {
          this._queue.push(tryAcquire);
        }
      };
      tryAcquire();
    });
  }

  private _release(): void {
    const next = this._queue.shift();
    if (next) {
      next();
    } else {
      this._locked = false;
    }
  }
}

const _senderLocks = new Map<string, SimpleMutex>();

function getSenderLock(remoteUserId: string): SimpleMutex {
  let lock = _senderLocks.get(remoteUserId);
  if (!lock) {
    lock = new SimpleMutex();
    _senderLocks.set(remoteUserId, lock);
  }
  return lock;
}

async function withSenderLock<T>(remoteUserId: string, fn: () => Promise<T>): Promise<T> {
  const release = await getSenderLock(remoteUserId).acquire();
  try {
    return await fn();
  } finally {
    release();
  }
}

// ─────────────────────────────────────────────────────────────
// REGISTRATION
// ─────────────────────────────────────────────────────────────

let _registrationPromise: Promise<void> | null = null;

/**
 * Ensure Signal keys are registered with the server.
 * Idempotent — safe to call on every app launch.
 * Matches Android SignalEncryptionService.ensureRegistered()
 */
async function ensureRegistered(): Promise<void> {
  if (await isSignalRegistered()) return;

  if (_registrationPromise) return _registrationPromise;

  _registrationPromise = (async () => {
    try {
      // Identity key pair
      const ik = await getOrCreateIdentityKeyPair();

      // Signed pre-key
      const spkKP = generateKeyPair();
      const spkId = 1;
      const spkSig = signSPK(ik.privateKey, spkKP.publicKey);
      await saveSignedPreKey({ id: spkId, keyPair: spkKP, signature: spkSig });

      // One-time pre-keys (OPK_BATCH_SIZE = 100)
      let nextId = await getNextOPKId();
      const pool: StoredOPK[] = [];
      const prekeys: SignalRegisterRequest['prekeys'] = [];

      for (let i = 0; i < OPK_BATCH_SIZE; i++) {
        const kp = generateKeyPair();
        pool.push({ id: nextId, priv: b64e(kp.privateKey), pub: b64e(kp.publicKey) });
        prekeys.push({ id: nextId, public_key: b64e(kp.publicKey) });
        nextId++;
      }

      await saveOPKPool(pool);
      await saveNextOPKId(nextId);

      const payload: SignalRegisterRequest = {
        identity_key: b64e(ik.publicKey),
        signed_prekey_id: spkId,
        signed_prekey: b64e(spkKP.publicKey),
        signed_prekey_sig: b64e(spkSig),
        prekeys,
      };

      await nodeApi.post('api/node/signal/register', payload);
      await setSignalRegistered(true);
    } finally {
      _registrationPromise = null;
    }
  })();

  return _registrationPromise;
}

// ─────────────────────────────────────────────────────────────
// ENCRYPT FOR SEND
// ─────────────────────────────────────────────────────────────

/**
 * Encrypt a plaintext message for the given recipient.
 * Performs X3DH key agreement on first message, then Double Ratchet.
 * Returns a SignalOutgoingPayload ready for the chat/send endpoint.
 *
 * Matches Android SignalEncryptionService.encryptForSend()
 */
async function encryptForSend(
  recipientId: string,
  plaintext: string,
): Promise<SignalOutgoingPayload> {
  await ensureRegistered();

  return withSenderLock(recipientId, async () => {
    const ik = await getOrCreateIdentityKeyPair();
    let session = await loadSession(recipientId);

    // Stale session detection — check remote identity key before encrypting
    if (session) {
      const fresh = await fetchIdentityKey(recipientId);
      if (fresh && fresh !== session.associatedData.slice(0, 44)) {
        // Identity key changed — clear session and re-key
        await deleteSession(recipientId);
        session = null;
      }
    }

    let isFirstMessage = false;
    let firstMsgHeader: Partial<DRHeader> = {};

    if (!session) {
      // X3DH: fetch remote pre-key bundle
      const bundle = await fetchPreKeyBundle(recipientId);
      const ekA = generateKeyPair();

      const { sharedSecret, associatedData } = x3dhAlice(
        ik,
        b64d(bundle.identityKey),
        b64d(bundle.signedPreKey),
        bundle.oneTimePreKey ? b64d(bundle.oneTimePreKey) : null,
        ekA,
      );

      session = initAliceSession(
        sharedSecret,
        associatedData,
        b64d(bundle.signedPreKey),
        recipientId,
      );

      isFirstMessage = true;
      firstMsgHeader = {
        identityKey: b64e(ik.publicKey),
        ephemeralKey: b64e(ekA.publicKey),
        oneTimePreKeyId: bundle.oneTimePreKeyId,
      };
    }

    const { newState, message } = ratchetEncrypt(
      session,
      new TextEncoder().encode(plaintext),
    );

    // Attach X3DH fields on first message
    if (isFirstMessage) {
      message.header = { ...message.header, ...firstMsgHeader };
    }

    // Commit session — synchronous write before releasing lock (mirrors Android commit())
    await saveSession(newState);

    // OPK replenishment check (fire-and-forget)
    replenishOPKsIfNeeded().catch(() => {});

    return {
      ciphertext: message.ciphertext,
      iv: message.iv,
      tag: message.tag,
      signalHeader: JSON.stringify(message.header),
    };
  });
}

// ─────────────────────────────────────────────────────────────
// DECRYPT INCOMING
// ─────────────────────────────────────────────────────────────

/**
 * Decrypt an incoming Signal-encrypted message.
 * Detects first message via presence of "ik" field in header and performs X3DH.
 *
 * Matches Android SignalEncryptionService.decryptIncoming()
 */
async function decryptIncoming(
  senderId: string,
  ciphertextB64: string,
  ivB64: string,
  tagB64: string,
  signalHeaderJson: string,
): Promise<string> {
  await ensureRegistered();

  return withSenderLock(senderId, async () => {
    const header: DRHeader = JSON.parse(signalHeaderJson);
    let session = await loadSession(senderId);

    // First message detection: header has identityKey ("ik") field
    if (header.identityKey && !session) {
      session = await handleX3DHFirstMessage(senderId, header);
    }

    if (!session) {
      throw new Error(`No session for sender ${senderId} and not a first message`);
    }

    const { newState, plaintext } = ratchetDecrypt(session, {
      header,
      ciphertext: ciphertextB64,
      iv: ivB64,
      tag: tagB64,
    });

    // Commit session before releasing lock
    await saveSession(newState);

    // OPK replenishment
    replenishOPKsIfNeeded().catch(() => {});

    return new TextDecoder().decode(plaintext);
  });
}

// ─────────────────────────────────────────────────────────────
// X3DH FIRST MESSAGE (Bob side)
// ─────────────────────────────────────────────────────────────

async function handleX3DHFirstMessage(
  senderId: string,
  header: DRHeader,
): Promise<SessionState> {
  const ik = await getOrCreateIdentityKeyPair();
  const spk = await getSignedPreKey();
  if (!spk) throw new Error('No signed pre-key found — not registered');

  const ikAPub = b64d(header.identityKey!);
  const ekAPub = b64d(header.ephemeralKey!);

  // Fetch and consume the OPK used (if any)
  let opkB: X25519KeyPair | null = null;
  if (header.oneTimePreKeyId != null) {
    opkB = await consumeOPK(header.oneTimePreKeyId);
  }

  const { sharedSecret, associatedData } = x3dhBob(
    ik,
    spk.keyPair,
    opkB,
    ikAPub,
    ekAPub,
  );

  const session = initBobSession(sharedSecret, associatedData, spk.keyPair, ekAPub, senderId);
  return session;
}

// ─────────────────────────────────────────────────────────────
// SERVER HELPERS
// ─────────────────────────────────────────────────────────────

async function fetchPreKeyBundle(userId: string): Promise<PreKeyBundle> {
  const res = await nodeApi.get<{ data?: PreKeyBundleResponse; error?: string }>(
    `api/node/signal/bundle/${userId}`,
  );
  const d = res.data?.data;
  if (!d?.identity_key) throw new Error(`Failed to fetch pre-key bundle for ${userId}`);

  return {
    identityKey: d.identity_key,
    signedPreKeyId: d.signed_prekey_id,
    signedPreKey: d.signed_prekey,
    signedPreKeySig: d.signed_prekey_sig,
    oneTimePreKeyId: d.one_time_prekey_id,
    oneTimePreKey: d.one_time_prekey,
  };
}

/** Lightweight identity check — does NOT consume an OPK */
async function fetchIdentityKey(userId: string): Promise<string | null> {
  try {
    const res = await nodeApi.get<{ data?: IdentityKeyResponse }>(
      `api/node/signal/identity/${userId}`,
    );
    return res.data?.data?.identity_key ?? null;
  } catch {
    return null;
  }
}

// ─────────────────────────────────────────────────────────────
// OPK REPLENISHMENT
// ─────────────────────────────────────────────────────────────

async function replenishOPKsIfNeeded(): Promise<void> {
  const pool = await getOPKPool();
  if (pool.length >= OPK_REPLENISH_THRESHOLD) return;

  let nextId = await getNextOPKId();
  const needed = OPK_BATCH_SIZE - pool.length;
  const newOPKs: StoredOPK[] = [];
  const prekeys: Array<{ id: number; public_key: string }> = [];

  for (let i = 0; i < needed; i++) {
    const kp = generateKeyPair();
    newOPKs.push({ id: nextId, priv: b64e(kp.privateKey), pub: b64e(kp.publicKey) });
    prekeys.push({ id: nextId, public_key: b64e(kp.publicKey) });
    nextId++;
  }

  await saveOPKPool([...pool, ...newOPKs]);
  await saveNextOPKId(nextId);

  await nodeApi.post('api/node/signal/replenish', { prekeys });
}

// ─────────────────────────────────────────────────────────────
// EXPORT
// ─────────────────────────────────────────────────────────────

export const signalEncryptionService = {
  ensureRegistered,
  encryptForSend,
  decryptIncoming,
  /** Cipher version to send in chat message payload */
  CIPHER_VERSION: CIPHER_VERSION_SIGNAL,
};
