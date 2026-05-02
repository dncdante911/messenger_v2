// ============================================================
// WorldMates Messenger — Avatar Component
//
// Shows a rounded user photo (via expo-image).
// Falls back to coloured initials when no URI is provided.
// Optionally overlays a green online-status dot.
// ============================================================

import React, { useMemo } from 'react';
import { View, Text, StyleSheet, StyleProp, ViewStyle } from 'react-native';
import { Image } from 'expo-image';
import { MEDIA_BASE_URL } from '../../constants/api';

// ─────────────────────────────────────────────────────────────
// PALETTE — deterministic background colours from name hash
// ─────────────────────────────────────────────────────────────

const AVATAR_COLORS = [
  '#F28B82',
  '#FBBC04',
  '#34A853',
  '#4ECDC4',
  '#7C83FD',
  '#FF7EB3',
  '#56A0D3',
  '#A37CC8',
  '#FF9F43',
  '#26C6DA',
];

function hashName(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) & 0xffffffff;
  }
  return Math.abs(h);
}

function getAvatarColor(name: string): string {
  if (!name) return AVATAR_COLORS[0];
  return AVATAR_COLORS[hashName(name) % AVATAR_COLORS.length];
}

function getInitials(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return '?';
  const parts = trimmed.split(/\s+/);
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

/** Resolve a potentially relative URL to an absolute one. */
function resolveUri(uri: string): string {
  if (!uri) return '';
  if (uri.startsWith('http://') || uri.startsWith('https://') || uri.startsWith('file://')) {
    return uri;
  }
  return `${MEDIA_BASE_URL}${uri.startsWith('/') ? uri.slice(1) : uri}`;
}

// ─────────────────────────────────────────────────────────────
// PROPS
// ─────────────────────────────────────────────────────────────

export interface AvatarProps {
  uri?: string;
  name?: string;
  size?: number;
  showOnline?: boolean;
  isOnline?: boolean;
  style?: StyleProp<ViewStyle>;
}

// ─────────────────────────────────────────────────────────────
// COMPONENT
// ─────────────────────────────────────────────────────────────

export const Avatar: React.FC<AvatarProps> = ({
  uri,
  name = '',
  size = 44,
  showOnline = false,
  isOnline = false,
  style,
}) => {
  const resolvedUri = useMemo(() => (uri ? resolveUri(uri) : ''), [uri]);
  const bgColor = useMemo(() => getAvatarColor(name), [name]);
  const initials = useMemo(() => getInitials(name), [name]);
  const fontSize = useMemo(() => Math.round(size * 0.38), [size]);

  const dotSize = 10;
  const dotOffset = 0;

  return (
    <View style={[{ width: size, height: size }, style]}>
      {resolvedUri ? (
        <Image
          source={{ uri: resolvedUri }}
          style={[styles.image, { width: size, height: size, borderRadius: size / 2 }]}
          contentFit="cover"
          transition={150}
          cachePolicy="memory-disk"
        />
      ) : (
        <View
          style={[
            styles.fallback,
            { width: size, height: size, borderRadius: size / 2, backgroundColor: bgColor },
          ]}
        >
          <Text style={[styles.initials, { fontSize }]}>{initials}</Text>
        </View>
      )}

      {showOnline && (
        <View
          style={[
            styles.onlineDot,
            {
              width: dotSize,
              height: dotSize,
              borderRadius: dotSize / 2,
              bottom: dotOffset,
              right: dotOffset,
              backgroundColor: isOnline ? '#34C759' : '#8E8E93',
            },
          ]}
        />
      )}
    </View>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  image: {
    backgroundColor: '#2A2B3D',
  },
  fallback: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    color: '#FFFFFF',
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  onlineDot: {
    position: 'absolute',
    borderWidth: 2,
    borderColor: '#1A1B2E',
  },
});
