<?php
/**
 * API v2 Router
 * Routes requests based on 'type' parameter to appropriate endpoint files
 *
 * This router works alongside WoWonder's api-v2.php:
 * - api-v2.php requires server_key (used by official WoWonder app)
 * - This index.php works with access_token only (used by WorldMates messenger)
 */

header('Content-Type: application/json; charset=UTF-8');

// Load API v2 configuration (sets up $db, $sqlConnect, $wo, WoWonder functions)
require_once(__DIR__ . '/config.php');

// Get request type
$type = $_GET['type'] ?? $_POST['type'] ?? '';

// List of endpoints that don't require authentication
$public_endpoints = [
    'auth',
    'create-account',
    'create_account',
    'active_account_sms',
    'send_verification_code',
    'verify_code',
    'quick_register',
    'quick_verify',
    'send-reset-password-email',
    'send_reset_password_email',
    'reset_password',
    'check_username',
    'get_site_settings',
    'get-site-settings',
    'test_init',
    'check_mobile_update'
];

// Validate access_token for protected endpoints
if (!in_array($type, $public_endpoints)) {
    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';

    if (empty($access_token)) {
        http_response_code(401);
        echo json_encode([
            'api_status' => 401,
            'error_message' => 'access_token is required'
        ]);
        exit;
    }

    // Validate token using config.php's validateAccessToken function
    $user_id = validateAccessToken($pdo, $access_token);

    if (!$user_id) {
        http_response_code(401);
        echo json_encode([
            'api_status' => 401,
            'error_message' => 'Invalid or expired access_token'
        ]);
        exit;
    }

    // Get full user data using WoWonder function
    if (function_exists('Wo_UserData')) {
        $user_data = Wo_UserData($user_id);
        if (!empty($user_data)) {
            $wo['user'] = $user_data;
            $wo['loggedin'] = true;
        }
    } else {
        // Fallback: set minimal user data
        $wo['user'] = [
            'user_id' => $user_id,
            'id' => $user_id
        ];
        $wo['loggedin'] = true;
    }
}

// ============================================
// Variables required by WoWonder endpoint files
// (normally set by api-v2.php / init.php)
// ============================================

// $non_allowed: user fields that must not be exposed in API responses
if (!isset($non_allowed)) {
    $non_allowed = [
        'password', 'background_image_status', 'email_code', 'type',
        'start_up', 'start_up_info', 'startup_follow', 'startup_image',
        'id', 'cover_full', 'cover_org', 'avatar_org', 'app_session',
        'last_email_sent', 'sms_code', 'pro_time', 'css_file', 'src',
        'followers_data', 'following_data', 'likes_data', 'album_data',
        'groups_data', 'sidebar_data', 'showlastseen', 'joined', 'social_login',
    ];
}

// $timezone: user's DateTimeZone object for time formatting in endpoint files
if (!isset($timezone)) {
    $tz_string = !empty($wo['user']['timezone']) ? $wo['user']['timezone'] : 'UTC';
    try {
        $timezone = new DateTimeZone($tz_string);
    } catch (Exception $e) {
        $timezone = new DateTimeZone('UTC');
    }
}

// Check if type is provided
if (empty($type)) {
    http_response_code(400);
    echo json_encode([
        'api_status' => 400,
        'error_message' => 'Missing required parameter: type'
    ]);
    exit;
}

