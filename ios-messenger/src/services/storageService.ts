// ============================================================
// WorldMates Messenger — Storage Service
// Wraps expo-secure-store (Keychain) for tokens and
// AsyncStorage for user preferences / cached user object.
// ============================================================

import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { User } from '../api/types';

// ─── Keychain keys ────────────────────────────────────────────
const KEY_TOKEN = 'wm_access_token';
const KEY_USER_ID = 'wm_user_id';
const KEY_TOKEN_EXPIRES_AT = 'wm_token_expires_at';

// ─── AsyncStorage keys ───────────────────────────────────────
const KEY_LANGUAGE = 'wm_language';
const KEY_THEME = 'wm_theme';
const KEY_FIRST_LAUNCH = 'wm_first_launch_done';
const KEY_USER = 'wm_user_object';
const KEY_REFRESH_TOKEN = 'wm_refresh_token';

// Token expires within 60s → proactive refresh (mirrors Android TokenRefreshInterceptor)
const TOKEN_EXPIRY_MARGIN_MS = 60_000;

// ─────────────────────────────────────────────────────────────
// SECURE (Keychain via expo-secure-store)
// ─────────────────────────────────────────────────────────────

/**
 * Retrieve the stored access token from the iOS Keychain.
 * Returns null if none has been set.
 */
async function getToken(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(KEY_TOKEN);
  } catch {
    return null;
  }
}

/**
 * Persist the access token to the iOS Keychain.
 */
async function setToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(KEY_TOKEN, token);
}

/**
 * Remove the access token from the Keychain.
 */
async function clearToken(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_TOKEN);
}

/**
 * Retrieve the stored refresh token.
 */
async function getRefreshToken(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(KEY_REFRESH_TOKEN);
  } catch {
    return null;
  }
}

/**
 * Persist the refresh token.
 */
async function setRefreshToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(KEY_REFRESH_TOKEN, token);
}

/**
 * Remove the refresh token.
 */
async function clearRefreshToken(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_REFRESH_TOKEN);
}

/**
 * Store the Unix timestamp (ms) at which the access token expires.
 */
async function setTokenExpiresAt(expiresAtMs: number): Promise<void> {
  await SecureStore.setItemAsync(KEY_TOKEN_EXPIRES_AT, String(expiresAtMs));
}

/**
 * Retrieve the token expiry timestamp (ms).  Returns 0 if not set.
 */
async function getTokenExpiresAt(): Promise<number> {
  try {
    const raw = await SecureStore.getItemAsync(KEY_TOKEN_EXPIRES_AT);
    return raw ? parseInt(raw, 10) : 0;
  } catch {
    return 0;
  }
}

/**
 * Delete the stored token expiry timestamp.
 */
async function clearTokenExpiresAt(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_TOKEN_EXPIRES_AT).catch(() => {});
}

/**
 * Returns true when the stored token will expire within TOKEN_EXPIRY_MARGIN_MS (60 s).
 * Matches Android `UserSession.isTokenExpiringSoon`.
 */
async function isTokenExpiringSoon(): Promise<boolean> {
  const expiresAt = await getTokenExpiresAt();
  if (!expiresAt) return true;
  return expiresAt - Date.now() < TOKEN_EXPIRY_MARGIN_MS;
}

/**
 * Atomic session save — persists all three token-related values together.
 * Use after login / token refresh to avoid partially-updated state.
 */
async function saveFullSession(
  accessToken: string,
  refreshToken: string,
  expiresAtMs: number,
  userId: string,
  user: User,
): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(KEY_TOKEN, accessToken),
    SecureStore.setItemAsync(KEY_REFRESH_TOKEN, refreshToken),
    SecureStore.setItemAsync(KEY_TOKEN_EXPIRES_AT, String(expiresAtMs)),
    SecureStore.setItemAsync(KEY_USER_ID, userId),
    AsyncStorage.setItem(KEY_USER, JSON.stringify(user)),
  ]);
}

