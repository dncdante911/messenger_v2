<?php
// +------------------------------------------------------------------------+
// | Get Story Reactions Endpoint (V2 API)
// | Returns reaction counts and optionally the list of users who reacted
// | Called directly by Android: /api/v2/endpoints/get_story_reactions.php
// | OR via router: ?type=get_story_reactions
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['story_id']) || !is_numeric($_POST['story_id']) || $_POST['story_id'] < 1) {
    $error_code    = 5;
    $error_message = 'story_id is required';
}

if (empty($error_code)) {
    $story_id       = (int)$_POST['story_id'];
    $logged_user_id = (int)$wo['user']['user_id'];
    $limit          = 20;
    $offset         = 0;

    if (!empty($_POST['limit']) && is_numeric($_POST['limit']) && (int)$_POST['limit'] > 0 && (int)$_POST['limit'] <= 50) {
        $limit = (int)$_POST['limit'];
    }
    if (!empty($_POST['offset']) && is_numeric($_POST['offset']) && (int)$_POST['offset'] > 0) {
        $offset = (int)$_POST['offset'];
    }

    // Check if story exists
    $story_q = mysqli_query($sqlConnect, "SELECT id FROM " . T_USER_STORY . " WHERE id = {$story_id} LIMIT 1");
    if (!$story_q || mysqli_num_rows($story_q) == 0) {
        $error_code    = 6;
        $error_message = 'Story not found';
    }
}

if (empty($error_code)) {
    // Get reaction counts by type
    $reaction_counts = stories_get_reactions($sqlConnect, $story_id, $logged_user_id);
    $reaction_counts['total'] = $reaction_counts['like'] + $reaction_counts['love'] + $reaction_counts['haha']
                              + $reaction_counts['wow'] + $reaction_counts['sad'] + $reaction_counts['angry'];

    // Optionally get list of users who reacted
    $users = [];
    if (!empty($_POST['get_users']) && $_POST['get_users'] == 1) {
        $reactions_q = mysqli_query($sqlConnect,
            "SELECT * FROM `Wo_StoryReactions`
             WHERE story_id = {$story_id}
             ORDER BY `time` DESC
             LIMIT {$offset}, {$limit}"
        );
        if ($reactions_q) {
            while ($r = mysqli_fetch_assoc($reactions_q)) {
                $u = stories_build_user_data((int)$r['user_id']);
                $u['reaction'] = $r['reaction'];
                $u['time'] = (int)$r['time'];
                $users[] = $u;
            }
        }
    }

    $response_data = array(
        'api_status' => 200,
        'reactions'  => $reaction_counts,
        'is_reacted' => $reaction_counts['is_reacted'],
        'type'       => $reaction_counts['type'],
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
