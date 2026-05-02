// ============================================================
// WorldMates Messenger — Socket.IO Service
//
// Singleton class that wraps Socket.IO v4.
// Ported from Android SocketManager.kt — mirrors every event,
// payload structure and reconnection strategy.
//
// Usage:
//   socketService.connect(token);
//   socketService.on('new_message', handler);
//   socketService.joinChat(userId);
// ============================================================

import { io, Socket } from 'socket.io-client';
import { EventEmitter } from 'eventemitter3';
import {
  SOCKET_URL,
  SOCKET_EVENT_AUTH,
  SOCKET_EVENT_PRIVATE_MESSAGE,
  SOCKET_EVENT_PRIVATE_MESSAGE_PAGE,
  SOCKET_EVENT_PAGE_MESSAGE,
  SOCKET_EVENT_NEW_MESSAGE,
  SOCKET_EVENT_TYPING,
  SOCKET_EVENT_TYPING_DONE,
  SOCKET_EVENT_RECORDING,
  SOCKET_EVENT_LAST_SEEN,
  SOCKET_EVENT_MESSAGE_SEEN,
  SOCKET_EVENT_GROUP_MESSAGE,
  SOCKET_EVENT_GROUP_TYPING,
  SOCKET_EVENT_GROUP_TYPING_DONE,
  SOCKET_EVENT_USER_ACTION,
  SOCKET_EVENT_GROUP_USER_ACTION,
  SOCKET_EVENT_MESSAGE_REACTION,
  SOCKET_EVENT_MESSAGE_PINNED,
  SOCKET_EVENT_CHANNEL_MESSAGE,
  SOCKET_EVENT_USER_ONLINE,
  SOCKET_EVENT_USER_OFFLINE,
  SOCKET_EVENT_CHAT_OPEN,
  SOCKET_EVENT_CHAT_CLOSE,
  SOCKET_EVENT_LIVE_LOCATION_START,
  SOCKET_EVENT_LIVE_LOCATION_UPDATE,
  SOCKET_EVENT_LIVE_LOCATION_STOP,
  SOCKET_EVENT_MESSAGE_EDITED,
  SOCKET_EVENT_MESSAGE_DELETED,
  SOCKET_EVENT_GROUP_MESSAGE_EDITED,
  SOCKET_EVENT_GROUP_MESSAGE_DELETED,
  SOCKET_EVENT_GROUP_HISTORY_CLEARED,
  SOCKET_EVENT_PRIVATE_HISTORY_CLEARED,
  SOCKET_EVENT_SIGNAL_IDENTITY_CHANGED,
  SOCKET_EVENT_GROUP_MEMBER_JOINED,
  SOCKET_EVENT_GROUP_MEMBER_LEFT,
  SOCKET_EVENT_STARS_RECEIVED,
  SOCKET_EVENT_CHANNEL_POST_CREATED,
  SOCKET_EVENT_CHANNEL_POST_UPDATED,
  SOCKET_EVENT_CHANNEL_POST_DELETED,
  SOCKET_EVENT_CHANNEL_COMMENT_ADDED,
  SOCKET_EVENT_CHANNEL_STREAM_STARTED,
  SOCKET_EVENT_CHANNEL_STREAM_ENDED,
  SOCKET_EVENT_CHANNEL_TYPING,
  SOCKET_EVENT_STORY_CREATED,
  SOCKET_EVENT_STORY_DELETED,
  SOCKET_EVENT_STORY_COMMENT_ADDED,
} from '../constants/api';

// ─────────────────────────────────────────────────────────────
// RECONNECTION CONFIG
// ─────────────────────────────────────────────────────────────

const MAX_RECONNECT_ATTEMPTS = 5;
const RECONNECT_BASE_DELAY_MS = 1_000; // doubled each attempt: 1s, 2s, 4s, 8s, 16s

// ─────────────────────────────────────────────────────────────
// SOCKET SERVICE CLASS
// ─────────────────────────────────────────────────────────────

