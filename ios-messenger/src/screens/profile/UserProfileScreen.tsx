import React, { useCallback, useEffect, useState } from 'react';
import {
  ActionSheetIOS,
  Alert,
  Image,
  Platform,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Feather } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import { profileApi } from '../../api/profileApi';
import { getMediaUrl } from '../../utils/mediaUtils';
import { formatCallDuration } from '../../utils/dateUtils';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import type { User, CallHistory } from '../../api/types';

type Props = NativeStackScreenProps<RootStackParamList, 'UserProfile'>;

function VerifiedBadge({ level }: { level: number }) {
  const theme = useTheme();
  if (!level) return null;
  const badgeColors: Record<number, string> = {
    1: '#1DA1F2',
    2: '#FFD700',
    3: '#00C853',
    4: '#7C4DFF',
  };
  return (
    <Feather
      name="check-circle"
      size={16}
      color={badgeColors[level] ?? theme.primary}
      style={styles.verifiedIcon}
    />
  );
}

function StatColumn({ label, value }: { label: string; value: string | number }) {
  const theme = useTheme();
  return (
    <View style={styles.statColumn}>
      <Text style={[styles.statValue, { color: theme.text }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: theme.textSecondary }]}>{label}</Text>
    </View>
  );
}

const UserProfileScreen: React.FC<Props> = ({ route, navigation }) => {
  const { userId } = route.params;
  const currentUser = useAuthStore((s) => s.user);
  const theme = useTheme();
  const { t } = useTranslation();

  const isOwnProfile = currentUser?.id === userId;

  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [recentCalls, setRecentCalls] = useState<CallHistory[]>([]);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = isOwnProfile
        ? await profileApi.getMyProfile()
        : await profileApi.getUserProfile(userId);
      setProfile(data);
      setIsFollowing(data.relationship?.isFollowing ?? false);
    } catch (e: unknown) {
      setError((e as Error)?.message ?? t('error_load_profile'));
    } finally {
      setLoading(false);
    }
  }, [userId, isOwnProfile, t]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const handleFollowToggle = async () => {
    if (!profile || followLoading) return;
    const next = !isFollowing;
    setIsFollowing(next);
    setFollowLoading(true);
    try {
      if (next) {
        await profileApi.followUser(userId);
      } else {
        await profileApi.unfollowUser(userId);
      }
    } catch {
      setIsFollowing(!next);
    } finally {
      setFollowLoading(false);
    }
  };

  const handleBlock = () => {
    Alert.alert(
      t('block_user'),
      `${t('block_user_confirm')} ${profile?.name ?? ''}?`,
      [
        { text: t('cancel'), style: 'cancel' },
        {
          text: t('block'),
          style: 'destructive',
          onPress: async () => {
            try {
              await profileApi.blockUser(userId);
              navigation.goBack();
            } catch {
              Alert.alert(t('error'), t('error_block_user'));
            }
          },
        },
      ],
    );
  };

  const handleHeaderMenu = () => {
    if (isOwnProfile) {
      Share.share({ message: `Check out my profile on WorldMates!` });
      return;
    }

    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        {
          options: [t('cancel'), t('report'), t('block')],
          cancelButtonIndex: 0,
          destructiveButtonIndex: 2,
        },
        (buttonIndex) => {
          if (buttonIndex === 1) Alert.alert(t('report'), t('report_submitted'));
          if (buttonIndex === 2) handleBlock();
        },
      );
    } else {
      Alert.alert(t('options'), undefined, [
        { text: t('report'), onPress: () => Alert.alert(t('report'), t('report_submitted')) },
        { text: t('block'), style: 'destructive', onPress: handleBlock },
        { text: t('cancel'), style: 'cancel' },
      ]);
    }
  };

  const handleMessage = () => {
    if (!profile) return;
    navigation.navigate('Messages', {
      chatId: userId,
      chatType: 'user',
      chatName: profile.name,
      chatAvatar: profile.avatar,
      userId,
    });
  };

  useEffect(() => {
    navigation.setOptions({
      headerTransparent: true,
      headerTitle: '',
      headerLeft: () => (
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerBtn}>
          <Feather name="arrow-left" size={22} color="#FFFFFF" />
        </TouchableOpacity>
      ),
      headerRight: () => (
        <TouchableOpacity onPress={handleHeaderMenu} style={styles.headerBtn}>
          <Feather name="more-vertical" size={22} color="#FFFFFF" />
        </TouchableOpacity>
      ),
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigation, isOwnProfile, profile]);

  if (loading) {
    return (
      <View style={[styles.centered, { backgroundColor: theme.background }]}>
        <LoadingSpinner size="large" />
      </View>
    );
  }

  if (error || !profile) {
    return (
      <View style={[styles.centered, { backgroundColor: theme.background }]}>
        <Feather name="alert-circle" size={40} color={theme.error} />
        <Text style={[styles.errorText, { color: theme.textSecondary }]}>{error ?? t('user_not_found')}</Text>
        <TouchableOpacity onPress={loadProfile} style={[styles.retryBtn, { backgroundColor: theme.primary }]}>
          <Text style={styles.retryText}>{t('retry')}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const avatarUri = getMediaUrl(profile.avatar);
  const rating = profile.details?.likesCount !== undefined
    ? (profile.details.likesCount / 100).toFixed(1)
    : '—';

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: theme.background }]}
      contentContainerStyle={styles.content}
      bounces={false}
    >
      {/* Cover photo */}
      <View style={[styles.coverContainer, { backgroundColor: theme.surface }]}>
        <LinearGradient
          colors={[theme.background, theme.surface]}
          style={StyleSheet.absoluteFill}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
        />
        {profile.cover ? (
          <Image
            source={{ uri: getMediaUrl(profile.cover) }}
            style={StyleSheet.absoluteFill}
            resizeMode="cover"
          />
        ) : null}

        {/* Avatar overlapping cover bottom */}
        <View style={styles.avatarWrapper}>
          {avatarUri ? (
            <Image source={{ uri: avatarUri }} style={[styles.avatar, { backgroundColor: theme.surface }]} />
          ) : (
            <View style={[styles.avatar, styles.avatarPlaceholder, { backgroundColor: theme.surface }]}>
              <Feather name="user" size={36} color={theme.textSecondary} />
            </View>
          )}
          {profile.isVerified && (
            <View style={[styles.verifiedBadgeContainer, { backgroundColor: theme.background }]}>
              <VerifiedBadge level={profile.verificationLevel} />
            </View>
          )}
        </View>
      </View>

      {/* Profile info */}
      <View style={styles.infoContainer}>
        <View style={styles.nameRow}>
          <Text style={[styles.name, { color: theme.text }]}>{profile.name}</Text>
        </View>

        <Text style={[styles.username, { color: theme.textSecondary }]}>@{profile.username}</Text>

        {profile.customStatus ? (
          <View style={styles.statusRow}>
            <Text style={[styles.statusText, { color: theme.textSecondary }]}>
              {profile.customStatus.emoji} {profile.customStatus.text}
            </Text>
          </View>
        ) : null}

        {profile.about ? (
          <Text style={[styles.bio, { color: theme.textSecondary }]}>{profile.about}</Text>
        ) : null}

        <View style={[styles.statsRow, { backgroundColor: theme.surface }]}>
          <StatColumn label={t('followers')} value={profile.followersCount} />
          <View style={[styles.statDivider, { backgroundColor: theme.divider }]} />
          <StatColumn label={t('following')} value={profile.followingCount} />
          <View style={[styles.statDivider, { backgroundColor: theme.divider }]} />
          <StatColumn label={t('rating')} value={`⭐ ${rating}`} />
        </View>

        <View style={styles.actionsRow}>
          {isOwnProfile ? (
            <TouchableOpacity
              style={[styles.actionBtn, { backgroundColor: theme.primary, flex: 1 }]}
              onPress={() => Alert.alert('', t('coming_soon'))}
              activeOpacity={0.8}
            >
              <Feather name="edit-2" size={16} color="#FFFFFF" />
              <Text style={styles.actionBtnText}>{t('edit_profile')}</Text>
            </TouchableOpacity>
          ) : (
            <>
              <TouchableOpacity
                style={[styles.actionBtn, { backgroundColor: theme.primary, flex: 1, marginRight: 8 }]}
                onPress={handleMessage}
                activeOpacity={0.8}
              >
                <Feather name="message-circle" size={16} color="#FFFFFF" />
                <Text style={styles.actionBtnText}>{t('message')}</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[
                  styles.actionBtn,
                  isFollowing
                    ? { backgroundColor: theme.surface, borderWidth: 1, borderColor: theme.divider, flex: 1 }
                    : { backgroundColor: theme.primary, flex: 1 },
                ]}
                onPress={handleFollowToggle}
                disabled={followLoading}
                activeOpacity={0.8}
              >
                <Feather
                  name={isFollowing ? 'user-check' : 'user-plus'}
                  size={16}
                  color="#FFFFFF"
                />
                <Text style={styles.actionBtnText}>
                  {isFollowing ? t('following') : t('follow')}
                </Text>
              </TouchableOpacity>
            </>
          )}
        </View>
      </View>

      <View style={[styles.separator, { backgroundColor: theme.divider }]} />

      {/* Info section */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>{t('info')}</Text>

        {profile.address || profile.city || profile.country ? (
          <View style={styles.infoRow}>
            <Feather name="map-pin" size={16} color={theme.textSecondary} />
            <Text style={[styles.infoText, { color: theme.textSecondary }]}>
              {[profile.address, profile.city, profile.country].filter(Boolean).join(', ')}
            </Text>
          </View>
        ) : null}

        {profile.birthday ? (
          <View style={styles.infoRow}>
            <Feather name="gift" size={16} color={theme.textSecondary} />
            <Text style={[styles.infoText, { color: theme.textSecondary }]}>{profile.birthday}</Text>
          </View>
        ) : null}

        {profile.website ? (
          <View style={styles.infoRow}>
            <Feather name="globe" size={16} color={theme.textSecondary} />
            <Text style={[styles.infoText, { color: theme.accent }]}>{profile.website}</Text>
          </View>
        ) : null}

        {!profile.address && !profile.birthday && !profile.website ? (
          <Text style={[styles.emptyInfo, { color: theme.textSecondary }]}>{t('no_info')}</Text>
        ) : null}
      </View>

      {/* Shared Media */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>{t('shared_media')}</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.mediaScroll}>
          {Array.from({ length: 6 }).map((_, i) => (
            <View key={i} style={[styles.mediaThumb, { backgroundColor: theme.surface }]}>
              <Feather name="image" size={28} color={theme.textSecondary} />
            </View>
          ))}
        </ScrollView>
      </View>

      {/* Recent Calls */}
      {isOwnProfile && recentCalls.length > 0 ? (
        <View style={styles.section}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>{t('recent_calls')}</Text>
          {recentCalls.map((call) => (
            <View key={call.id} style={[styles.callRow, { borderBottomColor: theme.divider }]}>
              <Feather
                name={call.callType === 'video' ? 'video' : 'phone'}
                size={18}
                color={call.status === 'missed' ? theme.error : theme.primary}
              />
              <View style={styles.callInfo}>
                <Text style={[styles.callName, { color: theme.text }]}>{call.otherUser?.name ?? t('unknown')}</Text>
                <Text style={[styles.callMeta, { color: theme.textSecondary }]}>
                  {call.direction === 'incoming' ? t('incoming') : t('outgoing')} ·{' '}
                  {call.status}
                  {call.duration > 0 ? ` · ${formatCallDuration(call.duration)}` : ''}
                </Text>
              </View>
            </View>
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { paddingBottom: 40 },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  errorText: { fontSize: 15, marginTop: 12, textAlign: 'center' },
  retryBtn: { marginTop: 20, paddingHorizontal: 28, paddingVertical: 10, borderRadius: 10 },
  retryText: { color: '#FFFFFF', fontWeight: '600' },
  headerBtn: { padding: 8 },
  coverContainer: { height: 200, position: 'relative' },
  avatarWrapper: { position: 'absolute', bottom: -40, left: 20 },
  avatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    borderWidth: 3,
    borderColor: '#FFFFFF',
  },
  avatarPlaceholder: { justifyContent: 'center', alignItems: 'center' },
  verifiedBadgeContainer: { position: 'absolute', bottom: 2, right: 2, borderRadius: 8, padding: 1 },
  verifiedIcon: { marginLeft: 6 },
  infoContainer: { marginTop: 50, paddingHorizontal: 20 },
  nameRow: { flexDirection: 'row', alignItems: 'center' },
  name: { fontSize: 22, fontWeight: '700' },
  username: { fontSize: 15, marginTop: 2 },
  statusRow: { marginTop: 8 },
  statusText: { fontSize: 14 },
  bio: { fontSize: 14, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  statsRow: {
    flexDirection: 'row',
    marginTop: 20,
    borderRadius: 14,
    paddingVertical: 14,
  },
  statColumn: { flex: 1, alignItems: 'center' },
  statValue: { fontSize: 18, fontWeight: '700' },
  statLabel: { fontSize: 12, marginTop: 2 },
  statDivider: { width: 1, marginVertical: 4 },
  actionsRow: { flexDirection: 'row', marginTop: 16 },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 10,
    gap: 6,
  },
  actionBtnText: { color: '#FFFFFF', fontWeight: '600', fontSize: 15 },
  separator: { height: 1, marginTop: 24, marginHorizontal: 20 },
  section: { marginTop: 20, paddingHorizontal: 20 },
  sectionTitle: { fontSize: 16, fontWeight: '700', marginBottom: 12 },
  infoRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 10, gap: 10 },
  infoText: { fontSize: 14 },
  emptyInfo: { fontSize: 14 },
  mediaScroll: { flexDirection: 'row' },
  mediaThumb: {
    width: 90,
    height: 90,
    borderRadius: 10,
    marginRight: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  callRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, gap: 12, borderBottomWidth: 1 },
  callInfo: { flex: 1 },
  callName: { fontSize: 15, fontWeight: '600' },
  callMeta: { fontSize: 13, marginTop: 2 },
});

export default UserProfileScreen;
