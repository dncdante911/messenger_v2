import React, { useEffect, useState, useCallback } from 'react';
import { t } from './i18n';
import { BarChart, StatCard, HeatmapBar } from './Charts';
import {
  loadGroupMembers, setGroupMemberRole, removeGroupMember, banGroupMember, unbanGroupMember,
  loadGroupJoinRequests, approveGroupJoinRequest, rejectGroupJoinRequest,
  updateGroupSettings, updateGroupInfo, getGroupStatistics, loadGroupAdminLogs,
} from './api';
import type { GroupItem, Session } from './types';
import type { GroupMemberFull, GroupSettings, GroupStatistics, GroupJoinRequest, AdminLogEntry } from './types';

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

type Tab = 'general' | 'permissions' | 'members' | 'requests' | 'stats' | 'logs';

interface Props {
  group: GroupItem;
  session: Session;
  onClose: () => void;
  onGroupUpdated?: () => void;
}

const DEFAULT_SETTINGS: GroupSettings = {
  allow_members_invite: true, allow_members_pin: false,
  allow_members_delete_messages: false, allow_voice_calls: true, allow_video_calls: true,
  slow_mode_seconds: 0, history_visible_for_new_members: true,
  allow_members_send_media: true, allow_members_send_stickers: true,
  allow_members_send_gifs: true, allow_members_send_links: true,
  allow_members_send_polls: true, anti_spam_enabled: false,
  max_messages_per_minute: 20, auto_mute_spammers: true, block_new_users_media: false,
};

// ─── Admin log action icons ────────────────────────────────────────────────────

function actionIcon(action: string): string {
  const map: Record<string, string> = {
    add_member: '➕', remove_member: '➖', ban_user: '🚫', unban_user: '✅',
    delete_message: '🗑', pin_message: '📌', unpin_message: '📍',
    change_title: '✏️', change_avatar: '🖼', change_description: '📝',
    change_role: '👑', change_settings: '⚙️',
    enable_slow_mode: '🐢', disable_slow_mode: '⚡',
  };
  return map[action] ?? '📋';
}

// ─── Main panel ────────────────────────────────────────────────────────────────

