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

      /** Upload a file as multipart/form-data through the Electron main process (no CORS). */
      upload?: (payload: {
        urlStr:   string;
        token:    string;
        fields:   Record<string, string>;
        fileName: string;
        fileMime: string;
        fileData: ArrayBuffer;
      }) => Promise<{ ok: boolean; status: number; text: string }>;

      /**
       * Get the real OS filesystem path for a File object (Electron 28+).
       * Must be called before the File is transferred across async boundaries.
       */
      getFilePath?: (file: File) => string;

      /**
       * Stream a large file (> 50 MB) to the server via fs.createReadStream
       * in the main process — keeps the renderer responsive.
       */
      uploadLargeFile?: (payload: {
        urlStr:   string;
        token:    string;
        fields:   Record<string, string>;
        fileName: string;
        fileMime: string;
        filePath: string;
      }) => Promise<{ ok: boolean; status: number; text: string }>;

      /**
       * Check whether a remote URL is already in the local media cache.
       * Returns a wm-cache:// URL if cached, null otherwise.
       */
      cacheGet?: (url: string) => Promise<string | null>;

      /**
       * Download a remote URL into the local media cache and return its
       * wm-cache:// URL, or null on error.  Download runs in the main process.
       */
      cachePut?: (url: string) => Promise<string | null>;

      /** Return cache stats: file count, total bytes used, and the 2 GB cap. */
      cacheStats?: () => Promise<{ count: number; totalBytes: number; maxBytes: number }>;

      /** Delete all locally-cached media files. */
      cacheClear?: () => Promise<void>;

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
