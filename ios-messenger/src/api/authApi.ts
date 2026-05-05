// ============================================================
// WorldMates Messenger — Auth API (Node.js)
//
// Full port of Android NodeAuthRepository.kt.
// All requests use application/x-www-form-urlencoded (Retrofit
// @FormUrlEncoded @Field on Android).
//
// Endpoints:
//   POST api/node/auth/login
//   POST api/node/auth/register
//   POST api/node/auth/send-code
//   POST api/node/auth/verify-code
//   POST api/node/auth/request-password-reset
//   POST api/node/auth/reset-password
//   POST api/node/auth/refresh
// ============================================================

import { nodeApi } from './apiClient';
import { storageService } from '../services/storageService';
import type {
  User,
  NodeLoginResponseData,
  NodeRegisterResponseData,
  NodeVerifyCodeResponseData,
  NodeSendCodeResponseData,
  NodePasswordResetRequestData,
  NodePasswordResetData,
  NodeTokenRefreshData,
  NodeAuthUserRaw,
} from './types';

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

/** Build URLSearchParams body (form-urlencoded) */
function form(fields: Record<string, string | number | undefined>): URLSearchParams {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined) p.append(k, String(v));
  }
  return p;
}

const FORM_HEADERS = { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } };

/** Map raw server user object to clean User type */
function mapUser(raw: NodeAuthUserRaw): User {
  const userId = String(raw.user_id);
  const firstName = raw.first_name ?? '';
  const lastName = raw.last_name ?? '';
  const name = [firstName, lastName].filter(Boolean).join(' ').trim() || raw.username || userId;

  return {
    id: userId,
    username: raw.username ?? '',
    name,
    firstName: firstName || undefined,
    lastName: lastName || undefined,
    avatar: raw.avatar ?? '',
    cover: raw.cover,
    email: raw.email,
    about: raw.about,
    isPro: Number(raw.is_pro ?? 0) === 1,
    verificationLevel: Number(raw.verification_level ?? 0),
    isVerified: Number(raw.verified ?? 0) > 0,
    isOnline: true,
    followersCount: Number(raw.followers_count ?? 0),
    followingCount: Number(raw.following_count ?? 0),
    isFounder: Number(raw.is_founder ?? 0) === 1,
  };
}

/**
 * Compute absolute expiry timestamp (ms) from server response.
 * Server may send `expires_at` (Unix s) or `expires_in` (seconds from now).
 * Falls back to 24 h.
 */
function computeExpiresAtMs(raw: { expires_at?: number; expires_in?: number }): number {
  if (raw.expires_at) return raw.expires_at * 1000;
  if (raw.expires_in) return Date.now() + raw.expires_in * 1000;
  return Date.now() + 86_400_000; // 24 h fallback
}

// ─────────────────────────────────────────────────────────────
// RESULT TYPES (what the store consumes)
// ─────────────────────────────────────────────────────────────

export interface LoginResult {
  user: User;
  accessToken: string;
  refreshToken: string;
  expiresAtMs: number;
}

export type RegisterResult =
  | { type: 'success'; user: User; accessToken: string; refreshToken: string; expiresAtMs: number }
  | { type: 'verification'; userId: string; verificationType: string; contactInfo: string };

export interface VerifyResult {
  user: User;
  accessToken: string;
  refreshToken: string;
  expiresAtMs: number;
}

export interface TokenRefreshResult {
  accessToken: string;
  refreshToken: string;
  expiresAtMs: number;
}

// ─────────────────────────────────────────────────────────────
// AUTH FUNCTIONS
// ─────────────────────────────────────────────────────────────

/**
 * Login with username/email and password.
 * Matches Android: POST api/node/auth/login (FormUrlEncoded)
 * Fields: username, password, device_type
 */
export async function login(usernameOrEmail: string, password: string): Promise<LoginResult> {
  const res = await nodeApi.post<{ data?: NodeLoginResponseData; error?: string; message?: string; api_status?: number }>(
    'api/node/auth/login',
    form({ username: usernameOrEmail, password, device_type: 'ios' }),
    FORM_HEADERS,
  );

  const body = res.data;

  if (!body.data?.access_token) {
    throw new Error(body.error ?? body.message ?? 'Login failed');
  }

  const d = body.data;
  const expiresAtMs = computeExpiresAtMs(d);

  return {
    user: mapUser(d.user),
    accessToken: d.access_token,
    refreshToken: d.refresh_token,
    expiresAtMs,
  };
}

/**
 * Register a new account.
 * Matches Android: POST api/node/auth/register (FormUrlEncoded)
 * Fields: username, email, password, confirm_password, gender, device_type, invite_code
 *
 * Returns either a full session (immediate login) or a
 * VerificationRequired marker when the server mandates email/phone confirm.
 */
