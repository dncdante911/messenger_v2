import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Share,
  RefreshControl,
  Dimensions,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack';
import { LinearGradient } from 'expo-linear-gradient';

import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { RootStackParamList } from '../../navigation/types';

type ChannelDetailsRouteProp = NativeStackScreenProps<RootStackParamList, 'ChannelDetails'>['route'];
type ChannelDetailsNav = NativeStackNavigationProp<RootStackParamList, 'ChannelDetails'>;

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface ChannelPost {
  id: string;
  text?: string;
  imageColor?: string;
  timestamp: Date;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  isLiked?: boolean;
}

interface ChannelInfo {
  id: string;
  name: string;
  description: string;
  subscriberCount: number;
  postCount: number;
  isSubscribed: boolean;
  avatarColor: string;
  link: string;
  isVerified?: boolean;
}

const CHANNEL_INFO: Record<string, ChannelInfo> = {
  c1: {
    id: 'c1',
    name: 'WorldMates Official',
    description: 'Офіційний канал WorldMates. Новини, оновлення та анонси нових функцій.',
    subscriberCount: 12540,
    postCount: 234,
    isSubscribed: false,
    avatarColor: '#1565C0',
    link: '@worldmates',
    isVerified: true,
  },
  c2: {
    id: 'c2',
    name: 'Tech News UA',
    description: 'Найкращі технологічні новини українською мовою.',
    subscriberCount: 8320,
    postCount: 1540,
    isSubscribed: true,
    avatarColor: '#0077B6',
    link: '@technewsua',
  },
  default: {
    id: 'default',
    name: 'Канал',
    description: 'Інформація про канал відсутня.',
    subscriberCount: 1000,
    postCount: 50,
    isSubscribed: false,
    avatarColor: '#1565C0',
    link: '@channel',
  },
};

const MOCK_POSTS: ChannelPost[] = [
  {
    id: 'p1',
    text: '🚀 WorldMates 2.0 тепер доступний! Новий інтерфейс, покращена швидкість і багато нових функцій. Завантажуйте оновлення просто зараз!',
    timestamp: new Date(Date.now() - 1000 * 60 * 30),
    viewCount: 8420,
    likeCount: 342,
    commentCount: 67,
    isLiked: false,
  },
  {
    id: 'p2',
    text: '🎉 Дякуємо за 10,000 підписників! Ми дуже раді вашій підтримці. На знак подяки ми підготували щось особливе...',
    imageColor: '#1565C0',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 3),
    viewCount: 12300,
    likeCount: 891,
    commentCount: 145,
    isLiked: true,
  },
  {
    id: 'p3',
    text: '📱 Нова функція: Голосові повідомлення з автотранскрипцією. Тепер ви можете читати голосові повідомлення навіть без навушників.',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 24),
    viewCount: 5640,
    likeCount: 213,
    commentCount: 38,
    isLiked: false,
  },
  {
    id: 'p4',
    text: '🔒 Безпека перш за все: двофакторна автентифікація тепер доступна для всіх користувачів. Увімкніть її в налаштуваннях вже зараз!',
    imageColor: '#2E7D32',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 48),
    viewCount: 9870,
    likeCount: 456,
    commentCount: 92,
    isLiked: false,
  },
  {
    id: 'p5',
    text: '💬 Груповим чатам — новий вигляд! Оновлені теми оформлення, кастомні фони та покращена продуктивність. Спробуйте вже сьогодні.',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 72),
    viewCount: 7230,
    likeCount: 318,
    commentCount: 54,
    isLiked: true,
  },
];

function formatCount(n: number): string {
  if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
  return String(n);
}

function formatPostTime(date: Date): string {
  const diff = (Date.now() - date.getTime()) / 1000;
  if (diff < 60) return 'Щойно';
  if (diff < 3600) return `${Math.floor(diff / 60)} хв тому`;
  if (diff < 86400) {
    return date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
  }
  return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short' });
}

function getInitials(name: string): string {
  return name.split(' ').map((w) => w[0]).join('').toUpperCase().slice(0, 2);
}

interface PostCardProps {
  post: ChannelPost;
  onLike: (id: string) => void;
}