// Route map: type => endpoint file
$routes = [
    // Debug
    'test_init' => 'endpoints/test_init.php',

    // Search endpoints
    'search' => 'endpoints/search.php',
    'recent_search' => 'endpoints/recent_search.php',
    'search_for_posts' => 'endpoints/search_for_posts.php',
    'search_group_messages' => 'endpoints/search_group_messages.php',

    // Authentication & Registration
    'auth' => 'endpoints/auth.php',
    'create-account' => 'endpoints/create-account.php',
    'create_account' => 'endpoints/create-account.php',
    'active_account_sms' => 'endpoints/active_account_sms.php',
    'check_username' => 'endpoints/check_username.php',
    'get_chats' => 'endpoints/get_chats.php',
    'get_user_messages' => 'endpoints/get_messages.php',

    // Verification
    'send_verification_code' => 'endpoints/send_verification_code.php',
    'verify_code' => 'endpoints/verify_code.php',

    // Quick Registration (simplified: phone/email + code)
    'quick_register' => 'endpoints/quick_register.php',
    'quick_verify' => 'endpoints/quick_verify.php',

    // Password Reset
    'send-reset-password-email' => 'endpoints/send-reset-password-email.php',
    'send_reset_password_email' => 'endpoints/send-reset-password-email.php',
    'reset_password' => 'endpoints/reset_password.php',

    // Messaging
    'send_message' => 'endpoints/send-message.php',
    'delete_message' => 'endpoints/delete_message.php',
    'read_chats' => 'endpoints/read_chats.php',
    'delete_chat' => 'endpoints/delete_chat.php',
    'delete_group_chat' => 'endpoints/delete_group_chat.php',

    // User actions
    'block_user' => 'endpoints/block_user.php',
    'unblock_user' => 'endpoints/unblock_user.php',

    // Groups
    'join_group' => 'endpoints/join-group.php',
    'upload_group_avatar' => 'endpoints/upload_group_avatar.php',
    'generate_group_qr' => 'endpoints/generate_group_qr.php',
    'join_group_by_qr' => 'endpoints/join_group_by_qr.php',
    'mute_group' => 'endpoints/mute_group.php',
    'unmute_group' => 'endpoints/unmute_group.php',
    'pin_group_message' => 'endpoints/pin_group_message.php',
    'unpin_group_message' => 'endpoints/unpin_group_message.php',
    'get_group_members' => 'endpoints/get_group_members.php',
    'delete_group' => 'endpoints/delete_group.php',
    'delete_group_member' => 'endpoints/delete_group_member.php',
    'make_group_admin' => 'endpoints/make_group_admin.php',
    'update-group-data' => 'endpoints/update-group-data.php',
    'get-group-data' => 'endpoints/get-group-data.php',

    // Stories
    'create_story' => 'endpoints/create-story.php',
    'delete_story' => 'endpoints/delete-story.php',
    'get_story_views' => 'endpoints/get_story_views.php',
    'mute_story' => 'endpoints/mute_story.php',
    'create_story_comment' => 'endpoints/create_story_comment.php',
    'get_story_comments' => 'endpoints/get_story_comments.php',
    'delete_story_comment' => 'endpoints/delete_story_comment.php',
    'get_story_reactions' => 'endpoints/get_story_reactions.php',
    'get_stories' => 'endpoints/get-stories.php',
    'get_user_stories' => 'endpoints/get-user-stories.php',
    'get_story_by_id' => 'endpoints/get_story_by_id.php',
    'react_story' => 'endpoints/react_story.php',

    // FCM Token
    'update_fcm_token' => 'endpoints/update_fcm_token.php',

    // User Settings
    'get-user-data' => 'endpoints/get-user-data.php',
    'update-user-data' => 'endpoints/update-user-data.php',
    'update-privacy-settings' => 'endpoints/update-privacy-settings.php',
    'update-notification-settings' => 'endpoints/update-notification-settings.php',
    'update-profile-picture' => 'endpoints/update-user-data.php', // Handled by update-user-data
    'update-cover-picture' => 'endpoints/update-user-data.php',   // Handled by update-user-data
    'upload_user_avatar' => 'endpoints/upload_user_avatar.php',
    'get-my-groups' => 'endpoints/get-my-groups.php',

    // Cloud Backup & Media Settings
    'get_media_settings' => 'endpoints/get_media_settings.php',
    'update_media_settings' => 'endpoints/update_media_settings.php',
    'get_cloud_backup_settings' => 'endpoints/get_cloud_backup_settings.php',
    'update_cloud_backup_settings' => 'endpoints/update_cloud_backup_settings.php',

    // Channels
    'get_channel_subscribers' => 'endpoints/get_channel_subscribers.php',
    'upload_channel_avatar' => 'endpoints/upload_channel_avatar.php',
    'generate_channel_qr' => 'endpoints/generate_channel_qr.php',
    'mute_channel' => 'endpoints/mute_channel.php',
    'unmute_channel' => 'endpoints/unmute_channel.php',

    // User Rating System (Karma/Trust)
    'rate_user' => 'endpoints/rate_user.php',
    'get_user_rating' => 'endpoints/get_user_rating.php',

    // Other
    'get_invites' => 'endpoints/get_invites.php',
    'get_live_friends' => 'endpoints/get_live_friends.php',
    'get_site_settings' => 'endpoints/get-site-settings.php',
    'subscribe_channel_by_qr' => 'endpoints/subscribe_channel_by_qr.php',
    'check_mobile_update' => 'endpoints/check_mobile_update.php',

    // Bot API (user-facing endpoints)
    'search_bots' => 'endpoints/bot_api.php',
    'get_bot_info' => 'endpoints/bot_api.php',
    'create_bot' => 'endpoints/bot_api.php',
    'get_my_bots' => 'endpoints/bot_api.php',
    'update_bot' => 'endpoints/bot_api.php',
    'delete_bot' => 'endpoints/bot_api.php',
    'regenerate_token' => 'endpoints/bot_api.php',
    'get_commands' => 'endpoints/bot_api.php',
    'set_commands' => 'endpoints/bot_api.php',
    'answer_callback_query' => 'endpoints/bot_api.php',
    'set_webhook' => 'endpoints/bot_api.php',
    'delete_webhook' => 'endpoints/bot_api.php',
    'get_webhook_info' => 'endpoints/bot_api.php',
    'send_poll' => 'endpoints/bot_api.php',
    'stop_poll' => 'endpoints/bot_api.php',
    'get_updates' => 'endpoints/bot_api.php',
    'edit_message' => 'endpoints/bot_api.php',
    'get_chat_member' => 'endpoints/bot_api.php',
    'set_user_state' => 'endpoints/bot_api.php',
    'get_user_state' => 'endpoints/bot_api.php',
];

// Check if route exists
if (!isset($routes[$type])) {
    http_response_code(404);
    echo json_encode([
        'api_status' => 404,
        'error_message' => "Unknown API type: $type"
    ]);
    exit;
}

$endpoint_file = __DIR__ . '/' . $routes[$type];

// Check if endpoint file exists
if (!file_exists($endpoint_file)) {
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'error_message' => "Endpoint file not found: {$routes[$type]}"
    ]);
    exit;
}

// Include and execute endpoint
require_once($endpoint_file);

// ============================================
// Output JSON response for WoWonder-style endpoints
// ============================================
// WoWonder endpoints set $response_data, $error_code, $error_message
// but don't output JSON themselves — we handle it here.
if (isset($error_code) && !empty($error_code)) {
    $response_data = array(
        'api_status' => 400,
        'errors' => array(
            'error_id' => $error_code,
            'error_text' => isset($error_message) ? $error_message : 'Unknown error',
        )
    );
}

if (isset($response_data) && !empty($response_data)) {
    echo json_encode($response_data);
}
