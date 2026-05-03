import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Dimensions,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { LinearGradient } from 'expo-linear-gradient';

import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { MainTabParamList, RootStackParamList } from '../../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Stories'>,
  NativeStackScreenProps<RootStackParamList>
>;

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const STORY_AVATAR_SIZE = 64;
const GRID_COLS = 3;
const GRID_ITEM_WIDTH = (SCREEN_WIDTH - 32 - 8 * (GRID_COLS - 1)) / GRID_COLS;
const GRID_ITEM_HEIGHT = GRID_ITEM_WIDTH * 1.5;

interface Story {
  id: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  previewColor: string;
  timestamp: Date;
  seen: boolean;
  viewCount?: number;
  isOwn?: boolean;
}

const PALETTE = ['#1565C0', '#0077B6', '#2E7D32', '#6A1B9A', '#E65100', '#AD1457', '#00695C', '#37474F'];
// Fixed gradient for unseen story ring (design constant, platform-independent)
const UNSEEN_STORY_GRADIENT: [string, string, string] = ['#F9A825', '#E91E63', '#9C27B0'];

const MOCK_STORIES: Story[] = [
  {
    id: 's0',
    userId: 'me',
    userName: 'Моя сторіс',
    previewColor: '#1565C0',
    timestamp: new Date(Date.now() - 1000 * 60 * 30),
    seen: true,
    viewCount: 24,
    isOwn: true,
  },
  {
    id: 's1',
    userId: 'u1',
    userName: 'Олексій К.',
    previewColor: PALETTE[1],
    timestamp: new Date(Date.now() - 1000 * 60 * 45),
    seen: false,
  },
  {
    id: 's2',
    userId: 'u2',
    userName: 'Марія І.',
    previewColor: PALETTE[2],
    timestamp: new Date(Date.now() - 1000 * 60 * 90),
    seen: false,
  },
  {
    id: 's3',
    userId: 'u3',
    userName: 'Андрій М.',
    previewColor: PALETTE[3],
    timestamp: new Date(Date.now() - 1000 * 60 * 120),
    seen: true,
  },
  {
    id: 's4',
    userId: 'u4',
    userName: 'Софія Б.',
    previewColor: PALETTE[4],
    timestamp: new Date(Date.now() - 1000 * 60 * 180),
    seen: false,
  },
  {
    id: 's5',
    userId: 'u5',
    userName: 'Дмитро Ш.',
    previewColor: PALETTE[5],
    timestamp: new Date(Date.now() - 1000 * 60 * 240),
    seen: true,
  },
  {
    id: 's6',
    userId: 'u6',
    userName: 'Катерина Л.',
    previewColor: PALETTE[6],
    timestamp: new Date(Date.now() - 1000 * 60 * 300),
    seen: false,
  },
  {
    id: 's7',
    userId: 'u7',
    userName: 'Ігор П.',
    previewColor: PALETTE[7],
    timestamp: new Date(Date.now() - 1000 * 60 * 360),
    seen: true,
  },
];

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

