<?php
// +------------------------------------------------------------------------+
// | Delete Story Endpoint (V2 API)
// | Deletes a story owned by the authenticated user
// | Called directly by Android: /api/v2/endpoints/delete-story.php
// | OR via router: ?type=delete_story
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

if (empty($_POST['story_id'])) {
    $error_code    = 4;
    $error_message = 'story_id (POST) is missing';
}

if (empty($error_code)) {
    $story_id = Wo_Secure($_POST['story_id']);
    $delete   = Wo_DeleteStatus($story_id);
    if ($delete) {
        $response_data = array(
            'api_status' => 200,
            'message'    => "Story #{$story_id} successfully deleted."
        );
    } else {
        $response_data = array(
            'api_status' => 400,
            'error_message' => 'Failed to delete story or story not found'
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
