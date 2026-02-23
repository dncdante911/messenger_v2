<?php
// +------------------------------------------------------------------------+
// | Get Stories Endpoint (V2 API)
// | Returns active stories from the current user and their contacts
// | Called directly by Android: /api/v2/endpoints/get-stories.php?access_token=...
// | OR via router: ?type=get_stories
// +------------------------------------------------------------------------+

// === DEBUG: Capture ANY fatal error, even in bootstrap ===
// This MUST be the very first thing, before any require/include
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);
ini_set('error_log', __DIR__ . '/stories_debug.log');
register_shutdown_function(function() {
    $error = error_get_last();
    if ($error && in_array($error['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        // Log to file for server-side debugging
        $msg = date('Y-m-d H:i:s') . " FATAL: {$error['message']} in {$error['file']}:{$error['line']}\n";
        @file_put_contents(__DIR__ . '/stories_debug.log', $msg, FILE_APPEND);

        // Also try to send JSON response
        if (!headers_sent()) {
            http_response_code(500);
            header('Content-Type: application/json; charset=UTF-8');
        }
        $json = json_encode([
            'api_status' => 500,
            'error_message' => 'Fatal: ' . $error['message'],
            'error_file' => basename($error['file']),
            'error_line' => $error['line']
        ]);
        echo $json;
        // Also log the JSON
        @file_put_contents(__DIR__ . '/stories_debug.log', "Response: $json\n", FILE_APPEND);
    }
});

@file_put_contents(__DIR__ . '/stories_debug.log',
    date('Y-m-d H:i:s') . " === get-stories.php START ===\n", FILE_APPEND);

// Step 1: Load bootstrap
@file_put_contents(__DIR__ . '/stories_debug.log',
    date('Y-m-d H:i:s') . " Step 1: Loading _stories_bootstrap.php...\n", FILE_APPEND);

require_once(__DIR__ . '/_stories_bootstrap.php');

@file_put_contents(__DIR__ . '/stories_debug.log',
    date('Y-m-d H:i:s') . " Step 2: Bootstrap loaded OK. user_id=" . ($wo['user']['user_id'] ?? 'NULL') . "\n", FILE_APPEND);

$response_data = array('api_status' => 400);

$logged_user_id = (int)$wo['user']['user_id'];
$limit = 35;

if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
    $limit = (int)$_POST['limit'];
}

$current_time = time();
$expire_threshold = $current_time - 86400; // 24h ago

// Get list of muted users
$muted_user_ids = [];
if (defined('T_MUTE_STORY')) {
    $mq = mysqli_query($sqlConnect, "SELECT story_user_id FROM " . T_MUTE_STORY . " WHERE user_id = {$logged_user_id}");
    if ($mq) {
        while ($mr = mysqli_fetch_assoc($mq)) {
            $muted_user_ids[] = (int)$mr['story_user_id'];
        }
    }
}

@file_put_contents(__DIR__ . '/stories_debug.log',
    date('Y-m-d H:i:s') . " Step 3: Querying stories...\n", FILE_APPEND);

// Fetch non-expired stories ordered by newest first
$query = "SELECT * FROM " . T_USER_STORY . "
          WHERE (`expire` IS NULL OR `expire` = '' OR CAST(`expire` AS UNSIGNED) > {$current_time})
          AND CAST(`posted` AS UNSIGNED) > {$expire_threshold}
          ORDER BY `id` DESC
          LIMIT {$limit}";

$sql_result = mysqli_query($sqlConnect, $query);

$stories = array();

if ($sql_result && mysqli_num_rows($sql_result) > 0) {
    // Cache user data per user_id to avoid repeated queries
    $user_data_cache = [];

    while ($story_row = mysqli_fetch_assoc($sql_result)) {
        $story_user_id = (int)$story_row['user_id'];

        // Skip muted users
        if (in_array($story_user_id, $muted_user_ids)) {
            continue;
        }

        // Cache user data
        if (!isset($user_data_cache[$story_user_id])) {
            $user_data_cache[$story_user_id] = stories_build_user_data($story_user_id);
        }

        $stories[] = stories_build_story($sqlConnect, $story_row, $logged_user_id, $user_data_cache[$story_user_id]);
    }
}

@file_put_contents(__DIR__ . '/stories_debug.log',
    date('Y-m-d H:i:s') . " Step 4: Built " . count($stories) . " stories. Sending response...\n", FILE_APPEND);

$response_data = array(
    'api_status' => 200,
    'stories'    => $stories,
);

stories_output($response_data);
