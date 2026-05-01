'use strict';

const { app, BrowserWindow, ipcMain, Tray, Menu, Notification, nativeImage, protocol, net } = require('electron');
const path   = require('node:path');
const https  = require('node:https');
const zlib   = require('node:zlib');
const fs     = require('node:fs');
const crypto = require('node:crypto');
const { pathToFileURL } = require('node:url');

// wm-cache:// serves local cached media files; must register before app is ready.
protocol.registerSchemesAsPrivileged([
  { scheme: 'wm-cache', privileges: { standard: true, secure: true, stream: true, supportFetchAPI: true } },
]);

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
  { host: 'worldmates.club',   sni: null              },
  { host: '46.232.232.38',     sni: 'worldmates.club' },
  { host: '93.171.188.229',    sni: 'worldmates.club' },
];
const RETRY_CODES = new Set(['ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'UND_ERR_SOCKET']);

// body can be string (text) or Buffer (binary)
function requestVia(endpoint, url, method, headers, body) {
  return new Promise((resolve, reject) => {
    const reqHeaders = Object.assign({}, headers);
    if (endpoint.sni) reqHeaders['Host'] = url.hostname;

    const bodyBuf = body === null || body === undefined ? null
      : Buffer.isBuffer(body) ? body
      : Buffer.from(body, 'utf8');

    if (bodyBuf) reqHeaders['Content-Length'] = bodyBuf.length.toString();

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
    if (bodyBuf) req.write(bodyBuf);
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

// ─── Binary upload proxy (bypasses CORS for multipart/form-data) ─────────────

const UPLOAD_BOUNDARY = '----WMFormBoundary7MA4YWxkTrZu0gW';

function buildMultipartBody(fields, fileName, fileMime, fileData) {
  const parts = [];
  for (const [name, value] of Object.entries(fields || {})) {
    parts.push(Buffer.from(
      `--${UPLOAD_BOUNDARY}\r\n` +
      `Content-Disposition: form-data; name="${name}"\r\n\r\n` +
      `${value}\r\n`
    ));
  }
  parts.push(Buffer.from(
    `--${UPLOAD_BOUNDARY}\r\n` +
    `Content-Disposition: form-data; name="file"; filename="${fileName}"\r\n` +
    `Content-Type: ${fileMime}\r\n\r\n`
  ));
  // fileData arrives as ArrayBuffer (structured clone from renderer)
  parts.push(Buffer.from(fileData));
  parts.push(Buffer.from(`\r\n--${UPLOAD_BOUNDARY}--\r\n`));
  return Buffer.concat(parts);
}

/** Builds only the multipart preamble (fields + file header). Used by the streaming upload. */
function buildMultipartHeader(fields, fileName, fileMime) {
  const parts = [];
  for (const [name, value] of Object.entries(fields || {})) {
    parts.push(Buffer.from(
      `--${UPLOAD_BOUNDARY}\r\n` +
      `Content-Disposition: form-data; name="${name}"\r\n\r\n` +
      `${value}\r\n`
    ));
  }
  parts.push(Buffer.from(
    `--${UPLOAD_BOUNDARY}\r\n` +
    `Content-Disposition: form-data; name="file"; filename="${fileName}"\r\n` +
    `Content-Type: ${fileMime}\r\n\r\n`
  ));
  return Buffer.concat(parts);
}

const MULTIPART_FOOTER = Buffer.from(`\r\n--${UPLOAD_BOUNDARY}--\r\n`);

/** Stream a large file to the server without loading it into memory. */
function uploadStreamVia(endpoint, url, headers, headerBuf, filePath, footerBuf) {
  return new Promise((resolve, reject) => {
    const reqHeaders = Object.assign({}, headers);
    if (endpoint.sni) reqHeaders['Host'] = url.hostname;

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
        method:   'POST',
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
    req.write(headerBuf);

    const stream = fs.createReadStream(filePath);
    stream.on('data',  chunk => { if (!req.write(chunk)) stream.pause(); });
    req.on('drain',    ()    => stream.resume());
    stream.on('end',   ()    => { req.write(footerBuf); req.end(); });
    stream.on('error', err   => { req.destroy(err); reject(err); });
  });
}

/**
 * wm:upload-large  { urlStr, token, fields, fileName, fileMime, filePath }
 * Streams a file from the local filesystem to the server using fs.createReadStream.
 * filePath comes from webUtils.getPathForFile() in the preload — never from user text input.
 */
ipcMain.handle('wm:upload-large', async (_event, { urlStr, token, fields, fileName, fileMime, filePath }) => {
  if (!path.isAbsolute(filePath)) throw new Error('filePath must be absolute');

  let url;
  try { url = new URL(urlStr); } catch (e) { throw e; }

  const stat       = fs.statSync(filePath);
  const headerBuf  = buildMultipartHeader(fields, fileName, fileMime);
  const footerBuf  = MULTIPART_FOOTER;
  const totalSize  = headerBuf.length + stat.size + footerBuf.length;

  const headers = {
    'access-token': token,
    'Content-Type': `multipart/form-data; boundary=${UPLOAD_BOUNDARY}`,
    'Content-Length': String(totalSize),
  };

  let lastErr;
  for (const endpoint of WM_ENDPOINTS) {
    try {
      return await uploadStreamVia(endpoint, url, headers, headerBuf, filePath, footerBuf);
    } catch (e) {
      const code = e.code ?? '';
      console.error(`[wm:upload-large] ${endpoint.host} → ${code || e.message}`);
      if (RETRY_CODES.has(code)) { lastErr = e; continue; }
      throw e;
    }
  }
  throw lastErr;
});

ipcMain.handle('wm:upload', async (_event, { urlStr, token, fields, fileName, fileMime, fileData }) => {
  let url;
  try { url = new URL(urlStr); } catch (e) { throw e; }

  const body    = buildMultipartBody(fields, fileName, fileMime, fileData);
  const headers = {
    'access-token': token,
    'Content-Type': `multipart/form-data; boundary=${UPLOAD_BOUNDARY}`,
  };

  let lastErr;
  for (const endpoint of WM_ENDPOINTS) {
    try {
      return await requestVia(endpoint, url, 'POST', headers, body);
    } catch (e) {
      const code = e.code ?? '';
      console.error(`[wm:upload] ${endpoint.host} → ${code || e.message}`);
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

// ─── Media cache ─────────────────────────────────────────────────────────────
// Files cached in <userData>/media-cache/, keyed by MD5(url)+ext.
// LRU eviction by atime when total exceeds 2 GB.

const CACHE_MAX_BYTES = 2 * 1024 * 1024 * 1024;
/** @type {string | null} */
let CACHE_DIR = null;

function urlToCacheKey(urlStr) {
  const hash = crypto.createHash('md5').update(urlStr).digest('hex');
  let ext = '';
  try { ext = path.extname(new URL(urlStr).pathname); } catch {}
  return hash + (ext || '');
}

function wmCacheUrl(key) {
  return `wm-cache:///${encodeURIComponent(key)}`;
}

function evictCacheIfNeeded() {
  if (!CACHE_DIR) return;
  try {
    const entries = fs.readdirSync(CACHE_DIR).map(f => {
      const fp = path.join(CACHE_DIR, f);
      const s  = fs.statSync(fp);
      return { fp, size: s.size, atime: s.atimeMs };
    }).sort((a, b) => a.atime - b.atime);  // oldest first

    let total = entries.reduce((s, e) => s + e.size, 0);
    for (const entry of entries) {
      if (total <= CACHE_MAX_BYTES) break;
      try { fs.unlinkSync(entry.fp); } catch {}
      total -= entry.size;
    }
  } catch {}
}

/** Binary GET through the endpoint failover chain. */
function downloadBinaryVia(endpoint, url) {
  return new Promise((resolve, reject) => {
    const reqHeaders = {};
    if (endpoint.sni) reqHeaders['Host'] = url.hostname;

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
        method:   'GET',
        headers:  reqHeaders,
        agent,
      },
      (res) => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          res.resume();
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        const chunks = [];
        res.on('data',  c => chunks.push(c));
        res.on('end',   () => resolve(Buffer.concat(chunks)));
        res.on('error', reject);
      }
    );
    req.on('error', reject);
    req.end();
  });
}

async function downloadBinary(urlStr) {
  let url;
  try { url = new URL(urlStr); } catch (e) { throw e; }
  let lastErr;
  for (const endpoint of WM_ENDPOINTS) {
    try {
      return await downloadBinaryVia(endpoint, url);
    } catch (e) {
      const code = e.code ?? '';
      if (RETRY_CODES.has(code)) { lastErr = e; continue; }
      throw e;
    }
  }
  throw lastErr;
}

/**
 * wm:cache-get  urlStr → wm-cache:///key  or  null
 * Returns the local cache URL if the asset is already cached, null otherwise.
 */
ipcMain.handle('wm:cache-get', (_e, urlStr) => {
  if (!CACHE_DIR || !urlStr) return null;
  const key = urlToCacheKey(urlStr);
  const fp  = path.join(CACHE_DIR, key);
  if (!fs.existsSync(fp)) return null;
  try {
    const now = new Date();
    fs.utimesSync(fp, now, fs.statSync(fp).mtime);
  } catch {}
  return wmCacheUrl(key);
});

/**
 * wm:cache-put  urlStr → wm-cache:///key  or  null
 * Downloads the asset from the server (in the main process) and writes it to cache.
 */
ipcMain.handle('wm:cache-put', async (_e, urlStr) => {
  if (!CACHE_DIR || !urlStr) return null;
  const key = urlToCacheKey(urlStr);
  const fp  = path.join(CACHE_DIR, key);
  if (fs.existsSync(fp)) {
    try { const now = new Date(); fs.utimesSync(fp, now, fs.statSync(fp).mtime); } catch {}
    return wmCacheUrl(key);
  }
  try {
    const buf = await downloadBinary(urlStr);
    fs.writeFileSync(fp, buf);
    evictCacheIfNeeded();
    return wmCacheUrl(key);
  } catch (e) {
    console.error('[wm:cache-put]', urlStr, e.message);
    return null;
  }
});

/** wm:cache-stats → { count, totalBytes, maxBytes } */
ipcMain.handle('wm:cache-stats', () => {
  if (!CACHE_DIR) return { count: 0, totalBytes: 0, maxBytes: CACHE_MAX_BYTES };
  try {
    const files      = fs.readdirSync(CACHE_DIR);
    const totalBytes = files.reduce((s, f) => {
      try { return s + fs.statSync(path.join(CACHE_DIR, f)).size; } catch { return s; }
    }, 0);
    return { count: files.length, totalBytes, maxBytes: CACHE_MAX_BYTES };
  } catch {
    return { count: 0, totalBytes: 0, maxBytes: CACHE_MAX_BYTES };
  }
});

/** wm:cache-clear — deletes all cached files. */
ipcMain.handle('wm:cache-clear', () => {
  if (!CACHE_DIR) return;
  try {
    for (const f of fs.readdirSync(CACHE_DIR)) {
      try { fs.unlinkSync(path.join(CACHE_DIR, f)); } catch {}
    }
  } catch {}
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
      autoplayPolicy: 'no-user-gesture-required',
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
  // Initialise cache directory
  CACHE_DIR = path.join(app.getPath('userData'), 'media-cache');
  fs.mkdirSync(CACHE_DIR, { recursive: true });

  // Serve cached media as wm-cache:///key — stays local, never hits the network
  protocol.handle('wm-cache', (request) => {
    const filename = decodeURIComponent(new URL(request.url).pathname.slice(1));
    const filePath = path.join(CACHE_DIR, filename);
    return net.fetch(pathToFileURL(filePath).toString());
  });

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
