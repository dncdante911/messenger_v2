// ============================================================
// WorldMates Messenger — Presence Service
// TypeScript port of Android PresenceTracker.kt
//
// Global singleton Zustand store tracking online user IDs.
// The chatStore wires socket events into this store.
// Components can subscribe directly for reactive online badges.
// ============================================================

import { create } from 'zustand';

interface PresenceState {
  /** Set of string user IDs currently online */
  onlineUsers: Set<string>;
  /** chatId/userId → ISO timestamp of last seen */
  lastSeenAt: Record<string, string>;

  setOnline: (userId: string) => void;
  setOffline: (userId: string) => void;
  isOnline: (userId: string) => boolean;
  setLastSeen: (userId: string, timestamp: string) => void;
  getLastSeen: (userId: string) => string | null;
  /** Bulk-update from server snapshot */
  setOnlineBulk: (userIds: string[]) => void;
  clearAll: () => void;
}

export const usePresenceStore = create<PresenceState>()((set, get) => ({
  onlineUsers: new Set<string>(),
  lastSeenAt: {},

  setOnline: (userId) =>
    set((s) => {
      const next = new Set(s.onlineUsers);
      next.add(String(userId));
      return { onlineUsers: next };
    }),

  setOffline: (userId) =>
    set((s) => {
      const next = new Set(s.onlineUsers);
      next.delete(String(userId));
      return { onlineUsers: next };
    }),

  isOnline: (userId) => get().onlineUsers.has(String(userId)),

  setLastSeen: (userId, timestamp) =>
    set((s) => ({ lastSeenAt: { ...s.lastSeenAt, [String(userId)]: timestamp } })),

  getLastSeen: (userId) => get().lastSeenAt[String(userId)] ?? null,

  setOnlineBulk: (userIds) =>
    set({ onlineUsers: new Set(userIds.map(String)) }),

  clearAll: () => set({ onlineUsers: new Set(), lastSeenAt: {} }),
}));

/** Convenience non-hook accessor for use outside React components */
export const presenceService = {
  isOnline: (userId: string): boolean =>
    usePresenceStore.getState().isOnline(userId),
  setOnline: (userId: string): void =>
    usePresenceStore.getState().setOnline(userId),
  setOffline: (userId: string): void =>
    usePresenceStore.getState().setOffline(userId),
  setLastSeen: (userId: string, timestamp: string): void =>
    usePresenceStore.getState().setLastSeen(userId, timestamp),
};
