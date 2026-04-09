/**
 * Signal Protocol Service — TypeScript port of SignalEncryptionService.kt
 *
 * Closely mirrors the Android implementation:
 *   – AsyncMutex per sender (equivalent to Kotlin Mutex in senderLocks map)
 *   – msgId deduplication check inside the lock (same as Android)
 *   – OPK not found → return null only, do NOT wipe state (prevents infinite loop)
 *   – BAD_DECRYPT (DOMException OperationError) → clear session, return null
 *   – Stale IK detection before sending (fetches /signal/identity/:userId)
 *   – OPK replenishment when running low
 *
 * localStorage key schema (all prefixed "wm_signal_"):
 *   wm_signal_registered          "1" when keys have been uploaded to server
 *   wm_signal_ik                  JSON { priv: Base64, pub: Base64 }
 *   wm_signal_spk                 JSON { id, kp: {priv, pub}, sig }
 *   wm_signal_opks                JSON { nextId, keys: [{id, priv, pub}] }
 *   wm_signal_session_<userId>    JSON SessionState
 *   wm_signal_cache_<msgId>       plaintext string
 */

import type { SignalOutgoingPayload, SessionState, X25519KeyPair } from './signal';
import {
  b64Decode,
  b64Encode,
  generateKeyPair,
  initAliceSession,
  initBobSession,
  ratchetDecrypt,
  ratchetEncrypt,
  x3dhAlice,
  x3dhBob
} from './signal';

// ─── Constants ────────────────────────────────────────────────────────────────

export const CIPHER_VERSION_SIGNAL = 3;
const OPK_BATCH_SIZE               = 100;
const OPK_REPLENISH_LOW            = 20;

const KEY_REGISTERED = 'wm_signal_registered';
const KEY_IK         = 'wm_signal_ik';
const KEY_SPK        = 'wm_signal_spk';
const KEY_OPKS       = 'wm_signal_opks';
const KEY_SESSION    = (uid: number) => `wm_signal_session_${uid}`;
const KEY_CACHE      = (msgId: number) => `wm_signal_cache_${msgId}`;

// ─── Async mutex (port of Kotlin's kotlinx.coroutines.sync.Mutex) ─────────────
//
// JavaScript is single-threaded but async/await creates interleaved execution.
// Without serialisation two concurrent decryptIncoming() calls can both read the
// same session, advance the ratchet from the same position, and overwrite each
// other's saves — breaking all subsequent messages (identical to the Android bug
// that motivated senderLocks).  This simple Promise-chain mutex ensures only one
// coroutine runs the critical section at a time, per remote user.

class AsyncMutex {
  private queue: Array<() => void> = [];
  private locked = false;

  async withLock<T>(fn: () => Promise<T>): Promise<T> {
    await this._acquire();
    try {
      return await fn();
    } finally {
      this._release();
    }
  }

  private _acquire(): Promise<void> {
    if (!this.locked) {
      this.locked = true;
      return Promise.resolve();
    }
    return new Promise<void>(resolve => this.queue.push(resolve));
  }

  private _release(): void {
    const next = this.queue.shift();
    if (next) {
      next(); // wake next waiter
    } else {
      this.locked = false;
    }
  }
}

// ─── Serialisable key types ───────────────────────────────────────────────────

interface StoredKP { priv: string; pub: string }
interface StoredSPK { id: number; kp: StoredKP; sig: string }
interface StoredOPK { id: number; priv: string; pub: string }
interface StoredOPKStore { nextId: number; keys: StoredOPK[] }

function kpToStored(kp: X25519KeyPair): StoredKP {
  return { priv: b64Encode(kp.privateKeyRaw), pub: b64Encode(kp.publicKeyRaw) };
}
function storedToKP(s: StoredKP): X25519KeyPair {
  return { privateKeyRaw: b64Decode(s.priv), publicKeyRaw: b64Decode(s.pub) };
}

