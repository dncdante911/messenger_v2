'use strict';

const { app, BrowserWindow, ipcMain, Tray, Menu, Notification, nativeImage } = require('electron');
const path = require('node:path');
const https = require('node:https');
const zlib  = require('node:zlib');

const isDev = Boolean(process.env.VITE_DEV_SERVER_URL);

// ─── PNG icon generator ───────────────────────────────────────────────────────
// Generates minimal valid RGBA PNG buffers at runtime — no external asset files.

function buildCrc32Table() {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[i] = c >>> 0;
  }
  return t;
}
const CRC_TABLE = buildCrc32Table();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = (CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)) >>> 0;
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf  = Buffer.alloc(4); lenBuf.writeUInt32BE(data.length);
  const crcBuf  = Buffer.alloc(4); crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

/**
 * Build an RGBA PNG where each pixel is determined by pixelFn(x, y) → [r, g, b, a].
 */
function buildRgbaPng(w, h, pixelFn) {
  const sig  = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; // bit-depth=8, color-type=6 (RGBA)

  const rows = [];
  for (let y = 0; y < h; y++) {
    const row = Buffer.alloc(1 + w * 4);
    row[0] = 0; // filter: None
    for (let x = 0; x < w; x++) {
      const [r, g, b, a] = pixelFn(x, y, w, h);
      row[1 + x * 4]     = r;
      row[1 + x * 4 + 1] = g;
      row[1 + x * 4 + 2] = b;
      row[1 + x * 4 + 3] = a;
    }
    rows.push(row);
  }
  const idat = zlib.deflateSync(Buffer.concat(rows));
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', idat), pngChunk('IEND', Buffer.alloc(0))]);
}

/** Soft-edged circle with anti-aliased 1-pixel border. */
function makeCirclePng(size, r, g, b) {
  const cx = (size - 1) / 2, cy = (size - 1) / 2, radius = size / 2 - 1.5;
  return buildRgbaPng(size, size, (x, y) => {
    const dx = x - cx, dy = y - cy;
    const dist = Math.sqrt(dx * dx + dy * dy);
    const alpha = dist <= radius     ? 255
                : dist <= radius + 1 ? Math.round((radius + 1 - dist) * 255)
                : 0;
    return [r, g, b, alpha];
  });
}

// Tray icon: #1f6feb (blue) — 22×22
const TRAY_ICON_BUF = makeCirclePng(22, 31, 111, 235);
// Taskbar overlay badge: #f85149 (red) — 16×16
const BADGE_ICON_BUF = makeCirclePng(16, 248, 81, 73);

// ─── Mini i18n for the main process ──────────────────────────────────────────
// Only tray / menu strings — renderer handles the full translation table.

const MAIN_I18N = {
  ru: { show: 'Показать',  quit: 'Выйти',  unread: n => `Непрочитанных: ${n}`, tooltip: 'WorldMates Messenger' },
  uk: { show: 'Показати',  quit: 'Вийти',  unread: n => `Непрочитаних: ${n}`,  tooltip: 'WorldMates Messenger' },
  en: { show: 'Show',      quit: 'Quit',   unread: n => `Unread: ${n}`,         tooltip: 'WorldMates Messenger' },
};
let mainLang = 'ru';
const mt = () => MAIN_I18N[mainLang] || MAIN_I18N.ru;

// ─── App globals ──────────────────────────────────────────────────────────────

/** @type {BrowserWindow | null} */
let mainWindow = null;
/** @type {Tray | null} */
let tray = null;
let unreadCount = 0;
let isQuitting = false;

// ─── Tray helpers ─────────────────────────────────────────────────────────────

function buildTrayMenu() {
  const m = mt();
  return Menu.buildFromTemplate([
    {
      label: m.show,
      click: () => { mainWindow?.show(); mainWindow?.focus(); },
    },
    { type: 'separator' },
    {
      label: m.quit,
      click: () => { isQuitting = true; app.quit(); },
    },
  ]);
}

function updateTray() {
  if (!tray) return;
  const m = mt();
  tray.setToolTip(unreadCount > 0 ? m.unread(unreadCount) : m.tooltip);
  tray.setContextMenu(buildTrayMenu());
}

function setupTray() {
  const icon = nativeImage.createFromBuffer(TRAY_ICON_BUF);
  tray = new Tray(icon);
  updateTray();
  // Show window on tray icon click / double-click
  tray.on('click',        () => { mainWindow?.show(); mainWindow?.focus(); });
  tray.on('double-click', () => { mainWindow?.show(); mainWindow?.focus(); });
}

// ─── Taskbar overlay badge (Windows) ─────────────────────────────────────────

function updateWindowBadge() {
  if (!mainWindow) return;
  if (unreadCount > 0) {
    mainWindow.setOverlayIcon(
      nativeImage.createFromBuffer(BADGE_ICON_BUF),
      String(unreadCount)
    );
  } else {
    mainWindow.setOverlayIcon(null, '');
  }
}

// ─── HTTP proxy (existing) ────────────────────────────────────────────────────

