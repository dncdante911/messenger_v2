// ============================================================
// WorldMates Messenger — Socket Hooks
// Typed React hooks for subscribing to Socket.IO events.
// ============================================================

import { useEffect, useRef, useState } from 'react';
import { socketService } from '../services/socketService';
import {
  SOCKET_EVENT_USER_ONLINE,
  SOCKET_EVENT_USER_OFFLINE,
} from '../constants/api';

// ─────────────────────────────────────────────────────────────
// useSocket
// ─────────────────────────────────────────────────────────────

/**
 * Subscribe to a single socket event for the lifetime of the component.
 * The caller is responsible for passing a stable handler (e.g. via useCallback)
 * to avoid redundant re-subscriptions on every render.
 *
 * @param event   The socket event name to subscribe to.
 * @param handler Callback invoked whenever the event fires.
 */
export function useSocket(event: string, handler: (data: unknown) => void): void {
  // Keep a ref to the latest handler so we never need to re-subscribe when
  // the handler identity changes between renders.
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    const stableHandler = (data: unknown) => handlerRef.current(data);
    socketService.on(event, stableHandler);
    return () => {
      socketService.off(event, stableHandler);
    };
  }, [event]);
}

// ─────────────────────────────────────────────────────────────
// useSocketMultiple
// ─────────────────────────────────────────────────────────────

/**
 * Subscribe to multiple socket events at once.
 * Each key in `handlers` is an event name; the value is its callback.
 *
 * @param handlers  Map of { [eventName]: handler }
 */
export function useSocketMultiple(handlers: Record<string, (data: unknown) => void>): void {
  // Store the latest handlers map in a ref to avoid unnecessary effect runs.
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  // We derive a stable key from the event names so that subscriptions are only
  // rebuilt when the set of events changes (not when handler identities change).
  const eventNames = Object.keys(handlers).sort().join(',');

  useEffect(() => {
    const stableHandlers: Array<{ event: string; fn: (data: unknown) => void }> = Object.keys(
      handlersRef.current,
    ).map((event) => {
      const fn = (data: unknown) => {
        handlersRef.current[event]?.(data);
      };
      socketService.on(event, fn);
      return { event, fn };
    });

    return () => {
      stableHandlers.forEach(({ event, fn }) => socketService.off(event, fn));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventNames]);
}

// ─────────────────────────────────────────────────────────────
// useOnlineStatus
// ─────────────────────────────────────────────────────────────

interface OnlinePayload {
  user_id?: string;
  userId?: string;
}

/**
 * Track the real-time online status of a specific user.
 * Subscribes to SOCKET_EVENT_USER_ONLINE / SOCKET_EVENT_USER_OFFLINE and
 * filters events to the given userId.
 *
 * @param userId  The ID of the user to track.
 * @returns       `true` while the user is online, `false` otherwise.
 *                Initial state is `false` (unknown → treated as offline).
 */
export function useOnlineStatus(userId: string): boolean {
  const [isOnline, setIsOnline] = useState(false);

  useEffect(() => {
    const handleOnline = (data: unknown) => {
      const payload = data as OnlinePayload;
      const id = payload?.user_id ?? payload?.userId;
      if (id === userId) setIsOnline(true);
    };

    const handleOffline = (data: unknown) => {
      const payload = data as OnlinePayload;
      const id = payload?.user_id ?? payload?.userId;
      if (id === userId) setIsOnline(false);
    };

    socketService.on(SOCKET_EVENT_USER_ONLINE, handleOnline);
    socketService.on(SOCKET_EVENT_USER_OFFLINE, handleOffline);

    return () => {
      socketService.off(SOCKET_EVENT_USER_ONLINE, handleOnline);
      socketService.off(SOCKET_EVENT_USER_OFFLINE, handleOffline);
    };
  }, [userId]);

  return isOnline;
}