// ─── localStorage helpers ─────────────────────────────────────────────────────

function lsGet<T>(key: string): T | null {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try { return JSON.parse(raw) as T; } catch { return null; }
}
function lsSet(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value));
}

// ─── Node API shim (filled by api.ts at runtime) ──────────────────────────────

export interface PreKeyBundle {
  identity_key:        string;
  signed_prekey_id:    number;
  signed_prekey:       string;
  signed_prekey_sig:   string;
  one_time_prekey_id?: number;
  one_time_prekey?:    string;
}

export interface NodeApiShim {
  registerSignalKeys(payload: {
    identity_key:       string;
    signed_prekey_id:   number;
    signed_prekey:      string;
    signed_prekey_sig:  string;
    prekeys:            string;
  }): Promise<boolean>;

  getSignalBundle(userId: number): Promise<PreKeyBundle | null>;

  replenishSignalPreKeys(prekeys: string): Promise<void>;

  /** Fetch only the identity key — does NOT consume any OPK.
   *  Used for stale-session detection before sending. */
  getSignalIdentityKey(userId: number): Promise<string | null>;
}

// ─── SignalService ────────────────────────────────────────────────────────────

export class SignalService {
  private static _instance: SignalService | null = null;
  private nodeApi: NodeApiShim;

  /**
   * Per-sender mutex map — serialises all crypto operations for a given remote
   * user ID (both encrypt and decrypt), identical to Android's senderLocks map.
   *
   * Prevents the race: two concurrent decryptIncoming() calls both read the same
   * session → both advance ratchet from same position → second save overwrites
   * first → chain broken → all subsequent messages BAD_DECRYPT.
   */
  private senderLocks = new Map<number, AsyncMutex>();

  private constructor(nodeApi: NodeApiShim) {
    this.nodeApi = nodeApi;
  }

  static getInstance(nodeApi: NodeApiShim): SignalService {
    if (!SignalService._instance) {
      SignalService._instance = new SignalService(nodeApi);
    } else {
      SignalService._instance.nodeApi = nodeApi; // update token after re-login
    }
    return SignalService._instance;
  }

  static resetInstance(): void {
    SignalService._instance = null;
  }

  // ─── Lock helpers ───────────────────────────────────────────────────────────

  private getLock(userId: number): AsyncMutex {
    let lock = this.senderLocks.get(userId);
    if (!lock) {
      lock = new AsyncMutex();
      this.senderLocks.set(userId, lock);
    }
    return lock;
  }

  // ─── Identity key ───────────────────────────────────────────────────────────

  async getOrCreateIdentityKey(): Promise<X25519KeyPair> {
    const stored = lsGet<StoredKP>(KEY_IK);
    if (stored) return storedToKP(stored);
    const kp = generateKeyPair();
    lsSet(KEY_IK, kpToStored(kp));
    return kp;
  }

  // ─── Signed pre-key ─────────────────────────────────────────────────────────

  async getOrCreateSignedPreKey(): Promise<StoredSPK> {
    const stored = lsGet<StoredSPK>(KEY_SPK);
    if (stored) return stored;
    const kp: StoredSPK = {
      id:  1,
      kp:  kpToStored(generateKeyPair()),
      sig: b64Encode(new Uint8Array(64)) // placeholder — no Ed25519 here
    };
    lsSet(KEY_SPK, kp);
    return kp;
  }

  // ─── One-time pre-keys ──────────────────────────────────────────────────────

  async generateAndSaveOPKBatch(): Promise<{ id: number; pub: string }[]> {
    // Use a random high starting ID on fresh install so IDs never collide
    // with IDs from a previous registration on the same server OPK pool.
    const store = lsGet<StoredOPKStore>(KEY_OPKS)
      ?? { nextId: Math.floor(Math.random() * 900_000) + 100_000, keys: [] };
    const batch: { id: number; pub: string }[] = [];

    for (let i = 0; i < OPK_BATCH_SIZE; i++) {
      const kp = generateKeyPair();
      const id = store.nextId++;
      store.keys.push({ id, priv: b64Encode(kp.privateKeyRaw), pub: b64Encode(kp.publicKeyRaw) });
      batch.push({ id, pub: b64Encode(kp.publicKeyRaw) });
    }

    lsSet(KEY_OPKS, store);
    return batch;
  }

