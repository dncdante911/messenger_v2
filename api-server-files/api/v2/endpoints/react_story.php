<?php
// +------------------------------------------------------------------------+
// | React Story Endpoint (V2 API)
// | Add or remove a reaction on a story (toggle)
// | Called directly by Android: /api/v2/endpoints/react_story.php
// | OR via router: ?type=react_story
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['id']) || !is_numeric($_POST['id'])) {
    $error_code    = 3;
    $error_message = 'id (story_id) is required';
}

$allowed_reactions = array('like', 'love', 'haha', 'wow', 'sad', 'angry');
if (empty($error_code) && (empty($_POST['reaction']) || !in_array($_POST['reaction'], $allowed_reactions))) {
    $error_code    = 4;
    $error_message = 'reaction must be one of: ' . implode(', ', $allowed_reactions);
}

if (empty($error_code)) {
    $story_id       = (int)$_POST['id'];
    $reaction_type  = mysqli_real_escape_string($sqlConnect, $_POST['reaction']);
    $logged_user_id = (int)$wo['user']['user_id'];

    // Check if story exists
    $story_q = mysqli_query($sqlConnect, "SELECT id FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    if (!$story_q || mysqli_num_rows($story_q) == 0) {
        $error_code    = 5;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    // Check if reaction already exists
    $existing_result = mysqli_query($sqlConnect, "SELECT * FROM `Wo_StoryReactions` WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id} LIMIT 1");

    if ($existing_result && mysqli_num_rows($existing_result) > 0) {
        $existing = mysqli_fetch_assoc($existing_result);
        if ($existing['reaction'] === $reaction_type) {
            // Same reaction — remove it (toggle off)
            mysqli_query($sqlConnect, "DELETE FROM `Wo_StoryReactions` WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id}");
            $response_data = array(
                'api_status' => 200,
                'message'    => 'Reaction removed',
                'action'     => 'removed',
            );
        } else {
            // Different reaction — update it
            mysqli_query($sqlConnect, "UPDATE `Wo_StoryReactions` SET `reaction` = '{$reaction_type}' WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id}");
            $response_data = array(
                'api_status' => 200,
                'message'    => 'Reaction updated',
                'action'     => 'updated',
                'reaction'   => $reaction_type,
            );
        }
    } else {
        // Insert new reaction
        mysqli_query($sqlConnect, "INSERT INTO `Wo_StoryReactions` (`story_id`, `user_id`, `reaction`, `time`) VALUES ({$story_id}, {$logged_user_id}, '{$reaction_type}', " . time() . ")");
        $response_data = array(
            'api_status' => 200,
            'message'    => 'Reaction added',
            'action'     => 'added',
            'reaction'   => $reaction_type,
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
