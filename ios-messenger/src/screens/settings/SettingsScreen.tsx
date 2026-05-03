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
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import { getMediaUrl } from '../../utils/mediaUtils';
import type { ThemeColors } from '../../theme';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Settings'>,
  NativeStackScreenProps<RootStackParamList>
>;

interface SettingsRowProps {
  icon: React.ComponentProps<typeof Feather>['name'];
  label: string;
  value?: string;
  onPress: () => void;
  danger?: boolean;
  isLast?: boolean;
  theme: ThemeColors;
}

const SettingsRow: React.FC<SettingsRowProps> = ({
  icon,
  label,
  value,
  onPress,
  danger = false,
  isLast = false,
  theme,
}) => (
  <TouchableOpacity
    style={styles.row}
    onPress={onPress}
    activeOpacity={0.7}
  >
    <View style={styles.rowLeft}>
      <View style={[styles.iconWrap, { backgroundColor: danger ? theme.error + '22' : theme.background }]}>
        <Feather name={icon} size={18} color={danger ? theme.error : theme.primary} />
      </View>
      <Text style={[styles.rowLabel, { color: danger ? theme.error : theme.text }]}>{label}</Text>
    </View>
    <View style={styles.rowRight}>
      {value ? <Text style={[styles.rowValue, { color: theme.textSecondary }]}>{value}</Text> : null}
      <Feather name="chevron-right" size={18} color={theme.textTertiary} />
    </View>
    {!isLast && <View style={[styles.rowSeparator, { backgroundColor: theme.divider }]} />}
  </TouchableOpacity>
);

interface SectionProps {
  title: string;
  children: React.ReactNode;
  theme: ThemeColors;
}

const Section: React.FC<SectionProps> = ({ title, children, theme }) => (
  <View style={styles.section}>
    <Text style={[styles.sectionHeader, { color: theme.textTertiary }]}>{title}</Text>
    <View style={[styles.sectionCard, { backgroundColor: theme.surface }]}>{children}</View>
  </View>
);

const SettingsScreen: React.FC<Props> = ({ navigation }) => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const theme = useTheme();
  const { t, language } = useTranslation();

  const comingSoon = useCallback(() => {
    Alert.alert(t('coming_soon'), '');
  }, [t]);

  const handleLogout = useCallback(() => {
    Alert.alert(
      t('logout_confirm_title'),
      t('logout_confirm_message'),
      [
        { text: t('logout_confirm_cancel'), style: 'cancel' },
        {
          text: t('logout_confirm_yes'),
          style: 'destructive',
          onPress: () => {
            logout().catch(() => {
              Alert.alert(t('error'), t('logout_error'));
            });
          },
        },
      ],
    );
  }, [logout, t]);

  const handleDeleteAccount = useCallback(() => {
    Alert.alert(
      t('delete_account'),
      t('delete_account_warning'),
      [
        { text: t('cancel'), style: 'cancel' },
        { text: t('delete'), style: 'destructive', onPress: comingSoon },
      ],
    );
  }, [comingSoon, t]);

  const handleGoToProfile = useCallback(() => {
    if (!user) return;
    navigation.navigate('UserProfile', { userId: user.id });
  }, [navigation, user]);

  const langDisplay = language === 'uk' ? 'Українська' : language === 'ru' ? 'Русский' : 'English';

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: theme.background }]}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {/* Profile card */}
      <TouchableOpacity
        style={[styles.profileCard, { backgroundColor: theme.surface }]}
        onPress={handleGoToProfile}
        activeOpacity={0.8}
      >
        {user?.avatar ? (
          <Image source={{ uri: getMediaUrl(user.avatar) }} style={styles.profileAvatar} />
        ) : (
          <View style={[styles.profileAvatar, styles.profileAvatarPlaceholder, { backgroundColor: theme.divider }]}>
            <Feather name="user" size={26} color={theme.textSecondary} />
          </View>
        )}
        <View style={styles.profileInfo}>
          <Text style={[styles.profileName, { color: theme.text }]}>{user?.name ?? t('loading')}</Text>
          <Text style={[styles.profileUsername, { color: theme.textSecondary }]}>@{user?.username ?? ''}</Text>
        </View>
        <Feather name="chevron-right" size={20} color={theme.textTertiary} />
      </TouchableOpacity>

      <Section title={t('settings_account')} theme={theme}>
        <SettingsRow icon="user" label={t('edit_profile')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="lock" label={t('privacy_settings')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="shield" label={t('security')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="bell" label={t('notifications')} onPress={comingSoon} theme={theme} isLast />
      </Section>

      <Section title={t('settings_chats')} theme={theme}>
        <SettingsRow icon="folder" label={t('chat_folders')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="bookmark" label={t('saved_messages')} onPress={() => navigation.navigate('SavedMessages')} theme={theme} />
        <SettingsRow icon="edit-3" label={t('drafts')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="trash-2" label={t('auto_delete_media')} onPress={comingSoon} theme={theme} isLast />
      </Section>

      <Section title={t('settings_appearance')} theme={theme}>
        <SettingsRow icon="moon" label={t('theme')} value={t('theme_classic_blue')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="globe" label={t('language')} value={langDisplay} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="type" label={t('font_size')} onPress={comingSoon} theme={theme} isLast />
      </Section>

      <Section title={t('settings_storage')} theme={theme}>
        <SettingsRow icon="hard-drive" label={t('manage_storage')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="cloud" label={t('cloud_backup')} onPress={comingSoon} theme={theme} isLast />
      </Section>

      <Section title={t('settings_about')} theme={theme}>
        <SettingsRow icon="help-circle" label={t('help_support')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="file-text" label={t('privacy_policy')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="file" label={t('terms_of_service')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="star" label={t('rate_app')} onPress={comingSoon} theme={theme} />
        <SettingsRow icon="info" label={t('version')} value="2.0.0" onPress={() => {}} theme={theme} isLast />
      </Section>

      <Section title={t('settings_danger')} theme={theme}>
        <SettingsRow icon="log-out" label={t('logout')} onPress={handleLogout} danger theme={theme} />
        <SettingsRow icon="user-x" label={t('delete_account')} onPress={handleDeleteAccount} danger theme={theme} isLast />
      </Section>

      <View style={styles.footer} />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { paddingTop: 12, paddingBottom: 40 },
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginBottom: 24,
    padding: 16,
    borderRadius: 16,
  },
  profileAvatar: { width: 56, height: 56, borderRadius: 28 },
  profileAvatarPlaceholder: { justifyContent: 'center', alignItems: 'center' },
  profileInfo: { flex: 1, marginLeft: 14 },
  profileName: { fontSize: 17, fontWeight: '700' },
  profileUsername: { fontSize: 13, marginTop: 2 },
  section: { marginBottom: 24, paddingHorizontal: 16 },
  sectionHeader: { fontSize: 12, fontWeight: '600', letterSpacing: 0.6, marginBottom: 8, marginLeft: 4 },
  sectionCard: { borderRadius: 14, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 13,
    position: 'relative',
  },
  rowLeft: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  rowRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  iconWrap: { width: 32, height: 32, borderRadius: 8, justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  rowLabel: { fontSize: 15 },
  rowValue: { fontSize: 14 },
  rowSeparator: { position: 'absolute', bottom: 0, left: 60, right: 0, height: StyleSheet.hairlineWidth },
  footer: { height: 20 },
});

export default SettingsScreen;
