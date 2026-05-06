import React, { useCallback, useEffect, useRef, useState } from 'react';
import VideoPlayer from './VideoPlayer';
import { t } from './i18n';

export type MediaItem = {
  url:      string;
  type:     'image' | 'video' | 'gif';
  thumb?:   string;
  caption?: string;
  date?:    string;
  sender?:  string;
};

interface Props {
  items:    MediaItem[];
  onClose:  () => void;
  title?:   string;
  loading?: boolean;
}

export default function MediaGallery({ items, onClose, title, loading }: Props) {
  const [lightbox, setLightbox] = useState<number | null>(null);
  const [filter,   setFilter]   = useState<'all' | 'photo' | 'video'>('all');
  const lightboxRef = useRef<HTMLDivElement>(null);

  const filtered = items.filter(it => {
    if (filter === 'photo') return it.type === 'image' || it.type === 'gif';
    if (filter === 'video') return it.type === 'video';
    return true;
  });

  const open = (idx: number) => setLightbox(idx);
  const close = () => setLightbox(null);

  const prev = useCallback(() => {
    if (lightbox === null) return;
    setLightbox(i => (i! > 0 ? i! - 1 : filtered.length - 1));
  }, [lightbox, filtered.length]);

  const next = useCallback(() => {
    if (lightbox === null) return;
    setLightbox(i => (i! < filtered.length - 1 ? i! + 1 : 0));
  }, [lightbox, filtered.length]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (lightbox === null) {
        if (e.key === 'Escape') onClose();
        return;
      }
      if (e.key === 'Escape')      close();
      if (e.key === 'ArrowLeft')   prev();
      if (e.key === 'ArrowRight')  next();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [lightbox, onClose, prev, next]);

  const downloadItem = (item: MediaItem) => {
    const a = document.createElement('a');
    a.href = item.url;
    a.download = item.url.split('/').pop() ?? 'media';
    a.click();
  };

  return (
    <div className="mg-overlay" onClick={onClose}>
      <div className="mg-panel" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="mg-header">
          <h3 className="mg-title">{title ?? t('gallery.title')}</h3>
          <div className="mg-filters">
            {(['all', 'photo', 'video'] as const).map(f => (
              <button
                key={f}
                className={`mg-filter-btn ${filter === f ? 'mg-filter-btn--on' : ''}`}
                onClick={() => setFilter(f)}
              >
                {t(`gallery.filter_${f}`)}
              </button>
            ))}
          </div>
          <button className="mg-close" onClick={onClose}>✕</button>
        </div>

        {/* Grid */}
        <div className="mg-grid-area">
          {loading ? (
            <div className="mg-empty">{t('gallery.loading')}</div>
          ) : filtered.length === 0 ? (
            <div className="mg-empty">{t('gallery.empty')}</div>
          ) : (
            <div className="mg-grid">
              {filtered.map((item, idx) => (
                <div
                  key={idx}
                  className="mg-cell"
                  onClick={() => open(idx)}
                >
                  {item.type === 'video' ? (
                    <div className="mg-cell__vid-wrap">
                      <video
                        src={item.url}
                        className="mg-cell__img"
                        preload="metadata"
                        muted
                        playsInline
                      />
                      <div className="mg-cell__play">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="white">
                          <circle cx="12" cy="12" r="12" fill="rgba(0,0,0,.45)"/>
                          <polygon points="10,7 18,12 10,17" fill="white"/>
                        </svg>
                      </div>
                    </div>
                  ) : (
                    <img
                      src={item.thumb ?? item.url}
                      alt={item.caption ?? ''}
                      className="mg-cell__img"
                      loading="lazy"
                    />
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Lightbox */}
      {lightbox !== null && (
        <div
          className="mg-lb-overlay"
          ref={lightboxRef}
          onClick={close}
        >
          <div className="mg-lb-box" onClick={e => e.stopPropagation()}>
            {/* Top bar */}
            <div className="mg-lb-topbar">
              <span className="mg-lb-counter">{lightbox + 1} / {filtered.length}</span>
              {filtered[lightbox].caption && (
                <span className="mg-lb-caption">{filtered[lightbox].caption}</span>
              )}
              <div className="mg-lb-actions">
                <button
                  className="mg-lb-btn"
                  onClick={() => downloadItem(filtered[lightbox])}
                  title={t('gallery.download')}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="7 10 12 15 17 10"/>
                    <line x1="12" y1="15" x2="12" y2="3"/>
                  </svg>
                </button>
                <button className="mg-lb-btn" onClick={close} title={t('gallery.close')}>✕</button>
              </div>
            </div>

            {/* Media */}
            <div className="mg-lb-media">
              {filtered[lightbox].type === 'video' ? (
                <VideoPlayer
                  src={filtered[lightbox].url}
                  className="mg-lb-video"
                />
              ) : (
                <img
                  src={filtered[lightbox].url}
                  alt={filtered[lightbox].caption ?? ''}
                  className="mg-lb-img"
                />
              )}
            </div>

            {/* Prev / Next */}
            {filtered.length > 1 && (
              <>
                <button className="mg-lb-nav mg-lb-nav--prev" onClick={e => { e.stopPropagation(); prev(); }}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="15 18 9 12 15 6"/>
                  </svg>
                </button>
                <button className="mg-lb-nav mg-lb-nav--next" onClick={e => { e.stopPropagation(); next(); }}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="9 18 15 12 9 6"/>
                  </svg>
                </button>
              </>
            )}

            {/* Sender / date */}
            {(filtered[lightbox].sender || filtered[lightbox].date) && (
              <div className="mg-lb-meta">
                {filtered[lightbox].sender && <span className="mg-lb-sender">{filtered[lightbox].sender}</span>}
                {filtered[lightbox].date   && <span className="mg-lb-date">{filtered[lightbox].date}</span>}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
