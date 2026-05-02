// ============================================================
// WorldMates Messenger — Auth API
//
// Thin, typed functions that wrap the WoWonder PHP auth endpoints.
// All functions return clean TypeScript objects; raw API shapes
// are mapped internally.
//
// Endpoints used:
//   POST BASE_URL + "?type=auth"
//   POST BASE_URL + "?type=user_register"
//   POST BASE_URL + "?type=send_reset_password" (forgot password)
//   POST BASE_URL + "?type=verify_code"
//   POST BASE_URL + "?type=resend_verify_code"
//   POST BASE_URL + "?type=logout" (or token clear only)
// ============================================================

import { phpApi, isPhpSuccess, normaliseApiStatus } from './apiClient';
import { storageService } from '../services/storageService';
import { socketService } from '../services/socketService';
import type { AuthUser, User, RegisterData } from './types';

// ─────────────────────────────────────────────────────────────
// INTERNAL TYPES (raw WoWonder PHP API shapes)
// ─────────────────────────────────────────────────────────────

interface RawAuthResponse {
  api_status: number | string;
  access_token?: string;
  user_id?: number | string;
  username?: string;
  avatar?: string;
  cover?: string;
  first_name?: string;
  last_name?: string;
  email?: string;
  phone_number?: string;
  about?: string;
  is_pro?: number | string;
  verified?: number | string;
  verification_level?: number | string;
  followers_count?: string | number;
  following_count?: string | number;
  status_emoji?: string;
  status_text?: string;
  is_founder?: number;
  error_code?: number | string;
  error_message?: string;
  errors?: { error_id?: number; error_text?: string };
  success_type?: string;
  message?: string;
}

// ─────────────────────────────────────────────────────────────
// MAPPING HELPER
// ─────────────────────────────────────────────────────────────

/**
 * Map the raw WoWonder PHP auth response to our clean AuthUser type.
 * Throws if the token or user_id is missing.
 */
function mapToAuthUser(raw: RawAuthResponse): AuthUser {
  if (!raw.access_token || raw.user_id == null) {
    throw new Error('Invalid auth response: missing token or user_id');
  }

  const userId = String(raw.user_id);
  const firstName = raw.first_name ?? '';
  const lastName = raw.last_name ?? '';
  const name =
    [firstName, lastName].filter(Boolean).join(' ').trim() || raw.username || userId;

  const user: User = {
    id: userId,
    username: raw.username ?? '',
    name,
    firstName: firstName || undefined,
    lastName: lastName || undefined,
    avatar: raw.avatar ?? '',
    cover: raw.cover,
    email: raw.email,
    phone: raw.phone_number,
    about: raw.about,
    isPro: Number(raw.is_pro ?? 0) === 1,
    verificationLevel: Number(raw.verification_level ?? 0),
    isVerified: Number(raw.verified ?? 0) > 0,
    isOnline: true,
    followersCount: Number(raw.followers_count ?? 0),
    followingCount: Number(raw.following_count ?? 0),
    customStatus:
      raw.status_emoji
        ? { emoji: raw.status_emoji, text: raw.status_text ?? '' }
        : undefined,
    isFounder: Number(raw.is_founder ?? 0) === 1,
  };

  return { ...user, token: raw.access_token };
}

/**
 * Persist token and userId after a successful auth response.
 */
async function persistSession(authUser: AuthUser): Promise<void> {
  await storageService.setToken(authUser.token);
  await storageService.setUserId(authUser.id);
  await storageService.saveUser(authUser);
}

// ─────────────────────────────────────────────────────────────
// PUBLIC API FUNCTIONS
// ─────────────────────────────────────────────────────────────

/**
 * Login with email/username and password.
 *
 * @throws Error with a human-readable message on failure.
 */
export async function login(email: string, password: string): Promise<AuthUser> {
  const res = await phpApi.post<RawAuthResponse>('', null, {
    params: {
      type: 'auth',
      username: email,
      password,
      device_type: 'ios',
    },
  });

  const raw = res.data;
  const status = normaliseApiStatus(raw.api_status);

  if (!isPhpSuccess(status)) {
    const msg =
      raw.error_message ??
      raw.errors?.error_text ??
      raw.message ??
      `Login failed (status ${status})`;
    throw new Error(msg);
  }

  const authUser = mapToAuthUser(raw);
  await persistSession(authUser);
  return authUser;
}

/**
 * Register a new account.
 *
 * @throws Error with a human-readable message on failure.
 */
export async function register(data: RegisterData): Promise<AuthUser> {
  const res = await phpApi.post<RawAuthResponse>('', null, {
    params: {
      type: 'user_register',
      username: data.username,
      email: data.email,
      password: data.password,
      first_name: data.firstName,
      last_name: data.lastName,
      gender: data.gender ?? '',
      birthday: data.birthday ?? '',
      device_type: 'ios',
    },
  });

  const raw = res.data;
  const status = normaliseApiStatus(raw.api_status);

  if (!isPhpSuccess(status)) {
    const msg =
      raw.error_message ??
      raw.errors?.error_text ??
      raw.message ??
      `Registration failed (status ${status})`;
    throw new Error(msg);
  }

  const authUser = mapToAuthUser(raw);
  await persistSession(authUser);
  return authUser;
}

/**
 * Send a password-reset email.
 *
 * @throws Error if the server rejects the request.
 */
export async function forgotPassword(email: string): Promise<void> {
  const res = await phpApi.post<RawAuthResponse>('', null, {
    params: {
      type: 'send_reset_password',
      email,
    },
  });

  const raw = res.data;
  const status = normaliseApiStatus(raw.api_status);

  if (!isPhpSuccess(status)) {
    const msg = raw.error_message ?? raw.message ?? `Request failed (status ${status})`;
    throw new Error(msg);
  }
}

/**
 * Verify a numeric code sent to the user's email/phone.
 * Used both for account confirmation and password reset flows.
 *
 * @param userId  Numeric string user ID returned at registration
 * @param code    6-digit (or similar) verification code
 * @throws Error on invalid code or expired token.
 */
export async function verifyCode(userId: string, code: string): Promise<void> {
  const res = await phpApi.post<RawAuthResponse>('', null, {
    params: {
      type: 'verify_code',
      user_id: userId,
      code,
    },
  });

  const raw = res.data;
  const status = normaliseApiStatus(raw.api_status);

  if (!isPhpSuccess(status)) {
    const msg = raw.error_message ?? raw.message ?? `Verification failed (status ${status})`;
    throw new Error(msg);
  }
}

/**
 * Resend the verification code to the user's email/phone.
 *
 * @param userId  Numeric string user ID
 * @throws Error if the server cannot resend.
 */
export async function resendCode(userId: string): Promise<void> {
  const res = await phpApi.post<RawAuthResponse>('', null, {
    params: {
      type: 'resend_verify_code',
      user_id: userId,
    },
  });

  const raw = res.data;
  const status = normaliseApiStatus(raw.api_status);

  if (!isPhpSuccess(status)) {
    const msg = raw.error_message ?? raw.message ?? `Resend failed (status ${status})`;
    throw new Error(msg);
  }
}

/**
 * Logout the current user.
 *
 * Disconnects the socket, clears the Keychain token and all local caches.
 * The server-side logout endpoint is called best-effort; local cleanup
 * always runs even if the network call fails.
 */
export async function logout(): Promise<void> {
  // Disconnect socket first so no more events fire.
  socketService.disconnect();

  // Best-effort server-side invalidation.
  try {
    await phpApi.post('', null, { params: { type: 'logout' } });
  } catch {
    // Ignore — we always clear local state.
  }

  await storageService.clearAll();
}
