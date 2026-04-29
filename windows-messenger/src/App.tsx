import React, { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { t, initLang, setLang, getLang, translateSocketStatus, type Lang } from './i18n';
import type { Socket } from 'socket.io-client';
import {
  archiveChat, AuthError, clearHistory,
  blockUser, unblockUser, loadBlockedUsers,
  createChannel, createChannelPost, deleteChannelPost, loadChannelPosts, loadMoreChannelPosts, markChannelPostViewed, reactToChannelPost, searchChannels, voteChannelPoll,
  loadChannelComments, addChannelComment, deleteChannelComment, reactToChannelComment,
  createGroup, createStory, searchGroups,
  createNodeApiShim, deleteConversation, deleteMessage, deleteGroupMessage, editMessage, editGroupMessage,
  getIceServers, initiateCall, endCall, loadChannels, loadChats, loadArchivedChats,
  loadGroups, loadGroupMessages, loadMoreGroupMessages, loadMessages, loadMoreMessages, loadStories,
  login, loginByPhone, markGroupSeen, markSeen, markStorySeen,
  muteChat, normaliseMessage, pinChat, pinMessage,
  getMyProfile, updateMyProfile, uploadAvatar,
  loadCallHistory, deleteCallRecord, clearCallHistory,
  loadPrivacySettings, updatePrivacySettings,
  reactToMessage, reactToGroupMessage, registerAccount,
  searchMessages,
  sendMessage, sendGroupMessage, sendMessageWithMedia, sendVoiceMessage, TURN_FALLBACK,
  uploadMedia,
  type UserProfile,
} from './api';
import type { ChannelPost, ChannelPoll, ChannelComment, PollOption } from './types';
import { SignalService, CIPHER_VERSION_SIGNAL } from './signalService';
import { signalSelfTest } from './signal';
import {
  createChatSocket, emitChatClose, emitChatOpen, emitCallSignal,
  emitTyping, type CallSignalPayload
} from './socket';
import { createLocalVideoStream, createPeerConnection } from './webrtc';
import type {
  ActiveSection, CallHistoryItem, CallState, ChatItem, ChannelItem, GroupItem,
  MessageItem, PrivacySettings, ReplyTarget, Session, StoryItem
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
  const text = asText(raw, '');
  if (!text) return t('misc.noMessages');
  // Base64-encoded ciphertext: long, no spaces, only base64 chars
  if (text.length > 30 && !/\s/.test(text) && /^[A-Za-z0-9+/=]+$/.test(text)) return t('bubble.encrypted');
  return text.slice(0, 50);
}

// ─── Notification sound ───────────────────────────────────────────────────────
// Two-tone beep synthesised via Web Audio API — no external asset files needed.

function playNotificationBeep(): void {
  try {
    const ctx = new AudioContext();

    function scheduleTones() {
      function tone(freq: number, startAt: number, duration: number, vol = 0.22) {
        const osc  = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.type = 'sine';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0, ctx.currentTime + startAt);
        gain.gain.linearRampToValueAtTime(vol, ctx.currentTime + startAt + 0.01);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + startAt + duration);
        osc.start(ctx.currentTime + startAt);
        osc.stop(ctx.currentTime + startAt + duration);
        return osc;
      }
      tone(880,  0,    0.22); // A5
      const last = tone(1047, 0.18, 0.28); // C6
      last.onended = () => { ctx.close().catch(() => {}); };
    }

    // Chromium/Electron may start AudioContext in 'suspended' state when there
    // has been no prior user gesture.  Resume first, then schedule the tones.
    if (ctx.state === 'suspended') {
      ctx.resume().then(scheduleTones).catch(() => {});
    } else {
      scheduleTones();
    }
  } catch { /* AudioContext unavailable */ }
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
  msg, isOwn, onReply, onEdit, onDelete, onReact, onOpenMedia, userId
}: {
  msg: MessageItem; isOwn: boolean; userId: number;
  onReply: (m: MessageItem) => void;
  onEdit:  (m: MessageItem) => void;
  onDelete: (m: MessageItem) => void;
  onReact: (m: MessageItem, emoji: string) => void;
  onOpenMedia: (src: string) => void;
}) {
  const [showActions, setShowActions] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);

  const isEncrypted = msg.cipher_version === CIPHER_VERSION_SIGNAL;
  const mediaIsImage = msg.media_type === 'image' || (!msg.media_type && msg.media && /\.(jpg|jpeg|png|gif|webp)$/i.test(msg.media));
  const isVoice = msg.media_type === 'voice' || msg.media_type === 'audio'
    || !!(msg.media_filename?.match(/^VOICE_/i))
    || !!(msg.media && /\/VOICE_[^/]*\.(webm|ogg|m4a|opus|aac)/i.test(msg.media));

  return (
    <div
      className={`bubble-row ${isOwn ? 'own' : ''}`}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => { setShowActions(false); setShowEmojiPicker(false); }}
    >
      {showActions && (
        <div className={`bubble-actions ${isOwn ? 'actions-left' : 'actions-right'}`}>
          <button className="action-btn" title={t('bubble.reply')} onClick={() => onReply(msg)}>↩</button>
          <button className="action-btn" title={t('bubble.react')} onClick={() => setShowEmojiPicker(v => !v)}>😀</button>
          {isOwn && <button className="action-btn" title={t('bubble.edit')} onClick={() => onEdit(msg)}>✎</button>}
          {isOwn && <button className="action-btn" title={t('bubble.delete')} onClick={() => onDelete(msg)}>🗑</button>}
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
              <span className="reply-from">{msg.reply_to.from_id === userId ? t('bubble.you') : t('bubble.user')}</span>
              <span className="reply-text">{asText(msg.reply_to.text, t('bubble.media')).slice(0, 80)}</span>
            </div>
          </div>
        )}

        {/* Media */}
        {msg.media && (
          <div className="bubble-media">
            {mediaIsImage
              ? <img src={msg.media} alt="media" className="media-img" onClick={() => onOpenMedia(msg.media!)} />
              : msg.media_type === 'video'
                ? <video src={msg.media} controls className="media-video" />
                : isVoice
                  ? <audio src={msg.media} controls className="media-audio" />
                  : <a href={msg.media} target="_blank" rel="noreferrer" className="media-file">
                      📎 {msg.media_filename ?? t('misc.downloadFile')}
                    </a>
            }
          </div>
        )}

        {/* Text */}
        {msg._decryptFailed ? (
          <p className="bubble-text decrypt-msg">{t('bubble.encrypted')}</p>
        ) : msg.text ? (() => {
          const call = parseCallMessage(msg.text);
          return call
            ? <CallBubble call={call} />
            : <p className="bubble-text">
                {renderText(msg.text)}
                {msg.is_edited && <span className="edited-mark">{t('bubble.edited')}</span>}
              </p>;
        })() : msg.cipher_version === CIPHER_VERSION_SIGNAL ? (
          <p className="bubble-text decrypt-msg">{t('bubble.encrypted')}</p>
        ) : null}

        {/* Footer: time + status */}
        <div className="bubble-footer">
          {isEncrypted && <span className="lock-icon" title={t('bubble.e2eTitle')}>🔒</span>}
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

// ─── Text formatting (Markdown-lite) ──────────────────────────────────────────
// Supports: **bold**, _italic_, `code`, ||spoiler||

function Spoiler({ text }: { text: string }) {
  const [revealed, setRevealed] = React.useState(false);
  return (
    <span
      className={`spoiler ${revealed ? 'revealed' : ''}`}
      onClick={() => setRevealed(v => !v)}
      title={revealed ? '' : 'Нажмите, чтобы показать'}
    >
      {text}
    </span>
  );
}

const FORMAT_RE = /(\*\*(.+?)\*\*|_(.+?)_|`(.+?)`|\|\|(.+?)\|\|)/gs;

function renderText(text: string): React.ReactNode {
  const nodes: React.ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  FORMAT_RE.lastIndex = 0;
  while ((m = FORMAT_RE.exec(text)) !== null) {
    if (m.index > last) nodes.push(text.slice(last, m.index));
    if (m[2] !== undefined) nodes.push(<strong key={m.index}>{m[2]}</strong>);
    else if (m[3] !== undefined) nodes.push(<em key={m.index}>{m[3]}</em>);
    else if (m[4] !== undefined) nodes.push(<code key={m.index} className="inline-code">{m[4]}</code>);
    else if (m[5] !== undefined) nodes.push(<Spoiler key={m.index} text={m[5]} />);
    last = m.index + m[0].length;
  }
  if (last < text.length) nodes.push(text.slice(last));
  // No formatting tokens found → return plain string for perf
  return nodes.length === 1 && typeof nodes[0] === 'string' ? nodes[0] : <>{nodes}</>;
}

// ─── Call message parser ──────────────────────────────────────────────────────

type CallMsgPayload = {
  callType: 'audio' | 'video';
  roomName: string;
  initiatorName: string;
  maxParticipants: number;
  isPremiumCall: boolean;
};

function parseCallMessage(text: string): CallMsgPayload | null {
  if (!text || text[0] !== '{') return null;
  try {
    const p = JSON.parse(text) as Record<string, unknown>;
    if (typeof p.callType !== 'string' || typeof p.roomName !== 'string') return null;
    return {
      callType:        p.callType === 'video' ? 'video' : 'audio',
      roomName:        String(p.roomName),
      initiatorName:   String(p.initiatorName ?? ''),
      maxParticipants: Number(p.maxParticipants ?? 5),
      isPremiumCall:   Boolean(p.isPremiumCall),
    };
  } catch { return null; }
}

function PollWidget({
  poll, postId, onVote,
}: { poll: ChannelPoll; postId: number; onVote: (pollId: number, optionId: number) => void }) {
  const hasVoted = poll.options.some(o => o.is_voted);
  const showResults = hasVoted || poll.is_closed;
  const maxPct = Math.max(...poll.options.map(o => o.percent), 1);
  return (
    <div className="post-poll">
      <div className="poll-question">{poll.question}</div>
      {poll.options.map(opt => (
        <button
          key={opt.id}
          className={`poll-option ${opt.is_voted ? 'voted' : ''} ${poll.is_closed ? 'closed' : ''}`}
          disabled={showResults || poll.is_closed}
          onClick={() => onVote(poll.id, opt.id)}
        >
          {showResults && (
            <div className="poll-bar" style={{ width: `${(opt.percent / maxPct) * 100}%` }} />
          )}
          <span className="poll-option-text">{opt.text}</span>
          {showResults && <span className="poll-option-pct">{opt.percent}%</span>}
        </button>
      ))}
      <div className="poll-footer">
        {poll.total_votes} {t('channel.totalVotes')}
        {poll.is_anonymous && <span> · {t('channel.anonymous')}</span>}
        {poll.is_closed && <span> · {t('channel.pollClosed')}</span>}
      </div>
    </div>
  );
}

function CallBubble({ call }: { call: CallMsgPayload }) {
  return (
    <div className="bubble-call">
      <span className="bubble-call-icon">{call.callType === 'video' ? '📹' : '📞'}</span>
      <div className="bubble-call-info">
        <span className="bubble-call-title">
          {call.callType === 'video' ? t('call.videoCall') : t('call.voiceCall')}
        </span>
        <span className="bubble-call-meta">{call.initiatorName}</span>
      </div>
    </div>
  );
}

