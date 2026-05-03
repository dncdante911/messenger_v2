import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { BaseColors, ThemePalettes, ThemeVariantKey } from './colors';

export interface ThemeColors {
  // Backgrounds
  background: string;
  surface: string;
  surfaceElevated: string;
  inputBackground: string;
  tabBar: string;

  // Text
  text: string;
  textSecondary: string;
  textTertiary: string;
  textInverse: string;

  // Brand
  primary: string;
  primaryDark: string;
  primaryLight: string;
  secondary: string;
  accent: string;

  // Messages
  messageBubbleOwn: string;
  messageBubbleOther: string;
  messageBubbleOwnText: string;
  messageBubbleOtherText: string;

  // UI elements
  divider: string;
  border: string;
  badge: string;
  online: string;
  offline: string;

  // Feedback
  error: string;
  success: string;
  warning: string;

  // Status
  messageRead: string;
  messageDelivered: string;
  messageSent: string;

  // Misc
  overlay: string;
  white: string;
  black: string;
  transparent: string;
  isDark: boolean;
  variant: ThemeVariantKey;
}

function buildTheme(variant: ThemeVariantKey): ThemeColors {
  const palette = ThemePalettes[variant];
  return {
    background: palette.background,
    surface: palette.surface,
    surfaceElevated: palette.surfaceElevated,
    inputBackground: BaseColors.surfaceSearchDark,
    tabBar: palette.tabBar,
    text: BaseColors.textPrimaryDark,
    textSecondary: BaseColors.textSecondaryDark,
    textTertiary: BaseColors.textTertiary,
    textInverse: BaseColors.black,
    primary: palette.primary,
    primaryDark: palette.primaryDark,
    primaryLight: palette.primaryLight,
    secondary: palette.secondary,
    accent: palette.accent,
    messageBubbleOwn: palette.messageBubbleOwn,
    messageBubbleOther: palette.messageBubbleOther,
    messageBubbleOwnText: BaseColors.white,
    messageBubbleOtherText: BaseColors.textPrimaryDark,
    divider: BaseColors.dividerDark,
    border: BaseColors.dividerDark,
    badge: BaseColors.unreadBadge,
    online: BaseColors.online,
    offline: BaseColors.offline,
    error: BaseColors.error,
    success: BaseColors.success,
    warning: BaseColors.away,
    messageRead: BaseColors.messageRead,
    messageDelivered: BaseColors.messageDelivered,
    messageSent: BaseColors.messageSent,
    overlay: BaseColors.overlay,
    white: BaseColors.white,
    black: BaseColors.black,
    transparent: BaseColors.transparent,
    isDark: true,
    variant,
  };
}

interface ThemeState {
  variant: ThemeVariantKey;
  colors: ThemeColors;
  setVariant: (v: ThemeVariantKey) => Promise<void>;
  _hydrate: () => Promise<void>;
}

export const useThemeStore = create<ThemeState>((set) => ({
  variant: 'CLASSIC',
  colors: buildTheme('CLASSIC'),
  setVariant: async (v: ThemeVariantKey) => {
    await AsyncStorage.setItem('app_theme', v);
    set({ variant: v, colors: buildTheme(v) });
  },
  _hydrate: async () => {
    const stored = await AsyncStorage.getItem('app_theme');
    if (stored && stored in ThemePalettes) {
      const v = stored as ThemeVariantKey;
      set({ variant: v, colors: buildTheme(v) });
    }
  },
}));

// Main hook used in components
export function useTheme(): ThemeColors {
  return useThemeStore((s) => s.colors);
}

// Static access for non-component code
export const defaultTheme = buildTheme('CLASSIC');
export { BaseColors, ThemePalettes, ThemeVariantKey };
