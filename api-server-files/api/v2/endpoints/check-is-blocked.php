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
 * Check Is Blocked Endpoint
 *
 * POST параметры:
 * - user_id: ID пользователя для проверки
 *
 * Response:
 * {
 *   "api_status": 200,
 *   "is_blocked": true/false,
 *   "blocked_by_me": true/false,  // Я заблокировал этого пользователя
 *   "blocked_me": true/false       // Этот пользователь заблокировал меня
 * }
 */

$response_data = array(
    'api_status' => 400
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
        $current_user_id = $wo['user']['user_id'];

        // Проверяем, заблокировал ли я этого пользователя
        $i_blocked_them = Wo_IsBlocked($recipient_id);

        // Проверяем, заблокировал ли этот пользователь меня
        // Для этого нужно проверить обратное направление
        $db = Wo_Db();
        $query = $db->where('blocker', $recipient_id)
                    ->where('blocked', $current_user_id)
                    ->getOne(T_BLOCKS);

        $they_blocked_me = !empty($query);

        // Общий статус блокировки (хотя бы одно направление)
        $is_blocked = $i_blocked_them || $they_blocked_me;

        $response_data = array(
            'api_status' => 200,
            'is_blocked' => $is_blocked,
            'blocked_by_me' => $i_blocked_them,
            'blocked_me' => $they_blocked_me,
            'can_message' => !$is_blocked,
            'can_call' => !$is_blocked
        );
    }
}
