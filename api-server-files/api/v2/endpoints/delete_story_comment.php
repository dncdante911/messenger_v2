<?php
// +------------------------------------------------------------------------+
// | Delete Story Comment Endpoint (V2 API)
// | Deletes a comment (by owner, story owner, or admin)
// | Called directly by Android: /api/v2/endpoints/delete_story_comment.php
// | OR via router: ?type=delete_story_comment
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['comment_id']) || !is_numeric($_POST['comment_id']) || $_POST['comment_id'] < 1) {
    $error_code    = 3;
    $error_message = 'comment_id is missing or invalid';
}

if (empty($error_code)) {
    $comment_id     = (int)$_POST['comment_id'];
    $logged_user_id = (int)$wo['user']['user_id'];

    // Get the comment
    $comment_q = mysqli_query($sqlConnect, "SELECT * FROM " . T_STORY_COMMENTS . " WHERE id = {$comment_id} LIMIT 1");
    $comment = ($comment_q && mysqli_num_rows($comment_q) > 0) ? mysqli_fetch_assoc($comment_q) : null;

    if (empty($comment)) {
        $error_code    = 4;
        $error_message = 'Comment not found';
    } else {
        // Check if user owns the comment or is story owner or admin
        $is_comment_owner = ((int)$comment['user_id'] === $logged_user_id);

        $is_story_owner = false;
        $story_id = (int)$comment['story_id'];
        $story_q = mysqli_query($sqlConnect, "SELECT user_id FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
        if ($story_q && $story_row = mysqli_fetch_assoc($story_q)) {
            $is_story_owner = ((int)$story_row['user_id'] === $logged_user_id);
        }

        $is_admin = function_exists('Wo_IsAdmin') ? Wo_IsAdmin() : false;

        if (!$is_comment_owner && !$is_story_owner && !$is_admin) {
            $error_code    = 5;
            $error_message = 'You do not have permission to delete this comment';
        }
    }
}

if (empty($error_code)) {
    $story_id = (int)$comment['story_id'];

    $delete_result = mysqli_query($sqlConnect, "DELETE FROM " . T_STORY_COMMENTS . " WHERE id = {$comment_id}");

    if ($delete_result) {
        // Decrement comment count (don't go below 0)
        mysqli_query($sqlConnect, "UPDATE " . T_USER_STORY . " SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = {$story_id}");

        $response_data = array(
            'api_status' => 200,
            'message'    => 'Comment deleted successfully',
        );
    } else {
        $error_code    = 6;
        $error_message = 'Failed to delete comment';
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
