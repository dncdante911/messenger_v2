import React, { useMemo, useState } from 'react';
import { t } from './i18n';
import type { ChatItem, GroupItem, MessageItem } from './types';

interface Props {
  msg:      MessageItem;
  chats:    ChatItem[];
  groups:   GroupItem[];
  onSend:   (type: 'chat' | 'group', id: number, name: string) => void;
  onClose:  () => void;
}

export default function ForwardModal({ msg, chats, groups, onSend, onClose }: Props) {
  const [query, setQuery] = useState('');
  const [sent,  setSent]  = useState<number | null>(null);

  const q = query.trim().toLowerCase();

  const filteredChats = useMemo(
    () => chats.filter(c => c.name.toLowerCase().includes(q)),
    [chats, q]
  );
  const filteredGroups = useMemo(
    () => groups.filter(g => g.group_name.toLowerCase().includes(q)),
    [groups, q]
  );

  const preview = msg.text
    ? msg.text.slice(0, 80)
    : msg.media
      ? `📎 ${msg.media_filename ?? t('misc.downloadFile')}`
      : '';

  const handle = (type: 'chat' | 'group', id: number, name: string) => {
    setSent(id);
    onSend(type, id, name);
    setTimeout(onClose, 700);
  };

  return (
    <div className="fw-overlay" onClick={onClose}>
      <div className="fw-modal" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="fw-header">
          <span className="fw-title">{t('forward.title')}</span>
          <button className="fw-close" onClick={onClose}>✕</button>
        </div>

        {/* Preview */}
        <div className="fw-preview">
          <div className="fw-preview-bar" />
          <p className="fw-preview-text">{preview || '—'}</p>
        </div>

        {/* Search */}
        <div className="fw-search-wrap">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ position:'absolute', left:14, top:'50%', transform:'translateY(-50%)', color:'var(--text-secondary)' }}>
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            className="fw-search"
            placeholder={t('forward.search')}
            value={query}
            onChange={e => setQuery(e.target.value)}
            autoFocus
          />
        </div>

        {/* List */}
        <div className="fw-list">
          {filteredChats.length > 0 && (
            <>
              <div className="fw-list-label">{t('sidebar.messages')}</div>
              {filteredChats.map(c => (
                <button
                  key={c.user_id}
                  className={`fw-item ${sent === c.user_id ? 'fw-item--sent' : ''}`}
                  onClick={() => handle('chat', c.user_id, c.name)}
                  disabled={sent !== null}
                >
                  <div className="fw-item-avatar">{c.name[0]?.toUpperCase()}</div>
                  <span className="fw-item-name">{c.name}</span>
                  {sent === c.user_id && <span className="fw-item-check">✓</span>}
                </button>
              ))}
            </>
          )}

          {filteredGroups.length > 0 && (
            <>
              <div className="fw-list-label">{t('sidebar.groups')}</div>
              {filteredGroups.map(g => (
                <button
                  key={g.id}
                  className={`fw-item ${sent === g.id ? 'fw-item--sent' : ''}`}
                  onClick={() => handle('group', g.id, g.group_name)}
                  disabled={sent !== null}
                >
                  <div className="fw-item-avatar fw-item-avatar--group">👥</div>
                  <span className="fw-item-name">{g.group_name}</span>
                  {sent === g.id && <span className="fw-item-check">✓</span>}
                </button>
              ))}
            </>
          )}

          {filteredChats.length === 0 && filteredGroups.length === 0 && (
            <div className="fw-empty">{t('forward.notFound')}</div>
          )}
        </div>
      </div>
    </div>
  );
}
