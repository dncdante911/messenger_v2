export {};

declare global {
  interface Window {
    desktopApp?: {
      platform: string;
      appName:  string;

      /** Proxy an HTTPS request through the Electron main process. */
      request?: (payload: {
        url:      string;
        method?:  string;
        headers?: Record<string, string>;
        body?:    string;
      }) => Promise<{ ok: boolean; status: number; text: string }>;

      /** Show a native desktop / tray notification. */
      notify?: (payload: {
        title:   string;
        body:    string;
        chatId?: number;
      }) => Promise<void>;

      /** Update the unread-messages badge on the tray icon and taskbar. */
      setBadge?: (count: number) => Promise<void>;

      /**
       * Sync the active UI language to the main process so that tray menu
       * items use the correct language.
       */
      setLanguage?: (lang: 'ru' | 'uk' | 'en') => Promise<void>;

      /**
       * Register a callback invoked when the user clicks a notification and
       * the main process wants to open a specific chat.
       */
      onOpenChat?: (callback: (chatId: number) => void) => void;

      /** Remove all open-chat listeners (cleanup on unmount). */
      offOpenChat?: () => void;
    };
  }
}
