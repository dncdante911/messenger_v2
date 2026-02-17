<?php
// +------------------------------------------------------------------------+
// | Get Stories Endpoint (V2 API)
// | Returns active stories from the current user and their contacts
// | Called via index.php router: ?type=get_stories
// +------------------------------------------------------------------------+

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
        if (!empty($user_data) && !empty($non_allowed)) {
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
