<?php
// +------------------------------------------------------------------------+
// | Get Story By ID Endpoint (V2 API)
// | Returns a single story by its ID
// | Called via index.php router: ?type=get_story_by_id
// +------------------------------------------------------------------------+

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

    $story_row = $db->where('id', $story_id)->getOne(T_USER_STORY);

    if (empty($story_row)) {
        $error_code    = 4;
        $error_message = 'Story not found';
    } else {
        $story_user_id = (int)$story_row->user_id;

        $user_data = Wo_UserData($story_user_id);
        if (!empty($user_data) && !empty($non_allowed)) {
            foreach ($non_allowed as $key => $value) {
                unset($user_data[$value]);
            }
        }

        $current_time = time();
        $posted_ts = !empty($story_row->posted) ? (int)$story_row->posted : $current_time;
        $expire_ts = !empty($story_row->expire) ? (int)$story_row->expire : ($posted_ts + 86400);

        $story = array(
            'id'            => (int)$story_row->id,
            'user_id'       => $story_user_id,
            'title'         => $story_row->title ?? '',
            'description'   => $story_row->description ?? '',
            'posted'        => $posted_ts,
            'expire'        => $expire_ts,
            'thumbnail'     => $story_row->thumbnail ?? '',
            'user_data'     => $user_data,
            'is_owner'      => ($story_user_id === $logged_user_id),
            'is_viewed'     => 0,
            'view_count'    => 0,
            'comment_count' => 0,
        );

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
