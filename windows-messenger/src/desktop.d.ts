export {};

declare global {
  interface Window {
    desktopApp?: {
      platform: string;
      appName: string;
      request?: (payload: {
        url: string;
        method?: string;
        headers?: Record<string, string>;
        body?: string;
      }) => Promise<{ ok: boolean; status: number; text: string }>;
    };
  }
}