class SocketService {
  private socket: Socket | null = null;
  private token: string | null = null;
  private userId: string | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isManualDisconnect = false;

  /** Internal event bus — forwards Socket.IO events to React components. */
  readonly emitter = new EventEmitter();

  // ── Public API ───────────────────────────────────────────────

  /**
   * Connect to the WorldMates Socket.IO server.
   * Call once per session, right after a successful login.
   *
   * @param token  WoWonder access_token (session hash)
   * @param uid    Numeric user ID string
   */
  connect(token: string, uid?: string): void {
    if (this.socket?.connected) {
      // Already connected — re-authenticate in case the room map was reset.
      this._authenticateSocket();
      return;
    }

    this.token = token;
    this.userId = uid ?? null;
    this.isManualDisconnect = false;
    this.reconnectAttempts = 0;
    this._createSocket();
  }

  /**
   * Disconnect gracefully.
   * Call on logout or when the app goes to background (optional).
   */
  disconnect(): void {
    this.isManualDisconnect = true;
    this._clearReconnectTimer();
    this.socket?.disconnect();
    this.socket = null;
    this.token = null;
    this.userId = null;
  }

  /** Returns true if the underlying socket is currently connected. */
  isConnected(): boolean {
    return this.socket?.connected === true;
  }

  /**
   * Subscribe to a socket event.
   * Returns an unsubscribe function for use with useEffect cleanup.
   *
   * @example
   *   const off = socketService.on('private_message', handler);
   *   return () => off();
   */
  on(event: string, callback: (...args: unknown[]) => void): () => void {
    this.emitter.on(event, callback);
    return () => this.emitter.off(event, callback);
  }

  /**
   * Unsubscribe a specific handler from an event.
   */
  off(event: string, callback?: (...args: unknown[]) => void): void {
    if (callback) {
      this.emitter.off(event, callback);
    } else {
      this.emitter.removeAllListeners(event);
    }
  }

  /**
   * Emit a raw event to the server.
   * The Socket.IO socket must be connected — the call is silently dropped otherwise.
   */
  emit(event: string, data: unknown): void {
    if (!this.socket?.connected) {
      console.warn(`[SocketService] Cannot emit '${event}': not connected`);
      return;
    }
    this.socket.emit(event, data);
  }

  // ── Chat helpers ─────────────────────────────────────────────

  /**
   * Notify the server that the current user opened a private chat window.
   * Mirrors Android SocketManager.openChat().
   *
   * @param recipientId  String or numeric peer user ID
   * @param lastMessageId  ID of the last visible message (for seen receipts)
   */
  joinChat(recipientId: string | number, lastMessageId?: string | number): void {
    this.emit(SOCKET_EVENT_CHAT_OPEN, {
      user_id: this.token,
      recipient_id: recipientId,
      message_id: lastMessageId ?? 0,
      isGroup: false,
    });
  }

  /**
   * Notify the server that the current user closed a private chat window.
   */
  closeChat(recipientId: string | number): void {
    this.emit(SOCKET_EVENT_CHAT_CLOSE, {
      user_id: this.token,
      recipient_id: recipientId,
    });
  }

  // ── Message sending ──────────────────────────────────────────

  /**
   * Send a private (1-on-1) text message via Socket.IO.
   * The server field names must be exactly 'msg', 'from_id', 'to_id'
   * as documented in the Node.js PrivateMessageController.
   */
  sendPrivateMessage(data: {
    toId: string | number;
    msg: string;
    mediaId?: string;
    replyToId?: string | number;
    isSticker?: boolean;
    lat?: number;
    lng?: number;
    color?: string;
  }): void {
    this.emit(SOCKET_EVENT_PRIVATE_MESSAGE, {
      msg: data.msg,
      from_id: this.userId,
      to_id: data.toId,
      mediaId: data.mediaId,
      message_reply_id: data.replyToId,
      isSticker: data.isSticker ?? false,
      lat: data.lat,
      lng: data.lng,
      color: data.color,
    });
  }

