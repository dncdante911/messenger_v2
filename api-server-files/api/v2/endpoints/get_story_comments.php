<?php
// +------------------------------------------------------------------------+
// | Get Story Comments Endpoint (V2 API)
// | Returns paginated comments for a story
// | Called directly by Android: /api/v2/endpoints/get_story_comments.php
// | OR via router: ?type=get_story_comments
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

$limit  = 20;
$offset = 0;

if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
    $limit = (int)$_POST['limit'];
}
if (!empty($_POST['offset']) && is_numeric($_POST['offset']) && (int)$_POST['offset'] > 0) {
    $offset = (int)$_POST['offset'];
}

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    $error_code    = 3;
    $error_message = 'story_id is missing or invalid';
}

if (empty($error_code)) {
    $story_id = (int)$_POST['story_id'];

    // Check if story exists
    $story_q = mysqli_query($sqlConnect, "SELECT id FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    if (!$story_q || mysqli_num_rows($story_q) == 0) {
        $error_code    = 4;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    $comments_data = [];

    // Build query with cursor-based pagination (offset = last seen comment id)
    $where_offset = '';
    if ($offset > 0) {
        $where_offset = " AND id < {$offset}";
    }

    $comments_q = mysqli_query($sqlConnect,
        "SELECT * FROM " . T_STORY_COMMENTS . "
         WHERE story_id = {$story_id}{$where_offset}
         ORDER BY `time` DESC
         LIMIT {$limit}"
    );

    if ($comments_q && mysqli_num_rows($comments_q) > 0) {
        while ($c = mysqli_fetch_assoc($comments_q)) {
            $user_data = stories_build_user_data((int)$c['user_id']);

            $comments_data[] = [
                'id'        => (int)$c['id'],
                'story_id'  => (int)$c['story_id'],
                'user_id'   => (int)$c['user_id'],
                'text'      => $c['text'] ?? '',
                'time'      => (int)$c['time'],
                'user_data' => $user_data,
                'offset_id' => (int)$c['id'],
            ];
        }
    }

    // Get total count
    $total_q = mysqli_query($sqlConnect, "SELECT COUNT(*) AS cnt FROM " . T_STORY_COMMENTS . " WHERE story_id = {$story_id}");
    $total_count = 0;
    if ($total_q && $row = mysqli_fetch_assoc($total_q)) {
        $total_count = (int)$row['cnt'];
    }

    $response_data = array(
        'api_status' => 200,
        'comments'   => $comments_data,
        'total'      => $total_count,
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