// ─── Main App ─────────────────────────────────────────────────────────────────

export default function App() {
  // ── Language ───────────────────────────────────────────────────────────────
  const [lang, setLangState] = useState<Lang>(() => { initLang(); return getLang(); });

  function handleLangChange(l: Lang) {
    setLang(l);
    setLangState(l);
    window.desktopApp?.setLanguage?.(l);
  }

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
  const [showPassword, setShowPassword] = useState(false);

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
  const [groupSearch, setGroupSearch]   = useState('');
  const [channelSearch, setChannelSearch] = useState('');
  const [groupSearchResults, setGroupSearchResults]     = useState<GroupItem[] | null>(null);
  const [channelSearchResults, setChannelSearchResults] = useState<ChannelItem[] | null>(null);
  const groupSearchTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);
  const channelSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── In-chat message search ────────────────────────────────────────────────
  const [chatSearchOpen, setChatSearchOpen]       = useState(false);
  const [chatSearchQuery, setChatSearchQuery]     = useState('');
  const [chatSearchResults, setChatSearchResults] = useState<MessageItem[]>([]);
  const [chatSearchLoading, setChatSearchLoading] = useState(false);
  const chatSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Lightbox ──────────────────────────────────────────────────────────────
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);

  // ── Profile editing ───────────────────────────────────────────────────────
  const [myProfile,      setMyProfile]      = useState<UserProfile | null>(null);
  const [profileFirst,   setProfileFirst]   = useState('');
  const [profileLast,    setProfileLast]    = useState('');
  const [profileAbout,   setProfileAbout]   = useState('');
  const [profileUser,    setProfileUser]    = useState('');
  const [profileSaving,  setProfileSaving]  = useState<'idle' | 'saving' | 'done' | 'error'>('idle');

  // ── Archived chats ────────────────────────────────────────────────────────
  const [archivedChats,  setArchivedChats]  = useState<ChatItem[]>([]);
  const [showArchived,   setShowArchived]   = useState(false);
  const [archivedLoaded, setArchivedLoaded] = useState(false);

  // ── Blocked users ─────────────────────────────────────────────────────────
  const [blockedUsers,   setBlockedUsers]   = useState<UserProfile[]>([]);
  const [blockedLoaded,  setBlockedLoaded]  = useState(false);

  // ── Voice recording ───────────────────────────────────────────────────────
  const [isRecordingVoice, setIsRecordingVoice] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const voiceChunksRef   = useRef<Blob[]>([]);

  // ── Group chat ────────────────────────────────────────────────────────────
  const [selectedGroup, setSelectedGroup]     = useState<GroupItem | null>(null);
  const [groupMessages, setGroupMessages]     = useState<MessageItem[]>([]);
  const [groupMsgLoading, setGroupMsgLoading] = useState(false);
  const [groupMsgError, setGroupMsgError]     = useState(false);
  const [groupHasMore, setGroupHasMore]       = useState(false);
  const [newGroupMessage, setNewGroupMessage] = useState('');
  const [groupReplyTarget, setGroupReplyTarget] = useState<ReplyTarget | null>(null);
  const [groupEditingMsg, setGroupEditingMsg]   = useState<MessageItem | null>(null);
  const selectedGroupRef = useRef<GroupItem | null>(null);

  // ── Channel posts ─────────────────────────────────────────────────────────
  const [selectedChannel, setSelectedChannel]     = useState<ChannelItem | null>(null);
  const [channelPosts, setChannelPosts]           = useState<ChannelPost[]>([]);
  const [channelPostsLoading, setChannelPostsLoading] = useState(false);
  const [channelPostOffset, setChannelPostOffset] = useState(0);
  const [channelHasMore, setChannelHasMore]       = useState(false);
  const [newChannelPost, setNewChannelPost]       = useState('');
  const [channelPostMedia, setChannelPostMedia]   = useState<File | null>(null);
  const [votingPollId, setVotingPollId]           = useState<number | null>(null);
  const selectedChannelRef = useRef<ChannelItem | null>(null);

  // ── Channel comments ──────────────────────────────────────────────────────
  const [commentPost,      setCommentPost]      = useState<ChannelPost | null>(null);
  const [comments,         setComments]         = useState<ChannelComment[]>([]);
  const [commentsLoading,  setCommentsLoading]  = useState(false);
  const [newComment,       setNewComment]       = useState('');
  const [commentReplyTo,   setCommentReplyTo]   = useState<{ id: number; text: string } | null>(null);
  const commentsEndRef = useRef<HTMLDivElement>(null);

  // ── Story viewer ──────────────────────────────────────────────────────────
  const [viewingStoryIdx, setViewingStoryIdx] = useState<number | null>(null);
  const [storyProgress, setStoryProgress]     = useState(0);
  const storyTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── Call history ──────────────────────────────────────────────────────────
  const [callHistory,       setCallHistory]       = useState<CallHistoryItem[]>([]);
  const [callHistoryLoaded, setCallHistoryLoaded] = useState(false);
  const [callHistoryFilter, setCallHistoryFilter] = useState<'all'|'missed'|'incoming'|'outgoing'>('all');

  // ── Privacy settings ──────────────────────────────────────────────────────
  const [privacySettings, setPrivacySettings] = useState<PrivacySettings | null>(null);
  const [privacyLoaded,   setPrivacyLoaded]   = useState(false);
  const [privacySaving,   setPrivacySaving]   = useState<'idle'|'saving'|'done'|'error'>('idle');

  // ── Send error banner ─────────────────────────────────────────────────────
  const [sendError, setSendError]           = useState('');
  const [signalResetStatus, setSignalResetStatus] = useState<'idle'|'working'|'done'|'error'>('idle');

  // ── Signal service ────────────────────────────────────────────────────────
  const signalRef = useRef<SignalService | null>(null);

  /**
   * Queue of user IDs that need a signal:session_reset_request emitted.
   * Accumulated when the socket is disconnected and flushed on reconnect.
   * Prevents losing the recovery signal when the socket is temporarily offline.
   */
  const pendingResetsRef = useRef<Set<number>>(new Set());

  const messagesEndRef    = useRef<HTMLDivElement>(null);
  const messagesScrollRef = useRef<HTMLDivElement>(null);
  const composerRef       = useRef<HTMLTextAreaElement>(null);
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

      onConnected: () => {
        // ── Flush pending session reset requests ────────────────────────────
        // If we tried to emit session_reset_request while the socket was down,
        // the user IDs were queued.  Emit them all now.
        const pending = pendingResetsRef.current;
        if (pending.size > 0) {
          pending.forEach(uid => {
            s.emit('signal:session_reset_request', { target_user_id: uid });
            console.info('[Signal] Flushed pending session_reset_request to user', uid);
          });
          pendingResetsRef.current = new Set();
        }
        // ── Reload current chat ─────────────────────────────────────────────
        // After a reconnect we may have missed socket messages while offline.
        // Re-fetch from REST so the chat view is up to date.
        const chat = selectedChatRef.current;
        if (chat) {
          loadMessages(session.token, chat.user_id, session.userId)
            .then(r => Promise.all((r.messages ?? []).map(tryDecryptMessage)))
            .then(decrypted => setMessages(decrypted))
            .catch(() => {});
        }
      },

      onMessage: async (rawMsg) => {
        // Socket events arrive as raw server JSON — normalise before decrypting
        // so Signal messages have text_encrypted set (not text) and from_id/to_id
        // are always populated regardless of which field names the server uses.
        const msg = normaliseMessage(rawMsg as unknown as Record<string, unknown>);
        const decrypted = await tryDecryptMessage(msg);

        // If ANY E2EE decryption failed — whether from a broken DR session
        // (no X3DH fields) or from a missing OPK during X3DH (with 'ik' field) —
        // tell the sender to reset their session and retry.
        //
        //  • No 'ik': sender used an existing DR session whose state is ahead of
        //    ours (BAD_DECRYPT).  Reset makes them re-run X3DH on next send.
        //  • With 'ik': sender used an OPK we no longer have.  Reset makes them
        //    fetch a fresh bundle (with valid OPK IDs) and re-run X3DH.
        //
        // In both cases the sender receiving session_reset_request will clear their
        // outgoing session, and the next send triggers a fresh X3DH that Windows
        // can complete successfully.
        if (decrypted._decryptFailed && msg.cipher_version === 3 && msg.from_id) {
          if (s?.connected) {
            s.emit('signal:session_reset_request', { target_user_id: msg.from_id });
            console.info('[Signal] Emitted session_reset_request to user', msg.from_id);
          } else {
            // Socket offline — queue for when we reconnect (onConnected will flush)
            pendingResetsRef.current.add(msg.from_id);
            console.info('[Signal] Queued session_reset_request for user', msg.from_id,
              '(socket offline — will send on reconnect)');
          }
        }

        setMessages(prev => {
          // De-duplicate by id
          if (prev.some(m => m.id === decrypted.id)) return prev;
          if (decrypted.from_id === selectedChatRef.current?.user_id ||
              decrypted.to_id   === selectedChatRef.current?.user_id) {
            return [...prev, decrypted];
          }
          return prev;
        });

        // Update last message + unread count in chat list
        const chatPartnerId      = decrypted.from_id === session.userId ? decrypted.to_id : decrypted.from_id;
        const isIncomingFromOther = decrypted.from_id !== session.userId;
        const isActiveChatNow    = selectedChatRef.current?.user_id === chatPartnerId;

        setChats(prev => prev.map(c => {
          if (c.user_id !== chatPartnerId) return c;
          // Only overwrite last_message when we have real decrypted text.
          // If decryption failed or text is empty, keep the existing preview so
          // the sidebar doesn't flash back to "No messages".
          const newPreview = !decrypted._decryptFailed && decrypted.text
            ? decrypted.text
            : (decrypted.media ? t('bubble.media') : null);
          return {
            ...c,
            ...(newPreview != null ? { last_message: newPreview } : {}),
            time: 'now',
            unread_count: isIncomingFromOther && !isActiveChatNow
              ? (c.unread_count ?? 0) + 1
              : c.unread_count,
          };
        }));

        // Desktop notification + sound for incoming messages from other users.
        // • Sound plays whenever the message is NOT in the currently-open chat
        //   (so the user isn't beeped while actively reading the same thread).
        // • Native popup is always sent to main process; main.cjs suppresses it
        //   if the window is visible AND focused (document.hasFocus() is
        //   unreliable in Electron when the window is hidden/minimised).
        if (isIncomingFromOther) {
          const senderChat = chatsRef.current.find(c => c.user_id === chatPartnerId);
          const senderName = senderChat?.name ?? `User ${chatPartnerId}`;
          const msgBody = decrypted._decryptFailed
            ? t('bubble.encrypted')
            : asText(decrypted.text, t('bubble.media')).slice(0, 100);

          if (!isActiveChatNow) playNotificationBeep();
          window.desktopApp?.notify?.({ title: senderName, body: msgBody, chatId: chatPartnerId });
        }

        // After a successful X3DH session establishment (incoming message has `ik`
        // field), reload the current chat from the server and re-decrypt everything.
        // This fixes messages that arrived before the session was ready and were
        // shown as "🔒 Encrypted message" — they're now decryptable via the new
        // DR chain without requiring the user to switch chats.
        if (!decrypted._decryptFailed && msg.cipher_version === CIPHER_VERSION_SIGNAL && msg.signal_header) {
          try {
            const hdr = JSON.parse(msg.signal_header) as Record<string, unknown>;
            if ('ik' in hdr && selectedChatRef.current) {
              console.info('[Signal] X3DH established — reloading chat to re-decrypt previous failures');
              loadMessages(session.token, selectedChatRef.current.user_id, session.userId)
                .then(r => Promise.all((r.messages ?? []).map(tryDecryptMessage)))
                .then(reDecrypted => setMessages(reDecrypted))
                .catch(e => console.warn('[Signal] Post-X3DH reload failed:', e));
            }
          } catch { /* signal_header parse error — ignore */ }
        }
      },

      onGroupMessage: (rawMsg) => {
        const raw = rawMsg as unknown as Record<string, unknown>;
        const msg: MessageItem = {
          id:          Number(raw.id),
          from_id:     Number(raw.from_id ?? raw.sender_id),
          to_id:       0,
          text:        String(raw.text ?? ''),
          time_text:   String(raw.time_text ?? 'now'),
          time:        raw.time ? Number(raw.time) : Math.floor(Date.now() / 1000),
          media:       raw.media as string | undefined,
          media_type:  raw.media_type as MessageItem['media_type'],
          group_id:    raw.group_id ? Number(raw.group_id) : rawMsg.group_id,
          sender_name: raw.sender_name
            ? String(raw.sender_name)
            : raw.user_data
              ? String((raw.user_data as Record<string, unknown>).name ?? (raw.user_data as Record<string, unknown>).username ?? '')
              : undefined,
        };
        setGroupMessages(prev => {
          if (prev.some(m => m.id === msg.id)) return prev;
          if (msg.group_id === selectedGroupRef.current?.id) return [...prev, msg];
          return prev;
        });
        setGroups(prev => prev.map(g =>
          g.id === msg.group_id
            ? { ...g, last_message: msg.text || '[медиа]', time: 'now' }
            : g
        ));
        if (msg.from_id !== session.userId && msg.group_id !== selectedGroupRef.current?.id) {
          playNotificationBeep();
        }
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

      onSessionResetRequest: (e) => {
        // Peer's decryption failed — our outgoing session is stale.
        // Clear it so the next send includes X3DH headers and re-syncs both sides.
        if (signalRef.current) {
          signalRef.current.clearSessionFor(e.from_user_id);
          console.info('[Signal] Session reset requested by user', e.from_user_id,
            '— cleared outgoing session, next send will include X3DH');
        }
      },
    });

    setSocket(s);
    return () => { s.disconnect(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  // Keep refs so socket callbacks always read the latest values without stale closures
  const selectedChatRef = useRef<ChatItem | null>(null);
  const chatsRef        = useRef<ChatItem[]>([]);
  useEffect(() => { selectedChatRef.current = selectedChat; }, [selectedChat]);
  useEffect(() => { chatsRef.current = chats; }, [chats]);
  useEffect(() => { selectedGroupRef.current = selectedGroup; }, [selectedGroup]);
  useEffect(() => { selectedChannelRef.current = selectedChannel; }, [selectedChannel]);

  // ─── Badge count (tray + taskbar overlay) ───────────────────────────────
  useEffect(() => {
    const total = chats.reduce((sum, c) => sum + (c.unread_count ?? 0), 0);
    window.desktopApp?.setBadge?.(total);
  }, [chats]);

  // ─── Open-chat from notification click ──────────────────────────────────
  useEffect(() => {
    window.desktopApp?.onOpenChat?.((chatId) => {
      const chat = chatsRef.current.find(c => c.user_id === chatId);
      if (chat) {
        setSection('chats');
        setSelectedChat(chat);
        setChats(prev => prev.map(c => c.user_id === chatId ? { ...c, unread_count: 0 } : c));
      }
    });
    return () => window.desktopApp?.offOpenChat?.();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
        if (list.length > 0 && !selectedChat) selectChat(list[0]);
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

  // ─── Load group messages when group changes ──────────────────────────────

  useEffect(() => {
    if (!session || !selectedGroup) return;
    setGroupMsgError(false);
    setGroupMsgLoading(true);
    setGroupMessages([]);
    let cancelled = false;
    loadGroupMessages(session.token, selectedGroup.id)
      .then(r => {
        if (cancelled) return;
        setGroupMessages(r.messages ?? []);
        setGroupHasMore((r.messages ?? []).length >= 40);
        markGroupSeen(session.token, selectedGroup.id);
        setGroupMsgLoading(false);
      })
      .catch(() => {
        if (!cancelled) { setGroupMsgError(true); setGroupMsgLoading(false); }
      });
    return () => { cancelled = true; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, selectedGroup?.id]);

  // ─── Load channel posts when channel changes ──────────────────────────────

  useEffect(() => {
    if (!session || !selectedChannel) return;
    setChannelPostsLoading(true);
    setChannelPosts([]);
    setChannelPostOffset(0);
    let cancelled = false;
    loadChannelPosts(session.token, selectedChannel.id)
      .then(r => {
        if (cancelled) return;
        setChannelPosts(r.posts ?? []);
        setChannelHasMore((r.posts ?? []).length >= 30);
        setChannelPostOffset((r.posts ?? []).length);
        setChannelPostsLoading(false);
        // Mark first 10 posts as viewed
        (r.posts ?? []).slice(0, 10).forEach(p => markChannelPostViewed(session.token, p.id));
      })
      .catch(() => { if (!cancelled) setChannelPostsLoading(false); });
    return () => { cancelled = true; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, selectedChannel?.id]);

  // ─── Story viewer timer ───────────────────────────────────────────────────

  useEffect(() => {
    if (viewingStoryIdx === null) {
      if (storyTimerRef.current) { clearInterval(storyTimerRef.current); storyTimerRef.current = null; }
      setStoryProgress(0);
      return;
    }
    setStoryProgress(0);
    if (storyTimerRef.current) clearInterval(storyTimerRef.current);
    const duration = 5000;
    const step = 100;
    storyTimerRef.current = setInterval(() => {
      setStoryProgress(p => {
        const next = p + (step / duration) * 100;
        if (next >= 100) {
          setViewingStoryIdx(idx => {
            if (idx === null) return null;
            const nextIdx = idx + 1;
            return nextIdx < stories.length ? nextIdx : null;
          });
          return 0;
        }
        return next;
      });
    }, step);
    return () => { if (storyTimerRef.current) clearInterval(storyTimerRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewingStoryIdx]);

  function openStory(idx: number) {
    setViewingStoryIdx(idx);
    if (session && stories[idx]) markStorySeen(session.token, stories[idx].id);
  }

  function closeStory() { setViewingStoryIdx(null); }

  function nextStory() {
    setViewingStoryIdx(idx => {
      if (idx === null) return null;
      const next = idx + 1;
      if (next < stories.length) {
        if (session) markStorySeen(session.token, stories[next].id);
        return next;
      }
      return null;
    });
  }

  function prevStory() {
    setViewingStoryIdx(idx => {
      if (idx === null || idx === 0) return idx;
      return idx - 1;
    });
  }

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
  // Direct scrollTop assignment on the container is reliable in Electron;
  // scrollIntoView() targets the document viewport, not the inner container.
  // rAF guarantees the new bubble is painted before we measure scrollHeight.

  useEffect(() => {
    requestAnimationFrame(() => {
      const el = messagesScrollRef.current;
      if (el) el.scrollTop = el.scrollHeight;
    });
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
    // Pass msg.id so the service deduplicates concurrent calls for the same message
    // (socket event + REST list reload arriving simultaneously) — mirrors Android.
    const plaintext = await svc.decryptIncoming(senderId, msg.text_encrypted, msg.iv, msg.tag, msg.signal_header, msg.id);
    if (plaintext === null) return { ...msg, _decryptFailed: true };

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
        setAuthError(r.message ?? t('auth.error.regFailed'));
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
      setAuthError(r.message ?? t('auth.error.authFailed'));
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : t('auth.error.unknown'));
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
      setSendError(t('misc.sendError'));
      setTimeout(() => setSendError(''), 4000);
    }
  }

  // ─── Voice recording ──────────────────────────────────────────────────────

  async function startVoiceRecording() {
    if (!session || !selectedChat) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      // Prefer ogg (unambiguously audio on all platforms) over webm (video container)
      const mimeType = MediaRecorder.isTypeSupported('audio/ogg;codecs=opus') ? 'audio/ogg;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/ogg') ? 'audio/ogg'
        : MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus'
        : 'audio/webm';
      const mr = new MediaRecorder(stream, { mimeType });
      voiceChunksRef.current = [];
      mr.ondataavailable = e => { if (e.data.size > 0) voiceChunksRef.current.push(e.data); };
      mr.onstop = async () => {
        stream.getTracks().forEach(t => t.stop());
        const blob = new Blob(voiceChunksRef.current, { type: mimeType });
        const ext  = mimeType.includes('ogg') ? 'ogg' : 'webm';
        // Uppercase VOICE_ prefix so Android's mediaFileName.startsWith("VOICE_") check matches
        const file = new File([blob], `VOICE_${Date.now()}.${ext}`, { type: mimeType });
        try {
          await sendVoiceMessage(session.token, selectedChat.user_id, file);
        } catch { /* ignore send error for voice */ }
      };
      mr.start();
      mediaRecorderRef.current = mr;
      setIsRecordingVoice(true);
    } catch { /* microphone permission denied */ }
  }

  function stopVoiceRecording() {
    mediaRecorderRef.current?.stop();
    mediaRecorderRef.current = null;
    setIsRecordingVoice(false);
  }

  // ─── Profile ──────────────────────────────────────────────────────────────

  useEffect(() => {
    if (section !== 'settings' || myProfile || !session) return;
    getMyProfile(session.token).then(p => {
      setMyProfile(p);
      setProfileFirst(p.first_name ?? '');
      setProfileLast(p.last_name  ?? '');
      setProfileAbout(p.about     ?? '');
      setProfileUser(p.username   ?? '');
    }).catch(() => {});
  }, [section, myProfile, session]);

  // Auto-load call history when entering Calls section
  useEffect(() => {
    if (section === 'calls' && !callHistoryLoaded && session) {
      handleLoadCallHistory(callHistoryFilter);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section]);

  async function handleSaveProfile() {
    if (!session) return;
    setProfileSaving('saving');
    try {
      await updateMyProfile(session.token, {
        first_name: profileFirst,
        last_name:  profileLast,
        about:      profileAbout,
        username:   profileUser,
      });
      setMyProfile(prev => prev ? { ...prev, first_name: profileFirst, last_name: profileLast, about: profileAbout, username: profileUser } : prev);
      setProfileSaving('done');
      setTimeout(() => setProfileSaving('idle'), 2500);
    } catch {
      setProfileSaving('error');
      setTimeout(() => setProfileSaving('idle'), 2500);
    }
  }

  async function handleAvatarUpload(file: File) {
    if (!session) return;
    try {
      const url = await uploadAvatar(session.token, file);
      if (url) setMyProfile(prev => prev ? { ...prev, avatar: url } : prev);
    } catch { /* ignore */ }
  }

  // ─── Archived chats ───────────────────────────────────────────────────────

  async function handleToggleArchived() {
    if (!session) return;
    if (!archivedLoaded) {
      const r = await loadArchivedChats(session.token);
      setArchivedChats(r.data ?? []);
      setArchivedLoaded(true);
    }
    setShowArchived(v => !v);
  }

  async function handleUnarchive(userId: number) {
    if (!session) return;
    await archiveChat(session.token, userId, false);
    setArchivedChats(prev => prev.filter(c => c.user_id !== userId));
  }

  // ─── Block / unblock ──────────────────────────────────────────────────────

  async function handleBlockUser(userId: number) {
    if (!session || !window.confirm(t('chat.block') + '?')) return;
    await blockUser(session.token, userId);
  }

  async function handleLoadBlocked() {
    if (!session || blockedLoaded) return;
    const users = await loadBlockedUsers(session.token);
    setBlockedUsers(users);
    setBlockedLoaded(true);
  }

  async function handleUnblock(userId: number) {
    if (!session) return;
    await unblockUser(session.token, userId);
    setBlockedUsers(prev => prev.filter(u => u.id !== userId));
  }

  // ─── Call history ─────────────────────────────────────────────────────────

  async function handleLoadCallHistory(filter: 'all'|'missed'|'incoming'|'outgoing' = 'all') {
    if (!session) return;
    const items = await loadCallHistory(session.token, filter);
    setCallHistory(items);
    setCallHistoryLoaded(true);
  }

  async function handleDeleteCall(callId: number) {
    if (!session) return;
    await deleteCallRecord(session.token, callId).catch(console.error);
    setCallHistory(prev => prev.filter(c => c.id !== callId));
  }

  async function handleClearCallHistory() {
    if (!session) return;
    await clearCallHistory(session.token).catch(console.error);
    setCallHistory([]);
  }

  // ─── Privacy settings ─────────────────────────────────────────────────────

  async function handleLoadPrivacy() {
    if (!session || privacyLoaded) return;
    const ps = await loadPrivacySettings(session.token).catch(() => null);
    if (ps) setPrivacySettings(ps);
    setPrivacyLoaded(true);
  }

  async function handleSavePrivacy() {
    if (!session || !privacySettings) return;
    setPrivacySaving('saving');
    try {
      await updatePrivacySettings(session.token, privacySettings);
      setPrivacySaving('done');
      setTimeout(() => setPrivacySaving('idle'), 3000);
    } catch {
      setPrivacySaving('error');
      setTimeout(() => setPrivacySaving('idle'), 3000);
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
    setReplyTarget({ id: msg.id, from_id: msg.from_id, text: asText(msg.text, t('bubble.media')) });
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

  // ─── Group chat actions ───────────────────────────────────────────────────

  async function handleSendGroupMessage(e?: React.FormEvent) {
    e?.preventDefault();
    if (!session || !selectedGroup) return;
    const text = newGroupMessage.trim();
    if (!text && !groupEditingMsg) return;

    if (groupEditingMsg) {
      if (!text) return;
      await editGroupMessage(session.token, groupEditingMsg.id, text).catch(console.error);
      setGroupMessages(prev => prev.map(m => m.id === groupEditingMsg.id ? { ...m, text, is_edited: true } : m));
      setGroupEditingMsg(null);
      setNewGroupMessage('');
      return;
    }

    const optimisticId = -(Date.now());
    const optimistic: MessageItem = {
      id: optimisticId, from_id: session.userId, to_id: 0,
      text, time_text: 'now', time: Math.floor(Date.now() / 1000),
      group_id: selectedGroup.id,
      reply_to: groupReplyTarget ? { id: groupReplyTarget.id, from_id: groupReplyTarget.from_id, text: groupReplyTarget.text } : undefined,
      _pending: true,
    };
    setGroupMessages(prev => [...prev, optimistic]);
    setNewGroupMessage('');
    setGroupReplyTarget(null);

    try {
      const result = await sendGroupMessage(session.token, selectedGroup.id, text, groupReplyTarget?.id);
      setGroupMessages(prev => prev.map(m =>
        m.id === optimisticId ? { ...m, id: result.id ?? optimisticId, _pending: false } : m
      ));
    } catch {
      setGroupMessages(prev => prev.filter(m => m.id !== optimisticId));
      setSendError(t('misc.sendError'));
      setTimeout(() => setSendError(''), 4000);
    }
  }

  async function handleDeleteGroupMessage(msg: MessageItem) {
    if (!session) return;
    await deleteGroupMessage(session.token, msg.id).catch(console.error);
    setGroupMessages(prev => prev.filter(m => m.id !== msg.id));
  }

  async function handleLoadMoreGroupMessages() {
    if (!session || !selectedGroup || !groupHasMore || groupMessages.length === 0) return;
    const oldest = groupMessages[0].id;
    const r = await loadMoreGroupMessages(session.token, selectedGroup.id, oldest);
    setGroupMessages(prev => [...(r.messages ?? []), ...prev]);
    setGroupHasMore((r.messages ?? []).length >= 40);
  }

  // ─── Channel post actions ─────────────────────────────────────────────────

  async function handleCreateChannelPost(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !selectedChannel) return;
    const text = newChannelPost.trim();
    if (!text && !channelPostMedia) return;

    let mediaUrl: string | undefined;
    let mediaType: string | undefined;
    if (channelPostMedia) {
      try {
        const up = await uploadMedia(session.token, channelPostMedia);
        mediaUrl  = up.image_src ?? up.video_src ?? up.file_src;
        mediaType = channelPostMedia.type.startsWith('image') ? 'image'
          : channelPostMedia.type.startsWith('video') ? 'video' : 'file';
      } catch { /* upload failed, post without media */ }
    }

    setNewChannelPost('');
    setChannelPostMedia(null);
    await createChannelPost(session.token, selectedChannel.id, text, mediaUrl, mediaType).catch(console.error);
    const r = await loadChannelPosts(session.token, selectedChannel.id);
    setChannelPosts(r.posts ?? []);
    setChannelPostOffset((r.posts ?? []).length);
  }

  async function handleDeleteChannelPost(postId: number) {
    if (!session) return;
    await deleteChannelPost(session.token, postId).catch(console.error);
    setChannelPosts(prev => prev.filter(p => p.id !== postId));
  }

  async function handleVotePoll(pollId: number, optionId: number) {
    if (!session || votingPollId === pollId) return;
    setVotingPollId(pollId);
    try {
      await voteChannelPoll(session.token, pollId, [optionId]);
      if (selectedChannel) {
        const r = await loadChannelPosts(session.token, selectedChannel.id);
        setChannelPosts(r.posts ?? []);
      }
    } catch { /* ignore */ }
    setVotingPollId(null);
  }

  async function handleLoadMoreChannelPosts() {
    if (!session || !selectedChannel || !channelHasMore) return;
    const r = await loadMoreChannelPosts(session.token, selectedChannel.id, channelPostOffset);
    setChannelPosts(prev => [...prev, ...(r.posts ?? [])]);
    setChannelHasMore((r.posts ?? []).length >= 30);
    setChannelPostOffset(prev => prev + (r.posts ?? []).length);
  }

  async function handleOpenComments(post: ChannelPost) {
    setCommentPost(post);
    setCommentsLoading(true);
    setComments([]);
    setNewComment('');
    setCommentReplyTo(null);
    try {
      const list = await loadChannelComments(session!.token, post.id);
      setComments(list);
      setTimeout(() => commentsEndRef.current?.scrollIntoView(), 100);
    } catch { /* ignore */ }
    setCommentsLoading(false);
  }

  async function handleSendComment(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !commentPost || !newComment.trim()) return;
    const text = newComment.trim();
    setNewComment('');
    await addChannelComment(session.token, commentPost.id, text, commentReplyTo?.id).catch(console.error);
    setCommentReplyTo(null);
    const list = await loadChannelComments(session.token, commentPost.id);
    setComments(list);
    setTimeout(() => commentsEndRef.current?.scrollIntoView(), 100);
    // Update comment count in post list
    setChannelPosts(prev => prev.map(p => p.id === commentPost.id
      ? { ...p, comments_count: (p.comments_count ?? 0) + 1 } : p));
  }

  async function handleDeleteComment(commentId: number) {
    if (!session || !commentPost) return;
    await deleteChannelComment(session.token, commentId).catch(console.error);
    setComments(prev => prev.filter(c => c.id !== commentId));
  }

  async function handleReactComment(commentId: number, emoji: string) {
    if (!session) return;
    await reactToChannelComment(session.token, commentId, emoji).catch(console.error);
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

  // ─── Select chat (resets unread badge for that chat) ─────────────────────

  function selectChat(chat: ChatItem) {
    setSelectedChat(chat);
    setChats(prev => prev.map(c => c.user_id === chat.user_id ? { ...c, unread_count: 0 } : c));
  }

  // ─── Filtered lists ───────────────────────────────────────────────────────

  const filteredChats = chats.filter(c =>
    !searchQuery || c.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Groups/channels: show server search results when available, else client-side filter
  const filteredGroups = (groupSearchResults ?? groups).filter(g =>
    !groupSearchResults && groupSearch
      ? asText(g.group_name, '').toLowerCase().includes(groupSearch.toLowerCase())
      : true
  );
  const filteredChannels = (channelSearchResults ?? channels).filter(c =>
    !channelSearchResults && channelSearch
      ? asText(c.name, '').toLowerCase().includes(channelSearch.toLowerCase())
      : true
  );

  const typingInChat = selectedChat
    ? Array.from(typingUsers).some(id => id === selectedChat.user_id)
    : false;

  // ─── Group/Channel search handlers ────────────────────────────────────────

  function handleGroupSearch(q: string) {
    setGroupSearch(q);
    setGroupSearchResults(null);
    if (groupSearchTimer.current) clearTimeout(groupSearchTimer.current);
    if (!q.trim() || !session) return;
    groupSearchTimer.current = setTimeout(async () => {
      try {
        const r = await searchGroups(session.token, q.trim());
        setGroupSearchResults(r.data ?? []);
      } catch { /* keep local filter on error */ }
    }, 400);
  }

  function handleChannelSearch(q: string) {
    setChannelSearch(q);
    setChannelSearchResults(null);
    if (channelSearchTimer.current) clearTimeout(channelSearchTimer.current);
    if (!q.trim() || !session) return;
    channelSearchTimer.current = setTimeout(async () => {
      try {
        const r = await searchChannels(session.token, q.trim());
        setChannelSearchResults(r.data ?? []);
      } catch { /* keep local filter on error */ }
    }, 400);
  }

  // ─── In-chat search ───────────────────────────────────────────────────────

  function openChatSearch() {
    setChatSearchOpen(true);
    setChatSearchQuery('');
    setChatSearchResults([]);
  }

  function closeChatSearch() {
    setChatSearchOpen(false);
    setChatSearchQuery('');
    setChatSearchResults([]);
    if (chatSearchTimer.current) clearTimeout(chatSearchTimer.current);
  }

  function handleChatSearchInput(q: string) {
    setChatSearchQuery(q);
    if (chatSearchTimer.current) clearTimeout(chatSearchTimer.current);
    if (!q.trim() || !session || !selectedChat) { setChatSearchResults([]); return; }
    setChatSearchLoading(true);
    chatSearchTimer.current = setTimeout(async () => {
      try {
        const r = await searchMessages(session.token, selectedChat.user_id, q.trim());
        setChatSearchResults(r.messages ?? []);
      } catch { setChatSearchResults([]); }
      finally { setChatSearchLoading(false); }
    }, 400);
  }

  // ─── Drafts ───────────────────────────────────────────────────────────────

  function draftKey(userId: number) { return `wm_draft_${userId}`; }

  // Load draft when switching to a chat
  useEffect(() => {
    if (!selectedChat) return;
    const draft = localStorage.getItem(draftKey(selectedChat.user_id)) ?? '';
    setNewMessage(draft);
    // Reset search state when chat changes
    closeChatSearch();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedChat?.user_id]);

  function handleComposerInputWithDraft(text: string) {
    handleComposerInput(text);
    if (selectedChat) {
      if (text) localStorage.setItem(draftKey(selectedChat.user_id), text);
      else localStorage.removeItem(draftKey(selectedChat.user_id));
    }
  }

  // ─── Render: Auth ─────────────────────────────────────────────────────────

  if (!session) {
    return (
      <div className="auth-page">
        <div className="auth-glow" />
        <form className="auth-card" onSubmit={handleAuth}>

          {/* ── Brand ── */}
          <div className="auth-logo">
            <div className="auth-logo-icon">W</div>
            <div className="auth-logo-text">
              <h1 className="auth-title">WorldMates</h1>
              <p className="auth-subtitle">{t('auth.subtitle')}</p>
            </div>
          </div>

          {/* ── Login / Register tabs ── */}
          <div className="tabs">
            <button type="button" className={authMode === 'login' ? 'tab active' : 'tab'}
              onClick={() => setAuthMode('login')}>{t('auth.tab.login')}</button>
            <button type="button" className={authMode === 'register' ? 'tab active' : 'tab'}
              onClick={() => setAuthMode('register')}>{t('auth.tab.register')}</button>
          </div>

          {/* ── Login method (username / phone) ── */}
          {authMode === 'login' && (
            <div className="tabs">
              <button type="button" className={loginBy === 'username' ? 'tab active' : 'tab'}
                onClick={() => setLoginBy('username')}>{t('auth.field.username')}</button>
              <button type="button" className={loginBy === 'phone' ? 'tab active' : 'tab'}
                onClick={() => setLoginBy('phone')}>{t('auth.field.phone')}</button>
            </div>
          )}

          {/* ── Username ── */}
          {(authMode === 'register' || loginBy === 'username') && (
            <label className="field">
              <span>{t('auth.field.username')}</span>
              <input type="text" value={username} onChange={e => setUsername(e.target.value)}
                placeholder={t('auth.placeholder.username')} autoComplete="username"
                required={authMode === 'register' || loginBy === 'username'} />
            </label>
          )}

          {/* ── Phone ── */}
          {(authMode === 'register' || loginBy === 'phone') && (
            <label className="field">
              <span>{t('auth.field.phone')}</span>
              <input type="tel" value={phone} onChange={e => setPhone(e.target.value)}
                placeholder={t('auth.placeholder.phone')} autoComplete="tel"
                required={authMode === 'register' || loginBy === 'phone'} />
            </label>
          )}

          {/* ── Email (register only) ── */}
          {authMode === 'register' && (
            <label className="field">
              <span>{t('auth.field.email')}</span>
              <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                placeholder={t('auth.placeholder.email')} autoComplete="email" />
            </label>
          )}

          {/* ── Password ── */}
          <label className="field">
            <span>{t('auth.field.password')}</span>
            <div className="field-password-wrap">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password} onChange={e => setPassword(e.target.value)}
                placeholder={t('auth.placeholder.password')}
                autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                required
              />
              <button type="button" className="password-toggle" tabIndex={-1}
                onClick={() => setShowPassword(v => !v)}>
                {showPassword ? '🙈' : '👁'}
              </button>
            </div>
          </label>

          {authError && <div className="auth-error">{authError}</div>}

          <button className="btn-primary" type="submit" disabled={authLoading}>
            {authLoading ? t('auth.loading') : authMode === 'register' ? t('auth.createAccount') : t('auth.signIn')}
          </button>
        </form>
      </div>
    );
  }

  // ─── Render: Main App ─────────────────────────────────────────────────────

  const navItems: { key: ActiveSection; icon: string; label: string }[] = [
    { key: 'chats',    icon: '💬', label: t('nav.chats')    },
    { key: 'groups',   icon: '👥', label: t('nav.groups')   },
    { key: 'channels', icon: '📢', label: t('nav.channels') },
    { key: 'stories',  icon: '⭕', label: t('nav.stories')  },
    { key: 'calls',    icon: '📞', label: t('nav.calls')    },
    { key: 'settings', icon: '⚙️', label: t('nav.settings') },
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
              {callState.phase === 'outgoing' ? t('call.calling') :
               callState.phase === 'incoming' ? t('call.incoming') : t('call.connected')}
            </p>
            <div className="call-actions">
              {callState.phase === 'incoming' && (
                <button className="call-btn accept" onClick={() => {
                  setCallState(prev => ({ ...prev, phase: 'connected', duration: 0 } as CallState));
                }}>
                  {t('call.accept')}
                </button>
              )}
              <button className="call-btn decline" onClick={endActiveCall}>
                {callState.phase === 'incoming' ? t('call.decline') : t('call.end')}
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
          <button className="rail-btn" title={t('nav.logout')} onClick={logout}>
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
            {section === 'chats'    ? t('sidebar.messages') :
             section === 'groups'   ? t('sidebar.groups')   :
             section === 'channels' ? t('sidebar.channels') :
             section === 'stories'  ? t('sidebar.stories')  :
             section === 'calls'    ? t('sidebar.calls')    : t('sidebar.settings')}
          </h2>
          <div className="socket-badge" title={socketStatus}>
            <span className={`dot ${socketStatus.startsWith('Connected') ? 'green' : 'grey'}`} />
          </div>
        </div>

        {/* Search (chats only) */}
        {section === 'chats' && (
          <div className="search-box">
            <span className="search-icon">🔍</span>
            <input placeholder={t('sidebar.search')} value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)} />
          </div>
        )}

        {/* ── Chats list ─────────────────────────────────────────────────── */}
        {section === 'chats' && (
          <div className="list-scroll">
            {filteredChats.length === 0 && (
              <div className="empty-state">{t('sidebar.noChats')}</div>
            )}
            {filteredChats.map(chat => (
              <button key={chat.user_id}
                className={`chat-item ${selectedChat?.user_id === chat.user_id ? 'active' : ''}`}
                onClick={() => selectChat(chat)}
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

            {/* ── Archived chats ──────────────────────────────────────────── */}
            <button className="archived-toggle" onClick={handleToggleArchived}>
              <span>📦 {t('sidebar.archived')}</span>
              {archivedChats.length > 0 && <span className="unread-badge">{archivedChats.length}</span>}
              <span className="archived-toggle-arrow">{showArchived ? '▲' : '▼'}</span>
            </button>
            {showArchived && (
              archivedChats.length === 0
                ? <div className="empty-state" style={{ fontSize: 13 }}>{t('sidebar.noArchived')}</div>
                : archivedChats.map(chat => (
                  <div key={chat.user_id} className="chat-item archived-item">
                    <Avatar name={chat.name} src={chat.avatar} size={46} />
                    <div className="chat-item-body">
                      <div className="chat-item-row">
                        <span className="chat-item-name">{chat.name}</span>
                        <span className="chat-item-time">{formatChatTime(chat.time)}</span>
                      </div>
                      <div className="chat-item-row">
                        <span className="chat-item-preview">{previewLastMessage(chat.last_message)}</span>
                      </div>
                    </div>
                    <button className="icon-btn" title={t('chat.unarchive')}
                      onClick={e => { e.stopPropagation(); handleUnarchive(chat.user_id); }}>
                      📤
                    </button>
                  </div>
                ))
            )}
          </div>
        )}

        {/* ── Groups ────────────────────────────────────────────────────── */}
        {section === 'groups' && (
          <div className="list-scroll">
            <div className="search-box">
              <input
                value={groupSearch}
                onChange={e => handleGroupSearch(e.target.value)}
                placeholder={t('sidebar.searchGroups')}
              />
            </div>
            <form className="create-form" onSubmit={handleCreateGroup}>
              <input value={newGroupName} onChange={e => setNewGroupName(e.target.value)} placeholder={t('sidebar.newGroupName')} />
              <button type="submit" className="btn-sm">{t('sidebar.create')}</button>
            </form>
            {filteredGroups.length === 0 && <div className="empty-state">{t('sidebar.noGroups')}</div>}
            {filteredGroups.map(g => (
              <button key={g.id}
                className={`chat-item ${selectedGroup?.id === g.id ? 'active' : ''}`}
                onClick={() => setSelectedGroup(g)}
              >
                <Avatar name={asText(g.group_name, 'G')} src={g.avatar} size={44} />
                <div className="chat-item-body">
                  <div className="chat-item-row">
                    <span className="chat-item-name">{asText(g.group_name, t('nav.groups'))}</span>
                    <span className="chat-item-time">{g.time ? formatChatTime(g.time as string) : ''}</span>
                  </div>
                  <div className="chat-item-row">
                    <span className="chat-item-preview">
                      {g.last_message ? previewLastMessage(g.last_message) : `${g.members_count ?? 0} ${t('sidebar.members')}`}
                    </span>
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}

        {/* ── Channels ──────────────────────────────────────────────────── */}
        {section === 'channels' && (
          <div className="list-scroll">
            <div className="search-box">
              <input
                value={channelSearch}
                onChange={e => handleChannelSearch(e.target.value)}
                placeholder={t('sidebar.searchChannels')}
              />
            </div>
            <form className="create-form" onSubmit={handleCreateChannel}>
              <input value={newChannelName} onChange={e => setNewChannelName(e.target.value)} placeholder={t('sidebar.channelName')} />
              <input value={newChannelDesc} onChange={e => setNewChannelDesc(e.target.value)} placeholder={t('sidebar.description')} />
              <button type="submit" className="btn-sm">{t('sidebar.create')}</button>
            </form>
            {filteredChannels.length === 0 && <div className="empty-state">{t('sidebar.noChannels')}</div>}
            {filteredChannels.map(c => (
              <button key={c.id}
                className={`chat-item ${selectedChannel?.id === c.id ? 'active' : ''}`}
                onClick={() => setSelectedChannel(c)}
              >
                <Avatar name={asText(c.name, 'C')} src={c.avatar_url} size={44} />
                <div className="chat-item-body">
                  <div className="chat-item-row">
                    <span className="chat-item-name">{asText(c.name, t('nav.channels'))}</span>
                    <span className="chat-item-time">{c.time ? formatChatTime(c.time) : ''}</span>
                  </div>
                  <div className="chat-item-row">
                    <span className="chat-item-preview">
                      {c.last_post ?? `${c.subscribers_count ?? 0} ${t('sidebar.subscribers')}`}
                    </span>
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}

        {/* ── Stories ───────────────────────────────────────────────────── */}
        {section === 'stories' && (
          <div className="list-scroll">
            <form className="create-form" onSubmit={handleCreateStory}>
              <label className="file-label">
                {newStoryFile ? newStoryFile.name : t('sidebar.chooseMedia')}
                <input type="file" accept="image/*,video/*" style={{ display: 'none' }}
                  onChange={e => setNewStoryFile(e.target.files?.[0] ?? null)} />
              </label>
              <button type="submit" className="btn-sm" disabled={!newStoryFile}>{t('sidebar.uploadStory')}</button>
            </form>
            <div className="stories-grid">
              {stories.map((s, idx) => (
                <button key={s.id} className={`story-thumb ${s.is_seen ? 'story-seen' : ''}`} onClick={() => openStory(idx)}>
                  {s.file
                    ? s.file_type === 'video'
                      ? <video src={s.file} className="story-media" />
                      : <img src={s.file} alt="story" className="story-media" />
                    : <div className="story-placeholder">{s.user_name?.[0] ?? '?'}</div>
                  }
                  <span className="story-label">{s.user_name ?? `Story #${s.id}`}</span>
                  {!s.is_seen && <div className="story-unseen-ring" />}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* ── Calls ─────────────────────────────────────────────────────── */}
        {section === 'calls' && (
          <div className="list-scroll">
            {selectedChat && (
              <div className="call-controls">
                <p className="call-target">{t('call.callLabel')} <strong>{selectedChat.name}</strong></p>
                <button className="call-pill audio" onClick={() => startCall('audio')}>🎙 {t('call.voiceCall')}</button>
                <button className="call-pill video" onClick={() => startCall('video')}>📹 {t('call.videoCall')}</button>
              </div>
            )}
            <div className="call-history-header">
              <span className="call-history-title">{t('calls.history')}</span>
              <div className="call-filter-tabs">
                {(['all','missed','incoming','outgoing'] as const).map(f => (
                  <button key={f}
                    className={callHistoryFilter === f ? 'tab active' : 'tab'}
                    style={{ fontSize: 11, padding: '2px 8px' }}
                    onClick={() => {
                      setCallHistoryFilter(f);
                      setCallHistoryLoaded(false);
                      handleLoadCallHistory(f);
                    }}
                  >{t(`calls.${f}`)}</button>
                ))}
              </div>
              {callHistory.length > 0 && (
                <button className="btn-sm btn-outline" style={{ fontSize: 11 }} onClick={handleClearCallHistory}>
                  {t('calls.clearHistory')}
                </button>
              )}
            </div>
            {!callHistoryLoaded ? (
              <div className="empty-state" style={{ cursor: 'pointer' }} onClick={() => handleLoadCallHistory(callHistoryFilter)}>
                {t('calls.loadHistory')}
              </div>
            ) : callHistory.length === 0 ? (
              <div className="empty-state">{t('calls.noHistory')}</div>
            ) : callHistory.map(c => {
              const isOut = c.direction === 'outgoing';
              const isMissed = !isOut && (c.status === 'missed' || c.status === 'rejected' || c.status === 'failed');
              const name = c.call_category === 'personal'
                ? (c.other_user?.name || c.other_user?.username || `User ${c.other_user?.user_id}`)
                : (c.group_data?.group_name || `Group`);
              const avatar = c.call_category === 'personal' ? c.other_user?.avatar : c.group_data?.avatar;
              const icon = c.call_type === 'video' ? '📹' : '🎙';
              const dirIcon = isOut ? '↗' : isMissed ? '✕' : '↙';
              const durStr = c.duration > 0
                ? `${Math.floor(c.duration / 60)}:${String(c.duration % 60).padStart(2, '0')}`
                : '';
              const dateStr = new Date(c.timestamp * 1000).toLocaleDateString();
              return (
                <div key={c.id} className="call-history-item">
                  <div className="call-history-avatar"><Avatar name={name} src={avatar} size={36} /></div>
                  <div className="call-history-info">
                    <div className="call-history-name">{name}</div>
                    <div className={`call-history-meta${isMissed ? ' missed' : ''}`}>
                      <span>{dirIcon} {icon}</span>
                      <span>{dateStr}</span>
                      {durStr && <span>{durStr}</span>}
                    </div>
                  </div>
                  <button className="call-history-delete" title="Delete" onClick={() => handleDeleteCall(c.id)}>✕</button>
                </div>
              );
            })}
          </div>
        )}

        {/* ── Settings ──────────────────────────────────────────────────── */}
        {section === 'settings' && (
          <div className="list-scroll settings-panel">
            {/* ── Profile header ──────────────────────────────────────── */}
            <div className="settings-item">
              <label className="avatar-upload-label" title={t('settings.uploadAvatar')}>
                <Avatar name={session.username} src={myProfile?.avatar} size={56} />
                <input type="file" accept="image/*" style={{ display: 'none' }}
                  onChange={e => { const f = e.target.files?.[0]; if (f) handleAvatarUpload(f); }} />
                <span className="avatar-upload-badge">📷</span>
              </label>
              <div>
                <div className="settings-name">{myProfile ? `${myProfile.first_name ?? ''} ${myProfile.last_name ?? ''}`.trim() || session.username : session.username}</div>
                <div className="settings-sub">{t('settings.userId')} {session.userId}</div>
              </div>
            </div>

            {/* ── Edit profile ────────────────────────────────────────── */}
            <div className="settings-section">
              <div className="settings-label">{t('settings.editProfile')}</div>
              <input className="settings-input" placeholder={t('settings.firstName')}
                value={profileFirst} onChange={e => setProfileFirst(e.target.value)} />
              <input className="settings-input" placeholder={t('settings.lastName')}
                value={profileLast} onChange={e => setProfileLast(e.target.value)} />
              <input className="settings-input" placeholder={t('settings.username')}
                value={profileUser} onChange={e => setProfileUser(e.target.value)} />
              <textarea className="settings-input settings-textarea" placeholder={t('settings.about')}
                value={profileAbout} onChange={e => setProfileAbout(e.target.value)} rows={3} />
              <button
                className={profileSaving === 'done' ? 'btn-success' : profileSaving === 'error' ? 'btn-danger' : 'btn-primary'}
                disabled={profileSaving === 'saving'}
                onClick={handleSaveProfile}
              >
                {profileSaving === 'saving' ? t('settings.saving') :
                 profileSaving === 'done'   ? t('settings.saved') :
                 profileSaving === 'error'  ? t('settings.errorRetry') :
                 t('settings.saveProfile')}
              </button>
            </div>

            {/* ── Blocked users ────────────────────────────────────────── */}
            <div className="settings-section">
              <div className="settings-label"
                style={{ cursor: 'pointer' }}
                onClick={() => { handleLoadBlocked(); setBlockedLoaded(true); }}>
                {t('settings.blockedUsers')} {blockedUsers.length > 0 ? `(${blockedUsers.length})` : ''}
              </div>
              {blockedLoaded && (
                blockedUsers.length === 0
                  ? <div className="empty-state" style={{ fontSize: 13 }}>{t('settings.noBlocked')}</div>
                  : blockedUsers.map(u => (
                    <div key={u.id} className="settings-row">
                      <div className="settings-blocked-info">
                        <Avatar name={u.first_name ?? u.username} src={u.avatar} size={32} />
                        <span>{u.first_name ? `${u.first_name} ${u.last_name ?? ''}`.trim() : u.username}</span>
                      </div>
                      <button className="btn-sm btn-outline" onClick={() => handleUnblock(u.id)}>
                        {t('settings.unblock')}
                      </button>
                    </div>
                  ))
              )}
            </div>

            {/* ── Privacy ──────────────────────────────────────────────── */}
            <div className="settings-section">
              <div className="settings-label" style={{ cursor: privacyLoaded ? 'default' : 'pointer' }}
                onClick={() => !privacyLoaded && handleLoadPrivacy()}>
                {t('settings.privacy')}
              </div>
              {!privacyLoaded ? (
                <button className="btn-secondary" style={{ fontSize: 12, padding: '4px 10px' }} onClick={handleLoadPrivacy}>
                  {t('settings.loadPrivacy')}
                </button>
              ) : privacySettings && (<>
                <div className="privacy-row">
                  <span className="privacy-label">{t('settings.showLastSeen')}</span>
                  <select className="privacy-select" value={privacySettings.showlastseen}
                    onChange={e => setPrivacySettings(p => p ? { ...p, showlastseen: e.target.value } : p)}>
                    <option value="1">{t('settings.show')}</option>
                    <option value="0">{t('settings.hide')}</option>
                  </select>
                </div>
                <div className="privacy-row">
                  <span className="privacy-label">{t('settings.messagePrivacy')}</span>
                  <select className="privacy-select" value={privacySettings.message_privacy}
                    onChange={e => setPrivacySettings(p => p ? { ...p, message_privacy: e.target.value } : p)}>
                    <option value="0">{t('settings.everyone')}</option>
                    <option value="1">{t('settings.following')}</option>
                    <option value="2">{t('settings.nobody')}</option>
                  </select>
                </div>
                <div className="privacy-row">
                  <span className="privacy-label">{t('settings.followPrivacy')}</span>
                  <select className="privacy-select" value={privacySettings.follow_privacy}
                    onChange={e => setPrivacySettings(p => p ? { ...p, follow_privacy: e.target.value } : p)}>
                    <option value="0">{t('settings.everyone')}</option>
                    <option value="1">{t('settings.onlyMe')}</option>
                  </select>
                </div>
                <div className="privacy-row">
                  <span className="privacy-label">{t('settings.confirmFollowers')}</span>
                  <select className="privacy-select" value={privacySettings.confirm_followers}
                    onChange={e => setPrivacySettings(p => p ? { ...p, confirm_followers: e.target.value } : p)}>
                    <option value="0">{t('settings.no')}</option>
                    <option value="1">{t('settings.yes')}</option>
                  </select>
                </div>
                <button
                  className={privacySaving === 'done' ? 'btn-success' : privacySaving === 'error' ? 'btn-danger' : 'btn-primary'}
                  disabled={privacySaving === 'saving'}
                  style={{ marginTop: 8 }}
                  onClick={handleSavePrivacy}
                >
                  {privacySaving === 'saving' ? t('settings.saving') :
                   privacySaving === 'done'   ? t('settings.saved') :
                   privacySaving === 'error'  ? t('settings.errorRetry') :
                   t('settings.saveProfile')}
                </button>
              </>)}
            </div>

            <div className="settings-section">
              <div className="settings-label">{t('settings.language')}</div>
              <div className="settings-row">
                {(['ru', 'uk', 'en'] as Lang[]).map(l => (
                  <button key={l}
                    className={lang === l ? 'tab active' : 'tab'}
                    style={{ marginRight: 4 }}
                    onClick={() => handleLangChange(l)}
                  >
                    {t(`lang.${l}`)}
                  </button>
                ))}
              </div>
            </div>
            <div className="settings-section">
              <div className="settings-label">{t('settings.security')}</div>
              <div className="settings-row">
                <span>{t('settings.e2ee')}</span>
                <span className="badge-green">{t('settings.signalBadge')}</span>
              </div>
              <div className="settings-row">
                <span>{t('settings.keysReg')}</span>
                <span className="badge-green">✓</span>
              </div>
              <div className="settings-row" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: 8 }}>
                <span style={{ fontSize: 12, opacity: 0.7 }}>{t('settings.e2eeHint')}</span>
                <button
                  className={signalResetStatus === 'done' ? 'btn-success' : 'btn-secondary'}
                  disabled={signalResetStatus === 'working'}
                  onClick={async () => {
                    const svc = signalRef.current;
                    if (!svc) return;
                    setSignalResetStatus('working');
                    try {
                      svc.clearAllSignalState();
                      await svc.ensureRegistered();
                      setSignalResetStatus('done');
                      signalRef.current = SignalService.getInstance(createNodeApiShim(session.token));
                      setTimeout(() => setSignalResetStatus('idle'), 4000);
                      console.info('[Signal] Keys reset and re-registered — contacts will receive identity_changed');
                    } catch {
                      setSignalResetStatus('error');
                      setTimeout(() => setSignalResetStatus('idle'), 4000);
                    }
                  }}
                >
                  {signalResetStatus === 'working' ? t('settings.resetting') :
                   signalResetStatus === 'done'    ? t('settings.keysReset') :
                   signalResetStatus === 'error'   ? t('settings.errorRetry') :
                   t('settings.resetKeys')}
                </button>
              </div>
            </div>
            <div className="settings-section">
              <div className="settings-label">{t('settings.connection')}</div>
              <div className="settings-row">
                <span>{t('settings.socketStatus')}</span>
                <span>{translateSocketStatus(socketStatus)}</span>
              </div>
            </div>
            <button className="btn-danger" onClick={logout}>{t('settings.signOut')}</button>
          </div>
        )}
      </aside>

      {/* ── Story viewer overlay ──────────────────────────────────────────── */}
      {viewingStoryIdx !== null && stories[viewingStoryIdx] && (() => {
        const s = stories[viewingStoryIdx];
        return (
          <div className="story-viewer" onClick={closeStory}>
            <div className="story-viewer-progress">
              {stories.map((_, i) => (
                <div key={i} className="story-progress-track">
                  <div
                    className="story-progress-fill"
                    style={{ width: i < viewingStoryIdx ? '100%' : i === viewingStoryIdx ? `${storyProgress}%` : '0%' }}
                  />
                </div>
              ))}
            </div>
            <div className="story-viewer-header">
              <Avatar name={s.user_name ?? '?'} src={s.user_avatar} size={36} />
              <span className="story-viewer-name">{s.user_name ?? `Story #${s.id}`}</span>
              <span className="story-viewer-time">{s.created_at ?? ''}</span>
              <button className="story-viewer-close" onClick={e => { e.stopPropagation(); closeStory(); }}>✕</button>
            </div>
            <div className="story-viewer-media" onClick={e => e.stopPropagation()}>
              {s.file
                ? s.file_type === 'video'
                  ? <video src={s.file} autoPlay loop className="story-viewer-img" />
                  : <img src={s.file} alt="story" className="story-viewer-img" />
                : <div className="story-viewer-placeholder">{s.user_name?.[0] ?? '?'}</div>
              }
            </div>
            <button className="story-nav story-nav-prev" onClick={e => { e.stopPropagation(); prevStory(); }}>‹</button>
            <button className="story-nav story-nav-next" onClick={e => { e.stopPropagation(); nextStory(); }}>›</button>
          </div>
        );
      })()}

      {/* ── Lightbox ─────────────────────────────────────────────────────── */}
      {lightboxSrc && (
        <div className="lightbox" onClick={() => setLightboxSrc(null)}>
          <button className="lightbox-close" onClick={e => { e.stopPropagation(); setLightboxSrc(null); }}>✕</button>
          <img
            src={lightboxSrc}
            alt="full-size"
            className="lightbox-img"
            onClick={e => e.stopPropagation()}
          />
        </div>
      )}

      {/* ── Main chat view ────────────────────────────────────────────────── */}
      <main className="chat-main">
        {/* ── Group chat ──────────────────────────────────────────────────── */}
        {section === 'groups' && selectedGroup ? (
          <>
            <div className="chat-header">
              <div className="chat-header-left">
                <Avatar name={asText(selectedGroup.group_name, 'G')} src={selectedGroup.avatar} size={38} />
                <div className="chat-header-info">
                  <span className="chat-header-name">{asText(selectedGroup.group_name, t('nav.groups'))}</span>
                  <span className="chat-header-status">{selectedGroup.members_count ?? 0} {t('sidebar.members')}</span>
                </div>
              </div>
            </div>
            <div className="messages-scroll" ref={messagesScrollRef}>
              {groupHasMore && (
                <button className="load-more" onClick={handleLoadMoreGroupMessages}>{t('chat.loadEarlier')}</button>
              )}
              {groupMsgLoading && <div className="messages-loading"><div className="spinner" /></div>}
              {groupMsgError && (
                <div className="msg-load-error">
                  <p>{t('chat.loadError')}</p>
                  <button className="retry-btn" onClick={() => {
                    if (!session || !selectedGroup) return;
                    setGroupMsgError(false); setGroupMsgLoading(true);
                    loadGroupMessages(session.token, selectedGroup.id)
                      .then(r => { setGroupMessages(r.messages ?? []); setGroupMsgLoading(false); })
                      .catch(() => { setGroupMsgError(true); setGroupMsgLoading(false); });
                  }}>{t('chat.retry')}</button>
                </div>
              )}
              {groupMessages.map((msg, idx) => {
                const isOwn = msg.from_id === session.userId;
                const prevMsg = groupMessages[idx - 1];
                const showSenderName = !isOwn && (!prevMsg || prevMsg.from_id !== msg.from_id);
                return (
                  <div key={msg.id} className={`msg-row ${isOwn ? 'own' : ''}`}>
                    {!isOwn && (
                      <div style={{ width: 28, flexShrink: 0, alignSelf: 'flex-end', paddingBottom: 4 }}>
                        {showSenderName && <Avatar name={msg.sender_name ?? `User ${msg.from_id}`} size={28} />}
                      </div>
                    )}
                    <div className="bubble-row">
                      <div className={`bubble ${isOwn ? 'own' : ''} ${msg._pending ? 'pending' : ''}`}>
                        {showSenderName && !isOwn && (
                          <span className="group-sender-name">{msg.sender_name ?? `User ${msg.from_id}`}</span>
                        )}
                        {msg.reply_to && (
                          <div className="reply-quote">
                            <div className="reply-bar" />
                            <div className="reply-content">
                              <span className="reply-text">{asText(msg.reply_to.text, t('bubble.media')).slice(0, 80)}</span>
                            </div>
                          </div>
                        )}
                        {msg.media && (
                          <div className="bubble-media">
                            {msg.media_type === 'image' || (!msg.media_type && /\.(jpg|jpeg|png|gif|webp)$/i.test(msg.media))
                              ? <img src={msg.media} alt="media" className="media-img" onClick={() => setLightboxSrc(msg.media!)} />
                              : msg.media_type === 'video'
                                ? <video src={msg.media} controls className="media-video" />
                                : <a href={msg.media} target="_blank" rel="noreferrer" className="media-file">📎 {msg.media_filename ?? t('misc.downloadFile')}</a>
                            }
                          </div>
                        )}
                        {msg.text && (() => {
                          const call = parseCallMessage(msg.text);
                          return call
                            ? <CallBubble call={call} />
                            : <p className="bubble-text">{renderText(msg.text)}{msg.is_edited && <span className="edited-mark">{t('bubble.edited')}</span>}</p>;
                        })()}
                        <div className="bubble-footer">
                          <time className="bubble-time">{formatTime(msg.time_text, msg.time)}</time>
                          {isOwn && <span className="seen-tick">{msg.is_seen ? '✓✓' : '✓'}</span>}
                        </div>
                        {(msg.reactions ?? []).length > 0 && (
                          <div className="reactions">
                            {msg.reactions!.map((r, i) => (
                              <button key={i} className={`reaction-chip ${r.user_ids.includes(session.userId) ? 'mine' : ''}`}
                                onClick={() => reactToGroupMessage(session.token, msg.id, r.emoji).catch(console.error)}>
                                {r.emoji} {r.count}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                      {isOwn && (
                        <div className="bubble-actions actions-left">
                          <button className="action-btn" title={t('bubble.reply')} onClick={() => setGroupReplyTarget({ id: msg.id, from_id: msg.from_id, text: asText(msg.text, t('bubble.media')) })}>↩</button>
                          <button className="action-btn" title={t('bubble.edit')} onClick={() => { setGroupEditingMsg(msg); setNewGroupMessage(asText(msg.text, '')); }}>✎</button>
                          <button className="action-btn" title={t('bubble.delete')} onClick={() => handleDeleteGroupMessage(msg)}>🗑</button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>
            {sendError && <div className="send-error-banner">{sendError}</div>}
            <div className="composer">
              {(groupReplyTarget || groupEditingMsg) && (
                <div className="composer-banner">
                  <div className="composer-banner-line" />
                  <div className="composer-banner-content">
                    <span className="composer-banner-label">{groupEditingMsg ? t('chat.editing') : t('chat.replyTo')}</span>
                    <span className="composer-banner-text">{groupEditingMsg ? asText(groupEditingMsg.text, '').slice(0, 60) : groupReplyTarget!.text.slice(0, 60)}</span>
                  </div>
                  <button className="composer-banner-close" onClick={() => { setGroupReplyTarget(null); setGroupEditingMsg(null); setNewGroupMessage(''); }}>✕</button>
                </div>
              )}
              <form className="composer-row" onSubmit={handleSendGroupMessage}>
                <textarea
                  className="composer-input"
                  placeholder={t('chat.writePlaceholder')}
                  value={newGroupMessage}
                  rows={1}
                  onChange={e => setNewGroupMessage(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendGroupMessage(); } }}
                />
                <button type="submit" className={`send-btn ${newGroupMessage.trim() ? 'active' : ''}`} disabled={!newGroupMessage.trim() && !groupEditingMsg}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M22 2L11 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
                </button>
              </form>
            </div>
          </>
        ) : section === 'groups' ? (
          <div className="chat-empty"><div className="chat-empty-icon">👥</div><h3>{t('sidebar.groups')}</h3><p>{t('sidebar.noGroups')}</p></div>

        /* ── Channel posts ─────────────────────────────────────────────────── */
        ) : section === 'channels' && selectedChannel ? (
          <>
            <div className="chat-header">
              <div className="chat-header-left">
                {commentPost
                  ? <button className="icon-btn" onClick={() => setCommentPost(null)}>←</button>
                  : null
                }
                <Avatar name={asText(selectedChannel.name, 'C')} src={selectedChannel.avatar_url} size={38} />
                <div className="chat-header-info">
                  <span className="chat-header-name">{asText(selectedChannel.name, t('nav.channels'))}</span>
                  <span className="chat-header-status">{selectedChannel.subscribers_count ?? 0} {t('sidebar.subscribers')}</span>
                </div>
              </div>
            </div>
            {commentPost ? (
              /* ── Comments panel ─────────────────────────────────────────── */
              <div className="comments-panel">
                <div className="comments-header">
                  <button className="icon-btn" onClick={() => setCommentPost(null)}>←</button>
                  <span>{t('channel.comments')} · {commentPost.text?.slice(0, 30) || '...'}</span>
                </div>
                <div className="comments-list">
                  {commentsLoading && <div className="messages-loading"><div className="spinner" /></div>}
                  {comments.map(c => (
                    <div key={c.id} className="comment-item">
                      <Avatar name={c.user_name ?? c.username ?? '?'} src={c.user_avatar} size={32} />
                      <div className="comment-body">
                        <div className="comment-meta">
                          <span className="comment-author">{c.user_name ?? c.username ?? `User ${c.user_id}`}</span>
                          <span className="comment-time">{new Date(c.time * 1000).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})}</span>
                        </div>
                        <p className="comment-text">{renderText(c.text)}</p>
                        <div className="comment-actions">
                          {EMOJI_QUICK.slice(0, 5).map(e => (
                            <button key={e} className="reaction-chip add-reaction" onClick={() => handleReactComment(c.id, e)}>{e}</button>
                          ))}
                          <button className="action-btn" onClick={() => setCommentReplyTo({id: c.id, text: c.text.slice(0, 60)})}>↩</button>
                          {c.user_id === session.userId && (
                            <button className="action-btn" onClick={() => handleDeleteComment(c.id)}>🗑</button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                  <div ref={commentsEndRef} />
                </div>
                {commentReplyTo && (
                  <div className="composer-banner">
                    <div className="composer-banner-line" />
                    <div className="composer-banner-content">
                      <span className="composer-banner-label">{t('chat.replyTo')}</span>
                      <span className="composer-banner-text">{commentReplyTo.text}</span>
                    </div>
                    <button className="composer-banner-close" onClick={() => setCommentReplyTo(null)}>✕</button>
                  </div>
                )}
                <form className="composer-row" onSubmit={handleSendComment} style={{padding:'8px 12px'}}>
                  <textarea className="composer-input" placeholder={t('channel.addComment')} value={newComment}
                    rows={1} onChange={e => setNewComment(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendComment(e as unknown as React.FormEvent); }}} />
                  <button type="submit" className="send-btn" disabled={!newComment.trim()}>➤</button>
                </form>
              </div>
            ) : (
              /* ── Posts list (compact) ───────────────────────────────────── */
              <>
                <div className="messages-scroll channel-scroll" ref={messagesScrollRef}>
                  {channelPostsLoading && <div className="messages-loading"><div className="spinner" /></div>}
                  {!channelPostsLoading && channelPosts.length === 0 && (
                    <div className="chat-empty"><div className="chat-empty-icon">📢</div><h3>{t('sidebar.noChannels')}</h3></div>
                  )}
                  <div className="channel-feed">
                    {channelPosts.map(post => (
                      <div key={post.id} className="cp">
                        <div className="cp-header">
                          <Avatar name={asText(selectedChannel.name, 'C')} src={selectedChannel.avatar_url} size={24} />
                          <span className="cp-author">{asText(selectedChannel.name, '')}</span>
                          <span className="cp-time">{post.time ? formatTime(post.time) : ''}</span>
                          {post.is_pinned && <span className="cp-pin">📌</span>}
                          {post.publisher_id === session.userId && (
                            <button className="action-btn" style={{ marginLeft: 'auto' }} title={t('bubble.delete')}
                              onClick={() => handleDeleteChannelPost(post.id)}>🗑</button>
                          )}
                        </div>
                        {/* Multi-media gallery */}
                        {(post.media_items && post.media_items.length > 0) && (
                          <div className={`post-gallery count-${Math.min(post.media_items.length, 4)}`}>
                            {post.media_items.map((item, i) => (
                              <div key={i} className="post-gallery-item">
                                {item.type === 'video'
                                  ? <video src={item.url} controls className="post-gallery-media" />
                                  : item.type === 'image'
                                    ? <img src={item.url} alt="" className="post-gallery-media" onClick={() => setLightboxSrc(item.url)} />
                                    : <a href={item.url} target="_blank" rel="noreferrer" className="media-file">📎 {t('misc.downloadFile')}</a>
                                }
                              </div>
                            ))}
                          </div>
                        )}
                        {/* Poll widget */}
                        {post.poll && (
                          <PollWidget poll={post.poll} postId={post.id}
                            onVote={(pollId, optId) => handleVotePoll(pollId, optId)} />
                        )}
                        {post.text && <p className="cp-text">{post.text}</p>}
                        <div className="cp-footer">
                          <span className="cp-stat">👁 {post.views_count ?? 0}</span>
                          <button className="cp-comment-btn" onClick={() => handleOpenComments(post)}>
                            💬 {post.comments_count ?? 0}
                          </button>
                          <div className="reactions" style={{ marginTop: 0 }}>
                            {(post.reactions ?? []).map((r, i) => (
                              <button key={i} className="reaction-chip"
                                onClick={() => { if (session) reactToChannelPost(session.token, post.id, r.emoji).catch(console.error); }}>
                                {r.emoji} {r.count}
                              </button>
                            ))}
                            {EMOJI_QUICK.slice(0, 5).map(e => (
                              <button key={e} className="reaction-chip add-reaction"
                                onClick={() => { if (session) reactToChannelPost(session.token, post.id, e).then(() => {
                                  setChannelPosts(prev => prev.map(p => p.id !== post.id ? p : {
                                    ...p, reactions: (() => {
                                      const ex = (p.reactions ?? []).find(r => r.emoji === e);
                                      return ex
                                        ? (p.reactions ?? []).map(r => r.emoji === e ? { ...r, count: r.count + 1 } : r)
                                        : [...(p.reactions ?? []), { emoji: e, count: 1, user_ids: [session.userId] }];
                                    })()
                                  }));
                                }).catch(console.error); }}>
                                {e}
                              </button>
                            ))}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                  {channelHasMore && (
                    <button className="load-more" onClick={handleLoadMoreChannelPosts}>{t('chat.loadEarlier')}</button>
                  )}
                  <div ref={messagesEndRef} />
                </div>
                <div className="composer">
                  {channelPostMedia && (
                    <div className="attachment-preview">
                      <span>📎 {channelPostMedia.name}</span>
                      <button onClick={() => setChannelPostMedia(null)}>✕</button>
                    </div>
                  )}
                  <form className="composer-row" onSubmit={handleCreateChannelPost}>
                    <label className="icon-btn attach-btn" title={t('chat.attachFile')}>
                      📎
                      <input type="file" style={{ display: 'none' }} accept="image/*,video/*"
                        onChange={e => setChannelPostMedia(e.target.files?.[0] ?? null)} />
                    </label>
                    <textarea
                      className="composer-input"
                      placeholder={t('sidebar.uploadStory')}
                      value={newChannelPost}
                      rows={1}
                      onChange={e => setNewChannelPost(e.target.value)}
                      onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleCreateChannelPost(e as unknown as React.FormEvent); } }}
                    />
                    <button type="submit" className={`send-btn ${newChannelPost.trim() || channelPostMedia ? 'active' : ''}`}
                      disabled={!newChannelPost.trim() && !channelPostMedia}>
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M22 2L11 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
                    </button>
                  </form>
                </div>
              </>
            )}
          </>
        ) : section === 'channels' ? (
          <div className="chat-empty"><div className="chat-empty-icon">📢</div><h3>{t('sidebar.channels')}</h3><p>{t('sidebar.noChannels')}</p></div>

        /* ── Private chat (existing) ──────────────────────────────────────── */
        ) : !selectedChat || (section !== 'chats' && section !== 'calls') ? (
          <div className="chat-empty">
            <div className="chat-empty-icon">💬</div>
            <h3>{t('chat.selectConversation')}</h3>
            <p>{t('chat.selectConversationHint')}</p>
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
                      ? t('chat.typing')
                      : onlineUsers.has(selectedChat.user_id) ? t('chat.online') : t('chat.offline')}
                  </span>
                </div>
              </div>
              <div className="chat-header-actions">
                <button className="icon-btn" title={t('chat.search')} onClick={openChatSearch}>
                  🔍
                </button>
                <button className="icon-btn" title={t('call.voiceCall')} onClick={() => { setSection('calls'); startCall('audio'); }}>
                  🎙
                </button>
                <button className="icon-btn" title={t('call.videoCall')} onClick={() => { setSection('calls'); startCall('video'); }}>
                  📹
                </button>
                <button className="icon-btn" title={t('chat.mute')} onClick={() => muteChat(session.token, selectedChat.user_id, true)}>
                  🔕
                </button>
                <button className="icon-btn" title={t('chat.archive')} onClick={() => archiveChat(session.token, selectedChat.user_id, true)}>
                  📦
                </button>
                <button className="icon-btn danger" title={t('chat.block')} onClick={() => handleBlockUser(selectedChat.user_id)}>
                  🚫
                </button>
                <button className="icon-btn danger" title={t('chat.deleteConversation')}
                  onClick={() => { if (window.confirm(t('chat.deleteConversationConfirm'))) deleteConversation(session.token, selectedChat.user_id); }}>
                  🗑
                </button>
              </div>
            </div>

            {/* ── In-chat search panel ─────────────────────────────────── */}
            {chatSearchOpen && (
              <div className="chat-search-panel">
                <div className="chat-search-row">
                  <input
                    autoFocus
                    className="chat-search-input"
                    placeholder={t('chat.searchPlaceholder')}
                    value={chatSearchQuery}
                    onChange={e => handleChatSearchInput(e.target.value)}
                  />
                  <button className="icon-btn" onClick={closeChatSearch}>✕</button>
                </div>
                {chatSearchLoading && <div className="chat-search-loading">…</div>}
                {!chatSearchLoading && chatSearchQuery && chatSearchResults.length === 0 && (
                  <div className="chat-search-empty">{t('chat.searchEmpty')}</div>
                )}
                {chatSearchResults.map(msg => (
                  <div key={msg.id} className="chat-search-result">
                    <time className="chat-search-time">{formatTime(msg.time_text, msg.time)}</time>
                    <p className="chat-search-text">{msg.text ?? t('bubble.media')}</p>
                  </div>
                ))}
              </div>
            )}

            {/* ── Messages ─────────────────────────────────────────────── */}
            <div className="messages-scroll" ref={messagesScrollRef}>
              {hasMore && (
                <button className="load-more" onClick={handleLoadMore}>{t('chat.loadEarlier')}</button>
              )}

              {messagesLoading && (
                <div className="messages-loading">
                  <div className="spinner" />
                </div>
              )}

              {!messagesLoading && msgLoadError && messages.length === 0 && (
                <div className="msg-load-error">
                  <p>{t('chat.loadError').split('\n').map((line, i) => <span key={i}>{line}{i === 0 && <br />}</span>)}</p>
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
                  }}>{t('chat.retry')}</button>
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
                      onOpenMedia={setLightboxSrc}
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
                      {editingMsg ? t('chat.editing') : `${t('chat.replyTo')}${replyTarget!.from_id === session.userId ? t('chat.yourself') : selectedChat.name}`}
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
                <label className="icon-btn attach-btn" title={t('chat.attachFile')}>
                  📎
                  <input type="file" style={{ display: 'none' }}
                    accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar"
                    onChange={e => setPendingMedia(e.target.files?.[0] ?? null)} />
                </label>

                {/* Message input */}
                <textarea
                  ref={composerRef}
                  className="composer-input"
                  placeholder={editingMsg ? t('chat.editPlaceholder') : t('chat.writePlaceholder')}
                  value={newMessage}
                  rows={1}
                  onChange={e => handleComposerInputWithDraft(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
                  }}
                />

                {/* Voice record button (hidden when text/media is ready) */}
                {!newMessage.trim() && !pendingMedia && !editingMsg && (
                  <button
                    className={`icon-btn voice-btn ${isRecordingVoice ? 'recording' : ''}`}
                    title={isRecordingVoice ? t('chat.stopRecording') : t('chat.voiceMessage')}
                    onMouseDown={startVoiceRecording}
                    onMouseUp={stopVoiceRecording}
                    onMouseLeave={isRecordingVoice ? stopVoiceRecording : undefined}
                  >
                    🎤
                  </button>
                )}

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
