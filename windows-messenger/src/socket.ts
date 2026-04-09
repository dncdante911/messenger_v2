/**
 * Socket.IO client — port of SocketManager.kt
 *
 * Mirrors every real-time event the Android client handles:
 *   private_message, typing, typing_done, lastseen, message_reaction,
 *   message_pinned, on_user_loggedin, on_user_loggedoff, user_action,
 *   live_location_start/update/stop, group_message, call_signal, …
 */

import { io, type Socket } from 'socket.io-client';
import { SOCKET_URL } from './api';
import type {
  MessageItem,
  MessageReactionEvent,
  MessagePinnedEvent,
  MessageSeenEvent,
  TypingEvent,
  UserActionEvent,
  UserPresenceEvent
} from './types';

// ─── Handler map ─────────────────────────────────────────────────────────────

export type SocketHandlers = {
  onStatus?:              (status: string) => void;
  /** Fires on first connect AND every successful reconnect. */
  onConnected?:           () => void;
  onMessage?:             (msg: MessageItem) => void;
  onGroupMessage?:        (msg: MessageItem & { group_id: number }) => void;
  onTyping?:              (event: TypingEvent) => void;
  onTypingDone?:          (event: TypingEvent) => void;
  onUserAction?:          (event: UserActionEvent) => void;
  onMessageSeen?:         (event: MessageSeenEvent) => void;
  onReaction?:            (event: MessageReactionEvent) => void;
  onPinned?:              (event: MessagePinnedEvent) => void;
  onUserOnline?:          (event: UserPresenceEvent) => void;
  onUserOffline?:         (event: UserPresenceEvent) => void;
  onCallSignal?:          (payload: CallSignalPayload) => void;
  onIdentityChanged?:     (event: { user_id: number }) => void;
  /** Server relays this when a remote peer's decryption failed —
   *  we must clear our outgoing session so the next send includes X3DH. */
  onSessionResetRequest?: (event: { from_user_id: number }) => void;
};

export type CallSignalPayload = {
  type:    'offer' | 'answer' | 'ice' | 'end' | 'ringing';
  to:      number;
  from:    number;
  sdp?:    RTCSessionDescriptionInit;
  ice?:    RTCIceCandidateInit;
};

// ─── Socket factory ───────────────────────────────────────────────────────────

