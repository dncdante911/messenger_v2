const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('node:path');
const https = require('node:https');

const isDev = Boolean(process.env.VITE_DEV_SERVER_URL);

// Two endpoints to try in order.
// When connecting via raw IP, we still need:
//   - Host header = worldmates.club  (HTTP virtual-host routing)
//   - servername  = worldmates.club  (TLS SNI so the cert matches)
const WM_ENDPOINTS = [
  { host: 'worldmates.club', sni: null          },   // primary (DNS)
  { host: '46.232.232.38',   sni: 'worldmates.club' } // IP fallback
];

// Connection errors that warrant trying the next endpoint
const RETRY_CODES = new Set(['ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'UND_ERR_SOCKET']);

function requestVia(endpoint, url, method, headers, body) {
  return new Promise((resolve, reject) => {
    const reqHeaders = Object.assign({}, headers);
    if (endpoint.sni) reqHeaders['Host'] = url.hostname;   // force correct Host for IP routing
    if (body) reqHeaders['Content-Length'] = Buffer.byteLength(body, 'utf8').toString();

    const agent = new https.Agent({
      keepAlive: false,
      rejectUnauthorized: false,
      ...(endpoint.sni ? { servername: endpoint.sni } : {})
    });

    const req = https.request(
      {
        hostname: endpoint.host,
        port:     parseInt(url.port) || 443,
        path:     url.pathname + url.search,
        method,
        headers:  reqHeaders,
        agent
      },
      (res) => {
        const chunks = [];
        res.on('data',  (c) => chunks.push(c));
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

  const method = payload.method ?? 'GET';
  const headers = payload.headers ?? {};
  const body = payload.body != null ? String(payload.body) : null;

  let lastErr;
  for (const endpoint of WM_ENDPOINTS) {
    try {
      return await requestVia(endpoint, url, method, headers, body);
    } catch (e) {
      const code = e.code ?? '';
      console.error(`[wm:request] ${endpoint.host}:${url.port} → ${code || e.message}`);
      if (RETRY_CODES.has(code)) {
        lastErr = e;
        continue; // try next endpoint
      }
      throw e; // non-connection error (e.g. bad JSON) — don't retry
    }
  }
  throw lastErr;
});

function createWindow() {
  const window = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1080,
    minHeight: 700,
    backgroundColor: '#0e141f',
    title: 'WorldMates Messenger (Windows)',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  if (isDev) {
    window.loadURL(process.env.VITE_DEV_SERVER_URL);
    window.webContents.openDevTools({ mode: 'detach' });
  } else {
    const distPath = path.join(__dirname, '../dist/index.html');
    const fs = require('node:fs');
    if (!fs.existsSync(distPath)) {
      window.loadURL(`data:text/html,<body style="font-family:sans-serif;padding:40px;background:#0d1117;color:#e6edf3"><h2>Build not found</h2><p>Run <code style="background:#21262d;padding:2px 6px;border-radius:4px">npm run dev</code> to start in development mode, or <code style="background:#21262d;padding:2px 6px;border-radius:4px">npm run build</code> then <code style="background:#21262d;padding:2px 6px;border-radius:4px">npm start</code> for production.</p><button onclick="window.close()" style="margin-top:20px;padding:8px 16px;background:#1f6feb;color:#fff;border:none;border-radius:6px;cursor:pointer">Close</button></body>`);
    } else {
      window.loadFile(distPath);
    }
  }
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
