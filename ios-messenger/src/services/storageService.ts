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

// ─── AsyncStorage keys ───────────────────────────────────────
const KEY_LANGUAGE = 'wm_language';
const KEY_THEME = 'wm_theme';
const KEY_FIRST_LAUNCH = 'wm_first_launch_done';
const KEY_USER = 'wm_user_object';
const KEY_REFRESH_TOKEN = 'wm_refresh_token';

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
    AsyncStorage.multiRemove([KEY_LANGUAGE, KEY_THEME, KEY_FIRST_LAUNCH, KEY_USER]),
  ]);
}

// ─────────────────────────────────────────────────────────────
// EXPORT
// ─────────────────────────────────────────────────────────────

export const storageService = {
  // Keychain
  getToken,
  setToken,
  clearToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
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
