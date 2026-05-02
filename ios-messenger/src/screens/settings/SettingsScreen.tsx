// ============================================================
// WorldMates Messenger — SettingsScreen
// Tab 4 of the MainNavigator — full settings menu.
// ============================================================

import React, { useCallback } from 'react';
import {
  Alert,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';

import { RootStackParamList, MainTabParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';
import { getMediaUrl } from '../../utils/mediaUtils';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Settings'>,
  NativeStackScreenProps<RootStackParamList>
>;

// ─────────────────────────────────────────────────────────────
// COLORS
// ─────────────────────────────────────────────────────────────

const COLORS = {
  bg: '#1A1B2E',
  card: '#2A2B3D',
  accent: '#7C83FD',
  white: '#FFFFFF',
  secondary: '#8E8E93',
  separator: '#3A3B4D',
  danger: '#FF4D4F',
};

// ─────────────────────────────────────────────────────────────
// SUB-COMPONENTS
// ─────────────────────────────────────────────────────────────

interface SettingsRowProps {
  icon: React.ComponentProps<typeof Feather>['name'];
  label: string;
  value?: string;
  onPress: () => void;
  danger?: boolean;
  isLast?: boolean;
}

const SettingsRow: React.FC<SettingsRowProps> = ({
  icon,
  label,
  value,
  onPress,
  danger = false,
  isLast = false,
}) => (
  <TouchableOpacity
    style={[styles.row, isLast && styles.rowLast]}
    onPress={onPress}
    activeOpacity={0.7}
  >
    <View style={styles.rowLeft}>
      <View style={[styles.iconWrap, danger && styles.iconWrapDanger]}>
        <Feather name={icon} size={18} color={danger ? COLORS.danger : COLORS.accent} />
      </View>
      <Text style={[styles.rowLabel, danger && styles.rowLabelDanger]}>{label}</Text>
    </View>
    <View style={styles.rowRight}>
      {value ? <Text style={styles.rowValue}>{value}</Text> : null}
      <Feather name="chevron-right" size={18} color={COLORS.secondary} />
    </View>
    {!isLast && <View style={styles.rowSeparator} />}
  </TouchableOpacity>
);

interface SectionProps {
  title: string;
  children: React.ReactNode;
}

const Section: React.FC<SectionProps> = ({ title, children }) => (
  <View style={styles.section}>
    <Text style={styles.sectionHeader}>{title}</Text>
    <View style={styles.sectionCard}>{children}</View>
  </View>
);

// ─────────────────────────────────────────────────────────────
// MAIN COMPONENT
// ─────────────────────────────────────────────────────────────

const SettingsScreen: React.FC<Props> = ({ navigation }) => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const comingSoon = useCallback((feature: string) => {
    Alert.alert('Coming soon', `${feature} is not yet available.`);
  }, []);

  const handleLogout = useCallback(() => {
    Alert.alert(
      'Log Out',
      'Are you sure you want to log out?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Log Out',
          style: 'destructive',
          onPress: () => {
            logout().catch(() => {
              Alert.alert('Error', 'Logout failed. Please try again.');
            });
          },
        },
      ],
    );
  }, [logout]);

  const handleDeleteAccount = useCallback(() => {
    Alert.alert(
      'Delete Account',
      'This action is permanent and cannot be undone. All your data will be deleted.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => comingSoon('Account deletion'),
        },
      ],
    );
  }, [comingSoon]);

  const handleGoToProfile = useCallback(() => {
    if (!user) return;
    navigation.navigate('UserProfile', { userId: user.id });
  }, [navigation, user]);

  // ─────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {/* ── Profile card ── */}
      <TouchableOpacity
        style={styles.profileCard}
        onPress={handleGoToProfile}
        activeOpacity={0.8}
      >
        {user?.avatar ? (
          <Image
            source={{ uri: getMediaUrl(user.avatar) }}
            style={styles.profileAvatar}
          />
        ) : (
          <View style={[styles.profileAvatar, styles.profileAvatarPlaceholder]}>
            <Feather name="user" size={26} color={COLORS.secondary} />
          </View>
        )}
        <View style={styles.profileInfo}>
          <Text style={styles.profileName}>{user?.name ?? 'Loading…'}</Text>
          <Text style={styles.profileUsername}>@{user?.username ?? ''}</Text>
        </View>
        <Feather name="chevron-right" size={20} color={COLORS.secondary} />
      </TouchableOpacity>

      {/* ── Account ── */}
      <Section title="ACCOUNT">
        <SettingsRow
          icon="user"
          label="Edit Profile"
          onPress={() => comingSoon('Edit Profile')}
        />
        <SettingsRow
          icon="lock"
          label="Privacy Settings"
          onPress={() => comingSoon('Privacy Settings')}
        />
        <SettingsRow
          icon="shield"
          label="Security"
          onPress={() => comingSoon('Security')}
        />
        <SettingsRow
          icon="bell"
          label="Notifications"
          onPress={() => comingSoon('Notifications')}
          isLast
        />
      </Section>

      {/* ── Chats ── */}
      <Section title="CHATS">
        <SettingsRow
          icon="folder"
          label="Chat Folders"
          onPress={() => comingSoon('Chat Folders')}
        />
        <SettingsRow
          icon="bookmark"
          label="Saved Messages"
          onPress={() => navigation.navigate('SavedMessages')}
        />
        <SettingsRow
          icon="edit-3"
          label="Drafts"
          onPress={() => navigation.navigate('Drafts')}
        />
        <SettingsRow
          icon="trash-2"
          label="Auto-Delete Media"
          onPress={() => comingSoon('Auto-Delete Media')}
          isLast
        />
      </Section>

      {/* ── Appearance ── */}
      <Section title="APPEARANCE">
        <SettingsRow
          icon="moon"
          label="Theme"
          value="Midnight Indigo"
          onPress={() => comingSoon('Theme')}
        />
        <SettingsRow
          icon="globe"
          label="Language"
          value="English"
          onPress={() => comingSoon('Language')}
        />
        <SettingsRow
          icon="type"
          label="Font Size"
          onPress={() => comingSoon('Font Size')}
          isLast
        />
      </Section>

      {/* ── Storage ── */}
      <Section title="STORAGE">
        <SettingsRow
          icon="hard-drive"
          label="Manage Storage"
          onPress={() => comingSoon('Manage Storage')}
        />
        <SettingsRow
          icon="cloud"
          label="Cloud Backup"
          onPress={() => comingSoon('Cloud Backup')}
          isLast
        />
      </Section>

      {/* ── About ── */}
      <Section title="ABOUT">
        <SettingsRow
          icon="help-circle"
          label="Help & Support"
          onPress={() => comingSoon('Help & Support')}
        />
        <SettingsRow
          icon="file-text"
          label="Privacy Policy"
          onPress={() => comingSoon('Privacy Policy')}
        />
        <SettingsRow
          icon="file"
          label="Terms of Service"
          onPress={() => comingSoon('Terms of Service')}
        />
        <SettingsRow
          icon="star"
          label="Rate the App"
          onPress={() => comingSoon('Rate the App')}
        />
        <SettingsRow
          icon="info"
          label="Version"
          value="1.0.0"
          onPress={() => {}}
          isLast
        />
      </Section>

      {/* ── Danger zone ── */}
      <Section title="DANGER ZONE">
        <SettingsRow
          icon="log-out"
          label="Log Out"
          onPress={handleLogout}
          danger
        />
        <SettingsRow
          icon="user-x"
          label="Delete Account"
          onPress={handleDeleteAccount}
          danger
          isLast
        />
      </Section>

      <View style={styles.footer} />
    </ScrollView>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: COLORS.bg,
  },
  content: {
    paddingTop: 12,
    paddingBottom: 40,
  },

  // Profile card
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.card,
    marginHorizontal: 16,
    marginBottom: 24,
    padding: 16,
    borderRadius: 16,
  },
  profileAvatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: COLORS.separator,
  },
  profileAvatarPlaceholder: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  profileInfo: {
    flex: 1,
    marginLeft: 14,
  },
  profileName: {
    color: COLORS.white,
    fontSize: 17,
    fontWeight: '700',
  },
  profileUsername: {
    color: COLORS.secondary,
    fontSize: 13,
    marginTop: 2,
  },

  // Sections
  section: {
    marginBottom: 24,
    paddingHorizontal: 16,
  },
  sectionHeader: {
    color: COLORS.secondary,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.6,
    marginBottom: 8,
    marginLeft: 4,
  },
  sectionCard: {
    backgroundColor: COLORS.card,
    borderRadius: 14,
    overflow: 'hidden',
  },

  // Rows
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 13,
    position: 'relative',
  },
  rowLast: {
    // No bottom separator for the last row — handled by rowSeparator absence
  },
  rowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  rowRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  iconWrap: {
    width: 32,
    height: 32,
    borderRadius: 8,
    backgroundColor: '#1A1B2E',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  iconWrapDanger: {
    backgroundColor: '#2D1B1B',
  },
  rowLabel: {
    color: COLORS.white,
    fontSize: 15,
  },
  rowLabelDanger: {
    color: COLORS.danger,
  },
  rowValue: {
    color: COLORS.secondary,
    fontSize: 14,
  },
  rowSeparator: {
    position: 'absolute',
    bottom: 0,
    left: 60,
    right: 0,
    height: 1,
    backgroundColor: COLORS.separator,
  },

  footer: {
    height: 20,
  },
});

export default SettingsScreen;
