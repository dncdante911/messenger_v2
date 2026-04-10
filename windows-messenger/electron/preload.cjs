const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopApp', {
  platform: process.platform,
  appName:  'WorldMates Messenger',

  /** Proxy an HTTPS request through the main process (bypasses CORS). */
  request: (payload) => ipcRenderer.invoke('wm:request', payload),

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
