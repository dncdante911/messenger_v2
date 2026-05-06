import React, { useCallback, useEffect, useRef, useState } from 'react';
import { t } from './i18n';

interface Props {
  src: string;
  poster?: string;
  className?: string;
}

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];

function fmtTime(s: number): string {
  if (!isFinite(s) || s < 0) return '0:00';
  const m = Math.floor(s / 60);
  const ss = Math.floor(s % 60);
  return `${m}:${ss.toString().padStart(2, '0')}`;
}

function VolumeIcon({ volume, muted }: { volume: number; muted: boolean }) {
  if (muted || volume === 0) return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
      <line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/>
    </svg>
  );
  if (volume < 0.5) return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
      <path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
    </svg>
  );
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
      <path d="M19.07 4.93a10 10 0 0 1 0 14.14"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
    </svg>
  );
}

export default function VideoPlayer({ src, poster, className }: Props) {
  const videoRef    = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const progressRef  = useRef<HTMLDivElement>(null);
  const hideTimer    = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [playing,      setPlaying]      = useState(false);
  const [progress,     setProgress]     = useState(0);
  const [currentTime,  setCurrentTime]  = useState(0);
  const [duration,     setDuration]     = useState(0);
  const [volume,       setVolume]       = useState(1);
  const [muted,        setMuted]        = useState(false);
  const [buffered,     setBuffered]     = useState(0);
  const [speed,        setSpeed]        = useState(1);
  const [showSpeed,    setShowSpeed]    = useState(false);
  const [fullscreen,   setFullscreen]   = useState(false);
  const [showCtrl,     setShowCtrl]     = useState(true);
  const [dragging,     setDragging]     = useState(false);

  const resetHide = useCallback((immediate = false) => {
    setShowCtrl(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    if (!immediate) {
      hideTimer.current = setTimeout(() => setShowCtrl(false), 2800);
    }
  }, []);

  const seekTo = useCallback((clientX: number) => {
    const v   = videoRef.current;
    const bar = progressRef.current;
    if (!v || !bar || !v.duration) return;
    const rect = bar.getBoundingClientRect();
    const pct  = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    v.currentTime = pct * v.duration;
    setProgress(pct);
    setCurrentTime(pct * v.duration);
  }, []);

  const handleProgressMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    setDragging(true);
    seekTo(e.clientX);
  };

  useEffect(() => {
    if (!dragging) return;
    const onMove  = (e: MouseEvent) => seekTo(e.clientX);
    const onUp    = () => setDragging(false);
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => { window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
  }, [dragging, seekTo]);

  useEffect(() => {
    const onFsChange = () => setFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  const togglePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    const v = videoRef.current;
    if (!v) return;
    if (v.paused) v.play(); else v.pause();
    resetHide();
  };

  const handleTimeUpdate = () => {
    const v = videoRef.current;
    if (!v || !v.duration) return;
    setCurrentTime(v.currentTime);
    setProgress(v.currentTime / v.duration);
    if (v.buffered.length > 0) {
      setBuffered(v.buffered.end(v.buffered.length - 1) / v.duration);
    }
  };

  const toggleMute = (e: React.MouseEvent) => {
    e.stopPropagation();
    const v = videoRef.current;
    if (!v) return;
    const next = !muted;
    setMuted(next);
    v.muted = next;
  };

  const changeVolume = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    const v = videoRef.current;
    const val = parseFloat(e.target.value);
    setVolume(val);
    if (v) { v.volume = val; v.muted = val === 0; }
    setMuted(val === 0);
  };

  const applySpeed = (e: React.MouseEvent, s: number) => {
    e.stopPropagation();
    const v = videoRef.current;
    setSpeed(s);
    if (v) v.playbackRate = s;
    setShowSpeed(false);
  };

  const toggleFullscreen = (e: React.MouseEvent) => {
    e.stopPropagation();
    const el = containerRef.current;
    if (!el) return;
    if (!document.fullscreenElement) el.requestFullscreen?.();
    else document.exitFullscreen?.();
  };

  const handlePiP = async (e: React.MouseEvent) => {
    e.stopPropagation();
    const v = videoRef.current;
    if (!v) return;
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await v.requestPictureInPicture();
    } catch { /* unsupported */ }
  };

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    const onPlay  = () => { setPlaying(true);  resetHide(); };
    const onPause = () => { setPlaying(false); setShowCtrl(true); if (hideTimer.current) clearTimeout(hideTimer.current); };
    const onEnded = () => { setPlaying(false); setProgress(0); setCurrentTime(0); setShowCtrl(true); };
    v.addEventListener('play',  onPlay);
    v.addEventListener('pause', onPause);
    v.addEventListener('ended', onEnded);
    return () => { v.removeEventListener('play', onPlay); v.removeEventListener('pause', onPause); v.removeEventListener('ended', onEnded); };
  }, [resetHide]);

  return (
    <div
      ref={containerRef}
      className={`vp-wrap ${className ?? ''} ${fullscreen ? 'vp-fullscreen' : ''}`}
      onMouseMove={() => resetHide()}
      onMouseLeave={() => { if (playing) setShowCtrl(false); }}
    >
      <video
        ref={videoRef}
        src={src}
        poster={poster}
        className="vp-video"
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={() => setDuration(videoRef.current?.duration ?? 0)}
        onClick={togglePlay}
      />

      {/* Big center play overlay */}
      {!playing && (
        <div className="vp-center-play" onClick={togglePlay}>
          <svg width="52" height="52" viewBox="0 0 52 52" fill="none">
            <circle cx="26" cy="26" r="26" fill="rgba(0,0,0,.52)"/>
            <polygon points="21,16 38,26 21,36" fill="white"/>
          </svg>
        </div>
      )}

      {/* Controls */}
      <div
        className={`vp-controls ${showCtrl || !playing ? 'vp-controls--visible' : ''}`}
        onClick={e => e.stopPropagation()}
      >
        {/* Progress */}
        <div
          ref={progressRef}
          className="vp-progress"
          onMouseDown={handleProgressMouseDown}
        >
          <div className="vp-progress__bg" />
          <div className="vp-progress__buf" style={{ width: `${buffered * 100}%` }} />
          <div className="vp-progress__fill" style={{ width: `${progress * 100}%` }} />
          <div className="vp-progress__thumb" style={{ left: `${progress * 100}%` }} />
        </div>

        {/* Bottom row */}
        <div className="vp-ctrl-row">
          {/* Left: play, volume, time */}
          <div className="vp-ctrl-left">
            <button className="vp-btn" onClick={togglePlay} title={playing ? t('vp.pause') : t('vp.play')}>
              {playing
                ? <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><rect x="2" y="1" width="4" height="14" rx="1"/><rect x="10" y="1" width="4" height="14" rx="1"/></svg>
                : <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><polygon points="2,1 14,8 2,15"/></svg>
              }
            </button>

            <div className="vp-vol-wrap">
              <button className="vp-btn" onClick={toggleMute} title={t('vp.mute')}>
                <VolumeIcon volume={volume} muted={muted} />
              </button>
              <input
                type="range" min={0} max={1} step={0.02}
                value={muted ? 0 : volume}
                onChange={changeVolume}
                onClick={e => e.stopPropagation()}
                className="vp-vol-slider"
              />
            </div>

            <span className="vp-time">{fmtTime(currentTime)} / {fmtTime(duration)}</span>
          </div>

          {/* Right: speed, PiP, fullscreen */}
          <div className="vp-ctrl-right">
            <div className="vp-speed-wrap">
              <button
                className="vp-btn vp-btn--text"
                onClick={e => { e.stopPropagation(); setShowSpeed(v => !v); }}
                title={t('vp.speed')}
              >
                {speed}×
              </button>
              {showSpeed && (
                <div className="vp-speed-menu">
                  {SPEEDS.map(s => (
                    <button
                      key={s}
                      className={`vp-speed-item ${s === speed ? 'vp-speed-item--on' : ''}`}
                      onClick={e => applySpeed(e, s)}
                    >
                      {s}×
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button className="vp-btn" onClick={handlePiP} title={t('vp.pip')}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2"/><rect x="12" y="9" width="9" height="7" rx="1" fill="currentColor" stroke="none"/>
              </svg>
            </button>

            <button className="vp-btn" onClick={toggleFullscreen} title={t('vp.fullscreen')}>
              {fullscreen
                ? <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><path d="M8 3v3a2 2 0 0 1-2 2H3"/><path d="M21 8h-3a2 2 0 0 1-2-2V3"/><path d="M3 16h3a2 2 0 0 1 2 2v3"/><path d="M16 21v-3a2 2 0 0 1 2-2h3"/></svg>
                : <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/></svg>
              }
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
