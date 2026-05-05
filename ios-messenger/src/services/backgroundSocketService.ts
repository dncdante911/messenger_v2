// ============================================================
// WorldMates Messenger — Background Socket Service
//
// Handles socket lifecycle tied to iOS app foreground/background
// transitions (mirrors Android foreground service behaviour).
//
// iOS constraints:
//   • Persistent WebSocket in background is NOT allowed by iOS.
//   • expo-background-fetch fires at most every ~15 min.
//   • Strategy: keep socket alive while in foreground,
//     reconnect immediately on foreground return,
//     use push notifications (FCM/APNs) for background messages.
//
// Usage:
//   Call initBackgroundSocketService() once in App.tsx after login.
//   Call destroyBackgroundSocketService() on logout.
// ============================================================

import { AppState, AppStateStatus } from 'react-native';
import * as BackgroundFetch from 'expo-background-fetch';
import * as TaskManager from 'expo-task-manager';
import { socketService } from './socketService';
import { storageService } from './storageService';

const BG_FETCH_TASK = 'wm-socket-keepalive';

// ─────────────────────────────────────────────────────────────
// BACKGROUND FETCH TASK
// Runs at most every 15 min on iOS when app is suspended.
// We use it to verify token validity — actual messages arrive
// via push notifications when in background.
// ─────────────────────────────────────────────────────────────

TaskManager.defineTask(BG_FETCH_TASK, async () => {
  try {
    const token = await storageService.getToken();
    if (!token) return BackgroundFetch.BackgroundFetchResult.NoData;

    // Proactive token refresh so socket reconnects instantly on foreground
    const expiringSoon = await storageService.isTokenExpiringSoon();
    if (expiringSoon) {
      const { refreshTokens } = await import('../api/authApi');
      const rt = await storageService.getRefreshToken();
      if (rt) await refreshTokens(rt).catch(() => null);
    }

    return BackgroundFetch.BackgroundFetchResult.NewData;
  } catch {
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});

// ─────────────────────────────────────────────────────────────
// APP STATE LISTENER
// ─────────────────────────────────────────────────────────────

let _appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
let _previousState: AppStateStatus = AppState.currentState;

async function handleAppStateChange(nextState: AppStateStatus): Promise<void> {
  if (_previousState.match(/inactive|background/) && nextState === 'active') {
    // Returning to foreground — reconnect socket if not already connected
    if (!socketService.isConnected()) {
      const token = await storageService.getToken();
      const userId = await storageService.getUserId();
      if (token) {
        socketService.connect(token, userId ?? undefined);
      }
    }
  }

  if (nextState.match(/inactive|background/) && _previousState === 'active') {
    // Going to background — socket will be killed by iOS naturally;
    // we don't force-disconnect so short background trips reconnect faster.
  }

  _previousState = nextState;
}

// ─────────────────────────────────────────────────────────────
// PUBLIC API
// ─────────────────────────────────────────────────────────────

/**
 * Register AppState listener and expo-background-fetch task.
 * Call once after successful login.
 */
export async function initBackgroundSocketService(): Promise<void> {
  // Remove stale listener if any
  _appStateSubscription?.remove();
  _appStateSubscription = AppState.addEventListener('change', handleAppStateChange);
  _previousState = AppState.currentState;

  // Register background fetch (best-effort; may not be approved on all devices)
  try {
    await BackgroundFetch.registerTaskAsync(BG_FETCH_TASK, {
      minimumInterval: 15 * 60, // 15 minutes (iOS minimum)
      stopOnTerminate: false,
      startOnBoot: false,
    });
  } catch {
    // Background fetch may be unavailable in some environments — non-fatal
  }
}

/**
 * Tear down AppState listener and unregister background task.
 * Call on logout.
 */
export async function destroyBackgroundSocketService(): Promise<void> {
  _appStateSubscription?.remove();
  _appStateSubscription = null;

  try {
    const isRegistered = await TaskManager.isTaskRegisteredAsync(BG_FETCH_TASK);
    if (isRegistered) {
      await BackgroundFetch.unregisterTaskAsync(BG_FETCH_TASK);
    }
  } catch {
    // Non-fatal
  }
}
