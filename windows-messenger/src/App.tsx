import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import type { Socket } from 'socket.io-client';
import {
  archiveChat, AuthError, clearHistory, createChannel, createGroup, createStory,
  createNodeApiShim, deleteConversation, deleteMessage, editMessage,
  getIceServers, initiateCall, endCall, loadChannels, loadChats,
  loadGroups, loadMessages, loadMoreMessages, loadStories, login, loginByPhone,
  markSeen, muteChat, normaliseMessage, pinChat, pinMessage, reactToMessage, registerAccount,
  sendMessage, sendMessageWithMedia, TURN_FALLBACK
} from './api';
import { SignalService, CIPHER_VERSION_SIGNAL } from './signalService';
import { signalSelfTest } from './signal';
import {
  createChatSocket, emitChatClose, emitChatOpen, emitCallSignal,
  emitTyping, type CallSignalPayload
} from './socket';
import { createLocalVideoStream, createPeerConnection } from './webrtc';
import type {
  ActiveSection, CallState, ChatItem, ChannelItem, GroupItem,
  MessageItem, ReplyTarget, Session, StoryItem
} from './types';

// ─── Constants ────────────────────────────────────────────────────────────────

const SESSION_KEY = 'wm_windows_session';

const EMOJI_QUICK = ['👍','❤️','😂','😮','😢','😡','🔥','👏','🎉','💯'];

// ─── Helpers ──────────────────────────────────────────────────────────────────

function asText(v: unknown, fb = ''): string {
  if (v == null) return fb;
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  if (typeof v === 'object') { const t = (v as Record<string, unknown>).text; return typeof t === 'string' ? t : fb; }
  return fb;
}

function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('') || '?';
}

