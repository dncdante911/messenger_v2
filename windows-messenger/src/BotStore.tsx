import React, { useCallback, useEffect, useRef, useState } from 'react';
import { t } from './i18n';
import { searchBots, getBotLinkedUser } from './api';
import type { BotItem } from './types';
import type { Session } from './types';

interface Props {
  session: Session;
  onOpenChat: (userId: number, name: string, avatar?: string) => void;
}

type Category = { id: string; label: string; icon: string; query: string };

const CATEGORIES: Category[] = [
  { id: 'all',           label: 'bot.catAll',        icon: '🌐', query: '' },
  { id: 'utility',       label: 'bot.catUtility',    icon: '🔧', query: 'utility tool helper' },
  { id: 'news',          label: 'bot.catNews',        icon: '📰', query: 'news' },
  { id: 'entertainment', label: 'bot.catEntertain',   icon: '🎮', query: 'game fun entertainment' },
  { id: 'education',     label: 'bot.catEducation',   icon: '📚', query: 'learn study education' },
  { id: 'productivity',  label: 'bot.catProductivity',icon: '⚡', query: 'productivity work' },
  { id: 'shopping',      label: 'bot.catShopping',    icon: '🛍️', query: 'shop store buy' },
  { id: 'ai',            label: 'bot.catAi',          icon: '🤖', query: 'ai gpt assistant smart' },
];

function BotCard({
  bot,
  onLaunch,
  onMiniApp,
  loading,
}: {
  bot: BotItem;
  onLaunch: (bot: BotItem) => void;
  onMiniApp: (url: string) => void;
  loading: boolean;
}) {
  const initials = (bot.display_name || bot.username || '?')[0].toUpperCase();
  return (
    <div className="bs-card">
      <div className="bs-card__avatar">
        {bot.avatar
          ? <img src={bot.avatar} alt="" className="bs-card__avatar-img" />
          : <div className="bs-card__avatar-placeholder">{initials}</div>
        }
        {bot.web_app_url && <div className="bs-card__badge">App</div>}
      </div>
      <div className="bs-card__body">
        <div className="bs-card__name">{bot.display_name || bot.username}</div>
        <div className="bs-card__username">@{bot.username}</div>
        {bot.description && (
          <div className="bs-card__desc">{bot.description}</div>
        )}
      </div>
      <div className="bs-card__actions">
        {bot.web_app_url && (
          <button
            className="bs-btn bs-btn--app"
            onClick={() => onMiniApp(bot.web_app_url!)}
            title={t('bot.openApp')}
          >
            🌐
          </button>
        )}
        <button
          className="bs-btn bs-btn--launch"
          onClick={() => onLaunch(bot)}
          disabled={loading}
        >
          {loading ? '…' : t('bot.launch')}
        </button>
      </div>
    </div>
  );
}

export default function BotStore({ session, onOpenChat }: Props) {
  const [query,      setQuery]      = useState('');
  const [category,   setCategory]   = useState('all');
  const [bots,       setBots]       = useState<BotItem[]>([]);
  const [loading,    setLoading]    = useState(false);
  const [launching,  setLaunching]  = useState<string | null>(null);
  const [featured,   setFeatured]   = useState<BotItem[]>([]);
  const debounceRef  = useRef<ReturnType<typeof setTimeout> | null>(null);

  const doSearch = useCallback(async (q: string) => {
    setLoading(true);
    try {
      const results = await searchBots(q || ' ', 50);
      setBots(results);
    } catch { setBots([]); }
    setLoading(false);
  }, []);

  // Load featured on mount
  useEffect(() => {
    (async () => {
      try {
        const results = await searchBots(' ', 12);
        setFeatured(results.slice(0, 8));
        setBots(results);
      } catch { /* ignore */ }
    })();
  }, []);

  const handleSearch = (val: string) => {
    setQuery(val);
    setCategory('all');
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(val), 350);
  };

  const handleCategory = (cat: Category) => {
    setCategory(cat.id);
    setQuery('');
    doSearch(cat.query || ' ');
  };

  const handleLaunch = async (bot: BotItem) => {
    setLaunching(bot.bot_id_str);
    try {
      let uid = bot.user_id;
      if (!uid) uid = await getBotLinkedUser(session.token, bot.bot_id_str);
      if (uid) onOpenChat(uid, bot.display_name || bot.username, bot.avatar);
    } catch { /* ignore */ }
    setLaunching(null);
  };

  const handleMiniApp = (url: string) => {
    window.open(url, '_blank', 'noopener');
  };

  const showFeatured = !query && category === 'all' && featured.length > 0;

  return (
    <div className="bs-root">
      {/* Top search bar */}
      <div className="bs-topbar">
        <div className="bs-search-wrap">
          <svg className="bs-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            className="bs-search"
            placeholder={t('bot.searchPlaceholder')}
            value={query}
            onChange={e => handleSearch(e.target.value)}
          />
          {query && (
            <button className="bs-search-clear" onClick={() => { setQuery(''); doSearch(''); }}>✕</button>
          )}
        </div>
      </div>

      {/* Category chips */}
      <div className="bs-cats">
        {CATEGORIES.map(cat => (
          <button
            key={cat.id}
            className={`bs-cat ${category === cat.id ? 'bs-cat--on' : ''}`}
            onClick={() => handleCategory(cat)}
          >
            <span className="bs-cat__icon">{cat.icon}</span>
            <span>{t(cat.label)}</span>
          </button>
        ))}
      </div>

      <div className="bs-content">
        {/* Featured section */}
        {showFeatured && (
          <section className="bs-section">
            <h3 className="bs-section__title">
              <span className="bs-section__icon">⭐</span>
              {t('bot.featured')}
            </h3>
            <div className="bs-featured-row">
              {featured.map(bot => (
                <div key={bot.bot_id_str} className="bs-featured-card" onClick={() => handleLaunch(bot)}>
                  <div className="bs-featured-card__avatar">
                    {bot.avatar
                      ? <img src={bot.avatar} alt="" />
                      : <div className="bs-featured-card__placeholder">{(bot.display_name || bot.username || '?')[0].toUpperCase()}</div>
                    }
                  </div>
                  <div className="bs-featured-card__name">{bot.display_name || bot.username}</div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* All bots grid */}
        <section className="bs-section">
          {!showFeatured && (
            <h3 className="bs-section__title">
              <span className="bs-section__icon">
                {CATEGORIES.find(c => c.id === category)?.icon ?? '🤖'}
              </span>
              {query
                ? `${t('bot.resultsFor')} "${query}"`
                : t(CATEGORIES.find(c => c.id === category)?.label ?? 'bot.catAll')}
            </h3>
          )}
          {loading ? (
            <div className="bs-loading">
              <div className="spinner" />
              <span>{t('bot.searching')}</span>
            </div>
          ) : bots.length === 0 ? (
            <div className="bs-empty">
              <div className="bs-empty__icon">🤖</div>
              <div>{t('bot.notFound')}</div>
            </div>
          ) : (
            <div className="bs-grid">
              {bots.map(bot => (
                <BotCard
                  key={bot.bot_id_str}
                  bot={bot}
                  onLaunch={handleLaunch}
                  onMiniApp={handleMiniApp}
                  loading={launching === bot.bot_id_str}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
