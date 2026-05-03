import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import { authApi } from '../api/authApi';
import { storageService } from '../services/storageService';
import { socketService } from '../services/socketService';
import { useThemeStore } from '../theme';
import { useI18nStore } from '../i18n';
import type { User, RegisterData } from '../api/types';

interface AuthState {
  user: User | null;
  token: string | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  error: string | null;

  login: (email: string, password: string) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => Promise<void>;
  loadStoredAuth: () => Promise<void>;
  clearError: () => void;
  setUser: (user: User) => void;
}

export const useAuthStore = create<AuthState>()(
  immer((set, get) => ({
    user: null,
    token: null,
    isLoggedIn: false,
    isLoading: false,
    error: null,

    login: async (email: string, password: string) => {
      set((state) => {
        state.isLoading = true;
        state.error = null;
      });
      try {
        const response = await authApi.login(email, password);
        await storageService.saveToken(response.token);
        await storageService.saveUser(response.user);
        socketService.connect(response.token);
        set((state) => {
          state.user = response.user;
          state.token = response.token;
          state.isLoggedIn = true;
          state.isLoading = false;
        });
      } catch (err: any) {
        const message =
          err?.response?.data?.message || err?.message || 'Login failed. Please try again.';
        set((state) => {
          state.error = message;
          state.isLoading = false;
        });
        throw err;
      }
    },

    register: async (data: RegisterData) => {
      set((state) => {
        state.isLoading = true;
        state.error = null;
      });
      try {
        const response = await authApi.register(data);
        await storageService.saveToken(response.token);
        await storageService.saveUser(response.user);
        socketService.connect(response.token);
        set((state) => {
          state.user = response.user;
          state.token = response.token;
          state.isLoggedIn = true;
          state.isLoading = false;
        });
      } catch (err: any) {
        const message =
          err?.response?.data?.message || err?.message || 'Registration failed. Please try again.';
        set((state) => {
          state.error = message;
          state.isLoading = false;
        });
        throw err;
      }
    },

    logout: async () => {
      set((state) => {
        state.isLoading = true;
      });
      try {
        const { token } = get();
        if (token) {
          try {
            await authApi.logout(token);
          } catch {
            // Ignore logout API errors — proceed with local cleanup
          }
        }
        socketService.disconnect();
        await storageService.clearAuth();
        set((state) => {
          state.user = null;
          state.token = null;
          state.isLoggedIn = false;
          state.isLoading = false;
          state.error = null;
        });
      } catch (err: any) {
        socketService.disconnect();
        await storageService.clearAuth();
        set((state) => {
          state.user = null;
          state.token = null;
          state.isLoggedIn = false;
          state.isLoading = false;
        });
      }
    },

    loadStoredAuth: async () => {
      set((state) => {
        state.isLoading = true;
      });
      try {
        const token = await storageService.getToken();
        if (!token) {
          set((state) => {
            state.isLoading = false;
          });
          return;
        }
        const user = await storageService.getUser();
        if (!user) {
          await storageService.clearAuth();
          set((state) => {
            state.isLoading = false;
          });
          return;
        }
        // Verify token is still valid
        const verifiedUser = await authApi.verifyToken(token);
        await useThemeStore.getState()._hydrate();
        await useI18nStore.getState()._hydrate();
        socketService.connect(token);
        set((state) => {
          state.user = verifiedUser;
          state.token = token;
          state.isLoggedIn = true;
          state.isLoading = false;
        });
      } catch {
        await storageService.clearAuth();
        set((state) => {
          state.user = null;
          state.token = null;
          state.isLoggedIn = false;
          state.isLoading = false;
        });
      }
    },

    clearError: () => {
      set((state) => {
        state.error = null;
      });
    },

    setUser: (user: User) => {
      set((state) => {
        state.user = user;
      });
    },
  })),
);