function formatStoryTime(date: Date): string {
  const diff = (Date.now() - date.getTime()) / 1000;
  if (diff < 3600) return `${Math.floor(diff / 60)}хв`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}год`;
  return `${Math.floor(diff / 86400)}д`;
}

interface HorizontalStoryBubbleProps {
  story: Story;
  onPress: (story: Story) => void;
}

function HorizontalStoryBubble({ story, onPress }: HorizontalStoryBubbleProps) {
  const theme = useTheme();
  const { t } = useTranslation();

  if (story.isOwn) {
    return (
      <TouchableOpacity style={styles.storyBubble} onPress={() => onPress(story)} activeOpacity={0.8}>
        <View style={[styles.ownStoryRing, { borderColor: theme.divider }]}>
          <View style={[styles.storyAvatarInner, { backgroundColor: story.previewColor }]}>
            <Text style={styles.storyInitials}>{getInitials(story.userName)}</Text>
          </View>
          <View style={[styles.addStoryBadge, { backgroundColor: theme.primary, borderColor: theme.background }]}>
            <Feather name="plus" size={10} color={theme.white} />
          </View>
        </View>
        <Text style={[styles.storyBubbleLabel, { color: theme.textSecondary }]} numberOfLines={1}>
          {t('my_story')}
        </Text>
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity style={styles.storyBubble} onPress={() => onPress(story)} activeOpacity={0.8}>
      <LinearGradient
        colors={story.seen ? [theme.divider, theme.divider] : UNSEEN_STORY_GRADIENT}
        start={{ x: 0, y: 1 }}
        end={{ x: 1, y: 0 }}
        style={styles.storyRing}
      >
        <View style={[styles.storyRingInner, { borderColor: theme.background }]}>
          <View style={[styles.storyAvatarInner, { backgroundColor: story.previewColor }]}>
            <Text style={styles.storyInitials}>{getInitials(story.userName)}</Text>
          </View>
        </View>
      </LinearGradient>
      <Text style={[styles.storyBubbleLabel, { color: theme.textSecondary }]} numberOfLines={1}>
        {story.userName.split(' ')[0]}
      </Text>
    </TouchableOpacity>
  );
}

interface GridStoryCardProps {
  story: Story;
  onPress: (story: Story) => void;
}

function GridStoryCard({ story, onPress }: GridStoryCardProps) {
  const theme = useTheme();

  return (
    <TouchableOpacity
      style={[styles.gridCard, { width: GRID_ITEM_WIDTH, height: GRID_ITEM_HEIGHT }]}
      onPress={() => onPress(story)}
      activeOpacity={0.85}
    >
      <View style={[styles.gridCardBg, { backgroundColor: story.previewColor }]}>
        <Text style={styles.gridCardInitials}>{getInitials(story.userName)}</Text>
      </View>

      {!story.seen && <View style={[styles.unseenDot, { backgroundColor: theme.primary }]} />}

      <LinearGradient
        colors={['transparent', 'rgba(0,0,0,0.65)']}
        style={styles.gridCardGradient}
      >
        <Text style={styles.gridCardName} numberOfLines={1}>
          {story.userName}
        </Text>
        <Text style={styles.gridCardTime}>{formatStoryTime(story.timestamp)}</Text>
      </LinearGradient>
    </TouchableOpacity>
  );
}

export function StoriesScreen({ navigation }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();
  const [stories, setStories] = useState<Story[]>(MOCK_STORIES);
  const [refreshing, setRefreshing] = useState(false);

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 800);
  }, []);

  const handleStoryPress = useCallback(
    (story: Story) => {
      if (story.isOwn) {
        Alert.alert(t('coming_soon'), '');
        return;
      }
      setStories((prev) => prev.map((s) => (s.id === story.id ? { ...s, seen: true } : s)));
      Alert.alert(story.userName, t('coming_soon'));
    },
    [t],
  );

  const handleAddStory = useCallback(() => {
    Alert.alert(t('add_story'), t('coming_soon'));
  }, [t]);

  const contactStories = stories.filter((s) => !s.isOwn);
  const unseenCount = contactStories.filter((s) => !s.seen).length;

  const gridRows: Story[][] = [];
  for (let i = 0; i < contactStories.length; i += GRID_COLS) {
    gridRows.push(contactStories.slice(i, i + GRID_COLS));
  }

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: theme.divider }]}>
        <Text style={[styles.headerTitle, { color: theme.text }]}>{t('stories')}</Text>
        <TouchableOpacity
          onPress={handleAddStory}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={[styles.addBtn, { backgroundColor: theme.primary }]}
        >
          <Feather name="plus" size={18} color={theme.white} />
        </TouchableOpacity>
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={theme.primary}
            colors={[theme.primary]}
          />
        }
        contentContainerStyle={styles.scrollContent}
      >
        {/* Horizontal story row */}
        <View style={[styles.horizontalSection, { backgroundColor: theme.surface }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.horizontalScroll}>
            {stories.map((story) => (
              <HorizontalStoryBubble key={story.id} story={story} onPress={handleStoryPress} />
            ))}
          </ScrollView>
        </View>

        {/* Grid section */}
        <View style={styles.gridSection}>
          <View style={styles.gridSectionHeader}>
            <Text style={[styles.gridSectionTitle, { color: theme.textTertiary }]}>
              {t('stories_from_contacts').toUpperCase()}
            </Text>
            {unseenCount > 0 && (
              <View style={[styles.unseenBadge, { backgroundColor: theme.primary }]}>
                <Text style={[styles.unseenBadgeText, { color: theme.white }]}>{unseenCount}</Text>
              </View>
            )}
          </View>

          {contactStories.length === 0 ? (
            <View style={styles.empty}>
              <View style={[styles.emptyIcon, { backgroundColor: theme.surface }]}>
                <Feather name="aperture" size={36} color={theme.textTertiary} />
              </View>
              <Text style={[styles.emptyText, { color: theme.textSecondary }]}>{t('no_stories')}</Text>
            </View>
          ) : (
            <View style={styles.grid}>
              {gridRows.map((row, ri) => (
                <View key={ri} style={styles.gridRow}>
                  {row.map((story) => (
                    <GridStoryCard key={story.id} story={story} onPress={handleStoryPress} />
                  ))}
                  {row.length < GRID_COLS &&
                    Array(GRID_COLS - row.length)
                      .fill(null)
                      .map((_, i) => (
                        <View key={`empty-${i}`} style={{ width: GRID_ITEM_WIDTH }} />
                      ))}
                </View>
              ))}
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { fontSize: 20, fontWeight: '700' },
  addBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrollContent: { paddingBottom: 32 },
  horizontalSection: {
    paddingVertical: 12,
    marginBottom: 8,
  },
  horizontalScroll: {
    paddingHorizontal: 12,
    gap: 4,
  },
  storyBubble: {
    alignItems: 'center',
    width: STORY_AVATAR_SIZE + 16,
    paddingHorizontal: 4,
    gap: 6,
  },
  ownStoryRing: {
    width: STORY_AVATAR_SIZE,
    height: STORY_AVATAR_SIZE,
    borderRadius: STORY_AVATAR_SIZE / 2,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  storyRing: {
    width: STORY_AVATAR_SIZE + 4,
    height: STORY_AVATAR_SIZE + 4,
    borderRadius: (STORY_AVATAR_SIZE + 4) / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  storyRingInner: {
    width: STORY_AVATAR_SIZE - 2,
    height: STORY_AVATAR_SIZE - 2,
    borderRadius: (STORY_AVATAR_SIZE - 2) / 2,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  storyAvatarInner: {
    width: STORY_AVATAR_SIZE - 8,
    height: STORY_AVATAR_SIZE - 8,
    borderRadius: (STORY_AVATAR_SIZE - 8) / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  storyInitials: { fontSize: 18, fontWeight: '700', color: '#fff' },
  addStoryBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  storyBubbleLabel: { fontSize: 11, fontWeight: '500', textAlign: 'center', maxWidth: STORY_AVATAR_SIZE + 12 },
  gridSection: { paddingHorizontal: 16, paddingTop: 8 },
  gridSectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
    gap: 8,
  },
  gridSectionTitle: { fontSize: 12, fontWeight: '600', letterSpacing: 0.5 },
  unseenBadge: {
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  unseenBadgeText: { fontSize: 11, fontWeight: '700' },
  grid: { gap: 8 },
  gridRow: { flexDirection: 'row', gap: 8 },
  gridCard: {
    borderRadius: 12,
    overflow: 'hidden',
    position: 'relative',
  },
  gridCardBg: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  gridCardInitials: { fontSize: 28, fontWeight: '800', color: 'rgba(255,255,255,0.5)' },
  unseenDot: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  gridCardGradient: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 8,
    paddingBottom: 8,
    paddingTop: 20,
  },
  gridCardName: { fontSize: 12, fontWeight: '700', color: '#fff' },
  gridCardTime: { fontSize: 10, color: 'rgba(255,255,255,0.7)', marginTop: 1 },
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