export default function GroupAdminPanel({ group, session, onClose, onGroupUpdated }: Props) {
  const [tab, setTab] = useState<Tab>('general');

  // General state
  const [editName, setEditName]     = useState(group.group_name ?? '');
  const [editDesc, setEditDesc]     = useState('');
  const [editPrivate, setEditPrivate] = useState(false);
  const [infoSaving, setInfoSaving] = useState(false);
  const [settings, setSettings]     = useState<GroupSettings>(DEFAULT_SETTINGS);
  const [settingsSaving, setSettingsSaving] = useState(false);

  // Members
  const [members, setMembers]         = useState<GroupMemberFull[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [memberSearch, setMemberSearch] = useState('');
  const [showBanDialog, setShowBanDialog] = useState<GroupMemberFull | null>(null);
  const [banReason, setBanReason]     = useState('');
  const [showRoleDialog, setShowRoleDialog] = useState<GroupMemberFull | null>(null);

  // Join requests
  const [requests, setRequests]       = useState<GroupJoinRequest[]>([]);
  const [reqLoading, setReqLoading]   = useState(false);

  // Statistics
  const [stats, setStats]             = useState<GroupStatistics | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  // Admin logs
  const [logs, setLogs]               = useState<AdminLogEntry[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logPage, setLogPage]         = useState(1);
  const [logTotal, setLogTotal]       = useState(0);

  useEffect(() => {
    if (tab === 'members' && members.length === 0) loadMembers();
    if (tab === 'requests' && requests.length === 0) loadRequests();
    if (tab === 'stats' && !stats) loadStats();
    if (tab === 'logs' && logs.length === 0) loadLogs(1);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  const loadMembers = useCallback(async () => {
    setMembersLoading(true);
    setMembers(await loadGroupMembers(session.token, group.id));
    setMembersLoading(false);
  }, [session.token, group.id]);

  const loadRequests = useCallback(async () => {
    setReqLoading(true);
    setRequests(await loadGroupJoinRequests(session.token, group.id));
    setReqLoading(false);
  }, [session.token, group.id]);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    setStats(await getGroupStatistics(session.token, group.id));
    setStatsLoading(false);
  }, [session.token, group.id]);

  const loadLogs = useCallback(async (page: number) => {
    setLogsLoading(true);
    const { logs: newLogs, total } = await loadGroupAdminLogs(session.token, group.id, page);
    setLogs(prev => page === 1 ? newLogs : [...prev, ...newLogs]);
    setLogTotal(total);
    setLogPage(page);
    setLogsLoading(false);
  }, [session.token, group.id]);

  // ── Handlers ────────────────────────────────────────────────────────────────

  async function handleSaveGeneral() {
    setInfoSaving(true);
    await Promise.all([
      updateGroupInfo(session.token, group.id, editName.trim(), editDesc.trim(), editPrivate),
      updateGroupSettings(session.token, group.id, settings),
    ]);
    setInfoSaving(false);
    onGroupUpdated?.();
  }

  async function handleSaveSettings() {
    setSettingsSaving(true);
    await updateGroupSettings(session.token, group.id, settings);
    setSettingsSaving(false);
  }

  async function handleSetRole(member: GroupMemberFull, role: string) {
    await setGroupMemberRole(session.token, group.id, member.user_id, role);
    setMembers(prev => prev.map(m => m.user_id === member.user_id ? { ...m, role: role as GroupMemberFull['role'] } : m));
    setShowRoleDialog(null);
  }

  async function handleKick(member: GroupMemberFull) {
    await removeGroupMember(session.token, group.id, member.user_id);
    setMembers(prev => prev.filter(m => m.user_id !== member.user_id));
  }

  async function handleBanConfirm() {
    if (!showBanDialog) return;
    await banGroupMember(session.token, group.id, showBanDialog.user_id, banReason || undefined);
    setMembers(prev => prev.filter(m => m.user_id !== showBanDialog.user_id));
    setShowBanDialog(null); setBanReason('');
  }

  async function handleApprove(req: GroupJoinRequest) {
    await approveGroupJoinRequest(session.token, group.id, req.id);
    setRequests(prev => prev.filter(r => r.id !== req.id));
  }

  async function handleReject(req: GroupJoinRequest) {
    await rejectGroupJoinRequest(session.token, group.id, req.id);
    setRequests(prev => prev.filter(r => r.id !== req.id));
  }

  const filteredMembers = memberSearch
    ? members.filter(m => m.username.toLowerCase().includes(memberSearch.toLowerCase()))
    : members;

  const tabs: { id: Tab; icon: string; label: string }[] = [
    { id: 'general',     icon: '⚙️', label: t('grAdmin.tabGeneral') },
    { id: 'permissions', icon: '🔒', label: t('grAdmin.tabPerms') },
    { id: 'members',     icon: '👥', label: t('grAdmin.tabMembers') },
    { id: 'requests',    icon: '📩', label: t('grAdmin.tabRequests') },
    { id: 'stats',       icon: '📊', label: t('grAdmin.tabStats') },
    { id: 'logs',        icon: '📋', label: t('grAdmin.tabLogs') },
  ];

  return (
    <div className="admin-overlay" onClick={onClose}>
      <div className="admin-panel" onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="admin-panel-header">
          <MiniAvatar src={group.avatar} name={group.group_name} size={42} />
          <div>
            <h2 className="admin-panel-title">{group.group_name}</h2>
            <span className="admin-panel-sub">{t('grAdmin.panelTitle')} · {group.members_count ?? 0} {t('sidebar.members')}</span>
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
              {tb.id === 'requests' && requests.length > 0 && (
                <span className="admin-tab-badge">{requests.length}</span>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="admin-panel-content">

          {/* ── General ── */}
          {tab === 'general' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('grAdmin.groupInfo')}</h3>
              <div className="admin-field">
                <label>{t('grAdmin.groupName')}</label>
                <input className="admin-input" value={editName} onChange={e => setEditName(e.target.value)} />
              </div>
              <div className="admin-field">
                <label>{t('chAdmin.description')}</label>
                <textarea className="admin-textarea" rows={3} value={editDesc} onChange={e => setEditDesc(e.target.value)} />
              </div>
              <div className="admin-toggle-row">
                <span>{t('grAdmin.privateGroup')}</span>
                <button className={`toggle-btn ${editPrivate ? 'on' : ''}`}
                  onClick={() => setEditPrivate(p => !p)}>
                  <span className="toggle-knob" />
                </button>
              </div>

              <h3 className="admin-section-title" style={{ marginTop: 20 }}>{t('grAdmin.antiSpam')}</h3>
              <div className="admin-toggle-row">
                <span>{t('grAdmin.antiSpamEnabled')}</span>
                <button className={`toggle-btn ${settings.anti_spam_enabled ? 'on' : ''}`}
                  onClick={() => setSettings(p => ({ ...p, anti_spam_enabled: !p.anti_spam_enabled }))}>
                  <span className="toggle-knob" />
                </button>
              </div>
              {settings.anti_spam_enabled && (
                <>
                  <div className="admin-toggle-row">
                    <span>{t('grAdmin.autoMuteSpammers')}</span>
                    <button className={`toggle-btn ${settings.auto_mute_spammers ? 'on' : ''}`}
                      onClick={() => setSettings(p => ({ ...p, auto_mute_spammers: !p.auto_mute_spammers }))}>
                      <span className="toggle-knob" />
                    </button>
                  </div>
                  <div className="admin-toggle-row">
                    <span>{t('grAdmin.blockNewUsersMedia')}</span>
                    <button className={`toggle-btn ${settings.block_new_users_media ? 'on' : ''}`}
                      onClick={() => setSettings(p => ({ ...p, block_new_users_media: !p.block_new_users_media }))}>
                      <span className="toggle-knob" />
                    </button>
                  </div>
                  <div className="admin-field">
                    <label>{t('grAdmin.maxMsgPerMin')}</label>
                    <input type="number" className="admin-input" style={{ width: 80 }}
                      value={settings.max_messages_per_minute}
                      onChange={e => setSettings(p => ({ ...p, max_messages_per_minute: Number(e.target.value) }))} />
                  </div>
                </>
              )}

              <div className="admin-field" style={{ marginTop: 16 }}>
                <label>{t('grAdmin.slowMode')} ({t('grAdmin.seconds')})</label>
                <select className="admin-input" value={String(settings.slow_mode_seconds)}
                  onChange={e => setSettings(p => ({ ...p, slow_mode_seconds: Number(e.target.value) }))}>
                  <option value="0">{t('grAdmin.off')}</option>
                  <option value="10">10s</option>
                  <option value="30">30s</option>
                  <option value="60">1 min</option>
                  <option value="300">5 min</option>
                  <option value="900">15 min</option>
                  <option value="3600">1 {t('grAdmin.hour')}</option>
                </select>
              </div>

              <button className="btn-primary admin-save-btn" onClick={handleSaveGeneral} disabled={infoSaving}>
                {infoSaving ? '…' : t('misc.save')}
              </button>
            </div>
          )}

          {/* ── Permissions ── */}
          {tab === 'permissions' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('grAdmin.memberPerms')}</h3>
              {([
                ['allow_members_invite',          t('grAdmin.permInvite')],
                ['allow_members_pin',             t('grAdmin.permPin')],
                ['allow_members_delete_messages', t('grAdmin.permDelete')],
                ['allow_voice_calls',             t('grAdmin.permVoice')],
                ['allow_video_calls',             t('grAdmin.permVideo')],
                ['history_visible_for_new_members', t('grAdmin.permHistory')],
              ] as [keyof GroupSettings, string][]).map(([key, label]) => (
                <div key={key} className="admin-toggle-row">
                  <span>{label}</span>
                  <button className={`toggle-btn ${settings[key] ? 'on' : ''}`}
                    onClick={() => setSettings(p => ({ ...p, [key]: !p[key] }))}>
                    <span className="toggle-knob" />
                  </button>
                </div>
              ))}

              <h3 className="admin-section-title" style={{ marginTop: 20 }}>{t('grAdmin.mediaPerms')}</h3>
              {([
                ['allow_members_send_media',    t('grAdmin.permMedia')],
                ['allow_members_send_stickers', t('grAdmin.permStickers')],
                ['allow_members_send_gifs',     t('grAdmin.permGifs')],
                ['allow_members_send_links',    t('grAdmin.permLinks')],
                ['allow_members_send_polls',    t('grAdmin.permPolls')],
              ] as [keyof GroupSettings, string][]).map(([key, label]) => (
                <div key={key} className="admin-toggle-row">
                  <span>{label}</span>
                  <button className={`toggle-btn ${settings[key] ? 'on' : ''}`}
                    onClick={() => setSettings(p => ({ ...p, [key]: !p[key] }))}>
                    <span className="toggle-knob" />
                  </button>
                </div>
              ))}

              <button className="btn-primary admin-save-btn" onClick={handleSaveSettings} disabled={settingsSaving}>
                {settingsSaving ? '…' : t('misc.save')}
              </button>
            </div>
          )}

          {/* ── Members ── */}
          {tab === 'members' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('grAdmin.membersTitle')} ({members.length})</h3>
              <input className="admin-input admin-search-input" placeholder="🔍 Поиск…"
                value={memberSearch} onChange={e => setMemberSearch(e.target.value)} />
              {membersLoading && <div className="admin-loading"><div className="spinner" /></div>}
              <div className="admin-list">
                {filteredMembers.map(m => (
                  <div key={m.user_id} className="admin-member-row">
                    <MiniAvatar src={m.avatar} name={m.username} size={36} />
                    <div className="admin-member-info">
                      <span className="admin-member-name">{m.username}</span>
                      <span className={`role-badge role-${m.role}`}>{m.role}</span>
                      {m.is_muted && <span className="role-badge role-muted">muted</span>}
                    </div>
                    {m.role !== 'owner' && (
                      <div className="admin-member-actions">
                        <button className="btn-xs btn-secondary" onClick={() => setShowRoleDialog(m)}>
                          {t('grAdmin.changeRole')}
                        </button>
                        <button className="btn-xs btn-secondary" onClick={() => handleKick(m)}>
                          {t('chAdmin.kick')}
                        </button>
                        <button className="btn-xs btn-danger" onClick={() => setShowBanDialog(m)}>
                          {t('chAdmin.ban')}
                        </button>
                      </div>
                    )}
                  </div>
                ))}
                {!membersLoading && members.length === 0 && <div className="admin-empty">{t('chAdmin.noMembers')}</div>}
              </div>
            </div>
          )}

          {/* ── Join Requests ── */}
          {tab === 'requests' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('grAdmin.joinRequests')}</h3>
              {reqLoading && <div className="admin-loading"><div className="spinner" /></div>}
              {!reqLoading && requests.length === 0 && <div className="admin-empty">{t('grAdmin.noRequests')}</div>}
              <div className="admin-list">
                {requests.map(req => (
                  <div key={req.id} className="admin-member-row">
                    <MiniAvatar src={req.user_avatar} name={req.username} size={36} />
                    <div className="admin-member-info">
                      <span className="admin-member-name">{req.username}</span>
                      {req.message && <span className="admin-member-sub">"{req.message}"</span>}
                      <span className="admin-member-sub">
                        {new Date(req.created_time * 1000).toLocaleDateString()}
                      </span>
                    </div>
                    <div className="admin-member-actions">
                      <button className="btn-xs btn-primary" onClick={() => handleApprove(req)}>✓</button>
                      <button className="btn-xs btn-danger" onClick={() => handleReject(req)}>✕</button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Statistics ── */}
          {tab === 'stats' && (
            <div className="admin-section stats-section">
              {statsLoading && <div className="admin-loading"><div className="spinner" /></div>}
              {!statsLoading && !stats && <div className="admin-empty">{t('chAdmin.noStats')}</div>}
              {!statsLoading && stats && (
                <>
                  <div className="stats-kpi-grid">
                    <StatCard icon="👥" label={t('grAdmin.members')} value={stats.members_count} color="#4d9de0" />
                    <StatCard icon="💬" label={t('grAdmin.messages')} value={stats.messages_count} color="#a78bfa"
                      sub={`+${stats.messages_today} ${t('chAdmin.today')}`} />
                    <StatCard icon="🔥" label={t('grAdmin.active24h')} value={stats.active_members_24h} color="#f97316" />
                    <StatCard icon="📊" label={t('grAdmin.activeWeek')} value={stats.active_members_week} color="#34d399" />
                    <StatCard icon="🖼" label={t('grAdmin.mediaCount')} value={stats.media_count} color="#ec4899" />
                    <StatCard icon="🔗" label={t('grAdmin.linksCount')} value={stats.links_count} color="#f59e0b" />
                    <StatCard icon="📈" label={t('grAdmin.newWeek')} value={stats.new_members_week} color="#4d9de0"
                      trend={stats.growth_rate} />
                    <StatCard icon="📉" label={t('grAdmin.leftWeek')} value={stats.left_members_week} color="#ef4444" />
                  </div>

                  {/* Messages activity chart */}
                  <div className="stats-chart-block">
                    <h4 className="stats-chart-title">💬 {t('grAdmin.messageActivity')}</h4>
                    <BarChart data={[
                      stats.messages_today,
                      stats.messages_this_week,
                      stats.messages_this_month,
                      stats.messages_count,
                    ]} labels={[t('chAdmin.today'), t('chAdmin.thisWeek'), t('grAdmin.month'), t('grAdmin.total')]}
                    color="#a78bfa" height={120} />
                  </div>

                  {/* Peak hours heatmap */}
                  {stats.peak_hours && stats.peak_hours.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">🕐 {t('chAdmin.hourlyActivity')}</h4>
                      <HeatmapBar values={
                        (() => {
                          const h = Array(24).fill(0);
                          stats.peak_hours!.forEach(hr => { if (hr >= 0 && hr < 24) h[hr]++; });
                          return h;
                        })()
                      } />
                    </div>
                  )}

                  {/* Top contributors */}
                  {stats.top_contributors && stats.top_contributors.length > 0 && (
                    <div className="stats-chart-block">
                      <h4 className="stats-chart-title">🏆 {t('grAdmin.topContributors')}</h4>
                      <div className="stats-contributors">
                        {stats.top_contributors.slice(0, 10).map((c, i) => (
                          <div key={c.user_id} className="contributor-row">
                            <span className="contrib-rank">#{i + 1}</span>
                            <MiniAvatar src={c.avatar} name={c.name ?? c.username} size={30} />
                            <span className="contrib-name">{c.name ?? c.username}</span>
                            <span className="contrib-stats">
                              <span>💬 {c.messages_count}</span>
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* ── Admin Logs ── */}
          {tab === 'logs' && (
            <div className="admin-section">
              <h3 className="admin-section-title">{t('grAdmin.adminLogs')} ({logTotal})</h3>
              {logsLoading && logs.length === 0 && <div className="admin-loading"><div className="spinner" /></div>}
              <div className="admin-logs-list">
                {logs.map((log, i) => (
                  <div key={log.id ?? i} className="admin-log-row">
                    <span className="log-action-icon">{actionIcon(log.action)}</span>
                    <MiniAvatar src={log.admin_avatar} name={log.admin_name} size={28} />
                    <div className="log-body">
                      <span className="log-admin">{log.admin_name}</span>
                      <span className="log-action">{log.action.replace(/_/g, ' ')}</span>
                      {log.target_user_name && (
                        <span className="log-target">→ {log.target_user_name}</span>
                      )}
                    </div>
                    <time className="log-time">
                      {new Date(log.created_at).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                    </time>
                  </div>
                ))}
                {!logsLoading && logs.length === 0 && <div className="admin-empty">{t('grAdmin.noLogs')}</div>}
                {!logsLoading && logs.length < logTotal && (
                  <button className="admin-load-more" onClick={() => loadLogs(logPage + 1)}>
                    {t('chAdmin.loadMore')}
                  </button>
                )}
              </div>
            </div>
          )}

        </div>
      </div>

      {/* Role change dialog */}
      {showRoleDialog && (
        <div className="admin-dialog-overlay" onClick={() => setShowRoleDialog(null)}>
          <div className="admin-dialog" onClick={e => e.stopPropagation()}>
            <h3>{t('grAdmin.changeRole')}: {showRoleDialog.username}</h3>
            <div className="role-picker">
              {(['admin', 'moderator', 'member'] as const).map(r => (
                <button key={r}
                  className={`role-pick-btn role-${r} ${showRoleDialog.role === r ? 'active' : ''}`}
                  onClick={() => handleSetRole(showRoleDialog, r)}
                >{r}</button>
              ))}
            </div>
            <div className="admin-dialog-actions">
              <button className="btn-secondary" onClick={() => setShowRoleDialog(null)}>✕</button>
            </div>
          </div>
        </div>
      )}

      {/* Ban dialog */}
      {showBanDialog && (
        <div className="admin-dialog-overlay" onClick={() => setShowBanDialog(null)}>
          <div className="admin-dialog" onClick={e => e.stopPropagation()}>
            <h3>{t('chAdmin.banTitle')} {showBanDialog.username}</h3>
            <div className="admin-field">
              <label>{t('chAdmin.banReason')}</label>
              <input className="admin-input" value={banReason} onChange={e => setBanReason(e.target.value)} />
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
