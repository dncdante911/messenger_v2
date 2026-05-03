import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { TranslationKeys } from '../../i18n';
import { Avatar } from '../../components/common/Avatar';
import type { MainTabParamList, RootStackParamList } from '../../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Calls'>,
  NativeStackScreenProps<RootStackParamList>
>;

type CallType = 'incoming' | 'outgoing' | 'missed';
type CallFilter = 'all' | 'missed' | 'incoming' | 'outgoing';

interface CallItem {
  id: string;
  userId: string;
  name: string;
  avatar?: string;
  callType: 'voice' | 'video';
  direction: CallType;
  duration?: number;
  timestamp: Date;
  isGroupCall?: boolean;
  groupName?: string;
}

const MOCK_CALLS: CallItem[] = [
  {
    id: '1',
    userId: 'u1',
    name: 'Олексій Коваль',
    callType: 'voice',
    direction: 'incoming',
    duration: 312,
    timestamp: new Date(Date.now() - 1000 * 60 * 15),
  },
  {
    id: '2',
    userId: 'u2',
    name: 'Марія Іванова',
    callType: 'video',
    direction: 'outgoing',
    duration: 840,
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2),
  },
  {
    id: '3',
    userId: 'u3',
    name: 'Андрій Мельник',
    callType: 'voice',
    direction: 'missed',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 5),
  },
  {
    id: '4',
    userId: 'u4',
    name: 'Софія Бондар',
    callType: 'video',
    direction: 'incoming',
    duration: 1620,
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 24),
  },
  {
    id: '5',
    userId: 'u5',
    name: 'DevTeam Ukraine',
    callType: 'voice',
    direction: 'incoming',
    duration: 2400,
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 24),
    isGroupCall: true,
    groupName: 'DevTeam Ukraine',
  },
  {
    id: '6',
    userId: 'u6',
    name: 'Дмитро Шевченко',
    callType: 'voice',
    direction: 'missed',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 48),
  },
  {
    id: '7',
    userId: 'u7',
    name: 'Катерина Лисенко',
    callType: 'video',
    direction: 'outgoing',
    duration: 180,
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 48),
  },
  {
    id: '8',
    userId: 'u1',
    name: 'Олексій Коваль',
    callType: 'voice',
    direction: 'outgoing',
    duration: 540,
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 72),
  },
];

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}с`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s > 0 ? `${m}хв ${s}с` : `${m}хв`;
}

function formatCallTime(date: Date): string {
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const dayMs = 1000 * 60 * 60 * 24;

  if (diff < dayMs) {
    return date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
  }
  if (diff < 2 * dayMs) {
    return 'Вчора';
  }
  return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short' });
}

function groupByDate(calls: CallItem[]): { label: string; data: CallItem[] }[] {
  const groups: Record<string, CallItem[]> = {};
  const now = new Date();
  const todayStr = now.toDateString();
  const yesterday = new Date(now.getTime() - 86400000);
  const yesterdayStr = yesterday.toDateString();

  for (const call of calls) {
    const ds = call.timestamp.toDateString();
    let label: string;
    if (ds === todayStr) label = 'Сьогодні';
    else if (ds === yesterdayStr) label = 'Вчора';
    else label = call.timestamp.toLocaleDateString('uk-UA', { day: 'numeric', month: 'long' });
    if (!groups[label]) groups[label] = [];
    groups[label].push(call);
  }

  return Object.entries(groups).map(([label, data]) => ({ label, data }));
}

interface CallRowProps {
  item: CallItem;
  onCallBack: (item: CallItem) => void;
  onDelete: (item: CallItem) => void;
}

function CallRow({ item, onCallBack, onDelete }: CallRowProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const isMissed = item.direction === 'missed';
  const dirColor = isMissed ? theme.error : theme.success;

  const dirIcon: React.ComponentProps<typeof Feather>['name'] =
    item.direction === 'incoming'
      ? 'phone-incoming'
      : item.direction === 'outgoing'
      ? 'phone-outgoing'
      : 'phone-missed';

  return (
    <TouchableOpacity
      style={[styles.callRow, { backgroundColor: theme.surface }]}
      activeOpacity={0.7}
      onLongPress={() => {
        Alert.alert(item.name, '', [
          { text: t('call_back'), onPress: () => onCallBack(item) },
          { text: t('delete_call'), style: 'destructive', onPress: () => onDelete(item) },
          { text: t('cancel'), style: 'cancel' },
        ]);
      }}
    >
      <Avatar
        name={item.name}
        size={46}
        online={false}
      />

      <View style={styles.callInfo}>
        <Text style={[styles.callName, { color: isMissed ? theme.error : theme.text }]} numberOfLines={1}>
          {item.isGroupCall ? item.groupName : item.name}
        </Text>
        <View style={styles.callMeta}>
          <Feather name={dirIcon} size={13} color={dirColor} style={styles.callDirIcon} />
          <Text style={[styles.callSubtitle, { color: theme.textSecondary }]}>
            {item.isGroupCall
              ? t('group_call')
              : item.callType === 'video'
              ? t('video_call')
              : t('voice_call')}
            {item.duration ? `  ·  ${formatDuration(item.duration)}` : ''}
          </Text>
        </View>
      </View>

      <View style={styles.callRight}>
        <Text style={[styles.callTime, { color: theme.textTertiary }]}>{formatCallTime(item.timestamp)}</Text>
        <TouchableOpacity
          onPress={() => onCallBack(item)}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={[styles.callBackBtn, { backgroundColor: theme.primary + '18' }]}
        >
          <Feather
            name={item.callType === 'video' ? 'video' : 'phone'}
            size={16}
            color={theme.primary}
          />
        </TouchableOpacity>
      </View>
    </TouchableOpacity>
  );
}

const FILTERS: { key: CallFilter; label: TranslationKeys }[] = [
  { key: 'all', label: 'call_filter_all' },
  { key: 'missed', label: 'call_filter_missed' },
  { key: 'incoming', label: 'call_filter_incoming' },
  { key: 'outgoing', label: 'call_filter_outgoing' },
];

export function CallsScreen({ navigation }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();

  const [filter, setFilter] = useState<CallFilter>('all');
  const [calls, setCalls] = useState<CallItem[]>(MOCK_CALLS);
  const [refreshing, setRefreshing] = useState(false);

  const filtered = filter === 'all' ? calls : calls.filter((c) => c.direction === filter);
  const grouped = groupByDate(filtered);

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 800);
  }, []);

  const handleClearHistory = useCallback(() => {
    Alert.alert(t('clear_history'), t('clear_history_confirm'), [
      { text: t('cancel'), style: 'cancel' },
      {
        text: t('clear'),
        style: 'destructive',
        onPress: () => setCalls([]),
      },
    ]);
  }, [t]);

  const handleCallBack = useCallback(
    (item: CallItem) => {
      Alert.alert(t('coming_soon'), '');
    },
    [t],
  );

  const handleDelete = useCallback((item: CallItem) => {
    setCalls((prev) => prev.filter((c) => c.id !== item.id));
  }, []);

  const isEmpty = filtered.length === 0;
  const emptyKey: TranslationKeys = filter === 'missed' ? 'no_missed_calls' : 'no_calls';

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: theme.divider }]}>
        <Text style={[styles.headerTitle, { color: theme.text }]}>{t('calls')}</Text>
        {calls.length > 0 && (
          <TouchableOpacity onPress={handleClearHistory} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Feather name="trash-2" size={20} color={theme.textSecondary} />
          </TouchableOpacity>
        )}
      </View>

      {/* Filter tabs */}
      <View style={[styles.filterRow, { backgroundColor: theme.surface, borderBottomColor: theme.divider }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterScroll}>
          {FILTERS.map(({ key, label }) => {
            const active = filter === key;
            return (
              <TouchableOpacity
                key={key}
                style={[styles.filterTab, active && { borderBottomColor: theme.primary, borderBottomWidth: 2 }]}
                onPress={() => setFilter(key)}
                activeOpacity={0.7}
              >
                <Text
                  style={[
                    styles.filterLabel,
                    { color: active ? theme.primary : theme.textSecondary },
                    active && styles.filterLabelActive,
                  ]}
                >
                  {t(label)}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      </View>

      {isEmpty ? (
        <View style={styles.empty}>
          <View style={[styles.emptyIcon, { backgroundColor: theme.surface }]}>
            <Feather name="phone-off" size={36} color={theme.textTertiary} />
          </View>
          <Text style={[styles.emptyText, { color: theme.textSecondary }]}>{t(emptyKey)}</Text>
        </View>
      ) : (
        <FlatList
          data={grouped}
          keyExtractor={(g) => g.label}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={handleRefresh}
              tintColor={theme.primary}
              colors={[theme.primary]}
            />
          }
          renderItem={({ item: group }) => (
            <View>
              <Text style={[styles.dateHeader, { color: theme.textTertiary }]}>{group.label}</Text>
              {group.data.map((call, idx) => (
                <React.Fragment key={call.id}>
                  <CallRow item={call} onCallBack={handleCallBack} onDelete={handleDelete} />
                  {idx < group.data.length - 1 && (
                    <View style={[styles.separator, { backgroundColor: theme.divider, marginLeft: 76 }]} />
                  )}
                </React.Fragment>
              ))}
            </View>
          )}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
        />
      )}

      {/* New call FAB */}
      <TouchableOpacity
        style={[styles.fab, { backgroundColor: theme.primary, shadowColor: theme.primary }]}
        activeOpacity={0.85}
        onPress={() => Alert.alert(t('coming_soon'), '')}
      >
        <Feather name="phone-call" size={22} color={theme.white} />
      </TouchableOpacity>
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
  filterRow: {
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  filterScroll: {
    paddingHorizontal: 8,
  },
  filterTab: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  filterLabel: { fontSize: 14, fontWeight: '500' },
  filterLabelActive: { fontWeight: '700' },
  listContent: { paddingBottom: 90 },
  dateHeader: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.4,
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 6,
  },
  callRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  callInfo: { flex: 1, marginLeft: 12 },
  callName: { fontSize: 15, fontWeight: '600', marginBottom: 3 },
  callMeta: { flexDirection: 'row', alignItems: 'center' },
  callDirIcon: { marginRight: 4 },
  callSubtitle: { fontSize: 13 },
  callRight: { alignItems: 'flex-end', gap: 6 },
  callTime: { fontSize: 12 },
  callBackBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  separator: { height: StyleSheet.hairlineWidth },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 6,
  },
});
