import React, { useState } from 'react';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fmtNum(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.0', '') + 'M';
  if (n >= 1_000)     return (n / 1_000).toFixed(1).replace('.0', '') + 'K';
  return String(n);
}

const WEEK_LABELS = ['Пн','Вт','Ср','Чт','Пт','Сб','Вс'];

// ─── LineChart ─────────────────────────────────────────────────────────────────
// Shows a 7-day trend with gradient fill

export function LineChart({ data, color = '#4d9de0', height = 120, labels }: {
  data: number[];
  color?: string;
  height?: number;
  labels?: string[];
}) {
  const w = 460; const h = height;
  const pad = { t: 10, b: 28, l: 38, r: 12 };
  const innerW = w - pad.l - pad.r;
  const innerH = h - pad.t - pad.b;

  const vals = data.length > 0 ? data : [0];
  const max  = Math.max(...vals, 1);
  const min  = Math.min(...vals);

  const xs = vals.map((_, i) => pad.l + (i / (vals.length - 1 || 1)) * innerW);
  const ys = vals.map(v => pad.t + innerH - ((v - min) / (max - min || 1)) * innerH);

  const polyline = xs.map((x, i) => `${x},${ys[i]}`).join(' ');
  const area = `${pad.l},${h - pad.b} ${polyline} ${xs[xs.length-1]},${h - pad.b}`;

  const gradId = `lg_${color.replace('#','')}`;

  const [hovIdx, setHovIdx] = useState<number | null>(null);
  const lbs = labels ?? WEEK_LABELS.slice(0, vals.length);

  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', height }} preserveAspectRatio="none">
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.35" />
          <stop offset="100%" stopColor={color} stopOpacity="0.03" />
        </linearGradient>
      </defs>

      {/* Grid lines */}
      {[0, 0.5, 1].map((f, i) => (
        <line key={i}
          x1={pad.l} x2={w - pad.r}
          y1={pad.t + innerH * (1 - f)} y2={pad.t + innerH * (1 - f)}
          stroke="rgba(255,255,255,.06)" strokeWidth="1"
        />
      ))}

      {/* Area fill */}
      <polygon points={area} fill={`url(#${gradId})`} />

      {/* Line */}
      <polyline points={polyline} fill="none" stroke={color} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />

      {/* Data points + hover */}
      {xs.map((x, i) => (
        <g key={i} onMouseEnter={() => setHovIdx(i)} onMouseLeave={() => setHovIdx(null)}>
          <circle cx={x} cy={ys[i]} r={hovIdx === i ? 6 : 3.5}
            fill={hovIdx === i ? '#fff' : color}
            stroke={hovIdx === i ? color : 'none'}
            strokeWidth="2"
            style={{ transition: 'r .12s' }}
          />
          {hovIdx === i && (
            <g>
              <rect x={x - 28} y={ys[i] - 26} width="56" height="20" rx="4" fill="rgba(20,30,46,.95)" />
              <text x={x} y={ys[i] - 12} textAnchor="middle" fontSize="11" fill="#fff" fontWeight="600">
                {fmtNum(vals[i])}
              </text>
            </g>
          )}
          <text x={x} y={h - 6} textAnchor="middle" fontSize="10" fill="rgba(255,255,255,.45)">
            {lbs[i] ?? ''}
          </text>
        </g>
      ))}

      {/* Y axis label */}
      <text x={pad.l - 4} y={pad.t + 4} textAnchor="end" fontSize="9" fill="rgba(255,255,255,.35)">{fmtNum(max)}</text>
      <text x={pad.l - 4} y={h - pad.b} textAnchor="end" fontSize="9" fill="rgba(255,255,255,.35)">{fmtNum(min)}</text>
    </svg>
  );
}

// ─── BarChart ──────────────────────────────────────────────────────────────────

