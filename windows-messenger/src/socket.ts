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
  onCallSignal?:          (payload: CallSignalPayload) => void;  // legacy
  // ── New call protocol ──────────────────────────────────────────────────────
  onCallIncoming?:        (data: IncomingCallData) => void;
  onCallAnswer?:          (data: CallAnswerData) => void;
  onCallEnded?:           (data: { roomName: string }) => void;
  onCallRejected?:        (data: { roomName: string }) => void;
  onCallError?:           (data: { message: string; status?: string }) => void;
  onIceCandidate?:        (data: IceCandidateData) => void;
  // ── Group calls ───────────────────────────────────────────────────────────
  onGroupCallIncoming?:         (data: GroupCallIncomingData) => void;
  onGroupCallOffer?:            (data: GroupCallOfferData) => void;
  onGroupCallAnswer?:           (data: GroupCallAnswerData) => void;
  onGroupCallIceCandidate?:     (data: IceCandidateData) => void;
  onGroupCallParticipantJoined?:(data: { userId: number; userName: string; roomName: string }) => void;
  onGroupCallParticipantLeft?:  (data: { userId: number; roomName: string }) => void;
  onGroupCallEnded?:            (data: { roomName: string }) => void;
  onIdentityChanged?:     (event: { user_id: number }) => void;
  /** Server relays this when a remote peer's decryption failed —
   *  we must clear our outgoing session so the next send includes X3DH. */
  onSessionResetRequest?: (event: { from_user_id: number }) => void;
};

// ─── Call payload types ───────────────────────────────────────────────────────

/** Legacy — kept for backward compat, no longer used for signaling */
export type CallSignalPayload = {
  type:    'offer' | 'answer' | 'ice' | 'end' | 'ringing';
  to:      number;
  from:    number;
  sdp?:    RTCSessionDescriptionInit;
  ice?:    RTCIceCandidateInit;
};

export type IncomingCallData = {
  fromId:     number;
  fromName:   string;
  fromAvatar?: string;
  callType:   'audio' | 'video';
  roomName:   string;
  sdpOffer:   string;
  iceServers: RTCIceServer[];
};

export type CallAnswerData = {
  roomName:   string;
  sdpAnswer:  string;
  acceptedBy: number;
  iceServers: RTCIceServer[];
};

export type IceCandidateData = {
  roomName:      string;
  fromUserId:    number;
  toUserId?:     number;
  candidate:     RTCIceCandidateInit;
  sdpMLineIndex?: number | null;
  sdpMid?:        string | null;
};

export type GroupCallIncomingData = {
  fromId:       number;
  fromName:     string;
  fromAvatar?:  string;
  callType:     'audio' | 'video';
  roomName:     string;
  groupId:      number;
  groupName:    string;
  iceServers:   RTCIceServer[];
};

export type GroupCallOfferData = {
  fromUserId: number;
  sdpOffer:   string;
  roomName:   string;
};

export type GroupCallAnswerData = {
  fromUserId: number;
  sdpAnswer:  string;
  roomName:   string;
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
    // Server expects user_id = access_token (session hash), not a numeric ID
    socket.emit('join', { user_id: token, access_token: token });
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
    socket.emit('join', { user_id: token, access_token: token });
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

  // ── WebRTC call signals (legacy) ──────────────────────────────────────────
  socket.on('call_signal', (payload: CallSignalPayload) => handlers.onCallSignal?.(payload));

  // ── New call protocol ─────────────────────────────────────────────────────
  socket.on('call:incoming',         (d: IncomingCallData)    => handlers.onCallIncoming?.(d));
  socket.on('call:answer',           (d: CallAnswerData)      => handlers.onCallAnswer?.(d));
  socket.on('call:ended',            (d: { roomName: string })=> handlers.onCallEnded?.(d));
  socket.on('call:rejected',         (d: { roomName: string })=> handlers.onCallRejected?.(d));
  socket.on('call:error',            (d: { message: string; status?: string }) => handlers.onCallError?.(d));
  socket.on('ice:candidate',         (d: IceCandidateData)    => handlers.onIceCandidate?.(d));

  // ── Group call events ─────────────────────────────────────────────────────
  socket.on('group_call:incoming',          (d: GroupCallIncomingData)  => handlers.onGroupCallIncoming?.(d));
  socket.on('group_call:offer',             (d: GroupCallOfferData)     => handlers.onGroupCallOffer?.(d));
  socket.on('group_call:answer',            (d: GroupCallAnswerData)    => handlers.onGroupCallAnswer?.(d));
  socket.on('group_call:ice_candidate',     (d: IceCandidateData)       => handlers.onGroupCallIceCandidate?.(d));
  socket.on('group_call:participant_joined',(d: { userId: number; userName: string; roomName: string }) => handlers.onGroupCallParticipantJoined?.(d));
  socket.on('group_call:participant_left',  (d: { userId: number; roomName: string }) => handlers.onGroupCallParticipantLeft?.(d));
  socket.on('group_call:ended',             (d: { roomName: string })   => handlers.onGroupCallEnded?.(d));

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

// ── New call protocol emitters ────────────────────────────────────────────────

export function emitCallInitiate(socket: Socket | null, data: {
  fromId: number; toId?: number; groupId?: number;
  callType: 'audio' | 'video'; roomName: string; sdpOffer: string;
}): void {
  socket?.emit('call:initiate', data);
}

export function emitCallAccept(socket: Socket | null, data: {
  roomName: string; userId: number; sdpAnswer: string;
}): void {
  socket?.emit('call:accept', data);
}

export function emitCallEnd(socket: Socket | null, roomName: string): void {
  socket?.emit('call:end', { roomName });
}

export function emitCallReject(socket: Socket | null, roomName: string): void {
  socket?.emit('call:reject', { roomName });
}

export function emitIceCandidate(socket: Socket | null, data: {
  roomName: string; toUserId: number; fromUserId: number;
  candidate: RTCIceCandidateInit;
}): void {
  socket?.emit('ice:candidate', data);
}

export function emitGroupCallJoin(socket: Socket | null, data: {
  roomName: string; userId: number;
}): void {
  socket?.emit('group_call:join', data);
}

export function emitGroupCallOffer(socket: Socket | null, data: {
  roomName: string; toUserId: number; sdpOffer: string;
}): void {
  socket?.emit('group_call:offer', data);
}

export function emitGroupCallAnswer(socket: Socket | null, data: {
  roomName: string; toUserId: number; sdpAnswer: string;
}): void {
  socket?.emit('group_call:answer', data);
}

export function emitGroupCallIce(socket: Socket | null, data: {
  roomName: string; toUserId: number; candidate: RTCIceCandidateInit;
}): void {
  socket?.emit('group_call:ice_candidate', data);
}

export function emitGroupCallLeave(socket: Socket | null, roomName: string, userId: number): void {
  socket?.emit('group_call:leave', { roomName, userId });
}

export function emitGroupCallEnd(socket: Socket | null, roomName: string): void {
  socket?.emit('group_call:end', { roomName });
}
