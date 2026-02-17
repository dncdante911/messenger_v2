const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopApp', {
  platform: process.platform,
  appName: 'WorldMates Windows Messenger',
  request: (payload) => ipcRenderer.invoke('wm:request', payload)
});