export function BarChart({ data, color = '#4d9de0', height = 120, labels }: {
  data: number[];
  color?: string;
  height?: number;
  labels?: string[];
}) {
  const w = 460; const h = height;
  const pad = { t: 10, b: 28, l: 38, r: 12 };
  const innerW = w - pad.l - pad.r;
  const innerH = h - pad.t - pad.b;

  const vals = data.length > 0 ? data : [0];
  const max  = Math.max(...vals, 1);
  const barW = Math.max(4, (innerW / vals.length) * 0.65);
  const step = innerW / vals.length;

  const lbs = labels ?? WEEK_LABELS.slice(0, vals.length);
  const [hovIdx, setHovIdx] = useState<number | null>(null);

  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', height }} preserveAspectRatio="none">
      {[0, 0.5, 1].map((f, i) => (
        <line key={i}
          x1={pad.l} x2={w - pad.r}
          y1={pad.t + innerH * (1 - f)} y2={pad.t + innerH * (1 - f)}
          stroke="rgba(255,255,255,.06)" strokeWidth="1"
        />
      ))}

      {vals.map((v, i) => {
        const bh  = (v / max) * innerH;
        const x   = pad.l + step * i + (step - barW) / 2;
        const y   = pad.t + innerH - bh;
        const hov = hovIdx === i;
        return (
          <g key={i} onMouseEnter={() => setHovIdx(i)} onMouseLeave={() => setHovIdx(null)}>
            <rect x={x} y={y} width={barW} height={Math.max(bh, 2)}
              rx="3" fill={hov ? '#fff' : color} opacity={hov ? 1 : 0.75}
              style={{ transition: 'fill .12s, opacity .12s' }}
            />
            {hov && (
              <g>
                <rect x={x + barW/2 - 28} y={y - 26} width="56" height="20" rx="4" fill="rgba(20,30,46,.95)" />
                <text x={x + barW/2} y={y - 12} textAnchor="middle" fontSize="11" fill="#fff" fontWeight="600">
                  {fmtNum(v)}
                </text>
              </g>
            )}
            <text x={x + barW/2} y={h - 6} textAnchor="middle" fontSize="10" fill="rgba(255,255,255,.45)">
              {lbs[i] ?? ''}
            </text>
          </g>
        );
      })}

      <text x={pad.l - 4} y={pad.t + 4} textAnchor="end" fontSize="9" fill="rgba(255,255,255,.35)">{fmtNum(max)}</text>
    </svg>
  );
}

// ─── DonutChart ────────────────────────────────────────────────────────────────

