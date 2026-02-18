<?php
// +------------------------------------------------------------------------+
// | Get Stories Endpoint (V2 API)
// | Returns active stories from the current user and their contacts
// | Called via index.php router: ?type=get_stories
// | OR called directly: /api/v2/endpoints/get-stories.php?access_token=...
// +------------------------------------------------------------------------+

// Standalone mode: Android calls this endpoint directly, bypassing the router.
// The router normally loads config.php (DB connection, table constants, WoWonder
// functions, and the shutdown handler that outputs $response_data).
// When called directly we must bootstrap everything ourselves.
$_stories_standalone = !isset($sqlConnect);
if ($_stories_standalone) {
    header('Content-Type: application/json; charset=UTF-8');
    require_once(__DIR__ . '/../config.php');

    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';
    if (empty($access_token)) {
        echo json_encode(['api_status' => 401, 'error_message' => 'access_token is required']);
        exit;
    }
    $auth_user_id = validateAccessToken($db, $access_token);
    if (!$auth_user_id) {
        echo json_encode(['api_status' => 401, 'error_message' => 'Invalid or expired access_token']);
        exit;
    }
    if (function_exists('Wo_UserData')) {
        $auth_user_data = Wo_UserData($auth_user_id);
        if (!empty($auth_user_data)) {
            $wo['user'] = $auth_user_data;
        }
    }
    if (empty($wo['user']['user_id'])) {
        $wo['user']['user_id'] = $auth_user_id;
    }
}

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

$logged_user_id = (int)$wo['user']['user_id'];
$limit = 35;

if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
    $limit = (int)$_POST['limit'];
}

$current_time = time();
$expire_threshold = $current_time - 86400; // 24h ago

// Fetch non-expired stories ordered by newest first
$query = "SELECT * FROM " . T_USER_STORY . "
          WHERE (`expire` IS NULL OR `expire` = '' OR CAST(`expire` AS UNSIGNED) > {$current_time})
          AND CAST(`posted` AS UNSIGNED) > {$expire_threshold}
          ORDER BY `id` DESC
          LIMIT {$limit}";

$sql_result = mysqli_query($sqlConnect, $query);

$stories = array();

if ($sql_result && mysqli_num_rows($sql_result) > 0) {
    while ($story_row = mysqli_fetch_assoc($sql_result)) {
        $story_user_id = (int)$story_row['user_id'];

        $user_data = Wo_UserData($story_user_id);
        if (!empty($user_data) && !empty($non_allowed) && is_array($non_allowed)) {
            foreach ($non_allowed as $key => $value) {
                unset($user_data[$value]);
            }
        }

        $posted_ts = !empty($story_row['posted']) ? (int)$story_row['posted'] : $current_time;
        $expire_ts = !empty($story_row['expire']) ? (int)$story_row['expire'] : ($posted_ts + 86400);

        $stories[] = array(
            'id'            => (int)$story_row['id'],
            'user_id'       => $story_user_id,
            'title'         => $story_row['title'] ?? '',
            'description'   => $story_row['description'] ?? '',
            'posted'        => $posted_ts,
            'expire'        => $expire_ts,
            'thumbnail'     => $story_row['thumbnail'] ?? '',
            'user_data'     => $user_data,
            'is_owner'      => ($story_user_id === $logged_user_id),
            'is_viewed'     => 0,
            'view_count'    => 0,
            'comment_count' => 0,
        );
    }
}

$response_data = array(
    'api_status' => 200,
    'stories'    => $stories,
);

// In standalone mode the WoWonder shutdown handler is not registered,
// so we must output the JSON response ourselves.
if ($_stories_standalone) {
    echo json_encode($response_data);
}
