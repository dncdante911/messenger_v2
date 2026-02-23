<?php
// +------------------------------------------------------------------------+
// | Mute/Unmute Story Endpoint (V2 API)
// | Mute or unmute a user's stories
// | Called directly by Android: /api/v2/endpoints/mute_story.php
// | OR via router: ?type=mute_story
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['user_id']) || !is_numeric($_POST['user_id']) || (int)$_POST['user_id'] <= 0) {
    $error_code    = 4;
    $error_message = 'user_id is required';
}

$mute_type = $_POST['type'] ?? 'mute';
if (!in_array($mute_type, array('mute', 'unmute'))) {
    // Default to toggle: if already muted → unmute, else mute
    $mute_type = 'mute';
}

if (empty($error_code)) {
    $target_user_id = (int)$_POST['user_id'];
    $logged_user_id = (int)$wo['user']['user_id'];

    if ($target_user_id === $logged_user_id) {
        $error_code    = 6;
        $error_message = 'You cannot mute your own stories';
    }
}

if (empty($error_code)) {
    if ($mute_type === 'mute') {
        // Check if already muted
        $check = mysqli_query($sqlConnect, "SELECT id FROM " . T_MUTE_STORY . " WHERE user_id = {$logged_user_id} AND story_user_id = {$target_user_id} LIMIT 1");
        if ($check && mysqli_num_rows($check) > 0) {
            $error_code    = 5;
            $error_message = 'This user is already muted';
        } else {
            mysqli_query($sqlConnect, "INSERT INTO " . T_MUTE_STORY . " (user_id, story_user_id, `time`) VALUES ({$logged_user_id}, {$target_user_id}, " . time() . ")");
            $response_data = array(
                'api_status' => 200,
                'message'    => 'User muted',
            );
        }
    } else {
        // Unmute
        mysqli_query($sqlConnect, "DELETE FROM " . T_MUTE_STORY . " WHERE user_id = {$logged_user_id} AND story_user_id = {$target_user_id}");
        $response_data = array(
            'api_status' => 200,
            'message'    => 'User unmuted',
        );
    }
}

if ($error_code > 0) {
    $response_data = array(
        'api_status'    => 400,
        'error_code'    => $error_code,
        'error_message' => $error_message,
    );
}

stories_output($response_data);
