import type { NavigatorScreenParams } from '@react-navigation/native';

export type AuthStackParamList = {
  Splash: undefined;
  LanguageSelection: undefined;
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  Verification: { userId: string; email: string };
};

export type MainTabParamList = {
  Chats: undefined;
  Calls: undefined;
  Stories: undefined;
  Settings: undefined;
};

export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>;
  Main: NavigatorScreenParams<MainTabParamList>;
  Messages: {
    chatId: string;
    chatType: string;
    chatName: string;
    chatAvatar?: string;
    userId?: string;
  };
  GroupMessages: {
    groupId: string;
    groupName: string;
    groupAvatar?: string;
  };
  ChannelDetails: { channelId: string };
  UserProfile: { userId: string };
  GlobalSearch: undefined;
  SavedMessages: undefined;
  Notes: undefined;
  Drafts: undefined;
};
