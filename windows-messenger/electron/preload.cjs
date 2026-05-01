const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('desktopApp', {
  platform: process.platform,
  appName:  'WorldMates Messenger',

  /** Proxy an HTTPS request through the main process (bypasses CORS). */
  request: (payload) => ipcRenderer.invoke('wm:request', payload),

  /**
   * Upload a file via multipart/form-data through the main process (bypasses CORS).
   * Reads the entire file into an ArrayBuffer — suitable for files up to ~50 MB.
   * @param {{ urlStr, token, fields, fileName, fileMime, fileData: ArrayBuffer }} payload
   */
  upload: (payload) => ipcRenderer.invoke('wm:upload', payload),

  /**
   * Get the real filesystem path for a File object (Electron 28+).
   * Call this before transferring the File through any async boundary.
   * @param {File} file
   * @returns {string}
   */
  getFilePath: (file) => webUtils.getPathForFile(file),

  /**
   * Stream a large file to the server without copying it into renderer memory.
   * Requires filePath from getFilePath(). Used for files > 50 MB.
   * @param {{ urlStr, token, fields, fileName, fileMime, filePath: string }} payload
   */
  uploadLargeFile: (payload) => ipcRenderer.invoke('wm:upload-large', payload),

  /**
   * Check if a remote URL is already in the local media cache.
   * @param {string} url
   * @returns {Promise<string | null>}  wm-cache:///key or null
   */
  cacheGet: (url) => ipcRenderer.invoke('wm:cache-get', url),

  /**
   * Download a remote URL into the local media cache and return its local URL.
   * The download happens entirely in the main process.
   * @param {string} url
   * @returns {Promise<string | null>}  wm-cache:///key or null on error
   */
  cachePut: (url) => ipcRenderer.invoke('wm:cache-put', url),

  /**
   * Return cache statistics: { count, totalBytes, maxBytes }.
   * @returns {Promise<{ count: number; totalBytes: number; maxBytes: number }>}
   */
  cacheStats: () => ipcRenderer.invoke('wm:cache-stats'),

  /** Delete all cached media files. */
  cacheClear: () => ipcRenderer.invoke('wm:cache-clear'),

  /**
   * Show a native desktop notification.
   * The main process only shows it when the window is not focused.
   * @param {{ title: string, body: string, chatId?: number }} payload
   */
  notify: (payload) => ipcRenderer.invoke('wm:notify', payload),

  /**
   * Set the unread-messages badge on the tray icon and taskbar overlay.
   * @param {number} count
   */
  setBadge: (count) => ipcRenderer.invoke('wm:badge', { count }),

  /**
   * Tell the main process which language is active so the tray menu stays
   * in sync with the UI language.
   * @param {'ru' | 'uk' | 'en'} lang
   */
  setLanguage: (lang) => ipcRenderer.invoke('wm:set-language', lang),

  /**
   * Register a callback that fires when the user clicks a notification and
   * the main process asks us to open a specific chat.
   * @param {(chatId: number) => void} callback
   */
  onOpenChat: (callback) => {
    ipcRenderer.on('app:open-chat', (_event, chatId) => callback(chatId));
  },

  /** Remove all 'app:open-chat' listeners (call on cleanup). */
  offOpenChat: () => {
    ipcRenderer.removeAllListeners('app:open-chat');
  },
});