  /**
   * Send a group text message via Socket.IO.
   */
  sendGroupMessage(data: {
    groupId: string | number;
    msg: string;
    mediaId?: string;
    replyToId?: string | number;
    isSticker?: boolean;
    color?: string;
  }): void {
    this.emit(SOCKET_EVENT_GROUP_MESSAGE, {
      msg: data.msg,
      from_id: this.userId,
      group_id: data.groupId,
      mediaId: data.mediaId,
      message_reply_id: data.replyToId,
      isSticker: data.isSticker ?? false,
      color: data.color,
    });
  }

  // ── Typing & recording indicators ───────────────────────────

  /**
   * Emit a typing-started indicator to a private chat peer.
   * The server expects access_token (session hash) in user_id.
   */
  sendTyping(toId: string | number): void {
    this.emit(SOCKET_EVENT_TYPING, {
      access_token: this.token,
      user_id: this.userId,
      recipient_id: toId,
      is_typing: true,
    });
  }

  /**
   * Emit a typing-stopped indicator to a private chat peer.
   */
  sendTypingDone(toId: string | number): void {
    this.emit(SOCKET_EVENT_TYPING_DONE, {
      access_token: this.token,
      user_id: this.userId,
      recipient_id: toId,
      is_typing: false,
    });
  }

  /**
   * Emit a voice-recording indicator.
   * user_id must be the session hash (not numeric ID).
   */
  sendRecording(toId: string | number): void {
    this.emit(SOCKET_EVENT_RECORDING, {
      user_id: this.token,
      recipient_id: String(toId),
    });
  }

  /**
   * Emit an arbitrary user-action indicator (listening, viewing, choosing_sticker, etc.).
   */
  sendUserAction(toId: string | number, action: string, groupId?: string | number): void {
    const event = groupId ? SOCKET_EVENT_GROUP_USER_ACTION : SOCKET_EVENT_USER_ACTION;
    this.emit(event, {
      user_id: this.userId,
      recipient_id: toId,
      group_id: groupId,
      action,
    });
  }

  // ── Read receipts ────────────────────────────────────────────

  /**
   * Tell the server that all messages from fromId have been read.
   * Mirrors Android SocketManager.sendMessageSeen().
   */
  sendSeenMessages(fromId: string | number, messageId?: string | number): void {
    this.emit(SOCKET_EVENT_MESSAGE_SEEN, {
      access_token: this.token,
      user_id: this.userId,
      sender_id: fromId,
      message_id: messageId ?? 0,
    });
  }

  // ── Channel / Story helpers ──────────────────────────────────

  subscribeToChannel(channelId: string | number): void {
    this.emit('channel:subscribe', { channelId, userId: this.userId });
  }

  unsubscribeFromChannel(channelId: string | number): void {
    this.emit('channel:unsubscribe', { channelId, userId: this.userId });
  }

  subscribeToStories(friendIds: Array<string | number>): void {
    this.emit('story:subscribe', { userId: this.userId, friendIds });
  }

  unsubscribeFromStories(friendIds: Array<string | number>): void {
    this.emit('story:unsubscribe', { userId: this.userId, friendIds });
  }

  sendStoryView(storyId: string | number, storyOwnerId: string | number): void {
    this.emit('story:view', { storyId, userId: this.userId, storyOwnerId });
  }

  // ── Bot ──────────────────────────────────────────────────────

  sendBotCallbackQuery(
    botId: string,
    messageId: string | number | null,
    callbackData: string,
  ): void {
    this.emit('bot_callback_query', {
      bot_id: botId,
      message_id: messageId,
      callback_data: callbackData,
      user_id: this.token,
    });
  }

  // ── Live Location ────────────────────────────────────────────

  sendLiveLocationStart(toId: string | number): void {
    this.emit(SOCKET_EVENT_LIVE_LOCATION_START, { from_id: this.userId, to_id: toId });
  }