  /**
   * Pop the OPK with the given id from localStorage.
   * Returns null if the id is not found (store is desynced with server).
   *
   * IMPORTANT: On null, do NOT wipe Signal state.  Just return null and let
   * the caller signal the sender to retry with a fresh X3DH bundle fetch.
   * Wiping state here causes an infinite loop: the next REST reload of old
   * messages triggers the same OPK miss → wipe → reload → miss → wipe → …
   */
  consumeOPK(id: number): X25519KeyPair | null {
    const store = lsGet<StoredOPKStore>(KEY_OPKS);
    if (!store) {
      console.error('[Signal] consumeOPK: OPK store missing (id=%d)', id);
      return null;
    }
    const idx = store.keys.findIndex(k => k.id === id);
    if (idx < 0) {
      const available = store.keys.map(k => k.id);
      console.error('[Signal] consumeOPK: id=%d not found. Available: [%s]',
        id, available.slice(0, 10).join(', '));
      return null;
    }
    const [opk] = store.keys.splice(idx, 1);
    lsSet(KEY_OPKS, store);
    return { privateKeyRaw: b64Decode(opk.priv), publicKeyRaw: b64Decode(opk.pub) };
  }

  opkCount(): number {
    return lsGet<StoredOPKStore>(KEY_OPKS)?.keys.length ?? 0;
  }

  // ─── Session storage ────────────────────────────────────────────────────────

  loadSession(userId: number): SessionState | null {
    return lsGet<SessionState>(KEY_SESSION(userId));
  }

  saveSession(userId: number, state: SessionState): void {
    lsSet(KEY_SESSION(userId), state);
  }

  hasSession(userId: number): boolean {
    return this.loadSession(userId) !== null;
  }

  deleteSession(userId: number): void {
    localStorage.removeItem(KEY_SESSION(userId));
  }

  // ─── Registration flags ─────────────────────────────────────────────────────

  isRegistered(): boolean {
    return localStorage.getItem(KEY_REGISTERED) === '1';
  }

  setRegistered(v: boolean): void {
    if (v) localStorage.setItem(KEY_REGISTERED, '1');
    else   localStorage.removeItem(KEY_REGISTERED);
  }

  // ─── Plaintext cache ────────────────────────────────────────────────────────

  cacheDecryptedMessage(msgId: number, plaintext: string): void {
    localStorage.setItem(KEY_CACHE(msgId), plaintext);
  }

  getCachedDecryptedMessage(msgId: number): string | null {
    return localStorage.getItem(KEY_CACHE(msgId));
  }

  // ─── Public session management ──────────────────────────────────────────────

  /** Clear the DR session for a specific user (identity_changed / session_reset). */
  clearSessionFor(userId: number): void {
    this.deleteSession(userId);
    console.info('[Signal] Cleared DR session for user', userId);
  }