export function createChatSocket(token: string, handlers: SocketHandlers): Socket {
  const socket = io(SOCKET_URL, {
    transports:           ['websocket', 'polling'],
    auth:                 { token },
    // No reconnectionAttempts cap → default is Infinity.
    // This lets socket.io keep trying on transient network failures.
    reconnectionDelay:    3000,
    reconnectionDelayMax: 15000,
    timeout:              15000
  });

  socket.on('connect_error', (err: Error) => {
    handlers.onStatus?.(`Connection error: ${err.message}`);
  });

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  socket.on('connect', () => {
    handlers.onStatus?.('Connected');
    socket.emit('join', { access_token: token });
    // Notify App so it can flush pending actions (session resets, message reload).
    handlers.onConnected?.();
  });

  socket.on('disconnect', (reason: string) => {
    handlers.onStatus?.(`Disconnected (${reason})`);
    // socket.io does NOT auto-reconnect after 'io server disconnect'
    // (server explicitly called socket.disconnect()). Schedule a manual
    // socket.connect() call so we recover from server-side kicks / restarts.
    if (reason === 'io server disconnect') {
      setTimeout(() => {
        console.info('[Socket] Server-initiated disconnect — reconnecting in 5 s');
        socket.connect();
      }, 5000);
    }
  });

  socket.on('reconnect', (attempt: number) => {
    handlers.onStatus?.(`Reconnected (attempt ${attempt})`);
    socket.emit('join', { access_token: token });
    // 'connect' event also fires on reconnect, which calls onConnected — no
    // need to call it again here to avoid double-reloading.
  });

  socket.on('reconnect_failed', () => {
    handlers.onStatus?.('Reconnect failed — retrying in 15 s');
    // All automatic retry attempts were exhausted (shouldn't happen with no cap,
    // but guard anyway). Manually reconnect so we never stay offline permanently.
    setTimeout(() => socket.connect(), 15000);
  });

  // ── Private messages ───────────────────────────────────────────────────────

  socket.on('private_message', (data: MessageItem) => handlers.onMessage?.(data));
  socket.on('new_message',     (data: MessageItem) => handlers.onMessage?.(data));

  // ── Group messages ─────────────────────────────────────────────────────────

  socket.on('group_message', (data: MessageItem & { group_id: number }) => {
    handlers.onGroupMessage?.(data);
  });

  // ── Typing indicators ──────────────────────────────────────────────────────

  socket.on('typing',      (data: TypingEvent) => handlers.onTyping?.(data));
  socket.on('typing_done', (data: TypingEvent) => handlers.onTypingDone?.(data));

  // ── User action (recording, listening, etc.) ───────────────────────────────

  socket.on('user_action',       (data: UserActionEvent) => handlers.onUserAction?.(data));
  socket.on('group_user_action', (data: UserActionEvent) => handlers.onUserAction?.(data));

  // ── Read receipts ──────────────────────────────────────────────────────────

  socket.on('lastseen',       (data: MessageSeenEvent) => handlers.onMessageSeen?.(data));
  socket.on('seen_messages',  (data: MessageSeenEvent) => handlers.onMessageSeen?.(data));

  // ── Reactions & pins ───────────────────────────────────────────────────────

  socket.on('message_reaction', (data: MessageReactionEvent) => handlers.onReaction?.(data));
  socket.on('message_pinned',   (data: MessagePinnedEvent)   => handlers.onPinned?.(data));

  // ── Presence ───────────────────────────────────────────────────────────────

  socket.on('on_user_loggedin',  (data: UserPresenceEvent) =>
    handlers.onUserOnline?.({ ...data, is_online: true }));
  socket.on('on_user_loggedoff', (data: UserPresenceEvent) =>
    handlers.onUserOffline?.({ ...data, is_online: false }));
  socket.on('ping_for_lastseen', (data: UserPresenceEvent) =>
    handlers.onUserOnline?.({ ...data, is_online: true }));

  // ── WebRTC call signals ────────────────────────────────────────────────────

  socket.on('call_signal', (payload: CallSignalPayload) => handlers.onCallSignal?.(payload));

  // ── Signal identity change (sender reinstalled / changed device) ───────────
  // Server emits this when a contact re-registers their Signal keys.
  // We must clear the stale DR session so X3DH is re-run on the next message.

  socket.on('signal:identity_changed', (data: { user_id: number }) => {
    handlers.onIdentityChanged?.(data);
  });

  // ── Signal session reset request ─────────────────────────────────────────
  // Server relays this when a peer's decryption failed (session desync).
  // We clear our outgoing session so the next send includes fresh X3DH fields,
  // letting the peer re-initialise their incoming session.

  socket.on('signal:session_reset_request', (data: { from_user_id: number }) => {
    handlers.onSessionResetRequest?.(data);
  });

  return socket;
}

// ─── Outgoing helpers ─────────────────────────────────────────────────────────

export function emitTyping(socket: Socket | null, recipientId: number, done = false): void {
  if (!socket?.connected) return;
  socket.emit(done ? 'typing_done' : 'typing', { recipient_id: recipientId });
}

export function emitUserAction(
  socket:      Socket | null,
  recipientId: number,
  action:      UserActionEvent['action']
): void {
  if (!socket?.connected) return;
  socket.emit('user_action', { recipient_id: recipientId, action });
}

export function emitChatOpen(socket: Socket | null, chatUserId: number): void {
  if (!socket?.connected) return;
  socket.emit('is_chat_on', { user_id: chatUserId });
}

export function emitChatClose(socket: Socket | null, chatUserId: number): void {
  if (!socket?.connected) return;
  socket.emit('close_chat', { user_id: chatUserId });
}

export function emitCallSignal(socket: Socket | null, payload: CallSignalPayload): void {
  if (!socket?.connected) return;
  socket.emit('call_signal', payload);
}
