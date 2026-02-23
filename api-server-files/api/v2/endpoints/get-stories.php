<?php
// +------------------------------------------------------------------------+
// | Get Stories Endpoint (V2 API)
// | Returns active stories from the current user and their contacts
// | Called directly by Android: /api/v2/endpoints/get-stories.php?access_token=...
// | OR via router: ?type=get_stories
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

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

$response_data = array(
    'api_status' => 200,
    'stories'    => $stories,
);

stories_output($response_data);
