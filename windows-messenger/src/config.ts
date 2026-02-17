export const API_BASE_URL = 'https://worldmates.club/api/v2/';
export const SOCKET_URL = 'https://worldmates.club:449/';
export const REGISTER_PATH = 'https://worldmates.club/phone/register_user.php?type=user_registration';
export const WINDOWS_APP_BASE_URL = 'https://worldmates.club/api/windows_app/';

// Must match Android client constants.
export const SITE_ENCRYPT_KEY = '2ad9c757daccdfff436dc226779e20b719f6d6f8';
export const SERVER_KEY =
  'a8975daa76d7197ab87412b096696bb0e341eb4d-9bb411ab89d8a290362726fca6129e76-81746510';

export const ENDPOINTS = {
  auth: '?type=auth',
  chats: '?type=get_chats',
  messages: '?type=get_user_messages',
  sendMessage: '?type=send-message',
  sendMessageLegacy: '?type=send_message',
  uploadMedia: '?type=upload_media',
  groups: '/api/v2/group_chat_v2.php',
  channels: '/api/v2/channels.php',
  stories: '/api/v2/endpoints/get-stories.php',
  createStory: '/api/v2/endpoints/create-story.php',
  iceServers: '/api/ice-servers/',

  // Windows app API fallback (WoWonder desktop style)
  windowsLogin: 'login.php?type=user_login',
  windowsUsersList: 'get_users_list.php?type=get_users_list',
  windowsMessages: 'get_user_messages.php?type=get_user_messages',
  windowsInsertMessage: 'insert_new_message.php?type=insert_new_message'
};

export const TURN_FALLBACK = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  {
    urls: [
      'turn:worldmates.club:3478?transport=udp',
      'turn:worldmates.club:3478?transport=tcp',
      'turns:worldmates.club:5349?transport=tcp'
    ]
  }
];
