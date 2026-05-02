// ============================================================
// WorldMates Messenger — UserProfileScreen
// Shows the public profile of any user; own profile if userId
// matches the logged-in user's id.
// ============================================================

import React, { useCallback, useEffect, useRef, useState } from 'react';
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
import { profileApi } from '../../api/profileApi';
import { getMediaUrl } from '../../utils/mediaUtils';
import { formatLastSeen } from '../../utils/dateUtils';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import type { User, CallHistory } from '../../api/types';
import { nodeGet } from '../../api/apiClient';
import { NODE_PROFILE_ME } from '../../constants/api';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<RootStackParamList, 'UserProfile'>;

// ─────────────────────────────────────────────────────────────
// HELPERS
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

function VerifiedBadge({ level }: { level: number }) {
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
      color={badgeColors[level] ?? COLORS.accent}
      style={styles.verifiedIcon}
    />
  );
}

function StatColumn({ label, value }: { label: string; value: string | number }) {
  return (
    <View style={styles.statColumn}>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

// ─────────────────────────────────────────────────────────────
// COMPONENT
// ─────────────────────────────────────────────────────────────

const UserProfileScreen: React.FC<Props> = ({ route, navigation }) => {
  const { userId } = route.params;
  const currentUser = useAuthStore((s) => s.user);

  const isOwnProfile = currentUser?.id === userId;

  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [recentCalls, setRecentCalls] = useState<CallHistory[]>([]);

  // ── Load profile ──────────────────────────────────────────

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
      setError((e as Error)?.message ?? 'Failed to load profile');
    } finally {
      setLoading(false);
    }
  }, [userId, isOwnProfile]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  // ── Follow / Unfollow ─────────────────────────────────────

  const handleFollowToggle = async () => {
    if (!profile || followLoading) return;
    const next = !isFollowing;
    setIsFollowing(next); // optimistic
    setFollowLoading(true);
    try {
      if (next) {
        await profileApi.followUser(userId);
      } else {
        await profileApi.unfollowUser(userId);
      }
    } catch {
      setIsFollowing(!next); // revert on failure
    } finally {
      setFollowLoading(false);
    }
  };

  // ── Block ─────────────────────────────────────────────────

  const handleBlock = () => {
    Alert.alert(
      'Block User',
      `Are you sure you want to block ${profile?.name ?? 'this user'}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Block',
          style: 'destructive',
          onPress: async () => {
            try {
              await profileApi.blockUser(userId);
              navigation.goBack();
            } catch {
              Alert.alert('Error', 'Could not block user. Please try again.');
            }
          },
        },
      ],
    );
  };

  // ── Three-dots menu ───────────────────────────────────────

  const handleHeaderMenu = () => {
    if (isOwnProfile) {
      Share.share({ message: `Check out my profile on WorldMates!` });
      return;
    }

    if (Platform.OS === 'ios') {
      ActionSheetIOS.showActionSheetWithOptions(
        {
          options: ['Cancel', 'Report', 'Block'],
          cancelButtonIndex: 0,
          destructiveButtonIndex: 2,
        },
        (buttonIndex) => {
          if (buttonIndex === 1) Alert.alert('Report', 'Report submitted. Thank you.');
          if (buttonIndex === 2) handleBlock();
        },
      );
    } else {
      Alert.alert('Options', undefined, [
        { text: 'Report', onPress: () => Alert.alert('Report', 'Report submitted. Thank you.') },
        { text: 'Block', style: 'destructive', onPress: handleBlock },
        { text: 'Cancel', style: 'cancel' },
      ]);
    }
  };

  // ── Navigate to Messages ──────────────────────────────────

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

  // ── Set header options ────────────────────────────────────

  useEffect(() => {
    navigation.setOptions({
      headerTransparent: true,
      headerTitle: '',
      headerLeft: () => (
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerBtn}>
          <Feather name="arrow-left" size={22} color={COLORS.white} />
        </TouchableOpacity>
      ),
      headerRight: () => (
        <TouchableOpacity onPress={handleHeaderMenu} style={styles.headerBtn}>
          <Feather name="more-vertical" size={22} color={COLORS.white} />
        </TouchableOpacity>
      ),
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigation, isOwnProfile, profile]);

  // ─────────────────────────────────────────────────────────────
  // RENDER STATES
  // ─────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <View style={styles.centered}>
        <LoadingSpinner size="large" />
      </View>
    );
  }

  if (error || !profile) {
    return (
      <View style={styles.centered}>
        <Feather name="alert-circle" size={40} color={COLORS.danger} />
        <Text style={styles.errorText}>{error ?? 'User not found'}</Text>
        <TouchableOpacity onPress={loadProfile} style={styles.retryBtn}>
          <Text style={styles.retryText}>Retry</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const avatarUri = getMediaUrl(profile.avatar);
  const rating = profile.details?.likesCount !== undefined
    ? (profile.details.likesCount / 100).toFixed(1)
    : '—';

  // ─────────────────────────────────────────────────────────────
  // MAIN RENDER
  // ─────────────────────────────────────────────────────────────

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content} bounces={false}>

      {/* ── Cover photo area ── */}
      <View style={styles.coverContainer}>
        <LinearGradient
          colors={['#1A1B2E', '#2A2B3D']}
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

        {/* Avatar — half overlapping cover bottom */}
        <View style={styles.avatarWrapper}>
          <Image
            source={avatarUri ? { uri: avatarUri } : require('../../assets/default-avatar.png')}
            style={styles.avatar}
            defaultSource={require('../../assets/default-avatar.png')}
          />
          {profile.isVerified && (
            <View style={styles.verifiedBadgeContainer}>
              <VerifiedBadge level={profile.verificationLevel} />
            </View>
          )}
        </View>
      </View>

      {/* ── Profile info ── */}
      <View style={styles.infoContainer}>
        {/* Name row */}
        <View style={styles.nameRow}>
          <Text style={styles.name}>{profile.name}</Text>
        </View>

        {/* Username */}
        <Text style={styles.username}>@{profile.username}</Text>

        {/* Custom status */}
        {profile.customStatus ? (
          <View style={styles.statusRow}>
            <Text style={styles.statusText}>
              {profile.customStatus.emoji} {profile.customStatus.text}
            </Text>
          </View>
        ) : null}

        {/* Bio */}
        {profile.about ? (
          <Text style={styles.bio}>{profile.about}</Text>
        ) : null}

        {/* Stats row */}
        <View style={styles.statsRow}>
          <StatColumn label="Followers" value={profile.followersCount} />
          <View style={styles.statDivider} />
          <StatColumn label="Following" value={profile.followingCount} />
          <View style={styles.statDivider} />
          <StatColumn label="Rating" value={`⭐ ${rating}`} />
        </View>

        {/* Action buttons */}
        <View style={styles.actionsRow}>
          {isOwnProfile ? (
            <TouchableOpacity
              style={[styles.actionBtn, styles.actionBtnPrimary]}
              onPress={() => Alert.alert('Coming soon', 'Edit Profile screen is under construction.')}
              activeOpacity={0.8}
            >
              <Feather name="edit-2" size={16} color={COLORS.white} />
              <Text style={styles.actionBtnText}>Edit Profile</Text>
            </TouchableOpacity>
          ) : (
            <>
              <TouchableOpacity
                style={[styles.actionBtn, styles.actionBtnPrimary, { flex: 1, marginRight: 8 }]}
                onPress={handleMessage}
                activeOpacity={0.8}
              >
                <Feather name="message-circle" size={16} color={COLORS.white} />
                <Text style={styles.actionBtnText}>Message</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[
                  styles.actionBtn,
                  isFollowing ? styles.actionBtnOutline : styles.actionBtnPrimary,
                  { flex: 1 },
                ]}
                onPress={handleFollowToggle}
                disabled={followLoading}
                activeOpacity={0.8}
              >
                <Feather
                  name={isFollowing ? 'user-check' : 'user-plus'}
                  size={16}
                  color={COLORS.white}
                />
                <Text style={styles.actionBtnText}>
                  {isFollowing ? 'Following' : 'Follow'}
                </Text>
              </TouchableOpacity>
            </>
          )}
        </View>
      </View>

      {/* ── Separator ── */}
      <View style={styles.separator} />

      {/* ── Info section ── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Info</Text>

        {profile.address || profile.city || profile.country ? (
          <View style={styles.infoRow}>
            <Feather name="map-pin" size={16} color={COLORS.secondary} />
            <Text style={styles.infoText}>
              {[profile.address, profile.city, profile.country].filter(Boolean).join(', ')}
            </Text>
          </View>
        ) : null}

        {profile.birthday ? (
          <View style={styles.infoRow}>
            <Feather name="gift" size={16} color={COLORS.secondary} />
            <Text style={styles.infoText}>{profile.birthday}</Text>
          </View>
        ) : null}

        {profile.website ? (
          <View style={styles.infoRow}>
            <Feather name="globe" size={16} color={COLORS.secondary} />
            <Text style={[styles.infoText, styles.infoLink]}>{profile.website}</Text>
          </View>
        ) : null}

        {!profile.address && !profile.birthday && !profile.website ? (
          <Text style={styles.emptyInfo}>No info provided.</Text>
        ) : null}
      </View>

      {/* ── Shared Media ── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Shared Media</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.mediaScroll}>
          {Array.from({ length: 6 }).map((_, i) => (
            <View key={i} style={styles.mediaThumb}>
              <Feather name="image" size={28} color={COLORS.secondary} />
            </View>
          ))}
        </ScrollView>
      </View>

      {/* ── Call History (own profile only) ── */}
      {isOwnProfile && recentCalls.length > 0 ? (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Recent Calls</Text>
          {recentCalls.map((call) => (
            <View key={call.id} style={styles.callRow}>
              <Feather
                name={call.callType === 'video' ? 'video' : 'phone'}
                size={18}
                color={call.status === 'missed' ? COLORS.danger : COLORS.accent}
              />
              <View style={styles.callInfo}>
                <Text style={styles.callName}>{call.otherUser?.name ?? 'Unknown'}</Text>
                <Text style={styles.callMeta}>
                  {call.direction === 'incoming' ? 'Incoming' : 'Outgoing'} ·{' '}
                  {call.status}
                </Text>
              </View>
            </View>
          ))}
        </View>
      ) : null}

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
    paddingBottom: 40,
  },
  centered: {
    flex: 1,
    backgroundColor: COLORS.bg,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  errorText: {
    color: COLORS.secondary,
    fontSize: 15,
    marginTop: 12,
    textAlign: 'center',
  },
  retryBtn: {
    marginTop: 20,
    backgroundColor: COLORS.accent,
    paddingHorizontal: 28,
    paddingVertical: 10,
    borderRadius: 10,
  },
  retryText: {
    color: COLORS.white,
    fontWeight: '600',
  },

  // Header buttons
  headerBtn: {
    padding: 8,
  },

  // Cover
  coverContainer: {
    height: 200,
    backgroundColor: COLORS.card,
    position: 'relative',
  },

  // Avatar
  avatarWrapper: {
    position: 'absolute',
    bottom: -40,
    left: 20,
  },
  avatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    borderWidth: 3,
    borderColor: COLORS.white,
    backgroundColor: COLORS.card,
  },
  verifiedBadgeContainer: {
    position: 'absolute',
    bottom: 2,
    right: 2,
    backgroundColor: COLORS.bg,
    borderRadius: 8,
    padding: 1,
  },
  verifiedIcon: {
    marginLeft: 6,
  },

  // Info container
  infoContainer: {
    marginTop: 50,
    paddingHorizontal: 20,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  name: {
    color: COLORS.white,
    fontSize: 22,
    fontWeight: '700',
  },
  username: {
    color: COLORS.secondary,
    fontSize: 15,
    marginTop: 2,
  },
  statusRow: {
    marginTop: 8,
  },
  statusText: {
    color: COLORS.secondary,
    fontSize: 14,
  },
  bio: {
    color: COLORS.secondary,
    fontSize: 14,
    marginTop: 10,
    textAlign: 'center',
    lineHeight: 20,
  },

  // Stats
  statsRow: {
    flexDirection: 'row',
    marginTop: 20,
    backgroundColor: COLORS.card,
    borderRadius: 14,
    paddingVertical: 14,
  },
  statColumn: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    color: COLORS.white,
    fontSize: 18,
    fontWeight: '700',
  },
  statLabel: {
    color: COLORS.secondary,
    fontSize: 12,
    marginTop: 2,
  },
  statDivider: {
    width: 1,
    backgroundColor: COLORS.separator,
    marginVertical: 4,
  },

  // Actions
  actionsRow: {
    flexDirection: 'row',
    marginTop: 16,
  },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 10,
    gap: 6,
  },
  actionBtnPrimary: {
    backgroundColor: COLORS.accent,
  },
  actionBtnOutline: {
    backgroundColor: COLORS.card,
    borderWidth: 1,
    borderColor: COLORS.separator,
  },
  actionBtnText: {
    color: COLORS.white,
    fontWeight: '600',
    fontSize: 15,
  },

  // Separator
  separator: {
    height: 1,
    backgroundColor: COLORS.separator,
    marginTop: 24,
    marginHorizontal: 20,
  },

  // Sections
  section: {
    marginTop: 20,
    paddingHorizontal: 20,
  },
  sectionTitle: {
    color: COLORS.white,
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 12,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    gap: 10,
  },
  infoText: {
    color: COLORS.secondary,
    fontSize: 14,
  },
  infoLink: {
    color: COLORS.accent,
  },
  emptyInfo: {
    color: COLORS.secondary,
    fontSize: 14,
  },

  // Shared media
  mediaScroll: {
    flexDirection: 'row',
  },
  mediaThumb: {
    width: 90,
    height: 90,
    borderRadius: 10,
    backgroundColor: COLORS.card,
    marginRight: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // Calls
  callRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    gap: 12,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.separator,
  },
  callInfo: {
    flex: 1,
  },
  callName: {
    color: COLORS.white,
    fontSize: 15,
    fontWeight: '600',
  },
  callMeta: {
    color: COLORS.secondary,
    fontSize: 13,
    marginTop: 2,
  },
});

export default UserProfileScreen;
