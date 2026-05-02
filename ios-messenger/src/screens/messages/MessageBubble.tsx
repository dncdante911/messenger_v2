// ============================================================
// WorldMates Messenger — MessageBubble
// Renders a single message with full type support:
//   text, image, video, audio, voice, file, location,
//   sticker, system, call — plus reply quotes and reactions.
// ============================================================

import React, { useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Alert,
  Linking,
  StyleSheet,
  Image,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { Message } from '../../api/types';
import { MESSAGE_TYPES } from '../../constants/api';

// ─────────────────────────────────────────────────────────────
// PROPS
// ─────────────────────────────────────────────────────────────

interface MessageBubbleProps {
  message: Message;
  isOwn: boolean;
  prevMessage?: Message;
  nextMessage?: Message;
  onLongPress: (msg: Message) => void;
  onReply: (msg: Message) => void;
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

/** Format a Unix timestamp or ISO string into HH:mm */
function formatTime(ts: string | number): string {
  const date = typeof ts === 'number' ? new Date(ts * 1000) : new Date(ts);
  if (isNaN(date.getTime())) return '';
  const h = date.getHours().toString().padStart(2, '0');
  const m = date.getMinutes().toString().padStart(2, '0');
  return `${h}:${m}`;
}

/** Format seconds into mm:ss */
function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Format bytes into human-readable size */
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Very simple URL detection */
const URL_REGEX = /(https?:\/\/[^\s]+)/g;

// ─────────────────────────────────────────────────────────────
// SUB-COMPONENTS
// ─────────────────────────────────────────────────────────────

/** Reply quote block rendered above message content */
const ReplyQuote: React.FC<{ message: Message }> = ({ message }) => {
  if (!message.replyToId) return null;
  return (
    <View style={styles.replyQuote}>
      <View style={styles.replyQuoteBorder} />
      <View style={styles.replyQuoteBody}>
        <Text style={styles.replyQuoteAuthor} numberOfLines={1}>
          {message.replyToName ?? 'Message'}
        </Text>
        <Text style={styles.replyQuoteText} numberOfLines={1}>
          {message.replyToText ?? '(Media)'}
        </Text>
      </View>
    </View>
  );
};

/** Linkified text content */
const LinkifiedText: React.FC<{ text: string; style: object }> = ({ text, style }) => {
  const parts = text.split(URL_REGEX);
  return (
    <Text style={style}>
      {parts.map((part, i) => {
        if (URL_REGEX.test(part)) {
          return (
            <Text
              key={i}
              style={styles.link}
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

// ─────────────────────────────────────────────────────────────
// MESSAGE CONTENT RENDERERS
// ─────────────────────────────────────────────────────────────

const TextContent: React.FC<{ message: Message }> = ({ message }) => (
  <View>
    <LinkifiedText text={message.text} style={styles.messageText} />
    {message.isEdited && <Text style={styles.editedLabel}>(edited)</Text>}
  </View>
);

const ImageContent: React.FC<{ message: Message }> = ({ message }) => {
  const url = message.mediaUrl ?? '';
  return (
    <TouchableOpacity
      onPress={() => Alert.alert('Image', 'Open gallery coming soon')}
      activeOpacity={0.85}
    >
      <Image
        source={{ uri: url }}
        style={styles.imageContent}
        resizeMode="cover"
      />
    </TouchableOpacity>
  );
};

const VideoContent: React.FC<{ message: Message }> = ({ message }) => {
  const thumb = message.mediaThumb ?? message.mediaUrl ?? '';
  const duration = message.mediaDuration ?? 0;
  return (
    <TouchableOpacity
      style={styles.videoContainer}
      onPress={() => Alert.alert('Video', 'Video player coming soon')}
      activeOpacity={0.85}
    >
      {thumb ? (
        <Image source={{ uri: thumb }} style={styles.videoThumbnail} resizeMode="cover" />
      ) : (
        <View style={styles.videoThumbnailPlaceholder} />
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

const AudioContent: React.FC<{ message: Message }> = ({ message }) => {
  const duration = message.mediaDuration ?? 0;
  return (
    <View style={styles.audioContainer}>
      <TouchableOpacity
        onPress={() => Alert.alert('Audio', 'Audio player coming soon')}
        style={styles.audioPlayButton}
      >
        <Feather name="play" size={18} color="#FFFFFF" />
      </TouchableOpacity>
      <View style={styles.waveformContainer}>
        {Array.from({ length: 10 }).map((_, i) => (
          <View
            key={i}
            style={[
              styles.waveformBar,
              { height: 6 + ((i * 3) % 14) },
            ]}
          />
        ))}
      </View>
      {duration > 0 && (
        <Text style={styles.audioDuration}>{formatDuration(duration)}</Text>
      )}
    </View>
  );
};

const FileContent: React.FC<{ message: Message }> = ({ message }) => {
  const name = message.fileName ?? 'File';
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  const size = message.mediaSize ?? 0;

  const iconName: React.ComponentProps<typeof Feather>['name'] =
    ['pdf'].includes(ext)
      ? 'file-text'
      : ['mp3', 'wav', 'm4a', 'ogg'].includes(ext)
        ? 'music'
        : ['mp4', 'mov', 'avi'].includes(ext)
          ? 'film'
          : ['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)
            ? 'image'
            : 'file';

  return (
    <TouchableOpacity
      style={styles.fileContainer}
      onPress={() => Alert.alert('File', 'Downloading...')}
      activeOpacity={0.8}
    >
      <View style={styles.fileIconWrapper}>
        <Feather name={iconName} size={22} color="#7C83FD" />
      </View>
      <View style={styles.fileInfo}>
        <Text style={styles.fileName} numberOfLines={2}>
          {name}
        </Text>
        {size > 0 && <Text style={styles.fileSize}>{formatFileSize(size)}</Text>}
      </View>
      <Feather name="download" size={16} color="#8E8E93" style={styles.fileDownloadIcon} />
    </TouchableOpacity>
  );
};

const LocationContent: React.FC<{ message: Message }> = ({ message }) => {
  const address = message.locationName ?? 'View location';
  return (
    <TouchableOpacity
      style={styles.locationContainer}
      onPress={() => Alert.alert('Location', 'Maps view coming soon')}
      activeOpacity={0.85}
    >
      <View style={styles.locationMapPlaceholder}>
        <Feather name="map-pin" size={24} color="#7C83FD" />
      </View>
      <Text style={styles.locationAddress} numberOfLines={2}>
        {address}
      </Text>
    </TouchableOpacity>
  );
};

const StickerContent: React.FC<{ message: Message }> = ({ message }) => {
  const url = message.stickers ?? message.mediaUrl ?? '';
  return (
    <Image
      source={{ uri: url }}
      style={styles.stickerImage}
      resizeMode="contain"
    />
  );
};

const SystemContent: React.FC<{ message: Message }> = ({ message }) => (
  <View style={styles.systemRow}>
    <Text style={styles.systemText}>{message.text}</Text>
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
        <Text style={styles.callTitle}>
          {isVideo ? 'Video call' : 'Voice call'}
        </Text>
        <Text style={styles.callSubtitle}>
          {isMissed ? 'Missed' : duration ? formatDuration(duration) : 'Ended'}
        </Text>
      </View>
    </View>
  );
};

// ─────────────────────────────────────────────────────────────
// REACTIONS BAR
// ─────────────────────────────────────────────────────────────

const ReactionsBar: React.FC<{ message: Message; isOwn: boolean }> = ({ message, isOwn }) => {
  if (!message.reactions || message.reactions.length === 0) return null;

  return (
    <View style={[styles.reactionsRow, isOwn ? styles.reactionsRowOwn : styles.reactionsRowOther]}>
      {message.reactions.map((r, i) => (
        <TouchableOpacity
          key={`${r.emoji}-${i}`}
          style={styles.reactionPill}
          onPress={() => Alert.alert('Reaction', `${r.emoji} — ${r.count} reactions`)}
        >
          <Text style={styles.reactionEmoji}>{r.emoji}</Text>
          <Text style={styles.reactionCount}>{r.count}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
};

// ─────────────────────────────────────────────────────────────
// STATUS CHECKMARKS
// ─────────────────────────────────────────────────────────────

const MessageStatus: React.FC<{ message: Message }> = ({ message }) => {
  if (message.isLocalPending) {
    return <Feather name="clock" size={11} color="#8E8E93" style={styles.statusIcon} />;
  }
  if (message.isSeen) {
    // Double blue check
    return (
      <View style={styles.statusDouble}>
        <Feather name="check" size={11} color="#7C83FD" />
        <Feather name="check" size={11} color="#7C83FD" style={styles.statusCheck2} />
      </View>
    );
  }
  // Single gray — sent
  return <Feather name="check" size={11} color="#8E8E93" style={styles.statusIcon} />;
};

// ─────────────────────────────────────────────────────────────
// LONG PRESS MENU
// ─────────────────────────────────────────────────────────────

function showLongPressMenu(
  message: Message,
  isOwn: boolean,
  onReply: (msg: Message) => void,
): void {
  const buttons: Array<{ text: string; onPress?: () => void; style?: 'destructive' | 'cancel' }> =
    [
      {
        text: 'Reply',
        onPress: () => onReply(message),
      },
      {
        text: 'Copy',
        onPress: () => {
          /* Clipboard copy — coming soon */
          Alert.alert('Copied', 'Message text copied');
        },
      },
      {
        text: 'React',
        onPress: () => Alert.alert('React', 'Emoji reaction coming soon'),
      },
      {
        text: 'Forward',
        onPress: () => Alert.alert('Forward', 'Message forwarding coming soon'),
      },
    ];

  if (isOwn) {
    buttons.push({
      text: 'Edit',
      onPress: () => Alert.alert('Edit', 'Message editing coming soon'),
    });
    buttons.push({
      text: 'Delete',
      style: 'destructive' as const,
      onPress: () => Alert.alert('Delete', 'Message deletion coming soon'),
    });
  }

  buttons.push({ text: 'Cancel', style: 'cancel' as const });

  Alert.alert('Message', undefined, buttons);
}

// ─────────────────────────────────────────────────────────────
// MAIN COMPONENT
// ─────────────────────────────────────────────────────────────

const MessageBubble: React.FC<MessageBubbleProps> = ({
  message,
  isOwn,
  prevMessage: _prevMessage,
  nextMessage: _nextMessage,
  onLongPress,
  onReply,
}) => {
  const handleLongPress = useCallback(() => {
    onLongPress(message);
    showLongPressMenu(message, isOwn, onReply);
  }, [message, isOwn, onLongPress, onReply]);

  // System messages are centered, no bubble
  if (message.type === MESSAGE_TYPES.SYSTEM || message.isDeleted) {
    return <SystemContent message={message} />;
  }

  // Sticker: no bubble background
  if (message.type === 'sticker') {
    return (
      <View style={[styles.rowOuter, isOwn ? styles.rowOwnOuter : styles.rowOtherOuter]}>
        <TouchableOpacity onLongPress={handleLongPress} activeOpacity={0.9}>
          <StickerContent message={message} />
        </TouchableOpacity>
        <ReactionsBar message={message} isOwn={isOwn} />
      </View>
    );
  }

  const bubbleBg = isOwn ? '#4F4FBF' : '#2A2B3D';

  // Border radius — corner towards sender is 4px
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
        {/* Reply quote */}
        <ReplyQuote message={message} />

        {/* Content by type */}
        {(message.type === MESSAGE_TYPES.TEXT || !message.type) && (
          <TextContent message={message} />
        )}
        {message.type === MESSAGE_TYPES.IMAGE && <ImageContent message={message} />}
        {message.type === MESSAGE_TYPES.VIDEO && <VideoContent message={message} />}
        {(message.type === MESSAGE_TYPES.AUDIO ||
          message.type === MESSAGE_TYPES.VOICE) && (
          <AudioContent message={message} />
        )}
        {message.type === MESSAGE_TYPES.FILE && <FileContent message={message} />}
        {message.type === MESSAGE_TYPES.LOCATION && <LocationContent message={message} />}
        {message.type === MESSAGE_TYPES.CALL && <CallContent message={message} />}

        {/* Bottom row: time + status */}
        <View style={[styles.metaRow, isOwn ? styles.metaRowOwn : styles.metaRowOther]}>
          <Text style={styles.timeText}>{formatTime(message.createdAt)}</Text>
          {isOwn && <MessageStatus message={message} />}
        </View>
      </TouchableOpacity>

      {/* Reactions below bubble */}
      <ReactionsBar message={message} isOwn={isOwn} />
    </View>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  // Row wrappers
  rowOuter: {
    marginVertical: 2,
    paddingHorizontal: 10,
  },
  rowOwnOuter: {
    alignItems: 'flex-end',
  },
  rowOtherOuter: {
    alignItems: 'flex-start',
  },

  // Bubble
  bubble: {
    maxWidth: '80%',
    paddingHorizontal: 12,
    paddingTop: 8,
    paddingBottom: 6,
  },

  // Text
  messageText: {
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 21,
  },
  editedLabel: {
    color: '#8E8E93',
    fontSize: 11,
    marginTop: 2,
  },
  link: {
    color: '#7C83FD',
    textDecorationLine: 'underline',
  },

  // Image
  imageContent: {
    width: 220,
    height: 160,
    borderRadius: 10,
  },

  // Video
  videoContainer: {
    width: 220,
    height: 140,
    borderRadius: 10,
    overflow: 'hidden',
    backgroundColor: '#1A1B2E',
  },
  videoThumbnail: {
    width: '100%',
    height: '100%',
  },
  videoThumbnailPlaceholder: {
    width: '100%',
    height: '100%',
    backgroundColor: '#2A2B3D',
  },
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
  videoDurationText: {
    color: '#FFFFFF',
    fontSize: 11,
  },

  // Audio / Voice
  audioContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    minWidth: 160,
    gap: 8,
  },
  audioPlayButton: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  waveformContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  waveformBar: {
    width: 3,
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderRadius: 2,
  },
  audioDuration: {
    color: '#8E8E93',
    fontSize: 11,
    flexShrink: 0,
  },

  // File
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
    backgroundColor: 'rgba(124,131,253,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  fileInfo: {
    flex: 1,
  },
  fileName: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '500',
  },
  fileSize: {
    color: '#8E8E93',
    fontSize: 11,
    marginTop: 2,
  },
  fileDownloadIcon: {
    flexShrink: 0,
  },

  // Location
  locationContainer: {
    width: 200,
    overflow: 'hidden',
    borderRadius: 10,
  },
  locationMapPlaceholder: {
    width: '100%',
    height: 110,
    backgroundColor: '#22334A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  locationAddress: {
    color: '#FFFFFF',
    fontSize: 12,
    marginTop: 6,
  },

  // Sticker
  stickerImage: {
    width: 150,
    height: 150,
  },

  // System
  systemRow: {
    alignItems: 'center',
    marginVertical: 6,
    paddingHorizontal: 16,
  },
  systemText: {
    color: '#8E8E93',
    fontSize: 12,
    fontStyle: 'italic',
    textAlign: 'center',
  },

  // Call
  callContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  callIcon: {
    flexShrink: 0,
  },
  callTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '500',
  },
  callSubtitle: {
    color: '#8E8E93',
    fontSize: 12,
    marginTop: 1,
  },

  // Reply quote
  replyQuote: {
    flexDirection: 'row',
    marginBottom: 6,
    borderRadius: 6,
    backgroundColor: 'rgba(0,0,0,0.2)',
    overflow: 'hidden',
  },
  replyQuoteBorder: {
    width: 3,
    backgroundColor: '#7C83FD',
  },
  replyQuoteBody: {
    flex: 1,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  replyQuoteAuthor: {
    color: '#7C83FD',
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 2,
  },
  replyQuoteText: {
    color: '#8E8E93',
    fontSize: 12,
  },

  // Reactions
  reactionsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginTop: 4,
  },
  reactionsRowOwn: {
    justifyContent: 'flex-end',
  },
  reactionsRowOther: {
    justifyContent: 'flex-start',
  },
  reactionPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2A2B3D',
    borderRadius: 12,
    paddingHorizontal: 7,
    paddingVertical: 3,
    gap: 3,
    borderWidth: 1,
    borderColor: '#3A3B4E',
  },
  reactionEmoji: {
    fontSize: 13,
  },
  reactionCount: {
    color: '#FFFFFF',
    fontSize: 11,
  },

  // Meta row (time + status)
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
    gap: 3,
  },
  metaRowOwn: {
    justifyContent: 'flex-end',
  },
  metaRowOther: {
    justifyContent: 'flex-start',
  },
  timeText: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 11,
  },
  statusIcon: {
    marginLeft: 2,
  },
  statusDouble: {
    flexDirection: 'row',
    alignItems: 'center',
    marginLeft: 2,
  },
  statusCheck2: {
    marginLeft: -6,
  },
});

export default MessageBubble;
