import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { Socket } from 'socket.io-client';
import {
  createChannel,
  createGroup,
  createStory,
  getIceServers,
  loadChannels,
  loadChats,
  loadGroups,
  loadMessages,
  loadStories,
  login,
  loginByPhone,
  registerAccount,
  sendMessage,
  sendMessageWithMedia
} from './api';
import { encryptAesGcm, tryDecryptAesGcm } from './crypto';
import { createChatSocket } from './socket';
import type { ChannelItem, ChatItem, GroupItem, MessageItem, StoryItem } from './types';
import { createLocalVideoStream, createPeerConnection } from './webrtc';

type Session = { token: string; userId: number; username: string };
type AuthMode = 'login' | 'register';
type LoginMethod = 'username' | 'phone';
type Section = 'chats' | 'groups' | 'channels' | 'stories' | 'calls';

const sessionKey = 'wm_windows_session';
const aesSeed = 'worldmates-aes-256-gcm-desktop';


function asText(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (typeof value === 'object') {
    const candidate = (value as Record<string, unknown>).text;
    if (typeof candidate === 'string') return candidate;
    return fallback;
  }
  return fallback;
}


export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [loginMethod, setLoginMethod] = useState<LoginMethod>('username');
  const [activeSection, setActiveSection] = useState<Section>('chats');

  const [status, setStatus] = useState('Offline');
  const [error, setError] = useState('');
  const [socket, setSocket] = useState<Socket | null>(null);

  const [username, setUsername] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const [chats, setChats] = useState<ChatItem[]>([]);
  const [groups, setGroups] = useState<GroupItem[]>([]);
  const [channels, setChannels] = useState<ChannelItem[]>([]);
  const [stories, setStories] = useState<StoryItem[]>([]);

  const [selectedChatId, setSelectedChatId] = useState<number | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [encryptMessages, setEncryptMessages] = useState(true);

  const [pendingMedia, setPendingMedia] = useState<File | null>(null);
  const [newGroupName, setNewGroupName] = useState('');
  const [newChannelName, setNewChannelName] = useState('');
  const [newChannelDescription, setNewChannelDescription] = useState('');
  const [newStory, setNewStory] = useState<File | null>(null);
  const [callInfo, setCallInfo] = useState('Idle');

  useEffect(() => {
    const raw = localStorage.getItem(sessionKey);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw) as Session;
      if (parsed.token) setSession(parsed);
    } catch {
      localStorage.removeItem(sessionKey);
    }
  }, []);

  const selectedChat = useMemo(
    () => chats.find((chat) => chat.user_id === selectedChatId) ?? null,
    [chats, selectedChatId]
  );

  async function handleAuth(event: FormEvent) {
    event.preventDefault();
    setError('');

    try {
      if (authMode === 'register') {
        const registered = await registerAccount({ username, email, phoneNumber: phone, password });
        if (registered.api_status === '200' && registered.access_token && registered.user_id) {
          const next = { token: registered.access_token, userId: registered.user_id, username };
          localStorage.setItem(sessionKey, JSON.stringify(next));
          setSession(next);
          return;
        }
        setError(registered.message ?? 'Registration failed.');
        return;
      }

      const loggedIn =
        loginMethod === 'username' ? await login(username.trim(), password) : await loginByPhone(phone.trim(), password);

      if (loggedIn.api_status === '200' && loggedIn.access_token && loggedIn.user_id) {
        const next = {
          token: loggedIn.access_token,
          userId: loggedIn.user_id,
          username: loginMethod === 'username' ? username.trim() : phone.trim()
        };
        localStorage.setItem(sessionKey, JSON.stringify(next));
        setSession(next);
        return;
      }

      setError(loggedIn.message ?? 'Auth failed.');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown auth error';
      setError(`Auth failed: ${message}`);
    }
  }

  function logout() {
    localStorage.removeItem(sessionKey);
    setSession(null);
    setMessages([]);
    setChats([]);
    setGroups([]);
    setChannels([]);
    setStories([]);
    setSelectedChatId(null);
    socket?.disconnect();
    setSocket(null);
  }

  useEffect(() => {
    if (!session) return;

    Promise.all([
      loadChats(session.token, session.userId).then((response) => {
        const nextChats = response.data ?? [];
        setChats(nextChats);
        if (nextChats.length > 0) {
          setSelectedChatId((current) => current ?? nextChats[0].user_id ?? null);
        }
      }),
      loadGroups(session.token).then((response) => setGroups(response.data ?? [])),
      loadChannels(session.token).then((response) => setChannels(response.data ?? [])),
      loadStories(session.token).then((response) => setStories(response.stories ?? response.data ?? []))
    ]).catch(() => setError('Failed loading initial data from API.'));
  }, [session]);

  useEffect(() => {
    if (!session) return;

    const liveSocket = createChatSocket(session.token, {
      onStatus: setStatus,
      onMessage: async (message) => {
        const key = `${aesSeed}:${session.userId}:${selectedChatId ?? 0}`;
        const text = await tryDecryptAesGcm(message.text, key);
        if (message.from_id === selectedChatId || message.to_id === selectedChatId) {
          setMessages((current) => [...current, { ...message, text }]);
        }
      }
    });

    setSocket(liveSocket);
    return () => liveSocket.disconnect();
  }, [session, selectedChatId]);

  useEffect(() => {
    if (!session || !selectedChatId) return;

    loadMessages(session.token, selectedChatId, session.userId)
      .then(async (response) => {
        const key = `${aesSeed}:${session.userId}:${selectedChatId}`;
        const decrypted = await Promise.all(
          (response.messages ?? []).map(async (message) => ({
            ...message,
            text: await tryDecryptAesGcm(message.text, key)
          }))
        );
        setMessages(decrypted);
      })
      .catch(() => setError('Failed to load messages.'));
  }, [session, selectedChatId]);

  async function handleSendMessage(event: FormEvent) {
    event.preventDefault();
    if (!session || !selectedChatId || (!newMessage.trim() && !pendingMedia)) return;

    try {
      const key = `${aesSeed}:${session.userId}:${selectedChatId}`;
      const textRaw = newMessage.trim();
      const textPayload = textRaw ? (encryptMessages ? await encryptAesGcm(textRaw, key) : textRaw) : '';

      if (pendingMedia) {
        await sendMessageWithMedia(session.token, selectedChatId, textPayload, pendingMedia, session.userId);
      } else {
        await sendMessage(session.token, selectedChatId, textPayload, session.userId);
      }

      setMessages((current) => [
        ...current,
        {
          id: Date.now(),
          from_id: session.userId,
          to_id: selectedChatId,
          text: textRaw || `[Media] ${pendingMedia?.name ?? ''}`,
          time_text: 'now'
        }
      ]);

      setNewMessage('');
      setPendingMedia(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown send error';
      setError(`Failed to send message: ${message}`);
    }
  }

  async function handleCreateGroup(event: FormEvent) {
    event.preventDefault();
    if (!session || !newGroupName.trim()) return;
    await createGroup(session.token, newGroupName.trim());
    const refreshed = await loadGroups(session.token);
    setGroups(refreshed.data ?? []);
    setNewGroupName('');
  }

  async function handleCreateChannel(event: FormEvent) {
    event.preventDefault();
    if (!session || !newChannelName.trim()) return;
    await createChannel(session.token, newChannelName.trim(), newChannelDescription.trim());
    const refreshed = await loadChannels(session.token);
    setChannels(refreshed.data ?? []);
    setNewChannelName('');
    setNewChannelDescription('');
  }

  async function handleCreateStory(event: FormEvent) {
    event.preventDefault();
    if (!session || !newStory) return;
    const type = newStory.type.startsWith('video/') ? 'video' : 'image';
    await createStory(session.token, newStory, type);
    const refreshed = await loadStories(session.token);
    setStories(refreshed.stories ?? refreshed.data ?? []);
    setNewStory(null);
  }

  async function startVideoCall() {
    if (!session || !selectedChatId) return;
    setCallInfo('Preparing video call...');

    try {
      const servers = await getIceServers(session.userId);
      const peer = await createPeerConnection(servers);
      const stream = await createLocalVideoStream();
      stream.getTracks().forEach((track) => peer.addTrack(track, stream));
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);

      socket?.emit('call_signal', {
        type: 'offer',
        to: selectedChatId,
        from: session.userId,
        sdp: offer
      });

      setCallInfo('Offer sent. Waiting for answer...');
    } catch {
      setCallInfo('Call failed to initialize');
    }
  }

  if (!session) {
    return (
      <div className="auth-page">
        <form className="auth-card" onSubmit={handleAuth}>
          <h1>WorldMates Desktop</h1>
          <p>Login / Register with Android-compatible API.</p>

          <div className="switch-row">
            <button type="button" className={authMode === 'login' ? 'tab active' : 'tab'} onClick={() => setAuthMode('login')}>
              Login
            </button>
            <button
              type="button"
              className={authMode === 'register' ? 'tab active' : 'tab'}
              onClick={() => setAuthMode('register')}
            >
              Register
            </button>
          </div>

          {authMode === 'login' ? (
            <div className="switch-row">
              <button
                type="button"
                className={loginMethod === 'username' ? 'tab active' : 'tab'}
                onClick={() => setLoginMethod('username')}
              >
                By username
              </button>
              <button
                type="button"
                className={loginMethod === 'phone' ? 'tab active' : 'tab'}
                onClick={() => setLoginMethod('phone')}
              >
                By phone
              </button>
            </div>
          ) : null}

          {authMode === 'register' || loginMethod === 'username' ? (
            <label>
              Username
              <input value={username} onChange={(event) => setUsername(event.target.value)} required={authMode === 'register' || loginMethod === 'username'} />
            </label>
          ) : null}

          {authMode === 'register' || loginMethod === 'phone' ? (
            <label>
              Phone
              <input value={phone} onChange={(event) => setPhone(event.target.value)} required={authMode === 'register' || loginMethod === 'phone'} />
            </label>
          ) : null}

          {authMode === 'register' ? (
            <label>
              Email
              <input value={email} onChange={(event) => setEmail(event.target.value)} />
            </label>
          ) : null}

          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>

          {error ? <div className="error">{error}</div> : null}
          <button type="submit">{authMode === 'register' ? 'Create account' : 'Sign in'}</button>
        </form>
      </div>
    );
  }

  return (
    <div className="layout">
      <nav className="rail">
        <button className={activeSection === 'chats' ? 'rail-btn active' : 'rail-btn'} onClick={() => setActiveSection('chats')}>💬</button>
        <button className={activeSection === 'groups' ? 'rail-btn active' : 'rail-btn'} onClick={() => setActiveSection('groups')}>👥</button>
        <button className={activeSection === 'channels' ? 'rail-btn active' : 'rail-btn'} onClick={() => setActiveSection('channels')}>📢</button>
        <button className={activeSection === 'stories' ? 'rail-btn active' : 'rail-btn'} onClick={() => setActiveSection('stories')}>📱</button>
        <button className={activeSection === 'calls' ? 'rail-btn active' : 'rail-btn'} onClick={() => setActiveSection('calls')}>📞</button>
      </nav>

      <aside className="sidebar">
        <header className="sidebar-header">
          <div>
            <h2>WorldMates</h2>
            <small>{session.username}</small>
          </div>
          <button className="logout-btn" onClick={logout}>Exit</button>
        </header>
        <div className="status">{status}</div>

        {activeSection === 'chats' ? (
          <div className="chat-list">
            {chats.map((chat, index) => (
              <button
                key={`${chat.user_id}-${index}`}
                className={chat.user_id === selectedChatId ? 'chat-item active' : 'chat-item'}
                onClick={() => setSelectedChatId(chat.user_id)}
              >
                <strong>{chat.name}</strong>
                <span>{asText(chat.last_message, 'No messages yet')}</span>
              </button>
            ))}
          </div>
        ) : null}

        {activeSection === 'groups' ? (
          <>
            <form className="mini-form" onSubmit={handleCreateGroup}>
              <input value={newGroupName} onChange={(event) => setNewGroupName(event.target.value)} placeholder="New group" />
              <button type="submit">Create</button>
            </form>
            <div className="list-panel">
              {groups.length === 0 ? <div className="list-item">No groups found</div> : null}
              {groups.map((group, index) => <div key={`${group.id}-${index}`} className="list-item">{asText(group.group_name, 'Group')}</div>)}
            </div>
          </>
        ) : null}

        {activeSection === 'channels' ? (
          <>
            <form className="mini-form" onSubmit={handleCreateChannel}>
              <input value={newChannelName} onChange={(event) => setNewChannelName(event.target.value)} placeholder="Channel name" />
              <input value={newChannelDescription} onChange={(event) => setNewChannelDescription(event.target.value)} placeholder="Description" />
              <button type="submit">Create</button>
            </form>
            <div className="list-panel">
              {channels.length === 0 ? <div className="list-item">No channels found</div> : null}
              {channels.map((channel, index) => <div key={`${channel.id}-${index}`} className="list-item">{asText(channel.name, 'Channel')}</div>)}
            </div>
          </>
        ) : null}

        {activeSection === 'stories' ? (
          <>
            <form className="mini-form" onSubmit={handleCreateStory}>
              <input type="file" accept="image/*,video/*" onChange={(event) => setNewStory(event.target.files?.[0] ?? null)} />
              <button type="submit">Upload story</button>
            </form>
            <div className="list-panel">{stories.map((story, index) => <div key={`${story.id}-${index}`} className="list-item">Story #{story.id}</div>)}</div>
          </>
        ) : null}

        {activeSection === 'calls' ? (
          <div className="list-panel">
            <button className="call-btn" onClick={startVideoCall}>Start WebRTC video call</button>
            <p>{callInfo}</p>
          </div>
        ) : null}
      </aside>

      <main className="chat-view">
        <header className="chat-header">
          <h3>{selectedChat?.name ?? 'Select chat'}</h3>
          <p>AES-256-GCM: {encryptMessages ? 'enabled' : 'disabled'}</p>
        </header>

        <section className="messages">
          {messages.map((message, index) => (
            <article key={`${message.id}-${index}`} className={message.from_id === session.userId ? 'bubble own' : 'bubble'}>
              <p>{asText(message.text, '')}</p>
              {message.media ? (
                <a href={message.media} target="_blank" rel="noreferrer">
                  Download media
                </a>
              ) : null}
              <time>{message.time_text ?? ''}</time>
            </article>
          ))}
        </section>

        <form className="composer" onSubmit={handleSendMessage}>
          <label className="check-row">
            <input
              type="checkbox"
              checked={encryptMessages}
              onChange={(event) => setEncryptMessages(event.target.checked)}
            />
            AES-256-GCM
          </label>
          <input value={newMessage} onChange={(event) => setNewMessage(event.target.value)} placeholder="Message" />
          <input
            type="file"
            accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx"
            onChange={(event) => setPendingMedia(event.target.files?.[0] ?? null)}
          />
          <button type="submit">Send</button>
        </form>
      </main>
    </div>
  );
}
