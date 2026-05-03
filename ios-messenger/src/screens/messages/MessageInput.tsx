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
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';

interface MessageInputProps {
  onSend: (text: string) => void;
  onTyping: () => void;
  onTypingStop: () => void;
  replyTo?: Message | null;
  onCancelReply: () => void;
}

const TYPING_STOP_DELAY_MS = 1500;
const MAX_INPUT_LINES = 6;
const SEND_BUTTON_SIZE = 36;

const MessageInput: React.FC<MessageInputProps> = ({
  onSend,
  onTyping,
  onTypingStop,
  replyTo,
  onCancelReply,
}) => {
  const theme = useTheme();
  const { t } = useTranslation();
  const [text, setText] = useState('');
  const [isRecording, setIsRecording] = useState(false);
  const typingStopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<TextInput>(null);

  const handleTextChange = useCallback(
    (value: string) => {
      setText(value);
      if (value.length > 0) {
        onTyping();
        if (typingStopTimer.current) clearTimeout(typingStopTimer.current);
        typingStopTimer.current = setTimeout(() => onTypingStop(), TYPING_STOP_DELAY_MS);
      } else {
        if (typingStopTimer.current) clearTimeout(typingStopTimer.current);
        onTypingStop();
      }
    },
    [onTyping, onTypingStop],
  );

  useEffect(() => {
    return () => {
      if (typingStopTimer.current) clearTimeout(typingStopTimer.current);
    };
  }, []);

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText('');
    onTypingStop();
    if (typingStopTimer.current) clearTimeout(typingStopTimer.current);
  }, [text, onSend, onTypingStop]);

  const handleAttach = useCallback(() => {
    Alert.alert(t('attach'), t('choose_attachment'), [
      { text: t('camera'), onPress: () => Alert.alert(t('camera'), t('coming_soon')) },
      { text: t('gallery'), onPress: () => Alert.alert(t('gallery'), t('coming_soon')) },
      { text: t('file'), onPress: () => Alert.alert(t('file'), t('coming_soon')) },
      { text: t('location'), onPress: () => Alert.alert(t('location'), t('coming_soon')) },
      { text: t('sticker'), onPress: () => Alert.alert(t('sticker'), t('coming_soon')) },
      { text: t('cancel'), style: 'cancel' },
    ]);
  }, [t]);

  const handleEmoji = useCallback(() => {
    Alert.alert(t('emoji'), t('coming_soon'));
  }, [t]);

  const handleMicPressIn = useCallback(() => {
    setIsRecording(true);
  }, []);

  const handleMicPressOut = useCallback(() => {
    setIsRecording(false);
  }, []);

  const replyPreviewText = replyTo?.text ?? replyTo?.mediaUrl ?? t('media');
  const replyAuthor = replyTo?.senderName ?? replyTo?.replyToName ?? t('message');

  return (
    <View style={[styles.wrapper, { backgroundColor: theme.background, borderTopColor: theme.divider }]}>
      {replyTo != null && (
        <View style={[styles.replyPreviewContainer, { backgroundColor: theme.surfaceElevated }]}>
          <View style={[styles.replyPreviewBorder, { backgroundColor: theme.primary }]} />
          <View style={styles.replyPreviewContent}>
            <Text style={[styles.replyPreviewAuthor, { color: theme.primary }]} numberOfLines={1}>
              {t('replying_to')} {replyAuthor}
            </Text>
            <Text style={[styles.replyPreviewText, { color: theme.textSecondary }]} numberOfLines={1}>
              {replyPreviewText}
            </Text>
          </View>
          <TouchableOpacity
            onPress={onCancelReply}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            style={styles.replyCloseButton}
          >
            <Feather name="x" size={16} color={theme.textTertiary} />
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.inputRow}>
        <TouchableOpacity onPress={handleAttach} style={styles.iconButton}>
          <Feather name="paperclip" size={22} color={theme.textTertiary} />
        </TouchableOpacity>

        <TextInput
          ref={inputRef}
          style={[styles.textInput, { backgroundColor: theme.inputBackground, color: theme.text }]}
          value={text}
          onChangeText={handleTextChange}
          placeholder={t('type_message')}
          placeholderTextColor={theme.textTertiary}
          multiline
          maxLength={4096}
          numberOfLines={MAX_INPUT_LINES}
          returnKeyType="default"
          blurOnSubmit={false}
          textAlignVertical="center"
          selectionColor={theme.primary}
        />

        <TouchableOpacity onPress={handleEmoji} style={styles.iconButton}>
          <Feather name="smile" size={22} color={theme.textTertiary} />
        </TouchableOpacity>

        {text.trim().length > 0 ? (
          <TouchableOpacity
            onPress={handleSend}
            style={[styles.sendButton, { backgroundColor: theme.primary }]}
            activeOpacity={0.7}
          >
            <Feather name="arrow-up" size={18} color="#FFFFFF" />
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            onPressIn={handleMicPressIn}
            onPressOut={handleMicPressOut}
            style={[styles.micButton, isRecording && { backgroundColor: theme.error + '20', borderRadius: 19 }]}
            activeOpacity={0.7}
          >
            <Feather name="mic" size={20} color={isRecording ? theme.error : theme.textTertiary} />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: {
    paddingBottom: Platform.OS === 'ios' ? 8 : 4,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  replyPreviewContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 12,
    marginTop: 8,
    marginBottom: 4,
    borderRadius: 8,
    overflow: 'hidden',
  },
  replyPreviewBorder: {
    width: 3,
    alignSelf: 'stretch',
  },
  replyPreviewContent: {
    flex: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  replyPreviewAuthor: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 2,
  },
  replyPreviewText: {
    fontSize: 12,
  },
  replyCloseButton: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    alignSelf: 'center',
  },
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
    borderRadius: 22,
    paddingHorizontal: 14,
    paddingTop: Platform.OS === 'ios' ? 10 : 8,
    paddingBottom: Platform.OS === 'ios' ? 10 : 8,
    fontSize: 15,
    maxHeight: 130,
    minHeight: 42,
  },
  sendButton: {
    width: SEND_BUTTON_SIZE,
    height: SEND_BUTTON_SIZE,
    borderRadius: SEND_BUTTON_SIZE / 2,
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
});

export default MessageInput;