function PostCard({ post, onLike }: PostCardProps) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <View style={[styles.postCard, { backgroundColor: theme.surface }]}>
      {post.imageColor && (
        <View style={[styles.postImage, { backgroundColor: post.imageColor }]}>
          <Feather name="image" size={32} color="rgba(255,255,255,0.4)" />
        </View>
      )}

      {post.text && (
        <Text style={[styles.postText, { color: theme.text }]}>{post.text}</Text>
      )}

      <View style={[styles.postFooter, { borderTopColor: theme.divider }]}>
        <View style={styles.postStats}>
          <Feather name="eye" size={13} color={theme.textTertiary} />
          <Text style={[styles.postStatText, { color: theme.textTertiary }]}>{formatCount(post.viewCount)}</Text>
        </View>

        <View style={styles.postActions}>
          <TouchableOpacity
            style={styles.postAction}
            onPress={() => onLike(post.id)}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather
              name={post.isLiked ? 'heart' : 'heart'}
              size={16}
              color={post.isLiked ? theme.error : theme.textTertiary}
            />
            <Text style={[styles.postActionText, { color: post.isLiked ? theme.error : theme.textTertiary }]}>
              {formatCount(post.likeCount)}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.postAction}
            onPress={() => Alert.alert(t('coming_soon'), '')}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="message-circle" size={16} color={theme.textTertiary} />
            <Text style={[styles.postActionText, { color: theme.textTertiary }]}>
              {formatCount(post.commentCount)}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.postAction}
            onPress={() => Alert.alert(t('coming_soon'), '')}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="share-2" size={16} color={theme.textTertiary} />
          </TouchableOpacity>
        </View>

        <Text style={[styles.postTime, { color: theme.textTertiary }]}>{formatPostTime(post.timestamp)}</Text>
      </View>
    </View>
  );
}

interface ChannelHeaderProps {
  channel: ChannelInfo;
  onSubscribe: () => void;
  onShare: () => void;
}

function ChannelHeader({ channel, onSubscribe, onShare }: ChannelHeaderProps) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <View style={styles.channelHeader}>
      {/* Cover gradient */}
      <LinearGradient
        colors={[channel.avatarColor, channel.avatarColor + '88', theme.background]}
        style={styles.coverGradient}
      />

      {/* Avatar */}
      <View style={[styles.channelAvatar, { backgroundColor: channel.avatarColor, shadowColor: channel.avatarColor }]}>
        <Text style={styles.channelAvatarText}>{getInitials(channel.name)}</Text>
      </View>

      {/* Name + verified */}
      <View style={styles.channelNameRow}>
        <Text style={[styles.channelName, { color: theme.text }]}>{channel.name}</Text>
        {channel.isVerified && (
          <View style={[styles.verifiedBadge, { backgroundColor: theme.primary }]}>
            <Feather name="check" size={10} color={theme.white} />
          </View>
        )}
      </View>

      <Text style={[styles.channelLink, { color: theme.primary }]}>{channel.link}</Text>

      {/* Stats row */}
      <View style={styles.channelStats}>
        <View style={styles.channelStatItem}>
          <Text style={[styles.channelStatValue, { color: theme.text }]}>{formatCount(channel.subscriberCount)}</Text>
          <Text style={[styles.channelStatLabel, { color: theme.textTertiary }]}>{t('channel_subscribers_count')}</Text>
        </View>
        <View style={[styles.channelStatDivider, { backgroundColor: theme.divider }]} />
        <View style={styles.channelStatItem}>
          <Text style={[styles.channelStatValue, { color: theme.text }]}>{formatCount(channel.postCount)}</Text>
          <Text style={[styles.channelStatLabel, { color: theme.textTertiary }]}>{t('channel_posts_label')}</Text>
        </View>
      </View>

      {/* Description */}
      {channel.description ? (
        <Text style={[styles.channelDescription, { color: theme.textSecondary }]}>{channel.description}</Text>
      ) : null}

      {/* Action buttons */}
      <View style={styles.channelActions}>
        <TouchableOpacity
          style={[
            styles.subscribeBtn,
            {
              backgroundColor: channel.isSubscribed ? theme.surface : theme.primary,
              borderColor: channel.isSubscribed ? theme.divider : theme.primary,
            },
          ]}
          onPress={onSubscribe}
          activeOpacity={0.85}
        >
          <Feather
            name={channel.isSubscribed ? 'bell-off' : 'bell'}
            size={16}
            color={channel.isSubscribed ? theme.textSecondary : theme.white}
          />
          <Text
            style={[
              styles.subscribeBtnText,
              { color: channel.isSubscribed ? theme.textSecondary : theme.white },
            ]}
          >
            {channel.isSubscribed ? t('unsubscribe') : t('subscribe')}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.shareBtn, { backgroundColor: theme.surface, borderColor: theme.divider }]}
          onPress={onShare}
          activeOpacity={0.85}
        >
          <Feather name="share-2" size={16} color={theme.primary} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

