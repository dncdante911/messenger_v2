import React, { useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Alert,
  Linking,
  Share,
  StyleSheet,
  Image,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { Message } from '../../api/types';
import { MESSAGE_TYPES } from '../../constants/api';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { ThemeColors } from '../../theme';

interface MessageBubbleProps {
  message: Message;
  isOwn: boolean;
  prevMessage?: Message;
  nextMessage?: Message;
  onLongPress: (msg: Message) => void;
  onReply: (msg: Message) => void;
  onEdit?: (msg: Message) => void;
  onDelete?: (msg: Message) => void;
}

function formatTime(ts: string | number): string {
  let ms: number;
  if (typeof ts === 'number') {
    // > 1e12 → already milliseconds; otherwise treat as Unix seconds
    ms = ts > 1e12 ? ts : ts * 1000;
  } else {
    ms = new Date(ts).getTime();
  }
  const date = new Date(ms);
  if (isNaN(date.getTime())) return '';
  const h = date.getHours().toString().padStart(2, '0');
  const m = date.getMinutes().toString().padStart(2, '0');
  return `${h}:${m}`;
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const URL_REGEX = /(https?:\/\/[^\s]+)/g;

const ReplyQuote: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  if (!message.replyToId) return null;
  return (
    <View style={styles.replyQuote}>
      <View style={[styles.replyQuoteBorder, { backgroundColor: theme.primary }]} />
      <View style={styles.replyQuoteBody}>
        <Text style={[styles.replyQuoteAuthor, { color: theme.primary }]} numberOfLines={1}>
          {message.replyToName ?? 'Message'}
        </Text>
        <Text style={[styles.replyQuoteText, { color: theme.textSecondary }]} numberOfLines={1}>
          {message.replyToText ?? '(Media)'}
        </Text>
      </View>
    </View>
  );
};

const LinkifiedText: React.FC<{ text: string; style: object; theme: ThemeColors }> = ({ text, style, theme }) => {
  const parts = text.split(URL_REGEX);
  return (
    <Text style={style}>
      {parts.map((part, i) => {
        if (URL_REGEX.test(part)) {
          return (
            <Text
              key={i}
              style={[styles.link, { color: theme.accent }]}
              onPress={() => Linking.openURL(part).catch(() => null)}
            >
              {part}
            </Text>
          );
        }
        return part;
      })}
    </Text>
  );
};

const TextContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => (
  <View>
    <LinkifiedText text={message.text} style={styles.messageText} theme={theme} />
    {message.isEdited && <Text style={[styles.editedLabel, { color: theme.textTertiary }]}>(edited)</Text>}
  </View>
);

const ImageContent: React.FC<{ message: Message }> = ({ message }) => {
  const url = message.mediaUrl ?? '';
  return (
    <TouchableOpacity onPress={() => Alert.alert('', '')} activeOpacity={0.85}>
      <Image source={{ uri: url }} style={styles.imageContent} resizeMode="cover" />
    </TouchableOpacity>
  );
};

const VideoContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  const thumb = message.mediaThumb ?? message.mediaUrl ?? '';
  const duration = message.mediaDuration ?? 0;
  return (
    <TouchableOpacity
      style={[styles.videoContainer, { backgroundColor: theme.background }]}
      onPress={() => Alert.alert('', '')}
      activeOpacity={0.85}
    >
      {thumb ? (
        <Image source={{ uri: thumb }} style={styles.videoThumbnail} resizeMode="cover" />
      ) : (
        <View style={[styles.videoThumbnailPlaceholder, { backgroundColor: theme.surface }]} />
      )}
      <View style={styles.videoPlayOverlay}>
        <Feather name="play" size={28} color="#FFFFFF" />
      </View>
      {duration > 0 && (
        <View style={styles.videoDurationBadge}>
          <Text style={styles.videoDurationText}>{formatDuration(duration)}</Text>
        </View>
      )}
    </TouchableOpacity>
  );
};

const AudioContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  const duration = message.mediaDuration ?? 0;
  return (
    <View style={styles.audioContainer}>
      <TouchableOpacity
        onPress={() => Alert.alert('', '')}
        style={[styles.audioPlayButton, { backgroundColor: theme.primary }]}
      >
        <Feather name="play" size={18} color="#FFFFFF" />
      </TouchableOpacity>
      <View style={styles.waveformContainer}>
        {Array.from({ length: 10 }).map((_, i) => (
          <View key={i} style={[styles.waveformBar, { height: 6 + ((i * 3) % 14) }]} />
        ))}
      </View>
      {duration > 0 && (
        <Text style={[styles.audioDuration, { color: theme.textTertiary }]}>{formatDuration(duration)}</Text>
      )}
    </View>
  );
};

const FileContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  const name = message.fileName ?? 'File';
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  const size = message.mediaSize ?? 0;

  const iconName: React.ComponentProps<typeof Feather>['name'] =
    ['pdf'].includes(ext) ? 'file-text'
    : ['mp3', 'wav', 'm4a', 'ogg'].includes(ext) ? 'music'
    : ['mp4', 'mov', 'avi'].includes(ext) ? 'film'
    : ['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext) ? 'image'
    : 'file';

  return (
    <TouchableOpacity style={styles.fileContainer} onPress={() => Alert.alert('', '')} activeOpacity={0.8}>
      <View style={[styles.fileIconWrapper, { backgroundColor: theme.primary + '26' }]}>
        <Feather name={iconName} size={22} color={theme.primary} />
      </View>
      <View style={styles.fileInfo}>
        <Text style={styles.fileName} numberOfLines={2}>{name}</Text>
        {size > 0 && <Text style={[styles.fileSize, { color: theme.textTertiary }]}>{formatFileSize(size)}</Text>}
      </View>
      <Feather name="download" size={16} color={theme.textTertiary} style={styles.fileDownloadIcon} />
    </TouchableOpacity>
  );
};

const LocationContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  const address = message.locationName ?? 'Переглянути розташування';
  return (
    <TouchableOpacity
      style={styles.locationContainer}
      onPress={() => Alert.alert('', '')}
      activeOpacity={0.85}
    >
      <View style={[styles.locationMapPlaceholder, { backgroundColor: theme.surfaceElevated }]}>
        <Feather name="map-pin" size={24} color={theme.primary} />
      </View>
      <Text style={styles.locationAddress} numberOfLines={2}>{address}</Text>
    </TouchableOpacity>
  );
};

const StickerContent: React.FC<{ message: Message }> = ({ message }) => {
  const url = message.stickers ?? message.mediaUrl ?? '';
  return <Image source={{ uri: url }} style={styles.stickerImage} resizeMode="contain" />;
};

const SystemContent: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => (
  <View style={styles.systemRow}>
    <Text style={[styles.systemText, { color: theme.textSecondary }]}>{message.text}</Text>
  </View>
);

const CallContent: React.FC<{ message: Message }> = ({ message }) => {
  const isVideo = message.typeTwo === 'video_call';
  const isMissed = message.typeTwo === 'missed';
  const duration = message.mediaDuration;
  return (
    <View style={styles.callContainer}>
      <Feather
        name={isVideo ? 'video' : 'phone'}
        size={18}
        color={isMissed ? '#FF4444' : '#4CAF50'}
        style={styles.callIcon}
      />
      <View>
        <Text style={styles.callTitle}>{isVideo ? 'Video call' : 'Voice call'}</Text>
        <Text style={styles.callSubtitle}>
          {isMissed ? 'Missed' : duration ? formatDuration(duration) : 'Ended'}
        </Text>
      </View>
    </View>
  );
};

