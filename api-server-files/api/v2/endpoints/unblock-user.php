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

/**
 * Unblock User Endpoint
 *
 * POST параметры:
 * - user_id: ID пользователя для разблокировки
 *
 * Response:
 * {
 *   "api_status": 200,
 *   "block_status": "unblocked"
 * }
 */

$response_data = array(
    'api_status' => 400,
    'block_status' => 'invalid'
);

if (empty($_POST['user_id'])) {
    $error_code    = 3;
    $error_message = 'user_id (POST) is missing';
}

if (empty($error_code)) {
    $recipient_id   = Wo_Secure($_POST['user_id']);
    $recipient_data = Wo_UserData($recipient_id);

    if (empty($recipient_data)) {
        $error_code    = 6;
        $error_message = 'User not found';
    } else {
        $is_blocked = Wo_IsBlocked($recipient_id);

        if ($is_blocked === true) {
            $unblock = Wo_RemoveBlock($recipient_id);

            if ($unblock) {
                $response_data = array(
                    'api_status' => 200,
                    'block_status' => 'unblocked',
                    'message' => 'User successfully unblocked'
                );
            } else {
                $error_code    = 8;
                $error_message = 'Failed to unblock user';
            }
        } else {
            // Пользователь уже разблокирован
            $response_data = array(
                'api_status' => 200,
                'block_status' => 'already_unblocked',
                'message' => 'User is not blocked'
            );
        }
    }
}