export function ChannelDetailsScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const navigation = useNavigation<ChannelDetailsNav>();
  const route = useRoute<ChannelDetailsRouteProp>();
  const { channelId } = route.params;

  const [channel, setChannel] = useState<ChannelInfo>(
    CHANNEL_INFO[channelId] ?? CHANNEL_INFO.default,
  );
  const [posts, setPosts] = useState<ChannelPost[]>(MOCK_POSTS);
  const [refreshing, setRefreshing] = useState(false);

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 800);
  }, []);

  const handleSubscribe = useCallback(() => {
    setChannel((prev) => ({ ...prev, isSubscribed: !prev.isSubscribed }));
  }, []);

  const handleShare = useCallback(async () => {
    try {
      await Share.share({ message: `${channel.name} — ${channel.link}` });
    } catch {
      // Share cancelled
    }
  }, [channel]);

  const handleLike = useCallback((postId: string) => {
    setPosts((prev) =>
      prev.map((p) =>
        p.id === postId
          ? { ...p, isLiked: !p.isLiked, likeCount: p.isLiked ? p.likeCount - 1 : p.likeCount + 1 }
          : p,
      ),
    );
  }, []);

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]} edges={['top']}>
      {/* Back button overlay */}
      <TouchableOpacity
        style={[styles.backButton, { backgroundColor: theme.background + 'CC' }]}
        onPress={() => navigation.goBack()}
        hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
      >
        <Feather name="arrow-left" size={22} color={theme.primary} />
      </TouchableOpacity>

      <FlatList
        data={posts}
        keyExtractor={(item) => item.id}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={theme.primary}
            colors={[theme.primary]}
          />
        }
        ListHeaderComponent={
          <ChannelHeader channel={channel} onSubscribe={handleSubscribe} onShare={handleShare} />
        }
        ListEmptyComponent={
          <View style={styles.empty}>
            <View style={[styles.emptyIcon, { backgroundColor: theme.surface }]}>
              <Feather name="file-text" size={36} color={theme.textTertiary} />
            </View>
            <Text style={[styles.emptyText, { color: theme.textSecondary }]}>{t('no_posts')}</Text>
          </View>
        }
        renderItem={({ item }) => <PostCard post={item} onLike={handleLike} />}
        ItemSeparatorComponent={() => (
          <View style={[styles.postSeparator, { backgroundColor: theme.divider }]} />
        )}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.listContent}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  backButton: {
    position: 'absolute',
    top: 52,
    left: 16,
    zIndex: 10,
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listContent: { paddingBottom: 32 },
  channelHeader: { paddingBottom: 8 },
  coverGradient: {
    height: 120,
    width: '100%',
  },
  channelAvatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'center',
    marginTop: -40,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  channelAvatarText: { fontSize: 28, fontWeight: '800', color: '#fff' },
  channelNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 12,
  },
  channelName: { fontSize: 20, fontWeight: '700', textAlign: 'center' },
  verifiedBadge: {
    width: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
  },
  channelLink: { textAlign: 'center', fontSize: 14, marginTop: 2, marginBottom: 16 },
  channelStats: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  channelStatItem: { alignItems: 'center', paddingHorizontal: 24 },
  channelStatValue: { fontSize: 18, fontWeight: '700' },
  channelStatLabel: { fontSize: 12, marginTop: 2 },
  channelStatDivider: { width: 1, height: 32 },
  channelDescription: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
    paddingHorizontal: 24,
    marginBottom: 16,
  },
  channelActions: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 10,
    marginBottom: 16,
  },
  subscribeBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    borderWidth: 1.5,
    gap: 7,
  },
  subscribeBtnText: { fontSize: 15, fontWeight: '600' },
  shareBtn: {
    width: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    borderWidth: 1.5,
  },
  postCard: {
    marginHorizontal: 16,
    borderRadius: 14,
    overflow: 'hidden',
  },
  postImage: {
    height: 180,
    alignItems: 'center',
    justifyContent: 'center',
  },
  postText: {
    fontSize: 15,
    lineHeight: 22,
    padding: 14,
  },
  postFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  postStats: { flexDirection: 'row', alignItems: 'center', gap: 4, flex: 1 },
  postStatText: { fontSize: 12 },
  postActions: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  postAction: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  postActionText: { fontSize: 12 },
  postTime: { fontSize: 11, marginLeft: 12 },
  postSeparator: { height: 8, marginHorizontal: 0 },
  empty: {
    paddingVertical: 48,
    alignItems: 'center',
    gap: 14,
  },
  emptyIcon: {
    width: 80,
    height: 80,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: { fontSize: 16, fontWeight: '500' },
});