const WM_ENDPOINTS = [
  { host: 'worldmates.club', sni: null           },
  { host: '46.232.232.38',   sni: 'worldmates.club' },
];
const RETRY_CODES = new Set(['ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'UND_ERR_SOCKET']);

function requestVia(endpoint, url, method, headers, body) {
  return new Promise((resolve, reject) => {
    const reqHeaders = Object.assign({}, headers);
    if (endpoint.sni) reqHeaders['Host'] = url.hostname;
    if (body) reqHeaders['Content-Length'] = Buffer.byteLength(body, 'utf8').toString();

    const agent = new https.Agent({
      keepAlive: false,
      rejectUnauthorized: false,
      ...(endpoint.sni ? { servername: endpoint.sni } : {}),
    });

    const req = https.request(
      {
        hostname: endpoint.host,
        port:     parseInt(url.port) || 443,
        path:     url.pathname + url.search,
        method,
        headers:  reqHeaders,
        agent,
      },
      (res) => {
        const chunks = [];
        res.on('data',  c => chunks.push(c));
        res.on('end',   () => {
          const text = Buffer.concat(chunks).toString('utf8');
          resolve({ ok: res.statusCode >= 200 && res.statusCode < 300, status: res.statusCode, text });
        });
        res.on('error', reject);
      }
    );
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

ipcMain.handle('wm:request', async (_event, payload) => {
  let url;
  try { url = new URL(payload.url); } catch (e) { throw e; }

  const method  = payload.method  ?? 'GET';
  const headers = payload.headers ?? {};
  const body    = payload.body != null ? String(payload.body) : null;

  let lastErr;
  for (const endpoint of WM_ENDPOINTS) {
    try {
      return await requestVia(endpoint, url, method, headers, body);
    } catch (e) {
      const code = e.code ?? '';
      console.error(`[wm:request] ${endpoint.host}:${url.port} → ${code || e.message}`);
      if (RETRY_CODES.has(code)) { lastErr = e; continue; }
      throw e;
    }
  }
  throw lastErr;
});

// ─── Notification IPC ─────────────────────────────────────────────────────────

/**
 * wm:notify  { title: string, body: string, chatId?: number }
 * Shows a native desktop notification.
 * Skipped only when the window is both visible AND focused (user is actively
 * reading the app). When hidden, minimised, or another OS window has focus
 * the notification is always shown.
 */
ipcMain.handle('wm:notify', (_e, { title, body, chatId }) => {
  if (!Notification.isSupported()) return;

  // Suppress only when the user is actively looking at the app
  if (mainWindow && mainWindow.isVisible() && mainWindow.isFocused()) return;

  const n = new Notification({ title, body, silent: false });
  n.on('click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
      if (chatId != null) {
        mainWindow.webContents.send('app:open-chat', chatId);
      }
    }
  });
  n.show();
});

/**
 * wm:badge  { count: number }
 * Updates the tray tooltip and the Windows taskbar overlay icon.
 */
ipcMain.handle('wm:badge', (_e, { count }) => {
  unreadCount = Math.max(0, count);
  updateTray();
  updateWindowBadge();
});

/**
 * wm:set-language  'ru' | 'uk' | 'en'
 * Keeps the tray menu / tooltip in sync with the UI language.
 */
ipcMain.handle('wm:set-language', (_e, lang) => {
  if (lang === 'ru' || lang === 'uk' || lang === 'en') {
    mainLang = lang;
    updateTray();
  }
});

// ─── Window creation ──────────────────────────────────────────────────────────

function createWindow() {
  mainWindow = new BrowserWindow({
    width:      1440,
    height:     900,
    minWidth:   1080,
    minHeight:  700,
    backgroundColor: '#0e141f',
    title: 'WorldMates Messenger',
    icon: nativeImage.createFromBuffer(TRAY_ICON_BUF),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Hide to tray on close instead of quitting
  mainWindow.on('close', (e) => {
    if (!isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });

  if (isDev) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    const distPath = path.join(__dirname, '../dist/index.html');
    const fs = require('node:fs');
    if (!fs.existsSync(distPath)) {
      mainWindow.loadURL(
        `data:text/html,<body style="font-family:sans-serif;padding:40px;background:#0d1117;color:#e6edf3">` +
        `<h2>Build not found</h2>` +
        `<p>Run <code style="background:#21262d;padding:2px 6px;border-radius:4px">npm run dev</code> ` +
        `to start in development mode, or ` +
        `<code style="background:#21262d;padding:2px 6px;border-radius:4px">npm run build</code> ` +
        `then <code style="background:#21262d;padding:2px 6px;border-radius:4px">npm start</code> ` +
        `for production.</p>` +
        `<button onclick="window.close()" style="margin-top:20px;padding:8px 16px;background:#1f6feb;color:#fff;border:none;border-radius:6px;cursor:pointer">Close</button></body>`
      );
    } else {
      mainWindow.loadFile(distPath);
    }
  }
}

// ─── App lifecycle ────────────────────────────────────────────────────────────

app.whenReady().then(() => {
  setupTray();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('before-quit', () => { isQuitting = true; });

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