/**
 * Retrieve the numeric / string user ID from Keychain.
 */
async function getUserId(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(KEY_USER_ID);
  } catch {
    return null;
  }
}

/**
 * Persist the user ID to Keychain.
 */
async function setUserId(id: string): Promise<void> {
  await SecureStore.setItemAsync(KEY_USER_ID, id);
}

/**
 * Remove the user ID from Keychain.
 */
async function clearUserId(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_USER_ID);
}

// ─────────────────────────────────────────────────────────────
// ASYNC STORAGE (preferences & cache)
// ─────────────────────────────────────────────────────────────

/**
 * Return the currently stored UI language code (e.g. 'en', 'uk').
 * Falls back to 'en' when nothing is stored.
 */
async function getLanguage(): Promise<string> {
  try {
    return (await AsyncStorage.getItem(KEY_LANGUAGE)) ?? 'en';
  } catch {
    return 'en';
  }
}

/**
 * Persist the UI language code.
 */
async function setLanguage(lang: string): Promise<void> {
  await AsyncStorage.setItem(KEY_LANGUAGE, lang);
}

/**
 * Return the current theme ('light' | 'dark' | 'system').
 * Falls back to 'system'.
 */
async function getTheme(): Promise<string> {
  try {
    return (await AsyncStorage.getItem(KEY_THEME)) ?? 'system';
  } catch {
    return 'system';
  }
}

/**
 * Persist the theme preference.
 */
async function setTheme(theme: string): Promise<void> {
  await AsyncStorage.setItem(KEY_THEME, theme);
}

/**
 * Returns true if this is the very first launch (onboarding not yet shown).
 */
async function isFirstLaunch(): Promise<boolean> {
  try {
    const done = await AsyncStorage.getItem(KEY_FIRST_LAUNCH);
    return done === null;
  } catch {
    return true;
  }
}

/**
 * Mark that onboarding / first-launch flow has been completed.
 */
async function setFirstLaunchDone(): Promise<void> {
  await AsyncStorage.setItem(KEY_FIRST_LAUNCH, 'true');
}

/**
 * Load the full User object from the local cache.
 * Returns null if no user has been saved.
 */
async function getUser(): Promise<User | null> {
  try {
    const raw = await AsyncStorage.getItem(KEY_USER);
    if (!raw) return null;
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

/**
 * Persist the User object to the local cache.
 * Overwrites any previously cached user.
 */
async function saveUser(user: User): Promise<void> {
  await AsyncStorage.setItem(KEY_USER, JSON.stringify(user));
}

/**
 * Remove the cached User object.
 */
async function clearUser(): Promise<void> {
  await AsyncStorage.removeItem(KEY_USER);
}

/**
 * Wipe all persisted data — Keychain + AsyncStorage.
 * Call on logout.
 */
async function clearAll(): Promise<void> {
  await Promise.all([
    SecureStore.deleteItemAsync(KEY_TOKEN).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_REFRESH_TOKEN).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_USER_ID).catch(() => {}),
    SecureStore.deleteItemAsync(KEY_TOKEN_EXPIRES_AT).catch(() => {}),
    AsyncStorage.multiRemove([KEY_LANGUAGE, KEY_THEME, KEY_FIRST_LAUNCH, KEY_USER]),
  ]);
}

// ─────────────────────────────────────────────────────────────
// EXPORT
// ─────────────────────────────────────────────────────────────

export const storageService = {
  // Keychain — tokens
  getToken,
  setToken,
  clearToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
  getTokenExpiresAt,
  setTokenExpiresAt,
  clearTokenExpiresAt,
  isTokenExpiringSoon,
  saveFullSession,
  // Keychain — user identity
  getUserId,
  setUserId,
  clearUserId,
  // AsyncStorage
  getLanguage,
  setLanguage,
  getTheme,
  setTheme,
  isFirstLaunch,
  setFirstLaunchDone,
  getUser,
  saveUser,
  clearUser,
  clearAll,
};
