// ============================================================
// WorldMates Messenger — Messages Screen (Phase 3 stub)
//
// This screen will contain the full chat UI in Phase 3.
// Currently renders a minimal header + empty state so navigation
// works end-to-end from the Chats list.
// ============================================================

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { RootStackParamList } from '../../navigation/types';
import { Avatar } from '../../components/common/Avatar';

type MessagesRouteProp = RouteProp<RootStackParamList, 'Messages'>;

export function MessagesScreen() {
  const navigation = useNavigation();
  const route = useRoute<MessagesRouteProp>();
  const { chatName, chatAvatar } = route.params;

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={styles.backBtn}
        >
          <Feather name="chevron-left" size={26} color="#FFFFFF" />
        </TouchableOpacity>
        <Avatar uri={chatAvatar} name={chatName} size={36} />
        <Text style={styles.headerName} numberOfLines={1}>
          {chatName}
        </Text>
      </View>

      <View style={styles.body}>
        <Feather name="message-circle" size={48} color="#3A3B4D" />
        <Text style={styles.bodyText}>Messages coming in Phase 3</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#1A1B2E',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2A2B3D',
    gap: 10,
  },
  backBtn: {
    padding: 2,
  },
  headerName: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  bodyText: {
    color: '#8E8E93',
    fontSize: 15,
  },
});
