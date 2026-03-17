const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('node:path');

const isDev = Boolean(process.env.VITE_DEV_SERVER_URL);

ipcMain.handle('wm:request', async (_event, payload) => {
  const response = await fetch(payload.url, {
    method: payload.method ?? 'GET',
    headers: payload.headers ?? {},
    body: payload.body
  });

  const text = await response.text();

  return {
    ok: response.ok,
    status: response.status,
    text
  };
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
      // No build found — load a helpful error page
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