  sendLiveLocationUpdate(
    toId: string | number,
    lat: number,
    lng: number,
    accuracy?: number,
  ): void {
    this.emit(SOCKET_EVENT_LIVE_LOCATION_UPDATE, {
      from_id: this.userId,
      to_id: toId,
      lat,
      lng,
      accuracy: accuracy ?? 0,
    });
  }

  sendLiveLocationStop(toId: string | number): void {
    this.emit(SOCKET_EVENT_LIVE_LOCATION_STOP, { from_id: this.userId, to_id: toId });
  }

  // ── Raw access ───────────────────────────────────────────────

  /** Direct access to the underlying Socket.IO socket (for WebRTC signalling). */
  getSocket(): Socket | null {
    return this.socket;
  }

  // ─────────────────────────────────────────────────────────────
  // PRIVATE IMPLEMENTATION
  // ─────────────────────────────────────────────────────────────

  private _createSocket(): void {
    if (!this.token) return;

    try {
      this.socket = io(SOCKET_URL, {
        transports: ['websocket', 'polling'],
        query: {
          access_token: this.token,
          user_id: this.userId ?? '',
        },
        reconnection: false, // We handle reconnection manually for full control.
        timeout: 15_000,
        forceNew: false,
      });

      this._registerCoreHandlers();
      this._registerMessageHandlers();
      this._registerStatusHandlers();
      this._registerGroupHandlers();
      this._registerChannelHandlers();
      this._registerStoryHandlers();
      this._registerE2EEHandlers();
    } catch (err) {
      console.error('[SocketService] Failed to create socket:', err);
    }
  }

  private _registerCoreHandlers(): void {
    if (!this.socket) return;

    this.socket.on('connect', () => {
      console.log('[SocketService] Connected, id:', this.socket?.id);
      this.reconnectAttempts = 0;
      this._authenticateSocket();
      this.emitter.emit('socket:connected');
    });

    this.socket.on('disconnect', (reason: string) => {
      console.log('[SocketService] Disconnected, reason:', reason);
      this.emitter.emit('socket:disconnected', reason);
      if (!this.isManualDisconnect) {
        this._scheduleReconnect();
      }
    });

    this.socket.on('connect_error', (err: Error) => {
      console.error('[SocketService] Connection error:', err.message);
      this.emitter.emit('socket:error', err.message);
      if (!this.isManualDisconnect) {
        this._scheduleReconnect();
      }
    });

    this.socket.on('reconnect', () => {
      console.log('[SocketService] Reconnected');
      this.reconnectAttempts = 0;
      this._authenticateSocket();
      this.emitter.emit('socket:connected');
    });
  }

