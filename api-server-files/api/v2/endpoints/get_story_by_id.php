<?php
// +------------------------------------------------------------------------+
// | Get Story By ID Endpoint (V2 API)
// | Returns a single story by its ID with full media, views, reactions
// | Called directly by Android: /api/v2/endpoints/get_story_by_id.php
// | OR via router: ?type=get_story_by_id
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['id']) || !is_numeric($_POST['id'])) {
    $error_code    = 3;
    $error_message = 'id is required';
}

if (empty($error_code)) {
    $story_id       = (int)$_POST['id'];
    $logged_user_id = (int)$wo['user']['user_id'];

    $q = mysqli_query($sqlConnect, "SELECT * FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    $story_row = ($q && mysqli_num_rows($q) > 0) ? mysqli_fetch_assoc($q) : null;

    if (empty($story_row)) {
        $error_code    = 4;
        $error_message = 'Story not found';
    } else {
        // Mark story as viewed
        $story_uid = (int)$story_row['user_id'];
        if ($story_uid !== $logged_user_id) {
            $seen_check = mysqli_query($sqlConnect, "SELECT id FROM " . T_STORY_SEEN . " WHERE story_id = {$story_id} AND user_id = {$logged_user_id} LIMIT 1");
            if (!$seen_check || mysqli_num_rows($seen_check) == 0) {
                mysqli_query($sqlConnect, "INSERT INTO " . T_STORY_SEEN . " (story_id, user_id, `time`) VALUES ({$story_id}, {$logged_user_id}, '" . time() . "')");
            }
        }

        $story = stories_build_story($sqlConnect, $story_row, $logged_user_id);

        $response_data = array(
            'api_status' => 200,
            'story'      => $story,
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
