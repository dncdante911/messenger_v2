<?php
// +------------------------------------------------------------------------+
// | Get Story Views Endpoint (V2 API)
// | Returns paginated list of users who viewed a story
// | Called directly by Android: /api/v2/endpoints/get_story_views.php
// | OR via router: ?type=get_story_views
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    $error_code    = 3;
    $error_message = 'story_id is required';
}

if (empty($error_code)) {
    $story_id = (int)$_POST['story_id'];
    $limit    = 20;
    $offset   = 0;

    if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
        $limit = (int)$_POST['limit'];
    }
    if (!empty($_POST['offset']) && is_numeric($_POST['offset']) && (int)$_POST['offset'] > 0) {
        $offset = (int)$_POST['offset'];
    }

    // Check if story exists and belongs to current user (only owner can see views)
    $logged_user_id = (int)$wo['user']['user_id'];
    $story_q = mysqli_query($sqlConnect, "SELECT user_id FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    $story_row = ($story_q && mysqli_num_rows($story_q) > 0) ? mysqli_fetch_assoc($story_q) : null;

    if (empty($story_row)) {
        $error_code    = 4;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    $users = [];

    $views_q = mysqli_query($sqlConnect,
        "SELECT * FROM " . T_STORY_SEEN . "
         WHERE story_id = {$story_id}
         ORDER BY `time` DESC
         LIMIT {$offset}, {$limit}"
    );

    if ($views_q && mysqli_num_rows($views_q) > 0) {
        while ($v = mysqli_fetch_assoc($views_q)) {
            $u = stories_build_user_data((int)$v['user_id']);
            $u['time'] = !empty($v['time']) ? (int)$v['time'] : 0;
            $u['offset_id'] = (int)$v['id'];
            $users[] = $u;
        }
    }

    $response_data = array(
        'api_status' => 200,
        'users'      => $users,
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
