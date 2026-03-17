const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('node:path');
const https = require('node:https');

const isDev = Boolean(process.env.VITE_DEV_SERVER_URL);

// Use https.request (HTTP/1.1) instead of fetch/undici which may attempt
// HTTP/2 or have TLS negotiation issues with worldmates.club:449.
// A new agent per request (keepAlive: false) avoids ECONNRESET from
// the server closing pooled connections prematurely.
ipcMain.handle('wm:request', (_event, payload) => {
  return new Promise((resolve, reject) => {
    let url;
    try { url = new URL(payload.url); } catch (e) { return reject(e); }

    const body    = payload.body != null ? String(payload.body) : null;
    const headers = Object.assign({}, payload.headers ?? {});

    if (body) {
      headers['Content-Length'] = Buffer.byteLength(body, 'utf8').toString();
    }

    const agent = new https.Agent({ keepAlive: false, rejectUnauthorized: false });

    const req = https.request(
      {
        hostname: url.hostname,
        port:     parseInt(url.port) || 443,
        path:     url.pathname + url.search,
        method:   payload.method ?? 'GET',
        headers,
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
