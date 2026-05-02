import React, { useEffect, useState, useCallback } from 'react';
import { t } from './i18n';
import { LineChart, BarChart, DonutChart, HeatmapBar, StatCard } from './Charts';
import {
  getChannelStatistics, loadChannelAdmins, addChannelAdmin, removeChannelAdmin,
  loadChannelSubscribers, loadChannelBanned, banChannelMember, unbanChannelMember,
  kickChannelMember, updateChannelInfo, updateChannelSettings, getChannelActiveMembers,
} from './api';
import type { ChannelItem, Session } from './types';
import type {
  ChannelStatistics, ChannelAdmin, ChannelSettings,
  ChannelSubscriber, ChannelBannedMember, ActiveMember,
} from './types';

// ─── Avatar helper ─────────────────────────────────────────────────────────────

function MiniAvatar({ src, name, size = 36 }: { src?: string; name: string; size?: number }) {
  const initials = name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
  return src
    ? <img src={src} alt={name} style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
    : <div style={{
        width: size, height: size, borderRadius: '50%', background: 'var(--accent)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: size * 0.36, fontWeight: 700, color: '#fff', flexShrink: 0,
      }}>{initials}</div>;
}

// ─── Types ─────────────────────────────────────────────────────────────────────

type Tab = 'info' | 'settings' | 'stats' | 'admins' | 'members' | 'banned';

interface Props {
  channel: ChannelItem;
  session: Session;
  onClose: () => void;
  onChannelUpdated?: () => void;
}

// ─── Main panel ────────────────────────────────────────────────────────────────

