<?php
// +------------------------------------------------------------------------+
// | React Story Endpoint (V2 API)
// | Add or remove a reaction on a story (toggle)
// | Called via index.php router: ?type=react_story
// +------------------------------------------------------------------------+

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
    $reaction_type  = Wo_Secure($_POST['reaction']);
    $logged_user_id = (int)$wo['user']['user_id'];

    // Check if story exists
    $story = $db->where('id', $story_id)->getOne(T_USER_STORY);
    if (empty($story)) {
        $error_code    = 5;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    // Check if reaction already exists (using T_STORY_REACTIONS if available, else a simple approach)
    $existing_query = "SELECT * FROM `Wo_StoryReactions`
                       WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id} LIMIT 1";
    $existing_result = mysqli_query($sqlConnect, $existing_query);

    if ($existing_result && mysqli_num_rows($existing_result) > 0) {
        $existing = mysqli_fetch_assoc($existing_result);
        if ($existing['reaction'] === $reaction_type) {
            // Same reaction — remove it (toggle off)
            $del_query = "DELETE FROM `Wo_StoryReactions`
                          WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id}";
            mysqli_query($sqlConnect, $del_query);
            $response_data = array(
                'api_status' => 200,
                'message'    => 'Reaction removed',
                'action'     => 'removed',
            );
        } else {
            // Different reaction — update it
            $upd_query = "UPDATE `Wo_StoryReactions`
                          SET `reaction` = '" . $reaction_type . "'
                          WHERE `story_id` = {$story_id} AND `user_id` = {$logged_user_id}";
            mysqli_query($sqlConnect, $upd_query);
            $response_data = array(
                'api_status' => 200,
                'message'    => 'Reaction updated',
                'action'     => 'updated',
                'reaction'   => $reaction_type,
            );
        }
    } else {
        // Insert new reaction
        $ins_query = "INSERT INTO `Wo_StoryReactions` (`story_id`, `user_id`, `reaction`, `time`)
                      VALUES ({$story_id}, {$logged_user_id}, '{$reaction_type}', " . time() . ")";
        mysqli_query($sqlConnect, $ins_query);
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