  private _registerMessageHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    // Private messages
    bubble(SOCKET_EVENT_PRIVATE_MESSAGE);
    bubble(SOCKET_EVENT_PRIVATE_MESSAGE_PAGE);
    bubble(SOCKET_EVENT_PAGE_MESSAGE);
    bubble(SOCKET_EVENT_NEW_MESSAGE);
    // Message mutations
    bubble(SOCKET_EVENT_MESSAGE_EDITED);
    bubble(SOCKET_EVENT_MESSAGE_DELETED);
    bubble(SOCKET_EVENT_MESSAGE_REACTION);
    bubble(SOCKET_EVENT_MESSAGE_PINNED);
    bubble(SOCKET_EVENT_PRIVATE_HISTORY_CLEARED);
    // Group messages
    bubble(SOCKET_EVENT_GROUP_MESSAGE);
    bubble(SOCKET_EVENT_GROUP_MESSAGE_EDITED);
    bubble(SOCKET_EVENT_GROUP_MESSAGE_DELETED);
    bubble(SOCKET_EVENT_GROUP_HISTORY_CLEARED);
    // Channel
    bubble(SOCKET_EVENT_CHANNEL_MESSAGE);
  }

  private _registerStatusHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    bubble(SOCKET_EVENT_TYPING);
    bubble(SOCKET_EVENT_TYPING_DONE);
    bubble(SOCKET_EVENT_RECORDING);
    bubble(SOCKET_EVENT_LAST_SEEN);
    bubble(SOCKET_EVENT_MESSAGE_SEEN);
    bubble(SOCKET_EVENT_USER_ACTION);
    bubble(SOCKET_EVENT_GROUP_USER_ACTION);
    bubble(SOCKET_EVENT_USER_ONLINE);
    bubble(SOCKET_EVENT_USER_OFFLINE);
    bubble(SOCKET_EVENT_STARS_RECEIVED);
    // Legacy WoWonder status change (parses HTML)
    this.socket.on('user_status_change', (data: unknown) =>
      this.emitter.emit('user_status_change', data),
    );

    // Live location
    bubble(SOCKET_EVENT_LIVE_LOCATION_START);
    bubble(SOCKET_EVENT_LIVE_LOCATION_UPDATE);
    bubble(SOCKET_EVENT_LIVE_LOCATION_STOP);
  }

  private _registerGroupHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    bubble(SOCKET_EVENT_GROUP_TYPING);
    bubble(SOCKET_EVENT_GROUP_TYPING_DONE);
  }

  private _registerChannelHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    bubble(SOCKET_EVENT_CHANNEL_POST_CREATED);
    bubble(SOCKET_EVENT_CHANNEL_POST_UPDATED);
    bubble(SOCKET_EVENT_CHANNEL_POST_DELETED);
    bubble(SOCKET_EVENT_CHANNEL_COMMENT_ADDED);
    bubble(SOCKET_EVENT_CHANNEL_STREAM_STARTED);
    bubble(SOCKET_EVENT_CHANNEL_STREAM_ENDED);
    bubble(SOCKET_EVENT_CHANNEL_TYPING);
  }

  private _registerStoryHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    bubble(SOCKET_EVENT_STORY_CREATED);
    bubble(SOCKET_EVENT_STORY_DELETED);
    bubble(SOCKET_EVENT_STORY_COMMENT_ADDED);
  }

  private _registerE2EEHandlers(): void {
    if (!this.socket) return;

    const bubble = (event: string) => {
      this.socket!.on(event, (data: unknown) => this.emitter.emit(event, data));
    };

    bubble(SOCKET_EVENT_SIGNAL_IDENTITY_CHANGED);
    bubble(SOCKET_EVENT_GROUP_MEMBER_JOINED);
    bubble(SOCKET_EVENT_GROUP_MEMBER_LEFT);
  }

  /**
   * Authenticate the socket by emitting the 'join' event with the session hash.
   * Must be called after every (re-)connect event.
   */
  private _authenticateSocket(): void {
    if (!this.socket?.connected || !this.token) return;
    this.socket.emit(SOCKET_EVENT_AUTH, { user_id: this.token });
  }

  /**
   * Schedule a reconnection attempt using exponential back-off.
   * Stops after MAX_RECONNECT_ATTEMPTS failures.
   */
  private _scheduleReconnect(): void {
    if (this.isManualDisconnect) return;
    if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      console.warn('[SocketService] Max reconnect attempts reached');
      this.emitter.emit('socket:reconnect_failed');
      return;
    }

    this._clearReconnectTimer();
    this.reconnectAttempts += 1;
    const delay = RECONNECT_BASE_DELAY_MS * 2 ** (this.reconnectAttempts - 1);
    console.log(
      `[SocketService] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`,
    );

    this.reconnectTimer = setTimeout(() => {
      if (!this.isManualDisconnect && this.token) {
        this.socket?.disconnect();
        this.socket = null;
        this._createSocket();
      }
    }, delay);
  }

  private _clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}

// ─────────────────────────────────────────────────────────────
// SINGLETON EXPORT
// ─────────────────────────────────────────────────────────────

/** Application-wide Socket.IO singleton. Import and use directly. */
export const socketService = new SocketService();