export default function ChannelAdminPanel({ channel, session, onClose, onChannelUpdated }: Props) {
  const [tab, setTab] = useState<Tab>('info');

  // Info state
  const [editName, setEditName]     = useState(channel.name ?? '');
  const [editUsername, setEditUsername] = useState(channel.username ?? '');
  const [editDesc, setEditDesc]     = useState(channel.description ?? '');
  const [infoSaving, setInfoSaving] = useState(false);

  // Settings state
  const [settings, setSettings] = useState<ChannelSettings>({
    allow_comments: true, allow_reactions: true, allow_shares: true,
    allow_forwarding: true, show_statistics: true, show_views_count: true,
    notify_subscribers_new_post: true, signature_enabled: false,
    comments_moderation: false, comment_identity: 'user',
  });
  const [settingsSaving, setSettingsSaving] = useState(false);

  // Statistics state
  const [stats, setStats]         = useState<ChannelStatistics | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [activeMembers, setActiveMembers] = useState<ActiveMember[]>([]);
  const [statPeriod, setStatPeriod] = useState<7 | 30>(7);

  // Admins state
  const [admins, setAdmins]         = useState<ChannelAdmin[]>([]);
  const [adminsLoading, setAdminsLoading] = useState(false);
  const [addAdminSearch, setAddAdminSearch] = useState('');

  // Members state
  const [members, setMembers]         = useState<ChannelSubscriber[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [membersOffset, setMembersOffset]   = useState(0);
  const [memberSearch, setMemberSearch]     = useState('');

  // Banned state
  const [banned, setBanned]         = useState<ChannelBannedMember[]>([]);
  const [bannedLoading, setBannedLoading] = useState(false);

  // Ban dialog
  const [showBanDialog, setShowBanDialog] = useState<ChannelSubscriber | null>(null);
  const [banReason, setBanReason]   = useState('');
  const [banDuration, setBanDuration] = useState<string>('0');

  // Load on tab switch
  useEffect(() => {
    if (tab === 'stats' && !stats) loadStats();
    if (tab === 'admins' && admins.length === 0) loadAdmins();
    if (tab === 'members' && members.length === 0) loadMembers(0);
    if (tab === 'banned' && banned.length === 0) loadBanned();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    const [s, am] = await Promise.all([
      getChannelStatistics(session.token, channel.id),
      getChannelActiveMembers(session.token, channel.id, 30),
    ]);
    setStats(s);
    setActiveMembers(am);
    setStatsLoading(false);
  }, [session.token, channel.id]);

  const loadAdmins = useCallback(async () => {
    setAdminsLoading(true);
    setAdmins(await loadChannelAdmins(session.token, channel.id));
    setAdminsLoading(false);
  }, [session.token, channel.id]);

  const loadMembers = useCallback(async (offset: number) => {
    setMembersLoading(true);
    const list = await loadChannelSubscribers(session.token, channel.id, 50, offset);
    setMembers(prev => offset === 0 ? list : [...prev, ...list]);
    setMembersOffset(offset + list.length);
    setMembersLoading(false);
  }, [session.token, channel.id]);

  const loadBanned = useCallback(async () => {
    setBannedLoading(true);
    setBanned(await loadChannelBanned(session.token, channel.id));
    setBannedLoading(false);
  }, [session.token, channel.id]);

  // ── Handlers ────────────────────────────────────────────────────────────────

  async function handleSaveInfo() {
    setInfoSaving(true);
    await updateChannelInfo(session.token, channel.id, editName.trim(), editDesc.trim(), editUsername.trim() || undefined);
    setInfoSaving(false);
    onChannelUpdated?.();
  }

  async function handleSaveSettings() {
    setSettingsSaving(true);
    await updateChannelSettings(session.token, channel.id, settings);
    setSettingsSaving(false);
  }

  async function handleRemoveAdmin(userId: number) {
    await removeChannelAdmin(session.token, channel.id, userId);
    setAdmins(prev => prev.filter(a => a.user_id !== userId));
  }

  async function handleKick(sub: ChannelSubscriber) {
    if (!sub.user_id) return;
    await kickChannelMember(session.token, channel.id, sub.user_id);
    setMembers(prev => prev.filter(m => m.user_id !== sub.user_id));
  }

  async function handleBanConfirm() {
    if (!showBanDialog?.user_id) return;
    const dur = Number(banDuration);
    await banChannelMember(session.token, channel.id, showBanDialog.user_id, banReason || undefined, dur || undefined);
    setMembers(prev => prev.filter(m => m.user_id !== showBanDialog.user_id));
    setShowBanDialog(null); setBanReason(''); setBanDuration('0');
    if (tab === 'banned') loadBanned();
  }

  async function handleUnban(userId: number) {
    await unbanChannelMember(session.token, channel.id, userId);
    setBanned(prev => prev.filter(b => b.user_id !== userId));
  }

  const tabs: { id: Tab; icon: string; label: string }[] = [
    { id: 'info',     icon: '✏️', label: t('chAdmin.tabInfo') },
    { id: 'settings', icon: '⚙️', label: t('chAdmin.tabSettings') },
    { id: 'stats',    icon: '📊', label: t('chAdmin.tabStats') },
    { id: 'admins',   icon: '👑', label: t('chAdmin.tabAdmins') },
    { id: 'members',  icon: '👥', label: t('chAdmin.tabMembers') },
    { id: 'banned',   icon: '🚫', label: t('chAdmin.tabBanned') },
  ];

  const filteredMembers = memberSearch
    ? members.filter(m => (m.name ?? m.username ?? '').toLowerCase().includes(memberSearch.toLowerCase()))
    : members;

  return (
    <div className="admin-overlay" onClick={onClose}>
      <div className="admin-panel" onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="admin-panel-header">
          <MiniAvatar src={channel.avatar_url} name={channel.name} size={42} />
          <div>
            <h2 className="admin-panel-title">{channel.name}</h2>
            <span className="admin-panel-sub">{t('chAdmin.panelTitle')}</span>
          </div>
          <button className="admin-close-btn" onClick={onClose}>✕</button>
        </div>

        {/* Tabs */}
        <div className="admin-tabs">
          {tabs.map(tb => (
            <button key={tb.id}
              className={`admin-tab-btn ${tab === tb.id ? 'active' : ''}`}
              onClick={() => setTab(tb.id)}
            >
              <span className="admin-tab-icon">{tb.icon}</span>
              <span className="admin-tab-label">{tb.label}</span>
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="admin-panel-content">

          {/* ── Info ── */}
          {tab === 'info' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('chAdmin.basicInfo')}</h3>
              <div className="admin-field">
                <label>{t('chAdmin.channelName')}</label>
                <input className="admin-input" value={editName} onChange={e => setEditName(e.target.value)} />
              </div>
              <div className="admin-field">
                <label>@username</label>
                <input className="admin-input" value={editUsername} onChange={e => setEditUsername(e.target.value)} placeholder="optional" />
              </div>
              <div className="admin-field">
                <label>{t('chAdmin.description')}</label>
                <textarea className="admin-textarea" rows={3} value={editDesc} onChange={e => setEditDesc(e.target.value)} />
              </div>
              <div className="admin-subscribers-row">
                <span className="stat-pill">👥 {channel.subscribers_count ?? 0} {t('sidebar.subscribers')}</span>
              </div>
              <button className="btn-primary admin-save-btn" onClick={handleSaveInfo} disabled={infoSaving}>
                {infoSaving ? '…' : t('misc.save')}
              </button>
            </div>
          )}

          {/* ── Settings ── */}
          {tab === 'settings' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('chAdmin.contentSettings')}</h3>
              {([
                ['allow_comments',              t('chAdmin.allowComments')],
                ['allow_reactions',             t('chAdmin.allowReactions')],
                ['allow_shares',                t('chAdmin.allowShares')],
                ['allow_forwarding',            t('chAdmin.allowForwarding')],
                ['show_views_count',            t('chAdmin.showViewsCount')],
                ['notify_subscribers_new_post', t('chAdmin.notifyNewPost')],
                ['signature_enabled',           t('chAdmin.signature')],
                ['comments_moderation',         t('chAdmin.commentModeration')],
              ] as [keyof ChannelSettings, string][]).map(([key, label]) => (
                <div key={key} className="admin-toggle-row">
                  <span>{label}</span>
                  <button
                    className={`toggle-btn ${settings[key] ? 'on' : ''}`}
                    onClick={() => setSettings(prev => ({ ...prev, [key]: !prev[key] }))}
                  >
                    <span className="toggle-knob" />
                  </button>
                </div>
              ))}

              <div className="admin-field" style={{ marginTop: 16 }}>
                <label>{t('chAdmin.commentIdentity')}</label>
                <select className="admin-input" value={settings.comment_identity}
                  onChange={e => setSettings(prev => ({ ...prev, comment_identity: e.target.value as ChannelSettings['comment_identity'] }))}>
                  <option value="user">{t('chAdmin.identityUser')}</option>
                  <option value="channel">{t('chAdmin.identityChannel')}</option>
                  <option value="user_with_signature">{t('chAdmin.identitySignature')}</option>
                </select>
              </div>

              <button className="btn-primary admin-save-btn" onClick={handleSaveSettings} disabled={settingsSaving}>
                {settingsSaving ? '…' : t('misc.save')}
              </button>
            </div>
          )}

          {/* ── Statistics ── */}
          {tab === 'stats' && (
            <div className="admin-section stats-section">
              {statsLoading && <div className="admin-loading"><div className="spinner" /></div>}
              {!statsLoading && !stats && <div className="admin-empty">{t('chAdmin.noStats')}</div>}
              {!statsLoading && stats && (
                <>
                  {/* KPI cards */}
                  <div className="stats-kpi-grid">
                    <StatCard icon="👥" label={t('chAdmin.subscribers')}
                      value={stats.subscribers_count} trend={stats.growth_rate}
                      color="#4d9de0" sub={`+${stats.new_subscribers_week} ${t('chAdmin.thisWeek')}`} />
                    <StatCard icon="👁" label={t('chAdmin.totalViews')}
                      value={stats.views_total} color="#a78bfa"
                      sub={`↑ ${stats.views_last_week.toLocaleString()} ${t('chAdmin.lastWeek')}`} />
                    <StatCard icon="📊" label={t('chAdmin.avgViews')}
                      value={stats.avg_views_per_post} color="#34d399" />
                    <StatCard icon="💫" label={t('chAdmin.engagement')}
                      value={`${stats.engagement_rate.toFixed(1)}%`} color="#f59e0b" />
                    <StatCard icon="📝" label={t('chAdmin.totalPosts')}
                      value={stats.posts_count} color="#4d9de0"
                      sub={`${stats.posts_today} ${t('chAdmin.today')}`} />
                    <StatCard icon="💬" label={t('chAdmin.comments')}
                      value={stats.comments_total} color="#ec4899" />
                    <StatCard icon="❤️" label={t('chAdmin.reactions')}
                      value={stats.reactions_total} color="#ef4444" />
                    <StatCard icon="🔥" label={t('chAdmin.active24h')}
                      value={stats.active_subscribers_24h} color="#f97316" />
                  </div>

                  {/* Subscribers trend */}
                  {stats.subscribers_by_day && stats.subscribers_by_day.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">📈 {t('chAdmin.subscribersTrend')}</h4>
                      <LineChart data={stats.subscribers_by_day} color="#4d9de0" height={130} />
                    </div>
                  )}

                  {/* Views trend */}
                  {stats.views_by_day && stats.views_by_day.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">👁 {t('chAdmin.viewsTrend')}</h4>
                      <BarChart data={stats.views_by_day} color="#a78bfa" height={130} />
                    </div>
                  )}

                  {/* Content breakdown */}
                  {(stats.media_posts_count > 0 || stats.text_posts_count > 0) && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">📁 {t('chAdmin.contentBreakdown')}</h4>
                      <DonutChart segments={[
                        { label: t('chAdmin.mediaPosts'), value: stats.media_posts_count, color: '#4d9de0' },
                        { label: t('chAdmin.textPosts'),  value: stats.text_posts_count,  color: '#a78bfa' },
                      ]} size={150} />
                    </div>
                  )}

                  {/* Hourly heatmap */}
                  {stats.hourly_views && stats.hourly_views.length === 24 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">🕐 {t('chAdmin.hourlyActivity')}</h4>
                      <HeatmapBar values={stats.hourly_views} />
                    </div>
                  )}

                  {/* Growth stats */}
                  <div className="stats-chart-block">
                    <h4 className="stats-chart-title">📊 {t('chAdmin.subscribersDynamic')}</h4>
                    <div className="stats-dynamic-grid">
                      <div className="stats-dynamic-item up">
                        <span className="sdyn-label">+{t('chAdmin.today')}</span>
                        <span className="sdyn-value">+{stats.new_subscribers_today}</span>
                      </div>
                      <div className="stats-dynamic-item up">
                        <span className="sdyn-label">+{t('chAdmin.thisWeek')}</span>
                        <span className="sdyn-value">+{stats.new_subscribers_week}</span>
                      </div>
                      <div className="stats-dynamic-item down">
                        <span className="sdyn-label">-{t('chAdmin.leftWeek')}</span>
                        <span className="sdyn-value">-{stats.left_subscribers_week}</span>
                      </div>
                      <div className="stats-dynamic-item">
                        <span className="sdyn-label">{t('chAdmin.growthRate')}</span>
                        <span className="sdyn-value" style={{ color: stats.growth_rate >= 0 ? '#34d399' : '#ef4444' }}>
                          {stats.growth_rate >= 0 ? '+' : ''}{stats.growth_rate.toFixed(1)}%
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Top posts */}
                  {stats.top_posts && stats.top_posts.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">🏆 {t('chAdmin.topPosts')}</h4>
                      <div className="stats-top-posts">
                        {stats.top_posts.slice(0, 5).map((p, i) => (
                          <div key={p.id} className="stats-top-post-row">
                            <span className="stp-rank">#{i + 1}</span>
                            <div className="stp-body">
                              <p className="stp-text">{p.text.slice(0, 80) || (p.has_media ? '📸 Media' : '—')}</p>
                              <div className="stp-meta">
                                <span>👁 {p.views.toLocaleString()}</span>
                                <span>❤️ {p.reactions}</span>
                                <span>💬 {p.comments}</span>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Active members */}
                  {activeMembers.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">🔥 {t('chAdmin.activeMembers')}</h4>
                      <div className="stats-contributors">
                        {activeMembers.slice(0, 10).map((m, i) => (
                          <div key={m.user_id} className="contributor-row">
                            <span className="contrib-rank">#{i + 1}</span>
                            <MiniAvatar src={m.avatar_url} name={m.name ?? m.username ?? '?'} size={30} />
                            <span className="contrib-name">{m.name ?? m.username ?? `User ${m.user_id}`}</span>
                            <div className="contrib-stats">
                              <span>💬 {m.comment_count}</span>
                              <span>❤️ {m.reaction_count}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* ── Admins ── */}
          {tab === 'admins' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('chAdmin.adminsTitle')}</h3>
              <div className="admin-add-row">
                <input className="admin-input" placeholder={t('chAdmin.addAdminPlaceholder')}
                  value={addAdminSearch} onChange={e => setAddAdminSearch(e.target.value)} />
              </div>
              {adminsLoading && <div className="admin-loading"><div className="spinner" /></div>}
              <div className="admin-list">
                {admins.map(admin => (
                  <div key={admin.user_id} className="admin-member-row">
                    <MiniAvatar src={admin.avatar} name={admin.username} size={36} />
                    <div className="admin-member-info">
                      <span className="admin-member-name">{admin.username}</span>
                      <span className={`role-badge role-${admin.role}`}>{admin.role}</span>
                    </div>
                    {admin.role !== 'owner' && (
                      <button className="btn-xs btn-danger"
                        onClick={() => handleRemoveAdmin(admin.user_id)}
                      >{t('chAdmin.remove')}</button>
                    )}
                  </div>
                ))}
                {!adminsLoading && admins.length === 0 && (
                  <div className="admin-empty">{t('chAdmin.noAdmins')}</div>
                )}
              </div>
            </div>
          )}

          {/* ── Members ── */}
          {tab === 'members' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('chAdmin.membersTitle')}</h3>
              <input className="admin-input admin-search-input" placeholder="🔍 Поиск…"
                value={memberSearch} onChange={e => setMemberSearch(e.target.value)} />
              {membersLoading && members.length === 0 && <div className="admin-loading"><div className="spinner" /></div>}
              <div className="admin-list">
                {filteredMembers.map(sub => {
                  const name = sub.name ?? sub.username ?? `User ${sub.user_id}`;
                  return (
                    <div key={sub.user_id} className="admin-member-row">
                      <MiniAvatar src={sub.avatar} name={name} size={36} />
                      <div className="admin-member-info">
                        <span className="admin-member-name">{name}</span>
                        {sub.username && <span className="admin-member-sub">@{sub.username}</span>}
                        {sub.role && <span className={`role-badge role-${sub.role}`}>{sub.role}</span>}
                      </div>
                      <div className="admin-member-actions">
                        <button className="btn-xs btn-secondary" onClick={() => handleKick(sub)}>{t('chAdmin.kick')}</button>
                        <button className="btn-xs btn-danger" onClick={() => setShowBanDialog(sub)}>{t('chAdmin.ban')}</button>
                      </div>
                    </div>
                  );
                })}
                {!membersLoading && members.length === 0 && <div className="admin-empty">{t('chAdmin.noMembers')}</div>}
                {!membersLoading && members.length >= membersOffset && members.length > 0 && (
                  <button className="admin-load-more" onClick={() => loadMembers(membersOffset)}>
                    {t('chAdmin.loadMore')}
                  </button>
                )}
              </div>
            </div>
          )}

          {/* ── Banned ── */}
          {tab === 'banned' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('chAdmin.bannedTitle')}</h3>
              {bannedLoading && <div className="admin-loading"><div className="spinner" /></div>}
              <div className="admin-list">
                {banned.map(b => {
                  const name = b.name ?? b.username ?? `User ${b.user_id}`;
                  const perm = !b.banned_until || b.banned_until === 0;
                  const untilStr = perm ? t('chAdmin.permanent') : new Date(b.banned_until! * 1000).toLocaleDateString();
                  return (
                    <div key={b.user_id} className="admin-member-row">
                      <MiniAvatar src={b.avatar} name={name} size={36} />
                      <div className="admin-member-info">
                        <span className="admin-member-name">{name}</span>
                        {b.reason && <span className="admin-member-sub">{b.reason}</span>}
                        <span className="admin-member-sub">{t('chAdmin.until')}: {untilStr}</span>
                      </div>
                      <button className="btn-xs btn-primary" onClick={() => handleUnban(b.user_id)}>{t('chAdmin.unban')}</button>
                    </div>
                  );
                })}
                {!bannedLoading && banned.length === 0 && <div className="admin-empty">{t('chAdmin.noBanned')}</div>}
              </div>
            </div>
          )}

        </div>
      </div>

      {/* Ban dialog */}
      {showBanDialog && (
        <div className="admin-dialog-overlay" onClick={() => setShowBanDialog(null)}>
          <div className="admin-dialog" onClick={e => e.stopPropagation()}>
            <h3>{t('chAdmin.banTitle')} {showBanDialog.name ?? showBanDialog.username}</h3>
            <div className="admin-field">
              <label>{t('chAdmin.banReason')}</label>
              <input className="admin-input" value={banReason} onChange={e => setBanReason(e.target.value)} />
            </div>
            <div className="admin-field">
              <label>{t('chAdmin.banDuration')}</label>
              <select className="admin-input" value={banDuration} onChange={e => setBanDuration(e.target.value)}>
                <option value="0">{t('chAdmin.banPermanent')}</option>
                <option value="3600">{t('chAdmin.ban1h')}</option>
                <option value="86400">{t('chAdmin.ban24h')}</option>
                <option value="604800">{t('chAdmin.ban7d')}</option>
                <option value="2592000">{t('chAdmin.ban30d')}</option>
              </select>
            </div>
            <div className="admin-dialog-actions">
              <button className="btn-secondary" onClick={() => setShowBanDialog(null)}>✕</button>
              <button className="btn-danger" onClick={handleBanConfirm}>{t('chAdmin.banConfirm')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
