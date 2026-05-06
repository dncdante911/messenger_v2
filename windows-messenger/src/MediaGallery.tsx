import React, { useCallback, useEffect, useRef, useState } from 'react';
import VideoPlayer from './VideoPlayer';
import { t } from './i18n';

export type MediaItem = {
  url:       string;
  type:      'image' | 'video' | 'gif' | 'music' | 'voice';
  thumb?:    string;
  caption?:  string;
  date?:     string;
  sender?:   string;
  filename?: string;
};

type FilterTab = 'all' | 'photo' | 'video' | 'music' | 'voice';

interface Props {
  items:    MediaItem[];
  onClose:  () => void;
  title?:   string;
  loading?: boolean;
}

function AudioCard({ item, autoFocus }: { item: MediaItem; autoFocus?: boolean }) {
  const ref = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);

  const fmtTime = (s: number) => {
    if (!isFinite(s)) return '0:00';
    return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
  };

  const toggle = () => {
    const a = ref.current;
    if (!a) return;
    if (a.paused) { a.play(); setPlaying(true); }
    else { a.pause(); setPlaying(false); }
  };

  const seek = (e: React.MouseEvent<HTMLDivElement>) => {
    const a = ref.current;
    if (!a || !a.duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    a.currentTime = ((e.clientX - rect.left) / rect.width) * a.duration;
  };

  const isVoice = item.type === 'voice';
  const name = item.filename ?? item.url.split('/').pop() ?? 'audio';

  return (
    <div className={`mg-audio-card ${isVoice ? 'mg-audio-card--voice' : 'mg-audio-card--music'}`}>
      <audio
        ref={ref}
        src={item.url}
        onTimeUpdate={() => { const a = ref.current; if (a && a.duration) setProgress(a.currentTime / a.duration); }}
        onLoadedMetadata={() => setDuration(ref.current?.duration ?? 0)}
        onEnded={() => { setPlaying(false); setProgress(0); }}
      />

      {/* Icon */}
      <button className="mg-audio-play" onClick={toggle}>
        {playing
          ? <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="3" y="2" width="5" height="16" rx="1.5"/><rect x="12" y="2" width="5" height="16" rx="1.5"/></svg>
          : <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><polygon points="3,2 17,10 3,18"/></svg>
        }
      </button>

      <div className="mg-audio-body">
        <div className="mg-audio-name">{name}</div>
        {/* Waveform-style progress bar */}
        <div className="mg-audio-bar-wrap" onClick={seek}>
          <div className="mg-audio-bar-bg" />
          <div className="mg-audio-bar-fill" style={{ width: `${progress * 100}%` }} />
        </div>
        <div className="mg-audio-meta-row">
          <span className="mg-audio-time">{fmtTime(ref.current?.currentTime ?? 0)} / {fmtTime(duration)}</span>
          {item.date && <span className="mg-audio-date">{item.date}</span>}
        </div>
      </div>

      <a className="mg-audio-dl" href={item.url} download={name} title={t('gallery.download')} onClick={e => e.stopPropagation()}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
      </a>
    </div>
  );
}

