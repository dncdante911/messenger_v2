// ============================================================
// WorldMates Messenger — Axios API Client
//
// Two Axios instances:
//   phpApi   → https://worldmates.club/api/v2/ (WoWonder PHP)
//   nodeApi  → https://worldmates.club:449/     (Node.js REST)
//
// nodeApi specifics (mirrors Android TokenRefreshInterceptor.kt):
//   • Header:  access-token: <token>   (NOT Authorization: Bearer)
//   • Proactive refresh: if token expires within 60 s, refresh before request
//   • Reactive refresh:  on HTTP 401 or body "error_id":"401", refresh & retry
//   • tryRefresh() is serialised with a mutex (double-check pattern)
//   • Auth/refresh paths skip the refresh interceptor entirely
//
// phpApi specifics:
//   • Header:  Authorization: Bearer <token>
//   • 401 → emit auth:failure (no refresh, WoWonder PHP handles differently)
//
// Both share:
//   • 5xx retry with exponential back-off (max 3)
// ============================================================

import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { EventEmitter } from 'eventemitter3';
import { storageService } from '../services/storageService';
import {
  BASE_URL,
  NODE_BASE_URL,
  CONNECT_TIMEOUT_SECONDS,
  READ_TIMEOUT_SECONDS,
} from '../constants/api';
import type { ApiResponse, NodeResponse } from './types';

// ─────────────────────────────────────────────────────────────
// AUTH FAILURE EVENT BUS
// ─────────────────────────────────────────────────────────────

export const authEventBus = new EventEmitter();
export const AUTH_FAILURE_EVENT = 'auth:failure';

// ─────────────────────────────────────────────────────────────
// RETRY CONFIG
// ─────────────────────────────────────────────────────────────

const MAX_RETRIES = 3;
const RETRY_BASE_DELAY_MS = 500;

