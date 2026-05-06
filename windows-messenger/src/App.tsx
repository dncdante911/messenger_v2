import React, { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { t, initLang, setLang, getLang, translateSocketStatus, type Lang } from './i18n';
import type { Socket } from 'socket.io-client';
import {
  archiveChat, AuthError, clearHistory,
  blockUser, unblockUser, loadBlockedUsers,
  createChannelFull, createChannelPost, deleteChannelPost, loadChannelPosts, loadMoreChannelPosts, markChannelPostViewed, reactToChannelPost, searchChannels, voteChannelPoll,
  loadChannelComments, addChannelComment, deleteChannelComment, reactToChannelComment,
  createGroup, createStory, joinGroup, subscribeChannel, unsubscribeChannel, deleteChannel, searchGroups,
  createNodeApiShim, deleteConversation, deleteMessage, deleteGroupMessage, editMessage, editGroupMessage,
  endCall, loadChannels, loadChats, loadArchivedChats,
  loadGroups, loadGroupMessages, loadMoreGroupMessages, loadMessages, loadMoreMessages, loadStories,
  login, loginByPhone, markGroupSeen, markSeen, markStorySeen,
  muteChat, normaliseMessage, pinChat, pinMessage,
  getMyProfile, updateMyProfile, uploadAvatar,
  setCustomStatus,
  loadNotificationSettings, updateNotificationSettings, type NotificationSettings,
  loadCallHistory, deleteCallRecord, clearCallHistory,
  loadPrivacySettings, updatePrivacySettings,
  reactToMessage, reactToGroupMessage, registerAccount,
  searchMessages, searchUsers,
  sendMessage, sendGroupMessage, sendMessageWithMedia, sendVoiceMessage, TURN_FALLBACK,
  createTurnIceServers,
  type UserSearchResult,
  uploadMedia,
  loadStickerPacks, sendStickerMessage, sendGifMessage, loadTrendingGifs, searchGifs, searchBots,
  getBotLinkedUser,
  type UserProfile,
  listSaved, saveMessage as apiSaveMessage, unsaveMessage, type SavedMessageItem,
  listNotes, createNote, deleteNote, getNotesStorage, type NoteItem, type NotesStorageInfo,
  deleteStory, reactToStory as apiReactToStory,
  getStoryComments, createStoryComment, deleteStoryComment,
  listScheduledMessages, createScheduledMessage, deleteScheduledMessage, sendScheduledNow,
  type ScheduledMessage,
} from './api';
import type { ChannelPost, ChannelPoll, ChannelComment, PollOption, StickerPack, GifItem, BotItem, StoryComment } from './types';
import ChannelAdminPanel from './ChannelAdminPanel';
import GroupAdminPanel from './GroupAdminPanel';
import VideoPlayer from './VideoPlayer';
import MediaGallery, { type MediaItem } from './MediaGallery';
import { SignalService, CIPHER_VERSION_SIGNAL } from './signalService';
import { signalSelfTest } from './signal';
import {
  createChatSocket, emitChatClose, emitChatOpen,
  emitCallInitiate, emitCallAccept, emitCallEnd, emitCallReject,
  emitIceCandidate,
  emitGroupCallJoin, emitGroupCallEnd,
  emitTyping,
} from './socket';
import { createLocalVideoStream, createLocalAudioStream, createPeerConnection, createScreenShareStream, replaceVideoTrack } from './webrtc';
import { getChatMedia, getGroupMedia, getChannelMedia } from './api';
import type {
  ActiveSection, CallHistoryItem, CallState, ChatItem, ChannelItem, GroupItem,
  GroupCallPeer, MessageItem, PrivacySettings, ReplyTarget, Session, StoryItem
} from './types';

// ─── Constants ────────────────────────────────────────────────────────────────

const SESSION_KEY = 'wm_windows_session';

const EMOJI_QUICK = ['👍','❤️','😂','😮','😢','😡','🔥','👏','🎉','💯'];

const EMOJI_CATEGORIES: { label: string; icon: string; emojis: string[] }[] = [
  { label: 'Smileys', icon: '😀', emojis: ['😀','😃','😄','😁','😆','😅','🤣','😂','🙂','🙃','😉','😊','😇','🥰','😍','🤩','😘','😗','😚','😙','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🤫','🤔','🤐','🤨','😐','😑','😶','😏','😒','🙄','😬','🤥','😌','😔','😪','🤤','😴','😷','🤒','🤕','🤢','🤮','🤧','🥵','🥶','🥴','😵','🤯','🤠','🥳','😎','🤓','🧐','😕','😟','🙁','☹️','😮','😯','😲','😳','🥺','😦','😧','😨','😰','😥','😢','😭','😱','😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿'] },
  { label: 'Gestures', icon: '👋', emojis: ['👋','🤚','🖐','✋','🖖','👌','🤌','🤏','✌️','🤞','🤟','🤘','🤙','👈','👉','👆','🖕','👇','☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝','🙏','✍️','💅','🤳','💪','🦾','🦿','🦵','🦶','👂','🦻','👃','🫀','🫁','🧠','🦷','🦴','👀','👁','👅','👄','💋','🩸'] },
  { label: 'Animals', icon: '🐶', emojis: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈','🙉','🙊','🐔','🐧','🐦','🐤','🦆','🦅','🦉','🦇','🐺','🐗','🐴','🦄','🐝','🐛','🦋','🐌','🐞','🐜','🦟','🦗','🕷','🦂','🐢','🐍','🦎','🦖','🦕','🐙','🦑','🦐','🦞','🦀','🐡','🐠','🐟','🐬','🐳','🐋','🦈','🐊','🐅','🐆','🦓','🦍','🦧','🦣','🐘','🦛','🦏','🐪','🐫','🦒','🦘','🦬','🐃','🐂','🐄','🐎','🐖','🐏','🐑','🦙','🐐','🦌','🐕','🐩','🦮','🐕‍🦺','🐈','🐈‍⬛','🐓','🦃','🦤','🦚','🦜','🦢','🦩','🕊','🐇','🦝','🦨','🦡','🦫','🦦','🦥','🐁','🐀','🐿','🦔'] },
  { label: 'Food', icon: '🍕', emojis: ['🍎','🍊','🍋','🍇','🍓','🍒','🍑','🥭','🍍','🥥','🥝','🍅','🫐','🍆','🥑','🥦','🧄','🧅','🥔','🌽','🥕','🫛','🌶','🫑','🥒','🥬','🧇','🧆','🧀','🍳','🥚','🍖','🍗','🥩','🍔','🍟','🌭','🍕','🫓','🥪','🌮','🌯','🫔','🥙','🧆','🥗','🫕','🍱','🍘','🍙','🍚','🍛','🍜','🍝','🍠','🍢','🍣','🍤','🍥','🥮','🍡','🥟','🥠','🥡','🦀','🦞','🦐','🦑','🦪','🍦','🍧','🍨','🍩','🍪','🎂','🍰','🧁','🥧','🍫','🍬','🍭','🍮','🍯','☕','🍵','🧃','🥤','🧋','🍶','🍺','🍻','🥂','🍷','🥃','🍸','🍹','🧉','🍾'] },
  { label: 'Travel', icon: '✈️', emojis: ['🚗','🚕','🚙','🚌','🚎','🏎','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🏍','🛵','🚲','🛴','🛺','🚨','🚥','🚦','🛑','🚧','⛽','🚤','⛵','🛥','🚢','✈️','🛩','🛫','🛬','🪂','💺','🚁','🚟','🚠','🚡','🛰','🚀','🛸','🏠','🏡','🏢','🏣','🏤','🏥','🏦','🏨','🏩','🏪','🏫','🏬','🏭','🏯','🏰','💒','🗼','🗽','⛪','🕌','🛕','⛩','🕍','⛲','🗺','🌁','🌃','🏙','🌄','🌅','🌆','🌇','🌉','🎠','🎡','🎢','💈','🎪','🌍','🌎','🌏','🗾','🧭'] },
  { label: 'Objects', icon: '💡', emojis: ['⌚','📱','💻','⌨️','🖥','🖨','🖱','🖲','💽','💾','💿','📀','📷','📸','📹','🎥','📞','☎️','📟','📠','📺','📻','🎙','🎚','🎛','🧭','⏱','⏲','⏰','🕰','⌛','⏳','📡','🔋','🔌','💡','🔦','🕯','🪔','🧯','🛢','💸','💵','💴','💶','💷','💰','💳','💎','⚙️','🔧','🔨','🪛','🔩','🪚','🗜','⚖️','🔗','⛓','🧲','🔫','💣','🪖','🛡','🪓','🔪','🗡','⚔️','🪃','🏹','🪤','🪣','🪝','🧰','🧲','🪜'] },
  { label: 'Symbols', icon: '❤️', emojis: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖','💘','💝','💟','☮️','✝️','☪️','🕉','☸️','✡️','🔯','🕎','☯️','☦️','🛐','⛎','♈','♉','♊','♋','♌','♍','♎','♏','♐','♑','♒','♓','🆔','⚛️','🉑','☢️','☣️','📴','📳','🈶','🈚','🈸','🈺','🈷️','✴️','🆚','💮','🉐','㊙️','㊗️','🈴','🈵','🈹','🈲','🅰️','🅱️','🆎','🆑','🅾️','🆘','❌','⭕','🛑','⛔','📛','🚫','💯','💢','♨️','🚷','🚯','🚳','🚱','🔞','📵','🚭','❗','❕','❓','❔','‼️','⁉️','🔅','🔆','📶','🛜','📳','📴'] },
];

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

// ─── Media helpers ───────────────────────────────────────────────────────────

/** Compress an image to 80 % JPEG quality at max 1920 px — same as Android's
 *  IMAGE_COMPRESSION_QUALITY=80. GIFs and non-images pass through unchanged. */
async function compressImageIfNeeded(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') return file;
  return new Promise((resolve) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const MAX = 1920;
      let w = img.width, h = img.height;
      if (w > MAX || h > MAX) {
        if (w >= h) { h = Math.round(h * MAX / w); w = MAX; }
        else        { w = Math.round(w * MAX / h); h = MAX; }
      }
      const canvas = document.createElement('canvas');
      canvas.width = w; canvas.height = h;
      canvas.getContext('2d')!.drawImage(img, 0, 0, w, h);
      canvas.toBlob((blob) => {
        if (!blob) { resolve(file); return; }
        const name = file.name.replace(/\.[^.]+$/, '.jpg');
        resolve(new File([blob], name, { type: 'image/jpeg' }));
      }, 'image/jpeg', 0.80);
    };
    img.onerror = () => { URL.revokeObjectURL(url); resolve(file); };
    img.src = url;
  });
}

/** Resolve a relative server path to a full URL — mirrors Android's
 *  EncryptedMediaHandler.getFullMediaUrl().
 *  Server stores paths like: upload/photos/YYYY/MM/file.jpg
 *                            upload/videos/file.mp4
 *                            upload/audios/VOICE_xxx.ogg
 *                            upload/files/file.pdf     */
export function absMediaUrl(u: string | undefined): string {
  if (!u) return '';
  if (u.startsWith('http')) return u;
  return `https://worldmates.club/${u.replace(/^\//, '')}`;
}

// ─── Waveform bars (matching Android AudioAlbumComponent) ─────────────────────
const VOICE_BAR_HEIGHTS = [0.4, 0.7, 0.55, 0.9, 0.6, 0.8, 0.45, 0.75, 0.5, 0.65,
  0.85, 0.55, 0.7, 0.4, 0.6, 0.8, 0.5, 0.9, 0.65, 0.45];

// ─── Module-level message cache ──────────────────────────────────────────────
// Keeps the last fetched message list per chat so switching tabs is instant.
const _msgCache = new Map<number, MessageItem[]>();

// ─── Local media cache hook ───────────────────────────────────────────────────
// Deduplicates concurrent IPC calls (many bubbles mounting at once) and queues
// cache-put downloads so we never flood the main process.

/** Already-resolved local paths: url → wm-cache:// URL. Avoids IPC on re-render. */
const _cacheHits   = new Map<string, string>();
/** In-flight cache-get IPC calls: url → Promise. Prevents N identical calls. */
const _cacheChecks = new Map<string, Promise<string | null>>();
const _putQueue: string[] = [];
let   _putRunning = 0;
const PUT_CONCURRENCY = 3;

function _drainPutQueue() {
  while (_putRunning < PUT_CONCURRENCY && _putQueue.length > 0) {
    const url = _putQueue.shift()!;
    _putRunning++;
    window.desktopApp?.cachePut?.(url)
      .then(local => { if (local) _cacheHits.set(url, local); })
      .catch(() => {})
      .finally(() => { _putRunning--; _drainPutQueue(); });
  }
}

function _schedulePut(url: string) {
  if (_cacheHits.has(url) || _putQueue.includes(url)) return;
  _putQueue.push(url);
  _drainPutQueue();
}

function useCachedUrl(url: string): string {
  // Synchronous hit from module-level map — no IPC, no re-render needed
  const [src, setSrc] = useState<string>(() => _cacheHits.get(url) ?? url);

  useEffect(() => {
    if (!url || !window.desktopApp?.cacheGet) return;
    // Already resolved in this session
    const hit = _cacheHits.get(url);
    if (hit) { setSrc(hit); return; }

    let alive = true;
    // Deduplicate: share the same Promise if multiple bubbles check the same URL
    let p = _cacheChecks.get(url);
    if (!p) {
      p = window.desktopApp.cacheGet(url);
      _cacheChecks.set(url, p);
      p.finally(() => _cacheChecks.delete(url));
    }

    p.then(cached => {
      if (!alive) return;
      if (cached) {
        _cacheHits.set(url, cached);
        setSrc(cached);
      } else {
        _schedulePut(url); // download quietly in background, queued 3-at-a-time
      }
    }).catch(() => {});

    return () => { alive = false; };
  }, [url]);

  return src;
}

// ─── VoicePlayer — custom player matching Android's AudioAlbumComponent ───────
function VoicePlayer({ src, filename }: { src: string; filename?: string }) {
  const cachedSrc = useCachedUrl(src);
  const audioRef  = useRef<HTMLAudioElement>(null);
  const [playing,  setPlaying]  = useState(false);
  const [progress, setProgress] = useState(0);   // 0..1
  const [duration, setDuration] = useState(0);
  const [current,  setCurrent]  = useState(0);

  useEffect(() => {
    const a = audioRef.current;
    if (!a) return;
    const onMeta   = () => setDuration(a.duration || 0);
    const onTime   = () => { if (a.duration) { setProgress(a.currentTime / a.duration); setCurrent(a.currentTime); } };
    const onEnded  = () => { setPlaying(false); setProgress(0); setCurrent(0); };
    a.addEventListener('loadedmetadata', onMeta);
    a.addEventListener('timeupdate',     onTime);
    a.addEventListener('ended',          onEnded);
    return () => {
      a.removeEventListener('loadedmetadata', onMeta);
      a.removeEventListener('timeupdate',     onTime);
      a.removeEventListener('ended',          onEnded);
    };
  }, [src]);

  function togglePlay() {
    const a = audioRef.current;
    if (!a) return;
    if (playing) { a.pause(); setPlaying(false); }
    else         { a.play().catch(() => {}); setPlaying(true); }
  }

  function seek(e: React.MouseEvent<HTMLDivElement>) {
    const a = audioRef.current;
    if (!a || !a.duration) return;
    const r = e.currentTarget.getBoundingClientRect();
    const x = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    a.currentTime = x * a.duration;
    setProgress(x);
  }

  const fmt = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
  const displayTime = current > 0 ? fmt(current) : duration > 0 ? fmt(duration) : '0:00';

  return (
    <div className="voice-player">
      <audio ref={audioRef} src={cachedSrc} preload="metadata" />
      <button className="voice-play-btn" onClick={togglePlay} title={playing ? 'Пауза' : 'Відтворити'}>
        {playing ? '⏸' : '▶'}
      </button>
      <div className="voice-waveform-wrap">
        <div className="voice-waveform" onClick={seek}>
          <div className="voice-waveform-progress" style={{ width: `${progress * 100}%` }} />
          {VOICE_BAR_HEIGHTS.map((h, i) => (
            <div
              key={i}
              className={`voice-bar${playing ? ' playing' : ''}`}
              style={{ '--bar-h': h, '--bar-i': i } as React.CSSProperties}
            />
          ))}
        </div>
        {filename && <span className="voice-filename">{filename}</span>}
      </div>
      <span className="voice-time">{displayTime}</span>
    </div>
  );
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

const Bubble = React.memo(function Bubble({
  msg, isOwn, onReply, onEdit, onDelete, onReact, onOpenMedia, onSave, isSaved, userId
}: {
  msg: MessageItem; isOwn: boolean; userId: number; isSaved: boolean;
  onReply: (m: MessageItem) => void;
  onEdit:  (m: MessageItem) => void;
  onDelete: (m: MessageItem) => void;
  onReact: (m: MessageItem, emoji: string) => void;
  onOpenMedia: (src: string) => void;
  onSave: (m: MessageItem) => void;
}) {
  const [showActions, setShowActions] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);

  const isEncrypted = msg.cipher_version === CIPHER_VERSION_SIGNAL;

  const mediaUrl = absMediaUrl(msg.media);

  const mediaIsImage = msg.media_type === 'image'
    || (!msg.media_type && msg.media && /\.(jpg|jpeg|png|gif|webp)$/i.test(msg.media));

  const isVoice = msg.media_type === 'voice' || msg.media_type === 'audio'
    || !!(msg.media_filename?.match(/^VOICE_/i))
    || !!(msg.media && /\/VOICE_[^/]*\.(webm|ogg|m4a|opus|aac)/i.test(msg.media))
    || !!(msg.media && /\.(ogg|mp3|m4a|aac|opus|flac|wav)$/i.test(msg.media));

  const isVideo = msg.media_type === 'video'
    || (!msg.media_type && msg.media && /\.(mp4|webm|mov|mkv|m4v|avi)$/i.test(msg.media));

  // Cache thumbnails and stickers locally for instant re-renders
  const cachedMediaUrl = useCachedUrl(mediaIsImage || msg.media_type === 'sticker' || msg.media_type === 'gif' ? mediaUrl : '');

  return (
    <div
      id={`msg-${msg.id}`}
      className={`bubble-row ${isOwn ? 'own' : ''}`}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => { setShowActions(false); setShowEmojiPicker(false); }}
    >
      {showActions && (
        <div className={`bubble-actions ${isOwn ? 'actions-left' : 'actions-right'}`}>
          <button className="action-btn" title={t('bubble.reply')} onClick={() => onReply(msg)}>↩</button>
          <button className="action-btn" title={t('bubble.react')} onClick={() => setShowEmojiPicker(v => !v)}>😀</button>
          <button className="action-btn" title={isSaved ? 'Убрать из сохранённых' : 'Сохранить'} onClick={() => onSave(msg)} style={{ color: isSaved ? 'var(--accent)' : undefined }}>{isSaved ? '🔖' : '🏷️'}</button>
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
            {msg.media_type === 'sticker'
              ? <img src={cachedMediaUrl || mediaUrl} alt="sticker" className="bubble-sticker" loading="lazy" />
              : msg.media_type === 'gif'
                ? <img src={cachedMediaUrl || mediaUrl} alt="gif" className="bubble-gif" loading="lazy" onClick={() => onOpenMedia(mediaUrl)} />
                : mediaIsImage
                  ? <img src={cachedMediaUrl || mediaUrl} alt="media" className="media-img" loading="lazy" onClick={() => onOpenMedia(mediaUrl)} />
                  : isVideo
                    ? <VideoPlayer src={mediaUrl} className="media-video-player" />
                    : isVoice
                      ? <VoicePlayer src={mediaUrl} filename={msg.media_filename} />
                      : <a href={mediaUrl} target="_blank" rel="noreferrer" className="media-file">
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
});

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
  const [drawerOpen, setDrawerOpen] = useState(false);

  // ── Real-time ─────────────────────────────────────────────────────────────
  const [socket, setSocket]         = useState<Socket | null>(null);
  const socketRef                   = useRef<Socket | null>(null);
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
  const [callMuted,  setCallMuted]  = useState(false);
  const [callCamOff, setCallCamOff] = useState(false);
  const [screenSharing, setScreenSharing] = useState(false);
  const screenStreamRef = useRef<MediaStream | null>(null);
  const peerRef         = useRef<RTCPeerConnection | null>(null);
  const localStreamRef  = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const localVideoRef   = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef  = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef  = useRef<HTMLAudioElement | null>(null);
  const callTimerRef    = useRef<ReturnType<typeof setInterval> | null>(null);
  const callStateRef    = useRef<CallState>({ phase: 'idle' });
  // Group call peers (mesh WebRTC — one PC per participant)
  const groupPeersRef   = useRef<Map<number, GroupCallPeer>>(new Map());
  const [groupPeers,  setGroupPeers]  = useState<GroupCallPeer[]>([]);

  // ── Media gallery ─────────────────────────────────────────────────────────
  const [showGallery,    setShowGallery]    = useState(false);
  const [galleryItems,   setGalleryItems]   = useState<MediaItem[]>([]);
  const [galleryLoading, setGalleryLoading] = useState(false);
  const [galleryTitle,   setGalleryTitle]   = useState('');

  // ── PIN lock ──────────────────────────────────────────────────────────────
  const [pinLocked,   setPinLocked]   = useState(false);
  const [pinInput,    setPinInput]    = useState('');
  const [pinError,    setPinError]    = useState('');
  const [pinEnabled,  setPinEnabled]  = useState(false);
  const [showSetPin,  setShowSetPin]  = useState(false);
  const [newPin1,     setNewPin1]     = useState('');
  const [newPin2,     setNewPin2]     = useState('');

  // ── Sidebar search ────────────────────────────────────────────────────────
  const [searchQuery, setSearchQuery]   = useState('');
  const [groupSearch, setGroupSearch]   = useState('');
  const [channelSearch, setChannelSearch] = useState('');
  const [groupSearchResults, setGroupSearchResults]     = useState<GroupItem[] | null>(null);
  const [channelSearchResults, setChannelSearchResults] = useState<ChannelItem[] | null>(null);
  const [userSearchQuery,   setUserSearchQuery]   = useState('');
  const [userSearchResults, setUserSearchResults] = useState<UserSearchResult[] | null>(null);
  const groupSearchTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);
  const channelSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const userSearchTimer    = useRef<ReturnType<typeof setTimeout> | null>(null);

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
  // Extended profile fields
  const [profileBirthday, setProfileBirthday] = useState('');
  const [profileCity,     setProfileCity]     = useState('');
  const [profileWebsite,  setProfileWebsite]  = useState('');
  const [profileGender,   setProfileGender]   = useState('');
  const [profileWorking,  setProfileWorking]  = useState('');
  const [profileSchool,   setProfileSchool]   = useState('');
  const [profilePhone,    setProfilePhone]    = useState('');
  // Social links
  const [profileFb,   setProfileFb]   = useState('');
  const [profileTw,   setProfileTw]   = useState('');
  const [profileIg,   setProfileIg]   = useState('');
  const [profileLi,   setProfileLi]   = useState('');
  const [profileYt,   setProfileYt]   = useState('');

  // ── Custom status ─────────────────────────────────────────────────────────
  const [statusEmoji,       setStatusEmoji]       = useState('');
  const [statusText,        setStatusText]        = useState('');
  const [showStatusModal,   setShowStatusModal]   = useState(false);
  const [statusSaving,      setStatusSaving]      = useState<'idle'|'saving'|'done'|'error'>('idle');

  // ── Notification settings ─────────────────────────────────────────────────
  const [notifSettings,  setNotifSettings]  = useState<NotificationSettings | null>(null);
  const [notifLoaded,    setNotifLoaded]    = useState(false);
  const [notifSaving,    setNotifSaving]    = useState<'idle'|'saving'|'done'|'error'>('idle');

  // ── Archived chats ────────────────────────────────────────────────────────
  const [archivedChats,  setArchivedChats]  = useState<ChatItem[]>([]);
  const [showArchived,   setShowArchived]   = useState(false);
  const [archivedLoaded, setArchivedLoaded] = useState(false);

  // ── Blocked users ─────────────────────────────────────────────────────────
  const [blockedUsers,   setBlockedUsers]   = useState<UserProfile[]>([]);
  const [blockedLoaded,  setBlockedLoaded]  = useState(false);

  // ── Global search panel ────────────────────────────────────────────────────
  const [showGlobalSearch, setShowGlobalSearch]   = useState(false);
  const [globalSearchQ,    setGlobalSearchQ]      = useState('');
  const [globalSearchTab,  setGlobalSearchTab]    = useState<'people' | 'groups' | 'channels'>('people');
  const [globalSearchRes,  setGlobalSearchRes]    = useState<{
    people: UserSearchResult[]; groups: GroupItem[]; channels: ChannelItem[];
  }>({ people: [], groups: [], channels: [] });
  const [globalSearchLoading, setGlobalSearchLoading] = useState(false);
  const globalSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Saved messages ─────────────────────────────────────────────────────────
  const [showSaved,    setShowSaved]    = useState(false);
  const [savedItems,   setSavedItems]  = useState<SavedMessageItem[]>([]);
  const [savedLoading, setSavedLoading] = useState(false);
  const [savedSet,     setSavedSet]    = useState<Set<number>>(new Set());

  // ── Notes ──────────────────────────────────────────────────────────────────
  const [showNotes,     setShowNotes]     = useState(false);
  const [notes,         setNotes]         = useState<NoteItem[]>([]);
  const [notesLoading,  setNotesLoading]  = useState(false);
  const [noteInput,     setNoteInput]     = useState('');
  const [noteSending,   setNoteSending]   = useState(false);
  const [notesStorage,  setNotesStorage]  = useState<NotesStorageInfo | null>(null);

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

  // ── Emoji / Sticker / GIF picker ─────────────────────────────────────────
  const [showEmojiComposer, setShowEmojiComposer] = useState(false);
  const [emojiCatIdx,      setEmojiCatIdx]      = useState(0);
  const [showPicker,       setShowPicker]       = useState<'sticker'|'gif'|null>(null);
  const [stickerPacks,     setStickerPacks]     = useState<StickerPack[]>([]);
  const [stickerPacksLoaded, setStickerPacksLoaded] = useState(false);
  const [activeStickerPack, setActiveStickerPack] = useState<number | null>(null);
  const [gifResults,       setGifResults]       = useState<GifItem[]>([]);
  const [gifQuery,         setGifQuery]         = useState('');
  const gifDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Bot search ────────────────────────────────────────────────────────────
  const [showBotSearch,   setShowBotSearch]   = useState(false);
  const [botQuery,        setBotQuery]        = useState('');
  const [botResults,      setBotResults]      = useState<BotItem[]>([]);
  const botDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  // ── Settings nav tab ──────────────────────────────────────────────────────
  const [settingsTab,  setSettingsTab]  = useState<'profile'|'privacy'|'blocked'|'language'|'security'|'notifications'|'social'>('profile');
  const [settingsOpen, setSettingsOpen] = useState(false);

  // ── Story interactions ────────────────────────────────────────────────────
  const [storyComments,        setStoryComments]        = useState<StoryComment[]>([]);
  const [storyCommentsLoading, setStoryCommentsLoading] = useState(false);
  const [storyCommentInput,    setStoryCommentInput]    = useState('');
  const [showStoryComments,    setShowStoryComments]    = useState(false);
  const [storyPaused,          setStoryPaused]          = useState(false);
  const storyPausedRef = useRef(false);   // ref keeps timer in sync without re-creating interval

  // ── Drafts ────────────────────────────────────────────────────────────────
  const [chatDrafts, setChatDrafts] = useState<Record<number, string>>({});

  // ── Pinned message banner ─────────────────────────────────────────────────
  const [pinnedMessage, setPinnedMessage] = useState<MessageItem | null>(null);

  // ── Scheduled messages ────────────────────────────────────────────────────
  const [scheduledMessages,   setScheduledMessages]   = useState<ScheduledMessage[]>([]);
  const [showScheduledList,   setShowScheduledList]   = useState(false);
  const [showSchedulePicker,  setShowSchedulePicker]  = useState(false);
  const [scheduleDateTime,    setScheduleDateTime]    = useState('');

  // ── Channel admin panel ───────────────────────────────────────────────────
  const [showChannelAdmin, setShowChannelAdmin] = useState(false);
  const [showChannelInfo,  setShowChannelInfo]  = useState(false);

  // ── Group admin panel ─────────────────────────────────────────────────────
  const [showGroupAdmin, setShowGroupAdmin] = useState(false);
  const [showGroupInfo,  setShowGroupInfo]  = useState(false);

  // ── Create channel modal (enhanced) ──────────────────────────────────────
  const [createChannelUsername, setCreateChannelUsername] = useState('');
  const [createChannelIsPrivate, setCreateChannelIsPrivate] = useState(false);

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
      if (s.token) {
        setSession(s);
        // Restore PIN state
        const pinOn = localStorage.getItem('wm_pin_enabled') === '1';
        setPinEnabled(pinOn);
        if (pinOn && localStorage.getItem('wm_pin_hash')) setPinLocked(true);
      }
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
        // Join the numeric userId room so the server can route call events to us
        s.emit('call:register', { userId: session.userId, user_id: session.userId });

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

      // ── New call protocol ───────────────────────────────────────────────────
      onCallIncoming: (data) => {
        const caller: ChatItem = chatsRef.current.find(c => c.user_id === data.fromId)
          ?? { user_id: data.fromId, name: data.fromName, avatar: data.fromAvatar };
        setCallState({
          phase: 'incoming', peer: caller, type: data.callType,
          roomName: data.roomName, fromId: data.fromId,
          iceServers: data.iceServers, sdpOffer: data.sdpOffer,
        });
        playNotificationBeep();
      },

      onCallAnswer: async (data) => {
        const cs = callStateRef.current;
        if (cs.phase !== 'outgoing' || !peerRef.current) return;
        try {
          await peerRef.current.setRemoteDescription(parseSdp(data.sdpAnswer, 'answer'));
          setCallState({ phase: 'connected', peer: cs.peer, type: cs.type, roomName: cs.roomName, duration: 0 });
          startCallTimer();
        } catch (e) { console.error('[Call] setAnswer failed:', e); }
      },

      onCallEnded: () => {
        stopCallEverything();
        setCallState({ phase: 'idle' });
      },

      onCallRejected: () => {
        stopCallEverything();
        setCallState({ phase: 'idle' });
      },

      onCallError: (data) => {
        console.warn('[Call] Server error:', data.message, data.status);
        stopCallEverything();
        setCallState({ phase: 'idle' });
      },

      onIceCandidate: async (data) => {
        const pc = peerRef.current ?? groupPeersRef.current.get(data.fromUserId)?.pc;
        if (!pc) return;
        try {
          // Android/server sends candidate as a plain SDP string with sdpMLineIndex/sdpMid
          // as separate top-level fields.  RTCIceCandidate expects an init object.
          const init: RTCIceCandidateInit = typeof data.candidate === 'string'
            ? { candidate: data.candidate, sdpMLineIndex: data.sdpMLineIndex ?? 0, sdpMid: data.sdpMid ?? null }
            : data.candidate;
          if (init.candidate) await pc.addIceCandidate(new RTCIceCandidate(init));
        } catch { /* ignore stale candidates */ }
      },

      onGroupCallIncoming: (data) => {
        setCallState({
          phase: 'group_incoming',
          groupId: data.groupId, groupName: data.groupName,
          type: data.callType, roomName: data.roomName,
          fromName: data.fromName, iceServers: data.iceServers,
        });
        playNotificationBeep();
      },

      onGroupCallOffer: async (data) => {
        const cs = callStateRef.current;
        if (cs.phase !== 'group_connected') return;
        const { roomName } = cs;
        const servers = data.iceServers?.length ? data.iceServers : TURN_FALLBACK;
        const pc = await createPeerConnection(servers);
        const existing = groupPeersRef.current.get(data.fromUserId);
        if (existing) existing.pc.close();
        const peer: GroupCallPeer = { userId: data.fromUserId, name: `User ${data.fromUserId}`, pc };
        localStreamRef.current?.getTracks().forEach(t => pc.addTrack(t, localStreamRef.current!));
        pc.ontrack = (ev) => {
          const gp = groupPeersRef.current.get(data.fromUserId);
          if (gp) { gp.stream = ev.streams[0]; setGroupPeers([...groupPeersRef.current.values()]); }
        };
        pc.onicecandidate = ({ candidate }) => {
          if (candidate) s.emit('group_call:ice_candidate', {
            roomName, toUserId: data.fromUserId, fromUserId: session!.userId,
            candidate: candidate.candidate ?? '', sdpMLineIndex: candidate.sdpMLineIndex ?? 0, sdpMid: candidate.sdpMid ?? null,
          });
        };
        await pc.setRemoteDescription(parseSdp(data.sdpOffer, 'offer'));
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        s.emit('group_call:answer', { roomName, toUserId: data.fromUserId, sdpAnswer: answer.sdp ?? '' });
        groupPeersRef.current.set(data.fromUserId, peer);
        setGroupPeers([...groupPeersRef.current.values()]);
      },

      onGroupCallAnswer: async (data) => {
        const peer = groupPeersRef.current.get(data.fromUserId);
        if (peer) await peer.pc.setRemoteDescription(parseSdp(data.sdpAnswer, 'answer')).catch(console.error);
      },

      onGroupCallIceCandidate: async (data) => {
        const peer = groupPeersRef.current.get(data.fromUserId);
        if (!peer) return;
        const init: RTCIceCandidateInit = typeof data.candidate === 'string'
          ? { candidate: data.candidate, sdpMLineIndex: data.sdpMLineIndex ?? 0, sdpMid: data.sdpMid ?? null }
          : data.candidate;
        if (init.candidate) await peer.pc.addIceCandidate(new RTCIceCandidate(init)).catch(console.error);
      },

      onGroupCallParticipantLeft: (data) => {
        const peer = groupPeersRef.current.get(data.userId);
        if (peer) { peer.pc.close(); groupPeersRef.current.delete(data.userId); }
        setGroupPeers([...groupPeersRef.current.values()]);
      },

      onGroupCallEnded: () => {
        groupPeersRef.current.forEach(p => p.pc.close());
        groupPeersRef.current.clear();
        setGroupPeers([]);
        stopCallEverything();
        setCallState({ phase: 'idle' });
      },

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

    socketRef.current = s;
    setSocket(s);
    return () => { s.disconnect(); socketRef.current = null; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  // Keep refs so socket callbacks always read the latest values without stale closures
  const selectedChatRef = useRef<ChatItem | null>(null);
  const chatsRef        = useRef<ChatItem[]>([]);
  useEffect(() => { selectedChatRef.current = selectedChat; }, [selectedChat]);
  useEffect(() => { chatsRef.current = chats; }, [chats]);
  useEffect(() => { selectedGroupRef.current = selectedGroup; }, [selectedGroup]);

  // Keep pinnedMessage in sync with messages list
  useEffect(() => {
    const pinned = messages.find(m => m.is_pinned && !m.is_deleted) ?? null;
    setPinnedMessage(pinned);
  }, [messages]);
  useEffect(() => { selectedChannelRef.current = selectedChannel; }, [selectedChannel]);
  useEffect(() => { callStateRef.current = callState; }, [callState]);

  // When the call transitions to 'connected' the video/audio elements mount.
  // When phase transitions to 'connected' the video elements mount — attach stored streams.
  useEffect(() => {
    if (callState.phase === 'connected') {
      if (remoteStreamRef.current) {
        if (remoteVideoRef.current) remoteVideoRef.current.srcObject = remoteStreamRef.current;
        if (remoteAudioRef.current) remoteAudioRef.current.srcObject = remoteStreamRef.current;
      }
      // Local video element also mounts at this point — re-attach local stream
      if (localStreamRef.current && localVideoRef.current) {
        localVideoRef.current.srcObject = localStreamRef.current;
      }
    }
    if (callState.phase === 'idle') {
      remoteStreamRef.current = null;
    }
  }, [callState.phase]);

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
      setShowStoryComments(false);
      setStoryPaused(false); storyPausedRef.current = false;
      setStoryComments([]);
      return;
    }
    setStoryProgress(0);
    setShowStoryComments(false);
    setStoryPaused(false); storyPausedRef.current = false;
    if (storyTimerRef.current) clearInterval(storyTimerRef.current);
    const duration = 7000;
    const step = 100;
    storyTimerRef.current = setInterval(() => {
      if (storyPausedRef.current) return;
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

  function closeStory() {
    setViewingStoryIdx(null);
    setShowStoryComments(false);
    setStoryPaused(false); storyPausedRef.current = false;
    setStoryComments([]);
    setStoryCommentInput('');
  }

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

    // Show cached messages immediately — no spinner, no layout jump
    const cached = _msgCache.get(selectedChat.user_id);
    if (cached && cached.length > 0) {
      setMessages(cached);
      setMessagesLoading(false);
    } else {
      setMessages([]);
      setMessagesLoading(true);
    }

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
          _msgCache.set(selectedChat.user_id, decrypted); // update in-memory cache
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

  // Keep the module-level message cache in sync so socket messages are cached too
  useEffect(() => {
    if (selectedChat && messages.length > 0) {
      _msgCache.set(selectedChat.user_id, messages);
    }
  }, [messages, selectedChat?.user_id]);

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
    // Clear draft on send
    localStorage.removeItem(draftKey(selectedChat.user_id));
    setChatDrafts(prev => { const n = { ...prev }; delete n[selectedChat.user_id]; return n; });

    try {
      if (pendingMedia) {
        const mediaToSend = await compressImageIfNeeded(pendingMedia);
        await sendMessageWithMedia(session.token, selectedChat.user_id, text, mediaToSend, session.userId);
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
    if (!settingsOpen || myProfile || !session) return;
    getMyProfile(session.token).then(p => {
      setMyProfile(p);
      setProfileFirst(p.first_name ?? '');
      setProfileLast(p.last_name   ?? '');
      setProfileAbout(p.about      ?? '');
      setProfileUser(p.username    ?? '');
      setProfileBirthday(p.birthday ?? '');
      setProfileCity(p.city        ?? '');
      setProfileWebsite(p.website  ?? '');
      setProfileGender(p.gender    ?? '');
      setProfileWorking(p.working  ?? '');
      setProfileSchool(p.school    ?? '');
      setProfilePhone(p.phone      ?? '');
      setProfileFb(p.facebook      ?? '');
      setProfileTw(p.twitter       ?? '');
      setProfileIg(p.instagram     ?? '');
      setProfileLi(p.linkedin      ?? '');
      setProfileYt(p.youtube       ?? '');
      setStatusEmoji(p.status_emoji ?? '');
      setStatusText(p.status_text   ?? '');
    }).catch(() => {});
  }, [settingsOpen, myProfile, session]);

  // Auto-load call history when entering Calls section
  useEffect(() => {
    if (section === 'calls' && !callHistoryLoaded && session) {
      handleLoadCallHistory(callHistoryFilter);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [section]);

  // Auto-load privacy / blocked when their settings tab opens
  useEffect(() => {
    if (!session) return;
    if (settingsTab === 'privacy'       && !privacyLoaded) handleLoadPrivacy();
    if (settingsTab === 'blocked'       && !blockedLoaded) handleLoadBlocked();
    if (settingsTab === 'notifications' && !notifLoaded)  handleLoadNotifSettings();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settingsTab]);

  async function handleSaveProfile() {
    if (!session) return;
    setProfileSaving('saving');
    try {
      await updateMyProfile(session.token, {
        first_name:   profileFirst,
        last_name:    profileLast,
        about:        profileAbout,
        username:     profileUser,
        birthday:     profileBirthday,
        city:         profileCity,
        website:      profileWebsite,
        gender:       profileGender,
        working:      profileWorking,
        school:       profileSchool,
        phone_number: profilePhone,
      });
      setMyProfile(prev => prev ? {
        ...prev,
        first_name: profileFirst, last_name: profileLast,
        about: profileAbout, username: profileUser,
        birthday: profileBirthday, city: profileCity,
        website: profileWebsite, gender: profileGender,
        working: profileWorking, school: profileSchool, phone: profilePhone,
      } : prev);
      setProfileSaving('done');
      setTimeout(() => setProfileSaving('idle'), 2500);
    } catch {
      setProfileSaving('error');
      setTimeout(() => setProfileSaving('idle'), 2500);
    }
  }

  async function handleSaveSocialLinks() {
    if (!session) return;
    setProfileSaving('saving');
    try {
      await updateMyProfile(session.token, {
        facebook: profileFb, twitter: profileTw,
        instagram: profileIg, linkedin: profileLi, youtube: profileYt,
      });
      setProfileSaving('done');
      setTimeout(() => setProfileSaving('idle'), 2500);
    } catch {
      setProfileSaving('error');
      setTimeout(() => setProfileSaving('idle'), 2500);
    }
  }

  async function handleSaveCustomStatus() {
    if (!session) return;
    setStatusSaving('saving');
    try {
      await setCustomStatus(session.token, statusEmoji, statusText);
      setMyProfile(prev => prev ? { ...prev, status_emoji: statusEmoji, status_text: statusText } : prev);
      setStatusSaving('done');
      setTimeout(() => { setStatusSaving('idle'); setShowStatusModal(false); }, 1500);
    } catch {
      setStatusSaving('error');
      setTimeout(() => setStatusSaving('idle'), 2500);
    }
  }

  async function handleLoadNotifSettings() {
    if (!session || notifLoaded) return;
    const s = await loadNotificationSettings(session.token);
    setNotifSettings(s);
    setNotifLoaded(true);
  }

  async function handleSaveNotifSettings() {
    if (!session || !notifSettings) return;
    setNotifSaving('saving');
    try {
      await updateNotificationSettings(session.token, notifSettings);
      setNotifSaving('done');
      setTimeout(() => setNotifSaving('idle'), 2500);
    } catch {
      setNotifSaving('error');
      setTimeout(() => setNotifSaving('idle'), 2500);
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

  function handleInsertEmoji(emoji: string) {
    const ta = composerRef.current;
    if (ta) {
      const start = ta.selectionStart ?? newMessage.length;
      const end   = ta.selectionEnd   ?? newMessage.length;
      const next  = newMessage.slice(0, start) + emoji + newMessage.slice(end);
      handleComposerInput(next);
      // Restore cursor after emoji
      requestAnimationFrame(() => {
        ta.selectionStart = ta.selectionEnd = start + emoji.length;
        ta.focus();
      });
    } else {
      handleComposerInput(newMessage + emoji);
    }
  }

  // ─── Global search ────────────────────────────────────────────────────────

  useEffect(() => {
    if (!session || !globalSearchQ.trim()) {
      setGlobalSearchRes({ people: [], groups: [], channels: [] });
      return;
    }
    setGlobalSearchLoading(true);
    if (globalSearchTimer.current) clearTimeout(globalSearchTimer.current);
    globalSearchTimer.current = setTimeout(async () => {
      const q = globalSearchQ.trim();
      try {
        const [people, groups, channels] = await Promise.all([
          searchUsers(session.token, q),
          searchGroups(session.token, q).then(r => r.data ?? []),
          searchChannels(session.token, q).then(r => r.data ?? []),
        ]);
        setGlobalSearchRes({ people, groups, channels });
      } catch { /* keep previous */ }
      setGlobalSearchLoading(false);
    }, 350);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [globalSearchQ]);

  // ─── Saved messages ───────────────────────────────────────────────────────

  async function handleOpenSaved() {
    if (!session) return;
    setShowNotes(false);
    setShowSaved(true);
    setSavedLoading(true);
    try {
      const items = await listSaved(session.token);
      setSavedItems(items);
      setSavedSet(new Set(items.map(i => i.message_id)));
    } catch { /* keep previous */ }
    setSavedLoading(false);
  }

  async function handleOpenNotes() {
    if (!session) return;
    setShowSaved(false);
    setShowNotes(true);
    setNotesLoading(true);
    try {
      const [items, storage] = await Promise.all([
        listNotes(session.token),
        getNotesStorage(session.token),
      ]);
      setNotes(items);
      setNotesStorage(storage);
    } catch { /* keep previous */ }
    setNotesLoading(false);
  }

  async function handleCreateNote() {
    if (!session || !noteInput.trim() || noteSending) return;
    setNoteSending(true);
    const text = noteInput.trim();
    setNoteInput('');
    const created = await createNote(session.token, text);
    if (created) setNotes(prev => [created, ...prev]);
    setNoteSending(false);
  }

  async function handleDeleteNote(id: number) {
    if (!session) return;
    setNotes(prev => prev.filter(n => n.id !== id));
    await deleteNote(session.token, id);
  }

  async function handleSaveMessage(msg: MessageItem, chatName: string, senderName: string) {
    if (!session) return;
    const already = savedSet.has(msg.id);
    if (already) {
      setSavedSet(prev => { const s = new Set(prev); s.delete(msg.id); return s; });
      setSavedItems(prev => prev.filter(i => i.message_id !== msg.id));
      await unsaveMessage(session.token, msg.id, 'chat');
    } else {
      setSavedSet(prev => new Set(prev).add(msg.id));
      const item: Omit<SavedMessageItem, 'id' | 'saved_at'> = {
        message_id: msg.id, chat_type: 'chat',
        chat_id: selectedChat?.user_id ?? 0, chat_name: chatName,
        sender_name: senderName, text: msg.text ?? '',
        media_url: msg.media, media_type: msg.media_type,
        original_time: msg.time ?? 0,
      };
      setSavedItems(prev => [{ ...item, saved_at: Date.now() / 1000 }, ...prev]);
      await apiSaveMessage(session.token, item);
    }
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

  // ─── Scheduled messages ───────────────────────────────────────────────────

  async function handleOpenScheduledList() {
    if (!session || !selectedChat) return;
    const list = await listScheduledMessages(session.token, selectedChat.user_id);
    setScheduledMessages(list);
    setShowScheduledList(true);
  }

  async function handleScheduleMessage() {
    if (!session || !selectedChat || !scheduleDateTime || !newMessage.trim()) return;
    const sendAt = Math.floor(new Date(scheduleDateTime).getTime() / 1000);
    if (isNaN(sendAt) || sendAt <= Date.now() / 1000) return;
    const msg = await createScheduledMessage(session.token, selectedChat.user_id, newMessage.trim(), sendAt);
    if (msg) {
      setScheduledMessages(prev => [...prev, msg]);
      setNewMessage('');
      localStorage.removeItem(draftKey(selectedChat.user_id));
      setChatDrafts(prev => { const n = { ...prev }; delete n[selectedChat.user_id]; return n; });
    }
    setShowSchedulePicker(false);
    setScheduleDateTime('');
  }

  async function handleDeleteScheduled(id: number) {
    if (!session) return;
    await deleteScheduledMessage(session.token, id);
    setScheduledMessages(prev => prev.filter(m => m.id !== id));
  }

  async function handleSendScheduledNow(id: number) {
    if (!session) return;
    await sendScheduledNow(session.token, id);
    setScheduledMessages(prev => prev.filter(m => m.id !== id));
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

  // ─── Sticker / GIF picker ─────────────────────────────────────────────────

  async function openStickerPicker() {
    setShowPicker('sticker');
    if (!stickerPacksLoaded && session) {
      const packs = await loadStickerPacks(session.token);
      setStickerPacks(packs);
      setStickerPacksLoaded(true);
      if (packs.length > 0) setActiveStickerPack(packs[0].id);
    }
  }

  async function handleSendSticker(url: string) {
    if (!session || !selectedChat) return;
    setShowPicker(null);
    await sendStickerMessage(session.token, selectedChat.user_id, url).catch(console.error);
    const r = await loadMessages(session.token, selectedChat.user_id);
    setMessages(await Promise.all((r.messages ?? []).map(tryDecryptMessage)));
  }

  async function openGifPicker() {
    setShowPicker('gif');
    if (gifResults.length === 0) {
      const gifs = await loadTrendingGifs();
      setGifResults(gifs);
    }
  }

  async function handleGifSearch(q: string) {
    setGifQuery(q);
    if (gifDebounceRef.current) clearTimeout(gifDebounceRef.current);
    gifDebounceRef.current = setTimeout(async () => {
      const gifs = await searchGifs(q);
      setGifResults(gifs);
    }, 400);
  }

  async function handleSendGif(url: string) {
    if (!session || !selectedChat) return;
    setShowPicker(null);
    await sendGifMessage(session.token, selectedChat.user_id, url).catch(console.error);
    const r = await loadMessages(session.token, selectedChat.user_id);
    setMessages(await Promise.all((r.messages ?? []).map(tryDecryptMessage)));
  }

  // ─── Bot search ───────────────────────────────────────────────────────────

  function handleBotQueryChange(q: string) {
    setBotQuery(q);
    if (botDebounceRef.current) clearTimeout(botDebounceRef.current);
    botDebounceRef.current = setTimeout(async () => {
      if (q.trim().length < 2) { setBotResults([]); return; }
      const bots = await searchBots(q);
      setBotResults(bots);
    }, 400);
  }

  async function openBotChat(bot: BotItem) {
    if (!session) return;
    let uid = bot.user_id;
    if (!uid) uid = await getBotLinkedUser(session.token, bot.bot_id_str);
    if (!uid) return;
    const chatItem: ChatItem = {
      user_id: uid,
      name:    bot.display_name || bot.username,
      avatar:  bot.avatar,
    };
    selectChat(chatItem);
    setShowBotSearch(false);
    setSection('chats');
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
    await createChannelFull(
      session.token,
      newChannelName.trim(),
      newChannelDesc.trim(),
      createChannelUsername.trim() || undefined,
      createChannelIsPrivate,
    ).catch(console.error);
    const r = await loadChannels(session.token);
    setChannels(r.data ?? []);
    setNewChannelName('');
    setNewChannelDesc('');
    setCreateChannelUsername('');
    setCreateChannelIsPrivate(false);
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

  async function handleDeleteStory(storyId: number) {
    if (!session) return;
    setStories(prev => prev.filter(s => s.id !== storyId));
    closeStory();
    await deleteStory(session.token, storyId);
  }

  async function handleReactToStory(reaction: string) {
    if (!session || viewingStoryIdx === null) return;
    const s = stories[viewingStoryIdx];
    if (!s) return;
    await apiReactToStory(session.token, s.id, reaction);
    setStories(prev => prev.map((st, i) => {
      if (i !== viewingStoryIdx) return st;
      const r = st.reaction ?? { like:0, love:0, haha:0, wow:0, sad:0, angry:0, is_reacted:false };
      const alreadyThisReaction = r.is_reacted && r.reacted_type === reaction;
      const newCount = (r[reaction as keyof typeof r] as number) + (alreadyThisReaction ? -1 : 1);
      return {
        ...st,
        reaction: {
          ...r,
          [reaction]: Math.max(0, newCount),
          is_reacted: !alreadyThisReaction,
          reacted_type: alreadyThisReaction ? undefined : reaction,
        },
      };
    }));
  }

  async function handleOpenStoryComments() {
    if (!session || viewingStoryIdx === null) return;
    const s = stories[viewingStoryIdx];
    if (!s) return;
    setShowStoryComments(true);
    setStoryPaused(true); storyPausedRef.current = true;
    setStoryCommentsLoading(true);
    const comments = await getStoryComments(session.token, s.id);
    setStoryComments(comments);
    setStoryCommentsLoading(false);
  }

  async function handleSendStoryComment() {
    if (!session || viewingStoryIdx === null || !storyCommentInput.trim()) return;
    const s = stories[viewingStoryIdx];
    if (!s) return;
    const text = storyCommentInput.trim();
    setStoryCommentInput('');
    const created = await createStoryComment(session.token, s.id, text);
    if (created) setStoryComments(prev => [...prev, created]);
    setStories(prev => prev.map((st, i) =>
      i === viewingStoryIdx ? { ...st, comment_count: (st.comment_count ?? 0) + 1 } : st
    ));
  }

  async function handleDeleteStoryComment(commentId: number) {
    if (!session) return;
    setStoryComments(prev => prev.filter(c => c.id !== commentId));
    await deleteStoryComment(session.token, commentId);
  }

  // ─── WebRTC call helpers ───────────────────────────────────────────────────

  function startCallTimer() {
    if (callTimerRef.current) clearInterval(callTimerRef.current);
    callTimerRef.current = setInterval(() => {
      setCallState(prev => {
        if (prev.phase === 'connected')       return { ...prev, duration: prev.duration + 1 };
        if (prev.phase === 'group_connected') return { ...prev, duration: prev.duration + 1 };
        return prev;
      });
    }, 1000);
  }

  function stopCallTimer() {
    if (callTimerRef.current) { clearInterval(callTimerRef.current); callTimerRef.current = null; }
  }

  function stopLocalStream() {
    localStreamRef.current?.getTracks().forEach(t => t.stop());
    localStreamRef.current  = null;
    remoteStreamRef.current = null;
    if (localVideoRef.current)  localVideoRef.current.srcObject  = null;
    if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;
    if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null;
  }

  function stopCallEverything() {
    peerRef.current?.close();
    peerRef.current = null;
    stopLocalStream();
    stopCallTimer();
    setCallMuted(false);
    setCallCamOff(false);
  }

  function formatCallDuration(secs: number): string {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  // ─── 1-on-1 calls ─────────────────────────────────────────────────────────

  function parseSdp(raw: string, type: 'offer' | 'answer'): RTCSessionDescriptionInit {
    if (raw.startsWith('{')) {
      try { return JSON.parse(raw) as RTCSessionDescriptionInit; } catch { /* fall through */ }
    }
    return { type, sdp: raw };
  }

  async function startCall(type: 'audio' | 'video') {
    if (!session || !selectedChat) return;
    const roomName = `call_${session.userId}_${selectedChat.user_id}_${Date.now()}`;
    const servers  = await createTurnIceServers(session.userId);
    const pc       = await createPeerConnection(servers);
    peerRef.current = pc;

    try {
      const stream = type === 'video' ? await createLocalVideoStream() : await createLocalAudioStream();
      localStreamRef.current = stream;
      stream.getTracks().forEach(t => pc.addTrack(t, stream));
      if (localVideoRef.current && type === 'video') localVideoRef.current.srcObject = stream;
    } catch { /* no mic/cam */ }

    pc.ontrack = (ev) => {
      const stream = ev.streams[0];
      if (!stream) return;
      remoteStreamRef.current = stream;
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = stream;
      if (remoteAudioRef.current) remoteAudioRef.current.srcObject = stream;
    };
    pc.onicecandidate = ({ candidate }) => {
      if (candidate)
        emitIceCandidate(socketRef.current, { roomName, toUserId: selectedChat.user_id, fromUserId: session.userId, candidate: candidate.toJSON() });
    };

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    emitCallInitiate(socketRef.current, { fromId: session.userId, toId: selectedChat.user_id, callType: type, roomName, sdpOffer: offer.sdp ?? '' });
    socketRef.current?.emit('call:join_room', { roomName, userId: session.userId });
    setCallState({ phase: 'outgoing', peer: selectedChat, type, roomName });
  }

  async function acceptCall() {
    const cs = callState;
    if (cs.phase !== 'incoming') return;
    const { peer, type, roomName, iceServers, sdpOffer } = cs;
    const fallback = iceServers.length ? iceServers : await createTurnIceServers(session!.userId);
    const pc = await createPeerConnection(fallback);

    peerRef.current = pc;

    try {
      const stream = type === 'video' ? await createLocalVideoStream() : await createLocalAudioStream();
      localStreamRef.current = stream;
      stream.getTracks().forEach(t => pc.addTrack(t, stream));
      if (localVideoRef.current && type === 'video') localVideoRef.current.srcObject = stream;
    } catch { /* no mic/cam */ }

    pc.ontrack = (ev) => {
      const stream = ev.streams[0];
      if (!stream) return;
      remoteStreamRef.current = stream;
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = stream;
      if (remoteAudioRef.current) remoteAudioRef.current.srcObject = stream;
    };
    pc.onicecandidate = ({ candidate }) => {
      if (candidate)
        emitIceCandidate(socketRef.current, { roomName, toUserId: peer.user_id, fromUserId: session!.userId, candidate: candidate.toJSON() });
    };

    await pc.setRemoteDescription(parseSdp(sdpOffer, 'offer'));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    emitCallAccept(socketRef.current, { roomName, userId: session!.userId, sdpAnswer: answer.sdp ?? '' });
    setCallState({ phase: 'connected', peer, type, roomName, duration: 0 });
    startCallTimer();
  }

  function rejectIncomingCall() {
    const cs = callState;
    if (cs.phase !== 'incoming') return;
    emitCallReject(socket, cs.roomName);
    stopCallEverything();
    setCallState({ phase: 'idle' });
  }

  async function endActiveCall() {
    const cs = callStateRef.current;
    if (cs.phase === 'idle') return;
    if (cs.phase === 'group_connected') {
      emitGroupCallEnd(socket, cs.roomName);
      groupPeersRef.current.forEach(p => p.pc.close());
      groupPeersRef.current.clear();
      setGroupPeers([]);
    } else if (cs.phase === 'outgoing' || cs.phase === 'connected') {
      emitCallEnd(socket, cs.roomName);
      await endCall(session!.token, cs.peer.user_id).catch(console.error);
    } else if (cs.phase === 'incoming') {
      emitCallReject(socket, cs.roomName);
    }
    stopCallEverything();
    setCallState({ phase: 'idle' });
  }

  function handleMute() {
    const stream = localStreamRef.current;
    if (!stream) return;
    const next = !callMuted;
    stream.getAudioTracks().forEach(t => { t.enabled = !next; });
    setCallMuted(next);
  }

  function handleCamToggle() {
    const stream = localStreamRef.current;
    if (!stream) return;
    const next = !callCamOff;
    stream.getVideoTracks().forEach(t => { t.enabled = !next; });
    setCallCamOff(next);
  }

  async function handleScreenShare() {
    const pc = peerRef.current;
    if (!pc) return;
    if (screenSharing) {
      // Stop screen share, restore camera
      screenStreamRef.current?.getTracks().forEach(t => t.stop());
      screenStreamRef.current = null;
      const camTrack = localStreamRef.current?.getVideoTracks()[0];
      if (camTrack) replaceVideoTrack(pc, camTrack);
      if (localVideoRef.current && localStreamRef.current) {
        localVideoRef.current.srcObject = localStreamRef.current;
      }
      setScreenSharing(false);
    } else {
      try {
        const screenStream = await createScreenShareStream();
        screenStreamRef.current = screenStream;
        const screenTrack = screenStream.getVideoTracks()[0];
        replaceVideoTrack(pc, screenTrack);
        if (localVideoRef.current) localVideoRef.current.srcObject = screenStream;
        // Auto-stop when user clicks browser's "Stop sharing" button
        screenTrack.onended = () => handleScreenShare();
        setScreenSharing(true);
      } catch { /* user cancelled */ }
    }
  }

  async function openGallery(ctx: 'chat' | 'group' | 'channel') {
    setShowGallery(true);
    setGalleryLoading(true);
    setGalleryItems([]);
    try {
      if (ctx === 'chat' && selectedChat) {
        setGalleryTitle(selectedChat.name || '');
        const items = await getChatMedia(session!.token, selectedChat.user_id);
        setGalleryItems(items);
      } else if (ctx === 'group' && selectedGroup) {
        setGalleryTitle(selectedGroup.group_name);
        const items = await getGroupMedia(session!.token, selectedGroup.id);
        setGalleryItems(items);
      } else if (ctx === 'channel' && selectedChannel) {
        setGalleryTitle(selectedChannel.name);
        const items = await getChannelMedia(session!.token, selectedChannel.id);
        setGalleryItems(items);
      }
    } catch { /* ignore */ }
    setGalleryLoading(false);
  }

  // ─── Group calls ───────────────────────────────────────────────────────────

  async function acceptGroupCall() {
    const cs = callState;
    if (cs.phase !== 'group_incoming') return;
    const { groupId, groupName, type, roomName } = cs;

    try {
      const stream = type === 'video' ? await createLocalVideoStream() : await createLocalAudioStream();
      localStreamRef.current = stream;
      if (localVideoRef.current && type === 'video') localVideoRef.current.srcObject = stream;
    } catch { /* no mic/cam */ }

    emitGroupCallJoin(socket, { roomName, userId: session!.userId });
    setCallState({ phase: 'group_connected', groupId, groupName, type, roomName, duration: 0 });
    startCallTimer();
  }

  // ─── PIN lock ──────────────────────────────────────────────────────────────

  async function hashPin(pin: string): Promise<string> {
    const buf  = new TextEncoder().encode(pin);
    const hash = await crypto.subtle.digest('SHA-256', buf);
    return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('');
  }

  async function checkPin(pin: string): Promise<boolean> {
    const stored = localStorage.getItem('wm_pin_hash');
    if (!stored) return false;
    return (await hashPin(pin)) === stored;
  }

  async function enablePin(pin: string): Promise<boolean> {
    if (pin.length < 4) return false;
    localStorage.setItem('wm_pin_hash', await hashPin(pin));
    localStorage.setItem('wm_pin_enabled', '1');
    setPinEnabled(true);
    return true;
  }

  function disablePin() {
    localStorage.removeItem('wm_pin_hash');
    localStorage.removeItem('wm_pin_enabled');
    setPinEnabled(false);
    setPinLocked(false);
  }

  async function handleUnlockPin() {
    const ok = await checkPin(pinInput);
    if (ok) { setPinLocked(false); setPinInput(''); setPinError(''); }
    else    { setPinError(t('pin.wrong')); setPinInput(''); }
  }

  async function handleSetPin(e: React.FormEvent) {
    e.preventDefault();
    if (newPin1.length < 4) { setPinError(t('pin.tooShort')); return; }
    if (newPin1 !== newPin2) { setPinError(t('pin.mismatch')); return; }
    await enablePin(newPin1);
    setShowSetPin(false);
    setNewPin1(''); setNewPin2(''); setPinError('');
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

  function handleUserSearch(q: string) {
    setUserSearchQuery(q);
    setUserSearchResults(null);
    if (userSearchTimer.current) clearTimeout(userSearchTimer.current);
    if (q.trim().length < 2 || !session) return;
    userSearchTimer.current = setTimeout(async () => {
      try {
        const results = await searchUsers(session.token, q.trim());
        setUserSearchResults(results);
      } catch { setUserSearchResults([]); }
    }, 400);
  }

  async function handleJoinGroup(groupId: number) {
    if (!session) return;
    try {
      await joinGroup(session.token, groupId);
      const updated = await loadGroups(session.token);
      setGroups(updated.data ?? []);
    } catch (e) { console.error('[JoinGroup]', e); }
  }

  async function handleSubscribeChannel(channelId: number) {
    if (!session) return;
    try {
      await subscribeChannel(session.token, channelId);
      const updated = await loadChannels(session.token);
      setChannels(updated.data ?? []);
    } catch (e) { console.error('[SubscribeChannel]', e); }
  }

  function openUserChat(user: UserSearchResult) {
    const existing = chats.find(c => c.user_id === user.id);
    if (existing) {
      setSelectedChat(existing);
    } else {
      const synthetic: ChatItem = {
        user_id: user.id,
        name: [user.first_name, user.last_name].filter(Boolean).join(' ') || user.username,
        avatar: user.avatar,
      };
      setChats(prev => [synthetic, ...prev]);
      setSelectedChat(synthetic);
    }
    setSection('chats');
    setUserSearchQuery('');
    setUserSearchResults(null);
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
      if (text) {
        localStorage.setItem(draftKey(selectedChat.user_id), text);
        setChatDrafts(prev => ({ ...prev, [selectedChat.user_id]: text }));
      } else {
        localStorage.removeItem(draftKey(selectedChat.user_id));
        setChatDrafts(prev => { const n = { ...prev }; delete n[selectedChat.user_id]; return n; });
      }
    }
  }

  // Load all drafts from localStorage into state on init
  useEffect(() => {
    const drafts: Record<number, string> = {};
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith('wm_draft_')) {
        const uid = Number(key.replace('wm_draft_', ''));
        const val = localStorage.getItem(key);
        if (uid && val) drafts[uid] = val;
      }
    }
    setChatDrafts(drafts);
  }, []);

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
  ];

  return (
    <div className="layout">

      {/* ── PIN lock overlay ─────────────────────────────────────────────── */}
      {pinLocked && session && (
        <div className="pin-overlay">
          <div className="pin-overlay-logo">🔐</div>
          <div className="pin-overlay-title">{t('pin.lock')}</div>
          <div className="pin-overlay-sub">{t('pin.enter')}</div>
          <div className="pin-dots">
            {[0,1,2,3].map(i => (
              <div key={i} className={`pin-dot ${pinInput.length > i ? 'filled' : ''}`} />
            ))}
          </div>
          <input
            className="pin-input-field"
            type="password"
            inputMode="numeric"
            maxLength={8}
            autoFocus
            value={pinInput}
            onChange={e => { setPinInput(e.target.value.replace(/\D/g, '')); setPinError(''); }}
            onKeyDown={e => { if (e.key === 'Enter') handleUnlockPin(); }}
            placeholder="····"
          />
          {pinError && <div className="pin-error">{pinError}</div>}
          <button className="btn-primary" onClick={handleUnlockPin}>{t('pin.unlock')}</button>
        </div>
      )}

      {/* ── Call overlay ─────────────────────────────────────────────────── */}
      {callState.phase !== 'idle' && (
        <div className="call-overlay">
          {/* Hidden audio element — always mounted so ontrack can attach the remote stream
              for both audio and video calls before the visible UI elements exist */}
          <audio ref={remoteAudioRef} autoPlay style={{ display: 'none' }} />

          {/* Incoming 1-on-1 */}
          {callState.phase === 'incoming' && (
            <>
              <div className="call-incoming-card">
                <Avatar name={callState.peer.name} src={callState.peer.avatar} size={88} />
                <h2>{callState.peer.name}</h2>
                <div className="call-incoming-type">
                  {callState.type === 'video' ? t('call.videoCall') : t('call.voiceCall')}
                </div>
                <div className="call-incoming-actions">
                  <div style={{display:'flex',flexDirection:'column',alignItems:'center',gap:8}}>
                    <button className="call-btn-round decline" onClick={rejectIncomingCall}>✕</button>
                    <span className="call-btn-round-label">{t('call.decline')}</span>
                  </div>
                  <div style={{display:'flex',flexDirection:'column',alignItems:'center',gap:8}}>
                    <button className="call-btn-round accept" onClick={acceptCall}>✓</button>
                    <span className="call-btn-round-label">{t('call.accept')}</span>
                  </div>
                </div>
              </div>
            </>
          )}

          {/* Outgoing 1-on-1 */}
          {callState.phase === 'outgoing' && (
            <>
              <div className="call-audio-card">
                <Avatar name={callState.peer.name} src={callState.peer.avatar} size={88} />
                <h2>{callState.peer.name}</h2>
                <p className="call-status">{t('call.calling')}</p>
              </div>
              <div className="call-controls-bar">
                <button className="call-ctrl-btn end-btn" onClick={endActiveCall}>📵</button>
              </div>
            </>
          )}

          {/* Connected 1-on-1 */}
          {callState.phase === 'connected' && (
            <>
              {callState.type === 'video' ? (
                <div className="call-video-wrap">
                  <video ref={remoteVideoRef} className="call-remote-video" autoPlay playsInline />
                  <video ref={localVideoRef}  className="call-local-video"  autoPlay playsInline muted />
                  <div style={{position:'absolute',top:16,left:16,display:'flex',flexDirection:'column',gap:4}}>
                    <span style={{color:'#fff',fontWeight:600,fontSize:16,textShadow:'0 1px 4px rgba(0,0,0,.8)'}}>{callState.peer.name}</span>
                    <span style={{color:'rgba(255,255,255,.7)',fontSize:13}}>{formatCallDuration(callState.duration)}</span>
                  </div>
                </div>
              ) : (
                <div className="call-audio-card">
                  <Avatar name={callState.peer.name} src={callState.peer.avatar} size={88} />
                  <h2>{callState.peer.name}</h2>
                  <p className="call-duration">{formatCallDuration(callState.duration)}</p>
                </div>
              )}
              <div className="call-controls-bar">
                <button className={`call-ctrl-btn ${callMuted ? 'active' : ''}`} onClick={handleMute}>
                  {callMuted ? '🔇' : '🎙'}
                </button>
                {callState.type === 'video' && (
                  <button className={`call-ctrl-btn ${callCamOff ? 'active' : ''}`} onClick={handleCamToggle}>
                    {callCamOff ? '📷' : '📹'}
                  </button>
                )}
                <button
                  className={`call-ctrl-btn ${screenSharing ? 'active screen-share-active' : ''}`}
                  onClick={handleScreenShare}
                  title={screenSharing ? t('call.stopShare') : t('call.shareScreen')}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/>
                    {screenSharing && <line x1="2" y1="3" x2="22" y2="17" stroke="#34d399" strokeWidth="2"/>}
                  </svg>
                </button>
                <button className="call-ctrl-btn end-btn" onClick={endActiveCall} title={t('call.end')}>📵</button>
              </div>
            </>
          )}

          {/* Incoming group call */}
          {callState.phase === 'group_incoming' && (
            <>
              <div className="call-incoming-card">
                <div style={{fontSize:56}}>👥</div>
                <h2>{callState.groupName}</h2>
                <div className="call-incoming-type">
                  {t('call.groupIncoming')} · {callState.fromName}
                </div>
                <div className="call-incoming-actions">
                  <div style={{display:'flex',flexDirection:'column',alignItems:'center',gap:8}}>
                    <button className="call-btn-round decline" onClick={endActiveCall}>✕</button>
                    <span className="call-btn-round-label">{t('call.decline')}</span>
                  </div>
                  <div style={{display:'flex',flexDirection:'column',alignItems:'center',gap:8}}>
                    <button className="call-btn-round accept" onClick={acceptGroupCall}>✓</button>
                    <span className="call-btn-round-label">{t('call.accept')}</span>
                  </div>
                </div>
              </div>
            </>
          )}

          {/* Connected group call */}
          {callState.phase === 'group_connected' && (
            <>
              {groupPeers.length > 0 ? (
                <div className="group-call-grid">
                  {/* local tile */}
                  <div className="group-call-peer">
                    {callState.type === 'video'
                      ? <video ref={localVideoRef} autoPlay playsInline muted style={{width:'100%',height:'100%',objectFit:'cover'}} />
                      : <div style={{fontSize:40}}>🎙</div>
                    }
                    <span className="group-call-peer-label">You</span>
                  </div>
                  {groupPeers.map(p => (
                    <div key={p.userId} className="group-call-peer">
                      {p.stream
                        ? <video autoPlay playsInline ref={el => { if (el && p.stream) el.srcObject = p.stream; }} style={{width:'100%',height:'100%',objectFit:'cover'}} />
                        : <div style={{fontSize:40}}>🎙</div>
                      }
                      <span className="group-call-peer-label">{p.name}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="call-audio-card">
                  <div style={{fontSize:56}}>👥</div>
                  <h2>{callState.groupName}</h2>
                  <p className="call-status">{t('call.connected')}</p>
                  <p className="call-duration">{formatCallDuration(callState.duration)}</p>
                </div>
              )}
              <div className="call-controls-bar">
                <button className={`call-ctrl-btn ${callMuted ? 'active' : ''}`} onClick={handleMute}>
                  {callMuted ? '🔇' : '🎙'}
                </button>
                {callState.type === 'video' && (
                  <button className={`call-ctrl-btn ${callCamOff ? 'active' : ''}`} onClick={handleCamToggle}>
                    {callCamOff ? '📷' : '📹'}
                  </button>
                )}
                <button className="call-ctrl-btn end-btn" onClick={endActiveCall}>📵</button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Side drawer backdrop ─────────────────────────────────────────────── */}
      {drawerOpen && <div className="drawer-backdrop" onClick={() => setDrawerOpen(false)} />}

      {/* ── Side drawer ──────────────────────────────────────────────────────── */}
      <div className={`side-drawer ${drawerOpen ? 'open' : ''}`}>
        {/* Profile */}
        <div className="drawer-profile">
          <Avatar name={session.username} size={56} />
          <div className="drawer-profile-name">{session.username}</div>
          {myProfile?.status_emoji || myProfile?.status_text
            ? <div className="drawer-profile-status" onClick={() => { setShowStatusModal(true); setDrawerOpen(false); }}>
                {myProfile.status_emoji} {myProfile.status_text}
              </div>
            : <div className="drawer-profile-sub" style={{cursor:'pointer'}} onClick={() => { setShowStatusModal(true); setDrawerOpen(false); }}>
                WorldMates · Установить статус
              </div>
          }
        </div>

        {/* Nav items */}
        <nav className="drawer-nav">
          {navItems.map(({ key, icon, label }) => (
            <button key={key} className={`drawer-item ${section === key ? 'active' : ''}`}
              onClick={() => { setSection(key); setDrawerOpen(false); }}>
              <span className="drawer-item-icon">{icon}</span>
              <span className="drawer-item-label">{label}</span>
            </button>
          ))}

          <div className="drawer-divider" />

          <button className="drawer-item" onClick={() => { handleOpenSaved(); setDrawerOpen(false); }}>
            <span className="drawer-item-icon">🔖</span>
            <span className="drawer-item-label">Сохранённые</span>
          </button>

          <button className="drawer-item" onClick={() => { handleOpenNotes(); setDrawerOpen(false); }}>
            <span className="drawer-item-icon">📝</span>
            <span className="drawer-item-label">Заметки</span>
          </button>

          <div className="drawer-divider" />

          <button className="drawer-item" onClick={() => { setSettingsOpen(true); setDrawerOpen(false); }}>
            <span className="drawer-item-icon">⚙️</span>
            <span className="drawer-item-label">{t('nav.settings')}</span>
          </button>

          <div className="drawer-divider" />

          {/* Night mode toggle (visual only for now) */}
          <div className="drawer-toggle-row">
            <span className="drawer-item-icon">🌙</span>
            <span className="drawer-toggle-label">{t('nav.nightMode')}</span>
            <label className="tg-toggle">
              <input type="checkbox" readOnly checked />
              <div className="tg-toggle-track" />
              <div className="tg-toggle-thumb" />
            </label>
          </div>
        </nav>

        {/* Footer */}
        <div className="drawer-footer">
          <button className="drawer-item" style={{width:'100%', color:'var(--danger)'}}
            onClick={() => { setDrawerOpen(false); logout(); }}>
            <span className="drawer-item-icon">🚪</span>
            <span className="drawer-item-label">{t('nav.logout')}</span>
          </button>
          <div className="drawer-version">WallyMates · v2.0</div>
        </div>
      </div>

      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside className="sidebar">
        <div className="sidebar-head">
          <button className="hamburger-btn" onClick={() => setDrawerOpen(true)} title="Меню">
            ☰
          </button>
          <h2 className="sidebar-title">
            {section === 'chats'    ? t('sidebar.messages') :
             section === 'groups'   ? t('sidebar.groups')   :
             section === 'channels' ? t('sidebar.channels') :
             section === 'stories'  ? t('sidebar.stories')  :
             section === 'calls'    ? t('sidebar.calls')    : t('sidebar.settings')}
          </h2>
          <button className="hamburger-btn" title="Поиск" style={{ fontSize: 17 }}
            onClick={() => { setShowGlobalSearch(true); setGlobalSearchQ(''); }}>🔍</button>
          <div className="socket-badge" title={socketStatus}>
            <span className={`dot ${socketStatus.startsWith('Connected') ? 'green' : 'grey'}`} />
          </div>
        </div>

        {/* ── Global search overlay ──────────────────────────────────────── */}
        {showGlobalSearch && (
          <div className="search-panel" style={{ position: 'absolute', inset: 0, zIndex: 10 }}>
            <div className="search-panel-head">
              <button className="search-panel-back" onClick={() => { setShowGlobalSearch(false); setGlobalSearchQ(''); }}>←</button>
              <input
                autoFocus
                className="search-panel-input"
                placeholder="Поиск людей, групп, каналов…"
                value={globalSearchQ}
                onChange={e => setGlobalSearchQ(e.target.value)}
              />
            </div>
            <div className="search-tabs">
              {(['people', 'groups', 'channels'] as const).map(tab => (
                <button key={tab} className={`search-tab ${globalSearchTab === tab ? 'active' : ''}`}
                  onClick={() => setGlobalSearchTab(tab)}>
                  {tab === 'people' ? '👤 Люди' : tab === 'groups' ? '👥 Группы' : '📢 Каналы'}
                </button>
              ))}
            </div>
            <div className="search-results">
              {globalSearchLoading && <div className="search-loading">Поиск…</div>}
              {!globalSearchLoading && globalSearchQ.trim() === '' && (
                <div className="search-empty">
                  <div className="search-empty-icon">🔍</div>
                  <span>Введите запрос для поиска</span>
                </div>
              )}
              {!globalSearchLoading && globalSearchQ.trim() !== '' && globalSearchTab === 'people' && globalSearchRes.people.map(u => (
                <div key={u.id} className="search-result-row" onClick={() => {
                  setShowGlobalSearch(false);
                  setGlobalSearchQ('');
                  const found = chats.find(c => c.user_id === u.id);
                  if (found) selectChat(found);
                }}>
                  <Avatar name={u.username} src={u.avatar} size={40} />
                  <div className="search-result-info">
                    <div className="search-result-name">{u.first_name ?? ''} {u.last_name ?? ''} {!u.first_name && !u.last_name ? u.username : ''}</div>
                    <div className="search-result-sub">@{u.username}</div>
                  </div>
                  <span className="search-result-type people">Чат</span>
                </div>
              ))}
              {!globalSearchLoading && globalSearchQ.trim() !== '' && globalSearchTab === 'groups' && globalSearchRes.groups.map(g => (
                <div key={g.id} className="search-result-row" onClick={() => {
                  setShowGlobalSearch(false); setGlobalSearchQ('');
                  setSection('groups');
                  setSelectedGroup(g);
                }}>
                  <Avatar name={g.group_name} size={40} />
                  <div className="search-result-info">
                    <div className="search-result-name">{g.group_name}</div>
                    <div className="search-result-sub">{g.members_count ?? ''} участников</div>
                  </div>
                  <span className="search-result-type group">Группа</span>
                </div>
              ))}
              {!globalSearchLoading && globalSearchQ.trim() !== '' && globalSearchTab === 'channels' && globalSearchRes.channels.map(c => (
                <div key={c.id} className="search-result-row" onClick={() => {
                  setShowGlobalSearch(false); setGlobalSearchQ('');
                  setSection('channels');
                  setSelectedChannel(c);
                }}>
                  <Avatar name={c.name} src={c.avatar_url} size={40} />
                  <div className="search-result-info">
                    <div className="search-result-name">{c.name}</div>
                    <div className="search-result-sub">{c.description ?? ''}</div>
                  </div>
                  <span className="search-result-type channel">Канал</span>
                </div>
              ))}
              {!globalSearchLoading && globalSearchQ.trim() !== '' && (
                globalSearchTab === 'people'   && globalSearchRes.people.length   === 0 ||
                globalSearchTab === 'groups'   && globalSearchRes.groups.length   === 0 ||
                globalSearchTab === 'channels' && globalSearchRes.channels.length === 0
              ) && (
                <div className="search-empty">
                  <div className="search-empty-icon">😶</div>
                  <span>Ничего не найдено</span>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Search (chats only) */}
        {!showGlobalSearch && section === 'chats' && (
          <div className="search-box">
            <span className="search-icon">🔍</span>
            <input placeholder={t('sidebar.search')} value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)} />
          </div>
        )}

        {/* Find people (chats section) */}
        {section === 'chats' && (
          <div className="search-box" style={{ borderTop: 'none', paddingTop: 0 }}>
            <span className="search-icon">👤</span>
            <input
              placeholder={t('sidebar.searchPeople')}
              value={userSearchQuery}
              onChange={e => handleUserSearch(e.target.value)}
            />
          </div>
        )}

        {/* ── People search results ──────────────────────────────────────── */}
        {section === 'chats' && userSearchQuery.trim().length >= 2 && (
          <div className="list-scroll" style={{ maxHeight: 240, borderBottom: '1px solid var(--border)' }}>
            {userSearchResults === null && (
              <div className="empty-state" style={{ fontSize: 12 }}>…</div>
            )}
            {userSearchResults !== null && userSearchResults.length === 0 && (
              <div className="empty-state">{t('sidebar.noPeopleFound')}</div>
            )}
            {(userSearchResults ?? []).map(u => (
              <div key={u.id} className="chat-item" style={{ paddingRight: 8 }}>
                <Avatar name={u.first_name || u.username} src={u.avatar} size={36} />
                <div className="chat-item-body" style={{ flex: 1, minWidth: 0 }}>
                  <div className="chat-item-row">
                    <span className="chat-item-name">{[u.first_name, u.last_name].filter(Boolean).join(' ') || u.username}</span>
                  </div>
                  <div className="chat-item-row">
                    <span className="chat-item-preview" style={{ fontSize: 11 }}>@{u.username}</span>
                  </div>
                </div>
                <button className="btn-sm" onClick={() => openUserChat(u)}>{t('sidebar.chat')}</button>
              </div>
            ))}
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
                    {chatDrafts[chat.user_id] ? (
                      <span className="chat-item-preview">
                        <span className="draft-label">{t('draft.label')}:</span> {chatDrafts[chat.user_id]}
                      </span>
                    ) : (
                      <span className="chat-item-preview">{previewLastMessage(chat.last_message)}</span>
                    )}
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

            {/* ── Bot search ─────────────────────────────────────────────── */}
            <button className="archived-toggle" onClick={() => { setShowBotSearch(v => !v); setBotQuery(''); setBotResults([]); }}>
              <span>🤖 {t('sidebar.bots')}</span>
            </button>
            {showBotSearch && (
              <div>
                <input className="search-input" style={{margin: '0 8px 6px', width: 'calc(100% - 16px)'}}
                  placeholder={t('sidebar.searchBots')}
                  value={botQuery} onChange={e => handleBotQueryChange(e.target.value)} />
                {botResults.map(bot => (
                  <div key={bot.bot_id_str} className="chat-item" style={{paddingRight: 8, gap: 8}}>
                    <Avatar name={bot.display_name || bot.username} src={bot.avatar} size={36} />
                    <div style={{flex:1, minWidth:0}}>
                      <div className="chat-name">{bot.display_name || bot.username}</div>
                      {bot.description && <div className="chat-last" style={{fontSize:11}}>{bot.description.slice(0,60)}</div>}
                    </div>
                    <div style={{display:'flex', gap:4, flexShrink:0}}>
                      {bot.web_app_url && (
                        <button className="btn-sm" title="Mini App" onClick={() => window.open(bot.web_app_url, '_blank')}>🌐</button>
                      )}
                      <button className="btn-sm" onClick={() => openBotChat(bot)}>{t('sidebar.chat')}</button>
                    </div>
                  </div>
                ))}
              </div>
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
            {filteredGroups.map(g => {
              const alreadyIn = groups.some(og => og.id === g.id);
              const isSearch  = groupSearchResults !== null;
              return (
                <div key={g.id} className={`chat-item ${selectedGroup?.id === g.id ? 'active' : ''}`}
                  style={{ cursor: 'pointer' }}
                  onClick={() => { if (alreadyIn || !isSearch) setSelectedGroup(g); }}
                >
                  <Avatar name={asText(g.group_name, 'G')} src={g.avatar} size={44} />
                  <div className="chat-item-body" style={{ flex: 1, minWidth: 0 }}>
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
                  {isSearch && !alreadyIn && (
                    <button className="btn-sm" style={{ flexShrink: 0 }}
                      onClick={e => { e.stopPropagation(); handleJoinGroup(g.id); }}>
                      {t('sidebar.joinGroup')}
                    </button>
                  )}
                </div>
              );
            })}
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
            <form className="create-form create-channel-form" onSubmit={handleCreateChannel}>
              <div className="create-channel-header">
                <span className="create-channel-icon">📡</span>
                <span className="create-channel-title">{t('channel.createTitle')}</span>
              </div>
              <input className="create-channel-input" value={newChannelName}
                onChange={e => setNewChannelName(e.target.value)}
                placeholder={t('sidebar.channelName')} />
              <input className="create-channel-input" value={createChannelUsername}
                onChange={e => setCreateChannelUsername(e.target.value)}
                placeholder="@username (optional)" />
              <textarea className="create-channel-input create-channel-textarea" rows={2}
                value={newChannelDesc}
                onChange={e => setNewChannelDesc(e.target.value)}
                placeholder={t('sidebar.description')} />
              <div className="create-channel-privacy">
                <span>{t('channel.private')}</span>
                <button type="button"
                  className={`toggle-btn ${createChannelIsPrivate ? 'on' : ''}`}
                  onClick={() => setCreateChannelIsPrivate(p => !p)}>
                  <span className="toggle-knob" />
                </button>
              </div>
              <button type="submit" className="btn-primary create-channel-submit" disabled={!newChannelName.trim()}>
                {t('sidebar.create')}
              </button>
            </form>
            {filteredChannels.length === 0 && <div className="empty-state">{t('sidebar.noChannels')}</div>}
            {filteredChannels.map(c => {
              const alreadySubscribed = channels.some(oc => oc.id === c.id);
              const isSearch = channelSearchResults !== null;
              return (
                <div key={c.id}
                  className={`chat-item ${selectedChannel?.id === c.id ? 'active' : ''}`}
                  style={{ cursor: 'pointer' }}
                  onClick={() => { if (alreadySubscribed || !isSearch) setSelectedChannel(c); }}
                >
                  <Avatar name={asText(c.name, 'C')} src={c.avatar_url} size={44} />
                  <div className="chat-item-body" style={{ flex: 1, minWidth: 0 }}>
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
                  {isSearch && !alreadySubscribed && (
                    <button className="btn-sm" style={{ flexShrink: 0 }}
                      onClick={e => { e.stopPropagation(); handleSubscribeChannel(c.id); }}>
                      {t('sidebar.subscribeChannel')}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* ── Stories ───────────────────────────────────────────────────── */}
        {section === 'stories' && (
          <div className="stories-section">
            {/* Upload zone */}
            <form className="story-upload-zone" onSubmit={handleCreateStory}>
              <label className="story-upload-label" title="Добавить историю">
                <div className="story-upload-circle">
                  {newStoryFile
                    ? <span style={{fontSize:28}}>✅</span>
                    : <span className="story-upload-plus">+</span>
                  }
                </div>
                <span className="story-upload-caption">{newStoryFile ? newStoryFile.name.slice(0,14)+'…' : 'Ваша история'}</span>
                <input type="file" accept="image/*,video/*" style={{display:'none'}}
                  onChange={e => setNewStoryFile(e.target.files?.[0] ?? null)} />
              </label>
              {newStoryFile && (
                <button type="submit" className="story-upload-btn">
                  Опубликовать
                </button>
              )}
            </form>

            {/* Stories grid — Instagram style circles */}
            <div className="stories-circles-header">Истории</div>
            {stories.length === 0 && (
              <div className="stories-empty">
                <div style={{fontSize:52,opacity:.25}}>⭕</div>
                <p>Историй пока нет</p>
              </div>
            )}
            <div className="stories-circles-row">
              {stories.map((s, idx) => (
                <button key={s.id}
                  className={`story-circle-btn ${s.is_seen ? 'seen' : 'unseen'}`}
                  onClick={() => openStory(idx)}
                  title={s.user_name}>
                  <div className="story-circle-ring">
                    <div className="story-circle-avatar">
                      {s.user_avatar
                        ? <img src={s.user_avatar} alt={s.user_name} />
                        : <span>{(s.user_name ?? '?')[0].toUpperCase()}</span>
                      }
                    </div>
                  </div>
                  <span className="story-circle-name">{s.user_name?.split(' ')[0] ?? `#${s.id}`}</span>
                </button>
              ))}
            </div>

            {/* Story cards grid */}
            {stories.length > 0 && (
              <>
                <div className="stories-circles-header" style={{marginTop:20}}>Все истории</div>
                <div className="stories-cards-grid">
                  {stories.map((s, idx) => (
                    <button key={s.id}
                      className={`story-card ${s.is_seen ? 'seen' : ''}`}
                      onClick={() => openStory(idx)}>
                      <div className="story-card-media">
                        {s.file
                          ? s.file_type === 'video'
                            ? <video src={s.file} />
                            : <img src={s.file} alt="" />
                          : <div className="story-card-placeholder">{(s.user_name ?? '?')[0].toUpperCase()}</div>
                        }
                        {!s.is_seen && <div className="story-card-unseen-dot" />}
                      </div>
                      <div className="story-card-footer">
                        <Avatar name={s.user_name ?? '?'} src={s.user_avatar} size={22} />
                        <span className="story-card-name">{s.user_name?.split(' ')[0] ?? `#${s.id}`}</span>
                        {(s.views_count ?? 0) > 0 && <span className="story-card-views">👁 {s.views_count}</span>}
                      </div>
                    </button>
                  ))}
                </div>
              </>
            )}
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

        {/* ── Settings: content moved to <main> ──────────────────────── */}
        {section === 'settings' && (
          <div className="list-scroll settings-panel" style={{display:'none'}}>
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
        const totalReactions = s.reaction
          ? s.reaction.like + s.reaction.love + s.reaction.haha + s.reaction.wow + s.reaction.sad + s.reaction.angry
          : 0;
        return (
          <div className="story-viewer" onClick={e => { if (e.target === e.currentTarget) closeStory(); }}>
            {/* ── Progress bars ── */}
            <div className="story-viewer-progress">
              {stories.map((_, i) => (
                <div key={i} className="story-progress-track">
                  <div className="story-progress-fill"
                    style={{ width: i < viewingStoryIdx ? '100%' : i === viewingStoryIdx ? `${storyProgress}%` : '0%' }}
                  />
                </div>
              ))}
            </div>

            {/* ── Header ── */}
            <div className="story-viewer-header">
              <Avatar name={s.user_name ?? '?'} src={s.user_avatar} size={38} />
              <div className="story-viewer-meta">
                <span className="story-viewer-name">{s.user_name ?? `Story #${s.id}`}</span>
                <span className="story-viewer-time">{s.created_at ?? ''}</span>
              </div>
              {s.is_owner && (
                <button className="story-viewer-delete" title="Удалить историю"
                  onClick={e => { e.stopPropagation(); handleDeleteStory(s.id); }}>🗑</button>
              )}
              <button className="story-viewer-close" onClick={e => { e.stopPropagation(); closeStory(); }}>✕</button>
            </div>

            {/* ── Media card ── */}
            <div className="story-viewer-card"
              onMouseEnter={() => { setStoryPaused(true); storyPausedRef.current = true; }}
              onMouseLeave={() => { if (!showStoryComments) { setStoryPaused(false); storyPausedRef.current = false; } }}
              onClick={e => e.stopPropagation()}
            >
              <div className="story-viewer-media">
                {s.file
                  ? s.file_type === 'video'
                    ? <video src={s.file} autoPlay loop className="story-viewer-img" />
                    : <img src={s.file} alt="story" className="story-viewer-img" />
                  : <div className="story-viewer-placeholder">{(s.user_name ?? '?')[0].toUpperCase()}</div>
                }
              </div>

              {/* ── Reactions bar ── */}
              <div className="story-reactions-bar" onClick={e => e.stopPropagation()}>
                {([
                  { emoji: '❤️', key: 'like'  },
                  { emoji: '😍', key: 'love'  },
                  { emoji: '😂', key: 'haha'  },
                  { emoji: '😮', key: 'wow'   },
                  { emoji: '😢', key: 'sad'   },
                  { emoji: '😡', key: 'angry' },
                ] as { emoji: string; key: string }[]).map(r => {
                  const count = s.reaction?.[r.key as keyof typeof s.reaction] as number ?? 0;
                  const isActive = s.reaction?.is_reacted && s.reaction?.reacted_type === r.key;
                  return (
                    <button key={r.key}
                      className={`story-reaction-btn ${isActive ? 'active' : ''}`}
                      onClick={() => handleReactToStory(r.key)}
                      title={r.key}>
                      <span className="story-reaction-emoji">{r.emoji}</span>
                      {count > 0 && <span className="story-reaction-count">{count}</span>}
                    </button>
                  );
                })}
              </div>

              {/* ── Footer: pause/play + views + comment button ── */}
              <div className="story-viewer-footer" onClick={e => e.stopPropagation()}>
                {/* Pause / play toggle */}
                <button className="story-pause-btn"
                  title={storyPaused ? t('story.play') : t('story.pause')}
                  onClick={() => {
                    const next = !storyPaused;
                    setStoryPaused(next); storyPausedRef.current = next;
                  }}>
                  {storyPaused ? '▶' : '⏸'}
                </button>

                {(s.views_count ?? 0) > 0 && (
                  <span className="story-footer-stat">👁 {s.views_count}</span>
                )}
                {totalReactions > 0 && (
                  <span className="story-footer-stat">💫 {totalReactions}</span>
                )}
                <div style={{flex:1}} />

                {/* Big visible comment button */}
                <button className={`story-comment-toggle ${showStoryComments ? 'active' : ''}`}
                  onClick={() => {
                    if (showStoryComments) {
                      setShowStoryComments(false);
                      setStoryPaused(false); storyPausedRef.current = false;
                    } else {
                      handleOpenStoryComments();
                    }
                  }}>
                  💬 {t('story.comments')}
                  {(s.comment_count ?? 0) > 0 && <span className="story-comment-count-badge">{s.comment_count}</span>}
                </button>
              </div>

              {/* ── Comments panel ── */}
              {showStoryComments && (
                <div className="story-comments-panel" onClick={e => e.stopPropagation()}>
                  <div className="story-comments-head">
                    <span style={{fontWeight:600,fontSize:14}}>{t('story.comments')}</span>
                    <button className="story-viewer-close" style={{fontSize:12,width:24,height:24}}
                      onClick={() => {
                        setShowStoryComments(false);
                        setStoryPaused(false); storyPausedRef.current = false;
                      }}>✕</button>
                  </div>
                  <div className="story-comments-list">
                    {storyCommentsLoading && <div className="search-loading">Загрузка…</div>}
                    {!storyCommentsLoading && storyComments.length === 0 && (
                      <div className="story-comments-empty">Будьте первым, кто прокомментирует</div>
                    )}
                    {storyComments.map(c => (
                      <div key={c.id} className="story-comment-row">
                        <Avatar name={c.user_name ?? '?'} src={c.user_avatar} size={28} />
                        <div className="story-comment-body">
                          <span className="story-comment-author">{c.user_name}</span>
                          <span className="story-comment-text">{c.text}</span>
                        </div>
                        {c.user_id === session?.userId && (
                          <button className="note-delete-btn"
                            onClick={() => handleDeleteStoryComment(c.id)}>✕</button>
                        )}
                      </div>
                    ))}
                  </div>
                  <div className="story-comment-composer">
                    <input className="story-comment-input" placeholder={t('story.commentPlaceholder')}
                      value={storyCommentInput}
                      onChange={e => setStoryCommentInput(e.target.value)}
                      onFocus={() => { setStoryPaused(true); storyPausedRef.current = true; }}
                      onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendStoryComment(); } }}
                    />
                    <button className="story-comment-send" disabled={!storyCommentInput.trim()}
                      onClick={handleSendStoryComment}>→</button>
                  </div>
                </div>
              )}
            </div>

            {/* ── Nav arrows ── */}
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

        {/* ── Saved messages panel ─────────────────────────────────────────── */}
        {showSaved && (
          <div className="saved-panel">
            <div className="saved-panel-head">
              <button className="search-panel-back" onClick={() => setShowSaved(false)}>←</button>
              <span className="saved-panel-title">🔖 Сохранённые сообщения</span>
              {savedItems.length > 0 && (
                <button className="action-btn" title="Очистить всё" style={{ marginLeft: 'auto', color: 'var(--danger)' }}
                  onClick={async () => {
                    if (!session || !window.confirm('Очистить все сохранённые?')) return;
                    setSavedItems([]); setSavedSet(new Set());
                    await unsaveMessage(session.token, 0, 'clear_all').catch(async () => {
                      await import('./api').then(m => m.clearSaved(session.token));
                    });
                  }}>🗑</button>
              )}
            </div>
            <div className="saved-list">
              {savedLoading && <div className="search-loading">Загрузка…</div>}
              {!savedLoading && savedItems.length === 0 && (
                <div className="saved-empty">
                  <div className="saved-empty-icon">🔖</div>
                  <h3>Нет сохранённых</h3>
                  <p>Нажмите 🏷️ под любым сообщением, чтобы сохранить его здесь</p>
                </div>
              )}
              {savedItems.map((item, i) => (
                <div key={i} className="saved-item">
                  <button className="saved-item-unsave" title="Убрать"
                    onClick={async () => {
                      if (!session) return;
                      setSavedItems(prev => prev.filter((_, j) => j !== i));
                      setSavedSet(prev => { const s = new Set(prev); s.delete(item.message_id); return s; });
                      await unsaveMessage(session.token, item.message_id, item.chat_type);
                    }}>✕</button>
                  <div className="saved-item-meta">
                    <span className="saved-item-source">{item.chat_name || 'Чат'}</span>
                    <span className="saved-item-sender">· {item.sender_name}</span>
                    <span className="saved-item-time">{item.original_time ? new Date(item.original_time * 1000).toLocaleDateString('ru') : ''}</span>
                  </div>
                  {item.text && <div className="saved-item-text">{item.text}</div>}
                  {item.media_url && item.media_type === 'image' && (
                    <div className="saved-item-media">
                      <img src={absMediaUrl(item.media_url)} alt="media" loading="lazy"
                        onClick={() => setLightboxSrc(absMediaUrl(item.media_url!))} />
                    </div>
                  )}
                  {item.media_url && item.media_type !== 'image' && (
                    <div className="saved-item-meta" style={{ marginTop: 6 }}>
                      <a href={absMediaUrl(item.media_url)} target="_blank" rel="noreferrer" style={{ color: 'var(--accent)' }}>
                        📎 {item.media_type ?? 'Файл'}
                      </a>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Notes panel ──────────────────────────────────────────────── */}
        {showNotes && (
          <div className="notes-panel">
            <div className="notes-panel-head">
              <button className="search-panel-back" onClick={() => setShowNotes(false)}>←</button>
              <span className="notes-panel-title">📝 Заметки</span>
              {notesStorage && (
                <span className="notes-storage-badge">
                  {(notesStorage.used_bytes / 1048576).toFixed(1)} МБ
                  {notesStorage.quota_bytes > 0 && ` / ${(notesStorage.quota_bytes / 1073741824).toFixed(1)} ГБ`}
                </span>
              )}
            </div>

            <div className="notes-list">
              {notesLoading && <div className="search-loading">Загрузка…</div>}
              {!notesLoading && notes.length === 0 && (
                <div className="saved-empty">
                  <div className="saved-empty-icon">📝</div>
                  <h3>Нет заметок</h3>
                  <p>Напишите что-нибудь в поле ниже, чтобы создать первую заметку</p>
                </div>
              )}
              {notes.map(note => (
                <div key={note.id} className="note-card">
                  <div className="note-card-head">
                    <span className="note-type-icon">
                      {note.type === 'image' ? '🖼️' : note.type === 'video' ? '🎬' : note.type === 'audio' ? '🎵' : note.type === 'file' ? '📎' : '📝'}
                    </span>
                    <span className="note-card-time">
                      {note.created_at ? new Date(note.created_at * 1000).toLocaleString('ru', { day:'2-digit', month:'2-digit', hour:'2-digit', minute:'2-digit' }) : ''}
                    </span>
                    <button className="note-delete-btn" title="Удалить" onClick={() => handleDeleteNote(note.id)}>✕</button>
                  </div>
                  {note.text && <div className="note-card-text">{note.text}</div>}
                  {note.file_name && (
                    <div className="note-card-file">
                      <span className="note-card-fname">{note.file_name}</span>
                      {note.file_size > 0 && (
                        <span className="note-card-fsize">{(note.file_size / 1024).toFixed(1)} КБ</span>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div className="notes-composer">
              <textarea
                className="notes-composer-input"
                placeholder="Написать заметку…"
                rows={3}
                value={noteInput}
                onChange={e => setNoteInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); handleCreateNote(); } }}
              />
              <button
                className="notes-composer-send btn-primary"
                disabled={!noteInput.trim() || noteSending}
                onClick={handleCreateNote}
              >
                {noteSending ? '…' : 'Сохранить'}
              </button>
            </div>
          </div>
        )}

        {/* ── Custom status modal ───────────────────────────────────────── */}
        {showStatusModal && (
          <div className="status-modal-overlay" onClick={e => { if (e.target === e.currentTarget) setShowStatusModal(false); }}>
            <div className="status-modal">
              <div className="status-modal-head">
                <span className="status-modal-title">✨ Кастомный статус</span>
                <button className="settings-close-btn" style={{position:'static',width:28,height:28,fontSize:13}} onClick={() => setShowStatusModal(false)}>✕</button>
              </div>
              <div className="status-modal-preview">
                <span className="status-preview-emoji">{statusEmoji || '😶'}</span>
                <span className="status-preview-text">{statusText || 'Мой статус'}</span>
              </div>
              <div className="status-emoji-grid">
                {['😊','🔥','❄️','🎵','💻','🚀','🌙','☕','📚','🎮','💪','✈️','🍕','🎯','🤫','💡'].map(e => (
                  <button key={e} className={`status-emoji-btn ${statusEmoji === e ? 'active' : ''}`} onClick={() => setStatusEmoji(e)}>{e}</button>
                ))}
              </div>
              <input
                className="settings-field-input"
                placeholder="Что вы делаете? (макс. 60 символов)"
                maxLength={60}
                value={statusText}
                onChange={e => setStatusText(e.target.value)}
                style={{margin:'12px 0 16px'}}
              />
              <div style={{display:'flex',gap:10}}>
                <button className="btn-secondary" onClick={() => { setStatusEmoji(''); setStatusText(''); }}>Сбросить</button>
                <button
                  className={statusSaving==='done' ? 'btn-success' : statusSaving==='error' ? 'btn-danger' : 'btn-primary'}
                  style={{flex:1}}
                  disabled={statusSaving==='saving'}
                  onClick={handleSaveCustomStatus}
                >
                  {statusSaving==='saving' ? 'Сохраняю…' : statusSaving==='done' ? '✓ Сохранено' : statusSaving==='error' ? 'Ошибка' : 'Установить статус'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Settings full-screen overlay ──────────────────────────────── */}
        {settingsOpen ? (
          <div className="settings-overlay" onClick={e => { if (e.target === e.currentTarget) setSettingsOpen(false); }}>
          <div className="settings-main-view">
            {/* Close button */}
            <button className="settings-close-btn" onClick={() => setSettingsOpen(false)} title="Закрыть">✕</button>
            {/* Left nav column */}
            <div className="settings-nav-col">
              <div className="settings-profile-card">
                <label className="avatar-upload-label" title={t('settings.uploadAvatar')} style={{cursor:'pointer'}}>
                  <Avatar name={session.username} src={myProfile?.avatar} size={56} />
                  <span className="avatar-upload-badge">📷</span>
                  <input type="file" accept="image/*" style={{display:'none'}}
                    onChange={e => { const f = e.target.files?.[0]; if (f) handleAvatarUpload(f); }} />
                </label>
                <div>
                  <div className="settings-profile-name">
                    {myProfile ? `${myProfile.first_name ?? ''} ${myProfile.last_name ?? ''}`.trim() || session.username : session.username}
                  </div>
                  <div className="settings-profile-meta">@{session.username}</div>
                  <div className="settings-profile-meta">{t('settings.userId')} {session.userId}</div>
                </div>
              </div>

              {([
                { id: 'profile'       as typeof settingsTab, icon: '👤', label: t('settings.editProfile'),  badge: undefined as number|undefined },
                { id: 'social'        as typeof settingsTab, icon: '🔗', label: 'Соцсети',                  badge: undefined as number|undefined },
                { id: 'privacy'       as typeof settingsTab, icon: '🔒', label: t('settings.privacy'),       badge: undefined as number|undefined },
                { id: 'notifications' as typeof settingsTab, icon: '🔔', label: 'Уведомления',              badge: undefined as number|undefined },
                { id: 'blocked'       as typeof settingsTab, icon: '🚫', label: t('settings.blockedUsers'), badge: blockedUsers.length > 0 ? blockedUsers.length : undefined },
                { id: 'language'      as typeof settingsTab, icon: '🌐', label: t('settings.language'),      badge: undefined as number|undefined },
                { id: 'security'      as typeof settingsTab, icon: '🔐', label: t('settings.security'),      badge: undefined as number|undefined },
              ]).map(row => (
                <button key={row.id}
                  className={`settings-nav-row ${settingsTab === row.id ? 'active' : ''}`}
                  onClick={() => setSettingsTab(row.id)}>
                  <span className="settings-nav-icon">{row.icon}</span>
                  <span className="settings-nav-label">{row.label}</span>
                  {row.badge ? <span className="settings-nav-badge">{row.badge}</span> : null}
                  <span className="settings-nav-chevron">›</span>
                </button>
              ))}

              <div style={{flex:1}} />

              <div className="settings-nav-footer">
                <div className="settings-nav-status">
                  <span>📡 {t('settings.socketStatus')}</span>
                  <span style={{color: socketStatus === 'green' ? 'var(--online)' : 'var(--text-2)'}}>{translateSocketStatus(socketStatus)}</span>
                </div>
                <button className="btn-danger" style={{width:'100%'}} onClick={logout}>{t('settings.signOut')}</button>
              </div>
            </div>

            {/* Right content column */}
            <div className="settings-content-col">
              {settingsTab === 'profile' && (
                <>
                  <h2 className="settings-content-title">{t('settings.editProfile')}</h2>

                  {/* Status shortcut */}
                  <div className="settings-status-card" onClick={() => setShowStatusModal(true)}>
                    <span className="settings-status-emoji">{myProfile?.status_emoji || '😶'}</span>
                    <div>
                      <div className="settings-status-label">Кастомный статус</div>
                      <div className="settings-status-value">{myProfile?.status_text || myProfile?.status_emoji ? `${myProfile.status_emoji} ${myProfile.status_text}`.trim() : 'Нажмите, чтобы установить'}</div>
                    </div>
                    <span className="settings-nav-chevron" style={{marginLeft:'auto'}}>›</span>
                  </div>

                  <div className="settings-fields-grid">
                    <div className="settings-field">
                      <label className="settings-field-label">{t('settings.firstName')}</label>
                      <input className="settings-field-input" value={profileFirst} onChange={e => setProfileFirst(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">{t('settings.lastName')}</label>
                      <input className="settings-field-input" value={profileLast} onChange={e => setProfileLast(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">{t('settings.username')}</label>
                      <input className="settings-field-input" value={profileUser} onChange={e => setProfileUser(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Телефон</label>
                      <input className="settings-field-input" placeholder="+7 (999) 000-00-00" value={profilePhone} onChange={e => setProfilePhone(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Дата рождения</label>
                      <input className="settings-field-input" type="date" value={profileBirthday} onChange={e => setProfileBirthday(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Город</label>
                      <input className="settings-field-input" placeholder="Москва" value={profileCity} onChange={e => setProfileCity(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Пол</label>
                      <select className="settings-field-input" value={profileGender} onChange={e => setProfileGender(e.target.value)}>
                        <option value="">Не указан</option>
                        <option value="male">Мужской</option>
                        <option value="female">Женский</option>
                      </select>
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Место работы</label>
                      <input className="settings-field-input" placeholder="Компания" value={profileWorking} onChange={e => setProfileWorking(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Учебное заведение</label>
                      <input className="settings-field-input" placeholder="Университет" value={profileSchool} onChange={e => setProfileSchool(e.target.value)} />
                    </div>
                    <div className="settings-field">
                      <label className="settings-field-label">Сайт</label>
                      <input className="settings-field-input" placeholder="https://example.com" value={profileWebsite} onChange={e => setProfileWebsite(e.target.value)} />
                    </div>
                  </div>
                  <div className="settings-field" style={{marginTop:6}}>
                    <label className="settings-field-label">{t('settings.about')}</label>
                    <textarea className="settings-field-input settings-field-textarea" rows={3} value={profileAbout} onChange={e => setProfileAbout(e.target.value)} />
                  </div>
                  <button
                    className={profileSaving === 'done' ? 'btn-success' : profileSaving === 'error' ? 'btn-danger' : 'btn-primary'}
                    disabled={profileSaving === 'saving'}
                    onClick={handleSaveProfile}
                  >
                    {profileSaving === 'saving' ? t('settings.saving') : profileSaving === 'done' ? t('settings.saved') : profileSaving === 'error' ? t('settings.errorRetry') : t('settings.saveProfile')}
                  </button>
                </>
              )}

              {settingsTab === 'social' && (
                <>
                  <h2 className="settings-content-title">Социальные сети</h2>
                  <p style={{fontSize:13,color:'var(--text-2)',marginBottom:20}}>Добавьте ссылки на профили. Они будут видны в вашем профиле.</p>
                  {([
                    { icon: '🌐', label: 'Facebook',  val: profileFb,  set: setProfileFb,  ph: 'https://facebook.com/username' },
                    { icon: '🐦', label: 'Twitter/X', val: profileTw,  set: setProfileTw,  ph: 'https://x.com/username' },
                    { icon: '📸', label: 'Instagram', val: profileIg,  set: setProfileIg,  ph: 'https://instagram.com/username' },
                    { icon: '💼', label: 'LinkedIn',  val: profileLi,  set: setProfileLi,  ph: 'https://linkedin.com/in/username' },
                    { icon: '▶️', label: 'YouTube',   val: profileYt,  set: setProfileYt,  ph: 'https://youtube.com/@channel' },
                  ] as { icon:string; label:string; val:string; set:(v:string)=>void; ph:string }[]).map(s => (
                    <div key={s.label} className="settings-social-row">
                      <span className="settings-social-icon">{s.icon}</span>
                      <div className="settings-field" style={{flex:1,margin:0}}>
                        <label className="settings-field-label">{s.label}</label>
                        <input className="settings-field-input" placeholder={s.ph} value={s.val} onChange={e => s.set(e.target.value)} />
                      </div>
                    </div>
                  ))}
                  <button style={{marginTop:20}}
                    className={profileSaving === 'done' ? 'btn-success' : profileSaving === 'error' ? 'btn-danger' : 'btn-primary'}
                    disabled={profileSaving === 'saving'}
                    onClick={handleSaveSocialLinks}
                  >
                    {profileSaving === 'saving' ? t('settings.saving') : profileSaving === 'done' ? t('settings.saved') : profileSaving === 'error' ? t('settings.errorRetry') : 'Сохранить ссылки'}
                  </button>
                </>
              )}

              {settingsTab === 'notifications' && (
                <>
                  <h2 className="settings-content-title">Уведомления</h2>
                  {!notifLoaded ? (
                    <div className="settings-loading">{t('settings.loadingDots')}</div>
                  ) : notifSettings ? (
                    <>
                      {([
                        { field: 'email_notification',  label: 'Email-уведомления',        icon: '📧' },
                        { field: 'e_liked',             label: 'Лайки на мои посты',        icon: '❤️' },
                        { field: 'e_commented',         label: 'Комментарии',               icon: '💬' },
                        { field: 'e_followed',          label: 'Новые подписчики',           icon: '👤' },
                        { field: 'e_mentioned',         label: 'Упоминания',                icon: '📣' },
                        { field: 'e_joined_group',      label: 'Вступление в группу',       icon: '👥' },
                        { field: 'e_accepted',          label: 'Принятые запросы',           icon: '✅' },
                        { field: 'e_profile_wall_post', label: 'Записи на стене профиля',   icon: '📌' },
                        { field: 'e_shared',            label: 'Репосты',                   icon: '🔁' },
                        { field: 'e_visited',           label: 'Просмотры профиля',         icon: '👁️' },
                      ] as { field: keyof NotificationSettings; label: string; icon: string }[]).map(row => (
                        <div key={row.field} className="settings-notif-row">
                          <span className="settings-notif-icon">{row.icon}</span>
                          <span className="settings-notif-label">{row.label}</span>
                          <label className="tg-toggle">
                            <input type="checkbox"
                              checked={notifSettings[row.field] === 1}
                              onChange={e => setNotifSettings(p => p ? { ...p, [row.field]: e.target.checked ? 1 : 0 } : p)} />
                            <div className="tg-toggle-track" />
                            <div className="tg-toggle-thumb" />
                          </label>
                        </div>
                      ))}
                      <button style={{marginTop:20}}
                        className={notifSaving === 'done' ? 'btn-success' : notifSaving === 'error' ? 'btn-danger' : 'btn-primary'}
                        disabled={notifSaving === 'saving'}
                        onClick={handleSaveNotifSettings}
                      >
                        {notifSaving === 'saving' ? t('settings.saving') : notifSaving === 'done' ? t('settings.saved') : notifSaving === 'error' ? t('settings.errorRetry') : 'Сохранить'}
                      </button>
                    </>
                  ) : null}
                </>
              )}

              {settingsTab === 'privacy' && (
                <>
                  <h2 className="settings-content-title">{t('settings.privacy')}</h2>
                  {!privacyLoaded ? (
                    <div className="settings-loading">{t('settings.loadingDots')}</div>
                  ) : privacySettings ? (
                    <>
                      {([
                        { field: 'showlastseen',          labelKey: 'settings.showLastSeen',    options: [['1', t('settings.show')], ['0', t('settings.hide')]] },
                        { field: 'message_privacy',        labelKey: 'settings.messagePrivacy',  options: [['0', t('settings.everyone')], ['1', t('settings.following')], ['2', t('settings.nobody')]] },
                        { field: 'follow_privacy',         labelKey: 'settings.followPrivacy',   options: [['0', t('settings.everyone')], ['1', t('settings.onlyMe')]] },
                        { field: 'confirm_followers',      labelKey: 'settings.confirmFollowers',options: [['0', t('settings.no')], ['1', t('settings.yes')]] },
                        { field: 'friend_privacy',         labelKey: 'settings.friendPrivacy',   options: [['0', t('settings.everyone')], ['1', t('settings.following')], ['2', t('settings.onlyMe')]] },
                        { field: 'post_privacy',           labelKey: 'settings.postPrivacy',     options: [['0', t('settings.everyone')], ['1', t('settings.following')], ['2', t('settings.onlyMe')]] },
                        { field: 'show_activities_privacy',labelKey: 'settings.showActivities',  options: [['0', t('settings.show')], ['1', t('settings.hide')]] },
                        { field: 'birth_privacy',          labelKey: 'settings.birthPrivacy',    options: [['0', t('settings.show')], ['1', t('settings.hide')]] },
                        { field: 'visit_privacy',          labelKey: 'settings.visitPrivacy',    options: [['0', t('settings.show')], ['1', t('settings.hide')]] },
                      ] as { field: keyof PrivacySettings; labelKey: string; options: [string, string][] }[]).map(row => (
                        <div key={row.field} className="settings-privacy-row">
                          <span className="settings-privacy-label">{t(row.labelKey)}</span>
                          <select className="settings-privacy-select"
                            value={privacySettings[row.field]}
                            onChange={e => setPrivacySettings(p => p ? { ...p, [row.field]: e.target.value } : p)}>
                            {row.options.map(([val, lbl]) => <option key={val} value={val}>{lbl}</option>)}
                          </select>
                        </div>
                      ))}
                      <button style={{marginTop:16}}
                        className={privacySaving === 'done' ? 'btn-success' : privacySaving === 'error' ? 'btn-danger' : 'btn-primary'}
                        disabled={privacySaving === 'saving'}
                        onClick={handleSavePrivacy}
                      >
                        {privacySaving === 'saving' ? t('settings.saving') : privacySaving === 'done' ? t('settings.saved') : privacySaving === 'error' ? t('settings.errorRetry') : t('settings.saveProfile')}
                      </button>
                    </>
                  ) : null}
                </>
              )}

              {settingsTab === 'blocked' && (
                <>
                  <h2 className="settings-content-title">{t('settings.blockedUsers')}</h2>
                  {!blockedLoaded ? (
                    <div className="settings-loading">{t('settings.loadingDots')}</div>
                  ) : blockedUsers.length === 0 ? (
                    <div className="empty-state">{t('settings.noBlocked')}</div>
                  ) : (
                    blockedUsers.map(u => (
                      <div key={u.id} className="settings-blocked-row">
                        <Avatar name={u.first_name ?? u.username} src={u.avatar} size={36} />
                        <span className="settings-blocked-name">
                          {u.first_name ? `${u.first_name} ${u.last_name ?? ''}`.trim() : u.username}
                        </span>
                        <button className="btn-sm btn-outline" onClick={() => handleUnblock(u.id)}>
                          {t('settings.unblock')}
                        </button>
                      </div>
                    ))
                  )}
                </>
              )}

              {settingsTab === 'language' && (
                <>
                  <h2 className="settings-content-title">{t('settings.language')}</h2>
                  <div className="settings-lang-grid">
                    {(['ru', 'uk', 'en'] as Lang[]).map(l => (
                      <button key={l}
                        className={`settings-lang-btn ${lang === l ? 'active' : ''}`}
                        onClick={() => handleLangChange(l)}>
                        {l === 'ru' ? '🇷🇺 Русский' : l === 'uk' ? '🇺🇦 Українська' : '🇬🇧 English'}
                      </button>
                    ))}
                  </div>
                </>
              )}

              {settingsTab === 'security' && (
                <>
                  <h2 className="settings-content-title">{t('settings.security')}</h2>

                  {/* ── PIN lock ──────────────────────────────────────────── */}
                  <div className="settings-pin-card">
                    <div className="settings-pin-row">
                      <div>
                        <div style={{fontSize:15,fontWeight:600,marginBottom:4}}>{t('settings.pinLock')}</div>
                        <div className="settings-pin-hint">{t('settings.pinHint')}</div>
                      </div>
                      <span className={`settings-pin-status ${pinEnabled ? 'on' : 'off'}`}>
                        {pinEnabled ? t('settings.pinEnabled') : t('settings.pinDisabled')}
                      </span>
                    </div>
                    {pinError && <div className="pin-error" style={{marginBottom:8}}>{pinError}</div>}
                    {!showSetPin ? (
                      <div style={{display:'flex',gap:10,flexWrap:'wrap'}}>
                        {!pinEnabled && (
                          <button className="btn-primary" onClick={() => { setShowSetPin(true); setPinError(''); setNewPin1(''); setNewPin2(''); }}>
                            {t('settings.setPinCode')}
                          </button>
                        )}
                        {pinEnabled && (
                          <>
                            <button className="btn-secondary" onClick={() => { setShowSetPin(true); setPinError(''); setNewPin1(''); setNewPin2(''); }}>
                              {t('settings.changePinCode')}
                            </button>
                            <button className="btn-danger" onClick={disablePin}>
                              {t('settings.disablePinCode')}
                            </button>
                          </>
                        )}
                      </div>
                    ) : (
                      <form className="settings-pin-form" onSubmit={handleSetPin}>
                        <input
                          className="settings-pin-input"
                          type="password"
                          inputMode="numeric"
                          maxLength={8}
                          placeholder={t('pin.newPin')}
                          value={newPin1}
                          onChange={e => { setNewPin1(e.target.value.replace(/\D/g,'')); setPinError(''); }}
                        />
                        <input
                          className="settings-pin-input"
                          type="password"
                          inputMode="numeric"
                          maxLength={8}
                          placeholder={t('pin.confirmPin')}
                          value={newPin2}
                          onChange={e => { setNewPin2(e.target.value.replace(/\D/g,'')); setPinError(''); }}
                        />
                        <div style={{display:'flex',gap:10}}>
                          <button type="submit" className="btn-primary">{t('settings.saveProfile')}</button>
                          <button type="button" className="btn-secondary" onClick={() => { setShowSetPin(false); setPinError(''); }}>
                            {t('call.decline').replace('✕ ','')}
                          </button>
                        </div>
                      </form>
                    )}
                  </div>

                  {/* ── E2EE ─────────────────────────────────────────────── */}
                  <div className="settings-security-card">
                    <div style={{display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:10}}>
                      <span style={{fontSize:14}}>{t('settings.e2ee')}</span>
                      <span className="badge-green">{t('settings.signalBadge')}</span>
                    </div>
                    <div style={{display:'flex', alignItems:'center', justifyContent:'space-between', marginBottom:14}}>
                      <span style={{fontSize:14}}>{t('settings.keysReg')}</span>
                      <span className="badge-green">✓</span>
                    </div>
                    <p style={{fontSize:12, color:'var(--text-2)', marginBottom:14, lineHeight:1.5}}>{t('settings.e2eeHint')}</p>
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
                        } catch {
                          setSignalResetStatus('error');
                          setTimeout(() => setSignalResetStatus('idle'), 4000);
                        }
                      }}
                    >
                      {signalResetStatus === 'working' ? t('settings.resetting') : signalResetStatus === 'done' ? t('settings.keysReset') : signalResetStatus === 'error' ? t('settings.errorRetry') : t('settings.resetKeys')}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
          </div>
        ) : null}

        {/* ── Group chat ──────────────────────────────────────────────────── */}
        {section === 'groups' && selectedGroup ? (
          <>
            <div className="chat-header">
              <div className="chat-header-left">
                <button className="channel-header-clickable" onClick={() => setShowGroupInfo(v => !v)}>
                  <Avatar name={asText(selectedGroup.group_name, 'G')} src={selectedGroup.avatar} size={38} />
                  <div className="chat-header-info">
                    <span className="chat-header-name">{asText(selectedGroup.group_name, t('nav.groups'))}</span>
                    <span className="chat-header-status">{selectedGroup.members_count ?? 0} {t('sidebar.members')}</span>
                  </div>
                </button>
              </div>
              <div className="chat-header-actions">
                <button className="icon-btn" title={t('gallery.title')} onClick={() => openGallery('group')}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21 15 16 10 5 21"/>
                  </svg>
                </button>
                <button className={`icon-btn ${showGroupInfo ? 'active' : ''}`}
                  title={t('channel.info')} onClick={() => setShowGroupInfo(v => !v)}>ℹ️</button>
                {selectedGroup.is_admin && (
                  <button className="icon-btn admin-panel-btn" title={t('grAdmin.panelTitle')}
                    onClick={() => setShowGroupAdmin(true)}>⚙️</button>
                )}
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
                              ? <img src={absMediaUrl(msg.media)} alt="media" className="media-img" loading="lazy" onClick={() => setLightboxSrc(absMediaUrl(msg.media!))} />
                              : msg.media_type === 'video'
                                ? <VideoPlayer src={msg.media} className="media-video-player" />
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

            {/* ── Group info drawer ─────────────────────────────────────── */}
            {showGroupInfo && (
              <div className="channel-info-drawer">
                <div className="cid-hero">
                  <div className="cid-avatar-wrap">
                    <Avatar name={selectedGroup.group_name} src={selectedGroup.avatar} size={80} />
                  </div>
                  <h2 className="cid-name">{selectedGroup.group_name}</h2>
                  <div className="cid-stats-row">
                    <div className="cid-stat">
                      <span className="cid-stat-val">{(selectedGroup.members_count ?? 0).toLocaleString()}</span>
                      <span className="cid-stat-lbl">{t('sidebar.members')}</span>
                    </div>
                  </div>
                </div>
                {selectedGroup.is_admin && (
                  <div className="cid-section">
                    <button className="cid-btn cid-btn-manage" onClick={() => {
                      setShowGroupAdmin(true);
                      setShowGroupInfo(false);
                    }}>
                      ⚙️ {t('grAdmin.manageGroup')}
                    </button>
                  </div>
                )}
              </div>
            )}
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
                {/* Clickable channel info — opens right info drawer like Telegram */}
                <button className="channel-header-clickable" onClick={() => setShowChannelInfo(v => !v)}>
                  <Avatar name={asText(selectedChannel.name, 'C')} src={selectedChannel.avatar_url} size={38} />
                  <div className="chat-header-info">
                    <span className="chat-header-name">{asText(selectedChannel.name, t('nav.channels'))}</span>
                    <span className="chat-header-status">
                      {selectedChannel.subscribers_count ?? 0} {t('sidebar.subscribers')}
                    </span>
                  </div>
                </button>
              </div>
              <div className="chat-header-actions">
                {/* Search always visible */}
                <button className="icon-btn" title={t('gallery.title')} onClick={() => openGallery('channel')}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21 15 16 10 5 21"/>
                  </svg>
                </button>
                <button className="icon-btn" title={t('chat.search')} onClick={() => {}}>🔍</button>
                {/* Info toggle */}
                <button className={`icon-btn ${showChannelInfo ? 'active' : ''}`}
                  title={t('channel.info')} onClick={() => setShowChannelInfo(v => !v)}>ℹ️</button>
              </div>
            </div>
            {/* ── Channel info drawer (Telegram-style right panel) ───────── */}
            <div className={`channel-layout ${showChannelInfo ? 'with-info' : ''}`}>
              <div className="channel-feed-column">
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
                                    ? <img src={item.url} alt="" className="post-gallery-media" loading="lazy" onClick={() => setLightboxSrc(item.url)} />
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
              </div>{/* end channel-feed-column */}

              {/* ── Info drawer ────────────────────────────────────────────── */}
              {showChannelInfo && (
                <div className="channel-info-drawer">
                  {/* Avatar + name */}
                  <div className="cid-hero">
                    <div className="cid-avatar-wrap">
                      <Avatar name={selectedChannel.name} src={selectedChannel.avatar_url} size={80} />
                    </div>
                    <h2 className="cid-name">{selectedChannel.name}</h2>
                    {selectedChannel.username && (
                      <span className="cid-username">@{selectedChannel.username}</span>
                    )}
                    <div className="cid-stats-row">
                      <div className="cid-stat">
                        <span className="cid-stat-val">{(selectedChannel.subscribers_count ?? 0).toLocaleString()}</span>
                        <span className="cid-stat-lbl">{t('sidebar.subscribers')}</span>
                      </div>
                    </div>
                  </div>

                  {/* Description */}
                  {selectedChannel.description && (
                    <div className="cid-section">
                      <span className="cid-section-label">{t('chAdmin.description')}</span>
                      <p className="cid-description">{selectedChannel.description}</p>
                    </div>
                  )}

                  {/* Subscribe / Unsubscribe — only for non-owners */}
                  {!selectedChannel.is_owner && !selectedChannel.is_admin && (
                    <div className="cid-section">
                      {selectedChannel.is_subscribed ? (
                        <button className="cid-btn cid-btn-secondary" onClick={async () => {
                          if (!session) return;
                          await unsubscribeChannel(session.token, selectedChannel.id).catch(console.error);
                          setSelectedChannel(c => c ? { ...c, is_subscribed: false } : c);
                          setChannels(prev => prev.filter(c => c.id !== selectedChannel.id));
                        }}>
                          {t('channel.unsubscribe')}
                        </button>
                      ) : (
                        <button className="cid-btn cid-btn-primary" onClick={async () => {
                          if (!session) return;
                          await subscribeChannel(session.token, selectedChannel.id).catch(console.error);
                          setSelectedChannel(c => c ? { ...c, is_subscribed: true } : c);
                        }}>
                          {t('channel.subscribe')}
                        </button>
                      )}
                    </div>
                  )}

                  {/* Manage Channel — only for admins/owners */}
                  {(selectedChannel.is_owner || selectedChannel.is_admin) && (
                    <div className="cid-section">
                      <button className="cid-btn cid-btn-manage" onClick={() => {
                        setShowChannelAdmin(true);
                        setShowChannelInfo(false);
                      }}>
                        ⚙️ {t('chAdmin.manageChannel')}
                      </button>
                    </div>
                  )}

                  {/* Delete channel — only for owner */}
                  {selectedChannel.is_owner && (
                    <div className="cid-section">
                      <button className="cid-btn cid-btn-danger" onClick={async () => {
                        if (!session || !window.confirm(t('channel.deleteConfirm'))) return;
                        await deleteChannel(session.token, selectedChannel.id).catch(console.error);
                        setSelectedChannel(null);
                        const r = await loadChannels(session.token);
                        setChannels(r.data ?? []);
                        setShowChannelInfo(false);
                      }}>
                        🗑 {t('channel.delete')}
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>{/* end channel-layout */}
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
                <button className="icon-btn" title={t('gallery.title')} onClick={() => openGallery('chat')}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21 15 16 10 5 21"/>
                  </svg>
                </button>
                <button className="icon-btn" title={t('chat.search')} onClick={openChatSearch}>
                  🔍
                </button>
                <button className="icon-btn" title={t('call.voiceCall')} onClick={() => startCall('audio')}>
                  🎙
                </button>
                <button className="icon-btn" title={t('call.videoCall')} onClick={() => startCall('video')}>
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

            {/* ── Pinned message banner ─────────────────────────────── */}
            {pinnedMessage && (
              <div className="pinned-banner" onClick={() => {
                const el = document.getElementById(`msg-${pinnedMessage.id}`);
                el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
              }}>
                <span className="pinned-banner-icon">📌</span>
                <div className="pinned-banner-body">
                  <span className="pinned-banner-label">{t('pinned.banner')}</span>
                  <span className="pinned-banner-text">{pinnedMessage.text || t('bubble.media')}</span>
                </div>
                <button className="pinned-banner-close" title={t('pinned.unpin')}
                  onClick={e => { e.stopPropagation(); handlePin(pinnedMessage); }}>✕</button>
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
                      isSaved={savedSet.has(msg.id)}
                      onReply={handleReply}
                      onEdit={handleEditStart}
                      onDelete={handleDelete}
                      onReact={handleReact}
                      onOpenMedia={setLightboxSrc}
                      onSave={m => handleSaveMessage(m, selectedChat.name, isOwn ? (session.username ?? '') : selectedChat.name)}
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
              {/* ── Sticker / GIF picker popup ─────────────────────────── */}
              {showPicker && (
                <div className="sticker-gif-picker">
                  <div className="picker-tabs">
                    <button className={showPicker === 'sticker' ? 'tab active' : 'tab'} onClick={openStickerPicker}>{t('chat.stickers')}</button>
                    <button className={showPicker === 'gif' ? 'tab active' : 'tab'} onClick={openGifPicker}>GIF</button>
                    <button className="picker-close" onClick={() => setShowPicker(null)}>✕</button>
                  </div>

                  {showPicker === 'sticker' && (
                    <>
                      <div className="sticker-pack-tabs">
                        {stickerPacks.map(pack => (
                          <button key={pack.id}
                            className={`sticker-pack-tab ${activeStickerPack === pack.id ? 'active' : ''}`}
                            onClick={() => setActiveStickerPack(pack.id)}
                            title={pack.name}
                          >
                            {pack.icon_url ? <img src={pack.icon_url} alt={pack.name} width={24} height={24} /> : '🎭'}
                          </button>
                        ))}
                      </div>
                      <div className="sticker-grid">
                        {(stickerPacks.find(p => p.id === activeStickerPack)?.stickers ?? []).map(s => (
                          <button key={s.id} className="sticker-item" onClick={() => handleSendSticker(s.file_url)}>
                            <img src={s.thumbnail_url ?? s.file_url} alt={s.emoji ?? ''} />
                          </button>
                        ))}
                        {stickerPacks.length === 0 && stickerPacksLoaded && (
                          <div className="empty-state" style={{gridColumn:'1/-1'}}>{t('chat.noStickers')}</div>
                        )}
                      </div>
                    </>
                  )}

                  {showPicker === 'gif' && (
                    <>
                      <input className="gif-search-input" placeholder={t('chat.searchGif')}
                        value={gifQuery} onChange={e => handleGifSearch(e.target.value)} />
                      <div className="gif-grid">
                        {gifResults.map(g => (
                          <button key={g.id} className="gif-item" onClick={() => handleSendGif(g.url)}>
                            <img src={g.previewUrl} alt={g.title} loading="lazy" />
                          </button>
                        ))}
                        {gifResults.length === 0 && (
                          <div style={{gridColumn:'1/-1', color:'var(--text-2)', fontSize:13, padding:12, textAlign:'center'}}>
                            {gifQuery ? '…' : t('chat.gifTrending')}
                          </div>
                        )}
                      </div>
                      <div className="giphy-footer">Powered by GIPHY</div>
                    </>
                  )}
                </div>
              )}

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
                    accept="image/*,video/*,audio/*,.mp3,.ogg,.m4a,.aac,.opus,.flac,.wav,.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar"
                    onChange={e => setPendingMedia(e.target.files?.[0] ?? null)} />
                </label>

                {/* Emoji picker button */}
                <button
                  className={`icon-btn ${showEmojiComposer ? 'active' : ''}`}
                  title={t('chat.emoji')}
                  onClick={() => { setShowEmojiComposer(v => !v); setShowPicker(null); }}
                >😊</button>

                {/* Inline emoji picker panel */}
                {showEmojiComposer && (
                  <div className="emoji-composer-picker">
                    <div className="emoji-cats">
                      {EMOJI_CATEGORIES.map((cat, i) => (
                        <button key={i} className={`emoji-cat-btn ${emojiCatIdx === i ? 'active' : ''}`}
                          title={cat.label} onClick={() => setEmojiCatIdx(i)}>{cat.icon}</button>
                      ))}
                    </div>
                    <div className="emoji-compose-grid">
                      {EMOJI_CATEGORIES[emojiCatIdx].emojis.map((em, i) => (
                        <button key={i} className="emoji-compose-btn"
                          onClick={() => handleInsertEmoji(em)}>{em}</button>
                      ))}
                    </div>
                  </div>
                )}

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

                {/* Sticker / GIF buttons (hidden when text typed) */}
                {!newMessage.trim() && !pendingMedia && !editingMsg && (
                  <>
                    <button className="icon-btn" title={t('chat.stickerPicker')} onClick={() => { setShowEmojiComposer(false); showPicker === 'sticker' ? setShowPicker(null) : openStickerPicker(); }}>🎭</button>
                    <button className="icon-btn" title={t('chat.gifPicker')} onClick={() => { setShowEmojiComposer(false); showPicker === 'gif' ? setShowPicker(null) : openGifPicker(); }}>GIF</button>
                  </>
                )}

                {/* Scheduled message button */}
                <button
                  className="icon-btn"
                  title={t('scheduled.openList')}
                  onClick={() => newMessage.trim() ? setShowSchedulePicker(true) : handleOpenScheduledList()}
                >🕐</button>

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

              {/* ── Schedule picker modal ─────────────────────────────── */}
              {showSchedulePicker && (
                <div className="schedule-overlay" onClick={() => setShowSchedulePicker(false)}>
                  <div className="schedule-modal" onClick={e => e.stopPropagation()}>
                    <h3 className="schedule-modal-title">🕐 {t('scheduled.schedule')}</h3>
                    <p className="schedule-modal-preview">"{newMessage.trim().slice(0, 80)}{newMessage.trim().length > 80 ? '…' : ''}"</p>
                    <label className="schedule-field-label">{t('scheduled.pickDate')}</label>
                    <input
                      type="datetime-local"
                      className="schedule-datetime-input"
                      value={scheduleDateTime}
                      onChange={e => setScheduleDateTime(e.target.value)}
                      min={new Date(Date.now() + 60000).toISOString().slice(0, 16)}
                    />
                    <div className="schedule-modal-actions">
                      <button className="btn-secondary" onClick={() => setShowSchedulePicker(false)}>✕</button>
                      <button className="btn-primary" disabled={!scheduleDateTime} onClick={handleScheduleMessage}>
                        {t('scheduled.confirm')}
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* ── Scheduled messages list ────────────────────────────── */}
              {showScheduledList && (
                <div className="schedule-overlay" onClick={() => setShowScheduledList(false)}>
                  <div className="schedule-modal" onClick={e => e.stopPropagation()}>
                    <div className="schedule-list-header">
                      <h3 className="schedule-modal-title">🕐 {t('scheduled.title')}</h3>
                      <button className="icon-btn" onClick={() => setShowScheduledList(false)}>✕</button>
                    </div>
                    {scheduledMessages.length === 0 ? (
                      <p className="schedule-empty">{t('scheduled.noMessages')}</p>
                    ) : (
                      <div className="schedule-list">
                        {scheduledMessages.map(msg => (
                          <div key={msg.id} className="schedule-item">
                            <div className="schedule-item-body">
                              <p className="schedule-item-text">{msg.text}</p>
                              <time className="schedule-item-time">
                                {new Date(msg.send_at * 1000).toLocaleString()}
                              </time>
                            </div>
                            <div className="schedule-item-actions">
                              <button className="btn-xs btn-primary" onClick={() => handleSendScheduledNow(msg.id)}>
                                {t('scheduled.sendNow')}
                              </button>
                              <button className="btn-xs btn-danger" onClick={() => handleDeleteScheduled(msg.id)}>
                                {t('scheduled.delete')}
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </main>

      {/* ── Channel Admin Panel ─────────────────���──────────────────────── */}
      {showChannelAdmin && selectedChannel && session && (
        <ChannelAdminPanel
          channel={selectedChannel}
          session={session}
          onClose={() => setShowChannelAdmin(false)}
          onChannelUpdated={async () => {
            const r = await loadChannels(session.token);
            setChannels(r.data ?? []);
          }}
        />
      )}

      {/* ── Group Admin Panel ────────────────────────���──────────────────── */}
      {showGroupAdmin && selectedGroup && session && (
        <GroupAdminPanel
          group={selectedGroup}
          session={session}
          onClose={() => setShowGroupAdmin(false)}
          onGroupUpdated={async () => {
            const r = await loadGroups(session.token);
            setGroups(r.data ?? []);
          }}
        />
      )}

      {/* ── Media gallery overlay ─────────────────────────────────────────── */}
      {showGallery && (
        <MediaGallery
          items={galleryItems}
          loading={galleryLoading}
          title={galleryTitle}
          onClose={() => setShowGallery(false)}
        />
      )}
    </div>
  );
}
