/**
 * Signal Protocol Service — TypeScript port of SignalEncryptionService.kt + SignalKeyStore.kt
 *
 * Responsibilities:
 *   1. Generate and persist local identity/pre-keys in localStorage.
 *   2. Register keys with the server once per install.
 *   3. Establish DR sessions via X3DH on first contact.
 *   4. Encrypt / decrypt messages using Double Ratchet.
 *   5. Auto-replenish one-time pre-keys when running low.
 *
 * Storage keys (all prefixed with "wm_signal_"):
 *   wm_signal_registered       → "1" if keys have been uploaded
 *   wm_signal_ik               → JSON { privateKeyRaw: Base64, publicKeyRaw: Base64 }
 *   wm_signal_spk              → JSON { id, kp: {priv, pub}, sig }
 *   wm_signal_opks             → JSON { nextId, keys: [ {id, priv, pub} ] }
 *   wm_signal_session_<userId> → JSON SessionState
 *   wm_signal_cache_<msgId>    → plaintext string
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
const CIPHER_VERSION_SIGNAL = 3;
const OPK_BATCH_SIZE         = 100;
const OPK_REPLENISH_LOW      = 20;

const KEY_REGISTERED = 'wm_signal_registered';
const KEY_IK         = 'wm_signal_ik';
const KEY_SPK        = 'wm_signal_spk';
const KEY_OPKS       = 'wm_signal_opks';
const KEY_SESSION    = (uid: number) => `wm_signal_session_${uid}`;
const KEY_CACHE      = (msgId: number) => `wm_signal_cache_${msgId}`;

// ─── Storage helpers ──────────────────────────────────────────────────────────

function lsGet<T>(key: string): T | null {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try { return JSON.parse(raw) as T; } catch { return null; }
}

function lsSet(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value));
}

// ─── Serialisable key types ───────────────────────────────────────────────────

interface StoredKP { priv: string; pub: string }               // Base64 bytes
interface StoredSPK { id: number; kp: StoredKP; sig: string }  // sig = placeholder (no Ed25519 here)
interface StoredOPK { id: number; priv: string; pub: string }
interface StoredOPKStore { nextId: number; keys: StoredOPK[] }

function kpToStored(kp: X25519KeyPair): StoredKP {
  return { priv: b64Encode(kp.privateKeyRaw), pub: b64Encode(kp.publicKeyRaw) };
}
function storedToKP(s: StoredKP): X25519KeyPair {
  return { privateKeyRaw: b64Decode(s.priv), publicKeyRaw: b64Decode(s.pub) };
}

// ─── SignalService singleton ──────────────────────────────────────────────────

export class SignalService {
  private static _instance: SignalService | null = null;
  private nodeApi: NodeApiShim;

  private constructor(nodeApi: NodeApiShim) {
    this.nodeApi = nodeApi;
  }

  static getInstance(nodeApi: NodeApiShim): SignalService {
    if (!SignalService._instance) {
      SignalService._instance = new SignalService(nodeApi);
    } else {
      // Update the api reference (token may change after login)
      SignalService._instance.nodeApi = nodeApi;
    }
    return SignalService._instance;
  }

  static resetInstance(): void {
    SignalService._instance = null;
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
    const kp = generateKeyPair();
    const spk: StoredSPK = {
      id:  1,
      kp:  kpToStored(kp),
      sig: b64Encode(new Uint8Array(64)) // placeholder — no Ed25519 support needed here
    };
    lsSet(KEY_SPK, spk);
    return spk;
  }

  // ─── One-time pre-keys ──────────────────────────────────────────────────────

  async generateAndSaveOPKBatch(): Promise<{ id: number; pub: string }[]> {
    // Use a random high starting ID on fresh install so IDs never collide
    // across re-registrations (server-side mergePrekeys keeps old IDs).
    const store = lsGet<StoredOPKStore>(KEY_OPKS)
      ?? { nextId: Math.floor(Math.random() * 900_000) + 100_000, keys: [] };
    const batch: { id: number; pub: string }[] = [];

    for (let i = 0; i < OPK_BATCH_SIZE; i++) {
      const kp  = generateKeyPair();
      const id  = store.nextId++;
      store.keys.push({ id, priv: b64Encode(kp.privateKeyRaw), pub: b64Encode(kp.publicKeyRaw) });
      batch.push({ id, pub: b64Encode(kp.publicKeyRaw) });
    }

    lsSet(KEY_OPKS, store);
    return batch;
  }

  consumeOPK(id: number): X25519KeyPair | null {
    const store = lsGet<StoredOPKStore>(KEY_OPKS);
    if (!store) {
      console.error('[Signal] consumeOPK: OPK store missing from localStorage (id=%d)', id);
      return null;
    }
    const idx = store.keys.findIndex(k => k.id === id);
    if (idx < 0) {
      const available = store.keys.map(k => k.id);
      console.error('[Signal] consumeOPK: OPK id=%d not found. Available IDs: [%s]', id, available.join(', '));
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

  isRegistered(): boolean {
    return localStorage.getItem(KEY_REGISTERED) === '1';
  }

  setRegistered(v: boolean): void {
    if (v) localStorage.setItem(KEY_REGISTERED, '1');
    else   localStorage.removeItem(KEY_REGISTERED);
  }

  /** Clear ALL Signal state so next app start triggers a full re-registration.
   *  Call this when OPK mismatch is detected or identity key becomes invalid. */
  clearAllSignalState(): void {
    localStorage.removeItem(KEY_REGISTERED);
    localStorage.removeItem(KEY_IK);
    localStorage.removeItem(KEY_SPK);
    localStorage.removeItem(KEY_OPKS);
    // Remove all sessions
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && (k.startsWith('wm_signal_session_') || k.startsWith('wm_signal_cache_'))) {
        localStorage.removeItem(k);
        i--;
      }
    }
    console.info('[Signal] All Signal state cleared — will re-register on next launch');
  }

  /** Clear the DR session for a specific user (e.g. when they change device). */
  clearSessionFor(userId: number): void {
    this.deleteSession(userId);
    console.info('[Signal] Cleared DR session for user', userId);
  }

  // ─── Plaintext cache ────────────────────────────────────────────────────────

  cacheDecryptedMessage(msgId: number, plaintext: string): void {
    localStorage.setItem(KEY_CACHE(msgId), plaintext);
  }

  getCachedDecryptedMessage(msgId: number): string | null {
    return localStorage.getItem(KEY_CACHE(msgId));
  }

  // ─── Registration ───────────────────────────────────────────────────────────

  async ensureRegistered(): Promise<void> {
    if (this.isRegistered()) return;
    try {
      const ik      = await this.getOrCreateIdentityKey();
      const spk     = await this.getOrCreateSignedPreKey();
      const opks    = await this.generateAndSaveOPKBatch();

      const ok = await this.nodeApi.registerSignalKeys({
        identity_key:       b64Encode(ik.publicKeyRaw),
        signed_prekey_id:   spk.id,
        signed_prekey:      spk.kp.pub,
        signed_prekey_sig:  spk.sig,
        prekeys:            JSON.stringify(opks.map(o => ({ id: o.id, key: o.pub })))
      });

      if (ok) {
        this.setRegistered(true);
        console.info('[Signal] Keys registered, OPKs:', opks.length);
      }
    } catch (e) {
      console.error('[Signal] ensureRegistered error:', e);
    }
  }

  // ─── Encrypt outgoing ───────────────────────────────────────────────────────

  async encryptForSend(recipientId: number, plaintext: string): Promise<SignalOutgoingPayload | null> {
    try {
      let session    = this.loadSession(recipientId);
      let x3dhFields: Record<string, unknown> | null = null;

      // X3DH session establishment if no existing session
      if (!session) {
        const bundle = await this.nodeApi.getSignalBundle(recipientId);
        if (!bundle) {
          console.error('[Signal] No pre-key bundle for user', recipientId);
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
          ik:     b64Encode(ik.publicKeyRaw),
          ek:     b64Encode(ekA.publicKeyRaw),
          ...(bundle.one_time_prekey_id !== undefined ? { opk_id: bundle.one_time_prekey_id } : {})
        };
        console.info('[Signal] X3DH completed for user', recipientId);
      }

      // DR encrypt
      const [newSession, encMsg] = await ratchetEncrypt(session, new TextEncoder().encode(plaintext));
      this.saveSession(recipientId, newSession);

      // Replenish OPKs if running low
      if (this.opkCount() < OPK_REPLENISH_LOW) {
        this.replenishOPKsSilently();
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
  }

  // ─── Decrypt incoming ────────────────────────────────────────────────────────

  async decryptIncoming(
    senderId:         number,
    ciphertextB64:    string,
    ivB64:            string,
    tagB64:           string,
    signalHeaderJson: string
  ): Promise<string | null> {
    try {
      const h = JSON.parse(signalHeaderJson) as Record<string, unknown>;

      const ratchetKey = b64Decode(h['rk'] as string);
      const n  = Number(h['n']  ?? 0);
      const pn = Number(h['pn'] ?? 0);

      // X3DH on first message (or re-key after sender reinstall)
      if ('ik' in h) {
        const ikAPub = b64Decode(h['ik'] as string);
        const ekAPub = b64Decode(h['ek'] as string);
        const opkId  = h['opk_id'] !== undefined ? Number(h['opk_id']) : null;

        const ik   = await this.getOrCreateIdentityKey();
        const spk  = await this.getOrCreateSignedPreKey();
        const spkKP: X25519KeyPair = storedToKP(spk.kp);
        const opkKP = opkId !== null ? this.consumeOPK(opkId) : null;

        if (opkId !== null && opkKP === null) {
          console.error('[Signal] X3DH(Bob): opk_id=%d not found in localStorage — ' +
            'SK will differ from sender (wrong DH4). This usually means the OPK store ' +
            'was cleared after registration. Clear Signal state and re-register to fix.',
            opkId);
        }

        if (this.hasSession(senderId)) {
          this.deleteSession(senderId);
          console.info('[Signal] X3DH(Bob) clearing stale session for', senderId);
        }

        console.info('[Signal] X3DH(Bob) for sender=%d opk_id=%s opk_found=%s',
          senderId, opkId, opkKP !== null);
        const [sk, ad] = await x3dhBob(ik, spkKP, opkKP, ikAPub, ekAPub);
        const session  = initBobSession(sk, ad, spkKP, ekAPub, senderId);
        this.saveSession(senderId, session);
        console.info('[Signal] X3DH(Bob) completed for sender', senderId);
      }

      const session = this.loadSession(senderId);
      if (!session) {
        console.error('[Signal] No DR session for sender', senderId);
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
      return new TextDecoder().decode(plainBytes);
    } catch (e) {
      const errMsg = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
      console.error('[Signal] decryptIncoming FAILED from sender=%d: %s', senderId, errMsg);
      console.error('[Signal] Full error:', e);
      return null;
    }
  }

  // ─── OPK replenishment ──────────────────────────────────────────────────────

  private async replenishOPKsSilently(): Promise<void> {
    try {
      const batch = await this.generateAndSaveOPKBatch();
      await this.nodeApi.replenishSignalPreKeys(
        JSON.stringify(batch.map(o => ({ id: o.id, key: o.pub })))
      );
      console.info('[Signal] Replenished', batch.length, 'OPKs');
    } catch (e) {
      console.warn('[Signal] OPK replenishment failed (non-critical):', e);
    }
  }
}

// ─── Node API shim interface (filled by api.ts at runtime) ───────────────────

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
}

export { CIPHER_VERSION_SIGNAL };