interface RetryConfig extends InternalAxiosRequestConfig {
  _retryCount?: number;
  _skipRefresh?: boolean;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ─────────────────────────────────────────────────────────────
// NODE.JS TOKEN REFRESH — SKIP PATHS
// Matches Android TokenRefreshInterceptor SKIP_PATHS exactly.
// ─────────────────────────────────────────────────────────────

const REFRESH_SKIP_PATHS = new Set([
  'api/node/auth/login',
  'api/node/auth/refresh',
  'api/node/auth/register',
  'api/node/auth/quick-register',
  'api/node/auth/quick-verify',
  'api/node/auth/verify-code',
  'api/node/auth/send-code',
  'api/node/auth/request-password-reset',
  'api/node/auth/reset-password',
]);

function isSkipPath(url: string | undefined): boolean {
  if (!url) return false;
  return REFRESH_SKIP_PATHS.has(url.replace(/^\//, '').split('?')[0]);
}

// ─────────────────────────────────────────────────────────────
// TOKEN REFRESH MUTEX
// Serialises concurrent refresh calls (double-check pattern).
// ─────────────────────────────────────────────────────────────

let _refreshPromise: Promise<string | null> | null = null;

/**
 * Attempt a token refresh.  Concurrent callers all await the same promise.
 * Returns the new access token on success, null on failure.
 * On failure emits AUTH_FAILURE_EVENT and clears stored credentials.
 */
async function tryRefresh(): Promise<string | null> {
  if (_refreshPromise) return _refreshPromise;

  _refreshPromise = (async () => {
    try {
      const refreshToken = await storageService.getRefreshToken();
      if (!refreshToken) throw new Error('No refresh token');

      // Import lazily to avoid circular dependency (authApi imports nodeApi)
      const { refreshTokens } = await import('./authApi');
      const result = await refreshTokens(refreshToken);
      return result.accessToken;
    } catch {
      await storageService.clearAll();
      authEventBus.emit(AUTH_FAILURE_EVENT);
      return null;
    } finally {
      _refreshPromise = null;
    }
  })();

  return _refreshPromise;
}

// ─────────────────────────────────────────────────────────────
// NODE.JS REQUEST INTERCEPTOR
// 1. Skip auth/refresh paths.
// 2. Proactive refresh when token expires within 60 s.
// 3. Attach  access-token: <token>  header.
// ─────────────────────────────────────────────────────────────

async function nodeRequestInterceptor(
  config: InternalAxiosRequestConfig,
): Promise<InternalAxiosRequestConfig> {
  const rc = config as RetryConfig;

  if (!isSkipPath(config.url)) {
    const expiringSoon = await storageService.isTokenExpiringSoon();
    if (expiringSoon) {
      const newToken = await tryRefresh();
      if (newToken) {
        config.headers.set('access-token', newToken);
        return config;
      }
      // tryRefresh already emitted AUTH_FAILURE_EVENT
      return config;
    }
  }

  const token = await storageService.getToken();
  if (token) {
    config.headers.set('access-token', token);
  }

  return config;
}

// ─────────────────────────────────────────────────────────────
// NODE.JS RESPONSE INTERCEPTOR
// Reactive refresh on HTTP 401 or body error_id "401".
// ─────────────────────────────────────────────────────────────

function buildNodeResponseInterceptor(instance: AxiosInstance) {
  return {
    onFulfilled: async (response: AxiosResponse) => {
      // Body-level 401 check (server returns 200 with error_id:"401")
      const body = response.data as Record<string, unknown> | undefined;
      if (body && (body['error_id'] === '401' || body['error_id'] === 401)) {
        const config = response.config as RetryConfig;
        if (!config._skipRefresh && !isSkipPath(config.url)) {
          config._skipRefresh = true;
          const newToken = await tryRefresh();
          if (newToken) {
            config.headers.set('access-token', newToken);
            return instance(config);
          }
        }
      }
      return response;
    },
    onRejected: async (error: unknown) => {
      if (!axios.isAxiosError(error)) return Promise.reject(error);

      const status = error.response?.status;
      const config = error.config as RetryConfig | undefined;

      // ── 401: reactive refresh ───────────────────────────────
      if (status === 401 && config && !config._skipRefresh && !isSkipPath(config.url)) {
        config._skipRefresh = true;
        const newToken = await tryRefresh();
        if (newToken) {
          config.headers.set('access-token', newToken);
          return instance(config);
        }
        return Promise.reject(error);
      }

      // ── 5xx: retry with back-off ────────────────────────────
      if (status !== undefined && status >= 500 && config) {
        config._retryCount = (config._retryCount ?? 0) + 1;
        if (config._retryCount <= MAX_RETRIES) {
          await sleep(RETRY_BASE_DELAY_MS * 2 ** (config._retryCount - 1));
          return instance(config);
        }
      }

      return Promise.reject(error);
    },
  };
}

// ─────────────────────────────────────────────────────────────
// PHP REQUEST INTERCEPTOR  (Authorization: Bearer)
// ─────────────────────────────────────────────────────────────

async function phpRequestInterceptor(
  config: InternalAxiosRequestConfig,
): Promise<InternalAxiosRequestConfig> {
  const token = await storageService.getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
}

// ─────────────────────────────────────────────────────────────
// PHP RESPONSE INTERCEPTOR
// ─────────────────────────────────────────────────────────────

function buildPhpResponseInterceptor(instance: AxiosInstance) {
  return {
    onFulfilled: (response: AxiosResponse) => response,
    onRejected: async (error: unknown) => {
      if (!axios.isAxiosError(error)) return Promise.reject(error);

      const status = error.response?.status;
      const config = error.config as RetryConfig | undefined;

      if (status === 401) {
        await storageService.clearToken();
        await storageService.clearUserId();
        authEventBus.emit(AUTH_FAILURE_EVENT);
        return Promise.reject(error);
      }

      if (status !== undefined && status >= 500 && config) {
        config._retryCount = (config._retryCount ?? 0) + 1;
        if (config._retryCount <= MAX_RETRIES) {
          await sleep(RETRY_BASE_DELAY_MS * 2 ** (config._retryCount - 1));
          return instance(config);
        }
      }

      return Promise.reject(error);
    },
  };
}

// ─────────────────────────────────────────────────────────────
// CREATE INSTANCES
// ─────────────────────────────────────────────────────────────

const TIMEOUT_MS = (CONNECT_TIMEOUT_SECONDS + READ_TIMEOUT_SECONDS) * 1000;

function createNodeInstance(): AxiosInstance {
  const instance = axios.create({
    baseURL: NODE_BASE_URL,
    timeout: TIMEOUT_MS,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  });
  instance.interceptors.request.use(nodeRequestInterceptor, (err) => Promise.reject(err));
  const { onFulfilled, onRejected } = buildNodeResponseInterceptor(instance);
  instance.interceptors.response.use(onFulfilled, onRejected);
  return instance;
}

function createPhpInstance(): AxiosInstance {
  const instance = axios.create({
    baseURL: BASE_URL,
    timeout: TIMEOUT_MS,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  });
  instance.interceptors.request.use(phpRequestInterceptor, (err) => Promise.reject(err));
  const { onFulfilled, onRejected } = buildPhpResponseInterceptor(instance);
  instance.interceptors.response.use(onFulfilled, onRejected);
  return instance;
}

/** WoWonder PHP REST instance */
export const phpApi = createPhpInstance();

/** Node.js REST instance (access-token header + auto token refresh) */
export const nodeApi = createNodeInstance();

// ─────────────────────────────────────────────────────────────
// TYPED HELPER FUNCTIONS — Node.js API
// ─────────────────────────────────────────────────────────────

export async function nodeGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.get<NodeResponse<T>>(url, config);
  return res.data;
}

export async function nodePost<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.post<NodeResponse<T>>(url, data, config);
  return res.data;
}

export async function nodePut<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.put<NodeResponse<T>>(url, data, config);
  return res.data;
}

export async function nodeDelete<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.delete<NodeResponse<T>>(url, config);
  return res.data;
}

// ─────────────────────────────────────────────────────────────
// TYPED HELPER FUNCTIONS — PHP API
// ─────────────────────────────────────────────────────────────

export async function phpGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.get<ApiResponse<T>>(url, config);
  return res.data;
}

export async function phpPost<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.post<ApiResponse<T>>(url, data, config);
  return res.data;
}

export async function phpPut<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.put<ApiResponse<T>>(url, data, config);
  return res.data;
}

export async function phpDelete<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.delete<ApiResponse<T>>(url, config);
  return res.data;
}

// ─────────────────────────────────────────────────────────────
// UTILITY
// ─────────────────────────────────────────────────────────────

export function normaliseApiStatus(raw: number | string | undefined): number {
  if (typeof raw === 'number') return raw;
  if (typeof raw === 'string') return parseInt(raw, 10) || 0;
  return 0;
}

export function isPhpSuccess(raw: number | string | undefined): boolean {
  return normaliseApiStatus(raw) === 200;
}
