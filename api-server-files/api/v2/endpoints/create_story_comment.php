<?php
// Create Story Comment API - Standalone endpoint
// +------------------------------------------------------------------------+
// | @author Deen Doughouz (DoughouzForest)
// | @author_url 1: http://www.wowonder.com
// | @author_url 2: http://codecanyon.net/user/doughouzforest
// | @author_email: wowondersocial@gmail.com
// +------------------------------------------------------------------------+
// | WoWonder - The Ultimate Social Networking Platform
// | Copyright (c) 2018 WoWonder. All rights reserved.
// +------------------------------------------------------------------------+

// Enable error logging
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);
$log_dir = $_SERVER['DOCUMENT_ROOT'] . '/api/v2/logs';
if (!file_exists($log_dir)) {
    @mkdir($log_dir, 0777, true);
}
$log_file = $log_dir . '/create-story-comment-debug.log';
ini_set('error_log', $log_file);

error_log("=== create_story_comment.php START at " . date('Y-m-d H:i:s') . " ===");
error_log("POST: " . print_r($_POST, true));
error_log("GET: " . print_r($_GET, true));

// Load WoWonder initialization
$depth = '../../../';
$init_path = __DIR__ . '/' . $depth . 'assets/init.php';

if (!file_exists($init_path)) {
    error_log("ERROR: init.php not found at: " . $init_path);
    header('Content-Type: application/json');
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Server configuration error'
    ]);
    exit;
}

// Change to project root before loading init.php
$original_dir = getcwd();
$root_dir = realpath(__DIR__ . '/' . $depth);
chdir($root_dir);
error_log("Changed directory to: " . getcwd());

require_once($init_path);

chdir($original_dir);
error_log("Restored directory to: " . getcwd());
error_log("✅ init.php loaded successfully");

// Authentication via access_token (POST or GET)
$access_token = !empty($_POST['access_token']) ? Wo_Secure($_POST['access_token']) :
                (!empty($_GET['access_token']) ? Wo_Secure($_GET['access_token']) : '');

if (empty($access_token)) {
    error_log("ERROR: access_token is missing");
    header('Content-Type: application/json');
    echo json_encode([
        'api_status' => 400,
        'error_code' => 1,
        'error_message' => 'access_token is required'
    ]);
    exit;
}

// Authenticate user via access_token
$query = "SELECT user_id FROM " . T_APP_SESSIONS . " WHERE session_id = '" . $access_token . "' LIMIT 1";
$sql_query = mysqli_query($sqlConnect, $query);

if (!$sql_query || mysqli_num_rows($sql_query) == 0) {
    error_log("ERROR: Invalid access_token");
    header('Content-Type: application/json');
    echo json_encode([
        'api_status' => 400,
        'error_code' => 2,
        'error_message' => 'Invalid access_token'
    ]);
    exit;
}

$sql_fetch = mysqli_fetch_assoc($sql_query);
$user_id = $sql_fetch['user_id'];

$wo['user'] = Wo_UserData($user_id);
$wo['loggedin'] = true;

error_log("✅ User authenticated successfully: user_id={$user_id}, username={$wo['user']['username']}");

// Initialize response
$response_data = array('api_status' => 400);
$error_code = 0;
$error_message = '';

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    error_log("ERROR: story_id is missing or invalid");
    $error_code    = 3;
    $error_message = 'story_id is missing or invalid';
}

if (empty($_POST['text']) || ctype_space($_POST['text'])) {
    error_log("ERROR: comment text is empty");
    $error_code    = 4;
    $error_message = 'comment text is required';
}

if (empty($error_code)) {
    $story_id = Wo_Secure($_POST['story_id']);
    $text = Wo_Secure($_POST['text']);

    error_log("Creating comment: story_id={$story_id}, user_id={$wo['user']['user_id']}, text_length=" . strlen($text));

    // Check if story exists and is not expired
    $story = $db->where('id', $story_id)->getOne(T_USER_STORY);

    if (empty($story)) {
        error_log("ERROR: Story not found: id={$story_id}");
        $error_code    = 5;
        $error_message = 'Story not found';
    } else if ($story->expire < time()) {
        error_log("ERROR: Story has expired: id={$story_id}, expire={$story->expire}, now=" . time());
        $error_code    = 6;
        $error_message = 'Story has expired';
    }
}

if (empty($error_code)) {
    $comment_data = array(
        'story_id' => $story_id,
        'user_id' => $wo['user']['user_id'],  // Fixed: use user_id instead of id
        'text' => $text,
        'time' => time()
    );

    $comment_id = $db->insert(T_STORY_COMMENTS, $comment_data);

    if ($comment_id) {
        error_log("✅ Comment created: comment_id={$comment_id}");

        // Increment comment count
        $db->where('id', $story_id)->update(T_USER_STORY, array(
            'comment_count' => $db->inc(1)
        ));

        error_log("✅ Comment count updated");

        // Get the inserted comment with user data
        $comment = $db->where('id', $comment_id)->getOne(T_STORY_COMMENTS);

        if ($comment) {
            $user_data = Wo_UserData($comment->user_id);
            foreach ($non_allowed as $key => $value) {
                unset($user_data[$value]);
            }

            $comment->user_data = $user_data;

            // Send notification to story owner if not commenting on own story
            if ($story->user_id != $wo['user']['user_id']) {
                $notification_data_array = array(
                    'recipient_id' => $story->user_id,
                    'type' => 'comment_story',
                    'story_id' => $story_id,
                    'text' => '',
                    'url' => 'index.php?link1=timeline&u=' . $wo['user']['username'] . '&story=true&story_id=' . $story_id
                );
                Wo_RegisterNotification($notification_data_array);
                error_log("✅ Notification sent to user_id={$story->user_id}");
            }

            $response_data = array(
                'api_status' => 200,
                'comment' => $comment
            );

            error_log("✅ Comment creation successful");
        }
    } else {
        error_log("ERROR: Failed to create comment: " . $db->getLastError());
        $error_code    = 7;
        $error_message = 'Failed to create comment: ' . $db->getLastError();
    }
}

// Return error if needed
if ($error_code > 0) {
    $response_data = array(
        'api_status' => 400,
        'error_code' => $error_code,
        'error_message' => $error_message
    );
}

header('Content-Type: application/json');
echo json_encode($response_data);
error_log("=== create_story_comment.php END ===");