  /**
   * Clear ALL local Signal state.  Call only when explicitly needed (e.g. logout,
   * manual "reset keys" action).  Do NOT call this on OPK miss — that creates an
   * infinite loop of state wipes triggered by old encrypted messages in the DB.
   */
  clearAllSignalState(): void {
    localStorage.removeItem(KEY_REGISTERED);
    localStorage.removeItem(KEY_IK);
    localStorage.removeItem(KEY_SPK);
    localStorage.removeItem(KEY_OPKS);
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const k = localStorage.key(i);
      if (k && (k.startsWith('wm_signal_session_') || k.startsWith('wm_signal_cache_'))) {
        localStorage.removeItem(k);
      }
    }
    console.info('[Signal] All Signal state cleared');
  }

  // ─── Extract remote IK from session AD ─────────────────────────────────────

  /**
   * Extract the remote user's Base64 identity key from session.associatedData.
   * AD = IK_A ‖ IK_B (64 bytes).
   *   Initiator (Alice): our IK = [0..32), remote IK = [32..64)
   *   Responder (Bob):   remote IK = [0..32), our IK = [32..64)
   */
  private extractRemoteIkB64(session: SessionState): string | null {
    try {
      const ad = b64Decode(session.associatedData);
      if (ad.length < 64) return null;
      const remote = session.isInitiator ? ad.slice(32, 64) : ad.slice(0, 32);
      return b64Encode(remote);
    } catch {
      return null;
    }
  }

  // ─── Registration ───────────────────────────────────────────────────────────

  async ensureRegistered(): Promise<void> {
    if (this.isRegistered()) return;
    try {
      const ik   = await this.getOrCreateIdentityKey();
      const spk  = await this.getOrCreateSignedPreKey();
      const opks = await this.generateAndSaveOPKBatch();

      const ok = await this.nodeApi.registerSignalKeys({
        identity_key:      b64Encode(ik.publicKeyRaw),
        signed_prekey_id:  spk.id,
        signed_prekey:     spk.kp.pub,
        signed_prekey_sig: spk.sig,
        prekeys:           JSON.stringify(opks.map(o => ({ id: o.id, key: o.pub })))
      });

      if (ok) {
        this.setRegistered(true);
        console.info('[Signal] Registered — OPKs:', opks.length);
      }
    } catch (e) {
      console.error('[Signal] ensureRegistered error:', e);
    }
  }

  // ─── Encrypt outgoing ───────────────────────────────────────────────────────

  /**
   * Encrypt [plaintext] for [recipientId] via Double Ratchet.
   * If no session exists, performs X3DH first.
   *
   * Serialised per-recipient via AsyncMutex (same pattern as Android's senderLocks).
   */
  async encryptForSend(recipientId: number, plaintext: string): Promise<SignalOutgoingPayload | null> {
    const lock = this.getLock(recipientId);
    return lock.withLock(async () => {
      try {
        let session = this.loadSession(recipientId);
        let x3dhFields: Record<string, unknown> | null = null;

        // ── Stale session detection ──────────────────────────────────────────
        // Compare the remote IK cached in our session against what the server
        // currently has.  If they differ, the peer reinstalled and our session
        // is stale — using it would produce undecryptable ciphertext.
        // This check uses a lightweight endpoint that does NOT consume any OPK.
        if (session !== null) {
          const localIk  = this.extractRemoteIkB64(session);
          const serverIk = await this.nodeApi.getSignalIdentityKey(recipientId);
          if (localIk !== null && serverIk !== null && localIk !== serverIk) {
            console.warn('[Signal] encryptForSend: remote IK changed for %d — clearing stale session', recipientId);
            this.deleteSession(recipientId);
            session = null;
          }
        }

        // ── X3DH session establishment ───────────────────────────────────────
        if (session === null) {
          const bundle = await this.nodeApi.getSignalBundle(recipientId);
          if (!bundle) {
            console.error('[Signal] encryptForSend: no pre-key bundle for user', recipientId);
            return null;
          }

          const ik  = await this.getOrCreateIdentityKey();
          const ekA = generateKeyPair();

          const ikBPub  = b64Decode(bundle.identity_key);
          const spkBPub = b64Decode(bundle.signed_prekey);
          const opkBPub = bundle.one_time_prekey ? b64Decode(bundle.one_time_prekey) : null;

          const [sk, ad] = await x3dhAlice(ik, ikBPub, spkBPub, opkBPub, ekA);
          session = await initAliceSession(sk, ad, spkBPub, recipientId);

          x3dhFields = {
            ik: b64Encode(ik.publicKeyRaw),
            ek: b64Encode(ekA.publicKeyRaw),
            ...(bundle.one_time_prekey_id !== undefined ? { opk_id: bundle.one_time_prekey_id } : {})
          };
          console.info('[Signal] X3DH(Alice) completed for user', recipientId,
            'opk_id=', bundle.one_time_prekey_id ?? 'none');
        }

        // ── DR encrypt ───────────────────────────────────────────────────────
        const [newSession, encMsg] = await ratchetEncrypt(session, new TextEncoder().encode(plaintext));
        this.saveSession(recipientId, newSession);

        // Replenish OPKs outside the critical section (replenish is idempotent
        // and does not touch the session for recipientId).
        if (this.opkCount() < OPK_REPLENISH_LOW) {
          this._replenishOPKsSilently();
        }

        const headerMap: Record<string, unknown> = {
          rk: b64Encode(encMsg.header.ratchetKey),
          n:  encMsg.header.n,
          pn: encMsg.header.pn,
          ...x3dhFields
        };

        return {
          ciphertext:   b64Encode(encMsg.ciphertext),
          iv:           b64Encode(encMsg.iv),
          tag:          b64Encode(encMsg.tag),
          signalHeader: JSON.stringify(headerMap)
        };
      } catch (e) {
        console.error('[Signal] encryptForSend error for user', recipientId, e);
        return null;
      }
    });
  }

  // ─── Decrypt incoming ────────────────────────────────────────────────────────

  /**
   * Decrypt an incoming cipher_version=3 message.
   *
   * Serialised per-sender via AsyncMutex — mirrors Android's senderLocks pattern.
   * The msgId cache check is performed INSIDE the lock so a second concurrent
   * call (socket event + REST reload) returns the cached value without touching
   * the ratchet chain.
   *
   * On OPK miss: returns null WITHOUT wiping state.  The caller (App.tsx) is
   * responsible for emitting signal:session_reset_request to the sender so they
   * fetch a fresh pre-key bundle and retry.
   *
   * On BAD_DECRYPT (DOMException OperationError): clears the stale DR session
   * so the next outgoing message triggers a fresh X3DH key-agreement.
   *
   * @param msgId  Non-zero message ID enables deduplication caching.
   */
  async decryptIncoming(
    senderId:         number,
    ciphertextB64:    string,
    ivB64:            string,
    tagB64:           string,
    signalHeaderJson: string,
    msgId = 0
  ): Promise<string | null> {
    const lock = this.getLock(senderId);
    return lock.withLock(async () => {
      try {
        // ── Cache check (inside lock) ────────────────────────────────────────
        // If another concurrent call already decrypted this message while we
        // were waiting for the lock, return cached plaintext immediately.
        // DR keys are one-time — a second ratchet advance breaks all future msgs.
        if (msgId > 0) {
          const cached = this.getCachedDecryptedMessage(msgId);
          if (cached !== null) return cached;
        }

        const h = JSON.parse(signalHeaderJson) as Record<string, unknown>;

        const ratchetKey = b64Decode(h['rk'] as string);
        const n  = Number(h['n']  ?? 0);
        const pn = Number(h['pn'] ?? 0);

        // ── X3DH on first message (or re-key after sender reinstall) ─────────
        // 'ik' present ⟹ sender started fresh X3DH.  Always re-initialise,
        // even if we have an existing session (it would be stale anyway).
        if ('ik' in h) {
          const ikAPub = b64Decode(h['ik'] as string);
          const ekAPub = b64Decode(h['ek'] as string);
          const opkId  = h['opk_id'] !== undefined ? Number(h['opk_id']) : null;

          const ik   = await this.getOrCreateIdentityKey();
          const spk  = await this.getOrCreateSignedPreKey();
          const spkKP: X25519KeyPair = storedToKP(spk.kp);
          const opkKP = opkId !== null ? this.consumeOPK(opkId) : null;

          // If Alice sent opk_id but we can't find it, abort.
          // Falling back to 3-DH when Alice used 4-DH gives a different SK →
          // guaranteed BAD_DECRYPT.
          //
          // Do NOT wipe all Signal state here — that triggers an infinite loop:
          // the REST message reload fetches the same old message → miss → wipe
          // → reload → miss → wipe → …
          //
          // Just return null.  App.tsx will emit session_reset_request to the
          // sender, who then fetches a fresh bundle with valid OPK IDs and
          // sends a new X3DH message.
          if (opkId !== null && opkKP === null) {
            console.error('[Signal] X3DH(Bob): opk_id=%d not found locally — ' +
              'cannot compute 4-DH; returning null so sender retries with fresh bundle',
              opkId);
            // Delete any stale session so a future X3DH message can replace it
            if (this.hasSession(senderId)) {
              this.deleteSession(senderId);
            }
            return null;
          }

          // Delete stale session before creating the new one
          if (this.hasSession(senderId)) {
            this.deleteSession(senderId);
            console.info('[Signal] X3DH(Bob): clearing stale session for sender', senderId);
          }

          console.info('[Signal] X3DH(Bob) for sender=%d opk_id=%s',
            senderId, opkId ?? 'none');
          const [sk, ad] = await x3dhBob(ik, spkKP, opkKP, ikAPub, ekAPub);
          const newSession = initBobSession(sk, ad, spkKP, ekAPub, senderId);
          this.saveSession(senderId, newSession);
          console.info('[Signal] X3DH(Bob) completed for sender', senderId);
        }

        // ── DR decrypt ───────────────────────────────────────────────────────
        const session = this.loadSession(senderId);
        if (!session) {
          console.error('[Signal] decryptIncoming: no DR session for sender', senderId);
          return null;
        }

        const encMsg = {
          header:     { ratchetKey, n, pn },
          ciphertext: b64Decode(ciphertextB64),
          iv:         b64Decode(ivB64),
          tag:        b64Decode(tagB64)
        };

        const [newSession, plainBytes] = await ratchetDecrypt(session, encMsg);
        this.saveSession(senderId, newSession);
        const plainText = new TextDecoder().decode(plainBytes);

        // Cache inside the lock — any queued call for the same msgId returns
        // the cached value without touching the ratchet chain.
        if (msgId > 0) {
          this.cacheDecryptedMessage(msgId, plainText);
        }

        return plainText;

      } catch (e) {
        const errMsg = e instanceof Error ? `${e.name}: ${e.message}` : String(e);

        // DOMException('OperationError') = Web Crypto GCM tag mismatch.
        // Equivalent to Android's AEADBadTagException — the session is desynced.
        // Clear the session so the next outgoing message triggers a fresh X3DH.
        const isBadDecrypt = e instanceof DOMException && e.name === 'OperationError';
        if (isBadDecrypt) {
          console.warn('[Signal] decryptIncoming BAD_DECRYPT from sender=%d — ' +
            'session desynced; clearing session for re-key on next send', senderId);
          if (this.hasSession(senderId)) this.deleteSession(senderId);
        } else {
          console.error('[Signal] decryptIncoming FAILED from sender=%d: %s', senderId, errMsg);
        }
        return null;
      }
    });
  }

  // ─── OPK replenishment ──────────────────────────────────────────────────────

  /** Fire-and-forget OPK replenishment — non-critical, failures are logged only. */
  private _replenishOPKsSilently(): void {
    this.generateAndSaveOPKBatch()
      .then(batch => this.nodeApi.replenishSignalPreKeys(
        JSON.stringify(batch.map(o => ({ id: o.id, key: o.pub })))
      ))
      .then(() => console.info('[Signal] OPKs replenished:', OPK_BATCH_SIZE))
      .catch(e => console.warn('[Signal] OPK replenishment failed (non-critical):', e));
  }
}
