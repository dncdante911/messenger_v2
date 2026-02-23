<?php
// +------------------------------------------------------------------------+
// | Create Story Comment Endpoint (V2 API)
// | Adds a comment to a story
// | Called directly by Android: /api/v2/endpoints/create_story_comment.php
// | OR via router: ?type=create_story_comment
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    $error_code    = 3;
    $error_message = 'story_id is missing or invalid';
}

if (empty($error_code) && (empty($_POST['text']) || ctype_space($_POST['text']))) {
    $error_code    = 4;
    $error_message = 'comment text is required';
}

if (empty($error_code)) {
    $story_id       = (int)$_POST['story_id'];
    $text           = mysqli_real_escape_string($sqlConnect, trim($_POST['text']));
    $logged_user_id = (int)$wo['user']['user_id'];

    // Check if story exists and is not expired
    $story_q = mysqli_query($sqlConnect, "SELECT * FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    $story_row = ($story_q && mysqli_num_rows($story_q) > 0) ? mysqli_fetch_assoc($story_q) : null;

    if (empty($story_row)) {
        $error_code    = 5;
        $error_message = 'Story not found';
    } else if (!empty($story_row['expire']) && (int)$story_row['expire'] < time()) {
        $error_code    = 6;
        $error_message = 'Story has expired';
    }
}

if (empty($error_code)) {
    $now = time();

    // Insert comment
    $insert_q = "INSERT INTO " . T_STORY_COMMENTS . " (story_id, user_id, text, `time`)
                 VALUES ({$story_id}, {$logged_user_id}, '{$text}', {$now})";
    $insert_result = mysqli_query($sqlConnect, $insert_q);
    $comment_id = mysqli_insert_id($sqlConnect);

    if ($comment_id) {
        // Increment comment count
        mysqli_query($sqlConnect, "UPDATE " . T_USER_STORY . " SET comment_count = comment_count + 1 WHERE id = {$story_id}");

        // Build comment response
        $user_data = stories_build_user_data($logged_user_id);

        $comment = [
            'id'        => (int)$comment_id,
            'story_id'  => $story_id,
            'user_id'   => $logged_user_id,
            'text'      => $_POST['text'],
            'time'      => $now,
            'user_data' => $user_data,
            'offset_id' => (int)$comment_id,
        ];

        $response_data = array(
            'api_status' => 200,
            'comment'    => $comment,
        );
    } else {
        $error_code    = 7;
        $error_message = 'Failed to create comment';
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
