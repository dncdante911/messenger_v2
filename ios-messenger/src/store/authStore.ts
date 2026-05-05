// ============================================================
// WorldMates Messenger — Auth Store
//
// Handles the full login / register / verify / forgot-password
// lifecycle using the Node.js auth API.
//
// States:
//   idle          — not authenticated, no pending action
//   authenticated — logged in, user + token available
//   verifying     — server returned success_type="verification"
//                   (VerificationRequired — must enter code)
// ============================================================

import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import { authApi } from '../api/authApi';
import { storageService } from '../services/storageService';
import { socketService } from '../services/socketService';
import { useThemeStore } from '../theme';
import { useI18nStore } from '../i18n';
import type { User } from '../api/types';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

export interface VerificationContext {
  userId: string;
  verificationType: string;
  contactInfo: string;
  /** username used at registration — needed to resend code */
  username: string;
}

interface AuthState {
  user: User | null;
  accessToken: string | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  error: string | null;

  /** Set when server requires verification before granting a session */
  verificationRequired: boolean;
  verificationContext: VerificationContext | null;

  // ── Actions ──────────────────────────────────────────────
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  register: (
    username: string,
    email: string,
    password: string,
    gender?: string,
    inviteCode?: string,
  ) => Promise<void>;
  verifyCode: (code: string) => Promise<void>;
  resendVerificationCode: () => Promise<void>;
  requestPasswordReset: (email: string) => Promise<void>;
  resetPassword: (email: string, code: string, newPassword: string) => Promise<void>;
  logout: () => Promise<void>;
  loadStoredAuth: () => Promise<void>;
  clearError: () => void;
  setUser: (user: User) => void;
  cancelVerification: () => void;
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

async function persistAndConnect(
  user: User,
  accessToken: string,
  refreshToken: string,
  expiresAtMs: number,
): Promise<void> {
  await storageService.saveFullSession(accessToken, refreshToken, expiresAtMs, user.id, user);
  socketService.connect(accessToken);
  // Trigger Signal key registration in background — fire and forget
  schedulSignalRegistration();
}

function schedulSignalRegistration(): void {
  // Import lazily so this file doesn't pull in heavy crypto at startup
  import('../crypto/signal').then(({ signalEncryptionService }) => {
    signalEncryptionService.ensureRegistered().catch(() => {
      // Non-fatal — will retry on next message send
    });
  }).catch(() => {});
}

// ─────────────────────────────────────────────────────────────
// STORE
// ─────────────────────────────────────────────────────────────

export const useAuthStore = create<AuthState>()(
  immer((set, get) => ({
    user: null,
    accessToken: null,
    isLoggedIn: false,
    isLoading: false,
    error: null,
    verificationRequired: false,
    verificationContext: null,

    // ── Login ───────────────────────────────────────────────
    login: async (usernameOrEmail, password) => {
      set((s) => { s.isLoading = true; s.error = null; });
      try {
        const result = await authApi.login(usernameOrEmail, password);
        await persistAndConnect(result.user, result.accessToken, result.refreshToken, result.expiresAtMs);
        set((s) => {
          s.user = result.user;
          s.accessToken = result.accessToken;
          s.isLoggedIn = true;
          s.isLoading = false;
          s.verificationRequired = false;
          s.verificationContext = null;
        });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Login failed. Please try again.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Register ────────────────────────────────────────────
    register: async (username, email, password, gender = '', inviteCode = '') => {
      set((s) => { s.isLoading = true; s.error = null; });
      try {
        const result = await authApi.register(username, email, password, gender, inviteCode);

        if (result.type === 'verification') {
          set((s) => {
            s.isLoading = false;
            s.verificationRequired = true;
            s.verificationContext = {
              userId: result.userId,
              verificationType: result.verificationType,
              contactInfo: result.contactInfo,
              username,
            };
          });
          return;
        }

        await persistAndConnect(result.user, result.accessToken, result.refreshToken, result.expiresAtMs);
        set((s) => {
          s.user = result.user;
          s.accessToken = result.accessToken;
          s.isLoggedIn = true;
          s.isLoading = false;
          s.verificationRequired = false;
          s.verificationContext = null;
        });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Registration failed. Please try again.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Verify code (after registration / phone verification) ─
    verifyCode: async (code) => {
      const ctx = get().verificationContext;
      if (!ctx) throw new Error('No verification context');

      set((s) => { s.isLoading = true; s.error = null; });
      try {
        const result = await authApi.verifyCode(ctx.verificationType, ctx.contactInfo, code);
        await persistAndConnect(result.user, result.accessToken, result.refreshToken, result.expiresAtMs);
        set((s) => {
          s.user = result.user;
          s.accessToken = result.accessToken;
          s.isLoggedIn = true;
          s.isLoading = false;
          s.verificationRequired = false;
          s.verificationContext = null;
        });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Verification failed. Please check the code.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Resend verification code ─────────────────────────────
    resendVerificationCode: async () => {
      const ctx = get().verificationContext;
      if (!ctx) return;
      set((s) => { s.isLoading = true; s.error = null; });
      try {
        await authApi.sendVerificationCode(ctx.verificationType, ctx.contactInfo, ctx.username);
        set((s) => { s.isLoading = false; });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Failed to resend code.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Request password reset ───────────────────────────────
    requestPasswordReset: async (email) => {
      set((s) => { s.isLoading = true; s.error = null; });
      try {
        await authApi.requestPasswordReset(email);
        set((s) => { s.isLoading = false; });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Failed to send reset code.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Reset password ───────────────────────────────────────
    resetPassword: async (email, code, newPassword) => {
      set((s) => { s.isLoading = true; s.error = null; });
      try {
        await authApi.resetPassword(email, code, newPassword);
        set((s) => { s.isLoading = false; });
      } catch (err: unknown) {
        const message = extractMessage(err, 'Failed to reset password.');
        set((s) => { s.error = message; s.isLoading = false; });
        throw err;
      }
    },

    // ── Logout ───────────────────────────────────────────────
    logout: async () => {
      set((s) => { s.isLoading = true; });
      socketService.disconnect();
      await storageService.clearAll();
      set((s) => {
        s.user = null;
        s.accessToken = null;
        s.isLoggedIn = false;
        s.isLoading = false;
        s.error = null;
        s.verificationRequired = false;
        s.verificationContext = null;
      });
    },

    // ── Restore session on app launch ────────────────────────
    loadStoredAuth: async () => {
      set((s) => { s.isLoading = true; });
      try {
        const token = await storageService.getToken();
        const user = await storageService.getUser();

        if (!token || !user) {
          set((s) => { s.isLoading = false; });
          return;
        }

        await useThemeStore.getState()._hydrate();
        await useI18nStore.getState()._hydrate();
        socketService.connect(token);
        schedulSignalRegistration();

        set((s) => {
          s.user = user;
          s.accessToken = token;
          s.isLoggedIn = true;
          s.isLoading = false;
        });
      } catch {
        await storageService.clearAll();
        set((s) => {
          s.user = null;
          s.accessToken = null;
          s.isLoggedIn = false;
          s.isLoading = false;
        });
      }
    },

    clearError: () => set((s) => { s.error = null; }),
    setUser: (user) => set((s) => { s.user = user; }),
    cancelVerification: () =>
      set((s) => {
        s.verificationRequired = false;
        s.verificationContext = null;
        s.error = null;
      }),
  })),
);

// ─────────────────────────────────────────────────────────────
// UTILITY
// ─────────────────────────────────────────────────────────────

function extractMessage(err: unknown, fallback: string): string {
  if (err instanceof Error) return err.message || fallback;
  if (typeof err === 'object' && err !== null) {
    const e = err as Record<string, unknown>;
    const nested = (e['response'] as Record<string, unknown> | undefined);
    const data = nested?.['data'] as Record<string, unknown> | undefined;
    return String(data?.['message'] ?? data?.['error'] ?? fallback);
  }
  return fallback;
}
