import { useCallback } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import uk, { TranslationKeys } from './uk';
import ru from './ru';
import en from './en';

export type Language = 'uk' | 'ru' | 'en';

const translations = { uk, ru, en } as const;

interface I18nState {
  language: Language;
  setLanguage: (lang: Language) => Promise<void>;
  _hydrate: () => Promise<void>;
}

export const useI18nStore = create<I18nState>((set) => ({
  language: 'uk',
  setLanguage: async (lang: Language) => {
    await AsyncStorage.setItem('app_language', lang);
    set({ language: lang });
  },
  _hydrate: async () => {
    const stored = await AsyncStorage.getItem('app_language');
    if (stored && (stored === 'uk' || stored === 'ru' || stored === 'en')) {
      set({ language: stored });
    }
  },
}));

// Hook: useTranslation
export function useTranslation() {
  const language = useI18nStore((s) => s.language);
  const setLanguage = useI18nStore((s) => s.setLanguage);

  const t = useCallback(
    (key: TranslationKeys, params?: Record<string, string | number>): string => {
      const dict = translations[language] as Record<string, string>;
      let str = dict[key] ?? (translations.en as Record<string, string>)[key] ?? key;
      if (params) {
        Object.entries(params).forEach(([k, v]) => {
          str = str.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
        });
      }
      return str;
    },
    [language],
  );

  return { t, language, setLanguage };
}

// Non-hook version (for use outside components, e.g. in stores/services)
export function getTranslation(key: TranslationKeys, language: Language = 'uk'): string {
  const dict = translations[language] as Record<string, string>;
  return dict[key] ?? (translations.en as Record<string, string>)[key] ?? key;
}

export { TranslationKeys };
