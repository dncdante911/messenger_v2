// ============================================================
// WorldMates Messenger — Signal Key Store
// TypeScript port of Android SignalKeyStore.kt
//
// Storage backend: expo-secure-store (iOS Keychain / Android Keystore)
// Equivalent to Android's EncryptedSharedPreferences.
//
// Key naming mirrors Android exactly so backups can be cross-platform.
// ============================================================

import * as SecureStore from 'expo-secure-store';
import type { SessionState, StoredOPK, X25519KeyPair } from './signalTypes';
import { b64e, b64d, generateKeyPair } from './doubleRatchetManager';

// ─────────────────────────────────────────────────────────────
// STORAGE KEYS  (mirrors Android SignalKeyStore constants)
// ─────────────────────────────────────────────────────────────

const KEY_IK_PRIV = 'ik_priv';
const KEY_IK_PUB = 'ik_pub';
const KEY_SPK_ID = 'spk_id';
const KEY_SPK_PRIV = 'spk_priv';
const KEY_SPK_PUB = 'spk_pub';
const KEY_SPK_SIG = 'spk_sig';
const KEY_OPK_POOL = 'opk_pool';
const KEY_NEXT_OPK_ID = 'next_opk_id';
const KEY_SIGNAL_REGISTERED = 'signal_registered';

function sessionKey(userId: string): string {
  return `session_${userId}`;
}

// ─────────────────────────────────────────────────────────────
// IDENTITY KEY PAIR
// ─────────────────────────────────────────────────────────────

export async function saveIdentityKeyPair(kp: X25519KeyPair): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(KEY_IK_PRIV, b64e(kp.privateKey)),
    SecureStore.setItemAsync(KEY_IK_PUB, b64e(kp.publicKey)),
  ]);
}

export async function getIdentityKeyPair(): Promise<X25519KeyPair | null> {
  try {
    const priv = await SecureStore.getItemAsync(KEY_IK_PRIV);
    const pub = await SecureStore.getItemAsync(KEY_IK_PUB);
    if (!priv || !pub) return null;
    return { privateKey: b64d(priv), publicKey: b64d(pub) };
  } catch {
    return null;
  }
}

export async function getOrCreateIdentityKeyPair(): Promise<X25519KeyPair> {
  const existing = await getIdentityKeyPair();
  if (existing) return existing;
  const kp = generateKeyPair();
  await saveIdentityKeyPair(kp);
  return kp;
}

// ─────────────────────────────────────────────────────────────
// SIGNED PRE-KEY
// ─────────────────────────────────────────────────────────────

export interface SignedPreKey {
  id: number;
  keyPair: X25519KeyPair;
  signature: Uint8Array;
}

export async function saveSignedPreKey(spk: SignedPreKey): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(KEY_SPK_ID, String(spk.id)),
    SecureStore.setItemAsync(KEY_SPK_PRIV, b64e(spk.keyPair.privateKey)),
    SecureStore.setItemAsync(KEY_SPK_PUB, b64e(spk.keyPair.publicKey)),
    SecureStore.setItemAsync(KEY_SPK_SIG, b64e(spk.signature)),
  ]);
}

export async function getSignedPreKey(): Promise<SignedPreKey | null> {
  try {
    const [idStr, priv, pub, sig] = await Promise.all([
      SecureStore.getItemAsync(KEY_SPK_ID),
      SecureStore.getItemAsync(KEY_SPK_PRIV),
      SecureStore.getItemAsync(KEY_SPK_PUB),
      SecureStore.getItemAsync(KEY_SPK_SIG),
    ]);
    if (!idStr || !priv || !pub || !sig) return null;
    return {
      id: parseInt(idStr, 10),
      keyPair: { privateKey: b64d(priv), publicKey: b64d(pub) },
      signature: b64d(sig),
    };
  } catch {
    return null;
  }
}

// ─────────────────────────────────────────────────────────────
// ONE-TIME PRE-KEY POOL
// ─────────────────────────────────────────────────────────────

export async function saveOPKPool(pool: StoredOPK[]): Promise<void> {
  await SecureStore.setItemAsync(KEY_OPK_POOL, JSON.stringify(pool));
}

export async function getOPKPool(): Promise<StoredOPK[]> {
  try {
    const raw = await SecureStore.getItemAsync(KEY_OPK_POOL);
    if (!raw) return [];
    return JSON.parse(raw) as StoredOPK[];
  } catch {
    return [];
  }
}

export async function getNextOPKId(): Promise<number> {
  const raw = await SecureStore.getItemAsync(KEY_NEXT_OPK_ID);
  return raw ? parseInt(raw, 10) : 1;
}

export async function saveNextOPKId(id: number): Promise<void> {
  await SecureStore.setItemAsync(KEY_NEXT_OPK_ID, String(id));
}

