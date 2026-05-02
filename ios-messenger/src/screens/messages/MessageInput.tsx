// ============================================================
// WorldMates Messenger — MessageInput
// Chat input bar: reply preview, attach, text input, emoji, send/mic.
// ============================================================

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  Text,
  Alert,
  StyleSheet,
  Platform,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { Message } from '../../api/types';

// ─────────────────────────────────────────────────────────────
// PROPS
// ─────────────────────────────────────────────────────────────

interface MessageInputProps {
  onSend: (text: string) => void;
  onTyping: () => void;
  onTypingStop: () => void;
  replyTo?: Message | null;
  onCancelReply: () => void;
}

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────

const TYPING_STOP_DELAY_MS = 1500;
const MAX_INPUT_LINES = 6;
const SEND_BUTTON_SIZE = 36;

// ─────────────────────────────────────────────────────────────
// COMPONENT
// ─────────────────────────────────────────────────────────────

const MessageInput: React.FC<MessageInputProps> = ({
  onSend,
  onTyping,
  onTypingStop,
  replyTo,
  onCancelReply,
}) => {
  const [text, setText] = useState('');
  const [isRecording, setIsRecording] = useState(false);
  const typingStopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<TextInput>(null);

  // ── Typing indicator debounce ─────────────────────────────

  const handleTextChange = useCallback(
    (value: string) => {
      setText(value);

      if (value.length > 0) {
        onTyping();

        if (typingStopTimer.current) {
          clearTimeout(typingStopTimer.current);
        }
        typingStopTimer.current = setTimeout(() => {
          onTypingStop();
        }, TYPING_STOP_DELAY_MS);
      } else {
        if (typingStopTimer.current) {
          clearTimeout(typingStopTimer.current);
        }
        onTypingStop();
      }
    },
    [onTyping, onTypingStop],
  );

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (typingStopTimer.current) {
        clearTimeout(typingStopTimer.current);
      }
    };
  }, []);

  // ── Send ─────────────────────────────────────────────────

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed) return;

    onSend(trimmed);
    setText('');
    onTypingStop();

    if (typingStopTimer.current) {
      clearTimeout(typingStopTimer.current);
    }
  }, [text, onSend, onTypingStop]);

  // ── Attach ───────────────────────────────────────────────

  const handleAttach = useCallback(() => {
    Alert.alert('Attach', 'Choose attachment type', [
      {
        text: 'Camera',
        onPress: () => Alert.alert('Camera', 'Camera capture coming soon'),
      },
      {
        text: 'Gallery',
        onPress: () => Alert.alert('Gallery', 'Gallery picker coming soon'),
      },
      {
        text: 'File',
        onPress: () => Alert.alert('File', 'File picker coming soon'),
      },
      {
        text: 'Location',
        onPress: () => Alert.alert('Location', 'Location sharing coming soon'),
      },
      {
        text: 'Sticker',
        onPress: () => Alert.alert('Sticker', 'Sticker picker coming soon'),
      },
      { text: 'Cancel', style: 'cancel' },
    ]);
  }, []);

  // ── Emoji ────────────────────────────────────────────────

  const handleEmoji = useCallback(() => {
    Alert.alert('Emoji', 'Emoji picker coming soon');
  }, []);

  // ── Voice recording ──────────────────────────────────────

  const handleMicPressIn = useCallback(() => {
    setIsRecording(true);
    Alert.alert('Voice', 'Recording...');
  }, []);

  const handleMicPressOut = useCallback(() => {
    setIsRecording(false);
  }, []);

  // ── Reply preview helpers ─────────────────────────────────

  const replyPreviewText = replyTo?.text ?? replyTo?.mediaUrl ?? '(Media)';
  const replyAuthor = replyTo?.senderName ?? replyTo?.replyToName ?? 'Message';

  // ─────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────

  return (
    <View style={styles.wrapper}>
      {/* ── Reply preview ───────────────────────────────────── */}
      {replyTo != null && (
        <View style={styles.replyPreviewContainer}>
          <View style={styles.replyPreviewBorder} />
          <View style={styles.replyPreviewContent}>
            <Text style={styles.replyPreviewAuthor} numberOfLines={1}>
              Replying to {replyAuthor}
            </Text>
            <Text style={styles.replyPreviewText} numberOfLines={1}>
              {replyPreviewText}
            </Text>
          </View>
          <TouchableOpacity
            onPress={onCancelReply}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            style={styles.replyCloseButton}
          >
            <Feather name="x" size={16} color="#8E8E93" />
          </TouchableOpacity>
        </View>
      )}

      {/* ── Input row ───────────────────────────────────────── */}
      <View style={styles.inputRow}>
        {/* Attach */}
        <TouchableOpacity onPress={handleAttach} style={styles.iconButton}>
          <Feather name="paperclip" size={22} color="#8E8E93" />
        </TouchableOpacity>

        {/* Text input */}
        <TextInput
          ref={inputRef}
          style={styles.textInput}
          value={text}
          onChangeText={handleTextChange}
          placeholder="Message..."
          placeholderTextColor="#8E8E93"
          multiline
          maxLength={4096}
          numberOfLines={MAX_INPUT_LINES}
          returnKeyType="default"
          blurOnSubmit={false}
          textAlignVertical="center"
        />

        {/* Emoji */}
        <TouchableOpacity onPress={handleEmoji} style={styles.iconButton}>
          <Feather name="smile" size={22} color="#8E8E93" />
        </TouchableOpacity>

        {/* Send or Mic */}
        {text.trim().length > 0 ? (
          <TouchableOpacity onPress={handleSend} style={styles.sendButton} activeOpacity={0.7}>
            <Feather name="arrow-up" size={18} color="#FFFFFF" />
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            onPressIn={handleMicPressIn}
            onPressOut={handleMicPressOut}
            style={[styles.micButton, isRecording && styles.micButtonActive]}
            activeOpacity={0.7}
          >
            <Feather name="mic" size={20} color={isRecording ? '#FF4444' : '#8E8E93'} />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  wrapper: {
    backgroundColor: '#1A1B2E',
    paddingBottom: Platform.OS === 'ios' ? 8 : 4,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#2A2B3D',
  },

  // Reply preview
  replyPreviewContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 12,
    marginTop: 8,
    marginBottom: 4,
    backgroundColor: '#22233A',
    borderRadius: 8,
    overflow: 'hidden',
  },
  replyPreviewBorder: {
    width: 3,
    alignSelf: 'stretch',
    backgroundColor: '#7C83FD',
  },
  replyPreviewContent: {
    flex: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  replyPreviewAuthor: {
    color: '#7C83FD',
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 2,
  },
  replyPreviewText: {
    color: '#8E8E93',
    fontSize: 12,
  },
  replyCloseButton: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    alignSelf: 'center',
  },

  // Input row
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: 8,
    paddingTop: 8,
    paddingBottom: 4,
    gap: 4,
  },
  iconButton: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  textInput: {
    flex: 1,
    backgroundColor: '#2A2B3D',
    borderRadius: 22,
    paddingHorizontal: 14,
    paddingTop: Platform.OS === 'ios' ? 10 : 8,
    paddingBottom: Platform.OS === 'ios' ? 10 : 8,
    color: '#FFFFFF',
    fontSize: 15,
    maxHeight: 130,
    minHeight: 42,
  },
  sendButton: {
    width: SEND_BUTTON_SIZE,
    height: SEND_BUTTON_SIZE,
    borderRadius: SEND_BUTTON_SIZE / 2,
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    marginBottom: 3,
  },
  micButton: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  micButtonActive: {
    backgroundColor: 'rgba(255, 68, 68, 0.12)',
    borderRadius: 19,
  },
});

export default MessageInput;
