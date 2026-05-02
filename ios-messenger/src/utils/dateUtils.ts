// ============================================================
// WorldMates Messenger — Date Formatting Utilities
// ============================================================

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const WEEK = 7 * DAY;

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function toDate(dateStr: string | number): Date {
  if (typeof dateStr === 'number') return new Date(dateStr * 1000);
  // If the string is all digits, treat as a unix timestamp (seconds)
  if (/^\d+$/.test(dateStr)) return new Date(parseInt(dateStr, 10) * 1000);
  return new Date(dateStr);
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function diffDaysFromToday(date: Date): number {
  const today = startOfDay(new Date());
  const target = startOfDay(date);
  return Math.round((today.getTime() - target.getTime()) / (DAY * 1000));
}

// ─────────────────────────────────────────────────────────────
// PUBLIC EXPORTS
// ─────────────────────────────────────────────────────────────

/**
 * Format a timestamp for use in a chat list row.
 *   Today          → "14:32"
 *   Yesterday      → "Yesterday"
 *   This week      → "Mon"
 *   Older          → "12.05"
 */
export function formatMessageTime(dateStr: string): string {
  const date = toDate(dateStr);
  const diff = diffDaysFromToday(date);

  if (diff === 0) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  if (diff === 1) {
    return 'Yesterday';
  }
  if (diff < 7) {
    return DAY_NAMES[date.getDay()].slice(0, 3);
  }
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}`;
}

/**
 * Format a last-seen timestamp into a human-readable string.
 *   "online"
 *   "last seen today at 14:32"
 *   "last seen yesterday at 09:11"
 *   "last seen 3 days ago"
 *   "last seen 12 May"
 */
export function formatLastSeen(dateStr: string): string {
  if (!dateStr || dateStr === 'online') return 'online';

  const date = toDate(dateStr);
  const diff = diffDaysFromToday(date);
  const timeStr = `${pad(date.getHours())}:${pad(date.getMinutes())}`;

  if (diff === 0) {
    return `last seen today at ${timeStr}`;
  }
  if (diff === 1) {
    return `last seen yesterday at ${timeStr}`;
  }
  if (diff < 7) {
    return `last seen ${diff} days ago`;
  }
  return `last seen ${date.getDate()} ${MONTH_NAMES[date.getMonth()]}`;
}

/**
 * Format a call duration in seconds.
 *   65     → "1:05"
 *   3600   → "1:00:00"
 *   125    → "2:05"
 */
export function formatCallDuration(seconds: number): string {
  const s = Math.floor(seconds);
  const hrs = Math.floor(s / 3600);
  const mins = Math.floor((s % 3600) / 60);
  const secs = s % 60;

  if (hrs > 0) {
    return `${hrs}:${pad(mins)}:${pad(secs)}`;
  }
  return `${mins}:${pad(secs)}`;
}

/**
 * Short relative time string.
 *   < 1 min  → "just now"
 *   < 1 hr   → "2m ago"
 *   < 1 day  → "1h ago"
 *   < 30 days → "3d ago"
 *   older    → "12 May"
 */
export function timeAgo(dateStr: string): string {
  const date = toDate(dateStr);
  const nowSec = Date.now() / 1000;
  const dateSec = date.getTime() / 1000;
  const diff = Math.max(0, nowSec - dateSec);

  if (diff < MINUTE) return 'just now';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}m ago`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}h ago`;
  if (diff < 30 * DAY) return `${Math.floor(diff / DAY)}d ago`;
  return `${date.getDate()} ${MONTH_NAMES[date.getMonth()]}`;
}

/** Returns true when the given date string is today. */
export function isToday(dateStr: string): boolean {
  return diffDaysFromToday(toDate(dateStr)) === 0;
}

/** Returns true when the given date string is yesterday. */
export function isYesterday(dateStr: string): boolean {
  return diffDaysFromToday(toDate(dateStr)) === 1;
}

/**
 * Format a date for use as a chat message section divider.
 *   Today      → "Today"
 *   Yesterday  → "Yesterday"
 *   This week  → "Monday"
 *   Older      → "12 May 2026"
 */
export function formatDateDivider(dateStr: string): string {
  const date = toDate(dateStr);
  const diff = diffDaysFromToday(date);

  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  if (diff < 7) return DAY_NAMES[date.getDay()];
  return `${date.getDate()} ${MONTH_NAMES[date.getMonth()]} ${date.getFullYear()}`;
}
