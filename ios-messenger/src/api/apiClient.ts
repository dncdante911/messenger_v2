// ============================================================
// WorldMates Messenger — Axios API Client
//
// Two Axios instances:
//   phpApi   → https://worldmates.club/api/v2/ (WoWonder PHP)
//   nodeApi  → https://worldmates.club:449/     (Node.js REST)
//
// Both share:
//   • Request interceptor: attach Bearer token from Keychain
//   • Response interceptor: 401 → clear token & emit auth-fail
//   • Retry logic for 5xx errors (max 3 attempts, exponential backoff)
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
// Components can subscribe to 'auth:failure' to redirect to Login.
// ─────────────────────────────────────────────────────────────

export const authEventBus = new EventEmitter();
export const AUTH_FAILURE_EVENT = 'auth:failure';

// ─────────────────────────────────────────────────────────────
// RETRY CONFIG
// ─────────────────────────────────────────────────────────────

const MAX_RETRIES = 3;
const RETRY_BASE_DELAY_MS = 500; // doubled each attempt: 500, 1000, 2000 ms

/** Augment config to carry retry metadata */
interface RetryConfig extends InternalAxiosRequestConfig {
  _retryCount?: number;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ─────────────────────────────────────────────────────────────
// SHARED INTERCEPTORS
// ─────────────────────────────────────────────────────────────

/**
 * Request interceptor — attach "Authorization: Bearer <token>" header.
 * Token is read from Keychain on every request so rotations are picked up
 * automatically without restarting the app.
 */
async function requestInterceptor(
  config: InternalAxiosRequestConfig,
): Promise<InternalAxiosRequestConfig> {
  const token = await storageService.getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
}

/**
 * Response interceptor factory.
 * - On 401: clear token and emit AUTH_FAILURE_EVENT so the app can redirect.
 * - On 5xx: retry up to MAX_RETRIES times with exponential back-off.
 */
function buildResponseInterceptor(instance: AxiosInstance) {
  return {
    onFulfilled: (response: AxiosResponse) => response,
    onRejected: async (error: unknown) => {
      if (!axios.isAxiosError(error)) return Promise.reject(error);

      const status = error.response?.status;
      const config = error.config as RetryConfig | undefined;

      // ── 401: unauthorised ──────────────────────────────────
      if (status === 401) {
        await storageService.clearToken();
        await storageService.clearUserId();
        authEventBus.emit(AUTH_FAILURE_EVENT);
        return Promise.reject(error);
      }

      // ── 5xx: server error — retry with back-off ────────────
      if (status !== undefined && status >= 500 && config) {
        config._retryCount = (config._retryCount ?? 0) + 1;
        if (config._retryCount <= MAX_RETRIES) {
          const delay = RETRY_BASE_DELAY_MS * 2 ** (config._retryCount - 1);
          await sleep(delay);
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

function createInstance(baseURL: string): AxiosInstance {
  const timeoutMs = CONNECT_TIMEOUT_SECONDS * 1000 + READ_TIMEOUT_SECONDS * 1000;
  const instance = axios.create({
    baseURL,
    timeout: timeoutMs,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
  });

  instance.interceptors.request.use(requestInterceptor, (err) => Promise.reject(err));

  const { onFulfilled, onRejected } = buildResponseInterceptor(instance);
  instance.interceptors.response.use(onFulfilled, onRejected);

  return instance;
}

/** WoWonder PHP REST instance */
export const phpApi = createInstance(BASE_URL);

/** Node.js REST instance */
export const nodeApi = createInstance(NODE_BASE_URL);

// ─────────────────────────────────────────────────────────────
// TYPED HELPER FUNCTIONS — Node.js API
// ─────────────────────────────────────────────────────────────

/**
 * GET request against the Node.js API.
 * @example const data = await nodeGet<Chat[]>('api/node/chat/chats');
 */
export async function nodeGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.get<NodeResponse<T>>(url, config);
  return res.data;
}

/**
 * POST request against the Node.js API.
 */
export async function nodePost<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.post<NodeResponse<T>>(url, data, config);
  return res.data;
}

/**
 * PUT request against the Node.js API.
 */
export async function nodePut<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<NodeResponse<T>> {
  const res = await nodeApi.put<NodeResponse<T>>(url, data, config);
  return res.data;
}

/**
 * DELETE request against the Node.js API.
 */
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

/**
 * GET request against the WoWonder PHP API.
 * @example const data = await phpGet<User>('?type=get-user-data&user_id=123');
 */
export async function phpGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.get<ApiResponse<T>>(url, config);
  return res.data;
}

/**
 * POST request against the WoWonder PHP API.
 */
export async function phpPost<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.post<ApiResponse<T>>(url, data, config);
  return res.data;
}

/**
 * PUT request against the WoWonder PHP API.
 */
export async function phpPut<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.put<ApiResponse<T>>(url, data, config);
  return res.data;
}

/**
 * DELETE request against the WoWonder PHP API.
 */
export async function phpDelete<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const res = await phpApi.delete<ApiResponse<T>>(url, config);
  return res.data;
}

// ─────────────────────────────────────────────────────────────
// UTILITY: normalise api_status
// ─────────────────────────────────────────────────────────────

/**
 * Normalise api_status to a number.
 * WoWonder sometimes returns the status as a string.
 */
export function normaliseApiStatus(raw: number | string | undefined): number {
  if (typeof raw === 'number') return raw;
  if (typeof raw === 'string') return parseInt(raw, 10) || 0;
  return 0;
}

/**
 * Returns true when the WoWonder PHP response indicates success.
 */
export function isPhpSuccess(raw: number | string | undefined): boolean {
  return normaliseApiStatus(raw) === 200;
}
