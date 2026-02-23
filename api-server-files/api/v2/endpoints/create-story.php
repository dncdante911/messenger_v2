<?php
// +------------------------------------------------------------------------+
// | Create Story Endpoint (V2 API)
// | Creates a new story (image or video) for the authenticated user
// | Called directly by Android: /api/v2/endpoints/create-story.php
// | OR via router: ?type=create_story
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/_stories_bootstrap.php');

$response_data = array('api_status' => 400);
$error_code    = 0;
$error_message = '';

// Validate file upload
if (empty($_FILES['file']['tmp_name'])) {
    $error_code    = 3;
    $error_message = 'file is required';
}

$allowed_file_types = array('image', 'video');
if (empty($error_code) && (empty($_POST['file_type']) || !in_array($_POST['file_type'], $allowed_file_types))) {
    $error_code    = 4;
    $error_message = 'file_type must be "image" or "video"';
}

if (empty($error_code) && !empty($_POST['story_title']) && strlen($_POST['story_title']) > 100) {
    $error_code    = 5;
    $error_message = 'story_title must not exceed 100 characters';
}

if (empty($error_code) && !empty($_POST['story_description']) && strlen($_POST['story_description']) > 300) {
    $error_code    = 6;
    $error_message = 'story_description must not exceed 300 characters';
}

if (empty($error_code)) {
    $logged_user_id = (int)$wo['user']['user_id'];
    $file_type      = Wo_Secure($_POST['file_type']);

    $registration_data = array(
        'user_id' => $logged_user_id,
        'posted'  => time(),
        'expire'  => time() + 86400,
    );

    if (!empty($_POST['story_title']) && strlen($_POST['story_title']) >= 2) {
        $registration_data['title'] = Wo_Secure($_POST['story_title']);
    }
    if (!empty($_POST['story_description']) && strlen($_POST['story_description']) >= 2) {
        $registration_data['description'] = Wo_Secure($_POST['story_description']);
    }

    $last_id = Wo_InsertUserStory($registration_data);

    if (empty($last_id) || !is_numeric($last_id)) {
        $error_code    = 7;
        $error_message = 'Failed to create story record';
    } else {
        $fileInfo = array(
            'file'  => $_FILES['file']['tmp_name'],
            'name'  => $_FILES['file']['name'],
            'size'  => $_FILES['file']['size'],
            'type'  => $_FILES['file']['type'],
            'types' => 'jpg,png,mp4,gif,jpeg,mov,webm'
        );
        $media = Wo_ShareFile($fileInfo);

        if (empty($media) || empty($media['filename'])) {
            $error_code    = 8;
            $error_message = 'Failed to upload media file';
        } else {
            $filename = $media['filename'];

            $source_data = array(
                'story_id' => $last_id,
                'type'     => $file_type,
                'filename' => $filename,
                'expire'   => time() + 86400,
            );

            // Store video duration if provided
            if ($file_type === 'video' && !empty($_POST['video_duration'])) {
                $source_data['duration'] = (int)$_POST['video_duration'];
            }

            Wo_InsertUserStoryMedia($source_data);

            $thumb = '';
            // Generate thumbnail for image types
            if (in_array(strtolower(pathinfo($filename, PATHINFO_EXTENSION)), array('gif', 'jpg', 'png', 'jpeg'))) {
                $thumb = $filename;
            }
            // Use cover file as thumbnail if provided (for video)
            if (empty($thumb) && !empty($_FILES['cover']['tmp_name'])) {
                $coverInfo = array(
                    'file'  => $_FILES['cover']['tmp_name'],
                    'name'  => $_FILES['cover']['name'],
                    'size'  => $_FILES['cover']['size'],
                    'type'  => $_FILES['cover']['type'],
                    'types' => 'jpg,png,jpeg'
                );
                $cover_media = Wo_ShareFile($coverInfo);
                if (!empty($cover_media['filename'])) {
                    $thumb = $cover_media['filename'];
                }
            }

            if (!empty($thumb)) {
                $thumb_secure = Wo_Secure($thumb);
                mysqli_query($sqlConnect, "UPDATE " . T_USER_STORY . " SET thumbnail = '$thumb_secure' WHERE id = $last_id");
            }

            // Re-fetch the story from DB to return accurate data
            $story_q = mysqli_query($sqlConnect, "SELECT * FROM " . T_USER_STORY . " WHERE id = {$last_id} LIMIT 1");
            $story_row = ($story_q && mysqli_num_rows($story_q) > 0) ? mysqli_fetch_assoc($story_q) : null;

            if ($story_row) {
                $response_data = array(
                    'api_status' => 200,
                    'message'    => 'Story created successfully',
                    'story_id'   => $last_id,
                    'story'      => stories_build_story($sqlConnect, $story_row, $logged_user_id),
                );
            } else {
                $response_data = array(
                    'api_status' => 200,
                    'message'    => 'Story created successfully',
                    'story_id'   => $last_id,
                );
            }
        }
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