/** Remove a single OPK from the pool by id (called after Bob uses it) */
export async function consumeOPK(id: number): Promise<X25519KeyPair | null> {
  const pool = await getOPKPool();
  const idx = pool.findIndex((o) => o.id === id);
  if (idx === -1) return null;
  const opk = pool[idx];
  const newPool = pool.filter((_, i) => i !== idx);
  await saveOPKPool(newPool);
  return { privateKey: b64d(opk.priv), publicKey: b64d(opk.pub) };
}

// ─────────────────────────────────────────────────────────────
// REGISTRATION FLAG
// ─────────────────────────────────────────────────────────────

export async function isSignalRegistered(): Promise<boolean> {
  try {
    const v = await SecureStore.getItemAsync(KEY_SIGNAL_REGISTERED);
    return v === 'true';
  } catch {
    return false;
  }
}

export async function setSignalRegistered(value: boolean): Promise<void> {
  await SecureStore.setItemAsync(KEY_SIGNAL_REGISTERED, value ? 'true' : 'false');
}

// ─────────────────────────────────────────────────────────────
// SESSIONS
// Uses commit-style (awaited set) — mirrors Android `commit()` vs `apply()`
// ─────────────────────────────────────────────────────────────

export async function saveSession(state: SessionState): Promise<void> {
  await SecureStore.setItemAsync(sessionKey(state.remoteUserId), JSON.stringify(state));
}

export async function loadSession(remoteUserId: string): Promise<SessionState | null> {
  try {
    const raw = await SecureStore.getItemAsync(sessionKey(remoteUserId));
    if (!raw) return null;
    return JSON.parse(raw) as SessionState;
  } catch {
    return null;
  }
}

export async function deleteSession(remoteUserId: string): Promise<void> {
  await SecureStore.deleteItemAsync(sessionKey(remoteUserId)).catch(() => {});
}

// ─────────────────────────────────────────────────────────────
// BACKUP / RESTORE  (mirrors Android exportForBackup/importFromBackup)
// ─────────────────────────────────────────────────────────────

export interface SignalBackup {
  ikPriv: string;
  ikPub: string;
  spkId: number;
  spkPriv: string;
  spkPub: string;
  spkSig: string;
  opkPool: StoredOPK[];
  nextOpkId: number;
}

export async function exportForBackup(): Promise<SignalBackup | null> {
  try {
    const [ikPriv, ikPub, spkIdStr, spkPriv, spkPub, spkSig, nextOpkIdStr, pool] =
      await Promise.all([
        SecureStore.getItemAsync(KEY_IK_PRIV),
        SecureStore.getItemAsync(KEY_IK_PUB),
        SecureStore.getItemAsync(KEY_SPK_ID),
        SecureStore.getItemAsync(KEY_SPK_PRIV),
        SecureStore.getItemAsync(KEY_SPK_PUB),
        SecureStore.getItemAsync(KEY_SPK_SIG),
        SecureStore.getItemAsync(KEY_NEXT_OPK_ID),
        getOPKPool(),
      ]);

    if (!ikPriv || !ikPub || !spkIdStr || !spkPriv || !spkPub || !spkSig) return null;

    return {
      ikPriv,
      ikPub,
      spkId: parseInt(spkIdStr, 10),
      spkPriv,
      spkPub,
      spkSig,
      opkPool: pool,
      nextOpkId: nextOpkIdStr ? parseInt(nextOpkIdStr, 10) : 1,
    };
  } catch {
    return null;
  }
}

export async function importFromBackup(backup: SignalBackup): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(KEY_IK_PRIV, backup.ikPriv),
    SecureStore.setItemAsync(KEY_IK_PUB, backup.ikPub),
    SecureStore.setItemAsync(KEY_SPK_ID, String(backup.spkId)),
    SecureStore.setItemAsync(KEY_SPK_PRIV, backup.spkPriv),
    SecureStore.setItemAsync(KEY_SPK_PUB, backup.spkPub),
    SecureStore.setItemAsync(KEY_SPK_SIG, backup.spkSig),
    saveOPKPool(backup.opkPool),
    saveNextOPKId(backup.nextOpkId),
  ]);
  await setSignalRegistered(true);
}

/** Wipe all Signal keys — call on device change / logout */
export async function clearForDeviceChange(): Promise<void> {
  await Promise.all([
    SecureStore.deleteItemAsync(KEY_IK_PRIV).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_IK_PUB).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_SPK_ID).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_SPK_PRIV).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_SPK_PUB).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_SPK_SIG).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_OPK_POOL).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_NEXT_OPK_ID).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_SIGNAL_REGISTERED).catch(() => {}),
  ]);
}
