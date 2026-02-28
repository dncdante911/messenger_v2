<?php
// +------------------------------------------------------------------------+
// | Get User Stories Endpoint (V2 API)
// | Returns stories for a specific user.
// | POST archive=1  → archive mode: returns all stories within 12 months,
// |                   only allowed when requesting own profile.
// | Called directly by Android: /api/v2/endpoints/get-user-stories.php
// | OR via router: ?type=get_user_stories
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['user_id']) || !is_numeric($_POST['user_id'])) {
    $error_code    = 3;
    $error_message = 'user_id is required';
}

if (empty($error_code)) {
    $target_user_id = (int)$_POST['user_id'];
    $logged_user_id = (int)$wo['user']['user_id'];
    $limit = 35;

    if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
        $limit = (int)$_POST['limit'];
    }

    $current_time   = time();
    $archive_mode   = !empty($_POST['archive']) && $_POST['archive'] == '1'
                      && $target_user_id === $logged_user_id;

    if ($archive_mode) {
        // Archive view (own profile only): all stories within 12 months
        $twelve_months_ago = $current_time - (365 * 24 * 3600);
        $query = "SELECT * FROM " . T_USER_STORY . "
                  WHERE `user_id` = {$target_user_id}
                  AND CAST(`posted` AS UNSIGNED) > {$twelve_months_ago}
                  ORDER BY `id` DESC
                  LIMIT {$limit}";
    } else {
        // Active feed: stories posted within last 24 hours that haven't expired
        $expire_threshold = $current_time - 86400;
        $query = "SELECT * FROM " . T_USER_STORY . "
                  WHERE `user_id` = {$target_user_id}
                  AND (`expire` IS NULL OR `expire` = '' OR CAST(`expire` AS UNSIGNED) > {$current_time})
                  AND CAST(`posted` AS UNSIGNED) > {$expire_threshold}
                  ORDER BY `id` DESC
                  LIMIT {$limit}";
    }

    $sql_result = mysqli_query($sqlConnect, $query);

    $stories = array();

    if ($sql_result && mysqli_num_rows($sql_result) > 0) {
        $user_data = stories_build_user_data($target_user_id);

        while ($story_row = mysqli_fetch_assoc($sql_result)) {
            $stories[] = stories_build_story($sqlConnect, $story_row, $logged_user_id, $user_data);
        }
    }

    $response_data = array(
        'api_status'   => 200,
        'stories'      => $stories,
        'archive_mode' => $archive_mode,
    );
}

if ($error_code > 0) {
    $response_data = array(
        'api_status'    => 400,
        'error_code'    => $error_code,
        'error_message' => $error_message,
    );
}

stories_output($response_data);
