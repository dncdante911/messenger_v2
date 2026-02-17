export type AuthResponse = {
  api_status: string;
  access_token?: string;
  user_id?: number;
  message?: string;
};

export type ChatItem = {
  user_id: number;
  name: string;
  avatar?: string;
  last_message?: string;
  time?: string;
};

export type ChatListResponse = {
  api_status: string;
  data?: ChatItem[];
};

export type MessageItem = {
  id: number;
  from_id: number;
  to_id: number;
  text: string;
  time_text?: string;
  media?: string;
};

export type MessagesResponse = {
  api_status: string;
  messages?: MessageItem[];
};

export type GroupItem = {
  id: number;
  group_name: string;
  avatar?: string;
  members_count?: number;
};

export type ChannelItem = {
  id: number;
  name: string;
  username?: string;
  avatar_url?: string;
  subscribers_count?: number;
};

export type StoryItem = {
  id: number;
  user_id: number;
  user_name?: string;
  file?: string;
  file_type?: 'image' | 'video';
  created_at?: string;
};

export type GenericListResponse<T> = {
  api_status: string;
  data?: T[];
  stories?: T[];
  message?: string;
};

export type MediaUploadResponse = {
  api_status: string;
  filename?: string;
  media?: string;
  url?: string;
  message?: string;
};
