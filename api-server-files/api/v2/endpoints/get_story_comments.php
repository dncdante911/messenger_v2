<?php
// Get Story Comments API - Standalone endpoint
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
$log_file = $log_dir . '/get-story-comments-debug.log';
ini_set('error_log', $log_file);

error_log("=== get_story_comments.php START at " . date('Y-m-d H:i:s') . " ===");
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

$limit = (!empty($_POST['limit']) && is_numeric($_POST['limit']) && $_POST['limit'] > 0 && $_POST['limit'] <= 50 ? Wo_Secure($_POST['limit']) : 20);
$offset = (!empty($_POST['offset']) && is_numeric($_POST['offset']) && $_POST['offset'] > 0 ? Wo_Secure($_POST['offset']) : 0);

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    error_log("ERROR: story_id is missing or invalid");
    $error_code    = 3;
    $error_message = 'story_id is missing or invalid';
}

if (empty($error_code)) {
    $story_id = Wo_Secure($_POST['story_id']);
    error_log("Loading comments for story_id={$story_id}, limit={$limit}, offset={$offset}");

    // Check if story exists
    $story = $db->where('id', $story_id)->getOne(T_USER_STORY);

    if (empty($story)) {
        error_log("ERROR: Story not found: id={$story_id}");
        $error_code    = 4;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    $comments_data = array();

    $db->where('story_id', $story_id);
    $db->orderBy('time', 'DESC');

    if ($offset > 0) {
        $db->where('id', $offset, '<');
    }

    $comments = $db->get(T_STORY_COMMENTS, $limit);

    error_log("Found " . count($comments) . " comments");

    if (!empty($comments)) {
        foreach ($comments as $comment) {
            $user_data = Wo_UserData($comment->user_id);
            foreach ($non_allowed as $key => $value) {
                unset($user_data[$value]);
            }

            $comment->user_data = $user_data;
            $comment->offset_id = $comment->id;
            $comments_data[] = $comment;
        }
    }

    $total_count = $db->where('story_id', $story_id)->getValue(T_STORY_COMMENTS, 'COUNT(*)');

    $response_data = array(
        'api_status' => 200,
        'comments' => $comments_data,
        'total' => $total_count
    );

    error_log("✅ Returning " . count($comments_data) . " comments, total={$total_count}");
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
error_log("=== get_story_comments.php END ===");