export default function MediaGallery({ items, onClose, title, loading }: Props) {
  const [lightbox, setLightbox] = useState<number | null>(null);
  const [filter,   setFilter]   = useState<FilterTab>('all');

  const isAudio = (t: MediaItem['type']) => t === 'music' || t === 'voice';

  const filtered = items.filter(it => {
    if (filter === 'photo') return it.type === 'image' || it.type === 'gif';
    if (filter === 'video') return it.type === 'video';
    if (filter === 'music') return it.type === 'music';
    if (filter === 'voice') return it.type === 'voice';
    return true;
  });

  // lightbox only for visual items
  const visualFiltered = filtered.filter(it => !isAudio(it.type));
  const audioFiltered  = filtered.filter(it => isAudio(it.type));

  const close = () => setLightbox(null);

  const prev = useCallback(() => {
    setLightbox(i => i === null ? null : (i > 0 ? i - 1 : visualFiltered.length - 1));
  }, [visualFiltered.length]);

  const next = useCallback(() => {
    setLightbox(i => i === null ? null : (i < visualFiltered.length - 1 ? i + 1 : 0));
  }, [visualFiltered.length]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (lightbox === null) { if (e.key === 'Escape') onClose(); return; }
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowLeft') prev();
      if (e.key === 'ArrowRight') next();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [lightbox, onClose, prev, next]);

  const downloadItem = (item: MediaItem) => {
    const a = document.createElement('a');
    a.href = item.url;
    a.download = item.filename ?? item.url.split('/').pop() ?? 'media';
    a.click();
  };

  const counts: Record<FilterTab, number> = {
    all:   items.length,
    photo: items.filter(i => i.type === 'image' || i.type === 'gif').length,
    video: items.filter(i => i.type === 'video').length,
    music: items.filter(i => i.type === 'music').length,
    voice: items.filter(i => i.type === 'voice').length,
  };

  const FILTERS: { key: FilterTab; icon: string; label: string }[] = [
    { key: 'all',   icon: '⊞', label: 'gallery.filter_all'   },
    { key: 'photo', icon: '🖼', label: 'gallery.filter_photo' },
    { key: 'video', icon: '▶', label: 'gallery.filter_video' },
    { key: 'music', icon: '🎵', label: 'gallery.filter_music' },
    { key: 'voice', icon: '🎤', label: 'gallery.filter_voice' },
  ];

  const lbItem = lightbox !== null ? visualFiltered[lightbox] : null;

  return (
    <div className="mg-overlay" onClick={onClose}>
      <div className="mg-panel" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="mg-header">
          <h3 className="mg-title">{title ?? t('gallery.title')}</h3>
          <div className="mg-filters">
            {FILTERS.filter(f => counts[f.key] > 0 || f.key === 'all').map(f => (
              <button
                key={f.key}
                className={`mg-filter-btn ${filter === f.key ? 'mg-filter-btn--on' : ''}`}
                onClick={() => { setFilter(f.key); setLightbox(null); }}
              >
                <span className="mg-filter-icon">{f.icon}</span>
                {t(f.label)}
                {counts[f.key] > 0 && f.key !== 'all' && (
                  <span className="mg-filter-count">{counts[f.key]}</span>
                )}
              </button>
            ))}
          </div>
          <button className="mg-close" onClick={onClose}>✕</button>
        </div>

        {/* Content */}
        <div className="mg-grid-area">
          {loading ? (
            <div className="mg-empty">{t('gallery.loading')}</div>
          ) : filtered.length === 0 ? (
            <div className="mg-empty">{t('gallery.empty')}</div>
          ) : (
            <>
              {/* Visual grid (photos / videos) */}
              {visualFiltered.length > 0 && (
                <div className="mg-grid">
                  {visualFiltered.map((item, idx) => (
                    <div key={idx} className="mg-cell" onClick={() => setLightbox(idx)}>
                      {item.type === 'video' ? (
                        <div className="mg-cell__vid-wrap">
                          <video src={item.url} className="mg-cell__img" preload="metadata" muted playsInline />
                          <div className="mg-cell__play">
                            <svg width="24" height="24" viewBox="0 0 24 24">
                              <circle cx="12" cy="12" r="12" fill="rgba(0,0,0,.45)"/>
                              <polygon points="10,7 18,12 10,17" fill="white"/>
                            </svg>
                          </div>
                        </div>
                      ) : (
                        <img src={item.thumb ?? item.url} alt={item.caption ?? ''} className="mg-cell__img" loading="lazy" />
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* Audio list */}
              {audioFiltered.length > 0 && (
                <div className={`mg-audio-list ${visualFiltered.length > 0 ? 'mg-audio-list--below' : ''}`}>
                  {visualFiltered.length > 0 && audioFiltered.length > 0 && (
                    <div className="mg-audio-section-label">
                      {filter === 'voice' || (filter === 'all' && audioFiltered[0].type === 'voice')
                        ? t('gallery.filter_voice')
                        : t('gallery.filter_music')}
                    </div>
                  )}
                  {audioFiltered.map((item, idx) => (
                    <AudioCard key={idx} item={item} />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Lightbox (visual only) */}
      {lbItem !== null && lightbox !== null && (
        <div className="mg-lb-overlay" onClick={close}>
          <div className="mg-lb-box" onClick={e => e.stopPropagation()}>
            <div className="mg-lb-topbar">
              <span className="mg-lb-counter">{lightbox + 1} / {visualFiltered.length}</span>
              {lbItem.caption && <span className="mg-lb-caption">{lbItem.caption}</span>}
              <div className="mg-lb-actions">
                <button className="mg-lb-btn" onClick={() => downloadItem(lbItem)} title={t('gallery.download')}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                  </svg>
                </button>
                <button className="mg-lb-btn" onClick={close} title={t('gallery.close')}>✕</button>
              </div>
            </div>
            <div className="mg-lb-media">
              {lbItem.type === 'video'
                ? <VideoPlayer src={lbItem.url} className="mg-lb-video" />
                : <img src={lbItem.url} alt={lbItem.caption ?? ''} className="mg-lb-img" />
              }
            </div>
            {visualFiltered.length > 1 && (
              <>
                <button className="mg-lb-nav mg-lb-nav--prev" onClick={e => { e.stopPropagation(); prev(); }}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
                </button>
                <button className="mg-lb-nav mg-lb-nav--next" onClick={e => { e.stopPropagation(); next(); }}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
                </button>
              </>
            )}
            {(lbItem.sender || lbItem.date) && (
              <div className="mg-lb-meta">
                {lbItem.sender && <span className="mg-lb-sender">{lbItem.sender}</span>}
                {lbItem.date   && <span className="mg-lb-date">{lbItem.date}</span>}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
