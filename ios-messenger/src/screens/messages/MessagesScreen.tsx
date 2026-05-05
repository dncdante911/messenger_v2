// ============================================================
// WorldMates Messenger — MessagesScreen
// Full private chat screen.  Route params:
//   chatId, chatType, chatName, chatAvatar?, userId
// ============================================================

import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import { socketService } from '../../services/socketService';
import {
  SOCKET_EVENT_PRIVATE_MESSAGE,
  SOCKET_EVENT_TYPING,
  SOCKET_EVENT_TYPING_DONE,
  SOCKET_EVENT_MESSAGE_SEEN,
} from '../../constants/api';
import * as chatApi from '../../api/chatApi';
import { normaliseMessage } from '../../api/chatApi';
import type { Message } from '../../api/types';
import { Avatar } from '../../components/common/Avatar';
import MessageBubble from './MessageBubble';
import MessageInput from './MessageInput';
import TypingIndicator from './TypingIndicator';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<RootStackParamList, 'Messages'>;

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────

const TYPING_DEBOUNCE_MS = 500;

// ─────────────────────────────────────────────────────────────
// COMPONENT
// ─────────────────────────────────────────────────────────────

const MessagesScreen: React.FC<Props> = ({ navigation, route }) => {
  const { chatName, chatAvatar, userId = '' } = route.params;
  const theme = useTheme();
  const { t } = useTranslation();

  const currentUser = useAuthStore((s) => s.user);
  const currentUserId = currentUser?.id ?? '';

  // ── Local state ──────────────────────────────────────────

  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [isOnline, setIsOnline] = useState(false);
  const [lastSeen, setLastSeen] = useState<string>('');
  const [isTyping, setIsTyping] = useState(false);
  const [replyTo, setReplyTo] = useState<Message | null>(null);

  // ── Refs ─────────────────────────────────────────────────

  const flatListRef = useRef<FlatList<Message>>(null);
  const typingDebounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMounted = useRef(true);

  // ─────────────────────────────────────────────────────────
  // LOAD MESSAGES
  // ─────────────────────────────────────────────────────────

  const loadMessages = useCallback(async () => {
    if (!userId) return;
    setIsLoading(true);
    try {
      const data = await chatApi.getMessages(userId);
      if (!isMounted.current) return;
      setMessages(data);
      setHasMore(data.length > 0);
      // Mark as seen
      chatApi.markSeen(userId).catch(() => null);
    } catch {
      if (!isMounted.current) return;
      Alert.alert(t('error'), t('error_load_messages'));
    } finally {
      if (isMounted.current) setIsLoading(false);
    }
  }, [userId]);

  // ─────────────────────────────────────────────────────────
  // LOAD MORE (older messages — inverted list pulls from bottom)
  // ─────────────────────────────────────────────────────────

  const loadMore = useCallback(async () => {
    if (!hasMore || isLoadingMore || messages.length === 0) return;
    const lastMsg = messages[messages.length - 1];
    setIsLoadingMore(true);
    try {
      const older = await chatApi.loadMoreMessages(userId, lastMsg.id);
      if (!isMounted.current) return;
      if (older.length === 0) {
        setHasMore(false);
      } else {
        setMessages((prev) => [...prev, ...older]);
      }
    } catch {
      // Non-critical — silently ignore
    } finally {
      if (isMounted.current) setIsLoadingMore(false);
    }
  }, [hasMore, isLoadingMore, messages, userId]);

  // ─────────────────────────────────────────────────────────
  // USER STATUS
  // ─────────────────────────────────────────────────────────

  const loadUserStatus = useCallback(async () => {
    if (!userId) return;
    try {
      const status = await chatApi.getUserStatus(userId);
      if (!isMounted.current) return;
      setIsOnline(status.isOnline);
      setLastSeen(status.lastSeen ?? '');
    } catch {
      // Silent
    }
  }, [userId]);

  // ─────────────────────────────────────────────────────────
  // SOCKET HANDLERS
  // ─────────────────────────────────────────────────────────

  useEffect(() => {
    if (!userId) return;

    // Incoming private message — normalise snake_case server payload
    const handlePrivateMessage = (data: unknown) => {
      if (!isMounted.current) return;
      const msg = normaliseMessage(data as Record<string, unknown>);
      if (msg.fromId !== userId && msg.toId !== userId) return;
      setMessages((prev) => {
        if (prev.some((m) => m.id === msg.id)) return prev;
        return [msg, ...prev];
      });
      chatApi.markSeen(userId).catch(() => null);
    };

    // Typing indicators — server payload contains user_id (numeric) of the typer
    const handleTyping = (data: unknown) => {
      if (!isMounted.current) return;
      const payload = data as { user_id?: string | number; recipient_id?: string | number };
      if (
        String(payload?.user_id) === userId ||
        String(payload?.recipient_id) === userId
      ) {
        setIsTyping(true);
      }
    };

    const handleTypingDone = (data: unknown) => {
      if (!isMounted.current) return;
      const payload = data as { user_id?: string | number; recipient_id?: string | number };
      if (
        String(payload?.user_id) === userId ||
        String(payload?.recipient_id) === userId
      ) {
        setIsTyping(false);
      }
    };

    // Message seen — mark our sent messages as read
    const handleMessageSeen = (data: unknown) => {
      if (!isMounted.current) return;
      const payload = data as { sender_id?: string | number };
      if (String(payload?.sender_id) !== userId) return;
      setMessages((prev) =>
        prev.map((m) => (m.fromId === currentUserId ? { ...m, isSeen: true } : m)),
      );
    };

    socketService.on(SOCKET_EVENT_PRIVATE_MESSAGE, handlePrivateMessage);
    socketService.on(SOCKET_EVENT_TYPING, handleTyping);
    socketService.on(SOCKET_EVENT_TYPING_DONE, handleTypingDone);
    socketService.on(SOCKET_EVENT_MESSAGE_SEEN, handleMessageSeen);

    // Notify server that user opened this chat
    socketService.joinChat(userId);

    return () => {
      socketService.off(SOCKET_EVENT_PRIVATE_MESSAGE, handlePrivateMessage);
      socketService.off(SOCKET_EVENT_TYPING, handleTyping);
      socketService.off(SOCKET_EVENT_TYPING_DONE, handleTypingDone);
      socketService.off(SOCKET_EVENT_MESSAGE_SEEN, handleMessageSeen);
      socketService.closeChat(userId);
    };
  }, [userId, currentUserId]);

  // ─────────────────────────────────────────────────────────
  // MOUNT / UNMOUNT
  // ─────────────────────────────────────────────────────────

  useEffect(() => {
    isMounted.current = true;
    loadMessages();
    loadUserStatus();

    return () => {
      isMounted.current = false;
      if (typingDebounceTimer.current) {
        clearTimeout(typingDebounceTimer.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ─────────────────────────────────────────────────────────
  // SEND MESSAGE
  // ─────────────────────────────────────────────────────────

  const handleSend = useCallback(
    async (text: string) => {
      const tempId = `tmp_${Date.now()}`;
      const optimisticMsg: Message = {
        id: tempId,
        fromId: currentUserId,
        toId: userId,
        text,
        type: 'text',
        isEdited: false,
        isDeleted: false,
        isSeen: false,
        isPinned: false,
        isLocalPending: true,
        createdAt: Date.now(),
        ...(replyTo != null
          ? {
              replyToId: replyTo.id,
              replyToText: replyTo.text,
              replyToName: replyTo.senderName ?? replyTo.fromId,
            }
          : {}),
      };

      // Optimistic update
      setMessages((prev) => [optimisticMsg, ...prev]);
      const replyId = replyTo?.id;
      setReplyTo(null);

      // Signal typing done
      socketService.sendTypingDone(userId);

      try {
        const real = await chatApi.sendMessage(userId, text, replyId);
        if (!isMounted.current) return;
        setMessages((prev) =>
          prev.map((m) => (m.id === tempId ? { ...real, isLocalPending: false } : m)),
        );
      } catch {
        if (!isMounted.current) return;
        // Mark optimistic as errored
        setMessages((prev) =>
          prev.map((m) =>
            m.id === tempId
              ? { ...m, isLocalPending: false, text: `${m.text} ⚠️` }
              : m,
          ),
        );
        Alert.alert(t('error'), t('error_send_message'));
      }
    },
    [currentUserId, userId, replyTo],
  );

  // ─────────────────────────────────────────────────────────
  // TYPING INDICATOR EMIT
  // ─────────────────────────────────────────────────────────

  const handleTypingStart = useCallback(() => {
    if (typingDebounceTimer.current) {
      clearTimeout(typingDebounceTimer.current);
    }
    typingDebounceTimer.current = setTimeout(() => {
      socketService.sendTyping(userId);
    }, TYPING_DEBOUNCE_MS);
  }, [userId]);

  const handleTypingStop = useCallback(() => {
    if (typingDebounceTimer.current) {
      clearTimeout(typingDebounceTimer.current);
    }
    socketService.sendTypingDone(userId);
  }, [userId]);

  // ─────────────────────────────────────────────────────────
  // CALLS
  // ─────────────────────────────────────────────────────────

  const handleVoiceCall = useCallback(() => {
    Alert.alert(t('voice_call'), t('coming_soon'));
  }, [t]);

  const handleVideoCall = useCallback(() => {
    Alert.alert(t('video_call'), t('coming_soon'));
  }, [t]);

  // ─────────────────────────────────────────────────────────
  // MESSAGE INTERACTIONS
  // ─────────────────────────────────────────────────────────

  const handleLongPress = useCallback((_msg: Message) => {
    // Long-press menu is handled inside MessageBubble
  }, []);

  const handleReply = useCallback((msg: Message) => {
    setReplyTo(msg);
  }, []);

  const handleCancelReply = useCallback(() => {
    setReplyTo(null);
  }, []);

  const handleDelete = useCallback(
    async (msg: Message) => {
      Alert.alert(
        t('delete_message'),
        t('delete_message_confirm'),
        [
          { text: t('cancel'), style: 'cancel' },
          {
            text: t('delete'),
            style: 'destructive',
            onPress: async () => {
              setMessages((prev) => prev.filter((m) => m.id !== msg.id));
              try {
                await chatApi.deleteMessage(msg.id);
              } catch {
                setMessages((prev) => [msg, ...prev]);
                Alert.alert(t('error'), t('error_delete_message'));
              }
            },
          },
        ],
      );
    },
    [t],
  );

  const handleEdit = useCallback(
    (msg: Message) => {
      Alert.prompt(
        t('edit_message'),
        undefined,
        async (newText: string) => {
          if (!newText || newText.trim() === msg.text) return;
          const trimmed = newText.trim();
          setMessages((prev) =>
            prev.map((m) => (m.id === msg.id ? { ...m, text: trimmed, isEdited: true } : m)),
          );
          try {
            await chatApi.editMessage(msg.id, trimmed);
          } catch {
            setMessages((prev) =>
              prev.map((m) => (m.id === msg.id ? { ...m, text: msg.text, isEdited: msg.isEdited } : m)),
            );
            Alert.alert(t('error'), t('error_edit_message'));
          }
        },
        'plain-text',
        msg.text,
      );
    },
    [t],
  );

  // ─────────────────────────────────────────────────────────
  // RENDER HELPERS
  // ─────────────────────────────────────────────────────────

  const renderMessage = useCallback(
    ({ item, index }: { item: Message; index: number }) => (
      <MessageBubble
        message={item}
        isOwn={item.fromId === currentUserId}
        prevMessage={messages[index + 1]}
        nextMessage={messages[index - 1]}
        onLongPress={handleLongPress}
        onReply={handleReply}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />
    ),
    [currentUserId, messages, handleLongPress, handleReply, handleEdit, handleDelete],
  );

  const keyExtractor = useCallback((item: Message) => item.id, []);

  const renderListFooter = useCallback(() => {
    // Footer in an inverted list appears at the top (oldest messages)
    if (!isLoadingMore) return null;
    return (
      <ActivityIndicator
        size="small"
        color={theme.primary}
        style={styles.loadMoreSpinner}
      />
    );
  }, [isLoadingMore]);

  // ─────────────────────────────────────────────────────────
  // STATUS SUBTITLE
  // ─────────────────────────────────────────────────────────

  const statusSubtitle = isOnline ? t('online') : lastSeen ? `${t('last_seen')} ${lastSeen}` : '';

  // ─────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]}>
      {/* ── Header ──────────────────────────────────────────── */}
      <View style={[styles.header, { borderBottomColor: theme.divider, backgroundColor: theme.background }]}>
        {/* Back */}
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={styles.headerBack}
        >
          <Feather name="chevron-left" size={26} color={theme.text} />
        </TouchableOpacity>

        {/* Avatar + name + status */}
        <TouchableOpacity
          style={styles.headerCenter}
          activeOpacity={0.75}
          onPress={() => Alert.alert(t('profile'), t('coming_soon'))}
        >
          <Avatar
            uri={chatAvatar}
            name={chatName}
            size={36}
            showOnline
            isOnline={isOnline}
          />
          <View style={styles.headerTextBlock}>
            <Text style={[styles.headerName, { color: theme.text }]} numberOfLines={1}>
              {chatName}
            </Text>
            {statusSubtitle.length > 0 && (
              <Text style={[styles.headerStatus, { color: isOnline ? theme.online : theme.textSecondary }]} numberOfLines={1}>
                {statusSubtitle}
              </Text>
            )}
          </View>
        </TouchableOpacity>

        {/* Action icons */}
        <View style={styles.headerActions}>
          <TouchableOpacity
            onPress={handleVoiceCall}
            hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            style={styles.headerActionBtn}
          >
            <Feather name="phone" size={20} color={theme.text} />
          </TouchableOpacity>
          <TouchableOpacity
            onPress={handleVideoCall}
            hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            style={styles.headerActionBtn}
          >
            <Feather name="video" size={20} color={theme.text} />
          </TouchableOpacity>
        </View>
      </View>

      {/* ── Message list ────────────────────────────────────── */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.primary} />
        </View>
      ) : (
        <FlatList
          ref={flatListRef}
          data={messages}
          renderItem={renderMessage}
          keyExtractor={keyExtractor}
          inverted
          contentContainerStyle={styles.listContent}
          ListFooterComponent={renderListFooter}
          onEndReached={loadMore}
          onEndReachedThreshold={0.25}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          removeClippedSubviews
          maxToRenderPerBatch={20}
          windowSize={15}
        />
      )}

      {/* ── Typing indicator ────────────────────────────────── */}
      <TypingIndicator userName={chatName} isVisible={isTyping} />

      {/* ── Input ───────────────────────────────────────────── */}
      <MessageInput
        onSend={handleSend}
        onTyping={handleTypingStart}
        onTypingStop={handleTypingStop}
        replyTo={replyTo}
        onCancelReply={handleCancelReply}
      />
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerBack: {
    marginRight: 2,
    padding: 4,
  },
  headerCenter: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    overflow: 'hidden',
  },
  headerTextBlock: {
    flex: 1,
    justifyContent: 'center',
  },
  headerName: {
    fontSize: 16,
    fontWeight: '700',
  },
  headerStatus: {
    fontSize: 12,
    marginTop: 1,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  headerActionBtn: {
    padding: 6,
  },

  // Loading
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadMoreSpinner: {
    paddingVertical: 12,
  },

  // FlatList
  listContent: {
    paddingVertical: 10,
  },
});

export default MessagesScreen;
