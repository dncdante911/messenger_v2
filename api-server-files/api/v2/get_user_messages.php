<?php
/**
 * WorldMates Messenger - Personal Chat Messages (Standalone)
 *
 * Standalone endpoint — same pattern as group_chat_v2.php.
 * Does NOT go through index.php router (avoids WoWonder api-v2.php conflict).
 *
 * Usage: POST /api/v2/get_user_messages.php
 * Auth:  ?access_token=... or POST access_token
 * Params (POST): recipient_id, limit, before_message_id, after_message_id, use_gcm
 */

require_once(__DIR__ . '/config.php');

header('Content-Type: application/json');
ini_set('display_errors', 0);
ini_set('log_errors', 1);

// ── Auth ────────────────────────────────────────────────────────────────────
$access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';

$user_id = validateAccessToken($db, $access_token);
if (!$user_id) {
    http_response_code(401);
    echo json_encode(['api_status' => 401, 'error_message' => 'Invalid or expired access_token']);
    exit;
}

// ── User context (required by Wo_UserData, Wo_GetMedia, etc.) ──────────────
if (function_exists('Wo_UserData')) {
    $user_data = Wo_UserData($user_id);
    $wo['user'] = !empty($user_data) ? $user_data : ['user_id' => $user_id, 'timezone' => 'UTC'];
} else {
    $wo['user'] = ['user_id' => $user_id, 'timezone' => 'UTC'];
}
$wo['user']['user_id'] = $user_id; // guarantee correct sender id
$wo['loggedin'] = true;

// ── Delegate to endpoint ────────────────────────────────────────────────────
require_once(__DIR__ . '/endpoints/get_messages.php');