const ReactionsBar: React.FC<{ message: Message; isOwn: boolean; theme: ThemeColors }> = ({ message, isOwn, theme }) => {
  if (!message.reactions || message.reactions.length === 0) return null;
  return (
    <View style={[styles.reactionsRow, isOwn ? styles.reactionsRowOwn : styles.reactionsRowOther]}>
      {message.reactions.map((r, i) => (
        <TouchableOpacity
          key={`${r.emoji}-${i}`}
          style={[styles.reactionPill, { backgroundColor: theme.surface, borderColor: theme.divider }]}
          onPress={() => Alert.alert('', `${r.emoji} — ${r.count}`)}
        >
          <Text style={styles.reactionEmoji}>{r.emoji}</Text>
          <Text style={[styles.reactionCount, { color: theme.text }]}>{r.count}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
};

const MessageStatus: React.FC<{ message: Message; theme: ThemeColors }> = ({ message, theme }) => {
  if (message.isLocalPending) {
    return <Feather name="clock" size={11} color={theme.textTertiary} style={styles.statusIcon} />;
  }
  if (message.isSeen) {
    return (
      <View style={styles.statusDouble}>
        <Feather name="check" size={11} color={theme.accent} />
        <Feather name="check" size={11} color={theme.accent} style={styles.statusCheck2} />
      </View>
    );
  }
  return <Feather name="check" size={11} color={theme.textTertiary} style={styles.statusIcon} />;
};

const MessageBubble: React.FC<MessageBubbleProps> = ({
  message,
  isOwn,
  prevMessage: _prevMessage,
  nextMessage: _nextMessage,
  onLongPress,
  onReply,
  onEdit,
  onDelete,
}) => {
  const theme = useTheme();
  const { t } = useTranslation();

  const handleLongPress = useCallback(() => {
    onLongPress(message);
    const buttons: Array<{ text: string; onPress?: () => void; style?: 'destructive' | 'cancel' }> = [
      { text: t('reply'), onPress: () => onReply(message) },
      {
        text: t('copy'),
        onPress: () => {
          if (message.text) {
            Share.share({ message: message.text }).catch(() => null);
          }
        },
      },
      { text: t('react'), onPress: () => Alert.alert('', t('coming_soon')) },
      { text: t('forward'), onPress: () => Alert.alert('', t('coming_soon')) },
    ];
    if (isOwn) {
      if (onEdit) {
        buttons.push({ text: t('edit'), onPress: () => onEdit(message) });
      }
      if (onDelete) {
        buttons.push({ text: t('delete'), style: 'destructive', onPress: () => onDelete(message) });
      }
    }
    buttons.push({ text: t('cancel'), style: 'cancel' });
    Alert.alert(t('message'), undefined, buttons);
  }, [message, isOwn, onLongPress, onReply, onEdit, onDelete, t]);

  if (message.type === MESSAGE_TYPES.SYSTEM || message.isDeleted) {
    return <SystemContent message={message} theme={theme} />;
  }

  if (message.type === 'sticker') {
    return (
      <View style={[styles.rowOuter, isOwn ? styles.rowOwnOuter : styles.rowOtherOuter]}>
        <TouchableOpacity onLongPress={handleLongPress} activeOpacity={0.9}>
          <StickerContent message={message} />
        </TouchableOpacity>
        <ReactionsBar message={message} isOwn={isOwn} theme={theme} />
      </View>
    );
  }

  const bubbleBg = isOwn ? theme.messageBubbleOwn : theme.messageBubbleOther;
  const bubbleRadius = {
    borderTopLeftRadius: isOwn ? 18 : 4,
    borderTopRightRadius: isOwn ? 4 : 18,
    borderBottomLeftRadius: 18,
    borderBottomRightRadius: 18,
  };

  return (
    <View style={[styles.rowOuter, isOwn ? styles.rowOwnOuter : styles.rowOtherOuter]}>
      <TouchableOpacity
        onLongPress={handleLongPress}
        activeOpacity={0.85}
        style={[styles.bubble, { backgroundColor: bubbleBg }, bubbleRadius]}
      >
        <ReplyQuote message={message} theme={theme} />

        {(message.type === MESSAGE_TYPES.TEXT || !message.type) && (
          <TextContent message={message} theme={theme} />
        )}
        {message.type === MESSAGE_TYPES.IMAGE && <ImageContent message={message} />}
        {message.type === MESSAGE_TYPES.VIDEO && <VideoContent message={message} theme={theme} />}
        {(message.type === MESSAGE_TYPES.AUDIO || message.type === MESSAGE_TYPES.VOICE) && (
          <AudioContent message={message} theme={theme} />
        )}
        {message.type === MESSAGE_TYPES.FILE && <FileContent message={message} theme={theme} />}
        {message.type === MESSAGE_TYPES.LOCATION && <LocationContent message={message} theme={theme} />}
        {message.type === MESSAGE_TYPES.CALL && <CallContent message={message} />}

        <View style={[styles.metaRow, isOwn ? styles.metaRowOwn : styles.metaRowOther]}>
          <Text style={styles.timeText}>{formatTime(message.createdAt)}</Text>
          {isOwn && <MessageStatus message={message} theme={theme} />}
        </View>
      </TouchableOpacity>

      <ReactionsBar message={message} isOwn={isOwn} theme={theme} />
    </View>
  );
};

const styles = StyleSheet.create({
  rowOuter: { marginVertical: 2, paddingHorizontal: 10 },
  rowOwnOuter: { alignItems: 'flex-end' },
  rowOtherOuter: { alignItems: 'flex-start' },
  bubble: { maxWidth: '80%', paddingHorizontal: 12, paddingTop: 8, paddingBottom: 6 },
  messageText: { color: '#FFFFFF', fontSize: 15, lineHeight: 21 },
  editedLabel: { fontSize: 11, marginTop: 2 },
  link: { textDecorationLine: 'underline' },
  imageContent: { width: 220, height: 160, borderRadius: 10 },
  videoContainer: { width: 220, height: 140, borderRadius: 10, overflow: 'hidden' },
  videoThumbnail: { width: '100%', height: '100%' },
  videoThumbnailPlaceholder: { width: '100%', height: '100%' },
  videoPlayOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  videoDurationBadge: {
    position: 'absolute',
    bottom: 6,
    left: 8,
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: 5,
    paddingVertical: 2,
    borderRadius: 4,
  },
  videoDurationText: { color: '#FFFFFF', fontSize: 11 },
  audioContainer: { flexDirection: 'row', alignItems: 'center', minWidth: 160, gap: 8 },
  audioPlayButton: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  waveformContainer: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 2 },
  waveformBar: { width: 3, backgroundColor: 'rgba(255,255,255,0.5)', borderRadius: 2 },
  audioDuration: { fontSize: 11, flexShrink: 0 },
  fileContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    minWidth: 160,
    maxWidth: 240,
    gap: 10,
  },
  fileIconWrapper: {
    width: 40,
    height: 40,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  fileInfo: { flex: 1 },
  fileName: { color: '#FFFFFF', fontSize: 13, fontWeight: '500' },
  fileSize: { fontSize: 11, marginTop: 2 },
  fileDownloadIcon: { flexShrink: 0 },
  locationContainer: { width: 200, overflow: 'hidden', borderRadius: 10 },
  locationMapPlaceholder: { width: '100%', height: 110, alignItems: 'center', justifyContent: 'center' },
  locationAddress: { color: '#FFFFFF', fontSize: 12, marginTop: 6 },
  stickerImage: { width: 150, height: 150 },
  systemRow: { alignItems: 'center', marginVertical: 6, paddingHorizontal: 16 },
  systemText: { fontSize: 12, fontStyle: 'italic', textAlign: 'center' },
  callContainer: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  callIcon: { flexShrink: 0 },
  callTitle: { color: '#FFFFFF', fontSize: 14, fontWeight: '500' },
  callSubtitle: { color: 'rgba(255,255,255,0.6)', fontSize: 12, marginTop: 1 },
  replyQuote: {
    flexDirection: 'row',
    marginBottom: 6,
    borderRadius: 6,
    backgroundColor: 'rgba(0,0,0,0.2)',
    overflow: 'hidden',
  },
  replyQuoteBorder: { width: 3 },
  replyQuoteBody: { flex: 1, paddingHorizontal: 8, paddingVertical: 4 },
  replyQuoteAuthor: { fontSize: 12, fontWeight: '600', marginBottom: 2 },
  replyQuoteText: { fontSize: 12 },
  reactionsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: 4 },
  reactionsRowOwn: { justifyContent: 'flex-end' },
  reactionsRowOther: { justifyContent: 'flex-start' },
  reactionPill: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    paddingHorizontal: 7,
    paddingVertical: 3,
    gap: 3,
    borderWidth: 1,
  },
  reactionEmoji: { fontSize: 13 },
  reactionCount: { fontSize: 11 },
  metaRow: { flexDirection: 'row', alignItems: 'center', marginTop: 4, gap: 3 },
  metaRowOwn: { justifyContent: 'flex-end' },
  metaRowOther: { justifyContent: 'flex-start' },
  timeText: { color: 'rgba(255,255,255,0.5)', fontSize: 11 },
  statusIcon: { marginLeft: 2 },
  statusDouble: { flexDirection: 'row', alignItems: 'center', marginLeft: 2 },
  statusCheck2: { marginLeft: -6 },
});

export default MessageBubble;