export function DonutChart({ segments, size = 140 }: {
  segments: { label: string; value: number; color: string }[];
  size?: number;
}) {
  const r = 48; const ri = 30; const cx = size / 2; const cy = size / 2;
  const total = segments.reduce((s, seg) => s + seg.value, 0) || 1;
  const [hovIdx, setHovIdx] = useState<number | null>(null);

  let angle = -Math.PI / 2;
  const arcs = segments.map((seg, i) => {
    const theta = (seg.value / total) * 2 * Math.PI;
    const x1 = cx + r * Math.cos(angle);
    const y1 = cy + r * Math.sin(angle);
    angle += theta;
    const x2 = cx + r * Math.cos(angle);
    const y2 = cy + r * Math.sin(angle);
    const xi1 = cx + ri * Math.cos(angle - theta);
    const yi1 = cy + ri * Math.sin(angle - theta);
    const xi2 = cx + ri * Math.cos(angle);
    const yi2 = cy + ri * Math.sin(angle);
    const large = theta > Math.PI ? 1 : 0;
    return { ...seg, path: `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${xi2} ${yi2} A ${ri} ${ri} 0 ${large} 0 ${xi1} ${yi1} Z`, i };
  });

  const largest = [...segments].sort((a, b) => b.value - a.value)[0];

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ flexShrink: 0 }}>
        {arcs.map(arc => (
          <path key={arc.i} d={arc.path} fill={arc.color}
            opacity={hovIdx === null || hovIdx === arc.i ? 1 : 0.4}
            style={{ transition: 'opacity .15s', cursor: 'pointer' }}
            onMouseEnter={() => setHovIdx(arc.i)}
            onMouseLeave={() => setHovIdx(null)}
          />
        ))}
        {/* Center label */}
        <text x={cx} y={cy - 6} textAnchor="middle" fontSize="15" fontWeight="700" fill="#fff">
          {fmtNum(hovIdx !== null ? segments[hovIdx].value : (largest?.value ?? 0))}
        </text>
        <text x={cx} y={cy + 10} textAnchor="middle" fontSize="9" fill="rgba(255,255,255,.5)">
          {hovIdx !== null ? segments[hovIdx].label : (largest?.label ?? '')}
        </text>
      </svg>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {segments.map((seg, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}
            onMouseEnter={() => setHovIdx(i)} onMouseLeave={() => setHovIdx(null)}>
            <span style={{ width: 10, height: 10, borderRadius: 3, background: seg.color, flexShrink: 0 }} />
            <span style={{ fontSize: 12, color: 'rgba(255,255,255,.7)' }}>{seg.label}</span>
            <span style={{ fontSize: 12, fontWeight: 600, color: '#fff', marginLeft: 'auto' }}>
              {fmtNum(seg.value)} <span style={{ opacity: .5 }}>({Math.round(seg.value / total * 100)}%)</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── HeatmapBar ────────────────────────────────────────────────────────────────
// 24-cell hourly activity bar

export function HeatmapBar({ values, color = '#4d9de0' }: {
  values: number[];
  color?: string;
}) {
  const safe = values.length === 24 ? values : Array(24).fill(0);
  const max  = Math.max(...safe, 1);
  const [hovIdx, setHovIdx] = useState<number | null>(null);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <div style={{ display: 'flex', gap: 2 }}>
        {safe.map((v, i) => {
          const alpha = 0.08 + (v / max) * 0.9;
          return (
            <div key={i} title={`${String(i).padStart(2,'0')}:00 — ${fmtNum(v)}`}
              style={{
                flex: 1, height: 28, borderRadius: 3,
                background: `rgba(77,157,224,${alpha})`,
                cursor: 'default',
                outline: hovIdx === i ? '2px solid rgba(77,157,224,.8)' : 'none',
              }}
              onMouseEnter={() => setHovIdx(i)}
              onMouseLeave={() => setHovIdx(null)}
            />
          );
        })}
      </div>
      {/* Hour labels: every 4h */}
      <div style={{ display: 'flex', justifyContent: 'space-between', paddingInline: 1 }}>
        {['00','04','08','12','16','20','24'].map(h => (
          <span key={h} style={{ fontSize: 9, color: 'rgba(255,255,255,.35)', width: 20, textAlign: 'center' }}>{h}</span>
        ))}
      </div>
      {hovIdx !== null && (
        <div style={{ fontSize: 12, color: 'rgba(255,255,255,.6)', textAlign: 'center' }}>
          {String(hovIdx).padStart(2,'0')}:00 — {fmtNum(safe[hovIdx])} просмотров
        </div>
      )}
    </div>
  );
}

// ─── StatCard ──────────────────────────────────────────────────────────────────

export function StatCard({ icon, label, value, sub, color = '#4d9de0', trend }: {
  icon: string; label: string; value: string | number; sub?: string;
  color?: string; trend?: number;
}) {
  const trendPos = trend !== undefined && trend >= 0;
  return (
    <div className="stat-card">
      <div className="stat-card-icon" style={{ background: `${color}22`, color }}>{icon}</div>
      <div className="stat-card-body">
        <div className="stat-card-value">{typeof value === 'number' ? fmtNum(value) : value}</div>
        <div className="stat-card-label">{label}</div>
        {sub && <div className="stat-card-sub">{sub}</div>}
      </div>
      {trend !== undefined && (
        <div className={`stat-card-trend ${trendPos ? 'up' : 'down'}`}>
          {trendPos ? '↑' : '↓'} {Math.abs(trend).toFixed(1)}%
        </div>
      )}
    </div>
  );
}