export async function register(
  username: string,
  email: string,
  password: string,
  gender: string = '',
  inviteCode: string = '',
): Promise<RegisterResult> {
  const res = await nodeApi.post<{ data?: NodeRegisterResponseData; error?: string; message?: string; api_status?: number }>(
    'api/node/auth/register',
    form({ username, email, password, confirm_password: password, gender, device_type: 'ios', invite_code: inviteCode }),
    FORM_HEADERS,
  );

  const body = res.data;

  if (!body.data) {
    throw new Error(body.error ?? body.message ?? 'Registration failed');
  }

  const d = body.data;

  // Server may require verification before granting a session
  if (d.success_type === 'verification' || !d.access_token) {
    return {
      type: 'verification',
      userId: String(d.user_id ?? ''),
      verificationType: d.verification_type ?? 'email',
      contactInfo: d.contact_info ?? email,
    };
  }

  const expiresAtMs = computeExpiresAtMs(d);
  return {
    type: 'success',
    user: mapUser(d.user!),
    accessToken: d.access_token,
    refreshToken: d.refresh_token!,
    expiresAtMs,
  };
}

/**
 * Send a verification code to the user's email or phone.
 * Matches Android: POST api/node/auth/send-code
 * Fields: verification_type, contact_info, username
 */
export async function sendVerificationCode(
  verificationType: string,
  contactInfo: string,
  username: string,
): Promise<void> {
  const res = await nodeApi.post<{ data?: NodeSendCodeResponseData; error?: string; message?: string }>(
    'api/node/auth/send-code',
    form({ verification_type: verificationType, contact_info: contactInfo, username }),
    FORM_HEADERS,
  );

  const body = res.data;
  if (body.error) throw new Error(body.error);
}

/**
 * Verify the code sent to the user's email or phone.
 * Matches Android: POST api/node/auth/verify-code
 * Fields: verification_type, contact_info, code
 */
export async function verifyCode(
  verificationType: string,
  contactInfo: string,
  code: string,
): Promise<VerifyResult> {
  const res = await nodeApi.post<{ data?: NodeVerifyCodeResponseData; error?: string; message?: string }>(
    'api/node/auth/verify-code',
    form({ verification_type: verificationType, contact_info: contactInfo, code }),
    FORM_HEADERS,
  );

  const body = res.data;

  if (!body.data?.access_token || !body.data?.user) {
    throw new Error(body.error ?? body.message ?? 'Verification failed');
  }

  const d = body.data;
  const expiresAtMs = computeExpiresAtMs(d);
  return {
    user: mapUser(d.user),
    accessToken: d.access_token,
    refreshToken: d.refresh_token!,
    expiresAtMs,
  };
}

/**
 * Request a password-reset code by email.
 * Matches Android: POST api/node/auth/request-password-reset
 * Fields: email
 */
export async function requestPasswordReset(email: string): Promise<void> {
  const res = await nodeApi.post<{ data?: NodePasswordResetRequestData; error?: string; message?: string }>(
    'api/node/auth/request-password-reset',
    form({ email }),
    FORM_HEADERS,
  );

  const body = res.data;
  if (body.error) throw new Error(body.error);
}

/**
 * Reset password using the code from email.
 * Matches Android: POST api/node/auth/reset-password
 * Fields: email, code, new_password
 */
export async function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  const res = await nodeApi.post<{ data?: NodePasswordResetData; error?: string; message?: string }>(
    'api/node/auth/reset-password',
    form({ email, code, new_password: newPassword }),
    FORM_HEADERS,
  );

  const body = res.data;
  if (body.error) throw new Error(body.error);
}

/**
 * Refresh the access token.
 * Called by apiClient.ts tryRefresh() — do NOT call directly from UI.
 * Matches Android: POST api/node/auth/refresh
 * Fields: refresh_token
 *
 * On success, saves new tokens to SecureStore automatically.
 */
export async function refreshTokens(refreshToken: string): Promise<TokenRefreshResult> {
  const res = await nodeApi.post<{ data?: NodeTokenRefreshData; error?: string; message?: string }>(
    'api/node/auth/refresh',
    form({ refresh_token: refreshToken }),
    FORM_HEADERS,
  );

  const body = res.data;

  if (!body.data?.access_token) {
    throw new Error(body.error ?? body.message ?? 'Token refresh failed');
  }

  const d = body.data;
  const expiresAtMs = computeExpiresAtMs(d);

  await Promise.all([
    storageService.setToken(d.access_token),
    storageService.setRefreshToken(d.refresh_token),
    storageService.setTokenExpiresAt(expiresAtMs),
  ]);

  return { accessToken: d.access_token, refreshToken: d.refresh_token, expiresAtMs };
}

export const authApi = { login, register, sendVerificationCode, verifyCode, requestPasswordReset, resetPassword, refreshTokens };
