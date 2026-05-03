// Base colors — from Android Colors.kt
export const BaseColors = {
  // Primary brand (iOS-style blue)
  primary: '#0A84FF',
  primaryDark: '#0040DD',
  primaryLight: '#5AC8FA',

  // Secondary (cyan)
  secondary: '#00D4FF',
  secondaryDark: '#00A0C8',
  secondaryLight: '#64D2FF',

  // Background (dark)
  backgroundDark: '#0D1117',
  surfaceDark: '#161B22',
  surfaceElevatedDark: '#1C1E21',
  surfaceSearchDark: '#262C35',

  // Text (dark theme)
  textPrimaryDark: '#F0F2F5',
  textSecondaryDark: '#B0B3B8',
  textTertiary: '#8A8D91',

  // Messages
  messageBubbleOwnBlue: '#0A84FF',
  messageBubbleOtherDark: '#262C35',

  // Status
  online: '#00C851',
  away: '#FFBB33',
  busy: '#FF4444',
  offline: '#8A8D91',

  // Feedback
  success: '#00C851',
  error: '#FF4444',
  warning: '#FFBB33',
  info: '#33B5E5',

  // UI elements
  dividerDark: '#2F3336',
  unreadBadge: '#FF4444',
  typingIndicator: '#0A84FF',

  // Message status
  messageRead: '#00C851',
  messageDelivered: '#8A8D91',
  messageSent: '#B0B3B8',

  // Overlays
  overlay: 'rgba(0,0,0,0.8)',
  overlayLight: 'rgba(0,0,0,0.4)',

  // Avatar palette (from GroupAvatarColors)
  avatarColors: [
    '#FF6B9D', '#C446FF', '#0084FF', '#00C0FF',
    '#00E0A6', '#00C851', '#FFC107', '#FF9500',
    '#FF6347', '#FF1744', '#9C27B0', '#3F51B5',
    '#00BCD4', '#4CAF50', '#FF5722',
  ],

  // Media type colors
  mediaImage: '#00C851',
  mediaVideo: '#0A84FF',
  mediaAudio: '#FF9500',
  mediaFile: '#8E8E93',

  // Always
  white: '#FFFFFF',
  black: '#000000',
  transparent: 'transparent',
} as const;

// Theme variant palettes — from Android ThemeVariant.kt getPalette()
export const ThemePalettes = {
  CLASSIC: {
    primary: '#1565C0',
    primaryDark: '#003C8F',
    primaryLight: '#5E92F3',
    secondary: '#0288D1',
    messageBubbleOwn: '#1976D2',
    messageBubbleOther: '#262C35',
    accent: '#4FC3F7',
    background: '#0D1B3E',
    surface: '#12254F',
    surfaceElevated: '#1A3A6B',
    tabBar: '#0D1B3E',
    name: 'Classic Blue',
    emoji: '💙',
    isPremium: false,
  },
  OCEAN: {
    primary: '#00695C',
    primaryDark: '#004D40',
    primaryLight: '#4DB6AC',
    secondary: '#0277BD',
    messageBubbleOwn: '#00796B',
    messageBubbleOther: '#1A2E2C',
    accent: '#1DE9B6',
    background: '#001A12',
    surface: '#002A1E',
    surfaceElevated: '#003D2E',
    tabBar: '#001A12',
    name: 'Deep Ocean',
    emoji: '🌊',
    isPremium: false,
  },
  PURPLE: {
    primary: '#7B1FA2',
    primaryDark: '#4A0072',
    primaryLight: '#BA68C8',
    secondary: '#8E24AA',
    messageBubbleOwn: '#7B1FA2',
    messageBubbleOther: '#2A1A2E',
    accent: '#E040FB',
    background: '#12002B',
    surface: '#1E0040',
    surfaceElevated: '#2A0050',
    tabBar: '#12002B',
    name: 'Violet Dream',
    emoji: '💜',
    isPremium: false,
  },
  MONOCHROME: {
    primary: '#455A64',
    primaryDark: '#263238',
    primaryLight: '#78909C',
    secondary: '#546E7A',
    messageBubbleOwn: '#37474F',
    messageBubbleOther: '#2A2F32',
    accent: '#78909C',
    background: '#0F1417',
    surface: '#1C262B',
    surfaceElevated: '#263238',
    tabBar: '#0F1417',
    name: 'Dark Slate',
    emoji: '🖤',
    isPremium: false,
  },
  NORD: {
    primary: '#5E81AC',
    primaryDark: '#4C566A',
    primaryLight: '#81A1C1',
    secondary: '#88C0D0',
    messageBubbleOwn: '#5E81AC',
    messageBubbleOther: '#3B4252',
    accent: '#88C0D0',
    background: '#2E3440',
    surface: '#3B4252',
    surfaceElevated: '#434C5E',
    tabBar: '#2E3440',
    name: 'Nord Frost',
    emoji: '❄️',
    isPremium: false,
  },
  DRACULA: {
    primary: '#BD93F9',
    primaryDark: '#9B6EE8',
    primaryLight: '#D4B5FF',
    secondary: '#FF79C6',
    messageBubbleOwn: '#BD93F9',
    messageBubbleOther: '#44475A',
    accent: '#50FA7B',
    background: '#1E1F29',
    surface: '#282A36',
    surfaceElevated: '#373844',
    tabBar: '#1E1F29',
    name: 'Dracula Night',
    emoji: '🦇',
    isPremium: false,
  },
  MATERIAL_YOU: {
    primary: '#6750A4',
    primaryDark: '#4F378B',
    primaryLight: '#9A82DB',
    secondary: '#625B71',
    messageBubbleOwn: '#6750A4',
    messageBubbleOther: '#2B2930',
    accent: '#6750A4',
    background: '#1C1B1F',
    surface: '#2B2930',
    surfaceElevated: '#3A3740',
    tabBar: '#1C1B1F',
    name: 'Material You',
    emoji: '🎨',
    isPremium: false,
  },
} as const;

export type ThemeVariantKey = keyof typeof ThemePalettes;