function formatTime(timeText?: string, timeUnix?: number): string {
  if (timeText && timeText !== 'now') return timeText;
  const ts = timeUnix ? timeUnix * 1000 : Date.now();
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatChatTime(timeText?: string, timeUnix?: number): string {
  const ts = timeUnix ? timeUnix * 1000 : (timeText ? NaN : Date.now());
  const d  = isNaN(ts) ? new Date() : new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const diff = (now.getTime() - d.getTime()) / 86400000;
  if (diff < 7) return d.toLocaleDateString([], { weekday: 'short' });
  return d.toLocaleDateString([], { day: '2-digit', month: '2-digit' });
}

const AVATAR_PALETTE = ['#1f6feb','#388bfd','#8b5cf6','#ec4899','#f97316','#10b981','#06b6d4','#e11d48'];
function avatarColor(name: string): string {
  let h = 0; for (const c of name) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff;
  return AVATAR_PALETTE[Math.abs(h) % AVATAR_PALETTE.length];
}

/** Returns a human-readable preview of last_message, masking encrypted blobs. */
function previewLastMessage(raw: unknown): string {
  const t = asText(raw, '');
  if (!t) return 'No messages';
  // Base64-encoded ciphertext: long, no spaces, only base64 chars
  if (t.length > 30 && !/\s/.test(t) && /^[A-Za-z0-9+/=]+$/.test(t)) return '🔒 Encrypted message';
  return t.slice(0, 50);
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function Avatar({ name, src, size = 40, online }: { name: string; src?: string; size?: number; online?: boolean }) {
  return (
    <div className="avatar-wrap" style={{ width: size, height: size, flexShrink: 0 }}>
      {src
        ? <img className="avatar-img" src={src} alt={name} style={{ width: size, height: size }} />
        : <div className="avatar-letter" style={{ width: size, height: size, background: avatarColor(name), fontSize: size * 0.38 }}>
            {initials(name)}
          </div>
      }
      {online !== undefined && (
        <span className={`avatar-dot ${online ? 'online' : 'offline'}`} />
      )}
    </div>
  );
}

function TypingDots() {
  return (
    <div className="typing-indicator">
      <span /><span /><span />
    </div>
  );
}

function Bubble({
  msg, isOwn, onReply, onEdit, onDelete, onReact, userId
}: {
  msg: MessageItem; isOwn: boolean; userId: number;
  onReply: (m: MessageItem) => void;
  onEdit:  (m: MessageItem) => void;
  onDelete: (m: MessageItem) => void;
  onReact: (m: MessageItem, emoji: string) => void;
}) {
  const [showActions, setShowActions] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);

  const isEncrypted = msg.cipher_version === CIPHER_VERSION_SIGNAL;
  const mediaIsImage = msg.media_type === 'image' || (!msg.media_type && msg.media && /\.(jpg|jpeg|png|gif|webp)$/i.test(msg.media));

  return (
    <div
      className={`bubble-row ${isOwn ? 'own' : ''}`}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => { setShowActions(false); setShowEmojiPicker(false); }}
    >
      {showActions && (
        <div className={`bubble-actions ${isOwn ? 'actions-left' : 'actions-right'}`}>
          <button className="action-btn" title="Reply" onClick={() => onReply(msg)}>↩</button>
          <button className="action-btn" title="React" onClick={() => setShowEmojiPicker(v => !v)}>😀</button>
          {isOwn && <button className="action-btn" title="Edit" onClick={() => onEdit(msg)}>✎</button>}
          {isOwn && <button className="action-btn" title="Delete" onClick={() => onDelete(msg)}>🗑</button>}
          {showEmojiPicker && (
            <div className="emoji-picker">
              {EMOJI_QUICK.map(e => (
                <button key={e} className="emoji-btn" onClick={() => { onReact(msg, e); setShowEmojiPicker(false); }}>{e}</button>
              ))}
            </div>
          )}
        </div>
      )}

      <div className={`bubble ${isOwn ? 'own' : ''} ${msg._decryptFailed ? 'decrypt-failed' : ''} ${msg._pending ? 'pending' : ''}`}>
        {/* Reply quote */}
        {msg.reply_to && (
          <div className="reply-quote">
            <div className="reply-bar" />
            <div className="reply-content">
              <span className="reply-from">{msg.reply_to.from_id === userId ? 'You' : 'User'}</span>
              <span className="reply-text">{asText(msg.reply_to.text, '[media]').slice(0, 80)}</span>
            </div>
          </div>
        )}

        {/* Media */}
        {msg.media && (
          <div className="bubble-media">
            {mediaIsImage
              ? <img src={msg.media} alt="media" className="media-img" onClick={() => window.open(msg.media, '_blank')} />
              : msg.media_type === 'video'
                ? <video src={msg.media} controls className="media-video" />
                : msg.media_type === 'audio' || msg.media_type === 'voice'
                  ? <audio src={msg.media} controls className="media-audio" />
                  : <a href={msg.media} target="_blank" rel="noreferrer" className="media-file">
                      📎 {msg.media_filename ?? 'Download file'}
                    </a>
            }
          </div>
        )}

        {/* Text */}
        {msg._decryptFailed ? (
          <p className="bubble-text decrypt-msg">🔒 Encrypted message</p>
        ) : msg.text ? (
          <p className="bubble-text">
            {msg.text}
            {msg.is_edited && <span className="edited-mark"> (edited)</span>}
          </p>
        ) : msg.cipher_version === CIPHER_VERSION_SIGNAL ? (
          <p className="bubble-text decrypt-msg">🔒 Encrypted message</p>
        ) : null}

        {/* Footer: time + status */}
        <div className="bubble-footer">
          {isEncrypted && <span className="lock-icon" title="End-to-end encrypted">🔒</span>}
          <time className="bubble-time">{formatTime(msg.time_text, msg.time)}</time>
          {isOwn && <span className="seen-tick">{msg.is_seen ? '✓✓' : '✓'}</span>}
        </div>

        {/* Reactions */}
        {(msg.reactions ?? []).length > 0 && (
          <div className="reactions">
            {msg.reactions!.map((r, i) => (
              <button key={i} className={`reaction-chip ${r.user_ids.includes(userId) ? 'mine' : ''}`}
                onClick={() => onReact(msg, r.emoji)}>
                {r.emoji} {r.count}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Main App ─────────────────────────────────────────────────────────────────

export default function App() {
  // ── Auth state ─────────────────────────────────────────────────────────────
  const [session, setSession]       = useState<Session | null>(null);
  const [authMode, setAuthMode]     = useState<'login' | 'register'>('login');
  const [loginBy, setLoginBy]       = useState<'username' | 'phone'>('username');
  const [authLoading, setAuthLoading] = useState(false);

  const [username, setUsername]     = useState('');
  const [phone, setPhone]           = useState('');
  const [email, setEmail]           = useState('');
  const [password, setPassword]     = useState('');
  const [authError, setAuthError]   = useState('');

  // ── Navigation ────────────────────────────────────────────────────────────
  const [section, setSection]       = useState<ActiveSection>('chats');

  // ── Real-time ─────────────────────────────────────────────────────────────
  const [socket, setSocket]         = useState<Socket | null>(null);
  const [socketStatus, setSocketStatus] = useState('Offline');
  const [typingUsers, setTypingUsers]   = useState<Set<number>>(new Set());
  const [onlineUsers, setOnlineUsers]   = useState<Set<number>>(new Set());

  // ── Chat list ─────────────────────────────────────────────────────────────
  const [chats, setChats]           = useState<ChatItem[]>([]);
  const [groups, setGroups]         = useState<GroupItem[]>([]);
  const [channels, setChannels]     = useState<ChannelItem[]>([]);
  const [stories, setStories]       = useState<StoryItem[]>([]);

  // ── Active chat ───────────────────────────────────────────────────────────
  const [selectedChat, setSelectedChat]   = useState<ChatItem | null>(null);
  const [messages, setMessages]           = useState<MessageItem[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [msgLoadError, setMsgLoadError]       = useState(false);
  const [hasMore, setHasMore]             = useState(false);

  // ── Composer ──────────────────────────────────────────────────────────────
  const [newMessage, setNewMessage]   = useState('');
  const [pendingMedia, setPendingMedia] = useState<File | null>(null);
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null);
  const [editingMsg, setEditingMsg]   = useState<MessageItem | null>(null);

  // ── Create forms ──────────────────────────────────────────────────────────
  const [newGroupName, setNewGroupName]           = useState('');
  const [newChannelName, setNewChannelName]       = useState('');
  const [newChannelDesc, setNewChannelDesc]       = useState('');
  const [newStoryFile, setNewStoryFile]           = useState<File | null>(null);

  // ── Call ──────────────────────────────────────────────────────────────────
  const [callState, setCallState]   = useState<CallState>({ phase: 'idle' });
  const peerRef                     = useRef<RTCPeerConnection | null>(null);

  // ── Sidebar search ────────────────────────────────────────────────────────
  const [searchQuery, setSearchQuery]   = useState('');

  // ── Send error banner ─────────────────────────────────────────────────────
  const [sendError, setSendError]       = useState('');

  // ── Signal service ────────────────────────────────────────────────────────
  const signalRef = useRef<SignalService | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const composerRef    = useRef<HTMLTextAreaElement>(null);
  const typingTimer    = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ─── Session restore ──────────────────────────────────────────────────────

  useEffect(() => {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return;
    try {
      const s = JSON.parse(raw) as Session;
      if (s.token) setSession(s);
    } catch { localStorage.removeItem(SESSION_KEY); }
  }, []);

  // ─── Signal service init ──────────────────────────────────────────────────

  useEffect(() => {
    if (!session) { signalRef.current = null; return; }
    const svc = SignalService.getInstance(createNodeApiShim(session.token));
    signalRef.current = svc;
    // Run crypto self-test on first login to catch any broken primitives early
    signalSelfTest().then(ok => {
      if (!ok) console.error('[Signal] CRYPTO SELF-TEST FAILED — decryption will not work in this environment!');
    });
    svc.ensureRegistered().catch(console.error);
  }, [session]);

  // ─── Socket setup ─────────────────────────────────────────────────────────

  useEffect(() => {
    if (!session) return;

    const s = createChatSocket(session.token, {
      onStatus: setSocketStatus,

      onMessage: async (rawMsg) => {
        // Socket events arrive as raw server JSON — normalise before decrypting
        // so Signal messages have text_encrypted set (not text) and from_id/to_id
        // are always populated regardless of which field names the server uses.
        const msg = normaliseMessage(rawMsg as unknown as Record<string, unknown>);
        const decrypted = await tryDecryptMessage(msg);
        setMessages(prev => {
          // De-duplicate by id
          if (prev.some(m => m.id === decrypted.id)) return prev;
          if (decrypted.from_id === selectedChatRef.current?.user_id ||
              decrypted.to_id   === selectedChatRef.current?.user_id) {
            return [...prev, decrypted];
          }
          return prev;
        });
        // Update last message in chat list
        setChats(prev => prev.map(c =>
          c.user_id === (decrypted.from_id === session.userId ? decrypted.to_id : decrypted.from_id)
            ? { ...c, last_message: asText(decrypted.text, '[media]'), time: 'now' }
            : c
        ));
      },

      onTyping:     e => { if (e.sender_id !== session.userId) setTypingUsers(s => new Set([...s, e.sender_id])); },
      onTypingDone: e => { setTypingUsers(s => { const n = new Set(s); n.delete(e.sender_id); return n; }); },
      onUserOnline:  e => setOnlineUsers(s => new Set([...s, e.user_id])),
      onUserOffline: e => setOnlineUsers(s => { const n = new Set(s); n.delete(e.user_id); return n; }),

      onMessageSeen: e => {
        if (e.sender_id === session.userId) {
          setMessages(prev => prev.map(m =>
            m.id <= e.last_seen_id && m.from_id === session.userId ? { ...m, is_seen: true } : m
          ));
        }
      },

      onReaction: e => {
        setMessages(prev => prev.map(m => {
          if (m.id !== e.msg_id) return m;
          const existing = (m.reactions ?? []).find(r => r.emoji === e.emoji);
          if (existing) {
            return { ...m, reactions: m.reactions!.map(r =>
              r.emoji === e.emoji
                ? { ...r, count: r.count + 1, user_ids: [...r.user_ids, e.user_id] }
                : r
            )};
          }
          return { ...m, reactions: [...(m.reactions ?? []), { emoji: e.emoji, count: 1, user_ids: [e.user_id] }] };
        }));
      },

      onPinned: e => {
        setMessages(prev => prev.map(m => m.id === e.msg_id ? { ...m, is_pinned: e.pinned } : m));
      },

      onCallSignal: handleCallSignal,

      onIdentityChanged: (e) => {
        // Contact reinstalled their app — their old DR session is now invalid.
        // Clear it here so X3DH is re-run when the next message arrives.
        if (signalRef.current) {
          signalRef.current.clearSessionFor(e.user_id);
          console.info('[Signal] Cleared stale DR session for user', e.user_id,
            '(they re-registered their identity key)');
        }
      },
    });

    setSocket(s);
    return () => { s.disconnect(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  // Keep a ref to selectedChat so socket callbacks can read it
  const selectedChatRef = useRef<ChatItem | null>(null);
  useEffect(() => { selectedChatRef.current = selectedChat; }, [selectedChat]);

  // ─── Load initial data ────────────────────────────────────────────────────

  useEffect(() => {
    if (!session) return;
    // Each promise is individually resilient — a failure in one (e.g. HTTP 500 stories)
    // does NOT abort the others. Only AuthError propagates to trigger logout.
    const safe = <T,>(p: Promise<T>) =>
      p.catch((err: unknown) => { if (err instanceof AuthError) throw err; return null; });

    Promise.all([
      safe(loadChats(session.token, session.userId).then(r => {
        const list = r?.data ?? [];
        setChats(list);
        if (list.length > 0 && !selectedChat) setSelectedChat(list[0]);
      })),
      safe(loadGroups(session.token).then(r    => setGroups(r?.data ?? []))),
      safe(loadChannels(session.token).then(r  => setChannels(r?.data ?? []))),
      safe(loadStories(session.token).then(r   => setStories((r as {data?: StoryItem[]; stories?: StoryItem[]})?.data ?? (r as {data?: StoryItem[]; stories?: StoryItem[]})?.stories ?? [])))
    ]).catch(err => {
      if (err instanceof AuthError) { logout(); return; }
      console.error('Initial load error:', err);
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  // ─── Load messages when chat changes ─────────────────────────────────────

  useEffect(() => {
    if (!session || !selectedChat) return;

    emitChatOpen(socket, selectedChat.user_id);
    setMsgLoadError(false);
    setMessagesLoading(true);
    setMessages([]);

    let cancelled = false;

    const run = async () => {
      // Retry up to 3 times: immediately, then +2 s, then +5 s
      const delays = [0, 2000, 5000];
      for (const ms of delays) {
        if (cancelled) return;
        if (ms > 0) await new Promise(r => setTimeout(r, ms));
        if (cancelled) return;
        try {
          const r = await loadMessages(session.token, selectedChat.user_id, session.userId);
          if (cancelled) return;
          const decrypted = await Promise.all((r.messages ?? []).map(tryDecryptMessage));
          if (cancelled) return;
          setMessages(decrypted);
          setHasMore(decrypted.length >= 40);
          const lastMsg = decrypted[decrypted.length - 1];
          if (lastMsg) markSeen(session.token, selectedChat.user_id, lastMsg.id).catch(() => {});
          setMessagesLoading(false);
          return;
        } catch (e) {
          console.warn('[loadMessages] attempt failed:', e);
        }
      }
      if (!cancelled) { setMsgLoadError(true); setMessagesLoading(false); }
    };
    run();

    return () => { cancelled = true; emitChatClose(socket, selectedChat.user_id); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, selectedChat?.user_id]);

  // ─── Auto-scroll to bottom ────────────────────────────────────────────────

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  // ─── Signal decryption helper ─────────────────────────────────────────────

  const tryDecryptMessage = useCallback(async (msg: MessageItem): Promise<MessageItem> => {
    if (msg.cipher_version !== CIPHER_VERSION_SIGNAL) return msg;
    const svc = signalRef.current;
    if (!svc) return { ...msg, _decryptFailed: true };

    // Check plaintext cache first — covers sent messages and previously decrypted ones
    const cached = svc.getCachedDecryptedMessage(msg.id);
    if (cached) return { ...msg, text: cached };

    // Own sent messages can't be decrypted via DR (no self-session).
    // They should be in cache (stored when sent); if missing, mark as failed.
    if (session && msg.from_id === session.userId) {
      return { ...msg, _decryptFailed: true };
    }

    if (!msg.text_encrypted || !msg.iv || !msg.tag || !msg.signal_header) return msg;

    const senderId  = msg.from_id;
    const plaintext = await svc.decryptIncoming(senderId, msg.text_encrypted, msg.iv, msg.tag, msg.signal_header);
    if (plaintext === null) return { ...msg, _decryptFailed: true };

    svc.cacheDecryptedMessage(msg.id, plaintext);
    return { ...msg, text: plaintext };
  }, [session]);

  // ─── Auth ─────────────────────────────────────────────────────────────────

  async function handleAuth(e: FormEvent) {
    e.preventDefault();
    setAuthError('');
    setAuthLoading(true);
    try {
      if (authMode === 'register') {
        const r = await registerAccount({ username, email, phoneNumber: phone, password });
        if (r.api_status === '200' && r.access_token && r.user_id) {
          const s = { token: r.access_token, userId: r.user_id, username };
          localStorage.setItem(SESSION_KEY, JSON.stringify(s));
          setSession(s);
          return;
        }
        setAuthError(r.message ?? 'Registration failed.');
        return;
      }
      const r = loginBy === 'username'
        ? await login(username.trim(), password)
        : await loginByPhone(phone.trim(), password);

      if (r.api_status === '200' && r.access_token && r.user_id) {
        const s = { token: r.access_token, userId: r.user_id, username: loginBy === 'username' ? username.trim() : phone.trim() };
        localStorage.setItem(SESSION_KEY, JSON.stringify(s));
        setSession(s);
        return;
      }
      setAuthError(r.message ?? 'Auth failed.');
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setAuthLoading(false);
    }
  }

  function logout() {
    localStorage.removeItem(SESSION_KEY);
    socket?.disconnect();
    setSocket(null);
    setSession(null);
    setMessages([]);
    setChats([]);
    setSelectedChat(null);
    setGroups([]);
    setChannels([]);
    setStories([]);
  }

  /** Reset ALL Signal E2EE state: clears keys + sessions from localStorage,
   *  then reloads so the app re-registers with a brand-new identity key.
   *  The server detects the new key and emits signal:identity_changed to all
   *  contacts — they will clear stale DR sessions and resend X3DH on their
   *  next message, which Windows can then decrypt correctly. */
  function resetSignalState() {
    if (!signalRef.current) return;
    signalRef.current.clearAllSignalState();
    SignalService.resetInstance();
    // Re-register immediately with new keys
    const svc = SignalService.getInstance(createNodeApiShim(session!.token));
    signalRef.current = svc;
    svc.ensureRegistered()
      .then(() => console.error('[Signal] Re-registration complete — contacts will receive signal:identity_changed'))
      .catch(console.error);
  }

  // ─── Send message ─────────────────────────────────────────────────────────

  async function handleSend(e?: FormEvent) {
    e?.preventDefault();
    if (!session || !selectedChat) return;
    const text = newMessage.trim();
    if (!text && !pendingMedia && !editingMsg) return;

    // Handle edit
    if (editingMsg) {
      if (!text) return;
      await editMessage(session.token, editingMsg.id, text).catch(console.error);
      setMessages(prev => prev.map(m => m.id === editingMsg.id ? { ...m, text, is_edited: true } : m));
      setEditingMsg(null);
      setNewMessage('');
      return;
    }

    const optimisticId = -(Date.now());
    const optimistic: MessageItem = {
      id:       optimisticId,
      from_id:  session.userId,
      to_id:    selectedChat.user_id,
      text:     pendingMedia ? `[${pendingMedia.name}]` : text,
      time_text: 'now',
      time:     Math.floor(Date.now() / 1000),
      reply_to: replyTarget ? { id: replyTarget.id, from_id: replyTarget.from_id, text: replyTarget.text } : undefined,
      _pending: true
    };
    setMessages(prev => [...prev, optimistic]);
    setNewMessage('');
    setPendingMedia(null);
    setReplyTarget(null);

    try {
      if (pendingMedia) {
        await sendMessageWithMedia(session.token, selectedChat.user_id, text, pendingMedia, session.userId);
      } else {
        // Try Signal encryption
        let signalPayload: Parameters<typeof sendMessage>[4] = undefined;
        const svc = signalRef.current;
        if (svc) {
          const enc = await svc.encryptForSend(selectedChat.user_id, text);
          if (enc) {
            signalPayload = { ciphertext: enc.ciphertext, iv: enc.iv, tag: enc.tag, signalHeader: enc.signalHeader };
          }
        }
        const result = await sendMessage(
          session.token, selectedChat.user_id, text, session.userId,
          signalPayload, replyTarget?.id
        );
        // Cache plaintext for the real server ID so we can display it
        // after reload without needing to decrypt our own outgoing message
        if (signalPayload && result.id && signalRef.current) {
          signalRef.current.cacheDecryptedMessage(result.id, text);
        }
        // Replace optimistic message
        setMessages(prev => prev.map(m =>
          m.id === optimisticId
            ? { ...m, id: result.id ?? optimisticId, _pending: false, text, cipher_version: signalPayload ? CIPHER_VERSION_SIGNAL : undefined }
            : m
        ));
      }
    } catch (err) {
      setMessages(prev => prev.filter(m => m.id !== optimisticId));
      console.error('Send failed:', err);
      setSendError('Failed to send message. Server may be unavailable.');
      setTimeout(() => setSendError(''), 4000);
    }
  }

  // ─── Typing emit ──────────────────────────────────────────────────────────

  function handleComposerInput(text: string) {
    setNewMessage(text);
    if (!selectedChat || !socket) return;
    emitTyping(socket, selectedChat.user_id);
    if (typingTimer.current) clearTimeout(typingTimer.current);
    typingTimer.current = setTimeout(() => emitTyping(socket, selectedChat.user_id, true), 3000);
  }

  // ─── Load more messages ───────────────────────────────────────────────────

  async function handleLoadMore() {
    if (!session || !selectedChat || !hasMore || messages.length === 0) return;
    const oldestId = messages[0].id;
    const r = await loadMoreMessages(session.token, selectedChat.user_id, oldestId);
    const more = await Promise.all((r.messages ?? []).map(tryDecryptMessage));
    setMessages(prev => [...more, ...prev]);
    setHasMore(more.length >= 40);
  }

  // ─── Message actions ──────────────────────────────────────────────────────

  function handleReply(msg: MessageItem) {
    setReplyTarget({ id: msg.id, from_id: msg.from_id, text: asText(msg.text, '[media]') });
    composerRef.current?.focus();
  }

  function handleEditStart(msg: MessageItem) {
    setEditingMsg(msg);
    setNewMessage(asText(msg.text, ''));
    composerRef.current?.focus();
  }

  async function handleDelete(msg: MessageItem) {
    if (!session) return;
    await deleteMessage(session.token, msg.id, 'for_all').catch(console.error);
    setMessages(prev => prev.filter(m => m.id !== msg.id));
  }

  async function handleReact(msg: MessageItem, emoji: string) {
    if (!session) return;
    await reactToMessage(session.token, msg.id, emoji).catch(console.error);
  }

  async function handlePin(msg: MessageItem) {
    if (!session) return;
    await pinMessage(session.token, msg.id, !msg.is_pinned).catch(console.error);
    setMessages(prev => prev.map(m => m.id === msg.id ? { ...m, is_pinned: !m.is_pinned } : m));
  }

  // ─── Groups ───────────────────────────────────────────────────────────────

  async function handleCreateGroup(e: FormEvent) {
    e.preventDefault();
    if (!session || !newGroupName.trim()) return;
    await createGroup(session.token, newGroupName.trim()).catch(console.error);
    const r = await loadGroups(session.token);
    setGroups(r.data ?? []);
    setNewGroupName('');
  }

  // ─── Channels ─────────────────────────────────────────────────────────────

  async function handleCreateChannel(e: FormEvent) {
    e.preventDefault();
    if (!session || !newChannelName.trim()) return;
    await createChannel(session.token, newChannelName.trim(), newChannelDesc.trim()).catch(console.error);
    const r = await loadChannels(session.token);
    setChannels(r.data ?? []);
    setNewChannelName('');
    setNewChannelDesc('');
  }

  // ─── Stories ──────────────────────────────────────────────────────────────

  async function handleCreateStory(e: FormEvent) {
    e.preventDefault();
    if (!session || !newStoryFile) return;
    await createStory(session.token, newStoryFile, newStoryFile.type.startsWith('video/') ? 'video' : 'image');
    const r = await loadStories(session.token);
    setStories(r.data ?? []);
    setNewStoryFile(null);
  }

  // ─── WebRTC calls ─────────────────────────────────────────────────────────

  async function startCall(type: 'audio' | 'video') {
    if (!session || !selectedChat) return;
    const servers = await getIceServers(session.userId);
    const peer    = await createPeerConnection(servers.length ? servers : TURN_FALLBACK);
    peerRef.current = peer;

    if (type === 'video') {
      try {
        const stream = await createLocalVideoStream();
        stream.getTracks().forEach(t => peer.addTrack(t, stream));
      } catch { /* no camera */ }
    }

    peer.onicecandidate = ({ candidate }) => {
      if (candidate && socket) {
        emitCallSignal(socket, { type: 'ice', to: selectedChat.user_id, from: session.userId, ice: candidate.toJSON() });
      }
    };

    const offer = await peer.createOffer();
    await peer.setLocalDescription(offer);
    emitCallSignal(socket, { type: 'offer', to: selectedChat.user_id, from: session.userId, sdp: offer });

    await initiateCall(session.token, selectedChat.user_id, type).catch(console.error);
    setCallState({ phase: 'outgoing', peer: selectedChat, type });
  }

  async function handleCallSignal(payload: CallSignalPayload) {
    if (!session) return;

    if (payload.type === 'offer') {
      const peer    = await createPeerConnection(await getIceServers(session.userId));
      peerRef.current = peer;
      await peer.setRemoteDescription(payload.sdp!);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      emitCallSignal(socket, { type: 'answer', to: payload.from, from: session.userId, sdp: answer });

      const caller: ChatItem = chats.find(c => c.user_id === payload.from) ?? {
        user_id: payload.from, name: `User ${payload.from}`
      };
      setCallState({ phase: 'incoming', peer: caller, type: 'video', offerSdp: JSON.stringify(payload.sdp) });
    }

    if (payload.type === 'answer' && peerRef.current) {
      await peerRef.current.setRemoteDescription(payload.sdp!);
    }

    if (payload.type === 'ice' && peerRef.current && payload.ice) {
      await peerRef.current.addIceCandidate(payload.ice).catch(console.error);
    }

    if (payload.type === 'end') {
      peerRef.current?.close();
      peerRef.current = null;
      setCallState({ phase: 'idle' });
    }
  }

  async function endActiveCall() {
    if (!session) return;
    const peer = callState.phase !== 'idle' ? (callState as { peer: ChatItem }).peer : null;
    peerRef.current?.close();
    peerRef.current = null;
    setCallState({ phase: 'idle' });
    if (peer) {
      emitCallSignal(socket, { type: 'end', to: peer.user_id, from: session.userId });
      await endCall(session.token, peer.user_id).catch(console.error);
    }
  }

  // ─── Filtered lists ───────────────────────────────────────────────────────

  const filteredChats = chats.filter(c =>
    !searchQuery || c.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const typingInChat = selectedChat
    ? Array.from(typingUsers).some(id => id === selectedChat.user_id)
    : false;

  // ─── Render: Auth ─────────────────────────────────────────────────────────

  if (!session) {
    return (
      <div className="auth-page">
        <div className="auth-glow" />
        <form className="auth-card" onSubmit={handleAuth}>
          <div className="auth-logo">
            <div className="auth-logo-icon">WM</div>
            <h1 className="auth-title">WorldMates</h1>
          </div>
          <p className="auth-subtitle">Sign in to your messenger</p>

          <div className="tabs">
            <button type="button" className={authMode === 'login' ? 'tab active' : 'tab'} onClick={() => setAuthMode('login')}>Sign In</button>
            <button type="button" className={authMode === 'register' ? 'tab active' : 'tab'} onClick={() => setAuthMode('register')}>Register</button>
          </div>

          {authMode === 'login' && (
            <div className="tabs" style={{ marginTop: 0 }}>
              <button type="button" className={loginBy === 'username' ? 'tab active' : 'tab'} onClick={() => setLoginBy('username')}>Username</button>
              <button type="button" className={loginBy === 'phone' ? 'tab active' : 'tab'} onClick={() => setLoginBy('phone')}>Phone</button>
            </div>
          )}

          {(authMode === 'register' || loginBy === 'username') && (
            <label className="field">
              <span>Username</span>
              <input type="text" value={username} onChange={e => setUsername(e.target.value)}
                placeholder="your_username" autoComplete="username" required={authMode === 'register' || loginBy === 'username'} />
            </label>
          )}

          {(authMode === 'register' || loginBy === 'phone') && (
            <label className="field">
              <span>Phone</span>
              <input type="tel" value={phone} onChange={e => setPhone(e.target.value)}
                placeholder="+1 234 567 8900" autoComplete="tel" required={authMode === 'register' || loginBy === 'phone'} />
            </label>
          )}

          {authMode === 'register' && (
            <label className="field">
              <span>Email</span>
              <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="email@example.com" />
            </label>
          )}

          <label className="field">
            <span>Password</span>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)}
              placeholder="••••••••" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} required />
          </label>

          {authError && <div className="auth-error">{authError}</div>}

          <button className="btn-primary" type="submit" disabled={authLoading}>
            {authLoading ? 'Please wait…' : authMode === 'register' ? 'Create account' : 'Sign in'}
          </button>
        </form>
      </div>
    );
  }

  // ─── Render: Main App ─────────────────────────────────────────────────────

  const navItems: { key: ActiveSection; icon: string; label: string }[] = [
    { key: 'chats',    icon: '💬', label: 'Chats'    },
    { key: 'groups',   icon: '👥', label: 'Groups'   },
    { key: 'channels', icon: '📢', label: 'Channels' },
    { key: 'stories',  icon: '⭕', label: 'Stories'  },
    { key: 'calls',    icon: '📞', label: 'Calls'    },
    { key: 'settings', icon: '⚙️', label: 'Settings' },
  ];

  return (
    <div className="layout">

      {/* ── Call overlay ─────────────────────────────────────────────────── */}
      {callState.phase !== 'idle' && (
        <div className="call-overlay">
          <div className="call-card">
            <Avatar name={(callState as { peer: ChatItem }).peer.name} size={72} />
            <h2>{(callState as { peer: ChatItem }).peer.name}</h2>
            <p className="call-status">
              {callState.phase === 'outgoing' ? 'Calling…' :
               callState.phase === 'incoming' ? 'Incoming call' : 'Connected'}
            </p>
            <div className="call-actions">
              {callState.phase === 'incoming' && (
                <button className="call-btn accept" onClick={() => {
                  // Accept: peer connection already set in handleCallSignal
                  setCallState(prev => ({ ...prev, phase: 'connected', duration: 0 } as CallState));
                }}>
                  ✓ Accept
                </button>
              )}
              <button className="call-btn decline" onClick={endActiveCall}>
                ✕ {callState.phase === 'incoming' ? 'Decline' : 'End'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Navigation rail ───────────────────────────────────────────────── */}
      <nav className="rail">
        <div className="rail-top">
          <div className="rail-brand">W</div>
          {navItems.map(({ key, icon, label }) => (
            <button key={key}
              className={`rail-btn ${section === key ? 'active' : ''}`}
              onClick={() => setSection(key)}
              title={label}
            >
              {icon}
            </button>
          ))}
        </div>
        <div className="rail-bottom">
          <button className="rail-btn" title="Logout" onClick={logout}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
          <Avatar name={session.username} size={34} />
        </div>
      </nav>

      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside className="sidebar">
        <div className="sidebar-head">
          <h2 className="sidebar-title">
            {section === 'chats' ? 'Messages' : section === 'groups' ? 'Groups' :
             section === 'channels' ? 'Channels' : section === 'stories' ? 'Stories' :
             section === 'calls' ? 'Calls' : 'Settings'}
          </h2>
          <div className="socket-badge" title={socketStatus}>
            <span className={`dot ${socketStatus.startsWith('Connected') ? 'green' : 'grey'}`} />
          </div>
        </div>

        {/* Search (chats only) */}
        {section === 'chats' && (
          <div className="search-box">
            <span className="search-icon">🔍</span>
            <input placeholder="Search chats…" value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)} />
          </div>
        )}

        {/* ── Chats list ─────────────────────────────────────────────────── */}
        {section === 'chats' && (
          <div className="list-scroll">
            {filteredChats.length === 0 && (
              <div className="empty-state">No chats yet</div>
            )}
            {filteredChats.map(chat => (
              <button key={chat.user_id}
                className={`chat-item ${selectedChat?.user_id === chat.user_id ? 'active' : ''}`}
                onClick={() => setSelectedChat(chat)}
              >
                <Avatar name={chat.name} src={chat.avatar} size={46} online={onlineUsers.has(chat.user_id)} />
                <div className="chat-item-body">
                  <div className="chat-item-row">
                    <span className="chat-item-name">{chat.name}</span>
                    <span className="chat-item-time">{formatChatTime(chat.time)}</span>
                  </div>
                  <div className="chat-item-row">
                    <span className="chat-item-preview">{previewLastMessage(chat.last_message)}</span>
                    {(chat.unread_count ?? 0) > 0 && (
                      <span className="unread-badge">{chat.unread_count}</span>
                    )}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}

        {/* ── Groups ────────────────────────────────────────────────────── */}
        {section === 'groups' && (
          <div className="list-scroll">
            <form className="create-form" onSubmit={handleCreateGroup}>
              <input value={newGroupName} onChange={e => setNewGroupName(e.target.value)} placeholder="New group name…" />
              <button type="submit" className="btn-sm">Create</button>
            </form>
            {groups.length === 0 && <div className="empty-state">No groups</div>}
            {groups.map(g => (
              <div key={g.id} className="list-item">
                <Avatar name={asText(g.group_name, 'G')} src={g.avatar} size={44} />
                <div className="list-item-body">
                  <span className="list-item-name">{asText(g.group_name, 'Group')}</span>
                  <span className="list-item-sub">{g.members_count ?? 0} members</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* ── Channels ──────────────────────────────────────────────────── */}
        {section === 'channels' && (
          <div className="list-scroll">
            <form className="create-form" onSubmit={handleCreateChannel}>
              <input value={newChannelName} onChange={e => setNewChannelName(e.target.value)} placeholder="Channel name…" />
              <input value={newChannelDesc} onChange={e => setNewChannelDesc(e.target.value)} placeholder="Description…" />
              <button type="submit" className="btn-sm">Create</button>
            </form>
            {channels.length === 0 && <div className="empty-state">No channels</div>}
            {channels.map(c => (
              <div key={c.id} className="list-item">
                <Avatar name={asText(c.name, 'C')} src={c.avatar_url} size={44} />
                <div className="list-item-body">
                  <span className="list-item-name">{asText(c.name, 'Channel')}</span>
                  <span className="list-item-sub">{c.subscribers_count ?? 0} subscribers</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* ── Stories ───────────────────────────────────────────────────── */}
        {section === 'stories' && (
          <div className="list-scroll">
            <form className="create-form" onSubmit={handleCreateStory}>
              <label className="file-label">
                {newStoryFile ? newStoryFile.name : 'Choose image / video'}
                <input type="file" accept="image/*,video/*" style={{ display: 'none' }}
                  onChange={e => setNewStoryFile(e.target.files?.[0] ?? null)} />
              </label>
              <button type="submit" className="btn-sm" disabled={!newStoryFile}>Upload story</button>
            </form>
            <div className="stories-grid">
              {stories.map(s => (
                <div key={s.id} className="story-thumb">
                  {s.file
                    ? s.file_type === 'video'
                      ? <video src={s.file} className="story-media" />
                      : <img src={s.file} alt="story" className="story-media" />
                    : <div className="story-placeholder">{s.user_name?.[0] ?? '?'}</div>
                  }
                  <span className="story-label">{s.user_name ?? `Story #${s.id}`}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Calls ─────────────────────────────────────────────────────── */}
        {section === 'calls' && (
          <div className="list-scroll">
            {selectedChat ? (
              <div className="call-controls">
                <p className="call-target">Call: <strong>{selectedChat.name}</strong></p>
                <button className="call-pill audio" onClick={() => startCall('audio')}>🎙 Voice call</button>
                <button className="call-pill video" onClick={() => startCall('video')}>📹 Video call</button>
              </div>
            ) : (
              <div className="empty-state">Select a chat first</div>
            )}
          </div>
        )}

        {/* ── Settings ──────────────────────────────────────────────────── */}
        {section === 'settings' && (
          <div className="list-scroll settings-panel">
            <div className="settings-item">
              <Avatar name={session.username} size={48} />
              <div>
                <div className="settings-name">{session.username}</div>
                <div className="settings-sub">User ID: {session.userId}</div>
              </div>
            </div>
            <div className="settings-section">
              <div className="settings-label">Security</div>
              <div className="settings-row">
                <span>End-to-end encryption</span>
                <span className="badge-green">Signal Protocol v3</span>
              </div>
              <div className="settings-row">
                <span>Keys registered</span>
                <span className={signalRef.current?.isRegistered() ? 'badge-green' : 'badge-red'}>
                  {signalRef.current?.isRegistered() ? '✓ Active' : '✗ Not registered'}
                </span>
              </div>
              <div className="settings-row">
                <span>OPKs in store</span>
                <span>{signalRef.current?.opkCount() ?? 0}</span>
              </div>
              <div className="settings-row" style={{flexDirection:'column',alignItems:'flex-start',gap:6}}>
                <span style={{fontSize:12,color:'var(--text-secondary)'}}>
                  If messages show as 🔒 encrypted, tap Reset to force a fresh key exchange with all contacts.
                </span>
                <button
                  className="btn-warning"
                  style={{marginTop:4}}
                  onClick={() => {
                    if (window.confirm('Reset Signal E2EE keys?\n\nThis generates new keys and forces all contacts to restart the encrypted session. Messages already received will remain undecryptable.')) {
                      resetSignalState();
                    }
                  }}
                >
                  Reset Signal E2EE keys
                </button>
              </div>
            </div>
            <div className="settings-section">
              <div className="settings-label">Connection</div>
              <div className="settings-row">
                <span>Socket status</span>
                <span>{socketStatus}</span>
              </div>
            </div>
            <button className="btn-danger" onClick={logout}>Sign out</button>
          </div>
        )}
      </aside>

      {/* ── Main chat view ────────────────────────────────────────────────── */}
      <main className="chat-main">
        {!selectedChat || (section !== 'chats' && section !== 'calls') ? (
          <div className="chat-empty">
            <div className="chat-empty-icon">💬</div>
            <h3>Select a conversation</h3>
            <p>Choose a chat from the list to start messaging</p>
          </div>
        ) : (
          <>
            {/* ── Chat header ─────────────────────────────────────────── */}
            <div className="chat-header">
              <div className="chat-header-left">
                <Avatar name={selectedChat.name} src={selectedChat.avatar} size={38}
                  online={onlineUsers.has(selectedChat.user_id)} />
                <div className="chat-header-info">
                  <span className="chat-header-name">{selectedChat.name}</span>
                  <span className="chat-header-status">
                    {typingInChat
                      ? 'typing…'
                      : onlineUsers.has(selectedChat.user_id) ? 'online' : 'offline'}
                  </span>
                </div>
              </div>
              <div className="chat-header-actions">
                <button className="icon-btn" title="Voice call" onClick={() => { setSection('calls'); startCall('audio'); }}>
                  🎙
                </button>
                <button className="icon-btn" title="Video call" onClick={() => { setSection('calls'); startCall('video'); }}>
                  📹
                </button>
                <button className="icon-btn" title="Mute" onClick={() => muteChat(session.token, selectedChat.user_id, true)}>
                  🔕
                </button>
                <button className="icon-btn" title="Archive" onClick={() => archiveChat(session.token, selectedChat.user_id, true)}>
                  📦
                </button>
                <button className="icon-btn danger" title="Delete conversation"
                  onClick={() => { if (window.confirm('Delete conversation?')) deleteConversation(session.token, selectedChat.user_id); }}>
                  🗑
                </button>
              </div>
            </div>

            {/* ── Messages ─────────────────────────────────────────────── */}
            <div className="messages-scroll">
              {hasMore && (
                <button className="load-more" onClick={handleLoadMore}>Load earlier messages</button>
              )}

              {messagesLoading && (
                <div className="messages-loading">
                  <div className="spinner" />
                </div>
              )}

              {!messagesLoading && msgLoadError && messages.length === 0 && (
                <div className="msg-load-error">
                  <p>Couldn't load messages.<br />Server may be temporarily unavailable.</p>
                  <button className="retry-btn" onClick={() => {
                    setMsgLoadError(false);
                    setMessagesLoading(true);
                    const delays = [0, 2000, 5000];
                    let done = false;
                    const attempt = async () => {
                      for (const ms of delays) {
                        if (ms > 0) await new Promise(r => setTimeout(r, ms));
                        try {
                          const r = await loadMessages(session.token, selectedChat.user_id, session.userId);
                          const decrypted = await Promise.all((r.messages ?? []).map(tryDecryptMessage));
                          setMessages(decrypted);
                          setHasMore(decrypted.length >= 40);
                          done = true;
                          setMessagesLoading(false);
                          return;
                        } catch (e) { console.warn('[retry]', e); }
                      }
                      if (!done) { setMsgLoadError(true); setMessagesLoading(false); }
                    };
                    attempt();
                  }}>Retry</button>
                </div>
              )}

              {messages.map((msg, idx) => {
                const isOwn = msg.from_id === session.userId;
                const prevMsg = messages[idx - 1];
                const showAvatar = !isOwn && (!prevMsg || prevMsg.from_id !== msg.from_id);

                return (
                  <div key={msg.id} className={`msg-row ${isOwn ? 'own' : ''}`}>
                    {!isOwn && (
                      <div style={{ width: 28, flexShrink: 0, alignSelf: 'flex-end', paddingBottom: 4 }}>
                        {showAvatar
                          ? <Avatar name={selectedChat.name} src={selectedChat.avatar} size={28} />
                          : null
                        }
                      </div>
                    )}
                    <Bubble
                      msg={msg}
                      isOwn={isOwn}
                      userId={session.userId}
                      onReply={handleReply}
                      onEdit={handleEditStart}
                      onDelete={handleDelete}
                      onReact={handleReact}
                    />
                  </div>
                );
              })}

              {typingInChat && (
                <div className="msg-row">
                  <div style={{ width: 28, flexShrink: 0 }} />
                  <div className="bubble">
                    <TypingDots />
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* ── Send error banner ─────────────────────────────────────── */}
            {sendError && (
              <div className="send-error-banner">{sendError}</div>
            )}

            {/* ── Composer ──────────────────────────────────────────────── */}
            <div className="composer">
              {/* Reply/edit banner */}
              {(replyTarget || editingMsg) && (
                <div className="composer-banner">
                  <div className="composer-banner-line" />
                  <div className="composer-banner-content">
                    <span className="composer-banner-label">
                      {editingMsg ? '✎ Editing' : `↩ Reply to ${replyTarget!.from_id === session.userId ? 'yourself' : selectedChat.name}`}
                    </span>
                    <span className="composer-banner-text">
                      {editingMsg ? asText(editingMsg.text, '').slice(0, 60) : replyTarget!.text.slice(0, 60)}
                    </span>
                  </div>
                  <button className="composer-banner-close" onClick={() => { setReplyTarget(null); setEditingMsg(null); setNewMessage(''); }}>✕</button>
                </div>
              )}

              {/* File attachment preview */}
              {pendingMedia && (
                <div className="attachment-preview">
                  <span>📎 {pendingMedia.name}</span>
                  <button onClick={() => setPendingMedia(null)}>✕</button>
                </div>
              )}

              <div className="composer-row">
                {/* Attach button */}
                <label className="icon-btn attach-btn" title="Attach file">
                  📎
                  <input type="file" style={{ display: 'none' }}
                    accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar"
                    onChange={e => setPendingMedia(e.target.files?.[0] ?? null)} />
                </label>

                {/* Message input */}
                <textarea
                  ref={composerRef}
                  className="composer-input"
                  placeholder={editingMsg ? 'Edit message…' : 'Write a message…'}
                  value={newMessage}
                  rows={1}
                  onChange={e => handleComposerInput(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
                  }}
                />

                {/* Send button */}
                <button
                  className={`send-btn ${newMessage.trim() || pendingMedia ? 'active' : ''}`}
                  onClick={() => handleSend()}
                  disabled={!newMessage.trim() && !pendingMedia && !editingMsg}
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                    <path d="M22 2L11 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                    <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </button>
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
