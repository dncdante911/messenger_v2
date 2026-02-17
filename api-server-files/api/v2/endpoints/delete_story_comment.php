<?php
// +------------------------------------------------------------------------+
// | @author Deen Doughouz (DoughouzForest)
// | @author_url 1: http://www.wowonder.com
// | @author_url 2: http://codecanyon.net/user/doughouzforest
// | @author_email: wowondersocial@gmail.com
// +------------------------------------------------------------------------+
// | WoWonder - The Ultimate Social Networking Platform
// | Copyright (c) 2018 WoWonder. All rights reserved.
// +------------------------------------------------------------------------+

$response_data = array(
    'api_status' => 400
);

if (empty($_POST['comment_id']) || !is_numeric($_POST['comment_id']) || $_POST['comment_id'] < 1) {
    $error_code    = 3;
    $error_message = 'comment_id is missing or invalid';
}

if (empty($error_code)) {
    $comment_id = Wo_Secure($_POST['comment_id']);

    // Get the comment
    $comment = $db->where('id', $comment_id)->getOne(T_STORY_COMMENTS);

    if (empty($comment)) {
        $error_code    = 4;
        $error_message = 'Comment not found';
    } else {
        // Check if user owns the comment or is admin
        $story = $db->where('id', $comment->story_id)->getOne(T_USER_STORY);

        $is_owner = ($comment->user_id == $wo['user']['id']);
        $is_story_owner = ($story && $story->user_id == $wo['user']['id']);
        $is_admin = Wo_IsAdmin() || Wo_IsModerator();

        if (!$is_owner && !$is_story_owner && !$is_admin) {
            $error_code    = 5;
            $error_message = 'You do not have permission to delete this comment';
        }
    }
}

if (empty($error_code)) {
    $story_id = $comment->story_id; // Save story_id before deletion

    $delete = $db->where('id', $comment_id)->delete(T_STORY_COMMENTS);

    if ($delete) {
        // Decrement comment count
        $db->where('id', $story_id)->update(T_USER_STORY, array(
            'comment_count' => $db->dec(1)
        ));

        $response_data = array(
            'api_status' => 200,
            'message' => 'Comment deleted successfully'
        );
    } else {
        $error_code    = 6;
        $error_message = 'Failed to delete comment';
    }
}
